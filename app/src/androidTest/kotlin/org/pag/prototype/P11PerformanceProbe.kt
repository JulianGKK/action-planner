package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.*
import android.view.FrameMetrics
import androidx.activity.compose.setContent
import org.pag.data.product.P11SyntheticFixture
import org.pag.domain.*
import org.pag.prototype.product.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.*

/** Real repositories/Compose with synthetic session events, NEVER product credentials or storage. */
class P11PerformanceProbe:Instrumentation() {
    private var label="baseline"
    private var preparationOnly=false
    private var rapidOnly=false
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);label=arguments?.getString("p11Label") ?: "baseline";check(label in setOf("baseline","after"));preparationOnly=arguments?.getString("p11PreparationOnly")=="true";rapidOnly=arguments?.getString("p11RapidOnly")=="true";start()}
    private fun emit(group:String,index:Int,nanos:Long){sendStatus(0,Bundle().apply{putString("stream","P11,$label,$group,$index,${nanos/1000000.0}\n")})}
    private fun meta(name:String,value:Long){sendStatus(0,Bundle().apply{putString("stream","P11_META,$label,$name,$value\n")})}
    private class SyntheticSession:SessionPort {
        var state=SessionSnapshot(SessionState.LOCKED,1)
        private val observers=mutableListOf<(SessionSnapshot)->Unit>()
        override fun snapshot()=state
        override fun observe(observer:(SessionSnapshot)->Unit):Subscription {observers+=observer;observer(state);return Subscription{observers-=observer}}
        fun ready(){state=state.copy(state=SessionState.READY);observers.toList().forEach{it(state)}}
        override fun authenticate(experiment:Experiment)=error("No credential authentication in benchmarks")
        override fun testAccess(){}
        override fun userInteraction(){}
        override fun remainingAbsoluteMillis():Long?=180000L.takeIf{state.state==SessionState.READY}
        override fun lock(){state=SessionSnapshot(SessionState.LOCKED,state.generation+1);observers.toList().forEach{it(state)}}
    }
    private fun waitUntil(predicate:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+90000
        while(SystemClock.elapsedRealtime()<deadline){var ready=false;runOnMainSync{ready=predicate()};if(ready)return;SystemClock.sleep(10)}
        error("SYNTHETIC_BENCHMARK_TIMEOUT")
    }
    override fun onStart() {
        val report=Bundle();var phase="package-guard";var activity:MainActivity?=null
        val worker=Executors.newSingleThreadExecutor()
        val frames=HandlerThread("p11-frames").apply{start()}
        val frameData=mutableListOf<Pair<String,Long>>()
        val scrolling=java.util.concurrent.atomic.AtomicBoolean(false)
        val droppedFrames=java.util.concurrent.atomic.AtomicLong()
        var fixture:P11SyntheticFixture?=null
        try {
            check(targetContext.packageName=="org.pag.perf.p11probe")
            val power=targetContext.getSystemService(PowerManager::class.java)
            meta("THERMAL_START",power.currentThermalStatus.toLong())
            meta("FD_START",java.io.File("/proc/self/fd").list()!!.size.toLong())
            meta("NATIVE_START",Debug.getNativeHeapAllocatedSize())
            phase="synthetic-fixture"
            fixture=P11SyntheticFixture(targetContext,worker,targetContext.mainExecutor)
            val f=fixture
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val host=activity
            runOnMainSync{host.window.addOnFrameMetricsAvailableListener({_,metrics,dropped->
                droppedFrames.addAndGet(dropped.toLong())
                synchronized(frameData){if(frameData.size<200000){
                    frameData+="FRAME_TOTAL" to metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                    frameData+="FRAME_LAYOUT" to metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                    if(Build.VERSION.SDK_INT>=31)frameData+="FRAME_DEADLINE" to metrics.getMetric(FrameMetrics.DEADLINE)
                    if(scrolling.get())frameData+="SCROLL_FRAME_TOTAL" to metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                }}
            },Handler(frames.looper))}
            phase="synthetic-document-import"
            fun image(format:Bitmap.CompressFormat):ByteArray {
                val bitmap=Bitmap.createBitmap(1600,1000,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}
                return try{ByteArrayOutputStream().also{bitmap.compress(format,95,it)}.toByteArray()}finally{bitmap.recycle()}
            }
            if(!rapidOnly) {
            val pdf=PdfDocument().let{doc->try{
                repeat(3){i->val page=doc.startPage(PdfDocument.PageInfo.Builder(600,800,i+1).create());page.canvas.drawColor(Color.RED);doc.finishPage(page)}
                ByteArrayOutputStream().also{doc.writeTo(it)}.toByteArray()
            }finally{doc.close()}}
            for((format,bytes) in listOf(DocumentFormat.JPEG to image(Bitmap.CompressFormat.JPEG),DocumentFormat.PNG to image(Bitmap.CompressFormat.PNG),
                DocumentFormat.TEXT to "Synthetic UTF-8 🦋\n".repeat(10000).toByteArray(),DocumentFormat.PDF to pdf)) {
                val done=CountDownLatch(1);var passed=false
                f.documents.importDocument(f.activeId,"Synthetic ${format.name}",format,DocumentSource{bytes.inputStream()}){passed=it is DocumentResult.Value;done.countDown()}
                check(done.await(90,TimeUnit.SECONDS) && passed);bytes.fill(0)
            }
            }
            repeat(10){iteration->
                val session=SyntheticSession()
                lateinit var model:ProductViewModel
                runOnMainSync {
                    model=ProductViewModel(session,f.actions,ProductStorageState.READY_TO_OPEN,f.persons,null,f.operations,f.tasks,f.protocol,f.material,f.expenses,f.memoryReports){}
                    model.bindWorkspace(f.workspace)
                    model.bindDocuments(f.documents,object:DocumentPicker {
                        override fun launch(actionId:String,generation:Long)=error("No external picker in timed fixture")
                        override fun available(actionId:String,generation:Long)=false
                        override fun consume(actionId:String,generation:Long):DocumentSource?=null
                        override fun cancel(){}
                    })
                    model.documents.bindPdfRenderer(AndroidDocumentPdfRenderer(targetContext))
                    host.setContent{ProductScreen(model)}
                }
                fun measure(name:String,action:()->Unit,done:()->Boolean) {
                    phase=name
                    val firstDraw=java.util.concurrent.atomic.AtomicLong()
                    val contentDraw=java.util.concurrent.atomic.AtomicLong()
                    val contentDrawn=CountDownLatch(1)
                    val listener=android.view.ViewTreeObserver.OnDrawListener {
                        firstDraw.compareAndSet(0,System.nanoTime())
                        if(done() && contentDraw.compareAndSet(0,System.nanoTime()))contentDrawn.countDown()
                    }
                    val started=System.nanoTime()
                    runOnMainSync{host.window.decorView.viewTreeObserver.addOnDrawListener(listener);action()}
                    emit(name+"_TAP_RETURN",iteration,System.nanoTime()-started)
                    waitUntil(done)
                    emit(name+"_PUBLICATION",iteration,System.nanoTime()-started)
                    val frame=CountDownLatch(1)
                    runOnMainSync{host.window.decorView.postOnAnimation{frame.countDown()};host.window.decorView.invalidate()}
                    check(frame.await(5,TimeUnit.SECONDS))
                    emit(name+"_FRAME_CALLBACK",iteration,System.nanoTime()-started)
                    check(contentDrawn.await(5,TimeUnit.SECONDS))
                    emit(name+"_TAP_DRAW",iteration,firstDraw.get()-started)
                    emit(name+"_CONTENT_DRAW",iteration,contentDraw.get()-started)
                    runOnMainSync{host.window.decorView.viewTreeObserver.removeOnDrawListener(listener)}
                }
                measure("READY_LIST",session::ready){model.actionListState is ActionListState.Content}
                if(!rapidOnly) {
                measure("PREPARATION",{model.detail(f.preparationId)}){model.workspace!=null && !model.personsLoading}
                measure("PREP_PERSONS",{model.preparationArea(PreparationArea.PERSONS)}){model.workspace!=null && !model.personsLoading}
                measure("PREP_TASKS",{model.preparationArea(PreparationArea.TASKS)}){model.workspace!=null && !model.personsLoading}
                if(preparationOnly) {
                    measure("PREP_MATERIAL",{model.preparationArea(PreparationArea.MATERIAL)}){model.workspace!=null && !model.personsLoading && model.material.board!=null && !model.material.loading}
                    measure("PREP_COSTS",{model.preparationArea(PreparationArea.COSTS)}){model.workspace!=null && !model.personsLoading && model.expenses.board!=null && !model.expenses.loading}
                    runOnMainSync{session.lock()};check(model.workspace==null)
                    return@repeat
                }
                measure("BACK",model::back){model.selected==null}
                }
                measure("ACTIVE_HUB",{model.detail(f.activeId)}){model.workspace!=null && !model.personsLoading}
                if(rapidOnly) {
                    measure("RAPID_AREAS",{model.activeArea(ActiveArea.PERSONS);model.activeArea(ActiveArea.TASKS);model.activeArea(ActiveArea.COSTS)}) {
                        model.activeArea==ActiveArea.COSTS && model.workspace!=null && !model.personsLoading && model.expenses.board!=null && !model.expenses.loading
                    }
                    runOnMainSync{session.lock()};check(model.workspace==null)
                    return@repeat
                }
                for(area in listOf(ActiveArea.PERSONS,ActiveArea.TASKS,ActiveArea.FUNCTIONS,ActiveArea.PROTOCOL,ActiveArea.MATERIAL,ActiveArea.COSTS,ActiveArea.MEMORY_REPORTS)) {
                    measure("ACTIVE_${area.name}",{model.activeArea(area)}){
                        model.workspace!=null && !model.personsLoading && when(area){
                            ActiveArea.PROTOCOL->model.protocol.timeline!=null && !model.protocol.loading
                            ActiveArea.MATERIAL->model.material.board!=null && !model.material.loading
                            ActiveArea.COSTS->model.expenses.board!=null && !model.expenses.loading
                            ActiveArea.MEMORY_REPORTS->model.memoryReports.headers!=null && !model.memoryReports.loading
                            else->true
                        }
                    }
                }
                measure("DOCUMENT_LIST",model::openDocuments){model.documents.items!=null && !model.documents.loading}
                val assets=model.documents.items!!
                for(format in listOf(DocumentFormat.JPEG,DocumentFormat.PNG)) {
                    measure(format.name,{model.documents.viewImage(assets.single{it.format==format})}){model.documents.image!=null}
                    runOnMainSync{model.documents.closeImage()}
                }
                measure("TEXT_FIRST",{model.documents.viewText(assets.single{it.format==DocumentFormat.TEXT})}){model.documents.text!=null}
                measure("TEXT_NEXT",{model.documents.nextTextPage()}){model.documents.text!=null && !model.documents.textLoading}
                runOnMainSync{model.documents.closeText()}
                measure("PDF_FIRST",{model.documents.viewPdf(assets.single{it.format==DocumentFormat.PDF})}){model.documents.pdfPage!=null}
                measure("PDF_NEXT",{model.documents.nextPdfPage()}){model.documents.pdfPage!=null && !model.documents.pdfLoading}
                measure("PDF_BACK",{model.documents.previousPdfPage()}){model.documents.pdfPage!=null && !model.documents.pdfLoading && model.documents.pdfIndex==0}
                runOnMainSync{model.documents.closePdf()}
                measure("DOCUMENT_BACK",model::closeDocuments){model.workspace!=null && !model.personsLoading}
                measure("SCROLL_TASKS_READY",{model.activeArea(ActiveArea.TASKS)}){model.workspace!=null && !model.personsLoading}
                phase="scroll-gestures"
                scrolling.set(true)
                val width=host.resources.displayMetrics.widthPixels.toFloat()
                val height=host.resources.displayMetrics.heightPixels.toFloat()
                repeat(8){gesture->
                    val down=SystemClock.uptimeMillis();val from=if(gesture<4).8f else .4f;val to=if(gesture<4).4f else .8f
                    for(step in 0..20) {
                        val action=when(step){0->android.view.MotionEvent.ACTION_DOWN;20->android.view.MotionEvent.ACTION_UP;else->android.view.MotionEvent.ACTION_MOVE}
                        val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,width*.5f,height*(from+(to-from)*step/20),0)
                        try{uiAutomation.injectInputEvent(event,true)}finally{event.recycle()}
                        if(step<20)SystemClock.sleep(8)
                    }
                }
                scrolling.set(false)
                runOnMainSync{session.lock()};check(model.workspace==null && model.documents.pdfPage==null)
            }
            phase="resource-end"
            fixture.close();fixture=null
            System.gc();SystemClock.sleep(500)
            meta("THERMAL_END",power.currentThermalStatus.toLong())
            meta("FD_END",java.io.File("/proc/self/fd").list()!!.size.toLong())
            meta("NATIVE_END",Debug.getNativeHeapAllocatedSize())
            meta("FRAME_DROPPED",droppedFrames.get())
            synchronized(frameData){frameData.forEachIndexed{i,(name,nanos)->emit(name,i,nanos)}}
            report.putString("result","PASS");finish(Activity.RESULT_OK,report)
        }catch(_:Throwable){report.putString("result","FAIL");report.putString("phase",phase);finish(Activity.RESULT_CANCELED,report)}
        finally {fixture?.close();activity?.let{runOnMainSync{it.finish()}};worker.shutdownNow();frames.quitSafely();frames.join(1000)}
    }
}
