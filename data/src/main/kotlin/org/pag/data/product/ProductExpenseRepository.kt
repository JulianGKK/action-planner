package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.useWriterConnection
import org.pag.domain.*
import java.util.UUID
import java.util.Collections
import java.util.concurrent.Executor

/** Existing short-lived READY/OPERATION/STEP/COMMIT/publication pattern, no P3b reads. */
class ProductExpenseRepository(app: Context, private val access: StorageAccess, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?, private val worker: Executor, private val main: Executor,
    private val blocked: () -> Unit,
    mutations: WorkspaceMutationInvalidation = UncachedWorkspaceMutations(), private val now: () -> Long = System::currentTimeMillis) : ExpenseRepository {
    private val readExclusion=FullviewReadExclusion(app,access,inventory,owner,worker,main,blocked,mutations,FullviewPreflight.expense)
    internal fun bindWorkspaceMemory(memory:OutstandingCoreRead){readExclusion.bind(memory)}
    private val writerBudget=OriginalWriterBudget(access,readExclusion::record)
    private val database = ActionDatabaseAccess(app.applicationContext)
    private val mutationBoundary = WorkspaceWriteBoundary(access, worker, main, mutations, blocked)
    override fun summary(actionId: String, publish: (OperationsResult<ExpenseSummary>) -> Unit) =
        run(actionId,publish) { d, c -> d.withReadTransaction { ExpenseStore(d, c, now).summary(actionId) } }
    override fun board(actionId: String, publish: (OperationsResult<ExpenseBoard>) -> Unit) =
        run(actionId,publish) { d, c -> d.withReadTransaction { ExpenseStore(d, c, now).board(actionId) } }
    override fun create(actionId: String, input: ExpenseInput, publish: (OperationsResult<Expense>) -> Unit) {
        if(!reserveWrite(actionId,input,null,publish))return
        val submitted = freezeExpenseInput(input)
        write(actionId,null,publish) { it.create(actionId, submitted) }
    }
    override fun edit(actionId: String, expected: Expense, input: ExpenseInput, publish: (OperationsResult<Expense>) -> Unit) {
        if(!reserveWrite(actionId,input,expected,publish))return
        val original = freezeExpenseExpected(expected)
        val submitted = freezeExpenseInput(input)
        write(actionId,expected.id,publish) { it.edit(actionId, original, submitted) }
    }
    override fun delete(actionId: String, expected: Expense, publish: (OperationsResult<Unit>) -> Unit) {
        if(!reserveWrite(actionId,null,expected,publish))return
        val original = freezeExpenseExpected(expected)
        write(actionId,expected.id,publish) { it.delete(actionId, original) }
    }
    private fun <T> reserveWrite(a:String,input:ExpenseInput?,expected:Expense?,publish:(OperationsResult<T>)->Unit):Boolean {
        var chars=(input?.title?.length?.toLong()?:0)+(expected?.title?.length?:0)
        var items=4L
        for(p in input?.payments.orEmpty()){chars+=p.payerLabel.length;items++}
        for(p in expected?.payments.orEmpty()){chars+=p.payerLabel.length;items++}
        if(writerBudget.reserve(a,chars,items))return true
        access.acquire(AccessScope.READY)?.publish{publish(OperationsResult.Rejected(OperationsFailure.BUDGET_REFUSED))};return false
    }
    private fun <T> write(actionId: String,id:String?, publish: (OperationsResult<T>) -> Unit,
        work: suspend (ExpenseStore) -> OperationsResult<T>) =
        mutationBoundary.submit<OperationsResult<T>>(WorkspaceMutation(WorkspaceWriterFamily.EXPENSE, actionId), publish,
            { OperationsResult.Rejected(OperationsFailure.STALE) }, { it is OperationsResult.Value }) { c, progress ->
            if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
            (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key ->
                database.use(inventory.database, key, c) { db ->
                    db.useWriterConnection { tx ->
                        confirmedWorkspaceTransaction(tx, c, progress, { it is OperationsResult.Rejected }) {
                            if(!writerBudget.preflight(tx,c,actionId,FullviewPreflight.expense,id))OperationsResult.Rejected(OperationsFailure.BUDGET_REFUSED)
                            else work(ExpenseStore(db, c, now))
                        }
                    }
                }
            }
        }
    private fun <T> run(actionId:String,publish: (OperationsResult<T>) -> Unit, work: suspend (ActionDatabase, StorageContext) -> OperationsResult<T>) {
        readExclusion.read(actionId,publish,{budget->OperationsResult.Rejected(if(budget)OperationsFailure.BUDGET_REFUSED else OperationsFailure.OWNER_UNCONFIRMED)},work)
    }
}
/** Payment leaves contain only immutable Strings/Longs. Copy and wrap the list before queue,
 * so caller mutation or a MutableList cast cannot change the submitted CAS/draft snapshot. */
internal fun freezeExpenseInput(input: ExpenseInput) = input.copy(payments = Collections.unmodifiableList(ArrayList(input.payments)))
internal fun freezeExpenseExpected(expected: Expense) = expected.copy(payments = Collections.unmodifiableList(ArrayList(expected.payments)))

