package org.pag.data.product

import java.io.*
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked

class DocumentPdfReadTest {
    @get:Rule val temp=TemporaryFolder()
    private var authorized=true
    private val c=object:StorageContext {
        override val generation=1L;override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=authorized
        override fun publish(result:()->Unit)=error("No UI")
    }
    private val id=UUID.randomUUID().toString();private val blob=UUID.randomUUID().toString()
    private val key=ByteArray(32){it.toByte()}
    private val bytes="%PDF-1.4\n".toByteArray()+ByteArray(2_100_000){42}
    private fun ciphertext()=ByteArrayOutputStream().also {DocumentFileStreams.encrypt(c,id,key,bytes.inputStream(),it)}.toByteArray()
    private fun fixture():Pair<File,DocumentReadSnapshot> {
        val root=temp.newFolder().canonicalFile
        File(root,"pag-documents-v1/staging").mkdirs();File(root,"pag-documents-v1/files").mkdir()
        File(root,"pag-documents-v1/files/$blob").writeBytes(ciphertext())
        return root to DocumentReadSnapshot(DocumentAsset(id,"a","synthetic",DocumentFormat.PDF,bytes.size.toLong(),1),blob,key.copyOf(),setOf(blob),emptySet(),emptySet())
    }
    private class Sink:DocumentPdfInput {
        var used=0;var rendered=false;var closed=false;var duringRender:()->Unit={};var onWrite:()->Unit={}
        override fun write(bytes:ByteArray,offset:Int,count:Int){assertTrue(count<=32768);used+=count;onWrite()}
        override fun render(page:Int,check:()->Unit):DocumentPdfPage {check();rendered=true;duringRender();check();return DocumentPdfPage(page,2,object:DocumentImage{override fun close(){}})}
        override fun close(){closed=true}
    }
    @Test fun fullAuthenticationAndFinalInventoryBeforeRenderAndKeyWiped() {
        val(root,s)=fixture();val sink=Sink();val file=File(root,"pag-documents-v1/files/$blob");val before=file.readBytes()
        sink.duringRender={assertEquals(bytes.size,sink.used);assertTrue(s.key.all{it==0.toByte()})}
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){DocumentPdfRead(root).render(c,s,0,DocumentPdfRenderer{sink}).close()}
        assertTrue(sink.rendered && sink.closed);assertArrayEquals(before,file.readBytes())
    }
    @Test fun lateTagAndTruncationNeverReachRenderer() {
        for(truncate in listOf(false,true)){
            val(root,s)=fixture();val file=File(root,"pag-documents-v1/files/$blob");val data=file.readBytes()
            file.writeBytes(if(truncate)data.copyOf(data.size-1) else data.also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})
            val sink=Sink()
            DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){assertThrows(ProductStorageFailure::class.java){DocumentPdfRead(root).render(c,s,0,DocumentPdfRenderer{sink})}}
            assertFalse(sink.rendered);assertTrue(sink.closed);s.close()
        }
    }
    @Test fun wrongAadKeyAndLengthNeverComplete() {
        for(mode in 0..2){val sink=Sink();assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.readPdf(c,
            if(mode==0)UUID.randomUUID().toString() else id,if(mode==1)ByteArray(32) else key,ciphertext().inputStream(),bytes.size.toLong()-(if(mode==2)1 else 0),sink)};assertFalse(sink.rendered)}
    }
    @Test fun unknownFileAndSymlinkNeverStartParser() {
        val(root,s)=fixture();val dir=File(root,"pag-documents-v1/files");val extra=File(dir,UUID.randomUUID().toString()).apply{writeText("synthetic")}
        fun denied(){DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){assertThrows(ProductStorageFailure::class.java){DocumentPdfRead(root).render(c,s,0,DocumentPdfRenderer{error("No renderer")})}}}
        denied();assertTrue(extra.exists());extra.delete();val file=File(dir,blob);file.delete();val outside=temp.newFile();Files.createSymbolicLink(file.toPath(),outside.toPath());denied();assertTrue(outside.exists());s.close()
    }
    @Test fun revokeDuringReadClosesStreamAndNeverRenders() {
        var closed=false;val sink=Sink();val input=object:ByteArrayInputStream(ciphertext()){
            override fun read(b:ByteArray,o:Int,n:Int)=super.read(b,o,n).also{authorized=false}
            override fun close(){closed=true}
        }
        assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.readPdf(c,id,key,input,bytes.size.toLong(),sink)}
        assertTrue(closed);assertFalse(sink.rendered)
    }
    @Test fun revokeAfterVerificationPreventsPagePublicationAndClosesSink(){
        val(root,s)=fixture();val sink=Sink();sink.duringRender={authorized=false}
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){assertThrows(StorageAccessRevoked::class.java){DocumentPdfRead(root).render(c,s,0,DocumentPdfRenderer{sink})}}
        assertTrue(sink.closed)
    }
    @Test fun finalInventoryChangeDuringDecryptionPreventsParser(){
        val(root,s)=fixture();val sink=Sink();val foreign=File(root,"pag-documents-v1/files/"+UUID.randomUUID())
        sink.onWrite={if(!foreign.exists())foreign.writeBytes(byteArrayOf(1))}
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){assertThrows(ProductStorageFailure::class.java){DocumentPdfRead(root).render(c,s,0,DocumentPdfRenderer{sink})}}
        assertFalse(sink.rendered);assertTrue(sink.closed);assertTrue(foreign.exists());s.close()
    }
    @Test fun unsupportedParserIsNotCiphertextCorruptionAndClosesInput(){
        val(root,s)=fixture();var closed=false
        val renderer=DocumentPdfRenderer{object:DocumentPdfInput{
            override fun write(bytes:ByteArray,offset:Int,count:Int){}
            override fun render(page:Int,check:()->Unit):DocumentPdfPage=throw DocumentPdfUnsupported()
            override fun close(){closed=true}
        }}
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){assertThrows(DocumentPdfUnsupported::class.java){DocumentPdfRead(root).render(c,s,0,renderer)}}
        assertTrue(closed);assertTrue(s.key.all{it==0.toByte()})
    }
    @Test fun nonPdfAndInvalidIndexNeverCreateSink(){
        val(root,s)=fixture()
        for(format in DocumentFormat.entries.filter{it!=DocumentFormat.PDF}) {
            val wrong=DocumentReadSnapshot(s.asset.copy(format=format),blob,key.copyOf(),setOf(blob),emptySet(),emptySet())
            wrong.use{DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){assertThrows(DocumentPdfUnsupported::class.java){DocumentPdfRead(root).render(c,wrong,0,DocumentPdfRenderer{error("No sink")})}}}
        }
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){assertThrows(DocumentPdfUnsupported::class.java){DocumentPdfRead(root).render(c,s,-1,DocumentPdfRenderer{error("No sink")})}};s.close()
    }

    @Test fun memoryFailureStillAuthenticatesEofAndChecksFinalInventory(){
        for(mode in 0..2) {
            val(root,s)=fixture();val file=File(root,"pag-documents-v1/files/$blob")
            if(mode==1){val damaged=file.readBytes();damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte();file.writeBytes(damaged)}
            var closed=false
            val renderer=DocumentPdfRenderer{object:DocumentPdfInput {
                override fun write(bytes:ByteArray,offset:Int,count:Int){
                    if(mode==2)File(root,"pag-documents-v1/files/"+UUID.randomUUID()).writeBytes(byteArrayOf(1))
                    throw DocumentPdfUnsupported(DocumentFailure.PDF_MEMORY_UNAVAILABLE)
                }
                override fun render(page:Int,check:()->Unit):DocumentPdfPage=error("No parser after memory failure")
                override fun close(){closed=true}
            }}
            s.use{DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){
                if(mode==0)assertEquals(DocumentFailure.PDF_MEMORY_UNAVAILABLE,
                    assertThrows(DocumentPdfUnsupported::class.java){DocumentPdfRead(root).render(c,s,0,renderer)}.reason)
                else assertThrows(ProductStorageFailure::class.java){DocumentPdfRead(root).render(c,s,0,renderer)}
            }}
            assertTrue(closed);assertTrue(s.key.all{it==0.toByte()})
        }
    }

    @Test fun missingBlobNeverCreatesAnonymousInput(){
        val(root,s)=fixture();File(root,"pag-documents-v1/files/$blob").delete()
        s.use{DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){
            assertThrows(ProductStorageFailure::class.java){DocumentPdfRead(root).render(c,s,0,DocumentPdfRenderer{error("No sink")})}
        }}
    }

}
