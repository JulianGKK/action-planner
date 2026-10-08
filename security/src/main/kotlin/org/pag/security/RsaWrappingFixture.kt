package org.pag.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/** B1.5 only. Standard RSA-OAEP ciphertext; no custom crypto/container/KDF or fallback. */
class RsaWrappingFixture(val directory: File, private val alias: String) {
    private val backend = object : BootstrapKeyBackend {
        override fun aliasPresent() = store().containsAlias(alias)
        override fun publicIdentity(): ByteArray = wrappingStep(WrappingFailure.KEY_POLICY) {
            verifiedKey()
            store().getCertificate(alias)?.publicKey?.encoded ?: fail(WrappingFailure.KEY_POLICY)
        }
        override fun createWrappedKey(): ByteArray = sanitized {
            if (aliasPresent()) fail(WrappingFailure.INVENTORY)
            val pair = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    .build())
            }.generateKeyPair()
            verifiedKey()
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            try {
                Cipher.getInstance("RSA/ECB/OAEPPadding").run {
                    init(Cipher.ENCRYPT_MODE, pair.public, parameters())
                    doFinal(bytes)
                }
            } finally { bytes.fill(0) }
        }
    }
    private val inventory = WrappingInventory(directory, backend)
    fun state() = inventory.state()
    fun checkedState() = inventory.checkedState()
    fun initializeNew(allowed: () -> Boolean) = inventory.initializeNew(allowed)

    fun prepare(): Prepared = sanitized {
        val wrapped = wrappingStep(WrappingFailure.WRAPPED_READ) { inventory.readWrapped() }
        val (key, properties) = wrappingStep(WrappingFailure.KEY_POLICY) { verifiedKey() }
        // JCA selects the Keystore cipher for this non-exportable private key at init.
        val cipher = wrappingStep(WrappingFailure.CIPHER_LOOKUP) { Cipher.getInstance("RSA/ECB/OAEPPadding") }
        wrappingStep(WrappingFailure.CIPHER_INIT) { cipher.init(Cipher.DECRYPT_MODE, key, parameters()) }
        Prepared(cipher, wrapped, properties)
    }

    class Prepared internal constructor(val cipher: Cipher, private val wrapped: ByteArray, val properties: String) {
        private val claimed = AtomicBoolean(false)
        fun operation(authenticatedCipher: Cipher): SyntheticOperation {
            if (authenticatedCipher !== cipher || !claimed.compareAndSet(false, true)) fail(WrappingFailure.WRONG_CIPHER)
            return object : SyntheticOperation {
                private var active: Cipher? = cipher
                override val properties = this@Prepared.properties
                override var diagnostic: WrappingFailure? = null
                    private set
                override fun perform() = OperationResult.NOT_AUTHORIZED
                override fun performAuthorized(allowed: () -> Boolean): OperationResult = useUnwrappedKey(allowed) { }
                override fun useUnwrappedKey(allowed: () -> Boolean, consume: (ByteArray) -> Unit): OperationResult {
                    val work = synchronized(this) { active.also { active = null } } ?: return OperationResult.CONSUMED
                    var decoded: ByteArray? = null
                    return try {
                        if (!allowed()) return OperationResult.NOT_AUTHORIZED
                        diagnostic = WrappingFailure.UNWRAP
                        decoded = work.doFinal(wrapped)
                        if (!allowed()) return OperationResult.NOT_AUTHORIZED
                        if (decoded.size != 32) return OperationResult.FAILED
                        consume(decoded)
                        if (!allowed()) return OperationResult.NOT_AUTHORIZED
                        diagnostic = null
                        OperationResult.SUCCEEDED
                    } catch (_: UserNotAuthenticatedException) { diagnostic = WrappingFailure.AUTH_REQUIRED; OperationResult.NOT_AUTHORIZED }
                    catch (failure: GeneralSecurityException) {
                        if (authenticationRequired(failure)) { diagnostic = WrappingFailure.AUTH_REQUIRED; OperationResult.NOT_AUTHORIZED } else OperationResult.FAILED
                    }
                    catch (_: RuntimeException) { OperationResult.FAILED }
                    finally { decoded?.fill(0) }
                }
                override fun release() { synchronized(this) { active = null } }
            }
        }
    }

    private fun verifiedKey(): Pair<PrivateKey, String> {
        val key = store().getKey(alias, null) as? PrivateKey ?: fail(WrappingFailure.KEY_POLICY)
        val info = KeyFactory.getInstance("RSA", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
        if (info.keystoreAlias != alias || key.algorithm != "RSA" || key.encoded != null ||
            info.origin != KeyProperties.ORIGIN_GENERATED || info.keySize != 2048 ||
            info.purposes != KeyProperties.PURPOSE_DECRYPT || info.blockModes.isNotEmpty() ||
            info.digests.toSet() != setOf(KeyProperties.DIGEST_SHA256) ||
            info.encryptionPaddings.toSet() != setOf(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP) ||
            !info.isUserAuthenticationRequired || info.userAuthenticationType != KeyProperties.AUTH_DEVICE_CREDENTIAL ||
            info.userAuthenticationValidityDurationSeconds !in setOf(-1, 0)) fail(WrappingFailure.KEY_POLICY)
        val backing = if (Build.VERSION.SDK_INT >= 31) when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
            else -> "UNKNOWN"
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "HARDWARE_API30" else "SOFTWARE_API30"
        }
        return key to "$backing; authHardware=${info.isUserAuthenticationRequirementEnforcedBySecureHardware}; RSA2048/OAEP; DEVICE_CREDENTIAL; PER_OPERATION; KEYSTORE_ALIAS_PRESENT; WRAPPED_KEY_PRESENT"
    }
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    companion object {
        private fun authenticationRequired(failure: Throwable): Boolean {
            var cause: Throwable? = failure
            repeat(8) {
                val current = cause ?: return false
                if (current is UserNotAuthenticatedException) return true
                if (Build.VERSION.SDK_INT >= 33 && current is android.security.KeyStoreException &&
                    current.requiresUserAuthentication()) return true
                cause = current.cause
            }
            return false
        }
        // Explicit API-30-compatible OAEP parameters; no provider-default substitution.
        fun parameters() = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        private fun fail(reason: WrappingFailure): Nothing = throw WrappingException(reason)
        private inline fun <T> sanitized(work: () -> T): T = try { work() }
        catch (failure: WrappingException) { throw failure }
        catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) { fail(WrappingFailure.KEY_INVALIDATED) }
        catch (_: UserNotAuthenticatedException) { fail(WrappingFailure.AUTH_REQUIRED) }
        catch (_: GeneralSecurityException) { fail(WrappingFailure.UNWRAP) }
        catch (_: IOException) { fail(WrappingFailure.IO) }
        catch (_: RuntimeException) { fail(WrappingFailure.KEY_POLICY) }
    }
}
