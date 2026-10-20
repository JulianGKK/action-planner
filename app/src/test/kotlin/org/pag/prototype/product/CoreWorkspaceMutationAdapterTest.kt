package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.prototype.product.ActiveWorkspaceValidity.*

class CoreWorkspaceMutationAdapterTest {
    private class H {
        val original=Binding("a",ActionStatus.ACTIVE,1,2)
        var binding:Binding?=original
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope)=object:StorageContext {
                override val scope=AccessScope.READY
                override val generation=1L
                override fun check(point:AccessPoint)=true
                override fun publish(result:()->Unit):Boolean {result();return true}
            }
        }
        val validity=ActiveWorkspaceValidity(original,access){binding}
        val port=ActiveWorkspaceMutationAdapter(validity,original)
        val all=Projection.entries.toSet()
        fun load(){val t=validity.beginRead(all)!!;assertTrue(validity.publishRead(t,all,Cleanup.COMPLETE){})}
    }
    @Test fun everyCoreFamilyInvalidatesAllDependenciesBeforeQueueOnTheSameEpoch() {
        for(family in WorkspaceWriterFamily.entries.filter{it!=WorkspaceWriterFamily.TASK}) {
            val h=H();h.load();assertTrue(h.validity.canReuse(h.all));val before=h.validity.mutationEpoch
            val write=h.port.beforeCoreQueue(WorkspaceMutation(family,if(family==WorkspaceWriterFamily.ACTION_CREATE)null else "a"),1)!!
            assertEquals(before+1,h.validity.mutationEpoch);assertTrue(h.validity.isPending())
            assertTrue(h.all.all{h.validity.isDirty(it)});assertFalse(h.validity.canReuse(h.all));assertNull(h.validity.beginRead(h.all))
            assertNull(h.port.beforeTaskQueue("a",1));assertEquals(before+1,h.validity.mutationEpoch)
            assertTrue(write.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)))
            assertFalse(h.validity.canReuse(h.all));assertTrue(write.isCurrent());h.load();assertFalse(write.isCurrent())
        }
    }
    @Test fun actionSwitchStatusExitAndUnknownNeverResurrectAnActiveBinding() {
        for(change in listOf("action","generation","permission","status-exit","unknown","cleanup")) {
            val h=H();h.load()
            val write=h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.ACTION_STATUS,"a"),1)!!
            when(change){
                "action"->h.binding=h.original.copy(actionId="b")
                "generation"->h.binding=h.original.copy(generation=2)
                "permission"->h.binding=h.original.copy(permissionRevision=3)
                "status-exit"->h.binding=null // COMPLETED is not a valid ACTIVE Binding
            }
            val accepted=write.finish(WorkspaceWriteCompletion(if(change=="unknown")WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.COMMITTED,
                if(change=="cleanup")WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED else WorkspaceWriteCleanup.COMPLETE))
            if(change in listOf("unknown","cleanup")){assertTrue(accepted);assertEquals(State.RECOVERY_REQUIRED,h.validity.state)}
            else{assertFalse(accepted);assertEquals(State.REVOKED,h.validity.state)}
            assertFalse(write.isCurrent());assertFalse(h.validity.canReuse(h.all));assertNull(h.validity.beginRead(h.all))
        }
    }
    @Test fun taskOnlyAdaptersRefuseUnknownCoreFamiliesRatherThanBypassInvalidation() {
        var tasks=0
        val old=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long):WorkspaceMutationInvalidation.Pending? {tasks++;return null}
        }
        assertNull(old.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.PERSON,"a"),1));assertEquals(0,tasks)
        val h=H();h.load()
        assertNull(h.port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,"b"),1))
        assertEquals(State.REVOKED,h.validity.state);assertFalse(h.validity.canReuse(h.all))
    }
}
