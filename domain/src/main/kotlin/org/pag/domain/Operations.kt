package org.pag.domain

import java.util.Locale

/** Organisational roles grant no application permissions. All references are action-local. */
data class Role(val id: String, val actionId: String, val name: String, val createdAt: Long, val editRevision: Long)
enum class OperationalStatus {
    NO_STATUS, ON_SITE, IDENTITY_CHECK, IN_CUSTODY, NOT_REACHABLE, RELEASED, LEFT, UNCLEAR
}
data class PersonRolePlan(val roleIds: Set<String>, val primaryRoleId: String?, val editRevision: Long)
/** Immutable editor-open value; missing storage row and stored empty plan are the same domain plan.
 * Full value CAS only: schema 11 supplies no monotone persistent ABA revision.
 */
class ExpectedPersonRolePlan(val actionId: String, val personId: String, plan: PersonRolePlan) {
    val editRevision = plan.editRevision
    val plan = plan.copy(roleIds = java.util.Collections.unmodifiableSet(LinkedHashSet(plan.roleIds)))
}
data class PersonOperationalState(val actionId: String, val personId: String, val currentRoleId: String?,
    val status: OperationalStatus, val currentPlaceLabel: String?, val updatedAt: Long, val editRevision: Long)
data class OperationalPerson(val person: Person, val plan: PersonRolePlan, val state: PersonOperationalState?)
data class OperationalBoard(val actionId: String, val status: ActionStatus, val roles: List<Role>, val persons: List<OperationalPerson>) {
    fun count(status: OperationalStatus) = persons.count { it.state?.status == status }
    fun roleCount(roleId: String?) = persons.count { it.state?.status == OperationalStatus.ON_SITE && it.state.currentRoleId == roleId }
    val presentation=OperationalPresentation(roles,persons)
    val placeSuggestions get()=presentation.places
    fun preferredRoles(personId:String):List<Role> = presentation.preferredRoles(personId)

}
object OperationalRules {
    val templates = listOf("Blockierende", "Support", "Hummel", "Gesa-Support", "Presse", "Aktionsleitung")
    fun normalizedName(name: String) = name.trim().lowercase(Locale.ROOT)
    fun validName(name: String) = name.trim().isNotEmpty()
    fun mayRead(status: ActionStatus) = status in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)
    fun mayPlan(status: ActionStatus) = status == ActionStatus.PREPARATION
    fun mayCreateRole(status: ActionStatus) = mayPlan(status) || status == ActionStatus.ACTIVE
    fun mayUpdateSnapshot(status: ActionStatus) = status == ActionStatus.ACTIVE
    fun placeRelevant(status: OperationalStatus) = status in setOf(OperationalStatus.IDENTITY_CHECK, OperationalStatus.IN_CUSTODY, OperationalStatus.UNCLEAR)
    fun roleAllowed(status: OperationalStatus) = status == OperationalStatus.ON_SITE || status == OperationalStatus.NO_STATUS
    fun validPlace(value: String) = Character.codePointCount(value.trim(), 0, value.trim().length) <= 80
    fun place(value: String, status: OperationalStatus): String? = if (placeRelevant(status)) value.trim().takeIf { it.isNotEmpty() } else null
    fun validPlan(roleIds: Set<String>, primaryRoleId: String?) = primaryRoleId == null || primaryRoleId in roleIds
}
enum class OperationsFailure { NOT_FOUND, NOT_ALLOWED, INVALID, DUPLICATE, IN_USE, STALE, TASK_EVENT_LIMIT_REACHED, REVISION_EXHAUSTED, OWNER_UNCONFIRMED, BUDGET_REFUSED }
sealed interface OperationsResult<out T> {
    data class Value<T>(val value: T): OperationsResult<T>
    data class Rejected(val reason: OperationsFailure): OperationsResult<Nothing>
}
interface OperationsRepository {
    fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit)
    fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit)
    /** Full editor-open identity, compared inside the outer write transaction; no persistent ABA revision. */
    fun renameRole(actionId: String, expected: Role, name: String, publish: (OperationsResult<Role>) -> Unit)
    /** Delete confirmation is bound to this exact previously selected identity. */
    fun deleteRole(actionId: String, expected: Role, publish: (OperationsResult<Unit>) -> Unit)
    fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit)
    fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState,
        status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit)
}
