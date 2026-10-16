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
class DocumentProductStorageTest {
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
        override suspend fun removeInterruptedImport(id:String,phase:String):Int=error("Not used here")
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
            check(inDb);check(docs.isNotEmpty())
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
    private val db=object:DocumentPublicationDatabase, DocumentImportDatabase {
        override fun inventory(c:StorageContext)=tx { dao.importInventory(c) }
        override fun reserve(c:StorageContext,job:DocumentJobRow)=tx { dao.reserveImport(job,c) }
        override fun advance(c:StorageContext,expected:DocumentJobRow,size:Long?)=tx { dao.advanceImport(expected,size,c) }
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
        override fun moveNoReplace(source:Path,destination:Path) { check(!inDb);Files.move(source,destination) }
        override fun unlink(source:Path) { check(!inDb);Files.delete(source) }
        override fun syncDirectory(directory:Path) { check(!inDb);FileChannel.open(directory,READ).use { it.force(true) } }
    }

    private fun stage(format:DocumentFormat=DocumentFormat.TEXT, source:()->InputStream): OperationsResult<DocumentStagingReceipt> =
        DocumentImportStaging(db,temp.root).stage(c,"a","synthetic",format,source)
    @Test fun sourceOpensExactlyOnceAfterWritingThenFinalCommitHasOneRowAndNoJob() {
        var opens=0;var closed=false
        val result=stage {
            assertFalse(inDb);assertEquals("IMPORT_WRITING",dao.jobs.single().phase);opens++
            object:ByteArrayInputStream("synthetic 🦋".toByteArray()) { override fun close(){closed=true;super.close()} }
        } as OperationsResult.Value
        assertEquals(1,opens);assertTrue(closed);assertTrue(dao.docs.isEmpty())
        DocumentPublication(db,temp.root,ops).complete(c,result.value.jobId)
        assertEquals(1,dao.docs.size);assertTrue(dao.jobs.isEmpty())
        val files=File(temp.root,"pag-documents-v1/files").listFiles()!!
        assertEquals(1,files.size);assertFalse(files.single().readBytes().contentEquals("synthetic 🦋".toByteArray()))
    }
    @Test fun rejectedBudgetNeverReadsSourceOrCreatesNamespace() {
        repeat(10) { dao.docs.add(DocumentRow(id(),"a","x","TEXT",DocumentRules.MAX_DOCUMENT_BYTES,1,id(),ByteArray(32))) }
        assertEquals(OperationsResult.Rejected(OperationsFailure.IN_USE),stage { error("No source on rejection") })
        assertFalse(File(temp.root,"pag-documents-v1").exists());assertTrue(dao.jobs.isEmpty())
    }
    @Test fun invalidCandidateFormatStaysWritingAndNeverBecomesClosedOrVisible() {
        val failure=assertThrows(DocumentInputFailure::class.java) { stage(DocumentFormat.PDF) { ByteArrayInputStream("not a PDF".toByteArray()) } }
        assertEquals(DocumentFailure.FORMAT,failure.reason)
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertTrue(dao.docs.isEmpty())
    }
    @Test fun providerAbortIsNeutralAndNeverProducesVisibleDocument() {
        val failure=assertThrows(DocumentInputFailure::class.java) { stage {
            oneAttemptSource(c,DocumentSource { object:InputStream(){override fun read():Int=throw IOException("synthetic provider detail")} })
        } }
        assertEquals(DocumentFailure.SOURCE_UNAVAILABLE,failure.reason);assertNull(failure.message);assertNull(failure.cause)
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase);assertTrue(dao.docs.isEmpty())
    }
    @Test fun providerPermissionLostNeverRetriesAndStaysWriting() {
        var opens=0
        assertThrows(DocumentInputFailure::class.java) { stage { oneAttemptSource(c,DocumentSource { opens++;throw SecurityException("synthetic") }) } }
        assertEquals(1,opens);assertEquals("IMPORT_WRITING",dao.jobs.single().phase)
    }
    private fun sized(size:Long)=object:InputStream(){var remaining=size
        override fun read():Int=if(remaining-- > 0)120 else -1
        override fun read(b:ByteArray,off:Int,len:Int):Int { if(remaining==0L)return -1;val n=minOf(len.toLong(),remaining).toInt();b.fill(120.toByte(),off,off+n);remaining-=n;return n }
    }
    @Test fun exactMaximumStreamsSuccessfullyWithoutWholeFileRam() {
        val result=stage { sized(DocumentRules.MAX_DOCUMENT_BYTES) } as OperationsResult.Value
        assertEquals(DocumentRules.MAX_DOCUMENT_BYTES,result.value.plaintextSize)
        assertEquals("IMPORT_CLOSED",dao.jobs.single().phase)
    }
    @Test fun firstByteOverMaximumIsUserErrorAndNeverClosed() {
        val error=assertThrows(DocumentInputFailure::class.java) { stage { sized(DocumentRules.MAX_DOCUMENT_BYTES+1) } }
        assertEquals(DocumentFailure.TOO_LARGE,error.reason);assertEquals("IMPORT_WRITING",dao.jobs.single().phase)
    }
    @Test fun allFourFormatsPassSourceAndFinalVerificationWithoutChangingBytes() {
        val samples=mapOf(DocumentFormat.PDF to "%PDF-synthetic".toByteArray(),DocumentFormat.PNG to byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a),DocumentFormat.JPEG to byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte()),DocumentFormat.TEXT to "synthetic\n 🦋".toByteArray())
        for((format,bytes) in samples) {
            val receipt=stage(format) { ByteArrayInputStream(bytes) } as OperationsResult.Value
            DocumentPublication(db,temp.root,ops).complete(c,receipt.value.jobId)
        }
        assertEquals(4,dao.docs.size);assertTrue(dao.jobs.isEmpty())
    }
    @Test fun wrongFormatAndInvalidUtf8NeverEscapeAsPersistedClosedImports() {
        for(bytes in listOf(byteArrayOf(0xc0.toByte(),0xaf.toByte()),byteArrayOf(0))) {
            assertThrows(DocumentInputFailure::class.java) { stage { ByteArrayInputStream(bytes) } }
            assertEquals("IMPORT_WRITING",dao.jobs.last().phase)
            // Isolated tests deliberately leave aborted jobs; maintenance owns cleanup.
            dao.jobs.clear();File(temp.root,"pag-documents-v1/staging").listFiles()!!.forEach{it.delete()}
        }
    }
    @Test fun sourceFailureCannotMaskFailingCiphertextSync() {
        val staging=DocumentImportStaging(db,temp.root) { throw IOException("synthetic disk sync") }
        assertThrows(ProductStorageFailure::class.java) { staging.stage(c,"a","x",DocumentFormat.PDF) { ByteArrayInputStream("bad".toByteArray()) } }
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase)
    }
    @Test fun providerReadAndProviderCloseFailureRemainNeutralUserErrors() {
        val failure=assertThrows(DocumentInputFailure::class.java) { stage {
            oneAttemptSource(c,DocumentSource { object:InputStream(){
                override fun read():Int=throw IOException("synthetic source read")
                override fun close(){throw IOException("synthetic source close")}
            } })
        } }
        assertEquals(DocumentFailure.SOURCE_UNAVAILABLE,failure.reason)
        assertTrue(failure.suppressed.isEmpty());assertNull(failure.cause)
        assertEquals("IMPORT_WRITING",dao.jobs.single().phase)
    }

}
