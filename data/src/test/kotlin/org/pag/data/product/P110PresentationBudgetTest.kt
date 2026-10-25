package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110PresentationBudgetTest {
    private fun entry(memory:OutstandingCoreRead):OutstandingCoreRead.FullviewEntry {
        val e=memory.beginFullview("a",1)!!;assertTrue(memory.enterFullview(e,"a",1))
        assertTrue(e.admit(ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units())))
        e.worker(Runnable{assertTrue(e.materialize())}).run();return e
    }
    private fun bytes(n:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=n-71)
    @Test fun r8ExactAndOneByteOverBeforePresentationConstruction(){
        val display=TaskPresentation.cost(40)
        val cost=ActiveWorkspaceBudget.estimateUnits(display).second
        for(over in listOf(0L,1L)){
            val memory=OutstandingCoreRead();val original=entry(memory)
            assertTrue(original.reserve(bytes(ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES-cost+over)))
            var produced=0
            if(original.reserve(display)){produced++;TaskBoard("a",emptyList(),emptyList(),false)}
            assertEquals(if(over==0L)1 else 0,produced)
            val state=memory.state()
            if(over==0L)assertEquals(ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES,state.localRetained)
            assertFalse(state.retirementConfirmed);assertNull(memory.beginFullview("a",1))
            println("P110_R8,over=$over,display_bytes=$cost,produced=$produced,state=$state")
        }
    }
    @Test fun t16ExactAndOneByteOverCannotConstructAnotherProjection(){
        val display=OperationalPresentation.cost(8,4,256)
        val cost=ActiveWorkspaceBudget.estimateUnits(display).second
        for(over in listOf(0L,1L)){
            val memory=OutstandingCoreRead();val e=entry(memory)
            // Admit a disjoint transition-only representation on the SAME original record.
            val child=e.supplement(WorkspacePart.MATERIAL_BOARD)!!
            assertTrue(child.admit(ActiveCoreReservation(ActiveWorkspaceBudget.Units(),bytes(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-cost-memory.state().localTransition+over))))
            var produced=0
            if(e.reserve(display)){produced++;OperationalBoard("a",ActionStatus.ACTIVE,emptyList(),emptyList())}
            assertEquals(if(over==0L)1 else 0,produced)
            if(over==0L)assertEquals(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES,memory.state().localTransition)
            assertFalse(memory.state().retirementConfirmed)
            println("P110_T16,over=$over,display_bytes=$cost,produced=$produced,state=${memory.state()}")
        }
    }
    @Test fun noFreeTextLimitAndCheckedOverflow(){
        val small=OperationalPresentation.cost(1,1,1)
        val large=OperationalPresentation.cost(1,1,100_000)
        assertTrue(ActiveWorkspaceBudget.estimateUnits(large).second>ActiveWorkspaceBudget.estimateUnits(small).second)
        try{TaskPresentation.cost(Long.MAX_VALUE);fail("overflow")}catch(_:ArithmeticException){}
        try{OperationalPresentation.cost(1,1,Long.MAX_VALUE);fail("overflow")}catch(_:ArithmeticException){}
    }
}
