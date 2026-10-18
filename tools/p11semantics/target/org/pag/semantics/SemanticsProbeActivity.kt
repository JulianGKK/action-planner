package org.pag.semantics

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import java.util.concurrent.atomic.AtomicInteger

/** Isolated, manifest-injected test target. No product runtime, repository, session or file APIs. */
class SemanticsProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName=="org.pag.test.p11semantics")
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onDestroy(){super.onDestroy();destroyed.incrementAndGet()}
    companion object {val destroyed=AtomicInteger()}
}
