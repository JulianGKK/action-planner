package org.pag.data.prototype.b3

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.security.FileStreamingCipher
import java.io.*
import java.security.SecureRandom

class B3StreamsTest {
    private var allowed = true
    private val context = object : StorageContext {
        override val scope = AccessScope.MAINTENANCE
        override fun check(point: AccessPoint) = allowed
        override fun publish(result: () -> Unit): Boolean { if (!allowed) return false; result(); return true }
    }
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val aad = "00000000-0000-0000-0000-000000000001".toByteArray()
    private val length = 3L * FileStreamingCipher.SEGMENT_BYTES + 317
    private fun streams(hook: (B3Point) -> Unit = {}) = B3Streams(FileStreamingCipher.fromUnwrappedKey(key), context, hook)
    private fun ciphertext(): ByteArray = ByteArrayOutputStream().also { output ->
        streams().use { it.encrypt(B3Source(length), output, aad) }
    }.toByteArray()

    @Test fun keyParametersRoundTripAndPublishNeedsFullEof() {
        val bytes = ciphertext()
        streams().use { stream ->
            val proof = stream.validate({ ByteArrayInputStream(bytes) }, aad, length)
            var published = false; stream.publish(proof) { published = true }
            assertTrue(published)
            var read = 0L
            stream.read(proof) { _, count -> read += count }
            assertEquals(length, read)
        }
    }
    @Test fun lateCorruptionCanExposeEarlySegmentsButCannotValidateOrPublish() {
        val bytes = ciphertext().also { it[it.lastIndex - 31] = (it[it.lastIndex - 31].toInt() xor 1).toByte() }
        var early = 0L
        FileStreamingCipher.fromUnwrappedKey(key).use { cipher ->
            cipher.decrypt(ByteArrayInputStream(bytes), aad).use { input ->
                val buffer = ByteArray(32768)
                try { while (true) { val n = input.read(buffer); if (n == -1) fail("B3-LATE-FAULT-NOT-DETECTED"); early += n } }
                catch (_: IOException) { assertTrue(early > 0); assertTrue(early < length) }
                finally { buffer.fill(0) }
            }
        }
        streams().use { stream ->
            var published = false
            try { val proof = stream.validate({ ByteArrayInputStream(bytes) }, aad, length); stream.publish(proof) { published = true }; fail("B3-PUBLISHED-INVALID") }
            catch (error: B3Exception) { assertEquals(B3Id.INTEGRITY, error.id) }
            assertFalse(published)
        }
    }
    @Test fun fullValidationRejectsWrongAadAndWrongKey() {
        val bytes = ciphertext()
        streams().use {
            try { it.validate({ ByteArrayInputStream(bytes) }, byteArrayOf(1), length); fail("B3-AAD") }
            catch (error: B3Exception) { assertEquals(B3Id.INTEGRITY, error.id) }
        }
        val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        B3Streams(FileStreamingCipher.fromUnwrappedKey(wrong), context).use {
            try { it.validate({ ByteArrayInputStream(bytes) }, aad, length); fail("B3-KEY") }
            catch (error: B3Exception) { assertEquals(B3Id.INTEGRITY, error.id) }
        }
        wrong.fill(0)
    }
    @Test fun noConsumerBytesAfterRevocation() {
        val bytes = ciphertext()
        var consumerBytes = 0
        var reading = false
        streams { if (reading && it == B3Point.READ) allowed = false }.use { stream ->
            val proof = stream.validate({ ByteArrayInputStream(bytes) }, aad, length)
            reading = true
            try { stream.read(proof) { _, count -> consumerBytes += count }; fail("B3-LATE") }
            catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
            assertEquals(0, consumerBytes)
        }
    }
    @Test fun revocationBeforePublishDoesNotCommit() {
        val bytes = ciphertext()
        streams { if (it == B3Point.BEFORE_PUBLISH) allowed = false }.use { stream ->
            val proof = stream.validate({ ByteArrayInputStream(bytes) }, aad, length)
            var published = false
            try { stream.publish(proof) { published = true }; fail("B3-PUBLISH") }
            catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
            assertFalse(published)
        }
    }
    @Test fun closeRevokesOldOwnerEvenIfContextIsStillValid() {
        val bytes = ciphertext(); val stream = streams()
        val proof = stream.validate({ ByteArrayInputStream(bytes) }, aad, length)
        stream.close()
        try { stream.read(proof) { _, _ -> fail("B3-OLD") }; fail("B3-CLOSED") }
        catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
    }
    @Test fun lengthMismatchIsNotEofSuccess() {
        val bytes = ciphertext()
        streams().use {
            try { it.validate({ ByteArrayInputStream(bytes) }, aad, length + 1); fail("B3-LENGTH") }
            catch (error: B3Exception) { assertEquals(B3Id.LENGTH, error.id) }
        }
    }
    @Test fun evidenceFromDifferentOwnerCannotCommit() {
        val bytes = ciphertext()
        streams().use { first -> streams().use { second ->
            val proof = first.validate({ ByteArrayInputStream(bytes) }, aad, length)
            var committed = false
            try { second.publish(proof) { committed = true }; fail("B3-FOREIGN-PROOF") }
            catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
            assertFalse(committed)
        } }
    }
    @Test fun closeCanFinalizeAfterRevocationButCannotPublish() {
        val output = ByteArrayOutputStream()
        streams { if (it == B3Point.BEFORE_CLOSE) allowed = false }.use { stream ->
            try { stream.encrypt(B3Source(1), output, aad); fail("B3-CLOSE-WIDERRUF") }
            catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
        }
        // Isolated test-only raw primitive demonstrates physical cleanup, not renewed access.
        FileStreamingCipher.fromUnwrappedKey(key).use { raw ->
            raw.decrypt(ByteArrayInputStream(output.toByteArray()), aad).use { input ->
                assertEquals(B3Source.marker[0].toInt() and 255, input.read()); assertEquals(-1, input.read())
            }
        }
        streams().use { stream ->
            try { stream.validate({ ByteArrayInputStream(output.toByteArray()) }, aad, 1); fail("B3-NO-REVALIDATION") }
            catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
        }
    }
    @Test fun earlyDenialClosesSourceAndDestination() {
        var sourceClosed = false; var destinationClosed = false
        val source = object : ByteArrayInputStream(byteArrayOf(1)) { override fun close() { sourceClosed = true; super.close() } }
        val output = object : ByteArrayOutputStream() { override fun close() { destinationClosed = true; super.close() } }
        allowed = false
        streams().use {
            try { it.encrypt(source, output, aad); fail("B3-EARLY-DENIAL") }
            catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
        }
        assertTrue(sourceClosed); assertTrue(destinationClosed)
    }
}
