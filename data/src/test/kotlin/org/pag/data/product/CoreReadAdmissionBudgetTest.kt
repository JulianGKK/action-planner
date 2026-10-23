package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class CoreReadAdmissionBudgetTest {
    private val r=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES
    private val t=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
    private fun units(bytes:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=bytes-71)
    private fun plan(retained:Long,transition:Long,work:suspend ()->Unit={})=
        PreparedCoreRead(ActiveCoreReservation(units(retained),units(transition)),work)

    @Test fun exactSeparateLimitsAndEachNextByteAreCheckedBeforeMaterialization() = runBlocking {
        for((retained,transition) in listOf(r to t,(r+1) to t,r to (t+1))) {
            var materialized=0
            val part=plan(retained,transition){materialized++}
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
        val parts=listOf(plan(r/2,t/2),plan(r/2,t/2))
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

    @Test fun personsPresentationOverheadSharesExactLimitsBeforeMaterialization() = runBlocking {
        val extra=CompactPersonsPresentationBudget.reservation
        val extraR=ActiveWorkspaceBudget.estimateUnits(extra.retained).second
        val extraT=ActiveWorkspaceBudget.estimateUnits(extra.transition).second
        assertEquals(152L,extraR);assertEquals(61088L,extraT)
        var work=0
        val fitting=plan(r-extraR,t-extraT){work++}
        val receipt=CoreReadAdmission.admit(listOf(fitting),extra)
        assertEquals(r,receipt.retained);assertEquals(t,receipt.transition)
        fitting.materialize(receipt);assertEquals(1,work)
        val overflowing=plan(r-extraR+1,t-extraT){work++}
        try {CoreReadAdmission.admit(listOf(overflowing),extra);fail("No free presentation handle")}
        catch(rejected:BoundedActiveRejected){assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)}
        assertEquals(1,work)
    }

    @Test fun overflowCannotAuthorizeMaterialization() {
        val overflowing=PreparedCoreRead(ActiveCoreReservation(
            ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units(references=Long.MAX_VALUE))) {fail("No work")}
        try {CoreReadAdmission.admit(listOf(overflowing));fail("Overflow must refuse")}
        catch(rejected:BoundedActiveRejected){assertEquals(BoundedActiveStatus.BUDGET_REFUSED,rejected.status)}
    }
}
