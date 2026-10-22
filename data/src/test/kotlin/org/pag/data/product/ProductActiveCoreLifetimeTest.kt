package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.util.concurrent.atomic.AtomicReference

/** Real loader/lifetime, coordinator and common writer owner. Synthetic auth and read
 * candidate only: no Room/SQLCipher or Android E2E claim. Hits cannot invoke the reader.
 */
class ProductActiveCoreLifetimeTest {
    private class H {
        var now=0L
        val session=SessionCoordinator(object:AuthenticationExperiment {
            override fun start(mode:KeyExperiment,complete:(AuthenticationOutcome)->Unit):Cancellation {
                complete(AuthenticationOutcome.Granted(object:SyntheticOperation {
                    override fun perform()=OperationResult.SUCCEEDED
                    override fun release(){}
                },now));return Cancellation{}
            }
        },Maintenance{it(true);Subscription{}},MonotonicTime{now},ProductSessionTiming.policy,{true})
        val port=UncachedWorkspaceMutations()
        var acquisitions=0;var publications=0;var reads=0
        var missing=false;var foreignGeneration=false;var wrongScope=false
        var falseAfter=false;var skipCallback=false
        var during:(()->Unit)?=null;var after:(()->Unit)?=null
        val contexts=mutableListOf<StorageContext>()
        val access=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                acquisitions++
                if(missing)return null
                val c=session.acquire(scope) ?: return null
                return object:StorageContext by c {
                    override val scope=if(wrongScope)AccessScope.MAINTENANCE else c.scope
                    override val generation=c.generation+if(foreignGeneration)1 else 0
                    override fun publish(result:()->Unit):Boolean {
                        publications++
                        val ok=c.publish {during?.invoke();if(!skipCallback)result()}
                        after?.invoke()
                        return ok && !falseAfter
                    }
                }.also{contexts.add(it)}
            }
        }
        lateinit var ticket:WorkspaceMutationInvalidation.ActiveReadTicket
        lateinit var life:ActiveCoreLifetime
        val slot=AtomicReference<ActiveCoreResult?>()
        val loader=ProductActiveCoreLoader(access,port,{it.run()},{it.run()}) {_,t,_->
            reads++;ticket=t
            val state=CompactTaskState(P3bFreeActiveCoreBinding(t.actionId,t.generation),t.mutationEpoch)
            val counts=CompactTaskCounts(0,0,0,0,0)
            ActiveCoreResult.Value(ActiveCorePayload(state,Action(t.actionId,"Synthetic","Synthetic",0,0,0,ActionStatus.ACTIVE),
                ActiveCorePeoplePage(state,CompactPeopleCounts(0,0,0,0,0,0,0,0),0,emptyList(),false),
                CompactFunctions(emptyList(),0,0,0),ActiveCoreTaskPage(state,counts,counts,emptyList(),false)),
                ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units()),0,0)
        }
        init {
            session.foreground(true);session.authenticate(Experiment.PER_OPERATION)
            session.observe{if(it.state in setOf(SessionState.LOCKED,SessionState.BLOCKED))port.revokeActiveRead()}
            check(loader.loadOwned("a") {value,lifetime->
                life=checkNotNull(lifetime);life.onInvalidated{slot.set(null)}
                check(life.publish{slot.set(value)})
            })
        }
        fun writer(document:Boolean,unknown:Boolean=false) {
            val pending=if(document)port.beforeDocumentQueue("a",ticket.generation)!!
                else port.beforeTaskQueue("a",ticket.generation)!!
            if(unknown) {
                if(document)pending.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED))
                else pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED))
            }
        }
    }
    @Test fun oldLifetimeCloseCannotRevokeNewRead() {
        val h=H();val old=h.life
        val next=h.port.beforeActiveRead("other",h.ticket.generation)!!
        old.close();old.close()
        assertTrue(next.isLive());assertTrue(h.port.isCurrentActiveRead(next))
        assertEquals(0L,h.port.mutationEpoch)
    }
    @Test fun explicitLifetimeCloseMustRevokeOriginalReadTicket() {
        val h=H()
        h.life.close()
        assertFalse(h.ticket.isLive())
        assertFalse(h.port.isCurrentActiveRead(h.ticket))
        assertNull(h.slot.get())
        assertEquals(0L,h.port.mutationEpoch)
    }
    @Test fun eachHitAcquiresFreshReadyPublishesAndKeepsExactReadPayloadBudgetAndEpoch() {
        val h=H();val original=h.ticket;val payload=h.slot.get();val initial=h.publications
        repeat(4) {
            assertTrue(h.life.authorizeHit("a"))
            assertSame(payload,h.slot.get());assertSame(original,h.ticket)
            assertTrue(h.port.isCurrentActiveRead(original))
        }
        assertEquals(5,h.acquisitions);assertEquals(initial+4,h.publications)
        assertEquals(5,h.contexts.size)
        for(i in h.contexts.indices)for(j in 0 until i)assertNotSame(h.contexts[i],h.contexts[j])
        assertEquals(1,h.reads);assertEquals(0L,h.port.mutationEpoch)
        assertFalse(h.loader.reuseEnabled);assertFalse(h.port.ramReuseEnabled)
    }
    @Test fun falseAfterCallbackNoCallbackMissingReadyGenerationAndScopeAreTerminal() {
        for(mode in 0..5) {
            val h=H()
            when(mode){0->h.falseAfter=true;1->h.skipCallback=true;2->h.missing=true
                3->h.foreignGeneration=true;4->h.wrongScope=true;5->h.after={throw IllegalStateException("synthetic dispatch failure")}}
            assertFalse(h.life.authorizeHit("a"));assertNull(h.slot.get())
            h.falseAfter=false;h.skipCallback=false;h.missing=false;h.foreignGeneration=false;h.wrongScope=false;h.after=null
            assertFalse(h.life.authorizeHit("a"));assertEquals(1,h.reads)
        }
    }
    @Test fun foreignActionAndPortOrReplacementTicketNeverAuthorizeOldData() {
        val h=H();assertFalse(h.life.authorizeHit("b"));assertNull(h.slot.get())
        val other=H();val foreign=UncachedWorkspaceMutations()
        assertFalse(foreign.isCurrentActiveRead(other.ticket))
        val replacement=other.port.beforeActiveRead("b",other.ticket.generation)!!
        assertNotSame(replacement,other.ticket);assertFalse(other.life.authorizeHit("a"));assertNull(other.slot.get())
    }
    @Test fun coreDocumentAndUnknownBeforeOrInsidePublicationRevokeOriginalSlot() {
        for(document in listOf(false,true))for(inside in listOf(false,true))for(unknown in listOf(false,true)) {
            val h=H()
            if(inside)h.during={h.writer(document,unknown)} else h.writer(document,unknown)
            assertFalse(h.life.authorizeHit("a"));assertNull(h.slot.get());assertFalse(h.ticket.isLive())
            assertEquals(1L,h.port.mutationEpoch);assertEquals(1,h.reads)
        }
    }
    @Test fun lockDuringPublishAndWorkerRevokeAfterCallbackCannotAuthorizeHit() {
        for(worker in listOf(false,true)) {
            val h=H()
            if(worker)h.after={val t=Thread{h.session.lock()};t.start();t.join(3000);assertFalse(t.isAlive)}
            else h.during={h.session.lock()}
            assertFalse(h.life.authorizeHit("a"));assertNull(h.slot.get())
            assertEquals(SessionState.LOCKED,h.session.snapshot().state)
            h.session.authenticate(Experiment.PER_OPERATION)
            assertFalse(h.life.authorizeHit("a"))
        }
    }
    @Test fun repeatedHitsDoNotRenewThirtySecondIdleAndWorkerTimeoutIsSynchronous() {
        val h=H()
        h.now=29_999;assertTrue(h.life.authorizeHit("a"))
        val failure=AtomicReference<Throwable?>()
        h.now=30_000
        val thread=Thread{try{assertFalse(h.contexts.first().check(AccessPoint.STEP))}catch(t:Throwable){failure.set(t)}}
        thread.start();thread.join(3000);assertFalse(thread.isAlive);assertNull(failure.get())
        assertNull(h.slot.get());assertFalse(h.life.authorizeHit("a"))
        assertEquals(SessionState.LOCKED,h.session.snapshot().state);assertEquals(0L,h.port.mutationEpoch)
    }
    @Test fun successfulHitFollowedByRevokeCloseOrNewLoadCannotRestoreSlot() {
        for(mode in 0..3) {
            val h=H();assertTrue(h.life.authorizeHit("a"))
            when(mode){0->h.session.lock();1->h.life.close();2->h.session.storageFailure()
                3->h.loader.load("b") {}}
            assertNull(h.slot.get());assertFalse(h.life.authorizeHit("a"))
        }
    }
    @Test fun wrongThreadDoesNotAcquireOrMutateOwnerAndDefaultLifetimeDenies() {
        val h=H();val acquisitions=h.acquisitions;val failed=AtomicReference<Throwable?>()
        val t=Thread{try{assertFalse(h.life.authorizeHit("a"))}catch(e:Throwable){failed.set(e)}}
        t.start();t.join(3000);assertFalse(t.isAlive);assertNull(failed.get())
        assertEquals(acquisitions,h.acquisitions);assertEquals(0L,h.port.mutationEpoch)
        assertTrue(h.life.authorizeHit("a"))
        val unprepared=object:ActiveCoreLifetime {
            override fun isCurrent()=true
            override fun publish(install:()->Unit):Boolean {error("must not fall back")}
            override fun onInvalidated(invalidate:()->Unit)=Subscription{}
            override fun close(){}
        }
        assertFalse(unprepared.authorizeHit("a"))
    }
    @Test fun namespaceFenceBeforeOrAfterPublicationDeniesWithoutWaitingOnIo() {
        for(after in listOf(false,true)) {
            val h=H()
            if(after)h.after={DocumentNamespaceLane.drainAndFence{}}
            else DocumentNamespaceLane.drainAndFence{}
            assertFalse(h.life.authorizeHit("a"));assertNull(h.slot.get())
            assertEquals(1,h.reads);assertEquals(0L,h.port.mutationEpoch)
        }
    }
}
