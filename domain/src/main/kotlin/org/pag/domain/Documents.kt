package org.pag.domain

enum class DocumentFormat { PDF, JPEG, PNG, TEXT }

/** SENSITIVE metadata only. No key, locator, source URI or person relation. */
data class DocumentAsset(val id: String, val actionId: String, val displayName: String,
    val format: DocumentFormat, val plaintextSize: Long, val importedAt: Long)
data class DocumentSummary(val count: Int)

object DocumentRules {
    const val MAX_DOCUMENTS = 100
    const val MAX_DOCUMENT_BYTES = 26_214_400L
    const val MAX_ACTION_BYTES = 262_144_000L
    fun normalizedName(name: String) = name.trim()
    fun validName(name: String): Boolean {
        val n = normalizedName(name)
        return n.isNotEmpty() && Character.codePointCount(n, 0, n.length) <= 160
    }
    fun validSize(bytes: Long) = bytes in 0..MAX_DOCUMENT_BYTES
    fun valid(asset: DocumentAsset) = asset.id.isNotBlank() && asset.actionId.isNotBlank() &&
        validName(asset.displayName) && normalizedName(asset.displayName) == asset.displayName &&
        validSize(asset.plaintextSize) && asset.importedAt >= 0
    fun mayRead(status: ActionStatus) = status in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)
    fun mayWrite(status: ActionStatus) = mayRead(status)
    fun mayDelete(status: ActionStatus) = mayRead(status)
    val order = compareBy<DocumentAsset> { it.importedAt }.thenBy { it.id }
}
