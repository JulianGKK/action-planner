package org.pag.prototype.p11fixture

import org.pag.domain.*

/** Shared synthetic ports for the existing P11 Functions probe and opt-in manual target.
 * No storage, credentials, platform authentication or product session policy.
 */
object P11FunctionsFixture {
    class Session(private val syntheticUnlock: Boolean = false) : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1)
        var observer: ((SessionSnapshot) -> Unit)? = null
        var interactions = 0
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription { this.observer = null } }
        override fun authenticate(experiment: Experiment) {
            check(syntheticUnlock) { "No authentication in synthetic probe" }
            value = SessionSnapshot(SessionState.READY, value.generation + 1)
            observer?.invoke(value)
        }
        override fun testAccess() {}
        override fun userInteraction() { interactions++ }
        override fun lock() { value = SessionSnapshot(SessionState.LOCKED, value.generation + 1); observer?.invoke(value) }
    }
    class Actions : ActionRepository {
        val row = Action("synthetic", "Übungsaktion", "Nur synthetische Probe", 1, 2, 0, ActionStatus.ACTIVE)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row)
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {}
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {}
    }
    class People(private val operations: Ops? = null) : PersonRepository {
        // Preserve the automatic probe's historical empty person-list fixture by default.
        private fun rows() = operations?.let { listOf(it.person.person, it.other.person) }.orEmpty()
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) = publish(PersonResult.Value(rows().filter { it.actionId == actionId }))
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(
            if (operations == null) Person(personId, actionId, "Motte", 0)
            else rows().singleOrNull { it.id == personId && it.actionId == actionId }))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    class Ops : OperationsRepository {
        var roles = listOf(Role("plan", "synthetic", "Blockierende", 0), Role("current", "synthetic", "Support", 0))
        var person = OperationalPerson(Person("p", "synthetic", "Motte", 0), PersonRolePlan(setOf("plan"), "plan"),
            PersonOperationalState("synthetic", "p", "current", OperationalStatus.ON_SITE, null, System.currentTimeMillis()))
        var other = OperationalPerson(Person("q", "synthetic", "Klee", 0), PersonRolePlan(emptySet(), null), PersonOperationalState("synthetic", "q", null, OperationalStatus.ON_SITE, null, 0))
        var reads = 0
        var statusCommits = 0
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { reads++; publish(OperationsResult.Value(OperationalBoard(actionId, ActionStatus.ACTIVE, roles, listOf(person, other)))) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {
            check(OperationalRules.validName(name))
            val role = Role("new", actionId, name.trim(), System.currentTimeMillis()); roles = roles + role
            publish(OperationsResult.Value(role))
        }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No planning in ACTIVE") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) {
            val current = when (personId) { "p" -> person; "q" -> other; else -> null }
            if (actionId != "synthetic" || current == null) {
                publish(OperationsResult.Rejected(OperationsFailure.NOT_FOUND)); return
            }
            if (expected != current.state) {
                publish(OperationsResult.Rejected(OperationsFailure.STALE)); return
            }
            val changed = current.copy(state = expected.copy(status = status,
                currentRoleId = roleId.takeIf { OperationalRules.roleAllowed(status) },
                currentPlaceLabel = OperationalRules.place(place, status), updatedAt = System.currentTimeMillis()))
            if (personId == "p") person = changed else other = changed
            statusCommits++
            publish(OperationsResult.Value(Unit))
        }
    }
    class Tasks : TaskRepository {
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) = publish(OperationsResult.Value(TaskBoard(actionId, emptyList(), emptyList())))
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No task operation") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No task operation") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No task operation") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No task operation") }
    }
}
