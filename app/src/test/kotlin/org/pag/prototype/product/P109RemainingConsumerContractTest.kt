package org.pag.prototype.product

import java.util.IdentityHashMap
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Known blocker witness, NOT a product budget PASS. The production ACTIVE screen calls
 * this same helper without a producer gate. TEST aliases deliberately retain generations.
 * No Compose/GC/worker return is offered as an owner-end receipt. */
class P109RemainingConsumerContractTest {
    @Test fun heldPresentationGenerationsHaveNoMarginalReservationAtTheExistingConsumerGate(){
        val task=Task("t","a","Synthetic",TaskPriority.NORMAL,TaskStatus.OPEN,null,null,null,null,null,0,0,null,0)
        val board=TaskBoard("a",List(40){task.copy(id="t-$it")},emptyList(),false)
        var reservations=0
        val action=Action("a","Synthetic","Synthetic",1,2,0,ActionStatus.ACTIVE)
        val model=TaskModel(null,{SessionSnapshot(SessionState.READY,1)},{action},{},{},suppliedBoard={board})
        model.bindCopyGate{_,producer->reservations++;producer();null}
        val held=List(32_768){activeTaskContent(model.board!!,null)}
        val identities=IdentityHashMap<List<Task>,Boolean>()
        for(value in held){assertEquals(40,value.active.size);identities[value.active]=true}
        assertEquals(held.size,identities.size)
        assertEquals(0,reservations)
        // Only one actually held 40-reference list per generation. Excludes ALL old graph,
        // scratch sorts/filter lists, summaries/strings, DTOs and callback/Compose resources.
        val units=ActiveWorkspaceBudget.Units(lists=held.size.toLong(),listCapacity=held.size.toLong()*40)
        val required=ActiveWorkspaceBudget.estimateUnits(units)
        assertEquals(ActiveWorkspaceBudget.Decision.ADMITTED,required.first)
        assertEquals(14_680_064L,required.second)
        println("P109_KNOWN_BLOCKER,generations=32768,held_lists=32768,list_capacity=1310720,minimum_R_model_bytes=14680064,reservations=0,product_budget=UNPROVEN")
        assertTrue(required.second>ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
        assertSame(task.javaClass,held.first().active.first().javaClass)
    }
}
