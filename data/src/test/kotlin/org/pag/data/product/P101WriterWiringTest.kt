package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.io.File

/** Source checks supplement actual boundary/SQLite/P77 tests; not standalone runtime proof. */
class P101WriterWiringTest {
    @Test fun originalRuntimePortIsInjectedAndMaintenanceRevokesBeforeWorkerQueue() {
        val source=File("src/main/kotlin/org/pag/data/product/ProductRuntime.kt").readText()
        val start=source.indexOf("authorizedMaintenance =")
        assertTrue(start>=0)
        val revoke=source.indexOf("workspaceMutations.revokeActiveRead()",start)
        assertTrue(revoke>=start);assertTrue(source.indexOf("worker.execute",start)>revoke)
        val leadership=source.substring(source.indexOf("val leadership:"),source.indexOf("private val leadershipLifetime"))
        assertTrue(leadership.contains("mutations=workspaceMutations"));assertTrue(leadership.contains("blocked=session::storageFailure"))
        val repo=File("src/main/kotlin/org/pag/data/product/ProductLeadershipRepository.kt").readText()
        assertFalse(repo.contains(".withWriteTransaction"))
        assertTrue(repo.contains("WorkspaceWriterFamily.LEADERSHIP"))
        assertTrue(repo.contains("e.transaction(tx,c)"));assertTrue(repo.contains("cleanupConfirmed={cleanup=it}"))
    }
    @Test fun leadershipProgressContainsOnlyOriginalProgressAndPrimitiveMetadata() {
        val fields=LeadershipWriteProgress::class.java.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}
        assertTrue(fields.isNotEmpty())
        assertTrue(fields.all{it.type.isPrimitive || it.type==WorkspaceWriteProgress::class.java})
    }
    @Test fun leadershipAndDocumentUseOnePendingAndUnknownRequiresFreshGeneration() {
        val port=UncachedWorkspaceMutations()
        val doc=port.beforeDocumentQueue("a",1)!!
        assertNull(port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.LEADERSHIP,"a"),1))
        assertTrue(doc.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.COMPLETE,WorkspaceWriteCleanup.COMPLETE,true)))
        val leadership=port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.LEADERSHIP,"a"),1)!!
        assertNull(port.beforeDocumentQueue("a",1));assertEquals(2L,port.mutationEpoch)
        assertTrue(leadership.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
        assertNull(port.beforeTaskQueue("a",1));assertNull(port.beforeDocumentQueue("a",1));assertNull(port.beforeActiveRead("a",1))
        // Only a real product READY-context producer may supply this rebinding in product.
        val recovered=port.beforeDocumentQueue("a",2)!!
        assertFalse(leadership.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)))
        assertTrue(recovered.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE)))
        assertFalse(port.ramReuseEnabled)
    }
    @Test fun absentOrUnconfirmedWorkEndNeverRebindsAndLateOldReceiptsCannotAcknowledgeB() {
        for(cleanup in listOf(null,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)) {
            val port=UncachedWorkspaceMutations();val a=port.beforeTaskQueue("a",1)!!
            if(cleanup!=null)a.recordWorkCompletion(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,cleanup))
            assertNull(port.beforeTaskQueue("a",2));assertNull(port.beforeActiveRead("a",2))
        }
        val port=UncachedWorkspaceMutations();val a=port.beforeTaskQueue("a",1)!!
        val committed=WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)
        a.recordWorkCompletion(committed)
        a.recordWorkCompletion(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE))
        assertFalse(a.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
        assertNull(port.beforeActiveRead("a",1))
        val b=port.beforeTaskQueue("a",2)!!;val epoch=port.mutationEpoch
        assertFalse(a.finish(committed));a.recordWorkCompletion(committed)
        assertNull(port.beforeActiveRead("a",2));assertEquals(epoch,port.mutationEpoch)
        assertTrue(b.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
    }

}
