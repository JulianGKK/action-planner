package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class TaskModelTest {
    private var session=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
    private var interactions=0
    private var onInteraction:()->Unit={}
    private var onLeaveSensitive:()->Unit={}
    private class Repo:TaskRepository {
        var pageRequested:Long?=null
        override fun historyPage(expected:Task,page:Long,publish:(CompactTaskResult)->Unit){historyCalls++;pageRequested=page;historyCallback=publish}
        var historyCalls=0
        var historyCallback:((CompactTaskResult)->Unit)?=null
        var after:CompactEventCursor?=null
        override fun history(expected:Task,after:CompactEventCursor?,publish:(CompactTaskResult)->Unit){historyCalls++;this.after=after;historyCallback=publish}
        var deferWrites=false;var pending:((OperationsResult<Task>)->Unit)?=null
        var writes=0;var expected:Task?=null;var input:TaskInput?=null;var deferred=false
        var callback:((OperationsResult<TaskBoard>)->Unit)?=null
        var tasks=listOf(Task("t","a","EA kontaktieren",TaskPriority.HIGH,TaskStatus.OPEN,null,"r","motte",null,null,1,1,null))
        override fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit){if(deferred)callback=publish else publish(OperationsResult.Value(TaskBoard(actionId,tasks,emptyList())))}
        override fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit){writes++;this.input=input;val t=tasks.first().copy(id="new",title=input.title,affectedPersonId=input.affectedPersonId);tasks=tasks+t;publish(OperationsResult.Value(t))}
        override fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit){writes++;this.expected=expected;this.input=input;if(deferWrites){pending=publish;return};if(tasks.singleOrNull{it.id==expected.id}!=expected)publish(OperationsResult.Rejected(OperationsFailure.STALE)) else {val t=expected.copy(title=input.title,priority=input.priority,assigneePersonId=input.assigneePersonId,assigneeRoleId=input.assigneeRoleId,affectedPersonId=input.affectedPersonId,dueAt=input.dueAt);tasks=tasks.map{if(it.id==t.id)t else it};publish(OperationsResult.Value(t))}}
        override fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit){writes++;val t=TaskRules.transition(expected,status,5)!!;tasks=tasks.map{if(it.id==t.id)t else it};publish(OperationsResult.Value(t))}
        override fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit){writes++;tasks=tasks.filterNot{it.id==expected.id};publish(OperationsResult.Value(Unit))}
    }
    private val repo=Repo()
    private val model=TaskModel(repo,{session},{action},{interactions++;onInteraction()},{onLeaveSensitive()})
    private fun historyPage(count:Int=32,total:Long=65,next:Boolean=true):CompactTaskResult.Value {
        val state=CompactTaskState(P3bFreeActiveCoreBinding("a",session.generation),0)
        val page=CompactTaskEvents(state,"t",total,List(count){TaskEvent("event-$it","a","t",TaskEventType.CREATED,it.toLong())},
            if(next)CompactEventCursor(state,"t",31,32) else null)
        return CompactTaskResult.Value(page,BoundedTaskReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units()),0,0)
    }
    @Test fun directHistoryPageReplacesPayloadAndUsesCorrectOrdinalWithoutRetainingPrefixes() {
        model.refresh();model.open(repo.tasks.first());model.loadHistory()
        repo.historyCallback!!(historyPage(total=128001))
        model.loadHistoryPage(4001);assertEquals(4001L,repo.pageRequested);assertNull(model.history)
        repo.historyCallback!!(historyPage(1,128001,false))
        assertEquals(128000L,model.historyOffset);assertEquals(1,model.history!!.events.size)
        model.loadHistoryPage(2001);repo.historyCallback!!(historyPage(32,128001,true))
        assertEquals(64000L,model.historyOffset);assertEquals(32,model.history!!.events.size)
        model.clear();assertNull(model.history)
    }
    @Test fun historyIsExplicitBoundedReplacementAndNeverNeededForEditor() {
        model.refresh();model.open(repo.tasks.first());assertEquals(0,repo.historyCalls)
        model.edit();assertEquals(0,repo.historyCalls);model.cancel()
        model.loadHistory();assertTrue(model.historyBusy);model.loadHistory();assertEquals(1,repo.historyCalls)
        repo.historyCallback!!(historyPage());assertEquals(32,model.history!!.events.size);assertEquals(0L,model.historyOffset)
        model.loadHistory();assertNull(model.history);assertNotNull(repo.after)
        repo.historyCallback!!(historyPage());assertEquals(32,model.history!!.events.size);assertEquals(32L,model.historyOffset)
        model.loadHistory();repo.historyCallback!!(historyPage(1,next=false));assertEquals(1,model.history!!.events.size);assertEquals(64L,model.historyOffset)
        model.loadHistory();assertEquals(3,repo.historyCalls)
        model.edit();assertNull(model.history);assertTrue(model.form)
    }
    @Test fun historyRejectsLateDuplicateRevokeActionAndEditorCallbacks() {
        for(kind in listOf("clear","lock","generation","action","editor")) {
            session=SessionSnapshot(SessionState.READY,session.generation+1);action=action.copy(id="a")
            model.clear();model.refresh();model.open(repo.tasks.first());model.loadHistory();val old=repo.historyCallback!!
            val result=historyPage()
            when(kind){"clear"->model.clear();"lock"->session=SessionSnapshot(SessionState.LOCKED,session.generation+1);"generation"->session=SessionSnapshot(SessionState.READY,session.generation+1);"action"->action=action.copy(id="b");else->model.edit()}
            old(result);old(result);assertNull(model.history)
        }
    }
    @Test fun historyFailureIsNotEmptyAndCanRestart() {
        model.refresh();model.open(repo.tasks.first())
        for(reason in listOf(BoundedActiveStatus.BUDGET_REFUSED,BoundedActiveStatus.ACCESS_REVOKED,BoundedActiveStatus.STORAGE_FAILURE)) {
            model.loadHistory(true);repo.historyCallback!!(CompactTaskResult.Rejected(reason))
            assertNull(model.history);assertFalse(model.historyBusy);assertEquals(reason,model.historyError)
        }
        model.loadHistory(true);repo.historyCallback!!(historyPage(0,0,false));assertEquals(0L,model.history!!.total);assertNull(model.historyError)
    }
    @Test fun workspaceRefreshKeepsOriginalEditorExpectedAndNewerPersistedTask(){
        model.refresh();val a=repo.tasks.first();model.open(a);model.edit()
        model.change(model.input.copy(title="Old draft"))
        val b=a.copy(title="Other writer",updatedAt=2);repo.tasks=listOf(b);model.refresh()
        assertEquals(b,model.selected);assertEquals("Old draft",model.input.title)
        model.save();assertEquals(a,repo.expected);assertEquals(OperationsFailure.STALE,model.error);assertEquals(b,repo.tasks.single());assertEquals("Old draft",model.input.title)
    }
    @Test fun onlyExplicitReloadReplacesExpectedAllInputFieldsAndComposeIdentity(){
        model.refresh();val a=repo.tasks.first();model.open(a);model.edit()
        val revision=model.formRevision
        val draft=a.input().copy(title="Draft",dueAt=100,priority=TaskPriority.LOW,assigneeRoleId=null,assigneePersonId="p",affectedPersonId=null)
        model.change(draft)
        val b=a.copy(title="New",priority=TaskPriority.NORMAL,assigneeRoleId=null,assigneePersonId="other",affectedPersonId="p",dueAt=200,updatedAt=2)
        repo.tasks=listOf(b);model.refresh();assertEquals(revision,model.formRevision);assertEquals(draft,model.input)
        model.save();assertEquals(a,repo.expected);assertEquals(OperationsFailure.STALE,model.error)
        model.reloadEditor();assertEquals(b.input(),model.input);assertTrue(model.formRevision>revision)
        model.change(model.input.copy(title="Confirmed new draft"));model.save()
        assertEquals(b,repo.expected);assertEquals("Confirmed new draft",repo.tasks.single().title);assertFalse(model.form)
    }
    @Test fun failedReloadMissingOrForeignTaskPreservesDraftAndRevision(){
        for(kind in listOf("failure","missing","foreign")){
            model.clear();repo.deferred=false;model.refresh();val a=repo.tasks.first();model.open(a);model.edit()
            model.change(model.input.copy(title="Draft",dueAt=300));repo.tasks=listOf(a.copy(updatedAt=a.updatedAt+1));model.save()
            val draft=model.input;val revision=model.formRevision;repo.deferred=true;model.reloadEditor()
            repo.callback!!(when(kind){"failure"->OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED);"missing"->OperationsResult.Value(TaskBoard("a",emptyList(),emptyList()));else->OperationsResult.Value(TaskBoard("b",listOf(a.copy(actionId="b")),emptyList()))})
            assertEquals(draft,model.input);assertEquals(revision,model.formRevision);assertFalse(model.busy)
        }
    }
    @Test fun lateDuplicateReloadCannotReplaceAnotherEditorOrItsDueDraft(){
        model.refresh();val a=repo.tasks.first();model.open(a);model.edit();repo.tasks=listOf(a.copy(updatedAt=2));model.save()
        repo.deferred=true;model.reloadEditor();val old=repo.callback!!
        model.clear();repo.deferred=false;model.refresh();model.open(repo.tasks.first());model.edit()
        model.change(model.input.copy(title="New editor",dueAt=400));val revision=model.formRevision
        old(OperationsResult.Value(TaskBoard("a",listOf(a),emptyList())))
        assertEquals("New editor",model.input.title);assertEquals(400L,model.input.dueAt);assertEquals(revision,model.formRevision)
    }
    @Test fun readRefreshWhileWritePendingDoesNotRebindOrLoseCommitCallback(){
        model.refresh();val a=repo.tasks.first();model.open(a);model.edit();model.change(model.input.copy(title="Submitted"))
        repo.deferWrites=true;model.save();val callback=repo.pending!!
        model.refresh();assertTrue(model.busy);assertEquals(a,repo.expected)
        callback(OperationsResult.Value(a.copy(title="Submitted")));assertFalse(model.busy);assertFalse(model.form)
        model.open(a);model.edit();model.change(model.input.copy(title="New draft"));val revision=model.formRevision
        callback(OperationsResult.Value(a));assertEquals("New draft",model.input.title);assertEquals(revision,model.formRevision)
    }
    @Test fun lockGenerationActionChangesRejectReloadAndWritePublication(){
        for(kind in listOf("lock","generation","action"))for(reload in listOf(false,true)){
            session=SessionSnapshot(SessionState.READY,session.generation+1);action=action.copy(id="a")
            model.clear();repo.deferred=false;repo.deferWrites=false;model.refresh();val a=repo.tasks.first();model.open(a);model.edit()
            if(reload){repo.tasks=listOf(a.copy(updatedAt=a.updatedAt+1));model.save();repo.deferred=true;model.reloadEditor()}
            else {repo.deferWrites=true;model.save()}
            val oldRead=repo.callback;val oldWrite=repo.pending
            when(kind){"lock"->session=SessionSnapshot(SessionState.LOCKED,session.generation+1);"generation"->session=SessionSnapshot(SessionState.READY,session.generation+1);else->action=action.copy(id="b")}
            model.clear()
            if(reload)oldRead!!(OperationsResult.Value(TaskBoard("a",listOf(a),emptyList()))) else oldWrite!!(OperationsResult.Value(a))
            assertFalse(model.form);assertEquals(TaskInput(""),model.input);assertNull(model.selected)
        }
    }
    @Test fun suppliedWorkspaceReconciliationCannotReplaceEditorExpected(){
        val a=repo.tasks.first();var workspace=TaskBoard("a",listOf(a),emptyList())
        val editor=TaskModel(repo,{session},{action},{},{},suppliedBoard={workspace},refreshWorkspace={})
        editor.open(a);editor.edit();editor.change(a.input().copy(title="Workspace draft"))
        val b=a.copy(title="Workspace B",updatedAt=2);repo.tasks=listOf(b);workspace=TaskBoard("a",listOf(b),emptyList())
        editor.reconcileSelection();assertEquals(b,editor.selected)
        editor.save();assertEquals(a,repo.expected);assertEquals(OperationsFailure.STALE,editor.error)
        assertEquals("Workspace draft",editor.input.title);assertEquals(b,repo.tasks.single())
    }
    @Test fun disappearanceFromDisplayCannotTurnEditIntoCreate(){
        model.refresh();val a=repo.tasks.first();model.open(a);model.edit();model.change(a.input().copy(title="Still an edit"))
        repo.tasks=emptyList();model.refresh();assertNull(model.selected);assertTrue(model.editingExisting)
        model.save();assertEquals(a,repo.expected);assertEquals(OperationsFailure.STALE,model.error)
        assertTrue(repo.tasks.isEmpty());assertTrue(model.form)
    }
    @Test fun creationHasMinimalDefaultsAndAffectedCanBeRemoved(){model.refresh();model.add("motte");assertEquals("motte",model.input.affectedPersonId);assertEquals(TaskPriority.NORMAL,model.input.priority);model.change(model.input.copy(title="x",affectedPersonId=null));model.save();assertNull(repo.input!!.affectedPersonId);assertEquals(1,repo.writes)}
    @Test fun cancelCreateAndEditNeverMutate(){model.refresh();model.add();model.change(TaskInput("draft"));model.cancel();model.open(repo.tasks.first());model.edit();model.change(TaskInput("edited"));model.cancel();assertEquals(0,repo.writes);assertEquals("EA kontaktieren",repo.tasks.first().title);assertFalse(model.form);assertEquals("",model.input.title)}
    @Test fun quickActionsCompleteAndReopen(){model.refresh();model.open(repo.tasks.first());model.transition(TaskStatus.IN_PROGRESS);assertEquals(TaskStatus.IN_PROGRESS,model.selected!!.status);model.transition(TaskStatus.DONE);assertEquals(TaskStatus.DONE,model.selected!!.status);assertNotNull(model.selected!!.completedAt);model.transition(TaskStatus.OPEN);assertNull(model.selected!!.completedAt);assertEquals(TaskStatus.OPEN,model.selected!!.status);model.transition(TaskStatus.CANCELLED);model.transition(TaskStatus.OPEN);assertEquals(5,repo.writes)}
    @Test fun activeDeleteAndCompletedWritesAreDenied(){model.refresh();model.open(repo.tasks.first());model.delete();assertEquals(0,repo.writes);action=action.copy(status=ActionStatus.COMPLETED,completedAt=9);model.edit();model.add();model.transition(TaskStatus.DONE);model.delete();assertEquals(0,repo.writes);assertFalse(model.form)}
    @Test fun preparationDeleteAndExplicitTemplateSelection(){action=action.copy(status=ActionStatus.PREPARATION);model.refresh();assertEquals(0,repo.writes);model.add(template=TaskRules.templates.first());assertEquals("RALLY_AT_ACTION",model.input.sourceTemplateKey);assertEquals(0,repo.writes);model.save();model.open(repo.tasks.first());model.delete();assertEquals(2,repo.writes)}
    @Test fun generationNavigationAndLockDiscardResultsAndDrafts(){model.refresh();val old=model.board;repo.deferred=true;model.refresh();session=SessionSnapshot(SessionState.READY,2);repo.callback!!(OperationsResult.Value(TaskBoard("a",emptyList(),emptyList())));assertSame(old,model.board);model.add();model.change(TaskInput("synthetic draft"));model.clear();assertNull(model.board);assertEquals("",model.input.title);session=SessionSnapshot(SessionState.LOCKED,3);model.add();assertFalse(model.form)}
    @Test fun boardFilterKeepsAffectedAndAssigneeSeparate(){model.refresh();model.showPerson("motte");assertEquals("motte",model.filter);assertEquals(1,model.board!!.affectedCount("motte"));assertEquals(0,model.board!!.affectedCount("r"))}
    @Test fun allLabelsAndStrictOptionalDueDate(){assertEquals(listOf("Niedrig","Normal","Hoch"),TaskPriority.entries.map(::priorityLabel));assertEquals(listOf("Offen","In Arbeit","Erledigt","Entfällt"),TaskStatus.entries.map(::taskStatusLabel));assertEquals("Wieder geöffnet",eventLabel(TaskEventType.REOPENED));assertNull(parseTaskDue(""));assertNull(parseTaskDue("31.02.2026 19:05"));assertNotNull(parseTaskDue("11.10.2026 19:05"))}
    @Test fun refreshDoesNotCountAsInteraction(){model.refresh();assertEquals(0,interactions)}
    @Test fun synchronousLockDuringOpenCannotRepublishTaskAfterObserverClears() {
        model.refresh()
        val task=repo.tasks.first()
        model.open(task);model.edit()
        onInteraction={session=SessionSnapshot(SessionState.LOCKED,2);model.clear()}
        model.open(task)
        assertNull(model.selected);assertNull(model.board);assertFalse(model.form)
        assertEquals(TaskInput(""),model.input);assertNull(model.filter)
        session=SessionSnapshot(SessionState.READY,3);onInteraction={}
        model.refresh()
        assertNull(model.selected);assertFalse(model.form);assertEquals(TaskInput(""),model.input)
        model.open(task)
        assertEquals(task,model.selected)
    }
    @Test fun synchronousRevokeRejectsEveryTaskPublicationAndWritePath() {
        val paths=listOf<()->Unit>(
            {model.add("motte",TaskRules.templates.first())}, {model.edit()},
            {model.change(TaskInput("stale",assigneeRoleId="r",affectedPersonId="motte"))},
            {model.showPerson("motte")}, {model.save()}, {model.transition(TaskStatus.DONE)}, {model.delete()})
        for(path in paths){
            session=SessionSnapshot(SessionState.READY,session.generation+1)
            action=action.copy(status=ActionStatus.PREPARATION)
            onInteraction={};model.refresh();model.open(repo.tasks.first());model.edit()
            onInteraction={session=SessionSnapshot(SessionState.LOCKED,session.generation+1);model.clear()}
            path()
            assertNull(model.selected);assertFalse(model.form);assertEquals(TaskInput(""),model.input)
            assertNull(model.filter);assertEquals(0,repo.writes)
        }
    }
    @Test fun readyAgainDuringInteractionStillRejectsOldGeneration() {
        model.refresh()
        onInteraction={session=SessionSnapshot(SessionState.LOCKED,2);model.clear();session=SessionSnapshot(SessionState.READY,3)}
        model.open(repo.tasks.first())
        assertNull(model.selected);assertFalse(model.form)
        model.add("motte");assertEquals(TaskInput(""),model.input);assertFalse(model.form)
    }
    @Test fun changedActionDuringInteractionRejectsOldTaskAndDraft() {
        model.refresh()
        onInteraction={action=action.copy(id="b")}
        model.open(repo.tasks.first());assertNull(model.selected)
        action=action.copy(id="a")
        model.change(TaskInput("stale"));assertEquals(TaskInput(""),model.input)
    }
    @Test fun revokeDuringSensitiveCleanupAlsoPreventsPublication() {
        model.refresh()
        onLeaveSensitive={session=SessionSnapshot(SessionState.LOCKED,session.generation+1);model.clear()}
        model.open(repo.tasks.first());assertNull(model.selected)
        session=SessionSnapshot(SessionState.READY,session.generation+1)
        model.add("motte");assertEquals(TaskInput(""),model.input);assertFalse(model.form)
    }
    @Test fun oldWriteCannotUseSessionThatReauthenticatesDuringInteraction() {
        model.refresh();model.open(repo.tasks.first());model.edit()
        onInteraction={session=SessionSnapshot(SessionState.LOCKED,2);model.clear();session=SessionSnapshot(SessionState.READY,3)}
        model.save()
        assertEquals(0,repo.writes);assertNull(model.selected);assertFalse(model.form)
    }
}
