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

/** One-shot product bridge: owner-thread admission, existing worker reader, owner-thread
 * ticket/session/lane check and central publication. It hands off a transient result only;
 * it is not a snapshot owner and never enables RAM reuse.
 */
class ProductActiveCoreLoader internal constructor(
    private val access: StorageAccess,
    private val mutations: WorkspaceMutationInvalidation,
    private val worker: Executor,
    private val main: Executor,
    private val readCandidate: (StorageContext, WorkspaceMutationInvalidation.ActiveReadTicket, Long) -> ActiveCoreResult
) : ActiveCoreLoadPort {
    private var reuseForIsolatedTest = false
    private var observation: ActiveCoreLoadObservation? = null
    override val reuseEnabled: Boolean get() = reuseForIsolatedTest
    internal constructor(access:StorageAccess,mutations:WorkspaceMutationInvalidation,worker:Executor,main:Executor,
        readCandidate:(StorageContext,WorkspaceMutationInvalidation.ActiveReadTicket,Long)->ActiveCoreResult,
        reuseEnabled:Boolean,observation:ActiveCoreLoadObservation?) : this(access,mutations,worker,main,readCandidate) {
        this.reuseForIsolatedTest=reuseEnabled
        this.observation=observation
    }
    private val ownerThread=Thread.currentThread()
    private var latestLoad: Any? = null

    /** Returns false before any worker/DB work when READY or common read admission is absent.
     * A true return means queued, not successful. The callback receives only an ephemeral,
     * freshly revalidated candidate; callers must not treat it as retained cache authority.
     */
    fun load(actionId: String, callback: (ActiveCoreResult) -> Unit): Boolean {
        return loadOwned(actionId) { result,lifetime -> try { callback(result) } finally { lifetime?.close() } }
    }
    override fun loadOwned(actionId: String, callback: (ActiveCoreResult, ActiveCoreLifetime?) -> Unit): Boolean {
        check(Thread.currentThread() === ownerThread) { "ACTIVE core admission is owner-thread-only" }
        if(actionId.isBlank()) return false
        val context=access.acquire(AccessScope.READY) ?: return false
        if(context.scope!=AccessScope.READY || context.generation<0 || !context.check(AccessPoint.OPERATION)) return false
        val ticket=mutations.beforeActiveRead(actionId,context.generation) ?: return false
        observation?.ticketIssued(ticket)
        if(ticket.actionId!=actionId || ticket.generation!=context.generation || ticket.mutationEpoch<0L) {
            mutations.revokeActiveRead();return false
        }
        // Closes the acquire -> admission race: expiry may have happened before this ticket existed.
        if(!context.check(AccessPoint.STEP) || !ticket.isLive()) {
            mutations.revokeActiveRead();return false
        }
        val loadIdentity=Any()
        latestLoad=loadIdentity
        val laneTicket=DocumentNamespaceLane.ticket()
        val started=AtomicBoolean(false)
        val delivered=AtomicBoolean(false)
        try {
            worker.execute {
                if(!started.compareAndSet(false,true)) return@execute
                if(!ticket.isLive() || !context.check(AccessPoint.STEP)) return@execute
                observation?.readerStarted()
                val result=try { readCandidate(context,ticket,laneTicket) }
                    catch (_: Exception) { ActiveCoreResult.Rejected(BoundedActiveStatus.STORAGE_FAILURE) }
                if(!ticket.isLive() || !context.check(AccessPoint.STEP) || !DocumentNamespaceLane.isCurrent(laneTicket)) return@execute
                try {
                    main.execute {
                        if(!delivered.compareAndSet(false,true)) return@execute
                        if(Thread.currentThread() !== ownerThread) {
                            mutations.revokeActiveRead()
                            return@execute
                        }
                        if(latestLoad !== loadIdentity) return@execute
                        fun current(): Boolean = latestLoad === loadIdentity && context.scope==AccessScope.READY &&
                            context.generation==ticket.generation && ticket.actionId==actionId && ticket.isLive() &&
                            mutations.isCurrentActiveRead(ticket) && context.check(AccessPoint.PUBLICATION) &&
                            DocumentNamespaceLane.isCurrent(laneTicket)
                        if(!current()) return@execute
                        if(result is ActiveCoreResult.Value) {
                            val core=result.core
                            if(core.state.binding.actionId!=actionId || core.state.binding.generation!=ticket.generation ||
                                core.state.mutationEpoch!=ticket.mutationEpoch || core.action.id!=actionId ||
                                core.action.status!=ActionStatus.ACTIVE) {
                                mutations.revokeActiveRead()
                                return@execute
                            }
                        }
                        // The callback is intentionally side-effect-free with respect to product state.
                        // Capture only a local bit inside the central gate so callback-then-false
                        // implementations cannot leave a retained/public result behind.
                        var entered=false
                        val accepted=runCatching { context.publish { if(current()) entered=true } }.getOrDefault(false)
                        if(!accepted || !entered || !current()) {
                            mutations.revokeActiveRead()
                            return@execute
                        }
                        val lifetime=if(result is ActiveCoreResult.Value)
                            ProductActiveCoreLifetime(access,context,mutations,ticket,laneTicket,ownerThread,
                                { latestLoad === loadIdentity }, observation?.let { observer -> { accepted:Boolean,elapsed:Long -> observer.hitAuthorized(accepted,elapsed) } } ?: {_,_->})
                            else null
                        // A rejected read owns no retention lifetime. Retire exactly its original
                        // ticket too (including budget refusal), without changing the writer epoch.
                        if(lifetime==null) mutations.revokeActiveRead(ticket)
                        try { callback(result,lifetime) } catch(error:Exception) { lifetime?.close();throw error }
                    }
                } catch (_: RejectedExecutionException) {
                    // This only revokes the outstanding ticket. No worker-side owner/epoch reset.
                    mutations.revokeActiveRead()
                } catch (_: Exception) {
                    mutations.revokeActiveRead()
                }
            }
        } catch (_: RejectedExecutionException) {
            mutations.revokeActiveRead();latestLoad=null;return false
        } catch (_: Exception) {
            mutations.revokeActiveRead();latestLoad=null;return false
        }
        return true
    }
}
