package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P109WriterBudgetTest {
    private class H(r:Long=100_000,t:Long=200_000){
        var allowed=true;var gen=1L
        val source=object:StorageAccess{override fun acquire(scope:AccessScope):StorageContext? {
            if(!allowed)return null
            val captured=gen
            return object:StorageContext{override val scope=AccessScope.READY;override val generation=captured
                override fun check(point:AccessPoint)=allowed && gen==captured
                override fun publish(result:()->Unit):Boolean{if(!check(AccessPoint.PUBLICATION))return false;result();return check(AccessPoint.PUBLICATION)}
            }
        }}
        val memory=OutstandingCoreRead();val entry=memory.beginFullview("a",1)!!
        val budget=OriginalWriterBudget(source){memory}
        init{assertTrue(memory.enterFullview(entry,"a",1))
            fun bytes(n:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=n-71)
            assertTrue(entry.admit(ActiveCoreReservation(bytes(r),bytes(t))));entry.worker(Runnable{assertTrue(entry.materialize())}).run()
        }
    }
    @Test fun submissionCopiesBookOriginalAndNeverReleaseOnWriterEnd(){
        val h=H();val before=h.memory.state();assertTrue(h.budget.reserve("a",1000,8))
        val n=ActiveWorkspaceBudget.estimateUnits(OriginalWriterBudget.copies(1000,8)).second
        assertEquals(before.localRetained+n,h.memory.state().localRetained)
        assertEquals(before.localTransition+n,h.memory.state().localTransition)
        assertNull(h.memory.beginFullview("a",1));assertFalse(h.memory.state().retirementConfirmed)
        h.allowed=false;assertFalse(h.budget.reserve("a",1,1))
    }
    @Test fun wrongActionGenerationAndOverflowCannotConstructWriterCopies(){
        val h=H();val before=h.memory.state()
        assertFalse(h.budget.reserve("other",10,1));h.gen=2;assertFalse(h.budget.reserve("a",10,1));h.gen=1
        assertFalse(h.budget.reserve("a",Long.MAX_VALUE,Long.MAX_VALUE));assertEquals(before,h.memory.state())
    }
    @Test fun exactRetainedAndTransitionThenOneByteRefuseWithoutChangingOldCost(){
        for(which in listOf("r","t"))for(offset in listOf(0L,1L)){
            val limit=if(which=="r")8_388_608L else 16_777_216L
            val start=limit-72+offset
            val h=H(if(which=="r")start else 100_000,if(which=="t")start else 200_000)
            val before=h.memory.state()
            val extra=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=1)
            assertEquals(72L,ActiveWorkspaceBudget.estimateUnits(extra).second)
            assertEquals(offset==0L,h.memory.reserveHeld("a",1,ActiveCoreReservation(extra,extra)))
            if(offset==1L)assertEquals(before,h.memory.state())
            else assertEquals(limit,if(which=="r")h.memory.state().localRetained else h.memory.state().localTransition)
        }
    }
    @Test fun currentStoredTextIsPreflightedBeforeAnyWriterDto()=runBlocking {
        SqliteHostConnection().use{db->
            db.execute("INSERT INTO actions VALUES('a','Synthetic','Description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
            db.execute("INSERT INTO persons VALUES('p','a',printf('%.*c',100000,'x'),printf('%.*c',100000,'x'),0,0)")
            val h=H(8_388_608-100_000,200_000);val before=h.memory.state();var dtos=0
            if(h.budget.preflight(db,h.source.acquire(AccessScope.READY)!!,"a",setOf("actions","persons"),"p"))dtos++
            assertEquals(0,dtos);assertEquals(0,db.blobReads);assertEquals(1,db.textReads)
            assertEquals(before,h.memory.state())
        }
    }
    @Test fun exposedCompactRecordCannotTakeUnchargedWriterFallback(){
        val h=H();assertFalse(h.budget.reserve("a",Long.MAX_VALUE,1))
        val compact=OutstandingCoreRead();compact.begin()!!
        val budget=OriginalWriterBudget(h.source){compact}
        assertFalse(budget.reserve("a",10,1))
    }
}
