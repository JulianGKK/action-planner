package org.pag.data.prototype

import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.data.prototype.b4.*
import org.pag.domain.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/** Only the Room-3.0.3 blocker. Scheduling uses a verified name for TEST control only;
 * the production handler classifies the exact own exception type, never a coroutine name.
 */
class B4RoomBlockerTest {
    private val fixture = B4TestFixture()
    @After fun releaseTestKeys() = fixture.close()
    private fun initialize() { fixture.room(creates = true) { } }
    private fun job() = B4Job(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1, B4Operation.IMPORT, B4Phase.IMPORT_RESERVED)
    private fun expected(work: () -> Unit) {
        val error = runCatching(work).exceptionOrNull()
        assertTrue("B4-EXPECTED-ACCESS-DENIAL", error is StorageAccessRevoked ||
            (error is B4Exception && error.id == B4Id.EXPIRED))
    }
    private fun status(store: B4RoomStore) = runBlocking { withTimeout(5000) { store.awaitBackground() } }
    private fun persisted(job: B4Job) {
        fixture.freshSession()
        fixture.room { store -> assertEquals(listOf(job), store.snapshot().jobs) }
    }

    @Test fun expiryAfterCommitBeforeScheduledRefreshIsControlled() {
        initialize(); val control = RefreshControl(); val job = job(); var completed = false
        expected {
            fixture.room(hooks = B4RoomHooks(control)) { store ->
                store.reserve(job); control.awaitPending()
                fixture.time = 100; control.release()
                assertEquals(B4Background.EXPECTED_REVOCATION, status(store))
                expected { store.snapshot() }
                store.close(); store.close(); assertTrue(store.resourcesClosed())
                expected { store.requireSuccessfulCompletion() }
                completed = true
            }
        }
        assertTrue(completed); assertEquals(SessionState.LOCKED, fixture.session.snapshot().state)
        persisted(job)
    }

    @Test fun expiryDuringRefreshAccessIsControlled() = accessRace(false)
    @Test fun generationChangeDuringRefreshAccessIsControlled() = accessRace(true)
    private fun accessRace(reauth: Boolean) {
        initialize(); val control = RefreshControl(); val gate = AccessGate(); val job = job(); var completed = false
        val old = fixture.context
        expected {
            fixture.room(hooks = B4RoomHooks(control, gate::check)) { store ->
                store.reserve(job); control.awaitPending(); gate.arm()
                control.release(); gate.awaitEntry()
                if (reauth) fixture.freshSession() else fixture.time = 100
                gate.release()
                assertEquals(B4Background.EXPECTED_REVOCATION, status(store))
                expected { store.snapshot() }; store.close(); assertTrue(store.resourcesClosed())
                completed = true
            }
        }
        assertTrue(completed); assertFalse(old.check(AccessPoint.COMMIT))
        persisted(job)
    }

    @Test fun closeWhileRefreshPendingCancelsAndDrainsIt() {
        initialize(); val control = RefreshControl(); val job = job()
        fixture.room(hooks = B4RoomHooks(control)) { store ->
            store.reserve(job); control.awaitPending()
            val closer = CloseThread(store)
            closer.awaitStart(); control.release(); closer.awaitDone()
            assertTrue(store.resourcesClosed()); assertEquals(B4Background.NONE, store.background)
            expected { store.snapshot() }; store.close()
        }
        persisted(job)
    }

    @Test fun closeWhileRefreshIsRunningWaitsForBarrierWithoutGrantingExpiredAccess() {
        initialize(); val control = RefreshControl(); val gate = AccessGate(); val job = job(); var completed = false
        expected {
            fixture.room(hooks = B4RoomHooks(control, gate::check)) { store ->
                store.reserve(job); control.awaitPending(); gate.arm(); control.release(); gate.awaitEntry()
                fixture.time = 100
                val closer = CloseThread(store); closer.awaitStart()
                assertFalse("B4-CLOSE-MUST-WAIT", closer.finished())
                gate.release(); closer.awaitDone()
                assertEquals(B4Background.EXPECTED_REVOCATION, status(store))
                assertTrue(store.resourcesClosed()); completed = true
            }
        }
        assertTrue(completed); persisted(job)
    }

    @Test fun closeImmediatelyAfterWriteAndRepeatedCloseReleaseAllHandles() {
        initialize(); val job = job()
        fixture.room { store ->
            store.reserve(job); store.close(); store.close()
            assertTrue(store.resourcesClosed()); expected { store.snapshot() }
        }
        persisted(job)
    }

    @Test fun oldDaoConnectionAndRoomCannotReopenAfterClose() {
        initialize()
        fixture.room { store ->
            val dao = store.db.jobs()
            val connection = store.driver.open(fixture.db.path)
            store.close()
            assertNotNull(runCatching { runBlocking { dao.jobs() } }.exceptionOrNull())
            assertNotNull(runCatching { store.driver.open(fixture.db.path) }.exceptionOrNull())
            assertNotNull(runCatching { connection.prepare("SELECT 1").use { it.step() } }.exceptionOrNull())
            assertTrue(store.resourcesClosed()); expected { store.snapshot() }
        }
    }

