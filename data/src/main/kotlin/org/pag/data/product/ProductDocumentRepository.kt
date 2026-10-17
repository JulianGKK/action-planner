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
    private val noBackupRoot = productNoBackupRoot(app.noBackupFilesDir)
    private val steps = SqlCipherDocumentImportDatabase(app, inventory, owner)
    private val staging = DocumentImportStaging(steps, noBackupRoot)
    private val publication = DocumentPublication(steps, noBackupRoot)
    private val deletion = DocumentDelete(SqlCipherDocumentDeleteDatabase(app, inventory, owner), noBackupRoot)
    override fun readPdf(actionId:String,expected:DocumentAsset,page:Int,request:DocumentPdfRequest,
        renderer:DocumentPdfRenderer,publish:(DocumentResult<DocumentPdfPage>)->Unit) {
        val requested=System.nanoTime()
        DocumentReadDispatch(access,worker,main,blocked).run(request,publish) {c ->
            request.measured(DocumentPdfPhase.WORKER_START,requested)
            if(page !in 0 until 10000)return@run DocumentResult.Rejected(DocumentFailure.PDF_UNSUPPORTED)
            if(interruptedGeneration==c.generation)return@run DocumentResult.Rejected(DocumentFailure.MAINTENANCE_REQUIRED)
            var held:DocumentReadSnapshot?=null
            try {
                val started=System.nanoTime()
                val snapshot=read(c) {db->db.documents().pdfSnapshot(actionId,expected,c).also {if(it is DocumentResult.Value)held=it.value}}
                request.measured(DocumentPdfPhase.DB_SNAPSHOT,started)
                when(snapshot) {
                    is DocumentResult.Rejected -> snapshot
                    is DocumentResult.Value -> DocumentResult.Value(DocumentPdfRead(noBackupRoot).render(c,snapshot.value,page,renderer,request))
                }
            } finally {held?.close()}
        }
    }
    override fun readText(actionId:String,expected:DocumentAsset,page:Int,request:DocumentTextRequest,
        publish:(DocumentResult<DocumentTextPage>)->Unit) {
        val started=System.nanoTime()
        DocumentReadDispatch(access,worker,main,blocked).run(request,publish) {c ->
            request.measured(DocumentTextPhase.WORKER_START,started)
            if(page !in 0 until DocumentTextLimits.MAX_PAGES)return@run DocumentResult.Rejected(DocumentFailure.TEXT_UNSUPPORTED)
            if(interruptedGeneration==c.generation)return@run DocumentResult.Rejected(DocumentFailure.MAINTENANCE_REQUIRED)
            var held:DocumentReadSnapshot?=null
            try {
                val dbStarted=System.nanoTime()
                val snapshot=read(c) {db -> db.documents().textSnapshot(actionId,expected,c).also {if(it is DocumentResult.Value)held=it.value}}
                request.measured(DocumentTextPhase.DB_SNAPSHOT,dbStarted)
                when(snapshot) {
                    is DocumentResult.Rejected -> snapshot
                    is DocumentResult.Value -> {
                        val s=snapshot.value;val inv=DocumentFileInventory(noBackupRoot)
                        val invStarted=System.nanoTime()
                        val files=inv.inspect(c,s.staging,s.required+s.optional)
                        if(!files.rootPresent || files.staging!=s.staging || !files.files.containsAll(s.required))throw ProductStorageFailure()
                        request.measured(DocumentTextPhase.INVENTORY,invStarted)
                        requireOperations(c)
                        val readStarted=System.nanoTime()
                        val result=inv.readText(c,s.blob,s.staging,files.files,s.asset.id,s.key,s.asset.plaintextSize,page,request)
                        request.measured(DocumentTextPhase.READ_AUTH_UTF8_PAGE,readStarted)
                        DocumentResult.Value(result)
                    }
                }
            } finally {held?.close()}
        }
    }
    override fun readImage(actionId:String, expected:DocumentAsset, request:DocumentImageRequest,
        decoder:DocumentImageDecoder, publish:(DocumentResult<DocumentImage>)->Unit) {
        val requested=System.nanoTime()
        DocumentReadDispatch(access,worker,main,blocked).run(request,publish) { c ->
            if(interruptedGeneration==c.generation) return@run DocumentResult.Rejected(DocumentFailure.MAINTENANCE_REQUIRED)
            request.measured(DocumentImagePhase.WORKER_START,requested)
            var held:DocumentReadSnapshot?=null
            try {
                val started=System.nanoTime()
                val snapshot=read(c) { db ->
                    db.documents().imageSnapshot(actionId,expected,c).also {if(it is DocumentResult.Value) held=it.value}
                }
                request.measured(DocumentImagePhase.DB_SNAPSHOT,started)
                when(snapshot) {
                    is DocumentResult.Rejected -> snapshot
                    is DocumentResult.Value -> DocumentResult.Value(DocumentImageRead(noBackupRoot).decode(c,snapshot.value,decoder,request))
                }
            } finally {held?.close()}
        }
    }
    private fun <T> read(c: StorageContext, work: suspend (ActionDatabase) -> T): T {
        requireOperations(c, AccessPoint.OPERATION)
        if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
        return withDocumentReadKey(owner() ?: throw ProductStorageFailure(),c) { key ->
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
