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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.pag.data.product.ProductRuntime
import org.pag.domain.ProductStorageState
import org.pag.domain.SessionState
import org.pag.prototype.product.ProductScreen
import org.pag.prototype.product.ProductViewModel
import java.util.concurrent.Executors

/** Product composition root. PrototypeActivity remains unregistered historical evidence. */
class MainActivity : ComponentActivity() {
    private lateinit var runtime: ProductRuntime
    private lateinit var model: ProductViewModel
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private val expiry = object : Runnable {
        override fun run() {
            if (!resumed) return
            runtime.session.checkExpiry()
            handler.postDelayed(this, 250)
        }
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { runtime.session.foreground(false) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        runtime = ProductRuntime(this, worker, mainExecutor) {
            resumed && window.decorView.hasWindowFocus() && !getSystemService(KeyguardManager::class.java).isDeviceLocked
        }
        val state = runtime.inventory.state()
        if (state == ProductStorageState.PARTIAL_OR_INVALID) runtime.session.storageFailure()
        model = ProductViewModel(runtime.session, runtime.actions, state, runtime.persons, runtime::start)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
        setContent { ProductScreen(model) }
    }
    override fun onResume() {
        super.onResume(); resumed = true
        runtime.session.foreground(!getSystemService(KeyguardManager::class.java).isDeviceLocked)
        if (runtime.session.snapshot().state == SessionState.LOCKED) {
            val state = runtime.inventory.state()
            model.updateBootstrap(state)
            if (state == ProductStorageState.PARTIAL_OR_INVALID) runtime.session.storageFailure()
        }
        runtime.authentication.foregroundAvailable()
        handler.removeCallbacks(expiry); handler.post(expiry)
    }
    override fun onPause() {
        resumed = false; handler.removeCallbacks(expiry)
        if (runtime.session.snapshot().state != SessionState.AUTHENTICATING) runtime.session.foreground(false)
        super.onPause()
    }
    override fun onStop() { runtime.session.foreground(false); super.onStop() }
    override fun onUserLeaveHint() { runtime.session.foreground(false); super.onUserLeaveHint() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::runtime.isInitialized) return
        if (!hasFocus && runtime.session.snapshot().state != SessionState.AUTHENTICATING) runtime.session.foreground(false)
        if (hasFocus && resumed) {
            runtime.session.foreground(!getSystemService(KeyguardManager::class.java).isDeviceLocked)
            runtime.authentication.foregroundAvailable()
        }
    }
    override fun onUserInteraction() { super.onUserInteraction(); if (::model.isInitialized && resumed) model.interaction() }
    override fun onDestroy() {
        handler.removeCallbacks(expiry)
        runtime.close(); worker.shutdownNow(); unregisterReceiver(screenOff)
        super.onDestroy()
    }
}
