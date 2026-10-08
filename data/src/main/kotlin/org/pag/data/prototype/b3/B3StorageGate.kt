package org.pag.data.prototype.b3

import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import org.pag.security.FileStreamingCipher
import org.pag.security.OperationResult
import org.pag.security.SyntheticOperation
import java.io.*
import java.util.UUID

/** Synthetic source is generated in RAM. No source file, hash, original name or MIME data. */
class B3Source(private val length: Long) : InputStream() {
    init { require(length in 0..B3StorageGate.MAX_SYNTHETIC_LENGTH) }
    private var position = 0L
    private val pattern = marker
    override fun read(): Int = if (position == length) -1 else pattern[(position++ % pattern.size).toInt()].toInt() and 255
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (count == 0) return 0
        if (position == length) return -1
        val n = minOf(count.toLong(), length - position).toInt()
        for (index in 0 until n) buffer[offset + index] = pattern[((position + index) % pattern.size).toInt()]
        position += n
        return n
    }
    companion object {
        val marker: ByteArray get() = "PAG_B3_SYNTHETIC_STREAM_ONLY".toByteArray(Charsets.US_ASCII)
    }
}

/** Single synthetic asset; no product repository, import recovery, deletion or final key hierarchy. */
class B3StorageGate(private val inventory: B3Inventory,
    private val hook: (B3Point) -> Unit = {}, private val length: Long = 8L * 1024 * 1024 + 17) {
    init { require(length in 0..MAX_SYNTHETIC_LENGTH) }
    fun run(context: StorageContext, operation: SyntheticOperation, initialize: Boolean,
        progress: (B3Id) -> Unit = {}): B3Id {
        var result = B3Id.BLOCKED
        var failure: B3Id? = null
        try {
            hook(B3Point.BEFORE_UNWRAP); context.requireB3(AccessPoint.OPERATION)
            val outcome = operation.useUnwrappedKey({ context.check(AccessPoint.STEP) }) { key ->
                try {
                    hook(B3Point.AFTER_UNWRAP); context.requireB3(); progress(B3Id.UNWRAP)
                    B3Streams(FileStreamingCipher.fromUnwrappedKey(key), context, hook).use { streams ->
                        val asset = if (initialize) {
                            inventory.keyReady()
                            val file = inventory.staging()
                            val asset = Asset(UUID.randomUUID().toString(), file.name, length)
                            SyncedOutput(file).use { output ->
                                streams.encrypt(B3Source(length), output, asset.aad())
                            }
                            context.requireB3(); progress(B3Id.ENCRYPT)
                            val evidence = streams.validate({ FileInputStream(file) }, asset.aad(), length)
                            progress(B3Id.VALIDATE)
                            streams.publish(evidence) {
                                // Exclusive encrypted manifest; standard Tink ciphertext, not a custom AEAD container.
                                val bytes = asset.encode()
                                try {
                                    context.requireB3(AccessPoint.COMMIT)
                                    if (!inventory.manifest.createNewFile()) throw B3Exception(B3Id.BLOCKED)
                                    SyncedOutput(inventory.manifest).use { output ->
                                        streams.encrypt(ByteArrayInputStream(bytes), output, MANIFEST_AAD)
                                    }
                                } finally { bytes.fill(0) }
                            }
                            asset
                        } else {
                            if (inventory.state() != B3State.COMPLETE_CANDIDATE) throw B3Exception(B3Id.BLOCKED)
                            readManifest(streams)
                        }
                        val file = inventory.file(asset.file)
                        // Also validate persisted manifest and re-read the complete asset before success.
                        if (readManifest(streams) != asset) throw B3Exception(B3Id.INTEGRITY)
                        val verified = streams.validate({ FileInputStream(file) }, asset.aad(), asset.length)
                        progress(B3Id.VALIDATE)
                        var offset = 0L
                        val marker = B3Source.marker
                        streams.read(verified) { bytes, count ->
                            // Bounded synthetic comparison only; no retaining/long-running consumer.
                            for (i in 0 until count) if (bytes[i] != marker[((offset + i) % marker.size).toInt()])
                                throw B3Exception(B3Id.INTEGRITY)
                            offset += count
                        }
                        context.requireB3(AccessPoint.PUBLICATION)
                        result = if (initialize) B3Id.PUBLISH else B3Id.RESTART
                    }
                } catch (error: B3Exception) { failure = error.id; throw error }
                catch (_: Exception) { failure = B3Id.BLOCKED; throw B3Exception(B3Id.BLOCKED) }
            }
            context.requireB3()
            return failure ?: if (outcome == OperationResult.SUCCEEDED) result else B3Id.BLOCKED
        } catch (error: B3Exception) { return error.id }
        catch (_: Exception) { return B3Id.BLOCKED }
        finally { operation.release() }
    }

    private data class Asset(val id: String, val file: String, val length: Long) {
        fun aad() = id.toByteArray(Charsets.US_ASCII)
        fun encode(): ByteArray = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { it.writeInt(1); it.writeUTF(id); it.writeUTF(file); it.writeLong(length); it.writeByte(3) }
        }.toByteArray()
    }
    private fun readManifest(streams: B3Streams): Asset {
        val verified = streams.validate({ FileInputStream(inventory.manifest) }, MANIFEST_AAD, 89)
        val bytes = ByteArray(89)
        var offset = 0
        try {
            streams.read(verified) { chunk, count ->
                chunk.copyInto(bytes, offset, 0, count); offset += count
            }
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != 1) throw B3Exception(B3Id.INTEGRITY)
                val asset = Asset(input.readUTF(), input.readUTF(), input.readLong())
                if (input.readUnsignedByte() != 3 || input.read() != -1 || !B3Inventory.opaque(asset.id) ||
                    !B3Inventory.opaque(asset.file) || asset.length < 0 || asset.length > MAX_SYNTHETIC_LENGTH)
                    throw B3Exception(B3Id.INTEGRITY)
                asset
            }
        } finally { bytes.fill(0) }
    }
    companion object {
        const val MAX_SYNTHETIC_LENGTH = 64L * 1024 * 1024
        // Public opaque synthetic manifest identity; contains no business metadata.
        private val MANIFEST_AAD = "00000000-0000-0000-0000-000000000003".toByteArray(Charsets.US_ASCII)
    }
}

/** Tink closes its destination after writing the last segment. Sync before that close;
 * this is not a claim of filesystem/Keystore/global atomicity or power-loss durability.
 */
private class SyncedOutput(file: File) : FileOutputStream(file) {
    private var finished = false
    override fun close() {
        if (finished) return
        finished = true
        try { fd.sync() } finally { super.close() }
    }
}
