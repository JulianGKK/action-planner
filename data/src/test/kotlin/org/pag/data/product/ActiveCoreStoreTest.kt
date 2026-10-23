package org.pag.data.product

import androidx.room3.PooledConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Real SQLite/schema 11. Trusted authority/resource-lifecycle simulations, not UI authority,
 * not actual Android SQLCipher, Room-close, key-lease or native-memory proof.
 */
class ActiveCoreStoreTest {
    internal class H(val people: Int = 20, val taskCount: Int = 40, val history: Int = if(taskCount>=1000)5 else 12,
        val roleCount: Int = 6) : AutoCloseable {
        val db = SqliteHostConnection()
        val requested = CompactTaskState(P3bFreeActiveCoreBinding("a",1),0)
        val authority = AtomicReference<CompactTaskState?>(requested)
        val ready = AtomicBoolean(true)
        var blocked = 0
        var publicationAttempts = 0
        var publicationAllowed = true
        var publicationAccepted = true
        var handleOpen = false
        var leaseOpen = false
        var elapsed = 0L
        var phase: (ActiveCoreStore.Phase) -> Unit = {}
        var afterDatabaseCleanup: () -> Unit = {}
        var afterLeaseCleanup: () -> Unit = {}
        var beforePublication: () -> Unit = {}
        var afterPublication: () -> Unit = {}
        val phases = ArrayList<ActiveCoreStore.Phase>()
        val metrics = linkedMapOf<ActiveCoreStore.Metric,Long>()
        var admission: CoreReadAdmission.Report? = null
        val queries = ArrayList<String>()
        val expected = ArrayList<Task>()
        val context = object : StorageContext {
            override val scope = AccessScope.READY
            override val generation = 1L
            override fun check(point: AccessPoint) = ready.get()
            override fun publish(result: () -> Unit): Boolean {
                publicationAttempts++
                assertFalse(handleOpen); assertFalse(leaseOpen); assertEquals(0,db.openStatements)
                beforePublication()
                if (!check(AccessPoint.PUBLICATION) || !publicationAllowed) return false
                result(); afterPublication(); return publicationAccepted
            }
        }
        fun exec(sql: String) = runBlocking { db.execute(sql) }
        init {
            require(history>=5 && taskCount.toLong()*history<=TaskRules.MAX_TASK_EVENTS_PER_ACTION)
            exec("INSERT INTO actions VALUES('a','Synthetic action','Synthetic description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            exec("WITH RECURSIVE n(i) AS (VALUES(0) UNION ALL SELECT i+1 FROM n WHERE i+1<$roleCount) INSERT INTO roles SELECT 'r'||i,'a','Synthetic role '||i,'synthetic role '||i,i FROM n")
            exec("WITH RECURSIVE n(i) AS (VALUES(0) UNION ALL SELECT i+1 FROM n WHERE i+1<$people) INSERT INTO persons SELECT 'p'||i,'a','Synthetic person '||i,'synthetic person '||i,i FROM n")
            exec("INSERT INTO person_planned_roles SELECT 'a',id,'r'||(createdAt%$roleCount) FROM persons")
            exec("INSERT INTO person_planned_roles SELECT 'a',id,'r'||((createdAt+1)%$roleCount) FROM persons")
            exec("INSERT INTO person_role_plans SELECT 'a',id,'r'||(createdAt%$roleCount) FROM persons")
            exec("INSERT INTO person_operational_states SELECT 'a',id,'r'||(createdAt%$roleCount),'ON_SITE',NULL,1 FROM persons")
            repeat(taskCount) { i ->
                val status = when(i%5) { 0 -> TaskStatus.OPEN; 1 -> TaskStatus.IN_PROGRESS; 3 -> TaskStatus.CANCELLED; else -> TaskStatus.DONE }
                expected.add(Task("t%04d".format(java.util.Locale.ROOT,i),"a","Synthetic task $i",TaskPriority.entries[i%3],status,
                    if(i%2==0) "p${i%people}" else null,if(i%2==1) "r${i%roleCount}" else null,"p${i%people}",
                    if(i%4==0) null else (i%3).toLong(),"template",(i%3).toLong(),(i%4).toLong(),if(status==TaskStatus.DONE) 3 else null))
            }
            exec("INSERT INTO tasks VALUES " + expected.joinToString { t ->
                fun q(s: String?) = s?.let { "'$it'" } ?: "NULL"
                "(${listOf(t.id,t.actionId,t.title,t.priority.name,t.status.name,t.assigneePersonId,t.assigneeRoleId,t.affectedPersonId).joinToString { q(it) }},${t.dueAt?:"NULL"},'template',${t.createdAt},${t.updatedAt},${t.completedAt?:"NULL"})"
            })
            // Complete legal type/lifecycle histories, preserving all event IDs and tie order.
            exec("WITH RECURSIVE n(i) AS (VALUES(0) UNION ALL SELECT i+1 FROM n WHERE i+1<$history), "+
                "h AS (SELECT id AS taskId,status,i,$history-1-CASE status WHEN 'DONE' THEN 2 WHEN 'OPEN' THEN 0 ELSE 1 END AS prefix FROM tasks CROSS JOIN n) "+
                "INSERT INTO task_events SELECT taskId||'-e-'||i,'a',taskId,CASE WHEN i=0 THEN 'CREATED' "+
                "WHEN i<=prefix THEN CASE WHEN prefix%2=1 AND i<=3 THEN CASE i WHEN 1 THEN 'STARTED' WHEN 2 THEN 'COMPLETED' ELSE 'REOPENED' END "+
                "ELSE CASE (i-CASE WHEN prefix%2=1 THEN 3 ELSE 0 END)%2 WHEN 1 THEN 'CANCELLED' ELSE 'REOPENED' END END "+
                "ELSE CASE status WHEN 'DONE' THEN CASE WHEN i=prefix+1 THEN 'STARTED' ELSE 'COMPLETED' END WHEN 'IN_PROGRESS' THEN 'STARTED' ELSE 'CANCELLED' END END,i/3 FROM h ORDER BY taskId,i")
            runBlocking { db.usePrepared("SELECT COUNT(*) FROM task_events") { s ->
                assertTrue(s.step());assertEquals(taskCount.toLong()*history,s.getLong(0));assertFalse(s.step())
            } }
            db.beforePrepare = { statement ->
                queries.add(statement)
                if (statement.startsWith("SELECT rowid,")) assertTrue("No payload before JOINT admission",ActiveCoreStore.Phase.ADMITTED in phases)
            }
        }
        fun read(person: String? = null, connection: PooledConnection = db,
            outstanding:((ActiveCoreReservation,Long,Long)->Unit)?=null): ActiveCoreResult {
            phases.clear();metrics.clear();queries.clear();admission=null
            val start = System.nanoTime()
            val result = activeCoreRead(requested, object : StorageAccess {
                override fun acquire(scope: AccessScope) = context.takeIf { scope==AccessScope.READY && ready.get() }
            }, authority::get, { blocked++ }) { c,current ->
                leaseOpen=true;handleOpen=true
                try {
                    runBlocking(Dispatchers.Unconfined) {
                        db.execute("BEGIN")
                        try {
                            ActiveCoreStore(connection,c,current,{
                                phases.add(it)
                                if(it==ActiveCoreStore.Phase.METADATA || it==ActiveCoreStore.Phase.ADMITTED)
                                    assertEquals(0,db.blobReads - blobsAtStart)
                                phase(it)
                            }, { metric,nanos -> metrics[metric]=nanos }, { admission=it },outstanding).read(requested,person)
                        } finally {
                            db.execute("ROLLBACK");handleOpen=false
                            assertEquals(0,db.openStatements);afterDatabaseCleanup()
                        }
                    }
                } finally { leaseOpen=false;afterLeaseCleanup() }
            }
            elapsed=System.nanoTime()-start
            blobsAtStart=db.blobReads
            return result
        }
        fun peopleRead(filter: CompactPeopleQuery = CompactPeopleQuery.All, after: CompactPeopleCursor? = null,
            detail: String? = null): CompactPeopleResult {
            phases.clear();queries.clear()
            val result=boundedProjectionRead<CompactPeopleResult, CompactPeopleResult.Value>(requested,object:StorageAccess {
                override fun acquire(scope:AccessScope)=context.takeIf{scope==AccessScope.READY && ready.get()}
            },authority::get,{blocked++},{CompactPeopleResult.Rejected(it)}) { c,current ->
                leaseOpen=true;handleOpen=true
                try { runBlocking {
                    db.execute("BEGIN")
                    try {
                        val store=CompactPeopleStore(db,c,current) {
                            when(it) {
                                CompactPeopleStore.Phase.METADATA->phase(ActiveCoreStore.Phase.METADATA)
                                CompactPeopleStore.Phase.ADMITTED->{phases.add(ActiveCoreStore.Phase.ADMITTED);phase(ActiveCoreStore.Phase.ADMITTED)}
                                CompactPeopleStore.Phase.ROW->phase(ActiveCoreStore.Phase.PEOPLE_ROW)
                                CompactPeopleStore.Phase.COMPLETE->phase(ActiveCoreStore.Phase.COMPLETE)
                            }
                        }
                        if(detail==null) store.page(requested,filter,after) else store.detail(requested,detail)
                    } finally {db.execute("ROLLBACK");handleOpen=false;afterDatabaseCleanup()}
                } } finally {leaseOpen=false;afterLeaseCleanup()}
            }
            blobsAtStart=db.blobReads
            return result
        }
        private var blobsAtStart = 0
        fun metadata(): Pair<ActiveCoreReservation,ActiveCoreReservation> = runBlocking {
            db.execute("BEGIN")
            try {
                CompactPeopleStore(db,context,{true}).prepareCore(requested).reservation to
                    CompactTaskStore(db,context,{true}).preparePage(requested,CompactTaskQuery(),null).reservation
            } finally { db.execute("ROLLBACK") }
        }
        override fun close() { assertFalse(handleOpen);assertFalse(leaseOpen);assertEquals(0,db.openStatements);db.close() }
    }
    private fun denied(reason: BoundedActiveStatus, result: ActiveCoreResult) = assertEquals(ActiveCoreResult.Rejected(reason),result)
    private fun cost(u: ActiveWorkspaceBudget.Units) = ActiveWorkspaceBudget.estimateUnits(u).also {
        assertEquals(ActiveWorkspaceBudget.Decision.ADMITTED,it.first)
    }.second
    private fun counts(tasks: List<Task>) = CompactTaskCounts(tasks.count{it.status==TaskStatus.OPEN}.toLong(),
        tasks.count{it.status==TaskStatus.IN_PROGRESS}.toLong(),tasks.count{TaskRules.active(it.status) && it.priority==TaskPriority.HIGH}.toLong(),
        tasks.count{it.status==TaskStatus.DONE}.toLong(),tasks.count{it.status==TaskStatus.CANCELLED}.toLong())
    private fun assertCore(h: H, result: ActiveCoreResult, person: String? = null) {
        assertTrue(result is ActiveCoreResult.Value)
        val v=result as ActiveCoreResult.Value
        assertEquals(h.requested,v.core.state);assertEquals(h.requested,v.core.tasks.state)
        assertEquals(Action("a","Synthetic action","Synthetic description",1,2,0,ActionStatus.ACTIVE),v.core.action)
        val page=v.core.people
        val functions=v.core.functions
        assertEquals(h.people.toLong(),page.overall.total)
        assertEquals(h.people.toLong(),page.overall.onSite)
        assertEquals(h.roleCount,functions.items.size)
        assertEquals((0 until h.roleCount).map{Role("r$it","a","Synthetic role $it",it.toLong())},functions.items.map{it.role})
        assertEquals((0 until minOf(h.people,32)).map{"p$it"},page.items.map{it.id})
        for((i,p) in page.items.withIndex()) {
            assertEquals("Synthetic person $i",p.pseudonym)
            assertEquals("r${i%h.roleCount}",p.currentRoleId)
            assertEquals("Synthetic role ${i%h.roleCount}",p.currentRoleName)
            assertEquals(OperationalStatus.ON_SITE,p.status)
            assertEquals(h.expected.count{it.affectedPersonId==p.id && TaskRules.active(it.status)}.toLong(),p.affectedActiveTasks)
        }
        assertEquals(h.people.toLong(),functions.items.sumOf{it.onSiteCount})
        assertEquals(0L,functions.withoutFunction)
        assertEquals(counts(h.expected),v.core.tasks.overall)
        val filtered=h.expected.filter{person==null || it.affectedPersonId==person}
        assertEquals(counts(filtered),v.core.tasks.filtered)
        assertEquals(filtered.filter{TaskRules.active(it.status)}.sortedWith(TaskRules.order).take(32).map{it.id},v.core.tasks.items.map{it.id})
        assertTrue(v.core.tasks.items.size<=32)
        assertTrue(v.retainedCharge<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES);assertTrue(v.transitionCharge<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
        assertEquals(cost(v.reservation.retained),v.retainedCharge);assertEquals(cost(v.reservation.transition),v.transitionCharge)
        assertEquals(1,h.queries.count { it.startsWith("SELECT rowid,") && it.contains(" FROM actions ") })
        assertFalse(h.queries.any { it.contains("task_events") })
        assertFalse(h.queries.any { it.contains("CAST(sourceTemplateKey AS BLOB)") })
        assertFalse(h.queries.any { it.startsWith("SELECT rowid,") &&
            (it.contains(" FROM person_planned_roles ") || it.contains(" FROM person_role_plans ")) })
    }

    @Test fun realisticCombinedSizesAndMedianP95HaveNoPreAdmissionPayloadOrEventPrefetch() {
        for((people,tasks,events) in listOf(Triple(20,40,12),Triple(100,250,20),Triple(200,1000,5))) H(people,tasks,events).use { h ->
            val (p,t)=h.metadata()
            val expectedR=cost(p.retained)+cost(t.retained)+cost(ActiveCoreStore.envelope.retained)
            val expectedT=cost(p.transition)+cost(t.transition)+cost(ActiveCoreStore.envelope.transition)
            val admitted=expectedR<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES && expectedT<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
            val samples=linkedMapOf<String,MutableList<Long>>()
            repeat(22) { sample ->
                val result=h.read()
                if(admitted) assertCore(h,result) else {
                    denied(BoundedActiveStatus.BUDGET_REFUSED,result)
                    assertEquals(0,h.db.blobReads);assertEquals(0,h.publicationAttempts)
                    assertFalse(ActiveCoreStore.Phase.PEOPLE_ROW in h.phases)
                    assertFalse(ActiveCoreStore.Phase.TASK_ROW in h.phases)
                }
                assertEquals(expectedR,h.admission!!.retained);assertEquals(expectedT,h.admission!!.transition)
                if(sample>=2) {
                    for((metric,nanos) in h.metrics) samples.getOrPut(metric.name){ArrayList()}.add(nanos)
                    samples.getOrPut("TOTAL_WITH_CLEANUP_PUBLICATION"){ArrayList()}.add(h.elapsed)
                }
            }
            fun stats(v: List<Long>): String {
                val sorted=v.sorted()
                val median=(sorted[9]+sorted[10])/2.0/1_000_000
                val p95=sorted[18]/1_000_000.0
                return "$median/$p95"
            }
            println("CORE people=$people roles=${h.roleCount} tasks=$tasks events=${tasks*events} admitted=$admitted "+
                "peopleRows=${p.transition.objects/128-16} peopleFields=${p.transition.strings/16-16} peopleUtf8Bytes=${p.transition.utf16Units/16-16384} "+
                "peopleR=${cost(p.retained)} peopleT=${cost(p.transition)} taskR=${cost(t.retained)} taskT=${cost(t.transition)} jointR=$expectedR jointT=$expectedT "+
                samples.entries.joinToString { (name,values)->"$name-ms-median/p95=${stats(values)}" })
            assertEquals(0,h.blocked)
        }
    }

    @Test fun filteredCountsUseWholeTaskSetAndPeopleRolesAreComplete()=H(20,250).use { h ->
        for(person in listOf(null,"p0","p1","p19","missing")) assertCore(h,h.read(person),person)
        val all=h.read() as ActiveCoreResult.Value
        assertEquals(32,all.core.tasks.items.size);assertTrue(all.core.tasks.hasMore)
        assertEquals(250L,all.core.tasks.overall.total)
    }

    @Test fun twoIndividuallyFittingRealProjectionsAreJointlyRefusedBeforeAnyPayload()=H(35,250).use { h ->
        val (before,tasks)=h.metadata()
        val target=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-cost(tasks.transition)/2
        val padding=(target-cost(before.transition))/80
        h.exec("UPDATE actions SET description=description||printf('%0${padding}d',0)")
        val (p,t)=h.metadata()
        for(part in listOf(p,t)) {
            assertTrue(cost(part.retained)<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
            assertTrue(cost(part.transition)<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
        }
        denied(BoundedActiveStatus.BUDGET_REFUSED,h.read())
        assertTrue(h.admission!!.transition>ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
        assertEquals(0,h.db.blobReads);assertEquals(0,h.db.payloadStatements);assertEquals(0,h.blocked)
        println("CORE separate-fit joint-refusal retained=${h.admission!!.retained} transition=${h.admission!!.transition}")
    }

    @Test fun preparationAloneNeverMaterializesAndAccountingHasNoDuplicateAction()=H().use { h ->
        val (p,t)=h.metadata()
        assertEquals(0,h.db.blobReads);assertEquals(0,h.db.payloadStatements)
        val result=h.read() as ActiveCoreResult.Value
        // Joint wrappers plus bounded original-ticket lifetime/owner metadata; Action still counted once.
        assertEquals(cost(p.retained)+cost(t.retained)+3872,result.retainedCharge)
        assertEquals(cost(p.transition)+cost(t.transition)+7808,result.transitionCharge)
        assertCore(h,result)
    }

    @Test fun lastRepresentableCombinedByteBoundaryRefusesBeforeEitherMaterializer()=H().use { h ->
        val baseline=h.read() as ActiveCoreResult.Value
        val difference=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-baseline.transitionCharge
        val padding=difference/80
        h.exec("UPDATE actions SET description=description||printf('%0${padding}d',0)")
        val exact=h.read() as ActiveCoreResult.Value
        assertEquals(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-difference%80,exact.transitionCharge)
        assertEquals(baseline.retainedCharge+padding*20,exact.retainedCharge)
        assertEquals(baseline.core.action.description.length+padding.toInt(),exact.core.action.description.length)
        val blobs=h.db.blobReads;val payloads=h.db.payloadStatements
        h.exec("UPDATE actions SET description=description||'x'")
        denied(BoundedActiveStatus.BUDGET_REFUSED,h.read())
        assertEquals(blobs,h.db.blobReads);assertEquals(payloads,h.db.payloadStatements);assertEquals(0,h.blocked)
        println("CORE exact-boundary paddingBytes=$padding retained=${exact.retainedCharge} transition=${exact.transitionCharge}; nextByte=REFUSED")
    }

    @Test fun everyStateLossAtEveryPhaseDeniesWholeCoreAndClosesStatements() {
        for(at in ActiveCoreStore.Phase.entries) for(change in listOf("revoke","mutation","action","generation","unknown","blocked","recovery")) H().use { h ->
            h.phase={ if(it==at) when(change) {
                "revoke","blocked" -> h.ready.set(false)
                "mutation" -> h.authority.set(h.requested.copy(mutationEpoch=1))
                "action" -> h.authority.set(h.requested.copy(binding=h.requested.binding.copy(actionId="other")))
                "generation" -> h.authority.set(h.requested.copy(binding=h.requested.binding.copy(generation=2)))
                else -> h.authority.set(null)
            } }
            denied(BoundedActiveStatus.ACCESS_REVOKED,h.read());assertEquals(0,h.publicationAttempts);assertEquals(0,h.blocked)
            if(at==ActiveCoreStore.Phase.METADATA) {assertNull(h.admission);assertEquals(0,h.db.blobReads)}
            if(at==ActiveCoreStore.Phase.ADMITTED) assertEquals(0,h.db.blobReads)
        }
    }

    @Test fun initialUnknownOrRevokedAuthorityDoesNoReadAndLatePublicationCannotEscape() {
        H().use { h -> h.authority.set(null);denied(BoundedActiveStatus.ACCESS_REVOKED,h.read());assertTrue(h.queries.isEmpty()) }
        H().use { h -> h.ready.set(false);denied(BoundedActiveStatus.ACCESS_REVOKED,h.read());assertTrue(h.queries.isEmpty()) }
        for(at in listOf("database","lease","publication","after-callback","revoke-after-callback","false-after-callback")) H().use { h ->
            val invalidate={h.authority.set(h.requested.copy(mutationEpoch=1))}
            when(at) {
                "database" -> h.afterDatabaseCleanup=invalidate
                "lease" -> h.afterLeaseCleanup=invalidate
                "publication" -> h.beforePublication=invalidate
                "after-callback" -> h.afterPublication=invalidate
                "revoke-after-callback" -> h.afterPublication={h.ready.set(false)}
                else -> h.publicationAccepted=false
            }
            denied(BoundedActiveStatus.ACCESS_REVOKED,h.read());assertEquals(0,h.blocked)
        }
    }

    @Test fun invalidRelationsTypesOrPartialMaterializationNeverPublishEmptyOrContent() {
        for(change in listOf("UPDATE tasks SET affectedPersonId='foreign'", "UPDATE person_operational_states SET currentRoleId='foreign'",
            "UPDATE roles SET normalizedName='mismatch' WHERE id='r0'", "UPDATE persons SET createdAt=x'00'", "DELETE FROM person_operational_states")) H().use { h ->
            h.exec("PRAGMA foreign_keys=OFF")
            h.exec("INSERT INTO actions VALUES('b','Synthetic','Synthetic description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            h.exec("INSERT INTO persons VALUES('foreign','b','Synthetic','synthetic',1)")
            h.exec("INSERT INTO roles VALUES('foreign','b','Synthetic','synthetic',1)")
            h.exec(change)
            denied(BoundedActiveStatus.INVALID_DATA,h.read());assertEquals(0,h.publicationAttempts);assertEquals(1,h.blocked)
        }
    }

    @Test fun sqlFailuresAndBothCleanupFailuresAreNotRetriedOrPublished() {
        for(at in listOf("metadata","people","tasks","database-cleanup","lease-cleanup")) H().use { h ->
            var failures=0
            if(at.endsWith("cleanup")) {
                val failure={ failures++;error("SYNTHETIC_CLEANUP_FAILURE") }
                if(at=="database-cleanup") h.afterDatabaseCleanup=failure else h.afterLeaseCleanup=failure
            } else {
                val previous=h.db.beforePrepare
                h.db.beforePrepare={ sql ->
                    previous(sql)
                    if((at=="metadata" && sql.contains("SELECT COUNT(*)") && sql.contains("FROM roles")) ||
                        (at=="people" && sql.startsWith("SELECT rowid,") && sql.contains(" FROM persons ")) ||
                        (at=="tasks" && sql.startsWith("SELECT rowid,") && sql.contains(" FROM tasks "))) {
                        failures++;error("SYNTHETIC_STORAGE_FAILURE")
                    }
                }
            }
            denied(BoundedActiveStatus.STORAGE_FAILURE,h.read());assertEquals(1,failures)
            assertEquals(0,h.publicationAttempts);assertEquals(1,h.blocked)
        }
        H().use { h ->
            // A real SQLite engine error as well as the injected phase/cleanup failures above.
            h.exec("DROP TABLE person_role_plans")
            denied(BoundedActiveStatus.STORAGE_FAILURE,h.read())
            assertEquals(1,h.queries.count { it.contains(" FROM person_role_plans ") })
            assertEquals(0,h.db.blobReads);assertEquals(0,h.publicationAttempts);assertEquals(1,h.blocked)
        }
    }

    @Test fun oversizedAggregateAndSingleTextsRefuseWithoutPayloadOrCorruptionSignal() {
        for(table in listOf("actions","persons","roles")) H().use { h ->
            val field=when(table){"actions"->"description";"persons"->"pseudonym";else->"name"}
            h.exec("UPDATE $table SET $field=printf('%01000000d',0)")
            denied(BoundedActiveStatus.BUDGET_REFUSED,h.read());assertEquals(0,h.db.blobReads);assertEquals(0,h.blocked)
        }
    }

    @Test fun metadataAndMaterializationCanResumeOnDifferentThreadsWithoutLedgerTickets()=H().use { h ->
        val executors=listOf(Executors.newSingleThreadExecutor(),Executors.newSingleThreadExecutor())
        var turn=0
        val threads=HashSet<Thread>()
        val switching=object : PooledConnection {
            override suspend fun <R> usePrepared(sql: String, block: suspend (SQLiteStatement) -> R): R {
                val result=h.db.usePrepared(sql,block)
                suspendCoroutine<Unit> { continuation -> executors[turn++%2].execute { continuation.resume(Unit) } }
                threads.add(Thread.currentThread())
                return result
            }
        }
        try { assertCore(h,h.read(connection=switching));assertTrue(threads.size>=2) }
        finally { executors.forEach { it.shutdown();assertTrue(it.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)) } }
    }

    @Test fun accountingRejectsUnrelatedReuseNegativeCancellationAndOverflowPlans()=runBlocking {
        fun reservation(objects: Long)=ActiveCoreReservation(ActiveWorkspaceBudget.Units(objects=objects),ActiveWorkspaceBudget.Units())
        var runs=0
        val first=PreparedCoreRead(reservation(1)){runs++;1}
        val foreign=PreparedCoreRead(reservation(1)){runs++;2}
        val receipt=CoreReadAdmission.admit(listOf(first))
        try { foreign.materialize(receipt);fail() } catch(f:BoundedActiveRejected){assertEquals(BoundedActiveStatus.ACCESS_REVOKED,f.status)}
        assertEquals(1,first.materialize(receipt))
        try { first.materialize(receipt);fail() } catch(f:BoundedActiveRejected){assertEquals(BoundedActiveStatus.ACCESS_REVOKED,f.status)}
        assertEquals(1,runs)
        for((units,reason) in listOf((-1L to BoundedActiveStatus.INVALID_DATA),(Long.MAX_VALUE to BoundedActiveStatus.BUDGET_REFUSED))) {
            val bad=PreparedCoreRead(reservation(units)){runs++;0}
            try { CoreReadAdmission.admit(listOf(bad,foreign));fail() } catch(f:BoundedActiveRejected){assertEquals(reason,f.status)}
        }
        assertEquals(1,runs)
    }
}
