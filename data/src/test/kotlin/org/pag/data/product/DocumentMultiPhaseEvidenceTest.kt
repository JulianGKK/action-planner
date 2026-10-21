package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.util.UUID

/** REVIEW DRAFT, test-source only. Fixed metadata per ONE attempt; no runtime port/authority.
 * DB confirmations supplied by the harness are simulated transaction ends, NOT SQLite evidence. */
internal class DocumentEvidenceDraft(val operation: Operation) {
    enum class Operation { IMPORT, DELETE }
    enum class Db { RESERVE_IMPORT, WRITING, CLOSED, PUBLISHED, DOCUMENT_INSERT_AND_JOB_REMOVE,
        RESERVE_DELETE, DETACH_PARENT, DELETE_JOB_REMOVE }
    enum class DbEnd { NOT_ATTEMPTED, ROLLED_BACK, COMMITTED, UNKNOWN }
    data class Local(val end: DbEnd, val changed: Boolean)
    enum class FileStep { CREATE, WRITE, CIPHER_CLOSE, FILE_SYNC, RENAME, SYNC_FILES, SYNC_STAGING, UNLINK }
    enum class FileEnd { NOT_STARTED, RETURNED, UNKNOWN }
    enum class Inventory { NOT_CHECKED, CONSISTENT, CONTRADICTORY, RECHECKED_AFTER_EFFECT }
    enum class Resource { DB, KEY, STREAM, SNAPSHOT }
    enum class Cleanup { NOT_ACQUIRED, CONFIRMED, FAILED_OR_UNCONFIRMED }
    enum class Recovery { NOT_REQUIRED, PERSISTED_JOB, AUTHORIZED_RECOVERY_REQUIRED, BLOCKED }
    enum class Publication { NOT_ADMITTED, ADMITTED, CALLBACK_EXECUTED, DENIED, DISPATCH_UNKNOWN }
    enum class Result { NOT_EXECUTED, COMPLETE, RECOVERY_REQUIRED, UNKNOWN }
    // No event log, strings, identifiers, key/byte arrays, paths or callback references.
    private val db = Db.entries.associateWith { Local(DbEnd.NOT_ATTEMPTED,false) }.toMutableMap()
    private val files = FileStep.entries.associateWith { FileEnd.NOT_STARTED }.toMutableMap()
    private val resources = Resource.entries.associateWith { Cleanup.NOT_ACQUIRED }.toMutableMap()
    var inventory = Inventory.NOT_CHECKED; private set
    var publication = Publication.NOT_ADMITTED; private set
    var jobPresent = false; private set
    private var finalChecked = false
    private fun effect() { finalChecked=false;if(inventory!=Inventory.CONTRADICTORY)inventory=Inventory.NOT_CHECKED }
    fun dbStarted(step:Db) { check(db.getValue(step).end==DbEnd.NOT_ATTEMPTED);db[step]=Local(DbEnd.UNKNOWN,false);effect() }
    fun dbEnded(step:Db, end:DbEnd, changed:Boolean=false) {
        check(db.getValue(step).end==DbEnd.UNKNOWN && end in setOf(DbEnd.COMMITTED,DbEnd.ROLLED_BACK))
        check(!changed || end==DbEnd.COMMITTED);db[step]=Local(end,changed)
    }
    fun file(step:FileStep, end:FileEnd) { check(end!=FileEnd.NOT_STARTED);files[step]=end;effect() }
    fun resource(resource:Resource, cleanup:Cleanup) { resources[resource]=cleanup }
    fun job(present:Boolean) { jobPresent=present }
    fun checked(final:Boolean=false, consistent:Boolean=true) {
        check(inventory!=Inventory.CONTRADICTORY || !consistent)
        inventory=if(!consistent)Inventory.CONTRADICTORY else if(final)Inventory.RECHECKED_AFTER_EFFECT else Inventory.CONSISTENT
        finalChecked=final && consistent
    }
    fun publication(value:Publication) {
        check(publication !in setOf(Publication.CALLBACK_EXECUTED,Publication.DENIED))
        if(value==Publication.CALLBACK_EXECUTED)check(publication==Publication.ADMITTED)
        publication=value
    }
    fun local(step:Db)=db.getValue(step)
    fun fileEnd(step:FileStep)=files.getValue(step)
    fun cleanup()=if(resources.values.any{it==Cleanup.FAILED_OR_UNCONFIRMED})
        WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED else WorkspaceWriteCleanup.COMPLETE
    private fun hasEffect()=db.values.any{it.changed} || files.values.any{it!=FileEnd.NOT_STARTED}
    private fun unknown()=db.values.any{it.end==DbEnd.UNKNOWN} || files.values.any{it==FileEnd.UNKNOWN}
    private fun requiredDb()=if(operation==Operation.IMPORT) listOf(Db.RESERVE_IMPORT,Db.WRITING,Db.CLOSED,Db.PUBLISHED,Db.DOCUMENT_INSERT_AND_JOB_REMOVE)
        else listOf(Db.RESERVE_DELETE,Db.DETACH_PARENT,Db.DELETE_JOB_REMOVE)
    private fun requiredFiles()=if(operation==Operation.IMPORT) listOf(FileStep.CREATE,FileStep.WRITE,FileStep.CIPHER_CLOSE,
        FileStep.FILE_SYNC,FileStep.RENAME,FileStep.SYNC_FILES,FileStep.SYNC_STAGING) else listOf(FileStep.UNLINK,FileStep.SYNC_FILES)
    fun result():Result = when {
        unknown() || cleanup()!=WorkspaceWriteCleanup.COMPLETE -> Result.UNKNOWN
        inventory==Inventory.CONTRADICTORY -> Result.RECOVERY_REQUIRED
        !hasEffect() && !jobPresent -> Result.NOT_EXECUTED
        !jobPresent && finalChecked && requiredDb().all{db.getValue(it)==Local(DbEnd.COMMITTED,true)} &&
            requiredFiles().all{files[it]==FileEnd.RETURNED} -> Result.COMPLETE
        else -> Result.RECOVERY_REQUIRED
    }
    fun recovery():Recovery = when {
        inventory==Inventory.CONTRADICTORY -> Recovery.BLOCKED
        unknown() || cleanup()!=WorkspaceWriteCleanup.COMPLETE -> Recovery.AUTHORIZED_RECOVERY_REQUIRED
        result() in setOf(Result.COMPLETE,Result.NOT_EXECUTED) -> Recovery.NOT_REQUIRED
        jobPresent -> Recovery.PERSISTED_JOB
        else -> Recovery.AUTHORIZED_RECOVERY_REQUIRED
    }
    /** Conservative fallback only. COMPLETE has no single-transaction core COMMITTED proof. */
    fun conservativeCompletion()=WorkspaceWriteCompletion(if(result()==Result.NOT_EXECUTED)
        WorkspaceWriteOutcome.ROLLED_BACK else WorkspaceWriteOutcome.UNKNOWN,cleanup())
}

