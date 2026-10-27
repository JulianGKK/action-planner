package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.ActiveWorkspaceBudget

class P113UiSlotTest {
    @Test fun fixedButtonQuoteIncludesBothOldSlotsAndAllCallbackContentObjects(){
        fun bytes(u:ActiveWorkspaceBudget.Units)=ActiveWorkspaceBudget.estimateUnits(u).second
        assertTrue(bytes(originalRowsCost(64))>=64*bytes(originalButtonControlCost))
        assertTrue(bytes(originalButtonControlCost)>=2*bytes(OriginalUiSlotCost.holderAndFirstSizing)+bytes(InputControlCost.click)+bytes(InputControlCost.content))
    }
    @Test fun groupedControlReservationHasIdenticalCostAndSizingCanBeConsumedOnlyOnce(){
        fun bytes(u:ActiveWorkspaceBudget.Units)=ActiveWorkspaceBudget.estimateUnits(u).second
        assertEquals(bytes(OriginalUiSlotCost.holder)+bytes(OriginalUiSlotCost.sizing),bytes(OriginalUiSlotCost.holderAndFirstSizing))
        assertEquals(bytes(listUiCost)+bytes(OriginalUiSlotCost.holderAndFirstSizing),bytes(boundedListBuilderCost))
        var live=true;var charged=0L
        val fence=object:OriginalUiFence{
            override fun live()=live
            override fun reserve(u:ActiveWorkspaceBudget.Units):Boolean{if(!live)return false;charged+=bytes(u);return true}
        }
        assertTrue(fence.reserve(OriginalUiSlotCost.holderAndFirstSizing))
        val slot=OriginalUiSlot<Any>();val held=charged
        assertTrue(slot.takeSizing(fence));assertEquals(held,charged)
        assertTrue(slot.takeSizing(fence));assertEquals(held+bytes(OriginalUiSlotCost.sizing),charged)
        live=false;assertFalse(slot.takeSizing(fence));assertEquals(held+bytes(OriginalUiSlotCost.sizing),charged)
    }
}
