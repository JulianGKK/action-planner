package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import org.pag.domain.*

/** UI state/port contracts only; encrypted storage and session enforcement have separate tests. */
class PersonViewModelTest {
    private class Session : SessionPort {
        var current = SessionSnapshot(SessionState.READY, 1)
        val observers = mutableListOf<(SessionSnapshot) -> Unit>()
        override fun snapshot() = current
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription {
            observers.add(observer); observer(current); return Subscription { observers.remove(observer) }
        }
        override fun authenticate(experiment: Experiment) {}
        override fun testAccess() {}
        var interactions=0
        override fun userInteraction() { interactions++ }
        override fun lock() { current = SessionSnapshot(SessionState.LOCKED,2); observers.toList().forEach { it(current) } }
    }
    private class Actions : ActionRepository {
        var row = Action("synthetic-action","synthetic","synthetic",1,2,0,ActionStatus.PREPARATION)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row.copy(id=id))
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { error("unused") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { error("unused") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) { error("unused") }
    }
    private class People : PersonRepository {
        var writes=0; var lists=0
        var onList:()->Unit={}
        var writeResult: PersonResult<Person>? = null
        var deferWrite = false
        var pendingWrite: ((PersonResult<Person>) -> Unit)? = null
        val row=Person("synthetic-person","synthetic-action","synthetic-01",1)
        var deferred: ((PersonResult<List<Person>>) -> Unit)?=null
        var defer=false
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) {
            onList(); lists++; if (defer) deferred=publish else publish(PersonResult.Value(listOf(row.copy(actionId=actionId))))
        }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(row))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {
            writes++; if (deferWrite) pendingWrite=publish else publish(writeResult ?: PersonResult.Value(row.copy(pseudonym=pseudonym)))
        }
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {
            writes++; if (deferWrite) pendingWrite=publish else publish(writeResult ?: PersonResult.Value(row.copy(pseudonym=pseudonym)))
        }
    }
    private val session=Session(); private val actions=Actions(); private val people=People()
    private val model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,people) {}
    private fun open() = model.detail(actions.row.id)
    @Test fun bannerTimerExpiresWithoutUserInteraction() = runBlocking {
        open(); model.addPerson(); model.savePerson("synthetic")
        val id=model.feedback!!.id
        val interactions=session.interactions
        val started=System.nanoTime()
        val timer=launch { expireFeedback(id, FEEDBACK_MIN_DURATION_MILLIS, model::dismissFeedback) }
        delay(1000)
        assertNotNull(model.feedback)
        timer.join()
        assertNull(model.feedback)
        assertTrue((System.nanoTime()-started)/1_000_000 >= FEEDBACK_MIN_DURATION_MILLIS)
        assertEquals(interactions,session.interactions)
    }

    @Test fun bannerAppearsOnlyAfterConfirmedSuccessAndDismissDoesNotInteract() {
        open(); model.addPerson(); people.deferWrite=true; model.savePerson("synthetic")
        assertNull(model.feedback)
        people.pendingWrite!!(PersonResult.Value(people.row))
        assertEquals("Person gespeichert", model.feedback!!.text)
        val id=model.feedback!!.id
        model.dismissFeedback(id-1); assertNotNull(model.feedback)
        model.dismissFeedback(id); assertNull(model.feedback)
    }
    @Test fun failedOrRevokedWriteNeverPublishesSuccessBanner() {
        open(); model.addPerson(); people.writeResult=PersonResult.Rejected(PersonRejection.DUPLICATE_PSEUDONYM)
        model.savePerson("synthetic"); assertNull(model.feedback)
        people.deferWrite=true; model.savePerson("synthetic"); val old=people.pendingWrite!!
        session.lock(); old(PersonResult.Value(people.row)); assertNull(model.feedback)
    }
    @Test fun synchronousRevokeDuringPersonRefreshCannotRepublishBanner() {
        open();model.addPerson();people.onList={session.lock()};model.savePerson("synthetic")
        assertNull(model.feedback);assertTrue(model.persons.isEmpty());assertFalse(model.personForm)
    }

    @Test fun personRefreshRetainsValidListUntilCompleteNewResult() {
        open();val before=model.workspace;model.addPerson();people.defer=true;model.savePerson("lowercase remains lowercase")
        assertSame(before,model.workspace);assertFalse(model.personsLoading);assertEquals(listOf(people.row),model.persons)
        people.deferred!!(PersonResult.Value(emptyList()));assertNotSame(before,model.workspace);assertTrue(model.persons.isEmpty())
    }

    @Test fun enteringAnotherPersonFormDoesNotDiscardValidWorkspaceRefresh() {
        open();model.addPerson();people.defer=true;model.savePerson("synthetic new")
        val pending=people.deferred!!;model.addPerson();model.cancelPerson()
        pending(PersonResult.Value(listOf(people.row.copy(pseudonym="synthetic new"))))
        assertEquals("synthetic new",model.persons.single().pseudonym)
    }

    @Test fun lockClearsSuccessBanner() {
        open(); model.addPerson(); model.savePerson("synthetic"); assertNotNull(model.feedback)
        session.lock(); assertNull(model.feedback)
    }

    @Test fun draftHasNoPersonPath() {
        actions.row=actions.row.copy(status=ActionStatus.DRAFT); open(); model.addPerson(); model.savePerson("synthetic")
        assertFalse(model.personsVisible); assertFalse(model.personForm); assertEquals(0,people.lists); assertEquals(0,people.writes)
    }
    @Test fun preparationListsPersonsAndPermitsAdd() {
        open(); assertTrue(model.personsVisible); assertTrue(model.personsEditable); assertEquals(listOf(people.row),model.persons)
        model.addPerson(); assertTrue(model.personForm); assertNull(model.editingPerson)
        model.savePerson("synthetic-02"); assertEquals(1,people.writes); assertFalse(model.personForm)
    }
    @Test fun cancelNewOrEditedPseudonymDoesNotWrite() {
        open(); model.addPerson(); model.cancelPerson(); assertFalse(model.personForm)
        model.personDetail(people.row.id); model.editPerson(); assertEquals(people.row,model.editingPerson)
        model.cancelPerson(); assertEquals(people.row,model.selectedPerson); assertEquals(0,people.writes)
    }
    @Test fun activeAndCompletedAreReadOnly() {
        for (status in listOf(ActionStatus.ACTIVE,ActionStatus.COMPLETED)) {
            actions.row=actions.row.copy(status=status); open(); assertTrue(model.personsVisible); assertFalse(model.personsEditable)
            model.personDetail(people.row.id); model.editPerson(); model.addPerson(); model.savePerson("synthetic")
            assertFalse(model.personForm); assertEquals(0,people.writes)
        }
    }
    @Test fun blankDoesNotReachRepository() {
        open(); model.addPerson(); model.savePerson("  ")
        assertEquals(PersonRejection.INVALID_PSEUDONYM,model.personError); assertEquals(0,people.writes)
    }
    @Test fun lockClearsAllPersonState() {
        open(); model.personDetail(people.row.id); model.editPerson(); session.lock()
        assertFalse(model.personsVisible); assertFalse(model.personForm); assertNull(model.selectedPerson)
        assertNull(model.editingPerson); assertTrue(model.persons.isEmpty()); assertNull(model.personError)
    }
    @Test fun oldNavigationResultCannotPopulateAnotherAction() {
        people.defer=true; open(); val old=people.deferred!!; model.detail("synthetic-other")
        old(PersonResult.Value(listOf(people.row)))
        assertTrue(model.persons.isEmpty()); assertEquals("synthetic-other",model.selected!!.id)
    }
}
