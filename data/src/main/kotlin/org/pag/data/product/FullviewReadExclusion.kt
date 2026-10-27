package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked

/** Direct ports use the SAME original record and a consistent preflight+DTO transaction.
 * No payload retained here; issued graphs stay booked beyond callback return. */
internal class FullviewReadExclusion(app:Context,private val access:StorageAccess,
    private val inventory:ProductInventory,private val owner:()->SessionDbKeyOwner?,
    private val worker:Executor,private val main:Executor,private val blocked:()->Unit,
    private val mutations:WorkspaceMutationInvalidation,private val projection:Set<String>) {
    private val database=ActionDatabaseAccess(app.applicationContext)
    private var memory=OutstandingCoreRead.product
    private var used=false
    @Synchronized internal fun bind(memory:OutstandingCoreRead){check(!used);this.memory=memory}
    @Synchronized internal fun record():OutstandingCoreRead {used=true;return memory}
    fun <T> read(actionId:String,publish:(T)->Unit,refused:(Boolean)->T,work:suspend(ActionDatabase,StorageContext)->T){
        val memory=record()
        val c=access.acquire(AccessScope.READY)?:return
        val entry=memory.beginFullview(actionId,c.generation) ?: run {
            main.execute{c.publish{publish(refused(false))}};return
        }
        val original=mutations.beforeActiveRead(actionId,c.generation)
        if(original==null){entry.workerRejected();main.execute{c.publish{publish(refused(false))}};return}
        val bounded=object:StorageContext by c { override fun check(point:AccessPoint)=original.isLive() && c.check(point) }
        fun current()=original.isLive() && c.check(AccessPoint.STEP)
        val task=entry.worker(Runnable {
            try{
                requireOperations(c,AccessPoint.OPERATION)
                if(!memory.enterFullview(entry,actionId,c.generation))return@Runnable
                if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                val value=(owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c,cleanupConfirmed={if(!it)entry.poison()}){db->db.withReadTransaction {
                    val reservation=FullviewPreflight(this,bounded,::current).prepare(actionId,projection)
                    if(!admitCurrentFullview(entry,reservation,::current))return@withReadTransaction refused(true)
                    work(db,bounded)
                }}}
                val delivery=entry.main(Runnable{c.publish{if(mutations.isCurrentActiveRead(original))publish(value)}})
                try{main.execute(delivery)}catch(_:RejectedExecutionException){entry.mainRejected()}
            }catch(f:StorageAccessRevoked){if(f.suppressed.isNotEmpty())entry.poison()}
             catch(f:BoundedActiveRejected){
                if(f.status==BoundedActiveStatus.BUDGET_REFUSED){
                    val delivery=entry.main(Runnable{c.publish{if(mutations.isCurrentActiveRead(original))publish(refused(true))}})
                    try{main.execute(delivery)}catch(_:RejectedExecutionException){entry.mainRejected()}
                }else if(f.status!=BoundedActiveStatus.ACCESS_REVOKED)main.execute{if(c.check(AccessPoint.STEP))blocked()}
             }
             catch(_:Exception){entry.poison();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(task)}catch(_:RejectedExecutionException){entry.workerRejected();main.execute{c.publish{publish(refused(false))}}}
    }
}
