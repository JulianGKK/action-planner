package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*

/** Real coordinator, original lifetime and native reader. Synthetic auth/maintenance/time;
 * atomic test slot is NOT the ProductViewModel or a Compose/Runtime E2E claim. */
class P73ActiveLifetimeTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.also{check(it.packageName=="org.pag.data.unlockprobe")}
    private fun main(work:()->Unit)=instrumentation.runOnMainSync(work)
    private class Queue:Executor {
        val work=ConcurrentLinkedQueue<Runnable>()
        override fun execute(command:Runnable){work.add(command)}
    }
    private inner class H(val fixture:P11SyntheticFixture) : AutoCloseable {
        val time=AtomicLong(0)
        val auth=object:AuthenticationExperiment {
            override fun start(mode:KeyExperiment,complete:(AuthenticationOutcome)->Unit):Cancellation {
                complete(AuthenticationOutcome.Granted(object:SyntheticOperation {
                    override fun perform()=OperationResult.SUCCEEDED
                    override fun release(){}
                },time.get()));return Cancellation{}
            }
        }
        val session=SessionCoordinator(auth,Maintenance{it(true);Subscription{}},MonotonicTime{time.get()},ProductSessionTiming.policy,{true})
        val mutations=UncachedWorkspaceMutations()
        val worker=Queue();val delivery=Queue()
        var observation:(ActiveCoreStore.Phase)->Unit={}
        val phases=mutableListOf<Pair<String,Long>>()
        val observer:Subscription
        val loader:ProductActiveCoreLoader
        val slot=AtomicReference<ActiveCoreResult?>()
        var lifetime:ActiveCoreLifetime?=null
        var callbacks=0
        var lastResult:ActiveCoreResult?=null
        init {
            session.foreground(true);session.authenticate(Experiment.PER_OPERATION)
            observer=session.observe{if(it.state in setOf(SessionState.LOCKED,SessionState.BLOCKED))mutations.revokeActiveRead()}
            loader=fixture.coreForSession(session,mutations,worker,delivery,{observation(it)},
                {p,n->synchronized(phases){phases.add(p.name to n)}},session::storageFailure)
        }
        fun load()=loader.loadOwned(fixture.activeId) { value,life ->
            callbacks++;lifetime=life;lastResult=value
            if(value is ActiveCoreResult.Value && life!=null) {
                life.onInvalidated {slot.set(null)}
                if(!life.publish{slot.set(value)})slot.set(null)
            }
        }
        fun deliver(){while(delivery.work.isNotEmpty())delivery.work.remove().run()}
        override fun close(){session.lock();observer.cancel();lifetime?.close();slot.set(null);delivery.work.clear();worker.work.clear()}
    }
    private fun worker(h:H) {
        val failure=AtomicReference<Throwable?>()
        val t=Thread {try{h.worker.work.remove().run()}catch(e:Throwable){failure.set(e)}}
        t.start();t.join(20000);assertFalse("worker did not return",t.isAlive);failure.get()?.let{throw AssertionError(it)}
    }
    private fun memory(label:String) {
        val memory=android.os.Debug.MemoryInfo().also(android.os.Debug::getMemoryInfo)
        instrumentation.sendStatus(0,android.os.Bundle().apply {
            putString("stream","P73_MEMORY,$label,${java.io.File("/proc/self/fd").list()!!.size},${android.os.Debug.getNativeHeapAllocatedSize()},${memory.totalPss},${android.os.Debug.getRuntimeStat("art.gc.gc-count")}\n")
        })
    }
    private fun fixture(test:(H)->Unit) {
        P11SyntheticFixture(app,personCountPerAction=200,taskCountPerAction=1000).use { f ->
            lateinit var h:H;main{h=H(f)}
            try{test(h)}finally{main{h.close()}}
        }
    }
    @Test fun realLifetimeRevokedSynchronouslyByWorkerIdleAndAbsoluteExpiry() {
        for(absolute in listOf(false,true))fixture { h ->
            main{assertTrue(h.load())};worker(h);main{h.deliver();assertNotNull(h.slot.get());assertTrue(h.lifetime!!.isCurrent())}
            if(absolute) for(now in 29000L..174000L step 29000L) main{h.time.set(now);h.session.userInteraction()}
            val context=h.session.acquire(AccessScope.READY)!!
            h.time.set(if(absolute)180000 else 30000)
            val failure=AtomicReference<Throwable?>()
            val t=Thread {try{assertFalse(context.check(AccessPoint.STEP));assertNull(h.slot.get())}catch(e:Throwable){failure.set(e)}}
            t.start();t.join(5000);assertFalse(t.isAlive);failure.get()?.let{throw AssertionError(it)}
            main{
                assertEquals(SessionState.LOCKED,h.session.snapshot().state)
                assertFalse(h.lifetime!!.isCurrent());h.session.authenticate(Experiment.PER_OPERATION)
                assertFalse(h.lifetime!!.isCurrent());assertNull(h.slot.get());assertEquals(0L,h.mutations.mutationEpoch)
            }
        }
    }
    @Test fun nativeRowloopRevokeOrWriterInvalidatesBeforeFinalPublication() {
        for(mode in listOf("lock","blocked","core","document"))fixture { h ->
            var seen=false
            main {
                h.observation={p->if(p==ActiveCoreStore.Phase.PEOPLE_ROW && !seen) {
                    seen=true
                    main { when(mode) {
                        "lock"->h.session.lock()
                        "blocked"->h.session.storageFailure()
                        "core"->assertNotNull(h.mutations.beforeTaskQueue(h.fixture.activeId,h.session.snapshot().generation))
                        else->assertNotNull(h.mutations.beforeDocumentQueue(h.fixture.activeId,h.session.snapshot().generation))
                    }}
                }}
                assertTrue(h.load())
            }
            worker(h);assertTrue("Row hook was not reached",seen)
            main{h.deliver();assertNull(h.slot.get());assertEquals(0,h.callbacks)}
        }
    }
    @Test fun writerRevokeAndLostMainAfterNativeCleanupNeverRestoreOldRead() {
        for(mode in listOf("core","document","lost","lock","foreground"))fixture { h ->
            main{assertTrue(h.load())};worker(h);assertEquals(1,h.delivery.work.size)
            main {
                when(mode) {
                    "core"->assertNotNull(h.mutations.beforeTaskQueue(h.fixture.activeId,h.session.snapshot().generation))
                    "document"->assertNotNull(h.mutations.beforeDocumentQueue(h.fixture.activeId,h.session.snapshot().generation))
                    "lock"->h.session.lock()
                    "foreground"->h.session.foreground(false)
                    else->h.delivery.work.clear()
                }
                h.deliver();assertNull(h.slot.get());assertEquals(0,h.callbacks)
            }
        }
    }
    @Test fun completeReadPhasesAndUnknownAdmissionAreSeparateFromRamReuse()=fixture { h ->
        main{assertTrue(h.load())};worker(h);memory("native_candidate_waiting_main")
        main{h.deliver();assertNotNull(h.slot.get())}
        memory("atomic_test_slot_loaded")
        main{
            assertFalse(h.loader.reuseEnabled);assertFalse(h.mutations.ramReuseEnabled)
            for((phase,ns) in h.phases)instrumentation.sendStatus(0,android.os.Bundle().apply {
                putString("stream","P73_PHASE,$phase,$ns\n")
            })
            val pending=h.mutations.beforeDocumentQueue(h.fixture.activeId,h.session.snapshot().generation)!!
            assertNull(h.slot.get())
            assertTrue(pending.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)))
            assertFalse(h.load());assertTrue(h.worker.work.isEmpty())
        }
        memory("atomic_test_slot_revoked")
    }
    @Test fun budgetInvalidDataAndInjectedSqlExceptionNeverPublishPartialCore() {
        for(mode in listOf("budget","invalid","sql")) {
            P11SyntheticFixture(app).use { f ->
                if(mode!="sql")f.nativeDriverProbe { driver ->
                    driver.open(f.databasePathForProbe()).use { connection ->
                        val sql=if(mode=="budget")"UPDATE tasks SET title=?" else "UPDATE actions SET title=?"
                        connection.prepare(sql).use { statement ->
                            statement.bindText(1,if(mode=="budget")"x".repeat(200000) else " ")
                            statement.step()
                        }
                    }
                }
                lateinit var h:H;main{h=H(f)}
                try {
                    main{
                        if(mode=="sql")h.observation={p->if(p==ActiveCoreStore.Phase.METADATA)throw android.database.sqlite.SQLiteException("SYNTHETIC_READ_FAULT")}
                        assertTrue(h.load())
                    }
                    worker(h)
                    main{
                        h.deliver();assertTrue("Partial core published in $mode",h.slot.get()==null)
                        if(mode=="budget")assertEquals(ActiveCoreResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED),h.lastResult)
                        else { assertEquals(SessionState.BLOCKED,h.session.snapshot().state);assertEquals(0,h.callbacks) }
                    }
                }finally{main{h.close()}}
            }
        }
    }
}
