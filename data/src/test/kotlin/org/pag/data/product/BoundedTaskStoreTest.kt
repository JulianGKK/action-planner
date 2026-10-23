package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual production SQL/decoder against SQLite schema 11; not an Android driver proof. */
class BoundedTaskStoreTest {
    private class H(encoding: String = "UTF-8") : AutoCloseable {
        val db = SqliteHostConnection(encoding)
        var allowed = true
        var generation = 1L
        var actionId = "a"
        var bound = true
        var blocks = 0
        var publications = 0
        var onPublication: () -> Unit = {}
        var onCleanup: () -> Unit = {}
        var onPhase: (BoundedTaskStore.Phase) -> Unit = {}
        val phases = mutableListOf<BoundedTaskStore.Phase>()
        val c = object : StorageContext {
            override val scope = AccessScope.READY
            override val generation = 1L
            override fun check(point: AccessPoint) = allowed && this@H.generation == generation
            override fun publish(result: () -> Unit): Boolean {
                assertEquals(0, db.openStatements); onPublication()
                if (!check(AccessPoint.PUBLICATION)) return false
                result(); publications++; return true
            }
        }
        fun exec(sql: String) = runBlocking { db.execute(sql) }
        fun seed(tasks: Int = 1, events: Int = 1) {
            exec("INSERT INTO actions VALUES('a','Synthetic','Description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            exec("INSERT INTO persons VALUES('p','a','Synthetic','synthetic',1)")
            exec("INSERT INTO roles VALUES('r','a','Synthetic','synthetic',1)")
            if (tasks > 0) exec("WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i+1 FROM n WHERE i<$tasks) " +
                "INSERT INTO tasks SELECT 't'||i,'a','Synthetic '||i,'NORMAL','OPEN','p',NULL,'p',NULL,NULL,i,i,NULL FROM n")
            if (events > 0) exec("WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i+1 FROM n WHERE i<$events) " +
                "INSERT INTO task_events SELECT 'e'||i,'a','t1','CREATED',i FROM n")
        }
        fun read(id: String = "a", requestedGeneration: Long = 1): BoundedTaskResult = boundedTaskRead(
            BoundedActiveBinding(id, requestedGeneration),
            object : StorageAccess { override fun acquire(scope: AccessScope) = c.takeIf { allowed } },
            { if (bound) BoundedActiveBinding(actionId, generation) else null }, { blocks++ }) { context, current ->
            runBlocking {
                db.execute("BEGIN")
                try { BoundedTaskStore(db, context, current) { phases.add(it); onPhase(it) }
                    .read(BoundedActiveBinding(id, requestedGeneration)) }
                finally { db.execute("ROLLBACK"); assertEquals(0, db.openStatements); onCleanup() }
            }
        }
        override fun close() { assertEquals(0, db.openStatements); db.close(); assertTrue(db.closed) }
    }
    private fun rejected(status: BoundedActiveStatus, result: BoundedTaskResult) =
        assertEquals(BoundedTaskResult.Rejected(status), result)

