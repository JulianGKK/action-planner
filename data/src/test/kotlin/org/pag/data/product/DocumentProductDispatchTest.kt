package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import java.util.concurrent.Executor

class DocumentProductDispatchTest {
    private class Queue:Executor { val tasks=mutableListOf<Runnable>();override fun execute(command:Runnable){tasks+=command};fun drain(){while(tasks.isNotEmpty())tasks.removeAt(0).run()} }
    private val worker=Queue();private val main=Queue()
    private var valid=true;private var denied=false;private var blocked=0;private var published=0;private var stored=false
    private val c=object:StorageContext {
        override val generation=7L;override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=valid
        override fun publish(result:()->Unit):Boolean { if(!valid)return false;result();return true }
    }
    private val access=object:StorageAccess { override fun acquire(scope:AccessScope):StorageContext? { assertEquals(AccessScope.READY,scope);return c.takeUnless{denied} } }
    private val dispatch=DocumentProductDispatch(access,worker,main,{blocked++})
    @Test fun onlyWorkerExecutesAndOnlyGatedMainPublishes() {
        dispatch.run<Unit>({published++}) { stored=true;DocumentResult.Value(Unit) }
        assertFalse(stored);assertEquals(0,published);worker.drain();assertTrue(stored);assertEquals(0,published)
        main.drain();assertEquals(1,published);assertEquals(0,blocked)
    }
    @Test fun absentReadyDoesNotQueueOrReadSource() {
        denied=true;dispatch.run<Unit>({error("No callback")}) { error("No operation") };assertTrue(worker.tasks.isEmpty())
    }
    @Test fun revokedBeforeWorkerNeverBeginsStorage() {
        dispatch.run<Unit>({error("No callback")}) { error("No operation") };valid=false;worker.drain();main.drain();assertEquals(0,blocked)
    }
    @Test fun commitThenRevokeBeforePublicationDoesNotClaimRollbackOrSuccess() {
        dispatch.run<Unit>({published++}) { stored=true;DocumentResult.Value(Unit) };worker.drain();valid=false;main.drain()
        assertTrue(stored);assertEquals(0,published);assertEquals(0,blocked)
    }
    @Test fun actualRevocationSuppressesCallbackButDoesNotFabricateRollback() {
        dispatch.run<Unit>({published++}) { stored=true;valid=false;throw StorageAccessRevoked() };worker.drain();main.drain()
        assertTrue(stored);assertEquals(0,published);assertEquals(0,blocked)
    }
    @Test fun unexpectedStorageFaultUsesBlockedPathInsteadOfUserRejection() {
        dispatch.run<Unit>({error("No harmless rejection")}) { throw IllegalStateException("synthetic Room fault") };worker.drain();main.drain();assertEquals(1,blocked)
    }
    @Test fun oldQueuedWorkerAfterMaintenanceFenceCannotStart() {
        dispatch.run<Unit>({published++}) { stored=true;DocumentResult.Value(Unit) }
        DocumentNamespaceLane.drainAndFence{};worker.drain();main.drain();assertFalse(stored);assertEquals(0,published);assertEquals(1,blocked)
    }
}
