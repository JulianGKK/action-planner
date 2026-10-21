package org.pag.domain

/** P3b-free ACTIVE core scope metadata, not an authority or permission revision.
 * Authenticity comes from READY StorageContext, the common ActiveReadTicket and the
 * fresh ACTIVE row check inside the authorized core read transaction.
 */
data class P3bFreeActiveCoreBinding(val actionId: String, val generation: Long) {
    init { require(actionId.isNotBlank() && generation >= 0) }
}

/** Historical spelling retained for bounded standalone proof APIs; there is no permission revision. */
typealias BoundedActiveBinding = P3bFreeActiveCoreBinding

enum class BoundedActiveStatus { ADMITTED, BUDGET_REFUSED, INVALID_DATA, STORAGE_FAILURE, ACCESS_REVOKED }

sealed interface BoundedActiveResult {
    val status: BoundedActiveStatus
    data class Value(val action: Action, val operations: OperationalBoard,
        val retainedCharge: Long, val transitionCharge: Long) : BoundedActiveResult {
        override val status = BoundedActiveStatus.ADMITTED
    }
    data class Rejected(override val status: BoundedActiveStatus) : BoundedActiveResult {
        init { require(status != BoundedActiveStatus.ADMITTED) }
    }
}
