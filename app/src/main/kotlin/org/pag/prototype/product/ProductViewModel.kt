package org.pag.prototype.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.pag.domain.*
import java.util.concurrent.atomic.AtomicReference

/** Only domain ports; no DB, DAO, Cipher, key, saved state or retained unlock runtime. */
class ProductViewModel(private val session: SessionPort, private val repository: ActionRepository,
    initialBootstrap: ProductStorageState, private val personRepository: PersonRepository,
    private val leadership: LeadershipRepository? = null,
    private val operationsRepository: OperationsRepository? = null,
    private val taskRepository: TaskRepository? = null, private val protocolRepository: ProtocolRepository? = null,
    private val materialRepository: MaterialRepository? = null, private val expenseRepository: ExpenseRepository? = null, private val memoryReportRepository: MemoryReportRepository? = null, private val begin: (Boolean) -> Unit) : ViewModel() {
    private var documentRepository: DocumentRepository? = null
    private var documentPicker: DocumentPicker? = null
    private var workspaceRepository:WorkspaceRepository?=null
    private val ownerThread=Thread.currentThread()
    private var ownerDispatch: (() -> Unit) -> Unit = { work -> check(Thread.currentThread() === ownerThread);work() }
    private var activeCorePort:ActiveCoreLoadPort?=null
    /** Metadata only. Lexical VM scopes, NOT an owner-end or reader-release receipt. */
    private class VmCoreScopes {
        private var active=1L // Original installation callback, before CoreSlot construction.
        private var published=false
        private var broken=false
        @Synchronized fun enter():Boolean {
            if(broken || active<0L || active==Long.MAX_VALUE || (active==0L && !published)) {
                broken=true;return false // Sticky refusal, never wrap or reopen a retired scope.
            }
            active++;return true
        }
        @Synchronized fun end() {
            if(broken || active<=0L) {broken=true;return}
            active--
        }
        @Synchronized fun publication(value:Boolean) {
            if(value && (broken || active==0L)) {broken=true;return}
            published=value
        }
        @Synchronized fun diagnosis():String =
            if(!broken && !published && active==0L) "VM_SCOPES_QUIESCENT" else "NOT_QUIESCENT"
    }
    private class CoreSlot(val value:ActiveCoreResult.Value,val lifetime:ActiveCoreLifetime,val identity:Any, val persons:CompactPersonsPresentation, val tasks:CompactTasksPresentation, val hub:CompactHubPresentation, val functions:CompactFunctionsPresentation, val scopes:VmCoreScopes) {
        var subscription:Subscription?=null
        fun close() { persons.invalidate();tasks.invalidate();hub.invalidate();functions.invalidate();subscription?.cancel();lifetime.close() }
    }
    private val coreSlot=AtomicReference<CoreSlot?>(null)
    /** The short monitor covers acquisition+registration and detachment, never external work. */
    private fun acquireCoreScope(identity:Any?=null):CoreSlot? = synchronized(coreSlot) {
        val slot=coreSlot.get() ?: return@synchronized null
        if(identity!=null && slot.identity!==identity)return@synchronized null
        if(slot.scopes.enter())slot else null
    }
    private inline fun <T> withCoreScope(identity:Any?=null,revoking:Boolean=false,body:(CoreSlot)->T):T? {
        var registered=false
        val slot=synchronized(coreSlot) {
            val held=coreSlot.get() ?: return@synchronized null
            if(identity!=null && held.identity!==identity)return@synchronized null
            registered=held.scopes.enter()
            if(registered || revoking)held else null
        } ?: return null
        try { return body(slot) } finally { if(registered)slot.scopes.end() }
    }
    private fun detachCoreSlot(slot:CoreSlot):Boolean = synchronized(coreSlot) {
        if(!coreSlot.compareAndSet(slot,null))false else {slot.scopes.publication(false);true}
    }
    private var coreLoad:Any?=null
    private var coreSignal by mutableStateOf(0L)
    private var fullActiveView by mutableStateOf(false)
    fun bindActiveCore(port:ActiveCoreLoadPort,dispatch:((()->Unit)->Unit)) {
        check(activeCorePort==null && Thread.currentThread() === ownerThread)
        activeCorePort=port;ownerDispatch=dispatch
    }
    private fun dropCoreAuthority() {
        // Even corrupt/exhausted diagnostic metadata must never prevent synchronous revoke.
        withCoreScope(revoking=true) { slot->if(detachCoreSlot(slot))slot.close() }
    }
    private fun clearActiveCore() { coreLoad=null;activeCorePort?.cancelPending();dropCoreAuthority();coreSignal++ }
    val compactActiveMode get() = activeCorePort!=null && !fullActiveView && selected?.status==ActionStatus.ACTIVE &&
        activeArea in setOf(ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS) &&
        selectedPerson==null && !personForm && !tasks.form && tasks.selected==null && operations.screen==OperationsScreen.OVERVIEW
    /** Internal original-slot validation only; never lends a payload to a caller. */
    private fun currentCoreSlot(slot:CoreSlot):Boolean {
        if(coreSlot.get() !== slot)return false
        val core=slot.value.core
        if(snapshot.state!=SessionState.READY || selected?.status!=ActionStatus.ACTIVE || selected?.id!=core.action.id ||
            snapshot.generation!=core.state.binding.generation || !slot.lifetime.isCurrent()) {
            if(detachCoreSlot(slot))slot.close()
            return false
        }
        return true
    }
    /** Pre-admitted metadata handles, never second page/payload roots. */
    internal val compactPersonsPresentation:CompactPersonsPresentation? get() = presentation(ActiveArea.PERSONS){it.persons}
    internal val compactTasksPresentation:CompactTasksPresentation? get() = presentation(ActiveArea.TASKS){it.tasks}
    internal val compactHubPresentation:CompactHubPresentation? get() = presentation(ActiveArea.HUB){it.hub}
    internal val compactFunctionsPresentation:CompactFunctionsPresentation? get() = presentation(ActiveArea.FUNCTIONS){it.functions}
    private inline fun <T> presentation(area:ActiveArea,read:(CoreSlot)->T):T? {
        coreSignal
        if(Thread.currentThread() !== ownerThread || activeArea!=area || !compactActiveMode)return null
        return withCoreScope { slot->
            if(!compactPresentationCurrent(slot,area)) {dropCompactPresentation(slot);null} else read(slot)
        }
    }
    private fun dropCompactPresentation(slot:CoreSlot) {
        if(detachCoreSlot(slot))slot.close() // No outstanding-read retirement.
    }
    private fun compactPresentationCurrent(slot:CoreSlot,area:ActiveArea):Boolean {
        val current=session.snapshot()
        val core=slot.value.core
        return coreSlot.get()===slot && activeArea==area && compactActiveMode && activeNavigationEnabled &&
            snapshot.state==SessionState.READY && current.state==SessionState.READY &&
            snapshot.generation==core.state.binding.generation && current.generation==core.state.binding.generation &&
            selected?.id==core.state.binding.actionId && selected?.status==ActionStatus.ACTIVE && slot.lifetime.isCurrent()
    }
    private inline fun <T> resolveCompactPresentation(identity:Any,area:ActiveArea,crossinline read:(ActiveCorePayload)->T):T? {
        if(Thread.currentThread() !== ownerThread)return null
        return withCoreScope(identity) { slot->
            if(!compactPresentationCurrent(slot,area) || !slot.lifetime.authorizeHit(slot.value.core.state.binding.actionId)) {
                dropCompactPresentation(slot);return null
            }
            var value:T?=null
            val accepted=slot.lifetime.publish { if(compactPresentationCurrent(slot,area))value=read(slot.value.core) }
            if(!accepted || !compactPresentationCurrent(slot,area)) {dropCompactPresentation(slot);return null}
            value
        }
    }
    internal fun compactPersonsShown(identity:Any):Int? = resolveCompactPresentation(identity,ActiveArea.PERSONS){it.people.items.size}
    internal fun compactPersonsTotal(identity:Any):Long? = resolveCompactPresentation(identity,ActiveArea.PERSONS){it.people.overall.total}
    internal fun compactPersonsHasMore(identity:Any):Boolean? = resolveCompactPresentation(identity,ActiveArea.PERSONS){it.people.hasMore}
    internal fun compactPersonsCount(identity:Any,status:OperationalStatus):Long? = resolveCompactPresentation(identity,ActiveArea.PERSONS){it.people.overall.count(status)}
    internal fun compactPersonPseudonym(identity:Any,index:Int):String? {
        if(index !in 0 until 32)return null
        return resolveCompactPresentation(identity,ActiveArea.PERSONS){it.people.items.getOrNull(index)?.pseudonym}
    }
    internal fun compactTasksShown(identity:Any):Int? = resolveCompactPresentation(identity,ActiveArea.TASKS){it.tasks.items.size}
    internal fun compactTasksActiveCount(identity:Any):Long? = resolveCompactPresentation(identity,ActiveArea.TASKS){it.tasks.overall.open+it.tasks.overall.inProgress}
    internal fun compactTasksHasMore(identity:Any):Boolean? = resolveCompactPresentation(identity,ActiveArea.TASKS){it.tasks.hasMore}
    internal fun compactTasksCount(identity:Any,status:TaskStatus):Long? = resolveCompactPresentation(identity,ActiveArea.TASKS){
        when(status) {
            TaskStatus.OPEN->it.tasks.overall.open;TaskStatus.IN_PROGRESS->it.tasks.overall.inProgress
            TaskStatus.DONE->it.tasks.overall.done;TaskStatus.CANCELLED->it.tasks.overall.cancelled
        }
    }
    internal fun compactTaskTitle(identity:Any,index:Int):String? {
        if(index !in 0 until 32)return null
        return resolveCompactPresentation(identity,ActiveArea.TASKS){it.tasks.items.getOrNull(index)?.title}
    }
    internal fun compactHubCurrent(identity:Any):Boolean? = resolveCompactPresentation(identity,ActiveArea.HUB){true}
    internal fun compactHubValue(identity:Any,index:Int,line:Int):Long? = resolveCompactPresentation(identity,ActiveArea.HUB){
        when(index) {
            0->if(line==0)it.people.overall.total else it.people.overall.onSite
            1->if(line==0)it.functions.items.size.toLong() else it.functions.onSiteCount
            else->if(line==0)it.tasks.overall.open else it.tasks.overall.inProgress
        }
    }
    internal fun compactHubOpen(identity:Any,area:ActiveArea) {
        // Authorization returns before navigation; no repository work inside publication.
        if(compactHubCurrent(identity)==true)activeArea(area)
    }
    internal fun compactFunctionsSize(identity:Any):Int?=resolveCompactPresentation(identity,ActiveArea.FUNCTIONS){it.functions.items.size}
    internal fun compactFunctionsOnSite(identity:Any):Long?=resolveCompactPresentation(identity,ActiveArea.FUNCTIONS){it.functions.onSiteCount}
    internal fun compactFunctionsWithout(identity:Any):Long?=resolveCompactPresentation(identity,ActiveArea.FUNCTIONS){it.functions.withoutFunction}
    internal fun compactFunctionName(identity:Any,index:Int):String?=resolveCompactPresentation(identity,ActiveArea.FUNCTIONS){it.functions.items.getOrNull(index)?.role?.name}
    internal fun compactFunctionCount(identity:Any,index:Int):Long?=resolveCompactPresentation(identity,ActiveArea.FUNCTIONS){it.functions.items.getOrNull(index)?.onSiteCount}
    internal fun compactFunctionLine(identity:Any,index:Int):String?=resolveCompactPresentation(identity,ActiveArea.FUNCTIONS){
        it.functions.items.getOrNull(index)?.let{function->"${function.role.name}: ${function.onSiteCount} vor Ort"}
    }
    internal fun compactPresentationComposition(identity:Any,observed:Boolean) {
        if(Thread.currentThread() !== ownerThread)return
        withCoreScope(identity) { slot->
            if(observed)slot.lifetime.compositionObserved() else slot.lifetime.compositionDisposed()
        }
    }
    fun useFullActiveView() {
        if(!activeNavigationEnabled)return
        clearActiveCore();activeCorePort?.fullViewStarting();fullActiveView=true;readWorkspace()
    }
    private fun loadActiveCore() {
        val port=activeCorePort ?: return
        val action=selected ?: return
        if(action.status!=ActionStatus.ACTIVE || snapshot.state!=SessionState.READY)return
        clearActiveCore();workspaceTicket?.cancel();workspaceTicket=null;workspaceRequest++
        workspace=null;tasks.clear();operations.clear()
        val identity=Any();coreLoad=identity
        val generation=snapshot.generation
        personsLoading=true;detailLoading=false;workspaceError=false
        var consumed=false
        val queued=port.loadOwned(action.id) { result,lifetime ->
            if(consumed) {if(withCoreScope {it.lifetime === lifetime}!=true)lifetime?.close();return@loadOwned}
            consumed=true
            if(coreLoad !== identity || snapshot.state!=SessionState.READY || snapshot.generation!=generation ||
                selected?.id!=action.id || selected?.status!=ActionStatus.ACTIVE || !compactActiveMode) { lifetime?.close();return@loadOwned }
            if(result !is ActiveCoreResult.Value || lifetime==null) {
                lifetime?.close();personsLoading=false;workspaceError=true;return@loadOwned
            }
            val slotIdentity=Any()
            val scopes=VmCoreScopes()
            try {
                val core=result.core
                if(core.action.id!=action.id || core.action.status!=ActionStatus.ACTIVE || core.state.binding.actionId!=action.id ||
                    core.state.binding.generation!=generation || core.people.state!=core.state || core.tasks.state!=core.state ||
                    core.people.items.size>32 || core.tasks.items.size>32 ||
                    ActiveWorkspaceBudget.estimateUnits(result.reservation.retained).let { it.first!=ActiveWorkspaceBudget.Decision.ADMITTED || it.second!=result.retainedCharge } ||
                    ActiveWorkspaceBudget.estimateUnits(result.reservation.transition).let { it.first!=ActiveWorkspaceBudget.Decision.ADMITTED || it.second!=result.transitionCharge } ||
                    result.retainedCharge !in 0..ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES || result.transitionCharge !in 0..ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES) {
                    lifetime.close();personsLoading=false;workspaceError=true;return@loadOwned
                }
                val slot=CoreSlot(result,lifetime,slotIdentity,CompactPersonsPresentation(this,slotIdentity),CompactTasksPresentation(this,slotIdentity),CompactHubPresentation(this,slotIdentity),CompactFunctionsPresentation(this,slotIdentity,core.functions.items.size),scopes)
                slot.subscription=lifetime.onInvalidated {
                    withCoreScope(slotIdentity,revoking=true) { held->
                        if(detachCoreSlot(held)) {
                            held.persons.invalidate();held.tasks.invalidate();held.hub.invalidate();held.functions.invalidate()
                            // No Compose mutation on a session worker; authority/reference already gone.
                            runCatching { ownerDispatch { coreSignal++ } }
                        }
                    }
                }
                val installed=lifetime.publish {
                    synchronized(coreSlot) {
                        if(coreLoad === identity) {coreSlot.set(slot);scopes.publication(true)}
                    }
                }
                if(!installed || coreSlot.get() !== slot || !lifetime.isCurrent()) {
                    detachCoreSlot(slot);slot.close()
                }
                personsLoading=false;workspaceRefreshing=false;coreSignal++
            } finally { scopes.end() }
        }
        if(!queued) {clearActiveCore();personsLoading=false;workspaceError=true}
    }
    fun bindWorkspace(repository:WorkspaceRepository) {check(workspaceRepository==null);workspaceRepository=repository}
    val documentsAvailable get() = documentRepository != null
    /** Composition-time optional port, preserving all existing test/demo constructors. */
    fun bindDocuments(repository: DocumentRepository, picker: DocumentPicker) {
        check(documentRepository == null)
        documentRepository=repository; documentPicker=picker
    }
    val documents = DocumentModel({ documentRepository }, { documentPicker }, { snapshot }, { selected },
        ::interaction, ::loadWorkspace, ::showFeedback, cameraAuthority = {
            val current=session.snapshot()
            current.state == SessionState.READY && current.generation == snapshot.generation
        })
    fun openDocuments() {
        val a=selected ?: return; val before=snapshot
        if (!documentsAvailable || before.state!=SessionState.READY || !DocumentRules.mayRead(a.status) ||
            saving || personSaving || sensitiveBusy || tasks.busy || operations.busy || protocol.busy ||
            material.busy || expenses.busy || memoryReports.busy || documents.busy) return
        if (memoryReports.available && memoryReports.dirty) { memoryReports.requestLeave { openDocuments() }; return }
        interaction()
        if (snapshot.state!=SessionState.READY || snapshot.generation!=before.generation || selected!=a) return
        clearPersons()
        documents.enter()
    }
    fun closeDocuments() {
        if (!documents.available || documents.busy) return
        interaction()
        documentPicker?.cancel(); documents.clear(); loadWorkspace()
    }
    var bootstrap by mutableStateOf(initialBootstrap); private set
    fun updateBootstrap(state: ProductStorageState) { bootstrap = state }
    var snapshot by mutableStateOf(session.snapshot()); private set
    var actionListState by mutableStateOf<ActionListState>(ActionListState.Loading); private set
    private var actionRequest = 0L
    var remainingMillis by mutableStateOf<Long?>(null); private set
    /** Technical display tick: never calls interaction or supplies a deadline. */
    fun refreshCountdown() { remainingMillis = session.remainingAbsoluteMillis() }
    private fun loadActions() {
        val generation = snapshot.generation
        val request = ++actionRequest
        if (actionListState !is ActionListState.Content && actionListState != ActionListState.Empty) actionListState = ActionListState.Loading
        repository.listActions { loaded ->
            if (snapshot.state != SessionState.READY || snapshot.generation != generation || request != actionRequest) return@listActions
            actionListState = if (loaded.isEmpty()) ActionListState.Empty else ActionListState.Content(loaded)
        }
    }
    val actions: List<Action> get() = (actionListState as? ActionListState.Content)?.actions.orEmpty()
    var selected by mutableStateOf<Action?>(null); private set
    var creating by mutableStateOf(false); private set
    var editing by mutableStateOf<Action?>(null); private set
    var confirming by mutableStateOf<Action?>(null); private set
    var changeError by mutableStateOf<ActionChangeRejection?>(null); private set
    var saving by mutableStateOf(false); private set
    val persons: List<Person> get() = workspace?.persons.orEmpty()
    var personsLoading by mutableStateOf(false); private set
    var selectedPerson by mutableStateOf<Person?>(null); private set
    var personForm by mutableStateOf(false); private set
    var editingPerson by mutableStateOf<Person?>(null); private set
    var personError by mutableStateOf<PersonRejection?>(null); private set
    var personSaving by mutableStateOf(false); private set
    var feedback by mutableStateOf<StatusFeedback?>(null); private set
    private var feedbackId = 0L
    fun dismissFeedback(id: Long) { if (feedback?.id == id) feedback = null }
    private var personRequest = 0L // Navigation ordering only, never session authority.
    val personsVisible get() = snapshot.state == SessionState.READY && selected?.let { PersonRules.mayRead(it.status) } == true
    internal val hasCasEditorDraft get() = personForm || tasks.form || operations.editingRole!=null ||
        (operations.screen==OperationsScreen.PLAN || operations.screen==OperationsScreen.LIVE || operations.screen==OperationsScreen.FUNCTION)
    val personsEditable get() = snapshot.state == SessionState.READY && selected?.let { PersonRules.mayManage(it.status) } == true
    private var workspaceRequest = 0L
    private var workspaceTicket:WorkspaceReadTicket?=null
    var detailLoading by mutableStateOf(false); private set
    var personDetailLoading by mutableStateOf(false); private set
    private var workspaceValue by mutableStateOf<WorkspaceSnapshot?>(null)
    var workspace:WorkspaceSnapshot?
        get() {
            if(Thread.currentThread()!==ownerThread)return null
            val held=workspaceValue?:return null
            val access=held.access ?: return held // Existing legacy/test ports; no new reference-navigation grant.
            var allowed:WorkspaceSnapshot?=null
            val accepted=access.publish(held.actionId,held.status,held.generation){
                if(workspaceValue===held && snapshot.state==SessionState.READY && snapshot.generation==held.generation &&
                    selected?.id==held.actionId && selected?.status==held.status)allowed=held
            }
            return if(accepted)allowed else null
        }
        private set(value){workspaceValue=value}
    var workspaceError by mutableStateOf(false); private set
    var workspaceRefusal by mutableStateOf<WorkspaceFailure?>(null); private set
    var preparationArea by mutableStateOf(PreparationArea.OVERVIEW); private set
    var workspaceRefreshing by mutableStateOf(false); private set
    val preparationAvailable get() = snapshot.state == SessionState.READY && selected?.status == ActionStatus.PREPARATION
    val memoryReportsAvailable get() = memoryReportRepository != null
    val expensesAvailable get() = expenseRepository != null
    val materialAvailable get() = materialRepository != null
    val preparationNavigationEnabled get() = preparationAvailable && !saving && !personSaving && !sensitiveBusy && !tasks.busy && !operations.busy && !protocol.busy && !material.busy && !expenses.busy && !memoryReports.busy && !documents.busy
    val preparationContentState: PreparationContentState get() {
        val content = workspace ?: return if (workspaceError) PreparationContentState.ERROR else PreparationContentState.LOADING
        if ((preparationArea == PreparationArea.TASKS && (content.tasks == null || content.operations == null)) ||
            (preparationArea == PreparationArea.ROLES && content.operations == null)) return PreparationContentState.ERROR
        val empty = when (preparationArea) {
            PreparationArea.OVERVIEW -> false
            PreparationArea.PERSONS -> content.persons.isEmpty()
            PreparationArea.TASKS -> content.tasks?.tasks?.isEmpty() == true
            PreparationArea.ROLES -> content.operations?.roles?.isEmpty() == true
            PreparationArea.MATERIAL -> material.board?.items?.isEmpty() == true
            PreparationArea.COSTS -> expenses.board?.expenses?.isEmpty() == true
        }
        return if (empty) PreparationContentState.EMPTY else PreparationContentState.CONTENT
    }
    /** Gate new app-owned data BEFORE the producer runs. Charges are never refunded on UI
     * disposal. Only these explicit producers are covered, not all existing form/render code. */
    private fun applyWorkspaceCopy(units:ActiveWorkspaceBudget.Units,producer:()->Unit):OperationsFailure? {
        val held=workspaceValue
        val access=held?.access
        if(access==null){if(held==null && workspaceRepository!=null)return OperationsFailure.NOT_ALLOWED;producer();return null} // Existing legacy/test models, not a controlled grant.
        if(!access.publish(held.actionId,held.status,held.generation){})return OperationsFailure.NOT_ALLOWED
        if(!access.reserve(units))return OperationsFailure.BUDGET_REFUSED
        return if(access.publish(held.actionId,held.status,held.generation){producer()})null else OperationsFailure.NOT_ALLOWED
    }
    fun acceptPersonDraft(value:String,assign:(String)->Unit) {
        interaction()
        if(!personsEditable || !personForm || personSaving)return
        val error=applyWorkspaceCopy(ActiveWorkspaceBudget.Units(strings=1,utf16Units=value.length.toLong(),references=1)){assign(value)}
        if(error!=null)personError=if(error==OperationsFailure.BUDGET_REFUSED)PersonRejection.BUDGET_REFUSED else PersonRejection.NOT_ALLOWED
    }
    /** Same original immutable graph, not a reload/cache hit or a second dataset. */
    private fun navigateOriginal(preparation:PreparationArea?=null,active:ActiveArea?=null):Boolean {
        val held=workspaceValue?:return false
        val access=held.access?:return false
        val action=selected?:return true
        if(action.id!=held.actionId || snapshot.generation!=held.generation || action.status!=held.status)return true
        if(active!=null)when(active){ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.MATERIAL,ActiveArea.COSTS,ActiveArea.DETAILS->Unit;else->return false}
        if(!access.publish(held.actionId,held.status,held.generation){}){workspaceRefusal=WorkspaceFailure.OWNER_UNCONFIRMED;workspaceError=true;return true}
        if(!access.reserve(ActiveWorkspaceBudget.Units(objects=16,references=64))){workspaceRefusal=WorkspaceFailure.BUDGET_REFUSED;workspaceError=true;return true}
        val accepted=access.publish(held.actionId,held.status,held.generation){
            if(workspaceValue!==held)return@publish
            clearPersons(preserveWorkspace=true)
            if(preparation!=null)preparationArea=preparation
            if(active!=null)activeArea=active
            editing=null;confirming=null;changeError=null
            if(preparation==PreparationArea.MATERIAL || active==ActiveArea.MATERIAL)material.enter(deferRead=true)
            if(preparation==PreparationArea.COSTS || active==ActiveArea.COSTS)expenses.enter(deferRead=true)
        }
        if(!accepted){workspaceRefusal=WorkspaceFailure.OWNER_UNCONFIRMED;workspaceError=true;return true}
        interaction()
        if(preparation==PreparationArea.MATERIAL || active==ActiveArea.MATERIAL)restoreOriginalModule(held,WorkspacePart.MATERIAL_BOARD)
        if(preparation==PreparationArea.COSTS || active==ActiveArea.COSTS)restoreOriginalModule(held,WorkspacePart.EXPENSE_BOARD)
        return true
    }
    private fun restoreOriginalModule(held:WorkspaceSnapshot,part:WorkspacePart) {
        val access=held.access?:return
        fun accept(value:WorkspaceSnapshot){
            if(part==WorkspacePart.MATERIAL_BOARD)value.materialBoard?.let{material.acceptWorkspaceBoard(value.actionId,value.generation,it)}
            else value.expenseBoard?.let{expenses.acceptWorkspaceBoard(value.actionId,value.generation,it)}
        }
        val present=if(part==WorkspacePart.MATERIAL_BOARD)held.materialBoard!=null else held.expenseBoard!=null
        if(present){access.publish(held.actionId,held.status,held.generation){accept(held)};return}
        val serial=workspaceRequest
        workspaceRefreshing=true
        workspaceRepository?.supplement(access,WorkspaceReadRequest(held.actionId,held.status,held.generation,setOf(part),ticket=workspaceTicket)){result->
            if(serial!=workspaceRequest || workspaceValue!==held)return@supplement
            access.publish(held.actionId,held.status,held.generation){
                workspaceRefreshing=false
                when(result){
                    is WorkspaceSupplementResult.Rejected->{workspaceRefusal=result.reason;workspaceError=true}
                    is WorkspaceSupplementResult.Value->{
                        val v=result.value
                        if(v.actionId!=held.actionId || v.generation!=held.generation){workspaceRefusal=WorkspaceFailure.STALE;workspaceError=true;return@publish}
                        // This header is included in the supplement's PRE-admitted finite builder/envelope costs.
                        val updated=held.copy(materialBoard=v.material?:held.materialBoard,expenseBoard=v.expenses?:held.expenseBoard)
                        workspace=updated;accept(updated)
                    }
                }
            }
        }
    }
    fun preparationArea(area: PreparationArea) {
        val before = snapshot
        val action = selected ?: return
        if (!preparationNavigationEnabled || (area == PreparationArea.MATERIAL && !materialAvailable) || (area == PreparationArea.COSTS && !expensesAvailable)) return
        if(navigateOriginal(preparation=area))return
        interaction()
        if (!preparationNavigationEnabled || snapshot.generation != before.generation || selected?.id != action.id) return
        if (area == preparationArea && !personForm && selectedPerson == null && !sensitiveSetup &&
            !tasks.form && tasks.selected == null && operations.screen == OperationsScreen.OVERVIEW) return
        clearPersons()
        if (!preparationAvailable || snapshot.generation != before.generation || selected?.id != action.id) return
        preparationArea = area
        editing = null; confirming = null; changeError = null
        if(workspaceRepository!=null)when(area) {
            PreparationArea.MATERIAL->material.enter(deferRead=true)
            PreparationArea.COSTS->expenses.enter(deferRead=true)
            else->Unit
        }
        loadWorkspace()
        if (area == PreparationArea.MATERIAL && workspaceRepository==null) material.enter()
        if (area == PreparationArea.COSTS && workspaceRepository==null) expenses.enter()
    }
    var activeArea by mutableStateOf(ActiveArea.HUB); private set
    val activeNavigationEnabled get() = snapshot.state == SessionState.READY && selected?.status == ActionStatus.ACTIVE &&
        !saving && !personSaving && !sensitiveBusy && !tasks.busy && !operations.busy && !protocol.busy && !material.busy && !expenses.busy && !memoryReports.busy && !documents.busy
    fun activeArea(area: ActiveArea) = navigateActive(area)
    private fun navigateActive(area: ActiveArea, retained: WorkspaceSnapshot? = null) {
        if(Thread.currentThread() !== ownerThread)return
        if (memoryReports.available && memoryReports.dirty) { memoryReports.requestLeave { navigateActive(area, retained) }; return }
        val before = snapshot
        val action = selected ?: return
        if (!activeNavigationEnabled || (area == ActiveArea.MATERIAL && !materialAvailable) || (area == ActiveArea.COSTS && !expensesAvailable) || (area == ActiveArea.MEMORY_REPORTS && !memoryReportsAvailable)) return
        if(navigateOriginal(active=area))return
        val hitNavigation=retained==null && activeCorePort?.reuseEnabled==true &&
            area in setOf(ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS)
        val candidate=if(hitNavigation)acquireCoreScope() else null
        try {
            fun currentNavigation() = activeNavigationEnabled && snapshot.generation==before.generation &&
                session.snapshot().let {it.state==SessionState.READY && it.generation==before.generation} &&
                selected?.id==action.id && selected?.status==ActionStatus.ACTIVE
            val reuse=if(candidate!=null) {
                // Validate the original slot around the fresh central gate. Never renew idle
                // for an attempted hit, including a denial that falls back to a fresh read.
                val admitted=currentCoreSlot(candidate) && candidate.lifetime.authorizeHit(action.id)
                val valid=admitted && currentNavigation() && coreSlot.get()===candidate &&
                    currentCoreSlot(candidate)
                if(!valid) {if(detachCoreSlot(candidate))candidate.close()}
                valid
            } else {
                // A synchronous revoke may already have removed the slot. This fallback
                // must not turn a denied hit into an idle renewal either. Gate-off fresh
                // reads and ordinary non-core/editor navigation retain their semantics.
                if(!hitNavigation)interaction()
                false
            }
            if (!currentNavigation()) return
            clearPersons(preserveCore=reuse)
            if (!currentNavigation()) return
            activeArea = area
            if(retained!=null)fullActiveView=true
            confirming = null; changeError = null
            if(reuse && coreSlot.get()===candidate && candidate!=null && currentCoreSlot(candidate)) {personsLoading=false;return}
            // A person-task tap already has both boards. Keep that valid snapshot so
            // its synchronous form opening needs no interaction from a load callback.
            if (area == ActiveArea.TASKS && retained?.actionId == action.id && retained.generation == before.generation &&
                retained.operations != null && retained.tasks != null) workspace = retained
            if(area==ActiveArea.MEMORY_REPORTS && workspaceRepository!=null)memoryReports.enter(deferList=true)
            if(workspaceRepository!=null)when(area) {
                ActiveArea.PROTOCOL->protocol.prepareWorkspaceTimeline()
                ActiveArea.MATERIAL->material.enter(deferRead=true)
                ActiveArea.COSTS->expenses.enter(deferRead=true)
                else->Unit
            }
            if (area != ActiveArea.DETAILS) loadWorkspace()
            if (area == ActiveArea.PROTOCOL && workspaceRepository==null) protocol.refresh()
            if (area == ActiveArea.MATERIAL && workspaceRepository==null) material.enter()
            if (area == ActiveArea.COSTS && workspaceRepository==null) expenses.enter()
            if (area == ActiveArea.MEMORY_REPORTS && workspaceRepository==null) memoryReports.enter()
        } finally { candidate?.scopes?.end() }
    }
    fun activePersonTasks(personId: String, create: Boolean) {
        val generation = snapshot.generation
        val actionId = selected?.id
        val content = workspace ?: return
        if (!activeNavigationEnabled || content.tasks == null || content.operations == null || content.persons.none { it.id == personId }) return
        navigateActive(ActiveArea.TASKS, content)
        if (!activeNavigationEnabled || snapshot.generation != generation || selected?.id != actionId || activeArea != ActiveArea.TASKS) return
        if (create) tasks.add(personId) else tasks.showPerson(personId)
    }
    val completedProtocolOpen get() = snapshot.state == SessionState.READY && selected?.status == ActionStatus.COMPLETED && protocol.completedFollowUp
    fun openCompletedProtocol() {
        if (selected?.status != ActionStatus.COMPLETED || protocolRepository == null || saving || personSaving || sensitiveBusy || tasks.busy || operations.busy || material.busy || expenses.busy || memoryReports.busy) return
        val retained = workspace
        protocol.openCompletedFollowUp {
            clearPersons()
            if (retained?.actionId == selected?.id && retained?.generation == snapshot.generation && retained?.status == ActionStatus.COMPLETED) workspace = retained
            else loadWorkspace()
        }
    }
    fun closeCompletedProtocol() {
        val before = snapshot; val a = selected ?: return
        if (!completedProtocolOpen || protocol.busy || expenses.busy || memoryReports.busy) return
        interaction()
        if (completedProtocolOpen && snapshot.generation == before.generation && selected?.id == a.id) protocol.clear()
    }
    val completedMaterialOpen get() = snapshot.state == SessionState.READY && selected?.status == ActionStatus.COMPLETED && material.completedView
    fun openCompletedMaterial() {
        if (selected?.status != ActionStatus.COMPLETED || !materialAvailable || saving || personSaving || sensitiveBusy || tasks.busy || operations.busy || protocol.busy || expenses.busy || memoryReports.busy) return
        val retained = workspace
        material.openCompleted {
            clearPersons()
            if (retained?.actionId == selected?.id && retained?.generation == snapshot.generation && retained?.status == ActionStatus.COMPLETED) workspace = retained
            else loadWorkspace()
        }
    }
    fun closeCompletedMaterial() {
        val before = snapshot; val a = selected ?: return
        if (!completedMaterialOpen || material.busy || expenses.busy || memoryReports.busy) return
        interaction()
        if (completedMaterialOpen && snapshot.generation == before.generation && selected?.id == a.id) material.clear()
    }
    val completedExpensesOpen get() = snapshot.state == SessionState.READY && selected?.status == ActionStatus.COMPLETED && expenses.completedView
    fun openCompletedExpenses() {
        if (selected?.status != ActionStatus.COMPLETED || !expensesAvailable || saving || personSaving || sensitiveBusy || tasks.busy || operations.busy || protocol.busy || material.busy || memoryReports.busy) return
        val retained = workspace
        expenses.openCompleted {
            clearPersons()
            if (retained?.actionId == selected?.id && retained?.generation == snapshot.generation && retained?.status == ActionStatus.COMPLETED) workspace = retained
            else loadWorkspace()
        }
    }
    fun closeCompletedExpenses() {
        val before = snapshot; val a = selected ?: return
        if (!completedExpensesOpen || expenses.busy) return
        interaction()
        if (completedExpensesOpen && snapshot.generation == before.generation && selected?.id == a.id) expenses.clear()
    }
    val completedMemoryReportsOpen get() = snapshot.state == SessionState.READY && selected?.status == ActionStatus.COMPLETED && memoryReports.completedView
    fun openCompletedMemoryReports() {
        if (selected?.status != ActionStatus.COMPLETED || !memoryReportsAvailable || saving || personSaving || sensitiveBusy || tasks.busy || operations.busy || protocol.busy || expenses.busy || material.busy || memoryReports.busy) return
        val retained = workspace
        memoryReports.openCompleted {
            clearPersons()
            if (retained?.actionId == selected?.id && retained?.generation == snapshot.generation && retained?.status == ActionStatus.COMPLETED) workspace = retained
            else loadWorkspace()
        }
    }
    fun closeCompletedMemoryReports() {
        if (!completedMemoryReportsOpen || memoryReports.busy) return
        memoryReports.requestLeave { if (completedMemoryReportsOpen) memoryReports.clear() }
    }
    val memoryReports = MemoryReportModel(memoryReportRepository, { snapshot }, { selected }, ::interaction, ::loadWorkspace, ::showFeedback)
    val protocol = ProtocolModel(protocolRepository, { snapshot }, { selected }, ::interaction, ::loadWorkspace, ::showFeedback)
    val tasks = TaskModel(taskRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive,
        { workspace?.tasks }, ::loadWorkspace, ::showFeedback).also{it.bindCopyGate(::applyWorkspaceCopy);it.bindWorkspaceScope{workspaceValue?.access}}
    val operations = OperationsModel(operationsRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive,
        { workspace?.operations }, ::loadWorkspace, ::showFeedback, ::returnedFromOperationalEdit).also{it.bindCopyGate(::applyWorkspaceCopy)}
    val material = MaterialModel(materialRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive, ::loadWorkspace, ::showFeedback).also{it.bindCopyGate(::applyWorkspaceCopy)}
    val expenses = ExpenseModel(expenseRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive, ::loadWorkspace, ::showFeedback).also{it.bindCopyGate(::applyWorkspaceCopy)}
    private fun returnedFromOperationalEdit(personId: String?) {
        if (personId != null && selectedPerson?.id == personId && personsVisible) refreshSensitive()
    }
    fun operationalPersonDetail() {
        val generation = snapshot.generation
        val actionId = selected?.id
        val id = operations.dismissEditForPersonDetail() ?: return
        if (snapshot.state == SessionState.READY && snapshot.generation == generation && selected?.id == actionId)
            personDetail(id)
    }
    private fun showFeedback(text: String) {
        if (snapshot.state == SessionState.READY) feedback = StatusFeedback(++feedbackId, text)
    }
    private fun clearPersons(preserveCore:Boolean=false,preserveWorkspace:Boolean=false) {
        if(!preserveCore && !preserveWorkspace)clearActiveCore()
        fullActiveView=preserveWorkspace && workspaceValue?.status==ActionStatus.ACTIVE
        if(!preserveWorkspace){workspaceTicket?.cancel();workspaceTicket=null}
        preparationArea = PreparationArea.OVERVIEW; activeArea = ActiveArea.HUB; workspaceRefreshing = false
        workspaceRequest++; if(!preserveWorkspace)workspace = null; workspaceError = false;workspaceRefusal=null; detailLoading = false; personDetailLoading = false
        tasks.clear(); operations.clear(); protocol.clear(); material.clear(); expenses.clear(); memoryReports.clear(); documents.clear(); clearSensitive(); personRequest++; personsLoading = false; selectedPerson = null
        personForm = false; editingPerson = null; personError = null; personSaving = false
    }
    private fun loadPersons() {
        clearSensitive(); personRequest++
        selectedPerson = null; personForm = false; editingPerson = null; personError = null; personSaving = false
        if (workspace?.status != selected?.status) {
            workspace = null; tasks.clear(); operations.clear(); protocol.clear(); material.clear(); expenses.clear(); memoryReports.clear(); documents.clear()
        }
        loadWorkspace()
    }
    /** Stage short gated reads locally. Only a complete current snapshot enters Compose state. */
    internal fun loadWorkspace()=readWorkspace()
    private fun readWorkspace(expectedAction:Action?=null,onOpeningRejected:(()->Unit)?=null) {
        val action = selected?.takeIf { personsVisible } ?: return
        if(compactActiveMode) {loadActiveCore();return}
        if(activeCorePort!=null){clearActiveCore();activeCorePort?.fullViewStarting()}
        val generation = snapshot.generation
        val request = ++workspaceRequest
        val previous = workspace
        val preparation = action.status == ActionStatus.PREPARATION
        val area = preparationArea
        val active = action.status == ActionStatus.ACTIVE
        val activeSection = activeArea
        if (active && activeSection == ActiveArea.DETAILS) return
        workspaceRefreshing = previous != null
        personsLoading = previous == null
        workspaceError = false;workspaceRefusal=null
        val needTasks = taskRepository != null && operations.screen == OperationsScreen.OVERVIEW &&
            (!active || (activeSection != ActiveArea.FUNCTIONS && (workspaceRepository==null || activeSection in setOf(ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.TASKS,ActiveArea.MATERIAL)))) &&
            (!preparation || (selectedPerson == null && area in setOf(PreparationArea.OVERVIEW, PreparationArea.TASKS, PreparationArea.MATERIAL)))
        val needMetadata = preparation && area == PreparationArea.OVERVIEW && selectedPerson == null &&
            operations.screen == OperationsScreen.OVERVIEW && leadership != null
        val needProtocol = active && activeSection in setOf(ActiveArea.HUB, ActiveArea.PROTOCOL) && protocolRepository != null
        val needMaterial = materialRepository != null && ((active && activeSection in setOf(ActiveArea.HUB, ActiveArea.MATERIAL)) ||
            (preparation && area == PreparationArea.OVERVIEW && selectedPerson == null))
        val needExpenses = expenseRepository != null && ((active && activeSection in setOf(ActiveArea.HUB, ActiveArea.COSTS)) ||
            (preparation && area == PreparationArea.OVERVIEW && selectedPerson == null))
        val needDocuments = documentRepository != null && ((active && activeSection == ActiveArea.HUB) ||
            (preparation && area == PreparationArea.OVERVIEW && selectedPerson == null))
        val needMemoryHeaders=workspaceRepository!=null && memoryReportRepository!=null && active && activeSection==ActiveArea.MEMORY_REPORTS && memoryReports.mode==MemoryReportMode.LIST && memoryReports.loading && memoryReports.headers==null
        val needTimeline=workspaceRepository!=null && protocolRepository!=null && active && activeSection==ActiveArea.PROTOCOL && protocol.loading && protocol.timeline==null && !protocol.nested
        val needMaterialBoard=workspaceRepository!=null && materialRepository!=null && ((active && activeSection==ActiveArea.MATERIAL) || (preparation && area==PreparationArea.MATERIAL)) && material.available && material.loading && material.board==null && !material.form && material.selected==null
        val needExpenseBoard=workspaceRepository!=null && expenseRepository!=null && ((active && activeSection==ActiveArea.COSTS) || (preparation && area==PreparationArea.COSTS)) && expenses.available && expenses.loading && expenses.board==null && !expenses.form && expenses.selected==null
        var documentSummary = previous?.documentSummary
        var documentsDone = !needDocuments
        val needMemory = memoryReportRepository != null && active && activeSection == ActiveArea.HUB
        var memorySummary = previous?.memoryReportSummary
        var memoryDone = !needMemory
        var expenseSummary = previous?.expenseSummary
        var expensesDone = !needExpenses
        var materialSummary = previous?.materialSummary
        var materialDone = !needMaterial
        var protocolSummary = previous?.protocolSummary
        var protocolDone = !needProtocol
        var workspaceAccess:WorkspaceAccess?=null
        var metadata = previous?.leadershipState
        var metadataDone = !needMetadata
        var people: List<Person>? = null
        var ops: OperationalBoard? = null
        var taskBoard: TaskBoard? = if (needTasks) null else previous?.tasks
        var loadedMemoryHeaders:List<MemoryReportHeader>?=null
        var loadedTimeline:List<ProtocolTimelineItem>?=null
        var loadedMaterial:MaterialBoard?=null
        var loadedExpenses:ExpenseBoard?=null
        var published=false
        var peopleDone = false
        var tasksDone = !needTasks
        fun current() = snapshot.state == SessionState.READY && snapshot.generation == generation &&
            selected?.id == action.id && selected?.status == action.status && request == workspaceRequest &&
            (!preparation || preparationArea == area) && (!active || activeArea == activeSection)
        fun failed() {
            if (!current()) return
            workspaceRequest++; personsLoading = false; workspaceRefreshing = false; workspaceError = true
            if(expectedAction!=null)detailLoading=false
        }
        fun publish() {
            if ((workspaceRepository!=null && published) || !current() || !peopleDone || !tasksDone || !metadataDone || !protocolDone || !materialDone || !expensesDone || !memoryDone || !documentsDone) return
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                if(needMemoryHeaders && !memoryReports.acceptWorkspaceHeaders(action.id,generation,loadedMemoryHeaders!!)){failed();return@withMutableSnapshot}
                if(needTimeline && !protocol.acceptWorkspaceTimeline(action.id,generation,loadedTimeline!!)){failed();return@withMutableSnapshot}
                if(needMaterialBoard && !material.acceptWorkspaceBoard(action.id,generation,loadedMaterial!!)){failed();return@withMutableSnapshot}
                if(needExpenseBoard && !expenses.acceptWorkspaceBoard(action.id,generation,loadedExpenses!!)){failed();return@withMutableSnapshot}
                if(expectedAction!=null)detailLoading=false
                workspace = WorkspaceSnapshot(action.id, generation, people.orEmpty(), ops, taskBoard, action.status, metadata, protocolSummary, materialSummary, expenseSummary, memorySummary, documentSummary,workspaceAccess,loadedMaterial,loadedExpenses)
                personsLoading = false; workspaceRefreshing = false
                published=workspaceRepository!=null
                tasks.reconcileSelection()
            }
        }
        val combined=workspaceRepository
        if(combined!=null) {
            workspaceTicket?.cancel()
            val ticket=WorkspaceReadTicket().also{workspaceTicket=it}
            val parts=buildSet {
                if(needTasks)add(WorkspacePart.TASKS)
                if(needProtocol)add(WorkspacePart.PROTOCOL_SUMMARY)
                if(needMaterial)add(WorkspacePart.MATERIAL_SUMMARY)
                if(needExpenses)add(WorkspacePart.EXPENSE_SUMMARY)
                if(needMemory)add(WorkspacePart.MEMORY_SUMMARY)
                if(needDocuments)add(WorkspacePart.DOCUMENT_SUMMARY)
                if(needMemoryHeaders)add(WorkspacePart.MEMORY_HEADERS)
                if(needTimeline)add(WorkspacePart.PROTOCOL_TIMELINE)
                if(needMaterialBoard)add(WorkspacePart.MATERIAL_BOARD)
                if(needExpenseBoard)add(WorkspacePart.EXPENSE_BOARD)
                if(needMetadata)add(WorkspacePart.LEADERSHIP_STATE)
            }
            combined.read(WorkspaceReadRequest(action.id,action.status,generation,parts,expectedAction,ticket)) { result ->
                if(!current() || published)return@read
                when(result) {
                    is WorkspaceResult.Rejected->{
                        workspaceRefusal=result.reason
                        if(expectedAction!=null && onOpeningRejected!=null){workspaceRequest++;onOpeningRejected()}
                        else failed()
                    }
                    is WorkspaceResult.Value->{
                        val loaded=result.value
                        if(loaded.actionId!=action.id || loaded.status!=action.status || loaded.generation!=generation || loaded.parts!=parts ||
                            loaded.operations.actionId!=action.id || loaded.operations.status!=action.status ||
                            (needTasks && loaded.tasks?.actionId!=action.id) || (needProtocol && loaded.protocol==null) ||
                            (needMaterial && loaded.material==null) || (needExpenses && loaded.expenses==null) ||
                            (needMemory && loaded.memory==null) || (needDocuments && loaded.documents==null) || (needMemoryHeaders && loaded.memoryHeaders==null) ||
                            (needTimeline && loaded.protocolTimeline==null) || (needMaterialBoard && loaded.materialBoard==null) || (needExpenseBoard && loaded.expenseBoard==null)) {failed();return@read}
                        workspaceAccess=loaded.access
                        if(needMetadata && loaded.access!=null){
                            metadata=loaded.leadershipState ?: run{failed();return@read};metadataDone=true
                        }
                        if(needMemoryHeaders)loadedMemoryHeaders=loaded.memoryHeaders
                        if(needTimeline)loadedTimeline=loaded.protocolTimeline
                        if(needMaterialBoard)loadedMaterial=loaded.materialBoard
                        if(needExpenseBoard)loadedExpenses=loaded.expenseBoard
                        ops=loaded.operations;people=loaded.operations.persons.map{it.person};peopleDone=true
                        if(needTasks)taskBoard=loaded.tasks
                        if(needProtocol)protocolSummary=loaded.protocol
                        if(needMaterial)materialSummary=loaded.material
                        if(needExpenses)expenseSummary=loaded.expenses
                        if(needMemory)memorySummary=loaded.memory
                        if(needDocuments)documentSummary=loaded.documents
                        tasksDone=true;protocolDone=true;materialDone=true;expensesDone=true;memoryDone=true;documentsDone=true
                        if(expectedAction!=null && needMetadata && loaded.access==null)leadership!!.actionState(action.id){metadataResult->
                            if(current()){metadata=metadataResult;metadataDone=true;publish()}
                        }
                        if(needMetadata && expectedAction==null && loaded.access==null)leadership!!.actionState(action.id){metadataResult->
                            if(current()){metadata=metadataResult;metadataDone=true;publish()}
                        }
                        publish()
                    }
                }
            }
            // Preserve the independent Leadership metadata/revoke contract. No P3b data in the batch.
            if(needMetadata && expectedAction==null && current() && WorkspacePart.LEADERSHIP_STATE !in parts)leadership!!.actionState(action.id) {result->
                if(current()){metadata=result;metadataDone=true;publish()}
            }
            return
        }
        if (operationsRepository != null) operationsRepository.board(action.id) { result ->
            if (!current()) return@board
            when (result) {
                is OperationsResult.Value -> {
                    if (result.value.actionId != action.id || result.value.status != action.status) { failed(); return@board }
                    ops = result.value; people = result.value.persons.map { it.person }; peopleDone = true; publish()
                }
                is OperationsResult.Rejected -> failed()
            }
        } else personRepository.listPersons(action.id) { result ->
            if (!current()) return@listPersons
            when (result) {
                is PersonResult.Value -> { people = result.value; peopleDone = true; publish() }
                is PersonResult.Rejected -> { personError = result.reason; failed() }
            }
        }
        if (needMetadata && current()) leadership.actionState(action.id) { result ->
            if (!current()) return@actionState
            metadata = result; metadataDone = true; publish()
        }
        if (needProtocol && current()) protocolRepository.summary(action.id) { result ->
            if (!current()) return@summary
            when (result) {
                is ProtocolResult.Value -> { protocolSummary = result.value; protocolDone = true; publish() }
                is ProtocolResult.Rejected -> failed()
            }
        }
        if (needMaterial && current()) materialRepository.summary(action.id) { result ->
            if (!current()) return@summary
            when (result) {
                is OperationsResult.Value -> { materialSummary = result.value; materialDone = true; publish() }
                is OperationsResult.Rejected -> failed()
            }
        }
        if (needExpenses && current()) expenseRepository.summary(action.id) { result ->
            if (!current()) return@summary
            when (result) {
                is OperationsResult.Value -> { expenseSummary = result.value; expensesDone = true; publish() }
                is OperationsResult.Rejected -> failed()
            }
        }
        if (needDocuments && current()) documentRepository!!.summary(action.id) { result ->
            if (!current()) return@summary
            when(result) {
                is DocumentResult.Value -> { documentSummary=result.value; documentsDone=true; publish() }
                is DocumentResult.Rejected -> failed()
            }
        }
        if (needMemory && current()) memoryReportRepository.summary(action.id) { result ->
            if (!current()) return@summary
            when (result) {
                is OperationsResult.Value -> { memorySummary = result.value; memoryDone = true; publish() }
                is OperationsResult.Rejected -> failed()
            }
        }
        if (needTasks && current()) taskRepository.board(action.id) { result ->
            if (!current()) return@board
            when (result) {
                is OperationsResult.Value -> {
                    if (result.value.actionId != action.id) { failed(); return@board }
                    taskBoard = result.value; tasksDone = true; publish()
                }
                is OperationsResult.Rejected -> failed()
            }
        }
    }
    fun addPerson() {
        interaction()
        if (personsEditable && !personSaving && !saving) {
            val failure=applyWorkspaceCopy(ActiveWorkspaceBudget.Units(objects=4,references=16)) { personRequest++; personDetailLoading = false; clearSensitive(); selectedPerson = null; editingPerson = null; personForm = true; personError = null }
            if(failure!=null)personError=if(failure==OperationsFailure.BUDGET_REFUSED)PersonRejection.BUDGET_REFUSED else PersonRejection.NOT_ALLOWED
        }
    }
    fun personDetail(id: String) {
        val action = selected?.takeIf { personsVisible && !personSaving } ?: return
        val generation = snapshot.generation
        interaction()
        if (!personsVisible || snapshot.generation != generation || selected?.id != action.id || personSaving) return
        val request = ++personRequest
        clearSensitive(); selectedPerson = null; personDetailLoading = true
        val sensitiveToken = sensitiveRequest
        fun current() = request == personRequest && selected?.id == action.id && personsVisible &&
            snapshot.generation == generation && sensitiveToken == sensitiveRequest
        val held=workspaceValue
        if(held?.access!=null){
            val accepted=held.access.publish(action.id,action.status,generation){
                if(current() && workspaceValue===held){
                    val person=held.persons.singleOrNull{it.id==id && it.actionId==action.id}
                    personDetailLoading=false;selectedPerson=person
                    personError=if(person==null)PersonRejection.NOT_FOUND else null
                }
            }
            if(!accepted){personDetailLoading=false;personError=PersonRejection.NOT_ALLOWED}
            else if(selectedPerson!=null)refreshSensitive()
            return
        }
        personRepository.getPerson(action.id, id) { result ->
            if (!current()) return@getPerson
            when (result) {
                is PersonResult.Value -> {
                    val person = result.value
                    if (person == null) { personDetailLoading = false; personError = PersonRejection.NOT_FOUND }
                    else if (leadership == null) { selectedPerson = person; personDetailLoading = false; personError = null }
                    else {
                        sensitiveInventoryLoading = true
                        leadership.inventory(action.id, person.id) { inventory ->
                            if (!current()) return@inventory
                            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                sensitiveInventoryLoading = false
                                when (inventory) {
                                    is SensitiveResult.Value -> sensitiveInventory = inventory.value
                                    is SensitiveResult.Rejected -> sensitiveError = inventory.reason
                                }
                                selectedPerson = person; personDetailLoading = false; personError = null
                            }
                        }
                    }
                }
                is PersonResult.Rejected -> { personDetailLoading = false; personError = result.reason }
            }
        }
    }
    fun editPerson() {
        interaction()
        if (personsEditable && !personSaving) selectedPerson?.let { person->
            val failure=applyWorkspaceCopy(ActiveWorkspaceBudget.Units(objects=4,references=16)){clearSensitive();editingPerson=person;personForm=true;personError=null}
            if(failure!=null)personError=if(failure==OperationsFailure.BUDGET_REFUSED)PersonRejection.BUDGET_REFUSED else PersonRejection.NOT_ALLOWED
        }
    }
    fun cancelPerson() {
        interaction()
        if (!personSaving) { personForm = false; editingPerson = null; personError = null; refreshSensitive() }
    }
    fun backToPersons() {
        interaction()
        if (!personSaving) { personRequest++; personDetailLoading = false; clearSensitive(); selectedPerson = null; personError = null }
    }
    // Only an explicit user action replaces the editor-open snapshot and draft.
    var personEditorRevision by mutableStateOf(0L); private set
    fun reloadPersonEditor() {
        interaction()
        val action = selected?.takeIf { personsEditable && personForm && !personSaving } ?: return
        val expected = editingPerson ?: return
        if (personError != PersonRejection.STALE) return
        val request = ++personRequest
        val generation = snapshot.generation
        personSaving = true
        var delivered = false
        personRepository.getPerson(action.id, expected.id) { result ->
            if (delivered) return@getPerson
            delivered = true
            if (request != personRequest || snapshot.state != SessionState.READY || snapshot.generation != generation ||
                selected?.id != action.id || !personsEditable || !personForm || editingPerson != expected) return@getPerson
            personSaving = false
            when (result) {
                is PersonResult.Value -> {
                    val fresh = result.value
                    if (fresh == null) personError = PersonRejection.NOT_FOUND
                    else if (fresh.id != expected.id || fresh.actionId != action.id) personError = PersonRejection.STALE
                    else { editingPerson = fresh; selectedPerson = fresh; personEditorRevision++; personError = null }
                }
                is PersonResult.Rejected -> personError = result.reason
            }
        }
    }
    fun savePerson(pseudonym: String) {
        interaction()
        val action = selected?.takeIf { personsEditable && personForm && !personSaving } ?: return
        if (!PersonRules.valid(pseudonym)) { personError = PersonRejection.INVALID_PSEUDONYM; return }
        val request = personRequest
        val generation = snapshot.generation
        personSaving = true
        feedback = null
        val publish: (PersonResult<Person>) -> Unit = { result ->
            if (request == personRequest && selected?.id == action.id && personsVisible) {
                personSaving = false
                when (result) {
                    is PersonResult.Value -> {
                        loadPersons()
                        if (snapshot.state == SessionState.READY && snapshot.generation == generation && selected?.id == action.id)
                            feedback = StatusFeedback(++feedbackId, "Person gespeichert")
                    }
                    is PersonResult.Rejected -> personError = result.reason
                }
            }
        }
        val existing = editingPerson
        if (existing == null) personRepository.createPerson(action.id, pseudonym, publish)
        else personRepository.updatePerson(action.id, existing, pseudonym, publish)
    }
    var sensitiveInventory by mutableStateOf<SensitiveInventory?>(null); private set
    var sensitiveInventoryLoading by mutableStateOf(false); private set
    var leadershipRecovery by mutableStateOf<LeadershipRecoveryRequest?>(null); private set
    var leadershipRecoveryPhase by mutableStateOf<LeadershipRecoveryPhase?>(null); private set
    var leadershipRecoveryError by mutableStateOf(false); private set
    private var recoveryStarting=false
    private var recoveryBeforeGeneration=-1L
    private var recoveryReadyGeneration=-1L
    val leadershipRecoveryAction get()=actions.singleOrNull{it.id==leadershipRecovery?.actionId}
    fun cancelLeadershipRecovery() {
        leadershipRecovery?.cancel();leadershipRecovery=null;leadershipRecoveryPhase=null
        recoveryBeforeGeneration=-1;recoveryReadyGeneration=-1
    }
    fun cancelLeadershipRecovery(expected:LeadershipRecoveryRequest) {
        if(leadershipRecovery===expected)cancelLeadershipRecovery()
    }
    fun dismissLeadershipRecoveryError(){leadershipRecoveryError=false}
    fun requestLeadershipRecovery(expectedActionId:String,generation:Long) {
        if(selected?.id!=expectedActionId || snapshot.generation!=generation)return
        interaction()
        if(selected?.id!=expectedActionId || snapshot.generation!=generation)return
        val action=selected?:return
        val port=leadership?:return
        if(!personsEditable || sensitiveBusy || leadershipRecovery!=null)return
        val request=++sensitiveRequest
        sensitiveBusy=true;leadershipRecoveryError=false
        port.prepareRecovery(action.id){result->
            if(request!=sensitiveRequest || snapshot.state!=SessionState.READY || snapshot.generation!=generation || selected?.id!=action.id) {
                (result as? SensitiveResult.Value)?.value?.cancel();return@prepareRecovery
            }
            sensitiveBusy=false
            when(result) {
                is SensitiveResult.Value->{leadershipRecovery=result.value;leadershipRecoveryPhase=LeadershipRecoveryPhase.CONFIRM_REAUTH}
                is SensitiveResult.Rejected->leadershipRecoveryError=true
            }
        }
    }
    fun authenticateLeadershipRecovery(expected:LeadershipRecoveryRequest,generation:Long) {
        if(leadershipRecovery!==expected || snapshot.generation!=generation)return
        interaction()
        if(leadershipRecovery!==expected || snapshot.generation!=generation || snapshot.state!=SessionState.READY || leadershipRecoveryPhase!=LeadershipRecoveryPhase.CONFIRM_REAUTH ||
            selected?.id!=leadershipRecovery?.actionId || !personsEditable)return
        recoveryBeforeGeneration=snapshot.generation
        leadershipRecoveryPhase=LeadershipRecoveryPhase.AUTHENTICATING
        recoveryStarting=true
        try {
            documentPicker?.cancel();session.lock()
            if(session.snapshot().state==SessionState.LOCKED)unlock()
        }finally{recoveryStarting=false}
    }
    fun confirmLeadershipRecovery(expected:LeadershipRecoveryRequest,displayedGeneration:Long) {
        if(leadershipRecovery!==expected || snapshot.generation!=displayedGeneration)return
        interaction()
        val intent=leadershipRecovery?.takeIf{it===expected}?:return
        if(snapshot.generation!=displayedGeneration)return
        val port=leadership?:return
        if(snapshot.state!=SessionState.READY || snapshot.generation!=recoveryReadyGeneration ||
            leadershipRecoveryPhase!=LeadershipRecoveryPhase.CONFIRM_RESET ||
            leadershipRecoveryAction?.status!=ActionStatus.PREPARATION)return
        val generation=snapshot.generation
        leadershipRecoveryPhase=LeadershipRecoveryPhase.WRITING;sensitiveBusy=true
        port.recover(intent){result->
            if(leadershipRecovery!==intent || leadershipRecoveryPhase!=LeadershipRecoveryPhase.WRITING ||
                snapshot.state!=SessionState.READY || snapshot.generation!=generation)return@recover
            sensitiveBusy=false
            cancelLeadershipRecovery()
            when(result) {
                is SensitiveResult.Value->showFeedback(if(intent.state==SensitiveState.INCOMPLETE_SETUP)
                    "Unvollständige Einrichtung zurückgesetzt" else "Entfernung des Leitungsschutzes abgeschlossen")
                is SensitiveResult.Rejected->leadershipRecoveryError=true
            }
        }
    }
    var sensitiveSetup by mutableStateOf(false); private set
    var sensitiveArea by mutableStateOf<SensitiveArea?>(null); private set
    var sensitiveOpened by mutableStateOf<SensitiveOpened?>(null); private set
    var sensitiveBusy by mutableStateOf(false); private set
    var sensitiveError by mutableStateOf<SensitiveFailure?>(null); private set
    private var sensitiveRequest = 0L
    private fun clearSensitive() {
        if(leadershipRecoveryPhase!=LeadershipRecoveryPhase.AUTHENTICATING)cancelLeadershipRecovery()
        sensitiveRequest++; leadership?.revoke(); sensitiveOpened?.grant?.cancel()
        sensitiveInventory = null; sensitiveInventoryLoading = false; sensitiveSetup = false; sensitiveArea = null
        sensitiveOpened = null; sensitiveBusy = false; sensitiveError = null
    }
    private fun refreshSensitive() {
        val port = leadership ?: return
        val action = selected?.takeIf { personsVisible } ?: return; val person = selectedPerson ?: return
        val generation = snapshot.generation
        val navigation = personRequest
        val request = ++sensitiveRequest
        sensitiveInventory = null; sensitiveError = null; sensitiveInventoryLoading = true
        val scope=workspaceValue?.access
        val receive:(SensitiveResult<SensitiveInventory>)->Unit=receive@{ result ->
            if (request != sensitiveRequest || navigation != personRequest || !personsVisible ||
                snapshot.generation != generation || selected?.id != action.id || selectedPerson?.id != person.id) return@receive
            sensitiveInventoryLoading = false
            when(result) {
                is SensitiveResult.Value -> sensitiveInventory = result.value
                is SensitiveResult.Rejected -> sensitiveError = result.reason
            }
        }
        if(scope!=null){
            if(!scope.publish(action.id,action.status,generation){}){sensitiveInventoryLoading=false;sensitiveError=SensitiveFailure.DENIED;return}
            port.workspaceInventory(action.id,person.id,scope,receive)
        }else port.inventory(action.id,person.id,receive)
    }
    fun requestActionProtectionSetup() {
        val before = snapshot
        val id = selected?.id
        interaction()
        val state = (workspace?.leadershipState as? SensitiveResult.Value)?.value
        if (preparationAvailable && snapshot.generation == before.generation && selected?.id == id &&
            preparationArea == PreparationArea.OVERVIEW && state == SensitiveState.UNINITIALIZED && !sensitiveBusy) {
            clearSensitive()
            if (preparationAvailable && snapshot.generation == before.generation && selected?.id == id)
                sensitiveSetup = true
        }
    }
    fun requestSensitiveSetup() {
        interaction()
        if (personsEditable && sensitiveInventory?.state == SensitiveState.UNINITIALIZED && !sensitiveBusy) {
            sensitiveSetup = true; sensitiveError = null
        }
    }
    fun setupSensitive(passphrase: CharArray, confirmation: CharArray) {
        interaction()
        val action = selected
        if (action == null || !personsEditable || !sensitiveSetup || sensitiveBusy || leadership == null) {
            passphrase.fill('\u0000'); confirmation.fill('\u0000'); return
        }
        sensitiveBusy = true; val request = sensitiveRequest
        leadership.setup(action.id, passphrase, confirmation) { result ->
            if (request != sensitiveRequest || !personsVisible) return@setup
            sensitiveBusy = false
            when(result) {
                is SensitiveResult.Value -> { sensitiveSetup = false; sensitiveError = null }
                is SensitiveResult.Rejected -> sensitiveError = result.reason
            }
            if (selectedPerson == null && preparationAvailable) loadWorkspace() else refreshSensitive()
        }
    }
    fun requestSensitive(area: SensitiveArea) {
        interaction()
        if (!personsVisible || sensitiveBusy || sensitiveInventory?.state != SensitiveState.READY) return
        if (!personsEditable && area !in sensitiveInventory!!.areas) return
        sensitiveRequest++; leadership?.revoke(); sensitiveOpened?.grant?.cancel()
        sensitiveOpened = null; sensitiveArea = area; sensitiveError = null
    }
    fun openSensitive(passphrase: CharArray) {
        interaction()
        val action=selected; val person=selectedPerson; val area=sensitiveArea
        if (action==null || person==null || area==null || !personsVisible || sensitiveBusy || leadership==null) {
            passphrase.fill('\u0000'); return
        }
        sensitiveBusy=true; val request=sensitiveRequest
        leadership.open(action.id,person.id,area,passphrase) { result ->
            if (request!=sensitiveRequest || !personsVisible || selectedPerson?.id!=person.id) {
                if(result is SensitiveResult.Value) result.value.grant.cancel()
                return@open
            }
            sensitiveBusy=false
            when(result) {
                is SensitiveResult.Value -> if(result.value.grant.valid()) { sensitiveOpened=result.value; sensitiveError=null }
                is SensitiveResult.Rejected -> sensitiveError=result.reason
            }
        }
    }
    fun saveSensitive(data: SensitiveData) {
        interaction()
        val opened=sensitiveOpened ?: return
        if(!personsEditable || sensitiveBusy || !opened.grant.valid()) { checkSensitiveExpiry(); return }
        sensitiveBusy=true; val request=sensitiveRequest
        leadership?.save(opened.grant,data) { result ->
            if(request!=sensitiveRequest || !personsVisible) return@save
            sensitiveBusy=false
            sensitiveOpened=null
            when(result) {
                is SensitiveResult.Value -> { sensitiveArea=null; sensitiveError=null; refreshSensitive() }
                is SensitiveResult.Rejected -> sensitiveError=result.reason
            }
        }
    }
    fun closeSensitive() {
        interaction()
        clearSensitive()
        if (selectedPerson == null && preparationAvailable) loadWorkspace() else refreshSensitive()
    }
    fun checkSensitiveExpiry() {
        val opened=sensitiveOpened ?: return
        if(!opened.grant.valid()) {
            leadership?.revoke(); sensitiveRequest++; sensitiveOpened=null; sensitiveBusy=false
            sensitiveError=SensitiveFailure.DENIED
        }
    }
    private fun acceptSession(next:SessionSnapshot) {
        var recoveryAuthenticated=false
        if(leadershipRecovery!=null) {
            when {
                next.state==SessionState.READY && leadershipRecoveryPhase==LeadershipRecoveryPhase.AUTHENTICATING -> {
                    if(next.generation>recoveryBeforeGeneration) {
                        recoveryReadyGeneration=next.generation
                        recoveryAuthenticated=true
                    }else cancelLeadershipRecovery()
                }
                next.state in setOf(SessionState.LOCKED,SessionState.BLOCKED) && !recoveryStarting -> cancelLeadershipRecovery()
                next.state!=SessionState.READY && leadershipRecoveryPhase!=LeadershipRecoveryPhase.AUTHENTICATING -> cancelLeadershipRecovery()
                next.state==SessionState.READY && snapshot.generation!=next.generation && leadershipRecoveryPhase!=LeadershipRecoveryPhase.AUTHENTICATING -> cancelLeadershipRecovery()
            }
        }

        val entered = next.state == SessionState.READY && (snapshot.state != SessionState.READY || snapshot.generation != next.generation)
        val generationChanged = snapshot.generation != next.generation
        snapshot = next
        if (next.state == SessionState.READY) bootstrap = ProductStorageState.READY_TO_OPEN
        if (next.state != SessionState.READY || generationChanged) {
            actionRequest++
            feedback = null
            remainingMillis = null
            actionListState = if (next.state == SessionState.BLOCKED) ActionListState.Error else ActionListState.Loading
            selected = null; creating = false; editing = null; confirming = null
            changeError = null; saving = false
            clearPersons()
        }
        if (entered) {
            // Obtain the projection before the first READY composition, never from an effect later.
            refreshCountdown()
            if (snapshot.state == SessionState.READY && snapshot.generation == next.generation) loadActions()
        }
        if(recoveryAuthenticated && leadershipRecovery!=null && snapshot.state==SessionState.READY &&
            snapshot.generation==recoveryReadyGeneration)leadershipRecoveryPhase=LeadershipRecoveryPhase.CONFIRM_RESET
    }
    private val subscription = session.observe { next ->
        // Preserve the existing synchronous unbound/test port contract. MainActivity always
        // binds the core port and its UI dispatcher before product authentication begins.
        if(Thread.currentThread() === ownerThread || activeCorePort==null) acceptSession(next)
        else {
            dropCoreAuthority()
            runCatching { ownerDispatch { if(session.snapshot()==next)acceptSession(next) } }
        }
    }
    fun unlock() = begin(bootstrap == ProductStorageState.EMPTY)
    /** Explicit lock followed by the ordinary unlock. No navigation or editor continuation. */
    fun restartSession() {
        val before = session.snapshot()
        if (!sessionRestartVisible(before.state, session.remainingAbsoluteMillis())) return
        interaction()
        // Interaction/clock/environment checks may synchronously revoke the session.
        val current = session.snapshot()
        if (current.state != SessionState.READY || current.generation != before.generation) return
        lock()
        // Lock observers complete all existing cleanup before entering the normal auth path.
        if (session.snapshot().state == SessionState.LOCKED) unlock()
    }
    fun lock() { cancelLeadershipRecovery();documentPicker?.cancel(); session.lock() }
    fun interaction() = session.userInteraction()
    fun createScreen() { cancelLeadershipRecovery();interaction(); if (snapshot.state == SessionState.READY) { clearPersons();selected = null; creating = true } }
    fun back() { cancelLeadershipRecovery();documentPicker?.cancel(); if (memoryReports.available && memoryReports.dirty) { memoryReports.requestLeave { back() }; return }; interaction(); selected = null; creating = false; editing = null; confirming = null; changeError = null; clearPersons() }
    fun edit() {
        interaction()
        selected?.takeIf { snapshot.state == SessionState.READY && ActionLifecycle.mayEdit(it.status) }?.let {
            editing = it; changeError = null
        }
    }
    fun cancelEdit() { interaction(); if (!saving) { editing = null; changeError = null } }
    fun requestAdvance() {
        interaction()
        selected?.takeIf { snapshot.state == SessionState.READY && ActionLifecycle.next(it.status) != null }?.let {
            confirming = it; changeError = null
        }
    }
    fun cancelAdvance() { interaction(); if (!saving) confirming = null }
    fun confirmAdvance() {
        interaction()
        val expected = confirming ?: return
        if (saving || snapshot.state != SessionState.READY) return
        saving = true
        repository.advanceActionStatus(expected.id, expected.status, ::changed)
    }
    private fun changed(result: ActionChangeResult) {
        saving = false; confirming = null
        when (result) {
            is ActionChangeResult.Applied -> {
                editing = null; changeError = null; selected = result.action
                loadPersons()
                loadActions()
            }
            is ActionChangeResult.Rejected -> changeError = result.reason
        }
    }
    fun detail(id: String) {
        cancelLeadershipRecovery()
        interaction()
        if(snapshot.state==SessionState.READY)openAction(id,true)
    }
    private fun openAction(id:String,allowCombined:Boolean) {
        clearPersons(); selected = null; detailLoading = true
        creating=false;editing=null;confirming=null;changeError=null
        val generation = snapshot.generation
        val request = personRequest
        val candidate=actions.singleOrNull{it.id==id}
        if(allowCombined && workspaceRepository!=null && candidate!=null && PersonRules.mayRead(candidate.status)) {
            selected=candidate
            readWorkspace(candidate){
                if(snapshot.state==SessionState.READY && snapshot.generation==generation && personRequest==request)openAction(id,false)
            }
            return
        }
        repository.getAction(id) {
            if (snapshot.state != SessionState.READY || snapshot.generation != generation || request != personRequest) return@getAction
            selected = it; detailLoading = false; creating = false; editing = null; confirming = null; changeError = null
            loadPersons()
        }
    }

    fun save(input: NewAction) {
        interaction()
        if (snapshot.state != SessionState.READY || saving || !input.valid()) return
        val expected = editing
        if (expected != null) {
            saving = true
            repository.updateAction(expected, input, ::changed)
            return
        }
        saving = true
        repository.createAction(input) { result ->
            saving = false; creating = false; selected = result
            loadActions()
        }
    }
    override fun onCleared() { cancelLeadershipRecovery();documentPicker?.cancel();clearPersons();selected=null;subscription.cancel();session.lock() }
}
