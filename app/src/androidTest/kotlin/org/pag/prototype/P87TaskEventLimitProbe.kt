package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.data.product.P87TaskEventDiagnostics
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Two small isolated SQLCipher/Compose cases: real writer, real error pixels/semantics.
 * Navigation/form data are set on Main; Save/status buttons receive actual touch events.
 */
class P87TaskEventLimitProbe:Instrumentation() {
    private var total=4999
    private var activity:Activity?=null
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private lateinit var diagnostics:P87TaskEventDiagnostics
    private val message="Die Grenze von 5.000 Aufgabenereignissen je Aktion ist erreicht."
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);total=arguments?.getString("total")?.toInt()?:4999;check(total in listOf(4999,5001));start()}
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val value=AtomicReference<T>();val failure=AtomicReference<Throwable?>()
        runOnMainSync{try{value.set(work())}catch(t:Throwable){failure.set(t)}}
        failure.get()?.let{throw it};return value.get()
    }
    private fun waitFor(label:String,test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<end){if(test())return;Thread.sleep(30)}
        error("P87 timeout $label; state=${main{model.snapshot.state}}")
    }
    private fun emit(text:String)=sendStatus(0,Bundle().apply{putString("stream",text+"\n")})
    private fun idle()=model.workspace!=null && !model.personsLoading && !model.workspaceRefreshing && !model.tasks.busy
    private fun dismissFeedback()=main{model.feedback?.let{model.dismissFeedback(it.id)}}
    private fun refresh() {main{model.tasks.refresh()};waitFor("workspace refresh"){main{idle()}}}
    private fun rejected(action:String) {
        waitFor("limit error"){main{!model.tasks.busy && model.tasks.error==OperationsFailure.TASK_EVENT_LIMIT_REACHED}}
        show(message)
        check(main{model.snapshot.state==SessionState.READY && model.feedback==null})
        check(diagnostics.counts(action).last()==maxOf(total,5000).toLong())
    }
    private fun history(task:Task,expected:Long) {
        main{model.tasks.open(task);model.tasks.loadHistoryPage(1)}
        waitFor("first history"){main{!model.tasks.historyBusy && model.tasks.history!=null}}
        main{val p=model.tasks.history!!;check(p.total==expected && p.events.size==32)}
        val last=(expected+31)/32
        main{model.tasks.loadHistoryPage(last)}
        waitFor("last history"){main{!model.tasks.historyBusy && model.tasks.history!=null && model.tasks.historyOffset==(last-1)*32}}
        main{val p=model.tasks.history!!;check(p.total==expected && p.events.size==((expected-1)%32+1).toInt() && p.next==null)}
        emit("P87_HISTORY,action=${task.actionId},total=$expected,last_page=$last,max_page_rows=32,no_truncation=true")
    }
    override fun onStart() {
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture","p87-$total").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity=a;h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            diagnostics=P87TaskEventDiagnostics(targetContext,h.runtime.tasks)
            waitFor("focus"){main{a.hasWindowFocus()}}
            for((action,status) in listOf("p11-preparation" to ActionStatus.PREPARATION,"p11-active" to ActionStatus.ACTIVE)) {
                tap("Entsperren");waitFor("READY list"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
                main{model.detail(action)}
                waitFor("opened action"){main{model.selected?.id==action && idle()}}
                main{if(status==ActionStatus.PREPARATION)model.preparationArea(PreparationArea.TASKS) else model.activeArea(ActiveArea.TASKS)}
                waitFor("tasks"){main{idle() && model.workspace?.tasks!=null}}
                check(main{model.workspace!!.tasks!!.tasks.size==40 && model.workspace!!.tasks!!.events.isEmpty()})
                val original=main{model.workspace!!.tasks!!.tasks.minBy{it.id}}
                var expected=original
                var selectedHistory=(total-40+1).toLong()
                if(total==4999) {
                    // Real status button consumes exactly the final slot.
                    main{model.tasks.open(original)};tap("In Arbeit")
                    waitFor("last-slot transition"){main{idle() && model.workspace!!.tasks!!.tasks.find{it.id==original.id}?.status==TaskStatus.IN_PROGRESS}}
                    expected=main{model.workspace!!.tasks!!.tasks.single{it.id==original.id}}
                    check(diagnostics.counts(action)==listOf(4999L));selectedHistory++
                    emit("P87_LAST_SLOT,action=$action,4999_to_5000=true,task_status_persisted=true")
                }
                history(expected,selectedHistory)
                main{model.tasks.open(expected)};dismissFeedback();tap("Erledigt");rejected(action)
                check(main{model.tasks.selected==expected})
                refresh();check(main{model.workspace!!.tasks!!.tasks.single{it.id==expected.id}==expected})
                emit("P87_UI,phase=$status,operation=transition,limit_visible=true,READY=true,false_success=false,task_unchanged=true")
                main{model.tasks.backToTasks();model.tasks.add();model.tasks.change(TaskInput("Synthetic rejected task"))}
                hideKeyboard();tap("Speichern");rejected(action)
                check(main{model.tasks.form && model.tasks.input.title=="Synthetic rejected task" && model.workspace!!.tasks!!.tasks.size==40})
                main{model.tasks.cancel()};refresh();check(main{model.workspace!!.tasks!!.tasks.size==40})
                emit("P87_UI,phase=$status,operation=create,limit_visible=true,READY=true,false_success=false,draft_retained=true,no_task_inserted=true")
                main{model.tasks.open(expected);model.tasks.edit();model.tasks.change(expected.input().copy(title="Synthetic eventless edit"))}
                val countCalls=diagnostics.counts(action).size
                hideKeyboard();tap("Speichern")
                waitFor("eventless edit"){main{idle() && !model.tasks.form && model.workspace!!.tasks!!.tasks.find{it.id==expected.id}?.title=="Synthetic eventless edit"}}
                check(main{model.feedback?.text=="Aufgabe gespeichert" && model.snapshot.state==SessionState.READY})
                check(diagnostics.counts(action).size==countCalls)
                expected=main{model.workspace!!.tasks!!.tasks.single{it.id==expected.id}}
                dismissFeedback();main{model.tasks.open(expected)};tap("Erledigt");rejected(action)
                history(expected,selectedHistory)
                emit("P87_EDIT,phase=$status,real_commit=true,event_count_unchanged=${maxOf(total,5000)},counts=${diagnostics.counts(action)}")
                main{model.lock()};waitFor("LOCKED"){main{model.snapshot.state==SessionState.LOCKED && model.workspace==null}}
            }
            main{a.finish()};waitFor("fixture cleanup"){h.cleanupConfirmed.get()}
            emit("P87_CASE,total=$total,ANDROID_PASS,prep_and_active=true,cleanup=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P87 PASS $total")})
        } catch(t:Throwable) {
            main{activity?.finish()}
            if(::h.isInitialized)runCatching{waitFor("failure cleanup"){h.cleanupConfirmed.get()}}
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P87 FAIL $total: ${t.stackTraceToString()}")})
        }
    }
    private fun hideKeyboard(){waitForIdleSync();Thread.sleep(400);if(main{activity!!.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())}==true)sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)}
    private fun node(text:String):AccessibilityNodeInfo? {
        val root=uiAutomation.rootInActiveWindow?:return null
        if(root.packageName?.toString()!=targetContext.packageName){root.recycle();return null}
        fun visit(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
            if(n.isVisibleToUser && (n.text?.toString()==text || (text==message && n.text?.toString()?.startsWith(message)==true)))return AccessibilityNodeInfo.obtain(n)
            for(i in 0 until n.childCount){val child=n.getChild(i)?:continue;try{visit(child)?.let{return it}}finally{child.recycle()}}
            return null
        }
        return try{visit(root)}finally{root.recycle()}
    }
    private fun visible(text:String)=node(text)?.let{it.recycle();true}?:false
    private fun show(text:String) {
        waitForIdleSync();Thread.sleep(300)
        repeat(10){if(visible(text))return;swipe(false)}
        repeat(20){if(visible(text))return;swipe(true)}
        check(visible(text)){"P87 actual UI text missing: $text"}
    }
    private fun tap(text:String) {
        show(text);val n=checkNotNull(node(text))
        try{val b=android.graphics.Rect();n.getBoundsInScreen(b);check(n.isEnabled && !b.isEmpty)
            val now=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
                val e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,b.exactCenterX(),b.exactCenterY(),0)
                e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try{check(uiAutomation.injectInputEvent(e,true))}finally{e.recycle()};Thread.sleep(60)
            }
        }finally{n.recycle()};waitForIdleSync()
    }
    private fun swipe(forward:Boolean) {
        check(main{activity!!.hasWindowFocus()})
        val size=main{activity!!.window.decorView.let{it.width to it.height}}
        val start=SystemClock.uptimeMillis();val from=size.second*(if(forward)0.78f else 0.28f);val to=size.second*(if(forward)0.28f else 0.78f)
        for(i in 0..8){val action=when(i){0->MotionEvent.ACTION_DOWN;8->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE}
            val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,size.first/2f,from+(to-from)*i/8,0)
            e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try{check(uiAutomation.injectInputEvent(e,true))}finally{e.recycle()};Thread.sleep(65)
        };waitForIdleSync();Thread.sleep(150)
    }
}
