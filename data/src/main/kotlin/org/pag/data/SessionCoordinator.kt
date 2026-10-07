package org.pag.data

import org.pag.domain.Experiment
import org.pag.domain.Maintenance
import org.pag.domain.Observation
import org.pag.domain.SessionLifecycle
import org.pag.domain.SessionPort
import org.pag.domain.SessionSnapshot
import org.pag.domain.SessionState
import org.pag.domain.Subscription
import org.pag.security.AuthenticationExperiment
import org.pag.security.AuthenticationOutcome
import org.pag.security.Cancellation
import org.pag.security.KeyExperiment
import org.pag.security.OperationResult
import org.pag.security.SecurityFailure
import org.pag.security.SyntheticOperation

/** One synchronization boundary covers generation checks, access and publication.
 * Observers are synchronous; the Android root dispatches every event on main.
 * No pending observer emission may later resurrect a previously published marker.
 */
class SessionCoordinator(
    private val authentication: AuthenticationExperiment,
    private val maintenance: Maintenance,
    private val accessEnvironmentAllowed: () -> Boolean = { true }
) : SessionPort, SessionLifecycle {
    private var current = SessionSnapshot()
    private var foregroundAllowed = false
    private val observers = mutableSetOf<(SessionSnapshot) -> Unit>()
    private var authCancellation: Cancellation? = null
    private var maintenanceCancellation: Subscription? = null
    private var operation: SyntheticOperation? = null

    @Synchronized override fun snapshot() = current

    @Synchronized override fun observe(observer: (SessionSnapshot) -> Unit): Subscription {
        observers.add(observer)
        observer(current)
        return Subscription { synchronized(this) { observers.remove(observer) } }
    }

    @Synchronized override fun foreground(allowed: Boolean) {
        foregroundAllowed = allowed
        if (!allowed) invalidate("A-LIFECYCLE")
    }

    @Synchronized override fun lock() = invalidate("A-LOCK")

    private fun invalidate(id: String, state: SessionState = SessionState.LOCKED,
        observation: Observation = Observation.NOT_TESTED) {
        // Invalidate first: cancellation can synchronously invoke old callbacks.
        val oldAuth = authCancellation
        val oldMaintenance = maintenanceCancellation
        val oldOperation = operation
        authCancellation = null
        maintenanceCancellation = null
        operation = null
        current = SessionSnapshot(state = state, generation = current.generation + 1,
            technicalId = id, platformAuthorization = observation, keyOperation = observation)
        oldAuth?.cancel()
        oldMaintenance?.cancel()
        oldOperation?.release()
        publish(current)
    }

    @Synchronized override fun authenticate(experiment: Experiment) {
        if (!foregroundAllowed || !accessEnvironmentAllowed() || current.state != SessionState.LOCKED) return
        val generation = current.generation + 1
        publish(SessionSnapshot(SessionState.AUTHENTICATING, generation, experiment, technicalId = "A-AUTH"))
        val mode = when (experiment) {
            Experiment.PER_OPERATION -> KeyExperiment.PER_OPERATION
            Experiment.TIME_WINDOW -> KeyExperiment.TIME_WINDOW
        }
        val cancellation = try {
            authentication.start(mode) { outcome -> authenticationCompleted(generation, outcome) }
        } catch (_: RuntimeException) {
            if (valid(generation, SessionState.AUTHENTICATING)) block("A-AUTH-ADAPTER-FAILED")
            return
        }
        if (valid(generation, SessionState.AUTHENTICATING)) authCancellation = cancellation
        else cancellation.cancel()
    }

    @Synchronized private fun authenticationCompleted(generation: Long, outcome: AuthenticationOutcome) {
        if (!valid(generation, SessionState.AUTHENTICATING)) {
            if (outcome is AuthenticationOutcome.Granted) outcome.operation.release()
            if (generation == current.generation && current.state == SessionState.AUTHENTICATING) invalidate("A-ENVIRONMENT")
            return
        }
        authCancellation = null
        when (outcome) {
            is AuthenticationOutcome.Rejected -> {
                if (outcome.reason == SecurityFailure.CANCELLED) invalidate("A-AUTH-CANCEL")
                else block("A-AUTH-${outcome.reason.name}")
            }
            is AuthenticationOutcome.Granted -> {
                operation = outcome.operation
                // Prompt success is not proof that the later key operation will succeed.
                publish(current.copy(state = SessionState.MAINTENANCE, technicalId = "A-SYNTHETIC-MAINTENANCE",
                    keyProperties = outcome.operation.properties))
                val cancellation = try {
                    maintenance.run { success -> maintenanceCompleted(generation, success) }
                } catch (_: RuntimeException) {
                    if (valid(generation, SessionState.MAINTENANCE)) block("A-MAINTENANCE-FAILED")
                    return
                }
                if (valid(generation, SessionState.MAINTENANCE)) maintenanceCancellation = cancellation
                else cancellation.cancel()
            }
        }
    }

    @Synchronized private fun maintenanceCompleted(generation: Long, success: Boolean) {
        if (!valid(generation, SessionState.MAINTENANCE)) {
            if (generation == current.generation && current.state == SessionState.MAINTENANCE) invalidate("A-ENVIRONMENT")
            return
        }
        maintenanceCancellation = null
        if (success) publish(current.copy(state = SessionState.READY, technicalId = "A-READY"))
        else block("A-MAINTENANCE-FAILED")
    }

    @Synchronized override fun testAccess() {
        if (!foregroundAllowed || current.state != SessionState.READY) return
        if (!accessEnvironmentAllowed()) { invalidate("A-ENVIRONMENT"); return }
        val generation = current.generation
        val result = try { operation?.perform() ?: OperationResult.FAILED }
        catch (_: RuntimeException) { OperationResult.FAILED }
        if (!valid(generation, SessionState.READY)) {
            if (generation == current.generation && current.state == SessionState.READY) invalidate("A-ENVIRONMENT")
            return
        }
        when (result) {
            OperationResult.SUCCEEDED -> publish(current.copy(
                platformAuthorization = Observation.SUCCEEDED, keyOperation = Observation.SUCCEEDED,
                technicalId = "A-ACCESS-OK", marker = "PAG_STAGE_A_SYNTHETIC_ONLY"
            ))
            OperationResult.NOT_AUTHORIZED -> block("A-KEY-AUTHORIZATION-DENIED", Observation.DENIED)
            OperationResult.FAILED -> block("A-KEY-OPERATION-FAILED", Observation.FAILED)
            OperationResult.CONSUMED -> publish(current.copy(
                marker = null, keyOperation = Observation.CONSUMED, technicalId = "A-OPERATION-CONSUMED"
            ))
        }
    }

    private fun valid(generation: Long, state: SessionState) =
        foregroundAllowed && accessEnvironmentAllowed() && current.generation == generation && current.state == state

    private fun block(id: String, observation: Observation = Observation.NOT_TESTED) {
        invalidate(id, SessionState.BLOCKED, observation)
    }

    private fun publish(snapshot: SessionSnapshot) {
        current = snapshot
        observers.toList().forEach { it(snapshot) }
    }
}
