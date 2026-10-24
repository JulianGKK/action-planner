package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.UUID

/** Real production metadata/intent/context seam. Fault contexts deliberately are NOT SessionAuthority. */
class P102LeadershipRecoveryTest {
    private val row=LeadershipRow("a","RESERVED",UUID.randomUUID().toString(),1,"Argon2id",19,65536,3,1,ByteArray(16),byteArrayOf())
    private val owner=Any()
    private fun intent(r:LeadershipRow=row,state:SensitiveState=SensitiveState.INCOMPLETE_SETUP)=
        LeadershipRecoveryIntent(owner,ActionStatus.PREPARATION,7,r,state)
    private fun context(g:Long=8,live:Boolean=true)=object:StorageContext {
        override val generation=g
        override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=live
        override fun publish(result:()->Unit):Boolean{if(!live)return false;result();return true}
    }
    private fun denied(work:()->Unit){assertThrows(SensitiveProblem::class.java){work()}}
    @Test fun exactlyEmptyReservedWithPresentUsableOrProvenAbsentAliasIsRecognized() {
        assertEquals(SensitiveState.INCOMPLETE_SETUP,LeadershipRecoveryChecks.state(row,emptyList(),true,true))
        assertEquals(SensitiveState.INCOMPLETE_SETUP,LeadershipRecoveryChecks.state(row,emptyList(),false,false))
        assertEquals(SensitiveState.INCOMPLETE_SETUP,LeadershipMetadata.classification(row,emptyList(),true,true))
    }
    @Test fun readyCorruptionForeignFormatAndSensitiveRowsNeverBecomeResetCandidates() {
        val bad=listOf(row.copy(state="READY",envelope=ByteArray(88)),row.copy(state="UNKNOWN"),row.copy(envelope=ByteArray(1)),
            row.copy(epoch="not-original"),row.copy(epoch=UUID(0,0).toString()),row.copy(cryptoVersion=2),row.copy(algorithm="other"),
            row.copy(kdfVersion=1),row.copy(memoryKiB=1),row.copy(iterations=1),row.copy(parallelism=2),row.copy(salt=ByteArray(15)),row.copy(actionId=""))
        bad.forEach{r->denied{LeadershipRecoveryChecks.state(r,emptyList(),true,true)}}
        val area=SensitiveRow("a","p","IDENTITY",1,1,ByteArray(60),ByteArray(33))
        denied{LeadershipRecoveryChecks.state(row,listOf(area),true,true)}
        denied{LeadershipRecoveryChecks.state(row,emptyList(),true,false)}
        denied{LeadershipRecoveryChecks.stateForRecovery(row,1,true,true)}
        denied{LeadershipRecoveryChecks.stateForRecovery(row,-1,false,false)}
    }
    @Test fun deletePendingIsDistinctAndRequiresOriginalEnvelopeAndValidAreas() {
        val r=row.copy(state="DELETE_PENDING",envelope=ByteArray(88))
        val area=SensitiveRow("a","p","IDENTITY",1,1,ByteArray(60),ByteArray(33))
        assertEquals(SensitiveState.DELETE_PENDING,LeadershipRecoveryChecks.state(r,listOf(area),false,false))
        denied{LeadershipRecoveryChecks.state(r,listOf(area.copy(actionId="foreign")),false,false)}
        denied{LeadershipRecoveryChecks.state(r.copy(envelope=ByteArray(0)),emptyList(),false,false)}
    }
    @Test fun originalIssuerSingleUseAndCancellationNarrowButNeverGrantAuthority() {
        val i=intent();assertNull(i.take(Any()));assertSame(i,i.take(owner));assertNull(i.take(owner))
        i.requireFresh(context());i.cancel();denied{i.requireFresh(context())}
        assertNull(i.take(owner));val cancelled=intent();cancelled.cancel();assertNull(cancelled.take(owner))
    }
    @Test fun sameOldInvalidOrRevokedGenerationCannotRecover() {
        for(g in listOf(-1L,0,6,7))denied{intent().requireFresh(context(g))}
        denied{intent().requireFresh(context(8,false))}
        intent().requireFresh(context(8))
        // A larger number alone isn't authority: the supplied original context must still authorize.
        denied{intent().requireFresh(context(Long.MAX_VALUE,false))}
    }
    @Test fun actionStatusIdentityAndOriginalSaltAreRecheckedAfterExternalEffect() {
        val i=intent();i.verify(ActionStatus.PREPARATION,row,0,true,true)
        denied{i.verify(ActionStatus.PREPARATION,row,1,true,true)}
        i.verify(ActionStatus.PREPARATION,row,0,false,false) // key-delete/DB gap is explicitly retryable with a new intent
        for(r in listOf(null,row.copy(actionId="b"),row.copy(epoch=UUID.randomUUID().toString()),row.copy(salt=ByteArray(16){1}),row.copy(state="READY",envelope=ByteArray(88))))
            denied{i.verify(ActionStatus.PREPARATION,r,0,false,false)}
        for(s in listOf(ActionStatus.ACTIVE,ActionStatus.COMPLETED))denied{i.verify(s,row,0,true,true)}
        i.cancel();denied{i.verify(ActionStatus.PREPARATION,row,0,false,false)}
    }
    @Test fun falseAfterCallbackReturnsFalseButAlreadyExecutedSideEffectIsNotUndone() {
        var calls=0
        val broken=object:StorageContext by context() {
            override fun publish(result:()->Unit):Boolean{result();return false}
        }
        assertFalse(restrictedLeadershipContext(broken){true}.publish{calls++})
        assertEquals(1,calls) // negative boundary, NOT evidence that a broken provider is safe for UI!
    }
    @Test fun delegateAndAdditionalRestrictionBothMustAcceptPublication() {
        var allow=true;var calls=0
        val bounded=restrictedLeadershipContext(context()){allow}
        assertTrue(bounded.publish{calls++});allow=false
        assertFalse(bounded.publish{calls++});assertFalse(bounded.check(AccessPoint.STEP));assertEquals(1,calls)
        assertFalse(restrictedLeadershipContext(context(live=false)){true}.publish{calls++})
        allow=true
        assertFalse(bounded.publish{calls++;allow=false})
        assertEquals(2,calls) // post-callback revoke also cannot undo the callback already executed
    }
}
