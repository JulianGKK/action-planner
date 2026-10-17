package org.pag.prototype.product

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.system.Os
import java.nio.ByteBuffer

/** One request per isolated bind. No storage, key, URI or product runtime access. */
class PdfRenderService:Service() {
    private val thread=HandlerThread("pdf-render")
    private var accepted=false
    override fun onCreate(){super.onCreate();check(Process.isIsolated());thread.start()}
    override fun onBind(intent:Intent):IBinder=Messenger(Handler(thread.looper) {message ->
        val response=Message.obtain();val data=Bundle()
        var input:ParcelFileDescriptor?=null;var output:ParcelFileDescriptor?=null
        var bitmap:Bitmap?=null;var pixels:ByteArray?=null;var phase=1
        try {
            check(!accepted);accepted=true
            message.data.classLoader=ParcelFileDescriptor::class.java.classLoader
            @Suppress("DEPRECATION")
            input=message.data.getParcelable("input")
            @Suppress("DEPRECATION")
            output=message.data.getParcelable("output")
            message.data.clear() // Releases lazy Parcel FD copies, not just decoded PFDs.
            check(input!=null && output!=null)
            check(Os.fstat(input.fileDescriptor).st_size in 1..org.pag.domain.DocumentRules.MAX_DOCUMENT_BYTES)
            check(Os.fstat(output.fileDescriptor).st_size==PdfRenderLimits.BYTES)
            val initializing=SystemClock.elapsedRealtime()
            phase=2;PdfRenderer(input).use {renderer ->
                data.putLong("init",SystemClock.elapsedRealtime()-initializing)
                check(renderer.pageCount in 1..10000 && message.arg1 in 0 until renderer.pageCount)
                val opening=SystemClock.elapsedRealtime()
                phase=3;renderer.openPage(message.arg1).use {page ->
                    data.putLong("open",SystemClock.elapsedRealtime()-opening)
                    val (w,h)=PdfRenderLimits.dimensions(page.width,page.height)
                    phase=4;bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    check(bitmap!!.allocationByteCount.toLong()<=PdfRenderLimits.BYTES)
                    bitmap!!.eraseColor(Color.WHITE)
                    val rendering=SystemClock.elapsedRealtime()
                    phase=5;page.render(bitmap!!,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    data.putLong("render",SystemClock.elapsedRealtime()-rendering)
                    phase=6;pixels=ByteArray(Math.multiplyExact(Math.multiplyExact(w,h),4))
                    bitmap!!.copyPixelsToBuffer(ByteBuffer.wrap(pixels!!))
                    phase=7;var position=0
                    while(position<pixels!!.size) {
                        val n=Os.pwrite(output.fileDescriptor,pixels!!,position,minOf(32768,pixels!!.size-position),position.toLong())
                        check(n>0);position+=n
                    }
                    data.putInt("width",w);data.putInt("height",h);data.putInt("pages",renderer.pageCount)
                }
            }
            data.putBoolean("ok",true)
        }catch(_:org.pag.domain.DocumentPdfUnsupported){data.putBoolean("ok",false);data.putString("failure","page")}
         catch(_:SecurityException){data.putBoolean("ok",false);data.putString("failure",if(phase==2)"password" else "renderer")}
         catch(_:Exception){data.putBoolean("ok",false);data.putString("failure",if(phase==2)"unsupported" else "renderer")}
         catch(_:OutOfMemoryError){data.putBoolean("ok",false);data.putString("failure","renderer")}
        finally {pixels?.fill(0);bitmap?.recycle();input?.close();output?.close();message.data.clear()}
        data.putInt("phase",phase);response.data=data
        try {message.replyTo?.send(response)}catch(_:RemoteException){}
        true
    }).binder
    override fun onDestroy(){thread.quitSafely();super.onDestroy();Process.killProcess(Process.myPid())}
}
