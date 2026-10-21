package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.Debug
import android.view.View
import android.view.ViewTreeObserver
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.SessionState
import org.pag.prototype.product.ActiveArea
import org.pag.prototype.product.ProductViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.io.File

/** Isolated full Runtime -> Room/SQLCipher -> ViewModel -> ProductScreen draw test.
 * Authentication material is synthetic and package-bound; this is not device-credential E2E.
 */
class P77RuntimeE2EProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    private fun field(activity: MainActivity, name: String): Any =
        MainActivity::class.java.getDeclaredField(name).let { it.isAccessible = true; checkNotNull(it.get(activity)) }

    override fun onStart() {
        var activity: MainActivity? = null
        try {
            check(targetContext.packageName == "org.pag.prototype.a.p77runtime")
            check(context.packageName == "org.pag.prototype.a.p77runtime.test")
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val model = field(activity, "model") as ProductViewModel
            val harness = field(activity, "p77Harness") as P77ProductRuntimeHarness
            val runtime = field(activity, "runtime") as org.pag.data.product.ProductRuntime
            check(runtime.activeCore.reuseEnabled)
            check(harness.runtime === runtime)
            check(runtime.inventory.state()==org.pag.domain.ProductStorageState.READY_TO_OPEN) {
                "Synthetic inventory not READY_TO_OPEN: ${runtime.inventory.state()}"
            }

            await("focused target activity") {
                var focused=false
                runOnMainSync { focused=activity.hasWindowFocus() && runtime.session.snapshot().state==SessionState.LOCKED }
                focused
            }
            runOnMainSync { model.unlock() }
            val readyEnd=SystemClock.elapsedRealtime()+TimeUnit.SECONDS.toMillis(90)
            while(SystemClock.elapsedRealtime()<readyEnd && runtime.session.snapshot().state!=SessionState.READY) Thread.sleep(100)
            check(runtime.session.snapshot().state==SessionState.READY) {
                "Synthetic unlock ended in ${runtime.session.snapshot().state}/${runtime.session.snapshot().technicalId}; inventory=${runtime.inventory.state()}"
            }
            await("action list") { model.actions.any { it.id == "p11-active" } }
            runOnMainSync { model.detail("p11-active") }
            await("ACTIVE core publication") {
                var present = false
                runOnMainSync { present = model.activeCore != null }
                present
            }

            val original = AtomicReference<Any?>()
            runOnMainSync { original.set(model.activeCore) }
            val originalCore=checkNotNull(original.get()) as org.pag.domain.ActiveCorePayload
            check(originalCore.action.id=="p11-active" && originalCore.action.status==org.pag.domain.ActionStatus.ACTIVE)
            check(originalCore.people.overall.total==200L && originalCore.tasks.overall.total==1000L)
            check(originalCore.people.items.size==32 && originalCore.tasks.items.size==32)
            check(originalCore.people.hasMore && originalCore.tasks.hasMore)
            check(harness.observations.tickets.get() == 1)
            check(harness.observations.reads.get() == 1)
            check(harness.observations.hasSingleOriginalTicket())
            val memoryBefore=memorySample()
            val idleBefore = requireNotNull(runtime.session.remainingAbsoluteMillis())
            val rows = mutableListOf<String>()
            val sequence = listOf(ActiveArea.PERSONS, ActiveArea.FUNCTIONS, ActiveArea.TASKS, ActiveArea.HUB)
            repeat(20) { for (area in sequence) {
                val navigationState = AtomicReference<String>()
                val elapsed = drawAfter(activity) {
                    model.activeArea(area)
                    check(model.activeCore === original.get())
                    navigationState.set("area=${model.activeArea},enabled=${model.activeNavigationEnabled},selected=${model.selected?.id},status=${model.selected?.status},session=${model.snapshot.state},generation=${model.snapshot.generation}")
                }
                check(navigationState.get().startsWith("area=$area,")) { "Compact navigation did not select $area: ${navigationState.get()}" }
                val visible = awaitVisibleArea(activity, model, originalCore, area)
                rows += "P77_COMPOSE,$area,${visible.joinToString("|")}"
                rows += "P77_DRAW,$area,$elapsed"
            } }
            val idleAfter = requireNotNull(runtime.session.remainingAbsoluteMillis())
            check(idleAfter <= idleBefore) { "Navigation appeared to extend the READY idle deadline" }
            check(harness.observations.tickets.get() == 1)
            check(harness.observations.reads.get() == 1)
            check(harness.observations.hitAttempts.get() == 80)
            check(harness.observations.hitAccepted.get() == 80)
            check(runtime.session.snapshot().state == SessionState.READY)
            var slotRetained=false
            runOnMainSync { slotRetained = model.activeCore === original.get() }
            check(slotRetained)

            // Real SessionCoordinator lock must synchronously invalidate the original owner.
            runOnMainSync { model.lock() }
            check(runtime.session.snapshot().state == SessionState.LOCKED)
            runOnMainSync { check(model.activeCore == null) }
            val memoryAfter=memorySample()
            val payload = requireNotNull(original.get())
            val report = buildString {
                append("P77_RESULT,synthetic_test_authority=true,product_runtime=true,reader_invocations=1,tickets=1,hit_attempts=80,accepted_hits=80,retained_payload_same=true,idle_extended=false,lock_revoked=true\n")
                rows.forEach { append(it).append('\n') }
                harness.observations.hitNanos.forEachIndexed { index,nanos -> append("P77_HIT,${index+1},$nanos\n") }
                append(memoryBefore).append('\n').append(memoryAfter).append('\n')
                append("P77_COUNTS,people=200,tasks=1000,functions=")
                val core=payload as org.pag.domain.ActiveCorePayload
                append(core.functions.items.size)
                append(",global_people=").append(core.people.overall.total)
                    .append(",global_tasks=").append(core.tasks.overall.total)
                    .append(",people_page=").append(payload.people.items.size)
                    .append(",tasks_page=").append(payload.tasks.items.size)
                    .append(",people_hasMore=").append(payload.people.hasMore)
                    .append(",tasks_hasMore=").append(payload.tasks.hasMore).append('\n')
            }
            sendStatus(0, Bundle().apply { putString("stream", report) })
            finish(android.app.Activity.RESULT_OK, Bundle().apply { putString("result", "P77 PASS: ProductRuntime SQLCipher core read, one ticket/read and 80 authorized hits; synthetic authority only") })
        } catch (error: Throwable) {
            finish(android.app.Activity.RESULT_CANCELED, Bundle().apply { putString("result", "P77 FAIL: ${error.stackTraceToString()}") })
        } finally {
            activity?.let { current -> runOnMainSync { current.finish() } }
        }
    }

    private fun await(label: String, timeoutSeconds: Long = 90, details: () -> String = { "" }, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(timeoutSeconds)
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Timed out waiting for $label${details().takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""}")
    }

    private fun awaitVisibleArea(activity: MainActivity, model: ProductViewModel,
        core: org.pag.domain.ActiveCorePayload, area: ActiveArea): Set<String> {
        var expected = emptySet<String>()
        var visible = emptySet<String>()
        var currentArea: ActiveArea? = null
        var compact = false
        var rootPackage: String? = null
        await("composed compact $area", timeoutSeconds = 8, details = {
            "area=$currentArea compact=$compact root=$rootPackage visible=${visible.size}/${expected.size} text=${visible.joinToString("|")}"
        }, condition = {
            runOnMainSync {
                currentArea = model.activeArea
                compact = model.compactActiveMode
            }
            if (currentArea != area || !compact) return@await false
            expected = when (area) {
                ActiveArea.HUB -> setOf("Lagebild", "${core.people.overall.total} insgesamt",
                    "Personen öffnen", "Aufgaben öffnen", "Funktionen öffnen")
                ActiveArea.PERSONS -> setOf("Personen",
                    "${core.people.items.size} von ${core.people.overall.total} angezeigt · weitere Einträge verfügbar",
                    requireNotNull(core.people.items.firstOrNull()?.pseudonym))
                ActiveArea.FUNCTIONS -> setOf("Funktionen",
                    "${core.functions.onSiteCount} Personen vor Ort · ${core.functions.withoutFunction} ohne Funktion")
                ActiveArea.TASKS -> setOf("Aufgaben",
                    "${core.tasks.items.size} von ${core.tasks.overall.open + core.tasks.overall.inProgress} angezeigt · weitere Einträge verfügbar",
                    requireNotNull(core.tasks.items.firstOrNull()?.title))
                else -> emptySet()
            }
            visible = expected.filter { isVisibleText(it) { rootPackage = it } }.toSet()
            expected.isNotEmpty() && visible.containsAll(expected)
        })
        return expected
    }

    private fun isVisibleText(search: String, onRoot: (String?) -> Unit): Boolean {
        val root = uiAutomation.rootInActiveWindow ?: run { onRoot(null); return false }
        onRoot(root.packageName?.toString())
        return try {
            root.findAccessibilityNodeInfosByText(search).orEmpty().any { node ->
                val visible = node.text?.toString()?.contains(search) == true ||
                    node.contentDescription?.toString()?.contains(search) == true
                node.recycle()
                visible
            }
        } finally {
            root.recycle()
        }
    }

    private fun memorySample():String {
        val stats=Debug.getRuntimeStats()
        return "P77_MEMORY,phase=${if(runtimePhase++==0)"after_core_before_hits" else "after_hits_and_lock"},pid=${android.os.Process.myPid()},fd=${File("/proc/self/fd").list()?.size ?: -1},pss_kb=${Debug.getPss()},native_heap_bytes=${Debug.getNativeHeapAllocatedSize()},art_gc_count=${stats["art.gc.gc-count"] ?: "unavailable"}"
    }
    private var runtimePhase=0

    private fun drawAfter(activity: MainActivity, action: () -> Unit): Long {
        val view = activity.window.decorView
        val done = CountDownLatch(1)
        val once = AtomicBoolean(false)
        val started = AtomicLong()
        val elapsed = AtomicLong()
        lateinit var listener: ViewTreeObserver.OnDrawListener
        runOnMainSync {
            listener = ViewTreeObserver.OnDrawListener {
                if (once.compareAndSet(false, true)) {
                    elapsed.set(SystemClock.elapsedRealtimeNanos() - started.get())
                    done.countDown()
                    view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener) }
                }
            }
            view.viewTreeObserver.addOnDrawListener(listener)
            started.set(SystemClock.elapsedRealtimeNanos())
            action()
            view.invalidate()
        }
        check(done.await(10, TimeUnit.SECONDS)) { "No Content OnDraw after navigation" }
        runOnMainSync { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener) }
        return elapsed.get()
    }
}
