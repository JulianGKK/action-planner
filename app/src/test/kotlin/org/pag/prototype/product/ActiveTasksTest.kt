package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class ActiveTasksTest {
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
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { loads.add(publish) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) {}
    }
    private class Tasks : TaskRepository {
        val loads = mutableListOf<(OperationsResult<TaskBoard>) -> Unit>()
        var rows = emptyList<Task>(); var events = emptyList<TaskEvent>(); var writes = 0
        var deferWrite = false; var pending: (() -> Unit)? = null
        private fun result(task: Task, publish: (OperationsResult<Task>) -> Unit) {
            writes++; if (deferWrite) pending = { publish(OperationsResult.Value(task)) } else publish(OperationsResult.Value(task))
        }
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) { loads.add(publish) }
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {
            val task = Task("new", actionId, input.title, input.priority, TaskStatus.OPEN, input.assigneePersonId, input.assigneeRoleId, input.affectedPersonId, input.dueAt, input.sourceTemplateKey, 5, 5, null)
            rows = rows + task; events = events + TaskEvent("e-new", actionId, task.id, TaskEventType.CREATED, 5); result(task, publish)
        }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {
            val task = expected.copy(title = input.title, priority = input.priority, assigneePersonId = input.assigneePersonId, assigneeRoleId = input.assigneeRoleId, affectedPersonId = input.affectedPersonId, dueAt = input.dueAt, updatedAt = 5)
            rows = rows.map { if (it.id == expected.id) task else it }; result(task, publish)
        }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) {
            val task = TaskRules.transition(expected, status, 5) ?: return publish(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED))
            rows = rows.map { if (it.id == expected.id) task else it }
            events = events + TaskEvent("e-$writes", actionId, task.id, TaskRules.event(expected.status, status)!!, 5); result(task, publish)
        }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { writes++; rows = rows.filterNot { it.id == expected.id }; publish(OperationsResult.Value(Unit)) }
    }
    private class Protection : LeadershipRepository {
        val states = mutableListOf<Pair<String, (SensitiveResult<SensitiveState>) -> Unit>>()
        var inventories = 0; var setups = 0; var opens = 0; var revokes = 0
        var setupResult: ((SensitiveResult<Unit>) -> Unit)? = null
        var onRevoke: (() -> Unit)? = null
        override fun actionState(actionId: String, publish: (SensitiveResult<SensitiveState>) -> Unit) { states.add(actionId to publish) }
        override fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit) { inventories++; publish(SensitiveResult.Value(SensitiveInventory(SensitiveState.READY))) }
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
    private val board = OperationalBoard("a", ActionStatus.ACTIVE, listOf(Role("r", "a", "Support", 0)), listOf(person))
    private val task = Task("t", "a", "synthetic task", TaskPriority.HIGH, TaskStatus.OPEN, null, "r", null, null, null, 0, 0, null)
    private val taskBoard = TaskBoard("a", listOf(task), emptyList())
    private fun open() { tasks.rows = taskBoard.tasks; model.detail("a") }
    private fun complete() {
        ops.loads.last()(OperationsResult.Value(board.copy(status = actions.row.status)))
        if (model.activeArea != ActiveArea.FUNCTIONS) tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = tasks.rows, events = tasks.events)))
    }
    private val zone = java.time.ZoneId.of("Europe/Berlin")
    private val now = java.time.ZonedDateTime.of(2026, 10, 13, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun openTasks() { open(); complete(); model.activeArea(ActiveArea.TASKS); complete() }
    private fun content() = activeTaskContent(model.tasks.board!!, model.tasks.filter)
    @Test fun emptyBoardHasNoFakeCounts() {
        assertEquals(listOf("Keine offenen Aufgaben"), activeTaskContent(taskBoard.copy(tasks = emptyList()), null).summary)
    }
    @Test fun summarySeparatesOpenProgressAndOnlyActiveHigh() {
        val rows = listOf(task, task.copy(id = "progress", status = TaskStatus.IN_PROGRESS), task.copy(id = "done", status = TaskStatus.DONE), task.copy(id = "cancelled", status = TaskStatus.CANCELLED))
        val content = activeTaskContent(taskBoard.copy(tasks = rows), null)
        assertEquals(listOf("1 offen · 1 in Arbeit", "2 hohe Priorität"), content.summary); assertEquals(2, content.closed.size)
    }
    @Test fun closedOnlyBoardHasNoHighAttention() {
        assertEquals(listOf("Keine offenen Aufgaben"), activeTaskContent(taskBoard.copy(tasks = listOf(task.copy(status = TaskStatus.DONE))), null).summary)
    }
    @Test fun domainSortAndStableTieBreakersArePreserved() {
        val rows = listOf(task.copy(id = "low", priority = TaskPriority.LOW), task.copy(id = "normal", priority = TaskPriority.NORMAL),
            task.copy(id = "none", dueAt = null), task.copy(id = "later", dueAt = 9), task.copy(id = "b", dueAt = 1, createdAt = 1),
            task.copy(id = "a", dueAt = 1, createdAt = 1), task.copy(id = "early-created", dueAt = 1, createdAt = 0))
        assertEquals(listOf("early-created", "a", "b", "later", "none", "normal", "low"), activeTaskContent(taskBoard.copy(tasks = rows.reversed()), null).active.map { it.id })
    }
    @Test fun cardsHaveTitlePriorityAndStatusForEveryExistingStatus() {
        TaskStatus.entries.forEach { status ->
            val card = activeTaskCard(task.copy(status = status), board, now, zone)
            assertEquals(task.title, card.title); assertEquals("Hoch · ${taskStatusLabel(status)}", card.status)
        }
    }
    @Test fun roleAssigneeIsResolvedWithoutPersonFallback() { assertEquals("Support", activeTaskCard(task, board, now, zone).assignee) }
    @Test fun personAssigneeUsesPseudonym() { assertEquals("synthetic", activeTaskCard(task.copy(assigneeRoleId = null, assigneePersonId = "p"), board, now, zone).assignee) }
    @Test fun unassignedIsExplicit() { assertEquals("Niemand zugeordnet", activeTaskCard(task.copy(assigneeRoleId = null), board, now, zone).assignee) }
    @Test fun affectedPersonIsIndependentOfAssignee() {
        val card = activeTaskCard(task.copy(affectedPersonId = "p"), board, now, zone)
        assertEquals("Support", card.assignee); assertEquals("synthetic", card.affected)
        assertNull(activeTaskCard(task, board, now, zone).affected)
    }
    @Test fun dueTimeUsesLocalDayWithoutChangingTimestampSemantics() {
        assertEquals("18:30", activeTaskCard(task.copy(dueAt = now + 16_200_000), board, now, zone).due)
        assertEquals("14.10.2026 14:00", activeTaskCard(task.copy(dueAt = now + 86_400_000), board, now, zone).due)
        assertNull(activeTaskCard(task, board, now, zone).due)
    }
    @Test fun openHasOnlyP5aActions() { assertEquals(listOf(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.CANCELLED), activeTaskActions(TaskStatus.OPEN)) }
    @Test fun progressHasOnlyCloseActions() { assertEquals(listOf(TaskStatus.DONE, TaskStatus.CANCELLED), activeTaskActions(TaskStatus.IN_PROGRESS)) }
    @Test fun bothClosedStatusesOnlyReopen() { listOf(TaskStatus.DONE, TaskStatus.CANCELLED).forEach { assertEquals(listOf(TaskStatus.OPEN), activeTaskActions(it)) } }
    @Test fun cardOpeningDoesNotLoadBoardOrProtection() {
        openTasks(); val reads = tasks.loads.size; model.tasks.open(task)
        assertEquals(task, model.tasks.selected); assertEquals(reads, tasks.loads.size); assertEquals(0, protection.inventories)
    }
    @Test fun editSaveKeepsFocusAndUpdatesSummaryAfterCompleteReplacement() {
        openTasks(); model.tasks.open(task); model.tasks.edit(); model.tasks.change(task.input().copy(title = "synthetic edited", priority = TaskPriority.LOW))
        val old = model.workspace; model.tasks.save(); assertSame(old, model.workspace); assertEquals(task, model.tasks.selected)
        complete(); assertEquals("synthetic edited", model.tasks.selected!!.title); assertEquals(listOf("1 offen · 0 in Arbeit"), content().summary)
        assertEquals("Aufgabe gespeichert", model.feedback!!.text)
    }
    @Test fun cancellingActiveEditReturnsToOriginalDetailWithoutReadOrWrite() {
        openTasks(); model.tasks.open(task); model.tasks.edit(); model.tasks.change(TaskInput("discard")); val reads = tasks.loads.size
        model.tasks.cancel(); assertEquals(task, model.tasks.selected); assertFalse(model.tasks.form); assertEquals(reads, tasks.loads.size); assertEquals(0, tasks.writes)
        model.tasks.backToTasks(); assertNull(model.tasks.selected)
    }
    @Test fun createHasExistingDefaultsAndFeedback() {
        openTasks(); model.tasks.add(); assertEquals(TaskInput(""), model.tasks.input)
        model.tasks.change(TaskInput("synthetic new")); model.tasks.save(); complete()
        assertEquals(TaskStatus.OPEN, tasks.rows.last().status); assertEquals(TaskPriority.NORMAL, tasks.rows.last().priority)
        assertEquals(listOf(TaskEventType.CREATED), model.tasks.board!!.events.map { it.type }); assertEquals("Aufgabe angelegt", model.feedback!!.text); assertEquals(listOf("2 offen · 0 in Arbeit", "1 hohe Priorität"), content().summary)
    }
    @Test fun closedSectionStartsCollapsedAndIsRamOnly() {
        openTasks(); assertFalse(model.tasks.closedExpanded); model.tasks.toggleClosed(); assertTrue(model.tasks.closedExpanded)
        model.tasks.toggleClosed(); assertFalse(model.tasks.closedExpanded)
    }
    @Test fun transitionKeepsClosedTaskVisibleUntilDeliberateBack() {
        openTasks(); model.tasks.open(task); model.tasks.transition(TaskStatus.DONE); val old = model.workspace
        assertEquals(TaskStatus.OPEN, model.tasks.selected!!.status); assertSame(old, model.workspace)
        complete(); assertEquals(TaskStatus.DONE, model.tasks.selected!!.status); assertTrue(content().active.isEmpty()); assertEquals(1, content().closed.size)
        model.tasks.backToTasks(); assertNull(model.tasks.selected); model.tasks.toggleClosed(); assertTrue(model.tasks.closedExpanded)
        model.tasks.open(model.tasks.board!!.closed.single()); model.tasks.transition(TaskStatus.OPEN); complete()
        assertEquals(TaskStatus.OPEN, model.tasks.selected!!.status); assertEquals(1, content().active.size)
    }
    @Test fun lifecycleChronicleContainsOnlyExistingEvents() {
        openTasks(); model.tasks.open(task)
        listOf(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.OPEN, TaskStatus.CANCELLED, TaskStatus.OPEN).forEach { model.tasks.transition(it); complete() }
        assertEquals(listOf(TaskEventType.STARTED, TaskEventType.COMPLETED, TaskEventType.REOPENED, TaskEventType.CANCELLED, TaskEventType.REOPENED), model.tasks.board!!.events.map { it.type })
        assertEquals(5, TaskEventType.entries.size)
    }
    @Test fun activeCannotDeleteAndCompletedCannotWrite() {
        openTasks(); model.tasks.open(task); model.tasks.delete(); assertEquals(0, tasks.writes)
        model.activeArea(ActiveArea.HUB); complete(); actions.row = actions.row.copy(status = ActionStatus.COMPLETED)
        model.detail("a"); complete(); model.tasks.open(task); model.tasks.edit(); model.tasks.add(); model.tasks.transition(TaskStatus.DONE); model.tasks.delete()
        assertEquals(0, tasks.writes); assertFalse(model.tasks.form); assertFalse(model.tasks.writable)
    }
    @Test fun filterUsesAffectedRatherThanAssignmentAndHasConsistentCounts() {
        val rows = listOf(task.copy(id = "affected", affectedPersonId = "p"), task.copy(id = "assigned", assigneePersonId = "p", assigneeRoleId = null), task.copy(id = "closed", affectedPersonId = "p", status = TaskStatus.CANCELLED))
        val view = activeTaskContent(taskBoard.copy(tasks = rows), "p")
        assertEquals(listOf("affected"), view.active.map { it.id }); assertEquals(listOf("closed"), view.closed.map { it.id }); assertEquals(listOf("1 offen · 0 in Arbeit", "1 hohe Priorität"), view.summary)
    }
    @Test fun personTaskPathPrefillsAffectedAndRemovingFilterDoesNotRead() {
        openTasks(); model.activePersonTasks("p", true); assertEquals("p", model.tasks.input.affectedPersonId)
        model.tasks.cancel(); model.tasks.showPerson("p"); model.tasks.toggleClosed(); val reads = tasks.loads.size
        model.tasks.showPerson(null); assertNull(model.tasks.filter); assertFalse(model.tasks.closedExpanded); assertEquals(reads, tasks.loads.size)
    }
    @Test fun initialTasksRevealIsAtomic() {
        open(); complete(); model.activeArea(ActiveArea.TASKS); assertNull(model.workspace)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertNull(model.workspace)
        ops.loads.last()(OperationsResult.Value(board)); assertNotNull(model.workspace)
    }
    @Test fun refreshKeepsSummaryActiveAndClosedCountTogether() {
        openTasks(); val old = model.workspace; model.loadWorkspace()
        tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = listOf(task.copy(status = TaskStatus.DONE))))); assertSame(old, model.workspace)
        ops.loads.last()(OperationsResult.Value(board)); assertNotSame(old, model.workspace); assertEquals(listOf("Keine offenen Aufgaben"), content().summary)
    }
    @Test fun staleRefreshRequestAndGenerationAreDiscarded() {
        openTasks(); model.loadWorkspace(); val stale = tasks.loads.last(); model.loadWorkspace(); complete(); val current = model.workspace
        stale(OperationsResult.Value(taskBoard.copy(tasks = emptyList()))); assertSame(current, model.workspace)
        model.tasks.open(task); model.tasks.edit(); model.tasks.showPerson("p"); model.tasks.toggleClosed()
        val late = tasks.loads.last(); session.emit(SessionState.READY, 2); late(OperationsResult.Value(taskBoard))
        assertNull(model.workspace); assertNull(model.tasks.selected); assertNull(model.tasks.filter); assertFalse(model.tasks.form); assertFalse(model.tasks.closedExpanded)
    }
    @Test fun staleWriteCannotRestoreTaskOrFeedbackAfterLock() {
        openTasks(); model.tasks.open(task); tasks.deferWrite = true; model.tasks.transition(TaskStatus.DONE); session.lock(); tasks.pending!!()
        assertNull(model.workspace); assertNull(model.tasks.selected); assertNull(model.feedback)
    }
    @Test fun lockClearsDetailEditorFilterAndDisclosure() {
        openTasks(); model.tasks.showPerson("p"); model.tasks.toggleClosed(); model.tasks.add("p"); model.tasks.change(TaskInput("synthetic draft")); session.lock()
        assertNull(model.workspace); assertNull(model.tasks.filter); assertFalse(model.tasks.form); assertFalse(model.tasks.closedExpanded); assertEquals(TaskInput(""), model.tasks.input)
    }
    @Test fun synchronousInteractionRevokeCannotRestoreDisclosureOrCancelState() {
        openTasks(); model.tasks.open(task); model.tasks.edit(); session.onInteraction = { session.lock() }
        model.tasks.cancel(); model.tasks.toggleClosed(); assertNull(model.tasks.selected); assertFalse(model.tasks.closedExpanded); assertFalse(model.tasks.form)
    }
}
