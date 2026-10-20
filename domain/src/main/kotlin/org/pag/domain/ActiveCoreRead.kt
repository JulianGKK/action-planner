package org.pag.domain

/** Disjoint immutable accounting units for ONE core graph, never storage authority or a
 * retention grant. No live thread-confined ledger/Representation may escape a read.
 */
data class ActiveCoreReservation(val retained: ActiveWorkspaceBudget.Units,
    val transition: ActiveWorkspaceBudget.Units)

/** One action object, complete people/functions/operations and ONE explicit task page.
 * No full TaskBoard, provenance/editor copies or event history. State is the same trusted
 * binding/epoch metadata used by compact paging; it grants no session permission.
 */
data class ActiveCorePayload(val state: CompactTaskState, val action: Action,
    val operations: OperationalBoard, val tasks: CompactTaskPage)

sealed interface ActiveCoreResult {
    data class Value(val core: ActiveCorePayload, val reservation: ActiveCoreReservation,
        val retainedCharge: Long, val transitionCharge: Long) : ActiveCoreResult
    data class Rejected(val reason: BoundedActiveStatus) : ActiveCoreResult {
        init { require(reason != BoundedActiveStatus.ADMITTED) }
    }
}

/** Worker-only first ACTIVE page (0..32 items), with global and affected-person counts.
 * A return does not authorize longer RAM retention. Snapshot ownership and all real writer
 * invalidation hooks remain separate integrations. Existing detail/history readers stay explicit.
 */
interface ActiveCoreReader {
    fun read(state: CompactTaskState, affectedPersonId: String? = null): ActiveCoreResult
}
