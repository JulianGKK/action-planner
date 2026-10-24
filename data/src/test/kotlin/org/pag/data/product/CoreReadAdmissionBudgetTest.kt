package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class CoreReadAdmissionBudgetTest {
    private val r=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES
    private val t=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
    private val sr=ActiveWorkspaceBudget.estimateUnits(CoreReadAdmission.revisionScratch.retained).second
    private val st=ActiveWorkspaceBudget.estimateUnits(CoreReadAdmission.revisionScratch.transition).second
    private fun units(bytes:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=bytes-71)
    private fun plan(retained:Long,transition:Long,work:suspend ()->Unit={})=
        PreparedCoreRead(ActiveCoreReservation(units(retained),units(transition)),work)

    @Test fun exactSeparateLimitsAndEachNextByteAreCheckedBeforeMaterialization() = runBlocking {
        for((retained,transition) in listOf(r to t,(r+1) to t,r to (t+1))) {
            var materialized=0
            val part=plan(retained-sr,transition-st){materialized++}
            try {
                val receipt=CoreReadAdmission.admit(listOf(part))
                assertEquals(r,receipt.retained);assertEquals(t,receipt.transition)
                assertEquals(r,retained);assertEquals(t,transition)
                part.materialize(receipt);assertEquals(1,materialized)
            } catch(rejected:BoundedActiveRejected) {
                assertTrue(retained>r || transition>t)
                assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)
                assertEquals(0,materialized)
            }
        }
    }

    @Test fun oldNewBuilderAndFormOverheadShareOneAdmission() {
        // Each part fits alone; the third, disjoint owner-controlled overhead must still count.
        val parts=listOf(plan(r/2-sr,t/2-st),plan(r/2,t/2))
        val admitted=CoreReadAdmission.admit(parts)
        assertEquals(r,admitted.retained);assertEquals(t,admitted.transition)
        for(overhead in listOf(
            ActiveCoreReservation(ActiveWorkspaceBudget.Units(references=1),ActiveWorkspaceBudget.Units()),
            ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units(references=1)))) {
            try {CoreReadAdmission.admit(parts,overhead);fail("Aggregate must refuse")}
            catch(rejected:BoundedActiveRejected){assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)}
        }
    }

    @Test fun outstandingReadChecksBothReceiptLimitsAndKeepsExclusiveFenceOnRefusal() {
        for((retained,transition) in listOf(r to t,(r+1) to t,r to (t+1))) {
            val owner=OutstandingCoreRead();val read=owner.begin()!!
            val reservation=ActiveCoreReservation(units(retained),units(transition))
            try {
                read.admit(reservation,retained,transition)
                assertEquals(r,retained);assertEquals(t,transition)
                assertEquals(r,owner.state().localRetained);assertEquals(t,owner.state().localTransition)
            } catch(rejected:BoundedActiveRejected) {
                assertTrue(retained>r || transition>t)
                assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)
                assertEquals(0L,owner.state().localRetained)
            }
            assertNull(owner.begin());assertEquals(t,owner.state().transition)
        }
    }

    @Test fun fourPresentationHandlesAndHubMetadataShareOneScratchAtExactLimits() = runBlocking {
        val extra=CompactPresentationBudget.reservation
        val extraR=ActiveWorkspaceBudget.estimateUnits(extra.retained).second
        val extraT=ActiveWorkspaceBudget.estimateUnits(extra.transition).second
        assertEquals(2728L,extraR);assertEquals(63816L,extraT)
        val handle=ActiveWorkspaceBudget.estimateUnits(CompactPresentationBudget.handle).second
        val hub=ActiveWorkspaceBudget.estimateUnits(CompactPresentationBudget.hub).second
        val shared=ActiveWorkspaceBudget.estimateUnits(CompactPresentationBudget.sharedTransition).second
        val scopes=ActiveWorkspaceBudget.estimateUnits(CompactPresentationBudget.vmScopes).second
        assertEquals(136L,scopes) // One metadata object and its original-slot reference, no per-entry allocations.
        assertEquals(152L,handle);assertEquals(2136L,hub);assertEquals(handle*3+hub+scopes,extraR);assertEquals(handle*3+hub+scopes+shared,extraT)
        var work=0
        val fitting=plan(r-extraR-sr,t-extraT-st){work++}
        val receipt=CoreReadAdmission.admit(listOf(fitting),extra)
        assertEquals(r,receipt.retained);assertEquals(t,receipt.transition)
        fitting.materialize(receipt);assertEquals(1,work)
        for((retained,transition) in listOf((r-extraR-sr+1) to (t-extraT-st),(r-extraR-sr) to (t-extraT-st+1))) {
            val overflowing=plan(retained,transition){work++}
            try {CoreReadAdmission.admit(listOf(overflowing),extra);fail("No free presentation handles/scratch")}
            catch(rejected:BoundedActiveRejected){assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)}
        }
        assertEquals(1,work)
    }

    @Test fun variableFunctionsFormattingIsJointlyAdmittedAtBothLimitsAndOverflowNeverWraps() = runBlocking {
        val dynamic=CompactPresentationBudget.functionsFormatting(100,35000)
        assertEquals(100*3028L+35000*32L,ActiveWorkspaceBudget.estimateUnits(dynamic).second)
        val fixed=CompactPresentationBudget.reservation
        val extra=ActiveCoreReservation(fixed.retained,CoreReadAdmission.add(fixed.transition,dynamic))
        val er=ActiveWorkspaceBudget.estimateUnits(extra.retained).second
        val et=ActiveWorkspaceBudget.estimateUnits(extra.transition).second
        var work=0
        val fitting=plan(r-er-sr,t-et-st){work++}
        val receipt=CoreReadAdmission.admit(listOf(fitting),extra)
        assertEquals(r,receipt.retained);assertEquals(t,receipt.transition);fitting.materialize(receipt)
        for((retained,transition) in listOf((r-er-sr+1) to (t-et-st),(r-er-sr) to (t-et-st+1))) {
            try {CoreReadAdmission.admit(listOf(plan(retained,transition){work++}),extra);fail("No free catalogue formatting")}
            catch(rejected:BoundedActiveRejected){assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)}
        }
        assertEquals(1,work)
        for((rows,bytes) in listOf(Long.MAX_VALUE to 0L,0L to Long.MAX_VALUE)) {
            try{CompactPresentationBudget.functionsFormatting(rows,bytes);fail("Checked overflow required")}
            catch(expected:ArithmeticException){}
        }
    }

    @Test fun overflowCannotAuthorizeMaterialization() {
        val overflowing=PreparedCoreRead(ActiveCoreReservation(
            ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units(references=Long.MAX_VALUE))) {fail("No work")}
        try {CoreReadAdmission.admit(listOf(overflowing));fail("Overflow must refuse")}
        catch(rejected:BoundedActiveRejected){assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)}
    }
}
