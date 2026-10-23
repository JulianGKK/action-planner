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
class P95FunctionsPresentationProbe:Instrumentation() {
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
        error("P95 timeout $label")
    }
    private fun emit(text:String)=sendStatus(0,Bundle().apply{putString("stream",text+"\n")})
    private fun visible(text:String):Boolean {
        val root=uiAutomation.rootInActiveWindow ?: return false
        fun visit(node:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(!node.refresh())return false
            if(node.isVisibleToUser && node.text?.toString()==text)return true
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
        error("P95 text not visible after bounded scroll")
    }
    private fun uiEvidence() {
        val root=uiAutomation.rootInActiveWindow ?: return
        try {
            emit("P95_UI_ROOT,package=${root.packageName},children=${root.childCount}")
            if(root.packageName?.toString()!=targetContext.packageName)return
            var count=0
            fun walk(n:android.view.accessibility.AccessibilityNodeInfo) {
                if(count>=24)return
                n.text?.toString()?.let{emit("P95_UI_NODE,visible=${n.isVisibleToUser},text=${it.take(140)}");count++}
                for(i in 0 until n.childCount) {val child=n.getChild(i) ?: continue;try{walk(child)}finally{child.recycle()}}
            }
            walk(root)
        } finally{root.recycle()}
    }
    override fun onStart() {
        var activity:Activity?=null;var h:P77ProductRuntimeHarness?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val service=uiAutomation.serviceInfo
            service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            uiAutomation.serviceInfo=service
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture","stress").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity=a
            val harness=field(a,"p77Harness") as P77ProductRuntimeHarness;h=harness
            val model=field(a,"model") as ProductViewModel
            waitFor("focus"){main{a.hasWindowFocus()}}
            main{model.unlock()}
            waitFor("ready list"){main{model.snapshot.state==SessionState.READY && model.actions.any{it.id=="p11-active"}}}
            check(!harness.runtime.activeCore.reuseEnabled && !harness.mutations.ramReuseEnabled)
            main {
                model.bindActiveCore(harness.runtime.activeCore){a.runOnUiThread(it)}
                harness.faults.worker.holdNext();model.detail("p11-active")
            }
            waitFor("initial worker held"){harness.faults.worker.isHeld()}
            // Cancel before any payload/exposure. The first attempt to switch stays fenced
            // until the original worker shell actually acknowledges cancellation.
            main{model.activeArea(ActiveArea.FUNCTIONS)}
            check(harness.observations.reads.get()==0)
            main{harness.faults.worker.take().run()}
            waitFor("unpublished acknowledgement"){harness.outstandingState().contains("retirementConfirmed=true")}
            main {
                harness.faults.holdNextCoreMain.set(true)
                model.activeArea(ActiveArea.FUNCTIONS)
            }
            waitFor("functions main handoff"){harness.faults.main.isHeld()}
            val late=main{harness.faults.main.take()}
            main{late.run()}
            waitFor("identity handle"){main{model.compactFunctionsPresentation!=null}}
            val view=main{checkNotNull(model.compactFunctionsPresentation)}
            main {
                check(view.size()==6 && view.onSite()==200L && view.withoutFunction()==0L)
                val names=HashSet<String>()
                repeat(6) {
                    val expected="Funktion Ä界🦋 $it"
                    check(view.name(it)==expected && view.count(it)==if(it==0)200L else 0L)
                    check(view.line(it)=="$expected: ${if(it==0)200 else 0} vor Ort")
                    check(names.add(checkNotNull(view.name(it))))
                }
                check(view.name(-1)==null && view.name(6)==null && view.count(6)==null && view.line(6)==null)
                check(model.workspace==null && harness.observations.reads.get()==1)
            }
            waitFor("composition commit"){main{field(a,"p86Committed")==a.javaClass.getMethod("p86StateToken").invoke(a)}}
            emit("P95_OWNER_BEFORE_UI,session=${main{model.snapshot.state}},area=${main{model.activeArea}},functions=${main{view.size()}}")
            Thread.sleep(500);uiEvidence()
            show("200 Personen vor Ort · 0 ohne Funktion")
            repeat(6) {index->
                show("Funktion Ä界🦋 $index: ${if(index==0)200 else 0} vor Ort")
                emit("P95_VISIBLE_FUNCTION,index=$index,onSite=${if(index==0)200 else 0},exactName=true")
            }
            emit("P95_UI,people=200,tasks=1000,storedEvents=5000,functions=6,withoutFunction=0,onSite=200,visibleFunctions=EXACT,reuse=false,ramReuse=false")
            main {
                val reads=harness.observations.reads.get()
                val beforeRevoke=checkNotNull(view.line(0))
                val pending=checkNotNull(harness.mutations.beforeCoreQueue(
                    WorkspaceMutation(WorkspaceWriterFamily.TASK,"p11-active"),model.snapshot.generation))
                check(view.line(1)==null && view.name(0)==null && view.count(0)==null && view.size()==null)
                check(beforeRevoke=="Funktion Ä界🦋 0: 200 vor Ort") // Previously returned text is not recalled.
                check(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
                check(view.line(0)==null)
                late.run();late.run()
                check(model.compactFunctionsPresentation==null && coreOwnerIdentity(model)==null)
                view.compositionDisposed()
                check(!harness.runtime.activeCore.loadOwned("p11-active"){_,_->error("Exposed core cannot reload")})
                check(harness.observations.reads.get()==reads)
                check(harness.outstandingState().contains("retirementConfirmed=false"))
                model.lock();check(view.line(0)==null)
            }
            emit("P95_FENCES,writerPending=DENIED,UNKNOWN=DENIED,lateMain=DENIED,lock=DENIED,newCoreReads=0,secondAdmission=DENIED,retirement=UNCONFIRMED,unknownInjection=protocol_metadata_only,scalarBeforeRevoke=RETURNED,scalarAfterRevoke=DENIED,atomicFrame=NOT_CLAIMED")
            main{a.finish()}
            waitFor("fixture cleanup"){harness.cleanupConfirmed.get()}
            emit("P95_CASE,ANDROID-PASS,fixtureCleanup=true,physicalWipe=NOT_CLAIMED,frameworkNativeBounds=NOT_CLAIMED")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P95 PASS")})
        } catch(t:Throwable) {
            emit("P95_FAIL,${t.javaClass.simpleName},${t.message}")
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P95 FAIL")})
        } finally {
            activity?.let{a->runCatching{main{a.finish()}}}
            h?.faults?.clear()
        }
    }
}
