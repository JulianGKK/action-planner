package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class TaskRulesTest {
    private fun task(s:TaskStatus=TaskStatus.OPEN)=Task("t","a","EA kontaktieren",TaskPriority.HIGH,s,null,"gesa","motte",null,"CONTACT_EA",1,1,9L.takeIf{s==TaskStatus.DONE})
    @Test fun titleCodepointsAndDefaults(){
        assertFalse(TaskRules.valid(TaskInput(" \n ")))
        assertTrue(TaskRules.valid(TaskInput(" 😀 ")))
        assertTrue(TaskRules.valid(TaskInput("😀".repeat(120))))
        assertFalse(TaskRules.valid(TaskInput("😀".repeat(121))))
        assertEquals(TaskPriority.NORMAL,TaskInput("x").priority)
        assertNull(TaskInput("x").dueAt)
    }
    @Test fun assignmentAndAffectedPersonAreIndependent(){
        assertTrue(TaskRules.valid(TaskInput("x",assigneeRoleId="r",affectedPersonId="p")))
        assertTrue(TaskRules.valid(TaskInput("x",assigneePersonId="p",affectedPersonId="other")))
        assertFalse(TaskRules.valid(TaskInput("x",assigneePersonId="p",assigneeRoleId="r")))
    }
    @Test fun allLifecyclePairsAndCompletionTimes(){
        val allowed=mapOf(
            (TaskStatus.OPEN to TaskStatus.IN_PROGRESS) to TaskEventType.STARTED,
            (TaskStatus.OPEN to TaskStatus.DONE) to TaskEventType.COMPLETED,
            (TaskStatus.IN_PROGRESS to TaskStatus.DONE) to TaskEventType.COMPLETED,
            (TaskStatus.OPEN to TaskStatus.CANCELLED) to TaskEventType.CANCELLED,
            (TaskStatus.IN_PROGRESS to TaskStatus.CANCELLED) to TaskEventType.CANCELLED,
            (TaskStatus.DONE to TaskStatus.OPEN) to TaskEventType.REOPENED,
            (TaskStatus.CANCELLED to TaskStatus.OPEN) to TaskEventType.REOPENED)
        for(from in TaskStatus.entries)for(to in TaskStatus.entries){
            assertEquals(allowed[from to to],TaskRules.event(from,to))
            val updated=TaskRules.transition(task(from),to,20)
            if(from to to in allowed){assertEquals(to,updated!!.status);assertEquals(20,updated.updatedAt);assertEquals(20L.takeIf{to==TaskStatus.DONE},updated.completedAt)}else assertNull(updated)
        }
    }
    @Test fun persistedStatusPolicy(){
        for(s in ActionStatus.entries){
            assertEquals(s in setOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE),TaskRules.mayWrite(s))
            assertEquals(s==ActionStatus.PREPARATION,TaskRules.mayDelete(s))
            assertEquals(s in setOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED),TaskRules.mayRead(s))
        }
    }
    @Test fun activeSortingAndAffectedCounts(){
        val high=task();val normal=high.copy(id="n",priority=TaskPriority.NORMAL,dueAt=2)
        val early=high.copy(id="e",dueAt=5);val later=high.copy(id="l",dueAt=10)
        val done=task(TaskStatus.DONE).copy(id="done")
        val board=TaskBoard("a",listOf(normal,high,later,done,early),emptyList())
        assertEquals(listOf("e","l","t","n"),board.active.map{it.id})
        assertEquals(4,board.affectedCount("motte"));assertEquals(listOf(done),board.closed)
    }
    @Test fun templatesAreOnlyInputsAndKeysAreStable(){
        assertEquals(5,TaskRules.templates.size)
        assertEquals("RALLY_AT_ACTION",TaskRules.templates.first().input().sourceTemplateKey)
        assertTrue(TaskRules.templates.all{TaskRules.valid(it.input())})
    }
}
