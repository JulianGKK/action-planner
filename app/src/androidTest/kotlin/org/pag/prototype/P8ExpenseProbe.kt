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
class P8ExpenseProbe : Instrumentation() {
    private var requestedScale: Float? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); requestedScale = arguments?.getString("font")?.toFloat(); start() }
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
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { error("No P8 action creation") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { error("No P8 action edit") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            check(expected == row.status); row = row.copy(status = ActionLifecycle.next(expected)!!, completedAt = 10L.takeIf { expected == ActionStatus.ACTIVE })
            publish(ActionChangeResult.Applied(row))
        }
    }
    private class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { error("Operations supplies people") }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) { error("No P8 person detail") }
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P8 person write") }
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P8 person write") }
    }
    private class Ops(private val actions: Actions) : OperationsRepository {
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) = publish(OperationsResult.Value(
            OperationalBoard(actionId, actions.row.status, emptyList(), listOf(OperationalPerson(Person("p", actionId, "Motte", 0),
                PersonRolePlan(emptySet(), null), PersonOperationalState(actionId, "p", null, OperationalStatus.ON_SITE, null, 0)), OperationalPerson(Person("q",actionId,"Meise",0),PersonRolePlan(emptySet(),null),null)))))
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P8 P4 mutation") }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No P8 P4 mutation") }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 P4 mutation") }
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 P4 mutation") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 P4 mutation") }
    }
    private class Tasks : TaskRepository {
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) = publish(OperationsResult.Value(TaskBoard(actionId, listOf(
            Task("t", actionId, "Synthetische Aufgabe", TaskPriority.NORMAL, TaskStatus.DONE, null, null, null, null, null, 0, 1, 1),
            Task("cancelled", actionId, "Synthetische entfallene Aufgabe", TaskPriority.NORMAL, TaskStatus.CANCELLED, null, null, null, null, null, 0, 1, null)), emptyList())))
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P8 task mutation") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No P8 task mutation") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No P8 task mutation") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No P8 task mutation") }
    }
    private class Protocols : ProtocolRepository {
        override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) = publish(ProtocolResult.Value(ProtocolSummary(0, null)))
        override fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit) { error("No P8 protocol timeline") }
        override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) { error("No P8 protocol detail") }
        override fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P8 protocol write") }
        override fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P8 protocol write") }
        override fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { error("No P8 protocol write") }
    }
    private class Expenses(private val actions:Actions):ExpenseRepository {
        var items=listOf<Expense>();var writes=0;var boardReads=0;var summaryReads=0;var completedReads=0
        override fun summary(actionId:String,publish:(OperationsResult<ExpenseSummary>)->Unit){summaryReads++;if(actions.row.status==ActionStatus.COMPLETED)completedReads++;publish(OperationsResult.Value(ExpenseSummary(items.size,ExpenseRules.sum(items.map{it.totalCents})!!)))}
        override fun board(actionId:String,publish:(OperationsResult<ExpenseBoard>)->Unit){boardReads++;if(actions.row.status==ActionStatus.COMPLETED)completedReads++;publish(OperationsResult.Value(ExpenseBoard(actionId,items)))}
        override fun create(actionId:String,input:ExpenseInput,publish:(OperationsResult<Expense>)->Unit){check(ExpenseRules.mayWrite(actions.row.status)&&ExpenseRules.valid(input));writes++;val i=ExpenseRules.normalized(input);val e=Expense("e$writes",actionId,i.title,i.totalCents,i.payments.map{ExpensePayment(it.payerLabel,it.amountCents)},writes.toLong());items=items+e;publish(OperationsResult.Value(e))}
        override fun edit(actionId:String,expected:Expense,input:ExpenseInput,publish:(OperationsResult<Expense>)->Unit){check(ExpenseRules.mayWrite(actions.row.status)&&items.contains(expected)&&ExpenseRules.valid(input));writes++;val i=ExpenseRules.normalized(input);val e=expected.copy(title=i.title,totalCents=i.totalCents,payments=i.payments.map{ExpensePayment(it.payerLabel,it.amountCents)});items=items.map{if(it.id==e.id)e else it};publish(OperationsResult.Value(e))}
        override fun delete(actionId:String,expected:Expense,publish:(OperationsResult<Unit>)->Unit){check(ExpenseRules.mayDelete(actions.row.status)&&items.contains(expected));writes++;items=items.filterNot{it.id==expected.id};publish(OperationsResult.Value(Unit))}
    }
    private class Materials:MaterialRepository {
        override fun summary(actionId:String,publish:(OperationsResult<MaterialSummary>)->Unit)=publish(OperationsResult.Value(MaterialSummary(0)))
        override fun board(actionId:String,publish:(OperationsResult<MaterialBoard>)->Unit){error("No P8 material board")}
        override fun create(actionId:String,input:MaterialInput,publish:(OperationsResult<MaterialItem>)->Unit){error("No P8 material write")}
        override fun edit(actionId:String,expected:MaterialItem,input:MaterialInput,publish:(OperationsResult<MaterialItem>)->Unit){error("No P8 material write")}
        override fun delete(actionId:String,expected:MaterialItem,publish:(OperationsResult<Unit>)->Unit){error("No P8 material write")}
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        runOnMainSync { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
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
    private fun top() { repeat(25) { if (!scroll(true)) return } }
    private fun bottom() { repeat(60) { if (!scroll(false)) return } }
    private fun visible(label: String, backwards: Boolean = label in listOf("PaG", "Lagebild", "Abgeschlossen", "Kosten eintragen", "Kosten bearbeiten")): AccessibilityNodeInfo {
        repeat(80) { attempt ->
            nodes().firstOrNull { match(it, label) && bounds(it).height() > 0 }?.let { return it }
            if (attempt > 3) scroll(backwards)
            waitForIdleSync(); SystemClock.sleep(100)
        }
        error("Expense label unreachable")
    }
    private var matchSeen=false
    private var enabledSeen=false
    private var controlClass="none"
    private fun click(label: String, backwards: Boolean = label.startsWith("‹") || label == "Übersicht") {
        matchSeen=false;enabledSeen=false
        repeat(80) {
            nodes().filter { match(it, label) }.forEach { node ->
                matchSeen=true
                var control: AccessibilityNodeInfo? = node
                while (control != null && !control.isClickable) control = control.parent
                control?.refresh()
                controlClass=control?.className?.toString().orEmpty()
                if(control?.isEnabled==true)enabledSeen=true
                if (control != null && control.isEnabled && bounds(control).height() > 0) {
                    val rect = bounds(control); check(rect.left >= 0 && rect.right <= targetContext.resources.displayMetrics.widthPixels)
                    if (control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { waitForIdleSync(); SystemClock.sleep(150); return }
                }
            }
            scroll(backwards); waitForIdleSync(); SystemClock.sleep(100)
        }
        error("Expense control unreachable")
    }
    private fun field(label: String, value: String) {
        top()
        var editable: AccessibilityNodeInfo? = visible(label, false)
        while (editable != null && !editable.isEditable) editable = editable.parent
        check(editable != null)
        check(editable!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
        waitForIdleSync(); SystemClock.sleep(100); editable!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id); waitForIdleSync(); SystemClock.sleep(100)
    }
    private fun privateHub() {
        val forbidden = listOf("Zugtickets", "Meise", "Motte", "Alex aus der Soligruppe", "Transporter")
        check(nodes().none { n -> forbidden.any { (n.text?.toString().orEmpty() + n.contentDescription?.toString().orEmpty()).contains(it) } })
    }
    override fun onStart() {
        var activity:MainActivity?=null;var phase="entry";var font=1f;var diagnosticModel:ProductViewModel?=null
        try {
            val launched=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity;activity=launched
            val density=targetContext.resources.displayMetrics.density
            for(scale in listOf(1f,2f).filter{requestedScale==null||requestedScale==it}) {
                font=scale;val session=Session();val actions=Actions();val port=Expenses(actions);lateinit var model:ProductViewModel
                runOnMainSync { launched.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,People(),operationsRepository=Ops(actions),taskRepository=Tasks(),protocolRepository=Protocols(),materialRepository=Materials(),expenseRepository=port){session.ready()};diagnosticModel=model;model.detail("synthetic");launched.setContent{CompositionLocalProvider(LocalDensity provides Density(density,scale)){ProductScreen(model)}} }
                phase="preparation-area";waitForIdleSync();click("Kosten")
                phase="preparation-draft";click("+ Kosten eintragen")
                phase="preparation-title";field("Bezeichnung","Zugtickets")
                phase="preparation-total";field("Gesamtbetrag","120,00")
                phase="preparation-copy-first";click("Meise")
                phase="preparation-payment-first";field("Betrag · Zahlung 1","50")
                phase="preparation-copy-second";click("Motte")
                phase="preparation-payment-second";field("Betrag · Zahlung 2","70")
                phase="preparation-save";visible("Zahlungen: 120,00 € von 120,00 €");click("Speichern");visible("Zugtickets");visible("Meise · 50,00 €");visible("Motte · 70,00 €")
                runOnMainSync { check(port.items.single().payments==listOf(ExpensePayment("Meise",5000),ExpensePayment("Motte",7000))) }
                phase="edit-external";click("Zugtickets");field("Bezahlt von · Zahlung 2","Alex aus der Soligruppe");click("Speichern");visible("Alex aus der Soligruppe · 70,00 €")
                phase="active-hub";top();click("Übersicht");click("Aktion starten");click("Bestätigen");visible("Lagebild");privateHub();visible("Gesamt: 120,00 €");val reads=port.boardReads
                runOnMainSync { check(model.workspace!!.expenseSummary==ExpenseSummary(1,12000));check(port.boardReads==reads) }
                phase="active-costs-open";click("Kosten öffnen");phase="active-create-open";click("+ Kosten eintragen");phase="active-create-title";field("Bezeichnung","Transporter");phase="active-create-total";field("Gesamtbetrag","180");phase="active-create-payment";click("+ freie Person / Bezeichnung");phase="active-create-payer";field("Bezahlt von · Zahlung 1","Privat vorgestreckt");phase="active-create-amount";field("Betrag · Zahlung 1","180");phase="active-create-save";click("Speichern");phase="active-create-back";top();click("‹ Zum Lagebild");visible("Gesamt: 300,00 €");privateHub()
                phase="active-draft-completion";click("Kosten öffnen");click("+ Kosten eintragen");field("Bezeichnung","Nicht speichern");top();click("‹ Zum Lagebild");click("Aktion abschließen");click("Bestätigen");visible("Abgeschlossen")
                runOnMainSync { check(model.expenses.draft==null&&!model.completedExpensesOpen&&port.completedReads==0) };bottom();top();runOnMainSync{check(port.completedReads==0)}
                phase="completed-open";click("Kosten verwalten");phase="completed-notice";visible("Kosten können auch nach Abschluss ergänzt oder korrigiert werden. Die Aktion selbst bleibt abgeschlossen.");phase="completed-edit-open";click("Zugtickets");phase="completed-edit-field";field("Bezeichnung","Zugtickets korrigiert");phase="completed-edit-save";click("Speichern");phase="completed-create-open";click("+ Kosten eintragen");phase="completed-create-fields";field("Bezeichnung","Synthetische Nachbereitung");field("Gesamtbetrag","0,50");phase="completed-create-person";click("Motte");phase="completed-create-amount";field("Betrag · Zahlung 1","0,50");phase="completed-create-save";click("Speichern")
                runOnMainSync { check(actions.row.status==ActionStatus.COMPLETED&&port.items.size==3);check(!model.tasks.writable&&!model.material.writable&&!model.operations.live&&!model.operations.planning);model.edit();check(model.editing==null) }
                phase="delete-cancel";click("Synthetische Nachbereitung");click("Kostenposition entfernen");visible("Der Kosteneintrag und seine Zahlungsaufteilung werden entfernt.");click("Eintrag behalten");click("Abbrechen");runOnMainSync{check(port.items.size==3)}
                phase="maximum-payment-controls";click("+ Kosten eintragen")
                runOnMainSync { model.expenses.change(ExpenseDraft("Synthetisches Maximum","0,32",List(32){ExpensePaymentDraft("Synthetische Bezeichnung","0,01")})) }
                bottom();visible("32 von maximal 32 Zahlungen")
                for(label in listOf("+ freie Person / Bezeichnung","Meise","Motte")) {
                    var control:AccessibilityNodeInfo?=visible(label,true)
                    while(control!=null&&!control.isClickable&&control.isEnabled)control=control.parent
                    check(control!=null&&!control.isEnabled)
                }
                runOnMainSync { check(model.expenses.draft!!.payments.size==32) }
                phase="lock-reauth";click("Sperren",true);visible("PaG");runOnMainSync{check(model.snapshot.state==SessionState.LOCKED&&model.expenses.board==null&&model.expenses.draft==null&&model.expenses.expected==null&&!model.completedExpensesOpen)};privateHub();phase="reauth-action-list";runOnMainSync{session.ready()};visible("Übungsaktion · Abgeschlossen");runOnMainSync{check(model.selected==null&&!model.completedExpensesOpen&&model.expenses.board==null)}
            }
            runOnMainSync{launched.finish()};finish(-1,Bundle().apply{putString("stream","P8 synthetic ProductScreen preparation/cent input/pseudonym/external label/active privacy/server summary/completed administration/isolation/delete cancel/lock/reauth/font=${requestedScale ?: "1+2"}: OK\n")})
        } catch(failure:Exception) {var state="unavailable";runOnMainSync{diagnosticModel?.let{state="session=${it.snapshot.state};area=${it.preparationArea};active=${it.activeArea};error=${it.expenses.error};form=${it.expenses.form};busy=${it.expenses.busy};valid=${it.expenses.draft?.input()!=null};focused=${activity?.window?.decorView?.hasWindowFocus()}"}};val root=uiAutomation.rootInActiveWindow?.packageName;activity?.let{runOnMainSync{it.finish()}};finish(0,Bundle().apply{putString("stream","P8 probe failed; font=$font; phase=$phase; technical=$state; match=$matchSeen; enabled=$enabledSeen; control=$controlClass; root=$root; lines=${failure.stackTrace.filter{it.className==P8ExpenseProbe::class.java.name}.take(5).map{it.lineNumber}}; no expense/payer/input values logged\n")})}
    }
}
