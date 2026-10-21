package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.io.File
import java.util.concurrent.Executor

/** Production mutation dispatch + the actual shared bounded port. Evidence construction here
 * checks dispatch handoff only; producer phase evidence is covered by §64 tests. */
class DocumentWriterIntegrationTest {
    private class Queue : Executor {
        val tasks=ArrayDeque<Runnable>()
        var reject=false
        override fun execute(command:Runnable) { if(reject)throw java.util.concurrent.RejectedExecutionException();tasks.addLast(command) }
        fun take()=tasks.removeFirst()
        fun drain(){while(tasks.isNotEmpty())take().run()}
    }
    private class Ctx(var valid:Boolean=true):StorageContext {
        override var generation=17L
        override val scope=AccessScope.READY
        var publications=0
        override fun check(point:AccessPoint)=valid
        override fun publish(result:()->Unit):Boolean {if(!valid)return false;publications++;result();return true}
    }
    private fun evidence(operation:DocumentMutationEvidence.Operation,complete:Boolean=false):DocumentMutationEvidence {
        val e=DocumentMutationEvidence(operation);e.begin(operation)
        if(complete) {
            val steps=if(operation==DocumentMutationEvidence.Operation.IMPORT)
                listOf(DocumentMutationEvidence.Step.RESERVED,DocumentMutationEvidence.Step.WRITING,
                    DocumentMutationEvidence.Step.CREATE,DocumentMutationEvidence.Step.CIPHER_CLOSE,
                    DocumentMutationEvidence.Step.FILE_SYNC,DocumentMutationEvidence.Step.CLOSED,
                    DocumentMutationEvidence.Step.RENAME,DocumentMutationEvidence.Step.SYNC_FILES,
                    DocumentMutationEvidence.Step.SYNC_STAGING,DocumentMutationEvidence.Step.PUBLISHED,
                    DocumentMutationEvidence.Step.DOCUMENT_COMMIT)
            else listOf(DocumentMutationEvidence.Step.DELETE_RESERVED,DocumentMutationEvidence.Step.DETACH,
                DocumentMutationEvidence.Step.UNLINK,DocumentMutationEvidence.Step.DELETE_SYNC,
                DocumentMutationEvidence.Step.DELETE_FINISH)
            steps.forEach(e::returned);e.finalInventoryConfirmed()
        } else e.returned(if(operation==DocumentMutationEvidence.Operation.IMPORT)
            DocumentMutationEvidence.Step.WRITING else DocumentMutationEvidence.Step.DETACH)
        return e
    }
    private inner class H {
        val port=UncachedWorkspaceMutations()
        val worker=Queue();val main=Queue();val context=Ctx()
        var accessCalls=0;var callbacks=0;var workCalls=0
        val access=object:StorageAccess {override fun acquire(scope:AccessScope):StorageContext? {accessCalls++;assertEquals(AccessScope.READY,scope);return context} }
        val dispatch=DocumentProductDispatch(access,worker,main,{error("unexpected blocked callback")})
        fun submit(action:String="action",e:DocumentMutationEvidence=evidence(DocumentMutationEvidence.Operation.IMPORT,true),
            value:DocumentResult<Unit> = DocumentResult.Value(Unit)) {
            dispatch.runMutation(action,port,e,{callbacks++}) {workCalls++;value}
        }
    }

