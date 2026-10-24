package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P104RevisionTest {
    private fun plan(h:CoreWriterIntegrationTest.H)=runBlocking{h.data.operations.personPlan("a","p0")!!}.let{PersonRolePlan(runBlocking{h.data.operations.personAssignments("a","p0")}.map{a->a.roleId}.toSet(),it.primaryRoleId,it.editRevision)}
    @Test fun identicalTargetsIncrementEveryRevisionExactlyOnceAndRollbackRestoresAll()=CoreWriterIntegrationTest.H().use{h->
        val person=h.personRow();assertTrue(h.personExpected(person,person.pseudonym) is PersonResult.Value);assertEquals(1L,h.personRow().editRevision)
        val role=h.roleRow();assertTrue(h.ops{it.role("a",role,role.name)} is OperationsResult.Value);assertEquals(1L,h.roleRow().editRevision)
        val empty=plan(h)
        assertTrue(h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",empty),empty)} is OperationsResult.Value)
        assertEquals(1L,plan(h).editRevision);assertEquals(2L,h.count("person_role_plans"))
        // Real transaction rollback, not an optimistic in-memory counter reset.
        runBlocking{
            h.db.execute("BEGIN IMMEDIATE")
            assertEquals(1,h.data.persons.rename("a","p0","Rollback","rollback",1))
            assertEquals(1,h.data.operations.renameRole("a","r0",1,"Rollback role","rollback role"))
            assertEquals(1,h.data.operations.claimPlan("a","p0",1))
            h.db.execute("ROLLBACK")
        }
        assertEquals(person.copy(editRevision=1),h.personRow());assertEquals(role.copy(editRevision=1),h.roleRow());assertEquals(empty.copy(editRevision=1),plan(h))
        h.advance(ActionStatus.PREPARATION)
        val state=h.state()
        assertTrue(h.ops{it.snapshot("a","p0",state,state.status,null,"")} is OperationsResult.Value)
        assertEquals(state.copy(editRevision=1,updatedAt=h.state().updatedAt),h.state())
        val committedState=h.state()
        runBlocking{h.db.execute("BEGIN IMMEDIATE");assertEquals(1,h.data.operations.changeState("a","p0",1,null,"LEFT",null,0));h.db.execute("ROLLBACK")}
        assertEquals(committedState,h.state())
    }
    @Test fun lastRepresentableRevisionIsAcceptedOnceForEveryKind()=CoreWriterIntegrationTest.H().use{h->
        for(table in listOf("persons","roles","person_role_plans"))h.exec("UPDATE $table SET editRevision=9223372036854775806")
        val p=h.personRow();assertTrue(h.personExpected(p,p.pseudonym) is PersonResult.Value);assertEquals(Long.MAX_VALUE,h.personRow().editRevision)
        val r=h.roleRow();assertTrue(h.ops{it.role("a",r,r.name)} is OperationsResult.Value);assertEquals(Long.MAX_VALUE,h.roleRow().editRevision)
        val plan=plan(h);assertTrue(h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",plan),plan)} is OperationsResult.Value);assertEquals(Long.MAX_VALUE,plan(h).editRevision)
        h.advance(ActionStatus.PREPARATION);h.exec("UPDATE person_operational_states SET editRevision=9223372036854775806")
        val state=h.state();assertTrue(h.ops{it.snapshot("a","p0",state,state.status,null,"")} is OperationsResult.Value);assertEquals(Long.MAX_VALUE,h.state().editRevision)
        val data=HostTaskData(h.db)
        fun write(work:suspend(TaskStore)->OperationsResult<Task>)=h.write<OperationsResult<Task>>(WorkspaceWriterFamily.TASK,{it is OperationsResult.Rejected},{null}){work(TaskStore(data,it){42})}
        val task=(write{it.create("a",TaskInput("Last revision"))} as OperationsResult.Value).value
        h.exec("UPDATE tasks SET editRevision=9223372036854775806")
        val last=(write{it.edit("a",task.copy(editRevision=Long.MAX_VALUE-1),task.input())} as OperationsResult.Value).value
        assertEquals(Long.MAX_VALUE,last.editRevision);assertEquals(1L,h.count("task_events"))
        assertEquals(OperationsResult.Rejected(OperationsFailure.REVISION_EXHAUSTED),write{it.transition("a",last,TaskStatus.CANCELLED)})
    }
    @Test fun finalPrimaryRoleFailureRollsBackHeaderRevisionAndAssignmentsTogether()=CoreWriterIntegrationTest.H().use{h->
        val original=plan(h)
        val chosen=PersonRolePlan(setOf("r0"),"r0",0)
        h.exec("CREATE TRIGGER synthetic_final_primary_failure BEFORE UPDATE OF primaryRoleId ON person_role_plans WHEN NEW.primaryRoleId IS NOT NULL BEGIN SELECT RAISE(ABORT,'SYNTHETIC_FINAL_PRIMARY_FAILURE'); END")
        var calls=0
        h.queue<OperationsResult<Unit>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{null},{calls++}){
            OperationsStore(h.data,it){42}.plan("a","p0",ExpectedPersonRolePlan("a","p0",original),chosen)
        }
        h.worker.drain();h.main.drain()
        assertEquals(0,calls);assertEquals(original,plan(h));assertEquals(0L,h.count("person_planned_roles"));assertEquals(2L,h.count("person_role_plans"))
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.last().outcome)
        runBlocking{h.db.usePrepared("PRAGMA foreign_key_check"){assertFalse(it.step())}}
    }
    @Test fun maximumAndForeignIdentityRejectBeforeAnyRevisionOrEventWrite()=CoreWriterIntegrationTest.H().use{h->
        for(table in listOf("persons","roles","person_role_plans"))h.exec("UPDATE $table SET editRevision=9223372036854775807")
        val person=h.personRow();val role=h.roleRow();val p=plan(h)
        assertEquals(PersonResult.Rejected(PersonRejection.REVISION_EXHAUSTED),h.personExpected(person,person.pseudonym))
        assertEquals(OperationsResult.Rejected(OperationsFailure.REVISION_EXHAUSTED),h.ops{it.role("a",role,role.name)})
        assertEquals(OperationsResult.Rejected(OperationsFailure.REVISION_EXHAUSTED),h.ops{it.deleteRole("a",role)})
        assertEquals(OperationsResult.Rejected(OperationsFailure.REVISION_EXHAUSTED),h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",p),p)})
        assertEquals(PersonResult.Rejected(PersonRejection.STALE),h.personExpected(person.copy(actionId="b"),"Foreign"))
        h.advance(ActionStatus.PREPARATION);h.exec("UPDATE person_operational_states SET editRevision=9223372036854775807")
        val old=h.state();assertEquals(OperationsResult.Rejected(OperationsFailure.REVISION_EXHAUSTED),h.ops{it.snapshot("a","p0",old,OperationalStatus.LEFT,null,"")})
        assertEquals(old,h.state())
    }
    @Test fun taskNoopRollbackMaxAndTransitionKeepRevisionAndEventAtomic()=CoreWriterIntegrationTest.H().use{h->
        val data=HostTaskData(h.db)
        fun write(work:suspend(TaskStore)->OperationsResult<Task>)=h.write<OperationsResult<Task>>(WorkspaceWriterFamily.TASK,{it is OperationsResult.Rejected},{null}){work(TaskStore(data,it){42})}
        val a=(write{it.create("a",TaskInput("A"))} as OperationsResult.Value).value
        val b=(write{it.edit("a",a,a.input())} as OperationsResult.Value).value
        assertEquals(a.copy(editRevision=1),b);assertEquals(1L,h.count("task_events"))
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),write{it.transition("a",a,TaskStatus.CANCELLED)})
        val c=(write{it.transition("a",b,TaskStatus.CANCELLED)} as OperationsResult.Value).value
        assertEquals(2L,c.editRevision);assertEquals(2L,h.count("task_events"))
        runBlocking{h.db.execute("BEGIN IMMEDIATE");h.db.execute("UPDATE tasks SET title='Rollback',editRevision=editRevision+1");h.db.execute("ROLLBACK")}
        assertEquals(c,runBlocking{data.tasks.get("a",c.id)!!.model()})
        h.exec("UPDATE tasks SET editRevision=9223372036854775807")
        val max=c.copy(editRevision=Long.MAX_VALUE)
        assertEquals(OperationsResult.Rejected(OperationsFailure.REVISION_EXHAUSTED),write{it.edit("a",max,max.input())})
        assertEquals(OperationsResult.Rejected(OperationsFailure.REVISION_EXHAUSTED),write{it.transition("a",max,TaskStatus.OPEN)})
        assertEquals(2L,h.count("task_events"))
    }
}
