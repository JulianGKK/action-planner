package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class OperationsModelTest {
    private var snapshot=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.PREPARATION)
    private class Repo:OperationsRepository {
        var status=ActionStatus.PREPARATION;var writes=0;var created:String?=null;var callback:((OperationsResult<OperationalBoard>)->Unit)?=null;var defer=false
        val roles=listOf(Role("s","a","Support",0),Role("g","a","Gesa-Support",0))
        var person=OperationalPerson(Person("p","a","synthetic",0),PersonRolePlan(setOf("s","g"),"s"),PersonOperationalState("a","p","s",OperationalStatus.NO_STATUS,null,0))
        override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit){if(defer)callback=publish else publish(OperationsResult.Value(OperationalBoard(actionId,status,roles,listOf(person))))}
        override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit){writes++;created=name;publish(OperationsResult.Value(Role("new",actionId,name,1)))}
        override fun renameRole(actionId:String,roleId:String,name:String,publish:(OperationsResult<Role>)->Unit){writes++;publish(OperationsResult.Value(roles[0].copy(name=name)))}
        override fun deleteRole(actionId:String,roleId:String,publish:(OperationsResult<Unit>)->Unit){writes++;publish(OperationsResult.Value(Unit))}
        override fun planRoles(actionId:String,personId:String,plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit){writes++;person=person.copy(plan=plan);publish(OperationsResult.Value(Unit))}
        override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit){writes++;person=person.copy(state=expected.copy(status=status,currentRoleId=roleId.takeIf{OperationalRules.roleAllowed(status)},currentPlaceLabel=OperationalRules.place(place,status),updatedAt=1));publish(OperationsResult.Value(Unit))}
    }
    private val repo=Repo();private var revoked=0;private var interactions=0
    private val model=OperationsModel(repo,{snapshot},{action},{interactions++},{revoked++})
    private fun enter(status:ActionStatus){action=action.copy(status=status);repo.status=status;model.refresh()}
    @Test fun draftOffersNoPlanOrLive(){enter(ActionStatus.DRAFT);model.plan("p");model.update("p");assertFalse(model.planning);assertFalse(model.live);assertEquals(OperationsScreen.OVERVIEW,model.screen)}
    @Test fun preparationAllowsMultipleAndOnePrimary(){enter(ActionStatus.PREPARATION);model.plan("p");model.primary("g");model.savePlan();assertEquals(setOf("s","g"),repo.person.plan.roleIds);assertEquals("g",repo.person.plan.primaryRoleId);assertEquals(1,repo.writes)}
    @Test fun cancelPlanPersistsNothing(){enter(ActionStatus.PREPARATION);model.plan("p");model.toggleRole("s");model.cancel();assertEquals(0,repo.writes);assertEquals("s",repo.person.plan.primaryRoleId)}
    @Test fun templateSelectionUsesNormalCreate(){enter(ActionStatus.PREPARATION);model.roles();model.createRole(OperationalRules.templates[0]);assertEquals("Blockierende",repo.created);assertEquals(1,repo.writes)}
    @Test fun activePseudonymOpensLiveAndRevokesSensitive(){enter(ActionStatus.ACTIVE);model.update("p");assertEquals(OperationsScreen.LIVE,model.screen);assertEquals("synthetic",model.person!!.person.pseudonym);assertEquals(1,revoked)}
    @Test fun custodyClearsFunctionAndUsesPlace(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.IN_CUSTODY);assertNull(model.liveRoleId);model.place(" Gesa 1 ");model.saveLive();assertEquals(1,repo.writes);assertEquals("Gesa 1",repo.person.state!!.currentPlaceLabel);assertEquals(setOf("s","g"),repo.person.plan.roleIds)}
    @Test fun onSiteSupportsFunctionAndPreferredOrder(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.ON_SITE);model.function("g");model.saveLive();assertEquals("g",repo.person.state!!.currentRoleId);assertEquals("s",repo.person.plan.primaryRoleId)}
    @Test fun leavingPlaceStatusClearsDraftPlace(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.IN_CUSTODY);model.place("Gesa 1");model.status(OperationalStatus.ON_SITE);assertEquals("",model.livePlace)}
    @Test fun completedReadOnlyAndNoDelete(){enter(ActionStatus.COMPLETED);model.update("p");model.plan("p");model.deleteRole("s");assertFalse(model.live);assertFalse(model.planning);assertEquals(0,repo.writes);assertEquals(OperationsScreen.OVERVIEW,model.screen)}
    @Test fun cancelLiveNoSnapshotPersistence(){enter(ActionStatus.ACTIVE);model.update("p");model.status(OperationalStatus.LEFT);model.cancel();assertEquals(0,repo.writes);assertEquals(OperationalStatus.NO_STATUS,repo.person.state!!.status)}
    @Test fun oldGenerationCannotPopulateBoard(){enter(ActionStatus.ACTIVE);repo.defer=true;model.refresh();snapshot=SessionSnapshot(SessionState.READY,2);val old=model.board;repo.callback!!(OperationsResult.Value(old!!.copy(actionId="old")));assertSame(old,model.board)}
    @Test fun clearDropsSnapshotAndDraft(){enter(ActionStatus.ACTIVE);model.update("p");model.place("synthetic");model.clear();assertNull(model.board);assertNull(model.personId);assertEquals("",model.livePlace)}
    @Test fun labelsAreCompleteAndGerman(){assertEquals("In Gewahrsam",operationalLabel(OperationalStatus.IN_CUSTODY));assertEquals("Keine Lageangabe",operationalLabel(OperationalStatus.NO_STATUS));assertEquals(8,OperationalStatus.entries.map(::operationalLabel).distinct().size)}
    @Test fun resultPublicationAndRefreshDoNotRenewInactivity(){enter(ActionStatus.PREPARATION);assertEquals(0,interactions);model.plan("p");val before=interactions;model.savePlan();assertEquals(before+1,interactions);assertEquals(OperationsScreen.OVERVIEW,model.screen)}

}
