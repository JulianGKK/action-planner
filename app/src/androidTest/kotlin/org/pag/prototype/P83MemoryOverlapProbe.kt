package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.*
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real P77 Runtime/Room/VM/Compose. Reflection reads existing roots only, creates no owner.
 * Slot traversal and counters are diagnostics; neither grants admission nor exact heap bytes.
 * Main is briefly parked to keep the already composed subtree while a real worker proceeds.
 */
class P83MemoryOverlapProbe:Instrumentation() {
    private var profile="large"
    private var scenario="replace"
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);profile=arguments?.getString("fixture") ?: "large";scenario=arguments?.getString("scenario") ?: "replace";start()}
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val r=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{r.set(work())}catch(t:Throwable){e.set(t)}}
        e.get()?.let{throw it};return r.get()
    }
    private fun waitFor(name:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(20)}
        error("P83 timeout $name")
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream",s+"\n")})
    private fun memory():String {
        val r=Runtime.getRuntime();val p=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}
        return "art_used=${r.totalMemory()-r.freeMemory()},art_committed=${r.totalMemory()},native_heap=${Debug.getNativeHeapAllocatedSize()},pss_kib=${p.totalPss},fd=${java.io.File("/proc/self/fd").list()?.size},gc=${Debug.getRuntimeStat("art.gc.gc-count")}"
    }
    /** Strong paths rooted in the actual WrappedComposition.original. Read on Main only.
     * Stops at models, observers and weak refs; never follows session/key/DB state.
     * Records class/field paths, never field values, row strings, key material or paths.
     */
    private fun cores(root:Any?):Map<Int,String> {
        val found=linkedMapOf<Int,String>();val seen=IdentityHashMap<Any,Boolean>()
        val queue=ArrayDeque<Pair<Any,String>>()
        if(root!=null)queue.add(root to root.javaClass.simpleName)
        while(queue.isNotEmpty()) {
            val (o,path)=queue.removeFirst()
            if(seen.put(o,true)!=null)continue
            check(seen.size<150000){"Diagnostic traversal cap reached"}
            if(o is ActiveCorePayload){found[System.identityHashCode(o)]=path;continue}
            if(o is Array<*>){o.forEachIndexed{i,v->if(v!=null)queue.add(v to "$path[$i]")};continue}
            val n=o.javaClass.name
            if(!(n.startsWith("androidx.compose.runtime.") || n.startsWith("org.pag.data.product.ProductActiveCoreLoader$") || o is ActiveCoreResult.Value))continue
            var c:Class<*>?=o.javaClass
            while(c!=null && c!=Any::class.java){
                c.declaredFields.filter{!Modifier.isStatic(it.modifiers) && !it.type.isPrimitive}.forEach {f->
                    f.isAccessible=true;f.get(o)?.let{queue.add(it to "$path.${f.name}")}
                };c=c.superclass
            }
        }
        return found
    }
    private fun composeRoots(a:Activity):Map<Int,String> {
        val result=linkedMapOf<Int,String>()
        val tag=a.resources.getIdentifier("wrapped_composition_tag","id",a.packageName)
        check(tag!=0){"Missing actual Compose root tag"}
        fun visit(v:View) {
            v.getTag(tag)?.let{result.putAll(cores(field(it,"original")))}
            if(v is ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i))
        }
        visit(a.window.decorView);return result
    }
    private fun census(core:ActiveCorePayload):String {
        val seen=IdentityHashMap<Any,Boolean>();val queue=ArrayDeque<Any>();queue.add(core)
        var objects=0;var strings=0;var units=0L;var lists=0;var entries=0
        while(queue.isNotEmpty()) {
            val o=queue.removeFirst();if(seen.put(o,true)!=null)continue
            when(o) {
                is String->{strings++;units+=o.length}
                is List<*>->{lists++;entries+=o.size;o.forEach{if(it!=null)queue.add(it)}}
                is Enum<*>->Unit
                else->if(o.javaClass.name.startsWith("org.pag.domain.")) {
                    objects++;o.javaClass.declaredFields.filter{!Modifier.isStatic(it.modifiers) && !it.type.isPrimitive}.forEach{f->f.isAccessible=true;f.get(o)?.let{queue.add(it)}}
                }
            }
        }
        return "dto_objects=$objects,distinct_strings=$strings,utf16_units=$units,lists=$lists,list_entries=$entries,people_cards=${core.people.items.size},task_items=${core.tasks.items.size},function_items=${core.functions.items.size},exact_heap_bytes=UNKNOWN"
    }
    override fun onStart() {
        var a:MainActivity?=null;var h:P77ProductRuntimeHarness?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            check(context.packageName=="org.pag.prototype.a.p77runtime.test")
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).putExtra("p83Fixture",profile).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            a=activity
            val harness=field(activity,"p77Harness") as P77ProductRuntimeHarness;h=harness
            val runtime=harness.runtime;val model=field(activity,"model") as ProductViewModel;val probe=harness.memory
            check(harness.memoryFixture==profile)
            waitFor("focused locked"){main{activity.hasWindowFocus() && runtime.session.snapshot().state==SessionState.LOCKED}}
            main{model.unlock()};waitFor("READY"){runtime.session.snapshot().state==SessionState.READY}
            waitFor("actions"){main{model.actions.any{it.id=="p11-active"}}}
            probe.begin(false)
            main{model.detail("p11-active")}
            waitFor("first read"){main{model.activeCore!=null || model.workspaceError}}
            fun fullView() {
                main{emit("P83_FULL_BEGIN,$profile,t_ns=${SystemClock.elapsedRealtimeNanos()},${memory()}");model.useFullActiveView();check(model.activeCore==null)}
                var lastTouch=SystemClock.elapsedRealtime()
                var touches=0
                waitFor("full workspace"){
                    if(scenario=="history_touch" && SystemClock.elapsedRealtime()-lastTouch>8000) {
                        val rect=main {
                            check(activity.hasWindowFocus() && activity.packageName==targetContext.packageName)
                            val view=activity.window.decorView
                            val origin=IntArray(2).also{view.getLocationOnScreen(it)}
                            android.graphics.Rect(origin[0],origin[1],origin[0]+view.width,origin[1]+view.height)
                        }
                        val t=SystemClock.uptimeMillis()
                        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                            val event=android.view.MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
                            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                            try{check(uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
                        }
                        lastTouch=SystemClock.elapsedRealtime();touches++
                        emit("P83_FULL_TOUCH,$profile,count=$touches,t_ns=${SystemClock.elapsedRealtimeNanos()},${memory()}")
                    }
                    main{
                    check(runtime.session.snapshot().state==SessionState.READY){"Full-view session denied: ${runtime.session.snapshot()}"}
                    check(!model.workspaceError){"Full workspace rejected"}
                    model.workspace!=null && !model.workspaceRefreshing
                }}
                val editorDraw=CountDownLatch(1)
                lateinit var editorObserver:android.view.ViewTreeObserver.OnDrawListener
                main {
                    editorObserver=android.view.ViewTreeObserver.OnDrawListener {if(model.tasks.form)editorDraw.countDown()}
                    activity.window.decorView.viewTreeObserver.addOnDrawListener(editorObserver)
                    check(model.activeCore==null)
                    val workspace=checkNotNull(model.workspace)
                    val board=checkNotNull(workspace.tasks)
                    val task=checkNotNull(board.tasks.firstOrNull())
                    model.tasks.open(task);model.tasks.edit();check(model.tasks.form)
                    // Disjoint logical per-field accounting, no dedup discount. Pure estimator,
                    // not a reservation or a certified size. History remains entirely available.
                    val historyUnits=ActiveWorkspaceBudget.Units(objects=board.events.size.toLong(),
                        strings=board.events.size*3L,utf16Units=board.events.sumOf{(it.id.length+it.actionId.length+it.taskId.length).toLong()},
                        lists=1,listCapacity=board.events.size.toLong(),references=board.events.size.toLong())
                    val historyCharge=ActiveWorkspaceBudget.estimateUnits(historyUnits).second
                    emit("P83_FULL,$profile,t_ns=${SystemClock.elapsedRealtimeNanos()},tasks=${board.tasks.size},events=${board.events.size},persons=${workspace.persons.size},form=${model.tasks.form},compact_slot=false,history_engineering_charge=$historyCharge,${memory()}")
                    if(profile=="stress")check(board.events.size==129000){"Complete stress history missing"}
                }
                check(editorDraw.await(5,TimeUnit.SECONDS)){"No actual editor frame"}
                main {
                    activity.window.decorView.viewTreeObserver.removeOnDrawListener(editorObserver)
                    check(model.tasks.form && model.workspace!=null && model.activeCore==null)
                    emit("P83_EDITOR_DRAW,$profile,t_ns=${SystemClock.elapsedRealtimeNanos()},form=true,compact_slot=false,${memory()}")
                    model.tasks.cancel();model.tasks.backToTasks();model.activeArea(ActiveArea.HUB)
                }
            }
            val firstR=probe.retained;val firstT=probe.transition
            emit("P83_ADMISSION,$profile,retained=$firstR,transition=$firstT,decision=${probe.decision},failure=${probe.failure}")
            if(probe.decision=="BUDGET_REFUSED") {
                main{check(model.activeCore==null && model.workspaceError);check(!harness.observations.originalTicketLive())}
                check(probe.candidate.get()==null)
                emit("P83_REFUSAL,$profile,payload=false,visible_error=true,original_ticket_revoked=true,${probe.finish()}")
                if(profile=="stress")fullView()
                main{model.lock()}
            } else {
                check(probe.decision=="ADMITTED")
                val oldId=main{val c=checkNotNull(model.activeCore);check(c.people.overall.total==when(profile){"small"->20L;"medium"->100L;else->200L});check(c.tasks.overall.total==when(profile){"small"->40L;"medium"->250L;else->1000L});emit("P83_OBJECTS,$profile,${census(c)}");System.identityHashCode(c)}
                emit("P83_CLEANUP,first,${probe.finish()}")
                main{model.activeArea(ActiveArea.PERSONS)}
                waitFor("actual old Compose payload"){main{oldId in composeRoots(activity)}}
                fun phase(name:String) {
                    val roots=composeRoots(activity);val queued=cores(harness.faults.main.heldTaskForObservation())
                    val vm=model.activeCore?.let{System.identityHashCode(it)} ?: 0
                    val candidate=probe.candidate.get()?.let{System.identityHashCode(it)} ?: 0
                    emit("P83_PHASE,$profile,$scenario,$name,t_ns=${SystemClock.elapsedRealtimeNanos()},old_id=$oldId,vm_id=$vm,candidate_id=$candidate,compose_ids=${roots.keys.joinToString("|")},queued_ids=${queued.keys.joinToString("|")},retained_charge=$firstR,reader_retained=${probe.retained},reader_transition=${probe.transition},${probe.nativeState()},${memory()}")
                    roots.forEach{(id,path)->emit("P83_ROOT,$name,$id,$path")}
                }
                main{phase("old_composed")}
                probe.begin(true)
                var newId=0
                val generation=runtime.session.snapshot().generation
                val epoch=main{harness.mutations.mutationEpoch}
                val oldLifetime=main{field(checkNotNull((field(model,"coreSlot") as AtomicReference<*>).get()),"lifetime") as ActiveCoreLifetime}
                main {
                    harness.faults.holdNextCoreMain.set(true)
                    // A denied original hit follows the unmodified fresh-read fallback.
                    harness.faults.falseAfterCallback.set(true)
                    model.activeArea(ActiveArea.TASKS)
                    check(probe.rowReached.await(20,TimeUnit.SECONDS)){"No filled row window"}
                    check(oldId in composeRoots(activity)){"Old Compose root missing during native read"}
                    check(model.activeCore==null)
                    phase("reader_window_open")
                    if(scenario=="worker_lock") {
                        val done=CountDownLatch(1)
                        Thread{try{runtime.session.lock()}finally{done.countDown()}}.start()
                        check(done.await(5,TimeUnit.SECONDS));probe.release()
                    } else {
                        probe.rowRelease.countDown()
                        check(probe.candidateReached.await(20,TimeUnit.SECONDS)){"No complete worker candidate: ${probe.failure}"}
                        newId=System.identityHashCode(checkNotNull(probe.candidate.get()))
                        check(newId!=oldId && oldId in composeRoots(activity))
                        phase("candidate_worker_complete")
                        probe.candidateRelease.countDown()
                        val end=SystemClock.elapsedRealtime()+5000
                        while(!harness.faults.main.isHeld() && SystemClock.elapsedRealtime()<end)Thread.sleep(5)
                        check(harness.faults.main.isHeld())
                        check(newId in cores(harness.faults.main.heldTaskForObservation())){"Main runnable did not retain candidate"}
                        phase("main_pending")
                        when(scenario) {
                            "replace","history_touch" -> {
                                harness.faults.main.take().run()
                                check(System.identityHashCode(checkNotNull(model.activeCore))==newId)
                                check(oldId in composeRoots(activity))
                                phase("new_vm_old_compose")
                                oldLifetime.close()
                                check(System.identityHashCode(checkNotNull(model.activeCore))==newId){"Old cleanup revoked newer ticket"}
                            }
                            "core_writer" -> {
                                harness.faults.worker.holdNext()
                                runtime.tasks.create("p11-active",TaskInput("Synthetic P83 writer")){}
                                check(harness.faults.worker.isHeld())
                            }
                            "document_writer" -> {
                                harness.faults.worker.holdNext()
                                runtime.documents.importDocument("p11-active","",DocumentFormat.TEXT,DocumentSource{error("Invalid source must not open")}){}
                                check(harness.faults.worker.isHeld())
                            }
                            "unknown" -> {val p=checkNotNull(harness.mutations.beforeDocumentQueue("p11-active",generation));check(p.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))}
                            "action" -> model.detail("p11-completed")
                            "generation" -> model.lock()
                            "lost_main" -> {harness.faults.main.discard();model.lock()}
                            "multi_pending" -> {
                                // Keep the existing second result runnable, start a third normal
                                // read. No second diagnostic Gate/owner/cache is introduced.
                                emit("P83_CLEANUP,second_before_third,${probe.finish()}")
                                probe.begin(true)
                                model.activeArea(ActiveArea.HUB)
                                check(probe.rowReached.await(20,TimeUnit.SECONDS))
                                phase("third_reader_second_pending")
                                probe.rowRelease.countDown()
                                check(probe.candidateReached.await(20,TimeUnit.SECONDS))
                                val thirdId=System.identityHashCode(checkNotNull(probe.candidate.get()))
                                check(thirdId!=newId && thirdId!=oldId)
                                check(oldId in composeRoots(activity) && newId in cores(harness.faults.main.heldTaskForObservation()))
                                phase("third_candidate_second_pending")
                                probe.candidateRelease.countDown()
                                model.lock()
                            }
                            else -> error("Unsupported scenario")
                        }
                        if(scenario !in setOf("replace","history_touch","lost_main"))harness.faults.main.take().run()
                        if(scenario !in setOf("replace","history_touch"))check(model.activeCore==null)
                    }
                }
                // Worker observation is emitted only after the original finallys, no rescue.
                waitFor("closed worker"){probe.nativeState().contains("driver_closed=true") && !probe.nativeState().contains("keylease_live=true")}
                emit("P83_CLEANUP,second,${probe.finish()}")
                if(scenario in setOf("core_writer","document_writer"))harness.faults.worker.release()
                if(scenario in setOf("replace","history_touch")) {
                    waitFor("new composed"){main{newId in composeRoots(activity)}}
                    main{phase("new_composed")}
                    fullView()
                } else {
                    main{check(model.activeCore==null);check(!harness.observations.originalTicketLive());if(scenario=="generation"){model.unlock()}}
                    if(scenario=="generation")waitFor("new generation"){runtime.session.snapshot().state==SessionState.READY && runtime.session.snapshot().generation!=generation}
                }
                main{model.lock();check(model.activeCore==null);check(!harness.mutations.ramReuseEnabled)}
                emit("P83_CONTRACT,$profile,$scenario,reads=${harness.observations.reads.get()},tickets=${harness.observations.tickets.get()},initial_epoch=$epoch,final_epoch=${main{harness.mutations.mutationEpoch}},joint_acceptance=NOT_PROVEN,native_allocator=UNKNOWN,frame_retention=UNKNOWN")
            }
            main{activity.finish()};waitFor("fixture cleanup"){harness.cleanupConfirmed.get()}
            emit("P83_CASE,$profile,$scenario,ANDROID-GREEN,fixture_cleanup=true,joint_budget_release=false")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P83 PASS $profile $scenario; observations only; joint acceptance BLOCKED")})
        } catch(t:Throwable) {
            emit("P83_ERROR,${h?.memory?.nativeState()}")
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P83 FAIL $profile $scenario: ${t.stackTraceToString()}")})
        } finally {h?.memory?.release();main{a?.finish()}}
    }
}
