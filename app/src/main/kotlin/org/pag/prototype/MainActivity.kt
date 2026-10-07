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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.pag.data.SessionCoordinator
import org.pag.data.SyntheticMaintenance
import org.pag.domain.SessionLifecycle
import org.pag.domain.SessionState
import org.pag.domain.Subscription
import org.pag.prototype.ui.PrototypeScreen
import org.pag.prototype.ui.PrototypeViewModel
import org.pag.security.PlatformCredentialExperiment

/** Composition root and lifecycle adapter only; no operative product data. */
class MainActivity : ComponentActivity() {
    private lateinit var lifecyclePort: SessionLifecycle
    private lateinit var model: PrototypeViewModel
    private lateinit var authentication: PlatformCredentialExperiment
    private var resumed = false
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecyclePort.foreground(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Never restore a session or UI bundle. Rotation also deliberately starts locked.
        super.onCreate(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        val handler = Handler(Looper.getMainLooper())
        val maintenance = SyntheticMaintenance { work ->
            val runnable = Runnable(work)
            handler.postDelayed(runnable, 1000)
            Subscription { handler.removeCallbacks(runnable) }
        }
        val environmentAllowed = {
            resumed && window.decorView.hasWindowFocus() &&
                !getSystemService(KeyguardManager::class.java).isDeviceLocked
        }
        authentication = PlatformCredentialExperiment(this, environmentAllowed)
        val coordinator = SessionCoordinator(authentication, maintenance, environmentAllowed)
        lifecyclePort = coordinator
        // Activity-owned VM: intentionally no retained unlock/controller after recreation.
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PrototypeViewModel(coordinator) as T
            }
        })[PrototypeViewModel::class.java]
        // A retained VM must not carry the old Activity's coordinator into a new Activity.
        // onDestroy clears the store; see lifecycle tests for recreation/process death.
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
        setContent { PrototypeScreen(model) }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        lifecyclePort.foreground(!getSystemService(KeyguardManager::class.java).isDeviceLocked)
        authentication.foregroundAvailable()
    }

    override fun onPause() {
        resumed = false
        // AUTHENTICATING has no released content; an own system dialog can pause us.
        // onStop is always invalidating, including during authentication.
        if (model.snapshot.state != SessionState.AUTHENTICATING) lifecyclePort.foreground(false)
        super.onPause()
    }

    override fun onStop() {
        lifecyclePort.foreground(false)
        super.onStop()
    }

    override fun onUserLeaveHint() {
        // User-initiated task switching invalidates even an AUTHENTICATING attempt.
        // If a platform credential implementation also emits this, fail closed.
        lifecyclePort.foreground(false)
        super.onUserLeaveHint()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::model.isInitialized) return
        if (!hasFocus && model.snapshot.state != SessionState.AUTHENTICATING) lifecyclePort.foreground(false)
        if (hasFocus && resumed) {
            lifecyclePort.foreground(!getSystemService(KeyguardManager::class.java).isDeviceLocked)
            authentication.foregroundAvailable()
        }
    }

    override fun onDestroy() {
        lifecyclePort.foreground(false)
        unregisterReceiver(screenOff)
        viewModelStore.clear()
        super.onDestroy()
    }
}
