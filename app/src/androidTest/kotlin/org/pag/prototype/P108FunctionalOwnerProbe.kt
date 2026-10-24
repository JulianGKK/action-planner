package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.pag.data.product.*
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/** Real product preflight + original record, Room/SQLCipher; P77 auth is SYNTHETIC.
 * Read admission is not a global Fullview/Form owner-end or draft-allocation proof. */
class P108FunctionalOwnerProbe:Instrumentation(){
    private var scenario="exposed"
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(30)};error("P108 timeout $label")}
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream","P108,$scenario,$s\n")})
    private fun <T> call(work:((T)->Unit)->Unit):T{val v=AtomicReference<T>();val n=AtomicInteger()
        main{work{v.set(it);n.incrementAndGet()}};await("one callback"){n.get()==1};return v.get()}
    private fun visible(text:String):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun walk(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.isVisibleToUser && n.text?.toString()==text)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(walk(c))return true}finally{c.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && walk(root)}finally{root.recycle()}
    }
    private fun setText(value:String):Boolean {
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(n.isVisibleToUser && n.isEditable)return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})
            for(i in 0 until n.childCount){val child=n.getChild(i)?:continue;try{if(visit(child))return true}finally{child.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("preparation","completed","active","stale","large"));start()}
    override fun onStart(){var activity:Activity?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture",when(scenario){"large"->"p86-stress32";"active"->"p87-4999";else->"p86-small"}).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a
            h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            await("focus"){main{a.hasWindowFocus()}};main{model.unlock()};await("READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
            val id=when(scenario){"stale"->"p11-preparation";"large"->"p11-active";else->"p11-$scenario"}
            main{model.detail(id)}
            if(scenario=="large"){
                await("large refusal"){main{!model.personsLoading && model.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED}}
                await("visible budget refusal"){visible("Die Vollansicht überschreitet das zulässige Speicherbudget.")}
                check(h.observations.workspaceMaterializations.get()==0)
                emit("LARGE,fixture=200_persons_1000_tasks_5000_events_32_roles,full_graphs=0,budget_refused_before_DTO=true")
                main{model.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
                emit("CASE,ANDROID_PASS,fixture_cleanup=true,closure=BLOCKED")
                finish(Activity.RESULT_OK,Bundle().apply{putString("result","P108 PASS $scenario")});return
            }
            await("initial workspace"){main{!model.personsLoading && model.workspace!=null}}
            val held=main{checkNotNull(model.workspace)} // Explicit TEST alias, never owner-end evidence.
            val scope=checkNotNull(held.access)
            check(h.observations.workspaceMaterializations.get()==1)
            await("visible title"){visible(main{model.selected!!.title})}
            emit("INITIAL,synthetic_auth=true,visible=true,persons=${held.persons.size},full_graphs=1,record=${h.outstandingState()}")
            val person=held.persons.first()
            if(scenario=="preparation" || scenario=="stale")main{model.preparationArea(PreparationArea.PERSONS)}
            if(scenario=="active")main{model.activeArea(ActiveArea.PERSONS)}
            main{model.personDetail(person.id)}
            await("person detail"){main{!model.personDetailLoading && model.selectedPerson===person}}
            await("visible person"){visible(person.pseudonym)}
            await("inventory returned"){main{!model.sensitiveInventoryLoading}}
            emit("PERSON,same_row_identity=true,visible=true,no_second_full_graph=true")
            if(scenario=="stale"){
                main{model.editPerson()}
                check(main{model.personForm && model.editingPerson===person})
                val draft="Synthetic P108 stale draft"
                await("editable draft"){setText(draft)}
                await("draft visible"){visible(draft)}
                val changed=call<PersonResult<Person>>{h.runtime.persons.updatePerson(held.actionId,person,"Synthetic P108 concurrent edit",it)}
                check(changed is PersonResult.Value && changed.value.editRevision==person.editRevision+1)
                main{model.savePerson(draft)}
                await("stale returned"){main{!model.personSaving && model.personError==PersonRejection.STALE}}
                check(main{model.personForm && model.editingPerson===person && model.feedback==null})
                emit("STALE,original_Expected_preserved=true,no_success=true,form_flag=true,draft_visible=${visible(draft)},workspace_available=${main{model.workspace!=null}},reload_not_yet_proven=true")
                main{model.reloadPersonEditor()}
                await("explicit reload refusal"){main{!model.personSaving && model.personError==PersonRejection.OWNER_UNCONFIRMED}}
                check(main{model.editingPerson===person && model.personForm && model.feedback==null})
                emit("STALE_RELOAD,explicit_request=OWNER_UNCONFIRMED,Expected_NOT_replaced=true,success=false,full_graphs=1,flow=BLOCKED")
            }else if(scenario=="preparation"){
                main{model.operations.plan(person.id)}
                check(main{model.operations.screen==OperationsScreen.PLAN})
                emit("PLAN,opened=true,revision=${held.operations!!.persons.single{it.person.id==person.id}.plan.editRevision}")
                main{model.operations.cancel()}
                await("cancel inventory returned"){main{!model.sensitiveInventoryLoading}}
                main{model.backToPersons();model.preparationArea(PreparationArea.TASKS)}
                check(main{model.workspace!!.tasks===held.tasks})
                val task=held.tasks!!.tasks.first()
                main{model.tasks.open(task);model.tasks.edit();model.tasks.changeTitle("Synthetic P108 cancelled draft")}
                await("task draft visible"){visible("Synthetic P108 cancelled draft")}
                main{model.tasks.cancel();model.preparationArea(PreparationArea.MATERIAL)}
                emit("MATERIAL_REQUEST,refreshing=${main{model.workspaceRefreshing}},refusal=${main{model.workspaceRefusal}},materializations=${h.observations.materialSupplements.get()},record=${h.outstandingState()}")
                await("material supplement"){main{model.material.board!=null && !model.workspaceRefreshing}}
                val mb=main{model.material.board!!}
                check(h.observations.materialSupplements.get()==1)
                main{model.preparationArea(PreparationArea.TASKS);model.preparationArea(PreparationArea.MATERIAL)}
                check(main{model.material.board===mb})
                main{model.preparationArea(PreparationArea.COSTS)}
                await("expense supplement"){main{model.expenses.board!=null && !model.workspaceRefreshing}}
                val eb=main{model.expenses.board!!}
                main{model.preparationArea(PreparationArea.TASKS);model.preparationArea(PreparationArea.COSTS)}
                check(main{model.expenses.board===eb})
                check(h.observations.materialSupplements.get()==1 && h.observations.expenseSupplements.get()==1)
                emit("NAVIGATION,task_draft_open_change_cancel=true,material_same_board=true,expense_same_board=true,each_supplement=1,full_graphs=1,record=${h.outstandingState()}")
            }else if(scenario=="active"){
                main{model.backToPersons();model.activeArea(ActiveArea.TASKS)}
                val task=held.tasks!!.tasks.minBy{it.id}
                main{model.tasks.open(task);model.tasks.loadHistory(true)}
                await("history first"){main{model.tasks.history!=null || model.tasks.historyError!=null}}
                val first=main{checkNotNull(model.tasks.history){"history error ${model.tasks.historyError}"}}
                check(first.events.size==32 && first.next!=null)
                main{model.tasks.loadHistory()}
                await("history second"){main{model.tasks.history!=null || model.tasks.historyError!=null}}
                val second=main{checkNotNull(model.tasks.history)}
                check(second.events.size==32 && first.events.map{it.id}.intersect(second.events.map{it.id}.toSet()).isEmpty())
                emit("HISTORY,two_pages=32+32,old_TEST_page_held=true,distinct=true,full_graphs=1,record=${h.outstandingState()}")
                main{model.tasks.edit();model.tasks.changeTitle("Synthetic P108 confirmed edit")}
                await("edit visible"){visible("Synthetic P108 confirmed edit")}
                main{model.tasks.save()}
                await("writer returned"){main{!model.tasks.busy}}
                check(main{!scope.publish(held.actionId,held.status,held.generation){error("old epoch revived")}})
                val stored=h.p108TaskScalars(held.actionId,task.id,"Synthetic P108 confirmed edit")
                check(stored=="title_match=1,revision=1,events=${first.total}")
                emit("STORAGE,$stored,edit_has_no_new_TaskEvent=true")
                emit("AFTER_COMMIT,old_scope_revoked=true,workspace_available=${main{model.workspace!=null}},refusal=${main{model.workspaceRefusal}},task_error=${main{model.tasks.error}},global_reload=BLOCKED,no_retirement_claim=true")
            }else emit("COMPLETED,readonly_person_detail=true,original_graph_used=true")
            val request=WorkspaceReadRequest(held.actionId,held.status,held.generation,setOf(WorkspacePart.TASKS))
            check(call<WorkspaceResult>{h.runtime.workspace.read(request,it)}==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
            check(h.observations.workspaceMaterializations.get()==1)
            main{model.lock()}
            check(main{!scope.publish(held.actionId,held.status,held.generation){error("locked access")}})
            main{model.unlock()};await("reauth READY"){main{model.snapshot.state==SessionState.READY}}
            check(main{!scope.publish(held.actionId,held.status,held.generation){error("old generation")}})
            emit("NEGATIVE,held_TEST_snapshot=true,second_full_graph=REFUSED,lock_and_new_generation=REFUSED,record=${h.outstandingState()}")
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            main{model.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,reuse=false,ramReuse=false,closure=BLOCKED")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P108 PASS $scenario")})
        }catch(t:Throwable){emit("FAILURE,"+t.stackTraceToString());runCatching{main{activity?.finish()};if(::h.isInitialized)await("failure fixture cleanup"){h.cleanupConfirmed.get()}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P108 FAIL $scenario")})}
    }
}
