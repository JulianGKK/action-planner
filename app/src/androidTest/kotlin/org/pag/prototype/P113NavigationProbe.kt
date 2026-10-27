package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Rect
import org.json.JSONObject
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Three product cases, actual injected touch DOWN -> correlated target content drawContent end.
 * Test authentication only; no reuse switch, Session extension, forced GC or composition pulses.
 */
class P113NavigationProbe:Instrumentation(){
    private var scenario="small"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val until=SystemClock.elapsedRealtime()+45_000;while(SystemClock.elapsedRealtime()<until){if(work())return;Thread.sleep(20)};error("P113 timeout $label")}
    private fun emit(kind:String,values:JSONObject=JSONObject())=sendStatus(0,Bundle().apply{putString("stream","P113 "+values.put("kind",kind).put("scenario",scenario)+"\n")})
    private fun rectangle(text:String):Rect?{
        val root=uiAutomation.rootInActiveWindow?:return null
        fun contains(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.text?.toString()==text)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(contains(c))return true}finally{c.recycle()}};return false
        }
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Rect?{
            if(!n.refresh())return null
            if(n.isVisibleToUser && n.isClickable && n.isEnabled && contains(n))return Rect().also{n.getBoundsInScreen(it)}
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{visit(c)?.let{return it}}finally{c.recycle()}};return null
        }
        return try{if(root.packageName?.toString()==targetContext.packageName)visit(root) else null}finally{root.recycle()}
    }
    private fun tap(rect:Rect){
        val down=SystemClock.uptimeMillis()
        for(action in intArrayOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
            e.source=InputDevice.SOURCE_TOUCHSCREEN
            try{sendPointerSync(e)}finally{e.recycle()}
            if(action==MotionEvent.ACTION_DOWN)Thread.sleep(20)
        }
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("small","large","negative"));start()}
    override fun onStart(){var activity:P86FullWorkspaceActivity?=null;var harness:P77ProductRuntimeHarness?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val profile=if(scenario=="small")"p111-small" else "p111-large32"
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture",profile).putExtra("p112Probe",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P86FullWorkspaceActivity
            activity=a;val h=field(a,"p77Harness") as P77ProductRuntimeHarness;harness=h;val m=field(a,"model") as ProductViewModel
            await("focus"){main{a.hasWindowFocus()}};main{m.unlock()};await("READY"){main{m.snapshot.state==SessionState.READY && m.actions.isNotEmpty()}}
            main{m.bindActiveCore(h.runtime.activeCore){a.runOnUiThread(it)};m.detail("p11-active")}
            await("initial HUB"){main{!m.personsLoading && m.compactHubPresentation!=null}}
            await("initial person card"){rectangle("Personen")!=null}
            emit("INITIAL",JSONObject().put("fixture",profile).put("ledger",h.outstandingState()))
            val samples=linkedMapOf<String,MutableList<Double>>()
            val forward=listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB)
            val backward=listOf(ActiveArea.TASKS,ActiveArea.FUNCTIONS,ActiveArea.PERSONS,ActiveArea.HUB)
            repeat(if(scenario=="negative")1 else 5){cycle->
                for(to in forward+backward){
                    val from=main{m.activeArea}
                    runCatching{uiAutomation.waitForIdle(50,1000)}
                    var rect:Rect?=null;await("touch ${to.label}"){rect=rectangle(to.label);rect!=null}
                    main{a.p113ArmTouch(to)};tap(checkNotNull(rect))
                    val frame=main{checkNotNull(a.p113TouchSample)}
                    try{frame.await()}catch(t:Throwable){
                        emit("NO_DRAW",JSONObject().put("rect",rect.toString()).put("page_lane",main{m.pageLane.name}).put("ui_revision",main{m.uiPresentationRevision}).put("navigation_enabled",main{m.activeNavigationEnabled}).put("up_dispatch_ns",a.p113UpDispatchNanos).put("actual_area",main{m.activeArea.name}).put("error",main{m.workspaceRefusal?.name}).put("ledger",h.outstandingState()));throw t
                    }
                    check(main{m.activeArea==to && !m.workspaceError && !m.personsLoading && !m.workspaceRefreshing})
                    val elapsed=(frame.drawEnd-frame.start)/1e6
                    val route="${from.name}->${to.name}"
                    samples.getOrPut(route){mutableListOf()}.add(elapsed)
                    emit("FRAME",JSONObject().put("route",route).put("cycle",cycle).put("sample_id",frame.id)
                        .put("event_uptime_ms",a.p113EventUptime).put("dispatch_ns",a.p113DispatchNanos).put("start_ns",frame.start)
                        .put("up_event_uptime_ms",a.p113UpEventUptime).put("up_dispatch_ns",a.p113UpDispatchNanos).put("up_return_ns",a.p113UpReturnNanos)
                        .put("composition_ns",frame.compositionStart).put("commit_ns",frame.commit).put("layout_ns",frame.layout)
                        .put("draw_start_ns",frame.drawStart).put("draw_end_ns",frame.drawEnd).put("touch_to_content_ms",elapsed))
                    main{a.p113ConsumeTouch()}
                }
            }
            var performance=true
            for((route,values) in samples){val sorted=values.sorted();val n=sorted.size
                val p50=if(n%2==1)sorted[n/2] else (sorted[n/2-1]+sorted[n/2])/2
                val p95=sorted[kotlin.math.ceil(n*0.95).toInt()-1]
                performance=performance && p95<=150
                emit("ROUTE",JSONObject().put("route",route).put("n",n).put("p50_ms",p50).put("p95_ms",p95).put("within_150ms",p95<=150))
            }
            if(scenario=="negative"){
                val held=main{checkNotNull(m.compactHubPresentation)}
                val late=main{checkNotNull(held.navigation(ActiveArea.TASKS))}
                val scope=main{field(m,"pageAccess") as WorkspaceAccess}
                val local=Regex("localTransition=(\\d+)").find(h.outstandingState())!!.groupValues[1].toLong()
                check(main{h.p110PadUiBoundary(scope,0,ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-local)})
                main{m.activeArea(ActiveArea.FUNCTIONS)}
                check(main{m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED && m.activeArea==ActiveArea.HUB})
                emit("BUDGET_REFUSED",JSONObject().put("ledger",h.outstandingState()).put("injected_padding_metadata_only",true))
                main{m.lock();late()}
                check(main{m.snapshot.state==SessionState.LOCKED && held.value(0,0)==null})
                emit("LOCK_REFUSED",JSONObject().put("late_callback_denied",true).put("retirement_confirmed",false))
            }
            check(h.observations.reads.get()==1 && h.observations.workspaceMaterializations.get()==0)
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            emit("CYCLE_COMPLETE",JSONObject().put("performance_within_150ms",performance).put("ledger",h.outstandingState()).put("core_reads",1).put("full_workspaces",0))
            main{m.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("PASS",JSONObject().put("functional",true).put("performance_within_150ms",performance).put("fixture_cleanup",true))
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P113 PASS $scenario")})
        }catch(t:Throwable){emit("FAIL",JSONObject().put("error",t.stackTraceToString()));runCatching{main{activity?.finish()};harness?.let{h->await("failure cleanup"){h.cleanupConfirmed.get()}}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P113 FAIL $scenario")})}
    }
}
