package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Real ProductViewModel; deliberately deferred reads and failing foreign mutation ports. */
class ExpenseWorkspaceTest {
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
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { writes++; error("No P8 action create") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { writes++; error("No P8 action edit") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            row = row.copy(status = ActionLifecycle.next(expected)!!, completedAt = 10L.takeIf { expected == ActionStatus.ACTIVE })
            publish(ActionChangeResult.Applied(row))
        }
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { error("Existing operations board supplies people") }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) { error("No P8 person detail read") }
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P8 person create") }
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P8 person edit") }
    }
    private class Ops : OperationsRepository {
        val reads = mutableListOf<(OperationsResult<OperationalBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { reads += publish }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P8 role write") }
        override fun renameRole(actionId: String, expected: Role, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P8 role write") }
        override fun deleteRole(actionId: String, expected: Role, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 role write") }
        override fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 plan write") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 snapshot write") }
    }
    private class Tasks : TaskRepository {
        val reads = mutableListOf<(OperationsResult<TaskBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) { reads += publish }
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P8 task create") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P8 task edit") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No P8 task transition") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 task delete") }
    }
    private class Protection : LeadershipRepository {
        var payloadReads = 0
        override fun actionState(actionId: String, publish: (SensitiveResult<SensitiveState>) -> Unit) = publish(SensitiveResult.Value(SensitiveState.READY))
        override fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit) { payloadReads++; error("No P8 P3b inventory") }
        override fun setup(actionId: String, passphrase: CharArray, confirmation: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P8 setup") }
        override fun open(actionId: String, personId: String, area: SensitiveArea, passphrase: CharArray, publish: (SensitiveResult<SensitiveOpened>) -> Unit) { payloadReads++; error("No P8 P3b open") }
        override fun save(grant: SensitiveGrant, data: SensitiveData, publish: (SensitiveResult<Unit>) -> Unit) { error("No P8 P3b write") }
        override fun deleteArea(grant: SensitiveGrant, publish: (SensitiveResult<Unit>) -> Unit) { error("No P8 P3b delete") }
        override fun deletePerson(actionId: String, personId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P8 person delete") }
        override fun deleteAction(actionId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P8 action delete") }
        override fun revoke() {}
    }
    private val session=Session();private val actions=Actions();private val ops=Ops();private val tasks=Tasks();private val protection=Protection()
    private val port=ExpenseFake();private val material=MaterialFake();private val protocol=ProtocolFake()
    private val model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,People(),protection,ops,tasks,protocol,material,port) {}
    private val item=Expense("e","a","TITLE-SENTINEL",100,listOf(ExpensePayment("PAYER-SENTINEL",100)),1)
    private fun complete(){ops.reads.last()(OperationsResult.Value(OperationalBoard("a",actions.row.status,emptyList(),emptyList())));tasks.reads.lastOrNull()?.invoke(OperationsResult.Value(TaskBoard("a",emptyList(),emptyList())));material.summaryCallback?.invoke(OperationsResult.Value(MaterialSummary(0)));port.summaryCallback?.invoke(OperationsResult.Value(ExpenseSummary(1,100)))}
    private fun open(s:ActionStatus=ActionStatus.ACTIVE){actions.row=actions.row.copy(status=s);model.detail("a");complete()}
    private fun costs(s:ActionStatus=ActionStatus.ACTIVE){open(s);when(s){ActionStatus.PREPARATION->model.preparationArea(PreparationArea.COSTS);ActionStatus.ACTIVE->model.activeArea(ActiveArea.COSTS);else->model.openCompletedExpenses()};complete();port.boardCallback!!(OperationsResult.Value(ExpenseBoard("a",listOf(item))))}
    @Test fun hubWaitsAtomicallyForExpenseSummaryAndNeverBoard(){model.detail("a");ops.reads.last()(OperationsResult.Value(OperationalBoard("a",ActionStatus.ACTIVE,emptyList(),emptyList())));tasks.reads.last()(OperationsResult.Value(TaskBoard("a",emptyList(),emptyList())));material.summaryCallback!!(OperationsResult.Value(MaterialSummary(0)));assertNull(model.workspace);assertEquals(0,port.boardReads);port.summaryCallback!!(OperationsResult.Value(ExpenseSummary(2,12000)));val cards=activeHubCards(actions.row,model.workspace!!.operations!!,model.workspace!!.tasks!!,model.workspace!!.protocolSummary,model.workspace!!.materialSummary,model.workspace!!.expenseSummary);assertEquals(ActiveArea.entries.drop(1).filter { it != ActiveArea.MEMORY_REPORTS },cards.map{it.area});assertEquals(listOf("2 Einträge","Gesamt: 120,00 €"),cards.single{it.area==ActiveArea.COSTS}.summary);assertFalse(cards.toString().contains("SENTINEL"));assertEquals(0,port.boardReads)}
    @Test fun summaryRefreshErrorRetainsOldSnapshotAndNeverInventsZero(){open();val old=model.workspace;model.loadWorkspace();ops.reads.last()(OperationsResult.Value(OperationalBoard("a",ActionStatus.ACTIVE,emptyList(),emptyList())));tasks.reads.last()(OperationsResult.Value(TaskBoard("a",emptyList(),emptyList())));material.summaryCallback!!(OperationsResult.Value(MaterialSummary(0)));port.summaryCallback!!(OperationsResult.Rejected(OperationsFailure.NOT_FOUND));assertSame(old,model.workspace);assertTrue(model.workspaceError)}
    @Test fun preparationCountsOnlyThenConsciouslyLoadsBoard(){open(ActionStatus.PREPARATION);assertEquals(ExpenseSummary(1,100),model.workspace!!.expenseSummary);assertEquals(0,port.boardReads);model.preparationArea(PreparationArea.COSTS);assertEquals(1,port.boardReads)}
    @Test fun normalCompletedZeroReadsAndExplicitCostExceptionDoesNotOpenOtherWrites(){open(ActionStatus.COMPLETED);model.loadWorkspace();complete();assertEquals(0,port.summaryReads);assertEquals(0,port.boardReads);assertEquals(0,port.writes);model.openCompletedExpenses();port.boardCallback!!(OperationsResult.Value(ExpenseBoard("a",listOf(item))));assertTrue(model.expenses.writable);model.expenses.edit(item);model.expenses.save();assertEquals(1,port.writes);assertFalse(model.tasks.writable);assertFalse(model.material.writable);assertFalse(model.operations.live);assertFalse(model.operations.planning);model.tasks.add();model.material.create();model.edit();assertNull(model.editing);assertEquals(0,material.writes);assertEquals(0,protection.payloadReads);assertEquals(0,actions.writes)}
    @Test fun activeDraftDiscardedOnCompletionWithoutAutomaticFollowup(){costs();model.expenses.create();model.expenses.change(ExpenseDraft.from(item));model.requestAdvance();model.confirmAdvance();complete();assertEquals(ActionStatus.COMPLETED,model.selected!!.status);assertNull(model.expenses.draft);assertNull(model.expenses.board);assertFalse(model.completedExpensesOpen);model.expenses.save();assertEquals(0,port.writes);model.openCompletedExpenses();assertTrue(model.completedExpensesOpen)}
    @Test fun areaSwitchesClearEveryContextAndDropLateCallbacks(){for(s in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE)){for(c in listOf("list","create","edit","delete")){costs(s);when(c){"create"->model.expenses.create();"edit","delete"->{model.expenses.edit(item);if(c=="delete")model.expenses.requestDelete()}};val late=port.boardCallback!!;if(s==ActionStatus.ACTIVE)model.activeArea(ActiveArea.MATERIAL) else model.preparationArea(PreparationArea.PERSONS);late(OperationsResult.Value(ExpenseBoard("a",listOf(item))));assertNull(model.expenses.board);assertNull(model.expenses.draft);assertNull(model.expenses.expected);assertFalse(model.expenses.confirmingDelete)}}}
    @Test fun completedAreasAreMutuallyExclusive(){costs(ActionStatus.COMPLETED);model.expenses.create();model.openCompletedMaterial();assertNull(model.expenses.draft);assertFalse(model.completedExpensesOpen);model.openCompletedExpenses();assertNull(model.material.board);assertFalse(model.completedMaterialOpen);model.openCompletedProtocol();assertFalse(model.completedExpensesOpen);assertNull(model.expenses.board)}
    @Test fun lockMatrixAndReauthNeverRestoreCosts(){for(s in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)){for(c in listOf("list","create","edit","delete","saving")){costs(s);when(c){"create"->model.expenses.create();"edit","delete","saving"->{model.expenses.edit(item);if(c=="delete")model.expenses.requestDelete();if(c=="saving")model.expenses.save()}};val read=port.boardCallback!!;val write=port.saved;session.lock();read(OperationsResult.Value(ExpenseBoard("a",listOf(item))));write?.invoke(OperationsResult.Value(item));assertNull(model.expenses.board);assertNull(model.expenses.draft);assertNull(model.expenses.expected);assertFalse(model.expenses.busy);assertNull(model.feedback);session.emit(SessionState.READY);assertNull(model.selected);assertFalse(model.completedExpensesOpen)}}}
    @Test fun actionAndDeletionPendingClearAndForbiddenNoPath(){costs();model.expenses.create();actions.row=actions.row.copy(status=ActionStatus.DELETION_PENDING);model.detail("b");assertNull(model.expenses.draft);assertNull(model.expenses.board);assertFalse(model.completedExpensesOpen);model.openCompletedExpenses();assertEquals(0,port.writes);actions.row=actions.row.copy(status=ActionStatus.DRAFT);model.detail("a");model.openCompletedExpenses();assertFalse(model.completedExpensesOpen)}
    @Test fun successRefreshesServerSummaryAndNoForeignMutationsOrCallbackInteractions(){costs();model.expenses.edit(item);model.expenses.save();val n=session.interactions;port.saved!!(OperationsResult.Value(item));ops.reads.last()(OperationsResult.Value(OperationalBoard("a",ActionStatus.ACTIVE,emptyList(),emptyList())));tasks.reads.last()(OperationsResult.Value(TaskBoard("a",emptyList(),emptyList())));port.boardCallback!!(OperationsResult.Value(ExpenseBoard("a",listOf(item))));assertEquals(n,session.interactions);model.activeArea(ActiveArea.HUB);complete();port.summaryCallback!!(OperationsResult.Value(ExpenseSummary(3,24500)));assertEquals(ExpenseSummary(3,24500),model.workspace!!.expenseSummary);assertEquals(0,material.writes);assertEquals(0,actions.writes);assertEquals(0,protection.payloadReads)}
}
