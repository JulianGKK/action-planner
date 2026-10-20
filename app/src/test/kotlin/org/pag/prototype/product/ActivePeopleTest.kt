package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class ActivePeopleTest {
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
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private class Ops : OperationsRepository {
        val loads = mutableListOf<(OperationsResult<OperationalBoard>) -> Unit>()
        var write: ((OperationsResult<Unit>) -> Unit)? = null
        var writtenState: PersonOperationalState? = null
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { loads.add(publish) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) {}
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
    @Test fun summaryCountsExistingStatusesAndOmitsZeros() {
        val persons = OperationalStatus.entries.mapIndexed { i, status -> live(status).copy(person = person.person.copy(id = "$i")) }
        assertEquals(listOf("8 insgesamt", "1 vor Ort", "1 in Gewahrsam", "1 nicht erreichbar", "1 ohne Lage"), activePeopleSummary(board.copy(persons = persons)))
        assertEquals(listOf("1 insgesamt", "1 vor Ort"), activePeopleSummary(board.copy(persons = listOf(live()))))
    }
    @Test fun emptySummaryIsHonest() { assertEquals(listOf("Noch keine Personen"), activePeopleSummary(board.copy(persons = emptyList()))) }
    @Test fun currentRoleIsNeverReplacedByPlannedRole() {
        val b = board.copy(roles = board.roles + Role("current", "a", "Hummel", 0))
        assertEquals("Hummel", activePersonCard(live(), b, now, zone).function)
        assertEquals("Keine Funktion", activePersonCard(live(role = null), b, now, zone).function)
        assertEquals("Keine Funktion", activePersonCard(live(role = "unknown"), b, now, zone).function)
    }
    @Test fun everyP4StatusAndPlaceIsProjectedWithoutChangingRules() {
        OperationalStatus.entries.forEach { status ->
            val p = live(status, role = "r".takeIf { OperationalRules.roleAllowed(status) }, place = OperationalRules.place(" Demo-Punkt ", status))
            val card = activePersonCard(p, board, now, zone)
            assertEquals(operationalLabel(status) + if (OperationalRules.placeRelevant(status)) " · Demo-Punkt" else "", card.situation)
            if (!OperationalRules.roleAllowed(status)) assertEquals("Keine Funktion", card.function)
        }
    }
    @Test fun underOneMinuteIsJustUpdated() { assertEquals("Gerade aktualisiert", activeUpdatedLabel(live(time = now - 59_999).state, now, zone)) }
    @Test fun exactOneMinute() { assertEquals("Aktualisiert vor 1 Min.", activeUpdatedLabel(live(time = now - 60_000).state, now, zone)) }
    @Test fun fiftyNineMinutes() { assertEquals("Aktualisiert vor 59 Min.", activeUpdatedLabel(live(time = now - 3_599_999).state, now, zone)) }
    @Test fun sixtyMinutesSameDay() { assertEquals("Zuletzt aktualisiert um 13:00", activeUpdatedLabel(live(time = now - 3_600_000).state, now, zone)) }
    @Test fun previousDayUsesDate() { assertEquals("Zuletzt aktualisiert am 12.10. um 14:00", activeUpdatedLabel(live(time = now - 86_400_000).state, now, zone)) }
    @Test fun localCalendarBoundaryPrecedesRelativeAge() {
        val midnight = java.time.ZonedDateTime.of(2026, 10, 13, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val state = live(time = midnight - 1).state
        assertEquals("Zuletzt aktualisiert am 12.10. um 23:59", activeUpdatedLabel(state, midnight, zone))
        assertEquals("Gerade aktualisiert", activeUpdatedLabel(state, midnight, java.time.ZoneId.of("UTC")))
    }
    @Test fun noStatusAndUnknownOrFutureTimestampNeverClaimFreshObservation() {
        assertNull(activeUpdatedLabel(live(OperationalStatus.NO_STATUS).state, now, zone))
        assertNull(activeUpdatedLabel(live(time = 0).state, now, zone))
        assertNull(activeUpdatedLabel(live(time = now + 1).state, now, zone))
        assertNull(activeUpdatedLabel(null, now, zone))
    }
    @Test fun cardFocusUsesCompleteExistingBoardWithoutInventoryOrRead() {
        openPeople(); val reads = ops.loads.size; model.operations.focus("p")
        assertEquals(OperationsScreen.PERSON, model.operations.screen); assertEquals("p", model.operations.personId)
        assertEquals(reads, ops.loads.size); assertEquals(0, protection.inventories)
        model.operations.backToList(); assertEquals(OperationsScreen.OVERVIEW, model.operations.screen); assertNull(model.operations.personId)
        assertEquals(reads, ops.loads.size)
    }
    @Test fun locationSaveReturnsToFocusedPersonAndRefreshesOnce() {
        openPeople(); model.operations.focus("p"); model.operations.update("p")
        model.operations.status(OperationalStatus.IN_CUSTODY); model.operations.place("Demo-Punkt")
        val reads = ops.loads.size; model.operations.saveLive()
        board = board.copy(persons = listOf(person.copy(state = ops.writtenState)))
        ops.write!!(OperationsResult.Value(Unit)); assertEquals(reads + 1, ops.loads.size)
        complete(); assertEquals(OperationsScreen.PERSON, model.operations.screen)
        assertEquals(OperationalStatus.IN_CUSTODY, model.operations.person!!.state!!.status)
        assertNull(model.operations.person!!.state!!.currentRoleId)
        model.operations.backToList(); model.activeArea(ActiveArea.HUB); complete()
        assertEquals("1 Person in Gewahrsam", activeHubCards(actions.row, model.workspace!!.operations!!, taskBoard)[0].attention)
    }
    @Test fun functionEditorWritesCurrentRoleAndPreservesPlan() {
        openPeople(); model.operations.focus("p"); model.operations.changeFunction()
        assertEquals(OperationsScreen.FUNCTION, model.operations.screen)
        model.operations.function("r"); model.operations.saveLive()
        board = board.copy(persons = listOf(board.persons.first().copy(state = ops.writtenState)))
        ops.write!!(OperationsResult.Value(Unit)); complete()
        assertEquals(OperationsScreen.PERSON, model.operations.screen)
        assertEquals("r", model.operations.person!!.state!!.currentRoleId)
        assertEquals(person.plan, model.operations.person!!.plan)
    }
    @Test fun unavailablePersonCannotOpenFunctionEditor() {
        openPeople(); board = board.copy(persons = listOf(live(OperationalStatus.IN_CUSTODY, role = null)))
        model.loadWorkspace(); complete(); model.operations.focus("p"); model.operations.changeFunction()
        assertEquals(OperationsScreen.PERSON, model.operations.screen)
    }
    @Test fun detailUsesExistingPersonInventoryPathAndReturnsWithoutBoardReload() {
        openPeople(); model.operations.focus("p"); val reads = ops.loads.size
        model.personDetail("p"); assertEquals("p", model.selectedPerson!!.id); assertEquals(1, protection.inventories)
        model.backToPersons(); assertNull(model.selectedPerson); assertEquals(OperationsScreen.PERSON, model.operations.screen)
        assertEquals(reads, ops.loads.size); assertEquals(0, protection.opens)
    }
    @Test fun cancelDiscardsDraftAndReturnsToFocus() {
        openPeople(); model.operations.focus("p"); model.operations.update("p"); model.operations.status(OperationalStatus.LEFT)
        model.operations.cancel(); complete()
        assertEquals(OperationsScreen.PERSON, model.operations.screen); assertEquals(OperationalStatus.ON_SITE, model.operations.person!!.state!!.status)
        assertNull(ops.write)
    }
    @Test fun initialPeopleRevealWaitsForBothBoards() {
        open(); complete(); model.activeArea(ActiveArea.PERSONS); assertNull(model.workspace)
        ops.loads.last()(OperationsResult.Value(board)); assertNull(model.workspace)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertNotNull(model.workspace)
    }
    @Test fun refreshRetainsOldSummaryAndListTogether() {
        openPeople(); val old = model.workspace; model.loadWorkspace()
        ops.loads.last()(OperationsResult.Value(board.copy(persons = emptyList()))); assertSame(old, model.workspace)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertTrue(model.workspace!!.persons.isEmpty())
    }
    @Test fun stalePeopleRequestAndGenerationCannotPublish() {
        openPeople(); model.loadWorkspace(); val stale = ops.loads.last(); model.loadWorkspace(); complete(); val current = model.workspace
        stale(OperationsResult.Value(board.copy(persons = emptyList()))); assertSame(current, model.workspace)
        model.operations.focus("p"); model.operations.update("p"); model.operations.saveLive(); val write = ops.write!!
        session.emit(SessionState.READY, 2); write(OperationsResult.Value(Unit))
        assertNull(model.workspace); assertNull(model.operations.personId); assertEquals(OperationsScreen.OVERVIEW, model.operations.screen)
    }
    @Test fun lockDropsFocusDraftAndLateRead() {
        openPeople(); model.operations.focus("p"); model.operations.update("p"); model.operations.place("Demo-Punkt")
        model.loadWorkspace(); val late = ops.loads.last(); session.lock(); late(OperationsResult.Value(board))
        assertNull(model.workspace); assertNull(model.operations.personId); assertEquals("", model.operations.livePlace)
    }
    @Test fun cleanupOrInteractionLockCannotReopenFocus() {
        openPeople(); protection.onRevoke = { session.lock() }; model.operations.focus("p")
        assertNull(model.workspace); assertNull(model.operations.personId)
    }
    @Test fun displayProjectionNeverReadsOrInteractsWithSession() {
        openPeople(); val interactions = session.interactions; val reads = ops.loads.size
        repeat(4) { activePersonCard(board.persons.first(), board, now + it * 60_000, zone) }
        assertEquals(interactions, session.interactions); assertEquals(reads, ops.loads.size)
    }
    @Test fun pendingDetailIsCancelledOnReturnToFocusedPerson() {
        openPeople(); model.operations.focus("p"); protection.deferInventory = true
        model.personDetail("p"); assertTrue(model.personDetailLoading)
        model.backToPersons(); protection.pendingInventory!!(SensitiveResult.Value(SensitiveInventory(SensitiveState.READY)))
        assertNull(model.selectedPerson); assertFalse(model.personDetailLoading); assertEquals(OperationsScreen.PERSON, model.operations.screen)
    }
    @Test fun cleanupLockDuringEditCannotRestoreDraftAfterClear() {
        openPeople(); model.operations.focus("p"); protection.onRevoke = { session.lock() }
        model.operations.update("p"); assertNull(model.workspace); assertNull(model.operations.personId)
        assertEquals(OperationsScreen.OVERVIEW, model.operations.screen); assertEquals("", model.operations.livePlace)
    }
}
