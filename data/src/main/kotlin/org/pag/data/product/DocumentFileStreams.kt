package org.pag.data.product

import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import org.pag.domain.DocumentFormat
import org.pag.domain.DocumentRules
import org.pag.security.FileStreamingCipher
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom

/** Technical observation, never a commit/publication permission or persisted proof. */
internal class DocumentVerification internal constructor(val documentId: String, val format: DocumentFormat,
    val plaintextSize: Long, private val context: StorageContext, private val generation: Long) {
    fun requireCurrent(candidate: StorageContext) {
        if (candidate !== context) throw StorageAccessRevoked()
        documentFileGate(candidate, generation)
    }
}

internal fun documentFileGate(c: StorageContext, generation: Long, point: AccessPoint = AccessPoint.STEP) {
    if (generation < 0 || c.generation != generation || !c.check(point) || c.generation != generation) throw StorageAccessRevoked()
}

/** No causes/suppressed provider exceptions, paths or input values escape this boundary. */
internal inline fun <T> documentFileBoundary(work: () -> T): T = try { work() }
catch (_: StorageAccessRevoked) { throw StorageAccessRevoked() }
catch (_: DocumentTextUnavailable) { throw DocumentTextUnavailable() }
catch (_: DocumentTextFormatFailure) { throw DocumentTextFormatFailure() }
catch (failure: DocumentInputFailure) {
    // Multiple neutral provider failures may coincide (read + close). An additional
    // ciphertext/sync/unknown failure must still dominate the expected input error.
    if (failure.suppressed.any { it !is DocumentInputFailure || it.suppressed.isNotEmpty() }) throw ProductStorageFailure()
    throw DocumentInputFailure(failure.reason)
}
catch (_: Exception) { throw ProductStorageFailure() }

internal class DocumentInputFailure(val reason: org.pag.domain.DocumentFailure) : RuntimeException()

/** Stream primitives only. Caller owns prior DB/job admission and safe stream acquisition.
 * No file creation, root setup, phase mutation, persistence or publication here.
 */
internal object DocumentFileStreams {
    const val BUFFER_BYTES = 32 * 1024

    fun <T> withNewKey(c: StorageContext, consume: (ByteArray) -> T): T = documentFileBoundary {
        val generation = c.generation
        documentFileGate(c, generation, AccessPoint.OPERATION)
        val key = ByteArray(32)
        try {
            documentFileGate(c, generation)
            SecureRandom().nextBytes(key)
            documentFileGate(c, generation)
            val result = consume(key)
            documentFileGate(c, generation)
            result
        } finally { key.fill(0) }
    }

    private fun <T> cipher(c: StorageContext, generation: Long, documentId: String, key: ByteArray,
        consume: (FileStreamingCipher, ByteArray) -> T): T {
        if (!DocumentStorageRules.opaque(documentId) || key.size != 32) throw ProductStorageFailure()
        val copy = key.copyOf()
        try {
            documentFileGate(c, generation, AccessPoint.OPERATION)
            FileStreamingCipher.fromUnwrappedKey(copy).use { cipher ->
                documentFileGate(c, generation)
                return consume(cipher, documentId.toByteArray(Charsets.UTF_8))
            }
        } finally { copy.fill(0) }
    }

