package org.pag.prototype
import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.*

/** Synthetic ports copied from the existing Ux3c fixture. No database/credentials. Same APK against both targets. */
class SemanticsActualProbe:Instrumentation(){
    private var runs=50;private var first=0;private var font="2";private var automation=true;private var reuse=false
    @Volatile private var trial=-1
    @Volatile private var phase="START"
    @Volatile private var scale=2f
    override fun onCreate(a:Bundle?){super.onCreate(a);runs=a?.getString("runs")?.toInt()?:50;first=a?.getString("first")?.toInt()?:0;font=a?.getString("font")?:"2";automation=a?.getString("automation")!="false";reuse=a?.getString("reuse")=="true";require(runs in 1..100);require(font in listOf("1","2","3.5","alternate"));start()}
    private fun emit(event:String,detail:String="")=sendStatus(0,Bundle().apply{putString("stream","ACT,$scale,$trial,$phase,$event,$detail\n")})
    private fun crash(t:Throwable){emit("CRASH",Thread.currentThread().name+","+t.javaClass.simpleName+","+t.stackTrace.take(18).joinToString("|"){it.className+"."+it.methodName+":"+it.lineNumber})}
    override fun onException(obj:Any?,t:Throwable):Boolean{crash(t);return super.onException(obj,t)}
    private fun main(f:()->Unit){var failure:Throwable?=null;runOnMainSync{try{f()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun await(f:()->Boolean){val end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){if(f())return;SystemClock.sleep(5)};error("ACTUAL_CONDITION_TIMEOUT")}
    @Suppress("DEPRECATION") private fun query(){val root=uiAutomation.rootInActiveWindow ?: return;val nodes=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo){nodes+=n;repeat(n.childCount){n.getChild(it)?.let(::walk)}};try{if(root.packageName?.toString()==targetContext.packageName)walk(root) else nodes+=root}finally{nodes.forEach{it.recycle()}}}
    internal class Session : SessionPort {
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
    internal class Actions : ActionRepository {
        val row = Action("synthetic", "Übungsaktion", "Nur synthetische Probe", 1, 2, 0, ActionStatus.ACTIVE)
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row)
        override fun createAction(action: NewAction, publish: (Action) -> Unit) {}
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) {}
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {}
    }
    internal class People : PersonRepository {
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) = publish(PersonResult.Value(emptyList()))
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(Person(personId, actionId, "Motte", 0)))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) {}
    }
    internal class Ops : OperationsRepository {
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
    internal class Tasks : TaskRepository {
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
    override fun onStart(){
        check(targetContext.packageName=="org.pag.perf.p11probe")
        val original=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler{thread,error->runCatching{crash(error)};original?.uncaughtException(thread,error)}
        var activity:MainActivity?=null;var poll:Thread?=null;val polling=AtomicBoolean(false);val pollError=AtomicReference<Throwable?>();var complete=false
        try{
            if(automation)uiAutomation.waitForIdle(100,2000)
            for(i in first until first+runs){
                trial=i;scale=if(font=="alternate")if(i%2==0)1f else 2f else font.toFloat();phase="CREATE";emit("BEGIN","automation=$automation;reuse=$reuse")
                if(activity==null)activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                val session=Session();val taskPort=Tasks();lateinit var m:ProductViewModel
                val composed=AtomicInteger(-1);val drawn=AtomicInteger(-1);val stamp=mutableIntStateOf(0)
                main{
                    m=ProductViewModel(session,Actions(),ProductStorageState.READY_TO_OPEN,People(),operationsRepository=Ops(),taskRepository=taskPort){}
                    m.detail("synthetic")
                    activity!!.setContent{val density=LocalDensity.current;val value=stamp.intValue;SideEffect{composed.set(value)};CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){Box(Modifier.fillMaxSize().drawWithContent{val v=stamp.intValue;drawContent();drawn.set(v)}){ProductScreen(m)}}}
                }
                fun ready(condition:()->Boolean){await{pollError.get()?.let{throw it};var yes=false;main{yes=condition()};yes};var version=0;main{version=stamp.intValue};await{composed.get()>=version&&drawn.get()>=version};waitForIdleSync()}
                ready{m.workspace!=null&&!m.personsLoading}
                polling.set(automation);if(automation)poll=Thread({try{while(polling.get()){query();SystemClock.sleep(5)}}catch(t:Throwable){pollError.set(t)}},"synthetic-product-query").apply{start()}
                fun step(name:String,action:()->Unit,condition:()->Boolean){phase=name;main{stamp.intValue++;action()};ready(condition)}
                step("LIST",{m.activeArea(ActiveArea.TASKS)},{m.workspace?.tasks!=null})
                step("DETAIL",{m.tasks.open(m.tasks.board!!.tasks.first{it.id=="t"})},{m.tasks.selected?.id=="t"})
                for(status in listOf(TaskStatus.IN_PROGRESS,TaskStatus.DONE,TaskStatus.OPEN))step("STATUS_$status",{m.tasks.transition(status)},{m.tasks.selected?.status==status})
                step("EDIT",{m.tasks.edit()},{m.tasks.form})
                step("INPUT",{m.tasks.change(m.tasks.input.copy(title="Synthetic changed"))},{m.tasks.input.title=="Synthetic changed"})
                step("SAVE_EDIT",{m.tasks.save()},{!m.tasks.form&&m.tasks.selected?.title=="Synthetic changed"})
                step("RETURN_LIST",{m.tasks.backToTasks()},{m.tasks.selected==null})
                step("CLOSED",{m.tasks.toggleClosed()},{m.tasks.closedExpanded})
                step("CLOSED_DETAIL",{m.tasks.open(m.tasks.board!!.tasks.first{it.id=="closed"})},{m.tasks.selected?.id=="closed"})
                step("CLOSED_RETURN",{m.tasks.backToTasks()},{m.tasks.selected==null})
                step("CREATE_FORM",{m.tasks.add()},{m.tasks.form})
                step("CREATE_INPUT",{m.tasks.change(m.tasks.input.copy(title="Synthetic new"))},{m.tasks.input.title=="Synthetic new"})
                step("CREATE_SAVE",{m.tasks.save()},{!m.tasks.form&&m.tasks.selected?.title=="Synthetic new"})
                step("CREATE_RETURN",{m.tasks.backToTasks()},{m.tasks.selected==null})
                step("FILTER",{m.tasks.showPerson("p")},{m.tasks.filter=="p"})
                step("UNFILTER",{m.tasks.showPerson(null)},{m.tasks.filter==null})
                step("LOCK",{session.lock()},{m.workspace==null&&m.selected==null&&!m.tasks.form})
                phase="CLEANUP";polling.set(false);poll?.join(10000);check(poll?.isAlive!=true);poll=null;pollError.get()?.let{throw it}
                if(!reuse){val old=activity!!;main{old.finish()};await{var destroyed=false;main{destroyed=old.isDestroyed};destroyed};activity=null}
                emit("PASS")
            }
            complete=true
        }catch(t:Throwable){emit("HARNESS_FAIL",t.javaClass.simpleName+","+t.stackTrace.take(8).joinToString("|"){it.className+"."+it.methodName+":"+it.lineNumber})}
        finally{polling.set(false);poll?.join(10000);main{activity?.finish()};waitForIdleSync()}
        finish(if(complete)-1 else 0,Bundle().apply{putString("stream",if(complete)"ACTUAL_PASS\n" else "ACTUAL_INCOMPLETE\n")})
    }
}
