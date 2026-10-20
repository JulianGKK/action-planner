package org.pag.prototype.product

import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** UI publication contracts; real storage/context and session clocks have separate regressions. */
class Ux1bSnapshotTest {
    private class Session : SessionPort {
        var value=SessionSnapshot()
        var listener:((SessionSnapshot)->Unit)?=null
        var interactions=0
        var remaining=102000L
        override fun snapshot()=value
        override fun observe(observer:(SessionSnapshot)->Unit):Subscription { listener=observer;observer(value);return Subscription{} }
        fun emit(state:SessionState,generation:Long){value=SessionSnapshot(state,generation);listener!!(value)}
        override fun remainingAbsoluteMillis()=remaining.takeIf{value.state==SessionState.READY}
        override fun authenticate(experiment:Experiment){}
        override fun testAccess(){}
        override fun userInteraction(){interactions++}
        override fun lock()=emit(SessionState.LOCKED,value.generation+1)
    }
    private val action=Action("a","synthetic action","synthetic description",1,2,0,ActionStatus.ACTIVE)
    private val person=Person("p","a","synthetic person",0)
    private val operational=OperationalBoard("a",action.status,listOf(Role("r","a","Support",0)),
        listOf(OperationalPerson(person,PersonRolePlan(setOf("r"),"r"),PersonOperationalState("a","p","r",OperationalStatus.ON_SITE,null,0))))
    private val task=Task("t","a","synthetic task",TaskPriority.HIGH,TaskStatus.OPEN,null,"r","p",null,null,0,0,null)
    private val taskBoard=TaskBoard("a",listOf(task),emptyList())
    private class Actions(var row:Action):ActionRepository {
        val loads=mutableListOf<(List<Action>)->Unit>()
        var detail:((Action?)->Unit)?=null
        override fun listActions(publish:(List<Action>)->Unit){loads.add(publish)}
        override fun getAction(id:String,publish:(Action?)->Unit){detail=publish}
        override fun createAction(action:NewAction,publish:(Action)->Unit){}
        override fun updateAction(expected:Action,changes:NewAction,publish:(ActionChangeResult)->Unit){}
        override fun advanceActionStatus(id:String,expected:ActionStatus,publish:(ActionChangeResult)->Unit){row=row.copy(status=ActionLifecycle.next(expected)!!,completedAt=3);publish(ActionChangeResult.Applied(row))}
    }
    private class People:PersonRepository {
        var reads=0
        override fun listPersons(actionId:String,publish:(PersonResult<List<Person>>)->Unit){reads++}
        override fun getPerson(actionId:String,personId:String,publish:(PersonResult<Person?>)->Unit){}
        override fun createPerson(actionId:String,pseudonym:String,publish:(PersonResult<Person>)->Unit){}
        override fun updatePerson(actionId:String,expected: Person,pseudonym:String,publish:(PersonResult<Person>)->Unit){}
    }
    private class Ops:OperationsRepository {
        var onRead:()->Unit={}
        val loads=mutableListOf<(OperationsResult<OperationalBoard>)->Unit>()
        var write:((OperationsResult<Role>)->Unit)?=null
        override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit){onRead();loads.add(publish)}
        override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit){write=publish}
        override fun renameRole(actionId:String,roleId:String,name:String,publish:(OperationsResult<Role>)->Unit){}
        override fun deleteRole(actionId:String,roleId:String,publish:(OperationsResult<Unit>)->Unit){}
        override fun planRoles(actionId:String,personId:String,plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit){}
        override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit){}
    }
    private class Tasks:TaskRepository {
        val loads=mutableListOf<(OperationsResult<TaskBoard>)->Unit>()
        var write:((OperationsResult<Task>)->Unit)?=null
        var input:TaskInput?=null
        override fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit){loads.add(publish)}
        override fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit){this.input=input;write=publish}
        override fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit){this.input=input;write=publish}
        override fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit){write=publish}
        override fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit){}
    }
    private val session=Session()
    private val actions=Actions(action)
    private val people=People()
    private val ops=Ops()
    private val tasks=Tasks()
    private val model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,people,
        operationsRepository=ops,taskRepository=tasks){}
    private fun open(){session.emit(SessionState.READY,1);model.detail("a");assertTrue(model.detailLoading);actions.detail!!(action)}
    private fun complete(){ops.loads.last()(OperationsResult.Value(operational));tasks.loads.last()(OperationsResult.Value(taskBoard))}
    @Test fun readyCountdownExistsBeforeFirstBodyResult(){session.emit(SessionState.READY,1);assertEquals(102000L,model.remainingMillis);assertEquals(ActionListState.Loading,model.actionListState);assertEquals(0,session.interactions)}
    @Test fun initialListLoadingThenOneCompleteContent(){session.emit(SessionState.READY,1);assertEquals(ActionListState.Loading,model.actionListState);actions.loads.last()(listOf(action));assertEquals(ActionListState.Content(listOf(action)),model.actionListState)}
    @Test fun personsOperationsAndTaskCountsWaitForBothReads(){open();ops.loads.last()(OperationsResult.Value(operational));assertNull(model.workspace);assertNull(model.operations.board);assertNull(model.tasks.board);assertTrue(model.persons.isEmpty());assertTrue(model.personsLoading);tasks.loads.last()(OperationsResult.Value(taskBoard));assertEquals(listOf(person),model.persons);assertEquals(operational,model.operations.board);assertEquals(1,model.tasks.board!!.affectedCount("p"));assertFalse(model.personsLoading);assertEquals(0,people.reads)}
    @Test fun tasksFirstCannotRevealCountsWithoutPeopleAndFunctions(){open();tasks.loads.last()(OperationsResult.Value(taskBoard));assertNull(model.workspace);assertNull(model.tasks.board);ops.loads.last()(OperationsResult.Value(operational));assertNotNull(model.workspace)}
    @Test fun emptyIsACompletedSnapshotNotAPartialRead(){open();ops.loads.last()(OperationsResult.Value(operational.copy(persons=emptyList())));assertNull(model.workspace);tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks=emptyList())));assertNotNull(model.workspace);assertTrue(model.persons.isEmpty());assertTrue(model.tasks.board!!.active.isEmpty())}
    @Test fun preparationUsesSameAtomicContract(){session.emit(SessionState.READY,1);model.detail("a");actions.detail!!(action.copy(status=ActionStatus.PREPARATION));ops.loads.last()(OperationsResult.Value(operational.copy(status=ActionStatus.PREPARATION)));assertNull(model.workspace);tasks.loads.last()(OperationsResult.Value(taskBoard));assertNotNull(model.workspace)}
    @Test fun revokeBetweenReadsCannotPublish(){open();ops.loads.last()(OperationsResult.Value(operational));val old=tasks.loads.last();session.lock();old(OperationsResult.Value(taskBoard));assertNull(model.workspace);assertNull(model.operations.board);assertNull(model.tasks.board);assertTrue(model.persons.isEmpty());assertNull(model.remainingMillis)}
    @Test fun staleGenerationCannotCompleteNewLoad(){open();val oldOps=ops.loads.last();val oldTasks=tasks.loads.last();session.emit(SessionState.READY,2);model.detail("a");actions.detail!!(action);oldOps(OperationsResult.Value(operational));oldTasks(OperationsResult.Value(taskBoard));assertNull(model.workspace);complete();assertEquals(2L,model.workspace!!.generation)}
    @Test fun staleActionBindingCannotCompleteNewLoad(){open();val old=ops.loads.last();model.detail("b");actions.detail!!(action.copy(id="b"));old(OperationsResult.Value(operational));assertNull(model.workspace);assertEquals("b",model.selected!!.id)}
    @Test fun errorIsNotEmptyOrPartialContent(){open();ops.loads.last()(OperationsResult.Value(operational));tasks.loads.last()(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED));assertTrue(model.workspaceError);assertNull(model.workspace);assertFalse(model.personsLoading)}
    @Test fun refreshKeepsOldSnapshotUntilCompleteReplacement(){open();complete();val old=model.workspace;model.loadWorkspace();assertSame(old,model.workspace);assertFalse(model.personsLoading);ops.loads.last()(OperationsResult.Value(operational.copy(persons=emptyList())));assertSame(old,model.workspace);tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks=emptyList())));assertNotSame(old,model.workspace);assertTrue(model.persons.isEmpty());assertTrue(model.tasks.board!!.active.isEmpty())}
    @Test fun olderRefreshCannotOverwriteNewerSnapshot(){open();complete();model.loadWorkspace();val oldOps=ops.loads.last();val oldTasks=tasks.loads.last();model.loadWorkspace();complete();val current=model.workspace;oldOps(OperationsResult.Value(operational.copy(persons=emptyList())));oldTasks(OperationsResult.Value(taskBoard.copy(tasks=emptyList())));assertSame(current,model.workspace)}
    @Test fun lifecycleChangeDropsOldWorkspaceAndTaskNavigationUntilNewSnapshot(){open();complete();model.tasks.showPerson("p");model.requestAdvance();model.confirmAdvance();assertNull(model.workspace);assertNull(model.tasks.board);assertNull(model.tasks.filter);ops.loads.last()(OperationsResult.Value(operational.copy(status=ActionStatus.COMPLETED)));assertNull(model.workspace);tasks.loads.last()(OperationsResult.Value(taskBoard));assertEquals(ActionStatus.COMPLETED,model.workspace!!.status)}
    @Test fun roleScreenRefreshReadsOnlyItsVisibleBoard(){open();complete();model.operations.roles();val count=tasks.loads.size;model.loadWorkspace();ops.loads.last()(OperationsResult.Value(operational));assertEquals(count,tasks.loads.size);model.operations.cancel();assertEquals(count+1,tasks.loads.size)}
    @Test fun customAndTemplateRoleSuccessProduceFeedbackOnlyAfterPublication(){open();complete();model.operations.roles();for(name in listOf("synthetic role",OperationalRules.templates.first())){model.operations.createRole(name);assertNull(model.feedback);ops.write!!(OperationsResult.Value(Role("new","a",name,0)));assertEquals("Rolle „$name“ angelegt",model.feedback!!.text);model.dismissFeedback(model.feedback!!.id)}}
    @Test fun failedOrRevokedRoleWriteDoesNotPublishBanner(){open();complete();model.operations.roles();model.operations.createRole("synthetic");ops.write!!(OperationsResult.Rejected(OperationsFailure.DUPLICATE));assertNull(model.feedback);model.operations.createRole("synthetic");val old=ops.write!!;session.lock();old(OperationsResult.Value(Role("new","a","synthetic",0)));assertNull(model.feedback)}
    @Test fun taskCreateEditAndReopenFeedbackIsConfirmedAndMinimal(){open();complete();model.tasks.add();model.tasks.change(TaskInput("lowercase remains lowercase"));model.tasks.save();assertEquals("lowercase remains lowercase",tasks.input!!.title);assertNull(model.feedback);tasks.write!!(OperationsResult.Value(task));assertEquals("Aufgabe angelegt",model.feedback!!.text);model.tasks.open(task);model.tasks.edit();model.tasks.save();tasks.write!!(OperationsResult.Value(task));assertEquals("Aufgabe gespeichert",model.feedback!!.text);model.tasks.open(task.copy(status=TaskStatus.DONE));model.tasks.transition(TaskStatus.OPEN);tasks.write!!(OperationsResult.Value(task));assertEquals("Aufgabe wieder geöffnet",model.feedback!!.text)}
    @Test fun revokedTaskCannotPublishFeedback(){open();complete();model.tasks.add();model.tasks.change(TaskInput("synthetic"));model.tasks.save();val old=tasks.write!!;session.lock();old(OperationsResult.Value(task));assertNull(model.feedback);assertNull(model.tasks.board);assertFalse(model.tasks.form)}
    @Test fun staleDetailCallbackCannotRestoreProtectedSelection(){session.emit(SessionState.READY,1);model.detail("a");val old=actions.detail!!;session.lock();old(action);assertNull(model.selected);assertFalse(model.detailLoading);assertNull(model.workspace)}
    @Test fun synchronousRevokeDuringRoleRefreshCannotRepublishBanner(){open();complete();model.operations.createRole("synthetic");ops.onRead={session.lock()};ops.write!!(OperationsResult.Value(Role("new","a","synthetic",0)));assertNull(model.feedback);assertNull(model.workspace)}
    @Test fun synchronousRevokeDuringTaskRefreshCannotRepublishBanner(){open();complete();model.tasks.add();model.tasks.change(TaskInput("synthetic"));model.tasks.save();ops.onRead={session.lock()};tasks.write!!(OperationsResult.Value(task));assertNull(model.feedback);assertNull(model.workspace);assertFalse(model.tasks.form)}
    @Test fun progressStartsFullEndsEmptyAndIsClamped(){assertEquals(1f,bannerProgress(0,5000));assertEquals(.5f,bannerProgress(2500,5000));assertEquals(0f,bannerProgress(5000,5000));assertEquals(0f,bannerProgress(6000,5000));assertEquals(1f,bannerProgress(-1,5000))}
    @Test fun progressTimerCannotInteractOrDismissNewerBanner()=runBlocking{open();complete();model.operations.createRole("synthetic first");ops.write!!(OperationsResult.Value(Role("new","a","synthetic",0)));val old=model.feedback!!.id;model.operations.createRole("synthetic second");ops.write!!(OperationsResult.Value(Role("new","a","synthetic",0)));val newest=model.feedback;val interactions=session.interactions;val progress=mutableListOf<Float>();expireFeedback(old,100,model::dismissFeedback){progress.add(it)};assertSame(newest,model.feedback);assertEquals(1f,progress.first());assertEquals(0f,progress.last());assertEquals(interactions,session.interactions);session.lock();assertNull(model.feedback)}
    @Test fun keyboardHintsMatchFieldPurpose(){for(kind in listOf(ProductTextKind.PERSON,ProductTextKind.ROLE,ProductTextKind.PLACE))assertEquals(KeyboardCapitalization.Words,productKeyboard(kind).capitalization);for(kind in listOf(ProductTextKind.ACTION_TITLE,ProductTextKind.TASK_TITLE,ProductTextKind.DESCRIPTION))assertEquals(KeyboardCapitalization.Sentences,productKeyboard(kind).capitalization);assertEquals(KeyboardCapitalization.None,productKeyboard(ProductTextKind.PASSPHRASE).capitalization)}
    @Test fun restartDropsTaskDraftAndStaleWriteBannerAcrossNewReady() {
        open(); complete(); session.remaining=20000
        model.tasks.add(); model.tasks.change(TaskInput("synthetic unsaved task")); model.tasks.save()
        val old=tasks.write!!
        model.restartSession()
        assertFalse(model.tasks.form); assertNull(model.tasks.board); assertNull(model.feedback)
        assertNull(model.workspace); assertNull(model.remainingMillis); assertNull(model.selected)
        session.emit(SessionState.READY,3)
        old(OperationsResult.Value(task))
        assertNull(model.feedback); assertFalse(model.tasks.form); assertNull(model.tasks.selected)
        assertNull(model.workspace); assertNull(model.selected)
    }
    @Test fun restartInvalidatesAtomicRevealReadsAndBannerExpiry() {
        open(); complete(); session.remaining=20000
        model.operations.createRole("synthetic role"); ops.write!!(OperationsResult.Value(Role("new","a","synthetic",0)))
        val banner=model.feedback!!.id
        val oldOps=ops.loads.last(); val oldTasks=tasks.loads.last()
        model.restartSession()
        assertNull(model.feedback); assertNull(model.workspace); assertNull(model.operations.board)
        session.emit(SessionState.READY,3)
        oldOps(OperationsResult.Value(operational)); oldTasks(OperationsResult.Value(taskBoard)); model.dismissFeedback(banner)
        assertNull(model.feedback); assertNull(model.workspace); assertNull(model.operations.board); assertNull(model.tasks.board)
        assertEquals(ActionListState.Loading,model.actionListState)
    }
    @Test fun restartDropsPersonAndOperationalEditorNavigation() {
        open(); complete(); session.remaining=20000
        model.operations.update("p"); assertEquals(OperationsScreen.LIVE,model.operations.screen)
        model.restartSession()
        assertEquals(OperationsScreen.OVERVIEW,model.operations.screen); assertNull(model.operations.personId)
        session.emit(SessionState.READY,3)
        assertNull(model.selectedPerson); assertFalse(model.personForm); assertNull(model.editingPerson)
        assertNull(model.selected); assertNull(model.workspace)
    }
}
