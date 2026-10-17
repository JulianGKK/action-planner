package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import org.pag.data.product.P11SyntheticFixture
import org.pag.domain.*
import org.pag.prototype.product.AndroidDocumentPdfRenderer
import java.io.ByteArrayOutputStream
import java.util.concurrent.*

/** Supplementary bounded synthetic PDF costs; no product credentials, files or database. */
class P11PdfVariantProbe:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart() {
        val result=Bundle();val worker=Executors.newSingleThreadExecutor();var phase="guard"
        fun emit(name:String,index:Int,nanos:Long)=sendStatus(0,Bundle().apply{
            putString("stream","P11,pdfvariants,$name,$index,${nanos/1000000.0}\n")
        })
        try {
            check(targetContext.packageName=="org.pag.perf.p11probe")
            fun meta(name:String,value:Long)=sendStatus(0,Bundle().apply{putString("stream","P11_META,pdfvariants,$name,$value\n")})
            meta("FD_START",java.io.File("/proc/self/fd").list()!!.size.toLong())
            meta("NATIVE_START",android.os.Debug.getNativeHeapAllocatedSize())
            P11SyntheticFixture(targetContext,worker,targetContext.mainExecutor).use { f ->
                fun await(work:((Boolean)->Unit)->Unit) {
                    val latch=CountDownLatch(1);var ok=false
                    work{ok=it;latch.countDown()};check(latch.await(90,TimeUnit.SECONDS) && ok)
                }
                fun pdf(pages:Int,withImage:Boolean):ByteArray {
                    val image=if(withImage)Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888) else null
                    val doc=PdfDocument()
                    try {
                        if(image!=null){val random=java.util.Random(11);val pixels=IntArray(1024*1024){random.nextInt() or (255 shl 24)}
                            try{image.setPixels(pixels,0,1024,0,0,1024,1024)}finally{pixels.fill(0)}}
                        repeat(pages){i->
                            val page=doc.startPage(PdfDocument.PageInfo.Builder(if(i%2==0)600 else 800,800,i+1).create())
                            page.canvas.drawText("Synthetic PDF page",30f,40f,Paint().apply{textSize=22f})
                            if(image!=null)page.canvas.drawBitmap(image,0f,80f,null)
                            doc.finishPage(page)
                        }
                        return ByteArrayOutputStream().also{doc.writeTo(it)}.toByteArray().also{check(it.size<=25*1024*1024)}
                    }finally{doc.close();image?.recycle()}
                }
                val specs=listOf(Triple("SMALL",1,false),Triple("MULTIPAGE",8,false),Triple("LARGER",8,true))
                for((name,pages,withImage) in specs){phase="import"
                    val bytes=pdf(pages,withImage)
                    try{await{done->f.documents.importDocument(f.activeId,"Synthetic $name",DocumentFormat.PDF,DocumentSource{bytes.inputStream()}){done(it is DocumentResult.Value)}}}
                    finally{bytes.fill(0)}
                }
                var assets:List<DocumentAsset> = emptyList()
                await{done->f.documents.list(f.activeId){if(it is DocumentResult.Value)assets=it.value;done(it is DocumentResult.Value)}}
                repeat(10){index->for((name,pages,_) in specs){
                    val asset=assets.single{it.displayName=="Synthetic $name"}
                    for((step,page) in (if(pages==1)listOf("FIRST" to 0) else listOf("FIRST" to 0,"NEXT" to 1,"BACK" to 0))){
                        phase="render";val prefix="${name}_${step}"
                        val request=DocumentPdfRequest(1){p,ms->emit("${prefix}_${p.name}",index,ms*1000000)}
                        val renderer=AndroidDocumentPdfRenderer(targetContext,
                            timing={p,ms->emit("${prefix}_${p.name}",index,ms*1000000)},
                            clientTiming={p,ns->emit("${prefix}_${p.name}",index,ns)})
                        val start=System.nanoTime()
                        await{done->f.documents.readPdf(f.activeId,asset,page,request,renderer){read->
                            (read as? DocumentResult.Value)?.value?.close();done(read is DocumentResult.Value)
                        }}
                        emit("${prefix}_TOTAL",index,System.nanoTime()-start)
                    }
                }}
            }
            System.gc();android.os.SystemClock.sleep(500)
            meta("FD_END",java.io.File("/proc/self/fd").list()!!.size.toLong())
            meta("NATIVE_END",android.os.Debug.getNativeHeapAllocatedSize())
            result.putString("result","PASS");finish(Activity.RESULT_OK,result)
        }catch(_:Throwable){result.putString("result","FAIL");result.putString("phase",phase);finish(Activity.RESULT_CANCELED,result)}
        finally{worker.shutdownNow();worker.awaitTermination(5,TimeUnit.SECONDS)}
    }
}
