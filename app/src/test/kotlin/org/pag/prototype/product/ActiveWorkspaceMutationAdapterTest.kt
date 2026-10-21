package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.prototype.product.ActiveWorkspaceValidity.*

class ActiveWorkspaceMutationAdapterTest {
    private class H(private val testGeneration:Long=1) {
        val initial=Binding("a",ActionStatus.ACTIVE,testGeneration,2)
        var binding:Binding?=initial
        var allowed=true
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext?=if(!allowed)null else object:StorageContext {
                override val scope=AccessScope.READY
                override val generation=testGeneration
                override fun check(point:AccessPoint)=allowed
                override fun publish(result:()->Unit):Boolean {if(!allowed)return false;result();return true}
            }
        }
        val validity=ActiveWorkspaceValidity(initial,access){binding}
        val adapter=ActiveWorkspaceMutationAdapter(validity,initial)
        val parts=ActiveWorkspaceMutationAdapter.parts
        fun load(){val read=validity.beginRead(parts)!!;assertTrue(validity.publishRead(read,parts,Cleanup.COMPLETE){})}
    }
    @Test fun synchronousBeforeQueueInvalidatesPeopleCardsTasksAndCountsAndPendingBlocksReads() {
        val h=H();h.load();assertTrue(h.validity.canReuse(h.parts))
        val write=h.adapter.beforeTaskQueue("a",1)!!
        assertTrue(h.validity.isPending());assertFalse(h.validity.canReuse(h.parts));assertNull(h.validity.beginRead(h.parts))
        assertTrue(h.parts.all{h.validity.isDirty(it)})
        assertNull(h.adapter.beforeTaskQueue("a",1));assertFalse(write.isCurrent())
        assertTrue(write.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)))
        assertTrue(write.isCurrent());assertFalse(h.validity.canReuse(h.parts)) // result != complete board patch
        assertFalse(write.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)))
        h.load();assertFalse(write.isCurrent());assertTrue(h.validity.canReuse(h.parts))
    }
    @Test fun rollbackStaysDirtyAndUnknownOrCleanupFailureIsTerminal() {
        for(outcome in WorkspaceWriteOutcome.entries) for(cleanup in WorkspaceWriteCleanup.entries) {
            val h=H();h.load();val write=h.adapter.beforeTaskQueue("a",1)!!
            assertTrue(write.finish(WorkspaceWriteCompletion(outcome,cleanup)))
            assertFalse(h.validity.canReuse(h.parts))
            if(outcome==WorkspaceWriteOutcome.UNKNOWN || cleanup!=WorkspaceWriteCleanup.COMPLETE) {
                assertEquals(State.RECOVERY_REQUIRED,h.validity.state);assertFalse(write.isCurrent());assertNull(h.validity.beginRead(h.parts))
            } else {assertEquals(State.ACTIVE,h.validity.state);assertTrue(write.isCurrent());h.load()}
        }
    }
    @Test fun scopeGenerationActionPermissionAndRevokeFenceLateResultsAndForeignTickets() {
        for(change in listOf("generation","action","permission","unknown","revoke")) {
            val h=H();val write=h.adapter.beforeTaskQueue("a",1)!!
            assertTrue(write.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)))
            when(change) {
                "generation"->h.binding=h.initial.copy(generation=2)
                "action"->h.binding=h.initial.copy(actionId="b")
                "permission"->h.binding=h.initial.copy(permissionRevision=3)
                "unknown"->h.binding=null
                else->h.allowed=false
            }
            assertFalse(write.isCurrent());assertEquals(State.REVOKED,h.validity.state)
        }
        for(action in listOf(true,false)) {
            val h=H();h.load()
            assertNull(h.adapter.beforeTaskQueue(if(action)"b" else "a",if(action)1 else 2))
            assertEquals(State.REVOKED,h.validity.state)
        }
    }
    @Test fun newReadOrEditorMutationFencesOldWriterResultWithoutMarkingAnyProjectionClean() {
        val h=H();val old=h.adapter.beforeTaskQueue("a",1)!!
        old.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE))
        h.adapter.beforeTaskQueue("a",1)!!
        assertFalse(old.isCurrent());assertFalse(h.validity.canReuse(h.parts))
        val other=H();val completed=other.adapter.beforeTaskQueue("a",1)!!
        completed.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE))
        other.validity.beginRead(other.parts)!!
        assertFalse(completed.isCurrent());assertFalse(other.validity.canReuse(other.parts))
    }
    /** Proposed conservative document fallback uses UNKNOWN; exercise the existing TASK port
     * only. DOCUMENT projection labels/adapter and runtime hookup remain UNWIRED. */
    @Test fun partialMultiPhaseFallbackNeverRestoresOldReadsEvenAfterNewReadyGeneration() {
        val old=H();old.load()
        val stale=old.validity.beginRead(old.parts)!!
        val pending=old.adapter.beforeTaskQueue("a",1)!!
        assertTrue(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
        var publications=0
        assertFalse(old.validity.publishRead(stale,old.parts,Cleanup.COMPLETE){publications++})
        assertEquals(State.RECOVERY_REQUIRED,old.validity.state)
        old.binding=old.initial.copy(generation=2)
        assertFalse(old.validity.canReuse(old.parts));assertNull(old.validity.beginRead(old.parts))
        val fresh=H(2)
        assertFalse(fresh.validity.canReuse(fresh.parts))
        fresh.load();assertTrue(fresh.validity.canReuse(fresh.parts))
        assertFalse(old.validity.publishRead(stale,old.parts,Cleanup.COMPLETE){publications++})
        assertEquals(0,publications);assertFalse(pending.isCurrent())
    }

    @Test fun unpreparedActiveAdapterDeniesDocumentsAndTypedFinishWithoutCoreFallbackOrCleanChanges() {
        val h=H();h.load();val before=h.validity.mutationEpoch
        assertNull(h.adapter.beforeDocumentQueue("a",1))
        assertEquals(before,h.validity.mutationEpoch);assertFalse(h.validity.isPending())
        val core=h.adapter.beforeTaskQueue("a",1)!!
        assertFalse(core.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.COMPLETE,WorkspaceWriteCleanup.COMPLETE,true)))
        assertTrue(h.validity.isPending());assertFalse(h.validity.canReuse(h.parts))
        assertTrue(core.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)))
        assertFalse(h.validity.canReuse(h.parts))
        assertNull(h.adapter.beforeDocumentQueue("a",1));assertFalse(h.validity.canReuse(h.parts))
        h.load();assertTrue(h.validity.canReuse(h.parts))
    }

}
