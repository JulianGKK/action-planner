package org.pag.domain

import java.util.concurrent.atomic.AtomicReference

/** Data evidence, never inferred from a repository success callback. COMMITTED/ROLLED_BACK
 * require a successful outer transaction end (or proven nonexecution for ROLLED_BACK);
 * cleanup requires the entire DB/key scope return.
 */
enum class WorkspaceWriteOutcome { COMMITTED, ROLLED_BACK, UNKNOWN }
enum class WorkspaceWriteCleanup { COMPLETE, FAILED_OR_UNCONFIRMED }
data class WorkspaceWriteCompletion(val outcome: WorkspaceWriteOutcome, val cleanup: WorkspaceWriteCleanup)

/** Whole document attempt, NOT an outer ACID transaction or a repository result.
 * NOT_EXECUTED requires proven nonexecution, including no uncertain DB opening.
 * COMPLETE claims all required local phases/file operations/syncs ended and no job remains.
 * RECOVERY_REQUIRED preserves known partial effects; UNKNOWN preserves uncertain evidence.
 * Cleanup remains independent: an unconfirmed resource end always prevents safe release.
 */
enum class DocumentWriteOutcome { NOT_EXECUTED, COMPLETE, RECOVERY_REQUIRED, UNKNOWN }
/** Producer assertion only, no evidence producer or session authority is supplied here.
 * COMPLETE additionally requires a fresh consistent GLOBAL inventory AFTER the last DB step.
 * The trusted future Data producer must prove that assertion and all DB/key/file/snapshot
 * cleanup; this boolean cannot establish it. No payload, locator, key or callback retention.
 */
data class DocumentWriteCompletion(val outcome: DocumentWriteOutcome, val cleanup: WorkspaceWriteCleanup,
    val finalInventoryConfirmed: Boolean = false) {
    init { require(outcome != DocumentWriteOutcome.COMPLETE || finalInventoryConfirmed) }
}

/** Fixed conservative impacts; no caller-supplied subset may omit dependent views. */
enum class WorkspaceWriterFamily { TASK, ACTION_CREATE, ACTION_EDIT, ACTION_STATUS, PERSON, OPERATIONS, MATERIAL, EXPENSE, PROTOCOL, MEMORY_REPORT, LEADERSHIP }
data class WorkspaceMutation(val family: WorkspaceWriterFamily, val actionId: String?) {
    init { require(if(family == WorkspaceWriterFamily.ACTION_CREATE) actionId == null else actionId != null) }
}

/** Owner-thread metadata port. beforeQueue MUST synchronously invalidate TASKS, TASK_COUNTS
 * and PEOPLE for tasks; core writers conservatively invalidate ALL core parts before queue;
 * MATERIAL/EXPENSE invalidate their board and summary (including overview counts/totals).
 * PROTOCOL/MEMORY_REPORT invalidate their read VALIDITY METADATA ONLY, never retain content.
 * No payload, key,
 * session authority or retention grant. A null ticket denies the write, never bypasses fencing.
 * finish is after ALL cleanup, including failures, and does not mark any projection clean.
 */
interface WorkspaceMutationInvalidation {
    fun beforeTaskQueue(actionId: String, generation: Long): Pending?
    /** Old task-only adapters fail CLOSED for other families until explicitly implemented. */
    fun beforeCoreQueue(mutation: WorkspaceMutation, generation: Long): Pending? = null
    /** Explicit opt-in, never a core-family fallback. Call on the owner thread, synchronously
     * BEFORE worker.execute, using an action and current READY generation from StorageContext.
     * The port cannot authenticate a raw generation or certify prior maintenance itself.
     */
    fun beforeDocumentQueue(actionId: String, generation: Long): Pending? = null

    /** Read-only admission from the SAME owner/epoch as writer admission. This ticket grants
     * neither SessionAccess nor permission; callers must separately hold and recheck a current
     * StorageContext. Implementations without an integrated owner fail closed.
     */
    fun beforeActiveRead(actionId: String, generation: Long): ActiveReadTicket? = null
    /** Owner-thread final fence immediately before central StorageContext publication. */
    fun isCurrentActiveRead(ticket: ActiveReadTicket): Boolean = false
    /** Explicit lifecycle/action/revoke fence. Does not change the mutation epoch. */
    fun revokeActiveRead() {}
    /** Worker-safe cancellation of only this exact read identity. Closing an old lifetime
     * must never revoke a newer read. Does not mutate pending, epoch or owner admission.
     */
    fun revokeActiveRead(ticket: ActiveReadTicket) {}

