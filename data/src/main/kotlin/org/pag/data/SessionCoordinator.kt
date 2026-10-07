package org.pag.data

import org.pag.domain.AccessPoint
import org.pag.domain.AccessScope
import org.pag.domain.MonotonicTime
import org.pag.domain.SessionPolicy
import org.pag.domain.StorageAccess
import org.pag.domain.StorageContext
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
 * No native I/O executes inside this monitor.
 * Observers are synchronous; the Android root dispatches every event on main.
 * No pending observer emission may later resurrect a previously published marker.
 */
class SessionCoordinator(
    private val authentication: AuthenticationExperiment,
    private val maintenance: Maintenance,
    private val clock: MonotonicTime,
    private val policy: SessionPolicy,
    private val accessEnvironmentAllowed: () -> Boolean = { true }
) : SessionPort, SessionLifecycle, StorageAccess {
    private var absoluteDeadline: Long? = null
    private var idleDeadline: Long? = null
    private var attemptStarted = 0L
    private var authenticationStarting = false
    private var accessInProgress = false
    private var current = SessionSnapshot()
    private var foregroundAllowed = false
    private val observers = mutableSetOf<(SessionSnapshot) -> Unit>()
    private var authCancellation: Cancellation? = null
    private var maintenanceCancellation: Subscription? = null
    private var operation: SyntheticOperation? = null

    @Synchronized override fun snapshot(): SessionSnapshot { enforceValidity(); return current }

    @Synchronized override fun observe(observer: (SessionSnapshot) -> Unit): Subscription {
        enforceValidity()
        observers.add(observer)
        observer(current)
        return Subscription { synchronized(this) { observers.remove(observer) } }
    }

    @Synchronized override fun foreground(allowed: Boolean) {
        foregroundAllowed = allowed
        if (!allowed) invalidate("A-LIFECYCLE") else enforceValidity()
    }

    @Synchronized override fun checkExpiry() { enforceValidity() }

    @Synchronized override fun userInteraction() {
        enforceValidity()
        if (current.state == SessionState.READY && foregroundAllowed && accessEnvironmentAllowed())
            idleDeadline = deadline(clock.nowMillis(), policy.inactivityMillis)
    }

    private fun deadline(start: Long, duration: Long): Long =
        if (start > Long.MAX_VALUE - duration) Long.MAX_VALUE else start + duration

    private fun enforceValidity() {
        if (current.state != SessionState.READY && current.state != SessionState.MAINTENANCE) return
        val now = clock.nowMillis()
        if (!foregroundAllowed || !accessEnvironmentAllowed()) invalidate("A-ENVIRONMENT")
        else if (absoluteDeadline == null || now >= absoluteDeadline!!) invalidate("B0-ABSOLUTE-EXPIRED")
        else if (current.state == SessionState.READY && (idleDeadline == null || now >= idleDeadline!!))
            invalidate("B0-IDLE-EXPIRED")
    }

    @Synchronized override fun acquire(scope: AccessScope): StorageContext? {
        enforceValidity()
        val expected = if (scope == AccessScope.READY) SessionState.READY else SessionState.MAINTENANCE
        if (!valid(current.generation, expected)) return null
        val generation = current.generation
        return object : StorageContext {
            override val scope = scope
            override fun check(point: AccessPoint): Boolean = synchronized(this@SessionCoordinator) {
                valid(generation, expected)
            }
            override fun publish(result: () -> Unit): Boolean = synchronized(this@SessionCoordinator) {
                if (!valid(generation, expected)) false else { result(); true }
            }
        }
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
        absoluteDeadline = null
        idleDeadline = null
        current = SessionSnapshot(state = state, generation = current.generation + 1,
            technicalId = id, platformAuthorization = observation, keyOperation = observation)
        oldAuth?.cancel()
        oldMaintenance?.cancel()
        oldOperation?.release()
        publish(current)
    }

    override fun authenticate(experiment: Experiment) {
        val generation = synchronized(this) {
            if (authenticationStarting || !foregroundAllowed || !accessEnvironmentAllowed() || current.state != SessionState.LOCKED) return
            authenticationStarting = true
            attemptStarted = clock.nowMillis()
            val next = current.generation + 1
            publish(SessionSnapshot(SessionState.AUTHENTICATING, next, experiment, technicalId = "A-AUTH"))
            next
        }
        val mode = when (experiment) {
            Experiment.PER_OPERATION -> KeyExperiment.PER_OPERATION
            Experiment.TIME_WINDOW -> KeyExperiment.TIME_WINDOW
        }
        val cancellation = try {
            authentication.start(mode) { outcome -> authenticationCompleted(generation, outcome) }
        } catch (_: RuntimeException) {
            synchronized(this) {
                authenticationStarting = false
                if (valid(generation, SessionState.AUTHENTICATING)) block("A-AUTH-ADAPTER-FAILED")
            }
            return
        }
        synchronized(this) {
            authenticationStarting = false
            if (valid(generation, SessionState.AUTHENTICATING)) authCancellation = cancellation
            else cancellation.cancel()
        }
    }

    private fun authenticationCompleted(generation: Long, outcome: AuthenticationOutcome) {
        synchronized(this) {
            if (!valid(generation, SessionState.AUTHENTICATING)) {
                if (outcome is AuthenticationOutcome.Granted) outcome.operation.release()
                if (generation == current.generation && current.state == SessionState.AUTHENTICATING)
                    invalidate("A-ENVIRONMENT")
                return
            }
            authCancellation = null
            when (outcome) {
                is AuthenticationOutcome.Rejected -> {
                    if (outcome.reason == SecurityFailure.CANCELLED) invalidate("A-AUTH-CANCEL")
                    else block("A-AUTH-${outcome.reason.name}")
                    return
                }
                is AuthenticationOutcome.Granted -> {
                    val now = clock.nowMillis()
                    if (outcome.authenticatedAtMillis < attemptStarted || outcome.authenticatedAtMillis > now) {
                        outcome.operation.release()
                        block("B0-AUTH-TIME-INVALID")
                        return
                    }
                    absoluteDeadline = deadline(outcome.authenticatedAtMillis, policy.absoluteMillis)
                    if (now >= absoluteDeadline!!) {
                        outcome.operation.release()
                        invalidate("B0-ABSOLUTE-EXPIRED")
                        return
                    }
                    operation = outcome.operation
                    publish(current.copy(state = SessionState.MAINTENANCE,
                        technicalId = "A-SYNTHETIC-MAINTENANCE", keyProperties = outcome.operation.properties))
                }
            }
        }
        // Adapters schedule work outside the coordinator monitor. Actual storage steps
        // additionally need a MAINTENANCE context; starting this dummy is no storage grant.
        synchronized(this) { if (!valid(generation, SessionState.MAINTENANCE)) return }
        val cancellation = try {
            maintenance.run { success -> maintenanceCompleted(generation, success) }
        } catch (_: RuntimeException) {
            synchronized(this) {
                if (valid(generation, SessionState.MAINTENANCE)) block("A-MAINTENANCE-FAILED")
            }
            return
        }
        synchronized(this) {
            if (valid(generation, SessionState.MAINTENANCE)) maintenanceCancellation = cancellation
            else cancellation.cancel()
        }
    }

    @Synchronized private fun maintenanceCompleted(generation: Long, success: Boolean) {
        if (!valid(generation, SessionState.MAINTENANCE)) {
            if (generation == current.generation && current.state == SessionState.MAINTENANCE) invalidate("A-ENVIRONMENT")
            return
        }
        maintenanceCancellation = null
        if (success) {
            idleDeadline = deadline(clock.nowMillis(), policy.inactivityMillis)
            publish(current.copy(state = SessionState.READY, technicalId = "A-READY"))
        }
        else block("A-MAINTENANCE-FAILED")
    }

    override fun testAccess() {
        val work = synchronized(this) {
            if (!valid(current.generation, SessionState.READY) || accessInProgress) return
            accessInProgress = true
            Pair(current.generation, operation)
        }
        // Native work is outside the coordinator monitor; locking can revoke it.
        val result = try { work.second?.perform() ?: OperationResult.FAILED }
        catch (_: RuntimeException) { OperationResult.FAILED }
        synchronized(this) {
            accessInProgress = false
            if (!valid(work.first, SessionState.READY)) return
            when (result) {
                OperationResult.SUCCEEDED -> publish(current.copy(
                    platformAuthorization = Observation.SUCCEEDED, keyOperation = Observation.SUCCEEDED,
                    technicalId = "A-ACCESS-OK", marker = "PAG_STAGE_A_SYNTHETIC_ONLY"))
                OperationResult.NOT_AUTHORIZED -> block("A-KEY-AUTHORIZATION-DENIED", Observation.DENIED)
                OperationResult.FAILED -> block("A-KEY-OPERATION-FAILED", Observation.FAILED)
                OperationResult.CONSUMED -> publish(current.copy(marker = null,
                    keyOperation = Observation.CONSUMED, technicalId = "A-OPERATION-CONSUMED"))
            }
        }
    }

    private fun valid(generation: Long, state: SessionState): Boolean {
        enforceValidity()
        return foregroundAllowed && accessEnvironmentAllowed() &&
            current.generation == generation && current.state == state
    }

    private fun block(id: String, observation: Observation = Observation.NOT_TESTED) {
        invalidate(id, SessionState.BLOCKED, observation)
    }

    private fun publish(snapshot: SessionSnapshot) {
        current = snapshot
        observers.toList().forEach { if (current === snapshot) it(snapshot) }
    }
}
