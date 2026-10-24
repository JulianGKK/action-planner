package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real Domain port only: caller-supplied synthetic metadata, no repository, DB, file I/O,
 * Data evidence producer, session authorization or runtime DOCUMENT integration. */
class DocumentPendingContractTest {
    private fun complete()=DocumentWriteCompletion(DocumentWriteOutcome.COMPLETE,WorkspaceWriteCleanup.COMPLETE,true)
    private fun notExecuted()=DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE)
    private fun coreCompletion(outcome:WorkspaceWriteOutcome=WorkspaceWriteOutcome.COMMITTED)=
        WorkspaceWriteCompletion(outcome,WorkspaceWriteCleanup.COMPLETE)
    private fun operations(port:UncachedWorkspaceMutations,generation:Long=1)=
        port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,"a"),generation)
    private fun assertBusy(port:UncachedWorkspaceMutations,generation:Long=1) {
        assertNull(port.beforeTaskQueue("a",generation));assertNull(operations(port,generation))
        assertNull(port.beforeDocumentQueue("a",generation));assertFalse(port.ramReuseEnabled)
    }
    @Test fun taskAndOperationsPendingDenyDocumentOnTheSamePort() {
        for(task in listOf(true,false)) {
            val port=UncachedWorkspaceMutations()
            val core=if(task)port.beforeTaskQueue("a",1)!! else operations(port)!!
            assertEquals(1L,port.mutationEpoch);assertNull(port.beforeDocumentQueue("a",1))
            assertFalse(core.isCurrent());assertEquals(1L,port.mutationEpoch)
            assertTrue(core.finish(coreCompletion()))
            val document=port.beforeDocumentQueue("a",1)!!
            assertEquals(2L,port.mutationEpoch);assertTrue(document.finishDocument(notExecuted()))
        }
    }
    @Test fun documentPendingDeniesTaskOperationsAndSecondDocumentWithoutAdvancingEpoch() {
        val port=UncachedWorkspaceMutations();val document=port.beforeDocumentQueue("a",1)!!
        assertEquals(1L,port.mutationEpoch);assertBusy(port);assertEquals(1L,port.mutationEpoch)
        assertFalse(document.isCurrent());assertTrue(document.finishDocument(complete()))
        val core=operations(port)!!;assertEquals(2L,port.mutationEpoch);assertFalse(document.isCurrent())
        assertTrue(core.finish(coreCompletion()))
    }
    @Test fun alternatingDocumentAndCoreAdmissionsUseExactlyOneEpoch() {
        val port=UncachedWorkspaceMutations()
        repeat(4) { index ->
            val doc=port.beforeDocumentQueue("a",1)!!
            assertEquals(index*2L+1,port.mutationEpoch);doc.finishDocument(notExecuted())
            val task=port.beforeTaskQueue("a",1)!!
            assertEquals(index*2L+2,port.mutationEpoch);task.finish(coreCompletion(WorkspaceWriteOutcome.ROLLED_BACK))
            assertFalse(doc.isCurrent());assertFalse(port.ramReuseEnabled)
        }
    }
    @Test fun blankActionsAndNegativeGenerationsCannotAdmitOrChangeEpoch() {
        val port=UncachedWorkspaceMutations()
        for(action in listOf(""," ","\t\n"))assertNull(port.beforeDocumentQueue(action,1))
        for(g in listOf(-1L,Long.MIN_VALUE))assertNull(port.beforeDocumentQueue("a",g))
        assertEquals(0L,port.mutationEpoch)
        val valid=port.beforeDocumentQueue("a",0)!! // zero is allowed by the existing generation contract
        assertEquals(1L,port.mutationEpoch);valid.finishDocument(notExecuted())
    }
    @Test fun pendingWithoutCompletionBlocksSameGenerationButNewReadyGenerationInvalidatesIt() {
        val port=UncachedWorkspaceMutations();val lost=port.beforeDocumentQueue("a",1)!!
        assertBusy(port);assertEquals(1L,port.mutationEpoch)
        val fresh=port.beforeDocumentQueue("a",2)!!
        assertFalse(lost.isCurrent());assertFalse(lost.finishDocument(complete()));assertBusy(port,2)
        assertEquals(2L,port.mutationEpoch);fresh.finishDocument(notExecuted())
    }
    @Test fun defaultTaskOnlyAndCoreCapableAdaptersDenyDocumentsWithoutFallback() {
        var tasks=0;var cores=0
        val taskOnly=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long):WorkspaceMutationInvalidation.Pending? {tasks++;return null}
        }
        val coreCapable=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long):WorkspaceMutationInvalidation.Pending? {tasks++;return null}
            override fun beforeCoreQueue(mutation:WorkspaceMutation,generation:Long):WorkspaceMutationInvalidation.Pending? {cores++;error("No fallback")}
        }
        assertNull(taskOnly.beforeDocumentQueue("a",1));assertNull(coreCapable.beforeDocumentQueue("a",1))
        assertEquals(0,tasks);assertEquals(0,cores)
    }
    @Test fun oldPendingDefaultDocumentCompletionCannotConsumeCoreWork() {
        var finished=false
        val old=object:WorkspaceMutationInvalidation.Pending {
            override fun finish(completion:WorkspaceWriteCompletion):Boolean {finished=true;return true}
            override fun isCurrent()=finished
        }
        assertFalse(old.finishDocument(complete()));assertFalse(finished)
        assertTrue(old.finish(coreCompletion()));assertTrue(finished)
    }
    @Test fun notExecutedWithConfirmedCleanupEndsPendingWithoutBecomingMutationSuccess() {
        val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
        assertTrue(ticket.finishDocument(notExecuted()));assertTrue(ticket.isCurrent());assertFalse(port.ramReuseEnabled)
        // The same current-ticket result occurs for NOT_EXECUTED and COMPLETE: not a success API.
        val next=port.beforeDocumentQueue("a",1)!!;assertFalse(ticket.isCurrent())
        assertTrue(next.finishDocument(complete()));assertTrue(next.isCurrent());assertFalse(port.ramReuseEnabled)
    }
    @Test fun completeRequiresExplicitFinalInventoryAssertionBeforeItCanBeSubmitted() {
        val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
        for(cleanup in WorkspaceWriteCleanup.entries) {
            assertThrows(IllegalArgumentException::class.java){DocumentWriteCompletion(DocumentWriteOutcome.COMPLETE,cleanup)}
        }
        assertBusy(port);assertEquals(1L,port.mutationEpoch)
        assertTrue(ticket.finishDocument(complete()))
    }
    @Test fun dataClassCopyCannotRemoveRequiredFinalInventoryAssertion() {
        val value=complete()
        assertThrows(IllegalArgumentException::class.java){value.copy(finalInventoryConfirmed=false)}
        assertEquals(DocumentWriteOutcome.COMPLETE,value.outcome)
        assertTrue(value.finalInventoryConfirmed)
    }
    @Test fun allTypedOutcomesAndCleanupCombinationsFollowConservativeReleaseRules() {
        for(outcome in DocumentWriteOutcome.entries) for(cleanup in WorkspaceWriteCleanup.entries) {
            val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
            val completion=DocumentWriteCompletion(outcome,cleanup,outcome==DocumentWriteOutcome.COMPLETE)
            assertTrue(ticket.finishDocument(completion));assertEquals(1L,port.mutationEpoch)
            val safe=outcome in setOf(DocumentWriteOutcome.NOT_EXECUTED,DocumentWriteOutcome.COMPLETE) && cleanup==WorkspaceWriteCleanup.COMPLETE
            assertEquals(safe,ticket.isCurrent());assertFalse(port.ramReuseEnabled)
            if(safe) {
                val next=operations(port)!!;assertEquals(2L,port.mutationEpoch)
                assertFalse(ticket.isCurrent());next.finish(coreCompletion())
            } else {
                assertBusy(port);assertEquals(1L,port.mutationEpoch)
            }
            assertFalse(ticket.finishDocument(completion))
        }
    }
    @Test fun inventoryAssertionCannotOverrideUnknownRecoveryOrFailedCleanup() {
        for(outcome in listOf(DocumentWriteOutcome.UNKNOWN,DocumentWriteOutcome.RECOVERY_REQUIRED)) {
            val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
            assertTrue(ticket.finishDocument(DocumentWriteCompletion(outcome,WorkspaceWriteCleanup.COMPLETE,true)))
            assertFalse(ticket.isCurrent());assertBusy(port)
        }
        val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
        assertTrue(ticket.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.COMPLETE,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,true)))
        assertBusy(port);assertFalse(ticket.isCurrent())
    }
    @Test fun duplicateDocumentFinishAndWrongCoreFinishCannotReleaseLaterPending() {
        val port=UncachedWorkspaceMutations();val old=port.beforeDocumentQueue("a",1)!!
        assertTrue(old.finishDocument(complete()));assertFalse(old.finishDocument(complete()))
        val next=port.beforeDocumentQueue("a",1)!!
        assertFalse(old.finishDocument(notExecuted()));assertFalse(old.finish(coreCompletion()))
        assertBusy(port);assertTrue(next.finishDocument(notExecuted()));assertFalse(old.isCurrent())
    }
    @Test fun everyCoreOutcomeOnDocumentTicketIsRejectedWithoutConsumingIt() {
        val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
        for(outcome in WorkspaceWriteOutcome.entries) for(cleanup in WorkspaceWriteCleanup.entries) {
            assertFalse(ticket.finish(WorkspaceWriteCompletion(outcome,cleanup)))
            assertBusy(port);assertEquals(1L,port.mutationEpoch)
        }
        assertTrue(ticket.finishDocument(complete()));assertTrue(ticket.isCurrent())
    }
    @Test fun everyDocumentOutcomeOnCoreTicketIsRejectedWithoutConsumingIt() {
        val port=UncachedWorkspaceMutations();val core=operations(port)!!
        for(outcome in DocumentWriteOutcome.entries) for(cleanup in WorkspaceWriteCleanup.entries) {
            assertFalse(core.finishDocument(DocumentWriteCompletion(outcome,cleanup,outcome==DocumentWriteOutcome.COMPLETE)))
            assertBusy(port);assertEquals(1L,port.mutationEpoch)
        }
        assertTrue(core.finish(coreCompletion()));assertTrue(core.isCurrent())
    }
    @Test fun ticketFromOtherPortCanOnlyFinishItsOwnPendingAndCannotReleaseThisOne() {
        val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
        val other=UncachedWorkspaceMutations();val foreign=other.beforeDocumentQueue("a",1)!!
        assertNotSame(ticket,foreign)
        assertTrue(foreign.finishDocument(complete())) // receiver owns only OTHER port, no target-ticket API
        assertBusy(port);assertFalse(ticket.isCurrent());assertEquals(1L,port.mutationEpoch)
        assertTrue(ticket.finishDocument(notExecuted()));assertFalse(foreign.finishDocument(notExecuted()))
    }
    @Test fun oldGenerationAndOldTicketsCannotFinishFreshDocumentOrCoreWork() {
        val port=UncachedWorkspaceMutations();val old=port.beforeDocumentQueue("a",1)!!
        assertTrue(old.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
        assertBusy(port);assertFalse(old.isCurrent())
        // Metadata fixture stands for a future caller's separately authorized READY generation.
        val fresh=port.beforeDocumentQueue("a",2)!!
        assertFalse(old.finishDocument(complete()));assertFalse(old.finish(coreCompletion()));assertFalse(old.isCurrent())
        assertBusy(port,2);assertEquals(2L,port.mutationEpoch);fresh.finishDocument(notExecuted())
        val core=operations(port,2)!!;assertFalse(old.finishDocument(notExecuted()));assertFalse(fresh.finishDocument(complete()))
        assertTrue(core.finish(coreCompletion()));assertFalse(old.isCurrent());assertFalse(fresh.isCurrent())
    }
    @Test fun oldCompletedTicketNeverBecomesCurrentAgainAcrossGenerationChanges() {
        val port=UncachedWorkspaceMutations();val old=port.beforeDocumentQueue("a",1)!!
        old.finishDocument(complete());assertTrue(old.isCurrent())
        val next=port.beforeTaskQueue("a",2)!!;assertFalse(old.isCurrent());next.finish(coreCompletion())
        val latest=port.beforeDocumentQueue("a",3)!!;latest.finishDocument(complete())
        assertFalse(old.isCurrent());assertFalse(next.isCurrent());assertTrue(latest.isCurrent())
    }
    @Test fun authorizedNewGenerationInvalidatesAnUnfinishedLostDispatchTicket() {
        val port=UncachedWorkspaceMutations();val lost=port.beforeDocumentQueue("a",1)!!
        assertBusy(port,1)
        val fresh=port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"a"),2)!!
        assertFalse(lost.isCurrent());assertFalse(lost.finishDocument(complete()));assertFalse(lost.finish(coreCompletion()))
        assertEquals(2L,port.mutationEpoch);assertBusy(port,2)
        assertTrue(fresh.finish(coreCompletion()));assertFalse(lost.isCurrent())
    }
    @Test fun newGenerationDoesNotChangeUnfinishedCoreTicketBlockingRule() {
        val port=UncachedWorkspaceMutations();val lostCore=operations(port,1)!!
        assertNull(port.beforeDocumentQueue("a",2));assertNull(operations(port,2));assertEquals(1L,port.mutationEpoch)
        assertTrue(lostCore.finish(coreCompletion()));val fresh=port.beforeDocumentQueue("a",2)!!
        assertEquals(2L,port.mutationEpoch);fresh.finishDocument(notExecuted())
    }
    @Test fun coreUnknownAndCleanupFailuresAlsoBlockDocumentAdmissionInSameGeneration() {
        for(cleanupFailure in listOf(false,true)) {
            val port=UncachedWorkspaceMutations();val core=port.beforeTaskQueue("a",1)!!
            core.finish(WorkspaceWriteCompletion(if(cleanupFailure)WorkspaceWriteOutcome.COMMITTED else WorkspaceWriteOutcome.UNKNOWN,
                if(cleanupFailure)WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED else WorkspaceWriteCleanup.COMPLETE))
            assertBusy(port);assertFalse(core.isCurrent())
            val fresh=port.beforeDocumentQueue("a",2)!!;assertFalse(core.isCurrent());fresh.finishDocument(notExecuted())
        }
    }
    @Test fun wrongThreadCannotAdmitFinishObserveOrChangeOwnerPending() {
        val port=UncachedWorkspaceMutations();val document=port.beforeDocumentQueue("a",1)!!
        val pool=Executors.newSingleThreadExecutor()
        try {
            pool.submit {
                assertThrows(IllegalStateException::class.java){port.beforeDocumentQueue("a",1)}
                assertThrows(IllegalStateException::class.java){port.beforeTaskQueue("a",1)}
                assertThrows(IllegalStateException::class.java){operations(port)}
                assertThrows(IllegalStateException::class.java){document.finishDocument(complete())}
                assertThrows(IllegalStateException::class.java){document.finish(coreCompletion())}
                assertThrows(IllegalStateException::class.java){document.isCurrent()}
            }.get(5,TimeUnit.SECONDS)
            assertBusy(port);assertEquals(1L,port.mutationEpoch);assertTrue(document.finishDocument(notExecuted()))
            val core=operations(port)!!
            pool.submit {
                assertThrows(IllegalStateException::class.java){core.finishDocument(complete())}
                assertThrows(IllegalStateException::class.java){core.finish(coreCompletion())}
            }.get(5,TimeUnit.SECONDS)
            assertBusy(port);assertTrue(core.finish(coreCompletion()))
        } finally {pool.shutdownNow()}
    }
    @Test fun completionHasOnlyOutcomeCleanupAndInventoryMetadataAndNoPublicationApi() {
        val fields=DocumentWriteCompletion::class.java.declaredFields.filterNot{it.isSynthetic}
        assertEquals(setOf(DocumentWriteOutcome::class.java,WorkspaceWriteCleanup::class.java,java.lang.Boolean.TYPE),fields.map{it.type}.toSet())
        assertEquals(3,fields.size)
        val port=UncachedWorkspaceMutations();val ticket=port.beforeDocumentQueue("a",1)!!
        assertTrue(ticket.finishDocument(complete()));assertTrue(ticket.isCurrent());assertFalse(port.ramReuseEnabled)
        // No read/hit/clean or callback operation is introduced on the metadata port.
        assertEquals(setOf("finish","finishDocument","isCurrent","recordWorkCompletion"),WorkspaceMutationInvalidation.Pending::class.java.declaredMethods.filterNot{it.isSynthetic}.map{it.name}.toSet())
    }
}
