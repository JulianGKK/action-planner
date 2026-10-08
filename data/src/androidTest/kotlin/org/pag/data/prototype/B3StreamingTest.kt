package org.pag.data.prototype

import android.app.KeyguardManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.pag.data.prototype.b3.*
import org.pag.domain.*
import org.pag.security.*
import java.io.*
import java.security.SecureRandom
import java.util.UUID

/** Real Tink/JCA/filesystem; fake key consumer only for positive streams.
 * No injected credential. Positive RSA + explicit prompt + force-stop is manual evidence.
 */
class B3StreamingTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private var allowed = true
    private val context = object : StorageContext {
        override val scope = AccessScope.MAINTENANCE
        override fun check(point: AccessPoint) = allowed
        override fun publish(result: () -> Unit): Boolean { if (!allowed) return false; result(); return true }
    }
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val aad = UUID.randomUUID().toString().toByteArray(Charsets.US_ASCII)
    private fun root() = File(app.noBackupFilesDir.canonicalFile, "b3-test-${UUID.randomUUID()}").also { assertTrue(it.mkdir()) }
    private fun streams(bytes: ByteArray = key, hook: (B3Point) -> Unit = {}) = B3Streams(FileStreamingCipher.fromUnwrappedKey(bytes), context, hook)
    private fun encrypt(file: File, length: Long) {
        streams().use { it.encrypt(B3Source(length), FileOutputStream(file), aad) }
    }
    private fun validate(file: File, length: Long, associated: ByteArray = aad) {
        streams().use { it.validate({ FileInputStream(file) }, associated, length) }
    }
    private fun reject(file: File, length: Long, associated: ByteArray = aad) {
        var published = false
        streams().use { stream ->
            try { val proof = stream.validate({ FileInputStream(file) }, associated, length); stream.publish(proof) { published = true }; fail("B3-INVALID-PUBLISHED") }
            catch (error: B3Exception) { assertTrue(error.id in setOf(B3Id.INTEGRITY, B3Id.LENGTH)) }
        }
        assertFalse(published)
    }
    @Test fun segmentBoundariesAndLargeStream() {
        val root = root()
        // 1 MiB is ciphertext segment size. Also test actual first plaintext boundary:
        // AES256 header is 40 bytes and GCM tag is 16 bytes, as specified by Tink.
        val segment = FileStreamingCipher.SEGMENT_BYTES.toLong()
        listOf(0L, 1L, segment - 57, segment - 56, segment - 55,
            segment - 1, segment, segment + 1, 3 * segment + 317, 32 * segment + 17).forEach { length ->
            val file = File(root, UUID.randomUUID().toString()); encrypt(file, length); validate(file, length)
        }
    }
    @Test fun bitflipTruncationAppendSwapAndDuplicationNeverPublish() {
        val root = root(); val original = File(root, UUID.randomUUID().toString())
        val segment = FileStreamingCipher.SEGMENT_BYTES
        val length = 3L * segment + 317
        encrypt(original, length)
        val bytes = original.readBytes()
        val mutations = mutableListOf<ByteArray>()
        listOf(0, 60, segment + 53, bytes.lastIndex - 7).forEach { index ->
            mutations.add(bytes.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() })
        }
        listOf(1, 70, bytes.size - 1, bytes.size / segment * segment).forEach { cut -> mutations.add(bytes.copyOf(cut)) }
        mutations.add(bytes + byteArrayOf(99))
        mutations.add(bytes.copyOf().also {
            bytes.copyInto(it, segment, 2 * segment, 3 * segment)
            bytes.copyInto(it, 2 * segment, segment, 2 * segment)
        })
        mutations.add(bytes.copyOf().also { bytes.copyInto(it, 2 * segment, segment, 2 * segment) })
        mutations.forEach { mutation ->
            val file = File(root, UUID.randomUUID().toString()); file.writeBytes(mutation); reject(file, length)
        }
    }
    @Test fun wrongKeyAadAndAssetSwapRejected() {
        val file = File(root(), UUID.randomUUID().toString()); val length = 2L * FileStreamingCipher.SEGMENT_BYTES
        encrypt(file, length)
        reject(file, length, UUID.randomUUID().toString().toByteArray())
        val other = ByteArray(32).also { SecureRandom().nextBytes(it) }
        streams(other).use {
            try { it.validate({ FileInputStream(file) }, aad, length); fail("B3-WRONG-KEY") }
            catch (error: B3Exception) { assertEquals(B3Id.INTEGRITY, error.id) }
        }
        other.fill(0)
    }
    @Test fun partialPlaintextIsPossibleBeforeLateIntegrityFailure() {
        val file = File(root(), UUID.randomUUID().toString()); val length = 3L * FileStreamingCipher.SEGMENT_BYTES
        encrypt(file, length)
        val bytes = file.readBytes(); bytes[bytes.lastIndex - 4] = (bytes[bytes.lastIndex - 4].toInt() xor 1).toByte(); file.writeBytes(bytes)
        var early = 0L
        FileStreamingCipher.fromUnwrappedKey(key).use { cipher ->
            cipher.decrypt(FileInputStream(file), aad).use { input ->
                val buffer = ByteArray(32768)
                try { while (true) { val n = input.read(buffer); if (n == -1) fail("B3-LATE-ERROR-MISSING"); early += n } }
                catch (_: IOException) { assertTrue(early > 0); assertTrue(early < length) }
                finally { buffer.fill(0) }
            }
        }
        reject(file, length)
    }
    private fun operation() = object : SyntheticOperation {
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() {}
        override fun useUnwrappedKey(allowed: () -> Boolean, consume: (ByteArray) -> Unit): OperationResult {
            if (!allowed()) return OperationResult.NOT_AUTHORIZED
            val copy = key.copyOf()
            try { consume(copy); return if (allowed()) OperationResult.SUCCEEDED else OperationResult.NOT_AUTHORIZED }
            catch (_: Exception) { return OperationResult.FAILED }
            finally { copy.fill(0) }
        }
    }
    private fun inventory(): B3Inventory {
        var state = BootstrapState.EMPTY
        val root = root()
        return B3Inventory(root) { state }.also { it.reserve(); File(root, "k").mkdir(); state = BootstrapState.COMPLETE }
    }
    @Test fun closeReopenSameEncryptedAssetWithoutMutation() {
        val inventory = inventory()
        assertEquals(B3Id.PUBLISH, B3StorageGate(inventory, length = 2L * FileStreamingCipher.SEGMENT_BYTES + 7).run(context, operation(), true))
        val before = inventory.root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(inventory.root).path to it.readBytes().toList() }
        assertEquals(B3State.COMPLETE_CANDIDATE, inventory.state())
        assertEquals(B3Id.RESTART, B3StorageGate(inventory).run(context, operation(), false))
        val after = inventory.root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(inventory.root).path to it.readBytes().toList() }
        assertTrue("B3-NO-MUTATION", before == after)
    }
    @Test fun revocationAtEveryBoundaryLeavesUnpublishedStaging() {
        B3Point.entries.filter { it != B3Point.READ }.forEach { point ->
            allowed = true; val inventory = inventory()
            assertEquals(point.name, B3Id.EXPIRED,
                B3StorageGate(inventory, { if (it == point) allowed = false }, 1048701).run(context, operation(), true))
            assertFalse(inventory.manifest.exists())
        }
    }
    @Test fun oldOwnerCannotDeliverBytesAfterRevocation() {
        val file = File(root(), UUID.randomUUID().toString()); val length = 1048701L
        encrypt(file, length)
        streams().use { stream ->
            val evidence = stream.validate({ FileInputStream(file) }, aad, length)
            allowed = false
            try { stream.read(evidence) { _, _ -> fail("B3-RELEASED-PLAINTEXT") }; fail("B3-OLD-OWNER") }
            catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
        }
    }
    @Test fun missingCiphertextOrUnknownFileNeverCreatesReplacement() {
        val inventory = inventory(); assertEquals(B3Id.PUBLISH, B3StorageGate(inventory, length = 1).run(context, operation(), true))
        File(inventory.root, "unknown").writeBytes(byteArrayOf(23))
        assertEquals(B3State.BLOCKED, inventory.state())
        assertEquals(B3Id.BLOCKED, B3StorageGate(inventory).run(context, operation(), false))
        assertTrue(File(inventory.root, "unknown").readBytes().contentEquals(byteArrayOf(23)))
        val missing = inventory() // Current attempt has key state but no asset; no automatic recreate.
        assertEquals(B3Id.BLOCKED, B3StorageGate(missing).run(context, operation(), false))
        assertFalse(missing.assets.exists())
    }
    @Test fun fileAliasRequiresFreshOperationAndCannotBeReplaced() {
        assumeTrue("B3-DEVICE-CREDENTIAL", app.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val root = root(); val alias = "pag.prototype.b3.test.${UUID.randomUUID()}"
        val fixture = RsaWrappingFixture(File(root, "k"), alias)
        fixture.initializeNew { true }
        val prepared = fixture.prepare()
        assertTrue(prepared.properties.contains("DEVICE_CREDENTIAL; PER_OPERATION"))
        val op = prepared.operation(prepared.cipher)
        assertEquals(OperationResult.NOT_AUTHORIZED, op.useUnwrappedKey({ true }) { fail("B3-NO-AUTO-UNWRAP") })
        op.release()
        val foreign = RsaWrappingFixture(fixture.directory, "$alias.other")
        assertEquals(BootstrapState.BLOCKED, foreign.state())
        try { foreign.initializeNew { true }; fail("B3-REPLACEMENT") } catch (_: WrappingException) {}
        assertEquals(BootstrapState.COMPLETE, fixture.state())
    }

    @After fun persistentMarkerScan() {
        key.fill(0)
        // Streaming scan with overlap; no whole large file is loaded for the inventory scan.
        val text = String(B3Source.marker, Charsets.US_ASCII)
        val patterns = listOf(Charsets.UTF_8, Charsets.US_ASCII, Charsets.UTF_16LE, Charsets.UTF_16BE).map { text.toByteArray(it) }
        val roots = listOf(app.filesDir, app.cacheDir, app.noBackupFilesDir, app.codeCacheDir,
            app.getDatabasePath("unused").parentFile!!)
        roots.filter { it.exists() }.forEach { directory ->
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                val overlap = patterns.maxOf { it.size } - 1
                val buffer = ByteArray(65536 + overlap)
                FileInputStream(file).use { input ->
                    var retained = 0
                    while (true) {
                        val count = input.read(buffer, retained, 65536)
                        if (count == -1) break
                        val size = retained + count
                        patterns.forEach { pattern ->
                            for (offset in 0..size - pattern.size) if (buffer[offset] == pattern[0]) {
                                var same = true
                                for (i in 1 until pattern.size) if (buffer[offset + i] != pattern[i]) { same = false; break }
                                assertFalse("B3-PERSISTENT-MARKER", same)
                            }
                        }
                        retained = minOf(overlap, size)
                        buffer.copyInto(buffer, 0, size - retained, size)
                    }
                }
                buffer.fill(0)
            }
        }
    }
}
