package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual model/port-call contracts; no simulated UI text is used as a storage proof. */
class Ux1bNavigationTest {
    private class Session:SessionPort {
        var current=SessionSnapshot(SessionState.READY,1)
        var observer:((SessionSnapshot)->Unit)?=null
        var onInteraction:()->Unit={}
        override fun snapshot()=current
        override fun observe(observer:(SessionSnapshot)->Unit):Subscription {this.observer=observer;observer(current);return Subscription{}}
        fun emit(state:SessionState,generation:Long){current=SessionSnapshot(state,generation);observer!!(current)}
        override fun authenticate(experiment:Experiment){}
        override fun testAccess(){}
        override fun userInteraction()=onInteraction()
        override fun lock()=emit(SessionState.LOCKED,current.generation+1)
    }
    private class Actions:ActionRepository {
        var row=Action("a","synthetic","synthetic",1,2,0,ActionStatus.PREPARATION)
        override fun listActions(publish:(List<Action>)->Unit)=publish(listOf(row))
        override fun getAction(id:String,publish:(Action?)->Unit)=publish(row.copy(id=id))
        override fun createAction(action:NewAction,publish:(Action)->Unit){}
        override fun updateAction(expected:Action,changes:NewAction,publish:(ActionChangeResult)->Unit){}
        override fun advanceActionStatus(id:String,expected:ActionStatus,publish:(ActionChangeResult)->Unit){}
    }
    private class People:PersonRepository {
        var lists=0;var gets=0
        override fun listPersons(actionId:String,publish:(PersonResult<List<Person>>)->Unit){lists++;publish(PersonResult.Value(emptyList()))}
        override fun getPerson(actionId:String,personId:String,publish:(PersonResult<Person?>)->Unit){gets++;publish(PersonResult.Value(Person(personId,actionId,"synthetic",0, editRevision = 0)))}
        override fun createPerson(actionId:String,pseudonym:String,publish:(PersonResult<Person>)->Unit){}
        override fun updatePerson(actionId:String,expected: Person,pseudonym:String,publish:(PersonResult<Person>)->Unit){}
    }
    private class Ops(private val status:()->ActionStatus):OperationsRepository {
        var reads=0;var writes=0
        var pendingSave:((OperationsResult<Unit>)->Unit)?=null
        override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit){
            reads++
            val person=OperationalPerson(Person("p",actionId,"synthetic",0, editRevision = 0),PersonRolePlan(setOf("r"),"r", editRevision = 0),
                PersonOperationalState(actionId,"p","r",OperationalStatus.ON_SITE,null,0, editRevision = 0))
            val other=person.copy(person=person.person.copy(id="other"),plan=PersonRolePlan(emptySet(),null, editRevision = 0),
                state=person.state!!.copy(personId="other",currentRoleId=null))
            publish(OperationsResult.Value(OperationalBoard(actionId,status(),listOf(Role("r",actionId,"Support",0, editRevision = 0)),listOf(person,other))))
        }
        override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit){}
        override fun renameRole(actionId:String,expected: Role,name:String,publish:(OperationsResult<Role>)->Unit){}
        override fun deleteRole(actionId:String,expected: Role,publish:(OperationsResult<Unit>)->Unit){}
        override fun planRoles(actionId:String,personId:String,expected: ExpectedPersonRolePlan, plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit){writes++;pendingSave=publish}
        override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit){}
    }
    private class Tasks:TaskRepository {
        var reads=0
        override fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit){reads++;publish(OperationsResult.Value(TaskBoard(actionId,emptyList(),emptyList())))}
        override fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit){}
        override fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit){}
        override fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit){}
        override fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit){}
    }
    private class Sensitive(private val session:Session):LeadershipRepository {
        data class Load(val actionId:String,val personId:String,val callback:(SensitiveResult<SensitiveInventory>)->Unit)
        val loads=mutableListOf<Load>()
        var defer=false;var opens=0;var writes=0
        var state=SensitiveState.READY
        private var active:SensitiveGrant?=null
        fun result()=SensitiveResult.Value(SensitiveInventory(state,setOf(SensitiveArea.IDENTITY)))
        override fun inventory(actionId:String,personId:String,publish:(SensitiveResult<SensitiveInventory>)->Unit){loads.add(Load(actionId,personId,publish));if(!defer)publish(result())}
        override fun open(actionId:String,personId:String,area:SensitiveArea,passphrase:CharArray,publish:(SensitiveResult<SensitiveOpened>)->Unit){
            opens++;passphrase.fill('\u0000');val generation=session.current.generation
            val grant=object:SensitiveGrant {
                override val actionId=actionId;override val personId=personId;override val area=area
                var live=true
                override fun valid()=live && session.current.state==SessionState.READY && session.current.generation==generation
                override fun cancel(){live=false}
            }
            active=grant;publish(SensitiveResult.Value(SensitiveOpened(grant,SensitiveData.Identity("synthetic protected fixture"))))
        }
        override fun revoke(){active?.cancel();active=null}
        override fun setup(actionId:String,passphrase:CharArray,confirmation:CharArray,publish:(SensitiveResult<Unit>)->Unit){writes++;passphrase.fill('\u0000');confirmation.fill('\u0000')}
        override fun save(grant:SensitiveGrant,data:SensitiveData,publish:(SensitiveResult<Unit>)->Unit){writes++}
        override fun deleteArea(grant:SensitiveGrant,publish:(SensitiveResult<Unit>)->Unit){}
        override fun deletePerson(actionId:String,personId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit){}
        override fun deleteAction(actionId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit){}
    }
    private val session=Session();private val actions=Actions();private val people=People()
    private val ops=Ops{actions.row.status};private val tasks=Tasks();private val sensitive=Sensitive(session)
    private val model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,people,sensitive,ops,tasks){}
    private fun openPerson(){model.detail("a");model.personDetail("p")}
    private fun beginPlan(){openPerson();model.operations.plan("p");assertEquals(OperationsScreen.PLAN,model.operations.screen);assertNull(model.sensitiveInventory);assertFalse(model.sensitiveInventoryLoading)}
    @Test fun liveToPersonDiscardsEditWithoutOverviewOrTaskRead(){
        actions.row=actions.row.copy(status=ActionStatus.ACTIVE);model.detail("a");model.operations.update("p")
        model.operations.status(OperationalStatus.IN_CUSTODY);model.operations.place("synthetic place")
        val boards=ops.reads;val taskReads=tasks.reads;val personReads=people.gets;val inventories=sensitive.loads.size
        sensitive.defer=true;model.operationalPersonDetail()
        assertEquals(boards,ops.reads);assertEquals(taskReads,tasks.reads);assertEquals(0,people.lists)
        assertEquals(personReads+1,people.gets);assertEquals(inventories+1,sensitive.loads.size)
        assertEquals(OperationsScreen.OVERVIEW,model.operations.screen);assertNull(model.operations.personId)
        assertEquals("",model.operations.livePlace);assertNull(model.operations.liveRoleId);assertEquals(OperationalStatus.NO_STATUS,model.operations.liveStatus)
        assertNull(model.selectedPerson);assertTrue(model.personDetailLoading);assertTrue(model.sensitiveInventoryLoading)
        sensitive.loads.last().callback(sensitive.result())
        assertEquals("p",model.selectedPerson!!.id);assertFalse(model.personDetailLoading);assertFalse(model.sensitiveInventoryLoading)
    }
    @Test fun liveNavigationRejectsRevokeDuringDiscard(){
        actions.row=actions.row.copy(status=ActionStatus.ACTIVE);model.detail("a");model.operations.update("p")
        val gets=people.gets;val reads=ops.reads;val taskReads=tasks.reads
        session.onInteraction={session.lock()};model.operationalPersonDetail()
        assertEquals(gets,people.gets);assertEquals(reads,ops.reads);assertEquals(taskReads,tasks.reads)
        assertNull(model.selectedPerson);assertNull(model.workspace);assertNull(model.operations.personId);assertFalse(model.sensitiveInventoryLoading)
    }
    @Test fun liveNavigationRejectsGenerationChangeBeforePersonRead(){
        actions.row=actions.row.copy(status=ActionStatus.ACTIVE);model.detail("a");model.operations.update("p")
        var calls=0;val gets=people.gets
        session.onInteraction={calls++;if(calls==2)session.emit(SessionState.READY,2)}
        model.operationalPersonDetail();assertEquals(gets,people.gets);assertNull(model.selectedPerson);assertNull(model.sensitiveInventory)
    }
    @Test fun liveNavigationCannotReadOldPersonAfterActionChange(){
        actions.row=actions.row.copy(status=ActionStatus.ACTIVE);model.detail("a");model.operations.update("p");val gets=people.gets
        session.onInteraction={session.onInteraction={};model.detail("other-action");model.operations.update("p")}
        model.operationalPersonDetail();assertEquals(gets,people.gets);assertEquals("other-action",model.selected!!.id)
        assertNull(model.selectedPerson);assertEquals(OperationsScreen.LIVE,model.operations.screen)
    }
    @Test fun liveNavigationCannotOverwriteAChangedPersonEdit(){
        actions.row=actions.row.copy(status=ActionStatus.ACTIVE);model.detail("a");model.operations.update("p");val gets=people.gets
        session.onInteraction={session.onInteraction={};model.operations.update("other")}
        model.operationalPersonDetail();assertEquals(gets,people.gets);assertNull(model.selectedPerson)
        assertEquals("other",model.operations.personId);assertEquals(OperationsScreen.LIVE,model.operations.screen)
    }
    @Test fun cancelPlanReloadsMetadataAndEndsLoading(){
        beginPlan();val before=sensitive.loads.size;sensitive.defer=true;model.operations.cancel()
        assertEquals(before+1,sensitive.loads.size);assertEquals("p",model.selectedPerson!!.id);assertTrue(model.sensitiveInventoryLoading)
        assertEquals("a",sensitive.loads.last().actionId);assertEquals("p",sensitive.loads.last().personId)
        sensitive.loads.last().callback(sensitive.result());assertFalse(model.sensitiveInventoryLoading);assertEquals(SensitiveState.READY,model.sensitiveInventory!!.state)
        model.requestSensitive(SensitiveArea.IDENTITY);assertEquals(SensitiveArea.IDENTITY,model.sensitiveArea);assertNull(model.sensitiveOpened)
    }
    @Test fun savePlanReloadsOnlyAfterConfirmedSuccess(){
        beginPlan();sensitive.defer=true;val before=sensitive.loads.size;model.operations.savePlan()
        assertEquals(before,sensitive.loads.size);assertEquals(OperationsScreen.PLAN,model.operations.screen)
        ops.pendingSave!!(OperationsResult.Value(Unit));assertEquals(before+1,sensitive.loads.size)
        assertEquals(OperationsScreen.OVERVIEW,model.operations.screen);assertTrue(model.sensitiveInventoryLoading)
        sensitive.loads.last().callback(sensitive.result());assertFalse(model.sensitiveInventoryLoading);assertNotNull(model.sensitiveInventory)
    }
    @Test fun rejectedPlanDoesNotPretendToReturnOrReloadMetadata(){beginPlan();val before=sensitive.loads.size;model.operations.savePlan();ops.pendingSave!!(OperationsResult.Rejected(OperationsFailure.STALE));assertEquals(before,sensitive.loads.size);assertEquals(OperationsScreen.PLAN,model.operations.screen);assertFalse(model.sensitiveInventoryLoading)}
    @Test fun lockDuringReturnedMetadataDiscardsPublication(){beginPlan();sensitive.defer=true;model.operations.cancel();val old=sensitive.loads.last();session.lock();old.callback(sensitive.result());assertNull(model.selectedPerson);assertNull(model.sensitiveInventory);assertFalse(model.sensitiveInventoryLoading)}
    @Test fun generationChangeDuringReturnedMetadataDiscardsPublication(){beginPlan();sensitive.defer=true;model.operations.cancel();val old=sensitive.loads.last();session.emit(SessionState.READY,2);old.callback(sensitive.result());assertNull(model.selectedPerson);assertNull(model.sensitiveInventory);assertFalse(model.sensitiveInventoryLoading)}
    @Test fun personChangeRejectsOldMetadataWithoutEndingNewLoad(){beginPlan();sensitive.defer=true;model.operations.cancel();val old=sensitive.loads.last();model.personDetail("other");val fresh=sensitive.loads.last();old.callback(sensitive.result());assertNull(model.selectedPerson);assertNull(model.sensitiveInventory);assertTrue(model.sensitiveInventoryLoading);fresh.callback(sensitive.result());assertEquals("other",model.selectedPerson!!.id);assertFalse(model.sensitiveInventoryLoading)}
    @Test fun olderSamePersonMetadataCannotOverwriteNewerReturnLoad(){
        beginPlan();sensitive.defer=true;model.operations.cancel();val old=sensitive.loads.last()
        model.operations.plan("p");model.operations.cancel();val fresh=sensitive.loads.last()
        old.callback(SensitiveResult.Value(SensitiveInventory(SensitiveState.PARTIAL_OR_INVALID)))
        assertNull(model.sensitiveInventory);assertTrue(model.sensitiveInventoryLoading)
        fresh.callback(sensitive.result());assertEquals(SensitiveState.READY,model.sensitiveInventory!!.state);assertFalse(model.sensitiveInventoryLoading)
    }
    @Test fun actionChangeDiscardsOldReturnedMetadata(){beginPlan();sensitive.defer=true;model.operations.cancel();val old=sensitive.loads.last();model.detail("other-action");old.callback(sensitive.result());assertEquals("other-action",model.selected!!.id);assertNull(model.selectedPerson);assertNull(model.sensitiveInventory);assertFalse(model.sensitiveInventoryLoading)}
    @Test fun leavingWhileReturningDiscardsOldMetadata(){beginPlan();sensitive.defer=true;model.operations.cancel();val old=sensitive.loads.last();model.backToPersons();old.callback(sensitive.result());assertNull(model.selectedPerson);assertNull(model.sensitiveInventory);assertFalse(model.sensitiveInventoryLoading)}
    @Test fun metadataFailureEndsLoadingAndKeepsBasicPerson(){beginPlan();sensitive.defer=true;model.operations.cancel();sensitive.loads.last().callback(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE));assertFalse(model.sensitiveInventoryLoading);assertNull(model.sensitiveInventory);assertEquals(SensitiveFailure.UNAVAILABLE,model.sensitiveError);assertEquals("p",model.selectedPerson!!.id);assertNull(model.sensitiveOpened)}
    @Test fun partialInventoryCannotBecomeSetupOrStepUp(){beginPlan();sensitive.defer=true;model.operations.cancel();sensitive.loads.last().callback(SensitiveResult.Value(SensitiveInventory(SensitiveState.PARTIAL_OR_INVALID)));assertFalse(model.sensitiveInventoryLoading);model.requestSensitiveSetup();model.requestSensitive(SensitiveArea.IDENTITY);assertFalse(model.sensitiveSetup);assertNull(model.sensitiveArea);assertTrue(model.personsVisible);assertEquals("p",model.selectedPerson!!.id);assertEquals(0,sensitive.opens)}
    @Test fun uninitializedReturnPermitsOnlyExplicitSetup(){beginPlan();sensitive.defer=true;model.operations.cancel();sensitive.loads.last().callback(SensitiveResult.Value(SensitiveInventory(SensitiveState.UNINITIALIZED)));assertFalse(model.sensitiveInventoryLoading);assertFalse(model.sensitiveSetup);assertEquals(0,sensitive.writes);model.requestSensitiveSetup();assertTrue(model.sensitiveSetup);assertEquals(0,sensitive.writes)}
    @Test fun returnNeverRestoresGrantPayloadOrPassphrase(){
        openPerson();model.requestSensitive(SensitiveArea.IDENTITY);val input="synthetic passphrase fixture".toCharArray();model.openSensitive(input)
        val old=model.sensitiveOpened!!.grant;assertTrue(old.valid());assertTrue(input.all{it=='\u0000'})
        model.operations.plan("p");assertFalse(old.valid());assertNull(model.sensitiveOpened);assertNull(model.sensitiveArea)
        sensitive.defer=true;model.operations.cancel();sensitive.loads.last().callback(sensitive.result())
        assertNotNull(model.sensitiveInventory);assertFalse(old.valid());assertNull(model.sensitiveOpened);assertNull(model.sensitiveArea);assertFalse(model.sensitiveBusy)
        assertEquals(1,sensitive.opens);assertEquals(0,sensitive.writes)
        model.requestSensitive(SensitiveArea.IDENTITY);assertEquals(SensitiveArea.IDENTITY,model.sensitiveArea);assertNull(model.sensitiveOpened);assertEquals(1,sensitive.opens)
    }
    @Test fun revokedPlanSaveCannotReloadMetadata(){beginPlan();model.operations.savePlan();val callback=ops.pendingSave!!;val reads=sensitive.loads.size;session.lock();callback(OperationsResult.Value(Unit));assertEquals(reads,sensitive.loads.size);assertNull(model.selectedPerson);assertNull(model.sensitiveInventory);assertFalse(model.sensitiveInventoryLoading)}
}
