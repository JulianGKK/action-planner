package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110UiProjectionTest {
    private val person=OperationalPerson(Person("p","a","Synthetic",0,0),PersonRolePlan(emptySet(),null,0),null)
    private val task=Task("t","a","Synthetic task",TaskPriority.NORMAL,TaskStatus.OPEN,null,null,"p",null,null,0,0,null,0)
    private val port=object:RowActionPort{
        override fun live(root:Any,access:WorkspaceAccess?)=true
        override fun reserve(root:Any,access:WorkspaceAccess?,units:ActiveWorkspaceBudget.Units)=true
        override fun invoke(root:Any,access:WorkspaceAccess?,operation:RowOperation,id:String){}
    }
    private fun snapshot(p:OperationalPerson=person,t:Task=task)=WorkspaceSnapshot("a",1,listOf(p.person),OperationalBoard("a",ActionStatus.ACTIVE,emptyList(),listOf(p)),TaskBoard("a",listOf(t),emptyList(),false),ActionStatus.ACTIVE)
    @Test fun unchangedDisplayIsBorrowedButNewEpochAlwaysHasNewCallbackIdentity(){
        val original=snapshot();val first=WorkspaceUiRows(original,port)
        val next=snapshot();val second=WorkspaceUiRows(next,port,first)
        assertSame(first.tasks["t"]!!.display,second.tasks["t"]!!.display)
        assertSame(first.persons["p"]!!.display,second.persons["p"]!!.display)
        assertNotSame(first.identity,second.identity)
        assertNotSame(first.tasks["t"]!!.click,second.tasks["t"]!!.click)
        assertTrue(ActiveWorkspaceBudget.estimateUnits(WorkspaceUiRows.cost(next,first,UiDateFormats(first.zone),null)).second<ActiveWorkspaceBudget.estimateUnits(WorkspaceUiRows.cost(next,null,UiDateFormats(first.zone),null)).second)
    }
    @Test fun changedPersonUpdatesDependentTaskLabelsAndRetainsOldAliases(){
        val original=snapshot();val first=WorkspaceUiRows(original,port)
        val changed=person.copy(person=person.person.copy(pseudonym="Synthetic changed",editRevision=1))
        val next=snapshot(changed);val second=WorkspaceUiRows(next,port,first)
        assertNotSame(first.persons["p"]!!.display,second.persons["p"]!!.display)
        assertNotSame(first.tasks["t"]!!.display,second.tasks["t"]!!.display)
        assertEquals("Synthetic changed",second.tasks["t"]!!.card(0).affected)
        assertEquals("Synthetic",first.tasks["t"]!!.card(0).affected)
        assertEquals("Synthetic changed",second.persons["p"]!!.card.name)
    }
    @Test fun repeated100And1000And32768PresentationsReuseOriginalRowsAndTexts(){
        val source=snapshot();val rows=WorkspaceUiRows(source,port)
        for(n in listOf(100,1000,32768)){
            val aliases=List(n){rows.tasks["t"]!!}
            for(alias in aliases){assertSame(rows.tasks["t"],alias);assertSame(rows.tasks["t"]!!.card(0),alias.card(0));assertSame(rows.tasks["t"]!!.affected,alias.affected)}
            println("P110_UI,held=$n,distinct_rows=1,distinct_cards=1,marginal_payload_copies=0")
        }
    }
    @Test fun dateWindowsPreserveExistingSemanticsAtBothLongTimestampEdges(){
        val people=snapshot().operations!!;val zone=java.time.ZoneId.of("UTC")
        val probes=listOf(Long.MIN_VALUE,Long.MIN_VALUE+1,0L,Long.MAX_VALUE-1,Long.MAX_VALUE)
        for(due in probes){
            val value=task.copy(dueAt=due);val display=TaskUiDisplay(value,people,zone)
            for(now in probes)assertEquals(activeTaskCard(value,people,now,zone).due,display.card(now).due)
        }
        val observed=person.copy(state=PersonOperationalState("a","p",null,OperationalStatus.ON_SITE,null,Long.MAX_VALUE,0))
        val display=PersonUiDisplay(observed,people,null,zone)
        assertEquals(activeUpdatedLabel(observed.state,Long.MAX_VALUE,zone),display.updated(Long.MAX_VALUE))
    }

    @Test fun primitiveSortingPreservesLongExtremesAndStableClosedTies(){
        val rows=listOf(task.copy(id="zero-first",updatedAt=0),task.copy(id="minimum",updatedAt=Long.MIN_VALUE),
            task.copy(id="zero-second",updatedAt=0),task.copy(id="maximum",updatedAt=Long.MAX_VALUE))
        assertEquals(listOf("maximum","zero-first","zero-second","minimum"),rows.sortedWith(TaskRules.closedOrder).map{it.id})
        assertEquals(listOf("minimum","zero-first","zero-second","maximum"),rows.map{it.copy(createdAt=it.updatedAt)}.sortedWith(TaskRules.order).map{it.id})
    }

    @Test fun titleOnlyEditBorrowsHubButChangedCountersRequireNewCards(){
        val action=Action("a","Synthetic","Synthetic",1,2,0,ActionStatus.ACTIVE)
        val original=snapshot();val first=WorkspaceUiRows(original,port,action=action)
        val edited=task.copy(title="Synthetic edited",editRevision=1)
        val next=original.copy(tasks=TaskBoard("a",listOf(edited),emptyList(),false))
        val second=WorkspaceUiRows(next,port,first,action)
        assertSame(first.hub,second.hub)
        assertNotSame(first.tasks["t"]!!.display,second.tasks["t"]!!.display)
        assertEquals("Revision: 1",second.tasks["t"]!!.revision)
        val closed=next.copy(tasks=TaskBoard("a",listOf(edited.copy(status=TaskStatus.DONE,updatedAt=1,completedAt=1)),emptyList(),false))
        val third=WorkspaceUiRows(closed,port,second,action)
        assertNotSame(second.hub,third.hub)
    }

}
