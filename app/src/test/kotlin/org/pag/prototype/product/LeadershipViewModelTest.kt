package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** UI state/port contracts only; encrypted storage and session enforcement have separate tests. */
class LeadershipViewModelTest {
    private class Session : SessionPort {
        var current = SessionSnapshot(SessionState.READY, 1)
        val observers = mutableListOf<(SessionSnapshot) -> Unit>()
        override fun snapshot() = current
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription {
            observers.add(observer); observer(current); return Subscription { observers.remove(observer) }
        }
        override fun authenticate(experiment: Experiment) {}
        override fun remainingAbsoluteMillis() = 20_000L.takeIf { current.state == SessionState.READY }
        override fun testAccess() {}
        override fun userInteraction() {}
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
        val row=Person("synthetic-person","synthetic-action","synthetic-01",1)
        var deferred: ((PersonResult<List<Person>>) -> Unit)?=null
        var defer=false
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) {
            lists++; if (defer) deferred=publish else publish(PersonResult.Value(listOf(row.copy(actionId=actionId))))
        }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(row))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {
            writes++; publish(PersonResult.Value(row.copy(pseudonym=pseudonym)))
        }
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {
            writes++; publish(PersonResult.Value(row.copy(pseudonym=pseudonym)))
        }
    }
    private class Sensitive : LeadershipRepository {
        var state=SensitiveState.UNINITIALIZED; var writes=0; var revokes=0
        var deferInventory=false
        var inventoryResult:((SensitiveResult<SensitiveInventory>)->Unit)?=null
        var deferred:((SensitiveResult<SensitiveOpened>)->Unit)?=null
        val grant=object:SensitiveGrant {
            override val actionId="synthetic-action";override val personId="synthetic-person";override val area=SensitiveArea.IDENTITY
            var live=true;override fun valid()=live;override fun cancel(){live=false}
        }
        override fun inventory(actionId:String,personId:String,publish:(SensitiveResult<SensitiveInventory>)->Unit){if(deferInventory)inventoryResult=publish else publish(SensitiveResult.Value(SensitiveInventory(state,setOf(SensitiveArea.IDENTITY))))}
        override fun setup(actionId:String,passphrase:CharArray,confirmation:CharArray,publish:(SensitiveResult<Unit>)->Unit){writes++;passphrase.fill('\u0000');confirmation.fill('\u0000');state=SensitiveState.READY;publish(SensitiveResult.Value(Unit))}
        override fun open(actionId:String,personId:String,area:SensitiveArea,passphrase:CharArray,publish:(SensitiveResult<SensitiveOpened>)->Unit){passphrase.fill('\u0000');deferred=publish}
        override fun save(grant:SensitiveGrant,data:SensitiveData,publish:(SensitiveResult<Unit>)->Unit){writes++;publish(SensitiveResult.Value(Unit))}
        override fun deleteArea(grant:SensitiveGrant,publish:(SensitiveResult<Unit>)->Unit){error("unused")}
        override fun deletePerson(actionId:String,personId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit){error("unused")}
        override fun deleteAction(actionId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit){error("unused")}
        override fun revoke(){revokes++}
    }
    private val session=Session();private val actions=Actions();private val people=People();private val sensitive=Sensitive()
    private val model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,people,sensitive){}
    private fun open(){model.detail(actions.row.id);model.personDetail(people.row.id)}
    private fun release(){sensitive.state=SensitiveState.READY;open();model.requestSensitive(SensitiveArea.IDENTITY);model.openSensitive("synthetic secret fixture".toCharArray());sensitive.deferred!!(SensitiveResult.Value(SensitiveOpened(sensitive.grant,SensitiveData.Identity("synthetic"))))}
    @Test fun personDetailRevealsOnlyWithItsInventoryMetadata() {
        model.detail(actions.row.id); sensitive.deferInventory=true;model.personDetail(people.row.id)
        assertTrue(model.personDetailLoading);assertNull(model.selectedPerson);assertNull(model.sensitiveInventory)
        sensitive.inventoryResult!!(SensitiveResult.Value(SensitiveInventory(SensitiveState.UNINITIALIZED)))
        assertFalse(model.personDetailLoading);assertEquals(people.row,model.selectedPerson);assertNotNull(model.sensitiveInventory)
        assertEquals(0,sensitive.writes)
    }
    @Test fun lockDuringPersonInventoryCannotRevealPersonOrMetadata() {
        model.detail(actions.row.id);sensitive.deferInventory=true;model.personDetail(people.row.id)
        val old=sensitive.inventoryResult!!;session.lock();old(SensitiveResult.Value(SensitiveInventory(SensitiveState.UNINITIALIZED)))
        assertFalse(model.personDetailLoading);assertNull(model.selectedPerson);assertNull(model.sensitiveInventory)
    }
    @Test fun leavingPersonDetailDiscardsPendingInventory() {
        model.detail(actions.row.id);sensitive.deferInventory=true;model.personDetail(people.row.id)
        val old=sensitive.inventoryResult!!;model.backToPersons();old(SensitiveResult.Value(SensitiveInventory(SensitiveState.UNINITIALIZED)))
        assertFalse(model.personDetailLoading);assertNull(model.selectedPerson);assertNull(model.sensitiveInventory)
    }
    @Test fun explicitSetupAndCancellationDoNotAutomaticallyWrite(){open();assertFalse(model.sensitiveSetup);assertEquals(0,sensitive.writes);model.requestSensitiveSetup();assertTrue(model.sensitiveSetup);model.closeSensitive();assertFalse(model.sensitiveSetup);assertEquals(0,sensitive.writes)}
    @Test fun partialSensitiveStateDoesNotBlockBasicOrPermitSetup(){sensitive.state=SensitiveState.PARTIAL_OR_INVALID;open();model.requestSensitiveSetup();assertFalse(model.sensitiveSetup);assertTrue(model.personsVisible);assertNotNull(model.selectedPerson)}
    @Test fun navigationCancelsAndDiscardsDelayedRelease(){sensitive.state=SensitiveState.READY;open();model.requestSensitive(SensitiveArea.IDENTITY);model.openSensitive("synthetic secret fixture".toCharArray());model.backToPersons();sensitive.deferred!!(SensitiveResult.Value(SensitiveOpened(sensitive.grant,SensitiveData.Identity("synthetic"))));assertNull(model.sensitiveOpened);assertFalse(sensitive.grant.valid())}
    @Test fun lockDropsSensitiveContent(){release();assertNotNull(model.sensitiveOpened);session.lock();assertNull(model.sensitiveOpened);assertNull(model.selectedPerson);assertFalse(sensitive.grant.valid())}
    @Test fun sessionRestartRevokesGrantAndNeverRestoresSensitiveAreaInNewGeneration() {
        release()
        model.restartSession()
        assertEquals(SessionState.LOCKED, model.snapshot.state)
        assertFalse(sensitive.grant.valid()); assertNull(model.sensitiveOpened)
        assertNull(model.selectedPerson); assertNull(model.sensitiveArea); assertFalse(model.sensitiveSetup)
        assertNull(model.remainingMillis)
        session.current = SessionSnapshot(SessionState.READY, 3)
        session.observers.toList().forEach { it(session.current) }
        assertNull(model.sensitiveOpened); assertNull(model.selectedPerson); assertNull(model.sensitiveArea)
        open(); model.requestSensitive(SensitiveArea.IDENTITY)
        assertNull(model.sensitiveOpened); assertEquals(SensitiveArea.IDENTITY, model.sensitiveArea)
    }
    @Test fun expiredReleaseClearsWithoutAutomaticReauth(){release();sensitive.grant.cancel();model.checkSensitiveExpiry();assertNull(model.sensitiveOpened);assertEquals(SensitiveFailure.DENIED,model.sensitiveError);assertEquals(SessionState.READY,model.snapshot.state)}
    @Test fun activeAndCompletedDoNotWrite(){for(status in listOf(ActionStatus.ACTIVE,ActionStatus.COMPLETED)){actions.row=actions.row.copy(status=status);release();model.saveSensitive(SensitiveData.Identity("synthetic changed"));assertEquals(0,sensitive.writes);assertFalse(model.personsEditable)}}
}
