package org.pag.prototype

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.B16AuthenticationExperiment
import org.pag.data.prototype.B16Inventory
import org.pag.data.prototype.B16StorageGate
import org.pag.data.prototype.b3.B3AuthenticationExperiment
import org.pag.data.prototype.b3.B3Inventory
import org.pag.domain.Experiment
import org.pag.domain.Maintenance
import org.pag.domain.MonotonicTime
import org.pag.domain.SessionPolicy
import org.pag.domain.SessionState
import org.pag.domain.Subscription
import org.pag.security.AuthenticationExperiment
import org.pag.security.RsaWrappingFixture
import org.pag.security.WrappingCredentialExperiment
import java.io.File
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Explicit test-only host runner. Never opens the old I2 namespaces.
 * One existing credential/storage adapter per invocation, not a multi-key product contract.
 */
class I3LifecycleProbe : Instrumentation() {
    private var phase = ""
    private var action = ""

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        phase = arguments?.getString("phase") ?: ""
        action = arguments?.getString("action") ?: ""
        start()
    }

    override fun onStart() {
        try {
            check(phase in PHASES)
            if (action == "legacy-inventory") {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                report(-1, listOf("B15" to "pag.prototype.b15.storage.v1",
                    "B16" to "pag.prototype.b16.storage.v1", "B3" to "pag.prototype.b3.file.v1")
                    .joinToString("\n") { (label, name) ->
                        "$label: ${if (store.containsAlias(name)) "PRESENT" else "ABSENT"}"
                    })
                return
            }
            if (action == "inventory") {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                report(-1, listOf("db", "file").joinToString("\n") { kind ->
                    "I3-$kind: ${if (store.containsAlias(alias(phase, kind))) "PRESENT" else "ABSENT"}"
                })
                return
            }
            check(action in setOf("db-create", "db-open", "file-create", "file-open"))
            check(targetContext.getSystemService(KeyguardManager::class.java).isDeviceSecure)
            authenticate()
        } catch (_: Exception) {
            report(0, "I3-PROBE-BLOCKED") // No exception payload or automatic recovery.
        }
    }

    private fun authenticate() {
        val handler = Handler(Looper.getMainLooper())
        val worker = Executors.newSingleThreadExecutor()
        val done = CountDownLatch(1)
        val application = targetContext.applicationContext as Application
        var activity: Activity? = null
        var resumed = false
        var session: SessionCoordinator? = null
        var credential: WrappingCredentialExperiment? = null
        var subscription: Subscription? = null
        var result = "I3-PROBE-BLOCKED"
        var success = false
        var completed = false
        // Evaluated only on main. No auth/session/storage starts during this wait.
        fun readiness(): String {
            val host = activity ?: return "I3-ACTIVITY-TIMEOUT"
            if (!resumed) return "I3-ACTIVITY-NOT-RESUMED"
            if (host.getSystemService(KeyguardManager::class.java).isDeviceLocked)
                return "I3-DEVICE-LOCKED"
            val view = host.window.decorView
            if (!view.isAttachedToWindow || !view.hasWindowFocus()) return "I3-WINDOW-NOT-READY"
            return "READY_FOR_AUTH"
        }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, state: Bundle?) { activity = a }
            override fun onActivityResumed(a: Activity) {
                resumed = true
                session?.foreground(!a.getSystemService(KeyguardManager::class.java).isDeviceLocked)
            }
            override fun onActivityPaused(a: Activity) {
                resumed = false
                if (session?.snapshot()?.state != SessionState.AUTHENTICATING) session?.foreground(false)
            }
            override fun onActivityStopped(a: Activity) { session?.foreground(false) }
            override fun onActivityDestroyed(a: Activity) { session?.foreground(false) }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}
        }
        val tick = object : Runnable {
            override fun run() {
                session?.checkExpiry()
                credential?.foregroundAvailable()
                if (done.count > 0) handler.postDelayed(this, 100)
            }
        }
        runOnMainSync { application.registerActivityLifecycleCallbacks(callbacks) }
        try {
            startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            waitForIdleSync()
            val readinessStarted = SystemClock.elapsedRealtime()
            var readinessId = "I3-ACTIVITY-TIMEOUT"
            while (true) {
                runOnMainSync { readinessId = readiness() }
                if (readinessId == "READY_FOR_AUTH" ||
                    SystemClock.elapsedRealtime() - readinessStarted >= ACTIVITY_WAIT_MILLIS) break
                // Instrumentation thread only; main remains available for lifecycle/focus events.
                SystemClock.sleep(50)
            }
            if (readinessId != "READY_FOR_AUTH") {
                result = readinessId
                completed = true
            } else {
                runOnMainSync {
                    try {
                        // Recheck at admission, without throwing a UI-thread readiness assertion.
                        val admission = readiness()
                        if (admission != "READY_FOR_AUTH") {
                            result = admission
                            done.countDown()
                            return@runOnMainSync
                        }
                        val host = activity ?: run {
                            result = "I3-ACTIVITY-TIMEOUT"
                            done.countDown()
                            return@runOnMainSync
                        }
                        val allowed = {
                            resumed && host.window.decorView.hasWindowFocus() &&
                                !host.getSystemService(KeyguardManager::class.java).isDeviceLocked
                        }
                        sendStatus(0, Bundle().apply { putString("i3", "READY_FOR_AUTH") })
                        val kind = if (action.startsWith("db-")) "db" else "file"
                        val root = File(targetContext.noBackupFilesDir.canonicalFile, "i3-$phase-$kind-v1")
                        val fixture = RsaWrappingFixture(File(root, "k"), alias(phase, kind))
                        val delegate = WrappingCredentialExperiment(host, fixture, worker, allowed)
                        credential = delegate
                        val adapter: AuthenticationExperiment = if (kind == "db") {
                            val inventory = B16Inventory(root, fixture::checkedState)
                            B16AuthenticationExperiment(inventory, delegate, B16StorageGate(inventory), worker, host.mainExecutor)
                        } else B3AuthenticationExperiment(B3Inventory(root, fixture::checkedState), delegate, worker, host.mainExecutor)
                        val coordinator = SessionCoordinator(adapter, Maintenance { Subscription {} },
                            MonotonicTime { SystemClock.elapsedRealtime() }, SessionPolicy(60_000, 15_000), allowed)
                        session = coordinator
                        val expected = when (action) {
                            "db-create" -> "B16-DB-CREATE-SUCCEEDED"
                            "db-open" -> "B16-RESTART-SUCCEEDED"
                            "file-create" -> "B3-PUBLISH-SUCCEEDED"
                            else -> "B3-RESTART-SUCCEEDED"
                        }
                        subscription = coordinator.observe { snapshot ->
                            if (snapshot.technicalId == expected) {
                                success = true
                                result = "I3-$action: ${snapshot.state} / ${snapshot.technicalId}"
                                done.countDown()
                            } else if (snapshot.state == SessionState.BLOCKED) {
                                result = "I3-$action: BLOCKED / ${snapshot.technicalId}"
                                done.countDown()
                            } else if (snapshot.state == SessionState.LOCKED && snapshot.generation > 0) {
                                result = "I3-$action: LOCKED / ${snapshot.technicalId}"
                                done.countDown()
                            }
                        }
                        coordinator.foreground(true)
                        handler.post(tick)
                        val experiment = when (action) {
                            "db-create" -> Experiment.DATABASE_INITIALIZE
                            "db-open" -> Experiment.DATABASE_REOPEN
                            "file-create" -> Experiment.B3_INITIALIZE
                            else -> Experiment.B3_REOPEN
                        }
                        coordinator.authenticate(experiment)
                    } catch (_: Exception) {
                        // Controlled failed test; no exception payload and no success on setup errors.
                        result = "I3-AUTH-SETUP-BLOCKED"
                        done.countDown()
                    }
                }
                completed = done.await(120, TimeUnit.SECONDS)
            }
        } finally {
            runOnMainSync {
                handler.removeCallbacks(tick)
                subscription?.cancel()
                session?.lock()
                application.unregisterActivityLifecycleCallbacks(callbacks)
            }
            worker.shutdownNow()
        }
        if (!completed) {
            success = false
            result = "I3-PROBE-TIMEOUT"
        }
        report(if (success) -1 else 0, result)
    }

    private fun report(code: Int, result: String) {
        finish(code, Bundle().apply { putString(REPORT_KEY_STREAMRESULT, "\n$result\n") })
    }

    companion object {
        private const val ACTIVITY_WAIT_MILLIS = 5_000L // Harness bound, not a session policy.
        private val PHASES = setOf("preclear", "postclear", "postreinstall")
        private fun alias(phase: String, kind: String) = "pag.prototype.i3.$phase.$kind.v1"
    }
}
