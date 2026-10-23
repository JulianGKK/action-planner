package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.data.product.ProductRuntime
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Real guarded Runtime/Room/SQLCipher/keyowner/VM; synthetic credentials, no product gate. */
class P82RoomCleanupProbe: Instrumentation() {
    private var scenario="success"
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);scenario=arguments?.getString("scenario") ?: "success";start()}
    private fun field(target:Any,name:String):Any?=target.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(target)}
    private fun <T> main(work:()->T):T {
        val result=AtomicReference<T>();val error=AtomicReference<Throwable?>()
        runOnMainSync { try{result.set(work())}catch(t:Throwable){error.set(t)} }
        error.get()?.let{throw it};return result.get()
    }
    private fun waitFor(label:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end) {if(work())return;Thread.sleep(25)}
        error("Timed out: $label")
    }
    override fun onStart() {
        var activity:MainActivity?=null
        var diagnostics:()->String={"not initialized"}
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            check(context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity=a
            val runtime=field(a,"runtime") as ProductRuntime
            val harness=field(a,"p77Harness") as P77ProductRuntimeHarness
            val model=field(a,"model") as ProductViewModel
            diagnostics=harness.cleanupFaults::diagnostic
            waitFor("LOCKED and focus"){main{a.hasWindowFocus() && runtime.session.snapshot().state==SessionState.LOCKED}}
            main{model.unlock()}
            waitFor("actual READY"){runtime.session.snapshot().state==SessionState.READY}
            waitFor("actions"){main{model.actions.any{it.id=="p11-active"}}}
            val oldContext=checkNotNull(runtime.session.acquire(AccessScope.READY))
            val oldOwner=(field(runtime,"owner") as AtomicReference<*>).get() as org.pag.data.product.SessionDbKeyOwner
            val beforeFd=java.io.File("/proc/self/fd").list()?.size
            val beforeHeap=android.os.Debug.getNativeHeapAllocatedSize()
            val beforePss=android.os.Debug.MemoryInfo().also{android.os.Debug.getMemoryInfo(it)}.totalPss
            val started=SystemClock.elapsedRealtimeNanos()
            harness.cleanupFaults.arm(scenario,runtime.session::lock)
            val originalEpoch=main{harness.mutations.mutationEpoch}
            main{model.detail("p11-active")}
            if(scenario=="success") {
                waitFor("real materialized core"){if(harness.cleanupFaults.finished())error(harness.cleanupFaults.diagnostic());main{coreOwnerIdentity(model)!=null}}
                main {
                    val core=checkNotNull(coreOwnerIdentity(model))
                    check(model.compactHubPresentation?.value(0,0)==200L && model.compactHubPresentation?.value(2,0)==1000L)
                    model.activeArea(ActiveArea.PERSONS);check(coreOwnerIdentity(model)===core)
                    check(model.compactPersonsPresentation?.shown()==32 && model.compactPersonsPresentation?.hasMore()==true)
                    model.activeArea(ActiveArea.TASKS);check(coreOwnerIdentity(model)===core)
                    check(model.compactTasksPresentation?.shown()==32 && model.compactTasksPresentation?.hasMore()==true)
                    check(harness.observations.hitAccepted.get()==1)
                    model.lock()
                }
            } else {
                waitFor("failed read revokes original ticket"){harness.observations.tickets.get()==1 && !harness.observations.originalTicketLive()}
                // DB/key finally finishes before the reader can report failure or lock-result.
                waitFor("worker cleanup completion"){harness.cleanupFaults.finished()}
                main {
                    check(coreOwnerIdentity(model)==null)
                    val hits=harness.observations.hitAccepted.get()
                    model.activeArea(ActiveArea.PERSONS)
                    check(harness.observations.hitAccepted.get()==hits && coreOwnerIdentity(model)==null)
                }
            }
            val native=harness.cleanupFaults.nativeDiagnostic()
            val evidence=harness.cleanupFaults.verify()
            if(scenario in setOf("native_step","native_getter"))sendStatus(0,Bundle().apply{putString("stream","P82_NATIVE_FAILURE,$scenario,$native\n")})
            var newLease=false
            check(runCatching{oldOwner.withDbKey(oldContext){newLease=true}}.isFailure)
            check(!newLease)
            main {
                check(!harness.observations.originalTicketLive())
                check(harness.observations.tickets.get()==1 && harness.observations.reads.get()==1)
                check(harness.mutations.mutationEpoch==originalEpoch)
                check(!harness.mutations.ramReuseEnabled)
                check(coreOwnerIdentity(model)==null)
            }
            val afterFd=java.io.File("/proc/self/fd").list()?.size
            val elapsed=SystemClock.elapsedRealtimeNanos()-started
            val afterHeap=android.os.Debug.getNativeHeapAllocatedSize()
            val afterPss=android.os.Debug.MemoryInfo().also{android.os.Debug.getMemoryInfo(it)}.totalPss
            val gc=android.os.Debug.getRuntimeStat("art.gc.gc-count")
            main{a.finish()};waitFor("fixture cleanup"){harness.cleanupConfirmed.get()}
            sendStatus(0,Bundle().apply{putString("stream","P82_CASE,$scenario,GREEN,$evidence,readers=1,tickets=1,partial_core=false,fd_before=$beforeFd,fd_after=$afterFd,native_before=$beforeHeap,native_after=$afterHeap,pss_kib_before=$beforePss,pss_kib_after=$afterPss,gc_count=$gc,cycle_ns=$elapsed,fixture_cleanup=true\n")})
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P82 PASS $scenario; actual Runtime/Room/SQLCipher/keyowner/VM; synthetic credentials; fault kind in report")})
        } catch(t:Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P82 FAIL $scenario: ${t.stackTraceToString()}; ${diagnostics()}")})
        } finally {main{activity?.finish()}}
    }
}
