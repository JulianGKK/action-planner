package org.pag.data.prototype.b2

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.*
import org.pag.domain.*
import org.pag.security.*

@RunWith(Parameterized::class)
class B2SessionTest(private val room: Boolean) {
    companion object { @JvmStatic @Parameterized.Parameters(name = "room={0}") fun variants() = listOf(arrayOf(false), arrayOf(true)) }
    private var time = 0L
    private val callbacks = mutableListOf<(AuthenticationOutcome) -> Unit>()
    private val modes = mutableListOf<KeyExperiment>()
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            modes.add(mode); callbacks.add(complete); return Cancellation {}
        }
    }, Maintenance { error("B2-NO-DUMMY") }, MonotonicTime { time }, SessionPolicy(100, 20))
    private val reopen = if (room) Experiment.B2_ROOM_REOPEN else Experiment.B2_DIRECT_REOPEN
    private val init = if (room) Experiment.B2_ROOM_INITIALIZE else Experiment.B2_DIRECT_INITIALIZE
    private class Work(override val initializes: Boolean) : StorageGateOperation {
        lateinit var done: (B16Id) -> Unit
        lateinit var access: StorageContext
        var released = false
        override val publicMarker = "PAG_B2_SYNTHETIC_ONLY"
        override fun diagnosticId(id: B16Id) = id.testId.replace("B16-", "B2-")
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() { released = true }
        override fun run(context: StorageContext, progress: (B16Id) -> Unit, complete: (B16Id) -> Unit) { access = context; done = complete }
    }
    private fun start(creates: Boolean = false): Work {
        session.foreground(true); session.authenticate(if (creates) init else reopen)
        val work = Work(creates); callbacks.last()(AuthenticationOutcome.Granted(work, time)); return work
    }
    @Test fun gateMustFinishBeforeReady() {
        val work = start(); assertEquals(SessionState.MAINTENANCE, session.snapshot().state); assertNull(session.acquire())
        work.done(B16Id.RESTART); assertEquals(SessionState.READY, session.snapshot().state)
        assertEquals("B2-RESTART-SUCCEEDED", session.snapshot().technicalId); assertEquals("PAG_B2_SYNTHETIC_ONLY", session.snapshot().marker)
    }
    @Test fun initCompletesLockedAndReleasesOperation() { val work = start(true); work.done(B16Id.CREATED); assertEquals(SessionState.LOCKED, session.snapshot().state); assertTrue(work.released) }
    @Test fun deadlinePreventsReady() { val work = start(); time = 100; work.done(B16Id.RESTART); assertEquals(SessionState.LOCKED, session.snapshot().state); assertNull(session.snapshot().marker) }
    @Test fun lateGateCannotEnterNewGeneration() { val old = start(); session.lock(); val fresh = start(); old.done(B16Id.RESTART); assertEquals(SessionState.MAINTENANCE, session.snapshot().state); assertFalse(old.access.check(AccessPoint.COMMIT)); fresh.done(B16Id.RESTART); assertEquals(SessionState.READY, session.snapshot().state) }
    @Test fun authAbortNeverStartsGate() { session.foreground(true); session.authenticate(reopen); callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED)); assertEquals(SessionState.LOCKED, session.snapshot().state) }
    @Test fun promptFailureIsBlockedWithoutRetry() { session.foreground(true); session.authenticate(reopen); callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR, WrappingFailure.PROMPT_START)); assertEquals("B2-PROMPT-START", session.snapshot().technicalId); assertEquals(SessionState.BLOCKED, session.snapshot().state); assertEquals(1, modes.size) }
    @Test fun existingInventoryIsNeverInitialized() { session.foreground(true); session.authenticate(init); callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, WrappingFailure.NOT_EMPTY)); assertEquals("B2-BOOTSTRAP-NOT-EMPTY", session.snapshot().technicalId); assertEquals(SessionState.BLOCKED, session.snapshot().state) }
    @Test fun parallelAttemptAndLateCallbackAreRejected() { session.foreground(true); session.authenticate(reopen); session.authenticate(init); assertEquals(1, modes.size); session.lock(); val work = Work(false); callbacks.last()(AuthenticationOutcome.Granted(work, time)); assertTrue(work.released); assertEquals(SessionState.LOCKED, session.snapshot().state) }
}
