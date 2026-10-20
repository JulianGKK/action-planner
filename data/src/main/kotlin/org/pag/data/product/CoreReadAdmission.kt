package org.pag.data.product

import java.util.concurrent.atomic.AtomicBoolean
import org.pag.domain.*

/** Transaction-local metadata/closure, not a payload registry or permission token.
 * Only this plan's materializer can run, once, after its aggregate accounting receipt.
 * No live ActiveWorkspaceBudget or Representation crosses any suspend point.
 */
internal class PreparedCoreRead<T>(val reservation: ActiveCoreReservation, private val work: suspend () -> T) {
    private val consumed = AtomicBoolean()
    suspend fun materialize(admission: CoreReadAdmission): T {
        if (!admission.includes(this) || !consumed.compareAndSet(false, true))
            throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
        return work()
    }
}

/** Synchronous, one owner thread from construction through seal/close. Result contains only
 * immutable units/costs and at most two transaction-local plan identities; NO ledger tickets.
 */
internal class CoreReadAdmission private constructor(val reservation: ActiveCoreReservation,
    val retained: Long, val transition: Long, private val parts: List<PreparedCoreRead<*>>) {
    data class Report(val retained: Long, val transition: Long,
        val decision: ActiveWorkspaceBudget.Decision)
    internal fun includes(part: PreparedCoreRead<*>) = parts.any { it === part }

    companion object {
        fun admit(parts: List<PreparedCoreRead<*>>, overhead: ActiveCoreReservation = empty,
            report: (Report) -> Unit = {}): CoreReadAdmission {
            require(parts.size in 1..2 && (parts.size == 1 || parts[0] !== parts[1]))
            // Validate each disjoint input before adding: negative/malformed units must never
            // cancel against another component's positive counts. Cost estimation grants nothing.
            for (part in parts.map { it.reservation } + overhead) {
                cost(part.retained); cost(part.transition)
            }
            val combined = try {
                parts.fold(overhead) { total, part -> ActiveCoreReservation(
                    add(total.retained, part.reservation.retained), add(total.transition, part.reservation.transition)) }
            } catch (_: ArithmeticException) { throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED) }
            val requestedRetained = cost(combined.retained)
            val requestedTransition = cost(combined.transition)
            val budget = ActiveWorkspaceBudget()
            try {
                val retained = budget.beginRetained()!!
                var decision = budget.admit(retained, combined.retained)
                var transitionCharge = 0L
                if (decision == ActiveWorkspaceBudget.Decision.ADMITTED) {
                    check(budget.seal(retained))
                    val transition = budget.beginTransition()!!
                    decision = budget.admit(transition, combined.transition)
                    if (decision == ActiveWorkspaceBudget.Decision.ADMITTED) {
                        check(budget.seal(transition))
                        transitionCharge = budget.accounting(transition)!!.chargedBytes
                    }
                }
                report(Report(requestedRetained, requestedTransition, decision))
                when (decision) {
                    ActiveWorkspaceBudget.Decision.INVALID_DATA -> throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
                    ActiveWorkspaceBudget.Decision.BUDGET_REFUSED -> throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)
                    ActiveWorkspaceBudget.Decision.ADMITTED -> Unit
                }
                return CoreReadAdmission(combined, budget.accounting(retained)!!.chargedBytes, transitionCharge, parts.toList())
            } finally { budget.close() }
        }

        private fun cost(units: ActiveWorkspaceBudget.Units): Long {
            val (decision, bytes) = ActiveWorkspaceBudget.estimateUnits(units)
            when (decision) {
                ActiveWorkspaceBudget.Decision.ADMITTED -> return bytes
                ActiveWorkspaceBudget.Decision.INVALID_DATA -> throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
                ActiveWorkspaceBudget.Decision.BUDGET_REFUSED -> throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)
            }
        }

        private fun add(a: ActiveWorkspaceBudget.Units, b: ActiveWorkspaceBudget.Units) = ActiveWorkspaceBudget.Units(
            objects = Math.addExact(a.objects,b.objects), strings = Math.addExact(a.strings,b.strings),
            utf16Units = Math.addExact(a.utf16Units,b.utf16Units), arrays = Math.addExact(a.arrays,b.arrays),
            arrayPayloadBytes = Math.addExact(a.arrayPayloadBytes,b.arrayPayloadBytes),
            lists = Math.addExact(a.lists,b.lists), listCapacity = Math.addExact(a.listCapacity,b.listCapacity),
            maps = Math.addExact(a.maps,b.maps), mapCapacity = Math.addExact(a.mapCapacity,b.mapCapacity),
            indexes = Math.addExact(a.indexes,b.indexes), indexCapacity = Math.addExact(a.indexCapacity,b.indexCapacity),
            references = Math.addExact(a.references,b.references))
        private val empty = ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units())
    }
}
