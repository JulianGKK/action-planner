package org.pag.security

enum class KeyExperiment { PER_OPERATION, TIME_WINDOW }
enum class SecurityFailure { CANCELLED, UNAVAILABLE, INVALID_KEY, NOT_AUTHORIZED, PLATFORM_ERROR }
fun interface Cancellation { fun cancel() }

/** Operation handles are kept below UI. No key bytes cross this interface. */
interface SyntheticOperation {
    val properties: String get() = "SYNTHETIC_FAKE"
    fun perform(): OperationResult
    fun release()
}
enum class OperationResult { SUCCEEDED, NOT_AUTHORIZED, FAILED, CONSUMED }
sealed interface AuthenticationOutcome {
    data class Granted(val operation: SyntheticOperation, val authenticatedAtMillis: Long) : AuthenticationOutcome
    data class Rejected(val reason: SecurityFailure) : AuthenticationOutcome
}
interface AuthenticationExperiment {
    fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation
}
