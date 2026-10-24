package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Exclusion tests only. A FullviewEntry is NOT an R8/T16 budget receipt. */
class P106FullviewOverlapTest {
    private fun request(action:String="a",generation:Long=1,ticket:WorkspaceReadTicket?=null)=WorkspaceReadRequest(action,ActionStatus.ACTIVE,generation,emptySet(),ticket=ticket)
    @Test fun pendingAndExposedOriginalCoreDenyFullviewWithoutAnotherRecord(){
        val owner=OutstandingCoreRead();val read=owner.begin()!!
        assertNull(owner.beginFullview("a",1))
        read.callbackOwnsValue();read.revoke();read.workerFinished();read.mainFinished()
        assertNull(owner.beginFullview("a",2));assertFalse(owner.state().retirementConfirmed)
    }
    @Test fun fullviewWorkerAndMainAreExclusiveUntilTheirActualReturns(){
        val owner=OutstandingCoreRead();val entry=owner.beginFullview("a",1)!!
        var main:Runnable?=null;var calls=0
        val work=entry.worker(Runnable{
            assertNull(owner.begin());assertNull(owner.beginFullview("a",1))
            assertTrue(owner.enterFullview(entry,request()))
            main=entry.main(Runnable{calls++;assertNull(owner.beginFullview("a",1));assertNull(owner.begin())})
        })
        work.run();assertNull(owner.beginFullview("a",1));main!!.run();main!!.run();work.run()
        assertEquals(1,calls);assertNotNull(owner.beginFullview("a",1))
        assertNull(owner.begin()) // Sticky fullview fence; callback ACK is no global end.
    }
    @Test fun cancelledForeignWrongGenerationAndDuplicateEntryCannotMaterialize(){
        val owner=OutstandingCoreRead();val other=OutstandingCoreRead()
        val entry=owner.beginFullview("a",1)!!;val ticket=WorkspaceReadTicket();ticket.cancel()
        assertFalse(owner.enterFullview(entry,request(ticket=ticket)))
        assertFalse(other.enterFullview(entry,request()))
        assertFalse(owner.enterFullview(entry,request("b")))
        assertFalse(owner.enterFullview(entry,request(generation=2)))
        assertTrue(owner.enterFullview(entry,request()))
        assertFalse(owner.enterFullview(entry,request()))
    }
    @Test fun cancellationBeforeMaterializationNeedsActualQueuedWorkerAck(){
        val owner=OutstandingCoreRead();val entry=owner.beginFullview("a",1)!!
        val worker=entry.worker(Runnable{})
        assertNull(owner.begin());worker.run();assertNotNull(owner.begin())
        assertFalse(owner.enterFullview(entry,request()))
    }
    @Test fun wrongRejectionDuringAnActualInvocationCannotAcknowledgeItsEnd(){
        val owner=OutstandingCoreRead();val entry=owner.beginFullview("a",1)!!
        val worker=entry.worker(Runnable{
            entry.workerRejected();assertNull(owner.beginFullview("a",1))
            val main=entry.main(Runnable{entry.mainRejected();assertNull(owner.beginFullview("a",1))})
            main.run();assertNull(owner.beginFullview("a",1))
        })
        worker.run();assertNotNull(owner.beginFullview("a",1))
    }
    @Test fun exceptionsAcknowledgeOnlyReturnedScopesAndNoLostMainIsInvented(){
        val owner=OutstandingCoreRead();val entry=owner.beginFullview("a",1)!!
        val worker=entry.worker(Runnable{
            assertTrue(owner.enterFullview(entry,request()))
            entry.main(Runnable{}) // Deliberately lost Main, no false acknowledgement.
            error("SYNTHETIC_DISPATCH_ERROR")
        })
        assertThrows(IllegalStateException::class.java){worker.run()}
        assertNull(owner.beginFullview("a",1));assertNull(owner.begin())
    }
    @Test fun oldAcknowledgementsCannotReleaseTheNextFullviewIdentity(){
        val owner=OutstandingCoreRead();val old=owner.beginFullview("a",1)!!
        val shell=old.worker(Runnable{})
        shell.run();val next=owner.beginFullview("b",2)!!
        old.workerRejected();old.mainRejected();shell.run()
        assertNull(owner.beginFullview("c",3));assertTrue(owner.enterFullview(next,request("b",2)))
    }
}
