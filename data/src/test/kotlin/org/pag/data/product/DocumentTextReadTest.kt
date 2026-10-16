package org.pag.data.product

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import java.io.*
import java.nio.file.Files
import java.util.UUID

class DocumentTextReadTest {
    @get:Rule val temp=TemporaryFolder()
    private var allowed=true
    private val c=object:StorageContext {
        override val scope=AccessScope.READY;override val generation=1L
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean=error("No publication")
    }
    private val id=UUID.randomUUID().toString();private val key=ByteArray(32){(it+1).toByte()}
    private fun encrypted(bytes:ByteArray)=ByteArrayOutputStream().also {DocumentFileStreams.encrypt(c,id,key,ByteArrayInputStream(bytes),it)}.toByteArray()
    private fun page(cipher:ByteArray,size:Long,index:Int)=DocumentFileStreams.readText(c,id,key,ByteArrayInputStream(cipher),size,index)
    @Test fun unicodeAndLineEndingsAcrossEveryPageAreLossless() {
        val text="\uFEFF"+"e\u0301🦋👩‍👩‍👧‍👦\tä\r\n".repeat(4000)+"no-final-newline"
        val bytes=text.toByteArray();val cipher=encrypted(bytes);val values=mutableListOf<String>()
        val count=page(cipher,bytes.size.toLong(),0).use {it.pageCount}
        for(i in 0 until count)page(cipher,bytes.size.toLong(),i).use {
            assertEquals(i,it.index);assertEquals(count,it.pageCount)
            val part=it.text()!!;assertTrue(part.toByteArray().size<=DocumentTextLimits.PAGE_BYTES);values+=part
            assertFalse(part.firstOrNull()?.let(Character::isLowSurrogate)==true)
            assertFalse(part.lastOrNull()?.let(Character::isHighSurrogate)==true)
        }
        assertEquals(text,values.joinToString(""))
    }
    @Test fun emojiAndCrlfAreNeverSplit() {
        for(tail in listOf("🦋","\r\n")) {
            val text="a".repeat(DocumentTextLimits.PAGE_BYTES-1)+tail+"z"
            val bytes=text.toByteArray();val cipher=encrypted(bytes)
            val first=page(cipher,bytes.size.toLong(),0).use {it.text()!!}
            val second=page(cipher,bytes.size.toLong(),1).use {it.text()!!}
            assertEquals("a".repeat(DocumentTextLimits.PAGE_BYTES-1),first);assertEquals(tail+"z",second)
        }
    }
    @Test fun emptyAndExactBoundaryNoPhantomPage() {
        for(text in listOf("","a".repeat(DocumentTextLimits.PAGE_BYTES))) {
            val bytes=text.toByteArray();val cipher=encrypted(bytes)
            page(cipher,bytes.size.toLong(),0).use {assertEquals(1,it.pageCount);assertEquals(text,it.text())}
            assertThrows(DocumentTextUnavailable::class.java){page(cipher,bytes.size.toLong(),1)}
        }
    }
    @Test fun malformedUtf8ControlsAndIncompleteSurrogateNeverReleaseText() {
        for(end in listOf(byteArrayOf(-64,-81),byteArrayOf(-16,-97),byteArrayOf(0),byteArrayOf(127),byteArrayOf(-19,-96,-128))) {
            val bytes=ByteArray(DocumentTextLimits.PAGE_BYTES+1){'x'.code.toByte()}+end
            val cipher=encrypted(bytes)
            assertThrows(DocumentTextFormatFailure::class.java){page(cipher,bytes.size.toLong(),0)}
        }
    }
    @Test fun lateCipherCorruptionTruncationWrongSizeKeyAndAadFailClosed() {
        val bytes=ByteArray(2_100_000){'x'.code.toByte()};val cipher=encrypted(bytes)
        val bad=cipher.copyOf().also {it[it.lastIndex-3]=(it[it.lastIndex-3].toInt() xor 1).toByte()}
        for(value in listOf(bad,cipher.copyOf(cipher.size-1)))assertThrows(ProductStorageFailure::class.java){page(value,bytes.size.toLong(),0)}
        assertThrows(ProductStorageFailure::class.java){page(cipher,bytes.size.toLong()-1,0)}
        assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.readText(c,UUID.randomUUID().toString(),key,ByteArrayInputStream(cipher),bytes.size.toLong(),0)}
        assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.readText(c,id,ByteArray(32),ByteArrayInputStream(cipher),bytes.size.toLong(),0)}
    }
    @Test fun formatErrorWaitsForFullAuthenticationAndNeverMasksLateTagFailure() {
        val bytes=byteArrayOf(0)+ByteArray(2_100_000){120};val cipher=encrypted(bytes);var read=0;var closed=false
        val stream=object:ByteArrayInputStream(cipher){
            override fun read(b:ByteArray,o:Int,n:Int):Int=super.read(b,o,n).also{if(it>0)read+=it}
            override fun close(){closed=true}
        }
        assertThrows(DocumentTextFormatFailure::class.java){DocumentFileStreams.readText(c,id,key,stream,bytes.size.toLong(),0)}
        assertEquals(cipher.size,read);assertTrue(closed)
        cipher[cipher.lastIndex]=(cipher.last().toInt() xor 1).toByte()
        assertThrows(ProductStorageFailure::class.java){page(cipher,bytes.size.toLong(),0)}
    }
    @Test fun revocationWhileReadingClosesAndNeverReturnsFirstPage() {
        val bytes="synthetic".repeat(10000).toByteArray();val cipher=encrypted(bytes);var closed=false
        val source=object:ByteArrayInputStream(cipher){override fun read(b:ByteArray,o:Int,n:Int):Int=super.read(b,o,n).also {allowed=false};override fun close(){closed=true}}
        assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.readText(c,id,key,source,bytes.size.toLong(),0)}
        assertTrue(closed)
    }
    @Test fun revocationDuringPagePreparationWipesOwnedResult() {
        var checks=0;var denied=false
        DocumentTextPagination(0){if(denied)throw StorageAccessRevoked();checks++}.use {p ->
            val chars="synthetic".toCharArray();p.accept(chars,chars.size);denied=true
            assertThrows(StorageAccessRevoked::class.java){p.finish()}
        }
        assertTrue(checks>0)
    }
    @Test fun completedPageWipesItsTransferredCharBufferAndRefusesReadsAfterClose() {
        val owned="synthetic".toCharArray();val page=DocumentTextPage(0,1,owned)
        assertEquals("synthetic",page.text());page.close();page.close();assertNull(page.text());assertTrue(owned.all {it=='\u0000'})
    }
    @Test fun namespaceUnknownAndSymlinkBlockAndCiphertextStaysUnchanged() {
        val base=temp.newFolder().canonicalFile;File(base,"pag-documents-v1/staging").mkdirs();val files=File(base,"pag-documents-v1/files");files.mkdir()
        val name=UUID.randomUUID().toString();val file=File(files,name);val plain="synthetic".toByteArray();val cipher=encrypted(plain);file.writeBytes(cipher)
        val inv=DocumentFileInventory(base)
        inv.readText(c,name,emptySet(),setOf(name),id,key,plain.size.toLong(),0).use {assertEquals("synthetic",it.text())}
        assertArrayEquals(cipher,file.readBytes())
        val unknown=File(files,UUID.randomUUID().toString());unknown.writeBytes(byteArrayOf(1))
        assertThrows(ProductStorageFailure::class.java){inv.readText(c,name,emptySet(),setOf(name),id,key,plain.size.toLong(),0)};assertTrue(unknown.exists());unknown.delete()
        val outside=temp.newFile();file.delete();Files.createSymbolicLink(file.toPath(),outside.toPath())
        assertThrows(ProductStorageFailure::class.java){inv.readText(c,name,emptySet(),setOf(name),id,key,plain.size.toLong(),0)};assertTrue(outside.exists())
    }
    @Test fun maximumDocumentUsesOnlyOneBoundedPageAndFullEof() {
        val maximum=DocumentRules.MAX_DOCUMENT_BYTES
        val input=object:InputStream(){var position=0L;override fun read():Int=error("Bulk only");override fun read(b:ByteArray,o:Int,n:Int):Int {
            if(position==maximum)return -1;val count=minOf(n.toLong(),maximum-position).toInt();b.fill(120.toByte(),o,o+count);position+=count;return count
        }}
        val cipher=ByteArrayOutputStream().also {DocumentFileStreams.encrypt(c,id,key,input,it)}.toByteArray()
        page(cipher,maximum,0).use {assertEquals(800,it.pageCount);assertEquals(DocumentTextLimits.PAGE_BYTES,it.text()!!.length)}
        page(cipher,maximum,799).use {assertEquals(DocumentTextLimits.PAGE_BYTES,it.text()!!.length)}
    }
}
