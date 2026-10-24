package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P104ReaderRevisionTest {
    @Test fun boundedFullReaderCarriesEveryNonzeroRevision()=ActiveCoreStoreTest.H(taskCount=1,history=5).use{h->
        for(table in EDIT_REVISION_TABLES)h.exec("UPDATE $table SET editRevision=7")
        h.db.beforePrepare={}
        val board=runBlocking{BoundedActiveStore(h.db,h.context,{true}).read(h.requested.binding)}.operations
        assertTrue(board.roles.all{it.editRevision==7L})
        assertTrue(board.persons.all{it.person.editRevision==7L && it.plan.editRevision==7L && it.state!!.editRevision==7L})
        val tasks=runBlocking{BoundedTaskStore(h.db,h.context,{true}).read(h.requested.binding)}
        assertTrue(tasks.board.tasks.all{it.editRevision==7L})
    }

    @Test fun normalLargeFixturesAndAllStoredRevisionsReachEditableDetailsWithoutZeroDefaults(){
        for(roles in listOf(6,32,100))ActiveCoreStoreTest.H(200,1000,5,roles).use{h->
            for(table in EDIT_REVISION_TABLES)h.exec("UPDATE $table SET editRevision=7")
            val core=h.read() as ActiveCoreResult.Value
            assertEquals(200L,core.core.people.overall.total);assertEquals(1000L,core.core.tasks.overall.total)
            assertEquals(roles,core.core.functions.items.size)
            assertTrue(core.core.functions.items.all{it.role.editRevision==7L})
            val person=((h.peopleRead(detail="p0") as CompactPeopleResult.Value).payload as CompactPersonDetail).person
            assertEquals(7L,person.person.editRevision);assertEquals(7L,person.plan.editRevision);assertEquals(7L,person.state!!.editRevision)
            h.db.beforePrepare={}
            val task=runBlocking{(CompactTaskStore(h.db,h.context,{true}).detail(h.requested,"t0000").payload as CompactTaskDetail).task}
            assertEquals(7L,task.editRevision)
            try{runBlocking{BoundedActiveStore(h.db,h.context,{true}).read(h.requested.binding)}
                println("P104_FULLVIEW roles=$roles admitted=true")
            }catch(e:BoundedActiveRejected){assertEquals(BoundedActiveStatus.BUDGET_REFUSED,e.status)
                println("P104_FULLVIEW roles=$roles BUDGET_REFUSED=true no_budget_relaxation=true")}
            println("P104_LARGE roles=$roles people=200 tasks=1000 events=5000 retained=${core.retainedCharge} transition=${core.transitionCharge} revisions=7 product_100_role_approval=false")
        }
    }
}
