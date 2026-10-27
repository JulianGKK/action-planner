package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P113FunctionsTest {
    @Test fun functionsChildReadsOnlyAdmittedCatalogueAndKeepsGlobalCounts()=runBlocking {
        ActiveCoreStoreTest.H(200,1000,5,32).use{h->
            val store=CompactPeopleStore(h.db,h.context,{h.ready.get()})
            var admitted=false
            h.db.beforePrepare={sql->if(sql.startsWith("SELECT rowid,"))assertTrue("No child payload before original admission",admitted)}
            val before=h.db.blobReads
            val plan=store.prepareFunctions(h.requested)
            assertEquals(before,h.db.blobReads)
            val memory=OutstandingCoreRead();val owner=memory.beginFullview("a",1)!!
            assertTrue(memory.enterFullview(owner,"a",1))
            val empty=ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units())
            assertTrue(owner.admit(empty));assertTrue(owner.materialize());owner.worker(Runnable{}).run()
            val child=owner.supplement(WorkspacePart.TASKS)!!
            val quote=CoreReadAdmission.admit(listOf(plan))
            assertTrue(child.admit(quote.reservation))
            admitted=true
            val functions=plan.materialize(quote)
            assertEquals(32,functions.items.size);assertEquals(200L,functions.onSiteCount)
            assertEquals(200L,functions.items.sumOf{it.onSiteCount});assertEquals(0L,functions.withoutFunction)
            assertEquals(before+32*4,h.db.blobReads)
            assertEquals(32L,store.roleCount("a"))
            assertEquals(quote.retained+ActiveWorkspaceBudget.estimateUnits(ActiveWorkspaceBudget.Units(objects=16,references=64)).second,memory.state().localRetained)
            assertFalse(owner.reserve(ActiveWorkspaceBudget.Units(arrayPayloadBytes=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)))
            assertNull(memory.begin());assertFalse(memory.state().retirementConfirmed)
        }
    }
}
