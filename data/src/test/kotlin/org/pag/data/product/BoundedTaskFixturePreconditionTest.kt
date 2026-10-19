package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Reproduces only fixture fields relevant to Task admission, against real SQLite schema 11.
 * UUID values differ, lengths do not. No Android execution or product correction.
 */
class BoundedTaskFixturePreconditionTest {
    @Test fun existingHundredTaskFixtureAlreadyExceedsTransitionBudget() = runBlocking {
        for (transitioned in listOf(false, true)) SqliteHostConnection().use { db ->
            db.execute("INSERT INTO actions VALUES('p11-active','Synthetic action','Synthetic benchmark',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            repeat(100) { i ->
                val task = "00000000-0000-0000-0000-%012d".format(java.util.Locale.ROOT,i)
                val event = "11111111-0000-0000-0000-%012d".format(java.util.Locale.ROOT,i)
                db.execute("INSERT INTO tasks VALUES('$task','p11-active','Synthetic task $i','NORMAL','OPEN',NULL,NULL,NULL,NULL,NULL,1,1,NULL)")
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
            assertTrue(charge>ActiveWorkspaceBudget.LIMIT_BYTES)
            val c = object : StorageContext {
                override val scope = AccessScope.READY
                override val generation = 1L
                override fun check(point: AccessPoint) = true
                override fun publish(result: () -> Unit): Boolean { result(); return true }
            }
            var blocks = 0
            val result = boundedTaskRead(BoundedActiveBinding("p11-active",1,0),
                object : StorageAccess { override fun acquire(scope: AccessScope) = c },
                { BoundedActiveBinding("p11-active",1,0) }, { blocks++ }) { context, current ->
                runBlocking {
                    db.execute("BEGIN")
                    try { BoundedTaskStore(db,context,current).read(BoundedActiveBinding("p11-active",1,0)) }
                    finally { db.execute("ROLLBACK") }
                }
            }
            assertEquals(BoundedTaskResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED),result)
            assertEquals(0,db.payloadStatements);assertEquals(0,db.blobReads);assertEquals(0,db.openStatements)
            assertEquals(0,blocks) // Test precondition, not storage corruption.
        }
    }

    @Test fun oversizedTemplateIsDomainValidButCannotIsolateRefusalFromAlreadyRefusedBaseline() {
        val input=TaskInput("Synthetic oversized template",sourceTemplateKey="x".repeat(200000))
        assertTrue(TaskRules.valid(input))
        val row=TaskRow("synthetic","p11-active",input.title,"NORMAL","OPEN",null,null,null,null,
            input.sourceTemplateKey,1,1,null)
        assertEquals(input,row.model().input())
        assertEquals(TaskEventType.STARTED,TaskRules.event(TaskStatus.OPEN,TaskStatus.IN_PROGRESS))
    }
}