    @Test fun smallBoardPreservesAllFieldsAndDomainProjections() = H().use { h ->
        h.seed(3, 0)
        h.exec("UPDATE tasks SET assigneePersonId=NULL,assigneeRoleId='r',priority='HIGH',dueAt=8,sourceTemplateKey='synthetic-key' WHERE id='t2'")
        h.exec("UPDATE tasks SET status='DONE',completedAt=7,updatedAt=7 WHERE id='t3'")
        h.exec("INSERT INTO task_events VALUES('later','a','t3','COMPLETED',7),('first','a','t3','CREATED',1),('tie','a','t3','STARTED',1)")
        val v = h.read() as BoundedTaskResult.Value
        val expected = TaskBoard("a", listOf(
            Task("t1","a","Synthetic 1",TaskPriority.NORMAL,TaskStatus.OPEN,"p",null,"p",null,null,1,1,null),
            Task("t2","a","Synthetic 2",TaskPriority.HIGH,TaskStatus.OPEN,null,"r","p",8,"synthetic-key",2,2,null),
            Task("t3","a","Synthetic 3",TaskPriority.NORMAL,TaskStatus.DONE,"p",null,"p",null,null,3,7,7)),
            listOf(TaskEvent("first","a","t3",TaskEventType.CREATED,1),TaskEvent("tie","a","t3",TaskEventType.STARTED,1),
                TaskEvent("later","a","t3",TaskEventType.COMPLETED,7)))
        assertEquals(expected, v.board); assertEquals(expected.active,v.board.active)
        assertEquals(expected.closed,v.board.closed); assertEquals(2,v.board.affectedCount("p"))
        assertTrue(h.phases.indexOf(BoundedTaskStore.Phase.ADMITTED)<h.phases.indexOf(BoundedTaskStore.Phase.TEXT))
        assertEquals(1,h.db.textReads)
    }
    @Test fun realLegacyDaoSqlHasIdenticalTaskContentAndEventOrder() = H().use { h ->
        h.seed(40,60)
        h.exec("UPDATE task_events SET occurredAt=occurredAt%4")
        val bounded = (h.read() as BoundedTaskResult.Value).board
        val tasks = runBlocking { h.db.usePrepared("SELECT * FROM tasks WHERE actionId=?") { q ->
            q.bindText(1,"a")
            buildList {
                while(q.step()) {
                    fun text(i:Int) = if(q.isNull(i))null else q.getText(i)
                    fun number(i:Int) = if(q.isNull(i))null else q.getLong(i)
                    add(TaskRow(q.getText(0),q.getText(1),q.getText(2),q.getText(3),q.getText(4),
                        text(5),text(6),text(7),number(8),text(9),q.getLong(10),q.getLong(11),number(12)).model())
                }
            }
        } }
        val events = runBlocking { h.db.usePrepared("SELECT * FROM task_events WHERE actionId=? ORDER BY occurredAt,rowid") { q ->
            q.bindText(1,"a");buildList {
                while(q.step()) add(TaskEventRow(q.getText(0),q.getText(1),q.getText(2),q.getText(3),q.getLong(4)).model())
            }
        } }
        val legacy=TaskBoard("a",tasks,events)
        assertEquals(legacy.tasks.toSet(),bounded.tasks.toSet())
        assertEquals(legacy.events,bounded.events);assertEquals(legacy.active,bounded.active)
        assertEquals(legacy.closed,bounded.closed)
    }
    @Test fun extremeRowidsAreNotSkippedOrOverflowed() = H().use { h ->
        h.seed(2,2)
        h.exec("UPDATE tasks SET rowid=-9223372036854775808 WHERE id='t1'")
        h.exec("UPDATE tasks SET rowid=9223372036854775807 WHERE id='t2'")
        h.exec("UPDATE task_events SET rowid=-9223372036854775808 WHERE id='e1'")
        h.exec("UPDATE task_events SET rowid=9223372036854775807 WHERE id='e2'")
        val v=h.read() as BoundedTaskResult.Value
        assertEquals(listOf("t1","t2"),v.board.tasks.map{it.id})
        assertEquals(listOf("e1","e2"),v.board.events.map{it.id})
    }
    @Test fun exceptionDuringMaterializationClosesStatementAndDeniesPublication() = H().use { h ->
        h.seed();h.onPhase={if(it==BoundedTaskStore.Phase.TEXT)error("synthetic read failure")}
        rejected(BoundedActiveStatus.STORAGE_FAILURE,h.read())
        assertEquals(0,h.db.openStatements);assertEquals(0,h.publications);assertEquals(1,h.blocks)
    }
    @Test fun realSqlSumOverflowCannotWrapIntoSmallAdmission() = H().use { h ->
        h.exec("CREATE TABLE overflow_fixture(value INTEGER)")
        h.exec("INSERT INTO overflow_fixture VALUES(9223372036854775807),(1)")
        assertThrows(IllegalStateException::class.java){runBlocking{
            h.db.usePrepared("SELECT SUM(value) FROM overflow_fixture"){it.step()}
        }}
        assertEquals(0,h.db.openStatements)
    }
    @Test fun emptyCompleteBoardIsAllowed() = H().use { h ->
        h.seed(0,0); assertEquals(TaskBoard("a",emptyList(),emptyList()),(h.read() as BoundedTaskResult.Value).board)
    }
    @Test fun foreignMissingAndNonActiveDeniedBeforePayload() {
        for (status in listOf("PREPARATION","COMPLETED")) H().use { h ->
            h.seed();h.exec("UPDATE actions SET status='$status',completedAt=${if(status=="COMPLETED") "3" else "NULL"}")
            rejected(BoundedActiveStatus.ACCESS_REVOKED,h.read());assertEquals(0,h.db.blobReads)
        }
        H().use { h ->h.seed();rejected(BoundedActiveStatus.ACCESS_REVOKED,h.read("foreign"));assertEquals(0,h.db.payloadStatements) }
    }
    @Test fun foreignRowsAreNeitherChargedNorLoaded() = H().use { h ->
        h.seed();h.exec("INSERT INTO actions VALUES('b','Other','Other',1,2,0,'ACTIVE','SENSITIVE',NULL)")
        h.exec("INSERT INTO tasks VALUES('foreign','b',printf('%01000000d',0),'NORMAL','OPEN',NULL,NULL,NULL,NULL,NULL,1,1,NULL)")
        assertEquals(listOf("t1"),(h.read() as BoundedTaskResult.Value).board.tasks.map{it.id})
    }
    @Test fun manyTasksAndEventsTraverseAllKeysetBatchesWithoutTruncation() = H().use { h ->
        h.seed(45,99);val v=h.read() as BoundedTaskResult.Value
        assertEquals(45,v.board.tasks.size);assertEquals(99,v.board.events.size)
        assertEquals((1..99).map{it.toLong()},v.board.events.map{it.occurredAt})
    }
    @Test fun manyTasksOrEventsRefusedBeforeAnyPayloadQuery() {
        for(events in listOf(false,true)) H().use { h ->
            h.seed(if(events)1 else 2000,if(events)2000 else 1)
            rejected(BoundedActiveStatus.BUDGET_REFUSED,h.read())
            assertEquals(0,h.db.payloadStatements);assertEquals(0,h.db.blobReads);assertEquals(0,h.blocks)
        }
    }
    @Test fun largeSingleValuesAndNulSuffixDeniedBeforeMaterializing() {
        for(change in listOf("UPDATE tasks SET title=printf('%01000000d',0)",
            "UPDATE tasks SET sourceTemplateKey='x'||char(0)||printf('%01000000d',0)",
            "UPDATE task_events SET id=printf('%01000000d',0)")) H().use { h ->
            h.seed();h.exec(change);rejected(BoundedActiveStatus.BUDGET_REFUSED,h.read())
            assertEquals(0,h.db.payloadStatements);assertEquals(0,h.db.blobReads)
        }
    }
    @Test fun supplementaryUnicodeAndNulRemainComplete() = H().use { h ->
        h.seed();h.exec("UPDATE tasks SET title='x'||char(0)||char(129419),sourceTemplateKey=char(129419)||char(0)||'end'")
        val t=(h.read() as BoundedTaskResult.Value).board.tasks.single()
        assertEquals("x\u0000\uD83E\uDD8B",t.title);assertEquals("\uD83E\uDD8B\u0000end",t.sourceTemplateKey)
    }
    @Test fun brokenTaskAndEventReferencesFailBeforeAdmission() {
        for(change in listOf("UPDATE tasks SET assigneePersonId='missing'","UPDATE tasks SET affectedPersonId='missing'",
            "UPDATE tasks SET assigneePersonId=NULL,assigneeRoleId='missing'","UPDATE task_events SET taskId='missing'",
            "UPDATE task_events SET actionId='foreign'")) H().use { h ->
            h.seed();h.exec("PRAGMA foreign_keys=OFF");h.exec(change)
            // A foreign action's rows are deliberately outside this reader's scope.
            if(change.contains("actionId='foreign'")) assertTrue(h.read() is BoundedTaskResult.Value)
            else {rejected(BoundedActiveStatus.INVALID_DATA,h.read());assertEquals(0,h.db.payloadStatements)}
        }
    }
    @Test fun crossActionReferenceIsInvalidEvenWithExistingId() = H().use { h ->
        h.seed();h.exec("PRAGMA foreign_keys=OFF")
        h.exec("INSERT INTO actions VALUES('b','Other','Other',1,2,0,'ACTIVE','SENSITIVE',NULL)")
        h.exec("UPDATE tasks SET actionId='b'")
        rejected(BoundedActiveStatus.INVALID_DATA,h.read());assertEquals(0,h.db.payloadStatements)
    }
    @Test fun invalidEnumsTypesCompletionAndExclusiveAssigneeAreInvalidData() {
        for(change in listOf("UPDATE tasks SET status='BROKEN'","UPDATE tasks SET priority='BROKEN'",
            "UPDATE tasks SET completedAt=3","UPDATE tasks SET assigneeRoleId='r'",
            "UPDATE task_events SET type='BROKEN'","UPDATE task_events SET occurredAt=x'00'",
            "UPDATE tasks SET title=' '","UPDATE tasks SET title=' untrimmed '",
            "UPDATE tasks SET title=CAST(x'EDA080' AS TEXT)")) H().use { h ->
            h.seed();h.exec(change);rejected(BoundedActiveStatus.INVALID_DATA,h.read());assertEquals(1,h.blocks)
        }
    }
    @Test fun duplicateIdentityRejectedBySchemaButRepeatedEventTypesAndTimesPreserved() = H().use { h ->
        h.seed();assertThrows(IllegalStateException::class.java){h.exec("INSERT INTO task_events VALUES('e1','a','t1','CREATED',1)")}
        h.exec("INSERT INTO task_events VALUES('e2','a','t1','CREATED',1)")
        assertEquals(listOf("e1","e2"),(h.read() as BoundedTaskResult.Value).board.events.map{it.id})
    }
    @Test fun lastRepresentableBudgetAndNextByteRefusalBeforeAnotherPayloadQuery() = H().use { h ->
        h.seed(1,2)
        // Adding ASCII bytes to a nullable template costs 80 transition bytes each.
        h.exec("UPDATE tasks SET sourceTemplateKey='x'")
        val first=h.read() as BoundedTaskResult.Value
        val remaining=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-first.transitionCharge
        val pad=remaining/80
        h.exec("UPDATE tasks SET sourceTemplateKey=sourceTemplateKey||printf('%0${pad}d',0)")
        val near=h.read() as BoundedTaskResult.Value
        assertEquals(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-remaining%80,near.transitionCharge)
        val queries=h.db.payloadStatements
        h.exec("UPDATE tasks SET sourceTemplateKey=sourceTemplateKey||'x'")
        rejected(BoundedActiveStatus.BUDGET_REFUSED,h.read());assertEquals(queries,h.db.payloadStatements)
    }
    @Test fun reservationReplaysInSharedLedgerAndCannotAddTwoFreeBudgets() = H().use { h ->
        h.seed();val v=h.read() as BoundedTaskResult.Value
        val b=ActiveWorkspaceBudget();val retained=b.beginRetained()!!
        assertEquals(ActiveWorkspaceBudget.Decision.ADMITTED,b.admit(retained,v.reservation.retained))
        assertEquals(v.retainedCharge,b.accounting(retained)!!.chargedBytes)
        assertEquals(ActiveWorkspaceBudget.Decision.BUDGET_REFUSED,b.admit(retained,ActiveWorkspaceBudget.Units(objects=65536)))
        assertFalse(b.seal(retained));assertNull(b.beginTransition());b.close()
    }
    @Test fun countAndMaximumGuardPrecedeSqlSums() {
        for(huge in listOf(false,true)) H().use { h ->
            h.seed(if(huge)65537 else 1,0)
            if(!huge)h.exec("UPDATE tasks SET sourceTemplateKey=printf('%08388609d',0)")
            var sums=0;h.db.beforePrepare={if(it.contains("SUM(length"))sums++}
            rejected(BoundedActiveStatus.BUDGET_REFUSED,h.read());assertEquals(0,sums);assertEquals(0,h.db.payloadStatements)
        }
    }
    @Test fun revokeAtEveryBoundaryClosesStatementsAndReturnsNoPayload() {
        for(phase in BoundedTaskStore.Phase.entries) H().use { h ->
            h.seed();h.onPhase={if(it==phase)h.allowed=false}
            rejected(BoundedActiveStatus.ACCESS_REVOKED,h.read())
        }
    }
    @Test fun generationAndActionBindingChangesDuringReadDenyPayload() {
        for(kind in 0..2) H().use { h ->
            h.seed();h.onPhase={if(it==BoundedTaskStore.Phase.ROW)when(kind){0->h.generation++;1->h.bound=false;else->h.actionId="other"}}
            rejected(BoundedActiveStatus.ACCESS_REVOKED,h.read())
        }
        H().use{h->h.seed();rejected(BoundedActiveStatus.ACCESS_REVOKED,h.read(requestedGeneration=2));assertEquals(0,h.db.blobReads)}
    }
    @Test fun latePublicationAfterCleanupIsDenied() {
        for(kind in 0..2) H().use { h ->
            h.seed();h.onPublication={when(kind){0->h.allowed=false;1->h.generation++;else->h.actionId="other"}}
            rejected(BoundedActiveStatus.ACCESS_REVOKED,h.read())
            // Action identity change enters the central publish callback, whose inner binding gate denies payload.
            assertEquals(if(kind==2)1 else 0,h.publications);assertEquals(0,h.db.openStatements)
        }
    }
    @Test fun cleanupFailureOverridesSuccessAndBudgetRefusal() {
        for(large in listOf(false,true)) H().use { h ->
            h.seed(if(large)2000 else 1);h.onCleanup={error("synthetic cleanup")}
            rejected(BoundedActiveStatus.STORAGE_FAILURE,h.read());assertEquals(1,h.blocks);assertEquals(0,h.publications)
        }
    }
    @Test fun sqlFailureAndInvalidEncodingAreNotBudgetPressure() {
        H().use { h->h.seed();h.exec("DROP TABLE task_events");rejected(BoundedActiveStatus.STORAGE_FAILURE,h.read());assertEquals(0,h.db.payloadStatements) }
        H("UTF-16le").use { h->h.seed();rejected(BoundedActiveStatus.INVALID_DATA,h.read());assertEquals(0,h.db.payloadStatements) }
    }
    @Test fun budgetLongOverflowAndNegativeUnitsRemainDistinct() {
        for(units in listOf(ActiveWorkspaceBudget.Units(objects=Long.MAX_VALUE),ActiveWorkspaceBudget.Units(strings=1,utf16Units=Long.MAX_VALUE))) {
            val b=ActiveWorkspaceBudget();assertEquals(ActiveWorkspaceBudget.Decision.BUDGET_REFUSED,b.admit(b.beginRetained()!!,units));b.close()
        }
        val b=ActiveWorkspaceBudget();assertEquals(ActiveWorkspaceBudget.Decision.INVALID_DATA,b.admit(b.beginRetained()!!,ActiveWorkspaceBudget.Units(objects=-1)));b.close()
    }
}
