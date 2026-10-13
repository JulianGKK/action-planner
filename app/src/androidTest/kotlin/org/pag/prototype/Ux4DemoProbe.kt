package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.pag.domain.ActionStatus
import org.pag.prototype.demo.*

/** Runs against the demo APK itself; real entry, RAM state and Compose controls, never product ports. */
class Ux4DemoProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) { if (node == null) return; result += node; repeat(node.childCount) { walk(node.getChild(it)) } }
        walk(uiAutomation.rootInActiveWindow); return result
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun match(node: AccessibilityNodeInfo, label: String): Boolean {
        val text = node.text?.toString() ?: return false
        // A large whole-card Button can expose its merged text instead of the
        // individual opening label. Click the actual enabled Button ancestor.
        return text == label || text == "✓ $label" || (label.endsWith(" öffnen") && text.contains(label))
    }
    private fun visible(label: String): AccessibilityNodeInfo {
        repeat(50) { attempt ->
            val tree = nodes(); tree.find { match(it, label) && bounds(it).height() > 0 }?.let { return it }
            // Let the accessibility frame catch up before scrolling away from a
            // newly published heading. Tour/home headings live at the top.
            if (attempt >= 5) tree.firstOrNull { it.isScrollable }?.performAction(
                if (label.startsWith("Rundgang") || label in listOf("PaG Demo", "Lagebild", "Vorbereitung im Überblick"))
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            waitForIdleSync(); SystemClock.sleep(80)
        }
        error("Synthetic demo label not reachable")
    }
    private fun smallScroll(backwards: Boolean) {
        val rect = nodes().firstOrNull { it.isScrollable }?.let(::bounds) ?: return
        if (rect.height() < 100) return
        val density = targetContext.resources.displayMetrics.density
        val distance = minOf(100 * density, rect.height() * 0.3f)
        val x = rect.centerX().toFloat()
        val from = if (backwards) rect.top + rect.height() * 0.3f else rect.bottom - rect.height() * 0.3f
        val to = from + if (backwards) distance else -distance
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN, from)
        repeat(5) { i -> SystemClock.sleep(30); inject(MotionEvent.ACTION_MOVE, from + (to - from) * (i + 1) / 5) }
        // Release without a fling so narrow headings cannot be skipped.
        SystemClock.sleep(150); inject(MotionEvent.ACTION_MOVE, to)
        SystemClock.sleep(100); inject(MotionEvent.ACTION_UP, to); waitForIdleSync()
    }
    private fun top() { repeat(25) { val scroll = nodes().firstOrNull { it.isScrollable } ?: return; if (!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return; waitForIdleSync(); SystemClock.sleep(50) } }
    private var clickDiagnostic = ""
    private fun click(label: String, backwards: Boolean = label.startsWith("‹") || label in listOf("Übersicht", "Personen", "Aufgaben", "Rollen")) {
        repeat(50) {
            val tree = nodes()
            for (node in tree.filter { match(it, label) }) {
                var control: AccessibilityNodeInfo? = node
                while (control != null && !control.isClickable) control = control.parent
                if (control != null && control.isEnabled && bounds(control).height() > 0) {
                    val rect = bounds(control); check(rect.left >= 0 && rect.right <= targetContext.resources.displayMetrics.widthPixels)
                    check(control.performAction(AccessibilityNodeInfo.ACTION_CLICK)); waitForIdleSync(); return
                }
            }
            smallScroll(backwards)
            waitForIdleSync(); SystemClock.sleep(180)
        }
        val finalTree = nodes()
        clickDiagnostic = "nodes=${finalTree.size},scrolls=${finalTree.count { it.isScrollable }},matches=${finalTree.count { match(it, label) }},buttons=" +
            finalTree.filter { it.isClickable }.take(12).joinToString { "${bounds(it)}:len=${it.text?.length ?: 0}:opens=${it.text?.contains(" öffnen") == true}" }
        error("Synthetic demo control not reachable")
    }
    private fun isolation(activity: MainActivity) {
        for (name in listOf("runtime", "model")) check(MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity) == null)
        for (name in listOf("worker\$delegate", "handler\$delegate")) {
            val lazy = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity) as Lazy<*>
            check(!lazy.isInitialized())
        }
        check(targetContext.databaseList().isEmpty())
        check(targetContext.noBackupFilesDir.listFiles().orEmpty().isEmpty())
    }
    override fun onStart() {
        var activity: MainActivity? = null; var scaleUnderTest = 1f; var phase = "entry"
        try {
            if (!BuildConfig.PAG_DEMO_ONLY) {
                check(targetContext.packageName == "org.pag.prototype.a")
                val product = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                activity = product; waitForIdleSync()
                val runtime = MainActivity::class.java.getDeclaredField("runtime").apply { isAccessible = true }.get(product) as org.pag.data.product.ProductRuntime
                check(runtime.session.snapshot().state == org.pag.domain.SessionState.LOCKED)
                check(MainActivity::class.java.getDeclaredField("demo").apply { isAccessible = true }.get(product) == null)
                visible("PaG")
                runOnMainSync { product.finish() }
                finish(-1, Bundle().apply { putString("stream", "UX4 normal APK: product runtime constructed, LOCKED, demo disabled, no credential request: OK\n") })
                return
            }
            check(BuildConfig.PAG_DEMO_ONLY && targetContext.packageName == "org.pag.prototype.a.demo")
            check(targetContext.applicationInfo.loadLabel(targetContext.packageManager).toString() == "PaG Demo")
            val launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = launched; waitForIdleSync(); isolation(launched)
            val model = MainActivity::class.java.getDeclaredField("demo").apply { isAccessible = true }.get(launched) as DemoState
            val density = targetContext.resources.displayMetrics.density
            for (scale in listOf(1f, 2f)) {
                scaleUnderTest = scale
                runOnMainSync { model.end(); launched.setContent { CompositionLocalProvider(LocalDensity provides Density(density, scale)) { DemoScreen(model) } } }
                waitForIdleSync(); visible("DEMO – nur Beispieldaten"); visible("PaG Demo")
                click("Vorbereitung ausprobieren"); visible("Vorbereitung im Überblick")
                runOnMainSync { check(model.action!!.status == ActionStatus.PREPARATION) }
                for ((page, label) in listOf(DemoPage.PERSONS to "Personen", DemoPage.TASKS to "Aufgaben", DemoPage.ROLES to "Rollen", DemoPage.OVERVIEW to "Übersicht")) {
                    phase = "tab-$label"
                    top(); click(label)
                    var selected: AccessibilityNodeInfo? = null
                    for (attempt in 0 until 50) {
                        selected = nodes().find { it.text?.toString() == "✓ $label" }
                        if (selected != null) break
                        waitForIdleSync(); SystemClock.sleep(80)
                    }
                    check(selected != null)
                    var tab: AccessibilityNodeInfo? = selected
                    while (tab != null && !tab.isSelected) tab = tab.parent
                    check(tab != null && bounds(tab).height() >= 48 * density)
                    runOnMainSync { check(model.page == page) }; visible("DEMO – nur Beispieldaten")
                }
                phase = "start-action"; click("Aktion starten"); click("Bestätigen"); visible("Lagebild")
                runOnMainSync { check(model.board!!.persons.all { it.state!!.status == org.pag.domain.OperationalStatus.NO_STATUS }) }
                click("Demo zurücksetzen"); runOnMainSync { check(model.action!!.status == ActionStatus.PREPARATION && model.editor == null) }
                click("Demo beenden"); visible("PaG Demo"); runOnMainSync { check(model.action == null && model.board == null) }
                click("Direkt ins Lagebild"); visible("Lagebild")
                for ((page, label) in listOf(DemoPage.PERSONS to "Personen", DemoPage.TASKS to "Aufgaben", DemoPage.FUNCTIONS to "Funktionen", DemoPage.DETAILS to "Aktionsdetails")) {
                    phase = "hub-$label"; top(); click(label, backwards = false); runOnMainSync { check(model.page == page) }; visible("DEMO – nur Beispieldaten")
                    top(); click("‹ Zum Lagebild"); runOnMainSync { check(model.page == DemoPage.HUB) }; visible("Lagebild")
                }
                runOnMainSync { model.transitionTask("t0", org.pag.domain.TaskStatus.DONE); model.edit(DemoEditor.LIVE, "p0"); model.saveLive(org.pag.domain.OperationalStatus.NOT_REACHABLE, null, "") }
                click("Demo zurücksetzen"); runOnMainSync { check(model.board!!.count(org.pag.domain.OperationalStatus.ON_SITE) == 3 && model.taskBoard!!.tasks.first().status == org.pag.domain.TaskStatus.OPEN) }
                phase = "tour-home"; click("Demo beenden"); click("Kurzer Rundgang")
                phase = "tour-one"; visible("Rundgang 1/4"); runOnMainSync { check(model.tourStep == 0) }
                click("Weiter"); phase = "tour-two"; runOnMainSync { check(model.tourStep == 1) }
                visible("Rundgang 2/4"); click("Überspringen")
                runOnMainSync { check(model.tourStep == null) }; isolation(launched)
                click("Demo beenden"); runOnMainSync { check(model.action == null) }
            }
            runOnMainSync { launched.finish() }
            finish(-1, Bundle().apply { putString("stream", "UX4 demo APK entry/no product runtime/model/worker/handler/DB/keys or credential; home/preparation/active/four cards/tour/reset/end/marker/touch bounds/font 1+2: OK\n") })
        } catch (failure: Exception) {
            activity?.let { runOnMainSync { it.finish() } }
            finish(0, Bundle().apply { putString("stream", "UX4 synthetic probe failed at ${failure.stackTrace.filter { it.className == Ux4DemoProbe::class.java.name }.take(5).joinToString { it.lineNumber.toString() }}; fontScale=$scaleUnderTest; phase=$phase; $clickDiagnostic; no input data logged\n") })
        }
    }
}
