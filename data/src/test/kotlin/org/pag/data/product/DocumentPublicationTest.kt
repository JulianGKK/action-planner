package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.util.UUID

/** Real Tink and host Linux hardlink/fsync, simulated DAO transaction rollback. No Android claim. */
class DocumentPublicationTest {
    @get:Rule val temp = TemporaryFolder()
    private class Access : StorageContext {
        var allowed=true;var commit=true
        override val generation=8L
        override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=allowed && (point!=AccessPoint.COMMIT || commit)
        override fun publish(result:()->Unit):Boolean=error("No external publication")
    }
    private val c=Access()
    private fun id()=UUID.randomUUID().toString()
    private fun copy(j:DocumentJobRow)=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,j.phase,j.stagingName,j.blobName,j.displayName,j.format,j.plaintextSize,j.fileKey?.copyOf())
    private fun copy(d:DocumentRow)=DocumentRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt,d.blobName,d.fileKey.copyOf())
    private inner class Dao : DocumentDao() {
        var status="ACTIVE"
        var jobs=mutableListOf<DocumentJobRow>()
        var docs=mutableListOf<DocumentRow>()
        var insertFails=false;var deleteFails=false
        override suspend fun removeDeleteParent(actionId:String,documentId:String):Int = error("Not used here")
        override suspend fun markDeletePending(id:String):Int = error("Not used here")
        override suspend fun removeDeleteJob(id:String):Int = error("Not used here")
        override suspend fun count(a:String)=docs.size
        override suspend fun list(a:String)=emptyList<DocumentMetadataRow>()
        override suspend fun get(a:String,id:String):DocumentMetadataRow?=null
        override suspend fun actionStatus(a:String)=status
        override suspend fun inventoryDocuments()=docs.map { copy(it) }
        override suspend fun inventoryJobs()=jobs.map { copy(it) }
        override suspend fun insertJob(job:DocumentJobRow) { jobs.add(copy(job)) }
        override suspend fun changeImportPhase(id:String,old:String,next:String,size:Long?):Int {
            val index=jobs.indexOfFirst { it.id==id && it.phase==old };if(index<0)return 0
            val j=jobs[index]
            jobs[index]=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,next,j.stagingName,j.blobName,j.displayName,j.format,size,j.fileKey?.copyOf())
            return 1
        }
        override suspend fun insertPublishedDocument(row:DocumentRow) {
            if(insertFails)throw IOException("synthetic insert fault")
            check(inDb);check(jobs.single { it.documentId==row.id }.phase=="FILE_PUBLISHED")
            docs.add(copy(row))
        }
        override suspend fun deletePublishedJob(id:String):Int {
            if(deleteFails)return 0
            check(inDb);check(docs.size==1)
            return if(jobs.removeIf { it.id==id && it.phase=="FILE_PUBLISHED" })1 else 0
        }
    }
    private var inDb=false
    private val dao=Dao()
    private var afterMark:()->Unit={}
    private var beforeFinish:()->Unit={}
    private var afterFinish:()->Unit={}
    private var beforeMark:()->Unit={}
    private var beforeSnapshot:(Int)->Unit={}
    private var snapshots=0
    private val phases=mutableListOf<String>()
    private val leakedSnapshots=mutableListOf<DocumentPublicationSnapshot>()
    private fun <T> tx(work:suspend()->T):T {
        check(!inDb);inDb=true
        val jobs=dao.jobs.map { copy(it) }.toMutableList();val docs=dao.docs.map { copy(it) }.toMutableList()
        try { return runBlocking { work() } }
        catch(e:Exception) { dao.jobs=jobs;dao.docs=docs;throw e }
        finally { inDb=false }
    }
    private val db=object:DocumentPublicationDatabase {
        override fun snapshot(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?):DocumentPublicationSnapshot {
            beforeSnapshot(++snapshots)
            return tx { dao.publicationSnapshot(id,phase,expected,c) }.also { leakedSnapshots.add(it) }
        }
        override fun markPublished(c:StorageContext,expected:DocumentJobRow) {
            beforeMark()
            tx { dao.markFilePublished(expected,c) };phases.add("FILE_PUBLISHED");afterMark()
        }
        override fun finish(c:StorageContext,expected:DocumentJobRow) {
            beforeFinish();tx { dao.commitPublishedDocument(expected,42,c) };afterFinish()
        }
    }
    private val ops=object:DocumentPublishFileOps {
        override fun link(source:Path,destination:Path) { check(!inDb);Files.createLink(destination,source) }
        override fun unlink(source:Path) { check(!inDb);Files.delete(source) }
        override fun syncDirectory(directory:Path) { check(!inDb);FileChannel.open(directory,READ).use { it.force(true) } }
    }
    private fun root()=File(temp.root,"pag-documents-v1")
    private fun staging(j:DocumentJobRow)=File(root(),"staging/${j.stagingName}")
    private fun final(j:DocumentJobRow)=File(root(),"files/${j.blobName}")
    private fun fixture(bytes:ByteArray="synthetic 🦋".toByteArray(),format:DocumentFormat=DocumentFormat.TEXT):DocumentJobRow {
        File(root(),"staging").mkdirs();File(root(),"files").mkdir()
        return DocumentFileStreams.withNewKey(c) { key ->
            val j=DocumentJobRow(id(),"a",id(),"IMPORT","IMPORT_CLOSED",id(),id(),"synthetic",format.name,bytes.size.toLong(),key.copyOf())
            staging(j).outputStream().use { output -> DocumentFileStreams.encrypt(c,j.documentId,key,ByteArrayInputStream(bytes),output) }
            dao.jobs.add(j);j
        }
    }
    private fun complete(j:DocumentJobRow,ops:DocumentPublishFileOps=this.ops)=DocumentPublication(db,temp.root,ops).complete(c,j.id)
    private fun invalid(work:()->Unit) { assertThrows(ProductStorageFailure::class.java,work) }
    private fun corrupt(file:File) { RandomAccessFile(file,"rw").use { it.seek(it.length()-1);val b=it.read();it.seek(it.length()-1);it.write(b xor 1) } }

    @Test fun verifiesBothLocationsAndAtomicallyTransfersKeyToOneDocument() {
        val j=fixture();val key=j.fileKey!!.copyOf()
        beforeMark={assertFalse(staging(j).exists());assertTrue(final(j).exists());assertTrue(dao.docs.isEmpty())}
        complete(j)
        assertEquals(listOf("FILE_PUBLISHED"),phases);assertTrue(dao.jobs.isEmpty())
        val d=dao.docs.single();assertEquals(j.documentId,d.id);assertEquals(42L,d.importedAt)
        assertArrayEquals(key,d.fileKey);assertFalse(staging(j).exists());assertTrue(final(j).isFile)
        assertTrue(leakedSnapshots.all { it.job.fileKey!!.all { b -> b==0.toByte() } })
    }
    @Test fun corruptedOrTruncatedStagingNeverMoves() {
        for(truncate in listOf(false,true)) {
            val j=fixture()
            if(truncate)RandomAccessFile(staging(j),"rw").use { it.setLength(it.length()-1) } else corrupt(staging(j))
            invalid { complete(j) };assertTrue(staging(j).exists());assertFalse(final(j).exists())
            assertEquals("IMPORT_CLOSED",dao.jobs.last().phase);assertTrue(dao.docs.isEmpty())
            // isolate second iteration without production cleanup
            dao.jobs.clear();staging(j).delete()
        }
    }
    @Test fun wrongAadOrKeyNeverMoves() {
        for(wrongKey in listOf(false,true)) {
            val j=fixture()
            if(wrongKey)j.fileKey!![0]=(j.fileKey[0].toInt() xor 1).toByte()
            else dao.jobs[0]=DocumentJobRow(j.id,j.actionId,id(),j.operation,j.phase,j.stagingName,j.blobName,j.displayName,j.format,j.plaintextSize,j.fileKey)
            invalid { complete(j) };assertTrue(staging(j).exists());assertFalse(final(j).exists())
            dao.jobs.clear();staging(j).delete()
        }
    }
    @Test fun wrongFormatNeverMovesDespiteValidEncryption() {
        val j=fixture("not pdf".toByteArray(),DocumentFormat.PDF)
        invalid { complete(j) };assertFalse(final(j).exists());assertTrue(dao.docs.isEmpty())
    }
    @Test fun existingTargetIsNotOverwritten() {
        val j=fixture();final(j).writeBytes(byteArrayOf(41))
        invalid { complete(j) };assertArrayEquals(byteArrayOf(41),final(j).readBytes());assertTrue(staging(j).exists())
    }
    @Test fun racingCollisionAtLinkIsNotOverwritten() {
        val j=fixture()
        val collision=object:DocumentPublishFileOps by ops {
            override fun link(source:Path,destination:Path) {
                Files.write(destination,byteArrayOf(42));ops.link(source,destination)
            }
        }
        invalid { complete(j,collision) }
        assertArrayEquals(byteArrayOf(42),final(j).readBytes());assertTrue(staging(j).exists())
        assertEquals("IMPORT_CLOSED",dao.jobs.single().phase)
    }
    @Test fun unknownFileOrSymlinkBlocksWithoutCleanup() {
        val j=fixture();val unknown=File(root(),"files/${id()}")
        unknown.writeBytes(byteArrayOf(1));invalid { complete(j) };assertTrue(unknown.exists());unknown.delete()
        val outside=temp.newFile();Files.createSymbolicLink(unknown.toPath(),outside.toPath())
        invalid { complete(j) };assertTrue(Files.isSymbolicLink(unknown.toPath()));assertTrue(staging(j).exists())
    }
    @Test fun linkFailureLeavesStagingAndClosed() {
        val j=fixture()
        val fail=object:DocumentPublishFileOps by ops { override fun link(source:Path,destination:Path) { throw IOException("synthetic") } }
        invalid { complete(j,fail) };assertTrue(staging(j).exists());assertFalse(final(j).exists());assertEquals("IMPORT_CLOSED",dao.jobs.single().phase)
    }
    @Test fun syncFailureDoesNotCommitPublishedOrRepairDoubleNames() {
        val j=fixture()
        val fail=object:DocumentPublishFileOps by ops { override fun syncDirectory(directory:Path) { throw IOException("sync") } }
        invalid { complete(j,fail) };assertTrue(staging(j).exists());assertTrue(final(j).exists())
        assertTrue(Files.isSameFile(staging(j).toPath(),final(j).toPath()));assertEquals("IMPORT_CLOSED",dao.jobs.single().phase)
        invalid { complete(j) };assertTrue(dao.docs.isEmpty())
    }
    @Test fun markCommitFailureLeavesClosedFinalOnly() {
        val j=fixture();beforeMark={c.commit=false}
        assertThrows(StorageAccessRevoked::class.java) { complete(j) }
        assertFalse(staging(j).exists());assertTrue(final(j).exists());assertEquals("IMPORT_CLOSED",dao.jobs.single().phase)
        assertTrue(dao.docs.isEmpty())
    }
    @Test fun corruptionAfterPublishedIsCaughtBySecondFullVerification() {
        val j=fixture(ByteArray(2*1024*1024){120});afterMark={corrupt(final(j))}
        invalid { complete(j) };assertEquals("FILE_PUBLISHED",dao.jobs.single().phase);assertTrue(dao.docs.isEmpty())
    }
    @Test fun finalInsertOrDeleteFailureRollsBackBoth() {
        val j=fixture();dao.insertFails=true
        invalid { complete(j) };assertEquals("FILE_PUBLISHED",dao.jobs.single().phase);assertTrue(dao.docs.isEmpty())
        dao.insertFails=false;dao.deleteFails=true
        val expected=copy(dao.jobs.single())
        invalid { tx { dao.commitPublishedDocument(expected,42,c) } }
        assertTrue(dao.docs.isEmpty());assertEquals("FILE_PUBLISHED",dao.jobs.single().phase)
    }
    @Test fun revokeBeforeFinalCommitDoesNotCreateDocument() {
        val j=fixture();beforeFinish={c.commit=false}
        assertThrows(StorageAccessRevoked::class.java) { complete(j) }
        assertEquals("FILE_PUBLISHED",dao.jobs.single().phase);assertTrue(dao.docs.isEmpty())
    }
    @Test fun revokeAfterCommitDoesNotClaimRollbackOrPublish() {
        val j=fixture();afterFinish={c.allowed=false}
        assertThrows(StorageAccessRevoked::class.java) { complete(j) }
        assertTrue(dao.jobs.isEmpty());assertEquals(1,dao.docs.size);assertTrue(final(j).exists())
    }
    @Test fun duplicateInvocationFailsWithoutSecondMoveOrRow() {
        val j=fixture();complete(j)
        invalid { complete(j) };assertEquals(1,dao.docs.size);assertTrue(dao.jobs.isEmpty());assertFalse(staging(j).exists())
    }
    @Test fun changedActionAndWrongJobAreDeniedBeforeFileMutation() {
        val j=fixture();dao.status="DELETION_PENDING"
        invalid { complete(j) };assertTrue(staging(j).exists());assertFalse(final(j).exists())
        dao.status="ACTIVE"
        invalid { DocumentPublication(db,temp.root,ops).complete(c,id()) };assertTrue(staging(j).exists())
    }
    @Test fun actionChangesDuringVerificationPreventMove() {
        val j=fixture();beforeSnapshot={ if(it==2)dao.status="DELETION_PENDING" }
        invalid { complete(j) };assertTrue(staging(j).exists());assertFalse(final(j).exists())
        assertEquals("IMPORT_CLOSED",dao.jobs.single().phase)
    }
    @Test fun changedMetadataPreventsPublishedCommitAfterPhysicalMove() {
        val j=fixture();beforeMark={
            dao.jobs[0]=DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,j.phase,j.stagingName,j.blobName,"changed",j.format,j.plaintextSize,j.fileKey)
        }
        invalid { complete(j) };assertFalse(staging(j).exists());assertTrue(final(j).exists())
        assertEquals("IMPORT_CLOSED",dao.jobs.single().phase);assertTrue(dao.docs.isEmpty())
    }
    @Test fun negativeRepositoryTimeRollsBackFinalTransaction() {
        val j=fixture();beforeFinish={}
        afterMark={}
        // Exercise actual DAO final guard after creating the required phase, without a second move.
        tx { dao.markFilePublished(j,c) }
        val expected=copy(dao.jobs.single())
        invalid { tx { dao.commitPublishedDocument(expected,-1,c) } }
        assertTrue(dao.docs.isEmpty());assertEquals("FILE_PUBLISHED",dao.jobs.single().phase)
    }
    @Test fun maintenanceFenceRejectsOldAdmission() {
        val ticket=DocumentNamespaceLane.ticket();DocumentNamespaceLane.drainAndFence {}
        invalid { DocumentNamespaceLane.exclusive(ticket) { error("No mutation") } }
    }
}
