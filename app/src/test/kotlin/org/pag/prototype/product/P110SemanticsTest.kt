package org.pag.prototype.product

import androidx.compose.ui.semantics.*
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110SemanticsTest {
    private class Fence:OriginalUiFence {
        var allowed=true;var booked=0
        override fun live()=allowed
        override fun reserve(units:ActiveWorkspaceBudget.Units):Boolean {if(!allowed)return false;booked++;return true}
    }
    @Test fun repeatedSemanticsQueriesBorrowIdenticalAdmittedListsAndActions(){
        val fence=Fence();check(fence.reserve(OriginalSemantics.cost))
        val original=OriginalSemantics(fence,0){contentDescription="Synthetic label";onClick(label="Synthetic action",action=null)}
        val first=SemanticsConfiguration();original.populate(first)
        for(n in listOf(100,1000,32768))repeat(n){
            val receiver=SemanticsConfiguration();original.populate(receiver)
            assertSame(first[SemanticsProperties.ContentDescription],receiver[SemanticsProperties.ContentDescription])
            assertSame(first[SemanticsActions.OnClick],receiver[SemanticsActions.OnClick])
        }
        assertEquals(1,fence.booked)
        fence.allowed=false;val revoked=SemanticsConfiguration();original.populate(revoked)
        assertFalse(revoked.contains(SemanticsProperties.ContentDescription));assertFalse(revoked.contains(SemanticsActions.OnClick))
    }
    @Test fun authorityCheckDoesNotSubscribeDetachedSemanticsToSnapshotChanges(){
        val live=androidx.compose.runtime.mutableStateOf(true)
        val fence=object:OriginalUiFence {
            override fun live()=live.value
            override fun reserve(units:ActiveWorkspaceBudget.Units)=true
        }
        val original=OriginalSemantics(fence,0){contentDescription="Synthetic"}
        var invalidations=0
        val observer=androidx.compose.runtime.snapshots.SnapshotStateObserver{it()}
        observer.start()
        observer.observeReads(Any(),{_:Any->invalidations++}){original.populate(SemanticsConfiguration())}
        live.value=false
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        assertEquals(0,invalidations)
        val after=SemanticsConfiguration();original.populate(after)
        assertFalse(after.contains(SemanticsProperties.ContentDescription))
        observer.stop();observer.clear()
    }

}
