package org.pag.data.product

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.B2Native
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor

/** Standalone synthetic data-test package, never product files, auth or device credentials. */
class ExpenseStorageTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var allowed = true; private var generation = 1L; private var denyCommit = false; private var time = 1000L
    private val owners = mutableListOf<SessionDbKeyOwner>()
    private val direct = Executor { it.run() }
    private fun context(scope: AccessScope = AccessScope.READY): StorageContext = object : StorageContext {
        private val captured = this@ExpenseStorageTest.generation
        override val generation = captured; override val scope = scope
        override fun check(point: AccessPoint): Boolean {
            if (denyCommit && point == AccessPoint.COMMIT) allowed = false
            return allowed && captured == this@ExpenseStorageTest.generation
        }
        override fun publish(result: () -> Unit): Boolean { if (!check(AccessPoint.PUBLICATION)) return false; result(); return true }
    }
    private fun fixture(): ProductInventory {
        val root = File(app.noBackupFilesDir.canonicalFile, "p8-test-${UUID.randomUUID()}")
        check(root.mkdirs()); check(root.resolve("store").mkdir())
        listOf("intent", "complete", "public").forEach { root.resolve(it).writeBytes(byteArrayOf(1)) }
        root.resolve("envelope").writeBytes(ByteArray(256))
        val i = ProductInventory(root, { true }, { true })
        ActionDatabaseAccess(app).use(i.database, key, context(AccessScope.MAINTENANCE), true) { d -> d.withWriteTransaction {
            listOf("a", "b").forEach { d.actions().insert(ActionRow(it, "synthetic", "synthetic", 1, 2, 0, "PREPARATION")) }
            PersonRow("p", "a", "Motte", "motte", 0, editRevision = 0).let{seeded->d.persons().insert(seeded);d.operations().insertPlan(RolePlanRow(seeded.actionId,seeded.id,null,0))}; PersonRow("q", "b", "Meise", "meise", 0, editRevision = 0).let{seeded->d.persons().insert(seeded);d.operations().insertPlan(RolePlanRow(seeded.actionId,seeded.id,null,0))}
            val store = TaskStore(d, context()) { time }
            value(store.create("a", TaskInput("synthetic task"))); value(store.create("b", TaskInput("synthetic foreign task")))
        } }
        return i
    }
    private fun <T> use(i: ProductInventory, work: suspend (ActionDatabase) -> T): T = ActionDatabaseAccess(app).use(i.database, key, context(), work = work)
    private fun <T> write(i: ProductInventory, work: suspend (ExpenseStore) -> OperationsResult<T>): OperationsResult<T> {
        val c = context()
        return ActionDatabaseAccess(app).use(i.database, key, c) { d -> d.withWriteTransaction {
            val result = work(ExpenseStore(d, c) { time }); requireOperations(c, AccessPoint.COMMIT); result
        } }
    }
    private fun <T> value(r: OperationsResult<T>): T { assertTrue(r is OperationsResult.Value); return (r as OperationsResult.Value).value }
    private fun <T> protocolValue(r: ProtocolResult<T>): T { assertTrue(r is ProtocolResult.Value); return (r as ProtocolResult.Value).value }
    private fun reject(r: OperationsResult<*>, why: OperationsFailure) = assertEquals(OperationsResult.Rejected(why), r)
    private fun task(i: ProductInventory, a: String = "a") = use(i) { it.tasks().list(a).single().model() }
    private fun input() = ExpenseInput(" Zugtickets ",12000,listOf(ExpensePaymentInput(" Meise ",5000),ExpensePaymentInput(" Motte ",7000)))
    private fun create(i: ProductInventory, input: ExpenseInput = input()) = value(write(i) { it.create("a", input) })
    private fun board(i: ProductInventory) = value(use(i) { d -> d.withReadTransaction { ExpenseStore(d, context()) { time }.board("a") } })
    private fun state(i: ProductInventory, phase: ActionStatus) = use(i) { d -> d.withWriteTransaction {
        d.actions().update(d.actions().get("a")!!.copy(status = phase.name, completedAt = time.takeIf { phase == ActionStatus.COMPLETED }))
    } }
    private fun native(i: ProductInventory, sql: String) = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE)).use { d -> d.execSQL("PRAGMA foreign_keys=ON"); d.execSQL(sql) }
    private fun repo(i: ProductInventory, main: Executor = direct, worker: Executor = direct): ExpenseRepository {
        val owner = SessionDbKeyOwner.bind(context(), key); owners += owner
        val access = object : StorageAccess { override fun acquire(scope: AccessScope) = context(scope).takeIf { it.check(AccessPoint.OPERATION) } }
        return ProductExpenseRepository(app, access, i, { owner }, worker, main, { fail("P8 unexpected storage block") }) { time }
    }
    private fun reset() { allowed = true; denyCommit = false; generation++ }
    @After fun wipe() { owners.forEach { it.invalidate() }; key.fill(0) }
    @Test fun freshNineHasExactlyTwoNewTablesAndNoPersonFk() {
        val i=fixture();B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use { d ->assertEquals(11,d.version)
            for((table,columns) in mapOf("expenses" to listOf("id","actionId","title","totalCents","createdAt"),"expense_payments" to listOf("actionId","expenseId","position","payerLabel","amountCents"))) {
                val names=mutableListOf<String>();d.rawQuery("PRAGMA table_info($table)",emptyArray<String>()).use { while(it.moveToNext())names+=it.getString(1) };assertEquals(columns,names)
                d.rawQuery("PRAGMA foreign_key_list($table)",emptyArray<String>()).use { while(it.moveToNext())assertEquals(if(table=="expenses")"actions" else "expenses",it.getString(2)) }
            }
        }
    }
    @Test fun createEditDeleteInAllThreePhasesWithOrderedDuplicatesAndImmutableIdentity() {
        val i=fixture();for(s in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)) {
            state(i,s);val e=create(i);assertEquals(ExpenseRules.normalized(input()),e.input());assertEquals(listOf("Meise","Motte"),e.payments.map{it.payerLabel})
            val n=value(write(i){it.edit("a",e,ExpenseInput("Transporter",18000,listOf(ExpensePaymentInput("Motte",8000),ExpensePaymentInput("Alex aus der Soligruppe",10000))))})
            assertEquals(e.id,n.id);assertEquals(e.createdAt,n.createdAt);assertEquals(n,board(i).expenses.single())
            use(i){d->assertEquals(listOf(0,1),d.expenses().payments("a",n.id).map{it.position})};value(write(i){it.delete("a",n)});assertTrue(board(i).expenses.isEmpty());use(i){assertTrue(it.expenses().payments("a",e.id).isEmpty())}
        }
    }
    @Test fun persistedDraftAndDeletionPendingRejectAllAccess() { val i=fixture();val e=create(i);for(s in listOf(ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)){state(i,s);reject(use(i){ExpenseStore(it,context()){time}.board("a")},OperationsFailure.NOT_ALLOWED);reject(use(i){ExpenseStore(it,context()){time}.summary("a")},OperationsFailure.NOT_ALLOWED);reject(write(i){it.create("a",input())},OperationsFailure.NOT_ALLOWED);reject(write(i){it.edit("a",e,input())},OperationsFailure.NOT_ALLOWED);reject(write(i){it.delete("a",e)},OperationsFailure.NOT_ALLOWED)} }
    @Test fun invalidInputAndOverflowNeverWrite() { val i=fixture();for(x in listOf(input().copy(title=" "),input().copy(totalCents=0),input().copy(payments=emptyList()),ExpenseInput("x",Long.MAX_VALUE,listOf(ExpensePaymentInput("a",Long.MAX_VALUE),ExpensePaymentInput("b",1))),ExpenseInput("x",33,List(33){ExpensePaymentInput("a",1)}))){reject(write(i){it.create("a",x)},OperationsFailure.INVALID)};assertTrue(board(i).expenses.isEmpty());val e=create(i,ExpenseInput("🦋".repeat(120),32,List(32){ExpensePaymentInput("🦋".repeat(80),1)}));assertEquals(32,e.payments.size) }
    @Test fun completePaymentReplacementWithoutHistoricalChildren() { val i=fixture();val e=create(i);val n=value(write(i){it.edit("a",e,ExpenseInput("x",3,List(3){ExpensePaymentInput("same",1)}))});assertEquals(3,n.payments.size);val next=value(write(i){it.edit("a",n,ExpenseInput("x",1,listOf(ExpensePaymentInput("last",1))))});use(i){assertEquals(listOf(ExpensePaymentRow("a",e.id,0,"last",1)),it.expenses().payments("a",e.id))};assertEquals(next,board(i).expenses.single()) }
    @Test fun staleIncludesParentEveryPaymentAndOrder() { val i=fixture();val e=create(i);for(old in listOf(e.copy(title="old"),e.copy(totalCents=12001),e.copy(createdAt=0),e.copy(actionId="b"),e.copy(payments=e.payments.reversed()),e.copy(payments=listOf(ExpensePayment("other",5000),e.payments[1])))){reject(write(i){it.edit("a",old,input())},OperationsFailure.STALE);reject(write(i){it.delete("a",old)},OperationsFailure.STALE)};val n=value(write(i){it.edit("a",e,input().copy(payments=listOf(ExpensePaymentInput("changed",5000),ExpensePaymentInput("Motte",7000))))});reject(write(i){it.edit("a",e,input())},OperationsFailure.STALE);assertEquals(n,board(i).expenses.single()) }
    @Test fun payerSnapshotSurvivesPersonRenameAndDeletionWithoutTaskMutation() { val i=fixture();val e=create(i);val t=task(i);use(i){d->d.withWriteTransaction { d.persons().rename("a","p","New","new",d.persons().get("a","p")!!.editRevision) }};assertEquals(e,board(i).expenses.single());native(i,"DELETE FROM persons WHERE id='p'");assertEquals(e,board(i).expenses.single());assertEquals(t,task(i)) }
    @Test fun actionCascadeAndCrossActionPaymentFk() { val i=fixture();val e=create(i);assertThrows(RuntimeException::class.java){use(i){d->d.withWriteTransaction{d.expenses().insertPayments(listOf(ExpensePaymentRow("b",e.id,2,"x",1)))}}};native(i,"DELETE FROM actions WHERE id='a'");use(i){assertTrue(it.expenses().list("a").isEmpty());assertTrue(it.expenses().payments("a",e.id).isEmpty())} }
    @Test fun malformedPositionsMissingChildrenAndTooManyChildrenFailMaterialization() { for(mode in listOf("gap","empty","tooMany")){val i=fixture();val e=create(i);when(mode){"gap"->native(i,"UPDATE expense_payments SET position=5 WHERE expenseId='${e.id}' AND position=1");"empty"->native(i,"DELETE FROM expense_payments WHERE expenseId='${e.id}'");else->use(i){d->d.withWriteTransaction { d.expenses().insertPayments((2..32).map{ExpensePaymentRow("a",e.id,it,"x",1)}) }}};assertThrows(ProductStorageFailure::class.java){board(i)}} }
    @Test fun paymentPositionCannotWrapThroughRoomIntMapping() {
        for(position in listOf("4294967296","-4294967296")) {
            val i=fixture();val e=create(i,ExpenseInput("Synthetic",1,listOf(ExpensePaymentInput("Synthetic",1))))
            native(i,"UPDATE expense_payments SET position=$position WHERE expenseId='${e.id}'")
            assertThrows(ProductStorageFailure::class.java){board(i)}
        }
    }
    @Test fun malformedAmountsNormalizationAndWrongSumFailMaterialization() { for(sql in listOf("amountCents=5000.5","position=0.5","amountCents=0","amountCents=-1","amountCents=5001","payerLabel=' '","payerLabel=' Motte '","payerLabel='${"x".repeat(81)}'")){val i=fixture();val e=create(i);native(i,"UPDATE expense_payments SET $sql WHERE expenseId='${e.id}' AND position=0");assertThrows(ProductStorageFailure::class.java){board(i)}} }
    @Test fun malformedParentAndOverflowFailMaterialization() { for(sql in listOf("title=' '","title=' x '","totalCents=12000.5","createdAt=1000.5","totalCents=0","totalCents=-1")){val i=fixture();val e=create(i);native(i,"UPDATE expenses SET $sql WHERE id='${e.id}'");assertThrows(ProductStorageFailure::class.java){board(i)}};val i=fixture();val e=create(i);native(i,"UPDATE expense_payments SET amountCents=9223372036854775807 WHERE expenseId='${e.id}'");assertThrows(ProductStorageFailure::class.java){board(i)} }
    @Test fun summaryOnlyParentCountAndTotalsAndBoardOrder() { val i=fixture();assertEquals(ExpenseSummary(0,0),value(use(i){ExpenseStore(it,context()){time}.summary("a")}));val a=create(i);time++;val b=create(i);assertEquals(listOf(a,b),board(i).expenses);assertEquals(ExpenseSummary(2,24000),value(use(i){ExpenseStore(it,context()){time}.summary("a")}));val huge=create(i,ExpenseInput("synthetic maximum",Long.MAX_VALUE,listOf(ExpensePaymentInput("synthetic",Long.MAX_VALUE))));assertThrows(RuntimeException::class.java){use(i){ExpenseStore(it,context()){time}.summary("a")}};value(write(i){it.delete("a",huge)});native(i,"DELETE FROM expense_payments");assertEquals(ExpenseSummary(2,24000),value(use(i){ExpenseStore(it,context()){time}.summary("a")}));assertThrows(ProductStorageFailure::class.java){board(i)} }
    @Test fun commitRevokeRollsBackCreateEditAndDelete() { val i=fixture();val e=create(i);for(op in listOf("create","edit","delete")){denyCommit=true;assertThrows(StorageAccessRevoked::class.java){write(i){when(op){"create"->it.create("a",input());"edit"->it.edit("a",e,input().copy(title="changed"));else->it.delete("a",e)}}};reset();assertEquals(listOf(e),board(i).expenses)} }
    @Test fun childFailureRollsBackParentCreateAndReplacement() { val i=fixture();val e=create(i);native(i,"CREATE TRIGGER synthetic_expense_failure BEFORE INSERT ON expense_payments WHEN NEW.position=1 BEGIN SELECT RAISE(ABORT,'SYNTHETIC_CHILD_FAILURE'); END");assertThrows(RuntimeException::class.java){create(i)};assertEquals(listOf(e),board(i).expenses);assertThrows(RuntimeException::class.java){write(i){it.edit("a",e,input().copy(title="changed"))}};assertEquals(listOf(e),board(i).expenses) }
    @Test fun generationRevokeBeforePublicationSuppressesConsumer() { val i=fixture();val queue=mutableListOf<Runnable>();var calls=0;val r=repo(i,Executor{queue+=it});r.create("a",input()){calls++};generation++;queue.forEach{it.run()};assertEquals(0,calls);assertEquals(1,board(i).expenses.size) }
    @Test fun revokeBeforeWorkerPreventsWriteAndPublication() { val i=fixture();val queue=mutableListOf<Runnable>();var calls=0;val r=repo(i,worker=Executor{queue+=it});r.create("a",input()){calls++};allowed=false;queue.forEach{it.run()};reset();assertEquals(0,calls);assertTrue(board(i).expenses.isEmpty()) }
    @Test fun completedCostsNeverOpenTasksMaterialOperationsOrActionWrites() { val i=fixture();state(i,ActionStatus.COMPLETED);create(i);use(i){d->d.withWriteTransaction { reject(TaskStore(d,context()){time}.create("a",TaskInput("x")),OperationsFailure.NOT_ALLOWED);reject(MaterialStore(d,context()){time}.create("a",MaterialInput("x")),OperationsFailure.NOT_ALLOWED) }};assertFalse(OperationalRules.mayCreateRole(ActionStatus.COMPLETED));assertFalse(OperationalRules.mayUpdateSnapshot(ActionStatus.COMPLETED));assertFalse(ActionLifecycle.mayEdit(ActionStatus.COMPLETED)) }
    @Test fun migrationEightToNinePreservesP7P6AndOldTablesTriggers() {
        val i=fixture();val c=context();val material=value(use(i){d->d.withWriteTransaction{MaterialStore(d,c){time}.create("a",MaterialInput("synthetic material",responsiblePersonId="p",linkedTaskId=taskId(d)))}})
        use(i){d->assertTrue(d.advanceAction("a",ActionStatus.PREPARATION,context()) is ActionChangeResult.Applied)}
        val entry=use(i){d->d.withWriteTransaction { protocolValue(ProtocolStore(d,context()){time}.create("a",ActionStatus.ACTIVE,ProtocolInput(ProtocolClassification.KEY_EVENT,"synthetic protocol",10,description="synthetic preserved"))) }}
        val before=use(i){d->protocolValue(ProtocolStore(d,context()){time}.detail("a",entry.entryId))};val oldTask=task(i);downgrade(i);val tables=tableNames(i);val triggers=triggers(i)
        assertEquals(ExpenseSummary(0,0),value(use(i){ExpenseStore(it,context()){time}.summary("a")}));assertEquals(tables+setOf("expenses","expense_payments","memory_reports","memory_answers","memory_addenda","documents","document_jobs"),tableNames(i));assertEquals(triggers,triggers(i).filterKeys { it in triggers })
        assertEquals(material,value(use(i){MaterialStore(it,context()){time}.board("a")}).items.single());assertEquals(oldTask,task(i));assertEquals(before,use(i){d->protocolValue(ProtocolStore(d,context()){time}.detail("a",entry.entryId))});create(i)
    }
    private suspend fun taskId(d:ActionDatabase)=d.tasks().list("a").single().id
    @Test fun migrationRevokeAndRetryRetainsEightWithoutReplacement(){val i=fixture();val t=task(i);downgrade(i);denyCommit=true;assertThrows(RuntimeException::class.java){use(i){}};reset();B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{assertEquals(8,it.version)};assertFalse(tableNames(i).contains("expenses"));assertEquals(t,task(i));B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{assertEquals(11,it.version)}}
    private fun downgrade(i:ProductInventory)=B2Native.open(i.database,key,context(AccessScope.MAINTENANCE)).use{d->d.beginTransaction();try{removeExpenseSchemaForLegacyFixture(d);d.execSQL("UPDATE room_master_table SET identity_hash='f05ba9d6758029cbc9074a9d90911194' WHERE id=42");d.version=8;d.setTransactionSuccessful()}finally{d.endTransaction()}}
    private fun tableNames(i:ProductInventory):Set<String> = B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{d->val names=mutableSetOf<String>();d.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",emptyArray<String>()).use{while(it.moveToNext())names+=it.getString(0)};names}
    private fun triggers(i:ProductInventory):Map<String,String> = B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{d->val rows=mutableMapOf<String,String>();d.rawQuery("SELECT name,sql FROM sqlite_master WHERE type='trigger'",emptyArray<String>()).use{while(it.moveToNext())rows[it.getString(0)]=it.getString(1)};rows}
}
