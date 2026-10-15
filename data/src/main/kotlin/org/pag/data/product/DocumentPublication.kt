package org.pag.data.product

import android.system.Os
import android.system.OsConstants
import org.pag.domain.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Scoped internal snapshot, deliberately not a data class or domain projection. */
internal class DocumentPublicationSnapshot(val job: DocumentJobRow, val inventory: DocumentImportInventory) : AutoCloseable {
    override fun close() { job.fileKey?.fill(0) }
}
internal interface DocumentPublicationDatabase {
    fun snapshot(c: StorageContext, id: String, phase: String, expected: DocumentJobRow? = null): DocumentPublicationSnapshot
    fun markPublished(c: StorageContext, expected: DocumentJobRow)
    fun finish(c: StorageContext, expected: DocumentJobRow)
}

/** Narrow platform boundary; production uses Android Linux link/unlink/fsync (API 21+).
 * link fails for existing destinations. No rename/copy fallback, even if unsupported.
 */
internal interface DocumentPublishFileOps {
    fun link(source: Path, destination: Path)
    fun unlink(source: Path)
    fun syncDirectory(directory: Path)
}
internal object AndroidDocumentPublishFileOps : DocumentPublishFileOps {
    override fun link(source: Path, destination: Path) = Os.link(source.toString(),destination.toString())
    override fun unlink(source: Path) = Files.delete(source)
    override fun syncDirectory(directory: Path) {
        val fd = Os.open(directory.toString(),OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,0)
        try {
            if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw ProductStorageFailure()
            Os.fsync(fd)
        } finally { Os.close(fd) }
    }
}

