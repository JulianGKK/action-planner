package org.pag.prototype.product

import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.system.Os
import android.system.OsConstants
import org.pag.domain.*
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Captures application context only. No file paths, data keys or persisted viewer state. */
internal class AndroidDocumentPdfRenderer(context:Context,private val diagnostic:(Int)->Unit={},private val timing:(DocumentPdfPhase,Long)->Unit=::pdfTiming):DocumentPdfRenderer {
    private val app=context.applicationContext
    override fun create(size:Long):DocumentPdfInput {
        if(size !in 1..DocumentRules.MAX_DOCUMENT_BYTES)throw DocumentPdfUnsupported()
        val fd=try {Os.memfd_create("pag-pdf",OsConstants.MFD_CLOEXEC)}catch(_:Exception){throw DocumentPdfUnsupported(DocumentFailure.PDF_MEMORY_UNAVAILABLE)}
        try {Os.ftruncate(fd,size)}catch(_:Exception){Os.close(fd);throw DocumentPdfUnsupported(DocumentFailure.PDF_MEMORY_UNAVAILABLE)}
        return object:DocumentPdfInput {
            private var input:FileDescriptor?=fd
            private var used=0L;private var fill=0L
            override fun write(bytes:ByteArray,offset:Int,count:Int) {
                val started=System.nanoTime()
                require(offset>=0 && count>=0 && offset<=bytes.size-count && used<=size-count)
                var n=0
                try {while(n<count) {val wrote=Os.pwrite(input!!,bytes,offset+n,count-n,used+n);check(wrote>0);n+=wrote}}
                catch(_:Exception){throw DocumentPdfUnsupported(DocumentFailure.PDF_MEMORY_UNAVAILABLE)}
                used+=count;fill+=System.nanoTime()-started
            }
            override fun render(page:Int,check:()->Unit):DocumentPdfPage {
                if(used!=size || page !in 0 until 10000)throw DocumentPdfUnsupported()
                check();timing(DocumentPdfPhase.MEMFD_FILL,fill/1_000_000)
                return renderIsolated(input ?: throw DocumentPdfUnsupported(),page,check)
            }
            override fun close(){val owned=input;input=null;owned?.let{Os.close(it)}}
        }
    }
    private fun renderIsolated(input:FileDescriptor,page:Int,check:()->Unit):DocumentPdfPage {
        val callbacks=HandlerThread("pdf-result")
        val connected=ArrayBlockingQueue<IBinder>(1);val answers=ArrayBlockingQueue<Bundle>(1)
        val failed=java.util.concurrent.atomic.AtomicBoolean(false)
        val accepting=java.util.concurrent.atomic.AtomicBoolean(true)
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder){connected.offer(binder)}
            override fun onServiceDisconnected(name:ComponentName){failed.set(true)}
            override fun onNullBinding(name:ComponentName){failed.set(true)}
            override fun onBindingDied(name:ComponentName){failed.set(true)}
        }
        var bound=false;var output:FileDescriptor?=null;var bitmap:Bitmap?=null;var pixels:ByteArray?=null;var reply:Bundle?=null
        val started=System.nanoTime()
        val deadline=SystemClock.elapsedRealtime()+15000
        fun <T> await(queue:ArrayBlockingQueue<T>):T {
            while(true){check();if(failed.get() || SystemClock.elapsedRealtime()>=deadline)throw DocumentPdfUnsupported(DocumentFailure.PDF_RENDERER_ERROR)
                queue.poll(50,TimeUnit.MILLISECONDS)?.let{return it}}
        }
        try {
            callbacks.start()
            val receiver=Messenger(Handler(callbacks.looper){message ->
                val bundle=message.data
                try {
                    val fields=setOf("ok","phase","failure","init","open","render","width","height","pages")
                    @Suppress("DEPRECATION")
                    val validTypes= !bundle.hasFileDescriptors() && fields.containsAll(bundle.keySet()) && bundle.keySet().all {key ->
                        when(key) {
                            "ok" -> bundle.get(key) is Boolean
                            "phase","width","height","pages" -> bundle.get(key) is Int
                            "init","open","render" -> bundle.get(key) is Long
                            "failure" -> (bundle.get(key) as? String)?.length?.let{it<=16}==true
                            else -> false
                        }
                    }
                    if(!accepting.get() || !validTypes) {
                        bundle.clear();failed.set(true)
                    } else if(!answers.offer(bundle))bundle.clear()
                } catch(_:Exception){bundle.clear();failed.set(true)}
                true
            })
            diagnostic(1)
            try {output=Os.memfd_create("pag-pixels",OsConstants.MFD_CLOEXEC);Os.ftruncate(output,PdfRenderLimits.BYTES)}
            catch(_:Exception){throw DocumentPdfUnsupported(DocumentFailure.PDF_MEMORY_UNAVAILABLE)}
            // Unique isolated instance per page; no reusable parser or plaintext cache.
            diagnostic(2);bound=app.bindIsolatedService(Intent(app,PdfRenderService::class.java),Context.BIND_AUTO_CREATE,
                "pdf_"+java.util.UUID.randomUUID().toString().replace("-",""),app.mainExecutor,connection)
            if(!bound)throw DocumentPdfUnsupported(DocumentFailure.PDF_RENDERER_ERROR)
            diagnostic(3);val remote=Messenger(await(connected));check()
            ParcelFileDescriptor.dup(input).use {source -> ParcelFileDescriptor.dup(output).use {target ->
                remote.send(Message.obtain().apply{arg1=page;replyTo=receiver;data=Bundle().apply{putParcelable("input",source);putParcelable("output",target)}})
            }}
            diagnostic(4);val result=await(answers);reply=result;diagnostic(100+result.getInt("phase"));check()
            if(!result.getBoolean("ok")) {
                val reason=when(result.getString("failure")) {
                    "password" -> DocumentFailure.PDF_PASSWORD_PROTECTED
                    "page" -> DocumentFailure.PDF_PAGE_TOO_LARGE
                    "unsupported" -> DocumentFailure.PDF_UNSUPPORTED
                    else -> DocumentFailure.PDF_RENDERER_ERROR
                }
                throw DocumentPdfUnsupported(reason)
            }
            for((key,phase) in listOf("init" to DocumentPdfPhase.RENDERER_INIT,"open" to DocumentPdfPhase.PAGE_OPEN,"render" to DocumentPdfPhase.PAGE_RENDER))timing(phase,result.getLong(key))
            diagnostic(5);val w=result.getInt("width");val h=result.getInt("height");val count=result.getInt("pages")
            PdfRenderLimits.validate(w,h)
            if(count !in 1..10000 || page !in 0 until count || Os.fstat(output).st_size!=PdfRenderLimits.BYTES)throw DocumentPdfUnsupported()
            diagnostic(6);pixels=ByteArray(Math.multiplyExact(Math.multiplyExact(w,h),4))
            var position=0
            while(position<pixels.size){check();val n=Os.pread(output,pixels,position,minOf(32768,pixels.size-position),position.toLong());if(n<=0)throw DocumentPdfUnsupported();position+=n}
            diagnostic(7);check();bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            if(bitmap.allocationByteCount.toLong()>PdfRenderLimits.BYTES)throw DocumentPdfUnsupported()
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));check()
            timing(DocumentPdfPhase.IPC_AND_BITMAP,(System.nanoTime()-started)/1_000_000)
            return DocumentPdfPage(page,count,BitmapDocumentImage(bitmap)).also{bitmap=null}
        }catch(failure:org.pag.data.prototype.b2.StorageAccessRevoked){throw failure}
         catch(failure:DocumentPdfUnsupported){check();throw failure}
         catch(_:Exception){check();throw DocumentPdfUnsupported(DocumentFailure.PDF_RENDERER_ERROR)}
         catch(_:OutOfMemoryError){throw DocumentPdfUnsupported(DocumentFailure.PDF_MEMORY_UNAVAILABLE)}
        finally {
            val cleaning=System.nanoTime()
            accepting.set(false);reply?.clear();answers.poll()?.clear()
            // Cleanup is independent: even a Binder teardown failure cannot skip local FD release.
            try {if(bound)app.unbindService(connection)} finally {
                try {pixels?.fill(0);bitmap?.recycle();output?.let{Os.close(it)}} finally {
                    callbacks.quitSafely();callbacks.join(1000)
                    timing(DocumentPdfPhase.CLEANUP,(System.nanoTime()-cleaning)/1_000_000)
                }
            }
        }
    }
}
