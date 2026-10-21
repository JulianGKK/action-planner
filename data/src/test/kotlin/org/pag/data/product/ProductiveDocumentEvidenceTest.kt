package org.pag.data.product

import androidx.room3.Transactor
import androidx.room3.TransactionScope
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import java.io.*
import java.nio.file.*
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption.READ
import java.util.UUID

/** Actual production evidence/transaction helper/coordinators/final DAO inventory + host files.
 * The DAO storage and transaction engine here are FAKE, not Room/SQLCipher or SQLite. */
class ProductiveDocumentEvidenceTest {
    @get:Rule val temp=TemporaryFolder()
    private class Access(override val scope:AccessScope=AccessScope.READY,override val generation:Long=1):StorageContext {
        var allowed=true;var publications=0
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean { if(!allowed)return false;publications++;result();return true }
    }
    private val c=Access()
    private var evidence=DocumentMutationEvidence(DocumentMutationEvidence.Operation.IMPORT)
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
    private var finalReads=0;private var lastDb:DocumentMutationEvidence.Step?=null
    private var commits=0;private var opens=0;private var closes=0
    private var renames=0;private var unlinks=0;private var syncAttempts=0;private var syncReturns=0;private var fileSyncs=0
    private var failFileSync=false;private var failDirectorySync=false;private var failFinalRead=false
    private var failCleanup=false;private var failEnd=false;private var failSnapshotClose=false
    private var afterDb:(DocumentMutationEvidence.Step)->Unit={}
    private var beforeDb:(DocumentMutationEvidence.Step)->Unit={}
    private fun <T> tx(work:suspend()->T):T {
        check(!inDb);inDb=true
        val oldDocs=dao.docs.map{copy(it)}.toMutableList();val oldJobs=dao.jobs.map{copy(it)}.toMutableList()
        try{return runBlocking{work()}}catch(t:Throwable){dao.docs=oldDocs;dao.jobs=oldJobs;throw t}finally{inDb=false}
    }
    private val transactor=object:Transactor {
        override suspend fun <R> usePrepared(sql:String,block:suspend(SQLiteStatement)->R):R=error("Fake DAO uses collections")
        override suspend fun inTransaction()=inDb
        override suspend fun <R> withTransaction(type:Transactor.SQLiteTransactionType,block:suspend TransactionScope<R>.()->R):R = tx {
            val scope=object:TransactionScope<R> {
                override suspend fun <T> usePrepared(sql:String,block:suspend(SQLiteStatement)->T):T=error("No SQL")
                override suspend fun <T> withNestedTransaction(block:suspend TransactionScope<T>.()->T):T=error("No nested proof")
                override suspend fun rollback(result:R):Nothing=error("No explicit rollback requested")
            }
            scope.block().also{if(failEnd)throw IOException("synthetic unknown end");commits++}
        }
    }
    private fun <T> read(work:suspend()->T)=documentEvidenceScope(evidence,DocumentMutationEvidence.Resource.DB_KEY){tx(work)}
    private fun <T> mutate(step:DocumentMutationEvidence.Step,changed:(T)->Boolean={true},work:suspend()->T):T {
        beforeDb(step)
        val value=documentEvidenceScope(evidence,DocumentMutationEvidence.Resource.DB_KEY) {
            runBlocking{confirmedDocumentTransaction(transactor,c,evidence,step,changed,work)}.also {
                if(failCleanup)throw IOException("synthetic key/db cleanup")
            }
        }
        lastDb=step;afterDb(step);return value
    }
    private val imports=object:DocumentImportDatabase,DocumentPublicationDatabase,DocumentFinalInventoryDatabase {
        override fun inventory(c:StorageContext)=read{dao.importInventory(c)}
        override fun reserve(c:StorageContext,job:DocumentJobRow)=mutate(DocumentMutationEvidence.Step.RESERVED,{it is OperationsResult.Value}){dao.reserveImport(job,c)}
        override fun advance(c:StorageContext,expected:DocumentJobRow,size:Long?){mutate(if(size==null)DocumentMutationEvidence.Step.WRITING else DocumentMutationEvidence.Step.CLOSED){dao.advanceImport(expected,size,c)}}
        override fun snapshot(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?):DocumentPublicationSnapshot {
            val value=read{dao.publicationSnapshot(id,phase,expected,c)}
            value.observe(evidence)
            if(failSnapshotClose) {
                // A close fault is injected at the same actual observed-use helper as production.
                val broken=object:AutoCloseable{override fun close(){value.close();throw IOException("snapshot close")}}
                broken.documentEvidenceUse(evidence,DocumentMutationEvidence.Resource.SNAPSHOT){}
            }
            return value
        }
        override fun markPublished(c:StorageContext,expected:DocumentJobRow){mutate(DocumentMutationEvidence.Step.PUBLISHED){dao.markFilePublished(expected,c)}}
        override fun finish(c:StorageContext,expected:DocumentJobRow){mutate(DocumentMutationEvidence.Step.DOCUMENT_COMMIT){dao.commitPublishedDocument(expected,1,c)}}
        override fun finalInventory(c:StorageContext)=read {
            assertTrue(lastDb in setOf(DocumentMutationEvidence.Step.DOCUMENT_COMMIT,DocumentMutationEvidence.Step.DELETE_FINISH));finalReads++
            if(failFinalRead)throw IOException("final read")
            dao.completedInventory(c)
        }
    }
    private val deletes=object:DocumentDeleteDatabase,DocumentFinalInventoryDatabase {
        override fun reserve(c:StorageContext,actionId:String,expected:DocumentAsset,jobId:String)=mutate(DocumentMutationEvidence.Step.DELETE_RESERVED){dao.reserveDelete(actionId,expected,jobId,c)}
        override fun snapshot(c:StorageContext,id:String,expected:DocumentDeleteSnapshot?)=read{dao.deleteSnapshot(id,expected,c)}
        override fun detach(c:StorageContext,expected:DocumentDeleteSnapshot){mutate(DocumentMutationEvidence.Step.DETACH){dao.detachDeleteParent(expected,c)}}
        override fun finish(c:StorageContext,expected:DocumentDeleteSnapshot){mutate(DocumentMutationEvidence.Step.DELETE_FINISH){dao.finishDelete(expected,c)}}
        override fun finalInventory(c:StorageContext)=imports.finalInventory(c)
    }
    private val ops=object:DocumentPublishFileOps {
        override fun moveNoReplace(source:Path,destination:Path){assertFalse(inDb);Files.move(source,destination);renames++}
        override fun unlink(source:Path){assertFalse(inDb);Files.delete(source);unlinks++}
        override fun syncDirectory(directory:Path){assertFalse(inDb);syncAttempts++;if(failDirectorySync)throw IOException("directory sync")
            FileChannel.open(directory,READ).use{it.force(true)};syncReturns++}
    }
    private fun producer()=DocumentMutationProducer(temp.root,evidence,ops){channel->
        fileSyncs++;if(failFileSync)throw IOException("file sync");channel.force(true)
    }
    private fun root()=File(temp.root,"pag-documents-v1")
    private fun metadata(d:DocumentRow)=DocumentMetadataRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt)
    private fun import(name:String="synthetic",source:()->InputStream={ByteArrayInputStream(byteArrayOf(120))}):DocumentResult<Unit> {
        try {
            val result=producer().import(c,imports,imports,imports,"a",name,DocumentFormat.TEXT) {
                opens++;val input=source()
                object:FilterInputStream(input){override fun close(){super.close();closes++}}
            }
            return if(result is OperationsResult.Value)DocumentResult.Value(Unit) else DocumentResult.Rejected(DocumentFailure.CAPACITY)
        }catch(f:DocumentInputFailure){return DocumentResult.Rejected(f.reason)}
    }
    private fun prepareDelete():DocumentAsset {
        assertEquals(DocumentResult.Value(Unit),import());assertOutcome(DocumentWriteOutcome.COMPLETE)
        val asset=metadata(dao.docs.single()).model()
        evidence=DocumentMutationEvidence(DocumentMutationEvidence.Operation.DELETE)
        commits=0;opens=0;closes=0;fileSyncs=0;syncAttempts=0;syncReturns=0;renames=0;finalReads=0;lastDb=null
        return asset
    }
    private fun delete(asset:DocumentAsset)=producer().delete(c,deletes,deletes,"a",asset)
    private fun assertOutcome(expected:DocumentWriteOutcome,cleanup:WorkspaceWriteCleanup=WorkspaceWriteCleanup.COMPLETE,callbacks:Int=0) {
        assertEquals(callbacks,c.publications)
        assertEquals(expected,evidence.completion().outcome);assertEquals(cleanup,evidence.completion().cleanup)
        assertEquals(expected==DocumentWriteOutcome.COMPLETE,evidence.completion().finalInventoryConfirmed)
    }
    private fun invalid(work:()->Unit){assertThrows(ProductStorageFailure::class.java,work)}

    @Test fun invalidBeforeReserveHasNoEffectOrSource() {
        assertTrue(producer().import(c,imports,imports,imports,"a","",DocumentFormat.TEXT){error("source")} is OperationsResult.Rejected)
        assertEquals(0,commits);assertFalse(root().exists());assertOutcome(DocumentWriteOutcome.NOT_EXECUTED)
    }
    @Test fun capacityBeforeInsertConfirmsReturnedScopeWithoutDocumentEffects() {
        repeat(100){dao.docs.add(DocumentRow(id(),"a","synthetic","TEXT",0,1,id(),ByteArray(32)))}
        assertEquals(DocumentResult.Rejected(DocumentFailure.CAPACITY),import())
        assertTrue(dao.jobs.isEmpty());assertEquals(1,commits);assertEquals(0,opens);assertFalse(root().exists())
        assertOutcome(DocumentWriteOutcome.NOT_EXECUTED)
    }
    @Test fun unavailableSourceRetainsWritingWithConfirmedCleanup() {
        assertEquals(DocumentResult.Rejected(DocumentFailure.SOURCE_UNAVAILABLE),import{oneAttemptSource(c,DocumentSource{throw IOException("source")})})
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(2,commits);assertEquals(1,opens)
        assertEquals(1,File(root(),"staging").list()!!.size);assertOutcome(DocumentWriteOutcome.RECOVERY_REQUIRED)
    }
    @Test fun tooLargeAfterWritingIsNeverRolledBackOrComplete() {
        var left=DocumentRules.MAX_DOCUMENT_BYTES+1
        val result=import{object:InputStream(){override fun read():Int=error("bulk")
            override fun read(b:ByteArray,off:Int,len:Int):Int{if(left==0L)return -1;val n=minOf(left,len.toLong()).toInt();b.fill(120,off,off+n);left-=n;return n}}}
        assertEquals(DocumentResult.Rejected(DocumentFailure.TOO_LARGE),result)
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(2,commits);assertEquals(1,closes)
        assertTrue(File(root(),"staging").listFiles()!!.single().length()>0);assertOutcome(DocumentWriteOutcome.RECOVERY_REQUIRED)
    }
    @Test fun revokeAfterReturnedWritingKeepsRootlessJobFailClosed() {
        afterDb={if(it==DocumentMutationEvidence.Step.WRITING)c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){import()}
        assertFalse(root().exists());assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(0,opens)
        assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.WRITING));assertOutcome(DocumentWriteOutcome.RECOVERY_REQUIRED)
    }
    @Test fun revokeAfterCipherCloseBeforeClosedPreservesFileForceAndEarlierCommits() {
        beforeDb={if(it==DocumentMutationEvidence.Step.CLOSED)c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){import()}
        assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.CIPHER_CLOSE));assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.FILE_SYNC))
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(2,commits)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun failedFileSyncHasNoClosedCommit() {
        failFileSync=true;invalid{import()}
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertEquals(1,fileSyncs)
        assertFalse(evidence.isConfirmed(DocumentMutationEvidence.Step.FILE_SYNC));assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun renameBeforeSyncFailureKeepsPhysicalEffectAndClosedPhase() {
        failDirectorySync=true;invalid{import()}
        assertEquals(1,renames);assertEquals(1,syncAttempts);assertEquals(0,syncReturns);assertEquals("IMPORT_CLOSED",dao.jobs.single().phase)
        assertEquals(1,File(root(),"files").list()!!.size);assertTrue(File(root(),"staging").list()!!.isEmpty())
        assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.RENAME));assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun failureAfterPublishedPreservesFourCommitsAndTwoDirectorySyncs() {
        afterDb={if(it==DocumentMutationEvidence.Step.PUBLISHED)throw IOException("after published")}
        invalid{import()};assertEquals(4,commits);assertEquals("FILE_PUBLISHED",dao.jobs.single().phase);assertEquals(2,syncReturns)
        assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.PUBLISHED));assertOutcome(DocumentWriteOutcome.RECOVERY_REQUIRED)
    }
    @Test fun completeImportIsProducedByRealStepsAndPostCommitGlobalCheck() {
        assertEquals(DocumentResult.Value(Unit),import());assertEquals(5,commits);assertEquals(1,fileSyncs);assertEquals(2,syncReturns)
        assertEquals(1,opens);assertEquals(1,closes);assertEquals(1,dao.docs.size);assertTrue(dao.jobs.isEmpty());assertEquals(1,finalReads);assertOutcome(DocumentWriteOutcome.COMPLETE)
    }
    @Test fun detachFailureAndRevokePreserveParentRemovalAndBlob() {
        val asset=prepareDelete();afterDb={if(it==DocumentMutationEvidence.Step.DETACH)c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){delete(asset)}
        assertTrue(dao.docs.isEmpty());assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertEquals(1,File(root(),"files").list()!!.size)
        assertEquals(2,commits);assertEquals(0,unlinks);assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.DETACH));assertOutcome(DocumentWriteOutcome.RECOVERY_REQUIRED)
    }
    @Test fun errorAfterDetachDoesNotInventGlobalRollback() {
        val asset=prepareDelete();afterDb={if(it==DocumentMutationEvidence.Step.DETACH)throw IOException("after detach")}
        invalid{delete(asset)};assertTrue(dao.docs.isEmpty());assertEquals(2,commits);assertEquals(0,unlinks)
        assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertOutcome(DocumentWriteOutcome.RECOVERY_REQUIRED)
    }
    @Test fun unlinkBeforeSyncFailureIsIndependentConfirmedEffect() {
        val asset=prepareDelete();failDirectorySync=true;invalid{delete(asset)}
        assertEquals(1,unlinks);assertEquals(0,syncReturns);assertTrue(File(root(),"files").list()!!.isEmpty())
        assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.UNLINK))
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun completeDeleteHasThreeCommitsAndFreshGlobalInventory() {
        val asset=prepareDelete();delete(asset);assertEquals(3,commits);assertEquals(1,unlinks);assertEquals(1,syncReturns)
        assertEquals(0,opens);assertEquals(0,fileSyncs);assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty());assertEquals(1,finalReads);assertOutcome(DocumentWriteOutcome.COMPLETE)
    }
    @Test fun failedFinalReadAfterImportCommitCannotProduceComplete() {
        failFinalRead=true;assertThrows(IOException::class.java){import()}
        assertEquals(5,commits);assertEquals(1,dao.docs.size);assertTrue(dao.jobs.isEmpty())
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun failedFinalReadAfterDeleteKeepsPhysicalRemovalWithoutComplete() {
        val asset=prepareDelete();failFinalRead=true;assertThrows(IOException::class.java){delete(asset)}
        assertEquals(3,commits);assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty());assertEquals(1,unlinks)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun finalUnknownLeafBlocksCompleteAndIsNeverRemoved() {
        val foreign=File(root(),"files/${id()}")
        afterDb={if(it==DocumentMutationEvidence.Step.DOCUMENT_COMMIT)foreign.writeBytes(byteArrayOf(42))}
        invalid{import()};assertEquals(5,commits);assertTrue(foreign.isFile);assertEquals(0,unlinks)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun finalHardlinkCollisionBlocksCompleteWithoutUnlinks() {
        afterDb={if(it==DocumentMutationEvidence.Step.DOCUMENT_COMMIT){
            val d=dao.docs.single();val name=id();dao.docs.add(DocumentRow(id(),"a","synthetic","TEXT",d.plaintextSize,1,name,d.fileKey.copyOf()))
            Files.createLink(File(root(),"files/$name").toPath(),File(root(),"files/${d.blobName}").toPath())
        }}
        invalid{import()};assertEquals(2,dao.docs.size);assertEquals(0,unlinks);assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun cleanupFaultAfterLocalCommitKeepsCommitButCannotConfirmResources() {
        failCleanup=true;invalid{import()};assertEquals("IMPORT_RESERVED",dao.jobs.single().phase)
        assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.RESERVED));assertFalse(root().exists())
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun unconfirmedSnapshotCloseNeverProducesComplete() {
        failSnapshotClose=true;invalid{import()};assertEquals("IMPORT_CLOSED",dao.jobs.single().phase);assertEquals(0,renames)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun unknownTransactionEndNeverGetsOptimisticallyConfirmed() {
        failEnd=true;invalid{import()};assertTrue(dao.jobs.isEmpty());assertEquals(0,commits)
        assertFalse(evidence.isConfirmed(DocumentMutationEvidence.Step.RESERVED));assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun dispatchReplayDoesNotRepeatOrUndoConfirmedImport() {
        val worker=mutableListOf<Runnable>();val main=mutableListOf<Runnable>();var callbacks=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{worker.add(it)},java.util.concurrent.Executor{main.add(it)},
            {error("blocked")}).run<Unit>({callbacks++}){import()}
        val w=worker.single();w.run();w.run();assertEquals(5,commits);assertOutcome(DocumentWriteOutcome.COMPLETE)
        val m=main.single();m.run();m.run();assertEquals(1,callbacks);assertEquals(1,opens)
        DocumentNamespaceLane.drainAndFence{};assertEquals(1,dao.docs.size);assertOutcome(DocumentWriteOutcome.COMPLETE,callbacks=1)
    }
    @Test fun lostMainAndMaintenanceFenceDoNotChangePhysicalEvidence() {
        val worker=mutableListOf<Runnable>();val main=mutableListOf<Runnable>();var callbacks=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{worker.add(it)},java.util.concurrent.Executor{main.add(it)},
            {error("blocked")}).run<Unit>({callbacks++}){import()}
        worker.single().run();val old=main.single();main.clear();assertEquals(0,callbacks);assertOutcome(DocumentWriteOutcome.COMPLETE)
        DocumentNamespaceLane.drainAndFence{};old.run();old.run();assertEquals(0,callbacks);assertEquals(5,commits);assertOutcome(DocumentWriteOutcome.COMPLETE)
    }
    @Test fun evidenceRetainsOnlyFixedMetadataAndNeverScopesSourcesKeysOrCallbacks() {
        val fields=DocumentMutationEvidence::class.java.declaredFields.filterNot{it.isSynthetic}
        assertTrue(fields.all{it.type.isPrimitive || it.type==IntArray::class.java || it.type==DocumentMutationEvidence.Operation::class.java || it.type==DocumentWriteCompletion::class.java})
        assertOutcome(DocumentWriteOutcome.NOT_EXECUTED)
        assertFalse(UncachedWorkspaceMutations().ramReuseEnabled)
    }
    @Test fun finalSymlinkRootIsRejectedWithoutFollowingOrRepairingIt() {
        val saved=File(temp.root,"saved")
        afterDb={if(it==DocumentMutationEvidence.Step.DOCUMENT_COMMIT){
            Files.move(root().toPath(),saved.toPath());Files.createSymbolicLink(root().toPath(),saved.toPath())
        }}
        invalid{import()};assertTrue(Files.isSymbolicLink(root().toPath()));assertEquals(1,dao.docs.size);assertEquals(0,unlinks)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun finalPartialRootCannotBeReconstructedOrCalledComplete() {
        afterDb={if(it==DocumentMutationEvidence.Step.DOCUMENT_COMMIT)Files.delete(File(root(),"staging").toPath())}
        invalid{import()};assertFalse(File(root(),"staging").exists());assertEquals(1,dao.docs.size);assertEquals(0,unlinks)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun finalMissingExpectedBlobIsNotACompletedImport() {
        afterDb={if(it==DocumentMutationEvidence.Step.DOCUMENT_COMMIT)Files.delete(File(root(),"files/${dao.docs.single().blobName}").toPath())}
        invalid{import()};assertEquals(1,dao.docs.size);assertEquals(0,unlinks);assertEquals(5,commits)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun finalInvalidDatabaseBindingsAreRejectedByActualDaoRules() {
        afterDb={if(it==DocumentMutationEvidence.Step.DOCUMENT_COMMIT)dao.docs.add(copy(dao.docs.single()))}
        invalid{import()};assertEquals(2,dao.docs.size);assertEquals(0,unlinks)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun finalUnconfirmedJobPreventsCompleteWithoutHeuristicDeletion() {
        afterDb={if(it==DocumentMutationEvidence.Step.DOCUMENT_COMMIT)dao.jobs.add(DocumentJobRow(id(),"a",id(),"IMPORT","IMPORT_RESERVED",id(),id(),"synthetic","TEXT",null,ByteArray(32)))}
        invalid{import()};assertEquals("IMPORT_RESERVED",dao.jobs.single().phase);assertEquals(1,dao.docs.size);assertEquals(0,unlinks)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun actualSourceCloseFailureCannotCertifyCleanupDespiteReturnedFileSync() {
        val result=import{oneAttemptSource(c,DocumentSource{
            object:ByteArrayInputStream(byteArrayOf(120)){override fun close(){throw IOException("source close")}}
        })}
        assertEquals(DocumentResult.Rejected(DocumentFailure.SOURCE_UNAVAILABLE),result)
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertTrue(evidence.isConfirmed(DocumentMutationEvidence.Step.FILE_SYNC))
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun aProducerCannotReuseOrAlterItsFrozenCompletionForAnotherAttempt() {
        val p=producer();assertTrue(p.import(c,imports,imports,imports,"a","synthetic",DocumentFormat.TEXT){ByteArrayInputStream(byteArrayOf(120))} is OperationsResult.Value)
        val frozen=evidence.completion();assertEquals(DocumentWriteOutcome.COMPLETE,frozen.outcome)
        assertThrows(IllegalStateException::class.java){p.import(c,imports,imports,imports,"a","synthetic",DocumentFormat.TEXT){error("no replay source")}}
        assertSame(frozen,evidence.completion());assertEquals(5,commits);assertEquals(1,dao.docs.size)
    }
    @Test fun partialSourceRejectionMayPublishOnceWithoutTurningIntoCompleteEvidence() {
        val worker=mutableListOf<Runnable>();val main=mutableListOf<Runnable>();var callbacks=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{worker.add(it)},java.util.concurrent.Executor{main.add(it)},
            {error("no storage failure")}).run<Unit>({result->
            assertEquals(DocumentResult.Rejected(DocumentFailure.SOURCE_UNAVAILABLE),result);callbacks++
        }) {import{oneAttemptSource(c,DocumentSource{throw IOException("unavailable")})}}
        worker.single().run();val m=main.single();m.run();m.run()
        assertEquals(1,callbacks);assertEquals(2,commits);assertEquals("IMPORT_WRITING",dao.jobs.single().phase)
        assertOutcome(DocumentWriteOutcome.RECOVERY_REQUIRED,callbacks=1)
    }
    @Test fun failedFinalInventoryBlocksOnceAndDoesNotPublishPhysicalSuccess() {
        failFinalRead=true;val worker=mutableListOf<Runnable>();val main=mutableListOf<Runnable>();var callbacks=0;var blocked=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{worker.add(it)},java.util.concurrent.Executor{main.add(it)},
            {blocked++}).run<Unit>({callbacks++}){import()}
        val w=worker.single();w.run();w.run();val m=main.single();m.run();m.run()
        assertEquals(0,callbacks);assertEquals(1,blocked);assertEquals(5,commits);assertEquals(1,dao.docs.size)
        assertOutcome(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)
    }
    @Test fun lostDeleteMainLeavesPhysicalCompletionAndDoesNotReplayUnlink() {
        val asset=prepareDelete();val worker=mutableListOf<Runnable>();val main=mutableListOf<Runnable>();var callbacks=0
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext=c}
        DocumentProductDispatch(access,java.util.concurrent.Executor{worker.add(it)},java.util.concurrent.Executor{main.add(it)},
            {error("no storage failure")}).run<Unit>({callbacks++}){delete(asset);DocumentResult.Value(Unit)}
        val w=worker.single();w.run();w.run();assertEquals(1,main.size);main.clear()
        assertEquals(0,callbacks);assertEquals(3,commits);assertEquals(1,unlinks);assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty())
        assertOutcome(DocumentWriteOutcome.COMPLETE)
    }

}
