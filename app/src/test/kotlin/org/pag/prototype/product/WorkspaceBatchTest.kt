package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class WorkspaceBatchTest {
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
        var transitions = 0;var gets=0
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) {gets++;publish(row.copy(id = id))}
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
        var creates=0
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {
            creates++;publish(OperationsResult.Value(Task("new",actionId,input.title,input.priority,TaskStatus.OPEN,input.assigneePersonId,input.assigneeRoleId,input.affectedPersonId,input.dueAt,input.sourceTemplateKey,1,1,null,0)))
        }
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
    private val pending=mutableListOf<Pair<WorkspaceReadRequest,(WorkspaceResult)->Unit>>()
    private var details=0
    init { model.bindWorkspace(object:WorkspaceRepository {
        override fun detail(original:WorkspaceAccess,request:WorkspaceDetailRequest,publish:(WorkspaceDetailResult)->Unit){details++;publish(WorkspaceDetailResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))}
        override fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit){pending.add(request to publish)}
    }) }
    private fun result(request:WorkspaceReadRequest)=WorkspaceRead(request.actionId,request.status,request.generation,request.parts,
        OperationalBoard(request.actionId,request.status,emptyList(),emptyList()),
        if(WorkspacePart.TASKS in request.parts)TaskBoard(request.actionId,emptyList(),emptyList()) else null,
        if(WorkspacePart.PROTOCOL_SUMMARY in request.parts)ProtocolSummary(0,null) else null,
        material=if(WorkspacePart.MATERIAL_SUMMARY in request.parts)MaterialSummary(0) else null,
        expenses=if(WorkspacePart.EXPENSE_SUMMARY in request.parts)ExpenseSummary(0,0) else null,
        memory=if(WorkspacePart.MEMORY_SUMMARY in request.parts)MemoryReportSummary(0,0) else null,
        memoryHeaders=if(WorkspacePart.MEMORY_HEADERS in request.parts)emptyList() else null,
        protocolTimeline=if(WorkspacePart.PROTOCOL_TIMELINE in request.parts)emptyList() else null,
        materialBoard=if(WorkspacePart.MATERIAL_BOARD in request.parts)MaterialBoard(request.actionId,emptyList()) else null,
        expenseBoard=if(WorkspacePart.EXPENSE_BOARD in request.parts)ExpenseBoard(request.actionId,emptyList()) else null)
    private fun complete(index:Int=pending.lastIndex,change:(WorkspaceRead)->WorkspaceRead={it}) {
        val (request,publish)=pending[index];publish(WorkspaceResult.Value(change(result(request))))
    }
    @Test fun oneExplicitBatchReplacesLegacyReadsAndPublishesAtomically() {
        model.detail("a");assertNull(model.workspace)
        assertEquals(setOf(WorkspacePart.TASKS,WorkspacePart.PROTOCOL_SUMMARY),pending.single().first.parts)
        assertTrue(ops.loads.isEmpty());assertTrue(tasks.loads.isEmpty());assertTrue(summaries.isEmpty());assertEquals(0,people.lists)
        complete();assertEquals("a",model.workspace!!.actionId);assertNotNull(model.workspace!!.tasks)
        assertNotNull(model.workspace!!.protocolSummary);assertTrue(protection.states.isEmpty())
    }
    @Test fun lockAndLateCompletionNeverRestoreContentAfterReauthentication() {
        model.detail("a");session.lock();complete();assertNull(model.workspace)
        session.emit(SessionState.READY,3);complete();assertNull(model.workspace);assertNull(model.selected)
    }
    @Test fun navigationAndAnotherActionFenceOldBatch() {
        model.detail("a");model.activeArea(ActiveArea.FUNCTIONS);complete(0);assertNull(model.workspace)
        assertTrue(pending.last().first.parts.isEmpty());complete();assertNotNull(model.workspace)
        model.detail("b");complete(0);assertNull(model.workspace);complete();assertEquals("b",model.workspace!!.actionId)
    }
    @Test fun incorrectBindingsAndMissingRequiredPartFailWithoutPartialPublication() {
        val mutations:List<(WorkspaceRead)->WorkspaceRead> = listOf(
            {it.copy(actionId="other")},{it.copy(status=ActionStatus.COMPLETED)},
            {it.copy(generation=0)},{it.copy(parts=emptySet())},{it.copy(tasks=null)},
            {it.copy(protocol=null)},{it.copy(operations=it.operations.copy(actionId="other"))})
        for(mutate in mutations){model.detail("a");complete(change=mutate);assertNull(model.workspace);assertTrue(model.workspaceError)}
    }
    @Test fun refreshKeepsSameAuthorizedSnapshotUntilWholeBatchAndDropsOlderRequest() {
        model.detail("a");complete();val before=model.workspace
        model.loadWorkspace();val old=pending.lastIndex;model.loadWorkspace()
        complete(old);assertSame(before,model.workspace);assertTrue(model.workspaceRefreshing)
        complete(change={it.copy(operations=it.operations.copy(roles=listOf(Role("r","a","Synthetic",0, editRevision = 0))))});assertNotSame(before,model.workspace);assertFalse(model.workspaceRefreshing)
    }
    @Test fun preparationStillWaitsForIndependentProtectionMetadata() {
        actions.row=actions.row.copy(status=ActionStatus.PREPARATION);session.emit(SessionState.LOCKED,2);session.emit(SessionState.READY,3);model.detail("a")
        complete();assertNull(model.workspace);assertEquals(1,protection.states.size)
        protection.states.single().second(SensitiveResult.Value(SensitiveState.READY));assertNotNull(model.workspace)
        assertEquals(0,protection.inventories);assertEquals(0,protection.opens)
    }
    private fun memoryModel(port:MemoryFake):ProductViewModel {
        val value=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,people,null,ops,tasks,protocolPort,memoryReportRepository=port){}
        value.bindWorkspace(object:WorkspaceRepository {
            override fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit){pending.add(request to publish)}
        })
        return value
    }
    @Test fun selectedMemoryListIsOneBatchWithoutTaskBoardOrSecondRead() {
        val memory=MemoryFake();val value=memoryModel(memory)
        value.detail("a");complete();value.activeArea(ActiveArea.MEMORY_REPORTS)
        assertEquals(setOf(WorkspacePart.MEMORY_HEADERS),pending.last().first.parts)
        assertNull(value.workspace);assertTrue(value.memoryReports.loading);assertEquals(0,memory.lists)
        complete();assertNotNull(value.workspace);assertEquals(emptyList<MemoryReportHeader>(),value.memoryReports.headers)
        assertFalse(value.memoryReports.loading);assertEquals(0,memory.lists);assertEquals(0,memory.details)
    }
    @Test fun wrongMemoryHeaderNeverPublishesPartialSnapshot() {
        val value=memoryModel(MemoryFake());value.detail("a");complete();value.activeArea(ActiveArea.MEMORY_REPORTS)
        complete(change={it.copy(memoryHeaders=listOf(MemoryReportHeader("r","other","Synthetic",1,MemoryReportStatus.DRAFT,0,0,1,1,null)))})
        assertNull(value.workspace);assertNull(value.memoryReports.headers);assertTrue(value.workspaceError)
    }
    @Test fun memoryBatchLockAndLateResultsCannotAutoopenAfterReauthentication() {
        val value=memoryModel(MemoryFake());value.detail("a");complete();value.activeArea(ActiveArea.MEMORY_REPORTS)
        val old=pending.lastIndex;session.lock();complete(old);session.emit(SessionState.READY,3);complete(old)
        assertNull(value.workspace);assertNull(value.selected);assertNull(value.memoryReports.headers);assertFalse(value.documents.available)
    }
    @Test fun memoryEditorRefreshDoesNotReenterListOrFetchHeaders() {
        val value=memoryModel(MemoryFake());value.detail("a");complete();value.activeArea(ActiveArea.MEMORY_REPORTS);complete()
        value.memoryReports.create();value.loadWorkspace()
        assertFalse(WorkspacePart.MEMORY_HEADERS in pending.last().first.parts);complete()
        assertFalse(value.workspaceError);assertEquals(MemoryReportMode.SECTION,value.memoryReports.mode)
    }
    @Test fun navigationKeySurvivesLoadCompletionAndContainsNoActionIdentifier() {
        model.detail("a");complete();model.activeArea(ActiveArea.PERSONS)
        val key=productNavigationKey(model);assertTrue(productReadPending(model));complete()
        assertEquals(key,productNavigationKey(model));assertFalse(productReadPending(model));assertEquals("ACTIVE_PERSONS",key)
    }
    @Test fun rapidNavigationDiscardsPendingBatchAndReturnRequestsFreshSnapshot() {
        model.detail("a");complete();model.activeArea(ActiveArea.PERSONS);val old=pending.lastIndex
        model.activeArea(ActiveArea.HUB);assertFalse(pending[old].first.ticket!!.active)
        complete(old);assertNull(model.workspace);complete();assertNotNull(model.workspace)
        val oldCount=pending.size;model.activeArea(ActiveArea.PERSONS)
        assertEquals(oldCount+1,pending.size);assertNull(model.workspace)
    }

    @Test fun initialActionOpenUsesFreshBoundWorkspaceWithoutSeparateDetailRead() {
        session.emit(SessionState.LOCKED,2);session.emit(SessionState.READY,3)
        model.detail("a");assertEquals(0,actions.gets);assertTrue(model.detailLoading);assertNull(model.workspace)
        assertEquals(actions.row,pending.single().first.expectedAction)
        complete();assertFalse(model.detailLoading);assertNotNull(model.workspace)
    }
    @Test fun staleInitialActionFallsBackWithoutAnotherInteractionOrOldPublication() {
        session.emit(SessionState.LOCKED,2);session.emit(SessionState.READY,3)
        model.detail("a");val interactions=session.interactions;val old=pending.lastIndex
        actions.row=actions.row.copy(title="Synthetic updated")
        pending[old].second(WorkspaceResult.Rejected(WorkspaceFailure.STALE))
        assertEquals(1,actions.gets);assertEquals(interactions,session.interactions)
        assertEquals("Synthetic updated",model.selected!!.title);assertNull(model.workspace)
        complete(old);assertNull(model.workspace);complete();assertNotNull(model.workspace)
    }

    @Test fun combinedInitialOpenCannotPublishOrAutoopenAfterGenerationChange() {
        session.emit(SessionState.LOCKED,2);session.emit(SessionState.READY,3)
        model.detail("a");assertNotNull(pending.single().first.expectedAction)
        session.lock();assertFalse(pending.single().first.ticket!!.active);complete();assertNull(model.selected);assertNull(model.workspace)
        session.emit(SessionState.READY,5);complete()
        assertNull(model.selected);assertNull(model.workspace);assertFalse(model.detailLoading)
    }

    @Test fun staleOpeningStatusFallsBackToDraftWithoutAnotherWorkspaceRead() {
        session.emit(SessionState.LOCKED,2);session.emit(SessionState.READY,3)
        model.detail("a");val interactions=session.interactions
        actions.row=actions.row.copy(status=ActionStatus.DRAFT)
        pending.single().second(WorkspaceResult.Rejected(WorkspaceFailure.STALE))
        assertEquals(ActionStatus.DRAFT,model.selected!!.status);assertNull(model.workspace)
        assertEquals(1,pending.size);assertEquals(1,actions.gets)
        assertEquals(interactions,session.interactions);assertFalse(model.detailLoading)
    }

    private val areaProtocol=ProtocolFake()
    private val areaMaterial=MaterialFake()
    private val areaExpenses=ExpenseFake()
    private fun areaModel():ProductViewModel = ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,
        people,null,ops,tasks,areaProtocol,areaMaterial,areaExpenses){}.also{m->
        m.bindWorkspace(object:WorkspaceRepository{override fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit){pending.add(request to publish)}})
        m.detail("a");complete()
    }
    @Test fun explicitAreaPayloadsShareOneBatchWithoutIndependentReadsOrPrefetch() {
        val m=areaModel()
        assertFalse(pending.single().first.parts.any{it in setOf(WorkspacePart.PROTOCOL_TIMELINE,WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD)})
        for((area,part) in listOf(ActiveArea.PROTOCOL to WorkspacePart.PROTOCOL_TIMELINE,ActiveArea.MATERIAL to WorkspacePart.MATERIAL_BOARD,ActiveArea.COSTS to WorkspacePart.EXPENSE_BOARD)) {
            val before=pending.size;m.activeArea(area);assertEquals(before+1,pending.size)
            assertEquals(setOf(part),pending.last().first.parts.intersect(setOf(WorkspacePart.PROTOCOL_TIMELINE,WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD)))
            assertNull(m.workspace);complete();assertNotNull(m.workspace)
            when(area){ActiveArea.PROTOCOL->assertNotNull(m.protocol.timeline);ActiveArea.MATERIAL->assertNotNull(m.material.board);else->assertNotNull(m.expenses.board)}
        }
        assertEquals(0,areaProtocol.timelineReads);assertEquals(0,areaMaterial.boardReads);assertEquals(0,areaExpenses.boardReads)
    }
    @Test fun malformedAreaBoardsCannotPublishPartialWorkspace() {
        val m=areaModel();m.activeArea(ActiveArea.MATERIAL)
        complete{it.copy(materialBoard=MaterialBoard("wrong",emptyList()))}
        assertNull(m.workspace);assertNull(m.material.board);assertTrue(m.workspaceError)
        m.activeArea(ActiveArea.COSTS);complete{it.copy(expenseBoard=ExpenseBoard("wrong",emptyList()))}
        assertNull(m.workspace);assertNull(m.expenses.board);assertTrue(m.workspaceError)
    }
    @Test fun areaPayloadsAreDiscardedOnRapidNavigationAndLockWithoutAutoopen() {
        val m=areaModel();m.activeArea(ActiveArea.PROTOCOL);val stale=pending.lastIndex
        m.activeArea(ActiveArea.COSTS);complete(stale);assertNull(m.protocol.timeline);assertNull(m.workspace)
        session.lock();complete();assertNull(m.expenses.board);assertNull(m.workspace)
        session.emit(SessionState.READY,3);complete();assertNull(m.selected);assertNull(m.workspace);assertNull(m.expenses.board)
    }
    @Test fun selectedMaterialAndExpenseEditorRefreshesDoNotReplaceDraftWithBatchPayload() {
        val m=areaModel();m.activeArea(ActiveArea.MATERIAL);complete();m.material.create();m.loadWorkspace()
        assertFalse(WorkspacePart.MATERIAL_BOARD in pending.last().first.parts);complete();assertTrue(m.material.form)
        m.material.cancel();m.activeArea(ActiveArea.COSTS);complete();m.expenses.create();m.loadWorkspace()
        assertFalse(WorkspacePart.EXPENSE_BOARD in pending.last().first.parts);complete();assertTrue(m.expenses.form)
    }
    @Test fun preparationMaterialAndCostsAreExplicitSingleBatchesWithoutProtocolPrefetch() {
        actions.row=actions.row.copy(status=ActionStatus.PREPARATION)
        val m=areaModel()
        m.preparationArea(PreparationArea.MATERIAL);assertTrue(WorkspacePart.MATERIAL_BOARD in pending.last().first.parts)
        assertFalse(WorkspacePart.PROTOCOL_TIMELINE in pending.last().first.parts);complete()
        assertNotNull(m.material.board);assertNotNull(m.workspace)
        m.preparationArea(PreparationArea.COSTS);assertTrue(WorkspacePart.EXPENSE_BOARD in pending.last().first.parts);complete()
        assertNotNull(m.expenses.board);assertNotNull(m.workspace)
        assertEquals(0,areaMaterial.boardReads);assertEquals(0,areaExpenses.boardReads)
    }

    /** Fake capability tests VM routing only; real record tested in P108WorkspaceAccessTest. */
    private class BoundAccess:WorkspaceAccess {
        var live=true;var budget=true;var reservations=0
        override fun publish(actionId:String,status:ActionStatus,generation:Long,work:()->Unit):Boolean {
            if(!live || actionId!="a" || generation!=1L)return false
            work();return live
        }
        override fun reserve(units:ActiveWorkspaceBudget.Units):Boolean {reservations++;return live && budget}
    }
    @Test fun p108OriginalNavigationKeepsSameGraphAndOriginalReadTicket() {
        val scope=BoundAccess();model.detail("a");complete(change={it.copy(access=scope)})
        val held=model.workspace!!;val original=pending.single().first.ticket!!
        for(area in listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB)) {
            model.activeArea(area);assertSame(held,model.workspace);assertTrue(original.active)
        }
        assertEquals(1,pending.size);assertTrue(ops.loads.isEmpty());assertTrue(tasks.loads.isEmpty())
        assertEquals(6,scope.reservations) // UI planning + representation and four navigations.
    }
    @Test fun p108RefusedCopyDoesNotRunProducerOrReplaceOriginalGraph() {
        val scope=BoundAccess();model.detail("a");complete(change={it.copy(access=scope)})
        val held=model.workspace;scope.budget=false
        model.activeArea(ActiveArea.PERSONS)
        assertSame(held,model.workspace);assertEquals(ActiveArea.HUB,model.activeArea)
        assertEquals(WorkspaceFailure.BUDGET_REFUSED,model.workspaceRefusal);assertEquals(1,pending.size)
    }
    @Test fun p108InvalidOriginalScopeNeverFallsBackToNewFullBoardRead() {
        val scope=BoundAccess();model.detail("a");complete(change={it.copy(access=scope)})
        scope.live=false;model.activeArea(ActiveArea.TASKS)
        assertNull(model.workspace);assertEquals(1,pending.size)
        assertEquals(WorkspaceFailure.OWNER_UNCONFIRMED,model.workspaceRefusal)
        assertTrue(ops.loads.isEmpty());assertTrue(tasks.loads.isEmpty())
    }
    @Test fun p108LateOriginalCallbackCannotRestoreAfterLock() {
        val scope=BoundAccess();model.detail("a");complete(change={it.copy(access=scope)})
        session.lock();complete(change={it.copy(access=scope)})
        assertNull(model.workspace);assertNull(model.selected)
        session.emit(SessionState.READY,3);complete(change={it.copy(access=scope)})
        assertNull(model.workspace);assertNull(model.selected)
    }
    @Test fun p110HeldPresentationsBorrowRowsAndIdentityOnlyCallbacks(){
        val scope=BoundAccess();model.detail("a");complete(change={it.copy(access=scope,tasks=TaskBoard("a",listOf(Task("p110-task","a","Synthetic P110 task",TaskPriority.NORMAL,TaskStatus.OPEN,null,null,null,null,null,0,0,null,0)),emptyList(),false))})
        val held=model.workspace!!;val task=held.tasks!!.tasks.first();val row=model.taskRow(task)!!
        val initial=scope.reservations
        val aliases=List(32_768){model.taskRow(task)!!}
        for(alias in aliases){assertSame(row,alias);assertSame(row.click,alias.click);assertSame(row.card(0),alias.card(0))}
        assertEquals(initial,scope.reservations)
        val prohibited=setOf(Task::class.java,TaskBoard::class.java,OperationalBoard::class.java,WorkspaceSnapshot::class.java,TaskUiRow::class.java)
        assertTrue(OriginalRowAction::class.java.declaredFields.none{it.type in prohibited})
        row.click();assertEquals(task,model.tasks.selected)
        session.lock();row.click();assertNull(model.tasks.selected);assertNull(model.workspace)
    }
    @Test fun p110CallbackRechecksAfterInteractionAndCannotCrossOriginalRoot(){
        val scope=BoundAccess();model.detail("a");complete(change={it.copy(access=scope,tasks=TaskBoard("a",listOf(Task("p110-task","a","Synthetic P110 task",TaskPriority.NORMAL,TaskStatus.OPEN,null,null,null,null,null,0,0,null,0)),emptyList(),false))})
        val task=model.workspace!!.tasks!!.tasks.first();val click=model.taskRow(task)!!.click
        session.onInteraction={session.lock()}
        click();assertNull(model.tasks.selected);assertNull(model.workspace)
        session.onInteraction={};session.emit(SessionState.READY,3);click();assertNull(model.tasks.selected)
    }
    @Test fun p110BudgetRefusalDoesNotPublishUiRowsOrRunAnyProducer(){
        val scope=BoundAccess();scope.budget=false
        var constructed=0
        val refused=admitOriginalUi(scope,"a",ActionStatus.ACTIVE,1,ActiveWorkspaceBudget.Units(objects=1)){constructed++;Any()}
        assertNull(refused);assertEquals(0,constructed)
        model.detail("a");complete(change={it.copy(access=scope)})
        assertNull(model.workspace);assertEquals(WorkspaceFailure.BUDGET_REFUSED,model.workspaceRefusal)
        scope.budget=true;scope.live=false
        assertNull(admitOriginalUi(scope,"a",ActionStatus.ACTIVE,1,ActiveWorkspaceBudget.Units()){constructed++;Any()})
        assertEquals(0,constructed)
    }

    @Test fun p110RepositoryBindingAfterConstructionActivatesProjectionAndNeverRepeatsCommittedCreate(){
        val scope=BoundAccess();model.detail("a");complete(change={it.copy(access=scope)})
        model.activeArea(ActiveArea.TASKS)
        model.tasks.add();model.tasks.changeTitle("Synthetic new");model.tasks.save()
        assertEquals(1,tasks.creates);assertEquals(1,details);assertEquals(1,pending.size)
        assertTrue(model.tasks.commitConfirmed);assertEquals(OperationsFailure.OWNER_UNCONFIRMED,model.tasks.error)
        assertTrue(model.tasks.form)
        model.tasks.save();assertEquals(1,tasks.creates);assertEquals(1,details)
    }

}
