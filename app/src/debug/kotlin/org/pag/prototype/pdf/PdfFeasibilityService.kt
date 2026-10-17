package org.pag.prototype.pdf

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.*

/** Phase A only: synthetic input, no product integration or storage access. */
class PdfFeasibilityService : Service() {
    private val thread = HandlerThread("pdf-feasibility")
    override fun onCreate() { super.onCreate(); thread.start() }
    override fun onBind(intent: Intent): IBinder = Messenger(Handler(thread.looper) { message ->
        // Debug-only lifecycle fault injection, registered solely in the isolated probe APK.
        val fault=intent.getIntExtra("fault",message.what)
        if(fault==91)throw IllegalStateException("Synthetic renderer crash")
        if(fault==92){SystemClock.sleep(60000);return@Handler true}
        val reply = Message.obtain().apply { what = 1 }
        val result = Bundle()
        var fd: ParcelFileDescriptor? = null
        try {
            message.data.classLoader = ParcelFileDescriptor::class.java.classLoader
            @Suppress("DEPRECATION")
            fd = message.data.getParcelable("input")
            message.data.clear()
            check(fd != null)
            if(fault in 93..95) {
                reply.data=Bundle().apply {
                    when(fault) {
                        93 -> putParcelable("width",fd)
                        94 -> putString("unexpected","synthetic")
                        95 -> putString("width","synthetic")
                    }
                }
                message.replyTo?.send(reply)
                return@Handler true
            }
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            try {
                // Constructor owns its PFD on success; caller closes it on failure as well.
                PdfRenderer(fd).use { renderer ->
                    result.putInt("pages", renderer.pageCount)
                    renderer.openPage(message.arg1).use { page ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        result.putBoolean("ink", (0 until 128).any { x ->
                            (0 until 128).any { y -> bitmap.getPixel(x,y) != Color.WHITE }
                        })
                    }
                }
                result.putBoolean("ok", true)
            } finally { bitmap.recycle() }
        } catch (_: Exception) { result.putBoolean("ok", false) }
        finally { fd?.close() }
        result.putInt("fds", java.io.File("/proc/self/fd").list()?.size ?: -1)
        result.putInt("uid", Process.myUid())
        result.putBoolean("internet", checkSelfPermission("android.permission.INTERNET") == android.content.pm.PackageManager.PERMISSION_GRANTED)
        reply.data = result
        try { message.replyTo?.send(reply) } catch (_: RemoteException) { }
        true
    }).binder
    override fun onDestroy() { thread.quitSafely(); super.onDestroy(); if(Process.isIsolated())Process.killProcess(Process.myPid()) }
}
