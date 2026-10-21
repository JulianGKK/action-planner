package org.pag.prototype

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import org.pag.prototype.product.DocumentPicker
import org.pag.prototype.product.PendingDocumentPicker
import org.pag.domain.DocumentSource
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicReference
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.pag.data.product.ProductRuntime
import org.pag.domain.ProductStorageState
import org.pag.domain.SessionState
import org.pag.prototype.demo.DemoState
import org.pag.prototype.demo.DemoScreen
import org.pag.prototype.product.ProductScreen
import org.pag.prototype.product.ProductViewModel
import java.util.concurrent.Executors

/** Product composition root. PrototypeActivity remains unregistered historical evidence. */
class MainActivity : ComponentActivity() {
    private lateinit var runtime: ProductRuntime
    private var p77Harness: org.pag.data.product.P77ProductRuntimeHarness? = null
    private lateinit var model: ProductViewModel
    private val worker by lazy { Executors.newSingleThreadExecutor() }
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var documentSession: org.pag.domain.Subscription? = null
    private var demo: DemoState? = null
    private var resumed = false
    private val pendingDocument = PendingDocumentPicker<Uri>()
    private val openDocumentContract = ActivityResultContracts.OpenDocument()
    private var pickerRequest: Int? = null
    private var nextPickerRequest = 20_000
    // Direct result dispatch avoids ActivityResultRegistry's saved pending-result Bundle.
    // No callback registration or source survives Activity recreation.
    @Deprecated("Uses direct OpenDocument dispatch to keep pending URI results out of SavedState")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode in 20_001..30_000) {
            if (requestCode == pickerRequest) {
                pickerRequest=null
                if (::runtime.isInitialized) {
                    pendingDocument.returned(openDocumentContract.parseResult(resultCode,data)?.takeIf { it.scheme == "content" })
                    val current=runtime.session.snapshot()
                    if(current.state==SessionState.READY) pendingDocument.ready(current.generation)
                }
                else pendingDocument.clear()
            }
            return
        }
        super.onActivityResult(requestCode,resultCode,data)
    }
    private fun discardOnExternalLeave() {
        if (::runtime.isInitialized) pendingDocument.leaving(runtime.session.snapshot().state==SessionState.AUTHENTICATING)
    }
    private val expiry = object : Runnable {
        override fun run() {
            if (!resumed) return
            runtime.session.checkExpiry()
            model.checkSensitiveExpiry()
            handler.postDelayed(this, 250)
        }
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { pendingDocument.clear(); runtime.session.foreground(false) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        launchAppMode(BuildConfig.PAG_DEMO_ONLY,
            demo = {
                val state = DemoState()
                if (BuildConfig.PAG_P76_PREVIEW) state.begin(org.pag.prototype.demo.DemoScenario.ACTIVE)
                demo = state
                setContent { DemoScreen(state) }
            },
            product = { initializeProduct() })
    }
    private fun initializeProduct() {
        val foregroundCheck = {
            resumed && window.decorView.hasWindowFocus() && !getSystemService(KeyguardManager::class.java).isDeviceLocked
        }
        runtime = if (BuildConfig.PAG_P77_RUNTIME_E2E) {
            org.pag.data.product.P77ProductRuntimeHarness.create(this, worker, mainExecutor, foregroundCheck)
                .also { p77Harness = it }.runtime
        } else ProductRuntime(this, worker, mainExecutor, foreground = foregroundCheck)
        val state = runtime.inventory.state()
        if (state == ProductStorageState.PARTIAL_OR_INVALID) runtime.session.storageFailure()
        model = ProductViewModel(runtime.session, runtime.actions, state, runtime.persons, runtime.leadership, runtime.operations, runtime.tasks, runtime.protocol, runtime.material, runtime.expenses, runtime.memoryReports, runtime::start)
        model.bindWorkspace(runtime.workspace)
        model.bindActiveCore(runtime.activeCore) { work -> mainExecutor.execute(work) }
        model.bindDocuments(runtime.documents, object : DocumentPicker {
            override fun launch(actionId: String, generation: Long) {
                val current=runtime.session.snapshot()
                if(current.state!=SessionState.READY || current.generation!=generation || model.selected?.id!=actionId) return
                if (pickerRequest != null || nextPickerRequest >= 30_000) return
                val request=++nextPickerRequest
                pickerRequest=request
                pendingDocument.begin(actionId,generation)
                // The picker is never treated as a continuation of this READY session.
                runtime.session.lock()
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(openDocumentContract.createIntent(this@MainActivity,
                        arrayOf("application/pdf","image/jpeg","image/png","text/plain")),request)
                }
                catch (_: android.content.ActivityNotFoundException) { pickerRequest=null; pendingDocument.returned(null) }
            }
            override fun unavailable(actionId: String) = pendingDocument.unavailable(actionId)
            override fun available(actionId: String, generation: Long) = pendingDocument.available(actionId,generation)
            override fun consume(actionId: String, generation: Long): DocumentSource? {
                val current=runtime.session.snapshot()
                if(current.state!=SessionState.READY || current.generation!=generation || model.selected?.id!=actionId) return null
                val uri=pendingDocument.consume(actionId,generation) ?: return null
                val once=AtomicReference<Uri?>(uri)
                return DocumentSource {
                    val selected=once.getAndSet(null) ?: throw FileNotFoundException()
                    val ready=runtime.session.snapshot()
                    if(ready.state!=SessionState.READY || ready.generation!=generation) throw FileNotFoundException()
                    contentResolver.openInputStream(selected) ?: throw FileNotFoundException()
                }
            }
            override fun actionSelected(actionId: String) { pendingDocument.actionSelected(actionId) }
            override fun cancel() { pickerRequest=null; pendingDocument.clear() }
        })
        model.documents.bindPdfRenderer(org.pag.prototype.product.AndroidDocumentPdfRenderer(this))
        model.documents.bindCameraPermission(object : org.pag.prototype.product.CameraPermission {
            override fun granted() = checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
            override fun request() {
                @Suppress("DEPRECATION")
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA),31_001)
            }
        })
        documentSession=runtime.session.observe { state ->
            when(state.state) {
                SessionState.READY -> pendingDocument.ready(state.generation)
                SessionState.LOCKED -> pendingDocument.locked()
                SessionState.BLOCKED -> pendingDocument.clear()
                else -> Unit
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
        setContent { ProductScreen(model, testAuthorityBanner = BuildConfig.PAG_P77_RUNTIME_E2E) }
    }
    override fun onResume() {
        super.onResume(); resumed = true
        if (!::runtime.isInitialized) return
        runtime.session.foreground(!getSystemService(KeyguardManager::class.java).isDeviceLocked)
        if (runtime.session.snapshot().state == SessionState.LOCKED) {
            val state = runtime.inventory.state()
            model.updateBootstrap(state)
            if (state == ProductStorageState.PARTIAL_OR_INVALID) runtime.session.storageFailure()
        }
        runtime.authenticationForegroundAvailable()
        handler.removeCallbacks(expiry); handler.post(expiry)
    }
    override fun onPause() {
        if (::model.isInitialized) { model.documents.clearViewers();model.documents.camera.clear() }
        discardOnExternalLeave()
        resumed = false
        if (::runtime.isInitialized) {
            handler.removeCallbacks(expiry)
            if (runtime.session.snapshot().state != SessionState.AUTHENTICATING) runtime.session.foreground(false)
        }
        super.onPause()
    }
    override fun onStop() { if (::runtime.isInitialized) runtime.session.foreground(false); demo?.end(); super.onStop() }
    override fun onUserLeaveHint() { discardOnExternalLeave(); if (::runtime.isInitialized) runtime.session.foreground(false); super.onUserLeaveHint() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::runtime.isInitialized) return
        if (!hasFocus && runtime.session.snapshot().state != SessionState.AUTHENTICATING) runtime.session.foreground(false)
        if (hasFocus && resumed) {
            runtime.session.foreground(!getSystemService(KeyguardManager::class.java).isDeviceLocked)
            runtime.authenticationForegroundAvailable()
        }
    }
    override fun onUserInteraction() { super.onUserInteraction(); if (::model.isInitialized && resumed) model.interaction() }
    override fun onDestroy() {
        documentSession?.cancel(); documentSession=null
        pendingDocument.clear()
        if (::model.isInitialized) { model.documents.clearViewers();model.documents.camera.clear() }
        demo?.end(); demo = null
        if (::runtime.isInitialized) {
            handler.removeCallbacks(expiry)
            p77Harness?.close() ?: runtime.close()
            p77Harness=null; worker.shutdownNow(); unregisterReceiver(screenOff)
        }
        super.onDestroy()
    }
}
