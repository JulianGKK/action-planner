package org.pag.data.product

import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import kotlinx.coroutines.runBlocking

class DocumentFoundationTest {
    private fun id() = UUID.randomUUID().toString()
    private fun document(size: Long = 0, a: String = "a") = DocumentRow(id(),a,"synthetic","TEXT",size,1,id(),ByteArray(32))
    private fun job(phase: String = "IMPORT_RESERVED", size: Long? = null, a: String = "a") =
        DocumentJobRow(id(),a,id(),"IMPORT",phase,id(),id(),"synthetic","PDF",size,ByteArray(32))
    private fun delete(d: DocumentRow, phase: String) = DocumentJobRow(id(),d.actionId,d.id,"DELETE",phase,
        null,d.blobName,null,null,d.plaintextSize,null)
    @Test fun nameTrimAndSupplementaryCodepointLimits() {
        assertEquals("synthetic",DocumentRules.normalizedName(" \nsynthetic\t "))
        assertFalse(DocumentRules.validName(" \n "))
        assertTrue(DocumentRules.validName("🦋".repeat(160)))
        assertFalse(DocumentRules.validName("🦋".repeat(161)))
        assertFalse(DocumentRules.valid(DocumentAsset("d","a"," x ",DocumentFormat.TEXT,0,0)))
    }
    @Test fun formatsFieldsAndLifecycleAreExactlyTheContract() {
        assertEquals(listOf("PDF","JPEG","PNG","TEXT"),DocumentFormat.entries.map { it.name })
        assertEquals(setOf("id","actionId","displayName","format","plaintextSize","importedAt"),DocumentAsset::class.java.declaredFields.map { it.name }.toSet())
        assertEquals(setOf("count"),DocumentSummary::class.java.declaredFields.map { it.name }.toSet())
        ActionStatus.entries.forEach { s ->
            val expected = s in setOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)
            assertEquals(expected,DocumentRules.mayRead(s)); assertEquals(expected,DocumentRules.mayWrite(s)); assertEquals(expected,DocumentRules.mayDelete(s))
        }
    }
    @Test fun sizeAndDeterministicOrder() {
        assertTrue(DocumentRules.validSize(0)); assertTrue(DocumentRules.validSize(26_214_400))
        assertFalse(DocumentRules.validSize(-1)); assertFalse(DocumentRules.validSize(26_214_401))
        val x = DocumentAsset("z","a","A",DocumentFormat.TEXT,0,1)
        val y = x.copy(id="a",displayName="Z")
        assertEquals(listOf(y,x),listOf(x,y).sortedWith(DocumentRules.order))
    }
    @Test fun allPhasesAndExactKeyShape() {
        assertEquals(6,DocumentPhase.entries.size)
        for (p in listOf("IMPORT_RESERVED","IMPORT_WRITING")) { assertTrue(DocumentStorageRules.valid(job(p))); assertFalse(DocumentStorageRules.valid(job(p,0))) }
        for (p in listOf("IMPORT_CLOSED","FILE_PUBLISHED")) { assertTrue(DocumentStorageRules.valid(job(p,0))); assertFalse(DocumentStorageRules.valid(job(p))) }
        val j=job()
        for (n in listOf(0,31,33)) assertFalse(DocumentStorageRules.valid(DocumentJobRow(j.id,j.actionId,j.documentId,j.operation,j.phase,j.stagingName,j.blobName,j.displayName,j.format,null,ByteArray(n))))
        assertFalse(DocumentStorageRules.valid(job("UNKNOWN")))
        assertFalse(DocumentStorageRules.opaque("../foreign"))
    }
    @Test fun unknownImportSizeReservesTwentyFiveMiBAndOneSlot() {
        assertEquals(DocumentCapacity(1,26_214_400),DocumentStorageRules.capacity(emptyList(),listOf(job())))
        assertEquals(DocumentCapacity(1,7),DocumentStorageRules.capacity(emptyList(),listOf(job("IMPORT_CLOSED",7))))
        val jobs=List(10){job()}
        assertFalse(DocumentStorageRules.capacity(emptyList(),jobs).admitsImport())
        assertTrue(DocumentStorageRules.capacity(emptyList(),jobs.dropLast(1)).admitsImport())
    }
    @Test fun countAndAggregateBoundariesIncludeHiddenAndPendingDeletes() {
        assertFalse(DocumentStorageRules.capacity(List(100){document()},emptyList()).admitsImport())
        assertTrue(DocumentStorageRules.capacity(List(99){document()},emptyList()).admitsImport())
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.capacity(List(101){document()},emptyList())}
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.capacity(List(11){document(DocumentRules.MAX_DOCUMENT_BYTES)},emptyList())}
        val d=document(5)
        assertEquals(DocumentCapacity(1,5),DocumentStorageRules.capacity(listOf(d),listOf(delete(d,"DELETE_RESERVED"))))
        assertEquals(DocumentCapacity(1,5),DocumentStorageRules.capacity(emptyList(),listOf(delete(d,"DELETE_FILE_PENDING"))))
    }
    @Test fun corruptOrDuplicateBindingsNeverCreateCapacity() {
        val d=document();val j=job()
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.capacity(listOf(d,d),emptyList())}
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.capacity(emptyList(),listOf(j,j))}
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.capacity(emptyList(),listOf(delete(d,"DELETE_RESERVED")))}
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.capacity(listOf(d),listOf(delete(d,"DELETE_FILE_PENDING")))}
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.capacity(listOf(document(Long.MAX_VALUE)),emptyList())}
    }
    @Test fun foreignActionBindingsFailClosed() {
        val d=document()
        val j=DocumentJobRow(id(),"b",d.id,"IMPORT","IMPORT_RESERVED",id(),id(),"synthetic","TEXT",null,ByteArray(32))
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.validateBindings(listOf(d),listOf(j))}
        val collision=DocumentJobRow(id(),"b",id(),"IMPORT","IMPORT_RESERVED",id(),d.blobName,"synthetic","TEXT",null,ByteArray(32))
        assertThrows(ProductStorageFailure::class.java){DocumentStorageRules.validateBindings(listOf(d),listOf(collision))}
    }
    private class FakeDao : DocumentDao() {
        var status="ACTIVE"; var beforeStatus:()->Unit={}; var inserted:ByteArray?=null
        var documents=emptyList<DocumentRow>(); var jobs=emptyList<DocumentJobRow>(); var failJobs=false
        override suspend fun changeImportPhase(id:String,old:String,next:String,size:Long?) = error("Not used in foundation tests")
        override suspend fun insertPublishedDocument(row: DocumentRow): Unit = error("Not used here")
        override suspend fun deletePublishedJob(id: String): Int = error("Not used here")
        override suspend fun count(a:String)=0
        override suspend fun list(a:String)=emptyList<DocumentMetadataRow>()
        override suspend fun get(a:String,id:String):DocumentMetadataRow?=null
        override suspend fun inventoryDocuments()=documents
        override suspend fun inventoryJobs():List<DocumentJobRow> { if(failJobs)throw ProductStorageFailure();return jobs }
        override suspend fun actionStatus(a:String):String { beforeStatus();return status }
        override suspend fun insertJob(job:DocumentJobRow) { inserted=job.fileKey!!.copyOf() }
    }
    private fun context()=object:StorageContext {
        override val generation=1L;override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=true
        override fun publish(result:()->Unit):Boolean { result();return true }
    }
    @Test fun reservationFreezesKeyBeforeSuspensionAndWipesReadCopiesOnFailure()=runBlocking {
        val j=job();j.fileKey!!.fill(7)
        val dao=FakeDao();dao.beforeStatus={j.fileKey.fill(0)}
        assertTrue(dao.reserveImport(j,context()) is OperationsResult.Value)
        assertArrayEquals(ByteArray(32){7},dao.inserted)
        val d=document();d.fileKey.fill(9)
        val failing=FakeDao();failing.documents=listOf(d);failing.failJobs=true
        try { failing.reserveImport(job(),context());fail("Expected read failure") } catch(_:ProductStorageFailure) {}
        assertArrayEquals(ByteArray(32),d.fileKey)
    }
    @Test fun reservationRejectsDisallowedActionAndFullBudgetWithoutInsert()=runBlocking {
        val dao=FakeDao();dao.status="DELETION_PENDING"
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),dao.reserveImport(job(),context()))
        assertNull(dao.inserted)
        dao.status="COMPLETED";dao.jobs=List(10){job()}
        assertEquals(OperationsResult.Rejected(OperationsFailure.IN_USE),dao.reserveImport(job(),context()))
        assertNull(dao.inserted)
    }
    @Test fun generatedProjectionsAreKeyFreeAndReserveIsTransactional() {
        val file=listOf(File("build/generated/ksp/debug/kotlin/org/pag/data/product/DocumentDao_Impl.kt"),
            File("data/build/generated/ksp/debug/kotlin/org/pag/data/product/DocumentDao_Impl.kt")).first { it.isFile }
        val code=file.readText().replace("`get`", "get")
        for (name in listOf("count","list","get")) {
            val sql=code.substringAfter("override suspend fun $name(").substringBefore("return performSuspending")
            assertFalse(sql.contains("fileKey"));assertFalse(sql.contains("blobName"));assertFalse(sql.contains("SELECT *"))
            assertTrue(sql.contains("NOT EXISTS"));assertTrue(sql.contains("operation='DELETE'"))
        }
        assertTrue(code.contains("performInTransactionSuspending"))
        assertFalse(code.contains("INSERT INTO `documents`"))
        assertTrue(code.contains("INSERT OR ABORT INTO `document_jobs`"))
    }
}