    /** Owns and closes both supplied streams even on early denial; result is only actual byte count. */
    fun encrypt(c: StorageContext, documentId: String, key: ByteArray, source: InputStream,
        destination: OutputStream, format: DocumentFormat? = null): Long = documentFileBoundary {
        val generation = c.generation
        source.use { input -> destination.use { output ->
            cipher(c, generation, documentId, key) { cipher, aad ->
                val buffer = ByteArray(BUFFER_BYTES)
                try {
                    var length = 0L
                    val formatCheck = format?.let { DocumentFormatCheck(it, true) }
                    try {
                        cipher.encrypt(output, aad).use { encrypted ->
                            while (true) {
                                documentFileGate(c, generation)
                                // Read at most one byte past the hard limit, including at exact maximum.
                                val maximumRead = minOf(buffer.size.toLong(), DocumentRules.MAX_DOCUMENT_BYTES - length + 1).toInt()
                                val count = input.read(buffer, 0, maximumRead)
                                documentFileGate(c, generation)
                                if (count == -1) break
                                if (count <= 0 || count > maximumRead) throw ProductStorageFailure()
                                if (count > DocumentRules.MAX_DOCUMENT_BYTES - length) {
                                    if (format == null) throw ProductStorageFailure()
                                    throw DocumentInputFailure(org.pag.domain.DocumentFailure.TOO_LARGE)
                                }
                                formatCheck?.accept(buffer,count)
                                length += count
                                documentFileGate(c, generation)
                                encrypted.write(buffer, 0, count)
                                buffer.fill(0)
                                documentFileGate(c, generation)
                            }
                            formatCheck?.finish()
                            documentFileGate(c, generation)
                        } // close may physically finish a last segment after revoke; it grants nothing.
                        documentFileGate(c, generation)
                        length
                    } finally { formatCheck?.close() }
                } finally { buffer.fill(0) }
            }
        } }.also { documentFileGate(c, generation) }
    }

    /** No plaintext consumer. Only full authenticated EOF + exact length + format may return. */
    fun verify(c: StorageContext, documentId: String, key: ByteArray, source: InputStream,
        expectedSize: Long, format: DocumentFormat): DocumentVerification = verifyInto(c,documentId,key,source,expectedSize,format,null)

    /** Bounded owned RAM; never returns before full authentication, EOF and format validation. */
    fun readImage(c: StorageContext, documentId: String, key: ByteArray, source: InputStream,
        expectedSize: Long, format: DocumentFormat): ByteArray {
        if (!DocumentRules.validSize(expectedSize) || format !in setOf(DocumentFormat.JPEG,DocumentFormat.PNG)) {
            source.close(); throw ProductStorageFailure()
        }
        var bytes:ByteArray?=null
        try {
            source.use {
                bytes=ByteArray(expectedSize.toInt())
                verifyInto(c,documentId,key,it,expectedSize,format,bytes).requireCurrent(c)
            }
            return bytes!!
        } catch (failure:Throwable) { bytes?.fill(0);throw failure }
    }

    /** Extract only one page while the existing strict UTF-8 validator authenticates all bytes. */
    fun readText(c:StorageContext,documentId:String,key:ByteArray,source:InputStream,expectedSize:Long,page:Int,
        request:org.pag.domain.DocumentTextRequest?=null):org.pag.domain.DocumentTextPage {
        var result:org.pag.domain.DocumentTextPage?=null
        try {
            source.use {
                DocumentTextPagination(page) {documentFileGate(c,c.generation)}.use { pages ->
                    var utf8Nanos=0L;var pageNanos=0L
                    verifyInto(c,documentId,key,it,expectedSize,DocumentFormat.TEXT,null,{chars,count ->
                        val start=System.nanoTime()
                        try {pages.accept(chars,count)}finally {pageNanos+=System.nanoTime()-start}
                    },{utf8Nanos+=it}).requireCurrent(c)
                    request?.duration(org.pag.domain.DocumentTextPhase.UTF8_DECODE,maxOf(0,utf8Nanos-pageNanos))
                    val preparing=System.nanoTime()
                    result=pages.finish()
                    request?.duration(org.pag.domain.DocumentTextPhase.PAGE_PREPARE,pageNanos+System.nanoTime()-preparing)
                }
            }
            return result!!
        } catch(failure:Throwable) {result?.close();throw failure}
    }

