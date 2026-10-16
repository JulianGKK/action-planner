package org.pag.prototype.product

import org.pag.domain.DocumentSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Activity RAM only; no URI/permission/authentication is persisted or owned by the model. */
interface DocumentPicker {
    fun launch(actionId: String, generation: Long)
    fun available(actionId: String, generation: Long): Boolean
    fun unavailable(actionId: String): Boolean = false
    fun consume(actionId: String, generation: Long): DocumentSource?
    fun actionSelected(actionId: String) {}
    fun cancel()
}

/** Testable one-shot handoff. A returned source is never itself an import authorization. */
internal class PendingDocumentPicker<T> {
    private var changes by mutableStateOf(0L)
    private var unavailableAction: String? = null
    private var actionId: String? = null
    private var oldGeneration: Long? = null
    private var source: T? = null
    private var freshGeneration: Long? = null
    var awaitingResult = false; private set
    @Synchronized fun begin(action: String, generation: Long) { clear(); actionId=action; oldGeneration=generation; awaitingResult=true }
    @Synchronized fun returned(value: T?) {
        if (!awaitingResult) return
        awaitingResult=false
        if (value == null) discard() else { source=value; changes++ }
    }
    @Synchronized fun available(action: String, generation: Long): Boolean {
        changes // Observe availability without placing a URI in Compose or SavedState.
        return source != null && actionId == action && oldGeneration != generation && freshGeneration == generation
    }
    @Synchronized fun unavailable(action: String): Boolean { changes; return unavailableAction == action }
    @Synchronized fun consume(action: String, generation: Long): T? {
        if (!available(action,generation)) return null
        val result=source; clear(); return result
    }
    /** Only the original picker departure and the normal credential UI may retain the handoff. */
    @Synchronized fun leaving(authenticating: Boolean) { if (!awaitingResult && !authenticating) discard() }
    @Synchronized fun ready(generation: Long) { if (source!=null && generation!=oldGeneration) { if (freshGeneration!=null && freshGeneration!=generation) discard() else if (freshGeneration!=generation) { freshGeneration=generation; changes++ } } }
    @Synchronized fun locked() { if (freshGeneration!=null) discard() }
    @Synchronized fun actionSelected(action: String) { if ((actionId!=null && actionId!=action) || (unavailableAction!=null && unavailableAction!=action)) clear() }
    private fun discard() { val previous=actionId ?: unavailableAction; clear(); unavailableAction=previous; changes++ }
    @Synchronized fun clear() { changes++; unavailableAction=null; freshGeneration=null; source=null; actionId=null; oldGeneration=null; awaitingResult=false }
}
