package org.pag.prototype.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.pag.domain.*

/** Only domain ports; no DB, DAO, Cipher, key, saved state or retained unlock runtime. */
class ProductViewModel(private val session: SessionPort, private val repository: ActionRepository,
    initialBootstrap: ProductStorageState, private val begin: (Boolean) -> Unit) : ViewModel() {
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
    private val subscription = session.observe { next ->
        val entered = next.state == SessionState.READY && snapshot.state != SessionState.READY
        snapshot = next
        if (next.state == SessionState.READY) bootstrap = ProductStorageState.READY_TO_OPEN
        if (next.state != SessionState.READY) { actions = emptyList(); selected = null; creating = false; editing = null; confirming = null; changeError = null; saving = false }
        if (entered) repository.listActions { actions = it }
    }
    fun unlock() = begin(bootstrap == ProductStorageState.EMPTY)
    fun lock() = session.lock()
    fun interaction() = session.userInteraction()
    fun createScreen() { interaction(); if (snapshot.state == SessionState.READY) { selected = null; creating = true } }
    fun back() { interaction(); selected = null; creating = false; editing = null; confirming = null; changeError = null }
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
                repository.listActions { actions = it }
            }
            is ActionChangeResult.Rejected -> changeError = result.reason
        }
    }
    fun detail(id: String) { interaction(); repository.getAction(id) { selected = it; creating = false; editing = null; confirming = null; changeError = null } }
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
    override fun onCleared() { subscription.cancel(); session.lock() }
}
