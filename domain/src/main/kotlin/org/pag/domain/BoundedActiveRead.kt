package org.pag.domain

/** Binding supplied by the trusted future scope adapter, never a permission token. */
data class BoundedActiveBinding(val actionId: String, val generation: Long, val permissionRevision: Long) {
    init { require(actionId.isNotBlank() && generation >= 0 && permissionRevision >= 0) }
}

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
