package org.pag.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.crypto.tink.subtle.AesGcmJce
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import android.security.keystore.KeyInfo
import javax.crypto.spec.GCMParameterSpec

class LeadershipCryptoFailure : RuntimeException("SENSITIVE-CRYPTO-DENIED")
data class LeadershipKdfParameters(val algorithm: String = "Argon2id", val version: Int = 19,
    val memoryKiB: Int = 65536, val iterations: Int = 3, val parallelism: Int = 1) {
    fun validate() { if (this != LeadershipKdfParameters()) throw LeadershipCryptoFailure() }
}
/** Established primitives only. No provider replacement, password verifier, or key registry. */
object LeadershipCrypto {
    fun random(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun derive(password: CharArray, salt: ByteArray, params: LeadershipKdfParameters): ByteArray {
        params.validate()
        if (salt.size != 16) throw LeadershipCryptoFailure()
        val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = ByteBuffer.allocate((password.size * encoder.maxBytesPerChar()).toInt() + 4)
        val output = ByteArray(32)
        var input: ByteArray? = null
        val config = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(params.version)
            .withMemoryAsKB(params.memoryKiB).withIterations(params.iterations).withParallelism(params.parallelism).withSalt(salt).build()
        try {
            encoder.encode(CharBuffer.wrap(password), encoded, true).throwExceptionIfError()
            encoder.flush(encoded).throwExceptionIfError()
            input = encoded.array().copyOf(encoded.position())
            Argon2BytesGenerator().apply { init(config) }.generateBytes(input, output)
            return output
        } catch (_: Exception) { output.fill(0); throw LeadershipCryptoFailure() }
        finally { input?.fill(0); encoded.array().fill(0); config.clear() }
    }
    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray = AesGcmJce(key).encrypt(plaintext, aad)
    fun decrypt(key: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray = AesGcmJce(key).decrypt(ciphertext, aad)
    private fun java.nio.charset.CoderResult.throwExceptionIfError() { if (isError) throwException() }
}
/** Device binding, explicitly NOT a second auth factor. No automatic key replacement. */
interface LeadershipDeviceKey {
    fun present(alias: String): Boolean
    fun create(alias: String)
    fun usable(alias: String): Boolean = present(alias)
    fun encrypt(alias: String, bytes: ByteArray, aad: ByteArray): ByteArray
    fun decrypt(alias: String, bytes: ByteArray, aad: ByteArray): ByteArray
    fun delete(alias: String)
}
class AndroidLeadershipDeviceKey : LeadershipDeviceKey {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun present(alias: String) = store().containsAlias(alias)
    override fun create(alias: String) {
        if (present(alias)) throw LeadershipCryptoFailure()
        KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build())
        }.generateKey()
    }
    private fun key(alias: String): SecretKey {
        val k = store().getKey(alias,null) as? SecretKey ?: throw LeadershipCryptoFailure()
        if (k.encoded != null || k.algorithm != "AES") throw LeadershipCryptoFailure()
        val info=SecretKeyFactory.getInstance("AES","AndroidKeyStore").getKeySpec(k,KeyInfo::class.java) as KeyInfo
        if(info.keySize!=256 || info.isUserAuthenticationRequired ||
            info.origin!=KeyProperties.ORIGIN_GENERATED ||
            info.purposes!=(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT) ||
            info.blockModes.toSet()!=setOf(KeyProperties.BLOCK_MODE_GCM) ||
            info.encryptionPaddings.toSet()!=setOf(KeyProperties.ENCRYPTION_PADDING_NONE))throw LeadershipCryptoFailure()
        return k
    }
    override fun usable(alias:String)=try {key(alias);true}catch(_:Exception){false}
    override fun encrypt(alias: String, bytes: ByteArray, aad: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key(alias)); updateAAD(aad)
        if (iv.size != 12) throw LeadershipCryptoFailure()
        iv + doFinal(bytes)
    }
    override fun decrypt(alias: String, bytes: ByteArray, aad: ByteArray): ByteArray {
        if (bytes.size < 28) throw LeadershipCryptoFailure()
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(alias),GCMParameterSpec(128,bytes,0,12)); updateAAD(aad)
            doFinal(bytes,12,bytes.size-12)
        }
    }
    override fun delete(alias: String) { store().deleteEntry(alias) }
}
