package org.pag.data.product

import org.pag.domain.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** The existing loader's single outstanding ownership record. Metadata + one pending handoff,
 * never a cache, session authority or a second budget. No ledger ticket crosses suspension.
 *
 * Reserve the ENTIRE 16 MiB app-controlled transition envelope until the known ownership chain ends.
 * The unused part of a local reader receipt is held, not granted again to another reader.
 * Scope changes grant no retirement or reload permission for exposed app-controlled values.
 * Native/frame calibration remains unconfirmed: this is an exclusion fence, not certification.
 */
internal class OutstandingCoreRead {
    enum class Phase { READER_ADMITTED, WORKER_MATERIALIZING, CANDIDATE_COMPLETE, MAIN_PENDING,
        MAIN_DELIVERED, RETAINED, COMPOSE_TRANSITION, RETIRED, DENIED, REVOKED, FAILED_OR_UNCONFIRMED }
    data class State(val phase:Phase, val retained:Long, val transition:Long, val localRetained:Long,
        val localTransition:Long, val pendingCandidates:Int, val compositionObserved:Boolean,
        val compositionDisposed:Boolean, val retirementConfirmed:Boolean, val refusal:String,
        val fullViewUnconfirmed:Boolean, val nativeFrameCalibration:String="UNCONFIRMED")
    private var current: Read?=null
    private var closed=false
    private var fullView=false
    private var refusal="none"
    private var last=Phase.RETIRED
    @Synchronized fun begin():Read? {
        if(closed || fullView || current!=null) {
            refusal=when {closed->"CLOSED";fullView->"FULL_VIEW_UNCONFIRMED";current!!.observed->"COMPOSE_OR_FRAME_UNCONFIRMED";else->"OUTSTANDING_READ"}
            return null
        }
        refusal="none"
        return Read().also{current=it}
    }
    @Synchronized fun fullViewStarting(){fullView=true;refusal="FULL_VIEW_UNCONFIRMED"}
    @Synchronized fun close(){closed=true}
    @Synchronized fun state():State {
        val r=current
        return State(r?.phase ?: last,if(r?.retainedReserved==true)r.retained else 0,
            if(r!=null)ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES else 0,r?.retained ?: 0,r?.transition ?: 0,
            if(r?.payload?.get() is ActiveCoreResult.Value || (r?.deliveringValue==true && !r.published))1 else 0,r?.observed==true,r?.disposed==true,
            r==null,refusal,fullView)
    }
    // Queue shells own their original Read only until the first actual invocation takes it.
    // The local original survives through dispatch's existing finally/ACK, not through a
    // persistent shell edge. Taking is NOT an acknowledgement or an admission signal.
    private class WorkerHandoff(private var read:Read?):Runnable {
        override fun run() {
            val original=synchronized(this) { read.also { read=null } } ?: return
            original.dispatchWorker()
        }
    }
    private class MainHandoff(private var read:Read?):Runnable {
        override fun run() {
            val original=synchronized(this) { read.also { read=null } } ?: return
            original.dispatchMain()
        }
    }
    inner class Read internal constructor() {
        internal var phase=Phase.READER_ADMITTED
        internal var retained=0L
        internal var transition=0L
        internal var published=false
        internal var retainedReserved=false
        internal var deliveringValue=false
        internal var observed=false
        internal var disposed=false
        internal val payload=AtomicReference<ActiveCoreResult?>()
        private var admitted=false
        private var revoked=false
        private var worker=false
        private var workerEnded=false
        private val workerWork=AtomicReference<Runnable?>()
        private var workerQueued=false
        private val workerDispatched=AtomicBoolean(false)
        private val mainDispatched=AtomicBoolean(false)
        private var delivering=false
        private var deliveryEnded=false
        private val mainWork=AtomicReference<Runnable?>()
        private var mainQueued=false
        private var mainAcknowledged=false
        private var storageClosed=true
        private var storageOpened=false
        private var cleanupFailed=false
        private var keyClosed=true
        private var callbackOwned=false
        private var exposed=false
        fun queueWorker(work:Runnable):Runnable?=synchronized(this@OutstandingCoreRead) {
            if(current!==this || revoked)return@synchronized null
            check(!workerQueued);workerQueued=true;workerWork.set(work);WorkerHandoff(this)
        }
        internal fun dispatchWorker() {
            if(!workerDispatched.compareAndSet(false,true))return
            try{workerWork.getAndSet(null)?.run()}
            finally{workerFinished()}
        }
        fun startWorker():Boolean=synchronized(this@OutstandingCoreRead) {
            if(current!==this || revoked || worker)return@synchronized false
            worker=true;true
        }
        /** Called by the real store AFTER local joint admission, BEFORE either materializer. */
        fun admit(reservation:ActiveCoreReservation,retained:Long,transition:Long)=synchronized(this@OutstandingCoreRead) {
            if(current!==this || revoked || admitted)throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
            val r=ActiveWorkspaceBudget.estimateUnits(reservation.retained)
            val t=ActiveWorkspaceBudget.estimateUnits(reservation.transition)
            if(r.first!=ActiveWorkspaceBudget.Decision.ADMITTED || t.first!=ActiveWorkspaceBudget.Decision.ADMITTED ||
                r.second!=retained || t.second!=transition || retained !in 0..ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES || transition !in 0..ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES) {
                phase=Phase.DENIED;throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)
            }
            // There cannot be another retained/transition obligation: begin() kept the sole
            // reservation exclusive, including old Compose and unconfirmed native cleanup.
            this.retained=retained;this.transition=transition;admitted=true;retainedReserved=true;phase=Phase.WORKER_MATERIALIZING
        }
        fun storageStarting()=synchronized(this@OutstandingCoreRead){storageOpened=true;storageClosed=false}
        fun storageEnded(confirmed:Boolean)=synchronized(this@OutstandingCoreRead){storageClosed=confirmed && !cleanupFailed}
        fun cleanupUnconfirmed()=synchronized(this@OutstandingCoreRead){if(storageOpened){cleanupFailed=true;storageClosed=false}}
        fun keyStarting()=synchronized(this@OutstandingCoreRead){keyClosed=false}
        fun keyEnded()=synchronized(this@OutstandingCoreRead){keyClosed=true}
        fun candidate(result:ActiveCoreResult)=synchronized(this@OutstandingCoreRead) {
            if(result is ActiveCoreResult.Value && (!admitted || !storageClosed || !keyClosed || result.retainedCharge!=retained || result.transitionCharge!=transition))
                throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
            if(!revoked){payload.set(result);phase=Phase.CANDIDATE_COMPLETE}
        }
        fun queueMain(work:Runnable):Runnable?=synchronized(this@OutstandingCoreRead) {
            if(revoked)return@synchronized null
            check(!mainQueued);mainQueued=true;mainWork.set(work);phase=Phase.MAIN_PENDING
            MainHandoff(this)
        }
        internal fun dispatchMain() {
            if(!mainDispatched.compareAndSet(false,true))return
            try{mainWork.getAndSet(null)?.run()}
            finally{synchronized(this@OutstandingCoreRead){mainAcknowledged=true;retireIfEnded()}}
        }
        fun mainRejected()=synchronized(this@OutstandingCoreRead){mainWork.set(null);mainAcknowledged=true;revoke()}

