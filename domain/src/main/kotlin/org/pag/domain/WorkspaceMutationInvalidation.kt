package org.pag.domain

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
enum class WorkspaceWriterFamily { TASK, ACTION_CREATE, ACTION_EDIT, ACTION_STATUS, PERSON, OPERATIONS, MATERIAL, EXPENSE, PROTOCOL, MEMORY_REPORT }
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
    interface Pending {
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
 * pending/latest identity), epoch advanced before queue, UNKNOWN terminal for that generation.
 * This is NOT an
 * alternative snapshot/session owner: it has no payload or read/hit API. RAM reuse remains
 * unconditionally disabled. Document metadata admission is available here in isolation;
 * document repositories/evidence producers and ACTIVE projections remain UNWIRED.
 */
class UncachedWorkspaceMutations : WorkspaceMutationInvalidation {
    private enum class Kind { CORE, DOCUMENT }
    private val owner = Thread.currentThread()
    private var pending: Ticket? = null
    private var latest: Ticket? = null
    private var uncertain = false
    private var generation: Long? = null
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
    private fun begin(generation: Long, kind: Kind): WorkspaceMutationInvalidation.Pending? {
        if (generation < 0) return null
        // Only a new READY generation supplied by the actual repository context may start
        // fresh UN-CACHED bookkeeping. It may supersede an abandoned DOCUMENT ticket after
        // authorized recovery; an unfinished CORE ticket keeps its established blocking rule.
        // The port itself does not certify maintenance or restore ActiveWorkspaceValidity.
        if (this.generation != generation) {
            if (pending?.kind == Kind.CORE) return null
            this.generation = generation; uncertain = false; pending = null; latest = null
        }
        if (pending != null || uncertain) return null
        mutationEpoch = try { Math.incrementExact(mutationEpoch) } catch (_: ArithmeticException) {
            uncertain = true; latest = null; return null
        }
        return Ticket(kind,generation).also { pending = it; latest = it }
    }
    private inner class Ticket(val kind: Kind, private val admittedGeneration: Long) : WorkspaceMutationInvalidation.Pending {
        override fun finish(completion: WorkspaceWriteCompletion): Boolean {
            owner()
            if (kind != Kind.CORE || pending !== this || generation != admittedGeneration) return false
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
