package org.pag.prototype.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.pag.domain.Experiment
import org.pag.domain.SessionPort

class PrototypeViewModel(private val session: SessionPort) : ViewModel() {
    var snapshot by mutableStateOf(session.snapshot())
        private set
    private val subscription = session.observe { snapshot = it }
    fun authenticate(experiment: Experiment) = session.authenticate(experiment)
    fun userInteraction() = session.userInteraction()
    fun testAccess() { session.userInteraction(); session.testAccess() }
    fun lock() = session.lock()
    override fun onCleared() {
        session.lock()
        subscription.cancel()
        snapshot = session.snapshot()
    }
}
