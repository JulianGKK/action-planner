package org.pag.data.product

import org.pag.domain.*

/** Only the original session/read ticket and original graph's booking. No payload references,
 * keys, replacement Session or independent allowance. Main-only synchronous publication.
 */
internal class OriginalWorkspaceAccess(
    internal val entry:OutstandingCoreRead.FullviewEntry,
    internal val source:StorageAccess,internal val original:StorageContext,
    private val ticket:WorkspaceMutationInvalidation.ActiveReadTicket,
    private val mutations:WorkspaceMutationInvalidation,
    internal val request:WorkspaceReadRequest,internal val thread:Thread
):WorkspaceAccess {
    internal val status get()=request.status
    internal val mutationEpoch get()=ticket.mutationEpoch
    private var publishing=0
    internal fun liveOnWorker()=entry.readable() && ticket.isLive() && request.ticket?.active!=false && original.check(AccessPoint.STEP)
    override fun publish(actionId:String,status:ActionStatus,generation:Long,work:()->Unit):Boolean {
        if(Thread.currentThread()!==thread || publishing>=2 || actionId!=request.actionId || status!=request.status || generation!=request.generation)return false
        val fresh=source.acquire(AccessScope.READY)?:return false
        fun current()=fresh.scope==AccessScope.READY && fresh.generation==original.generation &&
            liveOnWorker() && mutations.isCurrentActiveRead(ticket) && fresh.check(AccessPoint.PUBLICATION)
        if(!current())return false
        publishing++
        try {
            var ran=false
            val accepted=fresh.publish{if(current()){work();ran=true}}
            return accepted && ran && current()
        }finally{publishing--}
    }
    override fun heldAllowed():Boolean {
        if(Thread.currentThread()!==thread || publishing>=2)return false
        val fresh=source.acquire(AccessScope.READY)?:return false
        return fresh.scope==AccessScope.READY && fresh.generation==original.generation && entry.readable() &&
            request.ticket?.active!=false && original.check(AccessPoint.STEP) && fresh.check(AccessPoint.PUBLICATION)
    }
    override fun consume(units:ActiveWorkspaceBudget.Units,producer:()->Unit):Boolean {
        if(Thread.currentThread()!==thread || publishing>=2)return false
        val fresh=source.acquire(AccessScope.READY)?:return false
        fun current()=fresh.scope==AccessScope.READY && fresh.generation==original.generation &&
            entry.readable() && request.ticket?.active!=false && original.check(AccessPoint.STEP) && fresh.check(AccessPoint.PUBLICATION)
        if(!current())return false
        if(!entry.reserve(units))return false
        publishing++
        try{var ran=false
            val accepted=fresh.publish{if(current()){producer();ran=true}}
            return accepted && ran && current()
        }finally{publishing--}
    }
    override fun reserve(units:ActiveWorkspaceBudget.Units):Boolean {
        var booked=false
        return publish(request.actionId,request.status,request.generation){booked=entry.reserve(units)} && booked
    }
    companion object {
        // Disjoint, once per original preflight: capability + bounded synchronous control
        // scratch. These functions cannot queue/retain the fresh context or caller closure.
        // This is NOT an allowance for data copies, formatting or form/Compose callbacks.
        val cost=ActiveWorkspaceBudget.Units(objects=20,references=64)
    }
}
