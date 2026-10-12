package org.pag.data.product

import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*

/** Real grant and coordinator context; no substituted storage authorization/monitor. */
class LeadershipSessionConcurrencyTest {
    @Suppress("DEPRECATION") // JVM 17 Thread.id used only for monitor-owner evidence.
    private fun race(background: Boolean = false, consume: Boolean = false, expiry: Boolean = false,
        close: Boolean = false, restart: Boolean = false, newReady: Boolean = false) {
        var auth: ((AuthenticationOutcome) -> Unit)? = null
        var maintenance: ((Boolean) -> Unit)? = null
        var released = false
        val operation = object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() { released = true }
        }
        val session = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                auth = complete; return Cancellation {}
            }
        }, Maintenance { maintenance = it; Subscription {} }, MonotonicTime { 0 }, SessionPolicy(180000, 30000))
        session.foreground(true)
        session.authenticate(Experiment.PER_OPERATION)
        auth!!(AuthenticationOutcome.Granted(operation, 0)); maintenance!!(true)
        val context = session.acquire(AccessScope.READY)!!
        var grantTime = 0L
        val grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.IDENTITY,
            context, MonotonicTime { grantTime }, ByteArray(32) { 7 }, "synthetic-epoch", 0, byteArrayOf(), true)
        val keyField = grant.javaClass.getDeclaredField("bytes").apply { isAccessible = true }
        val ownedKey = keyField.get(grant) as ByteArray
        if (expiry) grantTime = 60000
        var payload: SensitiveData? = SensitiveData.Identity("synthetic")
        val cleanupEntered = CountDownLatch(1)
        session.observe {
            if (it.state != SessionState.READY) {
                cleanupEntered.countDown()
                grant.cancel()
                payload = null
            }
        }
        val held = CountDownLatch(1)
        val checkStarted = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val finished = CountDownLatch(2)
        lateinit var checker: Thread
        val locker = Thread {
            try {
                synchronized(session) {
                    held.countDown()
                    assertTrue(checkStarted.await(2, TimeUnit.SECONDS))
                    // Wait for the real check to contend for this exact coordinator monitor.
                    // No sleep or replacement StorageContext; bounded monitor-state inspection.
                    val bean = ManagementFactory.getThreadMXBean()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                    while (true) {
                        val info = bean.getThreadInfo(checker.id)
                        if (info?.threadState == Thread.State.BLOCKED &&
                            info.lockInfo?.identityHashCode == System.identityHashCode(session) &&
                            info.lockOwnerId == Thread.currentThread().id) break
                        check(System.nanoTime() < deadline) { "Grant did not reach coordinator monitor" }
                        Thread.yield()
                    }
                    if (background) session.foreground(false) else session.lock()
                    assertNull(payload); assertTrue(ownedKey.all { it == 0.toByte() })
                    if (restart || newReady) {
                        assertEquals(SessionState.LOCKED, session.snapshot().state)
                        session.authenticate(Experiment.PER_OPERATION)
                        if (newReady) {
                            auth(AuthenticationOutcome.Granted(operation, 0)); maintenance(true)
                        }
                    }
                }
            } catch (t: Throwable) { failure.compareAndSet(null, t) }
            finally { finished.countDown() }
        }.apply { isDaemon = true; name = "coordinator-revoke" }
        checker = Thread {
            try {
                assertTrue(held.await(2, TimeUnit.SECONDS)); checkStarted.countDown()
                if (close) { grant.cancel(); session.userInteraction() }
                else if (consume) assertThrows(SensitiveProblem::class.java) { grant.consume {} }
                else assertFalse(grant.valid())
            } catch (t: Throwable) { failure.compareAndSet(null, t) }
            finally { finished.countDown() }
        }.apply { isDaemon = true; name = "grant-check" }
        locker.start(); checker.start()
        assertTrue("Synchronous coordinator/grant cleanup must terminate", finished.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Concurrent operation failed", it) }
        assertEquals(0L, cleanupEntered.count)
        assertNull(payload); assertFalse(grant.valid())
        assertTrue(released); assertTrue(ownedKey.all { it == 0.toByte() }); assertNull(keyField.get(grant))
        assertEquals(if (newReady) SessionState.READY else if (restart) SessionState.AUTHENTICATING else SessionState.LOCKED,
            session.snapshot().state)
        assertNotEquals(context.generation, session.snapshot().generation)
        AccessPoint.entries.forEach { assertFalse(context.check(it)) }
        assertFalse(context.publish { fail("Old generation published") })
    }

    @Test fun lockWhileRealGrantChecksRealGeneration() = race()
    @Test fun lockWhileConsumerAcquiresLease() = race(consume = true)
    @Test fun lockWhileGrantExpires() = race(expiry = true)
    @Test fun lockWhileSensitiveAreaCloses() = race(close = true)
    @Test fun generationChangeDuringGrantAccess() = race(newReady = true)
    @Test fun synchronousCleanupBeforeRestartAuthentication() = race(restart = true)
    @Test fun backgroundLockWhileGrantChecks() = race(background = true)

    @Test fun revokeDuringConsumerWorkRejectsResultAndWipesLease() {
        // The work callback runs without the grant monitor: cancellation must finish
        // even while it is blocked, and the post-work validity gate denies its result.
        val contextSession = readySession()
        val context = contextSession.acquire(AccessScope.READY)!!
        val grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.CONTACT,
            context, MonotonicTime { 0 }, ByteArray(32) { 7 }, "synthetic-epoch", 0, byteArrayOf(), true)
        contextSession.observe { if (it.state != SessionState.READY) grant.cancel() }
        val entered = CountDownLatch(1); val resume = CountDownLatch(1); val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(); var lease: ByteArray? = null
        Thread {
            try {
                assertThrows(SensitiveProblem::class.java) {
                    grant.consume { bytes ->
                        assertFalse(Thread.holdsLock(grant)); lease = bytes; entered.countDown()
                        assertTrue(resume.await(2, TimeUnit.SECONDS))
                    }
                }
            } catch (t: Throwable) { failure.set(t) } finally { done.countDown() }
        }.apply { isDaemon = true }.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        try { contextSession.lock(); assertFalse(grant.valid()) } finally { resume.countDown() }
        assertTrue(done.await(3, TimeUnit.SECONDS)); failure.get()?.let { throw AssertionError(it) }
        assertTrue(lease!!.all { it == 0.toByte() })
    }

    @Test fun revokeBetweenContextCheckAndLocalKeyCopyDeniesConsumer() {
        val session = readySession()
        val checked = CountDownLatch(1); val resume = CountDownLatch(1)
        var armed = false
        val grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.IDENTITY,
            session.acquire(AccessScope.READY)!!, MonotonicTime {
                if (armed) { checked.countDown(); check(resume.await(3, TimeUnit.SECONDS)) }
                0
            }, ByteArray(32), "synthetic-epoch", 0, byteArrayOf(), true)
        session.observe { if (it.state != SessionState.READY) grant.cancel() }
        armed = true
        val done = CountDownLatch(1); val locked = CountDownLatch(1); val failure = AtomicReference<Throwable?>()
        Thread {
            try { assertThrows(SensitiveProblem::class.java) { grant.consume { fail("Revoked grant copied key") } } }
            catch (t: Throwable) { failure.compareAndSet(null, t) } finally { done.countDown() }
        }.apply { isDaemon = true }.start()
        assertTrue(checked.await(2, TimeUnit.SECONDS))
        Thread {
            try { session.lock() } catch (t: Throwable) { failure.compareAndSet(null, t) }
            finally { locked.countDown() }
        }.apply { isDaemon = true }.start()
        try { assertTrue("Cleanup cannot wait for an external grant clock/check", locked.await(2, TimeUnit.SECONDS)) }
        finally { resume.countDown() }
        assertTrue(done.await(3, TimeUnit.SECONDS)); failure.get()?.let { throw AssertionError(it) }
        assertFalse(grant.valid()); assertEquals(SessionState.LOCKED, session.snapshot().state)
    }

    @Test fun grantCancelHeldDuringCoordinatorCleanupTerminates() {
        val session = readySession()
        val grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.IDENTITY,
            session.acquire(AccessScope.READY)!!, MonotonicTime { 0 }, ByteArray(32), "synthetic-epoch", 0, byteArrayOf(), true)
        val grantHeld = CountDownLatch(1); val cleanupEntered = CountDownLatch(1); val done = CountDownLatch(2)
        val failure = AtomicReference<Throwable?>()
        session.observe { if (it.state != SessionState.READY) { cleanupEntered.countDown(); grant.cancel() } }
        Thread {
            try {
                synchronized(grant) {
                    grant.cancel(); grantHeld.countDown()
                    assertTrue(cleanupEntered.await(2, TimeUnit.SECONDS))
                    // cancel has no external calls and can release this monitor even
                    // when coordinator cleanup is waiting to cancel the same grant.
                }
            } catch (t: Throwable) { failure.compareAndSet(null, t) } finally { done.countDown() }
        }.apply { isDaemon = true }.start()
        Thread {
            try { assertTrue(grantHeld.await(2, TimeUnit.SECONDS)); session.lock() }
            catch (t: Throwable) { failure.compareAndSet(null, t) } finally { done.countDown() }
        }.apply { isDaemon = true }.start()
        assertTrue(done.await(5, TimeUnit.SECONDS)); failure.get()?.let { throw AssertionError(it) }
        assertFalse(grant.valid()); assertEquals(SessionState.LOCKED, session.snapshot().state)
    }

    @Test fun grantAdmissionReadsCoordinatorOnlyWhenAlreadyOwned() {
        lateinit var grant: LeadershipGrant
        var armed = false
        lateinit var session: SessionCoordinator
        session = readySession {
            if (armed && Thread.holdsLock(grant)) assertTrue("Grant acquired before coordinator", Thread.holdsLock(session))
            true
        }
        grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.IDENTITY,
            session.acquire(AccessScope.READY)!!, MonotonicTime { 0 }, ByteArray(32), "synthetic-epoch", 0, byteArrayOf(), true)
        armed = true
        assertTrue(grant.valid()); grant.consume { assertFalse(Thread.holdsLock(grant)) }
        session.lock(); assertFalse(grant.valid())
    }

    private fun readySession(environment: () -> Boolean = { true }): SessionCoordinator {
        val session = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                complete(AuthenticationOutcome.Granted(object : SyntheticOperation {
                    override fun perform() = OperationResult.SUCCEEDED
                    override fun release() {}
                }, 0)); return Cancellation {}
            }
        }, Maintenance { it(true); Subscription {} }, MonotonicTime { 0 }, SessionPolicy(180000, 30000), environment)
        session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        return session
    }
}
