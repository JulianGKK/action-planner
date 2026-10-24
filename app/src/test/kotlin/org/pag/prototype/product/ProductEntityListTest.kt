package org.pag.prototype.product
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
class ProductEntityListTest {
    private fun tasks(n:Int)=TaskBoard("synthetic",List(n){i->Task("task-$i","synthetic","Synthetic $i",TaskPriority.entries[i%3],TaskStatus.entries[i%4],null,null,"person-${i%200}",null,null,i.toLong(),i.toLong(),null, editRevision = 0)},emptyList())
    @Test fun largeCountsMatchOriginalRulesWithoutSortingPerPerson(){val b=tasks(1000);repeat(200){i->assertEquals(b.active.count{it.affectedPersonId=="person-$i"},b.affectedCount("person-$i"))}}
    @Test fun lazyRowsContainEveryTaskOnceAndKeepDomainOrder(){val b=tasks(1000);val c=activeTaskContent(b,null);assertEquals(b.active,c.active);assertEquals(b.closed,c.closed);val keys=(c.active+c.closed).map{entityRowKey("task",it.id)};assertEquals(1000,keys.size);assertEquals(1000,keys.toSet().size)}
    @Test fun scopedNumericAnchorsDoNotMixDestinations(){val p=ProductScrollPositions();p.at("ACTIVE_TASKS").apply{index=402;offset=19};p.at("ACTIVE_PERSONS").index=10;assertEquals(402,p.at("ACTIVE_TASKS").index);assertEquals(19,p.at("ACTIVE_TASKS").offset);assertEquals(0,ProductScrollPositions().at("ACTIVE_TASKS").index)}
    @Test fun statusChangesMoveRowsWithoutDuplicatingKeys(){val b=tasks(100);val t=b.tasks.first();val changed=b.copy(tasks=b.tasks.map{if(it.id==t.id)it.copy(status=TaskStatus.DONE) else it});assertEquals(100,(changed.active+changed.closed).map{entityRowKey("task",it.id)}.toSet().size);assertFalse(changed.active.any{it.id==t.id});assertTrue(changed.closed.any{it.id==t.id})}
}
