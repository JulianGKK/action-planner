package org.pag.data.prototype.b3

import org.pag.domain.AccessPoint
import org.pag.domain.AccessScope
import org.pag.domain.StorageContext
import org.pag.security.FileStreamingCipher
import java.io.InputStream
import java.io.OutputStream

enum class B3State { EMPTY, KEY_READY, STAGING, VERIFYING, PUBLISHED, COMPLETE_CANDIDATE, BLOCKED }
enum class B3Id(val testId: String) {
    EMPTY("B3-BOOTSTRAP-EMPTY"), COMPLETE("B3-BOOTSTRAP-COMPLETE"),
    BLOCKED("B3-BOOTSTRAP-BLOCKED"), NOT_EMPTY("B3-BOOTSTRAP-NOT-EMPTY"),
    UNWRAP("B3-KEY-UNWRAP-SUCCEEDED"), ENCRYPT("B3-ENCRYPT-SUCCEEDED"),
    VALIDATE("B3-VALIDATE-SUCCEEDED"), PUBLISH("B3-PUBLISH-SUCCEEDED"),
    INTEGRITY("B3-INTEGRITY-FAILED"), LENGTH("B3-LENGTH-MISMATCH"),
    // Reserved scenario IDs for known injected faults, not inferred from ambiguous crypto errors.
    TRUNCATED("B3-TRUNCATED"), AAD_MISMATCH("B3-AAD-MISMATCH"), WRONG_KEY("B3-WRONG-KEY"),
    EXPIRED("B3-SESSION-EXPIRED"), STALE("B3-STALE-GENERATION"),
    RESTART("B3-RESTART-SUCCEEDED"), IO("B3-IO-FAILED")
}
class B3Exception(val id: B3Id) : RuntimeException(id.testId)
enum class B3Point { BEFORE_UNWRAP, AFTER_UNWRAP, BEFORE_STREAM, SOURCE_READ, ENCRYPT,
    BEFORE_CLOSE, AFTER_CLOSE, VALIDATION, AFTER_VALIDATION, BEFORE_PUBLISH, READ }

internal fun StorageContext.requireB3(point: AccessPoint = AccessPoint.STEP) {
    if (scope != AccessScope.MAINTENANCE || !check(point)) throw B3Exception(B3Id.EXPIRED)
}

/** Short-lived, session-bound stream owner. Raw primitive/streams never escape this owner.
 * close may finalize ciphertext after revocation. This never grants publication.
 */
class B3Streams(private val cipher: FileStreamingCipher, private val context: StorageContext,
    private val hook: (B3Point) -> Unit = {}) : AutoCloseable {
    private var closed = false
    private fun check(point: AccessPoint = AccessPoint.STEP) {
        if (closed) throw B3Exception(B3Id.EXPIRED)
        context.requireB3(point)
    }
    fun encrypt(source: InputStream, destination: OutputStream, aad: ByteArray) {
        val buffer = ByteArray(32 * 1024)
        try {
            // Own both streams even if admission or constructing the Tink stream fails.
            source.use { input -> destination.use { output ->
                hook(B3Point.BEFORE_STREAM); check(AccessPoint.OPERATION)
                cipher.encrypt(output, aad).use { encrypted ->
                    while (true) {
                        check(); val count = input.read(buffer)
                        hook(B3Point.SOURCE_READ); check()
                        if (count == -1) break
                        if (count == 0) throw B3Exception(B3Id.IO)
                        encrypted.write(buffer, 0, count)
                        buffer.fill(0); hook(B3Point.ENCRYPT); check()
                    }
                    hook(B3Point.BEFORE_CLOSE); check()
                }
            } }
            hook(B3Point.AFTER_CLOSE); check()
        } catch (error: B3Exception) { throw error }
        catch (_: Exception) { throw B3Exception(B3Id.IO) }
        finally { buffer.fill(0) }
    }

    /** EOF evidence from validate(), bound to its short-lived owner and source.
     * Module internals are not a runtime security sandbox.
     */
    class Verified internal constructor(internal val owner: B3Streams, internal val aad: ByteArray,
        internal val length: Long, internal val source: () -> InputStream)

    fun validate(open: () -> InputStream, aad: ByteArray, length: Long): Verified {
        consume(open, aad, length) { _, _ -> }
        hook(B3Point.AFTER_VALIDATION); check()
        return Verified(this, aad.copyOf(), length, open)
    }

    /** Validation and internal streaming are two full I/O passes. Caller supplies the same
     * privately owned ciphertext, never a mutable externally supplied source.
     */
    fun read(evidence: Verified, consume: (ByteArray, Int) -> Unit) {
        if (evidence.owner !== this) throw B3Exception(B3Id.EXPIRED)
        consume(evidence.source, evidence.aad, evidence.length, consume)
    }

    fun publish(evidence: Verified, commit: () -> Unit) {
        if (evidence.owner !== this) throw B3Exception(B3Id.EXPIRED)
        hook(B3Point.BEFORE_PUBLISH); check(AccessPoint.COMMIT)
        // No I/O inside coordinator monitor. Admission and physical completion are distinct.
        commit(); check(AccessPoint.PUBLICATION)
    }

    private fun consume(open: () -> InputStream, aad: ByteArray, expected: Long,
        consumer: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(32 * 1024)
        var length = 0L
        try {
            check(AccessPoint.OPERATION)
            open().use { raw -> cipher.decrypt(raw, aad).use { decrypted ->
                while (true) {
                    check(); val count = decrypted.read(buffer)
                    hook(B3Point.VALIDATION); check()
                    if (count == -1) break // Only -1 is EOF; exceptions are never EOF.
                    if (count == 0 || count > expected - length) throw B3Exception(B3Id.LENGTH)
                    length += count
                    hook(B3Point.READ)
                    // Synchronous small-buffer consumption serialized with invalidation.
                    // No native I/O or long consumer work permitted in this callback.
                    if (!context.publish { consumer(buffer, count) }) throw B3Exception(B3Id.EXPIRED)
                    buffer.fill(0)
                }
            } }
            check()
            if (length != expected) throw B3Exception(B3Id.LENGTH)
        } catch (error: B3Exception) { throw error }
        catch (_: Exception) { throw B3Exception(B3Id.INTEGRITY) }
        finally { buffer.fill(0) }
    }
    override fun close() { closed = true; cipher.close() }
}
