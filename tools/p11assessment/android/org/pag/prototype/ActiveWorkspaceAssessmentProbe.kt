package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import org.pag.data.product.AssessmentSyntheticFixture
import org.pag.domain.*
import org.pag.prototype.product.*
import org.pag.assessment.AssessmentStore
import java.util.concurrent.*

/** Synthetic package only. Real SQLCipher/VM/Compose; simulated READY events, no credentials. */
class ActiveWorkspaceAssessmentProbe:Instrumentation() {
    private var variant="A";private var size="typical";private var scrollOnly=false;private var geometryTrace=false
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);geometryTrace=arguments?.getString("geometryTrace")=="true";scrollOnly=arguments?.getString("scrollOnly")=="true";variant=arguments?.getString("variant")?:"A";size=arguments?.getString("size")?:"typical";check(variant in setOf("A","B","C"));check(size in setOf("small","typical","large"));start()}
    private fun emit(name:String,i:Int,value:Double){sendStatus(0,Bundle().apply{putString("stream","ASSESS,$variant,$size,$name,$i,$value\n")})}
    internal class Session:SessionPort,StorageAccess {
        var state=SessionSnapshot(SessionState.LOCKED,1)
        val observers=mutableListOf<(SessionSnapshot)->Unit>()
        override fun snapshot()=state
        override fun observe(observer:(SessionSnapshot)->Unit):Subscription{observers+=observer;observer(state);return Subscription{observers-=observer}}
        fun ready(){state=state.copy(state=SessionState.READY);observers.toList().forEach{it(state)}}
        override fun lock(){state=state.copy(state=SessionState.LOCKED,generation=state.generation+1);observers.toList().forEach{it(state)}}
        override fun userInteraction(){}
        override fun authenticate(experiment:Experiment)=error("Synthetic events only")
        override fun testAccess(){}
        override fun remainingAbsoluteMillis()=180000L.takeIf{state.state==SessionState.READY}
        override fun acquire(scope:AccessScope):StorageContext? {
            if(scope!=AccessScope.READY || state.state!=SessionState.READY)return null
            val g=state.generation
            return object:StorageContext {
                override val generation=g;override val scope=AccessScope.READY
                override fun check(point:AccessPoint)=state.state==SessionState.READY && state.generation==g
                override fun publish(result:()->Unit):Boolean{if(!check(AccessPoint.PUBLICATION))return false;result();return true}
            }
        }
    }
    private fun crash(e:Throwable) {
        val category=when(e){is OutOfMemoryError->"OOM";is IllegalArgumentException->"ILLEGAL_ARGUMENT";is IllegalStateException->"ILLEGAL_STATE";else->"OTHER"}
        sendStatus(0,Bundle().apply{putString("stream","ASSESS_CRASH,$category,"+e.stackTrace.take(8).joinToString("|"){it.className+"."+it.methodName}+"\n")})
    }
    override fun onException(obj:Any?,e:Throwable):Boolean {crash(e);return super.onException(obj,e)}
    override fun onStart(){
        val original=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error -> runCatching{crash(error)};original?.uncaughtException(thread,error) }

        val output=Bundle();val worker=Executors.newSingleThreadExecutor();val frameThread=HandlerThread("assessment-frames").apply{start()}
        var fixture:AssessmentSyntheticFixture?=null;var host:MainActivity?=null;var phase="guard"
        try {
            check(targetContext.packageName=="org.pag.perf.p11probe")
            val counts=when(size){"small"->Triple(5,10,5);"typical"->Triple(20,100,20);else->Triple(200,1000,200)}
            phase="fixture"
            fixture=AssessmentSyntheticFixture(targetContext,counts.first,counts.second,counts.third,worker,targetContext.mainExecutor)
            val f=fixture
            host=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val activity=host
            val scrolling=java.util.concurrent.atomic.AtomicBoolean(false);val scrollFrames=mutableListOf<Long>();val layouts=mutableListOf<Long>();val totals=mutableListOf<Long>();val deadlines=mutableListOf<Long>();var lost=0L
            runOnMainSync{activity.window.addOnFrameMetricsAvailableListener({_,m,d->synchronized(totals){totals+=m.getMetric(FrameMetrics.TOTAL_DURATION);layouts+=m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION);if(scrolling.get())scrollFrames+=m.getMetric(FrameMetrics.TOTAL_DURATION);deadlines+=m.getMetric(FrameMetrics.DEADLINE);lost+=d}},Handler(frameThread.looper))}
            fun meta(name:String,v:Long)=emit(name,-1,v.toDouble())
            meta("NATIVE_START",Debug.getNativeHeapAllocatedSize());meta("GC_COUNT_START",Debug.getRuntimeStat("art.gc.gc-count").toLong());meta("GC_TIME_START",Debug.getRuntimeStat("art.gc.gc-time").toLong());meta("FD_START",java.io.File("/proc/self/fd").list()!!.size.toLong())
            meta("THERMAL_START",targetContext.getSystemService(PowerManager::class.java).currentThermalStatus.toLong())
            repeat(10){iteration->
                val session=Session();var binding:AssessmentStore.Binding?=null
                val store=AssessmentStore(session,{binding},16L*1024*1024)
                var reads=0;var hits=0;var preload=false
                val full=setOf(WorkspacePart.TASKS,WorkspacePart.PROTOCOL_SUMMARY,WorkspacePart.MATERIAL_SUMMARY,WorkspacePart.EXPENSE_SUMMARY,WorkspacePart.MEMORY_SUMMARY,WorkspacePart.DOCUMENT_SUMMARY,WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD)
                val repo=object:WorkspaceRepository {
                    override fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit){
                        binding=AssessmentStore.Binding(request.actionId,request.generation,request.status,0)
                        if(variant!="A")store.read(request)?.let{hits++;publish(WorkspaceResult.Value(it));return}
                        reads++
                        val start=System.nanoTime()
                        val load=if(variant=="C" && !preload)request.copy(parts=full) else request
                        f.workspace.read(load){result->
                            if(result is WorkspaceResult.Value && variant!="A" && !preload){
                                // One retained first snapshot only, not a growing registry. Explicitly synthetic budget charge.
                                store.install(binding!!,store.ticket(),result.value,16L*1024*1024)
                                preload=true
                                emit("INITIAL_DB_AND_BUILD",iteration,(System.nanoTime()-start)/1e6)
                            }
                            publish(if(result is WorkspaceResult.Value)WorkspaceResult.Value(result.value.copy(parts=request.parts)) else result)
                        }
                    }
                }
                val pending=mutableStateOf<(() -> Boolean)?>(null)
                val composed=java.util.concurrent.atomic.AtomicBoolean(false)
                lateinit var model:ProductViewModel
                runOnMainSync{
                    model=ProductViewModel(session,f.actions,ProductStorageState.READY_TO_OPEN,f.persons,null,f.operations,f.tasks,f.protocol,f.material,f.expenses,f.memoryReports){}
                    model.bindWorkspace(repo)
                    activity.setContent{val complete=pending.value?.invoke()==true;SideEffect{if(complete)composed.set(true)};CompositionLocalProvider(LocalScrollDiagnostic provides (if(geometryTrace) { minimum,c ->
                        if(minimum>100000)sendStatus(0,Bundle().apply{putString("stream","GEOMETRY,$minimum,${c.minWidth},${c.maxWidth},${c.minHeight},${c.maxHeight}\n")})
                    } else null)){ProductScreen(model)}}
                }
                fun measure(name:String,action:()->Unit,done:()->Boolean){
                    phase=name
                    val drawn=CountDownLatch(1);val first=java.util.concurrent.atomic.AtomicLong();val content=java.util.concurrent.atomic.AtomicLong()
                    val listener=ViewTreeObserver.OnDrawListener{first.compareAndSet(0,System.nanoTime());if(composed.get()&&done()&&content.compareAndSet(0,System.nanoTime()))drawn.countDown()}
                    val start=System.nanoTime()
                    runOnMainSync{activity.window.decorView.viewTreeObserver.addOnDrawListener(listener);composed.set(false);pending.value=done;action();activity.window.decorView.invalidate()}
                    emit(name+"_TAP_RETURN",iteration,(System.nanoTime()-start)/1e6)
                    val until=SystemClock.elapsedRealtime()+90000
                    while(SystemClock.elapsedRealtime()<until){var ready=false;runOnMainSync{ready=done()};if(ready)break;SystemClock.sleep(5)}
                    runOnMainSync{check(done());activity.window.decorView.invalidate()}
                    check(drawn.await(5,TimeUnit.SECONDS))
                    emit(name+"_FIRST_DRAW",iteration,(first.get()-start)/1e6)
                    emit(name+"_CONTENT_DRAW",iteration,(content.get()-start)/1e6)
                    runOnMainSync{activity.window.decorView.viewTreeObserver.removeOnDrawListener(listener)}
                }
                measure("LIST",session::ready){model.actionListState is ActionListState.Content}
                System.gc();SystemClock.sleep(80)
                val before=Runtime.getRuntime().let{it.totalMemory()-it.freeMemory()}
                measure("OPEN",{model.detail(f.activeId)}){model.workspace!=null&&!model.personsLoading}
                System.gc();SystemClock.sleep(80)
                emit("HEAP_OPEN_DELTA_BYTES",iteration,Runtime.getRuntime().let{it.totalMemory()-it.freeMemory()-before}.toDouble())
                if(scrollOnly) {
                    measure("SCROLL_READY",{model.activeArea(ActiveArea.TASKS)}){model.workspace?.tasks!=null&&!model.personsLoading}
                    scrolling.set(true)
                    val w=activity.resources.displayMetrics.widthPixels.toFloat();val h=activity.resources.displayMetrics.heightPixels.toFloat()
                    repeat(8){gesture->val down=SystemClock.uptimeMillis();val from=if(gesture<4).8f else .4f;val to=if(gesture<4).4f else .8f
                        for(step in 0..20){val action=when(step){0->android.view.MotionEvent.ACTION_DOWN;20->android.view.MotionEvent.ACTION_UP;else->android.view.MotionEvent.ACTION_MOVE}
                            val event=android.view.MotionEvent.obtain(down,down+step*16,action,w*.5f,h*(from+(to-from)*step/20f),0)
                            try{sendPointerSync(event)}finally{event.recycle()};SystemClock.sleep(16)
                        }
                    }
                    scrolling.set(false)
                    runOnMainSync{session.lock();store.clear()}
                    return@repeat
                }
                for(area in listOf(ActiveArea.PERSONS,ActiveArea.TASKS,ActiveArea.FUNCTIONS,ActiveArea.MATERIAL,ActiveArea.COSTS,ActiveArea.HUB)) {
                    measure(area.name,{model.activeArea(area)}){model.workspace!=null&&!model.personsLoading && when(area){ActiveArea.MATERIAL->model.material.board!=null&&!model.material.loading;ActiveArea.COSTS->model.expenses.board!=null&&!model.expenses.loading;else->true}}
                }
                measure("RAPID",{model.activeArea(ActiveArea.PERSONS);model.activeArea(ActiveArea.TASKS);model.activeArea(ActiveArea.HUB)}){model.activeArea==ActiveArea.HUB&&model.workspace!=null&&!model.personsLoading}
                measure("TASKS_FOR_FORM",{model.activeArea(ActiveArea.TASKS)}){model.workspace?.tasks!=null&&!model.personsLoading}
                val task=model.workspace!!.tasks!!.tasks.first()
                measure("EXISTING_TASK",{model.tasks.open(task);model.tasks.edit()}){model.tasks.form}
                runOnMainSync{model.tasks.cancel()}
                measure("NEW_TASK",{model.tasks.add()}){model.tasks.form}
                runOnMainSync{model.tasks.cancel()}
                measure("PERSONS_FOR_FORM",{model.activeArea(ActiveArea.PERSONS)}){model.workspace!=null&&!model.personsLoading}
                val person=model.workspace!!.persons.first()
                // Deliberately retain actual person DB path: no fabricated Stage-2 person-detail port.
                measure("EXISTING_PERSON",{model.personDetail(person.id);}){model.selectedPerson!=null&&!model.personDetailLoading}
                val start=System.nanoTime()
                runOnMainSync{session.lock();store.clear();check(model.workspace==null);check(!store.retainedForTest());check(store.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet()))==null)}
                emit("REVOKE",iteration,(System.nanoTime()-start)/1e6)
                emit("WORKSPACE_DB_READS",iteration,reads.toDouble());emit("WORKSPACE_HITS",iteration,hits.toDouble())
            }
            synchronized(totals){layouts.forEachIndexed{i,v->emit("FRAME_LAYOUT_MS",i,v/1e6)};scrollFrames.forEachIndexed{i,v->emit("SCROLL_FRAME_MS",i,v/1e6)};totals.forEachIndexed{i,v->emit("FRAME_TOTAL_MS",i,v/1e6)};emit("FRAME_OVER_DEADLINE_PERCENT",-1,100.0*totals.indices.count{totals[it]>deadlines[it]}/totals.size);meta("FRAME_METRICS_DROPPED",lost)}
            meta("GC_COUNT_END",Debug.getRuntimeStat("art.gc.gc-count").toLong());meta("GC_TIME_END",Debug.getRuntimeStat("art.gc.gc-time").toLong());meta("FD_END",java.io.File("/proc/self/fd").list()!!.size.toLong());meta("NATIVE_END",Debug.getNativeHeapAllocatedSize());meta("THERMAL_END",targetContext.getSystemService(PowerManager::class.java).currentThermalStatus.toLong())
            output.putString("stream","ASSESSMENT_PASS\n")
        }catch(_:Throwable){output.putString("stream","ASSESSMENT_FAILED_PHASE_$phase\n")}
        finally{runCatching{runOnMainSync{host?.finish()}};worker.shutdown();worker.awaitTermination(60,TimeUnit.SECONDS);fixture?.close();frameThread.quitSafely()}
        finish(if(output.getString("stream")=="ASSESSMENT_PASS\n")0 else 1,output)
    }
}
