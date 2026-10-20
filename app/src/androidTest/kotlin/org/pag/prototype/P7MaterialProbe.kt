package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.pag.domain.*
import org.pag.prototype.product.*

/** Real ProductScreen and native accessibility actions, entirely synthetic ports. */
class P7MaterialProbe : Instrumentation() {
    private var requestedScale: Float? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); requestedScale = arguments?.getString("font")?.toFloat(); start() }
    private class Session : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1); var observer: ((SessionSnapshot) -> Unit)? = null
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription {} }
        override fun authenticate(experiment: Experiment) { error("No auth in material probe") }
        override fun testAccess() {}
        override fun userInteraction() {}
        override fun lock() { value = SessionSnapshot(SessionState.LOCKED, value.generation + 1); observer!!(value) }
    }
    private class Actions(phase: ActionStatus = ActionStatus.PREPARATION) : ActionRepository {
        var row = Action("synthetic", "Übungsaktion", "Nur synthetische Probe", 1, 2, 0, phase)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row)
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { error("No P7 action creation") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { error("No P7 action edit") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            check(expected == row.status); row = row.copy(status = ActionLifecycle.next(expected)!!, completedAt = 10L.takeIf { expected == ActionStatus.ACTIVE })
            publish(ActionChangeResult.Applied(row))
        }
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { error("Operations supplies people") }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) { error("No P7 person detail") }
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P7 person write") }
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P7 person write") }
    }
    private class Ops(private val actions: Actions) : OperationsRepository {
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) = publish(OperationsResult.Value(
            OperationalBoard(actionId, actions.row.status, emptyList(), listOf(OperationalPerson(Person("p", actionId, "Motte", 0),
                PersonRolePlan(emptySet(), null), PersonOperationalState(actionId, "p", null, OperationalStatus.ON_SITE, null, 0))))))
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P7 P4 mutation") }
        override fun renameRole(actionId: String, expected: Role, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P7 P4 mutation") }
        override fun deleteRole(actionId: String, expected: Role, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 P4 mutation") }
        override fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 P4 mutation") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 P4 mutation") }
    }
    private class Tasks : TaskRepository {
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) = publish(OperationsResult.Value(TaskBoard(actionId, listOf(
            Task("t", actionId, "Synthetische Aufgabe", TaskPriority.NORMAL, TaskStatus.DONE, null, null, null, null, null, 0, 1, 1),
            Task("cancelled", actionId, "Synthetische entfallene Aufgabe", TaskPriority.NORMAL, TaskStatus.CANCELLED, null, null, null, null, null, 0, 1, null)), emptyList())))
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P7 task mutation") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P7 task mutation") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No P7 task mutation") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No P7 task mutation") }
    }
    private class Protocols : ProtocolRepository {
        override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) = publish(ProtocolResult.Value(ProtocolSummary(0, null)))
        override fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit) { error("No P7 protocol timeline") }
        override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) { error("No P7 protocol detail") }
        override fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P7 protocol write") }
        override fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P7 protocol write") }
        override fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P7 protocol write") }
    }
    private class Materials(private val actions: Actions) : MaterialRepository {
        var items = emptyList<MaterialItem>(); var writes = 0; var boardReads = 0; var completedBoardReads = 0; var completedSummaryReads = 0
        override fun summary(actionId: String, publish: (OperationsResult<MaterialSummary>) -> Unit) {
            if (actions.row.status == ActionStatus.COMPLETED) completedSummaryReads++
            publish(OperationsResult.Value(MaterialSummary(items.size)))
        }
        override fun board(actionId: String, publish: (OperationsResult<MaterialBoard>) -> Unit) {
            check(MaterialRules.mayRead(actions.row.status)); boardReads++; if (actions.row.status == ActionStatus.COMPLETED) completedBoardReads++
            publish(OperationsResult.Value(MaterialBoard(actionId, items.sortedWith(MaterialRules.order))))
        }
        override fun create(actionId: String, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit) {
            check(MaterialRules.mayWrite(actions.row.status) && MaterialRules.valid(input)); writes++
            val i = MaterialRules.normalized(input)
            val item = MaterialItem("m$writes", actionId, i.name, i.quantityLabel, i.placeLabel, i.responsiblePersonId, i.linkedTaskId, i.sourceTemplateKey, writes.toLong())
            items = items + item; publish(OperationsResult.Value(item))
        }
        override fun edit(actionId: String, expected: MaterialItem, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit) {
            check(MaterialRules.mayWrite(actions.row.status) && items.contains(expected) && MaterialRules.valid(input)); writes++
            val i = MaterialRules.normalized(input)
            val next = expected.copy(name = i.name, quantityLabel = i.quantityLabel, placeLabel = i.placeLabel, responsiblePersonId = i.responsiblePersonId, linkedTaskId = i.linkedTaskId)
            items = items.map { if (it.id == expected.id) next else it }; publish(OperationsResult.Value(next))
        }
        override fun delete(actionId: String, expected: MaterialItem, publish: (OperationsResult<Unit>) -> Unit) {
            check(MaterialRules.mayDelete(actions.row.status) && items.contains(expected)); writes++
            items = items.filterNot { it.id == expected.id }; publish(OperationsResult.Value(Unit))
        }
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) { if (node == null || !node.refresh()) return; result += node; repeat(node.childCount) { walk(node.getChild(it)) } }
        walk(uiAutomation.rootInActiveWindow); return result
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun match(node: AccessibilityNodeInfo, label: String): Boolean {
        val text = node.text?.toString().orEmpty()
        return text.replace(Regex("\\s+"), " ").trim() == label || text == "✓ $label" || text.split("\n").contains(label) || node.hintText?.toString() == label
    }
    private fun scroll(backwards: Boolean): Boolean {
        val node = nodes().firstOrNull { it.isScrollable } ?: return false
        val changed = node.performAction(if (backwards) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        waitForIdleSync(); SystemClock.sleep(100); return changed
    }
    private fun top() { repeat(60) { if (!scroll(true)) return } }
    private fun bottom() { repeat(60) { if (!scroll(false)) return } }
    private fun visible(label: String, backwards: Boolean = label in listOf("PaG", "Lagebild", "Abgeschlossen", "Material hinzufügen", "Material bearbeiten")): AccessibilityNodeInfo {
        repeat(80) { attempt ->
            nodes().firstOrNull { match(it, label) && bounds(it).height() > 0 }?.let { return it }
            if (attempt > 3) scroll(backwards)
            waitForIdleSync(); SystemClock.sleep(100)
        }
        error("Material label unreachable")
    }
    private fun click(label: String, backwards: Boolean = label.startsWith("‹") || label == "Übersicht") {
        repeat(80) {
            nodes().filter { match(it, label) }.forEach { node ->
                var control: AccessibilityNodeInfo? = node
                while (control != null && !control.isClickable) control = control.parent
                if (control != null && control.isEnabled && bounds(control).height() > 0) {
                    val rect = bounds(control); check(rect.left >= 0 && rect.right <= targetContext.resources.displayMetrics.widthPixels)
                    if (control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { waitForIdleSync(); SystemClock.sleep(150); return }
                }
            }
            scroll(backwards); waitForIdleSync(); SystemClock.sleep(100)
        }
        error("Material control unreachable")
    }
    private fun field(label: String, value: String) {
        var editable: AccessibilityNodeInfo? = visible(label)
        while (editable != null && !editable.isEditable) editable = editable.parent
        check(editable != null)
        check(editable!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        waitForIdleSync(); editable!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id); waitForIdleSync()
    }
    private fun privateHub() {
        val forbidden = listOf("Klebstoff", "2 Tuben", "Materialkiste", "Motte", "Synthetische Aufgabe", "Synthetische Plane")
        check(nodes().none { n -> forbidden.any { (n.text?.toString().orEmpty() + n.contentDescription?.toString().orEmpty()).contains(it) } })
    }
    override fun onStart() {
        var activity: MainActivity? = null; var phase = "entry"; var scaleUnderTest = 1f
        try {
            check(!BuildConfig.PAG_DEMO_ONLY)
            val launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = launched; val density = targetContext.resources.displayMetrics.density
            for (scale in listOf(1f, 2f).filter { requestedScale == null || it == requestedScale }) {
                scaleUnderTest = scale
                val session = Session(); val actions = Actions(); val port = Materials(actions)
                lateinit var model: ProductViewModel
                runOnMainSync {
                    model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, People(),
                        operationsRepository = Ops(actions), taskRepository = Tasks(), protocolRepository = Protocols(), materialRepository = port) {}
                    model.detail("synthetic")
                    launched.setContent { CompositionLocalProvider(LocalDensity provides Density(density, scale)) { ProductScreen(model) } }
                }
                phase = "preparation-area"; waitForIdleSync(); click("Material")
                phase = "preparation-template"; click("Klebstoff"); visible("Material hinzufügen")
                runOnMainSync { check(port.writes == 0 && model.material.input!!.sourceTemplateKey == "GLUE") }
                phase = "preparation-fields"; field("Menge / Anzahl · optional", "2 Tuben"); field("Ort · optional", "Materialkiste")
                click("Motte"); click("Synthetische Aufgabe · Erledigt"); click("Speichern")
                visible("Menge: 2 Tuben"); visible("Ort: Materialkiste"); visible("Verantwortlich: Motte"); visible("Aufgabe: Synthetische Aufgabe")
                runOnMainSync { check(port.writes == 1 && port.items.single().linkedTaskId == "t") }
                phase = "edit-detach-person"; click("Menge: 2 Tuben"); visible("Material bearbeiten"); click("Keine verantwortliche Person"); click("Speichern")
                runOnMainSync { check(port.items.single().responsiblePersonId == null && port.items.single().linkedTaskId == "t" && port.items.single().sourceTemplateKey == "GLUE") }
                phase = "delete-cancel"; click("Menge: 2 Tuben"); click("Material entfernen")
                visible("Der Materialeintrag wird entfernt. Verknüpfte Person und Aufgabe bleiben bestehen.")
                click("Eintrag behalten"); click("Abbrechen"); runOnMainSync { check(port.writes == 2 && port.items.size == 1) }
                phase = "active-hub"; top(); click("Übersicht"); val readsBeforeHub = port.boardReads
                click("Aktion starten"); click("Bestätigen"); visible("Lagebild"); privateHub(); visible("1 Materialeinträge")
                runOnMainSync { check(port.boardReads == readsBeforeHub && model.material.board == null) }
                phase = "active-create"; click("Material öffnen"); click("+ Material hinzufügen"); field("Bezeichnung", "Synthetische Plane"); click("Speichern")
                runOnMainSync { check(port.items.size == 2 && port.items.last().sourceTemplateKey == null && model.workspace!!.materialSummary == MaterialSummary(2)) }
                top(); click("‹ Zum Lagebild"); visible("2 Materialeinträge"); privateHub()
                phase = "completed-no-prefetch"; click("Aktion abschließen"); click("Bestätigen"); visible("Abgeschlossen")
                bottom(); top(); runOnMainSync { check(port.completedBoardReads == 0 && port.completedSummaryReads == 0 && !model.completedMaterialOpen) }
                phase = "completed-readonly"; click("Material ansehen"); visible("Materialliste"); visible("Menge: 2 Tuben"); bottom(); visible("Synthetische Plane")
                check(nodes().none { it.isClickable && listOf("+ Material hinzufügen", "Material entfernen", "Speichern", "Entfernen bestätigen").any { label -> match(it, label) } })
                runOnMainSync { val writes = port.writes; model.material.create(); model.material.edit(port.items.first()); model.material.save(); model.material.requestDelete(); model.material.delete(); check(port.writes == writes && !model.material.writable) }
                top(); click("‹ Zur abgeschlossenen Aktion"); runOnMainSync { check(model.material.board == null && !model.completedMaterialOpen) }
                phase = "lock-form"
                runOnMainSync {
                    val active = Actions(ActionStatus.ACTIVE)
                    model = ProductViewModel(session, active, ProductStorageState.READY_TO_OPEN, People(), operationsRepository = Ops(active), taskRepository = Tasks(), protocolRepository = Protocols(), materialRepository = Materials(active)) {}
                    model.detail("synthetic"); model.activeArea(ActiveArea.MATERIAL); model.material.create(MaterialRules.templates.first())
                    launched.setContent { CompositionLocalProvider(LocalDensity provides Density(density, scale)) { ProductScreen(model) } }
                }
                waitForIdleSync(); click("Sperren", backwards = true); visible("PaG")
                runOnMainSync { check(model.snapshot.state == SessionState.LOCKED && model.material.board == null && model.material.input == null && model.material.expected == null && !model.material.confirmingDelete) }
                privateHub()
            }
            runOnMainSync { launched.finish() }
            finish(-1, Bundle().apply { putString("stream", "P7 synthetic ProductScreen: preparation/template/fields/person/task/create/edit/delete cancel/active summary privacy/free create/completed no prefetch/readonly/lock/font=${requestedScale?.toString() ?: "1+2"}: OK\n") })
        } catch (failure: Exception) {
            activity?.let { runOnMainSync { it.finish() } }
            finish(0, Bundle().apply { putString("stream", "P7 material probe failed; font=$scaleUnderTest; phase=$phase; lines=${failure.stackTrace.filter { it.className == P7MaterialProbe::class.java.name }.take(5).map { it.lineNumber }}; no material/input data logged\n") })
        }
    }
}
