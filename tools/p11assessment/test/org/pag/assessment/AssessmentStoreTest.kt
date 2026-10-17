package org.pag.assessment
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
class AssessmentStoreTest {
    private class Harness {
        var allowed=true;var generation=1L;var publicationAllowed=true
        var scope:AssessmentStore.Binding?=AssessmentStore.Binding("synthetic",1,ActionStatus.ACTIVE,0)
        val access=object:StorageAccess {override fun acquire(scope:AccessScope):StorageContext? {
            if(!allowed)return null
            val g=generation
            return object:StorageContext {
                override val generation=g;override val scope=AccessScope.READY
                override fun check(point:AccessPoint)=allowed && generation==this@Harness.generation
                override fun publish(result:()->Unit):Boolean {if(!check(AccessPoint.PUBLICATION)||!publicationAllowed)return false;result();return true}
            }
        }}
        val store=AssessmentStore(access,{scope},1024)
        val read=WorkspaceRead("synthetic",ActionStatus.ACTIVE,1,setOf(WorkspacePart.TASKS),OperationalBoard("synthetic",ActionStatus.ACTIVE,emptyList(),emptyList()),TaskBoard("synthetic",emptyList(),emptyList()))
        val request=WorkspaceReadRequest("synthetic",ActionStatus.ACTIVE,1,setOf(WorkspacePart.TASKS))
        fun install()=store.install(scope!!,store.ticket(),read,100)
    }
    @Test fun authorizedHit(){val h=Harness();assertTrue(h.install());assertNotNull(h.store.read(h.request))}
    @Test fun noAuthorizationNoHit(){val h=Harness();h.install();h.allowed=false;assertNull(h.store.read(h.request));assertFalse(h.store.retainedForTest())}
    @Test fun changedGeneration(){val h=Harness();h.install();h.generation++;assertNull(h.store.read(h.request));assertFalse(h.store.retainedForTest())}
    @Test fun changedAction(){val h=Harness();h.install();h.scope=h.scope!!.copy(actionId="other");assertNull(h.store.read(h.request))}
    @Test fun changedVersion(){val h=Harness();h.install();h.scope=h.scope!!.copy(version=1);assertNull(h.store.read(h.request))}
    @Test fun changedPermissionOrStatus(){val h=Harness();h.install();h.scope=h.scope!!.copy(status=ActionStatus.DRAFT);assertNull(h.store.read(h.request))}
    @Test fun lateLoadCannotResurrect(){val h=Harness();val t=h.store.ticket();h.store.clear();assertFalse(h.store.install(h.scope!!,t,h.read,100))}
    @Test fun deniedPublication(){val h=Harness();h.publicationAllowed=false;assertFalse(h.install());assertFalse(h.store.retainedForTest())}
    @Test fun cancelRequest(){val h=Harness();h.install();val t=WorkspaceReadTicket();t.cancel();assertNull(h.store.read(h.request.copy(ticket=t)))}
    @Test fun missingPartMiss(){val h=Harness();h.install();assertNull(h.store.read(h.request.copy(parts=setOf(WorkspacePart.MATERIAL_BOARD))))}
    @Test fun budgetDegrades(){val h=Harness();assertFalse(h.store.install(h.scope!!,h.store.ticket(),h.read,1025));assertFalse(h.store.retainedForTest())}
    @Test fun unchangedVersionKeepsRead(){val h=Harness();h.install();assertNotNull(h.store.read(h.request));/* no optimistic install on failure */}
    @Test fun versionInvalidationClears(){val h=Harness();h.install();h.scope=h.scope!!.copy(version=1);h.store.clear();assertNull(h.store.read(h.request))}
    @Test fun oldVersionTokenIsNotCurrent(){val h=Harness();val old=h.scope;h.install();h.scope=h.scope!!.copy(version=1);assertNotEquals(old,h.scope);assertNull(h.store.read(h.request))}
}