    private fun verifyInto(c: StorageContext, documentId: String, key: ByteArray, source: InputStream,
        expectedSize: Long, format: DocumentFormat, destination:ByteArray?,text:((CharArray,Int)->Unit)?=null,
        utf8:((Long)->Unit)?=null): DocumentVerification = documentFileBoundary {
        val generation = c.generation
        var invalidText=false
        source.use { raw ->
            if (!DocumentRules.validSize(expectedSize)) throw ProductStorageFailure()
            cipher(c, generation, documentId, key) { cipher, aad ->
                val buffer = ByteArray(BUFFER_BYTES)
                DocumentFormatCheck(format,text=text,utf8=utf8,textReader=text!=null).use { formatCheck ->
                    try {
                        var length = 0L
                        cipher.decrypt(raw, aad).use { plaintext ->
                            while (true) {
                                documentFileGate(c, generation)
                                val maximumRead = minOf(buffer.size.toLong(), DocumentRules.MAX_DOCUMENT_BYTES - length + 1).toInt()
                                val count = plaintext.read(buffer, 0, maximumRead)
                                documentFileGate(c, generation)
                                if (count == -1) break // Exceptions/zero reads are never EOF.
                                if (count <= 0 || count > maximumRead || count > expectedSize - length) throw ProductStorageFailure()
                                destination?.let { buffer.copyInto(it,length.toInt(),0,count) }
                                length += count
                                if(!invalidText)try {formatCheck.accept(buffer,count)}catch(_:DocumentTextFormatFailure){invalidText=true}
                                buffer.fill(0)
                                documentFileGate(c, generation)
                            }
                            if (length != expectedSize) throw ProductStorageFailure()
                            documentFileGate(c, generation)
                            if(!invalidText)try {formatCheck.finish()}catch(_:DocumentTextFormatFailure){invalidText=true}
                            documentFileGate(c, generation)
                        }
                    } finally { buffer.fill(0) }
                }
            }
        }
        documentFileGate(c, generation)
        if(invalidText)throw DocumentTextFormatFailure()
        DocumentVerification(documentId,format,expectedSize,c,generation)
    }
}

/** Small binary prefix check, or full incremental strict UTF-8. No renderer or sanitizer. */
private class DocumentFormatCheck(private val format: DocumentFormat, private val sourceValidation: Boolean = false,
    private val text:((CharArray,Int)->Unit)?=null,private val utf8:((Long)->Unit)?=null,
    private val textReader:Boolean=false) : AutoCloseable {
    private fun invalid(): Nothing {
        if(sourceValidation)throw DocumentInputFailure(org.pag.domain.DocumentFailure.FORMAT)
        if(textReader)throw DocumentTextFormatFailure()
        throw ProductStorageFailure()
    }
    private val prefix = when (format) {
        DocumentFormat.PDF -> "%PDF-".toByteArray(Charsets.US_ASCII)
        DocumentFormat.PNG -> byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
        DocumentFormat.JPEG -> byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte())
        DocumentFormat.TEXT -> byteArrayOf()
    }
    private var prefixRead = 0
    private val decoder = if (format == DocumentFormat.TEXT) Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT) else null
    private val pending = if (decoder != null) ByteBuffer.allocate(DocumentFileStreams.BUFFER_BYTES + 4) else null
    private val chars = if (decoder != null) CharBuffer.allocate(DocumentFileStreams.BUFFER_BYTES) else null

    fun accept(bytes: ByteArray, count: Int) {
        if (decoder == null) {
            for (i in 0 until minOf(count,prefix.size - prefixRead)) {
                if (bytes[i] != prefix[prefixRead++]) invalid()
            }
        } else {
            pending!!.put(bytes,0,count); pending.flip()
            decode(false)
            pending.compact()
        }
    }
    private fun checkChars() {
        val output = chars!!
        output.flip()
        while (output.hasRemaining()) {
            val n = output.get().code
            if ((n in 0..31 && n != 9 && n != 10 && n != 13) || n == 127) invalid()
        }
        text?.invoke(output.array(),output.limit())
        output.array().fill('\u0000'); output.clear()
    }
    private fun decode(end: Boolean) {
        val started=if(utf8!=null)System.nanoTime() else 0L
        try {
        while (true) {
            val result = decoder!!.decode(pending!!,chars!!,end)
            checkChars()
            if (result.isError) invalid()
            if (result.isUnderflow) break
        }
        }finally {if(utf8!=null)utf8.invoke(System.nanoTime()-started)}
    }
    fun finish() {
        if (decoder == null) {
            if (prefixRead != prefix.size) invalid()
        } else {
            pending!!.flip(); decode(true)
            if (pending.hasRemaining()) invalid()
            while (true) {
                val result = decoder.flush(chars!!)
                checkChars()
                if (result.isError) invalid()
                if (result.isUnderflow) break
            }
        }
    }
    override fun close() { pending?.array()?.fill(0); chars?.array()?.fill('\u0000'); prefix.fill(0) }
}
