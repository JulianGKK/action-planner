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
import java.util.ServiceLoader

/** P77-only full-workspace feasibility root, mechanically mirrored from MainActivity at §86a.
 * Deliberate differences: isolated-package guard and no bindActiveCore. Lifecycle, session,
 * repository, document and Compose wiring are otherwise identical. Not a product fallback.
 */
class P86FullWorkspaceActivity : ComponentActivity() {
    private lateinit var runtime: ProductRuntime
    private var runtimeClose: (() -> Unit)? = null
    // Opaque test diagnostics only; the main source set has no reference to P77 classes.
    private var p77Harness: Any? = null
    private var testAuthorityBanner = false
    private var contentFrames: org.pag.prototype.product.CompactContentFrameObserver? = null
    private lateinit var model: ProductViewModel
    // Diagnostic IDs only; no payload retained and never used for authority or retirement.
    var p86Committed = ""; private set
    // Isolated diagnostic invalidation only: no session, lifetime or release authority.
    private val p110Pulse=androidx.compose.runtime.mutableIntStateOf(0)
    var p110Composed=0; private set
    var p110RowRenders=0; private set
    fun p110Invalidate(){p110Pulse.intValue++}
    fun p86StateToken() = listOf(System.identityHashCode(model.workspace),
        System.identityHashCode(model.tasks.history), model.tasks.form, model.tasks.selected?.id,
        model.tasks.historyBusy, model.snapshot, model.activeArea, model.preparationArea,
        model.personsLoading, model.workspaceRefreshing, model.selected?.status,
        model.operations.moreFunctionsExpanded).joinToString(":")
    // P77-only touch correlation. No dispatch/authority changes in product builds.
    private var p113NextArea:org.pag.prototype.product.ActiveArea?=null
    var p113TouchSample:P80ContentFrames.Sample?=null;private set
    var p113EventUptime=0L;private set
    var p113DispatchNanos=0L;private set
    var p113UpEventUptime=0L;private set
    var p113UpDispatchNanos=0L;private set
    var p113UpReturnNanos=0L;private set
    fun p113ArmTouch(area:org.pag.prototype.product.ActiveArea){check(p113TouchSample==null && p113NextArea==null);p113NextArea=area}
    fun p113ConsumeTouch(){(contentFrames as P80ContentFrames).consume(checkNotNull(p113TouchSample));p113TouchSample=null}
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean {
        if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN){
            p113NextArea?.let{area->
                p113NextArea=null;p113EventUptime=event.eventTime;p113DispatchNanos=System.nanoTime()
                val start=p113DispatchNanos-(android.os.SystemClock.uptimeMillis()-event.eventTime)*1_000_000L
                p113TouchSample=(contentFrames as P80ContentFrames).beginAt(area,start)
            }
        }
        if(event.actionMasked==android.view.MotionEvent.ACTION_UP && p113TouchSample!=null){
            p113UpEventUptime=event.eventTime;p113UpDispatchNanos=System.nanoTime()
            return super.dispatchTouchEvent(event).also{p113UpReturnNanos=System.nanoTime()}
        }
        return super.dispatchTouchEvent(event)
    }
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
        check(BuildConfig.PAG_P77_RUNTIME_E2E && packageName == "org.pag.prototype.a.p77runtime")
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
        if (BuildConfig.PAG_P77_RUNTIME_E2E) {
            check(BuildConfig.APPLICATION_ID == "org.pag.prototype.a.p77runtime" &&
                packageName == "org.pag.prototype.a.p77runtime") { "P77 runtime test requires its isolated application ID" }
            val providers = ServiceLoader.load(ProductRuntimeLaunchProvider::class.java, classLoader).toList()
            check(providers.size == 1) { "P77 runtime test requires exactly one variant-local provider" }
            val launch = providers.single().create(this, worker, mainExecutor, foregroundCheck)
            runtime = launch.runtime
            runtimeClose = launch.close
            p77Harness = launch.testHandle
            contentFrames = launch.contentFrames
            testAuthorityBanner = launch.testAuthorityBanner
        } else {
            runtime = ProductRuntime(this, worker, mainExecutor, foreground = foregroundCheck)
            runtimeClose = runtime::close
        }
        val state = runtime.inventory.state()
        if (state == ProductStorageState.PARTIAL_OR_INVALID) runtime.session.storageFailure()
        model = ProductViewModel(runtime.session, runtime.actions, state, runtime.persons, runtime.leadership, runtime.operations, runtime.tasks, runtime.protocol, runtime.material, runtime.expenses, runtime.memoryReports, runtime::start)
        model.bindWorkspace(runtime.workspace)
        // §86b: intentionally leave the existing optional compact port UNBOUND.
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
                    startActivityForResult(openDocumentContract.createIntent(this@P86FullWorkspaceActivity,
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
        setContent {
            val token = p86StateToken()
            val pulse=p110Pulse.intValue
            val observation=androidx.compose.runtime.remember(pulse){ {event:String->
                if(event=="COMPOSITION")p110Composed=pulse
                if(event=="TASK_ROW" || event=="PERSON_ROW")p110RowRenders++
            }}
            androidx.compose.runtime.CompositionLocalProvider(org.pag.prototype.product.LocalProductObservation provides observation){
                val density=androidx.compose.ui.platform.LocalDensity.current
                val font=intent.getFloatExtra("p116Font",density.fontScale)
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,font)) {
                    ProductScreen(model, testAuthorityBanner = testAuthorityBanner && !intent.getBooleanExtra("p116ProductGeometry",false), contentFrames = contentFrames)
                }
            }
            androidx.compose.runtime.SideEffect { p86Committed = token }
        }
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
    override fun onUserInteraction() { super.onUserInteraction(); if (::model.isInitialized && resumed) model.userActivity() }
    override fun onDestroy() {
        documentSession?.cancel(); documentSession=null
        pendingDocument.clear()
        if (::model.isInitialized) { model.documents.clearViewers();model.documents.camera.clear() }
        demo?.end(); demo = null
        if (::runtime.isInitialized) {
            handler.removeCallbacks(expiry)
            runtimeClose?.invoke() ?: runtime.close()
            p77Harness = null
            contentFrames = null
            runtimeClose = null
            worker.shutdownNow(); unregisterReceiver(screenOff)
        }
        super.onDestroy()
    }
}
