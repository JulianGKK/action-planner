package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.security.*

/** Real central coordinator; synthetic unwrap fake. No claim about platform authorization. */
class WrappingSessionTest {
    private var time = 0L
    private val callbacks = mutableListOf<(AuthenticationOutcome) -> Unit>()
    private val maintenance = mutableListOf<(Boolean) -> Unit>()
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callbacks.add(complete); return Cancellation { }
        }
    }, Maintenance { maintenance.add(it); Subscription { } }, MonotonicTime { time }, SessionPolicy(100, 20))
    private class Operation(val step: () -> Unit = {}) : SyntheticOperation {
        var unwrapped = 0
        var released = false
        override fun perform(): OperationResult { fail("B15-UNGUARDED-ENTRY"); return OperationResult.FAILED }
        override fun performAuthorized(allowed: () -> Boolean): OperationResult {
            if (!allowed()) return OperationResult.NOT_AUTHORIZED
            unwrapped++; step()
            return if (allowed()) OperationResult.SUCCEEDED else OperationResult.NOT_AUTHORIZED
        }
        override fun release() { released = true }
    }
    private fun ready(op: Operation): StorageContext {
        session.foreground(true); session.authenticate(Experiment.STORAGE_UNWRAP)
        callbacks.last()(AuthenticationOutcome.Granted(op, time)); maintenance.last()(true)
        return session.acquire()!!
    }
    @Test fun successPublishesOnlySyntheticUnwrapMarker() {
        val op = Operation(); ready(op); assertNull(session.snapshot().marker)
        session.testAccess(); assertEquals(1, op.unwrapped)
        assertEquals("B15-UNWRAP-SUCCEEDED", session.snapshot().technicalId)
        assertEquals("PAG_B15_SYNTHETIC_UNWRAP_ONLY", session.snapshot().marker)
        session.lock(); session.testAccess(); assertEquals(1, op.unwrapped); assertTrue(op.released)
    }
    @Test fun deadlineAfterNativeStepRejectsResult() {
        val op = Operation { time = 100 }; ready(op); session.testAccess()
        assertEquals(1, op.unwrapped); assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertNull(session.snapshot().marker)
    }
    @Test fun lockDuringStepRejectsResult() {
        val op = Operation { session.lock() }; ready(op); session.testAccess()
        assertNull(session.snapshot().marker); assertTrue(op.released)
    }
    @Test fun oldContextAndOldAuthCannotUnwrapAfterReauthentication() {
        val old = Operation(); val context = ready(old); val callback = callbacks.first()
        session.lock(); val fresh = Operation(); ready(fresh)
        val late = Operation(); callback(AuthenticationOutcome.Granted(late, time))
        assertTrue(late.released); assertEquals(0, late.unwrapped)
        assertFalse(context.check(AccessPoint.OPERATION)); session.testAccess()
        assertEquals(0, old.unwrapped); assertEquals(1, fresh.unwrapped)
    }
    @Test fun expiredBeforeAccessNeverUnwraps() {
        val op = Operation(); ready(op); time = 20; session.testAccess()
        assertEquals(0, op.unwrapped); assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
    @Test fun foregroundLossAndRemainingPlatformAuthorizationCannotRevive() {
        val op = Operation(); ready(op); session.foreground(false); session.foreground(true); session.testAccess()
        assertEquals(0, op.unwrapped); assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertEquals(1, callbacks.size)
    }
    @Test fun parallelWrappingAuthenticationAndLateMaintenanceAreRejected() {
        session.foreground(true); session.authenticate(Experiment.STORAGE_UNWRAP)
        session.authenticate(Experiment.STORAGE_INITIALIZE); assertEquals(1, callbacks.size)
        val op = Operation(); callbacks.last()(AuthenticationOutcome.Granted(op, time))
        session.lock(); maintenance.last()(true); session.testAccess()
        assertTrue(op.released); assertEquals(0, op.unwrapped)
    }
}
