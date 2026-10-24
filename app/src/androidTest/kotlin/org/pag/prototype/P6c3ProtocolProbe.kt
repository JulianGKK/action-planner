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

/** Real ProductScreen with synthetic ports only. No credentials, DB writes or system font change. */
class P6c3ProtocolProbe : Instrumentation() {
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
        val row = Action("synthetic", "Übungsaktion", "Nur synthetische Probe", 1, 2, 0, ActionStatus.COMPLETED)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row)
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { error("No completed action mutation") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) { error("No completed action mutation") }
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) = publish(PersonResult.Value(emptyList()))
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(Person(personId, actionId, "Motte", 0, editRevision = 0)))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private class Ops : OperationsRepository {
        var roles = listOf(Role("plan", "synthetic", "Blockierende", 0, editRevision = 0), Role("current", "synthetic", "Support", 0, editRevision = 0))
        var person = OperationalPerson(Person("p", "synthetic", "Motte", 0, editRevision = 0), PersonRolePlan(setOf("plan"), "plan", editRevision = 0),
            PersonOperationalState("synthetic", "p", "current", OperationalStatus.ON_SITE, null, System.currentTimeMillis(), editRevision = 0))
        var other = OperationalPerson(Person("q", "synthetic", "Klee", 0, editRevision = 0), PersonRolePlan(emptySet(), null, editRevision = 0), PersonOperationalState("synthetic", "q", null, OperationalStatus.ON_SITE, null, 0, editRevision = 0))
        var reads = 0
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { reads++; publish(OperationsResult.Value(OperationalBoard(actionId, ActionStatus.COMPLETED, roles, listOf(person, other)))) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4 mutation") }
        override fun renameRole(actionId: String, expected: Role, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4 mutation") }
        override fun deleteRole(actionId: String, expected: Role, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4 mutation") }
        override fun planRoles(actionId: String, personId: String, expected: ExpectedPersonRolePlan, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No planning in ACTIVE") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4 mutation") }
    }
    private class Tasks : TaskRepository {
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) = publish(OperationsResult.Value(TaskBoard(actionId, emptyList(), emptyList())))
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No task operation") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No task operation") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No task operation") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No task operation") }
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
        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) { if (node == null || !node.refresh()) return; result += node; repeat(node.childCount) { walk(node.getChild(it)) } }
        walk(uiAutomation.rootInActiveWindow); return result
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun match(node: AccessibilityNodeInfo, label: String): Boolean {
        val text = node.text?.toString() ?: return false
        // A large whole-card Button can expose its merged text instead of the
        // individual opening label. Click the actual enabled Button ancestor.
        return text.replace(Regex("\\s+"), " ").trim() == label || text == "✓ $label" || text.split("\n").contains(label) || (label == "Weitere Funktionen" && text.startsWith(label))
    }
    private fun visible(label: String): AccessibilityNodeInfo {
        repeat(50) { attempt ->
            val tree = nodes(); tree.find { match(it, label) && bounds(it).height() > 0 }?.let { return it }
            // Let the accessibility frame catch up before scrolling away from a
            // newly published heading. Tour/home headings live at the top.
            if (attempt >= 5) smallScroll(label in listOf("PaG", "Lagebild", "Schlüsselereignis erfassen", "Eintrag korrigieren", "Eintrag ergänzen"))
            waitForIdleSync(); SystemClock.sleep(80)
        }
        error("P6c3 label not reachable")
    }
    private fun smallScroll(backwards: Boolean) {
        val scroll = nodes().filter { it.isScrollable }.maxByOrNull { bounds(it).height() } ?: return
        scroll.performAction(if (backwards) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        waitForIdleSync(); SystemClock.sleep(100)
    }
    private fun top() { repeat(25) { val scroll = nodes().firstOrNull { it.isScrollable } ?: return; if (!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return; waitForIdleSync(); SystemClock.sleep(50) } }
    private fun bottom() { repeat(40) { val scroll = nodes().firstOrNull { it.isScrollable } ?: return; if (!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return; waitForIdleSync(); SystemClock.sleep(80) } }
    private var clickDiagnostic = ""
    private fun click(label: String, backwards: Boolean = label.startsWith("‹") || label in listOf("Übersicht", "Personen", "Aufgaben", "Rollen")) {
        repeat(50) {
            val tree = nodes()
            for (node in tree.filter { match(it, label) }) {
                var control: AccessibilityNodeInfo? = node
                while (control != null && !control.isClickable) control = control.parent
                if (control != null && control.isEnabled && bounds(control).height() > 0) {
                    val rect = bounds(control); check(rect.left >= 0 && rect.right <= targetContext.resources.displayMetrics.widthPixels)
                    if (control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { waitForIdleSync(); return }
                }
            }
            smallScroll(backwards)
            waitForIdleSync(); SystemClock.sleep(180)
        }
        val finalTree = nodes()
        clickDiagnostic = "scroll=" + finalTree.filter { it.isScrollable }.joinToString { "${it.className}:${bounds(it)}:actions=${it.actionList.map { a -> a.id }}" } + ";nodes=${finalTree.size},scrolls=${finalTree.count { it.isScrollable }},matches=${finalTree.count { match(it, label) }},buttons=" +
            finalTree.filter { it.isClickable }.take(12).joinToString { "${bounds(it)}:len=${it.text?.length ?: 0}:opens=${it.text?.contains(" öffnen") == true}" }
        error("P6c3 control not reachable")
    }
    private class Protocols : ProtocolRepository {
        val entries = mutableMapOf<String, ProtocolEntryDetail>().apply {
            val input = ProtocolInput(ProtocolClassification.KEY_EVENT, "Synthetischer ACTIVE-Ursprung", 1000, officialIdentifiers = listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, "SYNTHETIC-ONLY-ID")))
            val r = ProtocolRevision("active-original", "synthetic", "origin", 1, ProtocolRevisionType.ORIGINAL, input, 2000, false, false)
            put("origin", ProtocolEntryDetail(ProtocolEntry("origin", "synthetic", false), listOf(ProtocolRevisionDetail(r, emptyList(), null))))
        }
        var writes = 0; var detailReads = 0; var timelineReads = 0; var summaryReads = 0
        override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) { summaryReads++; error("No completed protocol summary") }
        override fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit) { timelineReads++; publish(ProtocolResult.Value(entries.values.map { d -> val r = d.current.revision; ProtocolTimelineItem(d.entry.id, r.id, r.revisionNumber, r.revisionType, r.input.classification, r.input.templateKey, r.input.title, r.input.occurredAt, r.input.occurredAtApproximate, r.recordedAt, r.recordedAfterCompletion, d.entry.createdAfterCompletion, r.mistaken, d.revisions.first().revision.recordedAt) }.sortedWith(compareBy<ProtocolTimelineItem> { it.occurredAt }.thenBy { it.originalRecordedAt }.thenBy { it.entryId }))) }
        override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) { detailReads++; publish(entries[entryId]?.let { ProtocolResult.Value(it) } ?: ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND)) }
        override fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput, publish: (ProtocolResult<ProtocolSaved>) -> Unit) {
            check(expectedActionStatus == ActionStatus.COMPLETED && ProtocolRules.valid(input)); writes++
            val id = "synthetic-$writes"; val r = ProtocolRevision("r-$writes", actionId, id, 1, ProtocolRevisionType.ORIGINAL, ProtocolRules.normalized(input), System.currentTimeMillis(), true, false)
            entries[id] = ProtocolEntryDetail(ProtocolEntry(id, actionId, true), listOf(ProtocolRevisionDetail(r, emptyList(), null)))
            publish(ProtocolResult.Value(ProtocolSaved(id, r.id, r.revisionNumber)))
        }
        override fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { check(expectedActionStatus == ActionStatus.COMPLETED); append(expectedRevision, revisionType, input, clearMistaken, publish) }
        override fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { check(expectedActionStatus == ActionStatus.COMPLETED); append(expectedRevision, ProtocolRevisionType.MARKED_MISTAKEN, expectedRevision.revision.input, false, publish) }
        private fun append(expected: ProtocolExpectedRevision, type: ProtocolRevisionType, input: ProtocolInput, clear: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) {
            val d = entries.getValue(expected.entry.id); check(d.current.revision == expected.revision && ProtocolRules.valid(input)); writes++
            val r = expected.revision.copy(id = "r-$writes", revisionNumber = expected.revision.revisionNumber + 1, revisionType = type, input = ProtocolRules.normalized(input), mistaken = ProtocolRules.nextMistaken(expected.revision.mistaken, type, clear)!!, recordedAt = System.currentTimeMillis(), recordedAfterCompletion = true)
            entries[d.entry.id] = d.copy(revisions = d.revisions + ProtocolRevisionDetail(r, emptyList(), null)); publish(ProtocolResult.Value(ProtocolSaved(d.entry.id, r.id, r.revisionNumber)))
        }
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
                val session = Session(); val port = Protocols()
                lateinit var model: ProductViewModel
                runOnMainSync {
                    model = ProductViewModel(session, Actions(), ProductStorageState.READY_TO_OPEN, People(), operationsRepository = Ops(), taskRepository = Tasks(), protocolRepository = port) {}
                    model.detail("synthetic")
                    launched.setContent { CompositionLocalProvider(LocalDensity provides Density(density, scale)) { ProductScreen(model) } }
                }
                phase = "completed-page"; waitForIdleSync(); visible("Abgeschlossen")
                runOnMainSync { check(port.timelineReads == 0 && port.detailReads == 0 && port.writes == 0 && port.summaryReads == 0 && !model.completedProtocolOpen) }
                click("Protokoll ansehen / nachbereiten", backwards = false); visible("Protokoll nachbereiten")
                phase = "origin"; visible("Chronologie"); click("Synthetischer ACTIVE-Ursprung", backwards = false)
                top(); check(nodes().none { it.text?.toString() == "Nach Abschluss erfasst" })
                click("Korrigieren", backwards = false); visible("Diese Korrektur wird als neue Fassung nach Abschluss gespeichert.")
                click("Speichern", backwards = false); visible("Diese Fassung wurde nach Abschluss erfasst.")
                runOnMainSync { check(!model.protocol.detail!!.entry.createdAfterCompletion && model.protocol.detail!!.current.revision.recordedAfterCompletion) }
                phase = "history"; click("Frühere Fassungen (1)", backwards = false); click("Fassung 1 · Original", backwards = false)
                top(); check(nodes().none { it.text?.toString() == "Diese Fassung wurde nach Abschluss erfasst." })
                runOnMainSync { check(model.protocol.historical!!.revision.recordedAfterCompletion == false) }
                click("‹ Zum aktuellen Eintrag")
                phase = "new-followup"; top(); click("‹ Protokoll"); top(); click("+ Nachtrag nach Abschluss", backwards = false)
                visible("Nach Abschluss erfasst"); name("Synthetischer Nachtrag")
                runOnMainSync { check(model.protocol.detail == null && model.protocol.form && port.writes == 1) }
                click("Speichern", backwards = false); visible("Nach Abschluss erfasst")
                runOnMainSync { check(model.protocol.detail!!.entry.createdAfterCompletion && model.protocol.detail!!.current.revision.recordedAfterCompletion) }
                top(); click("‹ Protokoll"); visible("Chronologie"); visible("Nach Abschluss erfasst")
                phase = "template-cancel"; top(); click("Polizei eingetroffen", backwards = false)
                visible("Nach Abschluss erfasst"); runOnMainSync { check(port.writes == 2) }
                click("Abbrechen", backwards = false); visible("Chronologie")
                phase = "addendum"; click("Synthetischer ACTIVE-Ursprung", backwards = false); click("Ergänzen", backwards = false)
                visible("Diese Ergänzung wird als neue Fassung nach Abschluss gespeichert."); click("Speichern", backwards = false)
                visible("Diese Fassung wurde nach Abschluss erfasst.")
                phase = "mistaken"; click("Als irrtümlich markieren", backwards = false)
                visible("Die neue Irrtumsmarkierung wird nach Abschluss erfasst."); runOnMainSync { check(port.writes == 3) }
                click("Irrtümlich markieren", backwards = false); visible("Irrtümlich markiert")
                visible("Sonstige sichtbare amtliche Kennung: SYNTHETIC-ONLY-ID")
                phase = "privacy"; top(); click("‹ Protokoll"); bottom()
                check(nodes().none { it.text?.contains("SYNTHETIC-ONLY-ID") == true })
                top(); click("‹ Zur abgeschlossenen Aktion"); visible("Abgeschlossen")
                runOnMainSync { check(!model.completedProtocolOpen && model.protocol.timeline == null && model.protocol.detail == null && port.summaryReads == 0) }
                check(nodes().none { it.text?.contains("SYNTHETIC-ONLY-ID") == true })
                phase = "lock-reauth"; click("Protokoll ansehen / nachbereiten", backwards = false); visible("Chronologie")
                click("Synthetischer ACTIVE-Ursprung", backwards = false); click("Korrigieren", backwards = false)
                runOnMainSync { session.lock(); check(!model.completedProtocolOpen && model.protocol.input == null && model.protocol.expected == null && model.protocol.timeline == null && model.protocol.detail == null) }
                visible("PaG")
                runOnMainSync { session.value = SessionSnapshot(SessionState.READY, 3); session.observer!!(session.value); model.detail("synthetic") }
                visible("Abgeschlossen"); runOnMainSync { check(!model.completedProtocolOpen && model.protocol.timeline == null && !model.tasks.writable && !model.operations.live && !model.operations.planning && model.editing == null) }

            }
            runOnMainSync { launched.finish() }
            finish(-1, Bundle().apply { putString("stream", "P6c3 synthetic ProductScreen: explicit completed entry/no prefetch/origin/correction/own history phases/create permanent marker/template cancel/addendum/mistaken/privacy/lock/reauth/readonly/font1+2: OK\n") })
        } catch (failure: Exception) {
            activity?.let { runOnMainSync { it.finish() } }
            finish(0, Bundle().apply { putString("stream", "P6c3 probe failed; font=$scaleUnderTest; phase=$phase; lines=${failure.stackTrace.filter { it.className == P6c3ProtocolProbe::class.java.name }.take(5).map { it.lineNumber }}; $clickDiagnostic; no input data logged\n") })
        }
    }
}