/** Internal technical completion, never a UI publication. Only a job ID is accepted. */
internal class DocumentPublication(private val database: DocumentPublicationDatabase, noBackupFilesDir: File,
    private val fileOps: DocumentPublishFileOps = AndroidDocumentPublishFileOps) {
    private val root = noBackupFilesDir.toPath().toAbsolutePath().resolve("pag-documents-v1")
    private val inventory = DocumentFileInventory(noBackupFilesDir)

    fun complete(c: StorageContext, jobId: String): Unit = documentFileBoundary {
        val generation=c.generation;val ticket=DocumentNamespaceLane.ticket()
        DocumentNamespaceLane.exclusive(ticket) { processInsideLane(c,generation,jobId,AccessScope.READY,"IMPORT_CLOSED") }
    }
    internal fun recoverInsideLane(c:StorageContext,jobId:String,phase:String) {
        DocumentNamespaceLane.requireExclusive()
        processInsideLane(c,c.generation,jobId,AccessScope.MAINTENANCE,phase)
    }
    private fun processInsideLane(c:StorageContext,g:Long,jobId:String,scope:AccessScope,phase:String) {
        gate(c,g,scope)
        if(!DocumentStorageRules.opaque(jobId) || phase !in setOf("IMPORT_CLOSED","FILE_PUBLISHED"))throw ProductStorageFailure()
        if(phase=="IMPORT_CLOSED") {
            database.snapshot(c,jobId,"IMPORT_CLOSED").use { closed ->
                gate(c,g,scope);requireInventory(c,closed.inventory)
                val j=closed.job
                val staging=j.stagingName!! in closed.inventory.staging
                if(!staging && (scope!=AccessScope.MAINTENANCE || j.blobName !in closed.inventory.files))throw ProductStorageFailure()
                inventory.verifyExisting(c,if(staging)DocumentFileArea.STAGING else DocumentFileArea.FILES,
                    if(staging)j.stagingName else j.blobName,closed.inventory.staging,closed.inventory.files,
                    j.documentId,j.fileKey!!,j.plaintextSize!!,DocumentFormat.valueOf(j.format!!)).requireCurrent(c)
                database.snapshot(c,jobId,"IMPORT_CLOSED",j).use { current ->
                    gate(c,g,scope);requireInventory(c,current.inventory)
                    if(staging) moveExclusive(c,g,current,scope)
                    else {
                        // Prior move may have crashed before directory sync or phase commit.
                        fileOps.syncDirectory(root.resolve("files"));gate(c,g,scope)
                        fileOps.syncDirectory(root.resolve("staging"));gate(c,g,scope)
                    }
                }
                gate(c,g,scope);database.markPublished(c,j);gate(c,g,scope)
                val expected=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,"FILE_PUBLISHED",j.stagingName,
                    j.blobName,j.displayName,j.format,j.plaintextSize,j.fileKey)
                finishInsideLane(c,g,jobId,scope,expected)
            }
            return
        }
        finishInsideLane(c,g,jobId,scope,null)
    }
    private fun finishInsideLane(c:StorageContext,g:Long,jobId:String,scope:AccessScope,expected:DocumentJobRow?) {
        database.snapshot(c,jobId,"FILE_PUBLISHED",expected).use { final ->
            gate(c,g,scope);requireInventory(c,final.inventory)
            val f=final.job
            inventory.verifyExisting(c,DocumentFileArea.FILES,f.blobName,final.inventory.staging,final.inventory.files,
                f.documentId,f.fileKey!!,f.plaintextSize!!,DocumentFormat.valueOf(f.format!!)).requireCurrent(c)
            gate(c,g,scope);requireInventory(c,final.inventory)
            database.finish(c,f);gate(c,g,scope)
        }
    }
    private fun gate(c: StorageContext, generation: Long,scope:AccessScope=AccessScope.READY) {
        documentFileGate(c,generation,AccessPoint.OPERATION)
        if (c.scope != scope) throw ProductStorageFailure()
        documentFileGate(c,generation)
    }
    private fun requireInventory(c: StorageContext, expected: DocumentImportInventory) {
        val actual = inventory.inspect(c,expected.staging,expected.files)
        if (!actual.rootPresent || actual.staging != expected.staging || actual.files != expected.files) throw ProductStorageFailure()
    }
    private fun attributes(c: StorageContext,g: Long,p: Path,scope:AccessScope): BasicFileAttributes {
        gate(c,g,scope)
        if (p.normalize() != p || p.toFile().canonicalFile.toPath() != p) throw ProductStorageFailure()
        val result = Files.readAttributes(p,BasicFileAttributes::class.java,NOFOLLOW_LINKS)
        if (!result.isRegularFile || result.isSymbolicLink || result.fileKey() == null) throw ProductStorageFailure()
        gate(c,g,scope)
        return result
    }
    private fun moveExclusive(c: StorageContext,g: Long,snapshot: DocumentPublicationSnapshot,scope:AccessScope) {
        val j = snapshot.job
        val source = root.resolve("staging").resolve(j.stagingName!!)
        val target = root.resolve("files").resolve(j.blobName)
        val before = attributes(c,g,source,scope)
        gate(c,g,scope)
        fileOps.link(source,target) // Atomic no-overwrite destination insertion, not check-then-rename.
        gate(c,g,scope)
        val after = attributes(c,g,target,scope)
        if (before.fileKey() != after.fileKey() || before.size() != after.size() || before.lastModifiedTime() != after.lastModifiedTime()) throw ProductStorageFailure()
        requireInventory(c,DocumentImportInventory(snapshot.inventory.files + j.blobName,snapshot.inventory.staging))
        fileOps.syncDirectory(root.resolve("files")) // Make new name durable before dropping old name.
        gate(c,g,scope)
        val original = attributes(c,g,source,scope)
        if (original.fileKey() != before.fileKey() || original.size() != before.size() || original.lastModifiedTime() != before.lastModifiedTime()) throw ProductStorageFailure()
        fileOps.unlink(source)
        gate(c,g,scope)
        fileOps.syncDirectory(root.resolve("staging"))
        gate(c,g,scope)
        requireInventory(c,DocumentImportInventory(snapshot.inventory.files + j.blobName,snapshot.inventory.staging - j.stagingName))
    }
}
