package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.pag.domain.ProductStorageState
import org.pag.prototype.product.PagLockedScreen
import org.pag.prototype.product.ProductInputScope

/** Non-invasive LOCKED-only probe. No credential flow, storage mutation or screenshots. */
class Ux1aShellProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var activity: android.app.Activity? = null
        try {
            val launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = launched
            waitForIdleSync()
            var root: AccessibilityNodeInfo? = null
            repeat(20) {
                if (root == null) { SystemClock.sleep(100); root = uiAutomation.rootInActiveWindow }
            }
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                nodes.add(node)
                for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
            }
            visit(checkNotNull(root))
            val labels = nodes.mapNotNull { it.text?.toString() }
            check("PaG" in labels && "Aktionsplaner" in labels)
            check("PaG ist gesperrt." in labels && "Deine Aktionsdaten sind geschützt." in labels)
            val button = nodes.firstOrNull { it.text?.toString() in listOf("Entsperren", "Geschützten Speicher einrichten") }
            checkNotNull(button)
            var target = checkNotNull(button)
            while (!target.isClickable) target = target.parent ?: break
            check(target.isClickable)
            val bounds = Rect(); target.getBoundsInScreen(bounds)
            val density = targetContext.resources.displayMetrics.density
            check(bounds.height() >= 48 * density && bounds.width() >= 48 * density)
            check(nodes.none { it.contentDescription?.toString()?.startsWith("Sitzung:") == true })
            check(labels.none { it in listOf("Aktionsliste", "Personen", "Aufgaben", "Rollen", "Anzahl:", "Person gespeichert") })
            // Render only synthetic bootstrap UI. No product initialization or system setting change.
            var starts = 0
            for (fontScale in listOf(1f, 2f)) {
                runOnMainSync {
                    (launched as MainActivity).setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                            MaterialTheme {
                                ProductInputScope(fontScale) {
                                    PagLockedScreen(ProductStorageState.EMPTY) { starts++ }
                                }
                            }
                        }
                    }
                }
                waitForIdleSync()
                SystemClock.sleep(500)
                nodes.clear()
                visit(checkNotNull(uiAutomation.rootInActiveWindow))
                val setup = checkNotNull(nodes.firstOrNull { it.text?.toString() == "Geschützten Speicher einrichten" })
                var touch = setup
                while (!touch.isClickable) touch = touch.parent ?: break
                val rectangle = Rect(); touch.getBoundsInScreen(rectangle)
                check(touch.isClickable && rectangle.height() >= 56 * density)
                check(rectangle.left >= 0 && rectangle.right <= targetContext.resources.displayMetrics.widthPixels)
                check(starts == 0)
            }
            runOnMainSync { launched.finish() }
            finish(-1, Bundle().apply { putString("stream", "UX1a LOCKED labels, touch target, absent protected shell, synthetic EMPTY/no-auto-init at font scales 1 and 2: OK\n") })
        } catch (_: Exception) {
            activity?.let { runOnMainSync { it.finish() } }
            finish(0, Bundle().apply { putString("stream", "UX1a LOCKED probe failed; no operational payload logged\n") })
        }
    }
}
