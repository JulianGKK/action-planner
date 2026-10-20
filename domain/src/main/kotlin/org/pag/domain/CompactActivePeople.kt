package org.pag.domain

/** Counts describe the ENTIRE authorized action, never just the displayed page. */
data class CompactPeopleCounts(val noStatus: Long, val onSite: Long, val identityCheck: Long,
    val inCustody: Long, val notReachable: Long, val released: Long, val left: Long, val unclear: Long) {
    val total get() = noStatus + onSite + identityCheck + inCustody + notReachable + released + left + unclear
    fun count(status: OperationalStatus) = when(status) {
        OperationalStatus.NO_STATUS -> noStatus; OperationalStatus.ON_SITE -> onSite
        OperationalStatus.IDENTITY_CHECK -> identityCheck; OperationalStatus.IN_CUSTODY -> inCustody
        OperationalStatus.NOT_REACHABLE -> notReachable; OperationalStatus.RELEASED -> released
        OperationalStatus.LEFT -> left; OperationalStatus.UNCLEAR -> unclear
    }
}

/** A function query ALWAYS means current ON_SITE membership, including null = Ohne Funktion.
 * This is independent of planned roles, task assignees and task affected-person filters.
 */
sealed interface CompactPeopleQuery {
    data object All : CompactPeopleQuery
    data class Function(val roleId: String?) : CompactPeopleQuery
}
data class CompactPersonCard(val id: String, val pseudonym: String, val createdAt: Long,
    val status: OperationalStatus, val currentRoleId: String?, val currentRoleName: String?,
    val currentPlaceLabel: String?, val updatedAt: Long, val affectedActiveTasks: Long)
data class CompactPeopleCursor(val state: CompactTaskState, val query: CompactPeopleQuery,
    val createdAt: Long, val id: String)
sealed interface CompactPeoplePayload { val state: CompactTaskState }
/** At most 32 cards. A non-null next requires another authorized read; even a full final
 * page may have an empty successor. Never concatenate pages across different state metadata.
 */
data class CompactPeoplePage(override val state: CompactTaskState, val query: CompactPeopleQuery,
    val overall: CompactPeopleCounts, val filteredCount: Long, val items: List<CompactPersonCard>,
    val next: CompactPeopleCursor?) : CompactPeoplePayload
/** Complete role identities, including unused roles; NO member/pseudonym lists. Admission
 * can refuse oversized role catalogs. All counts are exact ON_SITE counts across the action.
 */
data class CompactFunction(val role: Role, val onSiteCount: Long)
data class CompactFunctions(val items: List<CompactFunction>, val withoutFunction: Long,
    val onSiteCount: Long, val usedFunctions: Long)
/** Complete current person + plan + operational state. No unrelated person's editor graph.
 * Editor role choices use the complete bounded function catalog, not a fabricated board.
 * Oversized details fail explicitly; they are never silently truncated.
 */
data class CompactPersonDetail(override val state: CompactTaskState, val person: OperationalPerson) : CompactPeoplePayload
sealed interface CompactPeopleResult {
    data class Value(val payload: CompactPeoplePayload, val reservation: ActiveCoreReservation,
        val retainedCharge: Long, val transitionCharge: Long) : CompactPeopleResult
    data class Rejected(val reason: BoundedActiveStatus) : CompactPeopleResult
}
/** Explicit worker reads only. Replaces the previous page; does not authorize co-retention,
 * a cache or editor lifetime. Each request rechecks READY and the trusted state/epoch.
 */
interface CompactActivePeopleReader {
    fun page(state: CompactTaskState, query: CompactPeopleQuery = CompactPeopleQuery.All,
        after: CompactPeopleCursor? = null): CompactPeopleResult
    fun detail(state: CompactTaskState, personId: String): CompactPeopleResult
}
