package org.pag.data.product

import androidx.room3.Transactor
import androidx.room3.TransactionScope
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

/** Same TaskStore + confirmedTaskTransaction + TaskMutationBoundary as production, real
 * SQLite/schema 11. Room/native lifecycle is simulated; not an Android SQLCipher proof.
 */
class TaskMutationSqliteTest {
    private class Queue : Executor {
        val jobs=ArrayDeque<Runnable>()
        var before:()->Unit={}
        var reject=false
        override fun execute(command:Runnable) {before();if(reject) error("SYNTHETIC_QUEUE_REJECTED");jobs.add(command)}
        fun take()=jobs.removeFirst()
        fun drain(){while(jobs.isNotEmpty()) take().run()}
    }
    private class H : AutoCloseable {
        val db=SqliteHostConnection()
        val worker=Queue();val main=Queue();val mutations=UncachedWorkspaceMutations()
        var generation=1L;var allowed=true;var blocks=0;var publications=0
        var beforePublish:()->Unit={}
        var afterCommit:()->Unit={}
        var dbCleanup:()->Unit={};var leaseCleanup:()->Unit={}
        var beforeEnd:()->Unit={};var afterEvent:()->Unit={}
        var beginFailure=false;var endFailureBefore=false;var endFailureAfter=false;var rollbackFailure=false
        var handle=false;var lease=false;var active=false
        val reports=ArrayList<WorkspaceWriteCompletion>()
        var reads=0
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                if(!allowed || scope!=AccessScope.READY) return null
                val g=generation
                return object:StorageContext {
                    override val scope=AccessScope.READY
                    override val generation=g
                    override fun check(point:AccessPoint)=allowed && g==this@H.generation
                    override fun publish(result:()->Unit):Boolean {
                        beforePublish()
                        if(!check(AccessPoint.PUBLICATION)) return false
                        assertFalse(handle);assertFalse(lease);assertFalse(active);assertEquals(0,db.openStatements)
                        publications++;result();return true
                    }
                }
            }
        }
        val sink=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long):WorkspaceMutationInvalidation.Pending? {
                val ticket=mutations.beforeTaskQueue(actionId,generation)?:return null
                return object:WorkspaceMutationInvalidation.Pending {
                    override fun finish(completion:WorkspaceWriteCompletion):Boolean {reports.add(completion);return ticket.finish(completion)}
                    override fun isCurrent()=ticket.isCurrent()
                }
            }
        }
        val runner=TaskMutationBoundary(access,worker,main,sink,{blocks++})
        val dao=HostTaskData(db){afterEvent()}
        var time=10L
        init { exec("INSERT INTO actions VALUES('a','Synthetic','Synthetic action',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            worker.before={assertTrue(mutations.mutationEpoch>0);assertFalse(mutations.ramReuseEnabled)}
        }
        fun exec(sql:String)=runBlocking{db.execute(sql)}
        fun count(table:String)=runBlocking{db.usePrepared("SELECT COUNT(*) FROM $table"){assertTrue(it.step());it.getLong(0)}}
        fun task(id:String)=runBlocking{dao.tasks.get("a",id)!!.model()}
        fun <T> submit(publish:(OperationsResult<T>)->Unit, work:suspend(TaskStore)->OperationsResult<T>) {
            runner.submit("a",publish) { c,p ->
                lease=true;handle=true
                try {runBlocking {
                    try {
                        confirmedTaskTransaction(tx(c),c,p){work(TaskStore(dao,c){++time})}
                    } finally {handle=false;dbCleanup()}
                }} finally {lease=false;leaseCleanup()}
            }
        }
        fun tx(c:StorageContext)=object:Transactor {
            override suspend fun <R> usePrepared(sql:String,block:suspend(SQLiteStatement)->R)=db.usePrepared(sql,block)
            override suspend fun inTransaction()=active
            override suspend fun <R> withTransaction(type:Transactor.SQLiteTransactionType,block:suspend TransactionScope<R>.()->R):R {
                if(beginFailure) error("SYNTHETIC_BEGIN_FAILURE")
                db.execute("BEGIN IMMEDIATE");active=true
                val scope=object:TransactionScope<R> {
                    override suspend fun <T> usePrepared(sql:String,block:suspend(SQLiteStatement)->T)=db.usePrepared(sql,block)
                    override suspend fun <T> withNestedTransaction(block:suspend TransactionScope<T>.()->T):T=error("No nested proof")
                    override suspend fun rollback(result:R):Nothing=throw Rollback(result)
                }
                val result:R
                try {result=scope.block()} catch(e:Throwable) {
                    try {if(rollbackFailure) error("SYNTHETIC_ROLLBACK_FAILURE");db.execute("ROLLBACK");active=false} catch(rollback:Throwable){e.addSuppressed(rollback);throw e}
                    if(e is Rollback) { @Suppress("UNCHECKED_CAST") return e.result as R }
                    throw e
                }
                beforeEnd()
                try {
                    requireOperations(c,AccessPoint.COMMIT)
                    if(endFailureBefore) error("SYNTHETIC_END_FAILURE_BEFORE")
                    db.execute("COMMIT");active=false
                } catch(e:Throwable) {if(active){db.execute("ROLLBACK");active=false};throw e}
                if(endFailureAfter) error("SYNTHETIC_END_FAILURE_AFTER")
                afterCommit()
                return result
            }
        }
        fun create():Task {
            var value:Task?=null;submit<Task>({value=(it as OperationsResult.Value).value}){it.create("a",TaskInput("Synthetic task"))}
            worker.drain();main.drain();return value!!
        }
        override fun close(){if(active){exec("ROLLBACK");active=false};assertFalse(handle);assertFalse(lease);assertEquals(0,db.openStatements);db.close()}
    }
    private class Rollback(val result:Any?):RuntimeException()

    @Test fun beforeQueuePendingCommitAndCleanupAreDistinctAndCallbackDoesNotCreateEvidence()=H().use { h ->
        var calls=0
        h.submit<Task>({calls++}){it.create("a",TaskInput("Synthetic"))}
        assertEquals(1L,h.mutations.mutationEpoch);assertFalse(h.mutations.ramReuseEnabled)
        assertEquals(0L,h.count("tasks"));assertTrue(h.reports.isEmpty())
        val worker=h.worker.take();worker.run();worker.run() // even duplicated worker dispatch never repeats SQL
        assertEquals(1L,h.count("tasks"));assertEquals(1L,h.count("task_events"));assertTrue(h.reports.isEmpty());assertEquals(0,calls)
        val callback=h.main.take();callback.run();callback.run()
        assertEquals(1,calls);assertEquals(listOf(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)),h.reports)
        assertEquals(0,h.blocks);assertFalse(h.mutations.ramReuseEnabled)
    }
    @Test fun createEditTransitionsAndConflictsPreserveAtomicSingleEvents()=H().use { h ->
        var current=h.create();assertEquals(1L,h.count("task_events"))
        fun write(work:suspend(TaskStore)->OperationsResult<Task>):OperationsResult<Task> {
            var result:OperationsResult<Task>?=null;h.submit<Task>({result=it},work);h.worker.drain();h.main.drain();return result!!
        }
        val original=current
        current=(write{it.edit("a",current,TaskInput("Synthetic edited",priority=TaskPriority.HIGH))} as OperationsResult.Value).value
        assertEquals(1L,h.count("task_events"))
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),write{it.edit("a",original,TaskInput("Old editor"))})
        for(status in listOf(TaskStatus.IN_PROGRESS,TaskStatus.DONE,TaskStatus.OPEN,TaskStatus.CANCELLED)) {
            val previous=current
            val before=h.count("task_events")
            current=(write{it.transition("a",previous,status)} as OperationsResult.Value).value
            assertEquals(before+1,h.count("task_events"))
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),write{it.transition("a",previous,status)})
            assertEquals(before+1,h.count("task_events"))
            assertEquals(current,h.task(current.id))
        }
        assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.reports.last().outcome)
        assertTrue(h.reports.all{it.cleanup==WorkspaceWriteCleanup.COMPLETE})
        assertEquals(0,h.blocks)
    }
    @Test fun taskAndEventBothRollbackOnFailureAndUnconfirmedOutcomeNeverSucceeds() {
        for(at in listOf("begin","event","event-sql","end-before","end-after","rollback")) H().use { h ->
            var calls=0
            when(at) {
                "begin"->h.beginFailure=true
                "event"->h.afterEvent={error("SYNTHETIC_EVENT_FAILURE")}
                "event-sql"->h.exec("CREATE TRIGGER synthetic_event_failure BEFORE INSERT ON task_events BEGIN SELECT RAISE(ABORT,'SYNTHETIC_EVENT_FAILURE'); END")
                "end-before"->h.endFailureBefore=true
                "end-after"->h.endFailureAfter=true
                else->{h.afterEvent={error("SYNTHETIC_BODY_FAILURE")};h.rollbackFailure=true}
            }
            h.submit<Task>({calls++}){it.create("a",TaskInput("Synthetic"))};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.reports.single().outcome)
            assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.reports.single().cleanup)
            assertEquals(if(at=="end-after"||at=="rollback") 1L else 0L,h.count("tasks"))
            assertEquals(h.count("tasks"),h.count("task_events"))
            assertNull(h.mutations.beforeTaskQueue("a",1));assertEquals(1,h.blocks)
        }
    }
    @Test fun definiteDomainRollbackHasNoRowsNoEventAndNoCorruptionSignal()=H().use { h ->
        var result:OperationsResult<Task>?=null
        h.submit<Task>({result=it}){it.create("a",TaskInput(""))};h.worker.drain();h.main.drain()
        assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),result)
        assertEquals(listOf(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)),h.reports)
        assertEquals(0L,h.count("tasks"));assertEquals(0L,h.count("task_events"));assertEquals(0,h.blocks)
    }
    @Test fun revokeBeforeQueueBeforeCommitAfterCommitAndBeforePublicationDenySuccess() {
        for(at in listOf("initial","queued","commit","after-commit","publication","generation")) H().use { h ->
            var calls=0
            when(at) {
                "initial"->h.allowed=false
                "commit"->h.beforeEnd={h.allowed=false}
                "after-commit"->h.afterCommit={h.allowed=false}
                "publication"->h.beforePublish={h.allowed=false}
            }
            h.submit<Task>({calls++}){it.create("a",TaskInput("Synthetic"))}
            if(at=="queued") h.allowed=false
            if(at=="generation") h.generation++
            h.worker.drain();h.main.drain()
            assertEquals(0,calls)
            val committed=at=="after-commit"||at=="publication"
            assertEquals(if(committed)1L else 0L,h.count("tasks"));assertEquals(h.count("tasks"),h.count("task_events"))
            if(at=="initial") assertTrue(h.reports.isEmpty())
            if(at=="after-commit") assertEquals(WorkspaceWriteOutcome.COMMITTED,h.reports.single().outcome)
            assertEquals(0,h.blocks)
        }
    }
    @Test fun cleanupFailureAfterRealCommitNeverPublishesAndCannotBeRetried() {
        for(lease in listOf(false,true)) H().use { h ->
            val error={error("SYNTHETIC_CLEANUP_FAILURE")}
            if(lease) h.leaseCleanup=error else h.dbCleanup=error
            var calls=0;h.submit<Task>({calls++}){it.create("a",TaskInput("Synthetic"))};h.worker.drain();h.main.drain()
            assertEquals(1L,h.count("tasks"));assertEquals(1L,h.count("task_events"));assertEquals(0,calls)
            assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED),h.reports.single())
            assertNull(h.mutations.beforeTaskQueue("a",1));assertEquals(1,h.blocks)
        }
    }
    @Test fun concurrentEditorsPendingAndLateDuplicateCallbacksCannotDuplicateWrites()=H().use { h ->
        val task=h.create();var first=0;var conflict:OperationsResult<Task>?=null
        h.submit<Task>({first++}){it.transition("a",task,TaskStatus.DONE)}
        h.submit<Task>({conflict=it}){it.transition("a",task,TaskStatus.DONE)}
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),conflict)
        assertEquals(1,h.worker.jobs.size)
        h.worker.drain();val callback=h.main.take();callback.run();callback.run()
        assertEquals(1,first);assertEquals(2L,h.count("task_events"))
        var stale:OperationsResult<Task>?=null
        h.submit<Task>({stale=it}){it.edit("a",task,TaskInput("Old editor"))};h.worker.drain();h.main.drain()
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),stale);assertEquals(2L,h.count("task_events"))
    }
    @Test fun successCallbackCannotInventCommitAndFreshGenerationNeverRestoresRamReuse()=H().use { h ->
        h.runner.submit("a",{_:OperationsResult<Unit>->fail()}) {_,_->OperationsResult.Value(Unit)}
        h.worker.drain();h.main.drain()
        assertEquals(0L,h.count("tasks"));assertEquals(1,h.blocks)
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.reports.single().outcome)
        assertNull(h.mutations.beforeTaskQueue("a",1))
        // A fresh authorized READY generation can write again with NO cache; never resurrects
        // a revoked/uncertain ActiveWorkspaceValidity or old callback.
        val fresh=h.mutations.beforeTaskQueue("a",2)!!
        assertFalse(h.mutations.ramReuseEnabled)
        assertTrue(fresh.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
    }
    @Test fun generationChangeAfterCommitButBeforeMainDropsLateSuccess()=H().use { h ->
        var calls=0;h.submit<Task>({calls++}){it.create("a",TaskInput("Synthetic"))}
        h.worker.drain();h.generation++
        val callback=h.main.take();callback.run();callback.run()
        assertEquals(0,calls);assertEquals(1L,h.count("tasks"));assertEquals(1L,h.count("task_events"))
    }
    @Test fun cleanupFailureAfterConfirmedRollbackAndDeletedTaskCascadesEvents() {
        H().use { h ->
            h.dbCleanup={error("SYNTHETIC_CLEANUP_FAILURE")}
            h.submit<Task>({fail()}){it.create("a",TaskInput(""))};h.worker.drain();h.main.drain()
            assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED),h.reports.single())
            assertEquals(0L,h.count("tasks"));assertEquals(0L,h.count("task_events"));assertEquals(1,h.blocks)
        }
        H().use { h ->
            h.exec("UPDATE actions SET status='PREPARATION'")
            val task=h.create();var removed=false
            h.submit<Unit>({removed=it is OperationsResult.Value}){it.delete("a",task)}
            h.worker.drain();h.main.drain()
            assertTrue(removed);assertEquals(0L,h.count("tasks"));assertEquals(0L,h.count("task_events"))
        }
    }

    @Test fun queueRejectionNestedTransactionAndMainQueueLossStayFailClosed() {
        H().use { h ->h.worker.reject=true;h.submit<Task>({fail()}){it.create("a",TaskInput("Synthetic"))};assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.reports.single().outcome);assertEquals(0L,h.count("tasks"))}
        H().use { h ->
            h.main.reject=true;h.submit<Task>({fail()}){it.create("a",TaskInput("Synthetic"))}
            try {h.worker.drain();fail()} catch(_:IllegalStateException){}
            assertTrue(h.reports.isEmpty());assertNull(h.mutations.beforeTaskQueue("a",1));assertFalse(h.mutations.ramReuseEnabled)
            assertEquals(1L,h.count("task_events"))
        }
        H().use { h ->
            h.exec("BEGIN");h.active=true
            val progress=TaskWriteProgress()
            try {runBlocking{confirmedTaskTransaction(h.tx(h.access.acquire(AccessScope.READY)!!),h.access.acquire(AccessScope.READY)!!,progress){OperationsResult.Value(Unit)}};fail()}catch(_:ProductStorageFailure){}
            assertNotEquals(WorkspaceWriteOutcome.COMMITTED,progress.outcome)
        }
    }
}
