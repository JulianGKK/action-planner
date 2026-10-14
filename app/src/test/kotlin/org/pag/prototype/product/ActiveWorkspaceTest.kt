package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class ActiveWorkspaceTest {
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
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { loads.add(publish) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) {}
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
    private val summaries = mutableListOf<(ProtocolResult<ProtocolSummary>) -> Unit>()
    private val protocolDelegate = ProtocolFake()
    private val protocolPort = object : ProtocolRepository by protocolDelegate {
        override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) { summaries.add(publish) }
    }
    private val model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, people, protection, ops, tasks, protocolPort) {}
    private val person = OperationalPerson(Person("p", "a", "synthetic", 0), PersonRolePlan(setOf("r"), "r"), null)
    private val board = OperationalBoard("a", ActionStatus.ACTIVE, listOf(Role("r", "a", "Support", 0)), listOf(person))
    private val task = Task("t", "a", "synthetic task", TaskPriority.HIGH, TaskStatus.OPEN, null, "r", null, null, null, 0, 0, null)
    private val taskBoard = TaskBoard("a", listOf(task), emptyList())
    private fun open() = model.detail("a")
    private fun complete() {
        ops.loads.last()(OperationsResult.Value(board.copy(status = actions.row.status)))
        if (model.activeArea != ActiveArea.FUNCTIONS) tasks.loads.last()(OperationsResult.Value(taskBoard))
        if (model.selected?.status == ActionStatus.ACTIVE && model.activeArea in setOf(ActiveArea.HUB, ActiveArea.PROTOCOL)) summaries.last()(ProtocolResult.Value(ProtocolSummary(3, 1000)))
    }
    @Test fun activeStartsAtHubAndPublishesAllRequiredSourcesTogetherWithoutProtectionReads() {
        open(); assertEquals(ActiveArea.HUB, model.activeArea); assertNull(model.workspace)
        ops.loads.last()(OperationsResult.Value(board)); assertNull(model.workspace)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertNull(model.workspace)
        summaries.last()(ProtocolResult.Value(ProtocolSummary(3, 1000))); assertNotNull(model.workspace)
        assertTrue(protection.states.isEmpty()); assertEquals(0, protection.inventories)
        assertEquals(ActiveArea.entries.drop(1).filter { it != ActiveArea.MEMORY_REPORTS }.filter { it !in setOf(ActiveArea.PROTOCOL, ActiveArea.MATERIAL, ActiveArea.COSTS) }, activeHubCards(actions.row, board, taskBoard).map { it.area })
    }
    @Test fun allActiveAreasReturnToHubAndDetailsNeedNoBoardRead() {
        open(); complete()
        ActiveArea.entries.drop(1).filter { it != ActiveArea.MEMORY_REPORTS }.filter { it != ActiveArea.MATERIAL && it != ActiveArea.COSTS }.forEach { area ->
            val reads = ops.loads.size
            model.activeArea(area); assertEquals(area, model.activeArea)
            if (area == ActiveArea.DETAILS) assertEquals(reads, ops.loads.size) else complete()
            model.activeArea(ActiveArea.HUB); complete(); assertEquals(ActiveArea.HUB, model.activeArea)
        }
    }
    @Test fun functionsReadNoTasksOrProtection() {
        open(); complete(); val reads = tasks.loads.size
        model.activeArea(ActiveArea.FUNCTIONS); complete()
        assertEquals(reads, tasks.loads.size); assertTrue(protection.states.isEmpty())
    }
    @Test fun preparationTransitionOpensHub() {
        actions.row = actions.row.copy(status = ActionStatus.PREPARATION); open()
        model.requestAdvance(); model.confirmAdvance()
        assertEquals(ActionStatus.ACTIVE, model.selected!!.status); assertEquals(ActiveArea.HUB, model.activeArea)
        complete(); assertNotNull(model.workspace)
    }
    @Test fun summaryUsesCurrentOnSiteFunctionRatherThanPlan() {
        val live = person.copy(state = PersonOperationalState("a", "p", "current", OperationalStatus.ON_SITE, null, 1))
        val current = board.copy(roles = board.roles + Role("current", "a", "Hummel", 0), persons = listOf(live))
        val cards = activeHubCards(actions.row, current, taskBoard)
        assertEquals(listOf("1 Hummel"), cards[2].summary)
        assertTrue(cards[0].summary.contains("1 vor Ort")); assertNull(cards[0].attention)
        assertEquals(listOf("1 offen", "1 hohe Priorität"), cards[1].summary)
        assertNotNull(cards[1].attention); assertEquals(3, cards[3].summary.size)
    }
    @Test fun missingStateAndNoStatusBothCountWithoutClaimingAvailability() {
        val unknown = person.copy(person = person.person.copy(id = "q"), state = PersonOperationalState("a", "q", "r", OperationalStatus.NO_STATUS, null, 0))
        val cards = activeHubCards(actions.row, board.copy(persons = listOf(person, unknown)), taskBoard)
        assertTrue(cards[0].summary.contains("2 ohne Lage")); assertEquals("2 Personen ohne aktuelle Lage", cards[0].attention)
        assertEquals(listOf("Noch keine aktuellen Funktionen"), cards[2].summary)
    }
    @Test fun oneAttentionUsesCustodyThenUnreachableThenMissing() {
        fun p(id: String, status: OperationalStatus) = person.copy(person = person.person.copy(id = id), state = PersonOperationalState("a", id, null, status, null, 0))
        val cards = activeHubCards(actions.row, board.copy(persons = listOf(person, p("c", OperationalStatus.IN_CUSTODY), p("u", OperationalStatus.NOT_REACHABLE))), taskBoard)
        assertEquals("1 Person in Gewahrsam", cards[0].attention)
        assertNull(cards[2].attention); assertNull(cards[3].attention)
    }
    @Test fun zerosAndClosedHighTasksDoNotInventAttention() {
        val cards = activeHubCards(actions.row, board.copy(persons = emptyList()), taskBoard.copy(tasks = listOf(task.copy(status = TaskStatus.DONE))))
        assertEquals(listOf("Noch keine Personen"), cards[0].summary); assertNull(cards[0].attention)
        assertEquals(listOf("Keine offenen Aufgaben"), cards[1].summary); assertNull(cards[1].attention)
    }
    @Test fun refreshKeepsCompleteOldSnapshotUntilBothNewBoardsArrive() {
        open(); complete(); val old = model.workspace
        model.loadWorkspace(); ops.loads.last()(OperationsResult.Value(board.copy(persons = emptyList())))
        assertSame(old, model.workspace); assertTrue(model.workspaceRefreshing)
        tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = emptyList())))
        assertSame(old, model.workspace)
        summaries.last()(ProtocolResult.Value(ProtocolSummary(4, 2000)))
        assertNotSame(old, model.workspace); assertFalse(model.workspaceRefreshing)
    }
    @Test fun staleAreaAndGenerationCannotPublishAndLockClearsForms() {
        open(); val staleOps = ops.loads.last(); val staleTasks = tasks.loads.last()
        model.activeArea(ActiveArea.TASKS); complete(); model.tasks.add(); assertTrue(model.tasks.form)
        session.lock(); staleOps(OperationsResult.Value(board)); staleTasks(OperationsResult.Value(taskBoard))
        assertNull(model.workspace); assertNull(model.selected); assertFalse(model.tasks.form); assertEquals(ActiveArea.HUB, model.activeArea)
    }
    @Test fun areaChangeRejectsAfterInteractionOrCleanupRevoke() {
        open(); complete(); session.onInteraction = { session.lock() }
        model.activeArea(ActiveArea.DETAILS); assertNull(model.selected); assertEquals(ActiveArea.HUB, model.activeArea)
    }
    @Test fun largeTextAndNarrowWidthsUseOneColumn() {
        assertEquals(1, activeHubColumns(360f, 1f)); assertEquals(1, activeHubColumns(800f, 2f))
        assertEquals(2, activeHubColumns(600f, 1f)); assertEquals(1, activeHubColumns(599f, 1f))
    }
    @Test fun oldAreaReadCannotReplaceNewHub() {
        open(); complete(); model.activeArea(ActiveArea.FUNCTIONS); val old = ops.loads.last()
        model.activeArea(ActiveArea.HUB); complete(); val content = model.workspace
        old(OperationsResult.Value(board.copy(persons = emptyList())))
        assertSame(content, model.workspace)
    }
    @Test fun cleanupTriggeredLockCannotReopenNavigation() {
        open(); complete(); protection.onRevoke = { session.lock() }
        model.activeArea(ActiveArea.FUNCTIONS)
        assertNull(model.selected); assertNull(model.workspace); assertEquals(ActiveArea.HUB, model.activeArea)
    }
    @Test fun personTaskQuickActionUsesCompleteSnapshotWithoutLoadInteractionAndIsClearedByLock() {
        open(); complete(); model.activePersonTasks("p", true)
        assertEquals(ActiveArea.TASKS, model.activeArea); assertTrue(model.tasks.form)
        val interactions = session.interactions
        ops.loads.last()(OperationsResult.Value(board)); assertTrue(model.tasks.form)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertTrue(model.tasks.form)
        assertEquals(interactions, session.interactions)
        assertEquals("p", model.tasks.input.affectedPersonId)
        model.activeArea(ActiveArea.PERSONS); complete(); model.activePersonTasks("p", true)
        val lateOps = ops.loads.last(); val lateTasks = tasks.loads.last(); session.lock()
        lateOps(OperationsResult.Value(board)); lateTasks(OperationsResult.Value(taskBoard))
        assertFalse(model.tasks.form); assertNull(model.workspace)
    }
    @Test fun refreshFailureRetainsCompleteContentAndInitialFailureHasNoFakeCounts() {
        open(); ops.loads.last()(OperationsResult.Rejected(OperationsFailure.NOT_FOUND))
        assertNull(model.workspace); assertTrue(model.workspaceError)
        model.loadWorkspace(); complete(); val old = model.workspace
        model.loadWorkspace(); tasks.loads.last()(OperationsResult.Rejected(OperationsFailure.NOT_FOUND))
        assertSame(old, model.workspace); assertTrue(model.workspaceError); assertFalse(model.workspaceRefreshing)
    }
    @Test fun newGenerationDiscardsAreaAndLateHubCallbacks() {
        open(); model.activeArea(ActiveArea.FUNCTIONS); val late = ops.loads.last()
        session.emit(SessionState.READY, 2); late(OperationsResult.Value(board))
        assertNull(model.selected); assertNull(model.workspace); assertEquals(ActiveArea.HUB, model.activeArea)
    }

    @Test fun protocolSummaryFailureNeverPublishesPartialCountsAndRefreshKeepsOldSnapshot() {
        open(); complete(); val old = model.workspace
        model.loadWorkspace()
        ops.loads.last()(OperationsResult.Value(board.copy(persons = emptyList())))
        tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = emptyList())))
        summaries.last()(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE))
        assertSame(old, model.workspace); assertTrue(model.workspaceError)
        model.back(); open()
        summaries.last()(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE))
        assertNull(model.workspace)
    }
    @Test fun productHubHasFiveCardsWithOnlyProtocolMetadataAndAreaCleanupClearsProtocolDraft() {
        open(); complete()
        val content = model.workspace!!
        val cards = activeHubCards(actions.row, content.operations!!, content.tasks!!, content.protocolSummary)
        assertEquals(ActiveArea.entries.drop(1).filter { it != ActiveArea.MEMORY_REPORTS }.filter { it != ActiveArea.MATERIAL && it != ActiveArea.COSTS }, cards.map { it.area })
        assertEquals("3 Schlüsselereignisse", cards.single { it.area == ActiveArea.PROTOCOL }.summary.first())
        model.activeArea(ActiveArea.PROTOCOL); complete(); model.protocol.create("POLICE_ARRIVED")
        assertTrue(model.protocol.form); assertEquals(0, protocolDelegate.detailReads)
        model.activeArea(ActiveArea.PERSONS); complete(); assertFalse(model.protocol.form)
    }
    @Test fun lateSummaryAfterLockCannotPublishOrRestoreProtocolNavigation() {
        open(); val late = summaries.last(); model.protocol.create("POLICE_ARRIVED"); session.lock()
        late(ProtocolResult.Value(ProtocolSummary(99, 1000)))
        assertNull(model.workspace); assertFalse(model.protocol.form); assertNull(model.protocol.timeline)
    }

}
