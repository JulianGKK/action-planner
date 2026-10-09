package org.pag.domain

enum class ActionStatus { DRAFT, PREPARATION, ACTIVE, COMPLETED, DELETION_PENDING }
data class Action(val id: String, val title: String, val description: String,
    val startAt: Long, val expectedEndAt: Long, val createdAt: Long,
    val status: ActionStatus = ActionStatus.DRAFT)
data class NewAction(val title: String, val description: String, val startAt: Long, val expectedEndAt: Long) {
    fun valid() = title.isNotBlank() && description.isNotBlank() && expectedEndAt >= startAt
}
interface ActionRepository {
    fun listActions(publish: (List<Action>) -> Unit)
    fun getAction(id: String, publish: (Action?) -> Unit)
    fun createAction(action: NewAction, publish: (Action) -> Unit)
}
enum class ProductStorageState { EMPTY, READY_TO_OPEN, PARTIAL_OR_INVALID }
object ProductSessionTiming {
    val policy: SessionPolicy get() = SessionPolicy(180_000L, 30_000L)
}
