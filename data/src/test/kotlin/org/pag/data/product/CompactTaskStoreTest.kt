package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual SQLite/schema 11, no mocked SQL/results. Host bridge IPC is included in timings;
 * SQLCipher/preflight/native/Compose performance is deliberately NOT inferred from these.
 */
class CompactTaskStoreTest {
    private class Harness(people: Int=20, count: Int=40, history: Int=if(count>=1000)5 else 12) : AutoCloseable {
        val db=SqliteHostConnection()
        val binding=BoundedActiveBinding("active",1)
        var state: CompactTaskState?=CompactTaskState(binding,0)
        var allowed=true
        var publication=true
        var rejectAfterCallback=false
        var blocks=0
        var publications=0
        var clean=true
        var cleanupFailure=false
        var observer: (CompactTaskStore.Phase)->Unit = {}
        var elapsed=0L
        var admission=0L
        val expected=ArrayList<Task>()
        val c=object:StorageContext {
            override val scope=AccessScope.READY
            override val generation=1L
            override fun check(point:AccessPoint)=allowed
            override fun publish(result:()->Unit):Boolean {
                assertTrue(clean);assertEquals(0,db.openStatements);publications++
                if(!allowed || !publication) return false
                result();return !rejectAfterCallback
            }
        }
        fun exec(sql:String)=runBlocking { db.execute(sql) }
        fun literal(value:String?)=value?.let { "CAST(X'${it.toByteArray(Charsets.UTF_8).joinToString("") { b -> "%02x".format(b) }}' AS TEXT)" }?:"NULL"
        init {
            require(count.toLong()*history<=TaskRules.MAX_TASK_EVENTS_PER_ACTION)
            exec("INSERT INTO actions VALUES('active','Synthetic','Synthetic description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            exec("WITH RECURSIVE n(i) AS (VALUES(0) UNION ALL SELECT i+1 FROM n WHERE i+1<$people) INSERT INTO persons SELECT 'p-'||i,'active','Synthetic '||i,'synthetic '||i,1 FROM n")
            exec("INSERT INTO person_operational_states SELECT actionId,id,NULL,'NO_STATUS',NULL,1 FROM persons")
            exec("INSERT INTO roles VALUES('r','active','Synthetic role','synthetic role',1)")
            repeat(count) { i ->
                val status=when(i%5) {0->TaskStatus.OPEN;1->TaskStatus.IN_PROGRESS;3->TaskStatus.CANCELLED;else->TaskStatus.DONE}
                expected.add(Task("task-%04d".format(java.util.Locale.ROOT,i),"active",
                    if(i%7==0) "Ü🚩\u0000"+"x".repeat(116) else "Synthetic task $i",TaskPriority.entries[i%3],status,
                    if(i%2==0) "p-${i%people}" else null,if(i%2!=0) "r" else null,"p-${i%people}",
                    when(i%4) {0->null;1->Long.MIN_VALUE;else->(i%3).toLong()},"template",(i%3).toLong(),(i%4).toLong(),if(status==TaskStatus.DONE) 3 else null))
            }
            val rows=expected.joinToString { t -> "(${listOf(t.id,t.actionId,t.title,t.priority.name,t.status.name,t.assigneePersonId,t.assigneeRoleId,t.affectedPersonId).joinToString { literal(it) }},${t.dueAt?:"NULL"},${literal(t.sourceTemplateKey)},${t.createdAt},${t.updatedAt},${t.completedAt?:"NULL"})" }
            if(rows.isNotEmpty()) exec("INSERT INTO tasks VALUES $rows")
            // Legal lifecycle histories with fixed length and the requested final status.
            // OPEN prefixes combine 3-step start/complete/reopen and 2-step cancel/reopen cycles.
            require(history>=5)
            exec("WITH RECURSIVE n(i) AS (VALUES(0) UNION ALL SELECT i+1 FROM n WHERE i+1<$history), "+
                "history_rows AS (SELECT tasks.id AS taskId,status,i,$history-1-CASE status WHEN 'DONE' THEN 2 WHEN 'OPEN' THEN 0 ELSE 1 END AS prefix FROM tasks CROSS JOIN n) "+
                "INSERT INTO task_events SELECT taskId||'-e-'||i,'active',taskId,CASE WHEN i=0 THEN 'CREATED' "+
                "WHEN i<=prefix THEN CASE WHEN prefix%2=1 AND i<=3 THEN CASE i WHEN 1 THEN 'STARTED' WHEN 2 THEN 'COMPLETED' ELSE 'REOPENED' END "+
                "ELSE CASE (i-CASE WHEN prefix%2=1 THEN 3 ELSE 0 END)%2 WHEN 1 THEN 'CANCELLED' ELSE 'REOPENED' END END "+
                "ELSE CASE status WHEN 'DONE' THEN CASE WHEN i=prefix+1 THEN 'STARTED' ELSE 'COMPLETED' END WHEN 'IN_PROGRESS' THEN 'STARTED' ELSE 'CANCELLED' END END,i/3 "+
                "FROM history_rows ORDER BY taskId,i")
        }
        val current get()=checkNotNull(state)
        fun read(expectedState:CompactTaskState=current, operation:suspend (CompactTaskStore)->CompactTaskResult.Value):CompactTaskResult {
            val start=System.nanoTime();admission=0
            val result=compactTaskRead(expectedState,object:StorageAccess {override fun acquire(scope:AccessScope)=c.takeIf{allowed}},
                {state},{blocks++}) { context,current -> runBlocking {
                clean=false;db.execute("BEGIN")
                try { operation(CompactTaskStore(db,context,current) { phase ->
                    if(phase==CompactTaskStore.Phase.ADMITTED) admission=System.nanoTime()-start
                    observer(phase)
                }) } finally {
                    db.execute("ROLLBACK");assertEquals(0,db.openStatements);clean=true
                    if(cleanupFailure) error("SYNTHETIC_CLEANUP_FAILURE")
                }
            } }
            elapsed=System.nanoTime()-start
            return result
        }
        fun page(q:CompactTaskQuery=CompactTaskQuery(),after:CompactTaskCursor?=null,s:CompactTaskState=current)=read(s) { it.page(s,q,after) }
        fun detail(id:String,s:CompactTaskState=current)=read(s) { it.detail(s,id) }
        fun events(id:String,after:CompactEventCursor?=null,s:CompactTaskState=current)=read(s) { it.events(s,id,after) }
        fun value(r:CompactTaskResult)= (r.also { assertTrue("Expected admitted page, got $it",it is CompactTaskResult.Value) } as CompactTaskResult.Value)
        fun all(q:CompactTaskQuery):List<CompactTaskItem> {
            val result=ArrayList<CompactTaskItem>();var next:CompactTaskCursor?=null
            do {
                val page=value(page(q,next)).payload as CompactTaskPage
                assertTrue(page.items.size<=32)
                result.addAll(page.items);next=page.next
            } while(next!=null)
            assertEquals(result.size,result.map{it.id}.toSet().size)
            return result
        }
        override fun close() { assertEquals(0,db.openStatements);db.close() }
    }
    private fun counts(tasks:List<Task>)=CompactTaskCounts(tasks.count{it.status==TaskStatus.OPEN}.toLong(),tasks.count{it.status==TaskStatus.IN_PROGRESS}.toLong(),
        tasks.count{TaskRules.active(it.status) && it.priority==TaskPriority.HIGH}.toLong(),tasks.count{it.status==TaskStatus.DONE}.toLong(),tasks.count{it.status==TaskStatus.CANCELLED}.toLong())
    private fun assertDenied(reason:BoundedActiveStatus,r:CompactTaskResult)=assertEquals(CompactTaskResult.Rejected(reason),r)

