package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.media.ExifInterface
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import org.pag.domain.*
import org.pag.prototype.product.*
import java.io.File
import java.io.FilterInputStream
import java.util.concurrent.atomic.AtomicInteger

/** Only run against the separately installed org.pag.camera.p10dprobe package.
 * Real camera capture requires prior explicit user confirmation of an uncritical test scene.
 * No product DB, URI, image file or image data in instrumentation output.
 */
class P10dCameraProbe:Instrumentation() {
    private var font=1f
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);font=arguments?.getString("font")?.toFloat() ?: 1f;start()}
    private fun waitFor(work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+20_000
        while(SystemClock.elapsedRealtime()<end) {
            var value=false;runOnMainSync {value=work()};if(value)return
            SystemClock.sleep(100)
        }
        error("Synthetic timeout")
    }
    private fun clickText(label:String) {
        runOnMainSync {androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()}
        fun find(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
            if(node==null || !node.refresh())return false
            if(node.text?.toString()?.trim()==label) {
                var target:android.view.accessibility.AccessibilityNodeInfo=node
                while(!target.isClickable) target=target.parent ?: break
                if(!target.isClickable || !target.isEnabled)return false
                val bounds=android.graphics.Rect();target.getBoundsInScreen(bounds)
                check(bounds.height()>=48*targetContext.resources.displayMetrics.density)
                return target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
            repeat(node.childCount){if(find(node.getChild(it)))return true}
            return false
        }
        repeat(30) {
            if(find(uiAutomation.rootInActiveWindow))return
            SystemClock.sleep(100)
        }
        error("Synthetic button not available")
    }
    override fun onStart() {
        var phase="isolated-package"
        val result=Bundle()
        var activeDocument:DocumentModel?=null
        var activeActivity:MainActivity?=null
        try {
            check(targetContext.packageName=="org.pag.camera.p10dprobe")
            val activity=startActivitySync(Intent().setClassName(targetContext.packageName,"org.pag.prototype.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activeActivity=activity
            check(activity.checkSelfPermission(android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED)
            val closed=AtomicInteger();val released=AtomicInteger();val terminated=AtomicInteger()
            var writes=0;var requests=0;var saved:((DocumentResult<Unit>)->Unit)?=null
            val repo=object:DocumentRepository {
                override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit)=publish(DocumentResult.Value(DocumentSummary(0)))
                override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit)=publish(DocumentResult.Value(emptyList()))
                override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit)=error("No product deletion")
                override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit) {
                    check(format==DocumentFormat.JPEG);writes++
                    var length=0L
                    source.open().use { raw ->
                        val counted=object:FilterInputStream(raw) {
                            override fun read():Int=super.read().also {if(it>=0)length++}
                            override fun read(b:ByteArray,off:Int,len:Int):Int=`in`.read(b,off,len).also {if(it>0)length+=it}
                        }
                        val exif=ExifInterface(counted)
                        check(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)==null)
                        check(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)==null)
                        val buffer=ByteArray(32768)
                        try {while(counted.read(buffer)>=0)buffer.fill(0)} finally {buffer.fill(0)}
                    }
                    check(length in 5..DocumentRules.MAX_DOCUMENT_BYTES)
                    check(runCatching {source.open()}.isFailure)
                    saved=publish
                }
            }
            var controller:DocumentCameraController?=null
            val document=DocumentModel({repo},{null},{SessionSnapshot(SessionState.READY,1)},
                {Action("synthetic","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)},{},{},{})
            activeDocument=document
            phase="jpeg-metadata-validation"
            val bitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888)
            val bytes=java.io.ByteArrayOutputStream().use {out->bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,out);out.toByteArray()}
            try {
                phase="jpeg-fixture-rotation"
                check(cameraJpegRotation(bytes)==0)
                phase="jpeg-bad-header"
                check(runCatching {cameraJpegRotation(byteArrayOf(-1,-40,-1,-39))}.isFailure)
                // SOF bounds corruption is rejected without allocating a full decoded bitmap.
                phase="jpeg-large-bounds"
                var i=2
                while(i+8<bytes.size) {
                    if(bytes[i]==0xff.toByte() && bytes[i+1]==0xc0.toByte()) {
                        bytes[i+5]=0x7f;bytes[i+6]=0xff.toByte();bytes[i+7]=0x7f;bytes[i+8]=0xff.toByte();break
                    }
                    i++
                }
                check(runCatching {cameraJpegRotation(bytes)}.isFailure)
            } finally {bytes.fill(0);bitmap.recycle()}
            phase="permission-denial"
            runOnMainSync {
                document.enter()
                document.bindCameraPermission(object:CameraPermission {override fun granted()=false;override fun request(){requests++}})
                document.takePhoto();check(requests==1);check(document.camera.mode==CameraMode.CLOSED)
                document.bindCameraPermission(object:CameraPermission {override fun granted()=true;override fun request(){error("Granted")}})
                activity.setContent {
                    val base=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(base.density,font)) {
                        MaterialTheme {ProductInputScope(Unit) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                CameraCapturePane(document,{e->when(e) {
                                    CameraDeviceEvent.PROXY_CLOSED->closed.incrementAndGet()
                                    CameraDeviceEvent.UNBOUND->released.incrementAndGet()
                                    CameraDeviceEvent.CAPTURE_TERMINATED->terminated.incrementAndGet()
                                    else->Unit
                                }},{controller=it})
                            }
                        }}
                    }
                }
                document.takePhoto()
            }
            phase="preview"
            val start=SystemClock.elapsedRealtime()
            waitFor {document.camera.ready}
            result.putLong("preview_ms",SystemClock.elapsedRealtime()-start)
            var state:LiveData<CameraState>?=null
            val stateObserver=Observer<CameraState> {}
            runOnMainSync {state=controller!!.cameraStateForObservation()!!;state!!.observe(activity,stateObserver)}
            phase="jpeg-capture"
            val captureStart=SystemClock.elapsedRealtime()
            phase="capture-button"
            clickText("Foto aufnehmen")
            phase="jpeg-await"
            runOnMainSync {controller!!.take()}
            waitFor {document.camera.mode==CameraMode.REVIEW || document.camera.error!=null}
            phase="jpeg-review"
            runOnMainSync {check(document.camera.mode==CameraMode.REVIEW);check(closed.get()==1);check(released.get()==1);check(writes==0)}
            result.putLong("capture_ms",SystemClock.elapsedRealtime()-captureStart)
            phase="explicit-confirmation-one-shot"
            runOnMainSync {
                document.camera.accept();document.changeName("synthetic camera");check(writes==0)
                document.saveCamera();check(writes==1);check(document.camera.mode==CameraMode.SAVING)
                saved!!(DocumentResult.Value(Unit));check(document.camera.mode==CameraMode.CLOSED)
            }
            phase="private-file-inventory"
            for(root in listOf(activity.filesDir,activity.cacheDir,activity.noBackupFilesDir)) {
                for(file in root.walkTopDown().filter {it.isFile}) {
                    file.inputStream().use {check(!(it.read()==255 && it.read()==216 && it.read()==255))}
                }
            }
            phase="preview-lock-release"
            runOnMainSync {document.takePhoto()};waitFor {document.camera.ready}
            runOnMainSync {document.clear()};waitFor {released.get()==2}
            phase="late-capture-after-lock"
            runOnMainSync {document.enter();document.takePhoto()};waitFor {document.camera.ready}
            runOnMainSync {controller!!.take();document.clear()}
            phase="late-await-terminated"
            waitFor {terminated.get()>=2}
            phase="late-await-device-closed"
            waitFor {state!!.value?.type==CameraState.Type.CLOSED}
            runOnMainSync {state!!.removeObserver(stateObserver)}
            runOnMainSync {check(document.camera.mode==CameraMode.CLOSED);check(writes==1);activity.finish()}
            result.putString("result","PASS");finish(Activity.RESULT_OK,result)
        } catch (failure:Throwable) {
            if(failure is CameraPhotoRejected)result.putString("reason",failure.reason.name)
            runOnMainSync {
                activeDocument?.camera?.error?.let {result.putString("reason",it.name)}
                activeDocument?.clear();activeActivity?.finish()
            }
            result.putString("result","FAIL");result.putString("phase",phase);finish(Activity.RESULT_CANCELED,result)
        }
    }
}