        fun workerFinished()=synchronized(this@OutstandingCoreRead){workerEnded=true;retireIfEnded()}
        fun noWorker()=synchronized(this@OutstandingCoreRead){if(!worker)workerEnded=true;revoked=true;payload.set(null);workerWork.set(null);retireIfEnded()}
        fun takeForMain():ActiveCoreResult?=synchronized(this@OutstandingCoreRead) {
            if(revoked || delivering || deliveryEnded)return@synchronized null
            val result=payload.getAndSet(null) ?: return@synchronized null
            delivering=true;deliveringValue=result is ActiveCoreResult.Value;phase=Phase.MAIN_DELIVERED;result
        }
        fun callbackOwnsValue():Boolean=synchronized(this@OutstandingCoreRead){
            if(revoked)return@synchronized false
            callbackOwned=true;published=true;exposed=true;phase=Phase.RETAINED;true
        }
        fun mainFinished()=synchronized(this@OutstandingCoreRead){delivering=false;deliveringValue=false;deliveryEnded=true;retireIfEnded()}
        fun revoke()=synchronized(this@OutstandingCoreRead) {
            revoked=true;payload.set(null);workerWork.set(null);mainWork.set(null);callbackOwned=false;published=false;retainedReserved=false
            if(!workerQueued)workerEnded=true
            phase=if(exposed)Phase.COMPOSE_TRANSITION else Phase.REVOKED
            retireIfEnded()
        }
        fun compositionObserved()=synchronized(this@OutstandingCoreRead){observed=true}
        fun compositionDisposed()=synchronized(this@OutstandingCoreRead){disposed=true /* NOT frame retirement. */}
        private fun retireIfEnded() {
            if(!revoked || !workerEnded || delivering || callbackOwned || (mainQueued && !mainAcknowledged))return
            if(!storageClosed || !keyClosed){phase=Phase.FAILED_OR_UNCONFIRMED;return}
            if(exposed){phase=Phase.COMPOSE_TRANSITION;return} // No trustworthy frame-retirement producer yet.
            payload.set(null);phase=Phase.RETIRED;last=phase
            if(current===this)current=null // Identity-scoped; old cleanup cannot retire a newer read.
        }
    }
}
