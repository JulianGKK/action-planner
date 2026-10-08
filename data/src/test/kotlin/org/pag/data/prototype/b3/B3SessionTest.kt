package org.pag.data.prototype.b3

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.*
import org.pag.domain.*
import org.pag.security.*

class B3SessionTest {
    private var time = 0L
    private val callbacks = mutableListOf<(AuthenticationOutcome) -> Unit>()
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            assertTrue(mode in setOf(KeyExperiment.B3_INITIALIZE, KeyExperiment.B3_REOPEN))
            callbacks.add(complete); return Cancellation {}
        }
    }, Maintenance { error("B3-NO-DUMMY") }, MonotonicTime { time }, SessionPolicy(100, 20))
    private class Work(override val initializes: Boolean) : StorageGateOperation {
        lateinit var access: StorageContext
        lateinit var done: (B16Id) -> Unit
        var released = false
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() { released = true }
        override fun diagnosticId(id: B16Id) = if (id == B16Id.CREATED) B3Id.PUBLISH.testId else B3Id.RESTART.testId
        override fun run(context: StorageContext, progress: (B16Id) -> Unit, complete: (B16Id) -> Unit) { access = context; done = complete }
    }
    private fun start(init: Boolean = false): Work {
        session.foreground(true); session.authenticate(if (init) Experiment.B3_INITIALIZE else Experiment.B3_REOPEN)
        return Work(init).also { callbacks.last()(AuthenticationOutcome.Granted(it, time)) }
    }
    @Test fun gateCompletesBeforeReady() { val work = start(); assertNull(session.acquire()); work.done(B16Id.RESTART); assertEquals(SessionState.READY, session.snapshot().state); assertEquals("B3-RESTART-SUCCEEDED", session.snapshot().technicalId) }
    @Test fun initPublishesThenLocksAndReleases() { val work = start(true); work.done(B16Id.CREATED); assertEquals(SessionState.LOCKED, session.snapshot().state); assertEquals("B3-PUBLISH-SUCCEEDED", session.snapshot().technicalId); assertTrue(work.released) }
    @Test fun deadlineDeniesLateSuccess() { val work = start(); time = 100; work.done(B16Id.RESTART); assertEquals(SessionState.LOCKED, session.snapshot().state); assertNull(session.snapshot().marker) }
    @Test fun oldGenerationCannotPublishInNewSession() { val old = start(); session.lock(); val fresh = start(); old.done(B16Id.RESTART); assertFalse(old.access.check(AccessPoint.COMMIT)); assertEquals(SessionState.MAINTENANCE, session.snapshot().state); fresh.done(B16Id.RESTART); assertEquals(SessionState.READY, session.snapshot().state) }
    @Test fun parallelAndLateAuthenticationRejected() { session.foreground(true); session.authenticate(Experiment.B3_REOPEN); session.authenticate(Experiment.B3_INITIALIZE); assertEquals(1, callbacks.size); session.lock(); val old = Work(false); callbacks.first()(AuthenticationOutcome.Granted(old, time)); assertTrue(old.released); assertEquals(SessionState.LOCKED, session.snapshot().state) }
    @Test fun promptFailureRemainsBlockedWithB3Id() { session.foreground(true); session.authenticate(Experiment.B3_REOPEN); callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR, WrappingFailure.PROMPT_START)); assertEquals("B3-PROMPT-START", session.snapshot().technicalId); assertEquals(SessionState.BLOCKED, session.snapshot().state) }
    @Test fun abortDoesNotStartStorage() { session.foreground(true); session.authenticate(Experiment.B3_REOPEN); callbacks.last()(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED)); assertEquals(SessionState.LOCKED, session.snapshot().state) }
}
