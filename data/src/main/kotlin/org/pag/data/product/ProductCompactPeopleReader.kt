package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import org.pag.domain.*

/** Explicit standalone worker path, never constructed by ProductRuntime or ProductViewModel.
 * currentState MUST be supplied by a trusted synchronized mutation/permission adapter, never
 * inferred from UI snapshots. No new owner/token: existing key, context and DB preflights only.
 * Standalone calls replace a page/detail and do not authorize co-retention. ProductActiveCoreReader supplies joint
 * first-page admission; real writer hooks and the production owner remain #35/#37 blockers.
 */
class ProductCompactPeopleReader(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val currentState: () -> CompactTaskState?, private val blocked: () -> Unit) : CompactActivePeopleReader {
    private val database=ActionDatabaseAccess(app.applicationContext)
    override fun page(state: CompactTaskState, query: CompactPeopleQuery, after: CompactPeopleCursor?) =
        read(state) { it.page(state,query,after) }
    override fun detail(state: CompactTaskState, personId: String) = read(state) { it.detail(state,personId) }
    private fun read(state: CompactTaskState, work: suspend (CompactPeopleStore) -> CompactPeopleResult.Value): CompactPeopleResult =
        boundedProjectionRead<CompactPeopleResult, CompactPeopleResult.Value>(state,access,currentState,blocked,{ CompactPeopleResult.Rejected(it) }) { c,current ->
            requireOperations(c,AccessPoint.OPERATION)
            if(inventory.state()!=ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
            DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                requireOperations(c)
                if(!current()) throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
                (owner()?:throw ProductStorageFailure()).withDbKey(c) { key ->
                    database.use(inventory.database,key,c) { db ->
                        db.withReadTransaction { work(CompactPeopleStore(this,c,current)) }
                    }
                }
            }
        }
}
