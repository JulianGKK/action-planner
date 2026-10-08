package org.pag.prototype

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.pag.data.prototype.B16Inventory
import org.pag.data.prototype.B16StorageGate
import org.pag.data.prototype.B16AuthenticationExperiment
import org.pag.data.SessionCoordinator
import org.pag.data.SyntheticMaintenance
import org.pag.domain.MonotonicTime
import org.pag.domain.SessionPolicy
import org.pag.domain.SessionLifecycle
import org.pag.domain.SessionState
import org.pag.domain.Subscription
import org.pag.prototype.ui.PrototypeScreen
import org.pag.prototype.ui.PrototypeViewModel
import org.pag.security.PlatformCredentialExperiment
import org.pag.security.AuthenticationExperiment
import org.pag.security.KeyExperiment
import org.pag.security.RsaWrappingFixture
import org.pag.security.WrappingCredentialExperiment
import java.io.File
import java.util.concurrent.Executors

/** Composition root and lifecycle adapter only; no operative product data. */
class MainActivity : ComponentActivity() {
    private lateinit var lifecyclePort: SessionLifecycle
    private lateinit var model: PrototypeViewModel
    private lateinit var authentication: PlatformCredentialExperiment
    private lateinit var wrappingAuthentication: WrappingCredentialExperiment
    private lateinit var databaseAuthentication: WrappingCredentialExperiment
    private val wrappingWorker = Executors.newSingleThreadExecutor()
    private var resumed = false
    private val expiryHandler = Handler(Looper.getMainLooper())
    private val expiryCheck = object : Runnable {
        override fun run() {
            if (!resumed) return
            lifecyclePort.checkExpiry()
            if (resumed) expiryHandler.postDelayed(this, 250)
        }
    }
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::model.isInitialized && resumed) model.userInteraction()
    }
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
        // Explicitly reserved namespace: every entry is considered, including future DB/assets/work files.
        // Context's trusted private base can contain Android's /data/user/0 path alias.
        // Normalize that base; never canonicalize away a symlink at the reserved child.
        val fixture = RsaWrappingFixture(File(noBackupFilesDir.canonicalFile, "p15"), "pag.prototype.b15.storage.v1")
        wrappingAuthentication = WrappingCredentialExperiment(this, fixture, wrappingWorker, environmentAllowed)
        val databaseRoot = File(noBackupFilesDir.canonicalFile, "p16")
        val databaseFixture = RsaWrappingFixture(File(databaseRoot, "k"), "pag.prototype.b16.storage.v1")
        val inventory = B16Inventory(databaseRoot, databaseFixture::checkedState)
        databaseAuthentication = WrappingCredentialExperiment(this, databaseFixture, wrappingWorker, environmentAllowed)
        val databaseAdapter = B16AuthenticationExperiment(inventory, databaseAuthentication,
            B16StorageGate(inventory), wrappingWorker, mainExecutor)
        val authPort = object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (org.pag.security.AuthenticationOutcome) -> Unit) =
                if (mode == KeyExperiment.DATABASE_INITIALIZE || mode == KeyExperiment.DATABASE_REOPEN)
                    databaseAdapter.start(mode, complete)
                else if (mode == KeyExperiment.STORAGE_INITIALIZE || mode == KeyExperiment.STORAGE_UNWRAP)
                    wrappingAuthentication.start(mode, complete) else authentication.start(mode, complete)
        }
        val coordinator = SessionCoordinator(authPort, maintenance,
            MonotonicTime { SystemClock.elapsedRealtime() },
            // Synthetic prototype policy only. Not product durations.
            SessionPolicy(60_000, 15_000), environmentAllowed)
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
        wrappingAuthentication.foregroundAvailable()
        databaseAuthentication.foregroundAvailable()
        expiryHandler.removeCallbacks(expiryCheck)
        expiryHandler.post(expiryCheck)
    }

    override fun onPause() {
        resumed = false
        expiryHandler.removeCallbacks(expiryCheck)
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
            wrappingAuthentication.foregroundAvailable()
            databaseAuthentication.foregroundAvailable()
        }
    }

    override fun onDestroy() {
        expiryHandler.removeCallbacks(expiryCheck)
        lifecyclePort.foreground(false)
        wrappingWorker.shutdownNow()
        unregisterReceiver(screenOff)
        viewModelStore.clear()
        super.onDestroy()
    }
}
