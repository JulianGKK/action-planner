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
    operationsRepository: OperationsRepository? = null,
    taskRepository: TaskRepository? = null, private val begin: (Boolean) -> Unit) : ViewModel() {
    var bootstrap by mutableStateOf(initialBootstrap); private set
    fun updateBootstrap(state: ProductStorageState) { bootstrap = state }
    var snapshot by mutableStateOf(session.snapshot()); private set
    var actions by mutableStateOf<List<Action>>(emptyList()); private set
    var selected by mutableStateOf<Action?>(null); private set
    var creating by mutableStateOf(false); private set
    var editing by mutableStateOf<Action?>(null); private set
    var confirming by mutableStateOf<Action?>(null); private set
    var changeError by mutableStateOf<ActionChangeRejection?>(null); private set
    var saving by mutableStateOf(false); private set
    var persons by mutableStateOf<List<Person>>(emptyList()); private set
    var personsLoading by mutableStateOf(false); private set
    var selectedPerson by mutableStateOf<Person?>(null); private set
    var personForm by mutableStateOf(false); private set
    var editingPerson by mutableStateOf<Person?>(null); private set
    var personError by mutableStateOf<PersonRejection?>(null); private set
    var personSaving by mutableStateOf(false); private set
    private var personRequest = 0L // Navigation ordering only, never session authority.
    val personsVisible get() = snapshot.state == SessionState.READY && selected?.let { PersonRules.mayRead(it.status) } == true
    val personsEditable get() = snapshot.state == SessionState.READY && selected?.let { PersonRules.mayManage(it.status) } == true
    val tasks = TaskModel(taskRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive)
    val operations = OperationsModel(operationsRepository, { snapshot }, { selected }, ::interaction, ::clearSensitive)
    private fun clearPersons() {
        tasks.clear(); operations.clear(); clearSensitive(); personRequest++; persons = emptyList(); personsLoading = false; selectedPerson = null
        personForm = false; editingPerson = null; personError = null; personSaving = false
    }
    private fun loadPersons() {
        clearPersons()
        val action = selected?.takeIf { personsVisible } ?: return
        val request = personRequest
        personsLoading = true
        personRepository.listPersons(action.id) { result ->
            if (request != personRequest || selected?.id != action.id || !personsVisible) return@listPersons
            personsLoading = false
            when (result) {
                is PersonResult.Value -> { persons = result.value; operations.refresh(); tasks.refresh() }
                is PersonResult.Rejected -> personError = result.reason
            }
        }
    }
    fun addPerson() {
        interaction()
        if (personsEditable && !personSaving && !saving) { clearSensitive(); selectedPerson = null; editingPerson = null; personForm = true; personError = null }
    }
    fun personDetail(id: String) {
        interaction()
        val action = selected?.takeIf { personsVisible && !personSaving } ?: return
        val request = ++personRequest
        personRepository.getPerson(action.id, id) { result ->
            if (request != personRequest || selected?.id != action.id || !personsVisible) return@getPerson
            when (result) {
                is PersonResult.Value -> { clearSensitive(); selectedPerson = result.value; personError = if (result.value == null) PersonRejection.NOT_FOUND else null; refreshSensitive() }
                is PersonResult.Rejected -> personError = result.reason
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
        if (!personSaving) { clearSensitive(); selectedPerson = null; personError = null }
    }
    fun savePerson(pseudonym: String) {
        interaction()
        val action = selected?.takeIf { personsEditable && personForm && !personSaving } ?: return
        if (!PersonRules.valid(pseudonym)) { personError = PersonRejection.INVALID_PSEUDONYM; return }
        val request = personRequest
        personSaving = true
        val publish: (PersonResult<Person>) -> Unit = { result ->
            if (request == personRequest && selected?.id == action.id && personsVisible) {
                personSaving = false
                when (result) {
                    is PersonResult.Value -> loadPersons()
                    is PersonResult.Rejected -> personError = result.reason
                }
            }
        }
        val existing = editingPerson
        if (existing == null) personRepository.createPerson(action.id, pseudonym, publish)
        else personRepository.updatePerson(action.id, existing.id, pseudonym, publish)
    }
    var sensitiveInventory by mutableStateOf<SensitiveInventory?>(null); private set
    var sensitiveSetup by mutableStateOf(false); private set
    var sensitiveArea by mutableStateOf<SensitiveArea?>(null); private set
    var sensitiveOpened by mutableStateOf<SensitiveOpened?>(null); private set
    var sensitiveBusy by mutableStateOf(false); private set
    var sensitiveError by mutableStateOf<SensitiveFailure?>(null); private set
    private var sensitiveRequest = 0L
    private fun clearSensitive() {
        sensitiveRequest++; leadership?.revoke(); sensitiveOpened?.grant?.cancel()
        sensitiveInventory = null; sensitiveSetup = false; sensitiveArea = null
        sensitiveOpened = null; sensitiveBusy = false; sensitiveError = null
    }
    private fun refreshSensitive() {
        val action = selected ?: return; val person = selectedPerson ?: return
        val request = sensitiveRequest
        leadership?.inventory(action.id, person.id) { result ->
            if (request != sensitiveRequest || !personsVisible || selectedPerson?.id != person.id) return@inventory
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
        val entered = next.state == SessionState.READY && snapshot.state != SessionState.READY
        snapshot = next
        if (next.state == SessionState.READY) bootstrap = ProductStorageState.READY_TO_OPEN
        if (next.state != SessionState.READY) { actions = emptyList(); selected = null; creating = false; editing = null; confirming = null; changeError = null; saving = false; clearPersons() }
        if (entered) repository.listActions { actions = it }
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
                repository.listActions { actions = it }
            }
            is ActionChangeResult.Rejected -> changeError = result.reason
        }
    }
    fun detail(id: String) {
        interaction(); clearPersons()
        repository.getAction(id) { selected = it; creating = false; editing = null; confirming = null; changeError = null; loadPersons() }
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
            repository.listActions { actions = it }
        }
    }
    override fun onCleared() { clearSensitive(); subscription.cancel(); session.lock() }
}
