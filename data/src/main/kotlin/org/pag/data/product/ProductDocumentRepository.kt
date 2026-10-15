package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executor

/** Only metadata crosses the port. The established coordinators own all mutations. */
class ProductDocumentRepository(app: Context, private val access: StorageAccess, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?, private val worker: Executor, private val main: Executor,
    private val blocked: () -> Unit) : DocumentRepository {
    // Restrictive UX guard only: an expected source failure leaves WRITING for maintenance.
    // Never grants access or replaces persisted coordinator validation.
    private var interruptedGeneration: Long? = null
    private val dispatch = DocumentProductDispatch(access,worker,main,blocked)
    private val database = ActionDatabaseAccess(app.applicationContext)
    private val steps = SqlCipherDocumentImportDatabase(app, inventory, owner)
    private val staging = DocumentImportStaging(steps, app.noBackupFilesDir)
    private val publication = DocumentPublication(steps, app.noBackupFilesDir)
    private val deletion = DocumentDelete(SqlCipherDocumentDeleteDatabase(app, inventory, owner), app.noBackupFilesDir)
    private fun <T> read(c: StorageContext, work: suspend (ActionDatabase) -> T): T {
        requireOperations(c, AccessPoint.OPERATION)
        if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
        return (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key ->
            database.use(inventory.database, key, c) { db -> db.withReadTransaction { work(db) } }
        }
    }
    private suspend fun allowed(db: ActionDatabase, c: StorageContext, a: String): DocumentFailure? {
        requireOperations(c)
        val status = db.actions().get(a)?.model()?.status ?: return DocumentFailure.NOT_FOUND
        return if (DocumentRules.mayRead(status)) null else DocumentFailure.NOT_ALLOWED
    }
    override fun summary(actionId: String, publish: (DocumentResult<DocumentSummary>) -> Unit) = run(publish) { c ->
        read(c) { db ->
            allowed(db,c,actionId)?.let { return@read DocumentResult.Rejected(it) }
            requireOperations(c)
            val count = db.documents().count(actionId)
            if (count !in 0..DocumentRules.MAX_DOCUMENTS) throw ProductStorageFailure()
            DocumentResult.Value(DocumentSummary(count))
        }
    }
    override fun list(actionId: String, publish: (DocumentResult<List<DocumentAsset>>) -> Unit) = run(publish) { c ->
        read(c) { db ->
            allowed(db,c,actionId)?.let { return@read DocumentResult.Rejected(it) }
            requireOperations(c)
            val rows = db.documents().list(actionId).map { it.model() }
            if (rows.any { it.actionId != actionId } || rows.size > DocumentRules.MAX_DOCUMENTS) throw ProductStorageFailure()
            DocumentResult.Value(rows)
        }
    }
    override fun importDocument(actionId: String, name: String, format: DocumentFormat, source: DocumentSource,
        publish: (DocumentResult<Unit>) -> Unit) = run(publish) { c ->
        if (interruptedGeneration == c.generation) return@run DocumentResult.Rejected(DocumentFailure.MAINTENANCE_REQUIRED)
        val normalized = DocumentRules.normalizedName(name)
        if (!DocumentRules.validName(normalized)) return@run DocumentResult.Rejected(DocumentFailure.INVALID)
        try {
            when (val result = staging.stage(c,actionId,normalized,format) { oneAttemptSource(c,source) }) {
                is OperationsResult.Rejected -> DocumentResult.Rejected(when(result.reason) {
                    OperationsFailure.IN_USE -> DocumentFailure.CAPACITY
                    OperationsFailure.NOT_FOUND -> DocumentFailure.NOT_FOUND
                    OperationsFailure.NOT_ALLOWED -> DocumentFailure.NOT_ALLOWED
                    OperationsFailure.INVALID -> DocumentFailure.INVALID
                    else -> throw ProductStorageFailure()
                })
                is OperationsResult.Value -> {
                    requireOperations(c)
                    publication.complete(c,result.value.jobId)
                    DocumentResult.Value(Unit)
                }
            }
        } catch (failure: DocumentInputFailure) { interruptedGeneration=c.generation; DocumentResult.Rejected(failure.reason) }
    }
    override fun deleteDocument(actionId: String, expected: DocumentAsset, publish: (DocumentResult<Unit>) -> Unit) = run(publish) { c ->
        if (interruptedGeneration == c.generation) return@run DocumentResult.Rejected(DocumentFailure.MAINTENANCE_REQUIRED)
        val rejection = read(c) { db ->
            allowed(db,c,actionId) ?: run {
                requireOperations(c)
                val current = db.documents().get(actionId,expected.id)?.model()
                when { current == null -> DocumentFailure.NOT_FOUND; current != expected -> DocumentFailure.STALE; else -> null }
            }
        }
        if (rejection != null) DocumentResult.Rejected(rejection)
        else { deletion.delete(c,actionId,expected); DocumentResult.Value(Unit) }
    }
    private fun <T> run(publish: (DocumentResult<T>) -> Unit, work: (StorageContext) -> DocumentResult<T>) = dispatch.run(publish,work)
}

/** Shared executor/publication boundary, independently tested without Android DB substitutes. */
internal class DocumentProductDispatch(private val access: StorageAccess, private val worker: Executor,
    private val main: Executor, private val blocked: () -> Unit) {
    fun <T> run(publish: (DocumentResult<T>) -> Unit, work: (StorageContext) -> DocumentResult<T>) {
        val c = access.acquire(AccessScope.READY) ?: return
        val ticket = DocumentNamespaceLane.ticket()
        worker.execute {
            try {
                val result = DocumentNamespaceLane.exclusive(ticket) {
                    requireOperations(c,AccessPoint.OPERATION)
                    work(c).also { requireOperations(c) }
                }
                main.execute { c.publish { publish(result) } }
            } catch (_: StorageAccessRevoked) { /* inspect persisted state after the next real unlock */ }
              catch (_: Exception) { main.execute { if (c.check(AccessPoint.STEP)) blocked() } }
        }
    }
}

/** A source failure is a user error, unlike cipher/file/Room faults. No provider exception escapes. */
internal fun oneAttemptSource(c: StorageContext, source: DocumentSource): InputStream {
    requireOperations(c)
    fun unavailable(): Nothing = throw DocumentInputFailure(DocumentFailure.SOURCE_UNAVAILABLE)
    val stream = try { source.open() } catch (_: IOException) { unavailable() } catch (_: SecurityException) { unavailable() }
    return object : FilterInputStream(stream) {
        override fun read(): Int = try { super.read() } catch (_: IOException) { unavailable() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = try { `in`.read(b,off,len) } catch (_: IOException) { unavailable() }
        override fun close() { try { super.close() } catch (_: IOException) { unavailable() } }
    }
}
