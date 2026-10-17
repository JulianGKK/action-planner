package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import org.pag.domain.*
import org.pag.prototype.product.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class P10c3PdfViewerProbe:Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun synthetic(output:OutputStream){
        val doc=PdfDocument();val image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE)}
        try{repeat(3){i->val page=doc.startPage(PdfDocument.PageInfo.Builder(if(i==2)10000 else 200,if(i==2)8000 else 300,i+1).create())
            page.canvas.drawColor(if(i==0)Color.RED else Color.GREEN)
            page.canvas.drawBitmap(image,10f,10f,null);page.canvas.drawText("Synthetic PDF",20f,40f,Paint().apply{color=Color.BLACK})
            doc.finishPage(page)};doc.writeTo(output)
        }finally{doc.close();image.recycle()}
    }
    override fun onStart(){
        var phase="isolation";val report=Bundle();var activity:MainActivity?=null
        try{
            check(targetContext.packageName=="org.pag.pdf.p10c3probe")
            val roots=listOf(targetContext.filesDir,targetContext.cacheDir,targetContext.noBackupFilesDir)
            fun inventory()=roots.flatMap{it.walkTopDown().filter{f->f.isFile}.map{f->f.canonicalPath}.toList()}.toSet()
            val before=inventory();val renderer=AndroidDocumentPdfRenderer(targetContext,diagnostic={report.putInt("technical_phase",it)},timing={p,ms->report.putLong(p.name,ms)})
            val data=ByteArrayOutputStream().also{synthetic(it)}.toByteArray()
            val fdBefore=java.io.File("/proc/self/fd").list()!!.size
            val heapBefore=android.os.Debug.getNativeHeapAllocatedSize()
            report.putInt("fd_before",fdBefore)
            phase="real-render-pages"
            repeat(15){i->renderer.create(data.size.toLong()).use{input ->
                var offset=0;while(offset<data.size){val n=minOf(32768,data.size-offset);input.write(data,offset,n);offset+=n}
                val start=SystemClock.elapsedRealtime()
                input.render(i%3){}.use{page ->
                    check(page.index==i%3 && page.pageCount==3)
                    val bitmap=(page.image as BitmapDocumentImage).bitmap!!
                    check(bitmap.width<=2048 && bitmap.height<=2048 && bitmap.width.toLong()*bitmap.height<=PdfRenderLimits.PIXELS)
                    check(bitmap.allocationByteCount.toLong()<=PdfRenderLimits.BYTES)
                    check(bitmap.getPixel(bitmap.width/2,bitmap.height/2)!=Color.WHITE)
                }
                if(i==0)report.putLong("render_ms",SystemClock.elapsedRealtime()-start)
            }}
            phase="invalid-input"
            for(bytes in listOf("%PDF-invalid".toByteArray(),data.copyOf(20))){renderer.create(bytes.size.toLong()).use{input->input.write(bytes,0,bytes.size);var denied=false;try{input.render(0){}.close()}catch(_:DocumentPdfUnsupported){denied=true};check(denied)}}
            phase="password-pdf"
            context.assets.open("p10c3/password.pdf").use{stream->val bytes=stream.readBytes()
                renderer.create(bytes.size.toLong()).use{input->input.write(bytes,0,bytes.size);var denied=false;try{input.render(0){}.close()}catch(_:DocumentPdfUnsupported){denied=true};check(denied)};bytes.fill(0)}
            phase="external-link-no-action"
            context.assets.open("p10c3/link.pdf").use{stream->val bytes=stream.readBytes()
                renderer.create(bytes.size.toLong()).use{input->input.write(bytes,0,bytes.size);input.render(0){}.close()};bytes.fill(0)}
            phase="embedded-content-no-export"
            context.assets.open("p10c3/embedded.pdf").use{stream->val bytes=stream.readBytes()
                renderer.create(bytes.size.toLong()).use{input->input.write(bytes,0,bytes.size);input.render(0){}.close()};bytes.fill(0)}
            phase="extreme-page-rejected"
            context.assets.open("p10c3/extreme-page.pdf").use{stream->val bytes=stream.readBytes()
                renderer.create(bytes.size.toLong()).use{input->input.write(bytes,0,bytes.size);var denied=false
                    try{input.render(0){}.close()}catch(failure:DocumentPdfUnsupported){denied=failure.reason==DocumentFailure.PDF_PAGE_TOO_LARGE};check(denied)};bytes.fill(0)}
            phase="invalid-page-indices"
            renderer.create(data.size.toLong()).use{input->input.write(data,0,data.size)
                for(index in listOf(-1,3,10000)){var denied=false;try{input.render(index){}.close()}catch(_:DocumentPdfUnsupported){denied=true};check(denied)}}
            phase="cancel-render"
            renderer.create(data.size.toLong()).use{input->input.write(data,0,data.size);var denied=false
                try{input.render(0){if(report.getInt("technical_phase")==4)throw org.pag.data.prototype.b2.StorageAccessRevoked()}.close()}
                catch(_:org.pag.data.prototype.b2.StorageAccessRevoked){denied=true};check(denied)}
            for(fault in listOf(91,92,93,94,95)) {
                phase=when(fault){91->"binder-crash-cleanup";92->"binder-timeout-cleanup";else->"binder-invalid-reply-cleanup"}
                val died=java.util.concurrent.CountDownLatch(1)
                val wrapped=object:android.content.ContextWrapper(targetContext) {
                    override fun getApplicationContext():android.content.Context=this
                    override fun bindIsolatedService(service:Intent,flags:Int,instanceName:String,
                        executor:java.util.concurrent.Executor,connection:android.content.ServiceConnection):Boolean {
                        val tracked=object:android.content.ServiceConnection {
                            override fun onServiceConnected(name:android.content.ComponentName,binder:android.os.IBinder){
                                binder.linkToDeath({died.countDown()},0);connection.onServiceConnected(name,binder)
                            }
                            override fun onServiceDisconnected(name:android.content.ComponentName)=connection.onServiceDisconnected(name)
                            override fun onBindingDied(name:android.content.ComponentName)=connection.onBindingDied(name)
                        }
                        trackedConnection=tracked
                        return super.bindIsolatedService(Intent().setClassName(targetContext,"org.pag.prototype.pdf.PdfFeasibilityService").putExtra("fault",fault),flags,instanceName,executor,tracked)
                    }
                    private var trackedConnection:android.content.ServiceConnection?=null
                    override fun unbindService(connection:android.content.ServiceConnection){super.unbindService(trackedConnection!!);trackedConnection=null}
                }
                val failing=AndroidDocumentPdfRenderer(wrapped)
                failing.create(data.size.toLong()).use{input->input.write(data,0,data.size);var denied=false
                    try{input.render(0){}.close()}catch(failure:DocumentPdfUnsupported){denied=failure.reason==DocumentFailure.PDF_RENDERER_ERROR};check(denied)}
                check(died.await(10,java.util.concurrent.TimeUnit.SECONDS))
            }
            phase="fd-native-heap"
            System.gc();SystemClock.sleep(500)
            check(java.io.File("/proc/self/fd").list()!!.size<=fdBefore+6)
            report.putInt("fd_after",java.io.File("/proc/self/fd").list()!!.size)
            report.putLong("native_delta",android.os.Debug.getNativeHeapAllocatedSize()-heapBefore)
            phase="jpeg-png-regression"
            for((compression,format) in listOf(Bitmap.CompressFormat.JPEG to DocumentFormat.JPEG,Bitmap.CompressFormat.PNG to DocumentFormat.PNG)) {
                val bitmap=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}
                val bytes=try{ByteArrayOutputStream().also{bitmap.compress(compression,95,it)}.toByteArray()}finally{bitmap.recycle()}
                try{(AndroidDocumentImageDecoder.decode(bytes,format){} as BitmapDocumentImage).use{check(it.bitmap!!.width==200)}}finally{bytes.fill(0)}
            }
            // Native heap is measured, not claimed bounded by bitmap size.
            phase="compose-lifecycle"
            var state=SessionSnapshot(SessionState.READY,1);val action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
            val asset=DocumentAsset("p","a","synthetic",DocumentFormat.PDF,data.size.toLong(),1)
            val reads=mutableListOf<Pair<Int,(DocumentResult<DocumentPdfPage>)->Unit>>()
            val repo=object:DocumentRepository{
                override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit)=publish(DocumentResult.Value(listOf(asset)))
                override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit)=publish(DocumentResult.Value(DocumentSummary(1)))
                override fun readPdf(actionId:String,expected:DocumentAsset,page:Int,request:DocumentPdfRequest,renderer:DocumentPdfRenderer,publish:(DocumentResult<DocumentPdfPage>)->Unit){reads+=page to publish}
                override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit)=error("No import")
                override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit)=error("No delete")
            }
            val model=DocumentModel({repo},{null},{state},{action},{},{},{})
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            runOnMainSync{model.bindPdfRenderer(renderer);model.enter();model.viewPdf(asset)
                activity!!.setContent{MaterialTheme{ProductInputScope(Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){if(model.pdfViewed!=null)DocumentPdfPane(model)}}}}}
            fun complete(){val r=reads.last();renderer.create(data.size.toLong()).use{input->input.write(data,0,data.size);val rendered=input.render(r.first){};runOnMainSync{r.second(DocumentResult.Value(rendered))}};waitForIdleSync();SystemClock.sleep(100);waitForIdleSync()}
            complete();check(model.pdfPage!=null)
            runOnMainSync{model.nextPdfPage()};complete();check(model.pdfIndex==1)
            runOnMainSync{model.previousPdfPage()};complete();check(model.pdfIndex==0)
            runOnMainSync{state=SessionSnapshot(SessionState.LOCKED,2);model.clear()};waitForIdleSync();check(model.pdfPage==null && model.pdfViewed==null)
            runOnMainSync{state=SessionSnapshot(SessionState.READY,3);model.enter()};check(model.pdfViewed==null)
            data.fill(0);phase="no-plaintext-files";check(before==inventory())
            report.putString("result","PASS");finish(Activity.RESULT_OK,report)
        }catch(_:Throwable){report.putString("result","FAIL");report.putString("phase",phase);finish(Activity.RESULT_CANCELED,report)}
        finally{activity?.let{runOnMainSync{it.finish()}}}
    }
}
