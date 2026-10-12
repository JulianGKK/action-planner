package org.pag.data.product

import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*

/** Actual coordinator/grant/context. Only the monotonic clock and auth adapters are fixtures. */
class LeadershipKeyAdmissionTest {
    private class Fixture(policy: SessionPolicy = SessionPolicy(180000, 180000)) {
        val now = AtomicLong(0)
        val environment = AtomicBoolean(true)
        val pause = AtomicBoolean(false)
        val sampled = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val session = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                complete(AuthenticationOutcome.Granted(object : SyntheticOperation {
                    override fun perform() = OperationResult.SUCCEEDED
                    override fun release() {}
                }, now.get()))
                return Cancellation {}
            }
        }, Maintenance { it(true); Subscription {} }, MonotonicTime { now.get() }, policy, { environment.get() })
        init { session.foreground(true); session.authenticate(Experiment.PER_OPERATION) }
        val context = session.acquire(AccessScope.READY)!!
        val grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.IDENTITY,
            context, MonotonicTime {
                val sample = now.get()
                if (pause.compareAndSet(true, false)) { sampled.countDown(); await(resume) }
                sample
            }, ByteArray(32) { 7 }, "synthetic-epoch", 0, byteArrayOf(), true)
        val called = AtomicBoolean(false)
        val lease = AtomicReference<ByteArray?>()
        val failure = AtomicReference<Throwable?>()
        val consumerDone = CountDownLatch(1)
        fun consumer(): Thread = Thread {
            try {
                assertThrows(SensitiveProblem::class.java) {
                    grant.consume { called.set(true); lease.set(it) }
                }
            } catch (t: Throwable) { failure.compareAndSet(null, t) }
            finally { consumerDone.countDown() }
        }.apply { isDaemon = true; name = "key-admission-consumer" }
        fun assertDenied() {
            await(consumerDone); failure.get()?.let { throw AssertionError(it) }
            assertFalse("Invalid generation/expired grant reached key callback", called.get())
            assertNull("Rejected admission must not hand a lease to work", lease.get())
        }
    }

    @Test fun generationInvalidatedBeforeFinalAdmissionNeverCallsWork() {
        val f = Fixture(); val invalidated = CountDownLatch(1); val allowCancel = CountDownLatch(1)
        val locked = CountDownLatch(1)
        f.session.observe {
            if (it.state != SessionState.READY) { invalidated.countDown(); await(allowCancel); f.grant.cancel() }
        }
        f.pause.set(true); val consumer = f.consumer(); consumer.start(); await(f.sampled)
        val locker = Thread {
            try { f.session.lock() } catch (t: Throwable) { f.failure.set(t) } finally { locked.countDown() }
        }.apply { isDaemon = true }
        locker.start(); await(invalidated)
        try {
            f.resume.countDown()
            // Old code reaches this wait only AFTER giving the key to work. Safe
            // admission must wait for coordinator ownership BEFORE key acquisition.
            blockedOn(consumer, f.session)
        } finally { allowCancel.countDown() }
        await(locked); f.assertDenied()
        assertFalse(f.context.check(AccessPoint.COMMIT))
    }

    @Test fun cancelAlreadyWaitingCannotBeOvertakenForNewAdmission() = cancelWaiterRace()

    private fun cancelWaiterRace() {
        val f = Fixture(); val invalidated = CountDownLatch(1); val locked = CountDownLatch(1)
        f.session.observe { if (it.state != SessionState.READY) { invalidated.countDown(); f.grant.cancel() } }
        f.pause.set(true); val consumer = f.consumer(); consumer.start(); await(f.sampled)
        val locker = Thread {
            try { f.session.lock() } catch (t: Throwable) { f.failure.set(t) } finally { locked.countDown() }
        }.apply { isDaemon = true; name = "already-waiting-cancel" }
        synchronized(f.grant) {
            locker.start(); await(invalidated); blockedOn(locker, f.grant)
            f.resume.countDown(); blockedOn(consumer, f.grant)
        }
        await(locked); f.assertDenied()
    }

    @Test fun expiryCrossesBetweenEarlyCheckAndAdmissionNeverCallsWork() {
        val f = Fixture(); f.now.set(59999); f.pause.set(true)
        val consumer = f.consumer(); consumer.start(); await(f.sampled)
        synchronized(f.grant) {
            f.resume.countDown(); blockedOn(consumer, f.grant)
            f.now.set(60000)
        }
        f.assertDenied()
    }

    @Test fun cancelWaitingRaceRepeated128TimesNeverIssuesKey() { repeat(128) { cancelWaiterRace() } }

    @Test fun admittedBeforeRevokeWorkMayFinishAndLeaseIsWiped() {
        val f = Fixture(); var lease: ByteArray? = null; var finished = false
        f.session.observe { if (it.state != SessionState.READY) f.grant.cancel() }
        assertThrows(SensitiveProblem::class.java) {
            f.grant.consume { key ->
                assertFalse(Thread.holdsLock(f.session)); assertFalse(Thread.holdsLock(f.grant))
                lease = key; assertTrue(key.any { it != 0.toByte() })
                f.session.lock() // No lock waits for already-admitted CPU work.
                finished = true
            }
        }
        assertTrue(finished); assertTrue(lease!!.all { it == 0.toByte() })
        assertEquals(SessionState.LOCKED, f.session.snapshot().state)
    }

    @Test fun revokeBeforeConsumeNeverCallsWork() {
        val f = Fixture(); f.session.observe { if (it.state != SessionState.READY) f.grant.cancel() }
        f.session.lock(); f.consumer().start(); f.assertDenied()
    }

    @Test fun admissionAt59999MayFinishAfter60000ButCannotReturnStaleResult() {
        val f = Fixture(); f.now.set(59999); var called = false; var lease: ByteArray? = null
        assertThrows(SensitiveProblem::class.java) {
            f.grant.consume { key ->
                called = true; lease = key; assertEquals(59999L, f.now.get())
                f.now.set(60000) // Admission already completed, work is allowed to finish.
            }
        }
        assertTrue(called); assertTrue(lease!!.all { it == 0.toByte() })
    }

    @Test fun admissionAt60000NeverCallsWork() {
        val f = Fixture(); f.now.set(60000); f.consumer().start(); f.assertDenied()
    }

    @Test fun callbackExceptionWipesLease() {
        val f = Fixture(); var lease: ByteArray? = null
        assertThrows(IllegalStateException::class.java) {
            f.grant.consume { lease = it; throw IllegalStateException("synthetic callback failure") }
        }
        assertTrue(lease!!.all { it == 0.toByte() })
    }

    @Test fun absoluteSessionDeadlineCrossesBeforeAdmission() = sessionGateChangesBeforeAdmission(SessionPolicy(10, 180000)) { it.now.set(10) }
    @Test fun idleSessionDeadlineCrossesBeforeAdmission() = sessionGateChangesBeforeAdmission(SessionPolicy(180000, 10)) { it.now.set(10) }

    private fun sessionGateChangesBeforeAdmission(policy: SessionPolicy, change: (Fixture) -> Unit) {
        val f = Fixture(policy); f.session.observe { if (it.state != SessionState.READY) f.grant.cancel() }
        f.pause.set(true); val consumer = f.consumer(); consumer.start(); await(f.sampled)
        synchronized(f.grant) { f.resume.countDown(); blockedOn(consumer, f.grant); change(f) }
        f.assertDenied(); assertEquals(SessionState.LOCKED, f.session.snapshot().state)
    }

    @Test fun liveEnvironmentLossBeforeAdmissionNeverCallsWork() =
        sessionGateChangesBeforeAdmission(SessionPolicy(180000, 180000)) { it.environment.set(false) }

    @Test fun finalAdmissionRechecksTimeAfterWaitingForGrantMonitor() {
        val f = Fixture(); val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>()
        var copied = false
        f.now.set(59999)
        val consumer = Thread {
            try {
                val result = f.context.admitKey(60000) { authorize ->
                    synchronized(f.grant) { if (authorize()) { copied = true; true } else null }
                }
                assertNull(result)
            } catch (t: Throwable) { failure.set(t) } finally { done.countDown() }
        }.apply { isDaemon = true }
        synchronized(f.grant) { consumer.start(); blockedOn(consumer, f.grant); f.now.set(60000) }
        await(done); failure.get()?.let { throw AssertionError(it) }; assertFalse(copied)
    }

    @Test fun escapedAdmissionGuardCannotAuthorizeLater() {
        val f = Fixture(); var escaped: (() -> Boolean)? = null
        f.context.admitKey<Unit>(60000) { escaped = it; null }
        assertThrows(IllegalStateException::class.java) { escaped!!() }
    }

    @Test fun admissionGuardCannotRunOnAnotherThread() {
        val f = Fixture(); val done = CountDownLatch(1); val failure = AtomicReference<Throwable?>()
        f.context.admitKey<Unit>(60000) { authorize ->
            Thread {
                try { assertThrows(IllegalStateException::class.java) { authorize() } }
                catch (t: Throwable) { failure.set(t) } finally { done.countDown() }
            }.apply { isDaemon = true }.start()
            await(done); null
        }
        failure.get()?.let { throw AssertionError(it) }
    }

    @Test fun copyIsWipedIfAdmissionAdapterThrowsAfterCopy() {
        val f = Fixture(); var captured: ByteArray? = null; var called = false
        val context = object : StorageContext by f.context {
            override fun <T> admitKey(validUntilMillis: Long, acquire: (() -> Boolean) -> T?): T? {
                val result = f.context.admitKey(validUntilMillis, acquire)
                captured = result as? ByteArray
                throw IllegalStateException("synthetic adapter failure")
            }
        }
        val grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.IDENTITY,
            context, MonotonicTime { f.now.get() }, ByteArray(32) { 7 }, "synthetic-epoch", 0, byteArrayOf(), true)
        assertThrows(IllegalStateException::class.java) { grant.consume { called = true } }
        assertFalse(called); assertNotNull(captured); assertTrue(captured!!.all { it == 0.toByte() })
    }

    @Test fun providerWithoutAtomicAdmissionFailsClosed() {
        val context = object : StorageContext {
            override val generation = 1L
            override val scope = AccessScope.READY
            override fun check(point: AccessPoint) = true
            override fun publish(result: () -> Unit): Boolean { result(); return true }
        }
        val grant = LeadershipGrant("synthetic-action", "synthetic-person", SensitiveArea.IDENTITY,
            context, MonotonicTime { 0 }, ByteArray(32), "synthetic-epoch", 0, byteArrayOf(), true)
        assertThrows(SensitiveProblem::class.java) { grant.consume { fail("Ungated provider issued key") } }
        assertFalse(grant.valid())
    }

    companion object {
        private fun await(latch: CountDownLatch) { assertTrue("Controlled operation timed out", latch.await(3, TimeUnit.SECONDS)) }
        @Suppress("DEPRECATION")
        private fun blockedOn(thread: Thread, monitor: Any) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            val bean = ManagementFactory.getThreadMXBean()
            while (true) {
                val info = bean.getThreadInfo(thread.id)
                if (info?.threadState == Thread.State.BLOCKED && info.lockInfo?.identityHashCode == System.identityHashCode(monitor)) return
                check(System.nanoTime() < end) { "Thread did not reach expected security monitor" }
                Thread.yield()
            }
        }
    }
}
