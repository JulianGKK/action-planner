package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference

/** Production loader/port/lifetime; fake producer has NO DB/Compose/Native byte claims. */
class OutstandingCoreReadTest {
    private class H {
        val worker=ArrayDeque<Runnable>();val main=ArrayDeque<Runnable>()
        val mutations=UncachedWorkspaceMutations()
        var ready=true;var generation=1L;var reads=0;var callbacks=0
        var ticket:WorkspaceMutationInvalidation.ActiveReadTicket?=null
        var life:ActiveCoreLifetime?=null
        var failure=false;var uncertain=false;var refused=false
        var during:(()->Unit)?=null
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                if(!ready)return null
                val g=generation
                return object:StorageContext {
                    override val scope=AccessScope.READY;override val generation=g
                    override fun check(point:AccessPoint)=ready && generation==this@H.generation
                    override fun publish(result:()->Unit):Boolean {if(!check(AccessPoint.PUBLICATION))return false;result();return check(AccessPoint.PUBLICATION)}
                }
            }
        }
        val loader=ProductActiveCoreLoader(access,mutations,{worker.add(it)},{main.add(it)}){_,t,_,m->
            ticket=t;reads++
            if(uncertain){m.storageStarting();throw IllegalStateException("SYNTHETIC_UNCONFIRMED_DB_END")}
            if(failure)throw IllegalStateException("SYNTHETIC_BEFORE_DB")
            if(refused)ActiveCoreResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED) else {
                val units=ActiveWorkspaceBudget.Units(objects=100)
                val reservation=ActiveCoreReservation(units,units)
                m.admit(reservation,12800,12800);during?.invoke()
                val s=CompactTaskState(P3bFreeActiveCoreBinding(t.actionId,t.generation),t.mutationEpoch)
                val counts=CompactTaskCounts(0,0,0,0,0)
                ActiveCoreResult.Value(ActiveCorePayload(s,Action(t.actionId,"Synthetic","Synthetic",0,0,0,ActionStatus.ACTIVE),
                    ActiveCorePeoplePage(s,CompactPeopleCounts(0,0,0,0,0,0,0,0),0,emptyList(),false),
                    CompactFunctions(emptyList(),0,0,0),ActiveCoreTaskPage(s,counts,counts,emptyList(),false)),reservation,12800,12800)
            }
        }
        fun load()=loader.loadOwned("a"){_,l->callbacks++;life=l}
        fun pending(){assertTrue(load());worker.remove().run();assertEquals(1,loader.outstandingState().pendingCandidates)}
    }
    @Test fun aPendingCandidateBlocksSecondAndThirdBeforeTicketsAndSqlWithoutReplacingAuthority() {
        val h=H();h.pending();val ticket=h.ticket!!
        repeat(2){assertFalse(h.load())}
        assertEquals(1,h.reads);assertTrue(ticket.isLive());assertEquals(0L,h.mutations.mutationEpoch)
        assertEquals(1,h.loader.outstandingState().pendingCandidates)
        val task=h.main.remove();task.run();task.run()
        assertEquals(1,h.callbacks);assertEquals(0,h.loader.outstandingState().pendingCandidates)
        assertEquals(12800L,h.loader.outstandingState().retained)
        assertEquals(ActiveWorkspaceBudget.LIMIT_BYTES,h.loader.outstandingState().transition)
    }
    @Test fun discardDrainsOnlyKnownHandoffAndLateDuplicateCannotRevokeNewOriginalTicket() {
        val h=H();h.pending();val old=h.ticket!!;val task=h.main.remove()
        h.loader.cancelPending();assertFalse(old.isLive());assertFalse(h.loader.outstandingState().retirementConfirmed)
        assertFalse(h.load());task.run();assertTrue(h.loader.outstandingState().retirementConfirmed)
        assertTrue(h.load());h.worker.remove().run();val new=h.ticket!!
        task.run();task.run();assertTrue(new.isLive());assertEquals(0,h.callbacks)
        h.main.remove().run();assertEquals(1,h.callbacks);assertTrue(h.life!!.authorizeHit("a"))
        assertEquals(0L,h.mutations.mutationEpoch)
    }
    @Test fun anActuallyLostMainRemainsBookedEvenAfterCancellationWithoutQueueAcknowledgement() {
        val h=H();h.pending();h.main.clear() // No successful delivery acknowledgement.
        assertFalse(h.load());assertEquals(1,h.loader.outstandingState().pendingCandidates)
        h.loader.cancelPending();assertFalse(h.loader.outstandingState().retirementConfirmed)
        assertEquals(0,h.loader.outstandingState().pendingCandidates)
        assertEquals(ActiveWorkspaceBudget.LIMIT_BYTES,h.loader.outstandingState().transition)
        assertFalse(h.load())
    }
    @Test fun cancellationDuringMaterializationCannotRegrantBeforeWorkerScopeEnds() {
        val h=H();h.during={h.mutations.revokeActiveRead();assertFalse(h.load())}
        assertTrue(h.load());h.worker.remove().run()
        assertEquals(0,h.callbacks);assertTrue(h.main.isEmpty());assertTrue(h.loader.outstandingState().retirementConfirmed)
        assertEquals(0L,h.mutations.mutationEpoch)
    }
    @Test fun everyWriterSessionAndAuthorityRaceRevokesOnlyOriginalUndeliveredHandoff() {
        for(mode in listOf("CORE","DOCUMENT","UNKNOWN","LOCK","BLOCKED","GENERATION","ACTION")) {
            val h=H();h.pending();val old=h.ticket!!;val task=h.main.remove()
            when(mode) {
                "CORE"->assertNotNull(h.mutations.beforeTaskQueue("a",1))
                "DOCUMENT"->assertNotNull(h.mutations.beforeDocumentQueue("a",1))
                "UNKNOWN"->{val p=h.mutations.beforeDocumentQueue("a",1)!!;assertTrue(p.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)))}
                "ACTION"->assertNotNull(h.mutations.beforeActiveRead("b",1))
                else->{h.ready=false;h.mutations.revokeActiveRead();if(mode=="GENERATION")h.generation++}
            }
            assertFalse(old.isLive());task.run();task.run();assertEquals(0,h.callbacks)
            assertTrue(h.loader.outstandingState().retirementConfirmed)
            assertEquals(if(mode in setOf("CORE","DOCUMENT","UNKNOWN"))1L else 0L,h.mutations.mutationEpoch)
            if(mode!="ACTION")assertFalse(h.load())
            if(mode=="GENERATION"){h.ready=true;assertTrue(h.load())}
        }
    }
    @Test fun knownReaderFailureAndBudgetRefusalEndChargeButUnconfirmedCleanupStaysBooked() {
        for(mode in listOf("failure","budget","uncertain")) {
            val h=H();h.failure=mode=="failure";h.refused=mode=="budget";h.uncertain=mode=="uncertain"
            assertTrue(h.load());h.worker.remove().run();h.main.remove().run()
            assertFalse(h.ticket!!.isLive());assertEquals(1,h.callbacks);assertEquals(0L,h.mutations.mutationEpoch)
            assertEquals(mode!="uncertain",h.loader.outstandingState().retirementConfirmed)
            if(mode=="uncertain"){
                assertEquals(OutstandingCoreRead.Phase.FAILED_OR_UNCONFIRMED,h.loader.outstandingState().phase)
                assertEquals(ActiveWorkspaceBudget.LIMIT_BYTES,h.loader.outstandingState().transition);assertFalse(h.load())
            } else {assertEquals(0L,h.loader.outstandingState().transition);assertTrue(h.load())}
        }
    }
    @Test fun vmClearAndComposeDisposalDoNotConfirmFrameRetirementOrNewGenerationBudget() {
        val h=H();h.pending();h.main.remove().run()
        h.life!!.compositionObserved();h.life!!.close();h.life!!.compositionDisposed()
        assertFalse(h.ticket!!.isLive());assertFalse(h.load())
        h.generation=2;assertFalse(h.load())
        val state=h.loader.outstandingState()
        assertTrue(state.compositionObserved && state.compositionDisposed)
        assertFalse(state.retirementConfirmed);assertEquals(OutstandingCoreRead.Phase.COMPOSE_TRANSITION,state.phase)
        assertEquals(ActiveWorkspaceBudget.LIMIT_BYTES,state.transition)
    }
    @Test fun fullViewFencePreservesOldChargesAndCannotMintAnEditorFallbackCore() {
        val h=H();h.pending();h.main.remove().run();h.life!!.close();h.loader.fullViewStarting()
        assertFalse(h.load());assertTrue(h.loader.outstandingState().fullViewUnconfirmed)
        assertFalse(h.loader.outstandingState().retirementConfirmed)
    }
    @Test fun duplicateWorkerCannotAcknowledgeWhileOriginalWorkerStillMaterializes() {
        val h=H();val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        h.during={entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS))}
        assertTrue(h.load());val task=h.worker.remove()
        val error=AtomicReference<Throwable?>()
        val thread=Thread{try{task.run()}catch(t:Throwable){error.set(t)}}
        thread.start();assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
        try {
            task.run() // Erroneous concurrent executor repeat must not acknowledge the first.
            h.mutations.revokeActiveRead();assertFalse(h.load())
            assertFalse(h.loader.outstandingState().retirementConfirmed)
        } finally {release.countDown();thread.join(5000)}
        assertFalse(thread.isAlive);assertNull(error.get());assertTrue(h.loader.outstandingState().retirementConfirmed)
    }
    @Test fun uncertainQueueAcceptanceCannotFreeReservationBeforeTheQueuedShellAcknowledges() {
        for(workerQueue in listOf(false,true)) {
            val h=H();val queue=ArrayDeque<Runnable>()
            val uncertain=java.util.concurrent.Executor{queue.add(it);error("SYNTHETIC_QUEUE_ACCEPTANCE_UNKNOWN")}
            val inline=java.util.concurrent.Executor{it.run()}
            val loader=ProductActiveCoreLoader(h.access,h.mutations,if(workerQueue)uncertain else inline,
                if(workerQueue)inline else uncertain){_,_,_,_->ActiveCoreResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED)}
            val queued=loader.loadOwned("a"){_,_->fail("Canceled unknown queue must not deliver")}
            assertEquals(!workerQueue,queued)
            assertFalse(loader.outstandingState().retirementConfirmed)
            assertEquals(ActiveWorkspaceBudget.LIMIT_BYTES,loader.outstandingState().transition)
            assertFalse(loader.loadOwned("a"){_,_->fail()})
            queue.remove().run();assertTrue(loader.outstandingState().retirementConfirmed)
            assertEquals(0L,h.mutations.mutationEpoch)
        }
    }

}
