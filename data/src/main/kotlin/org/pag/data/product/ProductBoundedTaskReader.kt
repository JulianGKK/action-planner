package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/**
 * Explicit synchronous worker-only reader; NOT constructed by ProductRuntime/ViewModel.
 * Same inventory, key owner, full DB preflight, Room read transaction and cleanup as existing
 * repositories. No retained connection/payload. Caller must also gate any later UI publication.
 * Standalone/test-only admission: NOT authorized to coexist with another retained reader graph.
 * Combined snapshot use requires a single pre-materialization scope admission/owner first.
 */
class ProductBoundedTaskReader(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val currentBinding: () -> BoundedActiveBinding?, private val blocked: () -> Unit) {
    private val database = ActionDatabaseAccess(app.applicationContext)

    fun read(binding: BoundedActiveBinding): BoundedTaskResult = boundedTaskRead(
        binding, access, currentBinding, blocked) { c, current ->
        requireOperations(c, AccessPoint.OPERATION)
        if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            requireOperations(c)
            if (!current()) throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
            (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key ->
                database.use(inventory.database, key, c) { db ->
                    db.withReadTransaction { BoundedTaskStore(this, c, current).read(binding) }
                }
            }
        }
    }
}

/** Shared real result boundary; work returns only AFTER transaction/DB/key cleanup. */
internal fun boundedTaskRead(binding: BoundedActiveBinding, access: StorageAccess,
    currentBinding: () -> BoundedActiveBinding?, blocked: () -> Unit,
    work: (StorageContext, () -> Boolean) -> BoundedTaskResult.Value): BoundedTaskResult {
    fun denied(status: BoundedActiveStatus) = BoundedTaskResult.Rejected(status)
    val c = access.acquire(AccessScope.READY) ?: return denied(BoundedActiveStatus.ACCESS_REVOKED)
    fun current() = c.scope == AccessScope.READY && c.generation == binding.generation && currentBinding() == binding
    if (!current()) return denied(BoundedActiveStatus.ACCESS_REVOKED)
    return try {
        requireOperations(c, AccessPoint.OPERATION)
        val value = work(c, ::current)
        // DB and lease have already closed. Never return payload after denied publication.
        var result: BoundedTaskResult = denied(BoundedActiveStatus.ACCESS_REVOKED)
        c.publish { if (current()) result = value }
        result
    } catch (_: StorageAccessRevoked) {
        denied(BoundedActiveStatus.ACCESS_REVOKED)
    } catch (failure: BoundedActiveRejected) {
        if (!c.check(AccessPoint.STEP) || !current()) denied(BoundedActiveStatus.ACCESS_REVOKED)
        else {
            if (failure.status == BoundedActiveStatus.INVALID_DATA) blocked()
            denied(failure.status)
        }
    } catch (_: Exception) {
        if (!c.check(AccessPoint.STEP) || !current()) denied(BoundedActiveStatus.ACCESS_REVOKED)
        else { blocked(); denied(BoundedActiveStatus.STORAGE_FAILURE) }
    }
}
