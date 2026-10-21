package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.io.File

/** Read tickets are metadata from the real shared writer port, never session authority. */
class ActiveReadAdmissionTest {
    private fun core(port:UncachedWorkspaceMutations,generation:Long=1)=port.beforeCoreQueue(
        WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),generation)!!
    private fun complete(p:WorkspaceMutationInvalidation.Pending) =
        assertTrue(p.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)))
    private fun document(port:UncachedWorkspaceMutations,generation:Long=1)=
        port.beforeDocumentQueue("action",generation)!!
    private fun completeDocument(p:WorkspaceMutationInvalidation.Pending) = assertTrue(p.finishDocument(
        DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE)))

    @Test fun freePortIssuesOpaqueCurrentTicketWithoutAdvancingMutationEpoch() {
        val port=UncachedWorkspaceMutations()
        val epoch=port.mutationEpoch
        val ticket=port.beforeActiveRead("action",1)!!
        assertEquals("action",ticket.actionId);assertEquals(1L,ticket.generation)
        assertEquals(epoch,ticket.mutationEpoch);assertTrue(ticket.isLive())
        assertTrue(port.isCurrentActiveRead(ticket));assertEquals(epoch,port.mutationEpoch)
        assertFalse(port.ramReuseEnabled)
    }

    @Test fun coreAndDocumentPendingDenyReadAndAdmissionRevokesOldTicketSynchronously() {
        for(isDocument in listOf(false,true)) {
            val port=UncachedWorkspaceMutations();val read=port.beforeActiveRead("action",1)!!
            val pending=if(isDocument)document(port) else core(port)
            assertFalse(read.isLive());assertFalse(port.isCurrentActiveRead(read))
            assertNull(port.beforeActiveRead("action",1));assertEquals(1L,port.mutationEpoch)
            if(isDocument)completeDocument(pending) else complete(pending)
            assertFalse(read.isLive());assertFalse(port.isCurrentActiveRead(read))
            val next=port.beforeActiveRead("action",1)!!
            assertEquals(1L,next.mutationEpoch);assertNotSame(read,next)
        }
    }

    @Test fun everyCoreWriterFamilyAndDocumentShareTheSameReadRevocation() {
        val mutations=WorkspaceWriterFamily.entries.map { family ->
            WorkspaceMutation(family,if(family==WorkspaceWriterFamily.ACTION_CREATE)null else "action")
        }
        for(mutation in mutations) {
            val port=UncachedWorkspaceMutations();val read=port.beforeActiveRead("action",1)!!
            val pending=port.beforeCoreQueue(mutation,1)!!
            assertFalse("${mutation.family} must revoke before queue",read.isLive())
            assertFalse(port.isCurrentActiveRead(read));assertEquals(1L,port.mutationEpoch)
            complete(pending)
        }
        val port=UncachedWorkspaceMutations();val read=port.beforeActiveRead("action",1)!!
        val pending=document(port);assertFalse(read.isLive());completeDocument(pending)
    }

    @Test fun unknownRecoveryAndUnconfirmedCleanupDenyReadForGeneration() {
        val completions=listOf(
            WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE),
            WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED))
        for(completion in completions) {
            val port=UncachedWorkspaceMutations();val read=port.beforeActiveRead("action",1)!!
            val pending=core(port);assertFalse(read.isLive())
            assertTrue(pending.finish(completion));assertNull(port.beforeActiveRead("action",1))
        }
        for(completion in listOf(
            DocumentWriteCompletion(DocumentWriteOutcome.RECOVERY_REQUIRED,WorkspaceWriteCleanup.COMPLETE),
            DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE),
            DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED))) {
            val port=UncachedWorkspaceMutations();val read=port.beforeActiveRead("action",1)!!
            val pending=document(port);assertFalse(read.isLive())
            assertTrue(pending.finishDocument(completion));assertNull(port.beforeActiveRead("action",1))
        }
    }

    @Test fun invalidInputAndForeignPortCannotAdmitOrValidateTicket() {
        val port=UncachedWorkspaceMutations()
        assertNull(port.beforeActiveRead("",1));assertNull(port.beforeActiveRead("action",-1))
        assertEquals(0L,port.mutationEpoch)
        val other=UncachedWorkspaceMutations()
        val ticket=port.beforeActiveRead("action",1)!!
        assertFalse(other.isCurrentActiveRead(ticket));assertFalse(other.ramReuseEnabled)
    }

    @Test fun newActionBindingInvalidatesThePreviousReadTicket() {
        val port=UncachedWorkspaceMutations();val old=port.beforeActiveRead("first-action",1)!!
        val next=port.beforeActiveRead("second-action",1)!!
        assertFalse(old.isLive());assertFalse(port.isCurrentActiveRead(old))
        assertTrue(port.isCurrentActiveRead(next));assertEquals(0L,port.mutationEpoch)
    }

    @Test fun ownerThreadControlsAdmissionAndFinalCheckButWorkerCanObserveRevocation() {
        val port=UncachedWorkspaceMutations();val ticket=port.beforeActiveRead("action",1)!!
        val threadResult=java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker=Thread {
            try { port.beforeActiveRead("action",1) } catch(error:Throwable) { threadResult.set(error) }
        }
        worker.start();worker.join()
        assertTrue(threadResult.get() is IllegalStateException)
        assertTrue(ticket.isLive())
        val pending=core(port);assertFalse(ticket.isLive())
        val finalCheck=Thread {
            try { port.isCurrentActiveRead(ticket) } catch(error:Throwable) { threadResult.set(error) }
        }
        finalCheck.start();finalCheck.join()
        assertTrue(threadResult.get() is IllegalStateException)
        complete(pending)
    }

    @Test fun newReadGenerationAndExplicitRevokeNeverRestoreOldTicket() {
        val port=UncachedWorkspaceMutations();val old=port.beforeActiveRead("action",1)!!
        val fresh=port.beforeActiveRead("action",2)!!
        assertFalse(old.isLive());assertFalse(port.isCurrentActiveRead(old))
        assertTrue(port.isCurrentActiveRead(fresh));assertEquals(0L,port.mutationEpoch)
        port.revokeActiveRead();assertFalse(fresh.isLive());assertFalse(port.isCurrentActiveRead(fresh))
        val later=port.beforeActiveRead("other-action",2)!!
        assertNotSame(fresh,later);assertTrue(later.isLive())
    }

    @Test fun lostWriterCompletionKeepsReadAdmissionClosedAndNoSecondEpochExists() {
        val port=UncachedWorkspaceMutations();val old=port.beforeActiveRead("action",1)!!
        val pending=document(port)
        assertEquals(1L,port.mutationEpoch);assertFalse(old.isLive())
        // A lost Main Runnable leaves the actual shared document pending.
        assertNull(port.beforeActiveRead("action",1));assertEquals(1L,port.mutationEpoch)
        completeDocument(pending)
        val next=port.beforeActiveRead("action",1)!!
        assertEquals(1L,next.mutationEpoch);assertFalse(port.ramReuseEnabled)
    }

    @Test fun epochOverflowRevokesReadAndFailsClosedWithoutWrapping() {
        val port=UncachedWorkspaceMutations();val ticket=port.beforeActiveRead("action",1)!!
        val field=UncachedWorkspaceMutations::class.java.getDeclaredField("mutationEpoch").also{it.isAccessible=true}
        field.setLong(port,Long.MAX_VALUE)
        assertNull(port.beforeTaskQueue("action",1))
        assertFalse(ticket.isLive());assertNull(port.beforeActiveRead("action",1))
        assertEquals(Long.MAX_VALUE,port.mutationEpoch);assertFalse(port.ramReuseEnabled)
    }

    @Test fun unpreparedLegacyAdapterDeniesActiveReadAdmissionByDefault() {
        val legacy=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long):WorkspaceMutationInvalidation.Pending?=null
        }
        assertNull(legacy.beforeActiveRead("action",1))
        assertFalse(legacy.isCurrentActiveRead(object:WorkspaceMutationInvalidation.ActiveReadTicket {
            override val actionId="action";override val generation=1L;override val mutationEpoch=0L
            override fun isLive()=true
        }))
    }

    @Test fun runtimeRevokesThroughTheSameWriterPortOnLockBlockedAndClose() {
        val runtime=listOf(File("src/main/kotlin/org/pag/data/product/ProductRuntime.kt"),
            File("data/src/main/kotlin/org/pag/data/product/ProductRuntime.kt")).first{it.isFile}.readText()
        assertTrue(runtime.contains("private val workspaceMutations: WorkspaceMutationInvalidation"))
        val lifetime=runtime.substringAfter("private val keyLifetime = session.observe")
            .substringBefore("fun start(")
        assertTrue(lifetime.contains("SessionState.LOCKED, SessionState.BLOCKED"))
        assertTrue(lifetime.contains("workspaceMutations.revokeActiveRead()"))
        val close=runtime.substringAfter("fun close()")
            .substringBefore("val actions:")
        assertTrue(close.contains("workspaceMutations.revokeActiveRead()"))
    }
}
