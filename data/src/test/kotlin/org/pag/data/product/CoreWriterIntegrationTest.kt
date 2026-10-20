package org.pag.data.product

import androidx.room3.Transactor
import androidx.room3.TransactionScope
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

/** Actual shared stores/transaction/dispatch code, real SQLite/schema 11. Trusted binding
 * and resource/Room lifecycle simulations; not an Android SQLCipher or payload-owner proof.
 */
class CoreWriterIntegrationTest {
    private class Queue:Executor {
        val jobs=ArrayDeque<Runnable>()
        var onQueue:()->Unit={}
        override fun execute(command:Runnable){onQueue();jobs.add(command)}
        fun drain(){while(jobs.isNotEmpty())jobs.removeFirst().run()}
    }
    private class Rollback(val value:Any?):RuntimeException()
    private class H:AutoCloseable {
        val db=SqliteHostConnection();val data=HostCoreWriterData(db)
        val worker=Queue();val main=Queue();val epoch=UncachedWorkspaceMutations()
        val requests=ArrayList<WorkspaceMutation>();val completions=ArrayList<WorkspaceWriteCompletion>()
        var generation=1L;var permission=2L;var action="a";var ready=true;var blocks=0
        var active=false;var handle=false;var lease=false;var time=20L
        var beforeEnd:()->Unit={};var afterEnd:()->Unit={};var beforePublication:()->Unit={}
        var dbCleanup:()->Unit={};var keyCleanup:()->Unit={}
        var failEndBefore=false;var failEndAfter=false;var failRollback=false
        val context:StorageContext get()=access.acquire(AccessScope.READY)!!
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                if(!ready || scope!=AccessScope.READY)return null
                val g=generation;val r=permission
                return object:StorageContext {
                    override val scope=AccessScope.READY
                    override val generation=g
                    override fun check(point:AccessPoint)=ready && g==this@H.generation && r==permission
                    override fun publish(result:()->Unit):Boolean {
                        beforePublication();if(!check(AccessPoint.PUBLICATION))return false
                        assertFalse(handle);assertFalse(lease);assertFalse(active);assertEquals(0,db.openStatements)
                        result();return true
                    }
                }
            }
        }
        val sink=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long)=beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,actionId),generation)
            override fun beforeCoreQueue(mutation:WorkspaceMutation,generation:Long):WorkspaceMutationInvalidation.Pending? {
                val pending=epoch.beforeCoreQueue(mutation,generation)?:return null
                requests.add(mutation)
                val a=action;val p=permission
                return object:WorkspaceMutationInvalidation.Pending {
                    override fun finish(completion:WorkspaceWriteCompletion):Boolean {completions.add(completion);return pending.finish(completion)}
                    override fun isCurrent()=pending.isCurrent() && a==action && p==permission && generation==this@H.generation
                }
            }
        }
        val boundary=WorkspaceWriteBoundary(access,worker,main,sink,{blocks++})
        init {
            exec("INSERT INTO actions VALUES('a','Synthetic action','Synthetic description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
            exec("INSERT INTO actions VALUES('b','Synthetic other','Synthetic other description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
            exec("INSERT INTO persons VALUES('p0','a','Synthetic zero','synthetic zero',0),('p1','a','Synthetic one','synthetic one',1)")
            exec("INSERT INTO roles VALUES('r0','a','Synthetic role','synthetic role',0)")
            worker.onQueue={assertTrue(requests.isNotEmpty());assertTrue(epoch.mutationEpoch>0);assertFalse(epoch.ramReuseEnabled)}
        }
        fun exec(sql:String)=runBlocking{db.execute(sql)}
        fun count(table:String)=runBlocking{data.query("SELECT COUNT(*) FROM $table"){it.step();it.getLong(0)}}
        fun actionRow(id:String="a")=runBlocking{data.actions.get(id)!!.model()}
        fun state(id:String="p0")=runBlocking{data.operations.state("a",id)!!.model()}
        fun <R:Any> queue(family:WorkspaceWriterFamily,rejected:(R)->Boolean,busy:()->R?,publish:(R)->Unit,
            work:suspend(StorageContext)->R) {
            boundary.submit(WorkspaceMutation(family,if(family==WorkspaceWriterFamily.ACTION_CREATE)null else "a"),publish,busy,{!rejected(it)}) {c,p->
                lease=true;handle=true
                val value=try { runBlocking {try {confirmedWorkspaceTransaction(tx(c),c,p,rejected){work(c)}}finally{handle=false;dbCleanup()}} }
                    finally{lease=false;keyCleanup()}
                requireOperations(c,AccessPoint.PUBLICATION) // models existing post-cleanup wrapper/key gate
                value
            }
        }
        fun <R:Any> write(family:WorkspaceWriterFamily,rejected:(R)->Boolean,busy:()->R?,work:suspend(StorageContext)->R):R {
            var result:R?=null;queue(family,rejected,busy,{result=it},work);worker.drain();main.drain();return result!!
        }
        suspend fun validWork(family:WorkspaceWriterFamily,c:StorageContext):Any=when(family) {
            WorkspaceWriterFamily.ACTION_CREATE->ActionWriterStore(data,c).create(NewAction("Synthetic","Synthetic description",1,2))
            WorkspaceWriterFamily.ACTION_EDIT->ActionWriterStore(data,c).change(actionRow(),NewAction("Changed","Synthetic description",1,2))
            WorkspaceWriterFamily.ACTION_STATUS->ActionWriterStore(data,c).advance("a",ActionStatus.PREPARATION)
            WorkspaceWriterFamily.PERSON->PersonWriterStore(data,c).change("a",null,"Synthetic created")
            else->OperationsStore(data,c){++time}.role("a",null,"Synthetic created")
        }
        fun effect(family:WorkspaceWriterFamily)=when(family) {
            WorkspaceWriterFamily.ACTION_CREATE->count("actions")==3L
            WorkspaceWriterFamily.ACTION_EDIT->actionRow().title=="Changed"
            WorkspaceWriterFamily.ACTION_STATUS->actionRow().status==ActionStatus.ACTIVE
            WorkspaceWriterFamily.PERSON->count("persons")==3L
            else->count("roles")==2L
        }
        fun advance(from:ActionStatus)=write<ActionChangeResult>(WorkspaceWriterFamily.ACTION_STATUS,{it is ActionChangeResult.Rejected},{ActionChangeResult.Rejected(ActionChangeRejection.STALE)}){ActionWriterStore(data,it){++time}.advance("a",from)}
        fun personRow(id:String="p0")=runBlocking{data.persons.get("a",id)!!.model()}
        fun person(id:String?,name:String)=personExpected(id?.let{personRow(it)},name)
        fun personExpected(expected:Person?,name:String)=write<PersonResult<Person>>(WorkspaceWriterFamily.PERSON,{it is PersonResult.Rejected},{PersonResult.Rejected(PersonRejection.NOT_ALLOWED)}){PersonWriterStore(data,it){++time}.change("a",expected,name)}
        fun <T> ops(work:suspend(OperationsStore)->OperationsResult<T>)=write<OperationsResult<T>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{OperationsResult.Rejected(OperationsFailure.STALE)}){work(OperationsStore(data,it){++time})}
        fun tx(c:StorageContext)=object:Transactor {
            override suspend fun <R> usePrepared(sql:String,block:suspend(SQLiteStatement)->R)=db.usePrepared(sql,block)
            override suspend fun inTransaction()=active
            override suspend fun <R> withTransaction(type:Transactor.SQLiteTransactionType,block:suspend TransactionScope<R>.()->R):R {
                db.execute("BEGIN IMMEDIATE");active=true
                val scope=object:TransactionScope<R> {
                    override suspend fun <T> usePrepared(sql:String,block:suspend(SQLiteStatement)->T)=db.usePrepared(sql,block)
                    override suspend fun <T> withNestedTransaction(block:suspend TransactionScope<T>.()->T):T=error("No nested proof")
                    override suspend fun rollback(result:R):Nothing=throw Rollback(result)
                }
                val value:R
                try{value=scope.block()}catch(e:Throwable){
                    if(failRollback)throw e
                    db.execute("ROLLBACK");active=false
                    if(e is Rollback){@Suppress("UNCHECKED_CAST")return e.value as R};throw e
                }
                try {
                    beforeEnd();requireOperations(c,AccessPoint.COMMIT)
                    if(failEndBefore)error("SYNTHETIC_END_BEFORE")
                    db.execute("COMMIT");active=false
                }catch(e:Throwable){if(active){db.execute("ROLLBACK");active=false};throw e}
                afterEnd();if(failEndAfter)error("SYNTHETIC_END_AFTER")
                return value
            }
        }
        override fun close(){if(active){exec("ROLLBACK");active=false};assertFalse(handle);assertFalse(lease);assertEquals(0,db.openStatements);db.close()}
    }
    private fun <T> value(result:OperationsResult<T>)=(result as OperationsResult.Value).value

    @Test fun actionCreateEditAndBothStatusTransitionsAreOuterCommitsWithAtomicStates()=H().use { h->
        val created=h.write<Action>(WorkspaceWriterFamily.ACTION_CREATE,{false},{null}){ActionWriterStore(h.data,it){++h.time}.create(NewAction("Synthetic new","Synthetic description",1,2))}
        assertEquals(ActionStatus.DRAFT,created.status);assertEquals(3L,h.count("actions"))
        val original=h.actionRow()
        val edited=h.write<ActionChangeResult>(WorkspaceWriterFamily.ACTION_EDIT,{it is ActionChangeResult.Rejected},{null}){ActionWriterStore(h.data,it){++h.time}.change(original,NewAction("Synthetic edited","Synthetic description",1,3))}
        assertTrue(edited is ActionChangeResult.Applied)
        val stale=h.write<ActionChangeResult>(WorkspaceWriterFamily.ACTION_EDIT,{it is ActionChangeResult.Rejected},{null}){ActionWriterStore(h.data,it){++h.time}.change(original,NewAction("Stale editor","Synthetic description",1,3))}
        assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.STALE),stale)
        assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.completions.last().outcome)
        h.ops{it.plan("a","p0",PersonRolePlan(setOf("r0"),"r0"))}
        assertTrue(h.advance(ActionStatus.PREPARATION) is ActionChangeResult.Applied)
        assertEquals(ActionStatus.ACTIVE,h.actionRow().status);assertEquals(2L,h.count("person_operational_states"))
        assertEquals("r0",h.state().currentRoleId);assertEquals(OperationalStatus.NO_STATUS,h.state().status)
        assertTrue(h.advance(ActionStatus.ACTIVE) is ActionChangeResult.Applied)
        assertEquals(ActionStatus.COMPLETED,h.actionRow().status);assertNotNull(h.actionRow().completedAt)
        assertEquals(2L,h.count("person_operational_states"));assertEquals(0L,h.count("task_events"))
        assertTrue(h.completions.all{it.cleanup==WorkspaceWriteCleanup.COMPLETE});assertEquals(0,h.blocks)
    }
    @Test fun activationSqlFailureNeverPublishesPartialStateOrActionStatus()=H().use { h->
        h.exec("CREATE TRIGGER synthetic_activation_failure BEFORE INSERT ON person_operational_states WHEN NEW.personId='p1' BEGIN SELECT RAISE(ABORT,'SYNTHETIC_ACTIVATION_FAILURE'); END")
        var calls=0
        h.queue<ActionChangeResult>(WorkspaceWriterFamily.ACTION_STATUS,{it is ActionChangeResult.Rejected},{null},{calls++}){ActionWriterStore(h.data,it).advance("a",ActionStatus.PREPARATION)}
        h.worker.drain();h.main.drain()
        assertEquals(0,calls);assertEquals(ActionStatus.PREPARATION,h.actionRow().status);assertEquals(0L,h.count("person_operational_states"))
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome);assertEquals(1,h.blocks)
    }
    @Test fun peopleAndOperationsPreservePoliciesLabelsPlansAndExactOnSiteGroups()=H().use { h->
        val person=(h.person(null,"Synthetic added") as PersonResult.Value).value
        assertEquals(3L,h.count("persons"));assertEquals("Synthetic renamed",(h.person("p0","Synthetic renamed") as PersonResult.Value).value.pseudonym)
        assertEquals(PersonResult.Rejected(PersonRejection.DUPLICATE_PSEUDONYM),h.person("p1","Synthetic renamed"))
        val role=value(h.ops{it.role("a",null,"Synthetic second")})
        value(h.ops{it.role("a","r0","Synthetic role renamed")})
        value(h.ops{it.plan("a","p0",PersonRolePlan(setOf("r0",role.id),"r0"))})
        assertEquals(OperationsResult.Rejected(OperationsFailure.IN_USE),h.ops{it.deleteRole("a","r0")})
        val unused=value(h.ops{it.role("a",null,"Synthetic unused")});value(h.ops{it.deleteRole("a",unused.id)})
        h.advance(ActionStatus.PREPARATION)
        value(h.ops{it.snapshot("a","p0",h.state(),OperationalStatus.ON_SITE,"r0","")})
        value(h.ops{it.snapshot("a","p1",h.state("p1"),OperationalStatus.ON_SITE,null,"")})
        value(h.ops{it.snapshot("a",person.id,h.state(person.id),OperationalStatus.IN_CUSTODY,null,"Synthetic place")})
        assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),h.person("p0","Must not rename ACTIVE"))
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),h.ops{it.role("a","r0","Must not rename ACTIVE")})
        val c=h.context
        val compact=runBlocking{val p=CompactPeopleStore(h.db,c,{true}).prepareCore(CompactTaskState(BoundedActiveBinding("a",1,2),h.epoch.mutationEpoch));p.materialize(CoreReadAdmission.admit(listOf(p)))}
        assertEquals(3L,compact.people.overall.total);assertEquals(2L,compact.people.overall.onSite);assertEquals(1L,compact.people.overall.inCustody)
        assertEquals(1L,compact.functions.withoutFunction);assertEquals(1L,compact.functions.usedFunctions)
        assertEquals(1L,compact.functions.items.single{it.role.id=="r0"}.onSiteCount)
        val card=compact.people.items.single{it.id=="p0"};assertEquals("Synthetic renamed",card.pseudonym);assertEquals("Synthetic role renamed",card.currentRoleName)
        assertEquals(0,h.blocks);assertFalse(h.epoch.ramReuseEnabled)
    }
    @Test fun stalePersonEditorIsRejectedAndFirstValueSurvives()=H().use { h->
        val original=h.personRow()
        assertTrue(h.personExpected(original,"Editor one") is PersonResult.Value)
        assertEquals(PersonResult.Rejected(PersonRejection.STALE),h.personExpected(original,"Editor two from same old form"))
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),h.completions.last())
        val persisted=runBlocking{h.data.persons.get("a","p0")!!.model()}
        assertEquals("Editor one",persisted.pseudonym)
    }
    @Test fun fullPersonIdentityAndActionAreComparedBeforeAnyRenameSql()=H().use { h->
        val original=h.personRow()
        var updates=0
        h.db.beforePrepare={if(it.trimStart().startsWith("UPDATE persons",true)) updates++}
        for(expected in listOf(original.copy(pseudonym="Old"),original.copy(id="p1"),original.copy(createdAt=99),original.copy(actionId="b"))) {
            assertEquals(PersonResult.Rejected(PersonRejection.STALE),h.personExpected(expected,"Must not write"))
            assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.completions.last().outcome)
        }
        assertEquals(PersonResult.Rejected(PersonRejection.NOT_FOUND),h.personExpected(original.copy(id="missing"),"Missing"))
        assertEquals(0,updates);assertEquals(original,h.personRow())
        val changed=(h.personExpected(original,"New value") as PersonResult.Value).value
        assertEquals(original.copy(pseudonym="New value"),changed)
        assertTrue(h.personExpected(changed,"Sequential editor") is PersonResult.Value)
        assertEquals(2,updates);assertEquals(0,h.blocks)
    }
    @Test fun schema11FullStateCasExplicitlyCannotDetectAba()=H().use { h->
        val original=h.personRow()
        val b=(h.personExpected(original,"B") as PersonResult.Value).value
        val a=(h.personExpected(b,original.pseudonym) as PersonResult.Value).value
        assertEquals(original,a) // No persistent monotone revision in schema 11.
        assertTrue(h.personExpected(original,"Stale after ABA") is PersonResult.Value)
        assertEquals("Stale after ABA",h.personRow().pseudonym)
    }
    @Test fun personRenameUnknownCleanupAndRevokeCannotPublishValue() {
        for(at in listOf("end-before","end-after","cleanup","rollback","queued","commit","after-commit","permission","generation","action")) H().use {h->
            val original=h.personRow();var calls=0
            when(at){
                "end-before"->h.failEndBefore=true
                "end-after"->h.failEndAfter=true
                "cleanup"->h.dbCleanup={error("SYNTHETIC_CLEANUP")}
                "rollback"->h.failRollback=true
                "commit"->h.beforeEnd={h.ready=false}
                "after-commit"->h.afterEnd={h.ready=false}
            }
            h.queue<PersonResult<Person>>(WorkspaceWriterFamily.PERSON,{it is PersonResult.Rejected},{null},{calls++}) {
                PersonWriterStore(h.data,it).change("a",if(at=="rollback")original.copy(createdAt=99) else original,"Renamed")
            }
            if(at=="queued")h.ready=false
            h.worker.drain()
            when(at){"permission"->h.permission++;"generation"->h.generation++;"action"->h.action="b"}
            val late=h.main.jobs.firstOrNull();h.main.drain();late?.run()
            assertEquals(0,calls)
            assertEquals(if(at in listOf("end-after","cleanup","after-commit","permission","generation","action"))"Renamed" else original.pseudonym,h.personRow().pseudonym)
            if(at.startsWith("end") || at=="rollback")assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
            if(at=="cleanup")assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
        }
    }
    @Test fun competingPersonEditorsAndDuplicateDispatchCannotSilentlyOverwrite()=H().use {h->
        val original=h.personRow();val results=ArrayList<PersonResult<Person>>()
        fun enqueue(name:String)=h.queue<PersonResult<Person>>(WorkspaceWriterFamily.PERSON,{it is PersonResult.Rejected},{PersonResult.Rejected(PersonRejection.NOT_ALLOWED)},{results.add(it)}) {
            PersonWriterStore(h.data,it).change("a",original,name)
        }
        enqueue("First");enqueue("Concurrent busy")
        assertEquals(1,h.worker.jobs.size);assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),results.single())
        val work=h.worker.jobs.removeFirst();work.run();work.run()
        val publication=h.main.jobs.removeFirst();publication.run();publication.run()
        assertEquals(2,results.size);assertEquals("First",h.personRow().pseudonym)
        enqueue("Old queued editor");h.worker.drain();h.main.drain()
        assertEquals(PersonResult.Rejected(PersonRejection.STALE),results.last())
        assertEquals("First",h.personRow().pseudonym);assertFalse(h.epoch.ramReuseEnabled)
    }
    @Test fun crossFamilyPendingUsesOneEpochAndTaskProofCannotBypassIt()=H().use { h->
        var personCalls=0;var roleResult:OperationsResult<Role>?=null;var taskResult:OperationsResult<Task>?=null
        h.queue<PersonResult<Person>>(WorkspaceWriterFamily.PERSON,{it is PersonResult.Rejected},{null},{personCalls++}){PersonWriterStore(h.data,it).change("a",null,"Synthetic queued")}
        assertEquals(1L,h.epoch.mutationEpoch);assertEquals(1,h.worker.jobs.size)
        h.queue<OperationsResult<Role>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{OperationsResult.Rejected(OperationsFailure.STALE)},{roleResult=it}){OperationsStore(h.data,it){++h.time}.role("a",null,"Must not enqueue")}
        TaskMutationBoundary(h.access,h.worker,h.main,h.sink,{h.blocks++}).submit("a",{taskResult=it}){_,_->error("Must not run")}
        h.queue<Action>(WorkspaceWriterFamily.ACTION_CREATE,{false},{null},{fail()}){error("Must not run")}
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),roleResult);assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),taskResult)
        assertEquals(1L,h.epoch.mutationEpoch);assertEquals(1,h.worker.jobs.size)
        val worker=h.worker.jobs.removeFirst();worker.run();worker.run();assertEquals(0,personCalls)
        val main=h.main.jobs.removeFirst();main.run();main.run();assertEquals(1,personCalls)
        assertEquals(3L,h.count("persons"));assertEquals(1L,h.count("roles"));assertEquals(0L,h.count("task_events"));assertEquals(1,h.completions.size)
    }
    @Test fun allFamiliesUnknownEndAndCleanupNeverBecomeValueSuccess() {
        for(family in listOf(WorkspaceWriterFamily.ACTION_CREATE,WorkspaceWriterFamily.ACTION_EDIT,WorkspaceWriterFamily.ACTION_STATUS,WorkspaceWriterFamily.PERSON,WorkspaceWriterFamily.OPERATIONS))
            for(failure in listOf("end-before","end-after","database","key")) H().use {h->
                when(failure){"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;"database"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};else->h.keyCleanup={error("SYNTHETIC_CLEANUP")}}
                var calls=0
                h.queue<Any>(family,{false},{null},{calls++}) {c->h.validWork(family,c)}
                h.worker.drain();h.main.drain();assertEquals(0,calls);assertEquals(1,h.blocks)
                assertEquals(if(failure.startsWith("end"))WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.COMMITTED,h.completions.single().outcome)
                assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
                assertNull(h.epoch.beforeCoreQueue(WorkspaceMutation(family,if(family==WorkspaceWriterFamily.ACTION_CREATE)null else "a"),1))
            }
    }
    @Test fun explicitRejectedWriteIsRolledBackOnlyAfterActualRollbackReturns() {
        for(failure in listOf("none","rollback","cleanup")) H().use{h->
            if(failure=="rollback")h.failRollback=true
            if(failure=="cleanup")h.dbCleanup={error("SYNTHETIC_CLEANUP")}
            var calls=0
            h.queue<OperationsResult<Unit>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{null},{calls++;assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),it)}){
                h.data.operations.insertRole(RoleRow("temporary","a","Synthetic temporary","synthetic temporary",1))
                OperationsResult.Rejected(OperationsFailure.INVALID)
            }
            h.worker.drain();h.main.drain()
            assertEquals(if(failure=="none")1 else 0,calls)
            assertEquals(if(failure=="rollback")WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.ROLLED_BACK,h.completions.single().outcome)
            assertEquals(if(failure=="rollback")2L else 1L,h.count("roles"))
            assertEquals(if(failure=="none")0 else 1,h.blocks)
        }
    }
    @Test fun revokeScopeAndLateCallbacksCannotPublishAcrossActionPermissionOrGeneration() {
        for(family in coreFamilies) for(at in listOf("initial","queued","commit","after-commit","publication","action","permission","generation")) H().use{h->
            var calls=0
            when(at){"initial"->h.ready=false;"commit"->h.beforeEnd={h.ready=false};"after-commit"->h.afterEnd={h.ready=false};"publication"->h.beforePublication={h.ready=false}}
            h.queue<Any>(family,{false},{null},{calls++}){h.validWork(family,it)}
            if(at=="queued")h.ready=false
            h.worker.drain()
            when(at){"action"->h.action="b";"permission"->h.permission++;"generation"->h.generation++}
            val callback=h.main.jobs.firstOrNull();h.main.drain();callback?.run()
            assertEquals(0,calls);assertEquals(0,h.blocks)
            assertEquals(at !in listOf("initial","queued","commit"),h.effect(family))
        }
    }
    @Test fun eachNewFamilyConsumesDuplicateWorkerAndMainDispatchExactlyOnce() {
        for(family in coreFamilies) H().use {h->
            var calls=0;h.queue<Any>(family,{false},{null},{calls++}){h.validWork(family,it)}
            val work=h.worker.jobs.removeFirst();work.run();work.run()
            assertTrue(h.effect(family));assertEquals(0,calls)
            val publish=h.main.jobs.removeFirst();publish.run();publish.run()
            assertEquals(1,calls);assertEquals(1,h.completions.size);assertEquals(1L,h.epoch.mutationEpoch)
            assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),h.completions.single())
        }
    }
    @Test fun legacyBlankTaskScopeDenialDoesNotQueueOrAdvanceTheCommonEpoch()=H().use{h->
        var result:OperationsResult<Task>?=null
        TaskMutationBoundary(h.access,h.worker,h.main,h.sink,{fail()}).submit("",{result=it}){_,_->error("No work allowed")}
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),result)
        assertEquals(0L,h.epoch.mutationEpoch);assertTrue(h.worker.jobs.isEmpty());assertTrue(h.completions.isEmpty())
    }
    companion object {
        private val coreFamilies=WorkspaceWriterFamily.entries.filter{it!=WorkspaceWriterFamily.TASK}
    }
}