/** Actual staging/publication/delete/reconciliation + DAO rules + real host files/Tink.
 * Transaction rollback and lease cleanup are explicit TEST SIMULATIONS, not Room/SQLCipher. */
class DocumentMultiPhaseEvidenceTest {
    @get:Rule val temp=TemporaryFolder()
    private class Access(override val scope:AccessScope=AccessScope.READY,override val generation:Long=1):StorageContext {
        var allowed=true
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean { if(!allowed)return false;result();return true }
    }
    private val c=Access()
    private var evidence=DocumentEvidenceDraft(DocumentEvidenceDraft.Operation.IMPORT)
    private fun id()=UUID.randomUUID().toString()
    private fun copy(j:DocumentJobRow)=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,j.phase,j.stagingName,j.blobName,j.displayName,j.format,j.plaintextSize,j.fileKey?.copyOf())
    private fun copy(d:DocumentRow)=DocumentRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt,d.blobName,d.fileKey.copyOf())
    private inner class Dao:DocumentDao() {
        var docs=mutableListOf<DocumentRow>();var jobs=mutableListOf<DocumentJobRow>()
        override suspend fun count(a:String)=docs.count{d->jobs.none{it.operation=="DELETE" && it.documentId==d.id}}
        override suspend fun list(a:String)=docs.filter{d->jobs.none{it.operation=="DELETE" && it.documentId==d.id}}.map{metadata(it)}
        override suspend fun get(a:String,id:String)=list(a).singleOrNull{it.id==id}
        override suspend fun actionStatus(a:String)="ACTIVE"
        override suspend fun inventoryDocuments()=docs.map{copy(it)}
        override suspend fun inventoryJobs()=jobs.map{copy(it)}
        override suspend fun insertJob(job:DocumentJobRow){jobs.add(copy(job))}
        override suspend fun insertPublishedDocument(row:DocumentRow){docs.add(copy(row))}
        override suspend fun deletePublishedJob(id:String)=if(jobs.removeIf{it.id==id && it.phase=="FILE_PUBLISHED"})1 else 0
        override suspend fun changeImportPhase(id:String,old:String,next:String,size:Long?):Int {
            val i=jobs.indexOfFirst{it.id==id && it.phase==old};if(i<0)return 0
            val j=jobs[i];jobs[i]=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,next,j.stagingName,j.blobName,j.displayName,j.format,size,j.fileKey?.copyOf());return 1
        }
        override suspend fun removeDeleteParent(actionId:String,documentId:String)=if(docs.removeIf{it.id==documentId && it.actionId==actionId})1 else 0
        override suspend fun markDeletePending(id:String):Int {
            val i=jobs.indexOfFirst{it.id==id && it.phase=="DELETE_RESERVED"};if(i<0)return 0
            val j=jobs[i];jobs[i]=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,"DELETE_FILE_PENDING",null,j.blobName,null,null,j.plaintextSize,null);return 1
        }
        override suspend fun removeDeleteJob(id:String)=if(jobs.removeIf{it.id==id && it.phase=="DELETE_FILE_PENDING"})1 else 0
        override suspend fun removeInterruptedImport(id:String,phase:String)=if(jobs.removeIf{it.id==id && it.phase==phase})1 else 0
    }
    private val dao=Dao();private var inDb=false
    private var dbCalls=0;private var commits=0;private var opens=0;private var closes=0;private var sourceStreams=0
    private var fileSyncs=0;private var dirSyncAttempts=0;private var dirSyncReturns=0;private var renames=0;private var unlinks=0
    private var failSync=false;private var failFileSync=false;private var cleanupFailure=false
    private var afterDb:(DocumentEvidenceDraft.Db)->Unit={}
    private var beforeDb:(DocumentEvidenceDraft.Db)->Unit={}
    private val snapshots=mutableListOf<DocumentPublicationSnapshot>()
    private val borrowedKeys=mutableListOf<ByteArray>()
    private val channels=mutableListOf<FileChannel>()
    private var attemptedImport:DocumentJobRow?=null
    private fun <T> tx(work:suspend()->T):T {
        check(!inDb);inDb=true;dbCalls++
        val oldDocs=dao.docs.map{copy(it)}.toMutableList();val oldJobs=dao.jobs.map{copy(it)}.toMutableList()
        try {return runBlocking{work()}} catch(e:Exception){dao.docs=oldDocs;dao.jobs=oldJobs;throw e} finally{inDb=false}
    }
    private fun <T> mutation(step:DocumentEvidenceDraft.Db, changes:(T)->Boolean={true},work:suspend()->T):T {
        beforeDb(step)
        evidence.dbStarted(step);evidence.resource(DocumentEvidenceDraft.Resource.DB,DocumentEvidenceDraft.Cleanup.FAILED_OR_UNCONFIRMED)
        val value=try {tx(work)} catch(e:Exception) {
            evidence.dbEnded(step,DocumentEvidenceDraft.DbEnd.ROLLED_BACK)
            evidence.resource(DocumentEvidenceDraft.Resource.DB,DocumentEvidenceDraft.Cleanup.CONFIRMED);throw e
        }
        commits++;evidence.dbEnded(step,DocumentEvidenceDraft.DbEnd.COMMITTED,changes(value));evidence.job(dao.jobs.isNotEmpty())
        evidence.resource(DocumentEvidenceDraft.Resource.DB,if(cleanupFailure)DocumentEvidenceDraft.Cleanup.FAILED_OR_UNCONFIRMED else DocumentEvidenceDraft.Cleanup.CONFIRMED)
        if(cleanupFailure)throw IOException("synthetic unconfirmed lease cleanup after returned fake commit")
        afterDb(step);return value
    }
    private val importDb=object:DocumentImportDatabase {
        override fun inventory(c:StorageContext)=tx{dao.importInventory(c)}.also{evidence.checked()}
        override fun reserve(c:StorageContext,job:DocumentJobRow):OperationsResult<Unit> {
            attemptedImport=job;borrowedKeys.add(job.fileKey!!)
            evidence.resource(DocumentEvidenceDraft.Resource.KEY,DocumentEvidenceDraft.Cleanup.FAILED_OR_UNCONFIRMED)
            return mutation(DocumentEvidenceDraft.Db.RESERVE_IMPORT,{it is OperationsResult.Value}){dao.reserveImport(job,c)}
        }
        override fun advance(c:StorageContext,expected:DocumentJobRow,size:Long?) {
            if(size!=null) {
                assertEquals(opens,closes);assertTrue(channels.all{!it.isOpen})
                evidence.file(DocumentEvidenceDraft.FileStep.CIPHER_CLOSE,DocumentEvidenceDraft.FileEnd.RETURNED)
            }
            mutation(if(size==null)DocumentEvidenceDraft.Db.WRITING else DocumentEvidenceDraft.Db.CLOSED){dao.advanceImport(expected,size,c)}
        }
    }
    private val publicationDb=object:DocumentPublicationDatabase {
        override fun snapshot(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?)=
            tx{dao.publicationSnapshot(id,phase,expected,c)}.also{snapshots.add(it);evidence.resource(DocumentEvidenceDraft.Resource.SNAPSHOT,DocumentEvidenceDraft.Cleanup.FAILED_OR_UNCONFIRMED)}
        override fun markPublished(c:StorageContext,expected:DocumentJobRow){mutation(DocumentEvidenceDraft.Db.PUBLISHED){dao.markFilePublished(expected,c)}}
        override fun finish(c:StorageContext,expected:DocumentJobRow){mutation(DocumentEvidenceDraft.Db.DOCUMENT_INSERT_AND_JOB_REMOVE){dao.commitPublishedDocument(expected,1,c)}}
    }
    private val deleteDb=object:DocumentDeleteDatabase {
        override fun reserve(c:StorageContext,actionId:String,expected:DocumentAsset,jobId:String)=mutation(DocumentEvidenceDraft.Db.RESERVE_DELETE){dao.reserveDelete(actionId,expected,jobId,c)}
        override fun snapshot(c:StorageContext,id:String,expected:DocumentDeleteSnapshot?)=tx{dao.deleteSnapshot(id,expected,c)}
        override fun detach(c:StorageContext,expected:DocumentDeleteSnapshot){mutation(DocumentEvidenceDraft.Db.DETACH_PARENT){dao.detachDeleteParent(expected,c)}}
        override fun finish(c:StorageContext,expected:DocumentDeleteSnapshot){mutation(DocumentEvidenceDraft.Db.DELETE_JOB_REMOVE){dao.finishDelete(expected,c)}}
    }
    private val ops=object:DocumentPublishFileOps {
        override fun moveNoReplace(source:Path,destination:Path) {
            check(!inDb);Files.move(source,destination);renames++;evidence.file(DocumentEvidenceDraft.FileStep.RENAME,DocumentEvidenceDraft.FileEnd.RETURNED)
        }
        override fun unlink(source:Path) {
            check(!inDb);Files.delete(source);unlinks++;evidence.file(DocumentEvidenceDraft.FileStep.UNLINK,DocumentEvidenceDraft.FileEnd.RETURNED)
        }
        override fun syncDirectory(directory:Path) {
            check(!inDb);dirSyncAttempts++
            val step=if(directory.fileName.toString()=="files")DocumentEvidenceDraft.FileStep.SYNC_FILES else DocumentEvidenceDraft.FileStep.SYNC_STAGING
            evidence.file(step,DocumentEvidenceDraft.FileEnd.UNKNOWN)
            if(failSync)throw IOException("synthetic directory sync")
            FileChannel.open(directory,READ).use{it.force(true)};dirSyncReturns++;evidence.file(step,DocumentEvidenceDraft.FileEnd.RETURNED)
        }
    }
    private fun root()=File(temp.root,"pag-documents-v1")
    private fun metadata(d:DocumentRow)=DocumentMetadataRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt)
    private fun observeClosedResources() {
        assertFalse(inDb);assertTrue(borrowedKeys.all{k->k.all{it==0.toByte()}})
        assertTrue(channels.all{!it.isOpen});assertTrue(snapshots.all{s->s.job.fileKey!!.all{it==0.toByte()}})
        if(borrowedKeys.isNotEmpty())evidence.resource(DocumentEvidenceDraft.Resource.KEY,DocumentEvidenceDraft.Cleanup.CONFIRMED)
        if(snapshots.isNotEmpty())evidence.resource(DocumentEvidenceDraft.Resource.SNAPSHOT,DocumentEvidenceDraft.Cleanup.CONFIRMED)
        if(opens>0 || channels.isNotEmpty())evidence.resource(DocumentEvidenceDraft.Resource.STREAM,if(sourceStreams==closes)DocumentEvidenceDraft.Cleanup.CONFIRMED else DocumentEvidenceDraft.Cleanup.FAILED_OR_UNCONFIRMED)
    }
    private fun import(name:String="synthetic",source:()->InputStream={ByteArrayInputStream(byteArrayOf(120))}):DocumentResult<Unit> {
        // Repository's error mapping, explicitly reproduced around the actual coordinators.
        // No Android ProductDocumentRepository instance or synthetic runtime integration.
        try {
            val result=DocumentImportStaging(importDb,temp.root){channel->
                channels.add(channel);fileSyncs++;evidence.file(DocumentEvidenceDraft.FileStep.FILE_SYNC,DocumentEvidenceDraft.FileEnd.UNKNOWN)
                if(failFileSync)throw IOException("synthetic file sync")
                channel.force(true);evidence.file(DocumentEvidenceDraft.FileStep.FILE_SYNC,DocumentEvidenceDraft.FileEnd.RETURNED)
            }.stage(c,"a",name,DocumentFormat.TEXT) {
                opens++;evidence.resource(DocumentEvidenceDraft.Resource.STREAM,DocumentEvidenceDraft.Cleanup.FAILED_OR_UNCONFIRMED)
                val input=source();sourceStreams++
                object:FilterInputStream(input){override fun close(){super.close();closes++}}
            }
            if(result is OperationsResult.Rejected)return DocumentResult.Rejected(if(result.reason==OperationsFailure.IN_USE)DocumentFailure.CAPACITY else DocumentFailure.INVALID)
            DocumentPublication(publicationDb,temp.root,ops).complete(c,(result as OperationsResult.Value).value.jobId)
            return DocumentResult.Value(Unit)
        } catch(f:DocumentInputFailure) {return DocumentResult.Rejected(f.reason)} finally {
            // Observed host effects only; never infer successful cipher completion from existence.
            attemptedImport?.let { job ->
                val leaves=listOf(File(root(),"staging/${job.stagingName}"),File(root(),"files/${job.blobName}"))
                if(leaves.any{it.isFile})evidence.file(DocumentEvidenceDraft.FileStep.CREATE,DocumentEvidenceDraft.FileEnd.RETURNED)
                if(leaves.any{it.isFile && it.length()>0})evidence.file(DocumentEvidenceDraft.FileStep.WRITE,DocumentEvidenceDraft.FileEnd.RETURNED)
            }
            observeClosedResources()
        }
    }
    private fun finalCheck() {
        val state=tx{dao.reconciliationSnapshot(Access(AccessScope.MAINTENANCE))}
        val actual=DocumentFileInventory(temp.root).inspectRecovery(Access(AccessScope.MAINTENANCE),state.jobs.mapNotNull{it.stagingName}.toSet(),state.documents+state.jobs.map{it.blobName})
        val consistent=state.jobs.isEmpty() && actual.staging.isEmpty() && actual.files==state.documents
        evidence.job(state.jobs.isNotEmpty());evidence.checked(final=true,consistent=consistent)
    }
    private fun prepareDelete():DocumentAsset {
        assertEquals(DocumentResult.Value(Unit),import());finalCheck()
        val asset=metadata(dao.docs.single()).model()
        evidence=DocumentEvidenceDraft(DocumentEvidenceDraft.Operation.DELETE)
        borrowedKeys.clear();channels.clear();snapshots.clear();attemptedImport=null
        dbCalls=0;commits=0;renames=0;fileSyncs=0;dirSyncAttempts=0;dirSyncReturns=0;opens=0;closes=0;sourceStreams=0
        return asset
    }
    private fun delete(asset:DocumentAsset) {try{DocumentDelete(deleteDb,temp.root,ops).delete(c,"a",asset)}finally{observeClosedResources()}}

    @Test fun invalidBeforeReserveIsNonexecutionNotACommittedDocument() {
        assertEquals(DocumentResult.Rejected(DocumentFailure.INVALID),import(name=""))
        assertEquals(0,commits);assertTrue(dao.jobs.isEmpty());assertFalse(root().exists());assertEquals(0,opens)
        assertEquals(DocumentEvidenceDraft.Result.NOT_EXECUTED,evidence.result())
        assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,evidence.conservativeCompletion().outcome)
    }
    @Test fun capacityRejectionCommitsNoInsertAndCreatesNoSourceOrNamespace() {
        repeat(100){dao.docs.add(DocumentRow(id(),"a","synthetic","TEXT",0,1,id(),ByteArray(32)))}
        // Full-count rejection occurs before filesystem use; existing DB fixture has no host blobs.
        assertEquals(DocumentResult.Rejected(DocumentFailure.CAPACITY),import())
        assertEquals(1,commits);assertFalse(evidence.local(DocumentEvidenceDraft.Db.RESERVE_IMPORT).changed)
        assertTrue(dao.jobs.isEmpty());assertFalse(root().exists());assertEquals(0,opens)
        assertEquals(DocumentEvidenceDraft.Result.NOT_EXECUTED,evidence.result())
    }
    @Test fun unavailableSourceAfterWritingIsRejectionWithPersistedJob() {
        val result=import {oneAttemptSource(c,DocumentSource{throw IOException("synthetic unavailable source")})}
        assertEquals(DocumentResult.Rejected(DocumentFailure.SOURCE_UNAVAILABLE),result)
        assertEquals(2,commits);assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(1,opens)
        assertEquals(DocumentEvidenceDraft.DbEnd.COMMITTED,evidence.local(DocumentEvidenceDraft.Db.WRITING).end)
        assertEquals(DocumentEvidenceDraft.Recovery.PERSISTED_JOB,evidence.recovery())
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,evidence.conservativeCompletion().outcome)
    }
    @Test fun tooLargeAfterCipherWritesIsRejectionAndNeverGlobalRollback() {
        var remaining=DocumentRules.MAX_DOCUMENT_BYTES+1
        val result=import {object:InputStream(){
            override fun read():Int=error("bulk only")
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                if(remaining==0L)return -1
                val n=minOf(remaining,len.toLong()).toInt();b.fill(120,off,off+n);remaining-=n;return n
            }
        }}
        assertEquals(DocumentResult.Rejected(DocumentFailure.TOO_LARGE),result)
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(2,commits)
        assertTrue(File(root(),"staging").listFiles()!!.single().length()>0);assertEquals(1,opens);assertEquals(1,closes)
        assertEquals(DocumentEvidenceDraft.Recovery.PERSISTED_JOB,evidence.recovery())
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,evidence.conservativeCompletion().outcome)
    }
    @Test fun renameThenSyncFailureKeepsOlderClosedPhaseAndPhysicalFinalBlob() {
        failSync=true;assertThrows(ProductStorageFailure::class.java){import()}
        assertEquals("IMPORT_CLOSED",dao.jobs.single().phase);assertEquals(3,commits);assertEquals(1,renames)
        assertTrue(File(root(),"staging").list()!!.isEmpty());assertEquals(1,File(root(),"files").list()!!.size)
        assertEquals(1,dirSyncAttempts);assertEquals(0,dirSyncReturns)
        assertEquals(DocumentEvidenceDraft.FileEnd.UNKNOWN,evidence.fileEnd(DocumentEvidenceDraft.FileStep.SYNC_FILES))
        assertEquals(DocumentEvidenceDraft.Result.UNKNOWN,evidence.result())
    }
    @Test fun failureAfterPublishedPreservesFourIndependentCommitsAndClosedSnapshots() {
        afterDb={if(it==DocumentEvidenceDraft.Db.PUBLISHED)throw IOException("synthetic after published")}
        assertThrows(ProductStorageFailure::class.java){import()}
        assertEquals(4,commits);assertEquals("FILE_PUBLISHED",dao.jobs.single().phase);assertTrue(dao.docs.isEmpty())
        assertEquals(2,dirSyncReturns);assertEquals(WorkspaceWriteCleanup.COMPLETE,evidence.cleanup())
        assertEquals(DocumentEvidenceDraft.Recovery.PERSISTED_JOB,evidence.recovery())
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,evidence.conservativeCompletion().outcome)
    }
    @Test fun successfulImportRequiresSeparateFinalCheckForDraftCompletion() {
        assertEquals(DocumentResult.Value(Unit),import());assertEquals(5,commits);assertTrue(dbCalls>commits)
        assertEquals(1,opens);assertEquals(1,closes);assertEquals(1,fileSyncs);assertEquals(2,dirSyncReturns)
        assertEquals(1,dao.docs.size);assertTrue(dao.jobs.isEmpty());assertEquals(0,unlinks)
        assertEquals(DocumentEvidenceDraft.Result.RECOVERY_REQUIRED,evidence.result())
        finalCheck();assertEquals(DocumentEvidenceDraft.Result.COMPLETE,evidence.result())
        assertEquals(DocumentEvidenceDraft.Publication.NOT_ADMITTED,evidence.publication)
        // Even complete multi-phase evidence is not a single core transaction COMMITTED result.
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,evidence.conservativeCompletion().outcome)
    }
    @Test fun deleteFailureBeforeUnlinkCannotUndoReturnedDetachOrEarlierReserve() {
        val asset=prepareDelete();val blob=File(root(),"files/${dao.docs.single().blobName}")
        afterDb={if(it==DocumentEvidenceDraft.Db.DETACH_PARENT)throw IOException("synthetic before unlink")}
        assertThrows(ProductStorageFailure::class.java){delete(asset)}
        assertEquals(2,commits);assertTrue(dao.docs.isEmpty());assertTrue(blob.isFile);assertEquals(0,unlinks)
        assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertEquals(0,dirSyncAttempts)
        assertEquals(DocumentEvidenceDraft.DbEnd.COMMITTED,evidence.local(DocumentEvidenceDraft.Db.DETACH_PARENT).end)
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,evidence.conservativeCompletion().outcome)
    }
    @Test fun revokeAfterDetachDoesNotRestoreParentOrUnlinkBlob() {
        val asset=prepareDelete();afterDb={if(it==DocumentEvidenceDraft.Db.DETACH_PARENT)c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){delete(asset)}
        assertEquals(2,commits);assertTrue(dao.docs.isEmpty());assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase)
        assertEquals(1,File(root(),"files").list()!!.size);assertEquals(0,unlinks);assertEquals(0,dirSyncReturns)
        assertEquals(DocumentEvidenceDraft.Recovery.PERSISTED_JOB,evidence.recovery())
    }
    @Test fun returnedUnlinkIsNotDirectorySyncOrJobRemoval() {
        val asset=prepareDelete();failSync=true
        assertThrows(ProductStorageFailure::class.java){delete(asset)}
        assertEquals(2,commits);assertTrue(dao.docs.isEmpty());assertEquals(1,dao.jobs.size)
        assertTrue(File(root(),"files").list()!!.isEmpty());assertEquals(1,unlinks);assertEquals(0,dirSyncReturns)
        assertEquals(DocumentEvidenceDraft.FileEnd.RETURNED,evidence.fileEnd(DocumentEvidenceDraft.FileStep.UNLINK))
        assertEquals(DocumentEvidenceDraft.Result.UNKNOWN,evidence.result())
    }
    @Test fun successfulDeleteRequiresSeparateFinalCheckAndDoesNotReadSource() {
        val asset=prepareDelete();delete(asset)
        assertEquals(3,commits);assertEquals(0,opens);assertEquals(0,fileSyncs);assertEquals(1,unlinks);assertEquals(1,dirSyncReturns)
        assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty());assertEquals(DocumentEvidenceDraft.Result.RECOVERY_REQUIRED,evidence.result())
        finalCheck();assertEquals(DocumentEvidenceDraft.Result.COMPLETE,evidence.result())
    }
    @Test fun unconfirmedLeaseCleanupDoesNotEraseConfirmedLocalCommit() {
        cleanupFailure=true;assertThrows(ProductStorageFailure::class.java){import()}
        assertEquals(1,commits);assertEquals("IMPORT_RESERVED",dao.jobs.single().phase);assertFalse(root().exists())
        assertEquals(DocumentEvidenceDraft.DbEnd.COMMITTED,evidence.local(DocumentEvidenceDraft.Db.RESERVE_IMPORT).end)
        assertEquals(DocumentEvidenceDraft.Result.UNKNOWN,evidence.result())
        assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,evidence.cleanup())
    }
    @Test fun fileSyncFailureLeavesWritingAndRequiresAuthorizedRecovery() {
        failFileSync=true;assertThrows(ProductStorageFailure::class.java){import()}
        assertEquals(2,commits);assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(1,fileSyncs)
        assertEquals(DocumentEvidenceDraft.FileEnd.UNKNOWN,evidence.fileEnd(DocumentEvidenceDraft.FileStep.FILE_SYNC))
        assertEquals(DocumentEvidenceDraft.Recovery.AUTHORIZED_RECOVERY_REQUIRED,evidence.recovery())
    }
    @Test fun unknownDbEndCannotBeOverwrittenByLaterStepOrRejection() {
        val e=DocumentEvidenceDraft(DocumentEvidenceDraft.Operation.DELETE)
        e.dbStarted(DocumentEvidenceDraft.Db.RESERVE_DELETE);e.dbEnded(DocumentEvidenceDraft.Db.RESERVE_DELETE,DocumentEvidenceDraft.DbEnd.COMMITTED,true)
        e.dbStarted(DocumentEvidenceDraft.Db.DETACH_PARENT)
        assertEquals(DocumentEvidenceDraft.Result.UNKNOWN,e.result())
        assertEquals(DocumentEvidenceDraft.DbEnd.COMMITTED,e.local(DocumentEvidenceDraft.Db.RESERVE_DELETE).end)
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,e.conservativeCompletion().outcome)
        assertThrows(IllegalStateException::class.java){e.dbEnded(DocumentEvidenceDraft.Db.RESERVE_DELETE,DocumentEvidenceDraft.DbEnd.ROLLED_BACK)}
    }
    @Test fun rollbackOfLaterTransactionPreservesEarlierWritingCommit() {
        val e=DocumentEvidenceDraft(DocumentEvidenceDraft.Operation.IMPORT)
        e.dbStarted(DocumentEvidenceDraft.Db.WRITING);e.dbEnded(DocumentEvidenceDraft.Db.WRITING,DocumentEvidenceDraft.DbEnd.COMMITTED,true)
        e.dbStarted(DocumentEvidenceDraft.Db.CLOSED);e.dbEnded(DocumentEvidenceDraft.Db.CLOSED,DocumentEvidenceDraft.DbEnd.ROLLED_BACK)
        assertEquals(DocumentEvidenceDraft.Result.RECOVERY_REQUIRED,e.result())
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,e.conservativeCompletion().outcome)
    }
    @Test fun contradictoryInventoryRemainsBlockedRegardlessOfPublication() {
        evidence.checked(consistent=false);evidence.publication(DocumentEvidenceDraft.Publication.ADMITTED)
        evidence.publication(DocumentEvidenceDraft.Publication.CALLBACK_EXECUTED)
        assertEquals(DocumentEvidenceDraft.Recovery.BLOCKED,evidence.recovery())
        assertEquals(DocumentEvidenceDraft.Result.RECOVERY_REQUIRED,evidence.result())
        assertThrows(IllegalStateException::class.java){evidence.publication(DocumentEvidenceDraft.Publication.CALLBACK_EXECUTED)}
    }
    @Test fun draftContainsOnlyBoundedEnumsBooleansAndFixedMetadataMaps() {
        val allowed=setOf(java.lang.Boolean.TYPE,DocumentEvidenceDraft.Operation::class.java,
            DocumentEvidenceDraft.Inventory::class.java,DocumentEvidenceDraft.Publication::class.java,Map::class.java)
        val fields=DocumentEvidenceDraft::class.java.declaredFields.filterNot{it.isSynthetic}
        assertTrue(fields.all{it.type in allowed})
        assertEquals(setOf(DocumentEvidenceDraft.DbEnd::class.java,java.lang.Boolean.TYPE),
            DocumentEvidenceDraft.Local::class.java.declaredFields.filterNot{it.isSynthetic}.map{it.type}.toSet())
        assertFalse(UncachedWorkspaceMutations().ramReuseEnabled)
    }
    private fun recover(context:StorageContext) {
        val recoveryDb=object:DocumentReconciliationDatabase {
            override fun inventory(c:StorageContext)=tx{dao.reconciliationSnapshot(c)}
            override fun abort(c:StorageContext,expected:DocumentRecoveryJob){tx{dao.abortInterruptedImport(expected,c)}}
            override fun publication(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?)=
                tx{dao.maintenancePublicationSnapshot(id,phase,expected,c)}.also{snapshots.add(it)}
            override fun mark(c:StorageContext,expected:DocumentJobRow){tx{dao.maintenanceMarkPublished(expected,c)}}
            override fun commit(c:StorageContext,expected:DocumentJobRow){tx{dao.maintenanceCommitDocument(expected,1,c)}}
        }
        DocumentReconciliation(recoveryDb,deleteDb,temp.root,ops).run(context)
    }
    @Test fun authorizedRecoveryAfterFailedFileSyncUsesFreshEvidenceAndDoesNotRepairOldValidity() {
        failFileSync=true;assertThrows(ProductStorageFailure::class.java){import()}
        val old=evidence;assertEquals(DocumentEvidenceDraft.Result.UNKNOWN,old.result())
        evidence=DocumentEvidenceDraft(DocumentEvidenceDraft.Operation.IMPORT)
        recover(Access(AccessScope.MAINTENANCE,2));observeClosedResources();finalCheck()
        assertTrue(dao.jobs.isEmpty());assertTrue(dao.docs.isEmpty());assertEquals(1,unlinks);assertEquals(1,dirSyncReturns)
        assertEquals(DocumentEvidenceDraft.Inventory.RECHECKED_AFTER_EFFECT,evidence.inventory)
        assertEquals(DocumentEvidenceDraft.Result.UNKNOWN,old.result())
        // A fresh recovery completion is required; this new ledger has no import commit chain.
        assertNotEquals(DocumentEvidenceDraft.Result.COMPLETE,evidence.result())
    }
    @Test fun missingRootAndUnknownLeafPreventRecoveryDespiteEarlierLocalCommits() {
        afterDb={if(it==DocumentEvidenceDraft.Db.WRITING)c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){import()}
        assertFalse(root().exists());assertEquals(2,commits)
        assertThrows(ProductStorageFailure::class.java){recover(Access(AccessScope.MAINTENANCE,2))}
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(0,unlinks)
        Files.createDirectory(root().toPath());Files.createDirectory(File(root(),"staging").toPath());Files.createDirectory(File(root(),"files").toPath())
        val foreign=File(root(),"files/${id()}");foreign.writeBytes(byteArrayOf(42))
        assertThrows(ProductStorageFailure::class.java){recover(Access(AccessScope.MAINTENANCE,3))}
        assertTrue(foreign.isFile);assertEquals(1,dao.jobs.size);assertEquals(0,unlinks);assertEquals(0,dirSyncAttempts)
    }

    @Test fun cipherCloseAndReturnedFileSyncBeforeClosedCommitStillLeaveWritingOnRevoke() {
        beforeDb={if(it==DocumentEvidenceDraft.Db.CLOSED)c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){import()}
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(2,commits)
        assertEquals(1,fileSyncs);assertEquals(1,closes)
        assertEquals(DocumentEvidenceDraft.FileEnd.RETURNED,evidence.fileEnd(DocumentEvidenceDraft.FileStep.FILE_SYNC))
        assertEquals(DocumentEvidenceDraft.DbEnd.ROLLED_BACK,evidence.local(DocumentEvidenceDraft.Db.CLOSED).end)
        assertEquals(DocumentEvidenceDraft.DbEnd.COMMITTED,evidence.local(DocumentEvidenceDraft.Db.WRITING).end)
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,evidence.conservativeCompletion().outcome)
    }
    @Test fun realImportWorkerAndMainReplayHaveOnePhysicalChainAndOneAdmittedCallback() {
        val workers=mutableListOf<Runnable>();val mains=mutableListOf<Runnable>();var callbacks=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{workers.add(it)},java.util.concurrent.Executor{mains.add(it)},
            {error("No blocked")},afterPublicationAdmission={evidence.publication(DocumentEvidenceDraft.Publication.ADMITTED)})
            .run<Unit>({callbacks++;evidence.publication(DocumentEvidenceDraft.Publication.CALLBACK_EXECUTED)}) {
                import().also{finalCheck()}
            }
        val w=workers.single();w.run();w.run();assertEquals(5,commits);assertEquals(1,mains.size)
        val m=mains.single();m.run();m.run()
        assertEquals(1,callbacks);assertEquals(1,opens);assertEquals(1,renames)
        assertEquals(DocumentEvidenceDraft.Result.COMPLETE,evidence.result())
        assertEquals(DocumentEvidenceDraft.Publication.CALLBACK_EXECUTED,evidence.publication)
    }
    @Test fun realDeleteWithLostMainKeepsPhysicalCompletionAndUnknownDispatchSeparate() {
        val asset=prepareDelete();val workers=mutableListOf<Runnable>();val mains=mutableListOf<Runnable>();var callbacks=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{workers.add(it)},java.util.concurrent.Executor{mains.add(it)},
            {error("No blocked")}).run<Unit>({callbacks++}) {
                delete(asset);finalCheck();DocumentResult.Value(Unit)
            }
        workers.single().run();assertEquals(1,mains.size);mains.clear()
        evidence.publication(DocumentEvidenceDraft.Publication.DISPATCH_UNKNOWN)
        assertEquals(3,commits);assertEquals(1,unlinks);assertEquals(1,dirSyncReturns);assertEquals(0,callbacks)
        assertEquals(DocumentEvidenceDraft.Result.COMPLETE,evidence.result())
        assertEquals(DocumentEvidenceDraft.Publication.DISPATCH_UNKNOWN,evidence.publication)
        assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty())
    }
    @Test fun realImportCompletionDoesNotGrantCallbackAcrossMaintenanceFence() {
        val workers=mutableListOf<Runnable>();val mains=mutableListOf<Runnable>();var callbacks=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{workers.add(it)},java.util.concurrent.Executor{mains.add(it)},
            {error("No blocked")}).run<Unit>({callbacks++}) {import().also{finalCheck()}}
        workers.single().run();DocumentNamespaceLane.drainAndFence{};mains.single().run()
        evidence.publication(DocumentEvidenceDraft.Publication.DENIED)
        assertEquals(0,callbacks);assertEquals(5,commits);assertEquals(1,dao.docs.size)
        assertEquals(DocumentEvidenceDraft.Result.COMPLETE,evidence.result())
    }

}
