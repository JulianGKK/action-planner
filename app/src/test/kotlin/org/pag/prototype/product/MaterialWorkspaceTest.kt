package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Real ProductViewModel; deliberately deferred reads and failing foreign mutation ports. */
class MaterialWorkspaceTest {
    private class Session : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1); var observer: ((SessionSnapshot) -> Unit)? = null
        var interactions = 0; var onInteraction: () -> Unit = {}
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription {} }
        override fun authenticate(experiment: Experiment) { error("No material auth") }
        override fun testAccess() {}
        override fun userInteraction() { interactions++; onInteraction() }
        override fun lock() { emit(SessionState.LOCKED) }
        fun emit(state: SessionState) { value = SessionSnapshot(state, value.generation + 1); observer!!(value) }
    }
    private class Actions : ActionRepository {
        var row = Action("a", "synthetic action", "synthetic", 1, 2, 0, ActionStatus.ACTIVE)
        var writes = 0
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row.copy(id = id))
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { writes++; error("No P7 action create") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { writes++; error("No P7 action edit") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            row = row.copy(status = ActionLifecycle.next(expected)!!, completedAt = 10L.takeIf { expected == ActionStatus.ACTIVE })
            publish(ActionChangeResult.Applied(row))
        }
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { error("Existing operations board supplies people") }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) { error("No P7 person detail read") }
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P7 person create") }
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P7 person edit") }
    }
    private class Ops : OperationsRepository {
        val reads = mutableListOf<(OperationsResult<OperationalBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { reads += publish }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P7 role write") }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P7 role write") }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 role write") }
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 plan write") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 snapshot write") }
    }
    private class Tasks : TaskRepository {
        val reads = mutableListOf<(OperationsResult<TaskBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) { reads += publish }
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P7 task create") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P7 task edit") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No P7 task transition") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 task delete") }
    }
    private class Protection : LeadershipRepository {
        var payloadReads = 0
        override fun actionState(actionId: String, publish: (SensitiveResult<SensitiveState>) -> Unit) = publish(SensitiveResult.Value(SensitiveState.READY))
        override fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit) { payloadReads++; error("No P7 P3b inventory") }
        override fun setup(actionId: String, passphrase: CharArray, confirmation: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P7 setup") }
        override fun open(actionId: String, personId: String, area: SensitiveArea, passphrase: CharArray, publish: (SensitiveResult<SensitiveOpened>) -> Unit) { payloadReads++; error("No P7 P3b open") }
        override fun save(grant: SensitiveGrant, data: SensitiveData, publish: (SensitiveResult<Unit>) -> Unit) { error("No P7 P3b write") }
        override fun deleteArea(grant: SensitiveGrant, publish: (SensitiveResult<Unit>) -> Unit) { error("No P7 P3b delete") }
        override fun deletePerson(actionId: String, personId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P7 person delete") }
        override fun deleteAction(actionId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P7 action delete") }
        override fun revoke() {}
    }
    private val session = Session(); private val actions = Actions(); private val ops = Ops(); private val tasks = Tasks()
    private val protection = Protection(); private val port = MaterialFake(); private val protocol = ProtocolFake()
    private val model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, People(), protection,
        ops, tasks, protocol, port) {}
    private val person = Person("p", "a", "Motte", 0)
    private val opBoard get() = OperationalBoard("a", actions.row.status, emptyList(), listOf(OperationalPerson(person, PersonRolePlan(emptySet(), null), null)))
    private val task = Task("t", "a", "synthetic task", TaskPriority.NORMAL, TaskStatus.DONE, null, null, null, null, null, 0, 0, 0)
    private val taskBoard = TaskBoard("a", listOf(task, task.copy(id = "cancelled", status = TaskStatus.CANCELLED, completedAt = null)), emptyList())
    private val item = MaterialItem("m", "a", "MATERIAL-SENTINEL-NAME", "MATERIAL-SENTINEL-QTY", "MATERIAL-SENTINEL-PLACE", "p", "t", "GLUE", 1)
    private fun open(phase: ActionStatus = ActionStatus.ACTIVE) { actions.row = actions.row.copy(status = phase); model.detail("a") }
    private fun complete() {
        ops.reads.last()(OperationsResult.Value(opBoard)); tasks.reads.lastOrNull()?.invoke(OperationsResult.Value(taskBoard))
        port.summaryCallback?.invoke(OperationsResult.Value(MaterialSummary(1)))
    }
    private fun material(phase: ActionStatus = ActionStatus.ACTIVE) {
        open(phase); complete()
        when (phase) {
            ActionStatus.PREPARATION -> { model.preparationArea(PreparationArea.MATERIAL); complete() }
            ActionStatus.ACTIVE -> { model.activeArea(ActiveArea.MATERIAL); complete() }
            else -> model.openCompletedMaterial()
        }
        port.boardCallback!!(OperationsResult.Value(MaterialBoard("a", listOf(item))))
    }
    @Test fun activeHubWaitsForCountWithoutLoadingMaterialBoard() {
        open(); ops.reads.last()(OperationsResult.Value(opBoard)); tasks.reads.last()(OperationsResult.Value(taskBoard))
        assertNull(model.workspace); assertEquals(1, port.summaryReads); assertEquals(0, port.boardReads)
        port.summaryCallback!!(OperationsResult.Value(MaterialSummary(7)))
        val cards = activeHubCards(actions.row, opBoard, taskBoard, model.workspace!!.protocolSummary, model.workspace!!.materialSummary)
        assertEquals(ActiveArea.entries.drop(1).filter { it != ActiveArea.COSTS && it != ActiveArea.MEMORY_REPORTS }, cards.map { it.area })
        assertEquals(listOf("7 Materialeinträge"), cards.single { it.area == ActiveArea.MATERIAL }.summary)
        assertFalse(cards.toString().contains("MATERIAL-SENTINEL")); assertEquals(0, port.boardReads)
    }
    @Test fun summaryRefreshIsAtomicAndErrorKeepsOldCountInsteadOfZero() {
        open(); complete(); val old = model.workspace
        model.loadWorkspace(); ops.reads.last()(OperationsResult.Value(opBoard.copy(persons = emptyList())))
        tasks.reads.last()(OperationsResult.Value(taskBoard.copy(tasks = emptyList())))
        assertSame(old, model.workspace)
        port.summaryCallback!!(OperationsResult.Rejected(OperationsFailure.NOT_FOUND)); assertSame(old, model.workspace); assertTrue(model.workspaceError)
        model.back(); open(); port.summaryCallback!!(OperationsResult.Rejected(OperationsFailure.NOT_FOUND)); assertNull(model.workspace)
    }
    @Test fun preparationOverviewCountsOnlyAndMaterialLoadsBoardSeparately() {
        open(ActionStatus.PREPARATION); complete(); assertEquals(MaterialSummary(1), model.workspace!!.materialSummary)
        assertEquals(0, port.boardReads); model.preparationArea(PreparationArea.MATERIAL); complete()
        assertEquals(1, port.boardReads); assertTrue(model.material.loading); assertNull(model.material.board)
        assertEquals(taskBoard.tasks, model.workspace!!.tasks!!.tasks)
    }
    @Test fun completedHasZeroMaterialReadsUntilExplicitTapAndNoWrites() {
        open(ActionStatus.COMPLETED); complete(); model.refreshCountdown(); model.loadWorkspace(); complete()
        assertEquals(0, port.summaryReads); assertEquals(0, port.boardReads); assertEquals(0, port.writes)
        model.openCompletedMaterial(); assertTrue(model.completedMaterialOpen); assertEquals(1, port.boardReads)
        port.boardCallback!!(OperationsResult.Value(MaterialBoard("a", listOf(item))))
        model.material.create(); model.material.edit(item); model.material.save(); model.material.requestDelete(); model.material.delete()
        assertFalse(model.material.writable); assertNull(model.material.input); assertEquals(0, port.writes)
        model.closeCompletedMaterial(); assertFalse(model.completedMaterialOpen); assertNull(model.material.board)
    }
    @Test fun everyAreaSwitchDiscardsBoardDraftExpectedConfirmationAndLateReads() {
        for (phase in listOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE)) {
            for (context in listOf("list", "create", "edit", "delete")) {
                material(phase)
                when (context) {
                    "create" -> model.material.create(MaterialRules.templates.first())
                    "edit", "delete" -> { model.material.edit(item); if (context == "delete") model.material.requestDelete() }
                }
                val late = port.boardCallback!!
                if (phase == ActionStatus.PREPARATION) model.preparationArea(PreparationArea.PERSONS) else model.activeArea(ActiveArea.TASKS)
                assertNull(model.material.board); assertNull(model.material.input); assertNull(model.material.expected); assertFalse(model.material.confirmingDelete)
                late(OperationsResult.Value(MaterialBoard("a", listOf(item)))); assertNull(model.material.board)
            }
        }
    }
    @Test fun materialDraftNeverCrossesLifecycleOrAutoOpensCompleted() {
        for (phase in listOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE)) {
            material(phase); model.material.create(MaterialRules.templates.first()); model.requestAdvance(); model.confirmAdvance(); complete()
            assertNull(model.material.input); assertNull(model.material.expected); assertNull(model.material.board)
            assertFalse(model.completedMaterialOpen); model.material.save(); assertEquals(0, port.writes)
        }
    }
    @Test fun lockFromListFormEditAndConfirmationRejectsLateWriteAndReauthRestore() {
        for (phase in listOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)) {
            for (context in if (phase == ActionStatus.COMPLETED) listOf("list") else listOf("list", "create", "edit", "delete", "saving")) {
                material(phase)
                when (context) {
                    "create" -> model.material.create(MaterialRules.templates.first())
                    "edit", "delete", "saving" -> { model.material.edit(item); if (context == "delete") model.material.requestDelete(); if (context == "saving") model.material.save() }
                }
                val read = port.boardCallback!!; val write = port.saved
                session.lock(); read(OperationsResult.Value(MaterialBoard("a", listOf(item)))); write?.invoke(OperationsResult.Value(item))
                assertNull(model.material.board); assertNull(model.material.input); assertNull(model.material.expected); assertNull(model.feedback)
                assertFalse(model.material.busy); assertFalse(model.completedMaterialOpen)
                session.emit(SessionState.READY); assertNull(model.selected); assertFalse(model.material.available)
            }
        }
    }
    @Test fun actionAndDeletionPendingProjectionRemoveMaterialContext() {
        material(); model.material.edit(item); val late = port.boardCallback!!
        model.detail("other"); late(OperationsResult.Value(MaterialBoard("a", listOf(item)))); assertNull(model.material.board); assertNull(model.material.input)
        material(ActionStatus.COMPLETED); actions.row = actions.row.copy(status = ActionStatus.DELETION_PENDING); model.detail("a")
        model.openCompletedMaterial(); assertFalse(model.completedMaterialOpen); assertFalse(model.material.available); assertNull(model.material.board)
    }
    @Test fun successfulWriteReloadsRepositoryCountWithNoForeignMutationOrInteraction() {
        material(); model.material.create(MaterialRules.templates.first()); model.material.save(); val interactions = session.interactions
        port.saved!!(OperationsResult.Value(item)); val old = model.workspace
        ops.reads.last()(OperationsResult.Value(opBoard)); tasks.reads.last()(OperationsResult.Value(taskBoard)); assertSame(old, model.workspace)
        port.summaryCallback!!(OperationsResult.Value(MaterialSummary(9))); assertEquals(MaterialSummary(9), model.workspace!!.materialSummary)
        assertEquals(interactions, session.interactions); assertEquals(0, actions.writes); assertEquals(0, protection.payloadReads)
    }
    @Test fun completedMaterialAndProtocolDoNotOverlap() {
        material(ActionStatus.COMPLETED); model.openCompletedProtocol(); assertFalse(model.completedMaterialOpen); assertNull(model.material.board)
        assertTrue(model.completedProtocolOpen); model.openCompletedMaterial(); assertFalse(model.completedProtocolOpen); assertNull(model.protocol.timeline)
        assertTrue(model.completedMaterialOpen)
    }
    @Test fun missingPortAndForbiddenPhasesNeverExposeMaterialArea() {
        val noPort = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, People(), operationsRepository = ops, taskRepository = tasks) {}
        noPort.detail("a"); noPort.activeArea(ActiveArea.MATERIAL); assertEquals(ActiveArea.HUB, noPort.activeArea)
        for (phase in listOf(ActionStatus.DRAFT, ActionStatus.DELETION_PENDING)) {
            open(phase); val reads = port.boardReads; model.preparationArea(PreparationArea.MATERIAL); model.activeArea(ActiveArea.MATERIAL); model.openCompletedMaterial()
            assertEquals(reads, port.boardReads); assertFalse(model.material.available)
        }
    }
    @Test fun synchronousRevokingTapCannotOpenCompletedMaterial() {
        open(ActionStatus.COMPLETED); complete(); session.onInteraction = { session.lock() }; model.openCompletedMaterial()
        assertFalse(model.completedMaterialOpen); assertEquals(0, port.boardReads); assertNull(model.material.board)
    }
}
