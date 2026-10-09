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
    private val subscription = session.observe { next ->
        val entered = next.state == SessionState.READY && snapshot.state != SessionState.READY
        snapshot = next
        if (next.state == SessionState.READY) bootstrap = ProductStorageState.READY_TO_OPEN
        if (next.state != SessionState.READY) { actions = emptyList(); selected = null; creating = false }
        if (entered) repository.listActions { actions = it }
    }
    fun unlock() = begin(bootstrap == ProductStorageState.EMPTY)
    fun lock() = session.lock()
    fun interaction() = session.userInteraction()
    fun createScreen() { interaction(); if (snapshot.state == SessionState.READY) { selected = null; creating = true } }
    fun back() { interaction(); selected = null; creating = false }
    fun detail(id: String) { interaction(); repository.getAction(id) { selected = it; creating = false } }
    fun save(input: NewAction) {
        interaction()
        if (input.valid()) repository.createAction(input) { result ->
            creating = false; selected = result
            repository.listActions { actions = it }
        }
    }
    override fun onCleared() { subscription.cancel(); session.lock() }
}
