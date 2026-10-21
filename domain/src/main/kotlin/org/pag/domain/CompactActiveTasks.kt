package org.pag.domain

/** P3b-free core scope metadata; no permission revision or session authority. The epoch is
 * the common writer-port epoch from the same ActiveReadTicket, never a second counter.
 */
data class CompactTaskState(val binding: P3bFreeActiveCoreBinding, val mutationEpoch: Long) {
    init { require(mutationEpoch >= 0) }
}

enum class CompactTaskLane { ACTIVE, CLOSED }
/** Affected person, not assignee. Null means all tasks, matching TaskBoard. */
data class CompactTaskQuery(val lane: CompactTaskLane = CompactTaskLane.ACTIVE, val affectedPersonId: String? = null)
data class CompactTaskCounts(val open: Long, val inProgress: Long, val urgent: Long,
    val done: Long, val cancelled: Long) {
    val total get() = open + inProgress + done + cancelled
}

/** Deliberately not Task or TaskBoard: no template provenance, editor snapshot or history. */
data class CompactTaskItem(val id: String, val title: String, val priority: TaskPriority,
    val status: TaskStatus, val assigneePersonId: String?, val assigneeRoleId: String?,
    val affectedPersonId: String?, val dueAt: Long?, val createdAt: Long, val updatedAt: Long)

/** Keyset position, bound to the exact query/state. Not a permission/session token. */
data class CompactTaskCursor(val state: CompactTaskState, val query: CompactTaskQuery,
    val id: String, val priority: TaskPriority, val dueAt: Long?, val createdAt: Long, val updatedAt: Long)
data class CompactEventCursor(val state: CompactTaskState, val taskId: String,
    val occurredAt: Long, val rowId: Long)

sealed interface CompactTaskPayload
data class CompactTaskPage(val state: CompactTaskState, val query: CompactTaskQuery,
    val overall: CompactTaskCounts, val filtered: CompactTaskCounts,
    val items: List<CompactTaskItem>, val next: CompactTaskCursor?) : CompactTaskPayload
/** Complete single task, suitable as the existing optimistic writer's expected value. */
data class CompactTaskDetail(val state: CompactTaskState, val task: Task) : CompactTaskPayload
/** Explicit page, never presented as the complete history. Null next means end of history.
 * Total and all page positions belong to the same state; discard every old page on rejection.
 */
data class CompactTaskEvents(val state: CompactTaskState, val taskId: String, val total: Long,
    val events: List<TaskEvent>, val next: CompactEventCursor?) : CompactTaskPayload

sealed interface CompactTaskResult {
    data class Value(val payload: CompactTaskPayload, val reservation: BoundedTaskReservation,
        val retainedCharge: Long, val transitionCharge: Long) : CompactTaskResult
    data class Rejected(val reason: BoundedActiveStatus) : CompactTaskResult {
        init { require(reason != BoundedActiveStatus.ADMITTED) }
    }
}

/** Standalone worker-only proof path. Every call reacquires current storage authority.
 * At most one page/detail replaces the previous one; accumulating pages is NOT admitted.
 * Reservation units must later join ONE people/functions/tasks ledger before allocation.
 */
interface CompactActiveTaskReader {
    fun page(state: CompactTaskState, query: CompactTaskQuery = CompactTaskQuery(), after: CompactTaskCursor? = null): CompactTaskResult
    fun detail(state: CompactTaskState, taskId: String): CompactTaskResult
    fun events(state: CompactTaskState, taskId: String, after: CompactEventCursor? = null): CompactTaskResult
}
