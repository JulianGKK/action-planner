package org.pag.data.product

import android.content.Context
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.io.File
import java.util.UUID

/** Delete-only READY/MAINTENANCE gate. Existing READY-only Operations gate is unchanged. */
internal fun requireDocumentDelete(c:StorageContext,point:AccessPoint=AccessPoint.STEP) =
    documentFileGate(c,c.generation,point)

internal data class DocumentDeleteInventory(val requiredFiles: Set<String>, val optionalFiles: Set<String>, val staging: Set<String>)
/** No key/name/format copy in job; key-free parent metadata only while RESERVED. */
internal class DocumentDeleteSnapshot(val job: DocumentJobRow, val parent: DocumentMetadataRow?, val inventory: DocumentDeleteInventory)
internal fun sameDocumentDelete(a: DocumentDeleteSnapshot,b: DocumentDeleteSnapshot): Boolean {
    val x=a.job;val y=b.job
    return DocumentStorageRules.valid(x) && DocumentStorageRules.valid(y) && x.operation=="DELETE" && y.operation=="DELETE" &&
        x.id==y.id && x.actionId==y.actionId && x.documentId==y.documentId && x.phase==y.phase &&
        x.blobName==y.blobName && x.plaintextSize==y.plaintextSize && a.parent==b.parent && a.inventory==b.inventory
}
internal interface DocumentDeleteDatabase {
    fun reserve(c: StorageContext, actionId: String, expected: DocumentAsset, jobId: String): DocumentDeleteSnapshot
    fun snapshot(c: StorageContext,id: String,expected: DocumentDeleteSnapshot? = null): DocumentDeleteSnapshot
    fun detach(c: StorageContext,expected: DocumentDeleteSnapshot)
    fun finish(c: StorageContext,expected: DocumentDeleteSnapshot)
}

/** READY uses existing owner. Maintenance supplies a scoped existing authorized key consumer
 * per DB step, before READY-owner publication. This adapter owns no persistent/session key.
 */
internal class SqlCipherDocumentDeleteDatabase(app: Context, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?,
    private val maintenanceKeyForStep: ((StorageContext,(ByteArray) -> Unit) -> Unit)? = null) : DocumentDeleteDatabase {
    private val access=ActionDatabaseAccess(app.applicationContext)
    private fun <T> use(c: StorageContext,work:suspend (ActionDatabase)->T): T {
        requireDocumentDelete(c,AccessPoint.OPERATION)
        if (inventory.state()!=ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
        if(c.scope==AccessScope.READY) return (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key ->
            access.use(inventory.database,key,c,work=work)
        }
        val keyStep=maintenanceKeyForStep ?: throw ProductStorageFailure()
        var called=false;var completed=false;var result:T?=null
        val caller=Thread.currentThread();var active=true
        try { keyStep(c) { key ->
            if(Thread.currentThread()!==caller || !active || called || key.size!=32)throw ProductStorageFailure()
            called=true
            requireDocumentDelete(c)
            val lease=key.copyOf()
            try { result=access.use(inventory.database,lease,c,work=work);completed=true }
            finally { lease.fill(0) }
        } } finally { active=false }
        requireDocumentDelete(c)
        if(!completed)throw ProductStorageFailure()
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
    override fun reserve(c:StorageContext,actionId:String,expected:DocumentAsset,jobId:String)=use(c) { db ->
        db.withWriteTransaction { db.documents().reserveDelete(actionId,expected,jobId,c) }
    }
    override fun snapshot(c:StorageContext,id:String,expected:DocumentDeleteSnapshot?)=use(c) { db -> db.documents().deleteSnapshot(id,expected,c) }
    override fun detach(c:StorageContext,expected:DocumentDeleteSnapshot)=use(c) { db -> db.withWriteTransaction { db.documents().detachDeleteParent(expected,c) } }
    override fun finish(c:StorageContext,expected:DocumentDeleteSnapshot)=use(c) { db -> db.withWriteTransaction { db.documents().finishDelete(expected,c) } }
}

/** One-document deletion/recovery only, not a maintenance orchestrator or UI feature. */
internal class DocumentDelete(private val database: DocumentDeleteDatabase,noBackupFilesDir:File,
    private val fileOps:DocumentPublishFileOps=AndroidDocumentPublishFileOps) {
    private val directory=noBackupFilesDir.toPath().toAbsolutePath().resolve("pag-documents-v1/files")
    private val inventory=DocumentFileInventory(noBackupFilesDir)

    fun delete(c:StorageContext,actionId:String,expected:DocumentAsset):Unit=documentFileBoundary {
        val g=c.generation;val ticket=DocumentNamespaceLane.ticket()
        DocumentNamespaceLane.exclusive(ticket) {
            gate(c,g,AccessScope.READY)
            val reserved=database.reserve(c,actionId,expected,UUID.randomUUID().toString())
            gate(c,g,AccessScope.READY)
            continueDelete(c,g,reserved,AccessScope.READY)
        }
    }
    fun recover(c:StorageContext,jobId:String):Unit=documentFileBoundary {
        val g=c.generation
        gate(c,g,AccessScope.MAINTENANCE)
        DocumentNamespaceLane.drainAndFence {
            gate(c,g,AccessScope.MAINTENANCE)
            val snapshot=database.snapshot(c,jobId)
            continueDelete(c,g,snapshot,AccessScope.MAINTENANCE)
        }
    }
    private fun gate(c:StorageContext,g:Long,scope:AccessScope) {
        documentFileGate(c,g,AccessPoint.OPERATION)
        if(c.scope!=scope)throw ProductStorageFailure()
        documentFileGate(c,g)
    }
    private fun inspect(c:StorageContext,snapshot:DocumentDeleteSnapshot):Set<String> {
        val known=snapshot.inventory
        val actual=inventory.inspect(c,known.staging,known.requiredFiles+known.optionalFiles)
        if(!actual.rootPresent || actual.staging!=known.staging || !actual.files.containsAll(known.requiredFiles))throw ProductStorageFailure()
        return actual.files
    }
    private fun continueDelete(c:StorageContext,g:Long,start:DocumentDeleteSnapshot,scope:AccessScope) {
        gate(c,g,scope)
        var current=database.snapshot(c,start.job.id,start)
        var files=inspect(c,current)
        if(current.job.phase=="DELETE_RESERVED") {
            if(current.parent==null || current.job.blobName !in files)throw ProductStorageFailure()
            gate(c,g,scope)
            database.detach(c,current)
            gate(c,g,scope)
            current=database.snapshot(c,current.job.id)
            inspect(c,current)
        }
        if(current.job.phase!="DELETE_FILE_PENDING" || current.parent!=null)throw ProductStorageFailure()
        gate(c,g,scope)
        // Re-authorize exact DB bindings before unlink; no Room/DB-key lease survives return.
        current=database.snapshot(c,current.job.id,current)
        files=inspect(c,current)
        if(current.job.blobName in files) {
            gate(c,g,scope)
            fileOps.unlink(directory.resolve(current.job.blobName))
            gate(c,g,scope)
        }
        // Also sync on already-missing target: prior unlink may have preceded a crash.
        gate(c,g,scope)
        fileOps.syncDirectory(directory)
        gate(c,g,scope)
        current=database.snapshot(c,current.job.id,current)
        if(current.job.blobName in inspect(c,current))throw ProductStorageFailure()
        gate(c,g,scope)
        database.finish(c,current)
        gate(c,g,scope) // A denied return never claims rollback of a completed commit.
    }
}