    @Test fun rapidWritesCoalesceRefreshAndKeepAllCommittedPhases() {
        initialize(); val control = RefreshControl(); val original = job()
        val writing = original.next(B4Phase.STAGING_WRITING)
        val closed = writing.next(B4Phase.STAGING_CLOSED)
        fixture.room(hooks = B4RoomHooks(control)) { store ->
            store.reserve(original); control.awaitPending()
            store.advance(original, B4Phase.STAGING_WRITING)
            store.advance(writing, B4Phase.STAGING_CLOSED)
            assertEquals(1, control.scheduled.get())
            val closer = CloseThread(store); closer.awaitStart(); control.release(); closer.awaitDone()
            assertTrue(store.resourcesClosed())
        }
        persisted(closed)
    }

    @Test fun unexpectedIoInActualRefreshIsBlockedAndNeverClassifiedAsRevocation() = unexpectedRefresh {
        IOException("B4-INJECTED-BACKGROUND-IO")
    }
    @Test fun unexpectedSqliteInActualRefreshIsBlockedInsteadOfCaughtByRoom() = unexpectedRefresh {
        android.database.sqlite.SQLiteException("B4-INJECTED-BACKGROUND-SQLITE")
    }
    private fun unexpectedRefresh(error: () -> Exception) {
        initialize(); val control = RefreshControl(); val fault = AtomicReference<(() -> Unit)?>(null)
        val job = job(); lateinit var old: B4RoomStore
        expected { fixture.room(hooks = B4RoomHooks(control, { fault.getAndSet(null)?.invoke() })) { store ->
            old = store; store.reserve(job); control.awaitPending()
            fault.set { throw error() }
            control.release()
            assertEquals(B4Background.UNEXPECTED_BACKGROUND_FAULT, status(store))
            assertTrue(fixture.context.check(AccessPoint.COMMIT)) // Genuine fault, not an expired session.
            backgroundDenied { store.snapshot() }
            backgroundDenied { store.advance(job, B4Phase.STAGING_WRITING) }
            store.close(); assertTrue(store.resourcesClosed())
            backgroundDenied { store.requireSuccessfulCompletion() }
            fixture.maintenanceDone!!(false)
            assertEquals(SessionState.BLOCKED, fixture.session.snapshot().state)
        } }
        // The key consumer also denies publication after central Maintenance failure.
        // This callback therefore terminates with an access denial, handled by the test below.
        backgroundDenied { old.snapshot() }
        persisted(job)
    }

    @Test fun shortStepStoreHasNoRoomHandleAcrossFileWorkOrExpiry() {
        fixture.run(creates = true) { _, _ -> }
        var reached = false
        expected {
            fixture.run(fault = { if (it == B4Point.IMPORT_AFTER_JOB) {
                reached = true; fixture.time = 100
            } }) { p, _ -> p.importSynthetic(1) }
        }
        assertTrue(reached); fixture.freshSession()
        fixture.run { p, store ->
            assertEquals(B4Id.ABORTED, p.reconcile()); assertTrue(store.snapshot().jobs.isEmpty())
        }
    }

    private fun backgroundDenied(work: () -> Unit) {
        val failure = runCatching(work).exceptionOrNull()
        assertTrue("B4-BACKGROUND-MUST-BLOCK", failure is B4Exception && failure.id == B4Id.BACKGROUND)
    }
}

private class RefreshControl : CoroutineDispatcher() {
    private val queued = ConcurrentLinkedQueue<Pair<CoroutineContext, Runnable>>()
    private val pending = CountDownLatch(1)
    @Volatile private var holding = true
    val scheduled = AtomicInteger()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // Name is TEST scheduling only; never used by the actual fault classifier.
        if (holding && context[CoroutineName]?.name == "Room Invalidation Tracker Refresh") {
            queued.add(context to block); scheduled.incrementAndGet(); pending.countDown()
        } else Dispatchers.IO.dispatch(context, block)
    }
    fun awaitPending() { assertTrue("B4-REFRESH-NOT-SCHEDULED", pending.await(5, TimeUnit.SECONDS)) }
    fun release() {
        holding = false
        while (true) { val next = queued.poll() ?: break; Dispatchers.IO.dispatch(next.first, next.second) }
    }
}
private class AccessGate {
    private val action = AtomicReference<(() -> Unit)?>(null)
    private val entered = CountDownLatch(1)
    private val finish = CountDownLatch(1)
    fun arm() { action.set { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) { "B4-TEST-GATE-TIMEOUT" } } }
    fun check() { action.getAndSet(null)?.invoke() }
    fun awaitEntry() { assertTrue("B4-REFRESH-NO-ACCESS", entered.await(5, TimeUnit.SECONDS)) }
    fun release() { finish.countDown() }
}
private class CloseThread(store: B4RoomStore) {
    private val started = CountDownLatch(1)
    private val done = CountDownLatch(1)
    private val error = AtomicReference<Throwable?>(null)
    init { Thread {
        started.countDown()
        try { store.close() } catch (failure: Throwable) { error.set(failure) } finally { done.countDown() }
    }.start() }
    fun awaitStart() { assertTrue(started.await(5, TimeUnit.SECONDS)) }
    fun finished() = done.count == 0L
    fun awaitDone() { assertTrue("B4-CLOSE-TIMEOUT", done.await(5, TimeUnit.SECONDS)); assertNull(error.get()) }
}
