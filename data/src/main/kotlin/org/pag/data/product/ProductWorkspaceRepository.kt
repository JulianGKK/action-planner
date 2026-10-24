package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import java.util.concurrent.Executor
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/** Batches only explicitly requested reads. No connection/key lifetime crosses this call. */
class ProductWorkspaceRepository internal constructor(app:Context,private val access:StorageAccess,private val inventory:ProductInventory,
    private val owner:()->SessionDbKeyOwner?,private val worker:Executor,private val main:Executor,
    private val blocked:()->Unit,
    private val memory:OutstandingCoreRead,
    private val materializing:(()->Unit)?=null,
    private val mutations:WorkspaceMutationInvalidation?=null,
    private val testReservation:((ActiveCoreReservation)->ActiveCoreReservation)?=null,
    private val leadershipState:(suspend (ActionDatabase,StorageContext,String)->SensitiveResult<SensitiveState>)?=null,
    private val supplementMaterializing:((WorkspacePart)->Unit)?=null):WorkspaceRepository {
    constructor(app:Context,access:StorageAccess,inventory:ProductInventory,owner:()->SessionDbKeyOwner?,
        worker:Executor,main:Executor,blocked:()->Unit):this(app,access,inventory,owner,worker,main,blocked,OutstandingCoreRead.product,null,null)
    constructor(app:Context,access:StorageAccess,inventory:ProductInventory,owner:()->SessionDbKeyOwner?,
        worker:Executor,main:Executor,blocked:()->Unit,mutations:WorkspaceMutationInvalidation):this(app,access,inventory,owner,worker,main,blocked,OutstandingCoreRead.product,null,mutations)

    /** Missing modules only, never a second Operations/Task/Workspace board graph. */
    override fun supplement(access:WorkspaceAccess,request:WorkspaceReadRequest,publish:(WorkspaceSupplementResult)->Unit) {
        val scope=access as? OriginalWorkspaceAccess ?: return
        if(scope.source!==this.access || !memory.ownsFullview(scope.entry) || request.parts.size!=1)return
        val part=request.parts.single()
        if(part!=WorkspacePart.MATERIAL_BOARD && part!=WorkspacePart.EXPENSE_BOARD)return
        var child:OutstandingCoreRead.FullviewEntry.FullviewSupplement?=null
        val accepted=scope.publish(request.actionId,request.status,request.generation){child=scope.entry.supplement(part)}
        if(!accepted)return
        val original=child ?: run {
            scope.publish(request.actionId,request.status,request.generation){if(request.ticket?.active!=false)publish(WorkspaceSupplementResult.Rejected(scope.entry.additionalFailure))};return
        }
        val c=scope.original
        fun current()=scope.liveOnWorker() && request.ticket?.active!=false
        val task=original.worker(Runnable {
            try {
                val result=DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                    if(!current())throw StorageAccessRevoked()
                    (owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){db->db.withReadTransaction {
                        val names=if(part==WorkspacePart.MATERIAL_BOARD)FullviewPreflight.material else FullviewPreflight.expense
                        val reservation=FullviewPreflight(this,c,::current).prepare(request.actionId,names)
                        if(!current())throw StorageAccessRevoked()
                        if(!original.admit(reservation))return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
                        if(!current())throw StorageAccessRevoked()
                        val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                        supplementMaterializing?.invoke(part)
                        val data=if(part==WorkspacePart.MATERIAL_BOARD)when(val r=MaterialStore(db,guarded,System::currentTimeMillis).board(request.actionId)){
                            is OperationsResult.Value->WorkspaceSupplement(request.actionId,c.generation,material=r.value)
                            is OperationsResult.Rejected->return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                        }else when(val r=ExpenseStore(db,guarded,System::currentTimeMillis).board(request.actionId)){
                            is OperationsResult.Value->WorkspaceSupplement(request.actionId,c.generation,expenses=r.value)
                            is OperationsResult.Rejected->return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                        }
                        WorkspaceSupplementResult.Value(data)
                    }}}
                }
                val delivery=original.main(Runnable{scope.publish(request.actionId,request.status,request.generation){if(current())publish(result)}})
                try{main.execute(delivery)}catch(_:java.util.concurrent.RejectedExecutionException){original.mainRejected()}
            }catch(f:StorageAccessRevoked){if(f.suppressed.isNotEmpty())original.unconfirmed()}
             catch(f:BoundedActiveRejected){
                if(f.suppressed.isNotEmpty()){original.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
                else if(f.status==BoundedActiveStatus.BUDGET_REFUSED){
                    val delivery=original.main(Runnable{scope.publish(request.actionId,request.status,request.generation){if(current())publish(WorkspaceSupplementResult.Rejected(WorkspaceFailure.BUDGET_REFUSED))}})
                    try{main.execute(delivery)}catch(_:java.util.concurrent.RejectedExecutionException){original.mainRejected()}
                }else if(f.status!=BoundedActiveStatus.ACCESS_REVOKED){original.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
             }
             catch(_:Exception){original.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(task)}catch(_:java.util.concurrent.RejectedExecutionException){original.workerRejected()}
    }
    private val database=ActionDatabaseAccess(app.applicationContext)
    private class ReadCancelled:RuntimeException()
    override fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit) {
        val c=access.acquire(AccessScope.READY) ?: return
        if(request.generation!=c.generation)return
        fun refuse(){main.execute{c.publish{if(request.ticket?.active!=false)publish(WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))}}}
        if(memory.fullviewOverlapUnconfirmed()) {
            refuse()
            return
        }
        val entry=memory.beginFullview(request.actionId,request.generation) ?: run {
            refuse()
            return
        }
        val original=mutations?.beforeActiveRead(request.actionId,c.generation)
        if(original==null){entry.workerRejected();refuse();return}
        val publicationThread=Thread.currentThread()
        val frozen=request.copy(parts=request.parts.toSet())
        val ticket=DocumentNamespaceLane.ticket()
        fun requireRequest(){if(frozen.ticket?.active==false || !original.isLive())throw ReadCancelled()}
        val work=entry.worker(Runnable {
            try {
                requireRequest()
                val result=DocumentNamespaceLane.exclusive(ticket) {
                    requireRequest()
                    requireOperations(c,AccessPoint.OPERATION)
                    if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                    requireRequest()
                    (owner() ?: throw ProductStorageFailure()).withDbKey(c){key->
                        database.use(inventory.database,key,c){db->db.withReadTransaction {
                            WorkspaceStore(db,object:StorageContext by c { override fun check(point:AccessPoint)=original.isLive() && frozen.ticket?.active!=false && c.check(point) },memory,materializing,this,{original.isLive()},testReservation,leadershipState).read(frozen,entry)
                        }}
                    }.also{requireOperations(c)}
                }
                requireRequest()
                // Envelope/capability allocations are in OriginalWorkspaceAccess.cost.
                val delivered=if(result is WorkspaceResult.Value)WorkspaceResult.Value(result.value.copy(
                    access=OriginalWorkspaceAccess(entry,access,c,original,mutations!!,frozen,publicationThread))) else result
                val delivery=entry.main(Runnable{c.publish{if(frozen.ticket?.active!=false && mutations?.isCurrentActiveRead(original)==true)publish(delivered)}})
                try{main.execute(delivery)}catch(_:java.util.concurrent.RejectedExecutionException){entry.mainRejected()}

            }catch(_:ReadCancelled){/* Superseded before key/DB admission or after full cleanup. */}
             catch(_:StorageAccessRevoked){/* No stale publication. */}
             catch(_:Exception){main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(work)}catch(_:java.util.concurrent.RejectedExecutionException){entry.workerRejected()}
        // Uncertain queue acceptance deliberately retains the exclusion until actual dispatch.
    }
}

/** Same existing stores and permission checks, one atomic transaction. No LeadershipStore. */
internal class WorkspaceStore(private val db:ActionDatabase,private val c:StorageContext,
    private val memory:OutstandingCoreRead=OutstandingCoreRead.product,
    private val materializing:(()->Unit)?=null,
    private val sql:androidx.room3.PooledConnection?=null,private val original:()->Boolean={true},
    private val testReservation:((ActiveCoreReservation)->ActiveCoreReservation)?=null,
    private val leadershipState:(suspend (ActionDatabase,StorageContext,String)->SensitiveResult<SensitiveState>)?=null) {
    private fun <T> value(result:OperationsResult<T>):T=when(result) {
        is OperationsResult.Value->result.value
        is OperationsResult.Rejected->throw Rejected(result.reason)
    }
    private class Rejected(val reason:OperationsFailure):RuntimeException()
    suspend fun read(request:WorkspaceReadRequest,entry:OutstandingCoreRead.FullviewEntry?=null):WorkspaceResult {
        requireOperations(c,AccessPoint.OPERATION)
        if(request.generation!=c.generation)throw StorageAccessRevoked()
        if(request.ticket?.active==false)return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        if(entry==null || !memory.enterFullview(entry,request))return WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED)
        val connection=sql ?: return WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED)
        fun current()=original() && request.ticket?.active!=false && c.generation==request.generation && c.check(AccessPoint.STEP)
        try {
            val data=FullviewPreflight(connection,c,::current).prepare(request.actionId,FullviewPreflight.workspace(request.parts))
            // Common capability + bounded control scratch exactly ONCE, never again per supplement.
            val base=ActiveCoreReservation(CoreReadAdmission.add(data.retained,OriginalWorkspaceAccess.cost),CoreReadAdmission.add(data.transition,OriginalWorkspaceAccess.cost))
            val reservation=testReservation?.invoke(base) ?: base
            if(!admitCurrentFullview(entry,reservation,::current))return WorkspaceResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
        }catch(f:BoundedActiveRejected){
            if(f.status==BoundedActiveStatus.ACCESS_REVOKED)throw StorageAccessRevoked()
            if(f.status!=BoundedActiveStatus.BUDGET_REFUSED)throw ProductStorageFailure()
            return WorkspaceResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
        }
        materializing?.invoke()
        val action=db.actions().get(request.actionId)?.model() ?: return WorkspaceResult.Rejected(WorkspaceFailure.NOT_FOUND)
        requireOperations(c)
        if(request.expectedAction!=null && request.expectedAction!=action)return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        if(!OperationalRules.mayRead(action.status))return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
        if(action.status!=request.status)return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        val a=action.id;val parts=request.parts
        fun wants(part:WorkspacePart)=part in parts
        val now=System::currentTimeMillis
        return try {
            val operations=value(OperationsStore(db,c,now).board(a))
            val tasks=if(wants(WorkspacePart.TASKS))value(TaskStore(db,c,now).board(a)) else null
            val protocol=if(wants(WorkspacePart.PROTOCOL_SUMMARY))when(val summary=ProtocolStore(db,c,now).summary(a)) {
                is ProtocolResult.Value->summary.value
                is ProtocolResult.Rejected->return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
            } else null
            val material=if(wants(WorkspacePart.MATERIAL_SUMMARY))value(MaterialStore(db,c,now).summary(a)) else null
            val expenses=if(wants(WorkspacePart.EXPENSE_SUMMARY))value(ExpenseStore(db,c,now).summary(a)) else null
            val memory=if(wants(WorkspacePart.MEMORY_SUMMARY))value(MemoryReportStore(db,c,now).summary(a)) else null
            val documents=if(wants(WorkspacePart.DOCUMENT_SUMMARY)) {
                requireOperations(c)
                if(!DocumentRules.mayRead(action.status))return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                val count=db.documents().count(a)
                if(count !in 0..DocumentRules.MAX_DOCUMENTS)throw ProductStorageFailure()
                DocumentSummary(count)
            } else null
            val memoryHeaders=if(wants(WorkspacePart.MEMORY_HEADERS))value(MemoryReportStore(db,c,now).list(a)) else null
            val timeline=if(wants(WorkspacePart.PROTOCOL_TIMELINE))when(val read=ProtocolStore(db,c,now).timeline(a)) {
                is ProtocolResult.Value->read.value
                is ProtocolResult.Rejected->return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
            } else null
            val materialBoard=if(wants(WorkspacePart.MATERIAL_BOARD))value(MaterialStore(db,c,now).board(a)) else null
            val expenseBoard=if(wants(WorkspacePart.EXPENSE_BOARD))value(ExpenseStore(db,c,now).board(a)) else null
            val protection=if(wants(WorkspacePart.LEADERSHIP_STATE)) {
                val producer=leadershipState ?: return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                producer(db,c,a)
            }else null
            requireOperations(c)
            WorkspaceResult.Value(WorkspaceRead(a,action.status,c.generation,parts,operations,tasks,protocol,material,expenses,memory,documents,memoryHeaders,
                timeline,materialBoard,expenseBoard,leadershipState=protection))
        }catch(_:Rejected){WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)}
    }
}
