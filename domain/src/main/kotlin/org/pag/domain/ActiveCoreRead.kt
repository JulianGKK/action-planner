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
    /** Explicit child provenance on the original exposed owner; no second admission. */
    fun childAccess(ticket:WorkspaceReadTicket=WorkspaceReadTicket()):WorkspaceAccess? = null
    /** Owner-thread hit authorization using a fresh central READY context and the original
     * read identity. No UI side effects, new read/ticket, or idle renewal. Default deny.
     */
    fun authorizeHit(actionId: String): Boolean = false
    /** Owner-thread final validation against the original context and common port. */
    fun isCurrent(): Boolean
    /** Short reference installation only; false requires the consumer to drop its candidate. */
    fun publish(install: () -> Unit): Boolean
    /** One consumer; invalidation may run synchronously on a foreign session thread. */
    fun onInvalidated(invalidate: () -> Unit): Subscription
    /** Presentation evidence only. Disposal is NOT permission or physical frame retirement. */
    fun compositionObserved() {}
    fun compositionDisposed() {}
    fun close()
}

interface ActiveCoreLoadPort {
    /** Last explicit opening refusal only; no authority or admission receipt. */
    val openingFailure:WorkspaceFailure? get()=null
    /** Runtime default is false. A UI switch cannot grant reuse. */
    val reuseEnabled: Boolean get() = false
    /** Cancels only the pending handoff, never frees a published Compose graph by assumption. */
    fun cancelPending() {}
    /** Unknown full-projection costs fence later compact reads; full functionality stays separate. */
    fun fullViewStarting() {}
    /** Explicit user opening. May mint a fresh booked child, never retry a refused root. */
    fun openOwned(actionId:String,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit):Boolean=loadOwned(actionId,callback)
    fun loadOwned(actionId: String, callback: (ActiveCoreResult, ActiveCoreLifetime?) -> Unit): Boolean
}
