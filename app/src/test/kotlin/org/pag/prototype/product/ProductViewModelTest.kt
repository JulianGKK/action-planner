package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Domain fakes only. No Android lifecycle/key/DB behavior is simulated as proven. */
class ProductViewModelTest {
    private class Session : SessionPort {
        var current = SessionSnapshot(SessionState.READY, 1)
        val observers = mutableListOf<(SessionSnapshot) -> Unit>()
        override fun snapshot() = current
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription {
            observers.add(observer); observer(current); return Subscription { observers.remove(observer) }
        }
        override fun authenticate(experiment: Experiment) {}
        override fun testAccess() {}
        override fun userInteraction() {}
        override fun lock() { current = SessionSnapshot(SessionState.LOCKED,2); observers.toList().forEach { it(current) } }
    }
    private class Repo : ActionRepository {
        var row = Action("synthetic-id","synthetic title","synthetic description",1,2,0)
        var updates = 0
        var advances = 0
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row.takeIf { it.id == id })
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { error("no create expected") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {
            updates++; row = row.copy(title=changes.title,description=changes.description,startAt=changes.startAt,expectedEndAt=changes.expectedEndAt)
            publish(ActionChangeResult.Applied(row))
        }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            advances++; row=row.copy(status=ActionLifecycle.next(expected)!!,
                completedAt=if (expected == ActionStatus.ACTIVE) 5 else null)
            publish(ActionChangeResult.Applied(row))
        }
    }
    private val session=Session()
    private val repo=Repo()
    private val people = object : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) = publish(PersonResult.Value(emptyList()))
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(null))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("no person write expected") }
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("no person write expected") }
    }
    private val model=ProductViewModel(session,repo,ProductStorageState.READY_TO_OPEN,people) {}
    private fun selected() { model.detail(repo.row.id) }
    @Test fun draftHasEditingAndExactlyNextStep() {
        selected(); model.edit(); assertEquals(repo.row,model.editing); model.cancelEdit()
        model.requestAdvance(); assertEquals(repo.row,model.confirming)
        assertEquals(ActionStatus.PREPARATION,ActionLifecycle.next(model.confirming!!.status))
    }
    @Test fun editCancellationDoesNotWriteAndKeepsDetail() {
        selected(); val before=repo.row; model.edit(); model.cancelEdit()
        assertNull(model.editing); assertEquals(before,model.selected); assertEquals(before,repo.row); assertEquals(0,repo.updates)
    }
    @Test fun saveUpdatesDetailAndList() {
        selected(); model.edit(); model.save(NewAction("synthetic changed","synthetic changed",3,4))
        assertEquals(1,repo.updates); assertNull(model.editing)
        assertEquals(repo.row,model.selected); assertEquals(listOf(repo.row),model.actions)
    }
    @Test fun confirmationIsRequiredAndCancelDoesNotAdvance() {
        selected(); model.confirmAdvance(); assertEquals(0,repo.advances)
        model.requestAdvance(); model.cancelAdvance(); assertEquals(0,repo.advances)
    }
    @Test fun statusUpdatesDetailAndList() {
        selected(); model.requestAdvance(); model.confirmAdvance()
        assertEquals(1,repo.advances); assertEquals(ActionStatus.PREPARATION,model.selected!!.status)
        assertEquals(listOf(repo.row),model.actions); assertNull(model.confirming)
    }
    @Test fun completedOffersNoEditingOrAdvance() {
        repo.row=repo.row.copy(status=ActionStatus.COMPLETED,completedAt=5); selected()
        model.edit(); model.requestAdvance(); model.confirmAdvance()
        assertNull(model.editing); assertNull(model.confirming); assertEquals(0,repo.advances); assertEquals(0,repo.updates)
    }
    @Test fun lockDropsEditingConfirmationAndContents() {
        selected(); model.edit(); model.requestAdvance(); session.lock()
        assertNull(model.editing); assertNull(model.confirming); assertNull(model.selected); assertTrue(model.actions.isEmpty())
        model.save(NewAction("synthetic","synthetic",1,2)); model.confirmAdvance()
        assertEquals(0,repo.updates); assertEquals(0,repo.advances)
    }
}
