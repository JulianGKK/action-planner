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
        var writes=0;var input:TaskInput?=null;var deferred=false
        var callback:((OperationsResult<TaskBoard>)->Unit)?=null
        var tasks=listOf(Task("t","a","EA kontaktieren",TaskPriority.HIGH,TaskStatus.OPEN,null,"r","motte",null,null,1,1,null))
        override fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit){if(deferred)callback=publish else publish(OperationsResult.Value(TaskBoard(actionId,tasks,emptyList())))}
        override fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit){writes++;this.input=input;val t=tasks.first().copy(id="new",title=input.title,affectedPersonId=input.affectedPersonId);tasks=tasks+t;publish(OperationsResult.Value(t))}
        override fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit){writes++;this.input=input;val t=expected.copy(title=input.title);tasks=tasks.map{if(it.id==t.id)t else it};publish(OperationsResult.Value(t))}
        override fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit){writes++;val t=TaskRules.transition(expected,status,5)!!;tasks=tasks.map{if(it.id==t.id)t else it};publish(OperationsResult.Value(t))}
        override fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit){writes++;tasks=tasks.filterNot{it.id==expected.id};publish(OperationsResult.Value(Unit))}
    }
    private val repo=Repo()
    private val model=TaskModel(repo,{session},{action},{interactions++;onInteraction()},{onLeaveSensitive()})
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
