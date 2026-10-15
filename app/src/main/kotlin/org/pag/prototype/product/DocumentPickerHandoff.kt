package org.pag.prototype.product

import org.pag.domain.DocumentSource

/** Activity RAM only; no URI/permission/authentication is persisted or owned by the model. */
interface DocumentPicker {
    fun launch(actionId: String, generation: Long)
    fun available(actionId: String, generation: Long): Boolean
    fun consume(actionId: String, generation: Long): DocumentSource?
    fun actionSelected(actionId: String) {}
    fun cancel()
}

/** Testable one-shot handoff. A returned source is never itself an import authorization. */
internal class PendingDocumentPicker<T> {
    private var actionId: String? = null
    private var oldGeneration: Long? = null
    private var source: T? = null
    private var freshGeneration: Long? = null
    var awaitingResult = false; private set
    @Synchronized fun begin(action: String, generation: Long) { clear(); actionId=action; oldGeneration=generation; awaitingResult=true }
    @Synchronized fun returned(value: T?) {
        if (!awaitingResult) return
        awaitingResult=false
        if (value == null) clear() else source=value
    }
    @Synchronized fun available(action: String, generation: Long) = source != null && actionId == action && oldGeneration != generation && (freshGeneration == null || freshGeneration == generation)
    @Synchronized fun consume(action: String, generation: Long): T? {
        if (!available(action,generation)) return null
        val result=source; clear(); return result
    }
    /** Only the original picker departure and the normal credential UI may retain the handoff. */
    @Synchronized fun leaving(authenticating: Boolean) { if (!awaitingResult && !authenticating) clear() }
    @Synchronized fun ready(generation: Long) { if (source!=null && generation!=oldGeneration) { if (freshGeneration!=null && freshGeneration!=generation) clear() else freshGeneration=generation } }
    @Synchronized fun locked() { if (freshGeneration!=null) clear() }
    @Synchronized fun actionSelected(action: String) { if (actionId!=null && actionId!=action) clear() }
    @Synchronized fun clear() { freshGeneration=null; source=null; actionId=null; oldGeneration=null; awaitingResult=false }
}
