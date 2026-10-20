package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

/** Dispatch characterization only: state below is a simulated durable ledger, not SQLite evidence.
 * Gap tests deliberately describe existing behavior; they do not approve that behavior for reuse. */
class DocumentRecoveryDispatchContractTest {
    private class Queue : Executor {
        val tasks = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { tasks.add(command) }
        fun next() = tasks.removeAt(0).run()
    }
    private val worker = Queue()
    private val main = Queue()
    private var generation = 1L
    private var ready = true
    private var callbacks = 0
    private var blocked = 0
    private var mutations = 0
    private var phase = "NONE"
    private val access = object : StorageAccess {
        override fun acquire(scope: AccessScope): StorageContext? {
            if (!ready) return null
            val acquired = generation
            return object : StorageContext {
                override val generation = acquired
                override val scope = AccessScope.READY
                override fun check(point: AccessPoint) = ready && acquired == this@DocumentRecoveryDispatchContractTest.generation
                override fun publish(result: () -> Unit): Boolean {
                    if (!check(AccessPoint.STEP)) return false
                    result()
                    return true
                }
            }
        }
    }
    private fun dispatch(w: Executor = worker, m: Executor = main) = DocumentProductDispatch(access, w, m) { blocked++ }
    private fun submit(d: DocumentProductDispatch = dispatch()) = d.run<Unit>({ callbacks++ }) {
        mutations++
        phase = "FILE_PUBLISHED"
        DocumentResult.Value(Unit)
    }

    @Test fun lockBeforeWorkerLeavesSimulatedLedgerUntouched() {
        submit(); ready = false; worker.next()
        assertEquals("NONE", phase); assertEquals(0, mutations); assertTrue(main.tasks.isEmpty())
    }
    @Test fun generationChangeAfterPhysicalStepSuppressesPublicationWithoutUndo() {
        submit(); worker.next(); generation++; main.next()
        assertEquals("FILE_PUBLISHED", phase); assertEquals(1, mutations); assertEquals(0, callbacks)
    }
    @Test fun blockedBeforeAcquisitionDoesNotDispatch() {
        ready = false; submit()
        assertTrue(worker.tasks.isEmpty()); assertEquals("NONE", phase); assertEquals(0, callbacks)
    }
    @Test fun lostMainDispatchDoesNotAutomaticallyRetryOrUndo() {
        submit(); worker.next(); main.tasks.clear()
        assertEquals(1, mutations); assertEquals("FILE_PUBLISHED", phase); assertEquals(0, callbacks)
        assertTrue(worker.tasks.isEmpty())
    }
    @Test fun workerDispatchFailureEscapesWithoutMutationOrCallback() {
        assertThrows(IllegalStateException::class.java) { submit(dispatch(Executor { throw IllegalStateException("synthetic dispatch") })) }
        assertEquals(0, mutations); assertEquals(0, callbacks); assertEquals(0, blocked)
    }
    @Test fun mainDispatchFailureCannotUndoAlreadyExecutedWork() {
        submit(dispatch(m = Executor { throw IllegalStateException("synthetic dispatch") }))
        assertThrows(IllegalStateException::class.java) { worker.next() }
        assertEquals(1, mutations); assertEquals("FILE_PUBLISHED", phase); assertEquals(0, callbacks)
    }
    @Test fun exceptionAfterDurableStepBlocksWithoutFabricatingRollback() {
        dispatch().run<Unit>({ callbacks++ }) {
            mutations++; phase = "IMPORT_WRITING"; throw IllegalStateException("synthetic cleanup")
        }
        worker.next(); main.next()
        assertEquals("IMPORT_WRITING", phase); assertEquals(1, blocked); assertEquals(0, callbacks)
    }
    @Test fun fenceBeforeWorkerRejectsOldTicketWithoutStorageEffect() {
        submit(); DocumentNamespaceLane.drainAndFence {}; worker.next(); main.next()
        assertEquals(0, mutations); assertEquals(0, callbacks); assertEquals(1, blocked)
    }
    @Test fun gapDuplicateWorkerExecutesWorkTwice() {
        submit(); val runnable = worker.tasks.removeAt(0); runnable.run(); runnable.run()
        main.next(); main.next()
        assertEquals(2, mutations) // OPEN: desired at-most-once assertion (1) reproduced RED in §57
        assertEquals(2, callbacks)
    }
    @Test fun gapDuplicateMainPublishesTwice() {
        submit(); worker.next(); val runnable = main.tasks.removeAt(0); runnable.run(); runnable.run()
        assertEquals(1, mutations); assertEquals(2, callbacks) // OPEN: desired at-most-once assertion (1) reproduced RED in §57
    }
    @Test fun gapFenceAfterWorkerDoesNotFenceQueuedMainPublication() {
        submit(); worker.next(); DocumentNamespaceLane.drainAndFence {}; main.next()
        assertEquals("FILE_PUBLISHED", phase); assertEquals(1, callbacks) // OPEN: desired post-fence suppression (0) reproduced RED in §57
    }
    @Test fun gapDocumentDispatchDoesNotConsultCommonPendingOrAdvanceItsEpoch() {
        val common=UncachedWorkspaceMutations()
        val pending=common.beforeTaskQueue("synthetic-action",generation)!!
        submit();worker.next();main.next()
        assertEquals(1,mutations);assertEquals(1,callbacks);assertEquals(1L,common.mutationEpoch)
        assertNull(common.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,"synthetic-action"),generation))
        assertFalse(common.ramReuseEnabled)
        assertTrue(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
    }
}
