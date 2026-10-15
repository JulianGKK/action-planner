package org.pag.data.product

import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.io.File

/** Key-free inventory; no user text, key or external locator. */
internal data class DocumentRecoveryJob(val id:String,val actionId:String,val documentId:String,val operation:String,
    val phase:String,val stagingName:String?,val blobName:String)
internal fun DocumentJobRow.recoveryHeader()=DocumentRecoveryJob(id,actionId,documentId,operation,phase,stagingName,blobName)
internal data class DocumentRecoverySnapshot(val documents:Set<String>,val jobs:List<DocumentRecoveryJob>)
internal interface DocumentReconciliationDatabase {
    fun inventory(c:StorageContext):DocumentRecoverySnapshot
    fun abort(c:StorageContext,expected:DocumentRecoveryJob)
    fun publication(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?):DocumentPublicationSnapshot
    fun mark(c:StorageContext,expected:DocumentJobRow)
    fun commit(c:StorageContext,expected:DocumentJobRow)
}
internal class SqlCipherDocumentReconciliationDatabase(private val steps:SqlCipherDocumentDeleteDatabase,
    private val now:()->Long=System::currentTimeMillis):DocumentReconciliationDatabase {
    override fun inventory(c:StorageContext)=steps.maintenanceStep(c){it.documents().reconciliationSnapshot(c)}
    override fun abort(c:StorageContext,expected:DocumentRecoveryJob)=steps.maintenanceStep(c){ db ->
        db.withWriteTransaction { db.documents().abortInterruptedImport(expected,c) }
    }
    override fun publication(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?):DocumentPublicationSnapshot {
        var lease:DocumentPublicationSnapshot?=null;var returned=false
        try {
            val result=steps.maintenanceStep(c){db->db.documents().maintenancePublicationSnapshot(id,phase,expected,c).also{lease=it}}
            returned=true;return result
        } finally {if(!returned)lease?.close()}
    }
    override fun mark(c:StorageContext,expected:DocumentJobRow)=steps.maintenanceStep(c){db->
        db.withWriteTransaction { db.documents().maintenanceMarkPublished(expected,c) }
    }
    override fun commit(c:StorageContext,expected:DocumentJobRow)=steps.maintenanceStep(c){db->
        db.withWriteTransaction { db.documents().maintenanceCommitDocument(expected,now(),c) }
    }
}

/** Global maintenance only. No source URI, key owner, READY publication or root creation. */
internal class DocumentReconciliation(private val database:DocumentReconciliationDatabase,
    deleteDatabase:DocumentDeleteDatabase,private val noBackupFilesDir:File,
    private val fileOps:DocumentPublishFileOps=AndroidDocumentPublishFileOps) {
    private val files=DocumentFileInventory(noBackupFilesDir)
    private val root=noBackupFilesDir.toPath().toAbsolutePath().resolve("pag-documents-v1")
    private val deletion=DocumentDelete(deleteDatabase,noBackupFilesDir,fileOps)
    private fun gate(c:StorageContext,g:Long) {
        documentFileGate(c,g,AccessPoint.OPERATION)
        if(c.scope!=AccessScope.MAINTENANCE)throw ProductStorageFailure()
        documentFileGate(c,g)
    }
    private fun checked(c:StorageContext,g:Long):Pair<DocumentRecoverySnapshot,DocumentNamespaceSnapshot> {
        gate(c,g)
        val db=database.inventory(c) // Room is closed before touching the file namespace.
        gate(c,g)
        val actual=files.inspectRecovery(c,db.jobs.mapNotNull{it.stagingName}.toSet(),db.documents+db.jobs.map{it.blobName})
        if(!actual.rootPresent && (db.documents.isNotEmpty() || db.jobs.any{it.phase!="IMPORT_RESERVED"}))throw ProductStorageFailure()
        if(!actual.files.containsAll(db.documents))throw ProductStorageFailure()
        db.jobs.forEach { j ->
            val staging=j.stagingName in actual.staging;val final=j.blobName in actual.files
            val valid=when(j.phase) {
                "IMPORT_RESERVED" -> !staging && !final
                "IMPORT_WRITING" -> !final
                "IMPORT_CLOSED" -> staging xor final
                "FILE_PUBLISHED","DELETE_RESERVED" -> !staging && final
                "DELETE_FILE_PENDING" -> !staging
                else -> false
            }
            if(!valid)throw ProductStorageFailure()
        }
        gate(c,g)
        return db to actual
    }
    fun run(c:StorageContext,preflight:()->Unit={}) {
        val g=c.generation;gate(c,g)
        DocumentNamespaceLane.drainAndFence {
            gate(c,g)
            preflight() // Existing runtime DB preflight also runs only after old workers drain.
            gate(c,g)
            val first=checked(c,g).first // Validate ALL states before any mutation.
            val ordered=first.jobs.sortedWith(compareBy<DocumentRecoveryJob>{
                when(it.phase){"IMPORT_RESERVED","IMPORT_WRITING"->0;"IMPORT_CLOSED","FILE_PUBLISHED"->1;else->2}
            }.thenBy{it.id})
            val publication=DocumentPublication(object:DocumentPublicationDatabase {
                override fun snapshot(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?):DocumentPublicationSnapshot {
                    val lease=database.publication(c,id,phase,expected)
                    try {
                        val actual=checked(c,g).second
                        return DocumentPublicationSnapshot(lease.job,DocumentImportInventory(actual.files,actual.staging))
                    }catch(error:Throwable){lease.close();throw error}
                }
                override fun markPublished(c:StorageContext,expected:DocumentJobRow)=database.mark(c,expected)
                override fun finish(c:StorageContext,expected:DocumentJobRow)=database.commit(c,expected)
            },noBackupFilesDir,fileOps)
            ordered.forEach { job ->
                gate(c,g)
                val (current,actual)=checked(c,g)
                if(current.jobs.singleOrNull{it.id==job.id}!=job)throw ProductStorageFailure()
                when(job.phase) {
                    "IMPORT_RESERVED" -> database.abort(c,job)
                    "IMPORT_WRITING" -> {
                        if(job.stagingName in actual.staging) {
                            gate(c,g);fileOps.unlink(root.resolve("staging").resolve(job.stagingName!!));gate(c,g)
                        }
                        gate(c,g);fileOps.syncDirectory(root.resolve("staging"));gate(c,g)
                        if(job.stagingName in checked(c,g).second.staging)throw ProductStorageFailure()
                        database.abort(c,job)
                    }
                    "IMPORT_CLOSED","FILE_PUBLISHED" -> publication.recoverInsideLane(c,job.id,job.phase)
                    "DELETE_RESERVED","DELETE_FILE_PENDING" -> deletion.recoverInsideLane(c,job.id)
                    else -> throw ProductStorageFailure()
                }
                gate(c,g)
            }
            val (last,actual)=checked(c,g)
            if(last.jobs.isNotEmpty() || actual.staging.isNotEmpty() || actual.files!=last.documents)throw ProductStorageFailure()
            gate(c,g)
        }
    }
}
