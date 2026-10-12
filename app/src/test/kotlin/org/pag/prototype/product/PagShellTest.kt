package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class PagShellTest {
    @Test fun countdownFormatFloorsAndNeverGoesNegative() {
        assertEquals("3:00", countdownText(180000))
        assertEquals("1:42", countdownText(102000))
        assertEquals("0:09", countdownText(9999))
        assertEquals("0:00", countdownText(-1000))
    }
    private class Session : SessionPort {
        var current = SessionSnapshot()
        var observer: ((SessionSnapshot) -> Unit)? = null
        var interactions = 0
        override fun snapshot() = current
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription {
            this.observer = observer; observer(current); return Subscription {}
        }
        fun emit(state: SessionState, generation: Long) {
            current = SessionSnapshot(state, generation); observer!!(current)
        }
        override fun remainingAbsoluteMillis() = if (current.state == SessionState.READY) 102000L else null
        override fun authenticate(experiment: Experiment) {}
        override fun testAccess() {}
        override fun userInteraction() { interactions++ }
        override fun lock() { emit(SessionState.LOCKED, current.generation + 1) }
    }
    private class Repo : ActionRepository {
        val pending = mutableListOf<(List<Action>) -> Unit>()
        override fun listActions(publish: (List<Action>) -> Unit) { pending.add(publish) }
        override fun getAction(id: String, publish: (Action?) -> Unit) {}
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {}
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {}
    }
    private val people = object : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) {}
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) {}
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private val session = Session()
    private val repo = Repo()
    private var starts = 0
    private val model = ProductViewModel(session, repo, ProductStorageState.EMPTY, people) { starts++ }
    @Test fun initialLoadNeverPretendsEmpty() {
        assertEquals(0, starts)
        session.emit(SessionState.MAINTENANCE, 1)
        assertEquals(ActionListState.Loading, model.actionListState)
        session.emit(SessionState.READY, 1)
        assertEquals(ActionListState.Loading, model.actionListState)
        repo.pending.last()(emptyList())
        assertEquals(ActionListState.Empty, model.actionListState)
    }
    @Test fun completedLoadPublishesContent() {
        session.emit(SessionState.READY, 1)
        val rows = listOf(Action("synthetic", "synthetic title", "synthetic description", 1, 2, 0))
        repo.pending.last()(rows)
        assertEquals(ActionListState.Content(rows), model.actionListState)
    }
    @Test fun blockedIsErrorAndStaleLoadCannotRestoreContents() {
        session.emit(SessionState.READY, 1)
        val old = repo.pending.last()
        session.emit(SessionState.BLOCKED, 2)
        old(emptyList())
        assertEquals(ActionListState.Error, model.actionListState)
        session.emit(SessionState.READY, 3)
        old(emptyList())
        assertEquals(ActionListState.Loading, model.actionListState)
    }
    @Test fun generationChangeClearsOldProjectionAndContentBeforeNewLoad() {
        val rows = listOf(Action("synthetic", "synthetic title", "synthetic description", 1, 2, 0))
        session.emit(SessionState.READY, 1)
        val old = repo.pending.last()
        old(rows); model.refreshCountdown()
        assertNotNull(model.remainingMillis)
        session.emit(SessionState.READY, 2)
        assertEquals(102000L, model.remainingMillis) // new projection is ready in the same state transition
        assertTrue(model.actions.isEmpty())
        assertEquals(ActionListState.Loading, model.actionListState)
        old(rows)
        assertEquals(ActionListState.Loading, model.actionListState)
        repo.pending.last()(emptyList())
        assertEquals(ActionListState.Empty, model.actionListState)
    }

    @Test fun ticksDoNotInteractAndProjectionDisappearsAtRevoke() {
        session.emit(SessionState.READY, 1)
        repeat(180) { model.refreshCountdown() }
        assertEquals(102000L, model.remainingMillis)
        assertEquals(0, session.interactions)
        for (state in listOf(SessionState.LOCKED, SessionState.MAINTENANCE, SessionState.BLOCKED)) {
            session.emit(state, session.current.generation + 1)
            assertNull(model.remainingMillis)
            model.refreshCountdown(); assertNull(model.remainingMillis)
        }
    }
}