    interface ActiveReadTicket {
        val actionId: String
        val generation: Long
        val mutationEpoch: Long
        /** Worker-safe liveness hint only; final authorization must return to the owner thread. */
        fun isLive(): Boolean
        /** One bounded lifetime consumer; unprepared implementations deny retention. */
        fun onInvalidated(invalidate: () -> Unit): Subscription { invalidate(); return Subscription {} }
    }

    interface Pending {
        /** Worker-safe metadata receipt AFTER synchronous repository work/DB/key cleanup,
         * BEFORE UI dispatch. Not a runnable/payload end receipt or current-generation release.
         * Only a later trusted READY binding may recover an abandoned clean CORE dispatch.
         */
        fun recordWorkCompletion(completion:WorkspaceWriteCompletion) {}
        fun finish(completion: WorkspaceWriteCompletion): Boolean
        /** Only a document ticket may consume this completion; old adapters deny by default.
         * True means metadata completion consumed, not mutation success or UI authorization.
         * No completion marks a read projection clean.
         */
        fun finishDocument(completion: DocumentWriteCompletion): Boolean = false
        fun isCurrent(): Boolean
    }
}

/** Live default while the product snapshot owner is UNWIRED. Bounded metadata only (one
 * pending/latest identity plus one revocable read ticket), epoch advanced before queue,
 * UNKNOWN terminal for that generation. The read ticket is not a snapshot or hit API. RAM
 * reuse remains unconditionally disabled. Document metadata admission is available here;
 * ACTIVE permission binding/projections remain UNWIRED.
 */
