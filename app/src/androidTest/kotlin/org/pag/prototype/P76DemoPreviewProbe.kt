package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import org.pag.prototype.demo.DemoPage
import org.pag.prototype.demo.DemoScenario
import org.pag.prototype.demo.DemoState

/** Real Compose draw measurements for the explicitly synthetic UI preview.
 * It does not exercise ProductRuntime, SQLCipher, ProductActiveCoreLifetime or RAM hits.
 */
class P76DemoPreviewProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun verify(value: Boolean, message: String) { check(value) { message } }
    override fun onStart() {
        var activity: MainActivity? = null
        val raw = StringBuilder()
        val counts = linkedMapOf<DemoPage, Int>()
        val sorted = linkedMapOf<DemoPage, List<Long>>()
        try {
            verify(targetContext.packageName == "org.pag.prototype.a.p76demo", "Unexpected isolated target")
            verify(context.packageName == "org.pag.prototype.a.p76demo.test", "Unexpected isolated test package")
            val target = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = target
            val field = MainActivity::class.java.getDeclaredField("demo").apply { isAccessible = true }
            val model = checkNotNull(field.get(target) as? DemoState) { "Demo mode did not launch" }
            runOnMainSync { model.begin(DemoScenario.ACTIVE) }
            val pages = listOf(DemoPage.PERSONS, DemoPage.FUNCTIONS, DemoPage.TASKS, DemoPage.HUB)
            for (page in pages) {
                val samples = mutableListOf<Long>()
                repeat(20) {
                    val done = CountDownLatch(1)
                    val started = AtomicLong()
                    val recorded = AtomicBoolean(false)
                    runOnMainSync {
                        val decor = target.window.decorView
                        val listener = object : ViewTreeObserver.OnDrawListener {
                            override fun onDraw() {
                                if(recorded.compareAndSet(false,true)) {
                                    samples.add(SystemClock.elapsedRealtimeNanos() - started.get())
                                    done.countDown()
                                    decor.post { if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnDrawListener(this) }
                                }
                            }
                        }
                        decor.viewTreeObserver.addOnDrawListener(listener)
                        started.set(SystemClock.elapsedRealtimeNanos())
                        model.navigate(page)
                    }
                    verify(done.await(10, TimeUnit.SECONDS), "No Content-OnDraw for $page")
                    counts[page] = (counts[page] ?: 0) + 1
                }
                sorted[page] = samples.sorted()
                samples.forEachIndexed { i, ns -> raw.append("P76_DRAW,$page,$i,$ns\n") }
            }
            verify(model.action?.status == org.pag.domain.ActionStatus.ACTIVE, "Synthetic ACTIVE preview missing")
            verify(model.board?.persons?.size == 6 && model.taskBoard?.tasks?.size == 4, "Synthetic preview counts changed")
            val summary = sorted.entries.joinToString(";") { (page, values) ->
                val median = values[values.size / 2]
                val p95 = values[((values.size * 95 + 99) / 100 - 1).coerceAtMost(values.lastIndex)]
                "$page,n=${values.size},median_ns=$median,p95_ns=$p95"
            }
            raw.append("P76_RESULT,target=${targetContext.packageName},authorized_ram_hits=0,fresh_sql_reads=0,draws=${counts.values.sum()},$summary\n")
            sendStatus(0, Bundle().apply { putString("stream", raw.toString()) })
            finish(Activity.RESULT_OK, Bundle().apply {
                putString("result", "Synthetic UI preview measured; no authorized RAM hit or SQL read tested")
            })
        } catch (error: Throwable) {
            sendStatus(0, Bundle().apply { putString("stream", raw.toString() + "P76_FAILED,${android.util.Log.getStackTraceString(error)}\n") })
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("result", error.message ?: error.javaClass.simpleName) })
        } finally { activity?.let { runOnMainSync { it.finish() } } }
    }
}
