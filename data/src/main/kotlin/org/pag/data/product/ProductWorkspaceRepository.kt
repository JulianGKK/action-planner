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
    private val materializing:(()->Unit)?=null):WorkspaceRepository {
    constructor(app:Context,access:StorageAccess,inventory:ProductInventory,owner:()->SessionDbKeyOwner?,
        worker:Executor,main:Executor,blocked:()->Unit):this(app,access,inventory,owner,worker,main,blocked,OutstandingCoreRead.product)

    private val database=ActionDatabaseAccess(app.applicationContext)
    private class ReadCancelled:RuntimeException()
    override fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit) {
        val c=access.acquire(AccessScope.READY) ?: return
        if(request.generation!=c.generation)return
        if(memory.fullviewOverlapUnconfirmed()) {
            main.execute{c.publish{publish(WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))}}
            return
        }
        val entry=memory.beginFullview(request.actionId,request.generation) ?: run {
            main.execute{c.publish{publish(WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))}}
            return
        }
        val frozen=request.copy(parts=request.parts.toSet())
        val ticket=DocumentNamespaceLane.ticket()
        fun requireRequest(){if(frozen.ticket?.active==false)throw ReadCancelled()}
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
                            WorkspaceStore(db,c,memory,materializing).read(frozen,entry)
                        }}
                    }.also{requireOperations(c)}
                }
                requireRequest()
                val delivery=entry.main(Runnable{c.publish{if(frozen.ticket?.active!=false)publish(result)}})
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
    private val materializing:(()->Unit)?=null) {
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
            requireOperations(c)
            WorkspaceResult.Value(WorkspaceRead(a,action.status,c.generation,parts,operations,tasks,protocol,material,expenses,memory,documents,memoryHeaders,
                timeline,materialBoard,expenseBoard))
        }catch(_:Rejected){WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)}
    }
}
