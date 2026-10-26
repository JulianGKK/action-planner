package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110InputCallbackTest {
    private class Fence:OriginalUiFence {
        var allowed=true;var budget=true;var bookings=0
        override fun live()=allowed
        override fun reserve(units:ActiveWorkspaceBudget.Units):Boolean{if(!allowed || !budget)return false;bookings++;return true}
    }
    @Test fun noCallbackConstructionWhenReservationRefused(){
        val fence=Fence();fence.budget=false
        var used=0
        assertNull(admitInputClick(fence,FormInput{}){used++})
        assertEquals(0,used);assertEquals(0,fence.bookings)
    }
    @Test fun expiredCallbackCannotCleanFocusOrUseDataEvenWhenHeld(){
        val fence=Fence();var used=0;var dismissals=0
        val click=admitInputClick(fence,FormInput{dismissals++}){used++}!!
        assertEquals(1,fence.bookings)
        click();assertEquals(1,used);assertEquals(1,dismissals)
        fence.allowed=false;repeat(1000){click()}
        assertEquals(1,used);assertEquals(1,dismissals);assertEquals(2,fence.bookings)
        assertTrue(OriginalInputClick::class.java.declaredFields.none{it.type==TaskBoard::class.java || it.type==OperationalBoard::class.java || it.type==WorkspaceSnapshot::class.java})
    }
    @Test fun synchronousFocusCleanupRevokePreventsCallbackBody(){
        val fence=Fence();var used=0
        val click=admitInputClick(fence,FormInput{fence.allowed=false}){used++}!!
        click();assertEquals(0,used)
    }
}
