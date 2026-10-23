package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** One isolated real Runtime/SQLCipher/Compose case. No reuse opt-in, forced GC or wipe claim. */
class P96CoreSurfaceProbe:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val result=AtomicReference<T>();val failure=AtomicReference<Throwable?>()
        runOnMainSync {try{result.set(work())}catch(t:Throwable){failure.set(t)}}
        failure.get()?.let{throw it};return result.get()
    }
    private fun waitFor(label:String,work:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+45_000
        while(SystemClock.elapsedRealtime()<until){if(work())return;Thread.sleep(25)}
        error("P96 timeout $label")
    }
    private fun emit(text:String)=sendStatus(0,Bundle().apply{putString("stream",text+"\n")})
    private fun visible(text:String):Boolean {
        val root=uiAutomation.rootInActiveWindow ?: return false
        fun visit(node:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(!node.refresh())return false
            if(node.isVisibleToUser && node.text?.toString()?.lineSequence()?.any{it==text}==true)return true
            for(i in 0 until node.childCount) {
                val child=node.getChild(i) ?: continue
                try{if(visit(child))return true}finally{child.recycle()}
            }
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    private fun show(text:String) {
        repeat(8) {
            if(visible(text))return
            val metrics=targetContext.resources.displayMetrics
            val x=metrics.widthPixels/2f;val from=metrics.heightPixels*0.75f;val to=metrics.heightPixels*0.5f
            val start=SystemClock.uptimeMillis()
            for(step in 0..6) {
                val e=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),
                    if(step==0)android.view.MotionEvent.ACTION_DOWN else if(step==6)android.view.MotionEvent.ACTION_UP else android.view.MotionEvent.ACTION_MOVE,
                    x,from+(to-from)*step/6,0)
                e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try{check(uiAutomation.injectInputEvent(e,true))}finally{e.recycle()}
                Thread.sleep(if(step==5)200 else 60)
            }
            Thread.sleep(250)
        }
        uiEvidence()
        error("P96 text not visible after bounded scroll")
    }
    private fun uiEvidence() {
        val root=uiAutomation.rootInActiveWindow ?: return
        try {
            emit("P96_UI_ROOT,package=${root.packageName},children=${root.childCount}")
            if(root.packageName?.toString()!=targetContext.packageName)return
            var count=0
            fun walk(n:android.view.accessibility.AccessibilityNodeInfo) {
                if(count>=24)return
                n.text?.toString()?.let{emit("P96_UI_NODE,visible=${n.isVisibleToUser},text=${it.take(140)}");count++}
                for(i in 0 until n.childCount) {val child=n.getChild(i) ?: continue;try{walk(child)}finally{child.recycle()}}
            }
            walk(root)
        } finally{root.recycle()}
    }
    private fun handle(model:ProductViewModel):Any?=when(model.activeArea) {
        ActiveArea.HUB->model.compactHubPresentation;ActiveArea.PERSONS->model.compactPersonsPresentation
        ActiveArea.TASKS->model.compactTasksPresentation;ActiveArea.FUNCTIONS->model.compactFunctionsPresentation;else->null
    }
    private fun scalar(handle:Any):String?=when(handle) {
        is CompactHubPresentation->handle.value(0,0)?.toString()
        is CompactPersonsPresentation->handle.pseudonym(0)
        is CompactTasksPresentation->handle.title(0)
        is CompactFunctionsPresentation->handle.name(0)
        else->error("Unexpected presentation")
    }
    override fun onStart() {
        var activity:Activity?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            check(ProductViewModel::class.java.methods.none{it.name=="getActiveCore" || it.name=="observeActiveCoreComposition"})
            val service=uiAutomation.serviceInfo
            service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            uiAutomation.serviceInfo=service
            for(area in listOf(ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.TASKS,ActiveArea.FUNCTIONS)) {
                val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                    .putExtra("p83Fixture","p86-small").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                activity=a
                val h=field(a,"p77Harness") as P77ProductRuntimeHarness
                val model=field(a,"model") as ProductViewModel
                waitFor("focus"){main{a.hasWindowFocus()}}
                main{model.unlock()}
                waitFor("ready list"){main{model.snapshot.state==SessionState.READY && model.actions.any{it.id=="p11-active"}}}
                check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
                main {
                    model.bindActiveCore(h.runtime.activeCore){a.runOnUiThread(it)}
                    if(area==ActiveArea.HUB)h.faults.holdNextCoreMain.set(true) else h.faults.worker.holdNext()
                    model.detail("p11-active")
                }
                if(area!=ActiveArea.HUB) {
                    waitFor("unpublished worker"){h.faults.worker.isHeld()}
                    main{model.activeArea(area);check(h.observations.reads.get()==0);h.faults.worker.take().run()}
                    waitFor("actual cancellation acknowledgement"){h.outstandingState().contains("retirementConfirmed=true")}
                    main{h.faults.holdNextCoreMain.set(true);model.activeArea(area)}
                }
                waitFor("main handoff"){h.faults.main.isHeld()}
                val late=main{h.faults.main.take()};main{late.run()}
                val view=main{checkNotNull(handle(model))}
                val value=main{checkNotNull(scalar(view))}
                main {
                    check(coreOwnerIdentity(model)!=null && model.workspace==null && h.observations.reads.get()==1)
                    when(view) {
                        is CompactHubPresentation->check(view.value(0,0)==20L && view.value(1,0)==6L && view.value(2,0)==40L)
                        is CompactPersonsPresentation->check(view.total()==20L && view.shown()==20 && view.hasMore()==false)
                        is CompactTasksPresentation->check(view.activeCount()==40L && view.shown()==32 && view.hasMore()==true)
                        is CompactFunctionsPresentation->check(view.size()==6 && view.onSite()==20L && view.withoutFunction()==0L)
                    }
                }
                waitFor("composition commit"){main{field(a,"p86Committed")==a.javaClass.getMethod("p86StateToken").invoke(a)}}
                when(view) {
                    is CompactHubPresentation->show("20 insgesamt")
                    is CompactPersonsPresentation->show(value)
                    is CompactTasksPresentation->show(value)
                    is CompactFunctionsPresentation->show("Funktion Ä界🦋 0: 20 vor Ort")
                }
                main {
                    val reads=h.observations.reads.get()
                    val pending=checkNotNull(h.mutations.beforeTaskQueue("p11-active",model.snapshot.generation))
                    check(scalar(view)==null)
                    check(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
                    late.run();late.run();check(handle(model)==null && coreOwnerIdentity(model)==null)
                    check(!h.runtime.activeCore.loadOwned("p11-active"){_,_->error("Exposed core cannot readmit")})
                    check(h.observations.reads.get()==reads && h.outstandingState().contains("retirementConfirmed=false"))
                    model.lock();check(scalar(view)==null);a.finish()
                }
                waitFor("fixture cleanup"){h.cleanupConfirmed.get()}
                activity=null
                emit("P96_ROUTE,$area,visible=PASS,oneCoreRead=true,oldHandle=DENIED,UNKNOWN=DENIED,lateMain=DENIED,readmission=DENIED,reuse=false,ramReuse=false,fixtureCleanup=true")
            }
            emit("P96_CASE,ANDROID-PASS,scopes=4,publicBorrowApi=ABSENT,physicalWipe=NOT_CLAIMED")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P96 PASS")})
        } catch(t:Throwable) {
            emit("P96_FAIL,${t.javaClass.simpleName},${t.message}")
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P96 FAIL")})
        } finally {activity?.let{a->runCatching{main{a.finish()}}}}
    }
}
