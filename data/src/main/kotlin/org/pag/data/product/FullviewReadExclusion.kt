package org.pag.data.product

import android.content.Context
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked

/** Shared original-record exclusion, NOT a budget admission. The missing fresh-fullview
 * R8/T16 producer must not be inferred from success here. No payload retained in this object. */
internal class FullviewReadExclusion(app:Context,private val access:StorageAccess,
    private val inventory:ProductInventory,private val owner:()->SessionDbKeyOwner?,
    private val worker:Executor,private val main:Executor,private val blocked:()->Unit) {
    private val database=ActionDatabaseAccess(app.applicationContext)
    private var memory=OutstandingCoreRead.product
    private var used=false
    @Synchronized internal fun bind(memory:OutstandingCoreRead){check(!used);this.memory=memory}
    @Synchronized internal fun record():OutstandingCoreRead {used=true;return memory}
    fun <T> read(actionId:String,publish:(T)->Unit,refused:()->T,work:suspend(ActionDatabase,StorageContext)->T){
        val memory=record()
        val c=access.acquire(AccessScope.READY)?:return
        val entry=memory.beginFullview(actionId,c.generation) ?: run {
            main.execute{c.publish{publish(refused())}};return
        }
        val task=entry.worker(Runnable {
            try{
                requireOperations(c,AccessPoint.OPERATION)
                if(!memory.enterFullview(entry,actionId,c.generation))return@Runnable
                if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                val value=(owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){work(it,c)}}
                val delivery=entry.main(Runnable{c.publish{publish(value)}})
                try{main.execute(delivery)}catch(_:RejectedExecutionException){entry.mainRejected()}
            }catch(_:StorageAccessRevoked){}
             catch(_:Exception){main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(task)}catch(_:RejectedExecutionException){entry.workerRejected();main.execute{c.publish{publish(refused())}}}
    }
}
