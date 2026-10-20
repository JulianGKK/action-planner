package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class CompletedProtocolWorkspaceTest {
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
        var row = Action("a", "synthetic action", "synthetic description", 1, 2, 0, ActionStatus.COMPLETED)
        var transitions = 0; var edits = 0
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row.copy(id = id))
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { edits++ }
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
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
    }
    private class Tasks : TaskRepository {
        val loads = mutableListOf<(OperationsResult<TaskBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) { loads.add(publish) }
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
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
    private val protocolDelegate = ProtocolFake()
    private val model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, people, protection, ops, tasks, protocolDelegate) {}
    private val person = OperationalPerson(Person("p", "a", "synthetic", 0), PersonRolePlan(setOf("r"), "r"), null)
    private val board = OperationalBoard("a", ActionStatus.ACTIVE, listOf(Role("r", "a", "Support", 0)), listOf(person))
    private val task = Task("t", "a", "synthetic task", TaskPriority.HIGH, TaskStatus.OPEN, null, "r", null, null, null, 0, 0, null)
    private val taskBoard = TaskBoard("a", listOf(task), emptyList())
    private fun open() = model.detail("a")
    private fun complete() {
        ops.loads.last()(OperationsResult.Value(board.copy(status = actions.row.status)))
        if (model.activeArea != ActiveArea.FUNCTIONS) tasks.loads.last()(OperationsResult.Value(taskBoard))
    }

    @Test fun completedPageHasNoProtocolReadsUntilExplicitTapAndKeepsValidPeopleSnapshot() {
        open(); complete(); val before = model.workspace
        assertEquals(0, protocolDelegate.timelineReads); assertEquals(0, protocolDelegate.detailReads); assertEquals(0, protocolDelegate.summaryReads)
        model.openCompletedProtocol(); assertTrue(model.completedProtocolOpen); assertSame(before, model.workspace)
        assertEquals(1, protocolDelegate.timelineReads); assertEquals(0, protocolDelegate.detailReads)
        model.closeCompletedProtocol(); assertFalse(model.completedProtocolOpen); assertNull(model.protocol.timeline)
    }
    @Test fun completedP6WorksButOtherModelsAndActionRemainReadOnly() {
        open(); complete(); model.openCompletedProtocol(); model.protocol.create("POLICE_ARRIVED"); model.protocol.save()
        assertEquals(ActionStatus.COMPLETED, protocolDelegate.capturedStatus); assertEquals(1, protocolDelegate.writes)
        assertFalse(model.tasks.writable); assertFalse(model.operations.live); assertFalse(model.operations.planning)
        model.tasks.add(); model.tasks.open(task); model.tasks.edit(); model.tasks.transition(TaskStatus.DONE); model.tasks.delete()
        model.operations.createRole("synthetic"); model.operations.plan("p"); model.operations.update("p"); model.operations.saveLive(); model.operations.savePlan()
        model.edit(); model.requestAdvance(); model.confirmAdvance()
        assertNull(model.editing); assertNull(model.confirming); assertEquals(0, actions.edits); assertEquals(0, actions.transitions)
        assertEquals(ActionStatus.COMPLETED, model.selected!!.status)
    }
    @Test fun activeDraftIsClearedByActualLifecycleAndFollowUpRequiresFreshTap() {
        actions.row = actions.row.copy(status = ActionStatus.ACTIVE); open(); complete()
        model.activeArea(ActiveArea.PROTOCOL); complete(); model.protocol.create("POLICE_ARRIVED")
        assertTrue(model.protocol.form); model.requestAdvance(); model.confirmAdvance(); complete()
        assertEquals(ActionStatus.COMPLETED, model.selected!!.status); assertFalse(model.protocol.form)
        assertNull(model.protocol.expected); assertFalse(model.completedProtocolOpen)
        model.protocol.save(); assertEquals(0, protocolDelegate.writes)
        model.openCompletedProtocol(); assertTrue(model.completedProtocolOpen); assertNull(model.protocol.input)
    }
    @Test fun lockReauthAndActionChangeRemoveFollowUpAndLatePublication() {
        open(); complete(); model.openCompletedProtocol(); val late = protocolDelegate.timelineCallback!!
        model.protocol.create("POLICE_ARRIVED"); session.lock(); late(ProtocolResult.Value(emptyList()))
        assertFalse(model.completedProtocolOpen); assertNull(model.protocol.input); assertNull(model.protocol.timeline)
        session.emit(SessionState.READY, 3); open(); complete(); assertFalse(model.completedProtocolOpen)
        model.openCompletedProtocol(); val old = protocolDelegate.timelineCallback!!
        model.detail("b"); old(ProtocolResult.Value(emptyList()))
        assertFalse(model.completedProtocolOpen); assertNull(model.protocol.timeline)
    }
    @Test fun openingRejectsSynchronousInteractionAndCleanupRevoke() {
        open(); complete(); session.onInteraction = { session.lock() }; model.openCompletedProtocol()
        assertFalse(model.completedProtocolOpen); assertEquals(0, protocolDelegate.timelineReads)
        session.onInteraction = {}; session.emit(SessionState.READY, 3); open(); complete()
        protection.onRevoke = { session.lock() }; model.openCompletedProtocol()
        assertFalse(model.completedProtocolOpen); assertEquals(0, protocolDelegate.timelineReads)
    }

}
