package org.pag.data.product

import org.pag.domain.*
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

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
                            ProductActiveCoreLifetime(context,mutations,ticket,laneTicket,ownerThread) { latestLoad === loadIdentity }
                            else null
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
