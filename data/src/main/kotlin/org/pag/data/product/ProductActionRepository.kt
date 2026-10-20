package org.pag.data.product

import android.content.Context
import androidx.room3.useWriterConnection
import org.pag.domain.*
import java.util.concurrent.Executor

/** All action reads/writes use the authoritative READY context, including result publication. */
class ProductActionRepository(app: Context, private val access: StorageAccess,
    private val inventory: ProductInventory, private val owner: () -> SessionDbKeyOwner?,
    private val worker: Executor, private val main: Executor, mutations:WorkspaceMutationInvalidation,
    private val blocked: () -> Unit) : ActionRepository {
    constructor(app:Context,access:StorageAccess,inventory:ProductInventory,owner:()->SessionDbKeyOwner?,
        worker:Executor,main:Executor,blocked:()->Unit):this(app,access,inventory,owner,worker,main,UncachedWorkspaceMutations(),blocked)
    private val mutationBoundary=WorkspaceWriteBoundary(access,worker,main,mutations,blocked)
    private val application=app.applicationContext
    private val database = ActionDatabaseAccess(application)
    internal var timing:((String,Long)->Unit)?=null
    private fun now()=if(timing!=null)System.nanoTime() else 0L
    private fun measured(name:String,start:Long){try{timing?.invoke(name,System.nanoTime()-start)}catch(_:RuntimeException){}}
    override fun listActions(publish: (List<Action>) -> Unit) = run(publish) { db, _ -> db.actions().list().map { row -> row.model() } }
    override fun getAction(id: String, publish: (Action?) -> Unit) = run(publish) { db, _ -> db.actions().get(id)?.model() }
    override fun createAction(action: NewAction, publish: (Action) -> Unit) {
        if (!action.valid()) throw IllegalArgumentException("P1-ACTION-INVALID")
        write<Action>(WorkspaceMutation(WorkspaceWriterFamily.ACTION_CREATE,null),publish,{null},{false}) {it.create(action)}
    }
    override fun updateAction(expected:Action,changes:NewAction,publish:(ActionChangeResult)->Unit) =
        write(WorkspaceMutation(WorkspaceWriterFamily.ACTION_EDIT,expected.id),publish,
            {ActionChangeResult.Rejected(ActionChangeRejection.STALE)},{it is ActionChangeResult.Rejected}){it.change(expected,changes)}
    override fun advanceActionStatus(id:String,expected:ActionStatus,publish:(ActionChangeResult)->Unit) =
        write(WorkspaceMutation(WorkspaceWriterFamily.ACTION_STATUS,id),publish,
            {ActionChangeResult.Rejected(ActionChangeRejection.STALE)},{it is ActionChangeResult.Rejected}){it.advance(id,expected)}
    private fun <R:Any> write(mutation:WorkspaceMutation,publish:(R)->Unit,busy:()->R?,rejected:(R)->Boolean,
        work:suspend(ActionWriterStore)->R) = mutationBoundary.submit(mutation,publish,busy,{!rejected(it)}) {c,p->
        if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
        val observed=timing
        val dbAccess=if(observed==null)database else ActionDatabaseAccess(application,ActionPreflightHooks(timing={phase,ns->observed("DB_${phase.name}",ns)}))
        (owner()?:throw ProductStorageFailure()).withDbKey(c){key->dbAccess.use(inventory.database,key,c){db->
            db.useWriterConnection { tx -> confirmedWorkspaceTransaction(tx,c,p,rejected){work(ActionWriterStore(RoomCoreWriterData(db),c))} }
        }}
    }
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
