package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.data.product.P88StatementDiagnostics
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Three isolated actual SQLCipher/Room/B2 cases on normal 5k synthetic fixtures.
 * No latency benchmark, forced GC, synthetic close exception or release change.
 */
class P88StatementLifetimeProbe:Instrumentation() {
    private var scenario="paging"
    private var activity:Activity?=null
    private lateinit var harness:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private var diagnostic:P88StatementDiagnostics?=null
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("paging","revoke","statement_error"));start()}
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val value=AtomicReference<T>();val failure=AtomicReference<Throwable?>()
        runOnMainSync{try{value.set(work())}catch(t:Throwable){failure.set(t)}}
        failure.get()?.let{throw it};return value.get()
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream",s+"\n")})
    private fun waitFor(label:String,test:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<until){if(test())return;Thread.sleep(25)}
        error("P88 timeout $label")
    }
    private fun sample(label:String) {
        val r=Runtime.getRuntime();val p=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}
        emit("P88_SAMPLE,$scenario,$label,art_used=${r.totalMemory()-r.freeMemory()},native_heap=${Debug.getNativeHeapAllocatedSize()},pss_kib=${p.totalPss},fd=${java.io.File("/proc/self/fd").list()?.size},gc=${Debug.getRuntimeStat("art.gc.gc-count")},upper_bound=UNKNOWN")
    }
    override fun onStart() {
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture","stress").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity=a;harness=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            waitFor("focus"){main{a.hasWindowFocus()}}
            main{model.unlock()};waitFor("READY list"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
            check(!harness.runtime.activeCore.reuseEnabled && !harness.mutations.ramReuseEnabled)
            val d=P88StatementDiagnostics(targetContext,harness.runtime).also{diagnostic=it}
            sample("before_owned_scope")
            emit("P88_SCOPE,$scenario,${d.ownedScope(scenario)}")
            emit("P88_RESOURCES,$scenario,owned_scope,${d.evidence()}")
            sample("after_owned_scope")
            if(scenario=="paging") {
                main{model.detail("p11-active")}
                waitFor("full workspace"){main{model.workspace?.tasks!=null && !model.workspaceRefreshing && !model.personsLoading}}
                main{model.activeArea(ActiveArea.TASKS)}
                waitFor("task area"){main{model.activeArea==ActiveArea.TASKS && model.workspace?.tasks!=null && !model.workspaceRefreshing && !model.personsLoading}}
                main{
                    val b=model.workspace!!.tasks!!
                    check(b.tasks.size==1000 && b.events.isEmpty() && !b.eventsComplete)
                    model.tasks.open(b.tasks.minBy{it.id})
                }
                repeat(2){round->
                    for(page in listOf(1L,2L,63L,126L)) {
                        main{when(page){1L->model.tasks.loadHistory(true);2L->model.tasks.loadHistory();else->model.tasks.loadHistoryPage(page)}}
                        waitFor("history $round/$page"){main{!model.tasks.historyBusy && model.tasks.history!=null && model.tasks.historyOffset==(page-1)*32}}
                        main{
                            val p=model.tasks.history!!
                            check(p.total==4001L && p.events.size==if(page==126L)1 else 32)
                            check((p.next==null)==(page==126L))
                            p.events.forEachIndexed{i,e->val n=(page-1)*32+i;if(n==0L)check(e.type==TaskEventType.CREATED)else check(e.id=="p83-e$n" && e.occurredAt==n+100)}
                        }
                        d.assertIdle();emit("P88_PAGE,round=$round,page=$page,rows=${if(page==126L)1 else 32},total=4001,keyset_or_direct=true,${d.evidence()}")
                        sample("round_${round}_page_$page")
                    }
                }
            } else if(scenario=="revoke")check(main{model.snapshot.state==SessionState.LOCKED && model.workspace==null})
            else check(main{model.snapshot.state==SessionState.READY}) // Controlled error caller, not repository error policy.
            d.assertIdle();d.trace().forEach{emit("P88_TRACE,$scenario,$it")}
            emit("P88_FINAL,$scenario,${d.evidence()},gc_release_proof=false,native_wipe_proof=false")
            sample("before_finish")
            main{model.lock();a.finish()};waitFor("fixture cleanup"){harness.cleanupConfirmed.get()}
            emit("P88_CASE,$scenario,ANDROID_PASS,fixture_cleanup=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P88 PASS $scenario")})
        }catch(t:Throwable){
            diagnostic?.let{emit("P88_FAILURE_EVIDENCE,$scenario,${it.evidence()}");it.trace().forEach{s->emit("P88_TRACE,$scenario,$s")}}
            main{activity?.finish()}
            if(::harness.isInitialized)runCatching{waitFor("failure cleanup"){harness.cleanupConfirmed.get()}}
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P88 FAIL $scenario: ${t.stackTraceToString()}")})
        }
    }
}
