package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import java.io.*
import java.nio.file.Files
import java.util.UUID

class DocumentImportStagingTest {
    @get:Rule val temp = TemporaryFolder()
    private class Access : StorageContext {
        var allowed = true
        var commitAllowed = true
        override val generation = 4L
        override val scope = AccessScope.READY
        override fun check(point: AccessPoint) = allowed && (point != AccessPoint.COMMIT || commitAllowed)
        override fun publish(result: () -> Unit): Boolean = error("No publication allowed")
    }
    private val c = Access()
    private fun id() = UUID.randomUUID().toString()
    private fun copy(j: DocumentJobRow, phase: String = j.phase, size: Long? = j.plaintextSize) = DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,phase,j.stagingName,j.blobName,j.displayName,j.format,size,j.fileKey?.copyOf())
    private inner class Dao : DocumentDao() {
        var status = "ACTIVE"
        var docs = mutableListOf<DocumentRow>()
        var jobs = mutableListOf<DocumentJobRow>()
        override suspend fun insertPublishedDocument(row: DocumentRow): Unit = error("Not used here")
        override suspend fun deletePublishedJob(id: String): Int = error("Not used here")
        override suspend fun removeDeleteParent(actionId:String,documentId:String):Int = error("Not used here")
        override suspend fun markDeletePending(id:String):Int = error("Not used here")
        override suspend fun removeDeleteJob(id:String):Int = error("Not used here")
        override suspend fun count(a: String) = docs.size
        override suspend fun list(a: String) = emptyList<DocumentMetadataRow>()
        override suspend fun get(a: String,id: String): DocumentMetadataRow? = null
        override suspend fun actionStatus(a: String) = status
        override suspend fun inventoryDocuments() = docs.map { DocumentRow(it.id,it.actionId,it.displayName,it.format,it.plaintextSize,it.importedAt,it.blobName,it.fileKey.copyOf()) }
        override suspend fun inventoryJobs() = jobs.map { copy(it) }
        override suspend fun insertJob(job: DocumentJobRow) { jobs.add(copy(job)) }
        override suspend fun changeImportPhase(id: String,old: String,next: String,size: Long?): Int {
            val index = jobs.indexOfFirst { it.id == id && it.phase == old }
            if(index < 0) return 0
            jobs[index] = copy(jobs[index],next,size)
            return 1
        }
    }
    /** In-memory transaction rollback simulation; not a Room/SQLCipher runtime claim. */
    private inner class Database : DocumentImportDatabase {
        val dao = Dao()
        var inDb = false
        val phases = mutableListOf<String>()
        var beforeAdvance: (DocumentJobRow) -> Unit = {}
        private fun <T> tx(work: suspend () -> T): T {
            check(!inDb); inDb = true
            val old = dao.jobs.map { copy(it) }.toMutableList()
            try { return runBlocking { work() } }
            catch(e: Exception) { dao.jobs = old; throw e }
            finally { inDb = false }
        }
        override fun inventory(c: StorageContext) = tx { dao.importInventory(c) }
        override fun reserve(c: StorageContext,job: DocumentJobRow): OperationsResult<Unit> = tx {
            dao.reserveImport(job,c).also { if(it is OperationsResult.Value) phases.add("IMPORT_RESERVED") }
        }
        override fun advance(c: StorageContext,expected: DocumentJobRow,size: Long?) = tx {
            beforeAdvance(expected)
            dao.advanceImport(expected,size,c)
            phases.add(dao.jobs.single { it.id == expected.id }.phase)
            Unit
        }
    }
    private fun root() = File(temp.root,"pag-documents-v1")
    private fun source(db: Database,bytes: ByteArray): () -> InputStream = {
        assertFalse(db.inDb)
        assertEquals("IMPORT_WRITING",db.dao.jobs.last().phase)
        ByteArrayInputStream(bytes)
    }
    private fun invalid(work: () -> Unit) { assertThrows(ProductStorageFailure::class.java,work) }

    @Test fun reservedThenWritingThenSyncedClosedWithoutDocumentOrPublication() {
        val db=Database();var synced=false
        db.beforeAdvance = { if(it.phase == "IMPORT_RESERVED") assertFalse(root().exists()) }
        val result=DocumentImportStaging(db,temp.root) { channel ->
            assertTrue(channel.isOpen);assertFalse(db.inDb);channel.force(true);synced=true
        }.stage(c,"a","synthetic",DocumentFormat.TEXT,source(db,"synthetic 🦋".toByteArray()))
        assertTrue(result is OperationsResult.Value)
        assertTrue(synced)
        assertEquals(listOf("IMPORT_RESERVED","IMPORT_WRITING","IMPORT_CLOSED"),db.phases)
        val job=db.dao.jobs.single()
        assertEquals("synthetic 🦋".toByteArray().size.toLong(),job.plaintextSize)
        assertEquals(DocumentCapacity(1,job.plaintextSize!!),DocumentStorageRules.capacity(emptyList(),db.dao.jobs))
        assertTrue(db.dao.docs.isEmpty())
        val file=File(root(),"staging/${job.stagingName}")
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("synthetic"))
        assertEquals(0,File(root(),"files").list()!!.size)
        DocumentFileInventory(temp.root).verifyExisting(c,DocumentFileArea.STAGING,job.stagingName!!,setOf(job.stagingName),emptySet(),job.documentId,job.fileKey!!,job.plaintextSize!!,DocumentFormat.TEXT)
    }
    @Test fun rejectedReservationCreatesNeitherFileNorSource() {
        val db=Database();db.dao.status="COMPLETED" // lifecycle allows completed; commit denial must still roll back
        c.commitAllowed=false
        assertThrows(StorageAccessRevoked::class.java) {
            DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) { error("No source") }
        }
        assertTrue(db.dao.jobs.isEmpty());assertFalse(root().exists())
    }
    @Test fun disallowedActionDoesNotCreateAnything() {
        for(status in listOf("DRAFT","DELETION_PENDING")) {
            val db=Database();db.dao.status=status
            assertTrue(DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) { error("No source") } is OperationsResult.Rejected)
            assertTrue(db.dao.jobs.isEmpty());assertFalse(root().exists())
        }
    }
    @Test fun capacityRejectionUsesExistingDaoReservation() {
        val dao=Dao()
        repeat(10) { dao.jobs.add(DocumentJobRow(id(),"a",id(),"IMPORT","IMPORT_RESERVED",id(),id(),"x","TEXT",null,ByteArray(32))) }
        val j=DocumentJobRow(id(),"a",id(),"IMPORT","IMPORT_RESERVED",id(),id(),"x","TEXT",null,ByteArray(32))
        val result=runBlocking { dao.reserveImport(j,c) }
        assertEquals(OperationsResult.Rejected(OperationsFailure.IN_USE),result)
        assertEquals(10,dao.jobs.size);assertFalse(root().exists())
    }
    @Test fun sourceAbortAndOpenFailureLeaveWriting() {
        for(failOnRead in listOf(false,true)) {
            val base=temp.newFolder();val db=Database()
            invalid { DocumentImportStaging(db,base).stage(c,"a","x",DocumentFormat.TEXT) {
                if(!failOnRead)throw IOException("synthetic")
                object: InputStream() { override fun read(): Int = throw IOException("synthetic") }
            } }
            assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
            assertNull(db.dao.jobs.single().plaintextSize)
            assertEquals(1,File(base,"pag-documents-v1/staging").list()!!.size)
        }
    }
    @Test fun syncFailureLeavesWritingWithMaximumReservation() {
        val db=Database()
        invalid { DocumentImportStaging(db,temp.root) { throw IOException("sync") }.stage(c,"a","x",DocumentFormat.TEXT,source(db,byteArrayOf(1))) }
        assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
        assertEquals(DocumentRules.MAX_DOCUMENT_BYTES,DocumentStorageRules.capacity(emptyList(),db.dao.jobs).plaintextBytes)
    }
    @Test fun sourceCloseFailureLeavesWriting() {
        val db=Database()
        invalid { DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) {
            object:ByteArrayInputStream(byteArrayOf(1)) { override fun close() { throw IOException("close") } }
        } }
        assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
    }
    @Test fun revokeBeforeClosedCommitRollsBackPhaseAndSize() {
        val db=Database();db.beforeAdvance = { if(it.phase == "IMPORT_WRITING") c.commitAllowed=false }
        assertThrows(StorageAccessRevoked::class.java) { DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT,source(db,byteArrayOf(1))) }
        assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase);assertNull(db.dao.jobs.single().plaintextSize)
    }
    @Test fun changedBindingAndActionBlockClosed() {
        for(changeKey in listOf(false,true)) {
            val base=temp.newFolder();val db=Database()
            db.beforeAdvance = { if(it.phase == "IMPORT_WRITING") {
                if(changeKey) db.dao.jobs.single().fileKey!![0] = (it.fileKey!![0].toInt() xor 1).toByte() else db.dao.status="DELETION_PENDING"
            } }
            invalid { DocumentImportStaging(db,base).stage(c,"a","x",DocumentFormat.TEXT,source(db,byteArrayOf(1))) }
            assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
        }
    }
    @Test fun existingTargetCannotBeOverwrittenOrSourceOpened() {
        val db=Database()
        db.beforeAdvance = { expected -> if(expected.phase == "IMPORT_RESERVED") {
            // Simulates a foreign concurrent writer; coordinator must neither overwrite nor remove.
            File(root(),"staging").mkdirs();File(root(),"files").mkdir()
            File(root(),"staging/${expected.stagingName}").writeBytes(byteArrayOf(42))
        } }
        invalid { DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) { error("No source") } }
        assertEquals(42,File(root(),"staging").listFiles()!!.single().readBytes().single().toInt())
        assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
    }
    @Test fun unknownAndSymlinkRootsBlockBeforeSourceOrFileMutation() {
        for(symlink in listOf(false,true)) {
            val base=temp.newFolder();val r=File(base,"pag-documents-v1")
            if(symlink)Files.createSymbolicLink(r.toPath(),temp.newFolder().toPath())
            else { r.mkdir();File(r,"unknown").writeBytes(byteArrayOf(1)) }
            val db=Database()
            invalid { DocumentImportStaging(db,base).stage(c,"a","x",DocumentFormat.TEXT) { error("No source") } }
            assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
        }
    }
    @Test fun onlyKnownEmptyPartialInitialRootCanBeCompleted() {
        File(root(),"staging").mkdirs()
        val db=Database()
        DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT,source(db,byteArrayOf()))
        assertTrue(File(root(),"files").isDirectory);assertEquals("IMPORT_CLOSED",db.dao.jobs.single().phase)
    }
    @Test fun missingRootWithExistingClosedReferenceBlocks() {
        val db=Database();db.dao.jobs.add(DocumentJobRow(id(),"a",id(),"IMPORT","IMPORT_CLOSED",id(),id(),"x","TEXT",0,ByteArray(32)))
        invalid { DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) { error("No source") } }
        assertEquals(2,db.dao.jobs.size);assertFalse(root().exists())
    }
    @Test fun fenceInvalidatesQueuedOldWorkerBeforeAnyIo() {
        val ticket=DocumentNamespaceLane.ticket()
        DocumentNamespaceLane.drainAndFence {}
        invalid { DocumentNamespaceLane.exclusive(ticket) { error("Old work admitted") } }
    }
    @Test fun secondImportPreservesFirstClosedStaging() {
        val db=Database();val coordinator=DocumentImportStaging(db,temp.root)
        repeat(2) { coordinator.stage(c,"a","x",DocumentFormat.TEXT,source(db,byteArrayOf(120))) }
        assertEquals(2,db.dao.jobs.size)
        assertTrue(db.dao.jobs.all { it.phase == "IMPORT_CLOSED" })
        assertEquals(2,File(root(),"staging").list()!!.size)
        assertTrue(db.dao.docs.isEmpty())
    }
    @Test fun encryptionRejectsNonProgressingSourceWithoutClosed() {
        val db=Database()
        invalid { DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) {
            object:InputStream() {
                override fun read():Int=0
                override fun read(b:ByteArray,off:Int,len:Int):Int=0
            }
        } }
        assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
    }
    @Test fun fullCountBudgetLeavesExistingFilesAndSourceUntouched() {
        val db=Database()
        File(root(),"staging").mkdirs();File(root(),"files").mkdir()
        repeat(100) {
            val name=id()
            db.dao.docs.add(DocumentRow(id(),"a","x","TEXT",0,1,name,ByteArray(32)))
            File(root(),"files/$name").createNewFile()
        }
        val result=DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) { error("No source") }
        assertEquals(OperationsResult.Rejected(OperationsFailure.IN_USE),result)
        assertTrue(db.dao.jobs.isEmpty());assertEquals(100,File(root(),"files").list()!!.size)
        assertTrue(File(root(),"staging").list()!!.isEmpty())
    }
    @Test fun revokeDuringSourceReadCannotCommitClosed() {
        val db=Database();var closed=false
        assertThrows(StorageAccessRevoked::class.java) {
            DocumentImportStaging(db,temp.root).stage(c,"a","x",DocumentFormat.TEXT) {
                object:ByteArrayInputStream(byteArrayOf(1)) {
                    override fun read(b:ByteArray,off:Int,len:Int):Int {
                        val n=super.read(b,off,len);c.allowed=false;return n
                    }
                    override fun close() { closed=true;super.close() }
                }
            }
        }
        assertTrue(closed);assertEquals("IMPORT_WRITING",db.dao.jobs.single().phase)
    }
    @Test fun maintenanceLaneWaitsForOldWorkAndRejectsItsQueuedTicket() {
        val started=java.util.concurrent.CountDownLatch(1)
        val finish=java.util.concurrent.CountDownLatch(1)
        val fenced=java.util.concurrent.CountDownLatch(1)
        val ticket=DocumentNamespaceLane.ticket()
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val old=executor.submit { DocumentNamespaceLane.exclusive(ticket) {
                started.countDown();check(finish.await(5,java.util.concurrent.TimeUnit.SECONDS))
            } }
            assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS))
            val maintenance=executor.submit { DocumentNamespaceLane.drainAndFence { fenced.countDown() } }
            assertFalse(fenced.await(50,java.util.concurrent.TimeUnit.MILLISECONDS))
            finish.countDown();old.get(5,java.util.concurrent.TimeUnit.SECONDS)
            maintenance.get(5,java.util.concurrent.TimeUnit.SECONDS)
            invalid { DocumentNamespaceLane.exclusive(ticket) { error("Old worker") } }
        } finally { finish.countDown();executor.shutdownNow() }
    }
    @Test fun exactMaximumAndFirstExtraByte() {
        for(extra in listOf(0,1)) {
            val base=temp.newFolder();val db=Database();var read=0L
            val total=DocumentRules.MAX_DOCUMENT_BYTES+extra
            val work = { DocumentImportStaging(db,base).stage(c,"a","x",DocumentFormat.TEXT) {
                object:InputStream() {
                    override fun read():Int=error("Bulk only")
                    override fun read(b:ByteArray,off:Int,len:Int):Int {
                        if(read==total)return -1
                        val n=minOf(len.toLong(),total-read).toInt();b.fill(120,off,off+n);read+=n;return n
                    }
                }
            };Unit }
            if(extra==0)work() else invalid(work)
            assertEquals(total,read)
            assertEquals(if(extra==0)"IMPORT_CLOSED" else "IMPORT_WRITING",db.dao.jobs.single().phase)
        }
    }
}
