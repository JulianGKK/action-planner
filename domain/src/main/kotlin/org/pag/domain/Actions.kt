package org.pag.domain

enum class ActionStatus { DRAFT, PREPARATION, ACTIVE, COMPLETED, DELETION_PENDING }
data class Action(val id: String, val title: String, val description: String,
    val startAt: Long, val expectedEndAt: Long, val createdAt: Long,
    val status: ActionStatus = ActionStatus.DRAFT, val completedAt: Long? = null)
data class NewAction(val title: String, val description: String, val startAt: Long, val expectedEndAt: Long) {
    fun valid() = title.isNotBlank() && description.isNotBlank() && expectedEndAt >= startAt
}
interface ActionRepository {
    fun listActions(publish: (List<Action>) -> Unit)
    fun getAction(id: String, publish: (Action?) -> Unit)
    fun createAction(action: NewAction, publish: (Action) -> Unit)
    fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit)
    fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit)
}
enum class ProductStorageState { EMPTY, READY_TO_OPEN, PARTIAL_OR_INVALID }
object ProductSessionTiming {
    val policy: SessionPolicy get() = SessionPolicy(180_000L, 30_000L)
}

/** Conservative monotone P2 minimum. Deletion is a separate future workflow. */
object ActionLifecycle {
    fun next(status: ActionStatus): ActionStatus? = when (status) {
        ActionStatus.DRAFT -> ActionStatus.PREPARATION
        ActionStatus.PREPARATION -> ActionStatus.ACTIVE
        ActionStatus.ACTIVE -> ActionStatus.COMPLETED
        ActionStatus.COMPLETED, ActionStatus.DELETION_PENDING -> null
    }
    fun mayAdvance(from: ActionStatus, to: ActionStatus) = next(from) == to
    fun mayEdit(status: ActionStatus) = status == ActionStatus.DRAFT || status == ActionStatus.PREPARATION
    fun terminal(status: ActionStatus) = status == ActionStatus.COMPLETED || status == ActionStatus.DELETION_PENDING
}
enum class ActionChangeRejection { NOT_FOUND, STALE, NOT_EDITABLE, INVALID_TRANSITION, INVALID_FIELDS }
sealed interface ActionChangeResult {
    data class Applied(val action: Action) : ActionChangeResult
    data class Rejected(val reason: ActionChangeRejection) : ActionChangeResult
}
