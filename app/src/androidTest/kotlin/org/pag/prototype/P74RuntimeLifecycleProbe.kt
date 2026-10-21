package org.pag.prototype

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import org.pag.data.product.ProductRuntime
import org.pag.domain.*
import org.pag.prototype.product.ProductViewModel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real OS Activity callbacks and actual Runtime/model/Compose, deliberately LOCKED.
 * No credential injection or synthetic READY. This is NOT ACTIVE/native-read E2E.
 */
class P74RuntimeLifecycleProbe:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun runtime(a:MainActivity)=MainActivity::class.java.getDeclaredField("runtime").let {
        it.isAccessible=true;it.get(a) as ProductRuntime
    }
    private fun model(a:MainActivity)=MainActivity::class.java.getDeclaredField("model").let {
        it.isAccessible=true;it.get(a) as ProductViewModel
    }
    override fun onStart() {
        var current:MainActivity?=null
        var callbacks:Application.ActivityLifecycleCallbacks?=null
        var application:Application?=null
        var cases=0
        try {
            check(targetContext.packageName=="org.pag.validation.p74")
            check(context.packageName=="org.pag.validation.p74.test")
            check(!File(targetContext.noBackupFilesDir,"pag-db-v1").exists()) { "Isolated package must start empty" }
            val stopped=CountDownLatch(1);val restarted=CountDownLatch(1);val recreated=CountDownLatch(1)
            val replacement=AtomicReference<MainActivity?>()
            val original=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            current=original;application=original.application
            callbacks=object:Application.ActivityLifecycleCallbacks {
                override fun onActivityStopped(a:Activity){if(a===original)stopped.countDown()}
                override fun onActivityStarted(a:Activity){if(a===original && stopped.count==0L)restarted.countDown()}
                override fun onActivityResumed(a:Activity){if(a is MainActivity && a!==original){replacement.set(a);recreated.countDown()}}
                override fun onActivityCreated(a:Activity,b:Bundle?){}
                override fun onActivityPaused(a:Activity){}
                override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
                override fun onActivityDestroyed(a:Activity){}
            }
            application.registerActivityLifecycleCallbacks(callbacks)
            fun denied(a:MainActivity) { runOnMainSync {
                check(runtime(a).session.snapshot().state==SessionState.LOCKED)
                check(!runtime(a).activeCore.loadOwned("synthetic-unavailable") { _,_->error("Read without READY") })
                check(model(a).activeCore==null)
                check(!runtime(a).activeCore.reuseEnabled)
            };cases++ }
            denied(original)
            // Send a real HOME intent; do not invoke lifecycle/session methods directly.
            runOnMainSync { original.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
            check(stopped.await(15,TimeUnit.SECONDS)) { "OS onStop missing" };denied(original)
            targetContext.startActivity(Intent(targetContext,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            check(restarted.await(15,TimeUnit.SECONDS)) { "OS onStart missing" };denied(original)
            val oldRuntime=runtime(original)
            runOnMainSync { original.recreate() }
            check(recreated.await(15,TimeUnit.SECONDS)) { "Activity recreation missing" }
            current=checkNotNull(replacement.get());denied(current!!)
            runOnMainSync { check(oldRuntime!==runtime(current!!));check(oldRuntime.session.snapshot().state==SessionState.LOCKED) }
            val draw=CountDownLatch(1)
            runOnMainSync {
                val view=current!!.window.decorView
                val listener=object:android.view.ViewTreeObserver.OnDrawListener {
                    override fun onDraw(){check(model(current!!).activeCore==null);draw.countDown();view.post{view.viewTreeObserver.removeOnDrawListener(this)}}
                }
                view.viewTreeObserver.addOnDrawListener(listener);view.invalidate()
            }
            check(draw.await(15,TimeUnit.SECONDS)) { "Draw missing" };cases++
            check(!File(targetContext.noBackupFilesDir,"pag-db-v1").exists())
            sendStatus(0,Bundle().apply { putString("stream","P74_OS,cases=$cases,home_stop_start=true,recreated=true,locked_draw=true,active_e2e=false,keyguard_event_tested=false,keyguard_locked=${targetContext.getSystemService(KeyguardManager::class.java).isDeviceLocked}\n") })
            finish(Activity.RESULT_OK,Bundle().apply {putString("result","PASS: $cases LOCKED Runtime/OS checks; ACTIVE E2E BLOCKED by credential initialization")})
        } catch(t:Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply {putString("result","FAIL after $cases: ${t.stackTraceToString()}")})
        } finally {
            callbacks?.let { application?.unregisterActivityLifecycleCallbacks(it) }
            current?.let { a->runOnMainSync{a.finish()} }
        }
    }
}
