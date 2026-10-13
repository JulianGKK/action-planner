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
import org.pag.prototype.product.*

/** Real ProductScreen with synthetic ports only. No credentials, DB writes or system font change. */
class Ux3cTasksProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private class Session : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1)
        var observer: ((SessionSnapshot) -> Unit)? = null
        var interactions = 0
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription {} }
        override fun authenticate(experiment: Experiment) { error("No authentication in synthetic probe") }
        override fun testAccess() {}
        override fun userInteraction() { interactions++ }
        override fun lock() { value = SessionSnapshot(SessionState.LOCKED, value.generation + 1); observer!!(value) }
    }
    private class Actions : ActionRepository {
        val row = Action("synthetic", "Übungsaktion", "Nur synthetische Probe", 1, 2, 0, ActionStatus.ACTIVE)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row)
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {}
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {}
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) = publish(PersonResult.Value(emptyList()))
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(Person(personId, actionId, "Motte", 0)))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private class Ops : OperationsRepository {
        val roles = listOf(Role("plan", "synthetic", "Blockierende", 0), Role("current", "synthetic", "Support", 0))
        var person = OperationalPerson(Person("p", "synthetic", "Motte", 0), PersonRolePlan(setOf("plan"), "plan"),
            PersonOperationalState("synthetic", "p", "current", OperationalStatus.ON_SITE, null, System.currentTimeMillis()))
        var reads = 0
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { reads++; publish(OperationsResult.Value(OperationalBoard(actionId, ActionStatus.ACTIVE, roles, listOf(person)))) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) {}
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) {}
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No planning in ACTIVE") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) {
            check(expected == person.state)
            person = person.copy(state = expected.copy(status = status, currentRoleId = roleId.takeIf { OperationalRules.roleAllowed(status) }, currentPlaceLabel = OperationalRules.place(place, status), updatedAt = System.currentTimeMillis()))
            publish(OperationsResult.Value(Unit))
        }
    }
    private class Tasks : TaskRepository {
        var rows = listOf(
            Task("t", "synthetic", "Übungsaufgabe A", TaskPriority.HIGH, TaskStatus.OPEN, null, "current", "p", null, null, 1, 1, null),
            Task("other", "synthetic", "Übungsaufgabe B", TaskPriority.NORMAL, TaskStatus.IN_PROGRESS, "p", null, null, null, null, 2, 2, null),
            Task("closed", "synthetic", "Übungsaufgabe erledigt", TaskPriority.LOW, TaskStatus.DONE, null, null, "p", null, null, 3, 3, 3))
        var events = listOf(TaskEvent("created", "synthetic", "t", TaskEventType.CREATED, 1))
        private var serial = 0
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) = publish(OperationsResult.Value(TaskBoard(actionId, rows, events)))
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {
            val now = System.currentTimeMillis()
            val t = Task("new-${++serial}", actionId, input.title, input.priority, TaskStatus.OPEN, input.assigneePersonId, input.assigneeRoleId, input.affectedPersonId, input.dueAt, input.sourceTemplateKey, now, now, null)
            rows = rows + t; events = events + TaskEvent("create-$serial", actionId, t.id, TaskEventType.CREATED, now); publish(OperationsResult.Value(t))
        }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) {
            check(rows.single { it.id == expected.id } == expected)
            val t = expected.copy(title = input.title, priority = input.priority, assigneePersonId = input.assigneePersonId, assigneeRoleId = input.assigneeRoleId, affectedPersonId = input.affectedPersonId, dueAt = input.dueAt, updatedAt = System.currentTimeMillis())
            rows = rows.map { if (it.id == t.id) t else it }; publish(OperationsResult.Value(t))
        }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) {
            check(rows.single { it.id == expected.id } == expected)
            val now = System.currentTimeMillis(); val t = TaskRules.transition(expected, status, now)!!
            rows = rows.map { if (it.id == t.id) t else it }
            events = events + TaskEvent("event-${++serial}", actionId, t.id, TaskRules.event(expected.status, status)!!, now)
            publish(OperationsResult.Value(t))
        }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No ACTIVE delete") }
    }
    private fun title(value: String) {
        var field: AccessibilityNodeInfo? = null
        repeat(50) {
            field = nodes().firstOrNull { it.isEditable }
            if (field != null) return@repeat
            waitForIdleSync(); SystemClock.sleep(80)
        }
        check(field != null)
        check(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        waitForIdleSync()
        // Finish text entry like the keyboard's Done action; preserve the form.
        field!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        waitForIdleSync()
    }
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
        return text == label || text == "✓ $label" || text.split("\n").contains(label) || (label == "Erledigt / Entfallen" && text.startsWith(label))
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
    override fun onStart() {
        var activity: MainActivity? = null; var scaleUnderTest = 1f; var phase = "entry"
        try {
            check(!BuildConfig.PAG_DEMO_ONLY)
            val launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = launched
            val density = targetContext.resources.displayMetrics.density
            for (scale in listOf(1f, 2f)) {
                scaleUnderTest = scale
                val session = Session(); val ops = Ops(); val tasks = Tasks()
                lateinit var model: ProductViewModel
                runOnMainSync {
                    model = ProductViewModel(session, Actions(), ProductStorageState.READY_TO_OPEN, People(), operationsRepository = ops, taskRepository = tasks) {}
                    model.detail("synthetic")
                    launched.setContent { CompositionLocalProvider(LocalDensity provides Density(density, scale)) { ProductScreen(model) } }
                }
                phase = "hub"; waitForIdleSync(); visible("Lagebild"); top(); click("Aufgaben", backwards = false)
                visible("1 offen · 1 in Arbeit"); visible("Übungsaufgabe A")
                check(nodes().none { it.isClickable && it.text?.toString() in listOf("Erledigt", "In Arbeit", "Entfällt") })
                click("Übungsaufgabe A", backwards = true); visible("Zuständig: Support")
                phase = "start"; click("In Arbeit", backwards = false); visible("Hoch · In Arbeit")
                phase = "done"; click("Erledigt", backwards = false); visible("Hoch · Erledigt")
                runOnMainSync { check(model.tasks.selected!!.status == TaskStatus.DONE && model.tasks.board!!.events.any { it.type == TaskEventType.COMPLETED }) }
                phase = "reopen"; click("Wieder öffnen", backwards = false); visible("Hoch · Offen")
                phase = "edit"; click("Aufgabe bearbeiten", backwards = false); title("Übungsaufgabe geändert")
                runOnMainSync { check(model.tasks.form && model.tasks.input.title == "Übungsaufgabe geändert") }
                click("Speichern", backwards = false); visible("Übungsaufgabe geändert")
                runOnMainSync { check(model.tasks.selected!!.title == "Übungsaufgabe geändert" && !model.tasks.form) }
                top(); click("‹ Aufgaben"); visible("1 offen · 1 in Arbeit")
                phase = "closed"; click("Erledigt / Entfallen", backwards = false); visible("Übungsaufgabe erledigt")
                runOnMainSync { check(model.tasks.closedExpanded) }
                click("Übungsaufgabe erledigt", backwards = false); visible("Niedrig · Erledigt")
                top(); click("‹ Aufgaben")
                phase = "create"; top(); click("+ Aufgabe", backwards = false); title("Neue Übungsaufgabe")
                runOnMainSync { check(model.tasks.input.priority == TaskPriority.NORMAL && model.tasks.input.title == "Neue Übungsaufgabe") }
                click("Speichern", backwards = false); visible("2 offen · 1 in Arbeit")
                runOnMainSync { check(tasks.rows.any { it.title == "Neue Übungsaufgabe" && it.status == TaskStatus.OPEN }) }
                phase = "person-filter"; top(); click("‹ Zum Lagebild"); visible("Lagebild"); top(); click("Personen", backwards = false)
                visible("Motte"); click("Motte", backwards = true); visible("Aktuelle Funktion: Support")
                click("Aufgaben ansehen", backwards = false); visible("Filter · Betrifft: Motte")
                runOnMainSync { check(model.tasks.filter == "p" && activeTaskContent(model.tasks.board!!, "p").active.map { it.id } == listOf("t")) }
                click("Alle Aufgaben anzeigen", backwards = false); visible("2 offen · 1 in Arbeit")
                runOnMainSync { check(model.tasks.filter == null) }
                phase = "lock-detail"; top(); click("Übungsaufgabe geändert", backwards = false)
                runOnMainSync { session.lock(); check(model.tasks.selected == null && model.workspace == null && model.tasks.filter == null) }
                visible("PaG")
                phase = "lock-form"; runOnMainSync {
                    session.value = SessionSnapshot(SessionState.READY, session.value.generation + 1); session.observer!!(session.value)
                    model.detail("synthetic"); model.activeArea(ActiveArea.TASKS)
                }
                waitForIdleSync(); top(); click("+ Aufgabe", backwards = false); title("Verworfener Übungsentwurf")
                runOnMainSync { session.lock(); check(!model.tasks.form && model.tasks.input == TaskInput("") && !model.tasks.closedExpanded) }
                visible("PaG")
            }
            runOnMainSync { launched.finish() }
            finish(-1, Bundle().apply { putString("stream", "UX3c synthetic real ProductScreen: hub/tasks/card/start/done/reopen/edit/create/closed/person-filter/back/lock detail+form/font 1+2: OK\n") })
        } catch (failure: Exception) {
            activity?.let { runOnMainSync { it.finish() } }
            finish(0, Bundle().apply { putString("stream", "UX3c probe failed; font=$scaleUnderTest; phase=$phase; lines=${failure.stackTrace.filter { it.className == Ux3cTasksProbe::class.java.name }.take(5).map { it.lineNumber }}; $clickDiagnostic; no input data logged\n") })
        }
    }
}