internal class ExpenseStore(private val data: CoreWriterData, private val c: StorageContext, private val now: () -> Long) {
    constructor(db: ActionDatabase, c: StorageContext, now: () -> Long) : this(RoomCoreWriterData(db), c, now)
    private suspend fun <T> step(work: suspend () -> T): T { requireOperations(c); return work() }
    private fun denied(reason: OperationsFailure) = OperationsResult.Rejected(reason)
    private suspend fun permission(a: String, write: Boolean): OperationsFailure? {
        val status = step { data.actions.get(a)?.model()?.status } ?: return OperationsFailure.NOT_FOUND
        return if (if (write) ExpenseRules.mayWrite(status) else ExpenseRules.mayRead(status)) null else OperationsFailure.NOT_ALLOWED
    }
    private suspend fun materialize(row: ExpenseRow): Expense {
        // SQLite affinity alone does not prevent REAL values from being coerced by getLong.
        if (step { data.expenses.integerParent(row.actionId, row.id) } != 1 ||
            step { data.expenses.invalidPaymentTypes(row.actionId, row.id) } != 0) throw ProductStorageFailure()
        val children = step { data.expenses.payments(row.actionId, row.id) }
        if (children.map { it.position } != children.indices.toList() || children.any { it.actionId != row.actionId || it.expenseId != row.id }) throw ProductStorageFailure()
        val m = Expense(row.id, row.actionId, row.title, row.totalCents, children.map { ExpensePayment(it.payerLabel, it.amountCents) }, row.createdAt)
        if (!ExpenseRules.valid(m.input()) || ExpenseRules.normalized(m.input()) != m.input()) throw ProductStorageFailure()
        return m
    }
    suspend fun detail(a:String,id:String):OperationsResult<Expense> {
        permission(a,false)?.let{return OperationsResult.Rejected(it)}
        val row=step{data.expenses.get(a,id)} ?: return OperationsResult.Rejected(OperationsFailure.NOT_FOUND)
        return OperationsResult.Value(materialize(row))
    }
    suspend fun summary(a: String): OperationsResult<ExpenseSummary> {
        permission(a, false)?.let { return denied(it) }
        if (step { data.expenses.invalidTotals(a) } != 0) throw ProductStorageFailure()
        val s = step { data.expenses.summary(a) }
        if (s.count < 0 || s.totalCents < 0 || (s.count == 0) != (s.totalCents == 0L)) throw ProductStorageFailure()
        return OperationsResult.Value(ExpenseSummary(s.count, s.totalCents))
    }
    suspend fun board(a: String): OperationsResult<ExpenseBoard> {
        permission(a, false)?.let { return denied(it) }
        return OperationsResult.Value(ExpenseBoard(a, step { data.expenses.list(a) }.map { materialize(it) }))
    }
    private fun Expense.row() = ExpenseRow(id, actionId, title, totalCents, createdAt)
    private fun Expense.children() = payments.mapIndexed { index, p -> ExpensePaymentRow(actionId, id, index, p.payerLabel, p.amountCents) }
    suspend fun create(a: String, input: ExpenseInput): OperationsResult<Expense> {
        permission(a, true)?.let { return denied(it) }
        val i = ExpenseRules.normalized(input)
        if (!ExpenseRules.valid(i)) return denied(OperationsFailure.INVALID)
        val m = Expense(UUID.randomUUID().toString(), a, i.title, i.totalCents, i.payments.map { ExpensePayment(it.payerLabel, it.amountCents) }, now())
        step { data.expenses.insert(m.row()) }; step { data.expenses.insertPayments(m.children()) }
        return OperationsResult.Value(m)
    }
    private suspend fun existing(a: String, expected: Expense): OperationsResult<Expense> {
        permission(a, true)?.let { return denied(it) }
        val row = step { data.expenses.get(a, expected.id) } ?: return denied(OperationsFailure.NOT_FOUND)
        val old = materialize(row)
        if (expected.actionId != a || expected != old) return denied(OperationsFailure.STALE)
        return OperationsResult.Value(old)
    }
    suspend fun edit(a: String, expected: Expense, input: ExpenseInput): OperationsResult<Expense> {
        val old = existing(a, expected); if (old is OperationsResult.Rejected) return old
        val i = ExpenseRules.normalized(input)
        if (!ExpenseRules.valid(i)) return denied(OperationsFailure.INVALID)
        val m = expected.copy(title = i.title, totalCents = i.totalCents, payments = i.payments.map { ExpensePayment(it.payerLabel, it.amountCents) })
        if (step { data.expenses.update(m.row()) } != 1) throw ProductStorageFailure()
        step { data.expenses.clearPayments(a, m.id) }; step { data.expenses.insertPayments(m.children()) }
        return OperationsResult.Value(m)
    }
    suspend fun delete(a: String, expected: Expense): OperationsResult<Unit> {
        val old = existing(a, expected); if (old is OperationsResult.Rejected) return old
        if (!ExpenseRules.mayDelete(step { data.actions.get(a)!!.model().status })) return denied(OperationsFailure.NOT_ALLOWED)
        if (step { data.expenses.delete(a, expected.id) } != 1) throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
}
