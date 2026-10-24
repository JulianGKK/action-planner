package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class PreparationWorkspaceTest {
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
        var row = Action("a", "synthetic action", "synthetic description", 1, 2, 0, ActionStatus.PREPARATION)
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
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(Person(personId, actionId, "synthetic", 0, editRevision = 0)))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private class Ops : OperationsRepository {
        val loads = mutableListOf<(OperationsResult<OperationalBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { loads.add(publish) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun renameRole(actionId: String, expected: Role, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun deleteRole(actionId: String, expected: Role, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) {}
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
    private val model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, people, protection, ops, tasks) {}
    private val person = OperationalPerson(Person("p", "a", "synthetic", 0, editRevision = 0), PersonRolePlan(setOf("r"), "r", editRevision = 0), null)
    private val board = OperationalBoard("a", ActionStatus.PREPARATION, listOf(Role("r", "a", "Support", 0, editRevision = 0)), listOf(person))
    private val task = Task("t", "a", "synthetic task", TaskPriority.HIGH, TaskStatus.OPEN, null, "r", null, null, null, 0, 0, null, editRevision = 0)
    private val taskBoard = TaskBoard("a", listOf(task), emptyList())
    private fun open() = model.detail("a")
    private fun complete(state: SensitiveState = SensitiveState.UNINITIALIZED) {
        ops.loads.last()(OperationsResult.Value(board.copy(status = actions.row.status)))
        if (model.preparationArea in setOf(PreparationArea.OVERVIEW, PreparationArea.TASKS)) tasks.loads.last()(OperationsResult.Value(taskBoard))
        if (model.preparationArea == PreparationArea.OVERVIEW) protection.states.last().second(SensitiveResult.Value(state))
    }
    @Test fun draftHasNoWorkspaceOrPreparationReads() {
        actions.row = actions.row.copy(status = ActionStatus.DRAFT); open()
        assertFalse(model.preparationAvailable); assertNull(model.workspace)
        model.preparationArea(PreparationArea.TASKS)
        assertTrue(ops.loads.isEmpty()); assertTrue(tasks.loads.isEmpty()); assertTrue(protection.states.isEmpty()); assertEquals(0, people.lists)
    }
    @Test fun draftBeginsPreparationOnlyAfterConfirmation() {
        actions.row = actions.row.copy(status = ActionStatus.DRAFT); open(); model.requestAdvance()
        assertEquals(0, actions.transitions); model.confirmAdvance()
        assertEquals(ActionStatus.PREPARATION, model.selected!!.status); assertTrue(model.preparationAvailable); assertEquals(1, actions.transitions)
        assertEquals(PreparationContentState.LOADING, model.preparationContentState)
    }
    @Test fun preparationAreasIncludeMaterialAndStartAtOverview() {
        open(); assertTrue(model.preparationAvailable); assertEquals(6, PreparationArea.entries.size)
        assertEquals(PreparationArea.OVERVIEW, model.preparationArea); assertEquals(PreparationContentState.LOADING, model.preparationContentState)
    }
    @Test fun overviewStagesAllThreeReadsBeforeOneReveal() {
        open(); ops.loads.last()(OperationsResult.Value(board)); assertNull(model.workspace)
        protection.states.last().second(SensitiveResult.Value(SensitiveState.READY)); assertNull(model.workspace)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertNotNull(model.workspace)
        assertEquals(PreparationContentState.CONTENT, model.preparationContentState); assertEquals(0, protection.inventories); assertEquals(0, protection.opens)
    }
    @Test fun overviewMetadataLastCannotRevealPartialCounts() {
        open(); ops.loads.last()(OperationsResult.Value(board)); tasks.loads.last()(OperationsResult.Value(taskBoard))
        assertNull(model.workspace); assertTrue(model.persons.isEmpty())
        protection.states.last().second(SensitiveResult.Value(SensitiveState.UNINITIALIZED)); assertEquals(1, model.persons.size)
    }
    @Test fun summaryUsesLoadedPlansAndOnlyActiveHighTasks() {
        open(); ops.loads.last()(OperationsResult.Value(board.copy(persons = listOf(person, person.copy(person = person.person.copy(id = "p2"), plan = PersonRolePlan(emptySet(), null, editRevision = 0))))))
        tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = listOf(task, task.copy(id = "work", status = TaskStatus.IN_PROGRESS, priority = TaskPriority.NORMAL), task.copy(id = "done", status = TaskStatus.DONE)))))
        protection.states.last().second(SensitiveResult.Value(SensitiveState.READY))
        assertEquals(PreparationSummary(2, 1, 1, 1, 1, 1), preparationSummary(model.workspace!!))
    }
    @Test fun actionStatesRemainDistinctAndNeverFetchPersonInventory() {
        for (state in SensitiveState.entries) { open(); complete(state); assertEquals(SensitiveResult.Value(state), model.workspace!!.leadershipState) }
        assertEquals(0, protection.inventories); assertEquals(0, protection.opens); assertEquals(0, protection.setups)
    }
    @Test fun setupOnlyAfterExplicitTapOnUninitializedActionWithoutPerson() {
        open(); complete(); assertNull(model.selectedPerson); assertFalse(model.sensitiveSetup)
        model.requestActionProtectionSetup(); assertTrue(model.sensitiveSetup); assertEquals(0, protection.setups)
        val password = "synthetic phrase only".toCharArray(); model.setupSensitive(password, password.copyOf())
        assertEquals(1, protection.setups); assertTrue(password.all { it == '\u0000' })
        protection.setupResult!!(SensitiveResult.Value(Unit)); assertFalse(model.sensitiveSetup); assertTrue(model.workspaceRefreshing)
        complete(SensitiveState.READY); assertEquals(SensitiveResult.Value(SensitiveState.READY), model.workspace!!.leadershipState)
        assertEquals(0, protection.inventories); assertEquals(0, protection.opens)
    }
    @Test fun readyAndPartialDoNotPermitSetup() {
        for (state in listOf(SensitiveState.READY, SensitiveState.PARTIAL_OR_INVALID)) { open(); complete(state); model.requestActionProtectionSetup(); assertFalse(model.sensitiveSetup) }
    }
    @Test fun rejectedMetadataIsNotUninitializedAndEndsLoading() {
        open(); ops.loads.last()(OperationsResult.Value(board)); tasks.loads.last()(OperationsResult.Value(taskBoard))
        protection.states.last().second(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE))
        assertEquals(PreparationContentState.CONTENT, model.preparationContentState); model.requestActionProtectionSetup(); assertFalse(model.sensitiveSetup)
    }
    @Test fun personsAreaReadsOnlyExistingOperationsProjection() {
        open(); complete(); val taskReads = tasks.loads.size; val metadataReads = protection.states.size
        model.preparationArea(PreparationArea.PERSONS); assertNull(model.workspace); assertEquals(PreparationContentState.LOADING, model.preparationContentState)
        ops.loads.last()(OperationsResult.Value(board)); assertEquals(listOf(person.person), model.persons)
        assertEquals(taskReads, tasks.loads.size); assertEquals(metadataReads, protection.states.size); assertEquals(0, people.lists)
        model.personDetail("p"); assertEquals(1, protection.inventories); assertEquals("p", model.selectedPerson!!.id)
    }
    @Test fun roleAreaDoesNotReadTasksOrActionMetadata() {
        open(); complete(); val taskReads = tasks.loads.size; val metadataReads = protection.states.size
        model.preparationArea(PreparationArea.ROLES); ops.loads.last()(OperationsResult.Value(board))
        assertEquals(board, model.operations.board); assertNull(model.tasks.board); assertEquals(taskReads, tasks.loads.size); assertEquals(metadataReads, protection.states.size)
    }
    @Test fun tasksAreaWaitsForTasksAndAssignmentLabelsWithoutMetadataRead() {
        open(); complete(); val metadataReads = protection.states.size; model.preparationArea(PreparationArea.TASKS)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertNull(model.workspace)
        ops.loads.last()(OperationsResult.Value(board)); assertEquals(taskBoard, model.tasks.board); assertEquals(metadataReads, protection.states.size)
    }
    @Test fun emptyAreasRequireSuccessfulLoads() {
        for (area in listOf(PreparationArea.PERSONS, PreparationArea.ROLES, PreparationArea.TASKS)) {
            open(); complete(); model.preparationArea(area); assertEquals(PreparationContentState.LOADING, model.preparationContentState)
            ops.loads.last()(OperationsResult.Value(board.copy(roles = emptyList(), persons = emptyList())))
            if (area == PreparationArea.TASKS) tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = emptyList())))
            assertEquals(PreparationContentState.EMPTY, model.preparationContentState)
        }
    }
    @Test fun missingPlanningProducesOneShortCardLabelNotTechnicalSpam() {
        assertNull(plannedRoles(person.copy(plan = PersonRolePlan(emptySet(), null, editRevision = 0)), board))
        assertEquals("Support", plannedRoles(person, board))
        val other = Role("second", "a", "synthetic role", 0, editRevision = 0)
        assertEquals("synthetic role, Support", plannedRoles(person.copy(plan = PersonRolePlan(setOf("r", "second"), "second", editRevision = 0)), board.copy(roles = board.roles + other)))
    }
    @Test fun refreshKeepsOldContentUntilAllReplacementDataArrive() {
        open(); complete(); val old = model.workspace; model.loadWorkspace(); assertSame(old, model.workspace); assertTrue(model.workspaceRefreshing)
        ops.loads.last()(OperationsResult.Value(board.copy(persons = emptyList()))); tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = emptyList())))
        assertSame(old, model.workspace); protection.states.last().second(SensitiveResult.Value(SensitiveState.READY))
        assertNotSame(old, model.workspace); assertFalse(model.workspaceRefreshing); assertTrue(model.persons.isEmpty())
    }
    @Test fun refreshErrorRetainsValidContentWhileInitialErrorIsNotEmpty() {
        open(); ops.loads.last()(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED)); assertEquals(PreparationContentState.ERROR, model.preparationContentState)
        open(); complete(); val old = model.workspace; model.loadWorkspace(); ops.loads.last()(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED))
        assertSame(old, model.workspace); assertTrue(model.workspaceError); assertEquals(PreparationContentState.CONTENT, model.preparationContentState)
    }
    @Test fun areaSwitchDiscardsPendingOverviewAndFormDrafts() {
        open(); val oldOps = ops.loads.last(); val oldTasks = tasks.loads.last(); val oldState = protection.states.last().second
        model.preparationArea(PreparationArea.PERSONS); ops.loads.last()(OperationsResult.Value(board)); model.addPerson()
        assertTrue(model.personForm); model.preparationArea(PreparationArea.ROLES); assertFalse(model.personForm)
        oldOps(OperationsResult.Value(board)); oldTasks(OperationsResult.Value(taskBoard)); oldState(SensitiveResult.Value(SensitiveState.READY)); assertNull(model.workspace)
        ops.loads.last()(OperationsResult.Value(board)); assertEquals(PreparationArea.ROLES, model.preparationArea)
    }
    @Test fun generationChangeRejectsOldStateWithoutRestoringNavigation() {
        open(); val oldState = protection.states.last().second; val oldOps = ops.loads.last(); val oldTasks = tasks.loads.last()
        session.emit(SessionState.READY, 2); oldOps(OperationsResult.Value(board)); oldTasks(OperationsResult.Value(taskBoard)); oldState(SensitiveResult.Value(SensitiveState.READY))
        assertNull(model.workspace); assertNull(model.selected); assertEquals(PreparationArea.OVERVIEW, model.preparationArea)
    }
    @Test fun lockDropsAreaTaskDraftActionEditorAndPendingMetadata() {
        open(); complete(); model.preparationArea(PreparationArea.TASKS); complete(); model.tasks.add(); model.tasks.change(TaskInput("synthetic draft")); model.edit()
        session.lock(); assertNull(model.workspace); assertFalse(model.tasks.form); assertEquals("", model.tasks.input.title); assertNull(model.editing); assertEquals(PreparationArea.OVERVIEW, model.preparationArea)
    }
    @Test fun explicitStartUsesExistingLifecycleWithoutReadinessBlockers() {
        open(); complete(SensitiveState.PARTIAL_OR_INVALID); model.requestAdvance(); assertEquals(0, actions.transitions)
        model.confirmAdvance(); assertEquals(1, actions.transitions); assertEquals(ActionStatus.ACTIVE, model.selected!!.status); assertNull(model.workspace)
        assertEquals(PreparationArea.OVERVIEW, model.preparationArea); assertFalse(model.preparationAvailable)
    }
    @Test fun synchronousRevokeDuringAreaTapCannotRestoreSelection() {
        open(); complete(); session.onInteraction = { session.lock() }; model.preparationArea(PreparationArea.ROLES)
        assertNull(model.workspace); assertNull(model.selected); assertEquals(PreparationArea.OVERVIEW, model.preparationArea)
    }
    @Test fun staleMetadataFromOtherActionCannotOverwriteNewOverview() {
        open(); val old = protection.states.last().second; model.detail("other"); old(SensitiveResult.Value(SensitiveState.READY))
        assertEquals("other", model.selected!!.id); assertNull(model.workspace); assertEquals("other", protection.states.last().first)
    }
    @Test fun metadataAndRefreshDoNotCountAsUserInteraction() {
        open(); complete(); val interactions = session.interactions
        model.loadWorkspace(); complete(); model.refreshCountdown()
        assertEquals(interactions, session.interactions)
    }
    @Test fun lateSetupResultAfterLockCannotRestoreOverviewOrSetup() {
        open(); complete(); model.requestActionProtectionSetup()
        model.setupSensitive("synthetic phrase only".toCharArray(), "synthetic phrase only".toCharArray())
        val pending = protection.setupResult!!; val reads = protection.states.size
        session.lock(); pending(SensitiveResult.Value(Unit))
        assertNull(model.workspace); assertFalse(model.sensitiveSetup); assertFalse(model.sensitiveBusy)
        assertEquals(reads, protection.states.size)
    }
    @Test fun missingBoardsDoNotInventRolesTasksOrPlanningCounts() {
        val summary = preparationSummary(WorkspaceSnapshot("a", 1, listOf(person.person), null, null, ActionStatus.PREPARATION))
        assertEquals(1, summary.persons); assertNull(summary.roles); assertNull(summary.withoutPrimaryRole); assertNull(summary.open)
    }

    @Test fun closedTasksRemainContentInsteadOfFalseEmpty() {
        open(); complete(); model.preparationArea(PreparationArea.TASKS)
        ops.loads.last()(OperationsResult.Value(board))
        tasks.loads.last()(OperationsResult.Value(taskBoard.copy(tasks = listOf(task.copy(status = TaskStatus.DONE)))))
        assertEquals(PreparationContentState.CONTENT, model.preparationContentState)
        assertTrue(model.tasks.board!!.active.isEmpty()); assertEquals(1, model.tasks.board!!.closed.size)
    }
    @Test fun unsupportedTaskProviderIsErrorNotBlankOrEmptySuccess() {
        val ownSession = Session(); val ownOps = Ops()
        val ownModel = ProductViewModel(ownSession, actions, ProductStorageState.READY_TO_OPEN, people, operationsRepository = ownOps) {}
        ownModel.detail("a"); ownModel.preparationArea(PreparationArea.TASKS)
        ownOps.loads.last()(OperationsResult.Value(board))
        assertEquals(PreparationContentState.ERROR, ownModel.preparationContentState)
    }

    @Test fun revokeDuringAreaCleanupCannotRestoreRequestedArea() {
        open(); complete(); protection.onRevoke = { session.lock() }
        model.preparationArea(PreparationArea.ROLES)
        assertEquals(SessionState.LOCKED, model.snapshot.state); assertNull(model.workspace)
        assertEquals(PreparationArea.OVERVIEW, model.preparationArea)
    }
    @Test fun revokeDuringSetupCleanupCannotOpenPassphraseForm() {
        open(); complete(); protection.onRevoke = { session.lock() }
        model.requestActionProtectionSetup()
        assertEquals(SessionState.LOCKED, model.snapshot.state); assertFalse(model.sensitiveSetup)
        assertNull(model.workspace); assertEquals(0, protection.setups)
    }

}
