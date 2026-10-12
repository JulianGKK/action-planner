package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.security.*

class SessionCoordinatorTest {
    private class Auth : AuthenticationExperiment {
        val callbacks = mutableListOf<(AuthenticationOutcome) -> Unit>()
        var cancellations = 0
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callbacks.add(complete)
            return Cancellation { cancellations++ }
        }
    }
    private class Work : Maintenance {
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        override fun run(complete: (Boolean) -> Unit): Subscription {
            callbacks.add(complete)
            return Subscription { }
        }
    }
    private class Operation(var result: OperationResult = OperationResult.SUCCEEDED) : SyntheticOperation {
        var calls = 0
        var released = false
        override fun perform(): OperationResult { calls++; return result }
        override fun release() { released = true }
    }
    private val auth = Auth()
    private val work = Work()
    private val session = SessionCoordinator(auth, work, MonotonicTime { 0 }, SessionPolicy(1000, 1000))
    private fun start() {
        session.foreground(true)
        session.authenticate(Experiment.PER_OPERATION)
    }
    private fun ready(operation: Operation = Operation()): Operation {
        start()
        auth.callbacks.last()(AuthenticationOutcome.Granted(operation, 0))
        work.callbacks.last()(true)
        return operation
    }

    @Test fun readOnlyCountdownUsesAuthDeadlineAndDoesNotRenewIdleOrAbsolute() {
        var now = 0L
        val timed = SessionCoordinator(auth, work, MonotonicTime { now }, ProductSessionTiming.policy)
        timed.foreground(true)
        assertNull(timed.remainingAbsoluteMillis())
        timed.authenticate(Experiment.PER_OPERATION)
        auth.callbacks.last()(AuthenticationOutcome.Granted(Operation(), 0))
        now = 1000
        assertNull(timed.remainingAbsoluteMillis()) // Maintenance consumes absolute time.
        work.callbacks.last()(true)
        assertEquals(179000L, timed.remainingAbsoluteMillis())
        for (second in 2..30) { now = second * 1000L; timed.remainingAbsoluteMillis() }
        now = 31000
        assertNull(timed.remainingAbsoluteMillis()) // ticks did not renew 30-second idle.
        assertEquals(SessionState.LOCKED, timed.snapshot().state)
        timed.authenticate(Experiment.PER_OPERATION)
        auth.callbacks.last()(AuthenticationOutcome.Granted(Operation(), now))
        work.callbacks.last()(true)
        assertEquals(180000L, timed.remainingAbsoluteMillis())
        for (second in 1..180) {
            now = 31000 + second * 1000L
            timed.userInteraction()
            timed.remainingAbsoluteMillis()
        }
        assertNull(timed.remainingAbsoluteMillis()) // real activity also cannot renew absolute.
        assertEquals(SessionState.LOCKED, timed.snapshot().state)
        timed.storageFailure()
        assertNull(timed.remainingAbsoluteMillis())
    }

    @Test fun freshStartLocked() {
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertNull(session.snapshot().marker)
    }
    @Test fun validTransitionsAndMarkerRequireExplicitAccess() {
        start()
        assertEquals(SessionState.AUTHENTICATING, session.snapshot().state)
        auth.callbacks.last()(AuthenticationOutcome.Granted(Operation(), 0))
        assertEquals(SessionState.MAINTENANCE, session.snapshot().state)
        assertNull(session.snapshot().marker)
        work.callbacks.last()(true)
        assertEquals(SessionState.READY, session.snapshot().state)
        assertNull(session.snapshot().marker)
        session.testAccess()
        assertEquals("PAG_STAGE_A_SYNTHETIC_ONLY", session.snapshot().marker)
    }
    @Test fun authNotAllowedWithoutForeground() {
        session.authenticate(Experiment.TIME_WINDOW)
        assertTrue(auth.callbacks.isEmpty())
    }
    @Test fun duplicateAuthenticationRejected() {
        start()
        session.authenticate(Experiment.TIME_WINDOW)
        assertEquals(1, auth.callbacks.size)
    }
    @Test fun authenticationFromReadyRejected() {
        ready()
        session.authenticate(Experiment.TIME_WINDOW)
        assertEquals(1, auth.callbacks.size)
    }
    @Test fun everyLockInvalidatesGeneration() {
        val first = session.snapshot().generation
        session.lock()
        session.lock()
        assertEquals(first + 2, session.snapshot().generation)
    }
    @Test fun lateAuthenticationAfterLockReleasedAndIgnored() {
        start()
        session.lock()
        val operation = Operation()
        auth.callbacks.first()(AuthenticationOutcome.Granted(operation, 0))
        assertTrue(operation.released)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertTrue(work.callbacks.isEmpty())
    }
    @Test fun oldCallbackCannotChangeNewSession() {
        start()
        session.lock()
        session.authenticate(Experiment.TIME_WINDOW)
        val generation = session.snapshot().generation
        auth.callbacks.first()(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED))
        assertEquals(SessionState.AUTHENTICATING, session.snapshot().state)
        assertEquals(generation, session.snapshot().generation)
    }
    @Test fun lockingMaintenanceRejectsLateReady() {
        start()
        val operation = Operation()
        auth.callbacks.last()(AuthenticationOutcome.Granted(operation, 0))
        session.lock()
        work.callbacks.last()(true)
        assertTrue(operation.released)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
    @Test fun duplicateAuthResultCannotRunMaintenanceTwice() {
        start()
        val callback = auth.callbacks.last()
        callback(AuthenticationOutcome.Granted(Operation(), 0))
        val extra = Operation()
        callback(AuthenticationOutcome.Granted(extra, 0))
        assertTrue(extra.released)
        assertEquals(1, work.callbacks.size)
    }
    @Test fun readyToLockedClearsMarkerAndRevokesAccess() {
        val operation = ready()
        session.testAccess()
        session.lock()
        session.testAccess()
        assertEquals(1, operation.calls)
        assertTrue(operation.released)
        assertNull(session.snapshot().marker)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
    @Test fun accessRejectedInAllNonReadyStates() {
        session.testAccess()
        start()
        session.testAccess()
        val operation = Operation()
        auth.callbacks.last()(AuthenticationOutcome.Granted(operation, 0))
        session.testAccess()
        work.callbacks.last()(false)
        session.testAccess()
        assertEquals(0, operation.calls)
        assertEquals(SessionState.BLOCKED, session.snapshot().state)
    }
    @Test fun dialogAbortLocksWithoutPromptLoop() {
        start()
        auth.callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED))
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertEquals(1, auth.callbacks.size)
    }
    @Test fun keyFailureBlocksWithoutFallback() {
        start()
        auth.callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY))
        assertEquals(SessionState.BLOCKED, session.snapshot().state)
        assertEquals(1, auth.callbacks.size)
        assertNull(session.snapshot().marker)
    }
    @Test fun deviceUnlockAloneDoesNotOpenSession() {
        session.foreground(false)
        session.foreground(true)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertTrue(auth.callbacks.isEmpty())
    }
    @Test fun foregroundLossDuringReadyRevokesImmediately() {
        val operation = ready()
        session.testAccess()
        session.foreground(false)
        session.foreground(true)
        assertTrue(operation.released)
        assertNull(session.snapshot().marker)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
    @Test fun authorizationExpiryBlocksAndRemovesPreviouslyVisibleMarker() {
        val operation = ready()
        session.testAccess()
        operation.result = OperationResult.NOT_AUTHORIZED
        session.testAccess()
        assertNull(session.snapshot().marker)
        assertEquals(SessionState.BLOCKED, session.snapshot().state)
        assertEquals(Observation.DENIED, session.snapshot().platformAuthorization)
    }
    @Test fun lockDuringOperationCannotPublishOldResult() {
        start()
        auth.callbacks.last()(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform(): OperationResult { session.lock(); return OperationResult.SUCCEEDED }
            override fun release() { }
        }, 0))
        work.callbacks.last()(true)
        session.testAccess()
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertNull(session.snapshot().marker)
    }
    @Test fun liveEnvironmentCheckRejectsAccessBeforeLifecycleNotification() {
        var allowed = true
        val coordinator = SessionCoordinator(auth, work, MonotonicTime { 0 }, SessionPolicy(1000, 1000)) { allowed }
        coordinator.foreground(true)
        coordinator.authenticate(Experiment.TIME_WINDOW)
        val operation = Operation()
        auth.callbacks.last()(AuthenticationOutcome.Granted(operation, 0))
        work.callbacks.last()(true)
        coordinator.testAccess()
        allowed = false
        coordinator.testAccess()
        assertEquals(1, operation.calls)
        assertEquals(SessionState.LOCKED, coordinator.snapshot().state)
        assertNull(coordinator.snapshot().marker)
    }
}
