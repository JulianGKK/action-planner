package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** One mutable synthetic world behind all ports; tests cross actual VM publication/navigation. */
class ActiveIntegrationTest {
    private class Session : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1)
        var observer: ((SessionSnapshot) -> Unit)? = null
        var interactions = 0
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription {} }
        fun emit(state: SessionState, generation: Long) { value = SessionSnapshot(state, generation); observer!!(value) }
        override fun authenticate(experiment: Experiment) { error("No auth in synthetic integration") }
        override fun testAccess() {}
        override fun userInteraction() { interactions++ }
        override fun lock() = emit(SessionState.LOCKED, value.generation + 1)
    }
    private class World(status: ActionStatus = ActionStatus.ACTIVE) {
        var action = Action("a", "Übungsaktion", "Nur synthetisch", 1, 2, 0, status)
        val roles = listOf(Role("support", "a", "Support", 0), Role("hummel", "a", "Hummel", 0))
        var people = listOf(
            OperationalPerson(Person("p", "a", "Motte", 0), PersonRolePlan(setOf("support"), "support"), PersonOperationalState("a", "p", "support", OperationalStatus.ON_SITE, null, 1)),
            OperationalPerson(Person("q", "a", "Meise", 0), PersonRolePlan(setOf("hummel"), "hummel"), PersonOperationalState("a", "q", "hummel", OperationalStatus.NO_STATUS, null, 1)),
            OperationalPerson(Person("k", "a", "Klee", 0), PersonRolePlan(emptySet(), null), PersonOperationalState("a", "k", null, OperationalStatus.ON_SITE, null, 1)))
        var rows = listOf(
            Task("t", "a", "Übungsaufgabe", TaskPriority.HIGH, TaskStatus.OPEN, null, "support", "p", null, null, 1, 1, null),
            Task("assigned", "a", "Zuständigkeitsbeispiel", TaskPriority.NORMAL, TaskStatus.OPEN, "p", null, "k", null, null, 2, 2, null))
        var events = listOf(TaskEvent("e", "a", "t", TaskEventType.CREATED, 1))
        var defer = false; var opReads = 0; var taskReads = 0; var opWrites = 0; var taskWrites = 0
        val pendingOps = mutableListOf<(OperationsResult<OperationalBoard>) -> Unit>()
        val pendingTasks = mutableListOf<(OperationsResult<TaskBoard>) -> Unit>()
        fun operations() = OperationalBoard(action.id, action.status, roles, people)
        fun tasks() = TaskBoard(action.id, rows, events)
        fun complete() { pendingOps.removeAt(0)(OperationsResult.Value(operations())); pendingTasks.removeAt(0)(OperationsResult.Value(tasks())) }
        val actions = object : ActionRepository {
            override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(action))
            override fun getAction(id: String, publish: (Action?) -> Unit) = publish(action.copy(id = id))
            override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
            override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {}
            override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {}
        }
        val persons = object : PersonRepository {
            override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) = publish(PersonResult.Value(people.map { it.person }))
            override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(people.find { it.person.id == personId }?.person))
            override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
            override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        }
        val ops = object : OperationsRepository {
            override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { opReads++; if (defer) pendingOps.add(publish) else publish(OperationsResult.Value(operations())) }
            override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { opWrites++ }
            override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { opWrites++ }
            override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { opWrites++ }
            override fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { opWrites++ }
            override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) {
                assertEquals(expected, people.single { it.person.id == personId }.state); opWrites++
                people = people.map { if (it.person.id == personId) it.copy(state = expected.copy(status = status, currentRoleId = roleId.takeIf { OperationalRules.roleAllowed(status) }, currentPlaceLabel = OperationalRules.place(place, status), updatedAt = 5)) else it }
                publish(OperationsResult.Value(Unit))
            }
        }
        val taskPort = object : TaskRepository {
            override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) { taskReads++; if (defer) pendingTasks.add(publish) else publish(OperationsResult.Value(tasks())) }
            override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {
                taskWrites++; val t = Task("new", actionId, input.title, input.priority, TaskStatus.OPEN, input.assigneePersonId, input.assigneeRoleId, input.affectedPersonId, input.dueAt, input.sourceTemplateKey, 5, 5, null)
                rows = rows + t; events = events + TaskEvent("new-e", actionId, t.id, TaskEventType.CREATED, 5); publish(OperationsResult.Value(t))
            }
            override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {
                taskWrites++; val t = expected.copy(title = input.title, priority = input.priority, assigneePersonId = input.assigneePersonId, assigneeRoleId = input.assigneeRoleId, affectedPersonId = input.affectedPersonId, dueAt = input.dueAt, updatedAt = 5)
                rows = rows.map { if (it.id == t.id) t else it }; publish(OperationsResult.Value(t))
            }
            override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) {
                taskWrites++; val t = TaskRules.transition(expected, status, 5)!!
                rows = rows.map { if (it.id == t.id) t else it }; events = events + TaskEvent("e-$taskWrites", actionId, t.id, TaskRules.event(expected.status, status)!!, 5); publish(OperationsResult.Value(t))
            }
            override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { taskWrites++ }
        }
    }
    private class Protection : LeadershipRepository {
        var inventories = 0; var opens = 0
        override fun actionState(actionId: String, publish: (SensitiveResult<SensitiveState>) -> Unit) { error("No metadata for ACTIVE") }
        override fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit) { inventories++; publish(SensitiveResult.Value(SensitiveInventory(SensitiveState.READY, setOf(SensitiveArea.IDENTITY)))) }
        override fun setup(actionId: String, passphrase: CharArray, confirmation: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No setup") }
        override fun open(actionId: String, personId: String, area: SensitiveArea, passphrase: CharArray, publish: (SensitiveResult<SensitiveOpened>) -> Unit) { opens++; error("No payload for summaries") }
        override fun save(grant: SensitiveGrant, data: SensitiveData, publish: (SensitiveResult<Unit>) -> Unit) { error("No payload write") }
        override fun deleteArea(grant: SensitiveGrant, publish: (SensitiveResult<Unit>) -> Unit) { error("No delete") }
        override fun deletePerson(actionId: String, personId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No delete") }
        override fun deleteAction(actionId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No delete") }
        override fun revoke() {}
    }
    private class Harness(status: ActionStatus = ActionStatus.ACTIVE, protection: LeadershipRepository? = null) {
        val session = Session(); val world = World(status)
        val model = ProductViewModel(session, world.actions, ProductStorageState.READY_TO_OPEN, world.persons, leadership = protection, operationsRepository = world.ops, taskRepository = world.taskPort) {}
        init { model.detail("a") }
        fun area(area: ActiveArea) = model.activeArea(area)
        fun person() { area(ActiveArea.PERSONS); model.operations.focus("p") }
        fun cards() = activeHubCards(model.selected!!, model.workspace!!.operations!!, model.workspace!!.tasks!!)
    }
    @Test fun functionMutationProjectsIdenticallyAcrossPersonGroupAndHubWithoutTaskReassignment() {
        val h = Harness(); val m = h.model; val oldTasks = h.world.rows
        h.area(ActiveArea.FUNCTIONS); m.operations.focusFunction("support"); m.operations.focus("p")
        m.operations.changeFunction(); m.operations.function("hummel"); m.operations.saveLive(); m.operations.backToList()
        assertEquals(FunctionFocus("support"), m.operations.functionFocus); assertTrue(activeFunctionPeople(m.operations.board!!, "support").isEmpty())
        assertEquals(listOf("p"), activeFunctionPeople(m.operations.board!!, "hummel").map { it.person.id })
        assertSame(oldTasks, h.world.rows); assertEquals(0, h.world.taskWrites)
        h.area(ActiveArea.PERSONS); assertEquals("Hummel", activePersonCard(m.workspace!!.operations!!.persons.first(), m.workspace!!.operations!!, 5, java.time.ZoneId.of("UTC")).function)
        assertEquals("Support", activeTaskCard(m.tasks.board!!.tasks.first(), m.operations.board!!, 5, java.time.ZoneId.of("UTC")).assignee)
        h.area(ActiveArea.HUB); assertEquals(listOf("1 Hummel"), h.cards()[2].summary)
    }
    @Test fun nonOnSiteMutationsUpdatePersonFunctionsAndHubWithoutMutatingTasksOrAssignments() {
        for (status in OperationalStatus.entries.filter { !OperationalRules.roleAllowed(it) }) {
            val h = Harness(); val m = h.model; val rows = h.world.rows; val events = h.world.events
            h.person(); m.operations.update("p"); m.operations.status(status); m.operations.saveLive()
            assertEquals(status, m.operations.person!!.state!!.status); assertNull(m.operations.person!!.state!!.currentRoleId)
            assertSame(rows, h.world.rows); assertSame(events, h.world.events); assertEquals(0, h.world.taskWrites)
            assertEquals("p", rows.first().affectedPersonId); assertEquals("support", rows.first().assigneeRoleId)
            h.area(ActiveArea.FUNCTIONS); assertEquals(listOf("k"), activeFunctionContent(m.operations.board!!).without.map { it.person.id })
            h.area(ActiveArea.HUB); assertEquals(listOf("Noch keine aktuellen Funktionen"), h.cards()[2].summary)
            assertTrue(h.cards()[0].summary.contains("1 vor Ort"))
        }
    }
    @Test fun noStatusTechnicalRoleNeverBecomesAnActiveFunctionInAnyArea() {
        val h = Harness(); assertTrue(h.cards()[0].summary.contains("1 ohne Lage")); assertEquals(listOf("1 Support"), h.cards()[2].summary)
        h.area(ActiveArea.FUNCTIONS); assertEquals(listOf("support"), activeFunctionContent(h.model.operations.board!!).groups.map { it.role.id })
        h.area(ActiveArea.PERSONS); assertEquals("hummel", h.model.operations.board!!.persons[1].state!!.currentRoleId)
    }
    @Test fun personTaskCreationSelectsDetailAndUpdatesAffectedCountAndHub() {
        val h = Harness(); val m = h.model; h.person(); m.activePersonTasks("p", true)
        assertEquals("p", m.tasks.input.affectedPersonId); assertNull(m.tasks.input.assigneePersonId); assertNull(m.tasks.input.assigneeRoleId)
        m.tasks.change(m.tasks.input.copy(title = "Neue Übungsaufgabe", assigneeRoleId = "support")); m.tasks.save()
        assertEquals("new", m.tasks.selected?.id); assertEquals("p", m.tasks.selected?.affectedPersonId)
        assertEquals(2, m.tasks.board!!.affectedCount("p")); h.area(ActiveArea.HUB)
        assertEquals(listOf("3 offen", "1 hohe Priorität"), h.cards()[1].summary)
        h.area(ActiveArea.PERSONS); assertEquals(2, m.tasks.board!!.affectedCount("p"))
    }
    @Test fun affectedCountsFollowOnlyExplicitLifecycleAndFilterSurvivesDetailBack() {
        val h = Harness(); val m = h.model
        for (status in listOf(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.OPEN, TaskStatus.CANCELLED, TaskStatus.OPEN)) {
            h.person(); m.activePersonTasks("p", false)
            assertEquals(listOf("t"), (activeTaskContent(m.tasks.board!!, "p").active + activeTaskContent(m.tasks.board!!, "p").closed).map { it.id })
            m.tasks.open(m.tasks.board!!.tasks.first()); m.tasks.transition(status); assertEquals(status, m.tasks.selected!!.status)
            m.tasks.backToTasks(); assertEquals("p", m.tasks.filter)
            h.area(ActiveArea.HUB); val count = if (TaskRules.active(status)) 1 else 0
            assertEquals(count, m.tasks.board!!.affectedCount("p")); assertNull(m.tasks.filter)
            h.area(ActiveArea.PERSONS); assertEquals(count, m.tasks.board!!.affectedCount("p"))
        }
    }
    @Test fun filteredPersonTaskEntryDoesNotFilterByAssigneeAndCanReturnToFullBoard() {
        val h = Harness(); h.person(); h.model.activePersonTasks("p", false)
        assertEquals(listOf("t"), activeTaskContent(h.model.tasks.board!!, h.model.tasks.filter).active.map { it.id })
        val reads = h.world.taskReads; h.model.tasks.showPerson(null); assertEquals(2, activeTaskContent(h.model.tasks.board!!, null).active.size)
        assertEquals(reads, h.world.taskReads); h.area(ActiveArea.HUB); h.area(ActiveArea.TASKS); assertNull(h.model.tasks.filter)
    }
    @Test fun creationDetailWaitsForCompleteCrossBoardRefresh() {
        val h = Harness(); h.person(); h.model.activePersonTasks("p", true); h.model.tasks.change(h.model.tasks.input.copy(title = "Neu"))
        val old = h.model.workspace; h.world.defer = true; h.model.tasks.save(); assertSame(old, h.model.workspace); assertNull(h.model.tasks.selected)
        h.world.pendingTasks.removeAt(0)(OperationsResult.Value(h.world.tasks())); assertSame(old, h.model.workspace); assertNull(h.model.tasks.selected)
        h.world.pendingOps.removeAt(0)(OperationsResult.Value(h.world.operations())); assertEquals("new", h.model.tasks.selected?.id)
    }
    @Test fun crossAreaNavigationRejectsPendingCreatedTaskAndOldBoards() {
        val h = Harness(); h.person(); h.model.activePersonTasks("p", true); h.model.tasks.change(TaskInput("Neu", affectedPersonId = "p"))
        h.world.defer = true; h.model.tasks.save(); val oldOps = h.world.pendingOps.removeAt(0); val oldTasks = h.world.pendingTasks.removeAt(0)
        h.world.defer = false; h.area(ActiveArea.HUB); val current = h.model.workspace
        oldOps(OperationsResult.Value(h.world.operations())); oldTasks(OperationsResult.Value(h.world.tasks()))
        assertSame(current, h.model.workspace); assertNull(h.model.tasks.selected); assertEquals(ActiveArea.HUB, h.model.activeArea)
    }
    @Test fun userSelectionDuringCreateRefreshMustNotBeOverriddenByPendingFocus() {
        val h = Harness(); h.area(ActiveArea.TASKS); h.model.tasks.add(); h.model.tasks.change(TaskInput("Neu")); h.world.defer = true; h.model.tasks.save()
        h.model.tasks.open(h.model.tasks.board!!.tasks.last()); h.world.complete(); assertEquals("assigned", h.model.tasks.selected!!.id)
    }
    @Test fun personEditCancelAndGroupReturnNeverRequireAnExtraFullBoardRead() {
        val h = Harness(); h.area(ActiveArea.FUNCTIONS); h.model.operations.focusFunction("support"); h.model.operations.focus("p")
        h.model.operations.changeFunction(); h.model.operations.cancel(); val reads = h.world.opReads
        assertEquals(OperationsScreen.PERSON, h.model.operations.screen); h.model.operations.backToList()
        assertEquals(FunctionFocus("support"), h.model.operations.functionFocus); assertEquals(reads, h.world.opReads)
    }
    @Test fun lockClearsAllEightNestedContextsAndLateBoardsAcrossNewGeneration() {
        val paths = listOf<(Harness) -> Unit>(
            {}, { it.person(); it.model.personDetail("p") }, { it.person(); it.model.operations.update("p") },
            { it.person(); it.model.operations.changeFunction() },
            { it.area(ActiveArea.TASKS); it.model.tasks.open(it.model.tasks.board!!.tasks.first()) },
            { it.person(); it.model.activePersonTasks("p", true); it.model.tasks.change(TaskInput("Entwurf", affectedPersonId = "p")) },
            { it.area(ActiveArea.FUNCTIONS); it.model.operations.toggleMoreFunctions(); it.model.operations.focusFunction("support") },
            { it.area(ActiveArea.FUNCTIONS); it.model.operations.newFunction(); it.model.operations.changeNewFunctionName("Entwurf") })
        paths.forEach { enter ->
            val h = Harness(); enter(h); h.world.defer = true; h.model.loadWorkspace(); h.session.lock()
            h.session.emit(SessionState.READY, 3)
            h.world.pendingOps.forEach { it(OperationsResult.Value(h.world.operations())) }; h.world.pendingTasks.forEach { it(OperationsResult.Value(h.world.tasks())) }
            assertNull(h.model.workspace); assertNull(h.model.selected); assertNull(h.model.selectedPerson); assertNull(h.model.operations.personId)
            assertNull(h.model.operations.functionFocus); assertEquals("", h.model.operations.newFunctionName); assertFalse(h.model.operations.moreFunctionsExpanded)
            assertNull(h.model.tasks.selected); assertNull(h.model.tasks.filter); assertFalse(h.model.tasks.form); assertFalse(h.model.tasks.closedExpanded); assertEquals(TaskInput(""), h.model.tasks.input)
        }
    }
    @Test fun completedCrossAreaReadsRemainAvailableAndAllWritesAreDenied() {
        val h = Harness(ActionStatus.COMPLETED); val m = h.model
        assertNotNull(m.workspace?.operations); assertNotNull(m.workspace?.tasks)
        m.operations.focus("p"); m.operations.update("p"); m.operations.changeFunction(); m.operations.newFunction(); m.operations.saveNewFunction(); m.operations.renameRole("support", "X"); m.operations.deleteRole("support")
        m.tasks.open(m.tasks.board!!.tasks.first()); m.tasks.edit(); m.tasks.add(); m.tasks.transition(TaskStatus.DONE); m.tasks.delete()
        assertEquals(0, h.world.opWrites); assertEquals(0, h.world.taskWrites); assertFalse(m.tasks.form); assertFalse(m.operations.live)
    }
    @Test fun restoredPersonCountUsesAffectedActiveTasksAndNeverClaimsMissingBoardCount() {
        val h = Harness(); h.person(); assertEquals("1 offene Aufgabe", activeAffectedTaskLabel(h.model.tasks.board, "p"))
        h.model.activePersonTasks("p", true); h.model.tasks.change(TaskInput("Neu", affectedPersonId = "p")); h.model.tasks.save()
        h.area(ActiveArea.PERSONS); assertEquals("2 offene Aufgaben", activeAffectedTaskLabel(h.model.tasks.board, "p"))
        h.area(ActiveArea.TASKS); h.model.tasks.open(h.model.tasks.board!!.tasks.first()); h.model.tasks.transition(TaskStatus.DONE)
        h.area(ActiveArea.PERSONS); assertEquals("1 offene Aufgabe", activeAffectedTaskLabel(h.model.tasks.board, "p"))
        h.area(ActiveArea.FUNCTIONS); assertNull(activeAffectedTaskLabel(h.model.tasks.board, "p"))
    }
    @Test fun p3bDetailDoesNotCorruptFunctionReturnAndNoPayloadIsReadInCrossFlows() {
        val protection = Protection(); val h = Harness(protection = protection)
        h.area(ActiveArea.FUNCTIONS); h.model.operations.focusFunction("support"); h.model.operations.focus("p")
        assertEquals(0, protection.inventories); h.model.personDetail("p"); assertEquals(1, protection.inventories)
        h.model.backToPersons(); assertEquals(OperationsScreen.PERSON, h.model.operations.screen)
        h.model.operations.backToList(); assertEquals(FunctionFocus("support"), h.model.operations.functionFocus)
        h.area(ActiveArea.HUB); h.person(); h.model.activePersonTasks("p", false); assertEquals(0, protection.opens)
    }
    @Test fun crossAreaDisplayProjectionsNeverInteractOrReadPorts() {
        val h = Harness(); val interactions = h.session.interactions; val reads = h.world.opReads to h.world.taskReads
        repeat(3) {
            h.cards(); activePeopleSummary(h.model.operations.board!!); activeTaskContent(h.model.tasks.board!!, null)
            activeFunctionContent(h.model.operations.board!!); activeAffectedTaskLabel(h.model.tasks.board, "p")
        }
        assertEquals(interactions, h.session.interactions); assertEquals(reads, h.world.opReads to h.world.taskReads)
    }
    @Test fun functionFormDetailShortcutDiscardsOnlyDraftAndPreservesGroupReturnWithoutRead() {
        val protection = Protection(); val h = Harness(protection = protection)
        h.area(ActiveArea.FUNCTIONS); h.model.operations.focusFunction("support"); h.model.operations.focus("p")
        h.model.operations.changeFunction(); h.model.operations.function("hummel"); val reads = h.world.opReads
        h.model.operationalPersonDetail(); assertEquals("p", h.model.selectedPerson?.id)
        assertEquals(1, protection.inventories); assertEquals(reads, h.world.opReads); assertEquals(0, h.world.opWrites)
        h.model.backToPersons(); assertEquals("support", h.model.operations.person!!.state!!.currentRoleId)
        assertNull(h.model.operations.liveRoleId); h.model.operations.backToList(); assertEquals(FunctionFocus("support"), h.model.operations.functionFocus)
    }
}
