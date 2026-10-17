package org.pag.assessment

import org.pag.domain.*

/** TEST ONLY. No clocks, session authority, keys, storage or Android saved-state API. */
class AssessmentStore(private val access:StorageAccess, private val scope:()->Binding?, private val budget:Long) {
    data class Binding(val actionId:String,val generation:Long,val status:ActionStatus,val version:Long)
    private var binding:Binding?=null
    private var value:WorkspaceRead?=null
    private var epoch=0L
    fun ticket()=epoch
    fun clear(){epoch++;binding=null;value=null}
    fun install(expected:Binding,ticket:Long,read:WorkspaceRead,chargedBytes:Long):Boolean {
        val c=access.acquire(AccessScope.READY) ?: return false
        if(ticket!=epoch || scope()!=expected || c.generation!=expected.generation || !OperationalRules.mayRead(expected.status) ||
            read.actionId!=expected.actionId || read.generation!=expected.generation || read.status!=expected.status ||
            chargedBytes !in 0..budget)return false
        return c.publish { if(ticket==epoch && scope()==expected){binding=expected;value=read} }
    }
    fun read(request:WorkspaceReadRequest):WorkspaceRead? {
        val c=access.acquire(AccessScope.READY) ?: run{clear();return null}
        val b=binding ?: return null
        if(scope()!=b || c.generation!=b.generation || request.actionId!=b.actionId || request.generation!=b.generation || request.status!=b.status){clear();return null}
        if(request.ticket?.active==false)return null
        val v=value ?: return null
        if(!v.parts.containsAll(request.parts))return null
        var result:WorkspaceRead?=null
        c.publish { if(scope()==b && binding==b && request.ticket?.active!=false)result=v.copy(parts=request.parts) }
        return result
    }
    /** Inspect only to assert test cleanup; never a production access port. */
    fun retainedForTest()=value!=null
}
