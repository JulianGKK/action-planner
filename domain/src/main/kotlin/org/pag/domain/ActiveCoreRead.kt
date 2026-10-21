package org.pag.domain

/** Disjoint immutable accounting units for ONE core graph, never storage authority or a
 * retention grant. No live thread-confined ledger/Representation may escape a read.
 */
data class ActiveCoreReservation(val retained: ActiveWorkspaceBudget.Units,
    val transition: ActiveWorkspaceBudget.Units)

/** The core owns cursor-free first-page views. Separate Compact*Page/ Cursor values are
 * produced only by explicit follow-up reads and never retained in this core payload.
 */
data class ActiveCorePayload(val state: CompactTaskState, val action: Action,
    val people: ActiveCorePeoplePage, val functions: CompactFunctions, val tasks: ActiveCoreTaskPage)

data class ActiveCorePeoplePage(val state: CompactTaskState, val overall: CompactPeopleCounts,
    val filteredCount: Long, val items: List<CompactPersonCard>, val hasMore: Boolean)
data class ActiveCoreTaskPage(val state: CompactTaskState, val overall: CompactTaskCounts,
    val filtered: CompactTaskCounts, val items: List<CompactTaskItem>, val hasMore: Boolean)

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

/** Original-read lifetime only. No key, replacement ticket, or independent authorization. */
interface ActiveCoreLifetime {
    /** Owner-thread final validation against the original context and common port. */
    fun isCurrent(): Boolean
    /** Short reference installation only; false requires the consumer to drop its candidate. */
    fun publish(install: () -> Unit): Boolean
    /** One consumer; invalidation may run synchronously on a foreign session thread. */
    fun onInvalidated(invalidate: () -> Unit): Subscription
    fun close()
}

interface ActiveCoreLoadPort {
    /** Runtime default is false. A UI switch cannot grant reuse. */
    val reuseEnabled: Boolean get() = false
    fun loadOwned(actionId: String, callback: (ActiveCoreResult, ActiveCoreLifetime?) -> Unit): Boolean
}
