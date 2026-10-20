package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Real ProductViewModel; deliberately deferred reads and failing foreign mutation ports. */
class MemoryReportWorkspaceTest {
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
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P8 role write") }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 role write") }
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 plan write") }
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
    private val memory=MemoryFake().apply{delaySummary=true};private val expense=ExpenseFake();private val material=MaterialFake();private val protocol=ProtocolFake()
    private val model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,People(),protection,ops,tasks,protocol,material,expense,memory) {}
    private fun sources(){ops.reads.last()(OperationsResult.Value(OperationalBoard("a",actions.row.status,emptyList(),emptyList())));tasks.reads.lastOrNull()?.invoke(OperationsResult.Value(TaskBoard("a",emptyList(),emptyList())));material.summaryCallback?.invoke(OperationsResult.Value(MaterialSummary(0)));expense.summaryCallback?.invoke(OperationsResult.Value(ExpenseSummary(0,0)))}
    private fun open(phase:ActionStatus=ActionStatus.ACTIVE){actions.row=actions.row.copy(status=phase);model.detail("a");sources();memory.summaryCallback?.invoke(OperationsResult.Value(MemoryReportSummary(0,0)))}
    @Test fun atomicHubWaitsForP9SummaryAndReadsNoContent(){model.detail("a");sources();assertNull(model.workspace);assertEquals(0,memory.lists);assertEquals(0,memory.details);memory.summaryCallback!!(OperationsResult.Value(MemoryReportSummary(1,2)));val w=model.workspace!!;val cards=activeHubCards(actions.row,w.operations!!,w.tasks!!,w.protocolSummary,w.materialSummary,w.expenseSummary,w.memoryReportSummary);assertEquals(ActiveArea.entries.drop(1),cards.map{it.area});assertEquals(listOf("1 Entwürfe · 2 abgeschlossen"),cards.single{it.area==ActiveArea.MEMORY_REPORTS}.summary);assertFalse(cards.toString().contains("Motte"));assertEquals(0,memory.lists)}
    @Test fun refreshErrorRetainsCompleteSnapshot(){open();val old=model.workspace;model.loadWorkspace();sources();memory.summaryCallback!!(OperationsResult.Rejected(OperationsFailure.NOT_FOUND));assertSame(old,model.workspace);assertTrue(model.workspaceError)}
    @Test fun completedNormalHasZeroP9ReadsUntilTapAndOtherRulesStayClosed(){open(ActionStatus.COMPLETED);model.loadWorkspace();sources();assertEquals(0,memory.summaries);assertEquals(0,memory.lists);assertEquals(0,memory.details);assertEquals(0,memory.writes);model.openCompletedMemoryReports();assertTrue(model.completedMemoryReportsOpen);model.memoryReports.create();model.memoryReports.changeAuthor("Motte");model.memoryReports.save();assertEquals(1,memory.writes);assertEquals(ActionStatus.COMPLETED,model.selected!!.status);assertFalse(model.tasks.writable);assertFalse(model.material.writable);assertFalse(model.operations.live);model.edit();assertNull(model.editing);assertEquals(0,protection.payloadReads);assertEquals(0,actions.writes);assertEquals(0,material.writes)}
    @Test fun activeToCompletedClearsRamThenExplicitlyResumesPersistedDraft(){open();model.activeArea(ActiveArea.MEMORY_REPORTS);sources();model.memoryReports.create();model.memoryReports.changeAuthor("Motte");model.memoryReports.save();model.memoryReports.changeAnswer("B_ENDING_MODE",MemoryAnswerValue.Multi(listOf("SELF_LEFT")));val count=memory.summaries;model.requestAdvance();model.confirmAdvance();sources();assertNull(model.memoryReports.detail);assertEquals("",model.memoryReports.authorLabel);assertFalse(model.completedMemoryReportsOpen);assertNotNull(memory.stored);assertEquals(count,memory.summaries);model.openCompletedMemoryReports();model.memoryReports.open("r");assertEquals(1,model.memoryReports.sectionOrdinal);assertTrue(model.memoryReports.answers.isEmpty())}
    @Test fun crossAreaAsksDiscardAndLockClearsLatePublication(){open();model.activeArea(ActiveArea.MEMORY_REPORTS);sources();model.memoryReports.create();model.memoryReports.changeAuthor("Motte");model.activeArea(ActiveArea.TASKS);assertTrue(model.memoryReports.confirmingDiscard);model.memoryReports.confirmDiscard();assertEquals(ActiveArea.TASKS,model.activeArea);assertNull(model.memoryReports.headers);model.activeArea(ActiveArea.MEMORY_REPORTS);sources();model.memoryReports.create();model.memoryReports.changeAuthor("Motte");memory.delay=true;model.memoryReports.save();val cb=memory.pending!!;session.lock();cb(OperationsResult.Value(memory.stored!!.header));assertNull(model.memoryReports.detail);assertNull(model.memoryReports.headers);assertNull(model.feedback);session.emit(SessionState.READY);assertNull(model.selected);assertFalse(model.completedMemoryReportsOpen)}
    @Test fun writeRefreshesOnlyServerSummaryAndCallbacksAreNotInteractions(){open();model.activeArea(ActiveArea.MEMORY_REPORTS);sources();model.memoryReports.create();model.memoryReports.changeAuthor("Motte");memory.delay=true;model.memoryReports.save();val interactions=session.interactions;memory.pending!!(OperationsResult.Value(memory.stored!!.header));sources();assertEquals(interactions,session.interactions);model.activeArea(ActiveArea.HUB);sources();memory.summaryCallback!!(OperationsResult.Value(MemoryReportSummary(3,4)));assertEquals(MemoryReportSummary(3,4),model.workspace!!.memoryReportSummary);assertEquals(0,protection.payloadReads)}
    @Test fun deletionPendingDropsOpenReportAndLateCallbacks() {
        open();model.activeArea(ActiveArea.MEMORY_REPORTS);sources();model.memoryReports.create();model.memoryReports.changeAuthor("Motte");model.memoryReports.save()
        val late=memory.detailCallback!!;val saved=memory.stored!!
        actions.row=actions.row.copy(status=ActionStatus.DELETION_PENDING);model.detail("a");late(OperationsResult.Value(saved))
        assertFalse(model.memoryReports.available);assertNull(model.memoryReports.detail);assertNull(model.memoryReports.headers);assertEquals("",model.memoryReports.authorLabel)
    }
    @Test fun noPreparationDraftDeletionPath(){for(s in listOf(ActionStatus.PREPARATION,ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)){open(s);model.activeArea(ActiveArea.MEMORY_REPORTS);model.openCompletedMemoryReports();assertFalse(model.memoryReports.available)};assertEquals(0,memory.summaries);assertEquals(0,memory.lists)}
}
