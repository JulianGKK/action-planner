package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** Existing short-lived READY/OPERATION/STEP/COMMIT/publication pattern, no P3b reads. */
class ProductMaterialRepository(app: Context, private val access: StorageAccess, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?, private val worker: Executor, private val main: Executor,
    private val blocked: () -> Unit, private val now: () -> Long = System::currentTimeMillis) : MaterialRepository {
    private val database = ActionDatabaseAccess(app.applicationContext)
    override fun summary(actionId: String, publish: (OperationsResult<MaterialSummary>) -> Unit) =
        run(publish) { d, c -> d.withReadTransaction { MaterialStore(d, c, now).summary(actionId) } }
    override fun board(actionId: String, publish: (OperationsResult<MaterialBoard>) -> Unit) =
        run(publish) { d, c -> d.withReadTransaction { MaterialStore(d, c, now).board(actionId) } }
    override fun create(actionId: String, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit) =
        write(publish) { it.create(actionId, input) }
    override fun edit(actionId: String, expected: MaterialItem, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit) =
        write(publish) { it.edit(actionId, expected, input) }
    override fun delete(actionId: String, expected: MaterialItem, publish: (OperationsResult<Unit>) -> Unit) =
        write(publish) { it.delete(actionId, expected) }
    private fun <T> write(publish: (OperationsResult<T>) -> Unit, work: suspend (MaterialStore) -> OperationsResult<T>) =
        run(publish) { d, c -> d.withWriteTransaction { val result = work(MaterialStore(d, c, now)); requireOperations(c, AccessPoint.COMMIT); result } }
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
internal class MaterialStore(private val db: ActionDatabase, private val c: StorageContext, private val now: () -> Long) {
    private suspend fun <T> step(work: suspend () -> T): T { requireOperations(c); return work() }
    private fun denied(reason: OperationsFailure) = OperationsResult.Rejected(reason)
    private suspend fun permission(a: String, write: Boolean): OperationsFailure? {
        val status = step { db.actions().get(a)?.model()?.status } ?: return OperationsFailure.NOT_FOUND
        return if (if (write) MaterialRules.mayWrite(status) else MaterialRules.mayRead(status)) null else OperationsFailure.NOT_ALLOWED
    }
    suspend fun summary(a: String): OperationsResult<MaterialSummary> {
        permission(a, false)?.let { return denied(it) }
        return OperationsResult.Value(MaterialSummary(step { db.materials().count(a) }))
    }
    suspend fun board(a: String): OperationsResult<MaterialBoard> {
        permission(a, false)?.let { return denied(it) }
        return OperationsResult.Value(MaterialBoard(a, step { db.materials().list(a) }.map { it.model() }))
    }
    private suspend fun references(a: String, i: MaterialInput): Boolean {
        val person = i.responsiblePersonId; val task = i.linkedTaskId
        if (person != null && step { db.persons().get(a, person) } == null) return false
        if (task != null && step { db.tasks().get(a, task) } == null) return false
        return true
    }
    suspend fun create(a: String, input: MaterialInput): OperationsResult<MaterialItem> {
        permission(a, true)?.let { return denied(it) }
        val i = MaterialRules.normalized(input)
        if (!MaterialRules.valid(i)) return denied(OperationsFailure.INVALID)
        if (!references(a, i)) return denied(OperationsFailure.NOT_FOUND)
        val m = MaterialItem(UUID.randomUUID().toString(), a, i.name, i.quantityLabel, i.placeLabel,
            i.responsiblePersonId, i.linkedTaskId, i.sourceTemplateKey, now())
        step { db.materials().insert(m.row()) }
        return OperationsResult.Value(m)
    }
    private suspend fun existing(a: String, expected: MaterialItem): OperationsResult<MaterialItem> {
        permission(a, true)?.let { return denied(it) }
        val old = step { db.materials().get(a, expected.id) }?.model() ?: return denied(OperationsFailure.NOT_FOUND)
        if (expected.actionId != a || expected != old) return denied(OperationsFailure.STALE)
        return OperationsResult.Value(old)
    }
    suspend fun edit(a: String, expected: MaterialItem, input: MaterialInput): OperationsResult<MaterialItem> {
        val old = existing(a, expected); if (old is OperationsResult.Rejected) return old
        val i = MaterialRules.normalized(input)
        if (!MaterialRules.valid(i)) return denied(OperationsFailure.INVALID)
        if (!references(a, i)) return denied(OperationsFailure.NOT_FOUND)
        val updated = expected.copy(name = i.name, quantityLabel = i.quantityLabel, placeLabel = i.placeLabel,
            responsiblePersonId = i.responsiblePersonId, linkedTaskId = i.linkedTaskId)
        if (step { db.materials().update(updated.row()) } != 1) throw ProductStorageFailure()
        return OperationsResult.Value(updated)
    }
    suspend fun delete(a: String, expected: MaterialItem): OperationsResult<Unit> {
        val old = existing(a, expected); if (old is OperationsResult.Rejected) return old
        // mayDelete and mayWrite have the same two phases, explicitly separate domain contracts.
        if (!MaterialRules.mayDelete(step { db.actions().get(a)!!.model().status })) return denied(OperationsFailure.NOT_ALLOWED)
        if (step { db.materials().delete(a, expected.id) } != 1) throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
}
