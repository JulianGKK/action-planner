package org.pag.assessment
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.assessment.MutationContractProof.*
class MutationContractTest {
    private class H {
        var allowed=true;var generation=1L;var b:Binding?=Binding("synthetic",1,0)
        val access=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext?{
            if(!allowed)return null
            val gen=generation
            return object:StorageContext{override val generation=gen;override val scope=AccessScope.READY
                override fun check(point:AccessPoint)=allowed && gen==this@H.generation
                override fun publish(result:()->Unit):Boolean{if(!check(AccessPoint.PUBLICATION))return false;result();return true}}
        }}
        val p=MutationContractProof(access,{b})
        val parts=setOf(Part.PEOPLE,Part.TASK_COUNTS)
        fun loaded(){assertTrue(p.publishRead(p.startRead()!!,parts))}
    }
    @Test fun readsCoalesceAndDuplicateCallbackCannotOverwriteNextRead(){val h=H();val a=h.p.startRead()!!;assertSame(a,h.p.startRead());assertTrue(h.p.publishRead(a,h.parts));val b=h.p.startRead()!!;assertNotSame(a,b);assertFalse(h.p.publishRead(a,h.parts));assertTrue(h.p.publishRead(b,h.parts))}
    @Test fun pendingMutationStopsHitsAndCoalescesReads(){val h=H();h.loaded();h.p.begin(h.parts)!!;assertFalse(h.p.canHit(Part.PEOPLE));assertNull(h.p.startRead());assertNull(h.p.begin(h.parts))}
    @Test fun noSuccessBeforeCommit(){val h=H();h.loaded();val w=h.p.begin(h.parts)!!;var n=0;assertFalse(h.p.publishCommit(w,h.parts){n++});assertEquals(0,n)}
    @Test fun confirmedCommitAndPublicationAreDifferent(){val h=H();h.loaded();val w=h.p.begin(h.parts)!!;assertTrue(h.p.outcome(w,Outcome.COMMITTED));assertFalse(h.p.canHit(Part.PEOPLE));var n=0;assertTrue(h.p.publishCommit(w,h.parts){n++});assertEquals(1,n);assertTrue(h.p.canHit(Part.PEOPLE));assertFalse(h.p.publishCommit(w,h.parts){n++})}
    @Test fun rollbackDoesNotInventUpdatedData(){val h=H();h.loaded();val w=h.p.begin(h.parts)!!;h.p.outcome(w,Outcome.ROLLED_BACK);assertFalse(h.p.canHit(Part.PEOPLE));assertFalse(h.p.publishCommit(w,h.parts){fail()});h.loaded();assertTrue(h.p.canHit(Part.PEOPLE))}
    @Test fun unknownCommitRequiresAuthoritativeRead(){val h=H();h.loaded();val w=h.p.begin(h.parts)!!;h.p.outcome(w,Outcome.UNKNOWN);assertFalse(h.p.canHit(Part.TASK_COUNTS));assertFalse(h.p.publishCommit(w,h.parts){fail()})}
    @Test fun oldLoadCannotOverrideMutation(){val h=H();val r=h.p.startRead()!!;h.p.begin(h.parts);assertFalse(h.p.publishRead(r,h.parts))}
    @Test fun laterMutationFencesOldCommittedCallback(){val h=H();val a=h.p.begin(h.parts)!!;h.p.outcome(a,Outcome.COMMITTED);h.p.begin(h.parts);assertFalse(h.p.publishCommit(a,h.parts){fail()})}
    @Test fun freshReloadFencesDelayedPatch(){val h=H();val w=h.p.begin(h.parts)!!;h.p.outcome(w,Outcome.COMMITTED);h.loaded();assertFalse(h.p.publishCommit(w,h.parts){fail()})}
    @Test fun partialPatchCannotMarkAllCurrent(){val h=H();val w=h.p.begin(h.parts)!!;h.p.outcome(w,Outcome.COMMITTED);assertFalse(h.p.publishCommit(w,setOf(Part.PEOPLE)){fail()})}
    @Test fun revokeAfterCommitBeforePublication(){val h=H();val w=h.p.begin(h.parts)!!;h.p.outcome(w,Outcome.COMMITTED);h.allowed=false;assertFalse(h.p.publishCommit(w,h.parts){fail()});assertFalse(h.p.canHit(Part.PEOPLE))}
    @Test fun permissionOrActionChangeCannotReuse(){val h=H();h.loaded();h.b=h.b!!.copy(permissionRevision=1);assertFalse(h.p.canHit(Part.PEOPLE));h.loaded();h.b=h.b!!.copy(action="other");assertFalse(h.p.canHit(Part.PEOPLE))}
    @Test fun generationSwitchCannotReuse(){val h=H();h.loaded();h.generation++;assertFalse(h.p.canHit(Part.PEOPLE))}
}
