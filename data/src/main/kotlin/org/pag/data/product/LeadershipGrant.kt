package org.pag.data.product

import org.pag.domain.*

/** Instance-local, one person/area/generation. Presence is never authorization. */
internal class LeadershipGrant(override val actionId:String,override val personId:String,override val area:SensitiveArea,
    val context:StorageContext,private val clock:MonotonicTime,key:ByteArray,val epoch:String,
    val revision:Long,val keyEnvelope:ByteArray,val writable:Boolean,val expectedPayload:ByteArray?=null,val expectedRoot:LeadershipRow?=null):SensitiveGrant {
    private val deadline=clock.nowMillis().let { if(it>Long.MAX_VALUE-60000) Long.MAX_VALUE else it+60000 }
    private var bytes:ByteArray?=key.copyOf()
    @Synchronized override fun valid():Boolean {
        if(bytes==null) return false
        if(!inLifetime()){cancel();return false}
        return true
    }
    fun inLifetime() = context.scope==AccessScope.READY && context.check(AccessPoint.STEP) && clock.nowMillis()<deadline
    @Synchronized override fun cancel(){bytes?.fill(0);bytes=null}
    fun <T> consume(work:(ByteArray)->T):T {
        val lease=synchronized(this){if(!valid())throw SensitiveProblem(SensitiveFailure.DENIED);bytes!!.copyOf()}
        try { val result=work(lease);if(!valid())throw SensitiveProblem(SensitiveFailure.DENIED);return result }
        finally {lease.fill(0)}
    }
}
