package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.prototype.product.ActiveWorkspaceValidity.*

/** Deliberately adversarial central providers. A denied completion cannot undo an already
 * executed side effect. The future owner must publish prepared references atomically (#37).
 */
class ActiveWorkspacePublicationRaceTest {
    private class H {
        val initial=Binding("a",ActionStatus.ACTIVE,1,2)
        var binding:Binding?=initial
        var generation=1L;var allowed=true;var postCheckThrows=false
        var after:()->Unit={};var centralAccepted=true;var duplicate=false;var delayed=false
        var captured:(()->Unit)?=null
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                if(!allowed)return null
                val g=generation
                return object:StorageContext {
                    override val scope=AccessScope.READY
                    override val generation=g
                    override fun check(point:AccessPoint):Boolean {
                        if(postCheckThrows && point==AccessPoint.PUBLICATION)error("SYNTHETIC_FINAL_GATE_FAILURE")
                        return allowed && this@H.generation==g
                    }
                    override fun publish(result:()->Unit):Boolean {
                        captured=result
                        if(delayed)return centralAccepted
                        result();if(duplicate)result();after();return centralAccepted
                    }
                }
            }
        }
        val validity=ActiveWorkspaceValidity(initial,access){binding}
        val parts=Projection.entries.toSet()
        fun attempt(mode:String,work:()->Unit):Boolean=when(mode) {
            "read"->{val t=validity.beginRead(parts)!!;validity.publishRead(t,parts,Cleanup.COMPLETE,work)}
            "mutation"->{val t=validity.beginMutation(parts)!!;assertTrue(validity.finishMutation(t,Outcome.COMMITTED,Cleanup.COMPLETE));validity.publishMutation(t,parts,work)}
            else->{val t=validity.beginRead(parts)!!;assertTrue(validity.publishRead(t,parts,Cleanup.COMPLETE){});validity.publishCurrent(parts,work)}
        }
    }
    @Test fun callbackRanButFalseCentralReturnNeverReportsSuccessfulCompletion() {
        for(mode in listOf("read","mutation")) {
            val h=H();h.centralAccepted=false;var effects=0
            assertFalse(h.attempt(mode){effects++})
            assertEquals("Executed UI effects are NOT rolled back by false",1,effects)
            assertEquals(State.REVOKED,h.validity.state);assertFalse(h.validity.canReuse(h.parts))
        }
    }
    @Test fun revokePermissionGenerationAndActionLossAfterCallbackDenyCompletion() {
        for(mode in listOf("read","mutation")) for(change in listOf("revoke","permission","generation","action","owner-revoke")) {
            val h=H();var effects=0
            h.after={when(change) {
                "revoke"->h.allowed=false
                "permission"->h.binding=h.initial.copy(permissionRevision=3)
                "generation"->h.generation++
                "action"->h.binding=h.initial.copy(actionId="b")
                else->h.validity.revoke()
            }}
            assertFalse(h.attempt(mode){effects++});assertEquals(1,effects)
            assertEquals(State.REVOKED,h.validity.state);assertFalse(h.validity.canReuse(h.parts))
        }
    }
    @Test fun duplicateAndLateCentralCallbacksCannotRepeatSideEffects() {
        for(mode in listOf("read","mutation")) {
            val h=H();h.duplicate=true;var effects=0
            assertTrue(h.attempt(mode){effects++});assertEquals(1,effects)
            h.captured!!();assertEquals(1,effects)
            val late=H();late.delayed=true;var lateEffects=0
            assertFalse(late.attempt(mode){lateEffects++})
            late.captured!!();assertEquals(0,lateEffects);assertFalse(late.validity.canReuse(late.parts))
        }
    }
    @Test fun finalAuthorityFailureAfterCallbackIsRecoveryAndNeverAnUndoClaim() {
        val h=H();var effects=0;h.after={h.postCheckThrows=true}
        assertThrows(IllegalStateException::class.java){h.attempt("read"){effects++}}
        assertEquals(1,effects);assertEquals(State.RECOVERY_REQUIRED,h.validity.state)
        assertFalse(h.validity.canReuse(h.parts))
    }
    @Test fun consumedReadAndMutationTicketsDoNotPublishTwice() {
        val h=H();val read=h.validity.beginRead(h.parts)!!;var effects=0
        assertTrue(h.validity.publishRead(read,h.parts,Cleanup.COMPLETE){effects++})
        assertFalse(h.validity.publishRead(read,h.parts,Cleanup.COMPLETE){effects++})
        val mutation=h.validity.beginMutation(h.parts)!!
        assertTrue(h.validity.finishMutation(mutation,Outcome.COMMITTED,Cleanup.COMPLETE))
        assertTrue(h.validity.publishMutation(mutation,h.parts){effects++})
        assertFalse(h.validity.publishMutation(mutation,h.parts){effects++});assertEquals(2,effects)
    }
}
