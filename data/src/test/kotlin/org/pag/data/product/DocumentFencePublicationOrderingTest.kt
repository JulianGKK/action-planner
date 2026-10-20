package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/** Real unchanged session monitor, controlled scheduling, simulated physical ledger only.
 * No SQLite/Room/file mutation or native cleanup evidence. */
class DocumentFencePublicationOrderingTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun take() = tasks.removeFirst()
    }
    private class H(probe: () -> Unit = {}, admitted: () -> Unit = {}) {
        val worker = Queue()
        val main = Queue()
        var onMaintenance: () -> Unit = {}
        val session = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                complete(AuthenticationOutcome.Granted(object : SyntheticOperation {
                    override fun perform() = OperationResult.SUCCEEDED
                    override fun release() {}
                }, 0))
                return Cancellation {}
            }
        }, Maintenance { onMaintenance(); it(true); Subscription {} }, MonotonicTime { 0 }, SessionPolicy(180_000, 30_000))
        var physical = 0
        var callbacks = 0
        var blocks = 0
        var phase = "NONE"
        var ticket = -1L
        val dispatch = DocumentProductDispatch(session, worker, main, { blocks++ }, probe, admitted)
        init { session.foreground(true); session.authenticate(Experiment.PER_OPERATION); assertEquals(SessionState.READY, session.snapshot().state) }
        fun submit(callback: () -> Unit = { callbacks++ }) {
            ticket = DocumentNamespaceLane.ticket()
            dispatch.run<Unit>({ callback() }) {
                physical++; phase = "FILE_PUBLISHED"; DocumentResult.Value(Unit)
            }
        }
        fun work() = worker.take().run()
    }
    @Test fun lastCheckThenEffectiveFenceMustSuppressOldCallback() {
        val checked = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var h: H
        h = H(probe = {
            assertTrue(DocumentNamespaceLane.isCurrent(h.ticket))
            checked.countDown(); check(release.await(5, SECONDS))
        })
        h.submit(); h.work(); val main = h.main.take()
        val pool = Executors.newSingleThreadExecutor()
        try {
            val publishing = pool.submit { main.run() }
            assertTrue(checked.await(5, SECONDS))
            assertEquals(0, h.callbacks)
            DocumentNamespaceLane.drainAndFence {} // effective before callback is permitted to start
            assertFalse(DocumentNamespaceLane.isCurrent(h.ticket))
            release.countDown(); publishing.get(5, SECONDS)
            assertEquals(0, h.callbacks) // strict target assertion: reproduced RED before the admission fix
            assertEquals(1, h.physical); assertEquals("FILE_PUBLISHED", h.phase)
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun admissionBeforeFenceMayBeginCallbackAfterFenceAndFinishOnce() {
        val admitted = CountDownLatch(1); val release = CountDownLatch(1)
        lateinit var h: H
        h = H(admitted = {
            assertTrue(Thread.holdsLock(h.session)); admitted.countDown(); check(release.await(5, SECONDS))
        })
        h.submit(); h.work(); val main = h.main.take(); val pool = Executors.newSingleThreadExecutor()
        try {
            val publishing = pool.submit { main.run() }; assertTrue(admitted.await(5, SECONDS))
            assertEquals(0,h.callbacks); DocumentNamespaceLane.drainAndFence {}
            assertFalse(DocumentNamespaceLane.isCurrent(h.ticket))
            release.countDown(); publishing.get(5,SECONDS); main.run()
            assertEquals(1,h.callbacks); assertEquals(1,h.physical); assertEquals("FILE_PUBLISHED",h.phase)
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun maintenanceWaitingForSessionCannotBlockMainOnNamespaceOrdering() {
        val checked=CountDownLatch(1);val release=CountDownLatch(1);val fenced=CountDownLatch(1)
        val h=H(probe={checked.countDown();check(release.await(5,SECONDS))})
        val context=h.session.acquire(AccessScope.READY)!!
        h.submit();h.work();val main=h.main.take();val pool=Executors.newFixedThreadPool(2)
        try {
            val publishing=pool.submit{main.run()};assertTrue(checked.await(5,SECONDS))
            val maintenance=pool.submit{DocumentNamespaceLane.drainAndFence{
                fenced.countDown();assertTrue(context.check(AccessPoint.STEP))
            }}
            assertTrue(fenced.await(5,SECONDS))
            release.countDown();publishing.get(5,SECONDS);maintenance.get(5,SECONDS)
            assertEquals(0,h.callbacks);assertEquals(1,h.physical);assertEquals(SessionState.READY,h.session.snapshot().state)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun realSessionMainDoesNotWaitForNamespaceIO() {
        val h=H();h.submit();h.work();val main=h.main.take()
        val holding=CountDownLatch(1);val release=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        try {
            val io=pool.submit{DocumentNamespaceLane.exclusive(h.ticket){holding.countDown();check(release.await(5,SECONDS))}}
            assertTrue(holding.await(5,SECONDS));pool.submit{main.run()}.get(2,SECONDS)
            assertEquals(1,h.callbacks);release.countDown();io.get(5,SECONDS)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun concurrentRevokeAndFenceRespectAlreadySessionAuthorizedAdmission() {
        val admitted=CountDownLatch(1);val release=CountDownLatch(1);val revoking=CountDownLatch(1)
        val h=H(admitted={admitted.countDown();check(release.await(5,SECONDS))})
        h.submit();h.work();val main=h.main.take();val pool=Executors.newFixedThreadPool(3)
        try {
            val publishing=pool.submit{main.run()};assertTrue(admitted.await(5,SECONDS))
            val revoke=pool.submit{revoking.countDown();h.session.lock()};assertTrue(revoking.await(5,SECONDS))
            pool.submit{DocumentNamespaceLane.drainAndFence{}}.get(2,SECONDS)
            release.countDown();publishing.get(5,SECONDS);revoke.get(5,SECONDS);main.run()
            assertEquals(1,h.callbacks);assertEquals(SessionState.LOCKED,h.session.snapshot().state)
            assertEquals(1,h.physical);assertEquals("FILE_PUBLISHED",h.phase)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun callbackMayReenterSessionAndQueueFurtherUIRead() {
        val h=H();var reads=0;var readCallbacks=0
        h.submit{
            h.callbacks++;h.session.userInteraction();assertEquals(SessionState.READY,h.session.snapshot().state)
            h.dispatch.run<Unit>({readCallbacks++}){reads++;DocumentResult.Value(Unit)}
        }
        h.work();h.main.take().run();h.work();h.main.take().run()
        assertEquals(1,h.callbacks);assertEquals(1,reads);assertEquals(1,readCallbacks);assertEquals(1,h.physical)
    }
    @Test fun callbackMayReentrantlyRevokeButCannotReplayOrStartNewWork() {
        val h=H();h.submit{h.callbacks++;h.session.lock()};h.work();val main=h.main.take()
        main.run();main.run();h.submit()
        assertEquals(SessionState.LOCKED,h.session.snapshot().state);assertTrue(h.worker.tasks.isEmpty())
        assertEquals(1,h.callbacks);assertEquals(1,h.physical)
    }
    @Test fun callbackExceptionConsumesDeliveryWithoutRollbackOrReplay() {
        val h=H();h.submit{h.callbacks++;error("synthetic callback")};h.work();val main=h.main.take()
        assertThrows(IllegalStateException::class.java){main.run()};main.run()
        assertEquals(1,h.callbacks);assertEquals(1,h.physical);assertEquals(0,h.blocks);assertEquals("FILE_PUBLISHED",h.phase)
    }
    @Test fun revokeBeforeMainAndNewGenerationCannotResurrectOldResponse() {
        val h=H();h.submit();h.work();val main=h.main.take();val old=h.session.snapshot().generation
        h.session.lock();h.session.authenticate(Experiment.PER_OPERATION)
        assertEquals(SessionState.READY,h.session.snapshot().state);assertNotEquals(old,h.session.snapshot().generation)
        main.run();main.run();assertEquals(0,h.callbacks);assertEquals(1,h.physical)
    }
    @Test fun storageBlockedStillOverridesCurrentLane() {
        val h=H();h.submit();h.work();h.session.storageFailure();h.main.take().run()
        assertTrue(DocumentNamespaceLane.isCurrent(h.ticket));assertEquals(0,h.callbacks)
        assertEquals(SessionState.BLOCKED,h.session.snapshot().state);assertEquals(1,h.physical)
    }
    @Test fun freshMaintenanceFenceRejectsOldMainWithoutDuplicatingWork() {
        val h=H();h.submit();val work=h.worker.take();work.run();work.run();val main=h.main.take()
        h.onMaintenance={DocumentNamespaceLane.drainAndFence{}}
        h.session.lock();h.session.authenticate(Experiment.PER_OPERATION)
        main.run();main.run();assertEquals(0,h.callbacks);assertEquals(1,h.physical)
        h.submit();h.work();h.main.take().run();assertEquals(1,h.callbacks);assertEquals(2,h.physical)
    }
    @Test fun admissionsDoNotIntroduceAnotherEpochOrRestoreFencedTicket() {
        val ticket=DocumentNamespaceLane.ticket()
        assertTrue(DocumentNamespaceLane.tryAdmitPublication(ticket));assertEquals(ticket,DocumentNamespaceLane.ticket())
        assertTrue(DocumentNamespaceLane.tryAdmitPublication(ticket));DocumentNamespaceLane.drainAndFence{}
        assertEquals(Math.addExact(ticket,1),DocumentNamespaceLane.ticket())
        assertFalse(DocumentNamespaceLane.tryAdmitPublication(ticket))
    }
    @Test fun fenceMakesProgressDespiteConcurrentAdmissionAttempts() {
        val ticket=DocumentNamespaceLane.ticket();val started=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(2)
        try {
            val admitting=pool.submit{started.countDown();repeat(10_000){DocumentNamespaceLane.tryAdmitPublication(ticket)}}
            assertTrue(started.await(5,SECONDS))
            pool.submit{DocumentNamespaceLane.drainAndFence{}}.get(2,SECONDS)
            admitting.get(5,SECONDS)
            assertFalse(DocumentNamespaceLane.isCurrent(ticket));assertFalse(DocumentNamespaceLane.tryAdmitPublication(ticket))
        } finally {pool.shutdownNow()}
    }
}
