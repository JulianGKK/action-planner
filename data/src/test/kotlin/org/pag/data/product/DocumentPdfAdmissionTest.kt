package org.pag.data.product

import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked

/** Exercises the real shared DAO admission code; no Room/SQLCipher simulation claim. */
class DocumentPdfAdmissionTest {
    private var allowed=true
    private val context=object:StorageContext {
        override val generation=4L
        override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit)=false
    }
    private val asset=DocumentAsset(UUID.randomUUID().toString(),"action","synthetic",DocumentFormat.PDF,100,1)
    private val blob=UUID.randomUUID().toString()
    private val key=ByteArray(32){42}
    private inner class Dao:DocumentDao() {
        var status:String?="ACTIVE"
        var stored=asset
        var present=true
        var deleting=false
        val borrowedKeys=mutableListOf<ByteArray>()
        override suspend fun actionStatus(a:String)=status.takeIf{a==asset.actionId}
        override suspend fun inventoryDocuments()=if(!present)emptyList() else listOf(DocumentRow(
            stored.id,stored.actionId,stored.displayName,stored.format.name,stored.plaintextSize,stored.importedAt,
            blob,key.copyOf().also{borrowedKeys+=it}))
        override suspend fun inventoryJobs()=if(!deleting)emptyList() else listOf(DocumentJobRow(
            UUID.randomUUID().toString(),stored.actionId,stored.id,"DELETE","DELETE_RESERVED",null,blob,null,null,stored.plaintextSize,null))
        override suspend fun count(a:String)=error("Not used")
        override suspend fun list(a:String):List<DocumentMetadataRow> =error("Not used")
        override suspend fun get(a:String,id:String):DocumentMetadataRow? =error("Not used")
        override suspend fun insertJob(job:DocumentJobRow):Unit=error("Not used")
        override suspend fun changeImportPhase(id:String,old:String,next:String,size:Long?):Int=error("Not used")
        override suspend fun insertPublishedDocument(row:DocumentRow):Unit=error("Not used")
        override suspend fun deletePublishedJob(id:String):Int=error("Not used")
        override suspend fun removeDeleteParent(actionId:String,documentId:String):Int=error("Not used")
        override suspend fun markDeletePending(id:String):Int=error("Not used")
        override suspend fun removeDeleteJob(id:String):Int=error("Not used")
        override suspend fun removeInterruptedImport(id:String,phase:String):Int=error("Not used")
    }
    private val dao=Dao()
    private fun snapshot(a:String=asset.actionId,expected:DocumentAsset=asset)=runBlocking{dao.pdfSnapshot(a,expected,context)}
    private fun rejected(reason:DocumentFailure,a:String=asset.actionId,expected:DocumentAsset=asset){
        assertEquals(reason,(snapshot(a,expected) as DocumentResult.Rejected).reason)
        assertTrue(dao.borrowedKeys.all{bytes->bytes.all{it==0.toByte()}})
    }
    @Test fun allowedStatusesReturnOwnedKeyCopy(){
        for(status in listOf("PREPARATION","ACTIVE","COMPLETED")){
            dao.status=status
            val value=(snapshot() as DocumentResult.Value).value
            assertArrayEquals(key,value.key);assertEquals(setOf(blob),value.required)
            assertTrue(dao.borrowedKeys.all{bytes->bytes.all{it==0.toByte()}})
            value.close();assertTrue(value.key.all{it==0.toByte()});assertEquals(42,key[0].toInt())
        }
    }
    @Test fun forbiddenStatuses(){for(status in listOf("DRAFT","DELETION_PENDING")){dao.status=status;rejected(DocumentFailure.NOT_ALLOWED)}}
    @Test fun otherFormatsDenied(){for(format in listOf(DocumentFormat.JPEG,DocumentFormat.PNG,DocumentFormat.TEXT)){
        dao.stored=asset.copy(format=format);rejected(DocumentFailure.NOT_ALLOWED,expected=dao.stored)
    }}
    @Test fun wrongActionAndDocumentIds(){
        rejected(DocumentFailure.NOT_FOUND,a="missing")
        rejected(DocumentFailure.INVALID,expected=asset.copy(actionId="other"))
        rejected(DocumentFailure.NOT_FOUND,expected=asset.copy(id=UUID.randomUUID().toString()))
    }
    @Test fun everyExpectedFieldIsBound(){for(changed in listOf(asset.copy(displayName="changed"),asset.copy(format=DocumentFormat.TEXT),
        asset.copy(plaintextSize=99),asset.copy(importedAt=2)))rejected(DocumentFailure.STALE,expected=changed)}
    @Test fun missingAndDeletingDocumentsDenied(){dao.present=false;rejected(DocumentFailure.NOT_FOUND);dao.present=true;dao.deleting=true;rejected(DocumentFailure.NOT_FOUND)}
    @Test fun revokedSessionCannotReadSnapshot(){allowed=false;assertThrows(StorageAccessRevoked::class.java){snapshot()}}
}
