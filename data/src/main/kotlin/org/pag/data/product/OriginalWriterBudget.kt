package org.pag.data.product

import androidx.room3.PooledConnection
import org.pag.domain.*

/** No payload, cache, writer ticket or independent allowance. Both submissions and SQL
 * preflights book on the same original record. All costs survive worker/Main completion. */
internal class OriginalWriterBudget(private val access:StorageAccess,private val memory:()->OutstandingCoreRead) {
    fun reserve(actionId:String,chars:Long=0,items:Long=0):Boolean {
        val c=access.acquire(AccessScope.READY)?:return false
        if(c.scope!=AccessScope.READY || !c.check(AccessPoint.OPERATION))return false
        val units=try{copies(chars,items)}catch(_:ArithmeticException){return false}
        return memory().reserveHeld(actionId,c.generation,ActiveCoreReservation(units,units))!=false && c.check(AccessPoint.STEP)
    }
    suspend fun preflight(sql:PooledConnection,c:StorageContext,actionId:String,names:Set<String>,id:String?=null):Boolean {
        requireOperations(c)
        // Only the metadata descriptor is constructed here; submission control costs cover it.
        val request=id?.let{WorkspaceDetailRequest(actionId,ActionStatus.PREPARATION,c.generation,WorkspaceDetailKind.TASK,it,WorkspaceReadTicket())}
        val reservation=try{FullviewPreflight(sql,c,{c.check(AccessPoint.STEP)}).prepare(actionId,names,detail=request)}
            catch(f:BoundedActiveRejected){if(f.status==BoundedActiveStatus.BUDGET_REFUSED)return false else throw f}
        requireOperations(c)
        return memory().reserveHeld(actionId,c.generation,reservation)!=false
    }
    companion object {
        fun copies(chars:Long,items:Long):ActiveWorkspaceBudget.Units {
            require(chars>=0 && items>=0)
            fun mul(a:Long,b:Long)=Math.multiplyExact(a,b)
            fun add(a:Long,b:Long)=Math.addExact(a,b)
            return ActiveWorkspaceBudget.Units(objects=add(128,mul(items,32)),strings=add(64,mul(items,32)),
                utf16Units=add(4096,mul(chars,32)),arrays=32,arrayPayloadBytes=8192,
                lists=32,listCapacity=add(256,mul(items,64)),maps=32,mapCapacity=add(128,mul(items,16)),
                references=add(256,mul(items,128)))
        }
    }
}
