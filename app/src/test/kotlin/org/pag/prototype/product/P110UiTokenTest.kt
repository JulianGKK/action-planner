package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import androidx.compose.ui.semantics.*

class P110UiTokenTest {
    private class Fence:OriginalUiFence {
        var current=true;var reservations=0
        override fun live()=current
        override fun reserve(units:ActiveWorkspaceBudget.Units):Boolean {if(!current)return false;reservations++;return true}
    }
    @Test fun oldChoiceAndSemanticsCannotFollowLocalABAOrParentRevoke(){
        val parent=Fence();val identity=OriginalUiEpoch();var used=0
        assertTrue(parent.reserve(OriginalCheckbox.cost(9)))
        val token=OriginalUiToken(parent,identity,identity.advance())
        val label="Synthetic"
        val first=OriginalChoice(token,FormInput{},label,false,true){used++}
        assertSame(label,first.text)
        val aliases=List(32768){first}
        for(n in listOf(100,1000,32768))repeat(n){assertSame(first,aliases[it]);assertSame(first.text,aliases[it].text)}
        first.click();assertEquals(1,used)
        identity.advance();identity.advance() // A→B→A is still a different local generation.
        first.click();assertEquals(1,used)
        val receiver=SemanticsConfiguration();first.semantics.populate(receiver)
        assertFalse(receiver.contains(SemanticsProperties.Selected))
        val latest=OriginalUiToken(parent,identity,identity.version)
        assertTrue(parent.reserve(OriginalCheckbox.cost(9)))
        val next=OriginalChoice(latest,FormInput{},label,true,true){used++}
        next.click();assertEquals(2,used)
        parent.current=false;next.click();assertEquals(2,used)
        assertTrue(OriginalUiEpoch::class.java.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}.all{it.type==java.lang.Long.TYPE})
        assertTrue(OriginalUiToken::class.java.declaredFields.none{it.type==OriginalUiSlot::class.java || java.util.Collection::class.java.isAssignableFrom(it.type)})
    }
}
