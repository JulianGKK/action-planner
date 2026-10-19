package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.pag.domain.*
import org.pag.prototype.p11fixture.P11FunctionsFixture.Session
import org.pag.prototype.p11fixture.P11FunctionsFixture.Actions
import org.pag.prototype.p11fixture.P11FunctionsFixture.People
import org.pag.prototype.p11fixture.P11FunctionsFixture.Ops
import org.pag.prototype.p11fixture.P11FunctionsFixture.Tasks
import org.pag.prototype.product.*

/** Real ProductScreen with synthetic ports only. No credentials, DB writes or system font change. */
class Ux3dFunctionsProbe : Instrumentation() {
    private var requestedScale:Float?=null
    private var failureKind="ASSERTION"
    private var witness=false
    private var minimalHubObservation=false
    private val trace by lazy{UiReachabilityTrace(this,"FUNCTIONS")}
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments)
        requestedScale=arguments?.getString("font")?.toFloat();require(requestedScale==null || requestedScale in listOf(1f,2f))
        witness=arguments?.getString("witness")=="paired"
        minimalHubObservation=arguments?.getString("hubObservation")=="minimal"
        trace.refreshAncestors=arguments?.getString("navigation")=="parents";trace.refreshTree=arguments?.getString("navigation")=="nodes"
        trace.enabled=arguments?.getString("diagnostics")!="off";trace.detailed=arguments?.getString("diagnostics")=="detailed";start()
    }
    private fun name(value: String) {
        var field: AccessibilityNodeInfo? = null
        for (attempt in 0 until 50) {
            field = nodes().firstOrNull { it.isEditable }
            if (field != null) break
            waitForIdleSync(); SystemClock.sleep(80)
        }
        check(field != null)
        check(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        waitForIdleSync(); field!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id); waitForIdleSync()
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) { if (node == null) return; if(trace.refreshTree && !trace.node("TREE_NODE",node,true))return; result += node; repeat(node.childCount) { walk(node.getChild(it)) } }
        val root=uiAutomation.rootInActiveWindow
        trace.lastRootOwned=root?.packageName?.toString()==targetContext.packageName
        if(trace.lastRootOwned!=true)return emptyList()
        walk(root); return result
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun match(node: AccessibilityNodeInfo, label: String): Boolean {
        val text = node.text?.toString() ?: return false
        // A large whole-card Button can expose its merged text instead of the
        // individual opening label. Click the actual enabled Button ancestor.
        return text == label || text == "✓ $label" || text.split("\n").contains(label) || (label == "Weitere Funktionen" && text.startsWith(label))
    }
    private fun visible(label: String, backwards: Boolean = label.startsWith("Rundgang") || label in listOf("PaG Demo", "Lagebild", "Vorbereitung im Überblick")): AccessibilityNodeInfo {
        // Fixed capacity; entries contain only scalars encoded as strings, never nodes.
        val failedSearch = if (minimalHubObservation) arrayOfNulls<String>(50) else null
        repeat(50) { attempt ->
            val tree = nodes();trace.search(tree,label,backwards,attempt)
            if (!minimalHubObservation && trace.phase.startsWith("HC_") && (attempt in listOf(0,5,49) || (witness && attempt>=5)))
                trace.reachSnapshot(tree,label,witness)
            tree.find { match(it, label) && bounds(it).height() > 0 }?.let { return it }
            val scroll = tree.firstOrNull { it.isScrollable }
            // Read only metadata of nodes already returned by the original query.
            // No refresh, parent lookup, root query, event output or main-thread fence.
            val beforeDecision = if (failedSearch != null) {
                val target = tree.firstOrNull { match(it, label) }
                val targetBounds = target?.let(::bounds)
                val scrollBounds = scroll?.let(::bounds)
                fun geometry(rect: Rect?) = rect?.let { "${it.left}:${it.top}:${it.right}:${it.bottom}" } ?: "absent"
                val intersects = targetBounds != null && scrollBounds != null && Rect.intersects(targetBounds, scrollBounds)
                "attempt=$attempt;phase=${trace.phase};rootOwned=${trace.lastRootOwned};target=${target != null};" +
                    "targetPositive=${targetBounds?.let { it.width()>0 && it.height()>0 } == true};intersectsScroll=$intersects;" +
                    "targetBounds=${geometry(targetBounds)};scroll=${scroll != null};scrollBounds=${geometry(scrollBounds)};" +
                    "back=${scroll?.actionList?.any { it.id==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD } == true};" +
                    "forward=${scroll?.actionList?.any { it.id==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } == true};" +
                    "beforeDecision=${if(attempt>=5) if(backwards) "BACK" else "FORWARD" else "WAIT"}"
            } else null
            // Let the accessibility frame catch up before scrolling away from a
            // newly published heading. Tour/home headings live at the top.
            val accepted = if (attempt >= 5) trace.scroll(scroll,if(backwards) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) else null
            if (failedSearch != null) failedSearch[attempt] = "$beforeDecision;afterDecisionAccepted=${accepted ?: "notAttempted"}"
            waitForIdleSync(); SystemClock.sleep(80)
        }
        failureKind="UNREACHABLE"
        // After the original search has already failed: cannot turn failure into PASS.
        failedSearch?.forEach { entry ->
            if (entry != null) sendStatus(0, Bundle().apply { putString("stream", "VISIBLE_EXHAUSTED;$entry\n") })
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
            val tree = nodes();trace.search(tree,label,backwards,it)
            if (!minimalHubObservation && trace.phase in listOf("SG_E_GROUP_CLICK","SG_I_PERSON_OPEN") && it in listOf(0,49))
                trace.reachSnapshot(tree,label,witness)
            for (node in tree.filter { match(it, label) }) {
                var control: AccessibilityNodeInfo? = node
                while(control!=null){
                    if(!trace.node("CLICK_ANCESTOR",control,trace.refreshAncestors)){control=null;break}
                    if(control.isClickable)break
                    control=control.parent
                }
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
        failureKind="UNREACHABLE";error("Synthetic demo control not reachable")
    }
    private fun reportUncaught(error: Throwable) {
        sendStatus(0, Bundle().apply { putString("stream", "UI_UNCAUGHT," + error.javaClass.simpleName + "," + error.stackTrace.take(12).joinToString("|") { it.className + "." + it.methodName + ":" + it.lineNumber } + "\n") })
    }
    override fun onException(obj: Any?, error: Throwable): Boolean { reportUncaught(error); return super.onException(obj, error) }
    override fun onStart() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> runCatching { reportUncaught(error) }; originalHandler?.uncaughtException(thread, error) }
        var activity: MainActivity? = null; var scaleUnderTest = 1f; var phase = "entry"
        try {
            check(!BuildConfig.PAG_DEMO_ONLY);trace.start()
            val launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = launched;trace.attach(launched)
            val density = targetContext.resources.displayMetrics.density
            for (scale in (requestedScale?.let{listOf(it)}?:listOf(1f,2f))) {
                scaleUnderTest = scale;trace.font=scale
                val session = Session(); val ops = Ops()
                lateinit var model: ProductViewModel
                runOnMainSync {
                    model = ProductViewModel(session, Actions(), ProductStorageState.READY_TO_OPEN, People(), operationsRepository = ops, taskRepository = Tasks()) {}
                    trace.model=model;model.detail("synthetic")
                    launched.setContent { CompositionLocalProvider(LocalDensity provides Density(density, scale)) { trace.Content(model) } }
                }
                phase = "hub";trace.phase=phase; waitForIdleSync(); visible("Lagebild"); top(); click("Funktionen", backwards = false)
                visible("2 Personen vor Ort"); visible("1 mit Funktion"); visible("1 ohne Funktion")
                check(nodes().none { it.text?.toString()?.startsWith("Geplant:") == true })
                phase = "group";trace.phase=phase; click("Support · 1", backwards = false); visible("1 Person aktuell vor Ort")
                click("Motte", backwards = false); visible("Aktuelle Funktion: Support")
                phase = "change-function";trace.phase=phase; click("Funktion ändern", backwards = false); click("Blockierende", backwards = false); click("Übernehmen", backwards = false)
                visible("Aktuelle Funktion: Blockierende")
                runOnMainSync { check(ops.person.state!!.currentRoleId == "plan") }
                phase = "return-old-group";trace.phase=phase; top(); click("‹ Support"); visible("Derzeit niemand in dieser Funktion vor Ort.")
                top(); click("‹ Funktionen"); visible("Blockierende · 1")
                phase = "without";trace.phase=phase; click("Ohne Funktion · 1", backwards = false); visible("1 Person aktuell vor Ort")
                click("Klee", backwards = false); visible("Aktuelle Funktion: Keine Funktion")
                top(); click("‹ Ohne Funktion"); visible("1 Person aktuell vor Ort"); top(); click("‹ Funktionen")
                phase = "new-function";trace.phase=phase; top(); click("+ Neue Funktion", backwards = false); name("Übungsfunktion")
                runOnMainSync { check(model.operations.newFunctionName == "Übungsfunktion") }
                click("Funktion anlegen", backwards = false); top(); visible("Wer übernimmt gerade was?", backwards = true)
                phase = "other-functions";trace.phase=phase; click("Weitere Funktionen", backwards = false); visible("Übungsfunktion")
                runOnMainSync { check(model.operations.moreFunctionsExpanded && ops.roles.any { it.name == "Übungsfunktion" }) }
                fun step(name:String,work:()->Unit){phase=name;trace.phase=name;trace.event("BEGIN");work();trace.event("PASS")}
                step("SG_A_OVERVIEW"){runOnMainSync{check(model.activeArea==ActiveArea.FUNCTIONS && model.operations.screen==OperationsScreen.OVERVIEW)}}
                step("SG_B_DOMAIN_GROUP"){runOnMainSync{check(model.operations.board!!.persons.single{it.person.id=="p"}.state!!.currentRoleId=="plan")}}
                step("SG_C_GROUP_TREE"){if(trace.detailed)trace.search(nodes(),"Blockierende · 1",false,0)}
                step("SG_D_GROUP_REACH"){top();if(trace.detailed)trace.search(nodes(),"Blockierende · 1",false,0)}
                step("SG_E_GROUP_CLICK"){click("Blockierende · 1",backwards=false)}
                step("SG_F_GROUP_MODEL"){runOnMainSync{check(model.operations.screen==OperationsScreen.FUNCTION_DETAIL && model.operations.functionFocus?.roleId=="plan")}}
                step("SG_G_PERSON_DOMAIN"){runOnMainSync{check(model.operations.board!!.persons.any{it.person.id=="p" && it.state?.status==OperationalStatus.ON_SITE})};visible("1 Person aktuell vor Ort")}
                step("SG_H_PERSON_REACH"){if(trace.detailed)trace.search(nodes(),"Motte",false,0)}
                step("SG_I_PERSON_OPEN"){click("Motte",backwards=false);runOnMainSync{check(model.operations.personId=="p" && model.operations.screen==OperationsScreen.PERSON)}}
                step("SG_J_FUNCTION"){visible("Aktuelle Funktion: Blockierende")}
                phase="status";trace.phase=phase
                click("Lage ändern", backwards = false); visible("Lage aktualisieren"); click("In Gewahrsam", backwards = false); click("Übernehmen", backwards = false)
                visible("Aktuelle Lage: In Gewahrsam"); top(); click("‹ Blockierende"); visible("Derzeit niemand in dieser Funktion vor Ort.")
                top(); click("‹ Funktionen"); visible("1 Person vor Ort"); visible("0 mit Funktion"); visible("1 ohne Funktion")
                runOnMainSync { check(ops.person.state!!.currentRoleId == null && activeFunctionContent(model.operations.board!!).without.map { it.person.id } == listOf("q")) }
                if(minimalHubObservation){
                    // Original hub sequence, no extra main-thread checks/publications before search.
                    phase="HC_C_BACK_CLICK";trace.phase=phase;top();click("‹ Zum Lagebild")
                    phase="HC_L1_HEADING";trace.phase=phase;visible("Lagebild")
                    phase="HC_L2_CONTENT";trace.phase=phase;visible("Noch keine aktuellen Funktionen")
                }else{
                step("HC_A_COMMIT"){runOnMainSync{check(ops.statusCommits==2 && ops.person.state!!.status==OperationalStatus.IN_CUSTODY)}}
                step("HC_B_OPERATIONS"){runOnMainSync{check(model.operations.screen==OperationsScreen.OVERVIEW && model.operations.personId==null && model.operations.functionFocus==null)}}
                step("HC_C_BACK_CLICK"){top();click("‹ Zum Lagebild")}
                step("HC_D_ROUTE"){runOnMainSync{check(model.activeArea==ActiveArea.HUB && model.operations.screen==OperationsScreen.OVERVIEW)}}
                step("HC_E_WORKSPACE"){runOnMainSync{val workspace=checkNotNull(model.workspace);check(workspace.generation==session.value.generation && workspace.operations==model.operations.board && workspace.status==ActionStatus.ACTIVE && workspace.tasks!=null)}}
                step("HC_F_DOMAIN"){runOnMainSync{val content=activeFunctionContent(model.operations.board!!);check(content.without.size==1 && content.groups.isEmpty());val workspace=model.workspace!!;check(activeHubCards(model.selected!!,workspace.operations!!,workspace.tasks!!).single{it.area==ActiveArea.FUNCTIONS}.summary==listOf("Noch keine aktuellen Funktionen"))}}
                // Root draw counters are a measurement proxy, not a per-card Compose callback.
                step("HC_G_DRAW"){trace.event("DRAW_PROXY")}
                step("HC_H_TREE"){if(trace.detailed)trace.reachSnapshot(nodes(),"Noch keine aktuellen Funktionen",false)}
                step("HC_I_REFRESH"){if(witness)trace.reachSnapshot(nodes(),"Noch keine aktuellen Funktionen",true)}
                step("HC_J_GEOMETRY"){trace.event("VIEWPORT")}
                step("HC_K_FEEDBACK"){trace.event("FEEDBACK")}
                step("HC_L1_HEADING"){visible("Lagebild")}
                step("HC_L2_CONTENT"){visible("Noch keine aktuellen Funktionen")}
                }
                phase = "lock-detail";trace.phase=phase; top(); click("Funktionen", backwards = false); click("Ohne Funktion · 1", backwards = false)
                runOnMainSync { session.lock(); check(model.workspace == null && model.operations.functionFocus == null && model.operations.personId == null) }
                visible("PaG")
                phase = "lock-form";trace.phase=phase; runOnMainSync {
                    session.value = SessionSnapshot(SessionState.READY, session.value.generation + 1); session.observer!!(session.value)
                    model.detail("synthetic"); model.activeArea(ActiveArea.FUNCTIONS)
                }
                waitForIdleSync(); top(); click("+ Neue Funktion", backwards = false); name("Verworfener Übungsentwurf")
                runOnMainSync { session.lock(); check(model.operations.newFunctionName.isEmpty() && model.operations.functionFocus == null && !model.operations.moreFunctionsExpanded) }
                visible("PaG")
            }
            trace.stop();runOnMainSync { launched.finish() }
            finish(-1, Bundle().apply { putString("stream", "UX3d synthetic real ProductScreen: hub/distribution/group/person/function change/return empty group/without/new function/other/status/hub/lock detail+form/font=${requestedScale ?: "1+2"}: OK\n") })
        } catch (failure: Exception) {
            trace.event("FAIL","category=$failureKind;exception=${failure.javaClass.simpleName}");trace.stop();activity?.let { runOnMainSync { it.finish() } }
            finish(0, Bundle().apply { putString("stream", "UX3d probe failed; font=$scaleUnderTest; phase=$phase; category=$failureKind; lines=${failure.stackTrace.filter { it.className == Ux3dFunctionsProbe::class.java.name }.take(5).map { it.lineNumber }}; $clickDiagnostic; no input data logged\n") })
        }
    }
}
