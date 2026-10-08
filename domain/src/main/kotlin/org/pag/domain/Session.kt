package org.pag.domain

enum class SessionState { LOCKED, AUTHENTICATING, MAINTENANCE, READY, BLOCKED }
enum class Experiment { PER_OPERATION, TIME_WINDOW, STORAGE_INITIALIZE, STORAGE_UNWRAP, DATABASE_INITIALIZE, DATABASE_REOPEN }
enum class Observation { NOT_TESTED, SUCCEEDED, DENIED, FAILED, CONSUMED }

data class SessionSnapshot(
    val state: SessionState = SessionState.LOCKED,
    val generation: Long = 0,
    val experiment: Experiment? = null,
    val platformAuthorization: Observation = Observation.NOT_TESTED,
    val keyOperation: Observation = Observation.NOT_TESTED,
    val technicalId: String = "A-START",
    val marker: String? = null,
    val keyProperties: String = "NOT_TESTED"
)

fun interface Subscription { fun cancel() }

/** UI receives only this port. There is no separate freely writable unlock flag. */
interface SessionPort {
    fun snapshot(): SessionSnapshot
    fun observe(observer: (SessionSnapshot) -> Unit): Subscription
    fun authenticate(experiment: Experiment)
    fun testAccess()
    fun userInteraction()
    fun lock()
}

/** Only the Activity composition/lifecycle boundary receives this additional port. */
interface SessionLifecycle {
    fun foreground(allowed: Boolean)
    fun checkExpiry()
}

fun interface Maintenance {
    fun run(complete: (Boolean) -> Unit): Subscription
}
