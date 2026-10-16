package org.pag.data.product

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import java.io.*
import java.util.UUID
import java.nio.file.Files

class DocumentImageReadTest {
    @get:Rule val temporary=TemporaryFolder()
    private var allowed=true
    private val c=object:StorageContext {
        override val generation=1L;override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean=error("Not a publication")
    }
    private val id=UUID.randomUUID().toString();private val blob=UUID.randomUUID().toString()
    private val key=ByteArray(32){it.toByte()}
    private val bytes=byteArrayOf(-1,-40,-1)+ByteArray(2_100_000){42}
    private fun encrypted():ByteArray=ByteArrayOutputStream().also {
        DocumentFileStreams.encrypt(c,id,key,ByteArrayInputStream(bytes),it)
    }.toByteArray()
    @Test fun fullAuthenticatedEofBeforeDecoderAndWipeAfterwards() {
        val base=temporary.newFolder().canonicalFile
        File(base,"pag-documents-v1/staging").mkdirs();File(base,"pag-documents-v1/files").mkdir()
        val file=File(base,"pag-documents-v1/files/$blob");val ciphertext=encrypted();file.writeBytes(ciphertext)
        val s=DocumentReadSnapshot(DocumentAsset(id,"a","synthetic",DocumentFormat.JPEG,bytes.size.toLong(),1),blob,key.copyOf(),setOf(blob),emptySet(),emptySet())
        lateinit var borrowed:ByteArray
        val result=DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            DocumentImageRead(base).decode(c,s,DocumentImageDecoder { data,_,check ->
                check();borrowed=data;assertArrayEquals(bytes,data);assertTrue(s.key.all{it==0.toByte()})
                object:DocumentImage{override fun close(){}}
            })
        }
        result.close();assertTrue(borrowed.all{it==0.toByte()});assertArrayEquals(ciphertext,file.readBytes())
        assertEquals(setOf(blob),File(base,"pag-documents-v1/files").list()!!.toSet())
    }
    @Test fun lateCorruptionTruncationWrongSizeAndWrongBindingNeverReturnPlaintext() {
        val cipher=encrypted()
        val cases=listOf(cipher.copyOf(cipher.size-1),cipher.copyOf().also {it[it.lastIndex-3]=(it[it.lastIndex-3].toInt() xor 1).toByte()})
        for(data in cases) assertThrows(ProductStorageFailure::class.java) {
            DocumentFileStreams.readImage(c,id,key,ByteArrayInputStream(data),bytes.size.toLong(),DocumentFormat.JPEG)
        }
        assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.readImage(c,id,key,ByteArrayInputStream(cipher),bytes.size.toLong()-1,DocumentFormat.JPEG)}
        assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.readImage(c,UUID.randomUUID().toString(),key,ByteArrayInputStream(cipher),bytes.size.toLong(),DocumentFormat.JPEG)}
        assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.readImage(c,id,ByteArray(32),ByteArrayInputStream(cipher),bytes.size.toLong(),DocumentFormat.JPEG)}
    }
    @Test fun revokedReadClosesInputAndNeverReturnsBytes() {
        val cipher=encrypted();var closed=false
        val input=object:ByteArrayInputStream(cipher) {
            override fun read(b:ByteArray,o:Int,n:Int):Int=super.read(b,o,n).also {allowed=false}
            override fun close(){closed=true}
        }
        assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.readImage(c,id,key,input,bytes.size.toLong(),DocumentFormat.JPEG)}
        assertTrue(closed)
    }
    @Test fun pdfAndTextAreRejectedWithoutReads() {
        for(format in listOf(DocumentFormat.PDF,DocumentFormat.TEXT)) {
            var read=false;var closed=false
            val input=object:InputStream(){override fun read():Int{read=true;return -1};override fun close(){closed=true}}
            assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.readImage(c,id,key,input,0,format)}
            assertFalse(read);assertTrue(closed)
        }
    }
    @Test fun unknownAndSymlinkNeverReachDecoderOrModifyFiles() {
        val base=temporary.newFolder().canonicalFile
        File(base,"pag-documents-v1/staging").mkdirs();val dir=File(base,"pag-documents-v1/files");dir.mkdir()
        val file=File(dir,blob);file.writeBytes(encrypted());val extra=File(dir,UUID.randomUUID().toString());extra.writeBytes(byteArrayOf(1))
        val s=DocumentReadSnapshot(DocumentAsset(id,"a","synthetic",DocumentFormat.JPEG,bytes.size.toLong(),1),blob,key.copyOf(),setOf(blob),emptySet(),emptySet())
        fun denied()=DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            assertThrows(ProductStorageFailure::class.java){DocumentImageRead(base).decode(c,s,DocumentImageDecoder{_,_,_->error("No decoder")})}
        }
        denied();assertTrue(extra.exists());extra.delete()
        val outside=temporary.newFile();file.delete();Files.createSymbolicLink(file.toPath(),outside.toPath());denied();assertTrue(outside.exists())
    }
}
