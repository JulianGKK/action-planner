package org.pag.prototype.product

import java.util.IdentityHashMap
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Original 32,768-generation / 40-row witness retained as a production regression.
 * TEST aliases still retain every result. The producer now borrows the board's single
 * admitted immutable projection; there is no per-recomposition payload generation. */
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
        assertEquals(1,identities.size)
        assertSame(board.active,held.first().active)
        for(value in held)assertSame(held.first(),value)
        assertEquals(0,reservations)
        // Historical counterfactual: if every invocation allocated the former 40-reference list.
        // Preserve the original red-case size calculation, excluding ALL old graph,
        // scratch sorts/filter lists, summaries/strings, DTOs and callback/Compose resources.
        val units=ActiveWorkspaceBudget.Units(lists=held.size.toLong(),listCapacity=held.size.toLong()*40)
        val required=ActiveWorkspaceBudget.estimateUnits(units)
        assertEquals(ActiveWorkspaceBudget.Decision.ADMITTED,required.first)
        assertEquals(14_680_064L,required.second)
        println("P110_REGRESSION,generations=32768,held_aliases=32768,distinct_payload_lists=1,old_counterexample_bytes=14680064,marginal_payload_copies=0")
        assertTrue(required.second>ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
        assertSame(task.javaClass,held.first().active.first().javaClass)
    }
}
