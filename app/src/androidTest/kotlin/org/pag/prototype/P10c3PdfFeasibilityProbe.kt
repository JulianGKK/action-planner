package org.pag.prototype

import android.app.Instrumentation
import android.app.Activity
import android.content.*
import android.graphics.pdf.PdfDocument
import android.graphics.Color
import android.os.*
import android.system.Os
import android.system.OsConstants
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Synthetic phase-A probe. Never opens the product database or writes PDF files. */
class P10c3PdfFeasibilityProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle();var phase="isolation"
        val callbacks=HandlerThread("pdf-probe-replies").apply { start() }
        var bound=false
        val connected=ArrayBlockingQueue<IBinder>(1)
        val replies=ArrayBlockingQueue<Bundle>(1)
        val receiver=Messenger(Handler(callbacks.looper) { replies.offer(it.data);true })
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {connected.offer(binder)}
            override fun onServiceDisconnected(name:ComponentName) { }
        }
        try {
            check(targetContext.packageName=="org.pag.pdf.p10c3probe")
            val roots=listOf(targetContext.filesDir,targetContext.cacheDir,targetContext.noBackupFilesDir)
            val before=roots.flatMap {it.walkTopDown().filter {f->f.isFile}.map {f->f.canonicalPath}.toList()}.toSet()
            phase="bind-isolated"
            bound=targetContext.bindService(Intent().setClassName(targetContext,"org.pag.prototype.pdf.PdfFeasibilityService"),connection,Context.BIND_AUTO_CREATE)
            check(bound)
            val binder=connected.poll(10,TimeUnit.SECONDS) ?: error("bind")
            val death=java.util.concurrent.CountDownLatch(1);binder.linkToDeath({death.countDown()},0)
            val service=Messenger(binder)
            var serviceFds:Int?=null
            val fdBaseline=java.io.File("/proc/self/fd").list()!!.size
            repeat(12) { iteration ->
                phase="memfd-create"
                val fd=Os.memfd_create("pag-pdf-probe",OsConstants.MFD_CLOEXEC)
                try {
                    // Android's PDF generator writes directly to the anonymous descriptor.
                    val duplicate=ParcelFileDescriptor.dup(fd)
                    ParcelFileDescriptor.AutoCloseOutputStream(duplicate).use { output ->
                        if(iteration==10)output.write(byteArrayOf(1,2,3)) else { val document=PdfDocument();try {
                            val page=document.startPage(PdfDocument.PageInfo.Builder(200,200,1).create())
                            page.canvas.drawColor(Color.RED);document.finishPage(page);document.writeTo(output)
                        } finally {document.close()} }
                    }
                    phase="seek"
                    check(Os.fstat(fd).st_size>0)
                    Os.ftruncate(fd,Os.fstat(fd).st_size)
                    val prefix=ByteArray(3)
                    try {check(Os.pread(fd,prefix,0,3,0)==3);check(Os.pwrite(fd,prefix,0,3,0)==3)}
                    finally {prefix.fill(0)}
                    check(Os.lseek(fd,0,OsConstants.SEEK_SET)==0L)
                    phase="render-isolated"
                    ParcelFileDescriptor.dup(fd).use { transport ->
                        val message=Message.obtain().apply {arg1=if(iteration==11)5 else 0;replyTo=receiver;data=Bundle().apply {putParcelable("input",transport)}}
                        service.send(message)
                        val answer=replies.poll(15,TimeUnit.SECONDS) ?: error("render timeout")
                        phase="uid-distinct";check(answer.getInt("uid")!=Process.myUid())
                        phase="uid-isolated";check(answer.getInt("uid")%100000>=99000)
                        phase="no-internet";check(!answer.getBoolean("internet"))
                        phase="service-fd-count";val count=answer.getInt("fds");result.putInt("fds",count);result.putInt("baseline",serviceFds ?: -1);result.putInt("iteration",iteration);check(count>0)
                        if(serviceFds==null)serviceFds=count else check(count<=serviceFds!!+2)
                        phase="render-result"
                        if(iteration>=10)check(!answer.getBoolean("ok")) else {
                            check(answer.getBoolean("ok"));check(answer.getInt("pages")==1);check(answer.getBoolean("ink"))
                        }
                    }
                } finally { Os.close(fd) }
            }
            phase="cancellation-process-cleanup"
            targetContext.unbindService(connection);bound=false
            check(death.await(10,TimeUnit.SECONDS))
            for(command in listOf(91,92)) {
                phase=if(command==91)"isolated-crash" else "unbind-busy-renderer"
                bound=targetContext.bindService(Intent().setClassName(targetContext,"org.pag.prototype.pdf.PdfFeasibilityService"),connection,Context.BIND_AUTO_CREATE)
                check(bound)
                val fresh=connected.poll(10,TimeUnit.SECONDS) ?: error("rebind")
                val ended=java.util.concurrent.CountDownLatch(1);fresh.linkToDeath({ended.countDown()},0)
                Messenger(fresh).send(Message.obtain().apply{what=command})
                if(command==92)SystemClock.sleep(200)
                if(command==91)check(ended.await(10,TimeUnit.SECONDS))
                targetContext.unbindService(connection);bound=false
                check(ended.await(10,TimeUnit.SECONDS))
            }
            phase="fd-cleanup"
            check(java.io.File("/proc/self/fd").list()!!.size<=fdBaseline+2)
            phase="no-persistent-pdf"
            val after=roots.flatMap {it.walkTopDown().filter {f->f.isFile}.map {f->f.canonicalPath}.toList()}.toSet()
            check(after==before)
            result.putString("result","PASS");finish(Activity.RESULT_OK,result)
        } catch (_:Throwable) {result.putString("result","FAIL");result.putString("phase",phase);finish(Activity.RESULT_CANCELED,result)}
        finally {if(bound)targetContext.unbindService(connection);callbacks.quitSafely();callbacks.join(3000)}
    }
}
