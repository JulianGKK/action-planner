package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real loader/handoffs/lifetime, synthetic storage producer. Explicit test aliases are NOT
 * product owners. No GC, WeakReference, Compose, native lifetime or physical wipe inference. */
class P97CoreReferenceLifetimeTest {
    private fun field(o:Any,name:String):Any?=o.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(o)}
    private fun emptyCell(read:Any,name:String)=assertNull((field(read,name) as AtomicReference<*>).get())
    private fun cellsEmpty(read:Any){for(name in listOf("payload","workerWork","mainWork"))emptyCell(read,name)}
    private class H {
        val worker=ArrayDeque<Runnable>();val main=ArrayDeque<Runnable>()
        val mutations=UncachedWorkspaceMutations()
        @Volatile var ready=true
        var generation=1L;var reads=0;var callbacks=0
        var producerPause:((ActiveCoreResult.Value)->Unit)?=null
        var onDelivery:((ActiveCoreResult.Value,ActiveCoreLifetime)->Unit)?=null
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
        val loader=ProductActiveCoreLoader(access,mutations,{worker.add(it)},{main.add(it)}){_,ticket,_,memory->
            reads++
            val units=ActiveWorkspaceBudget.Units(objects=100)
            val reservation=ActiveCoreReservation(units,units)
            memory.admit(reservation,12800,12800)
            val state=CompactTaskState(P3bFreeActiveCoreBinding(ticket.actionId,ticket.generation),ticket.mutationEpoch)
            val counts=CompactTaskCounts(0,0,0,0,0)
            val result=ActiveCoreResult.Value(ActiveCorePayload(state,Action(ticket.actionId,"Synthetic","Synthetic",0,0,0,ActionStatus.ACTIVE),
                ActiveCorePeoplePage(state,CompactPeopleCounts(0,0,0,0,0,0,0,0),0,emptyList(),false),
                CompactFunctions(emptyList(),0,0,0),ActiveCoreTaskPage(state,counts,counts,emptyList(),false)),reservation,12800,12800)
            producerPause?.invoke(result) // result remains live in this worker-local scope.
            result
        }
        fun load()=loader.loadOwned("a"){value,life->
            callbacks++
            if(value is ActiveCoreResult.Value)onDelivery?.invoke(value,checkNotNull(life))
        }
    }
    private fun read(h:H):Any=checkNotNull(field(field(h.loader,"outstanding")!!,"current"))
    private fun life(h:H)=field(h.loader,"lifetime") as ActiveCoreLifetime
    private fun ack(read:Any,name:String)=assertEquals(true,field(read,name))
    private fun forbidden(h:H){val n=h.reads;assertFalse(h.load());assertEquals(n,h.reads);assertEquals(16777216L,h.loader.outstandingState().transition)}

    @Test fun cancelledBeforeMaterializationNeedsActualWorkerShellAcknowledgement() {
        val h=H();assertTrue(h.load());val r=read(h);val shell=h.worker.remove()
        assertSame(r,field(shell,"read")) // Queued original reference must survive cancellation until run.
        h.loader.cancelPending();cellsEmpty(r);assertEquals(false,field(r,"workerEnded"))
        forbidden(h);assertSame(r,field(shell,"read"));shell.run();assertNull(field(shell,"read"));ack(r,"workerEnded");assertTrue(h.loader.outstandingState().retirementConfirmed)
        assertEquals(0,h.reads);assertEquals(0,h.callbacks);shell.run();cellsEmpty(r)
        println("P97 before-materialization CONFIRMED only-after-worker-ack; original-payload-created=false")
    }
    @Test fun materializedCandidateHasOneCellAndCannotEndBeforeMainAcknowledgement() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val r=read(h)
        ack(r,"workerEnded");emptyCell(r,"workerWork")
        assertTrue((field(r,"payload") as AtomicReference<*>).get() is ActiveCoreResult.Value)
        assertNotNull((field(r,"mainWork") as AtomicReference<*>).get())
        assertEquals(false,field(r,"mainAcknowledged"));assertEquals(0,h.callbacks);forbidden(h)
        h.loader.cancelPending();cellsEmpty(r);forbidden(h)
        h.main.remove().run();ack(r,"mainAcknowledged");assertTrue(h.loader.outstandingState().retirementConfirmed)
        println("P97 candidate BLOCKED while-main-pending; cleared-cells-not-enough; CONFIRMED after-discard-ack")
    }
    @Test fun discardedMainAcknowledgesOnlyItsOwnReadAndLateDuplicateCannotRetireNewRead() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val original=read(h);val old=h.main.remove()
        assertSame(original,field(old,"read"));assertEquals(false,field(original,"mainAcknowledged"))
        h.loader.cancelPending();forbidden(h);assertSame(original,field(old,"read"))
        old.run();assertNull(field(old,"read"));ack(original,"mainAcknowledged");cellsEmpty(original)
        assertTrue(h.loader.outstandingState().retirementConfirmed)
        h.generation=2;assertTrue(h.load());val next=read(h);assertNotSame(original,next)
        h.worker.remove().run();old.run();old.run();assertSame(next,read(h));assertEquals(0,h.callbacks)
        h.main.remove().run();assertEquals(1,h.callbacks);assertFalse(h.loader.outstandingState().retirementConfirmed)
        println("P97 discarded-main CONFIRMED original identity only; duplicate-old-shell cannot retire new-read")
    }
    @Test fun cancelledMaterializedWorkerLocalBlocksEvenWithAllCellsEmpty() {
        val h=H();val entered=CountDownLatch(1);val release=CountDownLatch(1);val failure=AtomicReference<Throwable?>()
        h.producerPause={result->entered.countDown();check(release.await(5,TimeUnit.SECONDS));assertEquals("a",result.core.action.id)}
        assertTrue(h.load());val r=read(h);val shell=h.worker.remove()
        val thread=Thread{try{shell.run()}catch(t:Throwable){failure.set(t)}};thread.start()
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertNull(field(shell,"read"))
            h.mutations.revokeActiveRead();cellsEmpty(r)
            assertEquals(false,field(r,"workerEnded"));forbidden(h)
        } finally {release.countDown();thread.join(5000)}
        assertFalse(thread.isAlive);assertNull(failure.get());assertNull(field(shell,"read"));ack(r,"workerEnded");cellsEmpty(r)
        assertTrue(h.loader.outstandingState().retirementConfirmed);assertEquals(0,h.callbacks)
        println("P97 worker-local BLOCKED until-producer-return-and-worker-ack; then CONFIRMED named-chain")
    }
    @Test fun publishedMainResultLocalOutlivesRevocationAndExposedReadStaysBlockedAfterReturn() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val r=read(h)
        val shell=h.main.remove();assertSame(r,field(shell,"read"))
        h.onDelivery={result,life->
            assertNull(field(shell,"read"))
            assertEquals("a",result.core.action.id);emptyCell(r,"payload");emptyCell(r,"mainWork")
            assertEquals(true,field(r,"delivering"));assertEquals(false,field(r,"mainAcknowledged"))
            life.close();cellsEmpty(r);forbidden(h)
            assertEquals("a",result.core.action.id) // Scoped test callback parameter, not VM/registry.
        }
        shell.run();assertNull(field(shell,"read"));ack(r,"workerEnded");ack(r,"mainAcknowledged");ack(r,"deliveryEnded");cellsEmpty(r)
        assertEquals(false,field(r,"delivering"));assertEquals(true,field(r,"exposed"))
        assertFalse(h.loader.outstandingState().retirementConfirmed);forbidden(h)
        assertNull((field(life(h),"listener") as AtomicReference<*>).get())
        assertNull((field(life(h),"registration") as AtomicReference<*>).get())
        println("P97 main-local BLOCKED during-callback; CONFIRMED cell/scoped-return/registration ends; exposure retirement BLOCKED")
    }
    @Test fun explicitLegitimateTestAliasSurvivesEmptyCellsClosedLifetimeAndAcknowledgedHandoffs() {
        val h=H();var testReference:ActiveCoreResult.Value?=null
        h.onDelivery={result,life->testReference=result;life.close()}
        assertTrue(h.load());h.worker.remove().run();val r=read(h);h.main.remove().run();cellsEmpty(r)
        ack(r,"workerEnded");ack(r,"mainAcknowledged");assertEquals("a",testReference!!.core.action.id)
        forbidden(h);testReference=null;h.onDelivery=null
        assertFalse(h.loader.outstandingState().retirementConfirmed)
        println("P97 explicit-test-reference BLOCKED while-test-alias-held; dropping-it is no production-end-receipt")
    }
    /** Bounded test-only inspection, not a payload registry/production root or GC observation. */
    private fun reaches(root:Any,target:Any):Boolean {
        val queue=ArrayDeque<Any>();val seen=IdentityHashMap<Any,Boolean>();queue.add(root)
        while(queue.isNotEmpty() && seen.size<1000) {
            val value=queue.remove();if(value===target)return true
            if(seen.put(value,true)!=null)continue
            if(value is AtomicReference<*>) {value.get()?.let{queue.add(it)};continue}
            if(!value.javaClass.name.startsWith("org.pag."))continue
            for(f in value.javaClass.declaredFields)if(!java.lang.reflect.Modifier.isStatic(f.modifiers) && !f.type.isPrimitive) {
                f.isAccessible=true;f.get(value)?.let{queue.add(it)}
            }
        }
        return false
    }
    @Test fun retiredShellIsDetachedButExplicitOldReadAliasStillReachesFutureCandidate() {
        val h=H();assertTrue(h.load());val oldWorker=h.worker.remove();oldWorker.run();val oldShell=h.main.remove()
        val testOldRead=read(h) // Explicit TEST alias, distinct from the terminal shell.
        h.loader.cancelPending();oldShell.run()
        assertNull(field(oldWorker,"read"));assertNull(field(oldShell,"read"))
        assertTrue(h.loader.outstandingState().retirementConfirmed)
        assertTrue(h.load());h.worker.remove().run();val r=read(h)
        val testReference=(field(r,"payload") as AtomicReference<*>).get()!! // Explicit TEST alias for comparison.
        assertFalse(reaches(oldShell,testReference));assertFalse(reaches(oldWorker,testReference))
        assertTrue(reaches(testOldRead,testReference)) // Unchanged old Read.this$0 -> outstanding.current -> B.
        val before=h.loader.outstandingState()
        oldShell.run();oldWorker.run();oldShell.run();assertEquals(0,h.callbacks);assertSame(r,read(h))
        assertEquals(before,h.loader.outstandingState())
        h.loader.cancelPending();h.main.remove().run();cellsEmpty(r)
        println("P99 terminal worker/main shells detached: no path to B; explicit TEST old-Read alias still reaches B via owner; broader isolation BLOCKED")
    }
    @Test fun p99ParallelWorkerRunsTakeExactlyOneOriginalAndKeepActualEndFence() {
        val h=H();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val failure=AtomicReference<Throwable?>();h.producerPause={
            entered.countDown();check(release.await(5,TimeUnit.SECONDS));assertEquals("a",it.core.action.id)
        }
        assertTrue(h.load());val original=read(h);val shell=h.worker.remove()
        assertSame(original,field(shell,"read"))
        val start=CountDownLatch(1)
        val runners=List(8){Thread{try{check(start.await(5,TimeUnit.SECONDS));shell.run()}catch(t:Throwable){failure.compareAndSet(null,t)}}}
        runners.forEach{it.start()};start.countDown()
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertNull(field(shell,"read"))
            assertEquals(false,field(original,"workerEnded"));assertEquals(1,h.reads)
            h.loader.cancelPending();forbidden(h)
        } finally {release.countDown();runners.forEach{it.join(5000)}}
        assertTrue(runners.none{it.isAlive});assertNull(failure.get());assertNull(field(shell,"read"))
        ack(original,"workerEnded");assertTrue(h.loader.outstandingState().retirementConfirmed)
        assertEquals(1,h.reads);assertEquals(0,h.callbacks);assertTrue(h.main.isEmpty())
        assertTrue(h.load());val next=read(h);val before=h.loader.outstandingState()
        repeat(3){shell.run()};assertSame(next,read(h));assertEquals(before,h.loader.outstandingState())
        h.loader.cancelPending();h.worker.remove().run()
        println("P99 worker parallel take: one producer; shell detached while active; cancellation waits for actual worker ACK; old duplicate cannot affect B")
    }
    @Test fun p99MainDuplicatesDuringOwnerCallbackNeverAcknowledgeRunningDispatch() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val original=read(h);val shell=h.main.remove()
        assertSame(original,field(shell,"read"));val failure=AtomicReference<Throwable?>()
        h.onDelivery={result,life->
            assertNull(field(shell,"read"));assertEquals(false,field(original,"mainAcknowledged"))
            val start=CountDownLatch(1)
            val duplicates=List(8){Thread{try{check(start.await(5,TimeUnit.SECONDS));shell.run()}catch(t:Throwable){failure.compareAndSet(null,t)}}}
            duplicates.forEach{it.start()};start.countDown();duplicates.forEach{it.join(5000)}
            assertTrue(duplicates.none{it.isAlive});assertNull(failure.get())
            assertEquals(false,field(original,"mainAcknowledged"));assertEquals(true,field(original,"delivering"))
            life.close();forbidden(h);assertEquals("a",result.core.action.id)
        }
        shell.run();assertEquals(1,h.callbacks);ack(original,"mainAcknowledged");ack(original,"deliveryEnded")
        assertNull(field(shell,"read"));forbidden(h);shell.run();assertEquals(1,h.callbacks)
        println("P99 main duplicates during callback: shell empty, local original alive, no early ACK; exposed read still DENIED after actual return")
    }
    @Test fun p99ParallelMainFirstRunsTakeOnceAndCancellationStillNeedsActualAck() {
        val owner=OutstandingCoreRead();val original=checkNotNull(owner.begin());original.workerFinished()
        val calls=java.util.concurrent.atomic.AtomicInteger();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val shell=checkNotNull(original.queueMain(Runnable {
            calls.incrementAndGet();entered.countDown();check(release.await(5,TimeUnit.SECONDS))
        }))
        val failure=AtomicReference<Throwable?>();val start=CountDownLatch(1)
        val runners=List(8){Thread{try{check(start.await(5,TimeUnit.SECONDS));shell.run()}catch(t:Throwable){failure.compareAndSet(null,t)}}}
        assertSame(original,field(shell,"read"));runners.forEach{it.start()};start.countDown()
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertNull(field(shell,"read"))
            original.revoke();assertEquals(false,field(original,"mainAcknowledged"))
            assertNull(owner.begin());assertFalse(owner.state().retirementConfirmed)
        } finally {release.countDown();runners.forEach{it.join(5000)}}
        assertTrue(runners.none{it.isAlive});assertNull(failure.get());assertEquals(1,calls.get())
        ack(original,"mainAcknowledged");assertTrue(owner.state().retirementConfirmed)
        val next=checkNotNull(owner.begin());val before=owner.state();repeat(3){shell.run()}
        assertSame(next,field(owner,"current"));assertEquals(before,owner.state());assertEquals(1,calls.get())
        println("P99 parallel main first-take: one dispatch; cancel waits for actual main ACK; terminal shell inert for B")
    }
    @Test fun p99DispatchExceptionsStillRunOriginalFinallyWithoutShellRetention() {
        for(main in listOf(false,true)) {
            val owner=OutstandingCoreRead();val original=checkNotNull(owner.begin());var calls=0
            val work=Runnable{calls++;assertNull(owner.begin());throw IllegalStateException("SYNTHETIC_DISPATCH_FAILURE")}
            val shell=if(main) {
                original.workerFinished();checkNotNull(original.queueMain(work))
            } else checkNotNull(original.queueWorker(work))
            assertSame(original,field(shell,"read"))
            try{shell.run();fail("Expected synthetic dispatch exception")}catch(expected:IllegalStateException){assertEquals("SYNTHETIC_DISPATCH_FAILURE",expected.message)}
            assertNull(field(shell,"read"));ack(original,if(main)"mainAcknowledged" else "workerEnded")
            // ACK alone does not revoke or retire; original cancellation contract still applies.
            assertNull(owner.begin());original.revoke();assertTrue(owner.state().retirementConfirmed)
            val next=checkNotNull(owner.begin());val before=owner.state();repeat(3){shell.run()}
            assertEquals(1,calls);assertEquals(before,owner.state());assertSame(next,field(owner,"current"))
        }
        println("P99 direct real dispatch exceptions: original finally ACK preserved; no cancellation fabricated; duplicates inert for B")
    }
    @Test fun p99LoaderCallbackExceptionEndsMainButCannotRetireExposedOriginal() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val original=read(h);val shell=h.main.remove()
        h.onDelivery={_,_->throw IllegalStateException("SYNTHETIC_CALLBACK_FAILURE")}
        try{shell.run();fail("Expected synthetic callback exception")}catch(expected:IllegalStateException){assertEquals("SYNTHETIC_CALLBACK_FAILURE",expected.message)}
        assertNull(field(shell,"read"));ack(original,"mainAcknowledged");ack(original,"deliveryEnded")
        assertEquals(true,field(original,"exposed"));assertEquals(1,h.callbacks);forbidden(h)
        shell.run();assertEquals(1,h.callbacks)
        println("P99 loader callback exception: original main/end ACKs complete, shell detached, exposed admission remains DENIED")
    }
    @Test fun fullViewFenceDoesNotTurnExposedCoreOrUnintegratedEditorDataIntoAReleaseProof() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val r=read(h);h.main.remove().run()
        life(h).close();h.loader.fullViewStarting();cellsEmpty(r)
        ack(r,"workerEnded");ack(r,"mainAcknowledged");forbidden(h)
        assertTrue(h.loader.outstandingState().fullViewUnconfirmed)
        assertFalse(h.loader.outstandingState().retirementConfirmed)
        println("P97 fullview core-release BLOCKED; fullview/form/editor payload OUT_OF_SCOPE; fence unchanged")
    }
}
