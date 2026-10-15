package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.data.prototype.b4.*
import org.pag.domain.*
import org.pag.security.*
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.util.UUID

/** Actual DAO rules/Tink/host filesystem, explicitly simulated transaction rollback. */
class DocumentReconciliationTest {
    @get:Rule val temp=TemporaryFolder()
    private class Access(override val scope:AccessScope=AccessScope.MAINTENANCE):StorageContext {
        var allowed=true;var commit=true;var checks=0;var atCheck:()->Unit={}
        override val generation=20L
        override fun check(point:AccessPoint):Boolean {checks++;atCheck();return allowed && (point!=AccessPoint.COMMIT || commit)}
        override fun publish(result:()->Unit):Boolean=error("No content publication")
    }
    private val c=Access()
    private fun id()=UUID.randomUUID().toString()
    private fun copy(j:DocumentJobRow)=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,j.phase,j.stagingName,j.blobName,j.displayName,j.format,j.plaintextSize,j.fileKey?.copyOf())
    private fun copy(d:DocumentRow)=DocumentRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt,d.blobName,d.fileKey.copyOf())
    private inner class Dao:DocumentDao() {
        var docs=mutableListOf<DocumentRow>();var jobs=mutableListOf<DocumentJobRow>();var status="ACTIVE"
        override suspend fun count(a:String)=docs.size
        override suspend fun list(a:String)=emptyList<DocumentMetadataRow>()
        override suspend fun get(a:String,id:String):DocumentMetadataRow?=null
        override suspend fun inventoryDocuments()=docs.map{copy(it)}
        override suspend fun inventoryJobs()=jobs.map{copy(it)}
        override suspend fun actionStatus(a:String)=status
        override suspend fun insertJob(job:DocumentJobRow){jobs.add(copy(job))}
        override suspend fun insertPublishedDocument(row:DocumentRow){check(inDb);docs.add(copy(row))}
        override suspend fun deletePublishedJob(id:String)=if(jobs.removeIf{it.id==id && it.phase=="FILE_PUBLISHED"})1 else 0
        override suspend fun changeImportPhase(id:String,old:String,next:String,size:Long?):Int {
            val index=jobs.indexOfFirst{it.id==id && it.phase==old};if(index<0)return 0
            val j=jobs[index];jobs[index]=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,next,j.stagingName,j.blobName,j.displayName,j.format,size,j.fileKey?.copyOf());return 1
        }
        override suspend fun removeInterruptedImport(id:String,phase:String)=if(jobs.removeIf{it.id==id && it.phase==phase})1 else 0
        override suspend fun removeDeleteParent(actionId:String,documentId:String)=if(docs.removeIf{it.id==documentId && it.actionId==actionId})1 else 0
        override suspend fun markDeletePending(id:String)=changeImportPhase(id,"DELETE_RESERVED","DELETE_FILE_PENDING",jobs.single{it.id==id}.plaintextSize)
        override suspend fun removeDeleteJob(id:String)=if(jobs.removeIf{it.id==id && it.phase=="DELETE_FILE_PENDING"})1 else 0
    }
    private val dao=Dao();private var inDb=false;private var dbFailure:Exception?=null
    private var afterCommit:()->Unit={};private var beforeCommit:()->Unit={};private var afterMark:()->Unit={}
    private fun <T> tx(work:suspend()->T):T {
        dbFailure?.let{throw it};check(!inDb);inDb=true
        val docs=dao.docs.map{copy(it)}.toMutableList();val jobs=dao.jobs.map{copy(it)}.toMutableList()
        try{return runBlocking{work()}}catch(e:Exception){dao.docs=docs;dao.jobs=jobs;throw e}finally{inDb=false}
    }
    private val db=object:DocumentReconciliationDatabase,DocumentDeleteDatabase {
        override fun inventory(c:StorageContext)=tx{dao.reconciliationSnapshot(c)}
        override fun abort(c:StorageContext,expected:DocumentRecoveryJob){tx{dao.abortInterruptedImport(expected,c)};afterCommit()}
        override fun publication(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?)=tx{dao.maintenancePublicationSnapshot(id,phase,expected,c)}
        override fun mark(c:StorageContext,expected:DocumentJobRow){tx{dao.maintenanceMarkPublished(expected,c)};afterMark()}
        override fun commit(c:StorageContext,expected:DocumentJobRow){beforeCommit();tx{dao.maintenanceCommitDocument(expected,42,c)};afterCommit()}
        override fun reserve(c:StorageContext,actionId:String,expected:DocumentAsset,jobId:String)=tx{dao.reserveDelete(actionId,expected,jobId,c)}
        override fun snapshot(c:StorageContext,id:String,expected:DocumentDeleteSnapshot?)=tx{dao.deleteSnapshot(id,expected,c)}
        override fun detach(c:StorageContext,expected:DocumentDeleteSnapshot){tx{dao.detachDeleteParent(expected,c)}}
        override fun finish(c:StorageContext,expected:DocumentDeleteSnapshot){tx{dao.finishDelete(expected,c)}}
    }
    private var links=0;private var unlinks=0;private var syncs=0
    private val ops=object:DocumentPublishFileOps {
        override fun link(source:Path,destination:Path){check(!inDb);links++;Files.createLink(destination,source)}
        override fun unlink(source:Path){check(!inDb);unlinks++;Files.delete(source)}
        override fun syncDirectory(directory:Path){check(!inDb);syncs++;FileChannel.open(directory,READ).use{it.force(true)}}
    }
    private fun root()=File(temp.root,"pag-documents-v1")
    private fun dirs(){File(root(),"staging").mkdirs();File(root(),"files").mkdir()}
    private fun stage(j:DocumentJobRow)=File(root(),"staging/${j.stagingName}")
    private fun final(j:DocumentJobRow)=File(root(),"files/${j.blobName}")
    private fun job(phase:String,location:String="none"):DocumentJobRow=DocumentFileStreams.withNewKey(c){key->
        val j=DocumentJobRow(id(),"a",id(),"IMPORT",phase,id(),id(),"synthetic","TEXT",if(phase in setOf("IMPORT_RESERVED","IMPORT_WRITING"))null else 9,key.copyOf())
        dao.jobs.add(j)
        if(location!="none") {
            dirs();val file=if(location=="staging")stage(j) else final(j)
            DocumentFileStreams.encrypt(c,j.documentId,key,ByteArrayInputStream("synthetic".toByteArray()),file.outputStream())
        };j
    }
    private fun document():DocumentRow {
        val j=job("FILE_PUBLISHED","final");dao.jobs.remove(j)
        return DocumentRow(j.documentId,j.actionId,j.displayName!!,j.format!!,j.plaintextSize!!,1,j.blobName,j.fileKey!!.copyOf()).also{dao.docs.add(it)}
    }
    private fun deleteJob(phase:String):DocumentJobRow {
        val d=document();if(phase=="DELETE_FILE_PENDING")dao.docs.remove(d)
        return DocumentJobRow(id(),d.actionId,d.id,"DELETE",phase,null,d.blobName,null,null,d.plaintextSize,null).also{dao.jobs.add(it)}
    }
    private fun reconcile(context:StorageContext=c)=DocumentReconciliation(db,db,temp.root,ops).run(context)
    private fun invalid(work:()->Unit){assertThrows(ProductStorageFailure::class.java,work)}
    private fun corrupt(file:File){RandomAccessFile(file,"rw").use{it.seek(it.length()-1);it.write(42)}}

    @Test fun emptyMissingRootRemainsMissing(){reconcile();assertFalse(root().exists());assertEquals(0,unlinks)}
    @Test fun readyDocumentIsNotDecryptedOnUnlock(){val d=document();val f=File(root(),"files/${d.blobName}");f.writeBytes(byteArrayOf(1));reconcile();assertTrue(f.exists());assertEquals(1,dao.docs.size);assertEquals(0,unlinks)}
    @Test fun reservedWithoutRootIsDiscarded(){job("IMPORT_RESERVED");reconcile();assertTrue(dao.jobs.isEmpty());assertFalse(root().exists())}
    @Test fun reservedWithArtifactBlocks(){val j=job("IMPORT_RESERVED","staging");invalid{reconcile()};assertTrue(stage(j).exists());assertEquals(1,dao.jobs.size)}
    @Test fun writingStagingIsRemovedThenJobDeleted(){val j=job("IMPORT_WRITING","staging");reconcile();assertFalse(stage(j).exists());assertTrue(dao.jobs.isEmpty());assertEquals(1,unlinks);assertEquals(1,syncs)}
    @Test fun writingWithoutStagingIsIdempotent(){dirs();job("IMPORT_WRITING");reconcile();reconcile();assertTrue(dao.jobs.isEmpty());assertEquals(0,unlinks);assertEquals(1,syncs)}
    @Test fun writingWithFinalBlocks(){val j=job("IMPORT_WRITING","final");invalid{reconcile()};assertTrue(final(j).exists());assertEquals(0,unlinks)}
    @Test fun closedStagingIsVerifiedMovedAndCommitted(){val j=job("IMPORT_CLOSED","staging");reconcile();assertTrue(stage(j).exists().not());assertTrue(final(j).exists());assertTrue(dao.jobs.isEmpty());assertEquals(j.documentId,dao.docs.single().id);assertEquals(1,links)}
    @Test fun closedFinalIsVerifiedWithoutSecondMove(){val j=job("IMPORT_CLOSED","final");reconcile();assertTrue(final(j).exists());assertTrue(dao.jobs.isEmpty());assertEquals(1,dao.docs.size);assertEquals(0,links)}
    @Test fun closedBothBlocksWithoutHardlinkRepair(){val j=job("IMPORT_CLOSED","staging");Files.createLink(final(j).toPath(),stage(j).toPath());invalid{reconcile()};assertTrue(stage(j).exists());assertTrue(final(j).exists());assertEquals(0,unlinks)}
    @Test fun closedNeitherBlocks(){dirs();job("IMPORT_CLOSED");invalid{reconcile()};assertEquals(1,dao.jobs.size)}
    @Test fun publishedFinalCommitsExactlyOnce(){job("FILE_PUBLISHED","final");reconcile();reconcile();assertEquals(1,dao.docs.size);assertTrue(dao.jobs.isEmpty());assertEquals(0,links)}
    @Test fun corruptClosedAndPublishedArePreserved(){for(p in listOf("IMPORT_CLOSED","FILE_PUBLISHED")){val j=job(p,"final");corrupt(final(j));invalid{reconcile()};assertTrue(final(j).exists());assertTrue(dao.docs.isEmpty());dao.jobs.clear();final(j).delete()}}
    @Test fun bothDeletePhasesReuseRecovery(){val a=deleteJob("DELETE_RESERVED");val b=deleteJob("DELETE_FILE_PENDING");reconcile();assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isEmpty());assertFalse(final(a).exists());assertFalse(final(b).exists());assertEquals(2,unlinks)}
    @Test fun missingPendingBlobIsAccepted(){val j=deleteJob("DELETE_FILE_PENDING");final(j).delete();reconcile();assertTrue(dao.jobs.isEmpty());assertEquals(0,unlinks)}
    @Test fun unknownFileBlocksBeforeAnyKnownCleanup(){val j=job("IMPORT_WRITING","staging");val f=File(root(),"files/${id()}");f.writeBytes(byteArrayOf(1));invalid{reconcile()};assertTrue(f.exists());assertTrue(stage(j).exists());assertEquals(0,unlinks)}
    @Test fun symlinkBlocks(){val j=job("IMPORT_WRITING","staging");val f=File(root(),"files/${id()}");Files.createSymbolicLink(f.toPath(),stage(j).toPath());invalid{reconcile()};assertTrue(Files.isSymbolicLink(f.toPath()));assertEquals(0,unlinks)}
    @Test fun corruptBindingBlocksBeforeFilesystemMutation(){val j=job("IMPORT_WRITING","staging");dao.jobs.add(copy(j));invalid{reconcile()};assertTrue(stage(j).exists());assertEquals(0,unlinks)}
    @Test fun failedDatabaseOpenNeverCleansFiles(){val j=job("IMPORT_WRITING","staging");dbFailure=IOException("synthetic");assertThrows(IOException::class.java){reconcile()};assertTrue(stage(j).exists());assertEquals(0,unlinks)}
    @Test fun revokeDuringVerificationPreventsCommit(){val j=job("IMPORT_CLOSED","staging");var verifyChecks=0;c.atCheck={if(Thread.currentThread().stackTrace.any{it.className=="org.pag.data.product.DocumentFileStreams" && it.methodName=="verify"}){verifyChecks++;if(verifyChecks==5)c.allowed=false}};assertThrows(StorageAccessRevoked::class.java){reconcile()};assertTrue(verifyChecks>=5);assertTrue(dao.docs.isEmpty());assertTrue(dao.jobs.isNotEmpty());assertTrue(stage(j).exists()||final(j).exists())}
    @Test fun revokeAfterCommitDoesNotClaimRollback(){job("FILE_PUBLISHED","final");afterCommit={c.allowed=false};assertThrows(StorageAccessRevoked::class.java){reconcile()};assertTrue(dao.jobs.isEmpty());assertEquals(1,dao.docs.size)}
    @Test fun commitDenialRollsBackFinalInsert(){job("FILE_PUBLISHED","final");beforeCommit={c.commit=false};assertThrows(StorageAccessRevoked::class.java){reconcile()};assertTrue(dao.docs.isEmpty());assertEquals("FILE_PUBLISHED",dao.jobs.single().phase)}
    @Test fun allPhasesCanCoexistAndSettle(){dirs();job("IMPORT_RESERVED");job("IMPORT_WRITING","staging");job("IMPORT_CLOSED","final");job("FILE_PUBLISHED","final");deleteJob("DELETE_RESERVED");deleteJob("DELETE_FILE_PENDING");reconcile();assertEquals(2,dao.docs.size);assertTrue(dao.jobs.isEmpty())}
    @Test fun scopedMaintenanceKeyCannotEscape(){var escaped:((StorageContext,(ByteArray)->Unit)->Unit)?=null;val key=ByteArray(32){1};withDocumentMaintenanceKey(c,key){provider->escaped=provider;provider(c){assertSame(key,it)}};invalid{escaped!!(c){error("Escaped key")}};key.fill(0)}
    @Test fun changedHeaderAfterPublishedCannotSilentlyReachFinalCommit() {
        val original=job("IMPORT_CLOSED","staging")
        afterMark={val j=dao.jobs.single();dao.jobs[0]=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,j.phase,j.stagingName,j.blobName,"changed",j.format,j.plaintextSize,j.fileKey)}
        invalid{reconcile()};assertTrue(dao.docs.isEmpty());assertEquals("FILE_PUBLISHED",dao.jobs.single().phase);assertTrue(final(original).exists())
    }
    @Test fun readyScopeCannotRunMaintenance(){invalid{reconcile(Access(AccessScope.READY))};assertFalse(root().exists())}
    @Test fun backgroundFaultIsNotReclassifiedAsRevoke(){val fault=B4Exception(B4Id.BACKGROUND);dbFailure=fault;assertSame(fault,assertThrows(B4Exception::class.java){reconcile()})}
    @Test fun oldTicketsRejectedAfterGlobalFence(){val ticket=DocumentNamespaceLane.ticket();reconcile();invalid{DocumentNamespaceLane.exclusive(ticket){error("Old writer")}}}

    @Test fun globalFenceDrainsActualReaderBeforeFinishing() {
        val d=document();val reader=Access(AccessScope.READY)
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        var first=true;reader.atCheck={if(first){first=false;entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}}
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        val ticket=DocumentNamespaceLane.ticket()
        try {
            val reading=pool.submit{DocumentFileInventory(temp.root).verifyExisting(reader,DocumentFileArea.FILES,d.blobName,emptySet(),setOf(d.blobName),d.id,d.fileKey,9,DocumentFormat.TEXT)}
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
            val recovery=pool.submit{reconcile()}
            assertThrows(java.util.concurrent.TimeoutException::class.java){recovery.get(50,java.util.concurrent.TimeUnit.MILLISECONDS)}
            release.countDown();reading.get(5,java.util.concurrent.TimeUnit.SECONDS);recovery.get(5,java.util.concurrent.TimeUnit.SECONDS)
            invalid{DocumentNamespaceLane.exclusive(ticket){error("Old writer")}}
        }finally{release.countDown();pool.shutdownNow()}
    }
    @Test fun missingFinishedBlobBlocks() {
        val d=document();File(root(),"files/${d.blobName}").delete();invalid{reconcile()}
        assertEquals(1,dao.docs.size)
    }
    @Test fun partialRootIsNotSilentlyRecreated(){File(root(),"staging").mkdirs();invalid{reconcile()};assertFalse(File(root(),"files").exists())}
    @Test fun preflightRunsInsideLaneBeforeInventoryAndDoesNotCleanupOnFailure() {
        val j=job("IMPORT_WRITING","staging")
        assertThrows(IOException::class.java){DocumentReconciliation(db,db,temp.root,ops).run(c){DocumentNamespaceLane.requireExclusive();throw IOException("synthetic")}}
        assertTrue(stage(j).exists());assertEquals(0,unlinks)
    }
    @Test fun realSessionLateMaintenanceKeepsOriginalAbsoluteDeadline(){sessionFlow(false,false,170_000)}
    @Test fun realSessionOnlyReadiesAfterSuccessfulBarrier(){sessionFlow(false,false)}
    @Test fun realSessionBlocksOnUnknownInventory(){dirs();File(root(),"files/${id()}").writeBytes(byteArrayOf(1));sessionFlow(true,false)}
    @Test fun realSessionExpiryDuringMaintenanceDoesNotReady(){sessionFlow(false,true)}
    private fun sessionFlow(failure:Boolean,expire:Boolean,delay:Long=0) {
        var now=0L;var callback:((AuthenticationOutcome)->Unit)?=null;var queued:(()->Unit)?=null;var prepared=false
        lateinit var session:SessionCoordinator
        session=SessionCoordinator(object:AuthenticationExperiment{
            override fun start(mode:KeyExperiment,complete:(AuthenticationOutcome)->Unit):Cancellation{callback=complete;return Cancellation{}}
        },Maintenance{Subscription{}},MonotonicTime{now},SessionPolicy(180_000,30_000),{true},authorizedMaintenance={context,_,done->
            queued={var success=false;try{documentMaintenanceBarrier(context,{assertEquals(SessionState.MAINTENANCE,session.snapshot().state);reconcile(context)},{prepared=true});success=true}catch(_:Exception){};done(success)}
            Subscription{}
        })
        session.foreground(true);session.authenticate(Experiment.STORAGE_UNWRAP)
        callback!!(AuthenticationOutcome.Granted(object:SyntheticOperation{override fun perform()=OperationResult.SUCCEEDED;override fun release(){}},0))
        assertEquals(SessionState.MAINTENANCE,session.snapshot().state);assertFalse(prepared)
        now=if(expire)180_000 else delay
        queued!!()
        assertEquals(if(expire)SessionState.LOCKED else if(failure)SessionState.BLOCKED else SessionState.READY,session.snapshot().state)
        assertEquals(!failure&&!expire,prepared)
        if(!failure&&!expire){now=minOf(delay+30_000,180_000);session.checkExpiry();assertEquals(SessionState.LOCKED,session.snapshot().state)}
    }
}
