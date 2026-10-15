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

/** One attempt only; opened by the authorized worker after durable WRITING, never by UI. */
fun interface DocumentSource { fun open(): java.io.InputStream }
enum class DocumentFailure { INVALID, NOT_ALLOWED, NOT_FOUND, STALE, CAPACITY, MAINTENANCE_REQUIRED, SOURCE_UNAVAILABLE, TOO_LARGE, FORMAT }
sealed interface DocumentResult<out T> {
    data class Value<T>(val value: T) : DocumentResult<T>
    data class Rejected(val reason: DocumentFailure) : DocumentResult<Nothing>
}
/** Key-free product port. No URI, blob names, jobs or cryptographic data. */
interface DocumentRepository {
    fun summary(actionId: String, publish: (DocumentResult<DocumentSummary>) -> Unit)
    fun list(actionId: String, publish: (DocumentResult<List<DocumentAsset>>) -> Unit)
    fun importDocument(actionId: String, name: String, format: DocumentFormat, source: DocumentSource,
        publish: (DocumentResult<Unit>) -> Unit)
    fun deleteDocument(actionId: String, expected: DocumentAsset, publish: (DocumentResult<Unit>) -> Unit)
}
