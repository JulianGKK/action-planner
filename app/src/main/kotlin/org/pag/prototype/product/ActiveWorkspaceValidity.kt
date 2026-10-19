package org.pag.prototype.product

import org.pag.domain.AccessPoint
import org.pag.domain.AccessScope
import org.pag.domain.ActionStatus
import org.pag.domain.StorageAccess
import org.pag.domain.StorageContext

/**
 * Metadata only; NOT wired to navigation or writers. One owner thread, one ACTIVE binding,
 * no rebinding/reset after revoke or uncertain storage outcome. No payload/IO/key ownership.
 *
 * [currentBinding] must later come from the trusted scope/permission adapter, not a form.
 * That adapter must synchronously call [revoke] on scope/session/permission loss. Every
 * admission also consults StorageAccess; local metadata never grants storage permission.
 * Worker outcomes must be dispatched to the owner thread. No owner monitor is acquired
 * under the coordinator monitor. Publication callbacks must only swap prepared references;
 * no IO, projection building, lifecycle changes or reentrant mutation is permitted.
 */
internal class ActiveWorkspaceValidity(
    private val binding: Binding,
    private val access: StorageAccess,
    private val currentBinding: () -> Binding?
) {
    data class Binding(val actionId: String, val status: ActionStatus, val generation: Long,
        val permissionRevision: Long) {
        init {
            require(actionId.isNotBlank() && status == ActionStatus.ACTIVE)
            require(generation >= 0 && permissionRevision >= 0)
        }
    }
    enum class Projection { ACTION, PEOPLE, OPERATIONS, TASKS, TASK_COUNTS }
    /** Owner lifecycle only, never an assertion that the session is READY. */
    enum class State { ACTIVE, REVOKED, RECOVERY_REQUIRED }
    enum class Outcome { COMMITTED, ROLLED_BACK, UNKNOWN }
    enum class Cleanup { COMPLETE, FAILED }
    class ReadTicket internal constructor()
    class MutationTicket internal constructor()
    private data class Read(val ticket: ReadTicket, val mutation: Long, val load: Long,
        val parts: Set<Projection>)
    private data class Mutation(val ticket: MutationTicket, val epoch: Long,
        val parts: Set<Projection>)

    private val thread = Thread.currentThread()
    var state = State.ACTIVE; private set
    var mutationEpoch = 0L; private set
    var loadEpoch = 0L; private set
    private val clean = mutableSetOf<Projection>()
    private var read: Read? = null
    private var pending: Mutation? = null
    private var committed: Mutation? = null
    private var publishing = false

    private fun owner() { check(Thread.currentThread() === thread) }
    private fun bindingValid(): Boolean {
        if (state != State.ACTIVE) return false
        if (currentBinding() != binding) { terminate(State.REVOKED); return false }
        return state == State.ACTIVE
    }
    private fun context(): StorageContext? {
        owner()
        if (!bindingValid()) return null
        val c = access.acquire(AccessScope.READY)
        if (c == null || c.scope != AccessScope.READY || c.generation != binding.generation ||
            !c.check(AccessPoint.OPERATION) || !bindingValid()) {
            terminate(State.REVOKED)
            return null
        }
        return c
    }
    private fun terminate(next: State) {
        // Terminal identity invalidation does not require an increment (or risk overflow).
        if (state == State.RECOVERY_REQUIRED) return
        state = next
        read = null; pending = null; committed = null; clean.clear()
    }
    fun revoke() { owner(); terminate(State.REVOKED) }
    private fun advance(mutation: Boolean): Boolean = try {
        val nextLoad = Math.incrementExact(loadEpoch)
        val nextMutation = if (mutation) Math.incrementExact(mutationEpoch) else mutationEpoch
        loadEpoch = nextLoad; mutationEpoch = nextMutation
        true
    } catch (_: ArithmeticException) { terminate(State.RECOVERY_REQUIRED); false }

    fun isPending(): Boolean { owner(); return pending != null }
    fun isDirty(part: Projection): Boolean { owner(); return part !in clean }

    /** Advisory check with central admission; actual reference publication still needs its gate. */
    fun canReuse(parts: Set<Projection>): Boolean {
        context() ?: return false
        return parts.isNotEmpty() && pending == null && clean.containsAll(parts)
    }

    /** Call after original syntax/scope admission, BEFORE queueing any write side effect. */
    fun beginMutation(parts: Set<Projection>): MutationTicket? {
        context() ?: return null
        if (publishing || pending != null || parts.isEmpty() || !advance(true)) return null
        read = null; committed = null; clean.removeAll(parts)
        return MutationTicket().also { pending = Mutation(it, mutationEpoch, parts.toSet()) }
    }

    /** Trusted Data outcome only, NOT a commit gate and never callable as a UI save assertion. */
    fun finishMutation(ticket: MutationTicket, outcome: Outcome, cleanup: Cleanup): Boolean {
        owner()
        val write = pending?.takeIf { it.ticket === ticket } ?: return false
        if (publishing) return false
        if (outcome == Outcome.UNKNOWN || cleanup == Cleanup.FAILED) {
            terminate(State.RECOVERY_REQUIRED)
            return true
        }
        context() ?: return false
        if (pending !== write) return false
        pending = null
        committed = write.takeIf { outcome == Outcome.COMMITTED }
        // A definite rollback remains dirty until a fresh authorized read.
        return true
    }

    /** Same demand coalesces; a different concurrent demand is denied, not queued unboundedly. */
    fun beginRead(parts: Set<Projection>): ReadTicket? {
        context() ?: return null
        if (publishing || pending != null || parts.isEmpty()) return null
        read?.let { return it.ticket.takeIf { _ -> it.parts == parts } }
        if (!advance(false)) return null
        committed = null; clean.removeAll(parts)
        return ReadTicket().also { read = Read(it, mutationEpoch, loadEpoch, parts.toSet()) }
    }

    /** Cleanup failure is terminal even when the transaction itself was successful. */
    fun publishRead(ticket: ReadTicket, complete: Set<Projection>, cleanup: Cleanup,
        publication: () -> Unit): Boolean {
        owner()
        val loaded = read?.takeIf { it.ticket === ticket } ?: return false
        if (publishing) return false
        read = null // Every completion attempt consumes the ticket, including rejected attempts.
        if (cleanup == Cleanup.FAILED) { terminate(State.RECOVERY_REQUIRED); return false }
        if (complete != loaded.parts || loaded.mutation != mutationEpoch ||
            loaded.load != loadEpoch || pending != null) return false
        return publish(complete, loaded.mutation, loaded.load, publication)
    }

    fun publishMutation(ticket: MutationTicket, complete: Set<Projection>,
        publication: () -> Unit): Boolean {
        owner()
        val write = committed?.takeIf { it.ticket === ticket } ?: return false
        if (publishing) return false
        committed = null
        if (write.epoch != mutationEpoch || complete != write.parts || pending != null) return false
        return publish(complete, write.epoch, loadEpoch, publication)
    }

    /** Future hit publication still rechecks the central authority; unused by product today. */
    fun publishCurrent(parts: Set<Projection>, publication: () -> Unit): Boolean {
        owner()
        if (publishing || !canReuse(parts)) return false
        return publish(parts, mutationEpoch, loadEpoch, publication)
    }

    private fun publish(parts: Set<Projection>, expectedMutation: Long, expectedLoad: Long,
        publication: () -> Unit): Boolean {
        val frozen = parts.toSet()
        val c = context() ?: return false
        var delivered = false
        val accepted: Boolean
        try {
            accepted = c.publish {
                if (bindingValid() && pending == null && !publishing &&
                    mutationEpoch == expectedMutation && loadEpoch == expectedLoad) {
                    publishing = true
                    try {
                        publication()
                        if (bindingValid()) { clean.addAll(frozen); delivered = true }
                    } finally { publishing = false }
                }
            }
        } catch (error: Throwable) {
            terminate(State.RECOVERY_REQUIRED)
            throw error
        }
        if (!accepted && state == State.ACTIVE) terminate(State.REVOKED)
        return delivered
    }
}
