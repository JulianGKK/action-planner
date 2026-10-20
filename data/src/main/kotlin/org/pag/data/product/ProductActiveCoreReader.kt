package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/** Explicit worker-only proof path; NOT constructed by ProductRuntime/ProductViewModel.
 * Trusted state must invalidate BEFORE writers, and be null for Pending/UNKNOWN/BLOCKED/
 * Recovery/revoke. Real writer hooks (#35) and payload ownership (#37) remain unwired.
 */
class ProductActiveCoreReader(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val currentState: () -> CompactTaskState?, private val blocked: () -> Unit) : ActiveCoreReader {
    private val database = ActionDatabaseAccess(app.applicationContext)
    override fun read(state: CompactTaskState, affectedPersonId: String?): ActiveCoreResult =
        activeCoreRead(state, access, currentState, blocked) { c, current ->
            requireOperations(c, AccessPoint.OPERATION)
            if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
            DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                requireOperations(c)
                if (!current()) throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
                (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key ->
                    database.use(inventory.database, key, c) { db ->
                        db.withReadTransaction { ActiveCoreStore(this, c, current).read(state, affectedPersonId) }
                    }
                }
            }
        }
}

/** Work cannot return until original DB/transaction/key cleanup has completed. Only then
 * can StorageContext publish the SINGLE complete core under the same trusted binding/epoch.
 */
internal fun activeCoreRead(state: CompactTaskState, access: StorageAccess,
    currentState: () -> CompactTaskState?, blocked: () -> Unit,
    work: (StorageContext, () -> Boolean) -> ActiveCoreResult.Value): ActiveCoreResult {
    fun denied(status: BoundedActiveStatus) = ActiveCoreResult.Rejected(status)
    val c = access.acquire(AccessScope.READY) ?: return denied(BoundedActiveStatus.ACCESS_REVOKED)
    fun current() = c.scope == AccessScope.READY && c.generation == state.binding.generation && currentState() == state
    if (!current()) return denied(BoundedActiveStatus.ACCESS_REVOKED)
    return try {
        requireOperations(c, AccessPoint.OPERATION)
        val value = work(c, ::current)
        var result: ActiveCoreResult = denied(BoundedActiveStatus.ACCESS_REVOKED)
        val accepted = c.publish { if (current()) result = value }
        if (accepted && c.check(AccessPoint.PUBLICATION) && current()) result
        else denied(BoundedActiveStatus.ACCESS_REVOKED)
    } catch (_: StorageAccessRevoked) { denied(BoundedActiveStatus.ACCESS_REVOKED) }
    catch (failure: BoundedActiveRejected) {
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
