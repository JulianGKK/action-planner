package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*

/** Real coordinator/clock and ViewModel, deferred auth/maintenance/repository ports. */
class SessionRestartTest {
    private var now = 0L
    private var environment = true
    private val authResults = mutableListOf<(AuthenticationOutcome) -> Unit>()
    private val maintenanceResults = mutableListOf<(Boolean) -> Unit>()
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            authResults.add(complete); return Cancellation {}
        }
    }, Maintenance { complete -> maintenanceResults.add(complete); Subscription {} },
        MonotonicTime { now }, ProductSessionTiming.policy, { environment })
    private class Operation : SyntheticOperation {
        var released = false
        override fun perform() = OperationResult.SUCCEEDED
        override fun release() { released = true }
    }
    private val row = Action("synthetic-action", "synthetic", "synthetic", 1, 2, 0, ActionStatus.PREPARATION)
    private val loads = mutableListOf<(List<Action>) -> Unit>()
    private var detail: ((Action?) -> Unit)? = null
    private val repository = object : ActionRepository {
        override fun listActions(publish: (List<Action>) -> Unit) { loads.add(publish) }
        override fun getAction(id: String, publish: (Action?) -> Unit) { detail = publish }
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {}
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {}
    }
    private val person = Person("synthetic-person", row.id, "synthetic-01", 0, editRevision = 0)
    private val people = object : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { publish(PersonResult.Value(listOf(person))) }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) { publish(PersonResult.Value(person)) }
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private var activeGrant: SensitiveGrant? = null
    private val leadership = object : LeadershipRepository {
        override fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit) {
            publish(SensitiveResult.Value(SensitiveInventory(SensitiveState.READY, setOf(SensitiveArea.IDENTITY))))
        }
        override fun open(actionId: String, personId: String, area: SensitiveArea, passphrase: CharArray,
            publish: (SensitiveResult<SensitiveOpened>) -> Unit) {
            passphrase.fill('\u0000')
            // Test-only reflection keeps data's grant internal; this is the production
            // class with the actual acquired coordinator context, not a fake grant.
            val constructor = Class.forName("org.pag.data.product.LeadershipGrant").declaredConstructors
                .single { it.parameterCount == 12 }
            val grant = constructor.newInstance(actionId, personId, area, session.acquire(AccessScope.READY)!!,
                MonotonicTime { now }, ByteArray(32) { 7 }, "synthetic-epoch", 0L, byteArrayOf(), true, null, null) as SensitiveGrant
            activeGrant = grant
            publish(SensitiveResult.Value(SensitiveOpened(grant, SensitiveData.Identity("synthetic"))))
        }
        override fun revoke() { activeGrant?.cancel() }
        override fun setup(actionId: String, passphrase: CharArray, confirmation: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("unused") }
        override fun save(grant: SensitiveGrant, data: SensitiveData, publish: (SensitiveResult<Unit>) -> Unit) { error("unused") }
        override fun deleteArea(grant: SensitiveGrant, publish: (SensitiveResult<Unit>) -> Unit) { error("unused") }
        override fun deletePerson(actionId: String, personId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("unused") }
        override fun deleteAction(actionId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("unused") }
    }
    private var starts = 0
    private val model = ProductViewModel(session, repository, ProductStorageState.READY_TO_OPEN, people, leadership) { initialize ->
        assertFalse(initialize)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertNull(modelState().selected)
        assertNull(modelState().remainingMillis)
        assertNull(modelState().sensitiveOpened)
        activeGrant?.let { assertFalse(it.valid()) }
        starts++
        session.authenticate(Experiment.STORAGE_UNWRAP)
    }
    private fun modelState(): ProductViewModel = model
    private fun readyNearExpiry(remaining: Long = 20_000) {
        session.foreground(true)
        session.authenticate(Experiment.STORAGE_UNWRAP)
        authResults.last()(AuthenticationOutcome.Granted(Operation(), now))
        maintenanceResults.last()(true)
        // Keep the actual READY inactivity contract satisfied throughout the old session.
        while (now < 180_000 - remaining) {
            now = minOf(now + 10_000, 180_000 - remaining)
            session.userInteraction()
        }
        model.refreshCountdown()
    }
    @Test fun realGrantConcurrentWithRestartTapAndProductObserverCleanup() = realGrantRace()
    @Test fun realGrantConcurrentWithNormalLockAndProductObserverCleanup() = realGrantRace(restart = false)
    @Test fun realGrantConcurrentWithBackgroundLockAndProductObserverCleanup() = realGrantRace(restart = false, background = true)
    @Test fun realSensitiveAreaCloseConcurrentWithLock() = realGrantRace(restart = false, close = true)

    private fun realGrantRace(restart: Boolean = true, background: Boolean = false, close: Boolean = false) {
        readyNearExpiry()
        model.detail(row.id); detail!!(row); model.personDetail(person.id)
        model.requestSensitive(SensitiveArea.IDENTITY)
        model.openSensitive("synthetic fixture".toCharArray())
        val grant = activeGrant!!
        assertTrue(grant.valid()); assertNotNull(model.sensitiveOpened)
        val held = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(2)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        lateinit var checker: Thread
        val tap = Thread {
            try {
                synchronized(session) {
                    held.countDown()
                    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
                    while (checker.state != Thread.State.BLOCKED) {
                        check(System.nanoTime() < deadline) { "Grant check did not contend for coordinator" }
                        Thread.yield()
                    }
                    if (restart) model.restartSession() else if (background) session.foreground(false) else model.lock()
                }
            } catch (t: Throwable) { failure.compareAndSet(null, t) } finally { finished.countDown() }
        }.apply { isDaemon = true }
        checker = Thread {
            try {
                assertTrue(held.await(2, java.util.concurrent.TimeUnit.SECONDS))
                if (close) model.closeSensitive() else assertFalse(grant.valid())
            }
            catch (t: Throwable) { failure.compareAndSet(null, t) } finally { finished.countDown() }
        }.apply { isDaemon = true }
        tap.start(); checker.start()
        assertTrue("Product lock/restart/area-close must terminate", finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError(it) }
        assertEquals(if (restart) 1 else 0, starts)
        assertEquals(if (restart) SessionState.AUTHENTICATING else SessionState.LOCKED, model.snapshot.state)
        assertFalse(grant.valid()); assertNull(model.sensitiveOpened); assertNull(model.sensitiveArea)
        assertNull(model.selectedPerson); assertNull(model.selected); assertNull(model.remainingMillis)
        assertNull(model.feedback); assertFalse(model.tasks.form); assertNull(model.tasks.selected)
    }

    @Test fun visibilityUsesPositiveReadyTimeOnly() {
        assertFalse(sessionRestartVisible(SessionState.READY, 30_001))
        assertTrue(sessionRestartVisible(SessionState.READY, 30_000))
        assertTrue(sessionRestartVisible(SessionState.READY, 1))
        for (time in listOf(null, 0L, -1L)) assertFalse(sessionRestartVisible(SessionState.READY, time))
        for (state in SessionState.entries.filter { it != SessionState.READY })
            assertFalse(sessionRestartVisible(state, 20_000))
    }
    @Test fun tapRevokesOldContextAndEditorsBeforeOneNormalAuthRequest() {
        readyNearExpiry()
        loads.last()(listOf(row)); model.detail(row.id); detail!!(row); model.edit()
        val oldGeneration = session.snapshot().generation
        val old = session.acquire(AccessScope.READY)!!
        model.restartSession(); model.restartSession()
        assertEquals(1, starts)
        assertEquals(2, authResults.size) // initial unlock plus exactly one restart
        assertEquals(SessionState.AUTHENTICATING, session.snapshot().state)
        assertNotEquals(oldGeneration, session.snapshot().generation)
        assertFalse(old.check(AccessPoint.COMMIT))
        var published = false
        assertFalse(old.publish { published = true }); assertFalse(published)
        assertNull(model.editing); assertNull(model.selected); assertTrue(model.actions.isEmpty())
        assertNull(model.remainingMillis); assertNull(session.remainingAbsoluteMillis())
        assertEquals(1, maintenanceResults.size) // tap cannot start maintenance or READY
    }
    @Test fun cancelNeverReturnsToOldSessionOrContent() {
        readyNearExpiry(); val stale = loads.last(); model.restartSession()
        now += 5_000
        authResults.last()(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED))
        stale(listOf(row))
        assertEquals(SessionState.LOCKED, model.snapshot.state)
        assertNull(model.remainingMillis); assertTrue(model.actions.isEmpty()); assertNull(model.selected)
        assertEquals(1, starts)
    }
    @Test fun errorUsesExistingBlockedFailClosedContract() {
        readyNearExpiry(); model.restartSession()
        authResults.last()(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR))
        assertEquals(SessionState.BLOCKED, model.snapshot.state)
        assertNull(model.remainingMillis); assertTrue(model.actions.isEmpty()); assertEquals(1, starts)
    }
    @Test fun successRunsMaintenanceAndLoadsFreshWithoutNavigationContinuation() {
        readyNearExpiry(); val stale = loads.last()
        model.detail(row.id); detail!!(row)
        val oldGeneration = session.snapshot().generation
        model.restartSession()
        now += 5_000
        authResults.last()(AuthenticationOutcome.Granted(Operation(), now))
        assertEquals(SessionState.MAINTENANCE, model.snapshot.state)
        assertEquals(2, maintenanceResults.size); assertNull(model.remainingMillis); assertNull(model.selected)
        now += 2_000
        maintenanceResults.last()(true)
        assertEquals(SessionState.READY, model.snapshot.state)
        assertNotEquals(oldGeneration, model.snapshot.generation)
        assertEquals(178_000L, model.remainingMillis) // deadline starts at auth, maintenance consumes it
        assertNull(model.selected); assertNull(model.editing)
        stale(listOf(row)); assertEquals(ActionListState.Loading, model.actionListState)
        loads.last()(emptyList()); assertEquals(ActionListState.Empty, model.actionListState)
        val generation = model.snapshot.generation
        val duplicate = Operation()
        authResults.last()(AuthenticationOutcome.Granted(duplicate, now))
        assertTrue(duplicate.released); assertEquals(generation, model.snapshot.generation)
    }
    @Test fun staleVisibleButtonCannotRestartExpiredSession() {
        readyNearExpiry(1)
        now++
        model.restartSession()
        assertEquals(SessionState.LOCKED, model.snapshot.state)
        assertEquals(0, starts); assertEquals(1, authResults.size); assertNull(model.remainingMillis)
    }
    @Test fun tapOutsideThresholdDoesNothing() {
        readyNearExpiry(30_001)
        val generation = model.snapshot.generation
        model.restartSession()
        assertEquals(SessionState.READY, model.snapshot.state)
        assertEquals(generation, model.snapshot.generation); assertEquals(0, starts)
        assertEquals(30_001L, session.remainingAbsoluteMillis())
    }
    @Test fun foregroundLossRevokesRestartAndRejectsLateCredentialSuccess() {
        readyNearExpiry(); model.restartSession()
        session.foreground(false); session.foreground(true)
        val late = Operation()
        authResults.last()(AuthenticationOutcome.Granted(late, now))
        assertTrue(late.released); assertEquals(SessionState.LOCKED, model.snapshot.state)
        assertNull(model.remainingMillis); assertEquals(1, maintenanceResults.size)
    }
    @Test fun lostEnvironmentBeforeTapDoesNotStartAuth() {
        readyNearExpiry(); environment = false
        model.restartSession()
        assertEquals(SessionState.LOCKED, model.snapshot.state); assertEquals(0, starts)
    }
}
