package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual product admission, original Runtime/Room/SQLCipher/keyowner/VM. Synthetic P77 auth.
 * The unchanged §83 reports remain the historical three-graph RED characterization.
 */
class P85TaskHistoryProbe:Instrumentation() {
    private var scenario="multi_pending"
    private var fixture="stress"
    private var ownActivity:MainActivity?=null
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario") ?: scenario;fixture=arguments?.getString("fixture") ?: fixture;start()}
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())return work()
        val result=AtomicReference<T>();val error=AtomicReference<Throwable?>()
        runOnMainSync{try{result.set(work())}catch(t:Throwable){error.set(t)}}
        error.get()?.let{throw it};return result.get()
    }
    private fun waitFor(name:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(20)}
        error("P85a timeout $name")
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream",s+"\n")})
    private fun raw():String {
        val r=Runtime.getRuntime();val p=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}
        return "art_used=${r.totalMemory()-r.freeMemory()},native_heap=${Debug.getNativeHeapAllocatedSize()},pss_kib=${p.totalPss},fd=${java.io.File("/proc/self/fd").list()?.size},gc=${Debug.getRuntimeStat("art.gc.gc-count")}"
    }
    override fun onStart() {
        var activity:MainActivity?=null;var h:P77ProductRuntimeHarness?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            check(context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent(targetContext,MainActivity::class.java).putExtra("p83Fixture",fixture).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity=a;ownActivity=a
            val handle=field(a,"p77Harness") as P77ProductRuntimeHarness;h=handle
            val model=field(a,"model") as ProductViewModel;val runtime=handle.runtime
            waitFor("focused LOCKED"){main{a.hasWindowFocus() && runtime.session.snapshot().state==SessionState.LOCKED}}
            main{model.unlock()};waitFor("READY"){runtime.session.snapshot().state==SessionState.READY}
            waitFor("actions"){main{model.actions.any{it.id=="p11-active"}}}
            main{model.detail("p11-active")}
            waitFor("compact attempt"){main{model.activeCore!=null || model.workspaceError}}
            val began=SystemClock.elapsedRealtime()
            main{model.activeArea(ActiveArea.TASKS);model.useFullActiveView()}
            waitFor("bounded full workspace"){main{
                check(runtime.session.snapshot().state==SessionState.READY)
                !model.workspaceRefreshing && model.workspace?.tasks!=null
            }}
            val fullMs=SystemClock.elapsedRealtime()-began
            main {
                val board=checkNotNull(model.workspace?.tasks)
                val expected=if(fixture=="small")40 else 1000
                check(board.tasks.size==expected && board.events.isEmpty() && !board.eventsComplete)
                check(checkNotNull(model.workspace!!.operations).persons.size==if(fixture=="small")20 else 200)
                emit("P85a_BOARD,$fixture,tasks=${board.tasks.size},eager_events=${board.events.size},history_complete=${board.eventsComplete},elapsed_ms=$fullMs,${raw()}")
            }
            waitFor("tasks workspace"){main{!model.workspaceRefreshing && model.workspace?.tasks!=null}}
            val task=main{checkNotNull(model.workspace?.tasks?.tasks).minBy{it.id}.also{model.tasks.open(it)}}
            val draw=CountDownLatch(1)
            lateinit var listener:android.view.ViewTreeObserver.OnDrawListener
            main {
                listener=android.view.ViewTreeObserver.OnDrawListener{if(model.tasks.form)draw.countDown()}
                a.window.decorView.viewTreeObserver.addOnDrawListener(listener)
                model.tasks.edit();check(model.tasks.form)
            }
            check(draw.await(8,TimeUnit.SECONDS)){"Editor did not draw"}
            showText("Aufgabe bearbeiten")
            main{a.window.decorView.viewTreeObserver.removeOnDrawListener(listener);check(model.tasks.input==task.input());model.tasks.cancel();check(!model.tasks.form && model.tasks.selected==task)}
            waitForIdleSync()
            emit("P85a_DETAIL_STATE,$fixture,"+main{"session=${runtime.session.snapshot().state},form=${model.tasks.form},selected=${model.tasks.selected?.id},area=${model.activeArea},history=${model.tasks.history!=null},historyBusy=${model.tasks.historyBusy},error=${model.tasks.historyError}"})
            emit("P85a_UI_BEFORE_HISTORY,$fixture,${uiText()}")
            showText("Chronik laden")
            val seen=HashSet<String>();var lastTime=Long.MIN_VALUE;var count=0L;var pages=0;var oldCursor:CompactEventCursor?=null
            do {
                val start=SystemClock.elapsedRealtime()
                main{model.tasks.loadHistory()}
                waitFor("history page"){main{!model.tasks.historyBusy}}
                val page=main{check(model.tasks.historyError==null){"History failure ${model.tasks.historyError}"};checkNotNull(model.tasks.history)}
                check(page.events.size<=32)
                check(page.total==if(fixture=="stress")128001L else 1L)
                page.events.forEach{check(seen.add(it.id));check(it.occurredAt>=lastTime);lastTime=it.occurredAt}
                count+=page.events.size;pages++;oldCursor=page.next
                emit("P85a_PAGE,$fixture,page=$pages,rows=${page.events.size},total=${page.total},seen=$count,next=${page.next!=null},elapsed_ms=${SystemClock.elapsedRealtime()-start},${raw()}")
                showText("Ereignisse ${count-page.events.size+1}–$count von ${page.total}")
                // First two real pages prove replacement on stress; complete traversal on host.
                if(pages==2 && fixture=="stress")break
            } while(page.next!=null)
            check(main{model.tasks.history!!.events.size}<=32)
            if(fixture=="stress") for(number in listOf(4001L,2001L)) {
                val start=SystemClock.elapsedRealtime()
                main{model.tasks.loadHistoryPage(number)}
                waitFor("direct history page $number"){main{!model.tasks.historyBusy}}
                val page=main{check(model.tasks.historyError==null);checkNotNull(model.tasks.history)}
                check(page.total==128001L && page.events.size==if(number==4001L)1 else 32)
                check(main{model.tasks.historyOffset}==(number-1)*32)
                if(number==4001L)check(page.next==null)
                val offset=(number-1)*32
                showText("Ereignisse ${offset+1}–${offset+page.events.size} von ${page.total}")
                emit("P85a_DIRECT,$fixture,page=$number,rows=${page.events.size},offset=$offset,total=${page.total},elapsed_ms=${SystemClock.elapsedRealtime()-start},${raw()}")
            }
            // Existing real editor/CAS write path; refresh must still avoid the event list.
            main {model.tasks.edit();model.tasks.change(model.tasks.input.copy(title="Synthetic §85a 界🦋"));model.tasks.save()}
            waitFor("editor save and workspace refresh"){main{!model.tasks.busy && !model.workspaceRefreshing && model.tasks.selected?.title=="Synthetic §85a 界🦋"}}
            main{check(!model.tasks.form && model.workspace!!.tasks!!.events.isEmpty());check(!model.workspace!!.tasks!!.eventsComplete)}
            emit("P85a_EDITOR_SAVE,$fixture,real_repository=true,refreshed=true,events_not_fetched=true")
            if(oldCursor!=null) main {
                val epoch=handle.mutations.mutationEpoch
                var result:CompactTaskResult?=null
                runtime.tasks.history(checkNotNull(model.tasks.selected),oldCursor){result=it}
                check(result==CompactTaskResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED))
                check(handle.mutations.mutationEpoch==epoch)
                emit("P85a_CURSOR,$fixture,old_epoch_rejected=true,mutation_epoch=$epoch")
            }
            // Actual page scopes/held Main, common CORE and DOCUMENT metadata admission.
            for(document in listOf(false,true)) {
                main{handle.faults.main.holdNext();model.tasks.loadHistory(true)}
                waitFor("held history Main"){handle.faults.main.heldTaskForObservation()!=null}
                main {
                    val generation=runtime.session.snapshot().generation
                    val pending=checkNotNull(if(document)handle.mutations.beforeDocumentQueue("p11-active",generation) else handle.mutations.beforeTaskQueue("p11-active",generation))
                    handle.faults.main.take().run()
                    check(model.tasks.history==null && model.tasks.historyError==BoundedActiveStatus.ACCESS_REVOKED)
                    check(if(document)pending.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE)) else
                        pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
                    model.tasks.loadHistory(true)
                }
                waitFor("page after confirmed writer end"){main{!model.tasks.historyBusy}}
                main{check(model.tasks.history!=null)}
                emit("P85a_WRITER_FENCE,$fixture,document=$document,metadata_injection=true,late_payload=false,restart_after_confirmed_end=true")
            }
            main {
                val pending=checkNotNull(handle.mutations.beforeTaskQueue("p11-active",runtime.session.snapshot().generation))
                check(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
                model.tasks.loadHistory(true)
                check(model.tasks.history==null && model.tasks.historyError==BoundedActiveStatus.ACCESS_REVOKED)
            }
            emit("P85a_UNKNOWN,$fixture,history_denied=true")
            main {
                check(model.activeCore==null)
                check(handle.outstandingState().contains("fullViewUnconfirmed=true"))
                emit("P85a_LIFETIME,${handle.outstandingState()}")
                model.lock();check(model.tasks.history==null && model.tasks.selected==null)
            }
            emit("P85a_REVOKE,$fixture,history_cleared=true,session=${runtime.session.snapshot().state}")
            main{a.finish()};waitFor("own fixture cleanup"){handle.cleanupConfirmed.get()}
            emit("P85a_CASE,$fixture,ANDROID-GREEN,board_editor_history=true,complete_stress_traversal=false,direct_middle_end=${fixture=="stress"},fixture_cleanup=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P85a PASS $fixture: bounded full workspace/editor/history pages; not joint memory certification")})
        } catch(t:Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P85a FAIL $fixture: ${t.stackTraceToString()}")})
        } finally {main{activity?.finish()}}
    }
    private fun uiText():String {
        val root=uiAutomation.rootInActiveWindow ?: return "no_root"
        if(root.packageName?.toString()!=targetContext.packageName){root.recycle();return "foreign_root"}
        val result=ArrayList<String>()
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo){
            if(result.size>=100)return
            if(n.text!=null)result.add(n.text.toString().take(160))
            if(n.isScrollable)result.add("SCROLLABLE:${n.className}")
            for(i in 0 until n.childCount){val child=n.getChild(i) ?: continue;try{visit(child)}finally{child.recycle()}}
        }
        return try{visit(root);result.joinToString("|")}finally{root.recycle()}
    }
    private fun showText(text:String) {
        waitForIdleSync();Thread.sleep(500)
        repeat(4){if(containsText(text))return;swipe(false);Thread.sleep(200)}
        repeat(12){if(containsText(text))return;swipe(true);Thread.sleep(200)}
        check(containsText(text)){"No actual accessibility content: $text; ${uiText()}"}
    }
    private fun swipe(forward:Boolean) {
        val rect=main {
            val a=checkNotNull(ownActivity)
            check(a.packageName==targetContext.packageName)
            if(!a.hasWindowFocus())return@main null
            val v=a.window.decorView;val origin=IntArray(2).also{v.getLocationOnScreen(it)}
            android.graphics.Rect(origin[0],origin[1],origin[0]+v.width,origin[1]+v.height)
        } ?: return
        val root=uiAutomation.rootInActiveWindow ?: return
        try{if(root.packageName?.toString()!=targetContext.packageName)return}finally{root.recycle()}
        val start=SystemClock.uptimeMillis()
        val from=rect.top+rect.height()*(if(forward)0.78f else 0.30f)
        val to=rect.top+rect.height()*(if(forward)0.30f else 0.78f)
        for(i in 0..8) {
            val action=when(i){0->android.view.MotionEvent.ACTION_DOWN;8->android.view.MotionEvent.ACTION_UP;else->android.view.MotionEvent.ACTION_MOVE}
            val event=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,rect.exactCenterX(),from+(to-from)*i/8,0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try{check(uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
            Thread.sleep(25)
        }
    }
    private fun containsText(text:String):Boolean {
        val root=uiAutomation.rootInActiveWindow ?: return false
        if(root.packageName?.toString()!=targetContext.packageName){root.recycle();return false}
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(n.text?.toString()?.contains(text)==true)return true
            for(i in 0 until n.childCount){val child=n.getChild(i) ?: continue;try{if(visit(child))return true}finally{child.recycle()}}
            return false
        }
        return try{visit(root)}finally{root.recycle()}
    }
}