    @org.junit.Ignore("Historical 129k special case; not a standard product fixture or §87a run")
    @Test fun concentrated129000StoredEventsRemainCompletelyTraversableWithoutAccumulation()=Harness(200,1000,5).use { h ->
        h.exec("DELETE FROM task_events")
        h.exec("UPDATE tasks SET status='OPEN',completedAt=NULL")
        h.exec("INSERT INTO task_events SELECT id||'-created','active',id,'CREATED',0 FROM tasks")
        h.exec("WITH RECURSIVE n(i) AS (VALUES(1) UNION ALL SELECT i+1 FROM n WHERE i<128000) INSERT INTO task_events SELECT 'stress-'||i,'active','task-0000',CASE WHEN i%2=1 THEN 'CANCELLED' ELSE 'REOPENED' END,(i+2)/3 FROM n")
        var cursor:CompactEventCursor?=null;var seen=0L;var pages=0;var lastTime=Long.MIN_VALUE
        do {
            val page=h.value(h.read {it.events(h.current,"task-0000",cursor,true)}).payload as CompactTaskEvents
            assertEquals(128001L,page.total);assertTrue(page.events.size in 1..32)
            for(event in page.events) {
                assertEquals(if(seen==0L)"task-0000-created" else "stress-$seen",event.id)
                assertTrue(event.occurredAt>=lastTime);lastTime=event.occurredAt;seen++
            }
            cursor=page.next;pages++
        } while(cursor!=null)
        assertEquals(128001L,seen);assertEquals(4001,pages);assertEquals(0,h.db.openStatements)
        println("P85a full concentrated traversal stored=129000 selected=128001 pages=$pages maxRows=32 noAccumulation=true")
    }
    @Test fun directPageEntryReachesMiddleAndEndWithoutPrefixMaterialization()=Harness(200,1000,5).use { h ->
        h.exec("DELETE FROM task_events");h.exec("UPDATE tasks SET status='OPEN',completedAt=NULL")
        h.exec("INSERT INTO task_events SELECT id||'-created','active',id,'CREATED',0 FROM tasks")
        h.exec("WITH RECURSIVE n(i) AS (VALUES(1) UNION ALL SELECT i+1 FROM n WHERE i<4000) INSERT INTO task_events SELECT 'stress-'||i,'active','task-0000',CASE WHEN i%2=1 THEN 'CANCELLED' ELSE 'REOPENED' END,(i+2)/3 FROM n")
        for((number,first,last) in listOf(Triple(126L,"stress-4000","stress-4000"),Triple(63L,"stress-1984","stress-2015"),Triple(1L,"task-0000-created","stress-31"))) {
            val before=h.db.blobReads
            val page=h.value(h.read {it.eventsAtPage(h.current,"task-0000",number,true)}).payload as CompactTaskEvents
            assertEquals(4001L,page.total);assertEquals(first,page.events.first().id);assertEquals(last,page.events.last().id)
            assertTrue(page.events.size<=32);assertEquals(page.events.size*4,h.db.blobReads-before)
            if(number==126L)assertNull(page.next) else assertNotNull(page.next)
        }
        val before=h.db.payloadStatements
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.read {it.eventsAtPage(h.current,"task-0000",0,true)})
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.read {it.eventsAtPage(h.current,"task-0000",127,true)})
        assertDenied(BoundedActiveStatus.BUDGET_REFUSED,h.read {it.eventsAtPage(h.current,"task-0000",Long.MAX_VALUE,true)})
        assertEquals(before,h.db.payloadStatements);assertEquals(0,h.db.openStatements)
        println("P85a direct entry pages=126,63,1 stored=5000 selected=4001 maxPayload=32")
    }
    @Test fun directAndSequentialHistoryMatchEveryTieOrderedRowWithOneTransactionPreflight()=Harness(history=97).use { h ->
        // Nonmonotonic insertion times and ties: the reference order is SQLite's full
        // occurredAt/rowid order, independent of either paging entry point.
        h.exec("UPDATE task_events SET occurredAt=(rowid*37)%11 WHERE taskId='task-0000'")
        val reference=runBlocking { h.db.usePrepared("SELECT id FROM task_events WHERE actionId='active' AND taskId='task-0000' ORDER BY occurredAt,rowid") { s ->
            buildList { while(s.step())add(s.getText(0)) }
        } }
        for(status in listOf("PREPARATION","ACTIVE","COMPLETED")) {
            h.exec("UPDATE actions SET status='$status'")
            var cursor:CompactEventCursor?=null
            for(number in 1L..4L) {
                val sequential=h.value(h.read { it.events(h.current,"task-0000",cursor,true) }).payload as CompactTaskEvents
                val queries=ArrayList<String>();h.db.beforePrepare={queries.add(it)}
                val direct=h.value(h.read { it.eventsAtPage(h.current,"task-0000",number,true) }).payload as CompactTaskEvents
                h.db.beforePrepare={}
                assertEquals(sequential,direct)
                assertEquals(reference.drop(((number-1)*32).toInt()).take(32),direct.events.map{it.id})
                assertEquals(97L,direct.total);assertTrue(direct.events.size<=32)
                assertEquals(1,queries.count{it=="PRAGMA encoding"})
                assertEquals(1,queries.count{it=="SELECT COUNT(*) FROM task_events WHERE actionId=? AND taskId=?"})
                cursor=sequential.next
            }
            assertNull(cursor)
        }
    }

    @Test fun directPageStillChecksOffPageInvalidEventsAndRevokeAfterAnchor()=Harness(history=97).use { h ->
        h.exec("UPDATE task_events SET type='BROKEN' WHERE id='task-0000-e-96'")
        assertDenied(BoundedActiveStatus.INVALID_DATA,h.read{it.eventsAtPage(h.current,"task-0000",2,true)})
        assertEquals(0,h.db.blobReads);assertEquals(0,h.publications)
        h.exec("UPDATE task_events SET type='REOPENED' WHERE id='task-0000-e-96'")
        h.db.beforePrepare={if(it=="SELECT COUNT(*) FROM tasks WHERE actionId=? AND id=?")h.allowed=false}
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.read{it.eventsAtPage(h.current,"task-0000",2,true)})
        assertEquals(0,h.db.blobReads);assertEquals(0,h.publications)
    }

    @Test fun directPageAnchorRevokeAndFailureNeverPublishOrPretendEmpty()=Harness(history=64).use { h ->
        h.db.beforePrepare={if(it.contains("LIMIT 1 OFFSET"))h.allowed=false}
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.read {it.eventsAtPage(h.current,"task-0000",2,true)})
        assertEquals(0,h.publications);assertEquals(0,h.db.payloadStatements)
        h.allowed=true;h.db.beforePrepare={if(it.contains("LIMIT 1 OFFSET"))error("HOST_ANCHOR_FAILURE")}
        assertDenied(BoundedActiveStatus.STORAGE_FAILURE,h.read {it.eventsAtPage(h.current,"task-0000",2,true)})
        assertEquals(0,h.publications);assertEquals(0,h.db.openStatements)
    }
    @Test fun fullTaskBoardsNeverFetchActionHistoryThrough5000Events() {
        for((people,tasks,events) in listOf(Triple(20,40,6),Triple(100,250,20),Triple(200,1000,5))) Harness(people,tasks,events).use { h ->
            val queries=ArrayList<String>();h.db.beforePrepare={queries.add(it)}
            val board=runBlocking { (TaskStore(HostTaskData(h.db),h.c){0}.board("active") as OperationsResult.Value).value }
            assertEquals(tasks,board.tasks.size);assertFalse(board.eventsComplete);assertTrue(board.events.isEmpty())
            assertFalse(queries.any{it.contains("task_events")})
            assertEquals(h.expected.toSet(),board.tasks.toSet())
            assertEquals(tasks.toLong()*events,runBlocking{h.db.usePrepared("SELECT COUNT(*) FROM task_events"){it.step();it.getLong(0)}})
            println("P85a board persons=$people tasks=$tasks storedEvents=${tasks*events} historyFetched=false")
            // Fully traverse a selected task, including tied timestamps and Unicode IDs/titles.
            var next:CompactEventCursor?=null;val seen=ArrayList<TaskEvent>()
            do {
                val page=h.value(h.read { it.events(h.current,"task-0000",next,fullTaskRead=true) }).payload as CompactTaskEvents
                assertEquals(events.toLong(),page.total);assertTrue(page.events.size<=32)
                seen.addAll(page.events);next=page.next
            } while(next!=null)
            assertEquals(events,seen.size);assertEquals(events,seen.map{it.id}.toSet().size)
            assertEquals((0 until events).map{"task-0000-e-$it"},seen.map{it.id})
            assertEquals(seen.sortedBy{it.occurredAt},seen)
            assertEquals(0,h.db.openStatements)
        }
    }
    @Test fun fullHistorySupportsPreparationCompletedAndExactPageEndsButCompactStaysActiveOnly()=Harness(history=32).use { h ->
        for(status in listOf("PREPARATION","ACTIVE","COMPLETED")) {
            h.exec("UPDATE actions SET status='$status'")
            val page=h.value(h.read { it.events(h.current,"task-0000",null,fullTaskRead=true) }).payload as CompactTaskEvents
            assertEquals(32,page.events.size);assertNull(page.next)
            if(status!="ACTIVE")assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.events("task-0000"))
        }
        h.exec("UPDATE actions SET status='DRAFT'")
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.read {it.events(h.current,"task-0000",null,fullTaskRead=true)})
    }
    @Test fun fullHistoryRevokeFailureAndBudgetNeverReturnFalseEmpty()=Harness(history=64).use { h ->
        h.observer={if(it==CompactTaskStore.Phase.ROW)h.allowed=false}
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.read {it.events(h.current,"task-0000",null,true)})
        h.allowed=true;h.observer={}
        h.exec("UPDATE task_events SET id=printf('%.*c',2200000,'x') WHERE rowid=1")
        assertDenied(BoundedActiveStatus.BUDGET_REFUSED,h.read {it.events(h.current,"task-0000",null,true)})
        h.exec("UPDATE task_events SET type='BROKEN' WHERE rowid=1")
        assertDenied(BoundedActiveStatus.INVALID_DATA,h.read {it.events(h.current,"task-0000",null,true)})
        assertEquals(0,h.publications);assertEquals(0,h.db.openStatements)
    }
    @Test fun realisticSizesCoverEveryTaskWithExactCountsFiltersAndLegacyPriorityOrder() {
        for((people,tasks,history) in listOf(Triple(20,40,12),Triple(100,250,20),Triple(200,1000,5))) Harness(people,tasks,history).use { h ->
            val global=counts(h.expected)
            for(person in listOf(null,"p-0","p-1","p-${people-1}","missing")) {
                val expected=h.expected.filter{person==null || it.affectedPersonId==person}
                for(lane in CompactTaskLane.entries) {
                    val q=CompactTaskQuery(lane,person)
                    val first=h.value(h.page(q)).payload as CompactTaskPage
                    assertEquals(global,first.overall);assertEquals(counts(expected),first.filtered)
                    val wanted=if(lane==CompactTaskLane.ACTIVE) expected.filter{TaskRules.active(it.status)}.sortedWith(TaskRules.order)
                        else expected.filterNot{TaskRules.active(it.status)}.sortedWith(compareByDescending<Task>{it.updatedAt}.thenBy{it.id})
                    val delivered=h.all(q)
                    assertEquals(wanted.map{it.id},delivered.map{it.id})
                    assertEquals(wanted.map{it.title},delivered.map{it.title})
                }
            }
            // Sample first/following page, full detail and one chronology page separately.
            val samples=linkedMapOf<String,MutableList<Long>>()
            val charges=linkedMapOf<String,String>()
            var retained=0L;var transition=0L
            repeat(6) { sample ->
                fun record(name:String,work:()->CompactTaskResult):CompactTaskResult.Value {
                    val v=h.value(work())
                    assertTrue(v.retainedCharge<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
                    assertTrue(v.transitionCharge<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
                    if(sample>0) samples.getOrPut(name){ArrayList()}.add(h.elapsed)
                    retained=maxOf(retained,v.retainedCharge);transition=maxOf(transition,v.transitionCharge)
                    charges[name]="${v.retainedCharge}/${v.transitionCharge}"
                    return v
                }
                val first=record("first_total") { h.page() }.payload as CompactTaskPage
                if(sample>0) {
                    samples.getOrPut("admission"){ArrayList()}.add(h.admission)
                    samples.getOrPut("first_materialize_publish"){ArrayList()}.add(h.elapsed-h.admission)
                }
                if(first.next!=null) record("following") { h.page(after=first.next) }
                record("detail") { h.detail(h.expected.first().id) }
                record("history_page") { h.events(h.expected.first().id) }
                val historyStart=System.nanoTime()
                var eventNext:CompactEventCursor?=null
                var delivered=0
                do {
                    val page=h.value(h.events(h.expected.first().id,eventNext)).payload as CompactTaskEvents
                    delivered+=page.events.size;eventNext=page.next
                } while(eventNext!=null)
                assertEquals(history,delivered)
                if(sample>0) samples.getOrPut("history_complete"){ArrayList()}.add(System.nanoTime()-historyStart)
            }
            println("COMPACT people=$people tasks=$tasks storedEvents=${tasks*history} retainedMax=$retained transitionMax=$transition "+
                samples.entries.joinToString { (name,values)-> "$name-median-ms=${values.sorted()[values.size/2]/1_000_000.0}" }+" charges-retained/transition=$charges")
            assertEquals(0,h.blocks)
        }
    }

    @Test fun exactPageMultiplesAndAllClosedCountsHaveExplicitTerminalPages()=Harness(20,64).use { h ->
        h.exec("UPDATE tasks SET status='DONE',completedAt=3,updatedAt=3")
        val empty=h.value(h.page()).payload as CompactTaskPage
        assertTrue(empty.items.isEmpty());assertNull(empty.next)
        assertEquals(64L,empty.overall.done);assertEquals(0L,empty.overall.urgent)
        val q=CompactTaskQuery(CompactTaskLane.CLOSED)
        val first=h.value(h.page(q)).payload as CompactTaskPage
        assertEquals(32,first.items.size);assertNotNull(first.next)
        val second=h.value(h.page(q,first.next)).payload as CompactTaskPage
        assertEquals(32,second.items.size);assertNotNull(second.next)
        val terminal=h.value(h.page(q,second.next)).payload as CompactTaskPage
        assertTrue(terminal.items.isEmpty());assertNull(terminal.next)
        assertEquals(h.expected.map{it.id}.sorted(),(first.items+second.items).map{it.id})
    }

    @Test fun affectedPersonFilterKeepsExactCountsAndCoverageAcrossSeveralPages()=Harness(20,250).use { h ->
        h.exec("UPDATE tasks SET affectedPersonId='p-0'")
        for(lane in CompactTaskLane.entries) {
            val query=CompactTaskQuery(lane,"p-0")
            val first=h.value(h.page(query)).payload as CompactTaskPage
            assertEquals(first.overall,first.filtered);assertNotNull(first.next)
            val filtered=h.all(query)
            assertEquals(h.all(CompactTaskQuery(lane)).map{it.id},filtered.map{it.id})
            assertTrue(filtered.all{it.affectedPersonId=="p-0"})
        }
        val noAffected=h.value(h.page(CompactTaskQuery(affectedPersonId="p-2"))).payload as CompactTaskPage
        assertEquals(0L,noAffected.filtered.total);assertTrue(noAffected.items.isEmpty())
    }

    @Test fun maximumUnicodeTitlesAndNulProvenanceArePreservedWithoutTruncation()=Harness().use { h ->
        val title="🚩".repeat(120)
        val provenance="Synthetic\u0000🚩ü"
        h.exec("UPDATE tasks SET title=${h.literal(title)},sourceTemplateKey=${h.literal(provenance)} WHERE id='task-0000'")
        val page=h.value(h.page()).payload as CompactTaskPage
        assertEquals(title,page.items.single{it.id=="task-0000"}.title)
        val detail=(h.value(h.detail("task-0000")).payload as CompactTaskDetail).task
        assertEquals(title,detail.title);assertEquals(provenance,detail.sourceTemplateKey)
    }

    @Test fun selectedDetailIsCompleteAndLongHistoryHasExactTieOrderedCoverage()=Harness(200,20,128).use { h ->
        for(task in h.expected.take(4)) {
            assertEquals(task,(h.value(h.detail(task.id)).payload as CompactTaskDetail).task)
            var next:CompactEventCursor?=null
            val events=ArrayList<TaskEvent>()
            do {
                val page=h.value(h.events(task.id,next)).payload as CompactTaskEvents
                assertEquals(128L,page.total);assertTrue(page.events.size<=32)
                events.addAll(page.events);next=page.next
            } while(next!=null)
            assertEquals((0..127).map { "${task.id}-e-$it" },events.map{it.id})
            assertEquals(events.size,events.map{it.id}.toSet().size)
            assertEquals((0..127).map{(it/3).toLong()},events.map{it.occurredAt})
            assertEquals(TaskEventType.CREATED,events.first().type)
            var status=TaskStatus.OPEN
            for(event in events.drop(1)) {
                val target=when(event.type) {
                    TaskEventType.STARTED->TaskStatus.IN_PROGRESS
                    TaskEventType.COMPLETED->TaskStatus.DONE
                    TaskEventType.CANCELLED->TaskStatus.CANCELLED
                    TaskEventType.REOPENED->TaskStatus.OPEN
                    TaskEventType.CREATED->error("Duplicate creation")
                }
                assertEquals(event.type,TaskRules.event(status,target));status=target
            }
            assertEquals(task.status,status)
        }
    }

    @Test fun summaryDoesNotQueryHistoryOrFetchTemplateAndOversizeDetailRefusesBeforeBlob()=Harness().use { h ->
        h.exec("UPDATE tasks SET sourceTemplateKey=replace(printf('%0200000d',0),'0','x')")
        val queries=ArrayList<String>();h.db.beforePrepare={queries.add(it)}
        h.value(h.page())
        assertFalse(queries.any{it.contains("task_events")})
        assertFalse(queries.any{it.contains("CAST(sourceTemplateKey AS BLOB)")})
        val blobs=h.db.blobReads
        assertDenied(BoundedActiveStatus.BUDGET_REFUSED,h.detail(h.expected.first().id))
        assertEquals(blobs,h.db.blobReads);assertEquals(0,h.blocks)
        h.exec("UPDATE tasks SET sourceTemplateKey=replace(printf('%09000000d',0),'0','x')")
        assertDenied(BoundedActiveStatus.BUDGET_REFUSED,h.detail(h.expected.first().id))
        assertEquals(blobs,h.db.blobReads)
    }

    @Test fun exactDetailBudgetBoundaryAndOneByteBeyondAreControlled()=Harness().use { h ->
        h.exec("UPDATE tasks SET sourceTemplateKey='' WHERE id='task-0000'")
        val base=h.value(h.detail("task-0000"))
        val capacity=(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-base.transitionCharge)/80
        h.exec("UPDATE tasks SET sourceTemplateKey=replace(printf('%0${capacity}d',0),'0','x') WHERE id='task-0000'")
        val edge=h.value(h.detail("task-0000"))
        assertEquals(base.transitionCharge+capacity*80,edge.transitionCharge)
        assertEquals(capacity.toInt(),(edge.payload as CompactTaskDetail).task.sourceTemplateKey!!.length)
        val blobs=h.db.blobReads
        h.exec("UPDATE tasks SET sourceTemplateKey=sourceTemplateKey||'x' WHERE id='task-0000'")
        assertDenied(BoundedActiveStatus.BUDGET_REFUSED,h.detail("task-0000"))
        assertEquals(blobs,h.db.blobReads);assertEquals(0,h.blocks)
        println("COMPACT detail-boundary templateBytes=$capacity retained=${edge.retainedCharge} transition=${edge.transitionCharge}; nextByte=REFUSED")
    }

    @Test fun unicodeAndNulIdsKeepExactUtf16TaskRulesOrderAcrossPageBoundaries()=Harness().use { h ->
        h.exec("DELETE FROM task_events");h.exec("DELETE FROM tasks")
        val ids=(0..70).map { "same-"+(when(it%4){0->"\uE000";1->"🚩";2->"\u0000";else->"ä"})+"%03d".format(java.util.Locale.ROOT,it) }
        for(id in ids) h.exec("INSERT INTO tasks VALUES(${h.literal(id)},'active','Unicode 🚩','HIGH','OPEN',NULL,NULL,NULL,1,NULL,1,1,NULL)")
        assertEquals(ids.sorted(),h.all(CompactTaskQuery()).map{it.id})
    }

    @Test fun malformedTypesEnumsTitlesAndUtf8NeverPublishContent() {
        for(mutation in listOf("status='BROKEN'","priority='BROKEN'","title=''","title=' trailing '","title=replace(printf('%0121d',0),'0','x')", "title=CAST(X'80' AS TEXT)","dueAt='broken'","completedAt=2")) Harness().use { h ->
            h.exec("UPDATE tasks SET $mutation WHERE id='task-0000'")
            assertDenied(BoundedActiveStatus.INVALID_DATA,h.page());assertEquals(0,h.publications);assertEquals(1,h.blocks)
        }
    }

    @Test fun invalidTitlesOrUtf8OnLaterPagesInvalidateCountsBeforeAnyContent() {
        for(expression in listOf("title=''","title=CAST(X'7800' AS TEXT)||replace(printf('%0120d',0),'0','x')",
            "title=CAST(X'EDA080' AS TEXT)","title=CAST(X'F0808080' AS TEXT)","title='\u2000bad'","title='bad\u00a0'",
            "id=CAST(X'80' AS TEXT)")) Harness(200,1000).use { h ->
            h.exec("PRAGMA foreign_keys=OFF")
            h.exec("UPDATE tasks SET $expression WHERE id='task-0999'")
            assertDenied(BoundedActiveStatus.INVALID_DATA,h.page())
            assertEquals(0,h.publications);assertEquals(0,h.db.blobReads)
        }
    }

    @Test fun crossActionRelationsAndMutuallyExclusiveAssigneesFailClosed() {
        for(change in listOf("affectedPersonId='foreign'","assigneePersonId='foreign'","assigneeRoleId='foreign'","assigneeRoleId='r'")) Harness().use { h ->
            h.exec("PRAGMA foreign_keys=OFF")
            h.exec("INSERT INTO actions VALUES('other','Synthetic','Synthetic description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            h.exec("INSERT INTO persons VALUES('foreign','other','Synthetic','synthetic',1)")
            h.exec("INSERT INTO roles VALUES('foreign','other','Synthetic','synthetic',1)")
            h.exec("UPDATE tasks SET $change WHERE id='task-0000'")
            assertDenied(BoundedActiveStatus.INVALID_DATA,h.page());assertEquals(0,h.publications)
        }
    }

    @Test fun oversizedPageTextsAndIdentifiersRefuseBeforePayloadAndExactBudgetNeverGrows()=Harness().use { h ->
        h.exec("UPDATE tasks SET title=replace(printf('%0200000d',0),'0','x')")
        val blobs=h.db.blobReads
        assertDenied(BoundedActiveStatus.BUDGET_REFUSED,h.page());assertEquals(blobs,h.db.blobReads)
        h.exec("UPDATE tasks SET title='Synthetic',affectedPersonId=NULL")
        h.exec("INSERT INTO tasks VALUES(replace(printf('%0257d',0),'0','x'),'active','Synthetic','NORMAL','OPEN',NULL,NULL,NULL,NULL,NULL,1,1,NULL)")
        assertDenied(BoundedActiveStatus.BUDGET_REFUSED,h.page());assertEquals(blobs,h.db.blobReads)
        assertEquals(8388608L,ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
    }

    @Test fun keysetStateQueryAndPermissionGenerationChangesRejectContinuation()=Harness(100,250).use { h ->
        val closed=CompactTaskQuery(CompactTaskLane.CLOSED)
        val first=h.value(h.page(closed)).payload as CompactTaskPage
        assertNotNull(first.next)
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page(CompactTaskQuery(),first.next))
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page(closed,first.next!!.copy(id="missing")))
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page(closed,first.next!!.copy(updatedAt=999)))
        val old=h.current
        for(next in listOf(old.copy(mutationEpoch=1),old.copy(binding=old.binding.copy(generation=2)),old.copy(binding=old.binding.copy(actionId="other")))) {
            h.state=next
            assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page(closed,first.next,old))
        }
        h.state=null
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page(closed,first.next,old))
    }

    @Test fun mutationsRevokeUnknownAndPublicationDenialAtEveryPhasePublishNoPartialPayload() {
        for(phase in CompactTaskStore.Phase.entries) for(kind in listOf("mutation","revoke","unknown")) Harness().use { h ->
            val old=h.current
            h.observer={ if(it==phase) when(kind) {
                "mutation"->h.state=old.copy(mutationEpoch=1)
                "revoke"->h.allowed=false
                else->h.state=null
            } }
            assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page(s=old));assertEquals(0,h.publications)
        }
        Harness().use { h ->
            h.publication=false
            assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page());assertEquals(1,h.publications);assertEquals(0,h.blocks)
            h.publication=true;h.rejectAfterCallback=true
            assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page());assertEquals(2,h.publications);assertEquals(0,h.blocks)
        }
    }

    @Test fun interruptedPagesCannotResumeAfterActualMutationAndDetailsHistoryRevokeMidRead()=Harness(100,250,20).use { h ->
        val old=h.current
        val first=h.value(h.page()).payload as CompactTaskPage
        assertNotNull(first.next)
        h.state=old.copy(mutationEpoch=1)
        h.exec("UPDATE tasks SET status='DONE',completedAt=3 WHERE id='task-0000'")
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page(after=first.next,s=old))
        val replacement=h.value(h.page()).payload as CompactTaskPage
        assertEquals(first.overall.open-1,replacement.overall.open)
        assertEquals(first.overall.done+1,replacement.overall.done)
        for(detail in listOf(true,false)) {
            h.allowed=true
            val before=h.publications
            h.observer={if(it==CompactTaskStore.Phase.ROW)h.allowed=false}
            assertDenied(BoundedActiveStatus.ACCESS_REVOKED,if(detail)h.detail("task-0000") else h.events("task-0000"))
            assertEquals(before,h.publications)
        }
    }

    @Test fun interruptionsAndCleanupStorageErrorsRemainRejectedAndAreNotRetried()=Harness().use { h ->
        h.cleanupFailure=true
        assertDenied(BoundedActiveStatus.STORAGE_FAILURE,h.page());assertEquals(1,h.blocks);assertEquals(0,h.publications)
        h.cleanupFailure=false
        h.db.beforePrepare={if(it.startsWith("SELECT rowid,")) error("SYNTHETIC_STORAGE_FAILURE")}
        assertDenied(BoundedActiveStatus.STORAGE_FAILURE,h.page());assertEquals(2,h.blocks);assertEquals(0,h.publications)
    }

    @Test fun historyCursorCannotCrossTaskStateOrActionAndInvalidEventsFailClosed()=Harness().use { h ->
        val s=h.current
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.events("task-0000",CompactEventCursor(s,"task-0001",0,1)))
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.events("missing"))
        assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.events("task-0000",CompactEventCursor(s,"task-0000",0,Long.MAX_VALUE)))
        h.exec("UPDATE task_events SET type='BROKEN' WHERE taskId='task-0000'")
        assertDenied(BoundedActiveStatus.INVALID_DATA,h.events("task-0000"));assertEquals(0,h.publications)
    }

    @Test fun nonActiveMissingAndWrongEncodingFailClosed() {
        for(status in listOf("PREPARATION","COMPLETED","DRAFT","DELETION_PENDING")) Harness().use { h ->
            h.exec("UPDATE actions SET status='$status'")
            assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page());assertEquals(0,h.db.blobReads)
        }
        Harness().use { h ->
            h.exec("DELETE FROM actions")
            assertDenied(BoundedActiveStatus.ACCESS_REVOKED,h.page())
        }
        SqliteHostConnection("UTF-16le").use { db ->
            val c=object:StorageContext {
                override val scope=AccessScope.READY;override val generation=1L
                override fun check(point:AccessPoint)=true
                override fun publish(result:()->Unit):Boolean { result();return true }
            }
            try { runBlocking { CompactTaskStore(db,c,{true}).page(CompactTaskState(BoundedActiveBinding("a",1),0),CompactTaskQuery(),null) };fail() }
            catch(f:BoundedActiveRejected) {assertEquals(BoundedActiveStatus.INVALID_DATA,f.status)}
        }
    }
}
