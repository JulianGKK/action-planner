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
        var authentications=0
        override fun authenticate(experiment: Experiment) {authentications++}
        fun ready(generation:Long=3) {current=SessionSnapshot(SessionState.READY,generation);observers.toList().forEach{it(current)}}
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
        var prepares=0;var recoveries=0;var cancellations=0
        var recovered:((SensitiveResult<Unit>)->Unit)?=null
        override fun prepareRecovery(actionId:String,publish:(SensitiveResult<LeadershipRecoveryRequest>)->Unit) {
            prepares++
            if(state !in setOf(SensitiveState.INCOMPLETE_SETUP,SensitiveState.DELETE_PENDING)) {publish(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE));return}
            val kind=state
            publish(SensitiveResult.Value(object:LeadershipRecoveryRequest {
                override val actionId=actionId;override val state=kind
                override fun cancel(){cancellations++}
            }))
        }
        override fun recover(request:LeadershipRecoveryRequest,publish:(SensitiveResult<Unit>)->Unit){recoveries++;recovered=publish}
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
    private val model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,people,sensitive){session.authenticate(if(it)Experiment.STORAGE_INITIALIZE else Experiment.STORAGE_UNWRAP)}
    private fun open(){model.detail(actions.row.id);model.personDetail(people.row.id)}
    private var lastRecovery:LeadershipRecoveryRequest?=null // explicitly retained TEST metadata alias
    private fun requestRecovery(){model.requestLeadershipRecovery(actions.row.id,model.snapshot.generation);lastRecovery=model.leadershipRecovery}
    private fun authenticateRecovery(){model.authenticateLeadershipRecovery(checkNotNull(lastRecovery),model.snapshot.generation)}
    private fun confirmRecovery(){model.confirmLeadershipRecovery(checkNotNull(lastRecovery),model.snapshot.generation)}
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

    @Test fun recoveryRequiresUserIntentFreshAuthenticationAndSecondConfirmation() {
        sensitive.state=SensitiveState.INCOMPLETE_SETUP;open()
        assertEquals(0,sensitive.prepares);assertEquals(0,sensitive.recoveries)
        requestRecovery();assertEquals(LeadershipRecoveryPhase.CONFIRM_REAUTH,model.leadershipRecoveryPhase)
        confirmRecovery();assertEquals(0,sensitive.recoveries)
        authenticateRecovery();assertEquals(1,session.authentications)
        assertEquals(LeadershipRecoveryPhase.AUTHENTICATING,model.leadershipRecoveryPhase);assertNull(model.selected)
        session.ready();assertEquals(LeadershipRecoveryPhase.CONFIRM_RESET,model.leadershipRecoveryPhase)
        assertEquals(0,sensitive.recoveries);assertNull(model.feedback)
        confirmRecovery();assertEquals(1,sensitive.recoveries);assertNull(model.feedback)
        val callback=sensitive.recovered!!;callback(SensitiveResult.Value(Unit))
        assertEquals("Unvollständige Einrichtung zurückgesetzt",model.feedback?.text)
        val id=model.feedback!!.id;callback(SensitiveResult.Value(Unit));assertEquals(id,model.feedback!!.id)
    }
    @Test fun cancelledReauthAndUnchangedGenerationCannotExecuteRecovery() {
        sensitive.state=SensitiveState.INCOMPLETE_SETUP;open();requestRecovery()
        authenticateRecovery();session.lock();session.ready();confirmRecovery()
        assertEquals(0,sensitive.recoveries);assertNull(model.leadershipRecovery)
        open();requestRecovery();authenticateRecovery();session.ready(3)
        assertNull(model.leadershipRecovery);assertEquals(0,sensitive.recoveries)
    }
    @Test fun navigationLockAndLateRecoveryCallbackNeverPublishSuccess() {
        sensitive.state=SensitiveState.INCOMPLETE_SETUP;open();requestRecovery()
        authenticateRecovery();session.ready();confirmRecovery()
        val callback=sensitive.recovered!!;model.lock();callback(SensitiveResult.Value(Unit))
        assertNull(model.feedback);assertNull(model.leadershipRecovery)
        session.ready(5);open();requestRecovery();model.back();assertNull(model.leadershipRecovery)
        assertTrue(sensitive.cancellations>0)
    }
    @Test fun deletePendingHasExplicitSameReauthFlowAndRejectionIsNotSaved() {
        sensitive.state=SensitiveState.DELETE_PENDING;open();requestRecovery()
        assertEquals(SensitiveState.DELETE_PENDING,model.leadershipRecovery!!.state)
        authenticateRecovery();session.ready();confirmRecovery()
        sensitive.recovered!!(SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE))
        assertTrue(model.leadershipRecoveryError);assertNull(model.feedback);assertNull(model.leadershipRecovery)
    }

    @Test fun oldButtonIdentityCannotAuthenticateConfirmOrCancelNewIntent() {
        sensitive.state=SensitiveState.INCOMPLETE_SETUP;open();requestRecovery()
        val a=checkNotNull(lastRecovery);val oldGeneration=model.snapshot.generation
        model.cancelLeadershipRecovery();requestRecovery();val b=checkNotNull(lastRecovery)
        assertNotSame(a,b)
        model.authenticateLeadershipRecovery(a,oldGeneration)
        model.cancelLeadershipRecovery(a)
        assertSame(b,model.leadershipRecovery);assertEquals(0,session.authentications)
        authenticateRecovery();session.ready()
        model.confirmLeadershipRecovery(a,model.snapshot.generation)
        model.confirmLeadershipRecovery(b,oldGeneration)
        model.cancelLeadershipRecovery(a)
        assertSame(b,model.leadershipRecovery);assertEquals(0,sensitive.recoveries)
        confirmRecovery();assertEquals(1,sensitive.recoveries)
        sensitive.recovered!!(SensitiveResult.Value(Unit))
    }
}