    @Test fun sharedCorePendingDeniesImportBeforeWorkerAndSideEffects() {
        val h=H();val core=h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17)!!
        val before=h.port.mutationEpoch;h.submit()
        assertEquals(before,h.port.mutationEpoch);assertEquals(1,h.accessCalls) // one READY context, no second acquisition
        assertTrue(h.worker.tasks.isEmpty());assertEquals(0,h.workCalls);assertEquals(0,h.callbacks)
        core.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE))
    }

    @Test fun admittedDocumentAdvancesOnlyCommonEpochAndBlocksCoreAndSecondDocument() {
        val h=H();h.submit();assertEquals(1L,h.port.mutationEpoch);assertEquals(1,h.worker.tasks.size)
        assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,"action"),17))
        assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17))
        assertNull(h.port.beforeDocumentQueue("action",17));assertEquals(1L,h.port.mutationEpoch)
    }

    @Test fun completeImportAndDeleteConsumeTheSamePendingBeforeOneCallback() {
        for(op in DocumentMutationEvidence.Operation.entries) {
            val h=H();val before=h.port.mutationEpoch
            h.submit(e=evidence(op,true));assertEquals(before+1,h.port.mutationEpoch)
            assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17))
            h.worker.take().run();assertEquals(0,h.callbacks);assertEquals(1,h.workCalls)
            h.main.take().run();assertEquals(1,h.callbacks);assertEquals(1,h.context.publications)
            assertEquals(before+1,h.port.mutationEpoch)
            assertFalse(h.port.ramReuseEnabled)
            val core=h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17)!!
            assertEquals(before+2,h.port.mutationEpoch)
            core.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE))
        }
    }

    @Test fun partialEffectRejectionConsumesTicketAsUncertainAndNeverPublishes() {
        val h=H();h.submit(e=evidence(DocumentMutationEvidence.Operation.IMPORT),
            value=DocumentResult.Rejected(DocumentFailure.SOURCE_UNAVAILABLE))
        h.worker.drain();h.main.drain()
        assertEquals(0,h.callbacks);assertEquals(1L,h.port.mutationEpoch)
        assertNull(h.port.beforeDocumentQueue("action",17));assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17))
    }

    @Test fun rejectionBeforeEffectsMayPublishOnlyAfterNotExecutedCompletion() {
        val h=H();h.submit(e=DocumentMutationEvidence(DocumentMutationEvidence.Operation.IMPORT),
            value=DocumentResult.Rejected(DocumentFailure.INVALID))
        h.worker.drain();assertEquals(0,h.callbacks);h.main.drain();assertEquals(1,h.callbacks)
        assertEquals(1L,h.port.mutationEpoch)
        val core=h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17)!!
        core.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE))
    }

    @Test fun replayedWorkerAndMainCannotRepeatWorkOrCallback() {
        val h=H();h.submit();val worker=h.worker.take();worker.run();worker.run()
        assertEquals(1,h.workCalls);val main=h.main.take();main.run();main.run()
        assertEquals(1,h.callbacks);assertEquals(1,h.context.publications)
    }

    @Test fun revokeBeforeWorkerCanReleaseOnlyProvenNotExecutedAndNeverCallback() {
        val h=H();h.submit();h.context.valid=false;h.worker.drain();h.main.drain()
        assertEquals(0,h.workCalls);assertEquals(0,h.callbacks);assertEquals(1L,h.port.mutationEpoch)
        // A newly authorized context generation, not this stale ticket, is required for later admission.
        val next=h.port.beforeDocumentQueue("action",17)!!
        next.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE))
    }

    @Test fun lostMainRunnableLeavesTheSharedPendingBusy() {
        val h=H();h.submit();h.worker.drain();assertEquals(1,h.main.tasks.size)
        assertEquals(1L,h.port.mutationEpoch)
        assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,"action"),17))
        assertNull(h.port.beforeDocumentQueue("action",17));assertEquals(0,h.callbacks)
    }

    @Test fun separatelyAuthorizedNewReadyGenerationInvalidatesAbandonedTicketWithoutReplay() {
        val h=H();h.submit();h.worker.take().run();val staleMain=h.main.take()
        assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17))
        h.context.generation=18;h.submit()
        assertEquals(2L,h.port.mutationEpoch);assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),18))
        h.worker.take().run();h.main.take().run();staleMain.run()
        assertEquals(2,h.workCalls);assertEquals(1,h.callbacks);assertEquals(1,h.context.publications)
        val core=h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),18)!!
        assertEquals(3L,h.port.mutationEpoch);core.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE))
    }

    @Test fun fenceBeforeWorkerProvesNoExecutionAndDoesNotPublish() {
        val h=H();h.submit();DocumentNamespaceLane.drainAndFence{}
        h.worker.drain();h.main.drain()
        assertEquals(0,h.workCalls);assertEquals(0,h.callbacks)
        val next=h.port.beforeDocumentQueue("action",17)!!
        next.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE))
    }

    @Test fun unexpectedWorkerExceptionAfterAdmissionLeavesGenerationTerminallyUncertain() {
        val h=H();val e=evidence(DocumentMutationEvidence.Operation.IMPORT,true)
        h.dispatch.runMutation<Unit>("action",h.port,e,{h.callbacks++}) { throw IllegalStateException("synthetic worker fault") }
        h.worker.drain();h.main.drain()
        assertEquals(0,h.callbacks);assertNull(h.port.beforeDocumentQueue("action",17))
    }

    @Test fun wrongOwnerThreadCannotConsumePending() {
        val h=H();h.submit();h.worker.drain();val main=h.main.take()
        val failed=java.util.concurrent.atomic.AtomicBoolean(false)
        val thread=Thread { try{main.run()}catch(_:IllegalStateException){failed.set(true)} }
        thread.start();thread.join()
        assertTrue(failed.get());assertEquals(0,h.callbacks)
        assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),17))
    }

    @Test fun refusedMainQueueLeavesPendingAndDoesNotInventCompletion() {
        val h=H();h.submit();h.main.reject=true;h.worker.drain()
        assertEquals(0,h.callbacks)
        assertNull(h.port.beforeDocumentQueue("action",17))
        assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,"action"),17))
    }

    @Test fun fenceBetweenWorkerAndMainPreservesPhysicalCompletionButSuppressesCallback() {
        val h=H();h.submit();h.worker.drain();DocumentNamespaceLane.drainAndFence{}
        h.main.drain();assertEquals(0,h.callbacks);assertEquals(1,h.workCalls)
        val next=h.port.beforeDocumentQueue("action",17)!! // completed metadata ticket is safe; old lane cannot publish
        assertEquals(2L,h.port.mutationEpoch)
        next.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE))
    }

    @Test fun missingPortInvalidActionAndStaleContextNeverQueue() {
        val h=H();val e=evidence(DocumentMutationEvidence.Operation.IMPORT,true)
        h.dispatch.runMutation<Unit>("action",null,e,{error("no callback")}){error("no work")}
        h.dispatch.runMutation<Unit>(" ",h.port,e,{error("no callback")}){error("no work")}
        h.context.valid=false;h.dispatch.runMutation<Unit>("action",h.port,e,{error("no callback")}){error("no work")}
        assertTrue(h.worker.tasks.isEmpty());assertEquals(0L,h.port.mutationEpoch)
    }

    @Test fun runtimePassesItsSingleMutationOwnerToDocumentsAndReadsUseReadDispatch() {
        fun source(name:String)=listOf(File("src/main/kotlin/org/pag/data/product/$name.kt"),
            File("data/src/main/kotlin/org/pag/data/product/$name.kt")).first{it.isFile}.readText()
        val runtime=source("ProductRuntime")
        val docs=runtime.substringAfter("val documents: DocumentRepository").substringBefore("val operations")
        assertTrue(docs.contains("mutations = workspaceMutations"))
        val repository=source("ProductDocumentRepository")
        assertTrue(repository.contains("dispatch.runMutation(actionId,mutations,evidence"))
        for(read in listOf("summary","list","readPdf","readText","readImage")) {
            assertFalse("$read must remain a reader",repository.substringAfter("override fun $read(").substringBefore("\n    }").contains("runMutation"))
        }
        assertTrue(repository.contains("private fun <T> run(publish: (DocumentResult<T>) -> Unit, work: (StorageContext) -> DocumentResult<T>) = dispatch.run(publish,work)"))
    }
}
