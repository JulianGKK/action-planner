package org.pag.prototype.product

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Real ProductViewModel; deliberately deferred reads and failing foreign mutation ports. */
class DocumentWorkspaceTest {
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
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { publish(PersonResult.Value(emptyList())) }
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

    private class Documents:DocumentRepository {
        var summaries=0;var lists=0
        var pending:((DocumentResult<DocumentSummary>)->Unit)?=null
        override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit) { summaries++;pending=publish }
        override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit) { lists++;publish(DocumentResult.Value(emptyList())) }
        override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit) { error("No implicit import") }
        override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit) { error("No implicit delete") }
    }
    private val handoff=PendingDocumentPicker<String>()
    private val picker=object:DocumentPicker {
        override fun launch(actionId:String,generation:Long) {
            handoff.begin(actionId,generation)
            session.lock()
        }
        override fun available(actionId:String,generation:Long)=handoff.available(actionId,generation)
        override fun consume(actionId:String,generation:Long):DocumentSource?=null
        override fun cancel() { handoff.clear() }
    }
    private val session=Session();private val actions=Actions();private val docs=Documents();private val ops=Ops();private val tasks=Tasks()
    private fun model(status:ActionStatus):ProductViewModel {
        actions.row=actions.row.copy(status=status)
        return ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,People(),operationsRepository=ops,taskRepository=tasks,begin={}).also {
            it.bindDocuments(docs,picker);it.detail("a")
        }
    }
    private fun supplyWorkspace() {
        ops.reads.last()(OperationsResult.Value(OperationalBoard("a",actions.row.status,emptyList(),emptyList())))
        if(tasks.reads.isNotEmpty())tasks.reads.last()(OperationsResult.Value(TaskBoard("a",emptyList(),emptyList())))
    }
    @Test fun documentEntryAndExitInvalidateTheObservedScreenRoute() {
        for(status in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)) {
            val m=model(status)
            val observer=SnapshotStateObserver { it() }
            val scope=Any()
            var invalidations=0
            observer.start()
            try {
                // ProductScreen observes this route before DocumentWorkspace is composed.
                observer.observeReads(scope,{ invalidations++ }) { assertFalse(m.documents.available) }
                m.openDocuments()
                assertTrue(m.documents.available)
                Snapshot.sendApplyNotifications()
                assertTrue("Opening documents must invalidate the screen route",invalidations>0)

                invalidations=0
                observer.observeReads(scope,{ invalidations++ }) { assertTrue(m.documents.available) }
                m.closeDocuments()
                Snapshot.sendApplyNotifications()
                assertFalse(m.documents.available)
                assertTrue("Closing documents must invalidate the screen route",invalidations>0)
            } finally { observer.stop();observer.clear() }
        }
    }
    @Test fun completedHasZeroDocumentReadsUntilDeliberateOpen() {
        val m=model(ActionStatus.COMPLETED);supplyWorkspace()
        assertEquals(0,docs.summaries);assertEquals(0,docs.lists)
        m.openDocuments();assertTrue(m.documents.available);assertEquals(1,docs.lists);assertEquals(0,docs.summaries)
        m.closeDocuments();assertFalse(m.documents.available);assertEquals(0,docs.summaries)
    }
    @Test fun draftHasNoDocumentPath() {
        val m=model(ActionStatus.DRAFT);m.openDocuments();assertFalse(m.documents.available);assertEquals(0,docs.lists);assertEquals(0,docs.summaries)
    }
    @Test fun preparationSummaryOnlyAndDeliberateList() {
        val m=model(ActionStatus.PREPARATION);supplyWorkspace();assertNull(m.workspace)
        assertEquals(1,docs.summaries);assertEquals(0,docs.lists)
        docs.pending!!(DocumentResult.Value(DocumentSummary(2)));assertEquals(2,m.workspace!!.documentSummary!!.count)
        m.openDocuments();assertEquals(1,docs.lists);assertTrue(m.documents.available)
        m.back();assertFalse(m.documents.available);assertNull(m.documents.items)
    }
    @Test fun atomicHubRetainsOldSnapshotOnRefreshAndNeverInventsZero() {
        val m=model(ActionStatus.ACTIVE);supplyWorkspace();assertNull(m.workspace);assertEquals(0,docs.lists)
        docs.pending!!(DocumentResult.Value(DocumentSummary(3)));val previous=m.workspace!!
        m.loadWorkspace();supplyWorkspace();assertSame(previous,m.workspace)
        docs.pending!!(DocumentResult.Rejected(DocumentFailure.NOT_ALLOWED));assertSame(previous,m.workspace);assertTrue(m.workspaceError)
    }
    @Test fun hubNinthCardIsCountOnlyAndBeforeDetails() {
        val a=actions.row
        val cards=activeHubCards(a,OperationalBoard("a",ActionStatus.ACTIVE,emptyList(),emptyList()),TaskBoard("a",emptyList(),emptyList()),ProtocolSummary(0,null),MaterialSummary(0),ExpenseSummary(0,0),MemoryReportSummary(0,0),DocumentSummary(4))
        assertEquals(9,cards.size);assertEquals("Dokumente",cards[7].label);assertEquals(listOf("4 Dokumente"),cards[7].summary)
        assertTrue(cards[7].documents);assertEquals(ActiveArea.DETAILS,cards.last().area)
        assertEquals(8,cards.filterNot{it.documents}.size)
    }
    @Test fun leavingActionDiscardsPickerHandoffEvenWhenSameActionIsReopened() {
        val m=model(ActionStatus.ACTIVE)
        m.openDocuments()
        m.documents.chooseFile()
        assertEquals(SessionState.LOCKED,m.snapshot.state)
        assertNull(m.selected)
        handoff.returned("synthetic source")

        // The intended picker -> lock -> fresh READY -> deliberate entry still works.
        session.emit(SessionState.READY)
        handoff.ready(session.value.generation)
        assertNull(m.selected)
        m.detail("a")
        m.openDocuments()
        assertTrue(m.documents.canContinueImport)

        // Clearing only DocumentModel would leave this Activity-RAM source available.
        m.back()
        assertNull(m.selected)
        assertNull(m.documents.items)
        assertFalse(handoff.available("a",session.value.generation))
        m.detail("a")
        m.openDocuments()
        assertFalse(m.documents.canContinueImport)
        m.documents.continueImport()
        assertFalse(m.documents.importing)
    }
    @Test fun lockAndNewActionDiscardListAndLateSummary() {
        val m=model(ActionStatus.ACTIVE);supplyWorkspace();val old=docs.pending!!
        m.openDocuments();assertNotNull(m.documents.items);session.lock();assertNull(m.documents.items);assertFalse(m.documents.available)
        old(DocumentResult.Value(DocumentSummary(9)));assertNull(m.workspace)
    }
}
