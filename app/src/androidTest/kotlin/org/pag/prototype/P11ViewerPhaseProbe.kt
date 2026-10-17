package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import org.pag.data.product.P11SyntheticFixture
import org.pag.domain.*
import org.pag.prototype.product.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.*

/** Supplementary component costs, not a second before/after endpoint methodology. */
class P11ViewerPhaseProbe:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun emit(name:String,index:Int,nanos:Long){sendStatus(0,Bundle().apply{putString("stream","P11,phases,$name,$index,${nanos/1000000.0}\n")})}
    override fun onStart() {
        val report=Bundle();var phase="package-guard"
        val worker=Executors.newSingleThreadExecutor()
        try {
            check(targetContext.packageName=="org.pag.perf.p11probe")
            P11SyntheticFixture(targetContext,worker,targetContext.mainExecutor).use{f->
                fun await(work:((Boolean)->Unit)->Unit) {
                    val done=CountDownLatch(1);var passed=false
                    work{passed=it;done.countDown()};check(done.await(90,TimeUnit.SECONDS) && passed)
                }
                fun image(format:Bitmap.CompressFormat):ByteArray {
                    val bitmap=Bitmap.createBitmap(1600,1000,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.CYAN)}
                    return try{ByteArrayOutputStream().also{bitmap.compress(format,95,it)}.toByteArray()}finally{bitmap.recycle()}
                }
                val pdf=PdfDocument().let{doc->try{
                    repeat(3){i->val page=doc.startPage(PdfDocument.PageInfo.Builder(600,800,i+1).create());page.canvas.drawColor(Color.RED);doc.finishPage(page)}
                    ByteArrayOutputStream().also{doc.writeTo(it)}.toByteArray()
                }finally{doc.close()}}
                phase="synthetic-import"
                for((format,bytes) in listOf(DocumentFormat.JPEG to image(Bitmap.CompressFormat.JPEG),DocumentFormat.PNG to image(Bitmap.CompressFormat.PNG),
                    DocumentFormat.TEXT to "Synthetic UTF-8 🦋\n".repeat(10000).toByteArray(),DocumentFormat.PDF to pdf)) {
                    try{await{done->f.documents.importDocument(f.activeId,"Synthetic ${format.name}",format,DocumentSource{bytes.inputStream()}){done(it is DocumentResult.Value)}}}finally{bytes.fill(0)}
                }
                var assets:List<DocumentAsset> = emptyList()
                await{done->f.documents.list(f.activeId){if(it is DocumentResult.Value)assets=it.value;done(it is DocumentResult.Value)}}
                repeat(10){index->
                    for(format in listOf(DocumentFormat.JPEG,DocumentFormat.PNG)) {
                        phase=format.name
                        val request=DocumentImageRequest(1){p,ms->emit("${format.name}_${p.name}",index,ms*1000000)}
                        val decoder=DocumentImageDecoder{bytes,kind,check->AndroidDocumentImageDecoder.decodeTimed(bytes,kind,check){p,ns->emit("${format.name}_${p.name}",index,ns)}}
                        await{done->f.documents.readImage(f.activeId,assets.single{it.format==format},request,decoder){result->
                            (result as? DocumentResult.Value)?.value?.close();done(result is DocumentResult.Value)
                        }}
                    }
                    for(page in 0..1) {
                        phase="text-read"
                        val request=DocumentTextRequest(1){p,ms->emit("TEXT_${page}_${p.name}",index,ms*1000000)}
                        await{done->f.documents.readText(f.activeId,assets.single{it.format==DocumentFormat.TEXT},page,request){result->
                            (result as? DocumentResult.Value)?.value?.close();done(result is DocumentResult.Value)
                        }}
                        phase="pdf-read"
                        val pdfRequest=DocumentPdfRequest(1){p,ms->emit("PDF_${page}_${p.name}",index,ms*1000000)}
                        val renderer=AndroidDocumentPdfRenderer(targetContext,timing={p,ms->emit("PDF_${page}_${p.name}",index,ms*1000000)},
                            clientTiming={p,ns->emit("PDF_${page}_${p.name}",index,ns)})
                        await{done->f.documents.readPdf(f.activeId,assets.single{it.format==DocumentFormat.PDF},page,pdfRequest,renderer){result->
                            (result as? DocumentResult.Value)?.value?.close();done(result is DocumentResult.Value)
                        }}
                    }
                }
            }
            report.putString("result","PASS");finish(Activity.RESULT_OK,report)
        }catch(_:Throwable){report.putString("result","FAIL");report.putString("phase",phase);finish(Activity.RESULT_CANCELED,report)}
        finally{worker.shutdownNow();worker.awaitTermination(5,TimeUnit.SECONDS)}
    }
}
