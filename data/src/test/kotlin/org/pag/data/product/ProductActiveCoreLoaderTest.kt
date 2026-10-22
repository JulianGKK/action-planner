package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ProductActiveCoreLoaderTest {
    @Test fun ownedOriginalLifetimeSynchronouslyDropsSlotBeforeCoreOrDocumentQueue() {
        for(document in listOf(false,true)) {
            val h=Harness();val w=QueueExecutor();val m=QueueExecutor()
            val slot=AtomicReference<ActiveCoreResult?>();var lifetime:ActiveCoreLifetime?=null
            val loader=h.loader(w,m){_,t,_->value(t)}
            assertFalse(loader.reuseEnabled)
            assertTrue(loader.loadOwned("action") { result,life ->
                lifetime=life;life!!.onInvalidated {slot.set(null)}
                assertTrue(life.publish {slot.set(result)})
            })
            w.take().run();m.take().run();assertNotNull(slot.get())
            val generation=h.session.snapshot().generation
            val pending=if(document)h.mutations.beforeDocumentQueue("action",generation)
                else h.mutations.beforeTaskQueue("action",generation)
            assertNotNull(pending);assertNull(slot.get());assertFalse(lifetime!!.isCurrent())
            assertEquals(1L,h.mutations.mutationEpoch)
        }
    }
    @Test fun ownedLifetimeDropsOnRealWorkerExpiryAndDoesNotReviveAfterReauthentication() {
        val h=Harness();val w=QueueExecutor();val m=QueueExecutor();val slot=AtomicReference<ActiveCoreResult?>()
        var lifetime:ActiveCoreLifetime?=null
        h.loader(w,m){_,t,_->value(t)}.loadOwned("action") { result,life ->
            lifetime=life;life!!.onInvalidated{slot.set(null)};assertTrue(life.publish{slot.set(result)})
        }
        w.take().run();m.take().run();assertNotNull(slot.get())
        val context=h.session.acquire(AccessScope.READY)!!;h.now=30_000
        val failure=AtomicReference<Throwable?>()
        val thread=Thread {try{context.check(AccessPoint.STEP)}catch(error:Throwable){failure.set(error)}}
        thread.start();thread.join(3000)
        assertNull(failure.get());assertNull(slot.get());assertEquals(SessionState.LOCKED,h.session.snapshot().state)
        h.session.authenticate(Experiment.PER_OPERATION)
        assertFalse(lifetime!!.isCurrent());assertEquals(0L,h.mutations.mutationEpoch)
    }
    @Test fun retentionPublishCallbackThenFalseOrRevokeNeverAuthorizesSlot() {
        for(revoke in listOf(false,true)) {
            val port=UncachedWorkspaceMutations();val ticket=port.beforeActiveRead("action",1)!!
            val slot=AtomicReference<Any?>()
            val context=object:StorageContext {
                override val scope=AccessScope.READY;override val generation=1L
                override fun check(point:AccessPoint)=true
                override fun publish(result:()->Unit):Boolean {result();if(revoke)port.revokeActiveRead();return revoke}
            }
            val life=ProductActiveCoreLifetime(object:StorageAccess {override fun acquire(scope:AccessScope)=context},context,port,ticket,DocumentNamespaceLane.ticket(),Thread.currentThread(),loadCurrent={true})
            life.onInvalidated{slot.set(null)}
            assertFalse(life.publish{slot.set(Any())})
            assertNull(slot.get());assertFalse(life.isCurrent())
        }
    }
    @Test fun budgetRefusalRetiresOriginalTicketWithoutEpochChangeOrRetry() {
        val h=Harness();val w=QueueExecutor();val m=QueueExecutor()
        var original:WorkspaceMutationInvalidation.ActiveReadTicket?=null
        var reads=0;var deliveries=0
        val epoch=h.mutations.mutationEpoch
        val loader=h.loader(w,m){_,ticket,_->
            original=ticket;reads++;ActiveCoreResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED)
        }
        assertTrue(loader.loadOwned("action"){result,lifetime->
            deliveries++;assertNull(lifetime)
            assertEquals(ActiveCoreResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED),result)
            assertFalse(original!!.isLive())
        })
        w.take().run();assertTrue(original!!.isLive());m.take().run()
        assertFalse(original!!.isLive());assertEquals(1,reads);assertEquals(1,deliveries)
        assertEquals(epoch,h.mutations.mutationEpoch);assertTrue(w.queue.isEmpty());assertTrue(m.queue.isEmpty())
        val next=h.mutations.beforeActiveRead("action",h.session.snapshot().generation)!!
        h.mutations.revokeActiveRead(original!!);assertTrue(next.isLive())
    }
    private class QueueExecutor : java.util.concurrent.Executor {
        val queue=mutableListOf<Runnable>();var reject=false
        override fun execute(command: Runnable) { if(reject) throw java.util.concurrent.RejectedExecutionException();queue.add(command) }
        fun take():Runnable=queue.removeAt(0)
    }
    private class Harness {
        var now=0L
        val auth=object:AuthenticationExperiment {
            override fun start(mode:KeyExperiment,complete:(AuthenticationOutcome)->Unit):Cancellation {
                complete(AuthenticationOutcome.Granted(object:SyntheticOperation {
                    override fun perform()=OperationResult.SUCCEEDED
                    override fun release(){}
                },now));return Cancellation{}
            }
        }
        val session=SessionCoordinator(auth,Maintenance { done->done(true);Subscription{} },
            MonotonicTime { now },ProductSessionTiming.policy,{true})
        val mutations=UncachedWorkspaceMutations()
        init {
            session.foreground(true);session.authenticate(Experiment.PER_OPERATION)
            session.observe { if(it.state==SessionState.LOCKED || it.state==SessionState.BLOCKED) mutations.revokeActiveRead() }
        }
        fun loader(worker:QueueExecutor,main:QueueExecutor,read:(StorageContext,WorkspaceMutationInvalidation.ActiveReadTicket,Long)->ActiveCoreResult)=
            ProductActiveCoreLoader(session,mutations,worker,main,read)
    }
    private fun value(ticket:WorkspaceMutationInvalidation.ActiveReadTicket,status:ActionStatus=ActionStatus.ACTIVE):ActiveCoreResult {
        val state=CompactTaskState(P3bFreeActiveCoreBinding(ticket.actionId,ticket.generation),ticket.mutationEpoch)
        val people=ActiveCorePeoplePage(state,CompactPeopleCounts(0,0,0,0,0,0,0,0),0,emptyList(),false)
        val functions=CompactFunctions(emptyList(),0,0,0)
        val counts=CompactTaskCounts(0,0,0,0,0)
        val tasks=ActiveCoreTaskPage(state,counts,counts,emptyList(),false)
        return ActiveCoreResult.Value(ActiveCorePayload(state,Action(ticket.actionId,"Synthetic","Synthetic",0,0,0,status),people,functions,tasks),
            ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units()),0,0)
    }

    @Test fun readyTicketUsesTypedActionGenerationAndCommonEpochThenPublishesOnceOnOwner() {
        val h=Harness();val worker=QueueExecutor();val main=QueueExecutor();val calls=AtomicInteger()
        val seen=AtomicReference<WorkspaceMutationInvalidation.ActiveReadTicket?>()
        val loader=h.loader(worker,main){_,ticket,_->seen.set(ticket);value(ticket)}
        assertTrue(loader.load("action") { result ->
            calls.incrementAndGet();assertTrue(result is ActiveCoreResult.Value)
            assertTrue(h.mutations.isCurrentActiveRead(seen.get()!!))
        })
        assertEquals(0,h.mutations.mutationEpoch);assertEquals(1,worker.queue.size)
        val runnable=worker.take();runnable.run();assertEquals(1,main.queue.size)
        val mainRun=main.take();mainRun.run();mainRun.run()
        assertEquals(1,calls.get());assertEquals("action",seen.get()!!.actionId)
        assertEquals(h.session.snapshot().generation,seen.get()!!.generation)
        assertEquals(0L,seen.get()!!.mutationEpoch)
        // Transient load closes its lifetime after callback; completed reads must not stay live.
        assertFalse(seen.get()!!.isLive());assertFalse(h.mutations.isCurrentActiveRead(seen.get()!!))
        assertFalse(h.mutations.ramReuseEnabled)
    }

    @Test fun noReadyOrCoreOrDocumentPendingOrUnknownMeansNoWorker() {
        run {
            val h=Harness();h.session.lock();val w=QueueExecutor();val m=QueueExecutor()
            assertFalse(h.loader(w,m){_,t,_->value(t)}.load("action"){});assertTrue(w.queue.isEmpty())
        }
        for(document in listOf(false,true)) {
            val h=Harness();val generation=h.session.snapshot().generation
            val pending=if(document)h.mutations.beforeDocumentQueue("action",generation)!!
                else h.mutations.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),generation)!!
            val w=QueueExecutor();val m=QueueExecutor()
            assertFalse(h.loader(w,m){_,t,_->value(t)}.load("action"){});assertTrue(w.queue.isEmpty())
            pending.finish(if(document) WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)
                else WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE))
        }
        val h=Harness();val g=h.session.snapshot().generation
        val pending=h.mutations.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),g)!!
        pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE))
        val w=QueueExecutor();val m=QueueExecutor()
        assertFalse(h.loader(w,m){_,t,_->value(t)}.load("action"){});assertTrue(w.queue.isEmpty())

        val h2=Harness();val w2=QueueExecutor();val m2=QueueExecutor()
        val unprepared=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long):WorkspaceMutationInvalidation.Pending?=null
        }
        assertFalse(ProductActiveCoreLoader(h2.session,unprepared,w2,m2){_,t,_->value(t)}.load("action"){})
        assertTrue(w2.queue.isEmpty())
    }

    @Test fun writerStartingDuringReaderWorkInvalidatesOriginalTicketBeforeMain() {
        val h=Harness();val worker=QueueExecutor();val main=QueueExecutor()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val calls=AtomicInteger()
        val seen=AtomicReference<WorkspaceMutationInvalidation.ActiveReadTicket?>()
        val loader=h.loader(worker,main){_,ticket,_->seen.set(ticket);entered.countDown();check(release.await(3,TimeUnit.SECONDS));value(ticket)}
        assertTrue(loader.load("action"){calls.incrementAndGet()})
        val read=worker.take();val thread=Thread(read);thread.start();assertTrue(entered.await(3,TimeUnit.SECONDS))
        val write=h.mutations.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,"action"),h.session.snapshot().generation)!!
        assertFalse(seen.get()!!.isLive());assertEquals(1L,h.mutations.mutationEpoch)
        write.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE))
        release.countDown();thread.join(3000);assertFalse(thread.isAlive)
        assertTrue(main.queue.isEmpty());assertEquals(0,calls.get())
    }

    @Test fun writerAfterDbCleanupBeforeMainBlocksPublicationAndWrongCoreBindingIsRejected() {
        val h=Harness();val worker=QueueExecutor();val main=QueueExecutor();val calls=AtomicInteger()
        val loader=h.loader(worker,main){_,ticket,_->value(ticket)}
        assertTrue(loader.load("action"){calls.incrementAndGet()});worker.take().run()
        val pending=h.mutations.beforeDocumentQueue("action",h.session.snapshot().generation)!!
        pending.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE))
        main.take().run();assertEquals(0,calls.get())

        for(result in listOf(valueForMismatch("other"),valueForMismatch("action",ActionStatus.COMPLETED),valueForMismatch("action",ActionStatus.ACTIVE,99L))) {
            val h2=Harness();val w2=QueueExecutor();val m2=QueueExecutor();val c2=AtomicInteger()
            assertTrue(h2.loader(w2,m2){_,_,_->result}.load("action"){c2.incrementAndGet()})
            w2.take().run();m2.take().run();assertEquals(0,c2.get())
        }
    }

    private fun valueForMismatch(actionId:String,status:ActionStatus=ActionStatus.ACTIVE,generation:Long=1L):ActiveCoreResult {
        val fake=object:WorkspaceMutationInvalidation.ActiveReadTicket {
            override val actionId=actionId;override val generation=generation;override val mutationEpoch=0L
            override fun isLive()=true
        }
        return value(fake,status)
    }

    @Test fun workerSessionExpiryRevokesTicketAndFalseAfterCentralCallbackPublishesNothing() {
        val h=Harness();val worker=QueueExecutor();val main=QueueExecutor();val calls=AtomicInteger()
        val loader=h.loader(worker,main){_,ticket,_->assertTrue(ticket.isLive());value(ticket)}
        assertTrue(loader.load("action"){calls.incrementAndGet()})
        val read=worker.take();h.now=30_000L
        val thread=Thread(read);thread.start();thread.join(3000);assertFalse(thread.isAlive)
        assertEquals(SessionState.LOCKED,h.session.snapshot().state);assertFalse(h.mutations.ramReuseEnabled)
        assertTrue(main.queue.isEmpty());assertEquals(0,calls.get())

        val port=UncachedWorkspaceMutations();var returnedTicket:WorkspaceMutationInvalidation.ActiveReadTicket?=null
        val context=object:StorageContext {
            override val generation=7L;override val scope=AccessScope.READY
            override fun check(point:AccessPoint)=true
            override fun publish(result:()->Unit):Boolean { result();return false }
        }
        val access=object:StorageAccess { override fun acquire(scope:AccessScope)=context }
        val w=QueueExecutor();val m=QueueExecutor();val received=AtomicInteger()
        val falsePublish=ProductActiveCoreLoader(access,port,w,m){_,ticket,_->returnedTicket=ticket;value(ticket)}
        assertTrue(falsePublish.load("action"){received.incrementAndGet()});w.take().run();m.take().run()
        assertEquals(0,received.get());assertFalse(returnedTicket!!.isLive())
    }

    @Test fun lockOrBlockedBeforeMainCannotPublishAndBudgetOrCleanupRejectionIsWholeResult() {
        for(blocked in listOf(false,true)) {
            val h=Harness();val worker=QueueExecutor();val main=QueueExecutor();val calls=AtomicInteger()
            assertTrue(h.loader(worker,main){_,t,_->value(t)}.load("action"){calls.incrementAndGet()})
            worker.take().run()
            if(blocked) h.session.storageFailure() else h.session.lock()
            main.take().run();assertEquals(0,calls.get())
        }
        for(reason in listOf(BoundedActiveStatus.BUDGET_REFUSED,BoundedActiveStatus.STORAGE_FAILURE)) {
            val h=Harness();val worker=QueueExecutor();val main=QueueExecutor();val received=AtomicReference<ActiveCoreResult?>()
            assertTrue(h.loader(worker,main){_,_,_->ActiveCoreResult.Rejected(reason)}.load("action"){received.set(it)})
            worker.take().run();main.take().run()
            assertEquals(ActiveCoreResult.Rejected(reason),received.get())
        }
    }

    @Test fun lostMainAndNewReadyGenerationNeverRestoreTheOldTicket() {
        val h=Harness();val worker=QueueExecutor();val main=QueueExecutor();val calls=AtomicInteger()
        assertTrue(h.loader(worker,main){_,t,_->value(t)}.load("action"){calls.incrementAndGet()})
        worker.take().run();assertEquals(1,main.queue.size)
        val oldGeneration=h.session.snapshot().generation
        h.session.lock();h.session.authenticate(Experiment.PER_OPERATION)
        val newGeneration=h.session.snapshot().generation
        assertTrue(newGeneration>oldGeneration)
        main.take().run();assertEquals(0,calls.get())
        assertFalse(h.mutations.isCurrentActiveRead(object:WorkspaceMutationInvalidation.ActiveReadTicket {
            override val actionId="action";override val generation=oldGeneration;override val mutationEpoch=0L
            override fun isLive()=false
        }))
    }

    @Test fun lostMainRunnableNeverPublishesOrCreatesRamReuse() {
        val h=Harness();val worker=QueueExecutor();val main=QueueExecutor();val calls=AtomicInteger()
        assertTrue(h.loader(worker,main){_,t,_->value(t)}.load("action"){calls.incrementAndGet()})
        worker.take().run()
        assertEquals(1,main.queue.size)
        // Deliberately lose the queued Main Runnable: the loader has no payload slot/registry.
        assertEquals(0,calls.get());assertFalse(h.mutations.ramReuseEnabled)
    }

    @Test fun executorRejectionAndDuplicateWorkerDoNotStartSecondRead() {
        val h=Harness();val worker=QueueExecutor();worker.reject=true;val main=QueueExecutor();var reads=0
        assertFalse(h.loader(worker,main){_,t,_->reads++;value(t)}.load("action"){});assertEquals(0,reads)
        val h2=Harness();val w=QueueExecutor();val m=QueueExecutor()
        assertTrue(h2.loader(w,m){_,t,_->reads++;value(t)}.load("action"){})
        val run=w.take();run.run();run.run();assertEquals(1,reads)
    }

    @Test fun foreignMainRunnableIsDeniedAndMainExecutorRejectionRevokesTicket() {
        val h=Harness();val w=QueueExecutor();val m=QueueExecutor();val calls=AtomicInteger()
        assertTrue(h.loader(w,m){_,t,_->value(t)}.load("action"){calls.incrementAndGet()})
        w.take().run();val mainRun=m.take();val failure=AtomicReference<Throwable?>()
        val thread=Thread { try { mainRun.run() } catch(error:Throwable) { failure.set(error) } }
        thread.start();thread.join(3000)
        assertNull(failure.get());assertEquals(0,calls.get())

        val h3=Harness();val w3=QueueExecutor();val m3=QueueExecutor();val t3=AtomicReference<WorkspaceMutationInvalidation.ActiveReadTicket?>()
        assertTrue(h3.loader(w3,m3){_,t,_->t3.set(t);value(t)}.load("action"){})
        m3.reject=true;w3.take().run();assertFalse(t3.get()!!.isLive())
    }
}
