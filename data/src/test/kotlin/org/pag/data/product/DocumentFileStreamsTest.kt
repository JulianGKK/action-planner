package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import org.pag.security.FileStreamingCipher
import java.io.*
import java.util.UUID

class DocumentFileStreamsTest {
    private class Access : StorageContext {
        var allowed = true; var epoch = 1L; var publications = 0
        override val generation get() = epoch
        override val scope = AccessScope.READY
        override fun check(point: AccessPoint) = allowed
        override fun publish(result: () -> Unit): Boolean { publications++;error("No publication in file primitive") }
    }
    private val c = Access()
    private val id = UUID.randomUUID().toString()
    private fun <T> key(work: (ByteArray) -> T) = DocumentFileStreams.withNewKey(c,work)
    private fun encrypted(key: ByteArray, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also {
        assertEquals(bytes.size.toLong(),DocumentFileStreams.encrypt(c,id,key,ByteArrayInputStream(bytes),it))
    }.toByteArray()
    private fun verify(key: ByteArray, bytes: ByteArray, length: Long, format: DocumentFormat = DocumentFormat.TEXT) =
        DocumentFileStreams.verify(c,id,key,ByteArrayInputStream(bytes),length,format)
    private fun invalid(work: () -> Unit) { assertThrows(ProductStorageFailure::class.java,work) }

    @Test fun streamingRoundtripKeepsExactBytesAndDoesNotPublish() = key { key ->
        val source = "synthetisch, unverändert\r\n🦋\tÄ".repeat(3000).toByteArray()
        val ciphertext = encrypted(key,source)
        val evidence = verify(key,ciphertext,source.size.toLong())
        evidence.requireCurrent(c)
        // Isolated test-only raw decryption compares bounded chunks; no product plaintext consumer.
        FileStreamingCipher.fromUnwrappedKey(key).use { cipher -> cipher.decrypt(ByteArrayInputStream(ciphertext),id.toByteArray()).use { input ->
            val buffer=ByteArray(4096);var offset=0
            while(true){val n=input.read(buffer);if(n<0)break;for(i in 0 until n)assertTrue("Byte mismatch",buffer[i]==source[offset+i]);offset+=n}
            assertEquals(source.size,offset);buffer.fill(0)
        } }
        assertEquals(0,c.publications)
    }
    @Test fun wrongAadAndWrongKeyAreRejected() = key { key ->
        val bytes=encrypted(key,"synthetic".toByteArray())
        invalid { DocumentFileStreams.verify(c,UUID.randomUUID().toString(),key,ByteArrayInputStream(bytes),9,DocumentFormat.TEXT) }
        DocumentFileStreams.withNewKey(c) { wrong -> invalid { verify(wrong,bytes,9) } }
    }
    @Test fun revokeDuringVerificationCannotReturnEvidence() = key { key ->
        val ciphertext = encrypted(key, "synthetic".toByteArray())
        var closed = false
        val source = object : ByteArrayInputStream(ciphertext) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val result = super.read(bytes, offset, length)
                c.allowed = false
                return result
            }
            override fun close() { closed = true; super.close() }
        }
        assertThrows(StorageAccessRevoked::class.java) {
            DocumentFileStreams.verify(c,id,key,source,9,DocumentFormat.TEXT)
        }
        assertTrue(closed)
        assertEquals(0,c.publications)
        c.allowed = true
    }
    @Test fun truncationAndLateSegmentCorruptionNeverVerify() = key { key ->
        val length=2*FileStreamingCipher.SEGMENT_BYTES+17
        val bytes=encrypted(key,ByteArray(length){'x'.code.toByte()})
        invalid { verify(key,bytes.copyOf(bytes.size-1),length.toLong()) }
        bytes[bytes.lastIndex-4]=(bytes[bytes.lastIndex-4].toInt() xor 1).toByte()
        invalid { verify(key,bytes,length.toLong()) }
    }
    @Test fun expectedLengthAndInvalidInputsDoNotBecomeEofSuccess() = key { key ->
        val bytes=encrypted(key,"abc".toByteArray())
        for(length in listOf(0L,2L,4L,Long.MAX_VALUE,-1L))invalid { verify(key,bytes,length) }
        invalid { DocumentFileStreams.verify(c,"../invalid",key,ByteArrayInputStream(bytes),3,DocumentFormat.TEXT) }
        invalid { DocumentFileStreams.encrypt(c,id,ByteArray(31),ByteArrayInputStream(byteArrayOf()),ByteArrayOutputStream()) }
    }
    @Test fun exactTwentyFiveMiBAndOneExtraByteUseBoundedReads() = key { key ->
        class Source(private val length:Long):InputStream() {
            var position=0L;var largest=0;var closed=false;var buffer:ByteArray?=null
            override fun read():Int=error("Bulk read required")
            override fun read(bytes:ByteArray,offset:Int,len:Int):Int {
                largest=maxOf(largest,len);buffer=bytes
                if(position==length)return -1
                val n=minOf(len.toLong(),length-position).toInt();bytes.fill('x'.code.toByte(),offset,offset+n);position+=n;return n
            }
            override fun close(){closed=true}
        }
        class Sink:OutputStream() { var closed=false;override fun write(b:Int){};override fun write(b:ByteArray,o:Int,n:Int){};override fun close(){closed=true} }
        val maximum=DocumentRules.MAX_DOCUMENT_BYTES
        val source=Source(maximum);val sink=Sink()
        assertEquals(maximum,DocumentFileStreams.encrypt(c,id,key,source,sink))
        assertTrue(source.closed&&sink.closed);assertTrue(source.largest<=DocumentFileStreams.BUFFER_BYTES)
        assertTrue(source.buffer!!.all{it==0.toByte()})
        val extra=Source(maximum+1000);val rejected=Sink()
        invalid { DocumentFileStreams.encrypt(c,id,key,extra,rejected) }
        assertEquals(maximum+1,extra.position);assertTrue(extra.closed&&rejected.closed)
        assertTrue(extra.buffer!!.all{it==0.toByte()})
    }
    @Test fun binarySignaturesAndTooShortPrefixes() = key { key ->
        val inputs=mapOf(DocumentFormat.PDF to "%PDF-synthetic".toByteArray(),
            DocumentFormat.PNG to byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,0),
            DocumentFormat.JPEG to byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0))
        inputs.forEach { (format,source) ->
            verify(key,encrypted(key,source),source.size.toLong(),format)
            val wrong=source.copyOf().also{it[0]=0}
            invalid { verify(key,encrypted(key,wrong),wrong.size.toLong(),format) }
            invalid { verify(key,encrypted(key,source.copyOf(2)),2,format) }
        }
    }
    @Test fun utf8AcrossBufferBoundariesBomEmptyAndAllowedWhitespace() = key { key ->
        for(text in listOf("", "\uFEFF\t\r\nÄ🦋", "x".repeat(DocumentFileStreams.BUFFER_BYTES-1)+"🦋"+"é")) {
            val bytes=text.toByteArray();verify(key,encrypted(key,bytes),bytes.size.toLong())
        }
    }
    @Test fun malformedUtf8AndForbiddenControlsAtEndAreRejected() = key { key ->
        val prefix=ByteArray(DocumentFileStreams.BUFFER_BYTES-1){'x'.code.toByte()}
        for(bad in listOf(byteArrayOf(0xc0.toByte(),0xaf.toByte()),byteArrayOf(0xed.toByte(),0xa0.toByte(),0x80.toByte()),
            byteArrayOf(0xf0.toByte(),0x9f.toByte()),byteArrayOf(0xff.toByte()))) {
            val bytes=prefix+bad;invalid { verify(key,encrypted(key,bytes),bytes.size.toLong()) }
        }
        for(control in (0..31).filter{it !in listOf(9,10,13)}+127) {
            val bytes=prefix+control.toByte();invalid { verify(key,encrypted(key,bytes),bytes.size.toLong()) }
        }
    }
    @Test fun ioAndCloseExceptionsAreSanitizedAndNeverEof() = key { key ->
        val input=object:InputStream(){override fun read():Int=throw IOException("SYNTHETIC-PRIVATE-PATH")}
        val failure=assertThrows(ProductStorageFailure::class.java){DocumentFileStreams.encrypt(c,id,key,input,ByteArrayOutputStream())}
        assertEquals("P1-STORAGE-DENIED",failure.message);assertNull(failure.cause);assertEquals(0,failure.suppressed.size)
        val output=object:ByteArrayOutputStream(){override fun close(){throw IOException("SYNTHETIC-CLOSE")}}
        invalid { DocumentFileStreams.encrypt(c,id,key,ByteArrayInputStream(byteArrayOf()),output) }
        val ciphertext=encrypted(key,"synthetic".toByteArray())
        val throwing=object:ByteArrayInputStream(ciphertext){override fun read(b:ByteArray,o:Int,n:Int):Int { if(available()==0)throw IOException("SYNTHETIC-EOF-ERROR");return super.read(b,o,n) }}
        invalid { DocumentFileStreams.verify(c,id,key,throwing,9,DocumentFormat.TEXT) }
    }
    @Test fun revokeDuringReadClosesStreamsAndDoesNotWriteTheReadPlaintext() = key { key ->
        var reads=0;var closed=false;var outputClosed=false
        val source=object:ByteArrayInputStream(ByteArray(100){1}) {
            override fun read(b:ByteArray,o:Int,n:Int):Int { reads++;val read=super.read(b,o,n);c.allowed=false;return read }
            override fun close(){closed=true}
        }
        val output=object:ByteArrayOutputStream(){override fun close(){outputClosed=true}}
        assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.encrypt(c,id,key,source,output)}
        assertEquals(1,reads);assertTrue(closed&&outputClosed);assertEquals(0,c.publications)
        c.allowed=true // Only the isolated fake; not a product session revival.
    }
    @Test fun revokeAtCloseAndOldEvidenceNeverAuthorizeProgress() = key { key ->
        val output=object:ByteArrayOutputStream(){override fun close(){c.allowed=false}}
        assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.encrypt(c,id,key,ByteArrayInputStream(byteArrayOf()),output)}
        c.allowed=true
        val evidence=verify(key,encrypted(key,byteArrayOf()),0)
        assertThrows(StorageAccessRevoked::class.java){evidence.requireCurrent(Access())}
        c.epoch++
        assertThrows(StorageAccessRevoked::class.java){evidence.requireCurrent(c)}
        c.epoch-=1
    }
    @Test fun earlyDenialClosesResourcesAndNewKeysAreIndependentScopedBuffers() {
        var inputClosed=false;var outputClosed=false
        c.allowed=false
        val input=object:ByteArrayInputStream(byteArrayOf()){override fun close(){inputClosed=true}}
        val output=object:ByteArrayOutputStream(){override fun close(){outputClosed=true}}
        assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.encrypt(c,id,ByteArray(32),input,output)}
        assertTrue(inputClosed&&outputClosed);c.allowed=true
        lateinit var first:ByteArray;lateinit var second:ByteArray
        key { a -> first=a;key { b -> second=b;assertEquals(32,a.size);assertEquals(32,b.size);assertFalse("Independent keys",a.contentEquals(b)) } }
        assertTrue(first.all{it==0.toByte()});assertTrue(second.all{it==0.toByte()})
        lateinit var failed:ByteArray
        assertThrows(ProductStorageFailure::class.java){key { failed=it;throw IOException("SYNTHETIC") }}
        assertTrue(failed.all{it==0.toByte()})
    }
}
