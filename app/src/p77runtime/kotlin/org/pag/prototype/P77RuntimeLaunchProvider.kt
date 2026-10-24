package org.pag.prototype

import android.app.Activity
import org.pag.data.product.P77ProductRuntimeHarness
import java.util.concurrent.Executor

/** Packaged only by the isolated p77runtime build type. */
class P77RuntimeLaunchProvider : ProductRuntimeLaunchProvider {
    override fun create(activity: Activity, worker: Executor, main: Executor,
        foreground: () -> Boolean): ProductRuntimeLaunch {
        check(activity.packageName == "org.pag.prototype.a.p77runtime")
        check(BuildConfig.PAG_P77_RUNTIME_E2E && BuildConfig.APPLICATION_ID == activity.packageName)
        val harness = P77ProductRuntimeHarness.create(activity, worker, main, foreground,
            reuseEnabled = activity !is P86FullWorkspaceActivity && !activity.intent.getBooleanExtra("p100Probe",false))
        return ProductRuntimeLaunch(harness.runtime, harness::close, testAuthorityBanner = true, testHandle = harness, contentFrames = P80ContentFrames())
    }
}
