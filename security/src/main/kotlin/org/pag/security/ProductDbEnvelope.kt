package org.pag.security

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/** Standard RSA-OAEP bytes, X.509 public identity; not a custom cryptographic container. */
class ProductDbEnvelope(private val root: File, private val alias: String) {
    fun aliasPresent(): Boolean = store().containsAlias(alias)
    fun structurallyValid(): Boolean = try {
        privateKey()
        val public = store().getCertificate(alias).publicKey.encoded
        File(root, "envelope").isFile && File(root, "envelope").length() == 256L &&
            File(root, "public").isFile && File(root, "public").length() == public.size.toLong() &&
            java.security.MessageDigest.isEqual(File(root, "public").readBytes(), public)
    } catch (_: Exception) { false }
    /** Only after explicit setup admission. No DB key generated until successful authentication. */
    fun prepareNewRoot() {
        if (aliasPresent() || !root.isDirectory || root.list()?.toSet() != setOf("intent")) error("P1-ENVELOPE-STATE")
        KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_DEVICE_CREDENTIAL).build())
        }.generateKeyPair()
        privateKey()
        writeNew(File(root, "public"), store().getCertificate(alias).publicKey.encoded)
    }
    fun prepare(): Cipher = Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
        init(Cipher.DECRYPT_MODE, privateKey(), parameters())
    }
    fun operation(cipher: Cipher, initializes: Boolean): SyntheticOperation = object : SyntheticOperation {
        private var work: Cipher? = cipher
        override val properties = "RSA2048/OAEP DEVICE_CREDENTIAL PER_OPERATION"
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() { synchronized(this) { work = null } }
        override fun useUnwrappedKey(allowed: () -> Boolean, consume: (ByteArray) -> Unit): OperationResult {
            val admitted = synchronized(this) { work.also { work = null } } ?: return OperationResult.CONSUMED
            var key: ByteArray? = null
            return try {
                if (!allowed()) return OperationResult.NOT_AUTHORIZED
                if (initializes) {
                    val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    try {
                        val encrypt = Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
                            init(Cipher.ENCRYPT_MODE, store().getCertificate(alias).publicKey, parameters())
                        }
                        val wrapped = encrypt.doFinal(generated)
                        if (!allowed()) return OperationResult.NOT_AUTHORIZED
                        writeNew(File(root, "envelope"), wrapped)
                    } finally { generated.fill(0) }
                }
                if (!allowed() || !structurallyValid()) return OperationResult.NOT_AUTHORIZED
                key = admitted.doFinal(File(root, "envelope").readBytes())
                if (key.size != 32 || !allowed()) return OperationResult.NOT_AUTHORIZED
                consume(key)
                if (allowed()) OperationResult.SUCCEEDED else OperationResult.NOT_AUTHORIZED
            } catch (_: Exception) { OperationResult.FAILED }
            finally { key?.fill(0) }
        }
    }
    private fun privateKey(): PrivateKey {
        val key = store().getKey(alias, null) as? PrivateKey ?: error("P1-KEY-MISSING")
        val info = KeyFactory.getInstance("RSA", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)
        check(key.algorithm == "RSA" && key.encoded == null && info.keystoreAlias == alias &&
            info.origin == KeyProperties.ORIGIN_GENERATED && info.keySize == 2048 &&
            info.purposes == KeyProperties.PURPOSE_DECRYPT && info.blockModes.isEmpty() &&
            info.digests.toSet() == setOf(KeyProperties.DIGEST_SHA256) &&
            info.encryptionPaddings.toSet() == setOf(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP) &&
            info.isUserAuthenticationRequired && info.userAuthenticationType == KeyProperties.AUTH_DEVICE_CREDENTIAL &&
            info.userAuthenticationValidityDurationSeconds in setOf(-1, 0))
        return key
    }
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    companion object {
        private fun parameters() = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        private fun writeNew(file: File, bytes: ByteArray) {
            check(!Files.isSymbolicLink(file.toPath()) && file.createNewFile())
            FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
        }
    }
}

/** One freshly prepared cipher, one explicit credential prompt, no authorization window. */
class ProductCredentialAuthentication(private val activity: Activity,
    private val envelope: ProductDbEnvelope, private val worker: Executor,
    private val foreground: () -> Boolean, private val reserve: () -> Unit,
    private val existingValid: () -> Boolean) : AuthenticationExperiment {
    private var pending: (() -> Unit)? = null
    private var pendingOperation: SyntheticOperation? = null
    fun foregroundAvailable() { if (foreground()) { val p = pending; pending = null; pendingOperation = null; p?.invoke() } }
    override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
        val initialize = mode == KeyExperiment.STORAGE_INITIALIZE
        val cancelled = AtomicBoolean(false)
        val signal = CancellationSignal()
        var finished = false
        fun finish(outcome: AuthenticationOutcome) {
            if (cancelled.get() || finished) {
                if (outcome is AuthenticationOutcome.Granted) outcome.operation.release()
            } else {
                finished = true
                // A cancelled first setup may have reserved persistent state. Never offer repair.
                complete(if (initialize && outcome is AuthenticationOutcome.Rejected && outcome.reason == SecurityFailure.CANCELLED)
                    AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY) else outcome)
            }
        }
        if (mode !in setOf(KeyExperiment.STORAGE_INITIALIZE, KeyExperiment.STORAGE_UNWRAP) ||
            !activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            finish(AuthenticationOutcome.Rejected(SecurityFailure.UNAVAILABLE)); return Cancellation {}
        }
        worker.execute {
            val cipher = try {
                if (cancelled.get()) return@execute
                if (initialize) { reserve(); if (cancelled.get()) return@execute; envelope.prepareNewRoot() }
                else check(existingValid())
                envelope.prepare()
            } catch (_: Exception) {
                activity.mainExecutor.execute { finish(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY)) }
                return@execute
            }
            activity.mainExecutor.execute {
                if (cancelled.get()) return@execute
                if (!foreground()) { finish(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED)); return@execute }
                try {
                    BiometricPrompt.Builder(activity).setTitle("PaG entsperren")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
                        .authenticate(BiometricPrompt.CryptoObject(cipher), signal, activity.mainExecutor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    val authenticatedAt = SystemClock.elapsedRealtime()
                                    if (cancelled.get() || finished) return
                                    if (result.authenticationType != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL ||
                                        result.cryptoObject?.cipher !== cipher) {
                                        finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR)); return
                                    }
                                    val operation = envelope.operation(cipher, initialize)
                                    val granted = AuthenticationOutcome.Granted(operation, authenticatedAt)
                                    if (foreground()) finish(granted)
                                    else { pendingOperation = operation; pending = { finish(granted) } }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    finish(AuthenticationOutcome.Rejected(if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) SecurityFailure.CANCELLED else SecurityFailure.PLATFORM_ERROR))
                                }
                            })
                } catch (_: Exception) { finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR)) }
            }
        }
        return Cancellation { cancelled.set(true); pending = null; pendingOperation?.release(); pendingOperation = null; signal.cancel() }
    }
}
