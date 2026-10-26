package org.pag.prototype.product

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Size
import android.view.OrientationEventListener
import androidx.camera.core.CameraState
import androidx.lifecycle.LiveData
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class CameraDeviceEvent { BOUND, UNBOUND, PROXY_CLOSED, CAPTURE_TERMINATED }
internal enum class CameraTime { PREVIEW_READY, SHUTTER, JPEG_READY, JPEG_COPY, IMPORT_COMMIT, IMPORT_LIST }
internal fun cameraTime(phase: CameraTime, started: Long) {
    if (org.pag.prototype.BuildConfig.DEBUG) try {
        android.util.Log.i("PaGCameraTiming", "${phase.name} ms=${(System.nanoTime()-started)/1_000_000}")
    } catch (_: RuntimeException) { /* Diagnostics never affect the operation. */ }
}

/** Public CameraX memory API only. No OutputFileOptions, URI, MediaStore or file descriptor. */
internal class DocumentCameraController(private val context: Context, private val owner: LifecycleOwner,
    private val view: PreviewView, private val model: DocumentCameraModel,
    private val observe: (CameraDeviceEvent) -> Unit = {}) : AutoCloseable {
    private fun event(value: CameraDeviceEvent) { try {observe(value)} catch (_: Exception) {} }
    private val epoch=model.currentEpoch()
    private val main=context.mainExecutor
    private val started=System.nanoTime()
    private var closed=false
    private var provider: ProcessCameraProvider?=null
    private var preview: Preview?=null
    private var capture: ImageCapture?=null
    private var cameraState: LiveData<CameraState>?=null
    private val cameraObserver=Observer<CameraState> { state ->
        if (!closed && state.error != null) model.fail(epoch,CameraFailure.INITIALIZATION)
    }
    internal fun cameraStateForObservation() = cameraState
    private var previewReported=false
    private val orientation=object: OrientationEventListener(context) {
        override fun onOrientationChanged(degrees: Int) {
            if (!closed && degrees != ORIENTATION_UNKNOWN) capture?.targetRotation=ImageCapture.snapToSurfaceRotation(degrees)
        }
    }
    private val lifecycle=LifecycleEventObserver { _,event ->
        if (event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP || event==Lifecycle.Event.ON_DESTROY) {
            close();model.clear()
        }
    }
    private val stream=Observer<PreviewView.StreamState> { state ->
        if (state==PreviewView.StreamState.STREAMING && !closed && model.live(epoch)) {
            model.previewReady(epoch)
            if (!previewReported) {previewReported=true;cameraTime(CameraTime.PREVIEW_READY,started)}
        }
    }
    fun start() {
        if (!permitted()) { model.fail(epoch,CameraFailure.PERMISSION);return }
        model.attach(epoch,::close)
        owner.lifecycle.addObserver(lifecycle)
        view.previewStreamState.observe(owner,stream)
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!permitted()) { close();return@addListener }
            try {
                val p=future.get();provider=p
                if (!p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {model.fail(epoch,CameraFailure.NO_CAMERA);return@addListener}
                val imageCapture=ImageCapture.Builder().setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(90)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1600,1200),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                    .setTargetRotation(view.display?.rotation ?: android.view.Surface.ROTATION_0).build()
                val live=Preview.Builder().build()
                capture=imageCapture;preview=live
                live.setSurfaceProvider(view.surfaceProvider)
                val camera=p.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,live,imageCapture)
                cameraState=camera.cameraInfo.cameraState
                cameraState!!.observe(owner,cameraObserver)
                orientation.enable();event(CameraDeviceEvent.BOUND)
            } catch (_: Exception) { model.fail(epoch,CameraFailure.INITIALIZATION) }
        },main)
    }
    private fun permitted() = !closed && model.live(epoch) && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
    fun take() {
        if (!permitted()) {model.fail(epoch,CameraFailure.PERMISSION);return}
        val camera=capture ?: return
        val ticket=model.beginCapture() ?: return
        val time=System.nanoTime()
        try { camera.takePicture(main,object: ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureStarted() { if (ticket.valid()) cameraTime(CameraTime.SHUTTER,time) }
            override fun onCaptureSuccess(image: ImageProxy) {
                val copyTime=System.nanoTime()
                // The callback executor is the main executor, which is not shut down on navigation.
                // Even a late callback can therefore close its proxy; no queued image on a dying worker.
                var proxyClosed=false
                fun closeProxy() { if (!proxyClosed) {proxyClosed=true;image.close();event(CameraDeviceEvent.PROXY_CLOSED)} }
                try {
                    if (!permitted() || !ticket.valid()) return
                    val frame=object: CameraFrame {
                        override val jpeg get()=image.format==ImageFormat.JPEG
                        override val rotation get()=image.imageInfo.rotationDegrees
                        override val buffer get()=image.planes[0].buffer
                        override fun close()=closeProxy()
                    }
                    val photo=CameraPhoto.capture(frame,ticket,::cameraJpegRotation)
                    cameraTime(CameraTime.JPEG_COPY,copyTime);cameraTime(CameraTime.JPEG_READY,time)
                    model.captured(epoch,ticket,photo)
                } catch (failure: CameraPhotoRejected) { model.fail(epoch,failure.reason) }
                  catch (_: Exception) { model.fail(epoch,CameraFailure.INVALID_IMAGE) }
                finally { closeProxy();event(CameraDeviceEvent.CAPTURE_TERMINATED) }
            }
            override fun onError(exception: ImageCaptureException) { try { if (ticket.valid()) model.fail(epoch,CameraFailure.CAPTURE) } finally {event(CameraDeviceEvent.CAPTURE_TERMINATED)} }
        }) } catch (_: Exception) {model.fail(epoch,CameraFailure.CAPTURE)}
    }
    override fun close() {
        if (closed) return
        closed=true
        orientation.disable();owner.lifecycle.removeObserver(lifecycle)
        view.previewStreamState.removeObserver(stream)
        cameraState?.removeObserver(cameraObserver);cameraState=null
        provider?.unbind(*listOfNotNull(preview,capture).toTypedArray())
        if (provider != null) event(CameraDeviceEvent.UNBOUND)
        preview?.setSurfaceProvider(null)
        view.removeAllViews() // Drop the live TextureView/Surface; no frozen preview survives lock.
        capture=null;preview=null;provider=null
    }
}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun CameraCapturePane(documents: DocumentModel,
    observe: (CameraDeviceEvent) -> Unit = {}, controllerReady: (DocumentCameraController) -> Unit = {}) {
    val c=documents.camera
    c.error?.let { Text(cameraFailure(it)) }
    when(c.mode) {
        CameraMode.PREVIEW,CameraMode.CAPTURING -> {
            PreparationHeading("Foto aufnehmen")
            Text("Nur eine unkritische Szene aufnehmen. Das Foto wird erst nach deiner Bestätigung verschlüsselt gespeichert.")
            val context=LocalContext.current;val owner=LocalLifecycleOwner.current
            val epoch=c.currentEpoch()
            val view=remember(epoch) { PreviewView(context).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE } }
            val controller=remember(epoch,owner) {DocumentCameraController(context,owner,view,c,observe)}
            SideEffect {controllerReady(controller)}
            DisposableEffect(controller) {controller.start();onDispose {controller.close()}}
            AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(240.dp).originalSemantics {contentDescription="Live-Kameravorschau"})
            FormButton({controller.take()},enabled=c.ready && c.mode==CameraMode.PREVIEW) {
                Text(if(c.mode==CameraMode.CAPTURING) "Aufnahme läuft …" else if(!c.ready) "Kamera wird vorbereitet …" else "Foto aufnehmen")
            }
            FormOutlinedButton({documents.cancelCamera()}) {Text("Abbrechen")}
        }
        CameraMode.REVIEW -> {
            PreparationHeading("Foto aufgenommen")
            Text("JPEG · ${c.size} Bytes · Aufnahme nur im Arbeitsspeicher. Noch nicht gespeichert.")
            FormButton({c.accept()}) {Text("Aufnahme übernehmen")}
            FormOutlinedButton({documents.retakeCamera()}) {Text("Erneut aufnehmen")}
            FormOutlinedButton({documents.cancelCamera()}) {Text("Abbrechen")}
        }
        CameraMode.NAMING,CameraMode.SAVING -> Column(Modifier.imePadding()) {
            PreparationHeading("Kameraaufnahme speichern")
            FormTextField(documents.name,documents::changeName,label={Text("Dokumentname · 1–160 Zeichen")},
                enabled=!documents.busy,modifier=Modifier.fillMaxWidth(),keyboardOptions=productKeyboard(ProductTextKind.DESCRIPTION))
            Text("Format: JPEG. Die Aufnahme wird ausschließlich als verschlüsselte PaG-Kopie gespeichert.")
            FormButton({documents.saveCamera()},enabled=!documents.busy && org.pag.domain.DocumentRules.validName(documents.name)) {
                Text(if(documents.busy) "Import läuft …" else "Verschlüsselt speichern")
            }
            FormOutlinedButton({documents.retakeCamera()},enabled=!documents.busy) {Text("Erneut aufnehmen")}
            FormOutlinedButton({documents.cancelCamera()},enabled=!documents.busy) {Text("Abbrechen")}
        }
        CameraMode.CLOSED -> Unit
    }
}
internal fun cameraFailure(reason: CameraFailure)=when(reason) {
    CameraFailure.PERMISSION -> "Kameraberechtigung fehlt. Bitte den Zugriff erlauben und danach bewusst erneut ‚Foto aufnehmen‘ wählen. Bei dauerhafter Ablehnung kann die Berechtigung in den Android-App-Einstellungen erlaubt werden."
    CameraFailure.NO_CAMERA -> "Keine geeignete Kamera verfügbar. Der Dateiimport bleibt verfügbar."
    CameraFailure.INITIALIZATION -> "Die Kamera konnte nicht gestartet werden. Möglicherweise wird sie gerade von einer anderen App verwendet."
    CameraFailure.CAPTURE -> "Die Aufnahme ist fehlgeschlagen. Bitte die Kamera erneut bewusst öffnen."
    CameraFailure.TOO_LARGE -> "Die Aufnahme überschreitet 25 MiB und wurde verworfen."
    CameraFailure.INVALID_IMAGE -> "Die Kamera lieferte keine verwendbare JPEG-Aufnahme. Bitte erneut aufnehmen."
    CameraFailure.ORIENTATION -> "Die Bildausrichtung konnte nicht sicher übernommen werden. Bitte die Kamera erneut öffnen und aufnehmen."
}
