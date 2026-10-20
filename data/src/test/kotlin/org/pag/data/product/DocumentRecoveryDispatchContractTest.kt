package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

/** Dispatch characterization only: state below is a simulated durable ledger, not SQLite evidence.
 * Lifecycle tests enforce one-shot dispatch and observed fence invalidation; no global commit claim. */
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
    private var beforePublication: () -> Unit = {}
    private var publicationAllowed = true
    private var publicationReturn = true
    private val access = object : StorageAccess {
        override fun acquire(scope: AccessScope): StorageContext? {
            if (!ready) return null
            val acquired = generation
            return object : StorageContext {
                override val generation = acquired
                override val scope = AccessScope.READY
                override fun check(point: AccessPoint) = ready && acquired == this@DocumentRecoveryDispatchContractTest.generation
                override fun publish(result: () -> Unit): Boolean {
                    beforePublication()
                    if (!publicationAllowed || !check(AccessPoint.STEP)) return false
                    result()
                    return publicationReturn
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
    @Test fun duplicateWorkerExecutesWorkOnce() {
        submit(); val runnable = worker.tasks.removeAt(0); runnable.run(); runnable.run()
        assertEquals(1,main.tasks.size);main.next()
        assertEquals(1, mutations);assertEquals(1, callbacks)
    }
    @Test fun duplicateMainPublishesOnce() {
        submit(); worker.next(); val runnable = main.tasks.removeAt(0); runnable.run(); runnable.run()
        assertEquals(1, mutations); assertEquals(1, callbacks)
    }
    @Test fun fenceAfterWorkerSuppressesQueuedMainPublication() {
        submit(); worker.next(); DocumentNamespaceLane.drainAndFence {}; main.next()
        assertEquals("FILE_PUBLISHED", phase); assertEquals(0, callbacks)
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
    @Test fun duplicateWorkerAndMainRemainOneShot() {
        submit();val w=worker.tasks.removeAt(0);w.run();w.run()
        assertEquals(1,main.tasks.size);val m=main.tasks.removeAt(0);m.run();m.run()
        assertEquals(1,mutations);assertEquals(1,callbacks)
    }
    @Test fun fenceDuringContextPublicationSuppressesCallback() {
        submit();worker.next();beforePublication={DocumentNamespaceLane.drainAndFence{}}
        main.next();assertEquals(1,mutations);assertEquals(0,callbacks)
    }
    @Test fun callbackThenFalseDoesNotPermitReplay() {
        publicationReturn=false;submit();worker.next()
        val m=main.tasks.removeAt(0);m.run();m.run()
        assertEquals(1,callbacks);assertEquals(1,mutations)
    }
    @Test fun deniedPublicationCannotBeReplayedAfterPermissionReturns() {
        publicationAllowed=false;submit();worker.next();val m=main.tasks.removeAt(0)
        m.run();publicationAllowed=true;m.run();assertEquals(0,callbacks);assertEquals(1,mutations)
    }
    @Test fun duplicatedFailingWorkerAndMainBlockOnceWithoutRetry() {
        dispatch().run<Unit>({callbacks++}){mutations++;phase="DELETE_FILE_PENDING";error("synthetic after detach")}
        val w=worker.tasks.removeAt(0);w.run();w.run();assertEquals(1,main.tasks.size)
        val m=main.tasks.removeAt(0);m.run();m.run()
        assertEquals(1,mutations);assertEquals("DELETE_FILE_PENDING",phase)
        assertEquals(1,blocked);assertEquals(0,callbacks)
    }
    @Test fun completedPublicationIsNotUndoneByLaterFence() {
        submit();worker.next();val m=main.tasks.removeAt(0);m.run()
        DocumentNamespaceLane.drainAndFence{};m.run()
        assertEquals(1,callbacks);assertEquals("FILE_PUBLISHED",phase);assertEquals(1,mutations)
    }
    @Test fun summaryReadExecutesAndPublishesOnceWithoutMutation() {
        var reads=0
        dispatch().run({r:DocumentResult<DocumentSummary> -> assertEquals(DocumentResult.Value(DocumentSummary(3)),r);callbacks++}) {
            reads++;DocumentResult.Value(DocumentSummary(3))
        }
        val w=worker.tasks.removeAt(0);w.run();w.run();val m=main.tasks.removeAt(0);m.run();m.run()
        assertEquals(1,reads);assertEquals(1,callbacks);assertEquals(0,mutations);assertEquals("NONE",phase)
    }
    @Test fun rejectionPublishesOnceAndDoesNotClaimMutation() {
        var reads=0
        dispatch().run<Unit>({assertEquals(DocumentResult.Rejected(DocumentFailure.NOT_ALLOWED),it);callbacks++}) {
            reads++;DocumentResult.Rejected(DocumentFailure.NOT_ALLOWED)
        }
        val w=worker.tasks.removeAt(0);w.run();w.run();val m=main.tasks.removeAt(0);m.run();m.run()
        assertEquals(1,reads);assertEquals(1,callbacks);assertEquals(0,mutations)
    }
    @Test fun concurrentWorkerExecutionStartsOnlyOneWorkclosure() {
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        dispatch().run<Unit>({callbacks++}) {
            mutations++;entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))
            DocumentResult.Value(Unit)
        }
        val w=worker.tasks.removeAt(0)
        try {
            val first=pool.submit{w.run()};assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            pool.submit{w.run()}.get(5,java.util.concurrent.TimeUnit.SECONDS)
            release.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(1,mutations);assertEquals(1,main.tasks.size);main.next();assertEquals(1,callbacks)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun listReadKeepsResultAndDoesNotAddReadsOrFileWork() {
        var reads=0
        dispatch().run({r:DocumentResult<List<DocumentAsset>> -> assertEquals(DocumentResult.Value(emptyList<DocumentAsset>()),r);callbacks++}) {
            reads++;DocumentResult.Value(emptyList<DocumentAsset>())
        }
        val w=worker.tasks.removeAt(0);w.run();w.run();val m=main.tasks.removeAt(0);m.run();m.run()
        assertEquals(1,reads);assertEquals(1,callbacks);assertEquals(0,mutations)
    }
    @Test fun readAfterRevokeNeverPublishesEvenWhenMainIsReplayed() {
        var reads=0
        dispatch().run<Unit>({callbacks++}){reads++;DocumentResult.Value(Unit)}
        worker.next();val m=main.tasks.removeAt(0);ready=false;m.run();ready=true;m.run()
        assertEquals(1,reads);assertEquals(0,callbacks)
    }
    @Test fun concurrentMainExecutionPublishesOnlyOnce() {
        submit();worker.next();val m=main.tasks.removeAt(0)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        beforePublication={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
        try {
            val first=pool.submit{m.run()};assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            pool.submit{m.run()}.get(5,java.util.concurrent.TimeUnit.SECONDS)
            release.countDown();first.get(5,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(1,callbacks);assertEquals(1,mutations)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun mainDoesNotWaitForLongRunningNamespaceWork() {
        submit();worker.next();val m=main.tasks.removeAt(0)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val ticket=DocumentNamespaceLane.ticket()
        try {
            val holding=pool.submit{DocumentNamespaceLane.exclusive(ticket){
                entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))
            }}
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            pool.submit{m.run()}.get(2,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(1,callbacks);release.countDown();holding.get(5,java.util.concurrent.TimeUnit.SECONDS)
        } finally {release.countDown();pool.shutdownNow()}
    }
}
