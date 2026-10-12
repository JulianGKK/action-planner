package org.pag.data.product

import org.pag.domain.*

/** Instance-local, one person/area/generation. Presence is never authorization. */
internal class LeadershipGrant(override val actionId:String,override val personId:String,override val area:SensitiveArea,
    val context:StorageContext,private val clock:MonotonicTime,key:ByteArray,val epoch:String,
    val revision:Long,val keyEnvelope:ByteArray,val writable:Boolean,val expectedPayload:ByteArray?=null,val expectedRoot:LeadershipRow?=null):SensitiveGrant {
    private val deadline=clock.nowMillis().let { if(it>Long.MAX_VALUE-60000) Long.MAX_VALUE else it+60000 }
    private var bytes:ByteArray?=key.copyOf()
    override fun valid():Boolean {
        if(synchronized(this){bytes==null})return false
        // Context checks can synchronously invalidate the session and revoke this grant.
        // Never acquire the coordinator (or invoke external code) under the grant monitor.
        if(!inLifetime()){cancel();return false}
        return synchronized(this){bytes!=null}
    }
    fun inLifetime() = context.scope==AccessScope.READY && context.check(AccessPoint.STEP) && clock.nowMillis()<deadline
    @Synchronized override fun cancel(){bytes?.fill(0);bytes=null}
    fun <T> consume(work:(ByteArray)->T):T {
        if(!valid())throw SensitiveProblem(SensitiveFailure.DENIED)
        var lease:ByteArray?=null
        try {
            // The context owns its authority first. authorize is a scoped read of
            // that already-owned authority, not StorageContext.check under this lock.
            // Grant and session use the same monotonic epoch (elapsedRealtime).
            val admitted=context.admitKey(deadline){authorize->
                synchronized(this){
                    if(bytes==null || !authorize())null
                    else bytes?.copyOf()?.also{lease=it}
                }
            } ?: run{cancel();throw SensitiveProblem(SensitiveFailure.DENIED)}
            // Already-admitted synchronous work may finish across revoke/expiry.
            // The final snapshot still denies returning stale results, as before.
            val result=work(admitted);if(!valid())throw SensitiveProblem(SensitiveFailure.DENIED);return result
        } finally {lease?.fill(0)}
    }
}
