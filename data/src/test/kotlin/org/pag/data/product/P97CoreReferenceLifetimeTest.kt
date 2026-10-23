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
        h.loader.cancelPending();cellsEmpty(r);assertEquals(false,field(r,"workerEnded"))
        forbidden(h);shell.run();ack(r,"workerEnded");assertTrue(h.loader.outstandingState().retirementConfirmed)
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
        h.loader.cancelPending();forbidden(h);old.run();ack(original,"mainAcknowledged");cellsEmpty(original)
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
            assertTrue(entered.await(5,TimeUnit.SECONDS));h.mutations.revokeActiveRead();cellsEmpty(r)
            assertEquals(false,field(r,"workerEnded"));forbidden(h)
        } finally {release.countDown();thread.join(5000)}
        assertFalse(thread.isAlive);assertNull(failure.get());ack(r,"workerEnded");cellsEmpty(r)
        assertTrue(h.loader.outstandingState().retirementConfirmed);assertEquals(0,h.callbacks)
        println("P97 worker-local BLOCKED until-producer-return-and-worker-ack; then CONFIRMED named-chain")
    }
    @Test fun publishedMainResultLocalOutlivesRevocationAndExposedReadStaysBlockedAfterReturn() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val r=read(h)
        h.onDelivery={result,life->
            assertEquals("a",result.core.action.id);emptyCell(r,"payload");emptyCell(r,"mainWork")
            assertEquals(true,field(r,"delivering"));assertEquals(false,field(r,"mainAcknowledged"))
            life.close();cellsEmpty(r);forbidden(h)
            assertEquals("a",result.core.action.id) // Scoped test callback parameter, not VM/registry.
        }
        h.main.remove().run();ack(r,"workerEnded");ack(r,"mainAcknowledged");ack(r,"deliveryEnded");cellsEmpty(r)
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
    @Test fun retiredShellCanReachFutureCandidateThroughReadOuterOwnerButCannotDeliverIt() {
        val h=H();assertTrue(h.load());h.worker.remove().run();val oldShell=h.main.remove()
        h.loader.cancelPending();oldShell.run()
        assertTrue(h.loader.outstandingState().retirementConfirmed)
        assertTrue(h.load());h.worker.remove().run();val r=read(h)
        val testReference=(field(r,"payload") as AtomicReference<*>).get()!! // Explicit TEST alias for comparison.
        assertTrue(reaches(oldShell,testReference)) // old Read.this$0 -> outstanding.current -> new payload.
        oldShell.run();assertEquals(0,h.callbacks);assertSame(r,read(h))
        h.loader.cancelPending();h.main.remove().run();cellsEmpty(r)
        println("P97 retired-shell original-chain CONFIRMED; future-root isolation BLOCKED Read->outer.current backlink; no authority bypass")
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
