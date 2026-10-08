package org.pag.security

enum class KeyExperiment { PER_OPERATION, TIME_WINDOW, STORAGE_INITIALIZE, STORAGE_UNWRAP, DATABASE_INITIALIZE, DATABASE_REOPEN }
enum class SecurityFailure { CANCELLED, UNAVAILABLE, INVALID_KEY, NOT_AUTHORIZED, PLATFORM_ERROR }
fun interface Cancellation { fun cancel() }

/** Operation handles are kept below UI. No key bytes cross this interface. */
interface SyntheticOperation {
    val properties: String get() = "SYNTHETIC_FAKE"
    val diagnostic: WrappingFailure? get() = null
    fun perform(): OperationResult
    /** Each relevant native step rechecks the central revocable context; no I/O in its monitor. */
    fun performAuthorized(allowed: () -> Boolean): OperationResult =
        if (allowed()) perform() else OperationResult.NOT_AUTHORIZED
    /** Synchronous, non-retaining consumer; implementation wipes its owned plaintext afterwards. */
    fun useUnwrappedKey(allowed: () -> Boolean, consume: (ByteArray) -> Unit): OperationResult = OperationResult.NOT_AUTHORIZED
    fun release()
}
enum class OperationResult { SUCCEEDED, NOT_AUTHORIZED, FAILED, CONSUMED }
sealed interface AuthenticationOutcome {
    data class Granted(val operation: SyntheticOperation, val authenticatedAtMillis: Long) : AuthenticationOutcome
    data class Rejected(val reason: SecurityFailure, val diagnostic: WrappingFailure? = null) : AuthenticationOutcome
}
interface AuthenticationExperiment {
    fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation
}
