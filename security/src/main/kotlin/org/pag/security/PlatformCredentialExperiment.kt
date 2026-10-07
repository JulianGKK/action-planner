package org.pag.security

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import java.security.GeneralSecurityException
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/** Stage A only: standard JCA/Android Keystore synthetic operation, no storage scheme.
 * Two independent aliases; existing keys are verified and never deleted/replaced.
 */
class PlatformCredentialExperiment(
    private val activity: Activity,
    private val canPublishInForeground: () -> Boolean
) : AuthenticationExperiment {
    private var pendingForegroundResult: (() -> Unit)? = null
    private var pendingOperation: SyntheticOperation? = null

    /** Called by the Activity root, never by a Screen/ViewModel. */
    fun foregroundAvailable() {
        if (canPublishInForeground()) {
            val pending = pendingForegroundResult
            pendingForegroundResult = null
            pendingOperation = null
            pending?.invoke()
        }
    }

    override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
        // Never route a B1.5 experiment through a historical Stage-A key configuration.
        if (mode != KeyExperiment.PER_OPERATION && mode != KeyExperiment.TIME_WINDOW) {
            complete(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR))
            return Cancellation { }
        }
        val signal = CancellationSignal()
        var completed = false
        fun finish(outcome: AuthenticationOutcome) {
            if (completed || signal.isCanceled) {
                if (outcome is AuthenticationOutcome.Granted) outcome.operation.release()
                return
            }
            completed = true
            complete(outcome)
        }
        try {
            if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                finish(AuthenticationOutcome.Rejected(SecurityFailure.UNAVAILABLE))
                return Cancellation { signal.cancel() }
            }
            val (key, properties) = syntheticKey(mode)
            val cipher = if (mode == KeyExperiment.PER_OPERATION) newCipher(key) else null
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("PaG Prototyp A")
                .setSubtitle("Nur Gerätecredential · synthetischer Versuch")
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticatedAt = android.os.SystemClock.elapsedRealtime()
                    if (result.authenticationType != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) {
                        finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR))
                        return
                    }
                    // Reject a missing or substituted crypto operation, never silently downgrade.
                    if (cipher != null && result.cryptoObject?.cipher !== cipher) {
                        finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR))
                        return
                    }
                    val operation = KeystoreOperation(key, cipher, mode, properties)
                    if (signal.isCanceled || completed) {
                        operation.release()
                        return
                    }
                    // The dialog can return its result just before Activity focus/resume.
                    // No gate/content release until the matching Activity is foreground.
                    if (canPublishInForeground()) finish(AuthenticationOutcome.Granted(operation, authenticatedAt))
                    else {
                        pendingOperation = operation
                        pendingForegroundResult = { finish(AuthenticationOutcome.Granted(operation, authenticatedAt)) }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                    finish(AuthenticationOutcome.Rejected(
                        if (cancelled) SecurityFailure.CANCELLED else SecurityFailure.PLATFORM_ERROR
                    ))
                }
                // No onAuthenticationFailed counting; credential failures belong to Android.
            }
            if (cipher != null) prompt.authenticate(BiometricPrompt.CryptoObject(cipher), signal, activity.mainExecutor, callback)
            else prompt.authenticate(signal, activity.mainExecutor, callback)
        } catch (_: GeneralSecurityException) {
            finish(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY))
        } catch (_: IOException) {
            finish(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY))
        } catch (_: RuntimeException) {
            finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR))
        }
        return Cancellation {
            pendingForegroundResult = null
            pendingOperation?.release()
            pendingOperation = null
            signal.cancel()
        }
    }

    private fun syntheticKey(mode: KeyExperiment): Pair<SecretKey, String> {
        val alias = "pag.prototype.a.synthetic.${mode.name}.v1"
        val timeout = if (mode == KeyExperiment.PER_OPERATION) 0 else TEST_WINDOW_SECONDS
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // Non-sensitive synthetic inventory only, never a session/auth credential.
        // Distinguish first creation from a previously created alias that disappeared.
        val inventory = activity.getSharedPreferences("stage_a_synthetic_key_inventory", Activity.MODE_PRIVATE)
        val exists = store.containsAlias(alias)
        if (!exists && inventory.getBoolean(alias, false)) throw GeneralSecurityException("A-KEY-MISSING")
        val key = if (exists) {
            store.getKey(alias, null) as? SecretKey ?: throw GeneralSecurityException("A-KEY-TYPE")
        } else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(timeout, KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    .build())
            }.generateKey()
        }
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        val validTimeout = if (mode == KeyExperiment.PER_OPERATION) {
            // Builder uses 0; KeyInfo documents -1 for per-operation authorization.
            info.userAuthenticationValidityDurationSeconds in setOf(-1, 0)
        } else info.userAuthenticationValidityDurationSeconds == timeout
        if (!info.isUserAuthenticationRequired || info.userAuthenticationType != KeyProperties.AUTH_DEVICE_CREDENTIAL ||
            !validTimeout || info.keySize != 256 ||
            info.purposes != KeyProperties.PURPOSE_ENCRYPT ||
            info.blockModes.toSet() != setOf(KeyProperties.BLOCK_MODE_GCM) ||
            info.encryptionPaddings.toSet() != setOf(KeyProperties.ENCRYPTION_PADDING_NONE)) {
            throw GeneralSecurityException("A-KEY-POLICY")
        }
        if (!inventory.getBoolean(alias, false) && !inventory.edit().putBoolean(alias, true).commit()) {
            throw GeneralSecurityException("A-INVENTORY-WRITE")
        }
        val backing = if (Build.VERSION.SDK_INT >= 31) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                else -> "UNKNOWN"
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "HARDWARE_API30" else "SOFTWARE_API30"
        }
        return key to "$backing; authHardware=${info.isUserAuthenticationRequirementEnforcedBySecureHardware}"
    }

    private class KeystoreOperation(
        private var key: SecretKey?, private var cipher: Cipher?, private val mode: KeyExperiment,
        override val properties: String
    ) : SyntheticOperation {
        override fun perform(): OperationResult {
            val activeKey = key ?: return OperationResult.CONSUMED
            return try {
                val operation = if (mode == KeyExperiment.PER_OPERATION) {
                    cipher ?: return OperationResult.CONSUMED
                } else newCipher(activeKey)
                // No output, IV or key bytes are displayed, logged or persisted.
                operation.doFinal("PAG_STAGE_A_SYNTHETIC_ONLY".toByteArray(Charsets.UTF_8))
                if (mode == KeyExperiment.PER_OPERATION) cipher = null
                OperationResult.SUCCEEDED
            } catch (_: UserNotAuthenticatedException) {
                release()
                OperationResult.NOT_AUTHORIZED
            } catch (_: GeneralSecurityException) {
                release()
                OperationResult.FAILED
            } catch (_: RuntimeException) {
                release()
                OperationResult.FAILED
            }
        }
        override fun release() { cipher = null; key = null }
    }

    companion object {
        // Experiment parameter, not a product authorization-window decision.
        const val TEST_WINDOW_SECONDS = 15
        private fun newCipher(key: SecretKey): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key) // Provider chooses fresh random IV, never caller-reused.
        }
    }
}
