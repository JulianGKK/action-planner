package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.io.File
import java.util.UUID
import java.util.concurrent.*

/** Host files/Tink and actual DAO validation; transactions below simulate rollback, not Room. */
class DocumentDeleteTest {
    @get:Rule val temp=TemporaryFolder()
    private open class Access(override val scope:AccessScope=AccessScope.READY,override val generation:Long=10):StorageContext {
        var allowed=true;var commit=true
        override fun check(point:AccessPoint)=allowed && (point!=AccessPoint.COMMIT || commit)
        override fun publish(result:()->Unit):Boolean=error("No external publication")
    }
    private val c=Access()
    private val maintenance=Access(AccessScope.MAINTENANCE,11)
    private fun id()=UUID.randomUUID().toString()
    private fun copy(j:DocumentJobRow)=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,j.phase,j.stagingName,j.blobName,j.displayName,j.format,j.plaintextSize,j.fileKey?.copyOf())
    private fun copy(d:DocumentRow)=DocumentRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt,d.blobName,d.fileKey.copyOf())
    private val readKeys=mutableListOf<ByteArray>()
    private inner class Dao:DocumentDao() {
        var status="ACTIVE";var jobs=mutableListOf<DocumentJobRow>();var docs=mutableListOf<DocumentRow>()
        var failPending=false;var failFinish=false
        private fun visible(a:String)=docs.filter { d->d.actionId==a && jobs.none { it.documentId==d.id && it.operation=="DELETE" } }
        private fun metadata(d:DocumentRow)=DocumentMetadataRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt)
        override suspend fun count(a:String)=visible(a).size
        override suspend fun list(a:String)=visible(a).sortedWith(compareBy<DocumentRow>{it.importedAt}.thenBy{it.id}).map {metadata(it)}
        override suspend fun get(a:String,id:String)=visible(a).singleOrNull { it.id==id }?.let {metadata(it)}
        override suspend fun inventoryDocuments()=docs.map {copy(it).also { d->readKeys.add(d.fileKey) }}
        override suspend fun inventoryJobs()=jobs.map {copy(it)}
        override suspend fun actionStatus(a:String)=status
        override suspend fun insertJob(job:DocumentJobRow) {jobs.add(copy(job))}
        override suspend fun changeImportPhase(id:String,old:String,next:String,size:Long?):Int=error("No import")
        override suspend fun insertPublishedDocument(row:DocumentRow):Unit=error("No import")
        override suspend fun deletePublishedJob(id:String):Int=error("No import")
        override suspend fun removeDeleteParent(actionId:String,documentId:String):Int {
            check(inDb)
            return if(docs.removeIf {it.actionId==actionId && it.id==documentId})1 else 0
        }
        override suspend fun markDeletePending(id:String):Int {
            if(failPending)return 0
            val index=jobs.indexOfFirst {it.id==id && it.phase=="DELETE_RESERVED"};if(index<0)return 0
            val j=jobs[index];jobs[index]=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,"DELETE_FILE_PENDING",null,j.blobName,null,null,j.plaintextSize,null)
            return 1
        }
        override suspend fun removeDeleteJob(id:String):Int {
            if(failFinish)return 0
            return if(jobs.removeIf {it.id==id && it.phase=="DELETE_FILE_PENDING"})1 else 0
        }
    }
    private val dao=Dao();private var inDb=false
    private var afterReserve:()->Unit={};private var beforeDetach:()->Unit={};private var afterDetach:()->Unit={}
    private var beforeFinish:()->Unit={};private var afterFinish:()->Unit={}
    private fun <T> tx(work:suspend()->T):T {
        check(!inDb);inDb=true
        val oldDocs=dao.docs.map {copy(it)}.toMutableList();val oldJobs=dao.jobs.map {copy(it)}.toMutableList()
        try{return runBlocking{work()}}
        catch(e:Exception){dao.docs=oldDocs;dao.jobs=oldJobs;throw e}
        finally{inDb=false}
    }
    private val db=object:DocumentDeleteDatabase {
        override fun reserve(c:StorageContext,actionId:String,expected:DocumentAsset,jobId:String)=
            tx{dao.reserveDelete(actionId,expected,jobId,c)}.also{afterReserve()}
        override fun snapshot(c:StorageContext,id:String,expected:DocumentDeleteSnapshot?)=tx{dao.deleteSnapshot(id,expected,c)}
        override fun detach(c:StorageContext,expected:DocumentDeleteSnapshot){beforeDetach();tx{dao.detachDeleteParent(expected,c)};afterDetach()}
        override fun finish(c:StorageContext,expected:DocumentDeleteSnapshot){beforeFinish();tx{dao.finishDelete(expected,c)};afterFinish()}
    }
    private var unlinks=0;private var syncs=0
    private val ops=object:DocumentPublishFileOps {
        override fun link(source:Path,destination:Path):Unit=error("Delete never links")
        override fun unlink(source:Path){check(!inDb);unlinks++;assertTrue(dao.docs.none{it.blobName==source.fileName.toString()});assertEquals("DELETE_FILE_PENDING",dao.jobs.single{it.blobName==source.fileName.toString()}.phase);Files.delete(source)}
        override fun syncDirectory(directory:Path){check(!inDb);syncs++;FileChannel.open(directory,READ).use{it.force(true)}}
    }
    private fun root()=File(temp.root,"pag-documents-v1")
    private fun blob(d:DocumentRow)=File(root(),"files/${d.blobName}")
    private fun fixture():DocumentRow {
        File(root(),"files").mkdirs();File(root(),"staging").mkdir()
        return DocumentFileStreams.withNewKey(c){key->
            val d=DocumentRow(id(),"a","synthetic","TEXT",9,42,id(),key.copyOf())
            DocumentFileStreams.encrypt(c,d.id,key,ByteArrayInputStream("synthetic".toByteArray()),blob(d).outputStream())
            dao.docs.add(d);d
        }
    }
    private fun asset(d:DocumentRow)=DocumentMetadataRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt).model()
    private fun coordinator(fileOps:DocumentPublishFileOps=ops)=DocumentDelete(db,temp.root,fileOps)
    private fun delete(d:DocumentRow)=coordinator().delete(c,"a",asset(d))
    private fun reserved(d:DocumentRow)=db.reserve(c,"a",asset(d),id())
    private fun invalid(work:()->Unit){assertThrows(ProductStorageFailure::class.java,work)}

    @Test fun reservedHidesAllProjectionsAndCarriesNoKeyNameOrFormat() {
        val d=fixture();afterReserve={
            assertEquals(1,dao.docs.size)
            runBlocking{assertEquals(0,dao.count("a"));assertTrue(dao.list("a").isEmpty());assertNull(dao.get("a",d.id))}
            val j=dao.jobs.single();assertNull(j.fileKey);assertNull(j.displayName);assertNull(j.format);assertNull(j.stagingName)
            assertEquals(d.blobName,j.blobName);assertEquals(d.plaintextSize,j.plaintextSize)
        }
        delete(d);assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty());assertFalse(blob(d).exists())
        assertEquals(1,unlinks);assertEquals(1,syncs);assertTrue(readKeys.all {k->k.all{it==0.toByte()}})
    }
    @Test fun pendingFailureRollsBackParentAndKeepsReservedJob() {
        val d=fixture();dao.failPending=true
        invalid{delete(d)};assertEquals(1,dao.docs.size);assertEquals("DELETE_RESERVED",dao.jobs.single().phase)
        assertTrue(blob(d).exists());assertEquals(0,unlinks)
    }
    @Test fun missingBeforePendingBlocksWithoutParentDelete() {
        val d=fixture();blob(d).delete()
        invalid{delete(d)};assertEquals(1,dao.docs.size);assertEquals("DELETE_RESERVED",dao.jobs.single().phase);assertEquals(0,unlinks)
    }
    @Test fun missingAfterPendingIsIdempotentlyAcceptedAndSynced() {
        val d=fixture();val r=reserved(d);db.detach(c,r);blob(d).delete()
        coordinator().recover(maintenance,r.job.id)
        assertEquals(0,unlinks);assertEquals(1,syncs);assertTrue(dao.jobs.isEmpty());assertTrue(dao.docs.isEmpty())
    }
    @Test fun reservedRecoveryChecksParentAndDeletesKnownBlob() {
        val d=fixture();val r=reserved(d)
        coordinator().recover(maintenance,r.job.id)
        assertEquals(1,unlinks);assertTrue(dao.jobs.isEmpty());assertFalse(blob(d).exists())
    }
    @Test fun pendingRecoveryUnlinksExistingBlob() {
        val d=fixture();val r=reserved(d);db.detach(c,r)
        coordinator().recover(maintenance,r.job.id)
        assertEquals(1,unlinks);assertTrue(dao.jobs.isEmpty())
    }
    @Test fun reservedRecoveryWithMissingBlobBlocks() {
        val d=fixture();val r=reserved(d);blob(d).delete()
        invalid{coordinator().recover(maintenance,r.job.id)}
        assertEquals(1,dao.docs.size);assertEquals("DELETE_RESERVED",dao.jobs.single().phase)
    }
    @Test fun inconsistentParentBindingBlocksRecovery() {
        val d=fixture();val r=reserved(d)
        dao.docs[0]=DocumentRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize+1,d.importedAt,d.blobName,d.fileKey)
        invalid{coordinator().recover(maintenance,r.job.id)};assertTrue(blob(d).exists());assertEquals(0,unlinks)
    }
    @Test fun foreignFileBlocksWithoutAnyUnlink() {
        val d=fixture();val foreign=File(root(),"files/${id()}");foreign.writeBytes(byteArrayOf(1))
        invalid{delete(d)};assertTrue(foreign.exists());assertTrue(blob(d).exists());assertEquals(0,unlinks)
    }
    @Test fun symlinkTargetBlocksAndOutsideFileSurvives() {
        val d=fixture();blob(d).delete();val outside=temp.newFile();outside.writeBytes(byteArrayOf(42))
        Files.createSymbolicLink(blob(d).toPath(),outside.toPath())
        invalid{delete(d)};assertTrue(Files.isSymbolicLink(blob(d).toPath()));assertArrayEquals(byteArrayOf(42),outside.readBytes());assertEquals(0,unlinks)
    }
    @Test fun deletingOneDocumentPreservesOtherKnownCiphertextAndMetadata() {
        val first=fixture();val other=fixture();val before=blob(other).readBytes()
        delete(first)
        assertFalse(blob(first).exists());assertArrayEquals(before,blob(other).readBytes())
        assertEquals(listOf(other.id),dao.docs.map{it.id});assertTrue(dao.jobs.isEmpty())
        runBlocking{assertEquals(1,dao.count("a"));assertEquals(other.id,dao.list("a").single().id)}
    }
    @Test fun stagingIsNeverDeletedEvenForKnownClosedImport() {
        val d=fixture();val stage=id()
        dao.jobs.add(DocumentJobRow(id(),"a",id(),"IMPORT","IMPORT_CLOSED",stage,id(),"synthetic","TEXT",0,ByteArray(32)))
        val staging=File(root(),"staging/$stage");staging.writeBytes(byteArrayOf(1))
        val safeOps=object:DocumentPublishFileOps by ops{
            override fun unlink(source:Path){assertEquals(blob(d).toPath(),source);Files.delete(source);unlinks++}
        }
        coordinator(safeOps).delete(c,"a",asset(d));assertTrue(staging.exists());assertEquals(1,dao.jobs.size);assertEquals("IMPORT",dao.jobs.single().operation)
    }
    @Test fun unlinkFailureKeepsPendingWithoutLiveKey() {
        val d=fixture();val fail=object:DocumentPublishFileOps by ops{override fun unlink(source:Path){throw IOException("synthetic")}}
        invalid{coordinator(fail).delete(c,"a",asset(d))}
        assertTrue(dao.docs.isEmpty());assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertNull(dao.jobs.single().fileKey);assertTrue(blob(d).exists())
    }
    @Test fun syncFailureKeepsPendingAndRecoveryDoesNotUnlinkTwice() {
        val d=fixture();val fail=object:DocumentPublishFileOps by ops{override fun syncDirectory(directory:Path){throw IOException("sync")}}
        invalid{coordinator(fail).delete(c,"a",asset(d))};assertFalse(blob(d).exists());assertEquals(1,unlinks)
        coordinator().recover(maintenance,dao.jobs.single().id);assertEquals(1,unlinks);assertTrue(dao.jobs.isEmpty())
    }
    @Test fun revokeAtReservationCommitCreatesNoJob() {
        val d=fixture();c.commit=false
        assertThrows(StorageAccessRevoked::class.java){delete(d)}
        assertEquals(1,dao.docs.size);assertTrue(dao.jobs.isEmpty());assertTrue(blob(d).exists())
    }
    @Test fun revokeBeforePendingCommitPreservesParentAndReserved() {
        val d=fixture();beforeDetach={c.commit=false}
        assertThrows(StorageAccessRevoked::class.java){delete(d)}
        assertEquals(1,dao.docs.size);assertEquals("DELETE_RESERVED",dao.jobs.single().phase);assertEquals(0,unlinks)
    }
    @Test fun revokeAfterPendingCommitPreservesRealPendingState() {
        val d=fixture();afterDetach={c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){delete(d)}
        assertTrue(dao.docs.isEmpty());assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertTrue(blob(d).exists())
    }
    @Test fun finalCommitDenialKeepsPendingButTargetIsAlreadyAbsent() {
        val d=fixture();beforeFinish={c.commit=false}
        assertThrows(StorageAccessRevoked::class.java){delete(d)}
        assertTrue(dao.docs.isEmpty());assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertFalse(blob(d).exists())
    }
    @Test fun revokeAfterFinalCommitDoesNotPretendRollback() {
        val d=fixture();afterFinish={c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java){delete(d)}
        assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty());assertFalse(blob(d).exists())
    }
    @Test fun finalDeleteFailureKeepsPendingAndCanRecover() {
        val d=fixture();dao.failFinish=true
        invalid{delete(d)};assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertFalse(blob(d).exists())
        dao.failFinish=false;coordinator().recover(maintenance,dao.jobs.single().id);assertTrue(dao.jobs.isEmpty());assertEquals(1,unlinks)
    }
    @Test fun duplicateDeleteAndRecoveryNeverUnlinkTwice() {
        val d=fixture();delete(d)
        invalid{delete(d)};invalid{coordinator().recover(maintenance,id())};assertEquals(1,unlinks)
    }
    @Test fun staleSnapshotOrWrongActionCannotReserve() {
        val d=fixture()
        invalid{coordinator().delete(c,"a",asset(d).copy(displayName="changed"))}
        invalid{coordinator().delete(c,"other",asset(d))}
        assertTrue(dao.jobs.isEmpty());assertTrue(blob(d).exists())
    }
    @Test fun recoveryRequiresMaintenanceAndDoesNotReopenAction() {
        val d=fixture();val r=reserved(d)
        invalid{coordinator().recover(c,r.job.id)}
        dao.status="DELETION_PENDING"
        coordinator().recover(maintenance,r.job.id)
        assertEquals("DELETION_PENDING",dao.status);assertTrue(dao.jobs.isEmpty())
    }
    @Test fun readyDisallowedPhaseAndMaintenanceNewDeleteDenied() {
        val d=fixture();dao.status="DELETION_PENDING"
        invalid{delete(d)}
        invalid{coordinator().delete(maintenance,"a",asset(d))}
        assertTrue(dao.jobs.isEmpty());assertTrue(blob(d).exists())
    }
    @Test fun targetReappearingBeforeFinishKeepsPending() {
        val d=fixture();val ciphertext=blob(d).readBytes()
        val resurrect=object:DocumentPublishFileOps by ops {
            override fun syncDirectory(directory:Path){ops.syncDirectory(directory);blob(d).writeBytes(ciphertext)}
        }
        invalid{coordinator(resurrect).delete(c,"a",asset(d))}
        assertEquals("DELETE_FILE_PENDING",dao.jobs.single().phase);assertTrue(blob(d).exists());assertTrue(dao.docs.isEmpty())
    }
    @Test fun revokedMaintenanceCannotResumeJob() {
        val d=fixture();val r=reserved(d);maintenance.allowed=false
        assertThrows(StorageAccessRevoked::class.java){coordinator().recover(maintenance,r.job.id)}
        assertEquals(1,dao.docs.size);assertEquals(0,unlinks);assertTrue(blob(d).exists())
    }
    @Test fun parentUnexpectedlyPresentInPendingBlocks() {
        val d=fixture();val r=reserved(d);db.detach(c,r);dao.docs.add(copy(d))
        invalid{coordinator().recover(maintenance,r.job.id)}
        assertEquals(0,unlinks);assertTrue(blob(d).exists());assertEquals(1,dao.docs.size)
    }
    @Test fun fenceRejectsOldQueuedWorker() {
        val ticket=DocumentNamespaceLane.ticket();DocumentNamespaceLane.drainAndFence{}
        invalid{DocumentNamespaceLane.exclusive(ticket){error("Old worker")}}
    }
    @Test fun existingFileReaderDrainsBeforeDeleteReservation() {
        val d=fixture();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val executor=Executors.newFixedThreadPool(2)
        val readerAccess=object:Access(){
            var blocked=false
            override fun check(point:AccessPoint):Boolean {
                if(point==AccessPoint.STEP && !blocked){blocked=true;entered.countDown();check(release.await(5,TimeUnit.SECONDS))}
                return super.check(point)
            }
        }
        try {
            val reader=executor.submit{DocumentFileInventory(temp.root).verifyExisting(readerAccess,DocumentFileArea.FILES,d.blobName,emptySet(),setOf(d.blobName),d.id,d.fileKey,9,DocumentFormat.TEXT)}
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            val deleting=executor.submit{delete(d)}
            assertThrows(TimeoutException::class.java){deleting.get(50,TimeUnit.MILLISECONDS)}
            assertTrue(dao.jobs.isEmpty());release.countDown();reader.get(5,TimeUnit.SECONDS);deleting.get(5,TimeUnit.SECONDS)
            assertEquals(1,unlinks)
        }finally{release.countDown();executor.shutdownNow()}
    }
}
