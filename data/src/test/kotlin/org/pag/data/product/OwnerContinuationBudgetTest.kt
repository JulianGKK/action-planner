package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class OwnerContinuationBudgetTest {
    private fun units(bytes:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=bytes-71)
    private fun reservation(r:Long,t:Long)=ActiveCoreReservation(units(r),units(t))
    private fun root(m:OutstandingCoreRead,r:Long=1000,t:Long=1000):OutstandingCoreRead.FullviewEntry {
        val entry=m.beginFullview("a",1)!!
        assertTrue(m.enterFullview(entry,"a",1));assertTrue(entry.admit(reservation(r,t)));assertTrue(entry.materialize())
        entry.worker(Runnable{}).run()
        return entry
    }
    @Test fun cumulativeRetainedAndTransitionExactAndPlusOneRefuseBeforePayload() {
        for(retained in listOf(true,false))for(extra in listOf(0L,1L)){
            val m=OutstandingCoreRead()
            val binding=ActiveWorkspaceBudget.estimateUnits(m.bindingCost.retained).second
            val limit=if(retained)ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES else ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
            val initial=limit-binding-200
            val old=root(m,if(retained)initial else 1000,if(retained)1000 else initial)
            val child=m.continueSection("b",2)!!
            assertFalse(old.readable());assertNull(m.begin());assertNull(m.beginFullview("b",2))
            assertTrue(m.enterFullview(child,"b",2))
            var payloads=0
            val accepted=admitCurrentFullview(child,reservation(200+extra,200+extra)){true}
            if(accepted)payloads++
            assertEquals(extra==0L,accepted);assertEquals(if(extra==0L)1 else 0,payloads)
            val state=m.state()
            assertEquals(if(extra==0L)limit else limit-200,if(retained)state.localRetained else state.localTransition)
            assertTrue(state.localRetained<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
            assertTrue(state.localTransition<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
            assertFalse(state.retirementConfirmed)
        }
    }
    @Test fun bindingAdmissionAtExhaustedLedgerNeverConstructsAnotherBindingOrResetsCharges() {
        val m=OutstandingCoreRead();val old=root(m,ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES,1000)
        val before=m.state()
        assertNull(m.continueSection("b",2));assertNull(m.continueRead("a",3))
        assertEquals(WorkspaceFailure.BUDGET_REFUSED,m.continuationFailure)
        assertEquals(before.localRetained,m.state().localRetained)
        assertEquals(before.localTransition,m.state().localTransition)
        assertTrue(old.readable()) // Failed admission itself does not forge revocation/retirement.
    }
    @Test fun unconfirmedCleanupAndPendingSupplementBlockContinuationEvenAfterGenerationChange() {
        val m=OutstandingCoreRead();val old=root(m)
        val child=old.supplement(WorkspacePart.TASKS)!!
        assertNull(m.continueSection("b",2));assertNull(m.continueRead("b",2))
        child.unconfirmed();child.worker(Runnable{}).run()
        assertNull(m.continueSection("b",3));assertFalse(old.readable())
        assertEquals(1000L,m.state().localRetained-ActiveWorkspaceBudget.estimateUnits(ActiveWorkspaceBudget.Units(objects=16,references=64)).second)
    }
    @Test fun foreignRecordIsNeverRecognizedAsOriginalOwner() {
        val a=OutstandingCoreRead();val b=OutstandingCoreRead();val entry=root(a)
        assertTrue(a.ownsFullview(entry));assertFalse(b.ownsFullview(entry))
    }
    @Test fun freshSectionBindsWriterBudgetWithoutRevivingOldIdentity() {
        val m=OutstandingCoreRead();val a=root(m)
        val b=m.continueSection("b",2)!!
        assertTrue(m.enterFullview(b,"b",2));assertTrue(b.admit(reservation(1000,1000)));assertTrue(b.materialize());b.worker(Runnable{}).run()
        assertFalse(a.readable());assertTrue(b.readable())
        assertEquals(false,m.reserveHeld("a",1,reservation(100,100)))
        assertEquals(true,m.reserveHeld("b",2,reservation(100,100)))
        val again=m.continueSection("a",1)!!
        assertTrue(m.enterFullview(again,"a",1));assertTrue(again.admit(reservation(100,100)));assertTrue(again.materialize());again.worker(Runnable{}).run()
        assertTrue(again.readable());assertFalse(a.readable());assertFalse(b.readable())
        assertTrue(m.state().localRetained>2100)
    }
}
