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
class P9MemoryReportProbe : Instrumentation() {
    private var requestedScale: Float? = null
    private val trace by lazy { UiReachabilityTrace(this,"P9") }
    private var failureKind="ASSERTION"
    private var addendumSaveAttempts=0
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); trace.refreshAncestors=arguments?.getString("navigation")!="legacy";trace.detailed=arguments?.getString("diagnostics")=="detailed"; requestedScale = arguments?.getString("font")?.toFloat(); start() }
    private class Session : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1); var observer: ((SessionSnapshot) -> Unit)? = null
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription {} }
        override fun authenticate(experiment: Experiment) { ready() }
        fun ready() { value=SessionSnapshot(SessionState.READY,value.generation+1);observer!!(value) }
        override fun testAccess() {}
        override fun userInteraction() {}
        override fun lock() { value = SessionSnapshot(SessionState.LOCKED, value.generation + 1); observer!!(value) }
    }
    private class Actions(phase: ActionStatus = ActionStatus.PREPARATION) : ActionRepository {
        var row = Action("synthetic", "Übungsaktion", "Nur synthetische Probe", 1, 2, 0, phase)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row)
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { error("No P9 action creation") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { error("No P9 action edit") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            check(expected == row.status); row = row.copy(status = ActionLifecycle.next(expected)!!, completedAt = 10L.takeIf { expected == ActionStatus.ACTIVE })
            publish(ActionChangeResult.Applied(row))
        }
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { error("Operations supplies people") }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) { error("No P9 person detail") }
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P9 person write") }
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P9 person write") }
    }
    private class Ops(private val actions: Actions) : OperationsRepository {
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) = publish(OperationsResult.Value(
            OperationalBoard(actionId, actions.row.status, emptyList(), listOf(OperationalPerson(Person("p", actionId, "Motte", 0),
                PersonRolePlan(emptySet(), null), PersonOperationalState(actionId, "p", null, OperationalStatus.ON_SITE, null, 0)), OperationalPerson(Person("q",actionId,"Meise",0),PersonRolePlan(emptySet(),null),null)))))
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P9 P4 mutation") }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P9 P4 mutation") }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P9 P4 mutation") }
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No P9 P4 mutation") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P9 P4 mutation") }
    }
    private class Tasks : TaskRepository {
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) = publish(OperationsResult.Value(TaskBoard(actionId, listOf(
            Task("t", actionId, "Synthetische Aufgabe", TaskPriority.NORMAL, TaskStatus.DONE, null, null, null, null, null, 0, 1, 1),
            Task("cancelled", actionId, "Synthetische entfallene Aufgabe", TaskPriority.NORMAL, TaskStatus.CANCELLED, null, null, null, null, null, 0, 1, null)), emptyList())))
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P9 task mutation") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P9 task mutation") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No P9 task mutation") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No P9 task mutation") }
    }
    private class Protocols : ProtocolRepository {
        override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) = publish(ProtocolResult.Value(ProtocolSummary(0, null)))
        override fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit) { error("No P9 protocol timeline") }
        override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) { error("No P9 protocol detail") }
        override fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P9 protocol write") }
        override fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P9 protocol write") }
        override fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P9 protocol write") }
    }
    private class Materials:MaterialRepository {
        override fun summary(actionId:String,publish:(OperationsResult<MaterialSummary>)->Unit)=publish(OperationsResult.Value(MaterialSummary(0)))
        override fun board(actionId:String,publish:(OperationsResult<MaterialBoard>)->Unit){error("No P9 material board")}
        override fun create(actionId:String,input:MaterialInput,publish:(OperationsResult<MaterialItem>)->Unit){error("No P9 material write")}
        override fun edit(actionId:String,expected:MaterialItem,input:MaterialInput,publish:(OperationsResult<MaterialItem>)->Unit){error("No P9 material write")}
        override fun delete(actionId:String,expected:MaterialItem,publish:(OperationsResult<Unit>)->Unit){error("No P9 material write")}
    }
    private class Reports : MemoryReportRepository {
        val items=linkedMapOf<String,MemoryReportDetail>();var writes=0;var summaryReads=0;var listReads=0;var detailReads=0
        override fun summary(actionId:String,publish:(OperationsResult<MemoryReportSummary>)->Unit){summaryReads++;publish(OperationsResult.Value(MemoryReportSummary(items.values.count{it.header.status==MemoryReportStatus.DRAFT},items.values.count{it.header.status==MemoryReportStatus.COMPLETED})))}
        override fun list(actionId:String,publish:(OperationsResult<List<MemoryReportHeader>>)->Unit){listReads++;publish(OperationsResult.Value(items.values.map{it.header}.sortedWith(MemoryReportRules.order)))}
        override fun detail(actionId:String,reportId:String,publish:(OperationsResult<MemoryReportDetail>)->Unit){detailReads++;publish(OperationsResult.Value(items.getValue(reportId)))}
        override fun create(actionId:String,authorLabel:String,firstSection:MemorySectionSave,publish:(OperationsResult<MemoryReportHeader>)->Unit){writes++;val h=MemoryReportHeader("r$writes",actionId,authorLabel.trim(),1,MemoryReportStatus.DRAFT,0,0,100,100,null);items[h.id]=MemoryReportDetail(h,firstSection.answers,emptyList());publish(OperationsResult.Value(h))}
        override fun saveSection(actionId:String,expected:MemoryReportHeader,section:MemorySectionSave,publish:(OperationsResult<MemoryReportHeader>)->Unit){check(items.getValue(expected.id).header==expected);check(section.sectionOrdinal<=expected.lastSavedSectionOrdinal+1);writes++;val h=expected.copy(revision=expected.revision+1,lastSavedSectionOrdinal=maxOf(expected.lastSavedSectionOrdinal,section.sectionOrdinal),authorLabel=section.authorLabel?.trim()?:expected.authorLabel);val d=items.getValue(h.id);items[h.id]=d.copy(header=h,answers=d.answers.filterKeys{MemoryQuestionnaireV1.question(it)!!.sectionOrdinal!=section.sectionOrdinal}+section.answers);publish(OperationsResult.Value(h))}
        override fun complete(actionId:String,expected:MemoryReportHeader,publish:(OperationsResult<MemoryReportHeader>)->Unit){check(MemoryReportRules.mayComplete(expected));writes++;val h=expected.copy(status=MemoryReportStatus.COMPLETED,revision=expected.revision+1,completedAt=100);items[h.id]=items.getValue(h.id).copy(header=h);publish(OperationsResult.Value(h))}
        override fun addAddendum(actionId:String,expected:MemoryReportHeader,content:String,publish:(OperationsResult<MemoryReportHeader>)->Unit){check(expected.status==MemoryReportStatus.COMPLETED);writes++;val h=expected.copy(revision=expected.revision+1);val d=items.getValue(h.id);items[h.id]=d.copy(header=h,addenda=d.addenda+MemoryAddendum(d.addenda.size,content.trim(),100));publish(OperationsResult.Value(h))}
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
        return text.replace(Regex("\\s+"), " ").trim() == label || text == "✓ $label" || text.split("\n").contains(label) || node.hintText?.toString() == label || node.contentDescription?.toString() == label
    }
    private fun scroll(backwards: Boolean): Boolean {
        val node = nodes().firstOrNull { it.isScrollable } ?: return false
        val changed = node.performAction(if (backwards) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        waitForIdleSync(); SystemClock.sleep(100);if(trace.detailed)trace.event("SCROLL","back=$backwards;accepted=$changed;bounds=${bounds(node)}");return changed
    }
    private fun top() { repeat(120) { if (!scroll(true)) return } }
    private fun bottom() { repeat(120) { if (!scroll(false)) return } }
    private fun visible(label: String, backwards: Boolean = label in listOf("PaG", "Lagebild", "Abgeschlossen", "Kosten eintragen", "Kosten bearbeiten")): AccessibilityNodeInfo {
        repeat(80) { attempt ->
            nodes().also{if(attempt==0||attempt==4||attempt==79)trace.search(it,label,backwards,attempt)}.firstOrNull { match(it, label) && bounds(it).height() > 0 }?.let { return it }
            if (attempt > 3) scroll(backwards)
            waitForIdleSync(); SystemClock.sleep(100)
        }
        failureKind="UNREACHABLE";error("MemoryReport label unreachable")
    }
    private fun click(label: String, backwards: Boolean = label.startsWith("‹") || label == "Übersicht", writeOnce:Boolean=false) {
        repeat(80) {
            nodes().filter { match(it, label) }.forEach { node ->
                var control: AccessibilityNodeInfo? = node
                while (control != null && !control.isClickable) {
                    control=control.parent
                    // getParent() can return a stale cached copy even when the walked child was refreshed.
                    if(trace.refreshAncestors && control!=null){
                        val before="${control.isClickable}/${control.isEnabled}/${bounds(control).height()}"
                        val refreshed=control.refresh()
                        if(trace.detailed)trace.event("ANCESTOR","before=$before;after=${control.isClickable}/${control.isEnabled}/${bounds(control).height()};refreshed=$refreshed")
                        if(!refreshed){control=null;break}
                    }
                }
                if (control != null && control.isEnabled && bounds(control).height() > 0) {
                    val rect = bounds(control); check(rect.left >= 0 && rect.right <= targetContext.resources.displayMetrics.widthPixels)
                    if (rect.height() < 48 * targetContext.resources.displayMetrics.density) return@forEach
                    if(writeOnce)check(++addendumSaveAttempts==1)
                    val accepted=control.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if(writeOnce)check(accepted) // Unknown/rejected UI delivery is never retried as a write.
                    if (accepted) { waitForIdleSync(); SystemClock.sleep(150); return }
                }
            }
            scroll(backwards); waitForIdleSync(); SystemClock.sleep(100)
        }
        failureKind="UNREACHABLE";error("MemoryReport control unreachable")
    }
    private fun field(label: String, value: String) {
        top()
        repeat(90) {
            for(node in nodes().filter { match(it,label) }) {
                var editable:AccessibilityNodeInfo?=node
                while(editable!=null&&!editable.isEditable){editable=editable.parent;if(trace.refreshAncestors&&editable?.refresh()==false)editable=null}
                if(editable!=null&&bounds(editable).height()>0) {
                    check(editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}))
                    waitForIdleSync();editable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id);waitForIdleSync();return
                }
            }
            scroll(false);waitForIdleSync();SystemClock.sleep(100)
        }
        failureKind="UNREACHABLE";error("Memory input unreachable")
    }
    private fun noContent() {
        val forbidden=listOf("Motte","Meise","MEMORY-SENTINEL","HIV","Hepatitis","Fingerabdrücke","ED-Behandlung")
        check(nodes().none { n->forbidden.any{(n.text?.toString().orEmpty()+n.contentDescription?.toString().orEmpty()).contains(it)} })
    }
    private fun preA() {
        val forbidden=MemoryQuestionnaireV1.sections.drop(1).map{it.title}+listOf("HIV","Hepatitis","Fingerabdrücke")
        check(nodes().none{n->forbidden.any{(n.text?.toString().orEmpty()+n.contentDescription?.toString().orEmpty()).contains(it)}})
    }
    override fun onStart() {
        var activity:MainActivity?=null;var phase="entry";trace.phase=phase;var font=1f
        try {
            trace.start()
            val launched=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity;activity=launched;trace.attach(launched)
            val density=targetContext.resources.displayMetrics.density
            for(scale in listOf(1f,2f).filter{requestedScale==null||requestedScale==it}) {
                font=scale;trace.font=scale;val session=Session();val actions=Actions(ActionStatus.ACTIVE);val port=Reports();lateinit var model:ProductViewModel
                runOnMainSync {model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,People(),operationsRepository=Ops(actions),taskRepository=Tasks(),protocolRepository=Protocols(),materialRepository=Materials(),memoryReportRepository=port){session.ready()};trace.model=model;model.detail("synthetic");launched.setContent{CompositionLocalProvider(LocalDensity provides Density(density,scale)){trace.Content(model)}}}
                waitForIdleSync();phase="hub";trace.phase=phase;visible("Gedächtnisprotokolle öffnen");noContent();runOnMainSync{check(port.listReads==0&&port.detailReads==0)}
                click("Gedächtnisprotokolle öffnen");click("Neues Gedächtnisprotokoll");check(visible("Freie Erinnerung").isHeading);preA();runOnMainSync{check(port.writes==0)}
                phase="free-first";trace.phase=phase;click("Motte");field("Beschreibe in deinen eigenen Worten, was passiert ist.","MEMORY-SENTINEL synthetische freie Erinnerung 🦋");runOnMainSync{model.memoryReports.changeAnswer("A_FREE_NARRATIVE",MemoryAnswerValue.Text("🦋".repeat(8000)))};bottom();preA();click("Speichern und weiter");visible("Auflösung / Ende der Teilnahme",true)
                runOnMainSync{check(port.items.values.single().header.authorLabel=="Motte"&&model.memoryReports.sectionOrdinal==1)}
                phase="multi-semantics";trace.phase=phase;click("Selbst gelöst / selbst gegangen");
                var multi:AccessibilityNodeInfo?=visible("Selbst gelöst / selbst gegangen",true);while(multi!=null&&!multi.isCheckable)multi=multi.parent;check(multi!=null&&multi.isChecked)
                phase="dependency-parent";trace.phase=phase;click("Ja");phase="dependency-text";trace.phase=phase;field("Welche Absprachen, Zusagen oder Bedingungen erinnerst du?","MEMORY-SENTINEL detail");phase="dependency-save";trace.phase=phase;click("Speichern und weiter");phase="dependency-back";trace.phase=phase;click("‹ Zurück",false);phase="dependency-no";trace.phase=phase;click("Nein");visible("Diese Detailangabe passt nicht mehr zur aktuellen Auswahl.");phase="dependency-keep";trace.phase=phase;click("Detail behalten");click("Speichern und weiter");click("‹ Zurück",false);phase="dependency-remove";trace.phase=phase;click("Detail entfernen");click("Speichern und weiter")
                phase="persistent-lock";trace.phase=phase;click("Ja");field("Wo musstest du warten?","unsaved synthetic")
                // C_WAIT_PLACE is dependent; provide its parent first through the model then use real UI.
                phase="lock";trace.phase=phase;click("Sperren",true);visible("PaG");noContent();runOnMainSync{check(model.memoryReports.detail==null&&model.memoryReports.answers.isEmpty());session.ready()};visible("Übungsaktion · Aktiv");runOnMainSync{check(model.selected==null&&!model.memoryReports.available)}
                click("Übungsaktion · Aktiv");click("Gedächtnisprotokolle öffnen");click("Motte öffnen");visible("Warten / weiterer Ablauf",true);runOnMainSync{check(model.memoryReports.sectionOrdinal==2&&!model.memoryReports.answers.containsKey("C_WAIT_PLACE"))}
                phase="all-empty-sections";trace.phase=phase;for(section in 2..16){click("Speichern und weiter");runOnMainSync{check(model.memoryReports.expected!!.lastSavedSectionOrdinal==section)}}
                visible("Zusammenfassung",true);bottom();click("Gedächtnisprotokoll abschließen");click("Abschluss bestätigen");runOnMainSync{check(model.memoryReports.expected!!.status==MemoryReportStatus.COMPLETED)}
                fun step(name:String,work:()->Unit){phase=name;trace.phase=name;trace.event("BEGIN");work();trace.event("PASS")}
                step("A_REPORT_COMPLETED"){runOnMainSync{check(model.memoryReports.expected!!.status==MemoryReportStatus.COMPLETED);check(port.items.values.single().header.status==MemoryReportStatus.COMPLETED)}}
                step("B_ADDENDUM_ALLOWED_VISIBLE"){runOnMainSync{check(model.memoryReports.mode==MemoryReportMode.DETAIL&&!model.memoryReports.busy&&!model.memoryReports.loading)};bottom();visible("Ergänzung hinzufügen")}
                step("C_ADDENDUM_CLICK"){click("Ergänzung hinzufügen")}
                step("D_ADDENDUM_MODE"){runOnMainSync{check(model.memoryReports.mode==MemoryReportMode.ADDENDUM)}}
                step("E_INPUT_PRESENT"){visible("Ergänzung")}
                step("F_INPUT_ACCEPTED"){field("Ergänzung","MEMORY-SENTINEL Ergänzung");runOnMainSync{check(model.memoryReports.addendum=="MEMORY-SENTINEL Ergänzung")}}
                val beforeAddendumWrites=port.writes;addendumSaveAttempts=0
                step("G_SAVE_ADDENDUM"){runOnMainSync{check(MemoryReportRules.addendumValid(model.memoryReports.addendum))};visible("Ergänzung speichern");click("Ergänzung speichern",writeOnce=true)}
                step("H_SAVE_CONFIRMED"){runOnMainSync{check(addendumSaveAttempts==1);check(port.writes==beforeAddendumWrites+1);check(model.memoryReports.mode==MemoryReportMode.DETAIL&&!model.memoryReports.busy)}}
                step("I_PERSISTED_ADDENDUM"){runOnMainSync{check(port.items.values.single().addenda.single().content=="MEMORY-SENTINEL Ergänzung");check(model.memoryReports.detail!!.addenda.single().content=="MEMORY-SENTINEL Ergänzung")}}
                step("J_ADDENDUM_VISIBLE"){bottom();visible("Ergänzung 1")}
                phase="hub-counts";trace.phase=phase;top();click("‹ Zum Lagebild");noContent();runOnMainSync{check(model.workspace!!.memoryReportSummary==MemoryReportSummary(0,1))}
                phase="active-completed-draft";trace.phase=phase;click("Gedächtnisprotokolle öffnen");click("Neues Gedächtnisprotokoll");click("Meise");click("Speichern und weiter");runOnMainSync{model.memoryReports.changeAnswer("B_THREATS_OR_PRESSURE",MemoryAnswerValue.Option("UNKNOWN"));model.requestAdvance();model.confirmAdvance()};waitForIdleSync();val before=listOf(port.summaryReads,port.listReads,port.detailReads,port.writes);bottom();top();runOnMainSync{check(model.selected!!.status==ActionStatus.COMPLETED&&model.memoryReports.detail==null);check(before==listOf(port.summaryReads,port.listReads,port.detailReads,port.writes))}
                phase="completed-resume";trace.phase=phase;click("Gedächtnisprotokolle");click("Meise öffnen");runOnMainSync{check(model.memoryReports.sectionOrdinal==1&&model.memoryReports.answers.isEmpty());check(!model.tasks.writable&&!model.material.writable&&!model.operations.live);model.edit();check(model.editing==null)}
                click("‹ Zurück",false);click("‹ Zurück",false);click("Neues Gedächtnisprotokoll");field("Kürzel / Bericht von","Soligruppe Nord");click("Speichern und weiter");runOnMainSync{check(port.items.size==3&&actions.row.status==ActionStatus.COMPLETED)}
                phase="lock-final";trace.phase=phase;click("Sperren",true);visible("PaG");noContent()
            }
            trace.stop();runOnMainSync{launched.finish()};finish(-1,Bundle().apply{putString("stream","P9 synthetic ProductScreen free-first/dependencies/persistent drafts/completion/addenda/hub privacy/completed/lock/font=${requestedScale ?: "1+2"}: OK\n")})
        }catch(failure:Exception){trace.event("FAIL","category=$failureKind;exception=${failure.javaClass.simpleName}");trace.stop();activity?.let{runOnMainSync{it.finish()}};finish(0,Bundle().apply{putString("stream","P9 probe failed; font=$font; phase=$phase; category=$failureKind; lines=${failure.stackTrace.filter{it.className==P9MemoryReportProbe::class.java.name}.take(5).map{it.lineNumber}}; no report/input contents logged\n")})}
    }
}
