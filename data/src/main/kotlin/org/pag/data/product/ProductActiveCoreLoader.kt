package org.pag.data.product

import org.pag.domain.*
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** Optional package-local counters; only the isolated P77 debug authority supplies one. */
internal interface ActiveCoreLoadObservation {
    fun ticketIssued(ticket: WorkspaceMutationInvalidation.ActiveReadTicket)
    fun readerStarted()
    fun hitAuthorized(accepted: Boolean, elapsedNanos:Long)
}

/** One loader, one outstanding memory lifetime, one original readticket. The pending runnable
 * captures only the single handoff cell, never a separate immutable payload capture. */
class ProductActiveCoreLoader internal constructor(
    private val access: StorageAccess,
    private val mutations: WorkspaceMutationInvalidation,
    private val worker: Executor,
    private val main: Executor,
    private val readCandidate: (StorageContext, WorkspaceMutationInvalidation.ActiveReadTicket, Long, OutstandingCoreRead.Read) -> ActiveCoreResult,
    private val outstanding:OutstandingCoreRead=OutstandingCoreRead()
) : ActiveCoreLoadPort {
    internal constructor(access:StorageAccess,mutations:WorkspaceMutationInvalidation,worker:Executor,main:Executor,
        readCandidate:(StorageContext,WorkspaceMutationInvalidation.ActiveReadTicket,Long,OutstandingCoreRead.Read)->ActiveCoreResult):
        this(access,mutations,worker,main,readCandidate,OutstandingCoreRead())
    override var openingFailure:WorkspaceFailure?=null;private set
    private var reuseForIsolatedTest=false
    private var observation:ActiveCoreLoadObservation?=null
    override val reuseEnabled get()=reuseForIsolatedTest
    internal constructor(access:StorageAccess,mutations:WorkspaceMutationInvalidation,worker:Executor,main:Executor,
        readCandidate:(StorageContext,WorkspaceMutationInvalidation.ActiveReadTicket,Long,OutstandingCoreRead.Read)->ActiveCoreResult,
        reuseEnabled:Boolean,observation:ActiveCoreLoadObservation?,outstanding:OutstandingCoreRead=OutstandingCoreRead()):this(access,mutations,worker,main,readCandidate,outstanding) {
        reuseForIsolatedTest=reuseEnabled;this.observation=observation
    }
    private val ownerThread=Thread.currentThread()
    private var loaderClosed=false
    private var latestLoad:Any?=null
    private var lifetime:ProductActiveCoreLifetime?=null
    internal fun outstandingState()=outstanding.state()
    override fun cancelPending() {
        check(Thread.currentThread()===ownerThread)
        if(outstanding.state().phase !in setOf(OutstandingCoreRead.Phase.RETAINED,OutstandingCoreRead.Phase.COMPOSE_TRANSITION))lifetime?.close()
    }
    override fun fullViewStarting() {
        check(Thread.currentThread()===ownerThread)
        cancelPending();outstanding.fullViewStarting()
    }
    fun close(){
        loaderClosed=true;lifetime?.close()
        // Closing this Runtime does not certify the end of the original memory record.
    }
    fun load(actionId:String,callback:(ActiveCoreResult)->Unit):Boolean =
        loadOwned(actionId){result,life->try{callback(result)}finally{life?.close()}}

    override fun loadOwned(actionId:String,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit):Boolean = submit(actionId,false,callback)
    override fun openOwned(actionId:String,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit):Boolean = submit(actionId,true,callback)
    private fun submit(actionId:String,explicit:Boolean,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit):Boolean {
        check(Thread.currentThread()===ownerThread){"ACTIVE core admission is owner-thread-only"}
        openingFailure=null
        if(loaderClosed || actionId.isBlank())return false
        // Backpressure BEFORE READY/ticket/namespace/key/worker admission. It cannot cancel
        // another read, change an epoch or mint fallback authority just because it is busy.
        val child=explicit && outstanding.hasResource()
        val root=if(child)null else outstanding.begin() ?: return false
        val context=access.acquire(AccessScope.READY)
        if(context==null || context.scope!=AccessScope.READY || context.generation<0 || !context.check(AccessPoint.OPERATION)) {
            root?.noWorker();return false
        }
        val memory=root ?: outstanding.continueRead(actionId,context.generation) ?: run{openingFailure=outstanding.continuationFailure;return false}
        val ticket=mutations.beforeActiveRead(actionId,context.generation)
        if(ticket==null){memory.noWorker();return false}
        observation?.ticketIssued(ticket)
        if(ticket.actionId!=actionId || ticket.generation!=context.generation || ticket.mutationEpoch<0L ||
            !context.check(AccessPoint.STEP) || !ticket.isLive()) {
            mutations.revokeActiveRead(ticket);memory.noWorker();return false
        }
        val identity=Any();latestLoad=identity
        val lane=DocumentNamespaceLane.ticket()
        // Exactly ONE subscription on the original readticket, from admission to retirement.
        val life=ProductActiveCoreLifetime(access,context,mutations,ticket,lane,ownerThread,
            {latestLoad===identity},observation?.let{observer->{ok:Boolean,ns:Long->observer.hitAuthorized(ok,ns)}} ?: {_,_->},memory)
        lifetime=life
        val delivered=AtomicBoolean(false)
        val work=memory.queueWorker(Runnable work@{
                if(!memory.startWorker())return@work
                try {
                    if(!ticket.isLive() || !context.check(AccessPoint.STEP)){life.close();return@work}
                    observation?.readerStarted()
                    try {memory.candidate(readCandidate(context,ticket,lane,memory))}
                    catch(_:Exception){memory.candidate(ActiveCoreResult.Rejected(BoundedActiveStatus.STORAGE_FAILURE))}
                    if(!ticket.isLive() || !context.check(AccessPoint.STEP) || !DocumentNamespaceLane.isCurrent(lane)) {
                        life.close();return@work
                    }
                    val handoff=memory.queueMain(Runnable deliver@{
                            if(!delivered.compareAndSet(false,true))return@deliver
                            if(Thread.currentThread()!==ownerThread){life.close();return@deliver}
                            val result=memory.takeForMain() ?: return@deliver
                            try {
                                fun current()=latestLoad===identity && ticket.isLive() &&
                                    mutations.isCurrentActiveRead(ticket) && context.scope==AccessScope.READY &&
                                    context.generation==ticket.generation && context.check(AccessPoint.PUBLICATION) &&
                                    DocumentNamespaceLane.isCurrent(lane)
                                if(!current()){life.close();return@deliver}
                                if(result is ActiveCoreResult.Value &&
                                    (result.core.state.binding.actionId!=actionId || result.core.state.binding.generation!=ticket.generation ||
                                    result.core.state.mutationEpoch!=ticket.mutationEpoch || result.core.action.id!=actionId || result.core.action.status!=ActionStatus.ACTIVE)) {
                                    life.close();return@deliver
                                }
                                var entered=false
                                val accepted=runCatching{context.publish{if(current())entered=true}}.getOrDefault(false)
                                if(!accepted || !entered || !current()){life.close();return@deliver}
                                if(result is ActiveCoreResult.Value) {
                                    if(!memory.callbackOwnsValue()){life.close();return@deliver}
                                    try{callback(result,life)}catch(error:Exception){life.close();throw error}
                                } else {
                                    life.close();callback(result,null)
                                }
                            } finally {memory.mainFinished()}
                        })
                    if(handoff!=null)try{main.execute(handoff)}
                        catch(_:RejectedExecutionException){life.close();memory.mainRejected()}
                        catch(_:Exception){life.close()} // Queue acceptance uncertain: no acknowledgement.
                } catch(_:Exception){life.close()}
            }) ?: run{life.close();return false}
        try{worker.execute(work)}
        catch(_:RejectedExecutionException){life.close();memory.noWorker();return false}
        catch(_:Exception){life.close();return false} // Could have queued: keep reservation until shell acknowledges.
        return true
    }
}
