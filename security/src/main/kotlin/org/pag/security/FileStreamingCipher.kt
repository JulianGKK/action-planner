package org.pag.security

import com.google.crypto.tink.AccessesPartialKey
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.util.SecretBytes
import java.io.InputStream
import java.io.OutputStream

/** Crypto boundary only. Data owns session checks, buffers and stream lifecycle.
 * Never serialize this RAM-only handle. Tink owns additional unerasable key copies.
 */
class FileStreamingCipher private constructor(private var primitive: StreamingAead?) : AutoCloseable {
    fun encrypt(destination: OutputStream, aad: ByteArray): OutputStream =
        requireNotNull(primitive).newEncryptingStream(destination, aad)
    fun decrypt(source: InputStream, aad: ByteArray): InputStream =
        requireNotNull(primitive).newDecryptingStream(source, aad)
    override fun close() { primitive = null }

    companion object {
        const val SEGMENT_BYTES = 1_048_576
        /** Explicit key import is necessary for the established per-operation RSA unwrap.
         * Public Tink Key/Parameters APIs; no protos, reflection, keyset persistence or KDF.
         */
        @AccessesPartialKey
        fun fromUnwrappedKey(bytes: ByteArray): FileStreamingCipher {
            require(bytes.size == 32)
            StreamingAeadConfig.register()
            val key = AesGcmHkdfStreamingKey.create(
                PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB,
                SecretBytes.copyFrom(bytes, InsecureSecretKeyAccess.get()))
            val handle = KeysetHandle.newBuilder()
                .addEntry(KeysetHandle.importKey(key).withFixedId(1).makePrimary()).build()
            return FileStreamingCipher(handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java))
        }
    }
}
