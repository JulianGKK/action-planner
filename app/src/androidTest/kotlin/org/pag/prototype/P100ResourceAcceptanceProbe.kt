package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.ProductViewModel
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Focused #32 acceptance, original Runtime/session/reader; no exposure-fence reset. */
class P100ResourceAcceptanceProbe:Instrumentation() {
    private var scenario="repeat_boundary"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val result=AtomicReference<T>();val failure=AtomicReference<Throwable?>()
        runOnMainSync{try{result.set(work())}catch(t:Throwable){failure.set(t)}}
        failure.get()?.let{throw it};return result.get()
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream","P100,$scenario,$s\n")})
    private fun waitFor(label:String,test:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<until){if(test())return;Thread.sleep(20)}
        error("P100 timeout $label")
    }
    private fun sample(label:String) {
        val memory=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)};val r=Runtime.getRuntime()
        val files=File("/proc/self/fd").listFiles().orEmpty();var fixture=0;var unresolved=0
        for(fd in files)try {
            if(java.nio.file.Files.readSymbolicLink(fd.toPath()).toString().contains("p11-fixture-"))fixture++
        }catch(_:Exception){unresolved++}
        emit("SAMPLE,label=$label,art_used=${r.totalMemory()-r.freeMemory()},native_heap=${Debug.getNativeHeapAllocatedSize()},pss_kib=${memory.totalPss},fd=${files.size},known_fixture_fd=$fixture,unresolved_fd=$unresolved,gc=${Debug.getRuntimeStat("art.gc.gc-count")},upper_bound=UNKNOWN")
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("repeat_boundary","lock","cancel","sql_error","cleanup_unknown","budget_refused"));start()}
    override fun onStart() {
        var activity:Activity?=null;var harness:P77ProductRuntimeHarness?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent(targetContext,MainActivity::class.java).putExtra("p83Fixture","stress")
                .putExtra("p100Probe",true).putExtra("p100BudgetRefused",scenario=="budget_refused").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a
            val h=field(a,"p77Harness") as P77ProductRuntimeHarness;harness=h
            val model=field(a,"model") as ProductViewModel;val d=h.p100
            d.emit={emit(it);if(it.startsWith("CORE_ROW"))sample("core_row_open")}
            waitFor("focus"){main{a.hasWindowFocus()}}
            main{model.unlock()};waitFor("READY/list cleanup"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            sample("ready")
            if(scenario=="repeat_boundary") {
                repeat(3){round->
                    main{h.runtime.session.userInteraction()};sample("batch_${round}_before")
                    d.controlled(targetContext,h.runtime,"batches",main{h.mutations.mutationEpoch});sample("batch_${round}_after")
                }
                for(mode in listOf("R_exact","R_plus1","T_exact","T_plus1")) {
                    main{h.runtime.session.userInteraction()};d.controlled(targetContext,h.runtime,mode,main{h.mutations.mutationEpoch});sample("after_$mode")
                }
            }
            d.scenario=if(scenario=="repeat_boundary")"success" else scenario
            main{h.runtime.session.userInteraction();model.detail("p11-active")}
            if(scenario in setOf("lock","cancel")) {
                check(d.rowReached.await(60,TimeUnit.SECONDS));sample("parked_operation")
                main{
                    if(scenario=="lock")model.lock() else h.runtime.activeCore.cancelPending()
                    check(!h.observations.originalTicketLive())
                    check(!h.runtime.activeCore.loadOwned("p11-active"){_,_->error("Unexpected second admission")})
                }
                emit("PARKED_REVOKE,original_ticket_live=false,second_admission=DENIED,${h.outstandingState()}")
                d.rowRelease.countDown()
            }
            waitFor("resource/finally observation"){d.completed}
            if(scenario=="repeat_boundary") {
                waitFor("actual original core publication"){main{model.compactHubPresentation?.value(0,0)==200L}}
                main{
                    check(d.candidateChecked && model.compactHubPresentation?.value(2,0)==1000L)
                    model.lock()
                    check(!h.runtime.activeCore.loadOwned("p11-active"){_,_->error("Exposed read admitted")})
                }
                emit("EXPOSED,second_admission=DENIED,${h.outstandingState()}")
            } else {
                waitFor("worker/main end"){!h.observations.originalTicketLive() &&
                    h.outstandingState().let{if(scenario in setOf("lock","cancel","budget_refused"))it.contains("retirementConfirmed=true") else it.contains("FAILED_OR_UNCONFIRMED")}}
                main{
                    check(model.compactHubPresentation==null)
                    if(scenario in setOf("sql_error","cleanup_unknown")) {
                        check(model.snapshot.state==SessionState.BLOCKED)
                        check(!h.runtime.activeCore.loadOwned("p11-active"){_,_->error("Unconfirmed read admitted")})
                    } else if(scenario=="lock")check(model.snapshot.state==SessionState.LOCKED)
                    else if(scenario=="budget_refused")check(model.snapshot.state==SessionState.READY && d.failureKind=="BUDGET_REFUSED" && !d.rowWasReached())
                }
                emit("FAILURE_POLICY,state=${main{model.snapshot.state}},type=${d.failureKind},payload_row_seen=${d.rowWasReached()},${h.outstandingState()}")
            }
            check(h.observations.reads.get()==1 && h.observations.tickets.get()==1)
            d.assertIdle();emit("FINAL,${d.evidence()},injected_vendor_close_failure=false")
            sample("before_finish")
            val roots=targetContext.noBackupFilesDir.listFiles().orEmpty().filter{it.name.startsWith("p11-fixture-")}
            check(roots.size==1)
            main{model.lock();a.finish()};waitFor("actual fixture close"){h.cleanupConfirmed.get()}
            check(roots.none{it.exists()});check(targetContext.noBackupFilesDir.listFiles().orEmpty().none{it.name.startsWith("p11-fixture-")})
            sample("after_finish")
            emit("CASE,ANDROID_PASS,fixture_close_returned=true,fixture_absent=true,no_forced_gc=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P100 PASS $scenario")})
        } catch(t:Throwable) {
            emit("FAILURE,${t.stackTraceToString()}")
            harness?.p100?.rowRelease?.countDown()
            runCatching{main{activity?.finish()}}
            harness?.let{runCatching{waitFor("failure fixture cleanup"){it.cleanupConfirmed.get()}}}
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P100 FAIL $scenario")})
        }
    }
}
