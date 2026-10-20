package org.pag.domain

/** Data evidence, never inferred from a repository success callback. COMMITTED/ROLLED_BACK
 * require a successful outer transaction end (or proven nonexecution for ROLLED_BACK);
 * cleanup requires the entire DB/key scope return.
 */
enum class WorkspaceWriteOutcome { COMMITTED, ROLLED_BACK, UNKNOWN }
enum class WorkspaceWriteCleanup { COMPLETE, FAILED_OR_UNCONFIRMED }
data class WorkspaceWriteCompletion(val outcome: WorkspaceWriteOutcome, val cleanup: WorkspaceWriteCleanup)

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
    interface Pending {
        fun finish(completion: WorkspaceWriteCompletion): Boolean
        fun isCurrent(): Boolean
    }
}

/** Live default while the product snapshot owner is UNWIRED. Bounded metadata only (one
 * pending/latest identity), epoch advanced before queue, UNKNOWN terminal for that generation.
 * This is NOT an
 * alternative snapshot/session owner: it has no payload or read/hit API. RAM reuse remains
 * unconditionally disabled; remaining non-core writer families are NOT SAFE FOR RAM REUSE.
 */
class UncachedWorkspaceMutations : WorkspaceMutationInvalidation {
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
        if (pending != null || generation < 0 || mutation.actionId?.isBlank() == true) return null
        // Only a new READY generation supplied by the actual repository context may start
        // fresh UN-CACHED bookkeeping. Never restores an ActiveWorkspaceValidity instance.
        if (this.generation != generation) {
            this.generation = generation; uncertain = false; latest = null
        }
        if (uncertain) return null
        mutationEpoch = try { Math.incrementExact(mutationEpoch) } catch (_: ArithmeticException) {
            uncertain = true; latest = null; return null
        }
        return Ticket().also { pending = it; latest = it }
    }
    private inner class Ticket : WorkspaceMutationInvalidation.Pending {
        override fun finish(completion: WorkspaceWriteCompletion): Boolean {
            owner()
            if (pending !== this) return false
            pending = null
            if (completion.outcome == WorkspaceWriteOutcome.UNKNOWN ||
                completion.cleanup != WorkspaceWriteCleanup.COMPLETE) {
                uncertain = true; latest = null
            }
            return true
        }
        override fun isCurrent(): Boolean { owner(); return !uncertain && pending == null && latest === this }
    }
}
