package org.pag.prototype.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.pag.domain.*

/** Only domain ports; no DB, DAO, Cipher, key, saved state or retained unlock runtime. */
class ProductViewModel(private val session: SessionPort, private val repository: ActionRepository,
    initialBootstrap: ProductStorageState, private val personRepository: PersonRepository,
    private val leadership: LeadershipRepository? = null,
    private val operationsRepository: OperationsRepository? = null,
    private val taskRepository: TaskRepository? = null, private val begin: (Boolean) -> Unit) : ViewModel() {
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
    val personsEditable get() = snapshot.state == SessionState.READY && selected?.let { PersonRules.mayManage(it.status) } == true
    private var workspaceRequest = 0L
    var detailLoading by mutableStateOf(false); private set
    var personDetailLoading by mutableStateOf(false); private set
    var workspace by mutableStateOf<WorkspaceSnapshot?>(null); private set
    var workspaceError by mutableStateOf(false); private set
    val tasks = TaskModel(taskRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive,
        { workspace?.tasks }, ::loadWorkspace, ::showFeedback)
    val operations = OperationsModel(operationsRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive,
        { workspace?.operations }, ::loadWorkspace, ::showFeedback, ::returnedFromOperationalEdit)
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
    private fun clearPersons() {
        workspaceRequest++; workspace = null; workspaceError = false; detailLoading = false; personDetailLoading = false
        tasks.clear(); operations.clear(); clearSensitive(); personRequest++; personsLoading = false; selectedPerson = null
        personForm = false; editingPerson = null; personError = null; personSaving = false
    }
    private fun loadPersons() {
        clearSensitive(); personRequest++
        selectedPerson = null; personForm = false; editingPerson = null; personError = null; personSaving = false
        if (workspace?.status != selected?.status) {
            workspace = null; tasks.clear(); operations.clear()
        }
        loadWorkspace()
    }
    /** Stage short gated reads locally. Only a complete current snapshot enters Compose state. */
    internal fun loadWorkspace() {
        val action = selected?.takeIf { personsVisible } ?: return
        val generation = snapshot.generation
        val request = ++workspaceRequest
        val previous = workspace
        personsLoading = previous == null
        workspaceError = false
        val needTasks = taskRepository != null && operations.screen == OperationsScreen.OVERVIEW
        var people: List<Person>? = null
        var ops: OperationalBoard? = null
        var taskBoard: TaskBoard? = if (needTasks) null else previous?.tasks
        var peopleDone = false
        var tasksDone = !needTasks
        fun current() = snapshot.state == SessionState.READY && snapshot.generation == generation &&
            selected?.id == action.id && selected?.status == action.status && request == workspaceRequest
        fun failed() {
            if (!current()) return
            workspaceRequest++; personsLoading = false; workspaceError = true
        }
        fun publish() {
            if (!current() || !peopleDone || !tasksDone) return
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                workspace = WorkspaceSnapshot(action.id, generation, people.orEmpty(), ops, taskBoard, action.status)
                personsLoading = false
                tasks.reconcileSelection()
            }
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
        if (personsEditable && !personSaving && !saving) { personRequest++; personDetailLoading = false; clearSensitive(); selectedPerson = null; editingPerson = null; personForm = true; personError = null }
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
        if (personsEditable && !personSaving) selectedPerson?.let { clearSensitive(); editingPerson = it; personForm = true; personError = null }
    }
    fun cancelPerson() {
        interaction()
        if (!personSaving) { personForm = false; editingPerson = null; personError = null; refreshSensitive() }
    }
    fun backToPersons() {
        interaction()
        if (!personSaving) { personRequest++; personDetailLoading = false; clearSensitive(); selectedPerson = null; personError = null }
    }
    fun savePerson(pseudonym: String) {
        interaction()
        val action = selected?.takeIf { personsEditable && personForm && !personSaving } ?: return
        if (!PersonRules.valid(pseudonym)) { personError = PersonRejection.INVALID_PSEUDONYM; return }
        val request = personRequest
        val generation = snapshot.generation
        personSaving = true
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
        else personRepository.updatePerson(action.id, existing.id, pseudonym, publish)
    }
    var sensitiveInventory by mutableStateOf<SensitiveInventory?>(null); private set
    var sensitiveInventoryLoading by mutableStateOf(false); private set
    var sensitiveSetup by mutableStateOf(false); private set
    var sensitiveArea by mutableStateOf<SensitiveArea?>(null); private set
    var sensitiveOpened by mutableStateOf<SensitiveOpened?>(null); private set
    var sensitiveBusy by mutableStateOf(false); private set
    var sensitiveError by mutableStateOf<SensitiveFailure?>(null); private set
    private var sensitiveRequest = 0L
    private fun clearSensitive() {
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
        port.inventory(action.id, person.id) { result ->
            if (request != sensitiveRequest || navigation != personRequest || !personsVisible ||
                snapshot.generation != generation || selected?.id != action.id || selectedPerson?.id != person.id) return@inventory
            sensitiveInventoryLoading = false
            when(result) {
                is SensitiveResult.Value -> sensitiveInventory = result.value
                is SensitiveResult.Rejected -> sensitiveError = result.reason
            }
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
            refreshSensitive()
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
        clearSensitive(); refreshSensitive()
    }
    fun checkSensitiveExpiry() {
        val opened=sensitiveOpened ?: return
        if(!opened.grant.valid()) {
            leadership?.revoke(); sensitiveRequest++; sensitiveOpened=null; sensitiveBusy=false
            sensitiveError=SensitiveFailure.DENIED
        }
    }
    private val subscription = session.observe { next ->
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
    }
    fun unlock() = begin(bootstrap == ProductStorageState.EMPTY)
    fun lock() = session.lock()
    fun interaction() = session.userInteraction()
    fun createScreen() { interaction(); if (snapshot.state == SessionState.READY) { selected = null; creating = true } }
    fun back() { interaction(); selected = null; creating = false; editing = null; confirming = null; changeError = null; clearPersons() }
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
        interaction()
        if (snapshot.state != SessionState.READY) return
        clearPersons(); selected = null; detailLoading = true
        val generation = snapshot.generation
        val request = personRequest
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
    override fun onCleared() { clearSensitive(); subscription.cancel(); session.lock() }
}
