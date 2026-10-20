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
    internal class Queue:Executor {
        val jobs=ArrayDeque<Runnable>()
        var onQueue:()->Unit={}
        override fun execute(command:Runnable){onQueue();jobs.add(command)}
        fun drain(){while(jobs.isNotEmpty())jobs.removeFirst().run()}
    }
    private class Rollback(val value:Any?):RuntimeException()
    internal class H:AutoCloseable {
        val db=SqliteHostConnection();val data=HostCoreWriterData(db)
        val worker=Queue();val main=Queue();val epoch=UncachedWorkspaceMutations()
        val requests=ArrayList<WorkspaceMutation>();val completions=ArrayList<WorkspaceWriteCompletion>()
        var generation=1L;var permission=2L;var action="a";var ready=true;var blocks=0
        var storageReady=true
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
        fun roleRow(id:String="r0")=runBlocking{data.operations.role("a",id)!!.model()}
        fun actionRow(id:String="a")=runBlocking{data.actions.get(id)!!.model()}
        fun state(id:String="p0")=runBlocking{data.operations.state("a",id)!!.model()}
        fun <R:Any> queue(family:WorkspaceWriterFamily,rejected:(R)->Boolean,busy:()->R?,publish:(R)->Unit,
            work:suspend(StorageContext)->R) {
            boundary.submit(WorkspaceMutation(family,if(family==WorkspaceWriterFamily.ACTION_CREATE)null else "a"),publish,busy,{!rejected(it)}) {c,p->
                if(!storageReady)throw ProductStorageFailure()
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
            WorkspaceWriterFamily.MATERIAL->MaterialStore(data,c){++time}.create("a",MaterialInput("Synthetic material"))
            WorkspaceWriterFamily.PROTOCOL->ProtocolStore(data,c){++time}.create("a",ActionStatus.ACTIVE,ProtocolInput(ProtocolClassification.KEY_EVENT,"Synthetic",1))
            WorkspaceWriterFamily.EXPENSE->ExpenseStore(data,c){++time}.create("a",ExpenseInput("Synthetic expense",1,listOf(ExpensePaymentInput("Synthetic payer",1))))
            else->OperationsStore(data,c){++time}.role("a",null,"Synthetic created")
        }
        fun effect(family:WorkspaceWriterFamily)=when(family) {
            WorkspaceWriterFamily.ACTION_CREATE->count("actions")==3L
            WorkspaceWriterFamily.ACTION_EDIT->actionRow().title=="Changed"
            WorkspaceWriterFamily.ACTION_STATUS->actionRow().status==ActionStatus.ACTIVE
            WorkspaceWriterFamily.PERSON->count("persons")==3L
            WorkspaceWriterFamily.MATERIAL->count("materials")==1L
            WorkspaceWriterFamily.PROTOCOL->count("protocol_entries")==1L && count("protocol_revisions")==1L
            WorkspaceWriterFamily.EXPENSE->count("expenses")==1L && count("expense_payments")==1L
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
        h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",PersonRolePlan(emptySet(),null)),PersonRolePlan(setOf("r0"),"r0"))}
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
        value(h.ops{it.role("a",h.roleRow(),"Synthetic role renamed")})
        value(h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",PersonRolePlan(emptySet(),null)),PersonRolePlan(setOf("r0",role.id),"r0"))})
        assertEquals(OperationsResult.Rejected(OperationsFailure.IN_USE),h.ops{it.deleteRole("a",h.roleRow())})
        val unused=value(h.ops{it.role("a",null,"Synthetic unused")});value(h.ops{it.deleteRole("a",unused)})
        h.advance(ActionStatus.PREPARATION)
        value(h.ops{it.snapshot("a","p0",h.state(),OperationalStatus.ON_SITE,"r0","")})
        value(h.ops{it.snapshot("a","p1",h.state("p1"),OperationalStatus.ON_SITE,null,"")})
        value(h.ops{it.snapshot("a",person.id,h.state(person.id),OperationalStatus.IN_CUSTODY,null,"Synthetic place")})
        assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),h.person("p0","Must not rename ACTIVE"))
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),h.ops{it.role("a",h.roleRow(),"Must not rename ACTIVE")})
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
    @Test fun rolePlanOldEditorsConflictAndConfirmedFirstPlanSurvives()=H().use {h->
        val original=PersonRolePlan(emptySet(),null)
        val a=PersonRolePlan(setOf("r0"),"r0")
        val expected=ExpectedPersonRolePlan("a","p0",original)
        assertEquals(OperationsResult.Value(Unit),h.ops{it.plan("a","p0",expected,a)})
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.ops{it.plan("a","p0",expected,original)})
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),h.completions.last())
        val board=runBlocking{OperationsStore(h.data,h.context){0}.board("a")} as OperationsResult.Value
        assertEquals(a,board.value.persons.single{it.person.id=="p0"}.plan)
    }
    @Test fun rolePlanCasCoversWholeSetsPrimaryEmptyForeignAndMissingReferences()=H().use{h->
        h.exec("INSERT INTO roles VALUES('r1','a','Second','second',1),('foreign','b','Foreign','foreign',1)")
        val empty=PersonRolePlan(emptySet(),null)
        fun old(plan:PersonRolePlan)=ExpectedPersonRolePlan("a","p0",plan)
        fun save(expected:ExpectedPersonRolePlan,plan:PersonRolePlan)=h.ops{it.plan("a","p0",expected,plan)}
        val a=PersonRolePlan(setOf("r0","r1"),"r0")
        assertEquals(OperationsResult.Value(Unit),save(old(empty),a))
        var writes=0
        h.db.beforePrepare={if(it.trimStart().startsWith("DELETE",true) || it.trimStart().startsWith("INSERT",true))writes++}
        for(expected in listOf(old(empty),old(a.copy(primaryRoleId="r1")),old(PersonRolePlan(setOf("r0"),"r0")),
            ExpectedPersonRolePlan("b","p0",a),ExpectedPersonRolePlan("a","p1",a))) {
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),save(expected,empty))
            assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.completions.last().outcome)
        }
        for(id in listOf("missing","foreign"))assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),save(old(a),PersonRolePlan(setOf(id),id)))
        assertEquals(0,writes)
        val b=PersonRolePlan(setOf("r1"),"r1")
        assertEquals(OperationsResult.Value(Unit),save(old(a),b))
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),save(old(a),empty))
        val board=runBlocking{OperationsStore(h.data,h.context){0}.board("a")} as OperationsResult.Value
        assertEquals(b,board.value.persons.single{it.person.id=="p0"}.plan)
        assertEquals(empty,board.value.persons.single{it.person.id=="p1"}.plan)
        assertEquals(OperationsResult.Value(Unit),save(old(b),empty))
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),save(old(b),a))
        assertEquals(OperationsResult.Value(Unit),save(old(empty),a)) // Stored empty and absent empty have identical domain meaning.
    }
    @Test fun rolePlanExpectedIsImmutableAndSchema11CannotDetectAba()=H().use{h->
        val mutable=linkedSetOf("r0");val frozen=ExpectedPersonRolePlan("a","p0",PersonRolePlan(mutable,"r0"))
        mutable.clear();assertEquals(setOf("r0"),frozen.plan.roleIds)
        assertThrows(UnsupportedOperationException::class.java){(frozen.plan.roleIds as MutableSet).clear()}
        val a=PersonRolePlan(emptySet(),null);val b=frozen.plan
        val old=ExpectedPersonRolePlan("a","p0",a)
        assertTrue(h.ops{it.plan("a","p0",old,b)} is OperationsResult.Value)
        assertTrue(h.ops{it.plan("a","p0",frozen,a)} is OperationsResult.Value)
        assertTrue(h.ops{it.plan("a","p0",old,b)} is OperationsResult.Value) // Explicit negative ABA proof.
    }
    @Test fun planWritesKeepOuterOutcomeCleanupAndAuthorityGates() {
        for(at in listOf("normal","stale","end-before","end-after","cleanup","rollback","queued","commit","after-commit","permission","generation","action"))H().use{h->
            val empty=PersonRolePlan(emptySet(),null);val next=PersonRolePlan(setOf("r0"),"r0")
            val expected=ExpectedPersonRolePlan("a","p0",if(at in listOf("stale","rollback"))next else empty)
            var calls=0;var result:OperationsResult<Unit>?=null
            when(at){"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;"cleanup"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};"rollback"->h.failRollback=true;"commit"->h.beforeEnd={h.ready=false};"after-commit"->h.afterEnd={h.ready=false}}
            h.queue<OperationsResult<Unit>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{null},{calls++;result=it}){OperationsStore(h.data,it){0}.plan("a","p0",expected,next)}
            if(at=="queued")h.ready=false
            val work=h.worker.jobs.removeFirst();work.run();work.run()
            when(at){"permission"->h.permission++;"generation"->h.generation++;"action"->h.action="b"}
            val callback=h.main.jobs.first();h.main.drain();callback.run()
            assertEquals(if(at in listOf("normal","stale"))1 else 0,calls)
            if(at=="stale")assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),result)
            val changed=at in listOf("normal","end-after","cleanup","after-commit","permission","generation","action")
            assertEquals(if(changed)1L else 0L,h.count("person_planned_roles"))
            assertEquals(if(changed)1L else 0L,h.count("person_role_plans"))
            if(at in listOf("stale","normal"))assertEquals(WorkspaceWriteCompletion(if(at=="stale")WorkspaceWriteOutcome.ROLLED_BACK else WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),h.completions.single())
            if(at.startsWith("end") || at=="rollback")assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
            if(at=="cleanup")assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
            assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun planInsertFailureRollsBackRemovedAssignmentsAndConcurrentQueueRemainsSingle()=H().use{h->
        val empty=PersonRolePlan(emptySet(),null);val a=PersonRolePlan(setOf("r0"),"r0")
        assertTrue(h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",empty),a)} is OperationsResult.Value)
        h.exec("CREATE TRIGGER synthetic_plan_failure BEFORE INSERT ON person_role_plans BEGIN SELECT RAISE(ABORT,'SYNTHETIC_PLAN_FAILURE'); END")
        var calls=0
        h.queue<OperationsResult<Unit>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{null},{calls++}){OperationsStore(h.data,it){0}.plan("a","p0",ExpectedPersonRolePlan("a","p0",a),empty)}
        var busy:OperationsResult<Unit>?=null
        h.queue<OperationsResult<Unit>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{OperationsResult.Rejected(OperationsFailure.STALE)},{busy=it}){error("Must not queue")}
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),busy);assertEquals(1,h.worker.jobs.size)
        h.worker.drain();h.main.drain();assertEquals(0,calls)
        val board=runBlocking{OperationsStore(h.data,h.context){0}.board("a")} as OperationsResult.Value
        assertEquals(a,board.value.persons.single{it.person.id=="p0"}.plan)
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.last().outcome)
        assertEquals(1L,h.count("person_planned_roles"));assertEquals(1L,h.count("person_role_plans"))
    }
    @Test fun planForeignPersonAndDeletedRoleCannotChangeOtherData()=H().use{h->
        h.exec("INSERT INTO persons VALUES('foreign-person','b','Foreign','foreign',0)")
        val empty=PersonRolePlan(emptySet(),null);val chosen=PersonRolePlan(setOf("r0"),"r0")
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.ops{it.plan("a","foreign-person",ExpectedPersonRolePlan("a","foreign-person",empty),chosen)})
        h.exec("DELETE FROM roles WHERE id='r0'")
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",empty),chosen)})
        assertEquals(0L,h.count("person_planned_roles"));assertEquals(0L,h.count("person_role_plans"))
    }
    @Test fun roleRenameSecondOldEditorIsStale()=H().use{h->
        val original=h.roleRow()
        assertTrue(h.ops{it.role("a",original,"Editor A")} is OperationsResult.Value)
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.ops{it.role("a",original,"Editor B")})
        assertEquals("Editor A",h.roleRow().name)
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),h.completions.last())
    }
    @Test fun roleDeleteOldConfirmationCannotDeleteRenamedRole()=H().use{h->
        val original=runBlocking{h.data.operations.role("a","r0")!!.model()}
        assertTrue(h.ops{it.role("a",original,"Changed after dialog")} is OperationsResult.Value)
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.ops{it.deleteRole("a",original)})
        assertEquals("Changed after dialog",h.roleRow().name)
        assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.completions.last().outcome)
    }
    @Test fun roleIdentityCaseWhitespaceAndCreateValidationAreExactBeforeWrites()=H().use{h->
        val original=h.roleRow()
        h.exec("INSERT INTO roles VALUES('r1','a','Other','other',5),('foreign','b','Foreign','foreign',0)")
        var writes=0
        h.db.beforePrepare={if(it.trimStart().startsWith("UPDATE roles",true) || it.trimStart().startsWith("DELETE FROM roles",true))writes++}
        for(expected in listOf(original.copy(name=original.name.lowercase()),original.copy(name=" ${original.name} "),original.copy(createdAt=99),original.copy(id="r1"),original.copy(actionId="b"))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.ops{it.role("a",expected,"New")})
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.ops{it.deleteRole("a",expected)})
        }
        val missing=original.copy(id="missing")
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.ops{it.role("a",missing,"New")})
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.ops{it.deleteRole("a",missing)})
        assertEquals(OperationsResult.Rejected(OperationsFailure.DUPLICATE),h.ops{it.role("a",original," OTHER ")})
        assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.ops{it.role("a",original,"  ")})
        assertEquals(0,writes);assertEquals(original,h.roleRow())
        val renamed=value(h.ops{it.role("a",original," Mixed CASE ")})
        assertEquals(original.copy(name=" Mixed CASE "),renamed)
        val next=value(h.ops{it.role("a",renamed,"Next")})
        assertEquals(OperationsResult.Value(Unit),h.ops{it.deleteRole("a",next)})
        assertEquals(2L,h.count("roles"))
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),h.completions.last())
    }
    @Test fun currentRoleDeletePreservesAllOperationsAndTaskReferenceGates(){
        for(kind in listOf("planned","live","task"))H().use{h->
            val role=h.roleRow()
            when(kind){
                "planned"->assertTrue(h.ops{it.plan("a","p0",ExpectedPersonRolePlan("a","p0",PersonRolePlan(emptySet(),null)),PersonRolePlan(setOf("r0"),"r0"))} is OperationsResult.Value)
                "live"->h.exec("INSERT INTO person_operational_states VALUES('a','p0','r0','ON_SITE',NULL,1)")
                else->h.exec("INSERT INTO tasks VALUES('t','a','Synthetic','NORMAL','OPEN',NULL,'r0',NULL,NULL,NULL,1,1,NULL)")
            }
            assertEquals(OperationsResult.Rejected(OperationsFailure.IN_USE),h.ops{it.deleteRole("a",role)})
            assertEquals(role,h.roleRow());assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.completions.last().outcome)
        }
    }
    @Test fun roleFullValueCasStillCannotDetectSchema11Aba()=H().use{h->
        val a=h.roleRow();val b=value(h.ops{it.role("a",a,"B")})
        val again=value(h.ops{it.role("a",b,a.name)})
        assertEquals(a,again)
        assertTrue(h.ops{it.role("a",a,"Old editor after ABA")} is OperationsResult.Value)
        val current=h.roleRow();val changed=value(h.ops{it.role("a",current,"B again")})
        assertEquals(current,value(h.ops{it.role("a",changed,current.name)}))
        assertTrue(h.ops{it.deleteRole("a",current)} is OperationsResult.Value) // Negative ABA proof for delete too.
    }
    @Test fun renameAndDeleteKeepOuterCleanupAuthorityAndSingleDispatch(){
        for(delete in listOf(false,true))for(at in listOf("normal","stale","end-before","end-after","rollback","db-cleanup","key-cleanup","initial","queued","commit","after-commit","publication","permission","generation","action"))H().use{h->
            val original=h.roleRow();val expected=if(at in listOf("stale","rollback"))original.copy(createdAt=99) else original
            when(at){"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;"rollback"->h.failRollback=true;"db-cleanup"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};"key-cleanup"->h.keyCleanup={error("SYNTHETIC_CLEANUP")};"initial"->h.ready=false;"commit"->h.beforeEnd={h.ready=false};"after-commit"->h.afterEnd={h.ready=false};"publication"->h.beforePublication={h.ready=false}}
            var calls=0
            h.queue<OperationsResult<*>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{null},{calls++}){c->
                val store=OperationsStore(h.data,c){0};if(delete)store.deleteRole("a",expected) else store.role("a",expected,"Changed")
            }
            if(at=="queued")h.ready=false
            val work=h.worker.jobs.firstOrNull();h.worker.drain();work?.run()
            when(at){"permission"->h.permission++;"generation"->h.generation++;"action"->h.action="b"}
            val main=h.main.jobs.firstOrNull();h.main.drain();main?.run()
            assertEquals(if(at in listOf("normal","stale"))1 else 0,calls)
            val changed=at in listOf("normal","end-after","db-cleanup","key-cleanup","after-commit","publication","permission","generation","action")
            if(delete)assertEquals(if(changed)0L else 1L,h.count("roles")) else assertEquals(if(changed)"Changed" else original.name,h.roleRow().name)
            if(at.startsWith("end") || at=="rollback")assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
            if(at.endsWith("cleanup"))assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED),h.completions.single())
            if(at=="normal")assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),h.completions.single())
            if(at=="stale")assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),h.completions.single())
            assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun actionAndOperationalFullValuesCanRepeatWithoutPersistentRevision()=H().use{h->
        val a=h.actionRow()
        fun fields(title:String)=NewAction(title,a.description,a.startAt,a.expectedEndAt)
        fun change(expected:Action,title:String)=h.write<ActionChangeResult>(WorkspaceWriterFamily.ACTION_EDIT,{it is ActionChangeResult.Rejected},{null}){ActionWriterStore(h.data,it).change(expected,fields(title))}
        assertTrue(change(a,"B") is ActionChangeResult.Applied)
        assertTrue(change(h.actionRow(),a.title) is ActionChangeResult.Applied);assertEquals(a,h.actionRow())
        assertTrue(change(a,"Old action editor after ABA") is ActionChangeResult.Applied)
        h.advance(ActionStatus.PREPARATION);val original=h.state()
        fun update(expected:PersonOperationalState,status:OperationalStatus)=h.write<OperationsResult<Unit>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},{null}){c->
            // Same injected millisecond is a clock possibility, not a production revision.
            OperationsStore(h.data,c){original.updatedAt}.snapshot("a","p0",expected,status,null,"")
        }
        value(update(original,OperationalStatus.ON_SITE));value(update(h.state(),original.status))
        assertEquals(original,h.state());assertTrue(update(original,OperationalStatus.LEFT) is OperationsResult.Value)
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
        for(family in listOf(WorkspaceWriterFamily.ACTION_CREATE,WorkspaceWriterFamily.ACTION_EDIT,WorkspaceWriterFamily.ACTION_STATUS,WorkspaceWriterFamily.PERSON,WorkspaceWriterFamily.OPERATIONS,WorkspaceWriterFamily.MATERIAL,WorkspaceWriterFamily.EXPENSE,WorkspaceWriterFamily.PROTOCOL))
            for(failure in listOf("end-before","end-after","database","key")) H().use {h->
                when(failure){"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;"database"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};else->h.keyCleanup={error("SYNTHETIC_CLEANUP")}}
                if(family==WorkspaceWriterFamily.PROTOCOL)h.exec("UPDATE actions SET status='ACTIVE' WHERE id='a'")
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
            if(family==WorkspaceWriterFamily.PROTOCOL)h.exec("UPDATE actions SET status='ACTIVE' WHERE id='a'")
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
            if(family==WorkspaceWriterFamily.PROTOCOL)h.exec("UPDATE actions SET status='ACTIVE' WHERE id='a'")
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
    private fun <T> H.material(work:suspend(MaterialStore)->OperationsResult<T>)=
        write<OperationsResult<T>>(WorkspaceWriterFamily.MATERIAL,{it is OperationsResult.Rejected},
            {OperationsResult.Rejected(OperationsFailure.STALE)}){work(MaterialStore(data,it){++time})}
    private fun H.materialRow(id:String)=runBlocking{data.materials.get("a",id)?.model()}
    private fun H.materialQueue(publish:(OperationsResult<MaterialItem>)->Unit={},work:suspend(MaterialStore)->OperationsResult<MaterialItem>)=
        queue<OperationsResult<MaterialItem>>(WorkspaceWriterFamily.MATERIAL,{it is OperationsResult.Rejected},
            {OperationsResult.Rejected(OperationsFailure.STALE)},publish){work(MaterialStore(data,it){++time})}

    @Test fun materialCreateEditDeleteAreConfirmedOuterCommitsAndReadsDoNotAdvanceEpoch()=H().use{h->
        val item=value(h.material{it.create("a",MaterialInput(" Synthetic material "," 2 "," Box ","p0",sourceTemplateKey="GLUE"))})
        assertEquals("Synthetic material",item.name);assertEquals("2",item.quantityLabel);assertEquals("Box",item.placeLabel)
        val next=value(h.material{it.edit("a",item,item.input().copy(name="Changed",sourceTemplateKey="OTHER"))})
        assertEquals(item.createdAt,next.createdAt);assertEquals(item.sourceTemplateKey,next.sourceTemplateKey)
        val epoch=h.epoch.mutationEpoch
        runBlocking{
            val store=MaterialStore(h.data,h.context){0}
            assertEquals(MaterialSummary(1),value(store.summary("a")))
            assertEquals(listOf(next),value(store.board("a")).items)
        }
        assertEquals(epoch,h.epoch.mutationEpoch)
        assertEquals(OperationsResult.Value(Unit),h.material{it.delete("a",next)})
        assertNull(h.materialRow(item.id));assertEquals(3L,h.epoch.mutationEpoch)
        assertTrue(h.requests.all{it.family==WorkspaceWriterFamily.MATERIAL})
        assertTrue(h.completions.all{it==WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)})
        assertFalse(h.epoch.ramReuseEnabled)
    }
    @Test fun materialStaleEditorsAndDeleteKeepFirstCommittedItem()=H().use{h->
        val a=value(h.material{it.create("a",MaterialInput("A"))})
        val b=value(h.material{it.edit("a",a,a.input().copy(name="B"))})
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.material{it.edit("a",a,a.input().copy(name="C"))})
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.material{it.delete("a",a)})
        assertEquals(b,h.materialRow(a.id))
        assertTrue(h.completions.takeLast(2).all{it==WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)})
        assertTrue(h.material{it.edit("a",b,b.input().copy(name="Fresh"))} is OperationsResult.Value)
    }
    @Test fun materialComparesEveryExpectedFieldBeforeWriting()=H().use{h->
        val a=value(h.material{it.create("a",MaterialInput("A"))})
        var writes=0
        h.db.beforePrepare={if(it.startsWith("UPDATE materials") || it.startsWith("DELETE FROM materials"))writes++}
        for(old in listOf(a.copy(actionId="b"),a.copy(name="Other"),a.copy(quantityLabel="2"),a.copy(placeLabel="Box"),
            a.copy(responsiblePersonId="p0"),a.copy(linkedTaskId="t"),a.copy(sourceTemplateKey="GLUE"),a.copy(createdAt=100))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.material{it.edit("a",old,a.input())})
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.material{it.delete("a",old)})
        }
        assertEquals(0,writes);assertEquals(a,h.materialRow(a.id))
    }
    @Test fun materialInvalidMissingForeignReferencesAndActionsRollbackWithoutChanges()=H().use{h->
        h.exec("INSERT INTO persons VALUES('foreign','b','Synthetic foreign','synthetic foreign',0)")
        h.exec("INSERT INTO tasks VALUES('foreign-task','b','Synthetic','NORMAL','OPEN',NULL,NULL,NULL,NULL,NULL,1,1,NULL)")
        val a=value(h.material{it.create("a",MaterialInput("A"))})
        for(input in listOf(MaterialInput(" "),MaterialInput("x".repeat(121)),MaterialInput("A","x".repeat(41)),MaterialInput("A",placeLabel="x".repeat(81)))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.material{it.create("a",input)})
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.material{it.edit("a",a,input)})
        }
        for(input in listOf(MaterialInput("A",responsiblePersonId="foreign"),MaterialInput("A",linkedTaskId="foreign-task"))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.material{it.create("a",input)})
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.material{it.edit("a",a,input)})
        }
        for(action in listOf("b","missing")){
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.material{it.edit(action,a,a.input())})
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.material{it.delete(action,a)})
        }
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.material{it.delete("a",a.copy(id="missing"))})
        assertEquals(a,h.materialRow(a.id));assertEquals(1L,h.count("materials"))
        assertTrue(h.completions.drop(1).all{it==WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)})
    }
    @Test fun materialAllLifecycleGatesKeepDomainRules() {
        for(status in ActionStatus.entries)H().use{h->
            val a=value(h.material{it.create("a",MaterialInput("A"))})
            h.exec("UPDATE actions SET status='${status.name}',completedAt=${if(status==ActionStatus.COMPLETED)20 else "NULL"} WHERE id='a'")
            val create=h.material{it.create("a",MaterialInput("B"))}
            val edit=h.material{it.edit("a",a,a.input().copy(name="C"))}
            val current=h.materialRow(a.id)!!
            val delete=h.material{it.delete("a",current)}
            for(result in listOf(create,edit,delete))if(MaterialRules.mayWrite(status))assertTrue(result is OperationsResult.Value)
                else assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),result)
            runBlocking{val store=MaterialStore(h.data,h.context){0};
                if(MaterialRules.mayRead(status))assertTrue(store.summary("a") is OperationsResult.Value)
                else assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),store.summary("a"))}
        }
    }
    @Test fun materialSqlFailureAfterInsertRollsBackActualSideeffects()=H().use{h->
        h.exec("CREATE TRIGGER synthetic_material_failure AFTER INSERT ON materials BEGIN INSERT INTO materials SELECT * FROM materials WHERE id=NEW.id; END")
        var calls=0;h.materialQueue({calls++}){it.create("a",MaterialInput("A"))}
        h.worker.drain();h.main.drain()
        assertEquals(0,calls);assertEquals(0L,h.count("materials"));assertEquals(1,h.blocks)
        // Exception propagation provides no positive outer-end evidence, although SQL rolled back.
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
    }
    @Test fun materialExplicitRejectionAfterSideeffectsRequiresConfirmedRollback() {
        for(mode in listOf("normal","rollback","db-cleanup","key-cleanup"))H().use{h->
            if(mode=="rollback")h.failRollback=true
            if(mode=="db-cleanup")h.dbCleanup={error("SYNTHETIC_CLEANUP")}
            if(mode=="key-cleanup")h.keyCleanup={error("SYNTHETIC_CLEANUP")}
            var calls=0
            h.materialQueue({calls++;assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),it)}){
                value(it.create("a",MaterialInput("Temporary")))
                OperationsResult.Rejected(OperationsFailure.INVALID)
            }
            h.worker.drain();h.main.drain()
            assertEquals(if(mode=="normal")1 else 0,calls)
            assertEquals(if(mode=="rollback")1L else 0L,h.count("materials"))
            assertEquals(if(mode=="rollback")WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.ROLLED_BACK,h.completions.single().outcome)
            assertEquals(if(mode=="normal")WorkspaceWriteCleanup.COMPLETE else WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
        }
    }
    @Test fun materialAllWritesKeepCommitCleanupScopeAndOnceProofSeparate() {
        for(operation in listOf("create","edit","delete"))for(mode in listOf("normal","end-before","end-after","db-cleanup","key-cleanup","initial","queued","during-write","commit","after-commit","publication","permission","generation","action","blocked"))H().use{h->
            val a=value(h.material{it.create("a",MaterialInput("A"))});h.completions.clear()
            when(mode){"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;"db-cleanup"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};
                "key-cleanup"->h.keyCleanup={error("SYNTHETIC_CLEANUP")};"initial"->h.ready=false;"commit"->h.beforeEnd={h.ready=false};
                "after-commit"->h.afterEnd={h.ready=false};"publication"->h.beforePublication={h.ready=false};"blocked"->h.storageReady=false}
            if(mode=="during-write")h.db.beforePrepare={if(it.startsWith("INSERT INTO materials") || it.startsWith("UPDATE materials") || it.startsWith("DELETE FROM materials"))h.ready=false}
            var calls=0
            h.queue<OperationsResult<*>>(WorkspaceWriterFamily.MATERIAL,{it is OperationsResult.Rejected},{null},{calls++}){c->
                val store=MaterialStore(h.data,c){50}
                when(operation){"create"->store.create("a",MaterialInput("Changed"));"edit"->store.edit("a",a,a.input().copy(name="Changed"));else->store.delete("a",a)}
            }
            if(mode=="queued")h.ready=false
            val worker=h.worker.jobs.firstOrNull();h.worker.drain();worker?.run()
            when(mode){"permission"->h.permission++;"generation"->h.generation++;"action"->h.action="b"}
            val main=h.main.jobs.firstOrNull();h.main.drain();main?.run()
            assertEquals("$operation/$mode",if(mode=="normal")1 else 0,calls)
            val committed=mode in listOf("normal","end-after","db-cleanup","key-cleanup","after-commit","publication","permission","generation","action")
            when(operation){"create"->assertEquals(if(committed)2L else 1L,h.count("materials"));"edit"->assertEquals(if(committed)"Changed" else "A",h.materialRow(a.id)!!.name);else->assertEquals(!committed,h.materialRow(a.id)!=null)}
            if(mode=="initial")assertTrue(h.completions.isEmpty())
            else if(mode.startsWith("end"))assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
            else if(committed)assertEquals(WorkspaceWriteOutcome.COMMITTED,h.completions.single().outcome)
            if(mode.endsWith("cleanup"))assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
            if(mode=="normal")assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),h.completions.single())
            assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun materialPendingCannotBeBypassedByMaterialTaskOrOperationsAndReverse() {
        for(first in listOf(WorkspaceWriterFamily.MATERIAL,WorkspaceWriterFamily.TASK,WorkspaceWriterFamily.OPERATIONS))H().use{h->
            h.queue<OperationsResult<*>>(first,{it is OperationsResult.Rejected},{null},{}){c->
                when(first){
                    WorkspaceWriterFamily.MATERIAL->MaterialStore(h.data,c){0}.create("a",MaterialInput("A"))
                    WorkspaceWriterFamily.TASK->TaskStore(HostTaskData(h.db),c){0}.create("a",TaskInput("Synthetic task"))
                    else->OperationsStore(h.data,c){0}.role("a",null,"New role")
                }
            }
            for(other in listOf(WorkspaceWriterFamily.MATERIAL,WorkspaceWriterFamily.TASK,WorkspaceWriterFamily.OPERATIONS)){
                var result:OperationsResult<Unit>?=null
                h.queue<OperationsResult<Unit>>(other,{it is OperationsResult.Rejected},{OperationsResult.Rejected(OperationsFailure.STALE)},{result=it}){error("Must not execute")}
                assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),result)
            }
            assertEquals(1,h.worker.jobs.size);assertEquals(1L,h.epoch.mutationEpoch);assertTrue(h.completions.isEmpty())
            h.worker.drain();h.main.drain();assertEquals(1,h.completions.size)
            assertEquals(if(first==WorkspaceWriterFamily.MATERIAL)1L else 0L,h.count("materials"))
            assertEquals(if(first==WorkspaceWriterFamily.TASK)1L else 0L,h.count("tasks"))
            assertEquals(if(first==WorkspaceWriterFamily.OPERATIONS)2L else 1L,h.count("roles"))
        }
    }
    @Test fun materialValueWithoutOuterCommitEvidenceCannotPublish()=H().use{h->
        var calls=0
        h.boundary.submit<OperationsResult<Unit>>(WorkspaceMutation(WorkspaceWriterFamily.MATERIAL,"a"),{calls++},
            {null},{it is OperationsResult.Value}){_,_->OperationsResult.Value(Unit)}
        h.worker.drain();h.main.drain()
        assertEquals(0,calls);assertEquals(0L,h.count("materials"));assertEquals(1,h.blocks)
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED),h.completions.single())
    }
    @Test fun materialLostMainDispatchStaysPendingAndNoRetryCanRun()=H().use{h->
        h.materialQueue{it.create("a",MaterialInput("A"))};h.worker.drain()
        h.main.jobs.clear() // physical commit, deliberately lost dispatch, no finish evidence delivered
        var busy:OperationsResult<MaterialItem>?=null
        h.materialQueue({busy=it}){error("No retry")}
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),busy)
        assertTrue(h.worker.jobs.isEmpty());assertTrue(h.completions.isEmpty());assertEquals(1L,h.count("materials"))
    }
    @Test fun materialFailedDispatchNeverPublishesSuccess() {
        for(failWorker in listOf(true,false))H().use{h->
            if(failWorker)h.worker.onQueue={error("SYNTHETIC_DISPATCH")}else h.main.onQueue={error("SYNTHETIC_DISPATCH")}
            var calls=0;h.materialQueue({calls++}){it.create("a",MaterialInput("A"))}
            if(!failWorker)assertThrows(IllegalStateException::class.java){h.worker.drain()}
            assertEquals(0,calls);assertEquals(if(failWorker)0L else 1L,h.count("materials"))
            if(failWorker){assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),h.completions.single());assertEquals(1,h.blocks)}
            else{assertTrue(h.completions.isEmpty());var busy:OperationsResult<MaterialItem>?=null;h.materialQueue({busy=it}){error("No retry")};assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),busy)}
        }
    }
    companion object {
        private val coreFamilies=WorkspaceWriterFamily.entries.filter{it!=WorkspaceWriterFamily.TASK}
    }
}
