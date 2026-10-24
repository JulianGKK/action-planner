package org.pag.data.product

import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** The original authority remains the delegated context. A callback already executed by a
 * broken provider cannot be undone; actual SessionCoordinator publication is atomic. */
internal fun restrictedLeadershipContext(c:StorageContext,allowed:()->Boolean):StorageContext=object:StorageContext {
    override val generation=c.generation
    override val scope=c.scope
    override fun check(point:AccessPoint)=c.check(point)&&allowed()
    override fun publish(result:()->Unit):Boolean {
        if(!allowed())return false
        var delivered=false
        val accepted=c.publish{if(allowed()){result();delivered=true}}
        return accepted && delivered && allowed()
    }
    override fun <T> admitKey(validUntilMillis:Long,acquire:(()->Boolean)->T?):T? =
        c.admitKey(validUntilMillis){authorize->acquire{authorize() && allowed()}}
}

/** Exact existing setup format. No interpretation of corrupt/foreign data as empty. */
internal object LeadershipRecoveryChecks {
    fun state(row:LeadershipRow,rows:List<SensitiveRow>,present:Boolean,usable:Boolean):SensitiveState {
        val result=stateForRecovery(row,rows.size.toLong(),present,usable)
        if(result==SensitiveState.DELETE_PENDING)rows.forEach{LeadershipMetadata.validateArea(it,row.actionId)}
        return result
    }
    // Recovery reads only COUNT, never materializes sensitive ciphertext to prove emptiness.
    // DELETE_PENDING is an already committed authorized whole-area delete intent; as in the
    // original continuation, its remaining action-owned rows are removed without opening them.
    fun stateForRecovery(row:LeadershipRow,areaCount:Long,present:Boolean,usable:Boolean):SensitiveState {
        fun require(value:Boolean){if(!value)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)}
        require(row.actionId.isNotBlank() && areaCount>=0)
        val epoch=try{UUID.fromString(row.epoch)}catch(_:Exception){throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)}
        require(epoch.toString()==row.epoch && epoch.version()==4 && epoch.variant()==2)
        require(row.cryptoVersion==1 && row.algorithm=="Argon2id" && row.kdfVersion==19 &&
            row.memoryKiB==65536 && row.iterations==3 && row.parallelism==1 && row.salt.size==16)
        require(!present || usable) // unusable/unknown alias is never safe to delete
        return when(row.state) {
            "RESERVED"->{require(row.envelope.isEmpty() && areaCount==0L);SensitiveState.INCOMPLETE_SETUP}
            "DELETE_PENDING"->{require(row.envelope.size==88);SensitiveState.DELETE_PENDING}
            else->throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        }
    }
}

/** One bounded command snapshot, no registry, key, plaintext, StorageContext or authority.
 * Identity checks only narrow an independently authorized fresh READY generation. */
internal class LeadershipRecoveryIntent(private val issuer:Any,private val status:ActionStatus,
    private val beforeGeneration:Long,private val original:LeadershipRow,
    override val state:SensitiveState):LeadershipRecoveryRequest {
    private val spent=AtomicBoolean(false)
    private val cancelled=AtomicBoolean(false)
    override val actionId=original.actionId
    override fun cancel(){cancelled.set(true);spent.set(true)}
    fun live()=!cancelled.get()
    fun take(owner:Any):LeadershipRecoveryIntent? =
        if(issuer===owner && spent.compareAndSet(false,true))this else null
    fun requireFresh(c:StorageContext) {
        if(!live() || beforeGeneration<0 || c.generation<=beforeGeneration || c.scope!=AccessScope.READY || !c.check(AccessPoint.OPERATION))
            throw SensitiveProblem(SensitiveFailure.DENIED)
    }
    fun verify(currentStatus:ActionStatus,row:LeadershipRow?,areaCount:Long,present:Boolean,usable:Boolean) {
        if(!live() || currentStatus!=status || !LeadershipRules.mayWrite(currentStatus) || row==null ||
            row.copy(salt=original.salt,envelope=original.envelope)!=original ||
            !row.salt.contentEquals(original.salt) || !row.envelope.contentEquals(original.envelope) ||
            LeadershipRecoveryChecks.stateForRecovery(row,areaCount,present,usable)!=state)
            throw SensitiveProblem(SensitiveFailure.CONFLICT)
    }
}
