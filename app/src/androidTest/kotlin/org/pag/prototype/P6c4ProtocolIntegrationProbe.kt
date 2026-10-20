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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import org.pag.domain.*
import org.pag.prototype.product.*

/** Real ProductScreen with synthetic ports only. No credentials, DB writes or system font change. */
class P6c4ProtocolIntegrationProbe : Instrumentation() {
    private var requestedScale: Float? = null
    private var layoutOnly = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); requestedScale = arguments?.getString("font")?.toFloat(); layoutOnly = arguments?.getString("layoutOnly") == "true"; start() }
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
        var row = Action("synthetic", "Übungsaktion", "Nur synthetische Probe", 1, 2, 0, ActionStatus.ACTIVE)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row)
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { error("No completed action mutation") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) { check(row.status == ActionStatus.ACTIVE && expected == ActionStatus.ACTIVE); row = row.copy(status = ActionStatus.COMPLETED, completedAt = 3000); publish(ActionChangeResult.Applied(row)) }
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) = publish(PersonResult.Value(emptyList()))
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(Person(personId, actionId, "Motte", 0)))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, expected: Person, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    private class Ops(private val actions: Actions) : OperationsRepository {
        var roles = listOf(Role("plan", "synthetic", "Blockierende", 0), Role("current", "synthetic", "Support", 0))
        var person = OperationalPerson(Person("p", "synthetic", "Motte", 0), PersonRolePlan(setOf("plan"), "plan"),
            PersonOperationalState("synthetic", "p", "current", OperationalStatus.ON_SITE, null, System.currentTimeMillis()))
        var other = OperationalPerson(Person("q", "synthetic", "Klee", 0), PersonRolePlan(emptySet(), null), PersonOperationalState("synthetic", "q", null, OperationalStatus.ON_SITE, null, 0))
        var reads = 0
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { reads++; publish(OperationsResult.Value(OperationalBoard(actionId, actions.row.status, roles, (1..32).map { n -> OperationalPerson(Person("p$n", actionId, "Pseudonym %02d".format(n), n.toLong()), PersonRolePlan(emptySet(), null), null) }))) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4 mutation") }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4 mutation") }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4 mutation") }
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
    private fun name(value: String, finishInput: Boolean = true) {
        var field: AccessibilityNodeInfo? = null
        for (attempt in 0 until 50) {
            field = nodes().firstOrNull { it.isEditable }
            if (field != null) break
            waitForIdleSync(); SystemClock.sleep(80)
        }
        check(field != null)
        check(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        waitForIdleSync()
        if (finishInput) { field!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id); waitForIdleSync() }
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
        repeat(220) { attempt ->
            val tree = nodes(); tree.find { match(it, label) && bounds(it).height() > 0 }?.let { return it }
            // Let the accessibility frame catch up before scrolling away from a
            // newly published heading. Tour/home headings live at the top.
            if (attempt >= 5) smallScroll(label in listOf("PaG", "Lagebild", "Schlüsselereignis erfassen", "Eintrag korrigieren", "Eintrag ergänzen"))
            waitForIdleSync(); SystemClock.sleep(80)
        }
        error("P6c4 label not reachable")
    }
    private fun scrollNode(): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null || !node.refresh()) return null
            if (node.isScrollable) return node
            repeat(node.childCount) { find(node.getChild(it))?.let { n -> return n } }
            return null
        }
        return find(uiAutomation.rootInActiveWindow)
    }
    private fun smallScroll(backwards: Boolean) {
        val scroll = scrollNode() ?: return
        scroll.performAction(if (backwards) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        waitForIdleSync(); SystemClock.sleep(100)
    }
    private fun top() { repeat(220) { val scroll = scrollNode() ?: return; if (!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return; waitForIdleSync(); SystemClock.sleep(50) } }
    private fun bottom() { repeat(220) { val scroll = scrollNode() ?: return; if (!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return; waitForIdleSync(); SystemClock.sleep(80) } }
    private var clickDiagnostic = ""
    private fun click(label: String, backwards: Boolean = label.startsWith("‹") || label in listOf("Übersicht", "Personen", "Aufgaben", "Rollen")) {
        repeat(220) {
            val tree = nodes()
            for (node in tree.filter { match(it, label) }) {
                var control: AccessibilityNodeInfo? = node
                while (control != null && !control.isClickable) control = control.parent
                if (control != null && control.isEnabled && bounds(control).height() > 0) {
                    val rect = bounds(control); check(rect.left >= 0 && rect.right <= minOf(targetContext.resources.displayMetrics.widthPixels, (320 * targetContext.resources.displayMetrics.density).toInt()))
                    if (control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { waitForIdleSync(); SystemClock.sleep(150); return }
                }
            }
            smallScroll(backwards)
            waitForIdleSync(); SystemClock.sleep(180)
        }
        val finalTree = nodes()
        clickDiagnostic = "scroll=" + finalTree.filter { it.isScrollable }.joinToString { "${it.className}:${bounds(it)}:actions=${it.actionList.map { a -> a.id }}" } + ";nodes=${finalTree.size},scrolls=${finalTree.count { it.isScrollable }},matches=${finalTree.count { match(it, label) }},buttons=" +
            finalTree.filter { it.isClickable }.take(12).joinToString { "${bounds(it)}:len=${it.text?.length ?: 0}:opens=${it.text?.contains(" öffnen") == true}" }
        error("P6c4 control not reachable")
    }
    private class Protocols(private val actions: Actions) : ProtocolRepository {
        val entries = mutableMapOf<String, ProtocolEntryDetail>().apply {
            repeat(60) { index ->
                val id = "e%03d".format(index + 1)
                val count = if (index == 0) 24 else 1
                val revisions = (1..count).map { n ->
                    val input = ProtocolInput(if (index % 2 == 0) ProtocolClassification.KEY_EVENT else ProtocolClassification.RELEVANT,
                        "Ereignis %03d".format(index + 1), (index + 1).toLong(), occurredAtApproximate = index == 0,
                        description = "DETAIL-SENTINEL-DESCRIPTION-$n", placeLabel = "DETAIL-SENTINEL-PLACE",
                        sourceType = ProtocolSourceType.REPORTED, reportedByPersonId = "p1", affectedPersonIds = setOf("p2"),
                        announcementText = "DETAIL-SENTINEL-ANNOUNCEMENT", announcementKind = ProtocolAnnouncementKind.PARAPHRASE,
                        statedLegalBasis = "DETAIL-SENTINEL-LEGAL-BASIS",
                        officialIdentifiers = listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, "DETAIL-SENTINEL-IDENTIFIER-$n")))
                    val type = if (n == 1) ProtocolRevisionType.ORIGINAL else when (n % 3) {
                        0 -> ProtocolRevisionType.MARKED_MISTAKEN; 1 -> ProtocolRevisionType.CORRECTION; else -> ProtocolRevisionType.ADDENDUM
                    }
                    ProtocolRevisionDetail(ProtocolRevision("$id-r$n", "synthetic", id, n.toLong(), type, input, n.toLong() * 1000,
                        n > 12, n % 3 == 0), listOf(ProtocolPersonReference("p2", "DETAIL-SENTINEL-PSEUDONYM")), ProtocolPersonReference("p1", "DETAIL-SENTINEL-SOURCE"))
                }
                put(id, ProtocolEntryDetail(ProtocolEntry(id, "synthetic", false), revisions))
            }
        }
        var staleNext = false
        var writes = 0; var detailReads = 0; var timelineReads = 0; var summaryReads = 0
        override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) { summaryReads++; check(actions.row.status == ActionStatus.ACTIVE); publish(ProtocolResult.Value(ProtocolSummary(entries.values.count { it.current.revision.input.classification == ProtocolClassification.KEY_EVENT }.toLong(), entries.values.maxOfOrNull { it.revisions.first().revision.recordedAt }))) }
        override fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit) { timelineReads++; publish(ProtocolResult.Value(entries.values.map { d -> val r = d.current.revision; ProtocolTimelineItem(d.entry.id, r.id, r.revisionNumber, r.revisionType, r.input.classification, r.input.templateKey, r.input.title, r.input.occurredAt, r.input.occurredAtApproximate, r.recordedAt, r.recordedAfterCompletion, d.entry.createdAfterCompletion, r.mistaken, d.revisions.first().revision.recordedAt) }.sortedWith(compareBy<ProtocolTimelineItem> { it.occurredAt }.thenBy { it.originalRecordedAt }.thenBy { it.entryId }))) }
        override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) { detailReads++; publish(entries[entryId]?.let { ProtocolResult.Value(it) } ?: ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND)) }
        override fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput, publish: (ProtocolResult<ProtocolSaved>) -> Unit) {
            check(expectedActionStatus == actions.row.status && ProtocolRules.valid(input)); writes++
            val id = "synthetic-$writes"; val r = ProtocolRevision("r-$writes", actionId, id, 1, ProtocolRevisionType.ORIGINAL, ProtocolRules.normalized(input), System.currentTimeMillis(), actions.row.status == ActionStatus.COMPLETED, false)
            entries[id] = ProtocolEntryDetail(ProtocolEntry(id, actionId, actions.row.status == ActionStatus.COMPLETED), listOf(ProtocolRevisionDetail(r, input.affectedPersonIds.sorted().map { ProtocolPersonReference(it, "Pseudonym %02d".format(it.removePrefix("p").toInt())) }, input.reportedByPersonId?.let { ProtocolPersonReference(it, "Pseudonym %02d".format(it.removePrefix("p").toInt())) })))
            publish(ProtocolResult.Value(ProtocolSaved(id, r.id, r.revisionNumber)))
        }
        override fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { check(expectedActionStatus == actions.row.status); if (staleNext) { staleNext = false; writes++; publish(ProtocolResult.Rejected(ProtocolFailure.STALE)) } else append(expectedRevision, revisionType, input, clearMistaken, publish) }
        override fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { check(expectedActionStatus == actions.row.status); append(expectedRevision, ProtocolRevisionType.MARKED_MISTAKEN, expectedRevision.revision.input, false, publish) }
        private fun append(expected: ProtocolExpectedRevision, type: ProtocolRevisionType, input: ProtocolInput, clear: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) {
            val d = entries.getValue(expected.entry.id); check(d.current.revision == expected.revision && ProtocolRules.valid(input)); writes++
            val r = expected.revision.copy(id = "r-$writes", revisionNumber = expected.revision.revisionNumber + 1, revisionType = type, input = ProtocolRules.normalized(input), mistaken = ProtocolRules.nextMistaken(expected.revision.mistaken, type, clear)!!, recordedAt = System.currentTimeMillis(), recordedAfterCompletion = actions.row.status == ActionStatus.COMPLETED)
            entries[d.entry.id] = d.copy(revisions = d.revisions + ProtocolRevisionDetail(r, emptyList(), null)); publish(ProtocolResult.Value(ProtocolSaved(d.entry.id, r.id, r.revisionNumber)))
        }
    }
    private fun assertPrivate() {
        check(nodes().none { (it.text?.toString().orEmpty() + it.contentDescription?.toString().orEmpty()).contains("DETAIL-SENTINEL") }) { "Projection privacy" }
    }
    private fun control(label: String): AccessibilityNodeInfo {
        var n: AccessibilityNodeInfo? = visible(label)
        while (n != null && !n.isClickable && n.className?.toString() != "android.widget.Button") n = n.parent
        return checkNotNull(n)
    }
    private fun awaitState(label: String, expected: (AccessibilityNodeInfo) -> Boolean) {
        visible(label) // Compose exposes offscreen semantics only once they become reachable.
        repeat(50) {
            for (labelNode in nodes().filter { match(it, label) }) {
                var n: AccessibilityNodeInfo? = labelNode
                while (n != null) { n.refresh(); if (expected(n)) return; n = n.parent }
            }
            waitForIdleSync(); SystemClock.sleep(100)
        }
        error("Accessibility state missing")
    }
    private fun limit(max: Int, prefix: String) = prefix + "🐦".repeat(max - prefix.codePointCount(0, prefix.length))
    private fun awaitIme(activity: MainActivity, visible: Boolean) {
        repeat(50) {
            var current = false
            runOnMainSync { current = activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
            if (current == visible) return
            waitForIdleSync(); SystemClock.sleep(100)
        }
        error("IME visibility did not reach expected state")
    }
    override fun onStart() {
        var activity: MainActivity? = null; var scaleUnderTest = 1f; var phase = "entry"
        try {
            check(!BuildConfig.PAG_DEMO_ONLY)
            val launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = launched; val density = targetContext.resources.displayMetrics.density
            for (scale in listOf(1f, 2f).filter { !layoutOnly && (requestedScale == null || it == requestedScale) }) {
                scaleUnderTest = scale
                val session = Session(); val actions = Actions(); val port = Protocols(actions)
                lateinit var model: ProductViewModel
                runOnMainSync {
                    model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, People(), operationsRepository = Ops(actions), taskRepository = Tasks(), protocolRepository = port) {}
                    model.detail("synthetic")
                    launched.setContent { CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(320.dp)) { ProductScreen(model) }
                    } }
                }
                waitForIdleSync(); phase = "hub-privacy"; top(); assertPrivate()
                runOnMainSync { check(port.detailReads == 0 && port.timelineReads == 0 && model.workspace!!.protocolSummary!!.keyEventCount == 30L) }
                phase = "timeline-60"; click("Protokoll öffnen", backwards = false); visible("Chronologie"); assertPrivate()
                check(visible("Chronologie").isHeading)
                awaitState("Alle") { it.isSelected || it.isChecked }
                click("Schlüsselereignisse", backwards = false); runOnMainSync { check(model.protocol.filter == ProtocolFilter.KEY_EVENTS) }; awaitState("Schlüsselereignisse") { it.isSelected || it.isChecked }
                runOnMainSync { check(model.protocol.filtered!!.size == 30 && port.detailReads == 0) }
                click("Alle", backwards = true); click("Ereignis 060", backwards = false)
                runOnMainSync { check(model.protocol.detail!!.entry.id == "e060") }
                top(); click("‹ Protokoll"); assertPrivate(); top()
                phase = "timeline-card-semantics"; visible("Ereignis 001"); val card = control("Ereignis 001")
                check(card.actionList.count { it.id == AccessibilityNodeInfo.ACTION_CLICK } == 1)
                check(card.actionList.first { it.id == AccessibilityNodeInfo.ACTION_CLICK }.label?.toString() == "Ereignis 001 öffnen")
                click("Ereignis 001", backwards = false)
                phase = "history-24"; runOnMainSync { check(!model.protocol.history && model.protocol.detail!!.revisions.size == 24) }
                click("Frühere Fassungen (23)", backwards = false); runOnMainSync { check(model.protocol.history) }; awaitState("Frühere Fassungen (23)") { it.stateDescription?.toString()?.contains("Ausgeklappt") == true }
                click("Fassung 1 · Original", backwards = false); visible("Sonstige sichtbare amtliche Kennung: DETAIL-SENTINEL-IDENTIFIER-1")
                runOnMainSync { check(model.protocol.historical!!.revision.revisionNumber == 1L && !model.protocol.historical!!.revision.recordedAfterCompletion) }
                top(); click("‹ Zum aktuellen Eintrag"); top(); click("‹ Protokoll"); assertPrivate()
                phase = "cross-area"; top(); click("‹ Zum Lagebild"); assertPrivate()
                for (area in listOf(ActiveArea.PERSONS, ActiveArea.TASKS, ActiveArea.FUNCTIONS, ActiveArea.DETAILS)) {
                    runOnMainSync { model.activeArea(area); check(model.protocol.detail == null && model.protocol.timeline == null) }
                    waitForIdleSync(); assertPrivate(); runOnMainSync { model.activeArea(ActiveArea.HUB) }; waitForIdleSync()
                }
                phase = "max-form"; runOnMainSync { model.activeArea(ActiveArea.PROTOCOL); model.protocol.create() }; waitForIdleSync()
                // Representative real accessibility text entry; bulk boundary data seeded through the real model.
                name("synthetic boundary", finishInput = false)
                awaitIme(launched, true)
                val full = ProtocolInput(ProtocolClassification.RELEVANT, limit(120, "Maximum "), System.currentTimeMillis(), true,
                    description = limit(2000, "DETAIL-SENTINEL-DESCRIPTION "), sourceType = ProtocolSourceType.REPORTED, reportedByPersonId = "p1",
                    affectedPersonIds = (1..32).map { "p$it" }.toSet(), placeLabel = limit(80, "DETAIL-SENTINEL-PLACE "),
                    announcementText = limit(2000, "DETAIL-SENTINEL-ANNOUNCEMENT "), announcementKind = ProtocolAnnouncementKind.WORDING,
                    statedLegalBasis = limit(1000, "DETAIL-SENTINEL-LEGAL-BASIS "), officialIdentifiers = (1..32).map {
                        ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.DISPLAYED_NAME, limit(120, "DETAIL-SENTINEL-IDENTIFIER-$it ")) })
                runOnMainSync { model.protocol.change(full); model.protocol.toggleFields(); check(ProtocolRules.valid(model.protocol.input!!)) }
                // The title retains its entry focus while the long optional form is seeded.
                // Reach the end before any FormButton tap dismisses the IME.
                waitForIdleSync(); phase = "long-form-ime"; bottom(); visible("Speichern"); visible("Abbrechen")
                awaitIme(launched, true)
                top(); awaitState("Zeitpunkt ungefähr") { it.isChecked }
                phase = "persons-32"; visible("Mitgeteilt von · optional"); click("Pseudonym 02", backwards = false)
                runOnMainSync { check(model.protocol.input!!.reportedByPersonId == "p2" && model.protocol.input!!.affectedPersonIds.size == 32) }
                visible("Betroffene Personen · optional"); awaitState("Pseudonym 01") { it.isChecked }
                click("Pseudonym 01", backwards = false); runOnMainSync { check(model.protocol.input!!.affectedPersonIds.size == 31) }
                phase = "identifiers-32"; bottom(); awaitState("+ Kennung hinzufügen") { !it.isEnabled }
                runOnMainSync { model.protocol.addIdentifier(); check(model.protocol.input!!.officialIdentifiers.size == 32) }
                click("Kennung 32 entfernen", backwards = true)
                runOnMainSync { check(model.protocol.input!!.officialIdentifiers.size == 31); model.protocol.addIdentifier(); model.protocol.change(model.protocol.input!!.copy(officialIdentifiers = full.officialIdentifiers)) }
                waitForIdleSync(); bottom(); awaitState("+ Kennung hinzufügen") { !it.isEnabled }
                phase = "save-at-form-end"; visible("Speichern")
                check(bounds(control("Speichern")).height() >= (48 * density).toInt())
                check(bounds(control("Abbrechen")).height() >= (48 * density).toInt())
                click("Speichern", backwards = false)
                runOnMainSync { check(model.protocol.detail!!.current.revision.input.title == full.title && model.protocol.detail!!.current.revision.input.officialIdentifiers.size == 32) }
                phase = "long-detail"; bottom(); visible("Name laut sichtbarer Kennzeichnung / eigener Wahrnehmung: " + full.officialIdentifiers.last().value)
                top(); click("‹ Protokoll"); assertPrivate(); top(); click("‹ Zum Lagebild"); assertPrivate()
                phase = "active-summary"; runOnMainSync { check(model.workspace!!.protocolSummary!!.lastEntryRecordedAt != null) }
                phase = "active-stale"; runOnMainSync { model.activeArea(ActiveArea.PROTOCOL); model.protocol.open("e001"); model.protocol.revise(ProtocolRevisionType.CORRECTION); port.staleNext = true; model.protocol.save() }; waitForIdleSync()
                visible("Zur aktuellen Fassung · Eingaben verwerfen"); val writes = port.writes
                runOnMainSync { model.protocol.save(); check(port.writes == writes && model.protocol.form) }
                click("Zur aktuellen Fassung · Eingaben verwerfen", backwards = false); runOnMainSync { check(!model.protocol.form && model.protocol.expected == null) }
                phase = "complete"; runOnMainSync { model.requestAdvance(); model.confirmAdvance() }; waitForIdleSync()
                runOnMainSync { check(model.selected!!.status == ActionStatus.COMPLETED && !model.completedProtocolOpen && model.protocol.detail == null && model.protocol.timeline == null) }
                val reads = listOf(port.summaryReads, port.timelineReads, port.detailReads, port.writes)
                top(); visible("Abgeschlossen"); bottom(); top(); runOnMainSync { check(reads == listOf(port.summaryReads, port.timelineReads, port.detailReads, port.writes)) }
                phase = "completed-stale"; click("Protokoll ansehen / nachbereiten", backwards = false); runOnMainSync { model.protocol.open("e001"); model.protocol.revise(ProtocolRevisionType.CORRECTION); port.staleNext = true; model.protocol.save() }; waitForIdleSync()
                visible("Zur aktuellen Fassung · Eingaben verwerfen"); click("Zur aktuellen Fassung · Eingaben verwerfen", backwards = false)
                phase = "lock"; runOnMainSync { session.lock(); check(model.protocol.detail == null && model.protocol.timeline == null && model.protocol.input == null && !model.completedProtocolOpen) }; waitForIdleSync(); assertPrivate(); visible("PaG")
                runOnMainSync { session.value = SessionSnapshot(SessionState.READY, 3); session.observer!!(session.value) }; waitForIdleSync()
                runOnMainSync { check(model.selected == null && !model.completedProtocolOpen) }
            }
            // Render the actual ProductScreen around the hub's 600dp content threshold.
            // Local density supplies a virtual width without changing device settings.
            for (scale in listOf(1f, 2f).filter { requestedScale == null || it == requestedScale }) {
                scaleUnderTest = scale
                // Exact 600dp is covered by the pure helper test. Native pixel/dp
                // rounding can place a nominal 600dp layout fractionally below it.
                for (hubWidth in listOf(599f, 601f)) {
                    phase = "responsive-hub"
                    val virtualDensity = targetContext.resources.displayMetrics.widthPixels / (hubWidth + 48f)
                    var measuredWidth = 0f
                    var providedDensity = 0f
                    lateinit var hubModel: ProductViewModel
                    runOnMainSync {
                        val hubActions = Actions()
                        hubModel = ProductViewModel(Session(), hubActions, ProductStorageState.READY_TO_OPEN, People(),
                            operationsRepository = Ops(hubActions), taskRepository = Tasks(), protocolRepository = Protocols(hubActions)) {}
                        hubModel.detail("synthetic")
                        launched.setContent { CompositionLocalProvider(LocalDensity provides Density(virtualDensity, scale)) {
                            // Each virtual-density scenario needs fresh subcomposition scopes.
                            androidx.compose.runtime.key(hubWidth, scale) {
                                androidx.compose.foundation.layout.BoxWithConstraints(androidx.compose.ui.Modifier.fillMaxWidth()) {
                                    measuredWidth = maxWidth.value
                                    providedDensity = LocalDensity.current.density
                                    ProductScreen(hubModel)
                                }
                            }
                        } }
                    }
                    waitForIdleSync(); top(); assertPrivate()
                    val columns = activeHubColumns(hubWidth, scale)
                    val openingLabels = listOf("Personen öffnen", "Aufgaben öffnen", "Funktionen öffnen", "Protokoll öffnen", "Aktionsdetails öffnen")
                    openingLabels.forEachIndexed { index, label ->
                        val rect = bounds(control(label))
                        val expectedWidth = if (columns == 2 && index < 4) (hubWidth - 12f) / 2f else hubWidth
                        phase = "responsive-hub/width=$hubWidth/measured=$measuredWidth/density=$providedDensity/card=$index/columns=$columns/bounds=$rect/expectedPx=${expectedWidth * virtualDensity}"
                        check(kotlin.math.abs(rect.width() - expectedWidth * virtualDensity) <= 3f)
                        check(rect.height() >= 48f * virtualDensity - 1f)
                        check(rect.left >= 0 && rect.right <= targetContext.resources.displayMetrics.widthPixels)
                    }
                    runOnMainSync { check(hubModel.protocol.detail == null && hubModel.protocol.timeline == null) }
                }
            }
            runOnMainSync { launched.finish() }
            finish(-1, Bundle().apply { putString("stream", if (layoutOnly) "P6c4 layout-only diagnostic: 599/601dp hub/font=${requestedScale?.toString() ?: "1+2"}: OK\n"
                else "P6c4 ProductScreen: 320dp+599/601dp hub/font=${requestedScale?.toString() ?: "1+2"}/privacy/60 timeline/24 revisions/max Unicode/32 persons/32 identifiers/semantics/IME/cross-area/stale/phase/lock: OK\n") })
        } catch (failure: Exception) {
            activity?.let { runOnMainSync { it.finish() } }
            finish(0, Bundle().apply { putString("stream", "P6c4 probe failed; font=$scaleUnderTest; phase=$phase; lines=${failure.stackTrace.filter { it.className == P6c4ProtocolIntegrationProbe::class.java.name }.take(5).map { it.lineNumber }}; semantics=${nodes().filter { match(it, "Frühere Fassungen (23)") || match(it, "Schlüsselereignisse") || match(it, "Alle") }.map { "class=${it.className},selected=${it.isSelected},checked=${it.isChecked},click=${it.isClickable},state=${it.stateDescription},parentSelected=${it.parent?.isSelected},parentChecked=${it.parent?.isChecked},parentState=${it.parent?.stateDescription}" }}; no input data logged\n") })
        }
    }
}
