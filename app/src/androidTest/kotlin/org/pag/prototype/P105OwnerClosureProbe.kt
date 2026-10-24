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
import java.util.concurrent.atomic.AtomicInteger

/** Original Runtime/repositories/SQLCipher/Compose, synthetic P77 authentication only.
 * Held Runnables/DTOs are explicitly TEST aliases; never an owner-end proof. */
class P105OwnerClosureProbe:Instrumentation(){
    private var scenario="small"
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(30)};error("P105 timeout $label")}
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream","P105,$scenario,$s\n")})
    private fun visible(text:String):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun walk(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.isVisibleToUser && n.text?.toString()==text)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(walk(c))return true}finally{c.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && walk(root)}finally{root.recycle()}
    }
    private fun captures(root:Any,target:Any,depth:Int=7):Boolean{
        val seen=java.util.IdentityHashMap<Any,Boolean>()
        fun find(o:Any?,d:Int):Boolean{
            if(o===target)return true
            if(o==null || d==0 || seen.put(o,true)!=null)return false
            val name=o.javaClass.name
            if(!name.startsWith("org.pag.") && !name.startsWith("kotlin.jvm.internal.Ref"))return false
            return o.javaClass.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers) || it.type.isPrimitive}.any{f->
                f.isAccessible=true;find(f.get(o),d-1)}
        }
        return find(root,depth)
    }
    private fun unlock(){main{model.unlock()};await("READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}}
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("small","large"));start()}
    override fun onStart(){var a:Activity?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val activity=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture",if(scenario=="large")"p86-stress32" else "p86-small").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));a=activity
            h=field(activity,"p77Harness") as P77ProductRuntimeHarness;model=field(activity,"model") as ProductViewModel
            await("focus"){main{activity.hasWindowFocus()}};unlock()
            main{model.bindActiveCore(h.runtime.activeCore){activity.runOnUiThread(it)};model.detail("p11-active")}
            await("visible compact HUB"){visible(if(scenario=="large")"200 insgesamt" else "20 insgesamt")}
            val oldHandle=main{checkNotNull(model.compactHubPresentation)}
            val testOriginalSlot=main{(field(model,"coreSlot") as java.util.concurrent.atomic.AtomicReference<*>).get()!!}
            val testOriginalValue=field(testOriginalSlot,"value") as ActiveCoreResult.Value
            // Explicit TEST aliases to the original real SQLCipher-produced slot/value.
            // No new productive getter/owner and no inference that normal product leaks it.

            val readCount=h.observations.reads.get();check(readCount==1)
            main{model.activeArea(ActiveArea.PERSONS)}
            await("refusal"){main{model.workspaceError && !model.personsLoading}}
            check(main{oldHandle.value(0,0)==null});check(h.observations.reads.get()==readCount)
            val scopes=field(testOriginalSlot,"scopes")!!
            val diagnosis=scopes.javaClass.getDeclaredMethod("diagnosis").apply{isAccessible=true}.invoke(scopes)
            check(diagnosis=="VM_SCOPES_QUIESCENT")
            check(testOriginalValue.core.people.overall.total==(if(scenario=="large")200L else 20L))
            emit("LOCAL_END,VM_SCOPES_QUIESCENT=true,original_core_TEST_alias_alive=true,owner_end=BLOCKED")

            emit("CORE,first_visible=true,navigation_after_exposure=DENIED,old_handle=DENIED,reads=1,state="+h.outstandingState())
            // Existing fullview entry point, no fallback added. Hold its actual original Main.
            main{h.faults.main.holdNext();model.useFullActiveView()}
            await("fullview Main queued"){h.faults.main.isHeld()}
            val testMain=h.faults.main.take() // Explicit TEST alias to original product Runnable.
            main{testMain.run()}
            await("fullview published"){main{model.workspace!=null && !model.personsLoading}}
            val testWorkspace=main{checkNotNull(model.workspace)} // Explicit TEST observation alias.
            check(captures(testMain,testWorkspace.operations!!))
            val people=if(scenario=="large")200 else 20;val tasks=if(scenario=="large")1000 else 40
            check(testWorkspace.persons.size==people && testWorkspace.tasks!!.tasks.size==tasks)
            val t=testWorkspace.tasks!!.tasks.first()
            main{model.tasks.open(t);model.tasks.edit();model.tasks.change(model.tasks.input.copy(title="Synthetic P105 unsaved draft"))}
            await("visible actual task draft"){visible("Synthetic P105 unsaved draft")}
            check(main{model.tasks.form && (field(model.tasks,"editorExpected") as Task).editRevision==t.editRevision})
            main{model.tasks.cancel();model.tasks.edit()};check(main{model.tasks.input.title==t.title})
            main{model.tasks.change(model.tasks.input.copy(title="Synthetic P105 pending draft"));h.faults.worker.holdNext();model.tasks.save()}
            await("writer Worker queued"){h.faults.worker.isHeld()}
            val testWorker=h.faults.worker.take()
            check(captures(testWorker,t))
            main{model.lock()}
            check(main{model.workspace==null && !model.tasks.form && field(model.tasks,"editorExpected")==null})
            check(captures(testMain,testWorkspace.operations!!) && captures(testWorker,t))
            emit("FULLVIEW_FORM,actual_persons=$people,actual_tasks=$tasks,workspace_Main_retains_result_after_return=true,queued_writer_retains_Expected=true,Expected_revision=${t.editRevision},lock_clears_VM_not_callers=true,TEST_aliases_explicit=true,fullview_has_no_Core_admission_receipt=true,old_and_new_actual_graphs_simultaneous_TEST=true,form_draft_visible=true")
            main{testWorker.run();testMain.run();testMain.run()};unlock()
            val value=AtomicReference<OperationsResult<TaskBoard>>();val n=AtomicInteger()
            main{h.runtime.tasks.board("p11-active"){value.set(it);n.incrementAndGet()}}
            await("post-reauth task read"){n.get()==1}
            check((value.get() as OperationsResult.Value).value.tasks.single{it.id==t.id}==t)
            main{check(!h.runtime.activeCore.loadOwned("p11-active"){_,_->error("No exposed readmission")})}
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled && h.observations.reads.get()==readCount)
            emit("END,late_Main_no_restore=true,lock_before_writer_no_commit=true,reauth_readmission=DENIED,global_owner_end=BLOCKED")
            main{model.lock();activity.finish()};await("cleanup"){h.cleanupConfirmed.get()};a=null
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,reuse=false,ramReuse=false")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P105 PASS $scenario")})
        }catch(t:Throwable){emit("FAILURE,"+t.stackTraceToString());runCatching{main{a?.finish()}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P105 FAIL $scenario")})}
    }
}
