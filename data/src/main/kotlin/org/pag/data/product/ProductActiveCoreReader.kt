package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/** Existing compact SQL/materialization path. The product loader below supplies a common
 * ticket and READY context, then performs final owner-thread publication separately.
 */
class ProductActiveCoreReader(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val currentState: (() -> CompactTaskState?)? = null, private val blocked: () -> Unit) : ActiveCoreReader {
    private val database = ActionDatabaseAccess(app.applicationContext)
    override fun read(state: CompactTaskState, affectedPersonId: String?): ActiveCoreResult {
        val trustedState=currentState ?: return ActiveCoreResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED)
        return activeCoreRead(state, access, trustedState, blocked) { c, current ->
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

    /** Worker-only candidate read. It reuses the same Room transaction, stores and joint budget,
     * but deliberately does not call StorageContext.publish or retain a payload.
     */
    internal fun readForTicket(context: StorageContext,
        ticket: WorkspaceMutationInvalidation.ActiveReadTicket, laneTicket: Long,
        affectedPersonId: String? = null): ActiveCoreResult {
        val state=CompactTaskState(P3bFreeActiveCoreBinding(ticket.actionId,ticket.generation),ticket.mutationEpoch)
        fun current() = context.scope == AccessScope.READY && context.generation == ticket.generation &&
            ticket.isLive() && context.check(AccessPoint.STEP)
        if (!current()) return ActiveCoreResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED)
        return try {
            requireOperations(context,AccessPoint.OPERATION)
            if(inventory.state()!=ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
            DocumentNamespaceLane.exclusive(laneTicket) {
                requireOperations(context)
                if(!current()) throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
                (owner() ?: throw ProductStorageFailure()).withDbKey(context) { key ->
                    database.use(inventory.database,key,context) { db ->
                        db.withReadTransaction { ActiveCoreStore(this,context,::current).read(state,affectedPersonId) }
                    }
                }
            }
        } catch (_: StorageAccessRevoked) { ActiveCoreResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED) }
        catch (failure: BoundedActiveRejected) {
            if (!current()) ActiveCoreResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED)
            else {
                if(failure.status==BoundedActiveStatus.INVALID_DATA) blocked()
                ActiveCoreResult.Rejected(failure.status)
            }
        } catch (_: Exception) {
            if (!current()) ActiveCoreResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED)
            else { blocked(); ActiveCoreResult.Rejected(BoundedActiveStatus.STORAGE_FAILURE) }
        }
    }
}

/** Work cannot return until original DB/transaction/key cleanup has completed. Only then
 * can StorageContext publish the SINGLE complete core under the same trusted binding/epoch.
 */
internal fun activeCoreRead(state: CompactTaskState, access: StorageAccess,
    currentState: () -> CompactTaskState?, blocked: () -> Unit,
    work: (StorageContext, () -> Boolean) -> ActiveCoreResult.Value): ActiveCoreResult {
    return boundedProjectionRead(state, access, currentState, blocked, { ActiveCoreResult.Rejected(it) }, work)
}

/** Shared boundary for complete core/page/detail: cleanup must precede publication. */
internal fun <R, V : R> boundedProjectionRead(state: CompactTaskState, access: StorageAccess,
    currentState: () -> CompactTaskState?, blocked: () -> Unit,
    denied: (BoundedActiveStatus) -> R, work: (StorageContext, () -> Boolean) -> V): R {
    val c = access.acquire(AccessScope.READY) ?: return denied(BoundedActiveStatus.ACCESS_REVOKED)
    fun current() = c.scope == AccessScope.READY && c.generation == state.binding.generation && currentState() == state
    if (!current()) return denied(BoundedActiveStatus.ACCESS_REVOKED)
    return try {
        requireOperations(c, AccessPoint.OPERATION)
        val value = work(c, ::current)
        var result: R = denied(BoundedActiveStatus.ACCESS_REVOKED)
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
