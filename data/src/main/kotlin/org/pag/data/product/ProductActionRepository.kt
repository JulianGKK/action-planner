package org.pag.data.product

import android.content.Context
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** All action reads/writes use the authoritative READY context, including result publication. */
class ProductActionRepository(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val worker: Executor, private val main: Executor, private val blocked: () -> Unit) : ActionRepository {
    private val application=app.applicationContext
    private val database = ActionDatabaseAccess(application)
    internal var timing:((String,Long)->Unit)?=null
    private fun now()=if(timing!=null)System.nanoTime() else 0L
    private fun measured(name:String,start:Long){try{timing?.invoke(name,System.nanoTime()-start)}catch(_:RuntimeException){}}
    override fun listActions(publish: (List<Action>) -> Unit) = run(publish) { db, _ -> db.actions().list().map { row -> row.model() } }
    override fun getAction(id: String, publish: (Action?) -> Unit) = run(publish) { db, _ -> db.actions().get(id)?.model() }
    override fun createAction(action: NewAction, publish: (Action) -> Unit) {
        if (!action.valid()) throw IllegalArgumentException("P1-ACTION-INVALID")
        run(publish) { db, _ ->
            val row = ActionRow(UUID.randomUUID().toString(), action.title.trim(), action.description.trim(),
                action.startAt, action.expectedEndAt, System.currentTimeMillis(), ActionStatus.DRAFT.name)
            db.withWriteTransaction { db.actions().insert(row) }
            row.model()
        }
    }
    override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) =
        run(publish) { db, context -> db.changeAction(expected, changes, context) }
    override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) =
        run(publish) { db, context -> db.advanceAction(id, expected, context) }
    private fun <T> run(publish: (T) -> Unit, work: suspend (ActionDatabase, StorageContext) -> T) {
        val context = access.acquire(AccessScope.READY) ?: return
        val keyOwner = owner() ?: run { blocked(); return }
        val queued=now()
        worker.execute {
            measured("WORKER_WAIT",queued)
            try {
                val inventoryStarted=now()
                if (context.scope != AccessScope.READY || inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
                measured("PRODUCT_INVENTORY",inventoryStarted)
                val observed=timing
                val dbAccess=if(observed==null)database else ActionDatabaseAccess(application,ActionPreflightHooks(timing={p,ns->observed("DB_${p.name}",ns)}))
                val result = keyOwner.withDbKey(context) { key -> dbAccess.use(inventory.database, key, context) { db -> work(db, context) } }
                val publishing=now()
                main.execute {
                    measured("PUBLICATION_QUEUE",publishing)
                    val processing=now()
                    context.publish { publish(result) }
                    // Observation is outside the coordinator's publication monitor.
                    measured("PUBLICATION_AND_VM",processing)
                }
            } catch (_: Exception) {
                main.execute { if (context.check(AccessPoint.STEP)) blocked() }
            }
        }
    }
}
