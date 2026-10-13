package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class ActiveFunctionsTest {
    private class Session : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1)
        var observer: ((SessionSnapshot) -> Unit)? = null
        var onInteraction: () -> Unit = {}
        var interactions = 0
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription {} }
        fun emit(state: SessionState, generation: Long) { value = SessionSnapshot(state, generation); observer!!(value) }
        override fun authenticate(experiment: Experiment) {}
        override fun testAccess() {}
        override fun userInteraction() { interactions++; onInteraction() }
        override fun lock() = emit(SessionState.LOCKED, value.generation + 1)
    }
    private class Actions : ActionRepository {
        var row = Action("a", "synthetic action", "synthetic description", 1, 2, 0, ActionStatus.ACTIVE)
        var transitions = 0
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row.copy(id = id))
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {}
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            transitions++; row = row.copy(status = ActionLifecycle.next(expected)!!); publish(ActionChangeResult.Applied(row))
        }
    }
    private class People : PersonRepository {
        var lists = 0
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { lists++ }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(Person(personId, actionId, "synthetic", 0)))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private class Ops : OperationsRepository {
        val loads = mutableListOf<(OperationsResult<OperationalBoard>) -> Unit>()
        var write: ((OperationsResult<Unit>) -> Unit)? = null
        var creates = 0; var create: ((OperationsResult<Role>) -> Unit)? = null; var renamed = 0; var deleted = 0
        var writtenState: PersonOperationalState? = null
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { loads.add(publish) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { creates++; create = publish }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { renamed++ }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { deleted++ }
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) {
            writtenState = expected.copy(status = status, currentRoleId = roleId.takeIf { OperationalRules.roleAllowed(status) }, currentPlaceLabel = OperationalRules.place(place, status), updatedAt = 5)
            write = publish
        }
    }
    private class Tasks : TaskRepository {
        val loads = mutableListOf<(OperationsResult<TaskBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) { loads.add(publish) }
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {}
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {}
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) {}
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) {}
    }
    private class Protection : LeadershipRepository {
        val states = mutableListOf<Pair<String, (SensitiveResult<SensitiveState>) -> Unit>>()
        var deferInventory = false; var pendingInventory: ((SensitiveResult<SensitiveInventory>) -> Unit)? = null
        var inventories = 0; var setups = 0; var opens = 0; var revokes = 0
        var setupResult: ((SensitiveResult<Unit>) -> Unit)? = null
        var onRevoke: (() -> Unit)? = null
        override fun actionState(actionId: String, publish: (SensitiveResult<SensitiveState>) -> Unit) { states.add(actionId to publish) }
        override fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit) { inventories++; pendingInventory = publish; if (!deferInventory) publish(SensitiveResult.Value(SensitiveInventory(SensitiveState.READY))) }
        override fun setup(actionId: String, passphrase: CharArray, confirmation: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { setups++; passphrase.fill('\u0000'); confirmation.fill('\u0000'); setupResult = publish }
        override fun open(actionId: String, personId: String, area: SensitiveArea, passphrase: CharArray, publish: (SensitiveResult<SensitiveOpened>) -> Unit) { opens++; passphrase.fill('\u0000') }
        override fun save(grant: SensitiveGrant, data: SensitiveData, publish: (SensitiveResult<Unit>) -> Unit) {}
        override fun deleteArea(grant: SensitiveGrant, publish: (SensitiveResult<Unit>) -> Unit) {}
        override fun deletePerson(actionId: String, personId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) {}
        override fun deleteAction(actionId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) {}
        override fun revoke() { revokes++; val callback = onRevoke; onRevoke = null; callback?.invoke() }
    }
    private val session = Session(); private val actions = Actions(); private val people = People()
    private val ops = Ops(); private val tasks = Tasks(); private val protection = Protection()
    private val model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, people, protection, ops, tasks) {}
    private val person = OperationalPerson(Person("p", "a", "synthetic", 0), PersonRolePlan(setOf("r"), "r"), null)
    private var board = OperationalBoard("a", ActionStatus.ACTIVE, listOf(Role("r", "a", "Support", 0)), listOf(person))
    private val task = Task("t", "a", "synthetic task", TaskPriority.HIGH, TaskStatus.OPEN, null, "r", null, null, null, 0, 0, null)
    private val taskBoard = TaskBoard("a", listOf(task), emptyList())
    private fun open() = model.detail("a")
    private fun complete() {
        ops.loads.last()(OperationsResult.Value(board.copy(status = actions.row.status)))
        if (model.activeArea != ActiveArea.FUNCTIONS) tasks.loads.last()(OperationsResult.Value(taskBoard))
    }
    private val zone = java.time.ZoneId.of("Europe/Berlin")
    private val now = java.time.ZonedDateTime.of(2026, 10, 13, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun live(status: OperationalStatus = OperationalStatus.ON_SITE, time: Long = now, role: String? = "current", place: String? = null) =
        person.copy(state = PersonOperationalState("a", "p", role, status, place, time))
    private fun openPeople() {
        board = board.copy(roles = board.roles + Role("current", "a", "Hummel", 0), persons = listOf(live()))
        open(); complete(); model.activeArea(ActiveArea.PERSONS); complete()
    }
    private fun openFunctions() { openPeople(); model.activeArea(ActiveArea.FUNCTIONS); complete() }
    private fun content() = activeFunctionContent(model.workspace!!.operations!!)
    private fun applyLive() {
        board = board.copy(persons = board.persons.map { if (it.person.id == "p") it.copy(state = ops.writtenState) else it })
        ops.write!!(OperationsResult.Value(Unit)); complete()
    }
    @Test fun nobodyOnSiteIsNeutralAndShowsOnlyOtherRoles() {
        val view = activeFunctionContent(board)
        assertEquals(listOf("Niemand aktuell vor Ort"), view.summary); assertTrue(view.groups.isEmpty()); assertTrue(view.without.isEmpty()); assertEquals(board.roles, view.other)
    }
    @Test fun summaryUsesOnlyCurrentOnSiteDistribution() {
        openFunctions()
        val rows = listOf(live(role = "r"), live(role = "r").copy(person = person.person.copy(id = "q")), live(role = "current").copy(person = person.person.copy(id = "f")), live(role = null).copy(person = person.person.copy(id = "n")))
        val view = activeFunctionContent(board.copy(persons = rows))
        assertEquals(listOf("4 Personen vor Ort", "3 mit Funktion", "1 ohne Funktion", "2 aktuell genutzte Funktionen"), view.summary)
        assertEquals(listOf("r", "current"), view.groups.map { it.role.id }); assertEquals(listOf(2, 1), view.groups.map { it.people.size })
        assertEquals(listOf("n"), view.without.map { it.person.id })
    }
    @Test fun noStatusWithInitialRoleIsNotCurrentDistribution() {
        val view = activeFunctionContent(board.copy(persons = listOf(live(OperationalStatus.NO_STATUS, role = "r"))))
        assertTrue(view.groups.isEmpty()); assertTrue(view.without.isEmpty()); assertEquals(1, view.other.size)
    }
    @Test fun everyNonOnSiteStatusIsExcludedEvenWithSyntheticRole() {
        OperationalStatus.entries.filter { it != OperationalStatus.ON_SITE }.forEach { status ->
            val view = activeFunctionContent(board.copy(persons = listOf(live(status, role = "r"))))
            assertEquals(listOf("Niemand aktuell vor Ort"), view.summary); assertTrue(view.groups.isEmpty()); assertTrue(view.without.isEmpty())
        }
    }
    @Test fun plannedRolesNeverBecomeCurrentGroups() {
        openFunctions(); assertEquals(listOf("current"), content().groups.map { it.role.id }); assertEquals(listOf("r"), content().other.map { it.id })
    }
    @Test fun onlyWithoutFunctionHasItsOwnGroup() {
        val view = activeFunctionContent(board.copy(persons = listOf(live(role = null))))
        assertEquals(1, view.without.size); assertTrue(view.groups.isEmpty()); assertEquals(listOf("1 Person vor Ort", "0 mit Funktion", "1 ohne Funktion", "0 aktuell genutzte Funktionen"), view.summary)
    }
    @Test fun roleAndPersonOrderRemainRepositoryOrder() {
        openFunctions(); val people = listOf(live(role = "r").copy(person = person.person.copy(id = "z")), live(role = "current"), live(role = "r").copy(person = person.person.copy(id = "a")))
        val view = activeFunctionContent(board.copy(roles = board.roles.reversed(), persons = people))
        assertEquals(listOf("current", "r"), view.groups.map { it.role.id }); assertEquals(listOf("z", "a"), view.groups.last().people.map { it.person.id })
    }
    @Test fun projectionAgreesWithExistingP4RoleCounts() {
        openFunctions(); val b = board.copy(persons = listOf(live(role = "r"), live(role = null).copy(person = person.person.copy(id = "n")), live(OperationalStatus.NO_STATUS, role = "r").copy(person = person.person.copy(id = "u"))))
        activeFunctionContent(b).groups.forEach { assertEquals(b.roleCount(it.role.id), it.people.size) }
        assertEquals(b.roleCount(null), activeFunctionContent(b).without.size)
    }
    @Test fun unusedRolesAreSecondaryWithoutWarnings() { openFunctions(); assertEquals(listOf("Support"), content().other.map { it.name }); assertFalse(model.operations.moreFunctionsExpanded) }
    @Test fun groupFocusDoesNotReadTasksInventoryOrBoards() {
        openFunctions(); val reads = ops.loads.size; val taskReads = tasks.loads.size
        model.operations.focusFunction("current"); assertEquals(FunctionFocus("current"), model.operations.functionFocus); assertEquals(OperationsScreen.FUNCTION_DETAIL, model.operations.screen)
        assertEquals(reads, ops.loads.size); assertEquals(taskReads, tasks.loads.size); assertEquals(0, protection.inventories)
    }
    @Test fun withoutFunctionFocusIsDistinctFromNoFocus() {
        openFunctions(); model.operations.focusFunction(null); assertEquals(FunctionFocus(null), model.operations.functionFocus)
        assertEquals("Ohne Funktion", activeFunctionName(board, null)); model.operations.backToFunctions(); assertNull(model.operations.functionFocus)
    }
    @Test fun groupPersonOpensExistingUx3bAndReturnsToSameGroupWithoutRead() {
        openFunctions(); model.operations.focusFunction("current"); val reads = ops.loads.size
        model.operations.focus("p"); assertEquals(OperationsScreen.PERSON, model.operations.screen)
        assertEquals("p", model.operations.personId); model.operations.backToList(); assertEquals(OperationsScreen.FUNCTION_DETAIL, model.operations.screen)
        assertEquals(FunctionFocus("current"), model.operations.functionFocus); assertEquals(reads, ops.loads.size)
    }
    @Test fun changeAToBUsesExistingSnapshotWriteAndKeepsEmptyAContext() {
        openFunctions(); model.operations.focusFunction("current"); model.operations.focus("p"); model.operations.changeFunction(); model.operations.function("r"); model.operations.saveLive(); applyLive()
        assertEquals("r", model.operations.person!!.state!!.currentRoleId); assertEquals(listOf("r"), content().groups.map { it.role.id })
        model.operations.backToList(); assertEquals(FunctionFocus("current"), model.operations.functionFocus); assertTrue(activeFunctionPeople(board, "current").isEmpty())
        assertEquals(OperationsScreen.FUNCTION_DETAIL, model.operations.screen)
        assertEquals(listOf("1 Support"), activeHubCards(actions.row, board, taskBoard)[2].summary)
    }
    @Test fun removingFunctionMovesPersonToWithoutGroup() {
        openFunctions(); model.operations.focusFunction("current"); model.operations.focus("p"); model.operations.changeFunction(); model.operations.function(null); model.operations.saveLive(); applyLive()
        assertTrue(content().groups.isEmpty()); assertEquals(listOf("p"), content().without.map { it.person.id }); assertEquals("Keine Funktion", activePersonCard(board.persons.first(), board, now, zone).function)
    }
    @Test fun statusChangeRemovesPersonFromDistributionWithoutCountingAsUnassigned() {
        openFunctions(); model.operations.focusFunction("current"); model.operations.focus("p"); model.operations.update("p"); model.operations.status(OperationalStatus.IN_CUSTODY); model.operations.saveLive(); applyLive()
        assertTrue(content().groups.isEmpty()); assertTrue(content().without.isEmpty()); assertNull(model.operations.person!!.state!!.currentRoleId)
        assertEquals(listOf("Niemand aktuell vor Ort"), content().summary)
    }
    @Test fun newActiveFunctionReturnsToOverviewWithoutAssignmentAndHasFeedback() {
        openFunctions(); model.operations.newFunction(); model.operations.changeNewFunctionName("Demo-Funktion"); model.operations.saveNewFunction()
        val role = Role("new", "a", "Demo-Funktion", 5); board = board.copy(roles = board.roles + role)
        ops.create!!(OperationsResult.Value(role)); complete()
        assertEquals(OperationsScreen.OVERVIEW, model.operations.screen); assertEquals("", model.operations.newFunctionName)
        assertEquals("Funktion „Demo-Funktion“ angelegt", model.feedback!!.text); assertTrue(content().other.contains(role))
        assertEquals("current", board.persons.first().state!!.currentRoleId); assertEquals(1, ops.creates)
    }
    @Test fun activeRenameDeleteAndPlanningAreDenied() {
        openFunctions(); model.operations.renameRole("r", "Changed"); model.operations.deleteRole("r"); model.operations.plan("p")
        assertEquals(0, ops.renamed); assertEquals(0, ops.deleted); assertEquals(OperationsScreen.OVERVIEW, model.operations.screen)
    }
    @Test fun failedCreationKeepsDraftAndDoesNotClaimSuccess() {
        openFunctions(); model.operations.newFunction(); model.operations.changeNewFunctionName("Support"); model.operations.saveNewFunction()
        ops.create!!(OperationsResult.Rejected(OperationsFailure.DUPLICATE)); assertEquals(OperationsScreen.NEW_FUNCTION, model.operations.screen)
        assertEquals("Support", model.operations.newFunctionName); assertEquals(OperationsFailure.DUPLICATE, model.operations.error); assertNull(model.feedback)
    }
    @Test fun cancelNewFunctionDiscardsDraftWithoutWriteRead() {
        openFunctions(); model.operations.newFunction(); model.operations.changeNewFunctionName("Discard"); val reads = ops.loads.size
        model.operations.backToFunctions(); assertEquals("", model.operations.newFunctionName); assertEquals(0, ops.creates); assertEquals(reads, ops.loads.size)
    }
    @Test fun initialFunctionsAppearOnlyWithCompleteOperationalBoardAndNoTasks() {
        open(); complete(); val taskReads = tasks.loads.size; model.activeArea(ActiveArea.FUNCTIONS)
        assertNull(model.workspace); ops.loads.last()(OperationsResult.Value(board)); assertNotNull(model.workspace)
        assertEquals(taskReads, tasks.loads.size); assertNull(model.tasks.board)
    }
    @Test fun refreshRetainsWholeOldDistribution() {
        openFunctions(); val old = model.workspace; model.loadWorkspace(); assertSame(old, model.workspace)
        ops.loads.last()(OperationsResult.Value(board.copy(persons = emptyList()))); assertNotSame(old, model.workspace); assertEquals(listOf("Niemand aktuell vor Ort"), content().summary)
    }
    @Test fun oldRequestGenerationAndActionCannotRestoreFunctions() {
        openFunctions(); model.loadWorkspace(); val stale = ops.loads.last(); model.loadWorkspace(); complete(); val current = model.workspace
        stale(OperationsResult.Value(board.copy(persons = emptyList()))); assertSame(current, model.workspace)
        model.operations.focusFunction("current"); session.emit(SessionState.READY, 2); stale(OperationsResult.Value(board))
        assertNull(model.workspace); assertNull(model.operations.functionFocus)
        model.detail("b"); val late = ops.loads.last(); model.detail("a"); late(OperationsResult.Value(board.copy(actionId = "b"))); assertNull(model.workspace)
    }
    @Test fun lockClearsGroupPersonDisclosureAndDraft() {
        openFunctions(); model.operations.toggleMoreFunctions(); model.operations.focusFunction("current"); model.operations.focus("p"); session.lock()
        assertNull(model.workspace); assertNull(model.operations.functionFocus); assertNull(model.operations.personId); assertFalse(model.operations.moreFunctionsExpanded)
        session.emit(SessionState.READY, 3); openFunctions(); model.operations.newFunction(); model.operations.changeNewFunctionName("synthetic draft"); session.lock()
        assertEquals("", model.operations.newFunctionName); assertEquals(OperationsScreen.OVERVIEW, model.operations.screen)
    }
    @Test fun lateCreationAfterLockCannotPublishFeedbackOrContext() {
        openFunctions(); model.operations.newFunction(); model.operations.changeNewFunctionName("synthetic"); model.operations.saveNewFunction(); val late = ops.create!!
        session.lock(); late(OperationsResult.Value(Role("new", "a", "synthetic", 5))); assertNull(model.workspace); assertNull(model.feedback); assertEquals("", model.operations.newFunctionName)
    }
    @Test fun completedCannotCreateOrChangeFunctionsAndStatus() {
        actions.row = actions.row.copy(status = ActionStatus.COMPLETED); open(); complete()
        model.operations.newFunction(); model.operations.focusFunction("r"); model.operations.focus("p"); model.operations.update("p"); model.operations.saveNewFunction()
        assertEquals(OperationsScreen.OVERVIEW, model.operations.screen); assertEquals(0, ops.creates); assertNull(ops.write)
    }
    @Test fun synchronousRevokeCannotRestoreFunctionNavigationOrDisclosure() {
        openFunctions(); protection.onRevoke = { session.lock() }; model.operations.focusFunction("current")
        assertNull(model.workspace); assertNull(model.operations.functionFocus); assertFalse(model.operations.moreFunctionsExpanded)
    }
    @Test fun personDetailsReturnToPersonThenOriginalFunctionWithoutInventoryInGroup() {
        openFunctions(); model.operations.focusFunction("current"); model.operations.focus("p"); model.personDetail("p")
        assertEquals(1, protection.inventories); model.backToPersons(); model.operations.backToList(); assertEquals(FunctionFocus("current"), model.operations.functionFocus)
    }
}
