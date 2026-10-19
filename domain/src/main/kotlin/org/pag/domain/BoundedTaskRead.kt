package org.pag.domain

/** Disjoint accounting units, NOT permission or a second payload budget.
 * Future combined admission must add these to people/operations units in ONE scope ledger
 * BEFORE either graph is materialized. Standalone admission cannot authorize coexistence.
 */
data class BoundedTaskReservation(val retained: ActiveWorkspaceBudget.Units,
    val transition: ActiveWorkspaceBudget.Units)

sealed interface BoundedTaskResult {
    val status: BoundedActiveStatus
    data class Value(val board: TaskBoard, val reservation: BoundedTaskReservation,
        val retainedCharge: Long, val transitionCharge: Long) : BoundedTaskResult {
        override val status = BoundedActiveStatus.ADMITTED
    }
    data class Rejected(override val status: BoundedActiveStatus) : BoundedTaskResult {
        init { require(status != BoundedActiveStatus.ADMITTED) }
    }
}