class UncachedWorkspaceMutations : WorkspaceMutationInvalidation {
    private enum class Kind { CORE, DOCUMENT }
    private val owner = Thread.currentThread()
    private var pending: Ticket? = null
    private var latest: Ticket? = null
    private var uncertain = false
    private var generation: Long? = null
    private var admitting=false // Reentrant revoke listeners cannot start an overlapping admission.
    // Only ticket liveness may be revoked by a foreign-thread session observer.
    // Admission, pending and mutationEpoch remain owner-thread-only.
    private val activeRead = AtomicReference<ReadTicket?>(null)
    var mutationEpoch = 0L; private set
    val ramReuseEnabled: Boolean get() = false
    private fun owner() { check(Thread.currentThread() === owner) }
    override fun beforeTaskQueue(actionId: String, generation: Long) =
        beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,actionId),generation)
    override fun beforeCoreQueue(mutation: WorkspaceMutation, generation: Long): WorkspaceMutationInvalidation.Pending? {
        owner()
        if (mutation.actionId?.isBlank() == true) return null
        return begin(generation,Kind.CORE)
    }
    override fun beforeDocumentQueue(actionId: String, generation: Long): WorkspaceMutationInvalidation.Pending? {
        owner()
        if (actionId.isBlank()) return null
        return begin(generation,Kind.DOCUMENT)
    }
    override fun beforeActiveRead(actionId: String, generation: Long): WorkspaceMutationInvalidation.ActiveReadTicket? {
        owner()
        if (admitting || actionId.isBlank() || generation < 0) return null
        admitting=true
        try {
            if (!prepareGeneration(generation) || pending != null || uncertain) return null
            val ticket=ReadTicket(actionId,generation,mutationEpoch)
            activeRead.getAndSet(ticket)?.invalidate()
            return ticket
        } finally { admitting=false }
    }
    override fun isCurrentActiveRead(ticket: WorkspaceMutationInvalidation.ActiveReadTicket): Boolean {
        owner()
        val current=activeRead.get() ?: return false
        return ticket === current && current.isLive() && !uncertain && pending == null &&
            generation == current.generation && mutationEpoch == current.mutationEpoch
    }
    override fun revokeActiveRead() {
        // SessionCoordinator publishes synchronously on the thread that discovers
        // expiry. This operation only revokes the exact ticket; it never touches
        // owner-thread admission, pending, or epoch state.
        activeRead.getAndSet(null)?.invalidate()
    }
    override fun revokeActiveRead(ticket: WorkspaceMutationInvalidation.ActiveReadTicket) {
        if(ticket is ReadTicket && ticket.belongsTo(this)) {
            // Revoke the issued ticket even if a concurrent admission has already detached it.
            // CAS protects a newer registry entry; the original liveness must still end now.
            activeRead.compareAndSet(ticket,null)
            ticket.invalidate()
        }
    }
    private fun begin(generation: Long, kind: Kind): WorkspaceMutationInvalidation.Pending? {
        if(admitting)return null
        admitting=true
        try {
            if (!prepareGeneration(generation)) return null
            if (pending != null || uncertain) return null
            // Revoke synchronously before queue; listeners cannot reenter this unfinished admission.
            revokeActiveRead()
            mutationEpoch = try { Math.incrementExact(mutationEpoch) } catch (_: ArithmeticException) {
                uncertain = true; latest = null; return null
            }
            return Ticket(kind,generation).also { pending = it; latest = it }
        } finally { admitting=false }
    }
    private fun prepareGeneration(generation: Long): Boolean {
        if (generation < 0) return false
        // Only a new READY generation supplied by the actual repository context may start
        // fresh UN-CACHED bookkeeping. It may supersede an abandoned DOCUMENT ticket after
        // authorized recovery; a CORE ticket without a confirmed clean work end remains blocking.
        // The port itself does not certify maintenance or restore ActiveWorkspaceValidity.
        if (this.generation != generation) {
            val old=pending
            if (old?.kind == Kind.CORE) {
                val ended=old.workCompletion.get() ?: return false
                if(ended.cleanup!=WorkspaceWriteCleanup.COMPLETE)return false
                // Original synchronous work ended; production's serialized maintenance ran
                // before this fresh READY context. Old UI finish/patch can never consume B.
                pending=null;latest=null
            }
            revokeActiveRead()
            this.generation = generation; uncertain = false; pending = null; latest = null
        }
        return true
    }
    private inner class ReadTicket(override val actionId:String, override val generation:Long,
        override val mutationEpoch:Long) : WorkspaceMutationInvalidation.ActiveReadTicket {
        fun belongsTo(port:UncachedWorkspaceMutations)=this@UncachedWorkspaceMutations === port
        private val live=java.util.concurrent.atomic.AtomicBoolean(true)
        private val listener=AtomicReference<(() -> Unit)?>(null)
        fun invalidate() { live.set(false); listener.getAndSet(null)?.let { runCatching(it) } }
        override fun isLive() = live.get()
        override fun onInvalidated(invalidate: () -> Unit): Subscription {
            check(listener.compareAndSet(null,invalidate)) { "One ACTIVE lifetime consumer" }
            if(!live.get()) listener.getAndSet(null)?.let { runCatching(it) }
            return Subscription { listener.compareAndSet(invalidate,null) }
        }
    }
    private inner class Ticket(val kind: Kind, private val admittedGeneration: Long) : WorkspaceMutationInvalidation.Pending {
        val workCompletion=AtomicReference<WorkspaceWriteCompletion?>(null)
        override fun recordWorkCompletion(completion:WorkspaceWriteCompletion) {
            if(kind==Kind.CORE)workCompletion.compareAndSet(null,completion)
        }
        override fun finish(completion: WorkspaceWriteCompletion): Boolean {
            owner()
            if (kind != Kind.CORE || pending !== this || generation != admittedGeneration) return false
            if(workCompletion.get()?.let{it!=completion}==true)return false
            return end(completion.outcome == WorkspaceWriteOutcome.UNKNOWN || completion.cleanup != WorkspaceWriteCleanup.COMPLETE)
        }
        override fun finishDocument(completion: DocumentWriteCompletion): Boolean {
            owner()
            if (kind != Kind.DOCUMENT || pending !== this || generation != admittedGeneration) return false
            return end(completion.outcome == DocumentWriteOutcome.RECOVERY_REQUIRED || completion.outcome == DocumentWriteOutcome.UNKNOWN ||
                completion.cleanup != WorkspaceWriteCleanup.COMPLETE)
        }
        private fun end(unconfirmed: Boolean): Boolean {
            pending = null
            if (unconfirmed) {
                uncertain = true; latest = null
            }
            return true
        }
        override fun isCurrent(): Boolean { owner(); return !uncertain && pending == null && latest === this && generation == admittedGeneration }
    }
}
