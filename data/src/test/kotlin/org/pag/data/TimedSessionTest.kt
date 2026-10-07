package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.security.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TimedSessionTest {
    private class Clock(var time: Long = 0) : MonotonicTime { override fun nowMillis() = time }
    private val clock = Clock()
    private val auth = mutableListOf<(AuthenticationOutcome) -> Unit>()
    private val work = mutableListOf<(Boolean) -> Unit>()
    private var allowed = true
    private val session = SessionCoordinator(
        object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                auth.add(complete); return Cancellation { }
            }
        }, Maintenance { work.add(it); Subscription { } }, clock, SessionPolicy(100, 20), { allowed })
    private fun authenticate(at: Long = clock.time, operation: SyntheticOperation = object : SyntheticOperation {
        override fun perform() = OperationResult.SUCCEEDED
        override fun release() { }
    }) {
        session.foreground(true)
        session.authenticate(Experiment.PER_OPERATION)
        auth.last()(AuthenticationOutcome.Granted(operation, at))
    }
    private fun ready() { authenticate(); work.last()(true) }
    private fun state() = session.snapshot().state

    @Test fun authenticationStartsFreshGenerationAndAbsoluteClock() {
        val old = session.snapshot().generation
        clock.time = 7; authenticate()
        assertTrue(session.snapshot().generation > old)
        clock.time = 106; session.checkExpiry(); assertEquals(SessionState.MAINTENANCE, state())
        clock.time = 107; session.checkExpiry(); assertEquals(SessionState.LOCKED, state())
    }
    @Test fun maintenanceConsumesAbsoluteTimeButNotIdleTime() {
        authenticate(); clock.time = 90
        assertEquals(SessionState.MAINTENANCE, state())
        work.last()(true); assertEquals(SessionState.READY, state())
        clock.time = 100; assertEquals(SessionState.LOCKED, state())
    }
    @Test fun maintenanceLateCallbackCannotOpenExpiredSession() {
        authenticate(); clock.time = 100; work.last()(true)
        assertEquals(SessionState.LOCKED, state())
    }
    @Test fun delayedSuccessfulAuthKeepsOriginalTimestamp() {
        session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        clock.time = 101
        auth.last()(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() { }
        }, 0))
        assertEquals(SessionState.LOCKED, state()); assertTrue(work.isEmpty())
    }
    @Test fun activityOnlyRefreshesIdleNotAbsoluteDeadline() {
        ready()
        for (time in listOf(19L, 38L, 57L, 76L, 95L)) {
            clock.time = time; session.userInteraction(); assertEquals(SessionState.READY, state())
        }
        clock.time = 100; session.userInteraction(); assertEquals(SessionState.LOCKED, state())
        assertEquals(1, auth.size)
    }
    @Test fun inactivityCannotBeRevivedByLateInteraction() {
        ready(); clock.time = 20; session.userInteraction()
        assertEquals(SessionState.LOCKED, state())
    }
    @Test fun timersAndIoChecksDoNotCountAsActivity() {
        ready(); val context = session.acquire()!!
        clock.time = 19; session.checkExpiry(); assertTrue(context.check(AccessPoint.STEP))
        clock.time = 20; assertFalse(context.check(AccessPoint.OPERATION))
        assertEquals(SessionState.LOCKED, state())
    }
    @Test fun earlierDeadlineWinsAndEveryAccessPhaseRechecksTime() {
        ready(); val context = session.acquire()!!
        clock.time = 20
        AccessPoint.entries.forEach { assertFalse(context.check(it)) }
        var published = false
        assertFalse(context.publish { published = true }); assertFalse(published)
    }
    @Test fun absoluteDeadlineWinsDuringContinuingInteraction() {
        authenticate(); clock.time = 95; work.last()(true)
        val context = session.acquire()!!
        clock.time = 100
        assertFalse(context.check(AccessPoint.COMMIT))
        assertFalse(context.publish { fail("B0-LATE-PUBLICATION") })
    }
    @Test fun lockAndReauthRejectOldContextAndOldCallbacks() {
        ready(); val old = session.acquire()!!; val oldWork = work.first()
        session.lock(); ready(); val generation = session.snapshot().generation
        oldWork(true)
        assertFalse(old.check(AccessPoint.COMMIT)); assertFalse(old.publish { fail("B0-OLD") })
        assertEquals(generation, session.snapshot().generation)
        assertTrue(session.acquire()!!.check(AccessPoint.OPERATION))
    }
    @Test fun maintenanceContextCannotBecomeRegularContext() {
        authenticate()
        assertNull(session.acquire())
        val internal = session.acquire(AccessScope.MAINTENANCE)!!
        assertTrue(internal.check(AccessPoint.STEP))
        work.last()(true)
        assertFalse(internal.check(AccessPoint.OPERATION))
        assertTrue(session.acquire()!!.check(AccessPoint.OPERATION))
    }
    @Test fun liveForegroundConstraintRevokesStorage() {
        ready(); val context = session.acquire()!!
        allowed = false
        assertFalse(context.check(AccessPoint.OPERATION))
        assertEquals(SessionState.LOCKED, state())
    }
    @Test fun sleepSizedMonotonicJumpExpiresWithoutTimer() {
        ready(); clock.time = 1_000_000
        assertNull(session.acquire()); assertEquals(SessionState.LOCKED, state())
    }
    @Test fun recreationAndProcessStartNeverReconstructSession() {
        ready(); session.foreground(false)
        assertEquals(SessionState.LOCKED, state())
        val recreated = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit) = Cancellation { }
        }, Maintenance { Subscription { } }, clock, SessionPolicy(100, 20))
        recreated.foreground(true)
        assertEquals(SessionState.LOCKED, recreated.snapshot().state)
    }
    @Test fun remainingPlatformAuthorizationDoesNotStartAuthentication() {
        ready(); clock.time = 20; session.foreground(true); session.testAccess()
        assertEquals(SessionState.LOCKED, state()); assertEquals(1, auth.size)
    }
    @Test fun nativeWorkDoesNotHoldCoordinatorMonitor() {
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        authenticate(operation = object : SyntheticOperation {
            override fun perform(): OperationResult {
                entered.countDown(); check(finish.await(2, TimeUnit.SECONDS))
                return OperationResult.SUCCEEDED
            }
            override fun release() { }
        })
        work.last()(true)
        val worker = Thread { session.testAccess() }; worker.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        // No sleeping: another thread's native operation cannot postpone the lock.
        try { session.lock(); assertEquals(SessionState.LOCKED, state()) }
        finally { finish.countDown(); worker.join(2000) }
        assertFalse(worker.isAlive); assertNull(session.snapshot().marker)
    }
    @Test fun maintenanceSchedulingDoesNotHoldCoordinatorMonitor() {
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        var complete: ((AuthenticationOutcome) -> Unit)? = null
        val controller = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, callback: (AuthenticationOutcome) -> Unit): Cancellation {
                complete = callback; return Cancellation { }
            }
        }, Maintenance { callback ->
            entered.countDown(); check(finish.await(2, TimeUnit.SECONDS)); callback(true)
            Subscription { }
        }, clock, SessionPolicy(100, 20))
        controller.foreground(true); controller.authenticate(Experiment.PER_OPERATION)
        val worker = Thread { complete!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() { }
        }, 0)) }; worker.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        try { controller.lock(); assertEquals(SessionState.LOCKED, controller.snapshot().state) }
        finally { finish.countDown(); worker.join(2000) }
        assertFalse(worker.isAlive); assertEquals(SessionState.LOCKED, controller.snapshot().state)
    }
    @Test fun inFlightAuthenticationStartCannotOverlapAfterLock() {
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        var starts = 0
        val controller = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, callback: (AuthenticationOutcome) -> Unit): Cancellation {
                starts++; entered.countDown(); check(finish.await(2, TimeUnit.SECONDS))
                return Cancellation { }
            }
        }, Maintenance { Subscription { } }, clock, SessionPolicy(100, 20))
        controller.foreground(true)
        val worker = Thread { controller.authenticate(Experiment.PER_OPERATION) }; worker.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        try {
            controller.lock(); controller.authenticate(Experiment.PER_OPERATION)
            assertEquals(1, starts); assertEquals(SessionState.LOCKED, controller.snapshot().state)
        } finally { finish.countDown(); worker.join(2000) }
        assertFalse(worker.isAlive)
    }
    @Test fun successfulAuthFromFutureBlocksWithoutMaintenance() {
        session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        auth.last()(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() { }
        }, 1))
        assertEquals(SessionState.BLOCKED, state()); assertTrue(work.isEmpty())
    }
    @Test fun oldAuthSuccessAfterReauthenticationIsRejectedEvenWithinTimeWindow() {
        ready(); val previous = auth.first(); session.lock(); ready()
        val generation = session.snapshot().generation
        previous(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() { }
        }, 0))
        assertEquals(generation, session.snapshot().generation); assertEquals(SessionState.READY, state())
        assertEquals(2, work.size)
    }

}
