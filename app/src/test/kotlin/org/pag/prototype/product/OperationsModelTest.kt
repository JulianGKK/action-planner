package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class OperationsModelTest {
    private var snapshot=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.PREPARATION)
    private class Repo:OperationsRepository {
        var reads=0;var captured:ExpectedPersonRolePlan?=null;
        var status=ActionStatus.PREPARATION;var writes=0;var created:String?=null;var callback:((OperationsResult<OperationalBoard>)->Unit)?=null;var defer=false
        val roles=listOf(Role("s","a","Support",0, editRevision = 0),Role("g","a","Gesa-Support",0, editRevision = 0))
        var person=OperationalPerson(Person("p","a","synthetic",0, editRevision = 0),PersonRolePlan(setOf("s","g"),"s", editRevision = 0),PersonOperationalState("a","p","s",OperationalStatus.NO_STATUS,null,0, editRevision = 0))
        override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit){reads++;if(defer)callback=publish else publish(OperationsResult.Value(OperationalBoard(actionId,status,roles,listOf(person))))}
        override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit){writes++;created=name;publish(OperationsResult.Value(Role("new",actionId,name,1, editRevision = 0)))}
        override fun renameRole(actionId:String,expected: Role,name:String,publish:(OperationsResult<Role>)->Unit){writes++;publish(OperationsResult.Value(roles[0].copy(name=name)))}
        override fun deleteRole(actionId:String,expected: Role,publish:(OperationsResult<Unit>)->Unit){writes++;publish(OperationsResult.Value(Unit))}
        override fun planRoles(actionId:String,personId:String,expected: ExpectedPersonRolePlan, plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit){writes++;captured=expected;if(expected.actionId!=actionId || expected.personId!=personId || expected.plan!=person.plan)publish(OperationsResult.Rejected(OperationsFailure.STALE)) else {person=person.copy(plan=plan);publish(OperationsResult.Value(Unit))}}
        override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit){
            writes++
            if(expected!=person.state){publish(OperationsResult.Rejected(OperationsFailure.STALE));return}
            person=person.copy(state=expected.copy(status=status,currentRoleId=roleId.takeIf{OperationalRules.roleAllowed(status)},currentPlaceLabel=OperationalRules.place(place,status),updatedAt=1,editRevision=expected.editRevision+1))
            publish(OperationsResult.Value(Unit))
        }
    }
    private val repo=Repo();private var revoked=0;private var interactions=0
    private val messages=ArrayList<String>()
    private val model=OperationsModel(repo,{snapshot},{action},{interactions++},{revoked++},feedback={messages.add(it)})
    private fun enter(status:ActionStatus){action=action.copy(status=status);repo.status=status;model.refresh()}
    @Test fun stalePlanKeepsDraftAndOnlyExplicitReloadReplacesExpected() {
        enter(ActionStatus.PREPARATION);model.plan("p")
        val original=repo.person.plan;val reads=repo.reads
        model.toggleRole("s");model.primary("g")
        repo.person=repo.person.copy(plan=PersonRolePlan(setOf("s"),"s", editRevision = 0))
        model.savePlan()
        assertEquals(OperationsFailure.STALE,model.error);assertEquals(OperationsScreen.PLAN,model.screen)
        assertEquals(setOf("g"),model.roleIds);assertEquals("g",model.primaryRoleId)
        assertEquals(original,repo.captured!!.plan);assertEquals(reads,repo.reads);assertTrue(messages.isEmpty())
        model.savePlan();assertEquals(original,repo.captured!!.plan) // No implicit retry with fresh expected.
        model.reloadPlan()
        assertNull(model.error);assertEquals(setOf("s"),model.roleIds);assertEquals("s",model.primaryRoleId)
        model.toggleRole("g");model.primary("g");model.savePlan()
        assertEquals(PersonRolePlan(setOf("s"),"s", editRevision = 0),repo.captured!!.plan)
        assertEquals(OperationsScreen.OVERVIEW,model.screen)
    }
    @Test fun mutableSourceCannotChangeEditorExpected() {
        val source=linkedSetOf("s","g")
        repo.person=repo.person.copy(plan=PersonRolePlan(source,"s", editRevision = 0))
        enter(ActionStatus.PREPARATION);model.plan("p");source.remove("g")
        model.primary("g");model.savePlan()
        assertEquals(setOf("s","g"),repo.captured!!.plan.roleIds)
        assertEquals(OperationsFailure.STALE,model.error)
    }
    @Test fun failedReloadKeepsDraftAndDuplicateReloadCannotReplaceItLater() {
        enter(ActionStatus.PREPARATION);model.plan("p");model.toggleRole("s")
        repo.person=repo.person.copy(plan=PersonRolePlan(emptySet(),null, editRevision = 0));model.savePlan()
        repo.defer=true;model.reloadPlan();val old=repo.callback!!
        old(OperationsResult.Rejected(OperationsFailure.NOT_FOUND))
        assertEquals(setOf("g"),model.roleIds);assertEquals(OperationsScreen.PLAN,model.screen)
        old(OperationsResult.Value(OperationalBoard("a",ActionStatus.PREPARATION,repo.roles,listOf(repo.person))))
        assertEquals(setOf("g"),model.roleIds)
        model.savePlan();assertEquals(OperationsFailure.STALE,model.error)
        model.reloadPlan();val fresh=repo.callback!!
        fresh(OperationsResult.Value(OperationalBoard("a",ActionStatus.PREPARATION,repo.roles,listOf(repo.person))))
        model.toggleRole("g")
        fresh(OperationsResult.Value(OperationalBoard("a",ActionStatus.PREPARATION,repo.roles,listOf(repo.person))))
        assertEquals(setOf("g"),model.roleIds)
    }
    @Test fun reloadCannotRestoreDraftAcrossLockGenerationActionOrLostPlanningPermission() {
        for(at in listOf("lock","generation","action","permission","clear")) {
            snapshot=SessionSnapshot(SessionState.READY,1);action=action.copy(id="a",status=ActionStatus.PREPARATION)
            repo.defer=false;repo.person=repo.person.copy(plan=PersonRolePlan(setOf("s","g"),"s", editRevision = 0))
            model.clear();model.refresh();model.plan("p");model.toggleRole("s")
            repo.person=repo.person.copy(plan=PersonRolePlan(emptySet(),null, editRevision = 0));model.savePlan()
            repo.defer=true;model.reloadPlan();val late=repo.callback!!
            when(at){"lock"->{snapshot=SessionSnapshot(SessionState.LOCKED,2);model.clear()};"generation"->snapshot=SessionSnapshot(SessionState.READY,2);"action"->action=action.copy(id="b");"permission"->action=action.copy(status=ActionStatus.ACTIVE);else->model.clear()}
            val draft=model.roleIds
            late(OperationsResult.Value(OperationalBoard("a",ActionStatus.PREPARATION,repo.roles,listOf(repo.person))))
            assertEquals(at,draft,model.roleIds)
        }
    }
    @Test fun liveStaleKeepsDraftUntilExplicitReloadAndUsesPersistedRevision(){
        enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.IN_CUSTODY);model.place("Draft")
        val original=repo.person.state!!
        repo.person=repo.person.copy(state=original.copy(status=OperationalStatus.UNCLEAR,currentPlaceLabel="Other",editRevision=1))
        model.saveLive();assertEquals(OperationsFailure.STALE,model.error);assertEquals("Draft",model.livePlace);assertTrue(messages.isEmpty())
        model.saveLive();assertEquals(OperationsFailure.STALE,model.error)
        model.reloadLive();assertEquals("Other",model.livePlace);assertEquals(OperationalStatus.UNCLEAR,model.liveStatus);assertNull(model.error)
        model.place("Explicit fresh draft");model.saveLive();assertEquals(2L,repo.person.state!!.editRevision)
    }
    @Test fun liveReloadDuplicateAndOldSessionCallbacksCannotReplaceDraft(){
        for(change in listOf("lock","generation","action","permission","clear")){
            snapshot=SessionSnapshot(SessionState.READY,1);action=action.copy(id="a",status=ActionStatus.ACTIVE);repo.status=ActionStatus.ACTIVE;repo.defer=false
            repo.person=repo.person.copy(state=PersonOperationalState("a","p",null,OperationalStatus.UNCLEAR,"A",42,0))
            model.clear();model.refresh();model.update("p");model.place("Draft")
            repo.person=repo.person.copy(state=repo.person.state!!.copy(editRevision=1));model.saveLive()
            repo.defer=true;model.reloadLive();val old=repo.callback!!
            when(change){"lock"->{snapshot=SessionSnapshot(SessionState.LOCKED,2);model.clear()};"generation"->snapshot=SessionSnapshot(SessionState.READY,2);"action"->action=action.copy(id="b");"permission"->action=action.copy(status=ActionStatus.COMPLETED);else->model.clear()}
            val draft=model.livePlace
            repeat(2){old(OperationsResult.Value(OperationalBoard("a",ActionStatus.ACTIVE,repo.roles,listOf(repo.person))))}
            assertEquals(change,draft,model.livePlace)
        }
    }
    @Test fun draftOffersNoPlanOrLive(){enter(ActionStatus.DRAFT);model.plan("p");model.update("p");assertFalse(model.planning);assertFalse(model.live);assertEquals(OperationsScreen.OVERVIEW,model.screen)}
    @Test fun preparationAllowsMultipleAndOnePrimary(){enter(ActionStatus.PREPARATION);model.plan("p");model.primary("g");model.savePlan();assertEquals(setOf("s","g"),repo.person.plan.roleIds);assertEquals("g",repo.person.plan.primaryRoleId);assertEquals(1,repo.writes)}
    @Test fun cancelPlanPersistsNothing(){enter(ActionStatus.PREPARATION);model.plan("p");model.toggleRole("s");model.cancel();assertEquals(0,repo.writes);assertEquals("s",repo.person.plan.primaryRoleId)}
    @Test fun templateSelectionUsesNormalCreate(){enter(ActionStatus.PREPARATION);model.roles();model.createRole(OperationalRules.templates[0]);assertEquals("Blockierende",repo.created);assertEquals(1,repo.writes)}
    @Test fun activePseudonymOpensLiveAndRevokesSensitive(){enter(ActionStatus.ACTIVE);model.update("p");assertEquals(OperationsScreen.LIVE,model.screen);assertEquals("synthetic",model.person!!.person.pseudonym);assertEquals(1,revoked)}
    @Test fun custodyClearsFunctionAndUsesPlace(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.IN_CUSTODY);assertNull(model.liveRoleId);model.place(" Gesa 1 ");model.saveLive();assertEquals(1,repo.writes);assertEquals("Gesa 1",repo.person.state!!.currentPlaceLabel);assertEquals(setOf("s","g"),repo.person.plan.roleIds)}
    @Test fun onSiteSupportsFunctionAndPreferredOrder(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.ON_SITE);model.function("g");model.saveLive();assertEquals("g",repo.person.state!!.currentRoleId);assertEquals("s",repo.person.plan.primaryRoleId)}
    @Test fun leavingPlaceStatusClearsDraftPlace(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.IN_CUSTODY);model.place("Gesa 1");model.status(OperationalStatus.ON_SITE);assertEquals("",model.livePlace)}
    @Test fun completedReadOnlyAndNoDelete(){enter(ActionStatus.COMPLETED);model.update("p");model.plan("p");model.deleteRole(Role("s","a","synthetic",0, editRevision = 0));assertFalse(model.live);assertFalse(model.planning);assertEquals(0,repo.writes);assertEquals(OperationsScreen.OVERVIEW,model.screen)}
    @Test fun cancelLiveNoSnapshotPersistence(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.LEFT);model.cancel();assertEquals(0,repo.writes);assertEquals(OperationalStatus.NO_STATUS,repo.person.state!!.status)}
    @Test fun oldGenerationCannotPopulateBoard(){enter(ActionStatus.ACTIVE);repo.defer=true;model.refresh();snapshot=SessionSnapshot(SessionState.READY,2);val old=model.board;repo.callback!!(OperationsResult.Value(old!!.copy(actionId="old")));assertSame(old,model.board)}
    @Test fun clearDropsSnapshotAndDraft(){enter(ActionStatus.ACTIVE);model.update("p");model.place("synthetic");model.clear();assertNull(model.board);assertNull(model.personId);assertEquals("",model.livePlace)}
    @Test fun labelsAreCompleteAndGerman(){assertEquals("In Gewahrsam",operationalLabel(OperationalStatus.IN_CUSTODY));assertEquals("Keine Lageangabe",operationalLabel(OperationalStatus.NO_STATUS));assertEquals(8,OperationalStatus.entries.map(::operationalLabel).distinct().size)}
    @Test fun resultPublicationAndRefreshDoNotRenewInactivity(){enter(ActionStatus.PREPARATION);assertEquals(0,interactions);model.plan("p");val before=interactions;model.savePlan();assertEquals(before+1,interactions);assertEquals(OperationsScreen.OVERVIEW,model.screen)}

}
