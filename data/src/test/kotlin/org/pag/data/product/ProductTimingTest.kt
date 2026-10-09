package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*

class ProductTimingTest {
    private var now = 0L
    private lateinit var callback: (AuthenticationOutcome) -> Unit
    private lateinit var maintenanceComplete: (Boolean) -> Unit
    private var owner: SessionDbKeyOwner? = null
    private val auth = object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callback = complete; return Cancellation {}
        }
    }
    private val operation = object : SyntheticOperation {
        override fun perform() = OperationResult.SUCCEEDED
        override fun release() {}
    }
    private val session = SessionCoordinator(auth, Maintenance { Subscription {} }, MonotonicTime { now },
        ProductSessionTiming.policy, authorizedMaintenance = { c, _, complete ->
            owner = SessionDbKeyOwner.bind(c, ByteArray(32)); maintenanceComplete = complete; Subscription {}
        })
    init { session.observe { if (it.state in setOf(SessionState.LOCKED, SessionState.BLOCKED)) owner?.invalidate() } }
    private fun authenticated() {
        session.foreground(true); session.authenticate(Experiment.STORAGE_UNWRAP)
        now = 1_000; callback(AuthenticationOutcome.Granted(operation, now))
    }
    private fun ready() { authenticated(); maintenanceComplete(true) }
    @Test fun startsLockedWithoutKey() { assertEquals(SessionState.LOCKED,session.snapshot().state); assertNull(owner) }
    @Test fun maintenanceCountsTowardAbsoluteButHasNoIdleLimit() {
        authenticated(); now += 60_000
        assertEquals(SessionState.MAINTENANCE,session.snapshot().state)
        now = 180_999; assertEquals(SessionState.MAINTENANCE,session.snapshot().state)
        now = 181_000; assertEquals(SessionState.LOCKED,session.snapshot().state)
    }
    @Test fun readyExpiresAtExactlyThirtySecondsAndRevokesOwner() {
        ready(); val context = session.acquire()!!; val generation = session.snapshot().generation
        now = 30_999; assertEquals(SessionState.READY,session.snapshot().state)
        now = 31_000; assertEquals(SessionState.LOCKED,session.snapshot().state)
        assertTrue(session.snapshot().generation > generation)
        assertThrows(ProductStorageFailure::class.java) { owner!!.withDbKey(context) {} }
    }
    @Test fun realInteractionExtendsOnlyIdleAndNeverAbsolute() {
        ready(); now = 21_000; session.userInteraction(); now = 41_000
        assertEquals(SessionState.READY,session.snapshot().state)
        for (t in 50_000L..170_000L step 20_000) { now = t; session.userInteraction() }
        now = 181_000; assertEquals(SessionState.LOCKED,session.snapshot().state)
    }
    @Test fun storageAndPublicationDoNotExtendInactivity() {
        ready(); val c = session.acquire()!!; now = 21_000
        assertTrue(c.check(AccessPoint.STEP)); assertTrue(c.publish {})
        now = 31_000; assertFalse(c.check(AccessPoint.PUBLICATION)); assertEquals(SessionState.LOCKED,session.snapshot().state)
    }
    @Test fun appLeaveRevokesBeforeEitherDeadline() {
        ready(); val c = session.acquire()!!; session.foreground(false)
        assertEquals(SessionState.LOCKED,session.snapshot().state)
        assertThrows(ProductStorageFailure::class.java) { owner!!.withDbKey(c) {} }
    }
    @Test fun blockedAndMaintenanceFailureRevoke() {
        authenticated(); val c = session.acquire(AccessScope.MAINTENANCE)!!; maintenanceComplete(false)
        assertEquals(SessionState.BLOCKED,session.snapshot().state)
        assertThrows(ProductStorageFailure::class.java) { owner!!.withDbKey(c) {} }
    }
    @Test fun explicitLockRevokesAndOldGenerationStaysInvalid() {
        ready(); val old = session.acquire()!!; val oldOwner = owner!!; session.lock(); ready()
        assertThrows(ProductStorageFailure::class.java) { oldOwner.withDbKey(old) {} }
        assertFalse(old.check(AccessPoint.OPERATION))
        owner!!.withDbKey(session.acquire()!!) {}
    }
    @Test fun synchronousMaintenanceSuccessDoesNotCancelValidOwner() {
        var cancelled = false
        val next = SessionCoordinator(auth, Maintenance { Subscription {} }, MonotonicTime { now },
            ProductSessionTiming.policy, authorizedMaintenance = { _, _, complete ->
                complete(true); Subscription { cancelled = true }
            })
        next.foreground(true); next.authenticate(Experiment.STORAGE_UNWRAP)
        callback(AuthenticationOutcome.Granted(operation,now))
        assertEquals(SessionState.READY,next.snapshot().state)
        assertFalse(cancelled)
    }

}
