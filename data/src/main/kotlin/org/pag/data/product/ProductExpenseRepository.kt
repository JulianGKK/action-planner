package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** Existing short-lived READY/OPERATION/STEP/COMMIT/publication pattern, no P3b reads. */
class ProductExpenseRepository(app: Context, private val access: StorageAccess, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?, private val worker: Executor, private val main: Executor,
    private val blocked: () -> Unit, private val now: () -> Long = System::currentTimeMillis) : ExpenseRepository {
    private val database = ActionDatabaseAccess(app.applicationContext)
    override fun summary(actionId: String, publish: (OperationsResult<ExpenseSummary>) -> Unit) =
        run(publish) { d, c -> d.withReadTransaction { ExpenseStore(d, c, now).summary(actionId) } }
    override fun board(actionId: String, publish: (OperationsResult<ExpenseBoard>) -> Unit) =
        run(publish) { d, c -> d.withReadTransaction { ExpenseStore(d, c, now).board(actionId) } }
    override fun create(actionId: String, input: ExpenseInput, publish: (OperationsResult<Expense>) -> Unit) =
        write(publish) { it.create(actionId, input) }
    override fun edit(actionId: String, expected: Expense, input: ExpenseInput, publish: (OperationsResult<Expense>) -> Unit) =
        write(publish) { it.edit(actionId, expected, input) }
    override fun delete(actionId: String, expected: Expense, publish: (OperationsResult<Unit>) -> Unit) =
        write(publish) { it.delete(actionId, expected) }
    private fun <T> write(publish: (OperationsResult<T>) -> Unit, work: suspend (ExpenseStore) -> OperationsResult<T>) =
        run(publish) { d, c -> d.withWriteTransaction { val result = work(ExpenseStore(d, c, now)); requireOperations(c, AccessPoint.COMMIT); result } }
    private fun <T> run(publish: (OperationsResult<T>) -> Unit, work: suspend (ActionDatabase, StorageContext) -> OperationsResult<T>) {
        val c = access.acquire(AccessScope.READY) ?: return
        worker.execute {
            try {
                requireOperations(c, AccessPoint.OPERATION)
                if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
                val result = (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key -> database.use(inventory.database, key, c) { work(it, c) } }
                main.execute { c.publish { publish(result) } }
            } catch (_: Exception) { main.execute { if (c.check(AccessPoint.STEP)) blocked() } }
        }
    }
}
internal class ExpenseStore(private val db: ActionDatabase, private val c: StorageContext, private val now: () -> Long) {
    private suspend fun <T> step(work: suspend () -> T): T { requireOperations(c); return work() }
    private fun denied(reason: OperationsFailure) = OperationsResult.Rejected(reason)
    private suspend fun permission(a: String, write: Boolean): OperationsFailure? {
        val status = step { db.actions().get(a)?.model()?.status } ?: return OperationsFailure.NOT_FOUND
        return if (if (write) ExpenseRules.mayWrite(status) else ExpenseRules.mayRead(status)) null else OperationsFailure.NOT_ALLOWED
    }
    private suspend fun materialize(row: ExpenseRow): Expense {
        // SQLite affinity alone does not prevent REAL values from being coerced by getLong.
        if (step { db.expenses().integerParent(row.actionId, row.id) } != 1 ||
            step { db.expenses().invalidPaymentTypes(row.actionId, row.id) } != 0) throw ProductStorageFailure()
        val children = step { db.expenses().payments(row.actionId, row.id) }
        if (children.map { it.position } != children.indices.toList() || children.any { it.actionId != row.actionId || it.expenseId != row.id }) throw ProductStorageFailure()
        val m = Expense(row.id, row.actionId, row.title, row.totalCents, children.map { ExpensePayment(it.payerLabel, it.amountCents) }, row.createdAt)
        if (!ExpenseRules.valid(m.input()) || ExpenseRules.normalized(m.input()) != m.input()) throw ProductStorageFailure()
        return m
    }
    suspend fun summary(a: String): OperationsResult<ExpenseSummary> {
        permission(a, false)?.let { return denied(it) }
        if (step { db.expenses().invalidTotals(a) } != 0) throw ProductStorageFailure()
        val s = step { db.expenses().summary(a) }
        if (s.count < 0 || s.totalCents < 0 || (s.count == 0) != (s.totalCents == 0L)) throw ProductStorageFailure()
        return OperationsResult.Value(ExpenseSummary(s.count, s.totalCents))
    }
    suspend fun board(a: String): OperationsResult<ExpenseBoard> {
        permission(a, false)?.let { return denied(it) }
        return OperationsResult.Value(ExpenseBoard(a, step { db.expenses().list(a) }.map { materialize(it) }))
    }
    private fun Expense.row() = ExpenseRow(id, actionId, title, totalCents, createdAt)
    private fun Expense.children() = payments.mapIndexed { index, p -> ExpensePaymentRow(actionId, id, index, p.payerLabel, p.amountCents) }
    suspend fun create(a: String, input: ExpenseInput): OperationsResult<Expense> {
        permission(a, true)?.let { return denied(it) }
        val i = ExpenseRules.normalized(input)
        if (!ExpenseRules.valid(i)) return denied(OperationsFailure.INVALID)
        val m = Expense(UUID.randomUUID().toString(), a, i.title, i.totalCents, i.payments.map { ExpensePayment(it.payerLabel, it.amountCents) }, now())
        step { db.expenses().insert(m.row()) }; step { db.expenses().insertPayments(m.children()) }
        return OperationsResult.Value(m)
    }
    private suspend fun existing(a: String, expected: Expense): OperationsResult<Expense> {
        permission(a, true)?.let { return denied(it) }
        val row = step { db.expenses().get(a, expected.id) } ?: return denied(OperationsFailure.NOT_FOUND)
        val old = materialize(row)
        if (expected.actionId != a || expected != old) return denied(OperationsFailure.STALE)
        return OperationsResult.Value(old)
    }
    suspend fun edit(a: String, expected: Expense, input: ExpenseInput): OperationsResult<Expense> {
        val old = existing(a, expected); if (old is OperationsResult.Rejected) return old
        val i = ExpenseRules.normalized(input)
        if (!ExpenseRules.valid(i)) return denied(OperationsFailure.INVALID)
        val m = expected.copy(title = i.title, totalCents = i.totalCents, payments = i.payments.map { ExpensePayment(it.payerLabel, it.amountCents) })
        if (step { db.expenses().update(m.row()) } != 1) throw ProductStorageFailure()
        step { db.expenses().clearPayments(a, m.id) }; step { db.expenses().insertPayments(m.children()) }
        return OperationsResult.Value(m)
    }
    suspend fun delete(a: String, expected: Expense): OperationsResult<Unit> {
        val old = existing(a, expected); if (old is OperationsResult.Rejected) return old
        if (!ExpenseRules.mayDelete(step { db.actions().get(a)!!.model().status })) return denied(OperationsFailure.NOT_ALLOWED)
        if (step { db.expenses().delete(a, expected.id) } != 1) throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
}
