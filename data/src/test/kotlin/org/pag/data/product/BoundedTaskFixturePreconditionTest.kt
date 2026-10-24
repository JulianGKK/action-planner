package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Reproduces only fixture fields relevant to Task admission, against real SQLite schema 11.
 * UUID values differ, lengths do not. No Android execution or product correction.
 */
class BoundedTaskFixturePreconditionTest {
    @Test fun hundredTaskFixtureExceedsHistoricalEightButFitsNewTransitionBudget() = runBlocking {
        for (transitioned in listOf(false, true)) SqliteHostConnection().use { db ->
            db.execute("INSERT INTO actions VALUES('p11-active','Synthetic action','Synthetic benchmark',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            repeat(100) { i ->
                val task = "00000000-0000-0000-0000-%012d".format(java.util.Locale.ROOT,i)
                val event = "11111111-0000-0000-0000-%012d".format(java.util.Locale.ROOT,i)
                db.execute("INSERT INTO tasks(id,actionId,title,priority,status,assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey,createdAt,updatedAt,completedAt) VALUES('$task','p11-active','Synthetic task $i','NORMAL','OPEN',NULL,NULL,NULL,NULL,NULL,1,1,NULL)")
                db.execute("INSERT INTO task_events VALUES('$event','p11-active','$task','CREATED',1)")
            }
            if (transitioned) {
                db.execute("UPDATE tasks SET status='IN_PROGRESS' WHERE id='00000000-0000-0000-0000-000000000000'")
                db.execute("INSERT INTO task_events VALUES('22222222-0000-0000-0000-000000000000','p11-active','00000000-0000-0000-0000-000000000000','STARTED',1)")
            }
            suspend fun scalar(sql: String) = db.usePrepared(sql) { q ->
                check(q.step()); q.getLong(0)
            }
            val columns = mapOf(
                "actions" to "id,title,description,status,sensitivity",
                "tasks" to "id,actionId,title,priority,status,assigneePersonId,assigneeRoleId,affectedPersonId,sourceTemplateKey",
                "task_events" to "id,actionId,taskId,type")
            var rows=0L;var fields=0L;var bytes=0L
            for ((table,names) in columns) {
                val count=scalar("SELECT COUNT(*) FROM $table")
                rows=Math.addExact(rows,count)
                fields=Math.addExact(fields,Math.multiplyExact(count,names.split(',').size.toLong()))
                val lengths=names.split(',').joinToString("+"){"COALESCE(length(CAST($it AS BLOB)),0)"}
                bytes=Math.addExact(bytes,scalar("SELECT COALESCE(SUM($lengths),0) FROM $table"))
            }
            assertEquals(if(transitioned)202L else 201L,rows)
            assertEquals(if(transitioned)1309L else 1305L,fields)
            assertEquals(if(transitioned)16346L else 16250L,bytes)
            val charge=rows*20608+fields*2272+bytes*80+3072 // Current documented engineering weights.
            assertEquals(if(transitioned)8447616L else 8410240L,charge)
            assertTrue(charge>ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
            assertTrue(charge<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
            val c = object : StorageContext {
                override val scope = AccessScope.READY
                override val generation = 1L
                override fun check(point: AccessPoint) = true
                override fun publish(result: () -> Unit): Boolean { result(); return true }
            }
            var blocks = 0
            val result = boundedTaskRead(BoundedActiveBinding("p11-active",1),
                object : StorageAccess { override fun acquire(scope: AccessScope) = c },
                { BoundedActiveBinding("p11-active",1) }, { blocks++ }) { context, current ->
                runBlocking {
                    db.execute("BEGIN")
                    try { BoundedTaskStore(db,context,current).read(BoundedActiveBinding("p11-active",1)) }
                    finally { db.execute("ROLLBACK") }
                }
            }
            assertTrue(result is BoundedTaskResult.Value)
            assertEquals(charge,(result as BoundedTaskResult.Value).transitionCharge)
            assertTrue(db.payloadStatements>0);assertEquals(0,db.openStatements)
            assertEquals(0,blocks) // Test precondition, not storage corruption.
        }
    }

    private class Positive : AutoCloseable {
        val db=SqliteHostConnection()
        var allowed=true
        var allowPublication=true
        var attempts=0
        var blocks=0
        var cleaned=false
        val c=object:StorageContext {
            override val scope=AccessScope.READY
            override val generation=1L
            override fun check(point:AccessPoint)=allowed
            override fun publish(result:()->Unit):Boolean {
                assertTrue(cleaned);assertEquals(0,db.openStatements);attempts++
                if(!allowed || !allowPublication)return false
                result();return true
            }
        }
        fun exec(sql:String)=runBlocking{db.execute(sql)}
        init {
            exec("INSERT INTO actions VALUES('p11-active','Synthetic action','Synthetic benchmark',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            repeat(40){i->
                val id="00000000-0000-0000-0000-%012d".format(java.util.Locale.ROOT,i)
                val event="11111111-0000-0000-0000-%012d".format(java.util.Locale.ROOT,i)
                exec("INSERT INTO tasks(id,actionId,title,priority,status,assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey,createdAt,updatedAt,completedAt) VALUES('$id','p11-active','Synthetic task $i','NORMAL','OPEN',NULL,NULL,NULL,NULL,NULL,1,1,NULL)")
                exec("INSERT INTO task_events VALUES('$event','p11-active','$id','CREATED',1)")
            }
        }
        fun read():BoundedTaskResult=boundedTaskRead(BoundedActiveBinding("p11-active",1),
            object:StorageAccess{override fun acquire(scope:AccessScope)=c.takeIf{allowed}},
            {BoundedActiveBinding("p11-active",1)},{blocks++}){context,current->
            runBlocking {
                cleaned=false;db.execute("BEGIN")
                try{BoundedTaskStore(db,context,current).read(BoundedActiveBinding("p11-active",1))}
                finally{db.execute("ROLLBACK");assertEquals(0,db.openStatements);cleaned=true}
            }
        }
        fun complete()=read().also{assertTrue(it is BoundedTaskResult.Value)} as BoundedTaskResult.Value
        override fun close(){assertEquals(0,db.openStatements);db.close();assertTrue(db.closed)}
    }
    @Test fun fortyTasksAndFullHistoryAreAdmittedAcrossMultipleBatchesBeforeAndAfterTransition() = Positive().use { h ->
        val before=h.complete()
        assertEquals(40,before.board.tasks.size);assertEquals(40,before.board.events.size)
        assertEquals(5,h.db.payloadStatements) // Action one, Task two, Event two queries.
        assertEquals((0..39).map{"11111111-0000-0000-0000-%012d".format(java.util.Locale.ROOT,it)},before.board.events.map{it.id})
        val task=before.board.tasks.first()
        val next=checkNotNull(TaskRules.transition(task,TaskStatus.IN_PROGRESS,2))
        assertEquals(TaskEventType.STARTED,TaskRules.event(task.status,next.status))
        h.exec("BEGIN")
        try {
            h.exec("UPDATE tasks SET status='${next.status}',updatedAt=2 WHERE id='${task.id}' AND status='OPEN'")
            runBlocking{h.db.usePrepared("SELECT changes()") { q->check(q.step());assertEquals(1L,q.getLong(0)) }}
            h.exec("INSERT INTO task_events VALUES('22222222-0000-0000-0000-000000000000','p11-active','${task.id}','STARTED',2)")
            h.exec("COMMIT")
        }catch(failure:Throwable){h.exec("ROLLBACK");throw failure}
        val after=h.complete()
        assertEquals(40,after.board.tasks.size);assertEquals(41,after.board.events.size)
        assertEquals(before.board.events,after.board.events.dropLast(1))
        assertEquals(TaskEvent("22222222-0000-0000-0000-000000000000","p11-active",task.id,TaskEventType.STARTED,2),after.board.events.last())
        assertEquals(next,after.board.tasks.single{it.id==task.id})
        assertEquals(1,after.board.events.count{it.type==TaskEventType.STARTED})
        assertEquals(10,h.db.payloadStatements)
        assertEquals(849184L,before.retainedCharge);assertEquals(3387520L,before.transitionCharge)
        assertEquals(858528L,after.retainedCharge);assertEquals(3424896L,after.transitionCharge)
        assertEquals(0,h.blocks)
    }
    @Test fun oversizedTemplateAloneTurnsAdmittedFixtureIntoPrePayloadRefusal() = Positive().use { h ->
        val first=h.complete()
        repeat(3){assertEquals(first,h.complete())}
        val statements=h.db.payloadStatements;val blobs=h.db.blobReads
        val row=TaskRow("33333333-0000-0000-0000-000000000000","p11-active","Synthetic oversized template","NORMAL","OPEN",null,null,null,null,"x".repeat(200000),1,1,null, editRevision = 0)
        assertTrue(TaskRules.valid(row.model().input()))
        h.exec("BEGIN")
        try {
            h.exec("INSERT INTO tasks(id,actionId,title,priority,status,assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey,createdAt,updatedAt,completedAt) VALUES('${row.id}','p11-active','${row.title}','NORMAL','OPEN',NULL,NULL,NULL,NULL,replace(printf('%0200000d',0),'0','x'),1,1,NULL)")
            h.exec("INSERT INTO task_events VALUES('44444444-0000-0000-0000-000000000000','p11-active','${row.id}','CREATED',1)")
            h.exec("COMMIT")
        }catch(failure:Throwable){h.exec("ROLLBACK");throw failure}
        assertEquals(BoundedTaskResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED),h.read())
        assertEquals(statements,h.db.payloadStatements);assertEquals(blobs,h.db.blobReads);assertEquals(0,h.blocks)
    }
    @Test fun publicationDenialIsAfterCompleteReadAndCleanupAndRevokePreventsFurtherReads() = Positive().use { h ->
        h.complete();assertEquals(1,h.attempts)
        val queries=h.db.payloadStatements
        h.allowPublication=false
        assertEquals(BoundedTaskResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED),h.read())
        assertEquals(2,h.attempts);assertEquals(queries+5,h.db.payloadStatements);assertTrue(h.cleaned)
        h.allowPublication=true;assertEquals(40,h.complete().board.events.size)
        h.allowed=false
        val after=h.db.payloadStatements
        assertEquals(BoundedTaskResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED),h.read())
        assertEquals(3,h.attempts);assertEquals(after,h.db.payloadStatements);assertEquals(0,h.blocks)
    }

    @Test fun oversizedTemplateRemainsDomainValidWithoutChangingRules() {
        val input=TaskInput("Synthetic oversized template",sourceTemplateKey="x".repeat(200000))
        assertTrue(TaskRules.valid(input))
        val row=TaskRow("synthetic","p11-active",input.title,"NORMAL","OPEN",null,null,null,null,
            input.sourceTemplateKey,1,1,null, editRevision = 0)
        assertEquals(input,row.model().input())
        assertEquals(TaskEventType.STARTED,TaskRules.event(TaskStatus.OPEN,TaskStatus.IN_PROGRESS))
    }
}
