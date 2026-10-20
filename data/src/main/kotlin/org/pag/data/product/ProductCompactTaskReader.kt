package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/** Explicit standalone worker path, never constructed by ProductRuntime or ProductViewModel.
 * currentState MUST be supplied by a trusted synchronized mutation/permission adapter, never
 * inferred from UI snapshots. No new owner/token: existing key, context and DB preflights only.
 * A production adapter and shared pre-materialization admission remain #34/#37 blockers.
 */
class ProductCompactTaskReader(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val currentState: () -> CompactTaskState?, private val blocked: () -> Unit) : CompactActiveTaskReader {
    private val database=ActionDatabaseAccess(app.applicationContext)
    override fun page(state: CompactTaskState, query: CompactTaskQuery, after: CompactTaskCursor?) =
        read(state) { it.page(state,query,after) }
    override fun detail(state: CompactTaskState, taskId: String) = read(state) { it.detail(state,taskId) }
    override fun events(state: CompactTaskState, taskId: String, after: CompactEventCursor?) =
        read(state) { it.events(state,taskId,after) }

    private fun read(state: CompactTaskState, work: suspend (CompactTaskStore) -> CompactTaskResult.Value): CompactTaskResult =
        compactTaskRead(state,access,currentState,blocked) { c,current ->
            requireOperations(c,AccessPoint.OPERATION)
            if(inventory.state()!=ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
            DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                requireOperations(c)
                if(!current()) throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
                (owner()?:throw ProductStorageFailure()).withDbKey(c) { key ->
                    database.use(inventory.database,key,c) { db ->
                        db.withReadTransaction { work(CompactTaskStore(this,c,current)) }
                    }
                }
            }
        }
}

/** Publication only AFTER transaction/database/key cleanup, gated by current trusted metadata.
 * Callers must discard previous pages/details on any rejection. No local cache is authorized.
 */
internal fun compactTaskRead(state: CompactTaskState, access: StorageAccess,
    currentState: () -> CompactTaskState?, blocked: () -> Unit,
    work: (StorageContext, () -> Boolean) -> CompactTaskResult.Value): CompactTaskResult {
    fun denied(s: BoundedActiveStatus)=CompactTaskResult.Rejected(s)
    val c=access.acquire(AccessScope.READY)?:return denied(BoundedActiveStatus.ACCESS_REVOKED)
    fun current()=c.scope==AccessScope.READY && c.generation==state.binding.generation && currentState()==state
    if(!current()) return denied(BoundedActiveStatus.ACCESS_REVOKED)
    return try {
        requireOperations(c,AccessPoint.OPERATION)
        val value=work(c,::current)
        var result: CompactTaskResult=denied(BoundedActiveStatus.ACCESS_REVOKED)
        val accepted=c.publish { if(current()) result=value }
        if(accepted) result else denied(BoundedActiveStatus.ACCESS_REVOKED)
    } catch(_:StorageAccessRevoked) { denied(BoundedActiveStatus.ACCESS_REVOKED) }
    catch(f:BoundedActiveRejected) {
        if(!c.check(AccessPoint.STEP) || !current()) denied(BoundedActiveStatus.ACCESS_REVOKED)
        else { if(f.status==BoundedActiveStatus.INVALID_DATA) blocked();denied(f.status) }
    } catch(_:Exception) {
        if(!c.check(AccessPoint.STEP) || !current()) denied(BoundedActiveStatus.ACCESS_REVOKED)
        else { blocked();denied(BoundedActiveStatus.STORAGE_FAILURE) }
    }
}
