package org.pag.data.product

import android.content.Context
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

internal data class DocumentImportInventory(val files: Set<String>, val staging: Set<String>) {
    val empty get() = files.isEmpty() && staging.isEmpty()
}
internal fun sameDocumentImport(a: DocumentJobRow, b: DocumentJobRow): Boolean =
    a.id == b.id && a.actionId == b.actionId && a.documentId == b.documentId && a.operation == b.operation &&
        a.phase == b.phase && a.stagingName == b.stagingName && a.blobName == b.blobName &&
        a.displayName == b.displayName && a.format == b.format && a.plaintextSize == b.plaintextSize &&
        a.fileKey != null && b.fileKey != null && a.fileKey.contentEquals(b.fileKey)

/** Every call ends its Room/key lease before returning. Never invoked during file I/O. */
internal interface DocumentImportDatabase {
    fun inventory(c: StorageContext): DocumentImportInventory
    fun reserve(c: StorageContext, job: DocumentJobRow): OperationsResult<Unit>
    fun advance(c: StorageContext, expected: DocumentJobRow, size: Long?)
}
internal class SqlCipherDocumentImportDatabase(app: Context, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?, private val now: () -> Long = System::currentTimeMillis) : DocumentImportDatabase, DocumentPublicationDatabase {
    private val access = ActionDatabaseAccess(app.applicationContext)
    private fun <T> use(c: StorageContext, work: suspend (ActionDatabase) -> T): T {
        if (c.scope != AccessScope.READY || inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
        return (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key ->
            access.use(inventory.database,key,c,work=work)
        }
    }
    override fun snapshot(c: StorageContext, id: String, phase: String, expected: DocumentJobRow?): DocumentPublicationSnapshot {
        var leased: DocumentPublicationSnapshot? = null
        var returned = false
        try {
            val result = use(c) { db -> db.documents().publicationSnapshot(id,phase,expected,c).also { leased = it } }
            returned = true
            return result
        } finally { if (!returned) leased?.close() }
    }
    override fun markPublished(c: StorageContext, expected: DocumentJobRow) = use(c) { db ->
        db.withWriteTransaction { db.documents().markFilePublished(expected,c) }
    }
    override fun finish(c: StorageContext, expected: DocumentJobRow) = use(c) { db ->
        db.withWriteTransaction { db.documents().commitPublishedDocument(expected,now(),c) }
    }
    override fun inventory(c: StorageContext) = use(c) { db -> db.documents().importInventory(c) }
    override fun reserve(c: StorageContext, job: DocumentJobRow) = use(c) { db ->
        db.withWriteTransaction { db.documents().reserveImport(job,c) }
    }
    override fun advance(c: StorageContext, expected: DocumentJobRow, size: Long?) = use(c) { db ->
        db.withWriteTransaction { db.documents().advanceImport(expected,size,c) }
    }
}

/** Process-local namespace lane, never the SessionCoordinator monitor.
 * Future maintenance MUST drain/fence this same lane before cleanup. Not runtime-wired.
 */
internal object DocumentNamespaceLane {
    private val monitor = Any()
    @Volatile private var epoch = 0L
    fun requireExclusive() { if (!Thread.holdsLock(monitor)) throw ProductStorageFailure() }
    fun ticket() = epoch
    fun <T> exclusive(ticket: Long, work: () -> T): T = synchronized(monitor) {
        if (ticket != epoch) throw ProductStorageFailure()
        work()
    }
    fun <T> drainAndFence(work: () -> T): T = synchronized(monitor) {
        epoch = Math.addExact(epoch,1)
        work()
    }
}

/** Internal technical outcome only. No document row, publication or product import API. */
internal class DocumentStagingReceipt(val jobId: String, val plaintextSize: Long)
internal class DocumentImportStaging(private val database: DocumentImportDatabase, noBackupFilesDir: File,
    private val sync: (FileChannel) -> Unit = { it.force(true) }) {
    private val base = noBackupFilesDir.toPath().toAbsolutePath()
    private val root = base.resolve("pag-documents-v1")
    private val inventory = DocumentFileInventory(noBackupFilesDir)

    fun stage(c: StorageContext, actionId: String, displayName: String, format: DocumentFormat,
        source: () -> InputStream): OperationsResult<DocumentStagingReceipt> = documentFileBoundary {
        val generation = c.generation
        val ticket = DocumentNamespaceLane.ticket()
        DocumentNamespaceLane.exclusive(ticket) {
            documentFileGate(c,generation,AccessPoint.OPERATION)
            if (c.scope != AccessScope.READY || actionId.isBlank() || !DocumentRules.validName(displayName) || displayName != displayName.trim())
                return@exclusive OperationsResult.Rejected(OperationsFailure.INVALID)
            val known = database.inventory(c)
            // No document-namespace access until the durable WRITING transition below.
            DocumentFileStreams.withNewKey(c) { key ->
                val names = List(4) { UUID.randomUUID().toString() }
                if (names.toSet().size != 4) throw ProductStorageFailure()
                val reserved = DocumentJobRow(names[0],actionId,names[1],"IMPORT","IMPORT_RESERVED",names[2],names[3],displayName,format.name,null,key)
                when (val admission = database.reserve(c,reserved)) {
                    is OperationsResult.Rejected -> admission
                    is OperationsResult.Value -> {
                        documentFileGate(c,generation)
                        database.advance(c,reserved,null)
                        documentFileGate(c,generation)
                        val writing = DocumentJobRow(reserved.id,actionId,reserved.documentId,"IMPORT","IMPORT_WRITING",reserved.stagingName,
                            reserved.blobName,displayName,format.name,null,key)
                        if (known.empty) initialize(c,generation) else requireInventory(c,known)
                        val target = root.resolve("staging").resolve(writing.stagingName!!)
                        documentFileGate(c,generation)
                        // Atomic exclusive leaf creation; no retry and no unlink on any failure.
                        val length = FileChannel.open(target,CREATE_NEW,WRITE,NOFOLLOW_LINKS).use { channel ->
                            documentFileGate(c,generation)
                            checkDirectory(c,generation,root.resolve("staging"))
                            val output = SyncOnClose(channel,sync)
                            output.use {
                                documentFileGate(c,generation)
                                val input = source() // exactly one invocation, only after WRITING and exclusive create
                                DocumentFileStreams.encrypt(c,writing.documentId,key,input,it)
                            }
                        }
                        documentFileGate(c,generation)
                        // Recheck exact known namespace including this staging, before CLOSED commit.
                        requireInventory(c,DocumentImportInventory(known.files,known.staging + writing.stagingName))
                        database.advance(c,writing,length)
                        documentFileGate(c,generation)
                        OperationsResult.Value(DocumentStagingReceipt(writing.id,length))
                    }
                }
            }
        }
    }
    private fun checkDirectory(c: StorageContext, g: Long, p: Path) {
        documentFileGate(c,g)
        if (p.normalize() != p || p.toFile().canonicalFile.toPath() != p) throw ProductStorageFailure()
        val a = Files.readAttributes(p,BasicFileAttributes::class.java,NOFOLLOW_LINKS)
        if (!a.isDirectory || a.isSymbolicLink) throw ProductStorageFailure()
        documentFileGate(c,g)
    }
    private fun exists(c: StorageContext,g: Long,p: Path): Boolean {
        documentFileGate(c,g)
        return try { Files.readAttributes(p,BasicFileAttributes::class.java,NOFOLLOW_LINKS); true }
        catch (_: NoSuchFileException) { false }
    }
    private fun checkInitial(c: StorageContext,g: Long) {
        checkDirectory(c,g,base)
        if (!exists(c,g,root)) return
        checkDirectory(c,g,root)
        Files.newDirectoryStream(root).use { entries -> entries.forEach { child ->
            if (child.fileName.toString() !in setOf("staging","files")) throw ProductStorageFailure()
            checkDirectory(c,g,child)
            Files.newDirectoryStream(child).use { if (it.iterator().hasNext()) throw ProductStorageFailure() }
        } }
        documentFileGate(c,g)
    }
    private fun initialize(c: StorageContext,g: Long) {
        checkInitial(c,g)
        for (p in listOf(root,root.resolve("staging"),root.resolve("files"))) {
            documentFileGate(c,g)
            if (!exists(c,g,p)) Files.createDirectory(p)
            checkDirectory(c,g,p)
        }
        inventory.inspect(c,emptySet(),emptySet())
    }
    private fun requireInventory(c: StorageContext, known: DocumentImportInventory) {
        val actual = inventory.inspect(c,known.staging,known.files)
        if (!actual.rootPresent || actual.staging != known.staging || actual.files != known.files) throw ProductStorageFailure()
    }
}

/** Tink closes this wrapper: sync happens while the channel is still open, then close.
 * Repeated closes are no-ops. Any write/flush/force/close failure prevents CLOSED.
 */
private class SyncOnClose(private val channel: FileChannel, private val sync: (FileChannel) -> Unit) : OutputStream() {
    private val stream = Channels.newOutputStream(channel)
    private var closed = false
    override fun write(b: Int) = stream.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b,off,len)
    override fun flush() = stream.flush()
    override fun close() {
        if (closed) return
        closed = true
        try { stream.flush(); sync(channel) } finally { channel.close() }
    }
}
