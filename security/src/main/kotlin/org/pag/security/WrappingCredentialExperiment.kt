package org.pag.security

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** B1.5 auth adapter. Only per-operation DEVICE_CREDENTIAL; no fallback or auto bootstrap. */
class WrappingCredentialExperiment(
    private val activity: Activity,
    private val fixture: RsaWrappingFixture,
    private val worker: Executor,
    private val foregroundAllowed: () -> Boolean
) : AuthenticationExperiment {
    private var pending: (() -> Unit)? = null
    private var pendingOperation: SyntheticOperation? = null

    fun foregroundAvailable() {
        if (foregroundAllowed()) {
            val result = pending
            pending = null; pendingOperation = null
            result?.invoke()
        }
    }

    override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
        val signal = CancellationSignal()
        val cancelled = AtomicBoolean(false) // Cancellation only, never an unlock/session state.
        var finished = false // Main-thread-owned callback state.
        fun finish(outcome: AuthenticationOutcome) {
            if (cancelled.get() || finished) {
                if (outcome is AuthenticationOutcome.Granted) outcome.operation.release()
                return
            }
            finished = true
            complete(outcome)
        }
        if (mode != KeyExperiment.STORAGE_INITIALIZE && mode != KeyExperiment.STORAGE_UNWRAP) {
            finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR))
            return Cancellation { }
        }
        if (!activity.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            finish(AuthenticationOutcome.Rejected(SecurityFailure.UNAVAILABLE))
            return Cancellation { }
        }
        worker.execute {
            val prepared = try {
                if (cancelled.get()) return@execute
                WrappingPreparation.run(mode, fixture::checkedState,
                    { fixture.initializeNew { !cancelled.get() } },
                    {
                        if (cancelled.get()) throw WrappingException(WrappingFailure.SESSION_INVALID)
                        fixture.prepare()
                    })
            } catch (failure: Exception) {
                val diagnosis = (failure as? WrappingException)?.failure ?: WrappingFailure.INVENTORY
                activity.mainExecutor.execute {
                    finish(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, diagnosis))
                }
                return@execute
            }
            activity.mainExecutor.execute {
                if (cancelled.get()) return@execute
                if (!foregroundAllowed()) {
                    finish(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED)); return@execute
                }
                try {
                    val crypto = wrappingStep(WrappingFailure.CRYPTOOBJECT) {
                        BiometricPrompt.CryptoObject(prepared.cipher)
                    }
                    val prompt = wrappingStep(WrappingFailure.PROMPT_START) { BiometricPrompt.Builder(activity)
                        .setTitle("PaG Prototyp B1.5")
                        .setSubtitle("DEVICE_CREDENTIAL · operationsgebundenes RSA-OAEP")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build() }
                    wrappingStep(WrappingFailure.PROMPT_START) {
                    prompt.authenticate(crypto, signal, activity.mainExecutor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                val authenticatedAt = SystemClock.elapsedRealtime()
                                if (cancelled.get() || finished) return
                                if (result.authenticationType != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL ||
                                    result.cryptoObject?.cipher !== prepared.cipher) {
                                    finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR, WrappingFailure.CRYPTOOBJECT)); return
                                }
                                val operation = try { prepared.operation(result.cryptoObject!!.cipher!!) }
                                catch (_: Exception) {
                                    finish(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, WrappingFailure.CRYPTOOBJECT))
                                    return
                                }
                                val granted = AuthenticationOutcome.Granted(operation, authenticatedAt)
                                // No decrypt in a callback. Coordinator checks generation/deadline first;
                                // actual unwrap is an explicit guarded synthetic test-access operation.
                                if (foregroundAllowed()) finish(granted)
                                else {
                                    pendingOperation = operation
                                    pending = { finish(granted) }
                                }
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                val reason = if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)
                                    SecurityFailure.CANCELLED else SecurityFailure.PLATFORM_ERROR
                                finish(AuthenticationOutcome.Rejected(reason, WrappingFailure.PROMPT_REJECTED))
                            }
                        })
                    }
                } catch (failure: Exception) {
                    val diagnosis = (failure as? WrappingException)?.failure ?: WrappingFailure.PROMPT_START
                    finish(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR, diagnosis))
                }
            }
        }
        return Cancellation {
            cancelled.set(true)
            pending = null; pendingOperation?.release(); pendingOperation = null
            signal.cancel()
        }
    }
}
