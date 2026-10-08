package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.security.*
import org.pag.data.prototype.*

class B16SessionTest {
    private var time = 0L
    private var environment = true
    private val callbacks = mutableListOf<(AuthenticationOutcome) -> Unit>()
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callbacks.add(complete); return Cancellation {}
        }
    }, Maintenance { error("B16-DUMMY-MUST-NOT-RUN") }, MonotonicTime { time }, SessionPolicy(100, 20), { environment })
    private class Work(override val initializes: Boolean = false) : StorageGateOperation {
        lateinit var context: StorageContext
        lateinit var done: (B16Id) -> Unit
        lateinit var progress: (B16Id) -> Unit
        var released = false
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() { released = true }
        override fun run(context: StorageContext, progress: (B16Id) -> Unit, complete: (B16Id) -> Unit) {
            this.context = context; this.progress = progress; done = complete
        }
    }
    private fun start(work: Work = Work()): Work {
        session.foreground(true)
        session.authenticate(if (work.initializes) Experiment.DATABASE_INITIALIZE else Experiment.DATABASE_REOPEN)
        callbacks.last()(AuthenticationOutcome.Granted(work, time))
        assertEquals(SessionState.MAINTENANCE, session.snapshot().state)
        return work
    }
    @Test fun restartIsNotReadyBeforeGateCompletes() {
        val work = start(); assertNull(session.acquire()); assertEquals(AccessScope.MAINTENANCE, work.context.scope)
        work.done(B16Id.RESTART)
        assertEquals(SessionState.READY, session.snapshot().state)
        assertEquals(B16Id.RESTART.testId, session.snapshot().technicalId)
        assertFalse(work.context.check(AccessPoint.OPERATION))
    }
    @Test fun creationClosesSessionInsteadOfPublishingReady() {
        val work = start(Work(true)); work.done(B16Id.CREATED)
        assertEquals(SessionState.LOCKED, session.snapshot().state); assertTrue(work.released)
    }
    @Test fun deadlineDuringDatabaseWorkRejectsPublication() {
        val work = start(); time = 100; work.done(B16Id.RESTART)
        assertEquals(SessionState.LOCKED, session.snapshot().state); assertNull(session.snapshot().marker)
        assertFalse(work.context.check(AccessPoint.COMMIT))
    }
    @Test fun lateCallbacksCannotEnterNewGeneration() {
        val old = start(); session.lock(); val fresh = start()
        old.progress(B16Id.OPENED); old.done(B16Id.RESTART)
        assertEquals(SessionState.MAINTENANCE, session.snapshot().state)
        assertFalse(old.context.check(AccessPoint.STEP)); fresh.done(B16Id.RESTART)
        assertEquals(SessionState.READY, session.snapshot().state)
    }
    @Test fun foregroundLossRevokesOpenWork() {
        val work = start(); session.foreground(false); session.foreground(true); work.done(B16Id.RESTART)
        assertEquals(SessionState.LOCKED, session.snapshot().state); assertTrue(work.released)
    }
    @Test fun parallelPromptAndLateAuthAreRejected() {
        session.foreground(true); session.authenticate(Experiment.DATABASE_REOPEN)
        session.authenticate(Experiment.DATABASE_INITIALIZE); assertEquals(1, callbacks.size)
        session.lock(); val late = Work(); callbacks.first()(AuthenticationOutcome.Granted(late, time))
        assertTrue(late.released); assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
    @Test fun authAbortNeverOpensDatabase() {
        session.foreground(true); session.authenticate(Experiment.DATABASE_REOPEN)
        callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED))
        assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
    @Test fun nonEmptyDiagnosisReachesUiAndRemainsBlocked() {
        session.foreground(true); session.authenticate(Experiment.DATABASE_INITIALIZE)
        callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, WrappingFailure.NOT_EMPTY))
        assertEquals(SessionState.BLOCKED, session.snapshot().state)
        assertEquals("B16-BOOTSTRAP-NOT-EMPTY", session.snapshot().technicalId)
        assertNull(session.acquire()); assertEquals(1, callbacks.size)
    }
    @Test fun inventoryDiagnosisRemainsSeparateAndBlocked() {
        session.foreground(true); session.authenticate(Experiment.DATABASE_INITIALIZE)
        callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, WrappingFailure.INVENTORY))
        assertEquals(SessionState.BLOCKED, session.snapshot().state)
        assertEquals("B16-BOOTSTRAP-INVENTORY", session.snapshot().technicalId)
        assertNull(session.acquire()); assertEquals(1, callbacks.size)
    }
    @Test fun promptErrorBlocksWithoutRetry() {
        session.foreground(true); session.authenticate(Experiment.DATABASE_REOPEN)
        callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR, WrappingFailure.PROMPT_START))
        assertEquals(SessionState.BLOCKED, session.snapshot().state); assertEquals(1, callbacks.size)
    }
    @Test fun blockedGateNeverBecomesReady() {
        start().done(B16Id.INTEGRITY); assertEquals(SessionState.BLOCKED, session.snapshot().state)
    }
    @Test fun displayLockEnvironmentRevokesWork() {
        val work = start(); environment = false; work.done(B16Id.RESTART)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertFalse(work.context.check(AccessPoint.PUBLICATION))
    }
    @Test fun processRecreationHasNoDatabaseSession() {
        start().done(B16Id.RESTART)
        val recreated = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation = error("B16-NO-AUTO-AUTH")
        }, Maintenance { error("B16-NO-AUTO-OPEN") }, MonotonicTime { time }, SessionPolicy(100, 20))
        recreated.foreground(true)
        assertEquals(SessionState.LOCKED, recreated.snapshot().state); assertNull(recreated.acquire())
    }
    @Test fun maintenanceDoesNotRestartAbsoluteDeadline() {
        val work = start(); time = 90; work.done(B16Id.RESTART)
        time = 100; assertNull(session.acquire()); assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
}
