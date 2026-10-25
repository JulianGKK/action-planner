package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.pag.data.product.*
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Real isolated Compose/Room/SQLCipher. P77 authentication/data are SYNTHETIC.
 * Test aliases and invalidation diagnostics are NOT owner-end evidence. */
class P110ConsumerProbe:Instrumentation(){
    private var scenario="repeat-active"
    private val uiBoundaries=setOf("ui-r-exact","ui-r-over","ui-t-exact","ui-t-over")
    private val singleMembership=setOf("person-create","task-create","task-delete","material-create","material-delete","expense-create","expense-delete","role-create","role-rename","role-delete")
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val value=AtomicReference<T>();val error=AtomicReference<Throwable?>();runOnMainSync{try{value.set(work())}catch(t:Throwable){error.set(t)}};error.get()?.let{throw it};return value.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+60_000;while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(5)};error("P110 timeout $label")}
    private fun emit(value:String)=sendStatus(0,Bundle().apply{putString("stream","P110,$scenario,$value\n")})
    private fun visible(text:String):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.isVisibleToUser && n.text?.toString()?.contains(text)==true)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    private fun reveal(text:String):Boolean {
        if(visible(text))return true
        scroll();Thread.sleep(50)
        return visible(text)
    }
    private fun scroll(back:Boolean=false):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.isScrollable && n.performAction(if(back)android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in uiBoundaries || scenario in singleMembership || scenario in setOf("repeat-active","repeat-preparation","repeat-completed","membership"));start()}
    override fun onStart(){var activity:P86FullWorkspaceActivity?=null;var harness:P77ProductRuntimeHarness?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture","p86-small").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P86FullWorkspaceActivity
            activity=a
            val h=field(a,"p77Harness") as P77ProductRuntimeHarness;harness=h
            val m=field(a,"model") as ProductViewModel
            await("focus"){main{a.hasWindowFocus()}};main{m.unlock()}
            await("READY"){main{m.snapshot.state==SessionState.READY && m.actions.isNotEmpty()}}
            val status=if(scenario in uiBoundaries)"active" else if(scenario=="membership" || scenario in singleMembership)"preparation" else scenario.removePrefix("repeat-")
            if(scenario=="role-rename" || scenario=="role-delete"){
                val seeded=AtomicReference<Role>()
                main{h.runtime.operations.createRole("p11-preparation","Synthetic P110 existing role"){result->
                    check(result is OperationsResult.Value);seeded.set(result.value)
                }}
                await("role seed"){seeded.get()!=null}
            }
            main{m.detail("p11-$status")};await("workspace"){main{!m.personsLoading && m.workspace!=null}}
            val held=main{m.workspace!!}
            check(h.observations.workspaceMaterializations.get()==1)
            emit("INITIAL,synthetic_auth=true,record=${h.outstandingState()}")
            if(scenario in uiBoundaries){
                val candidate=held.copy()
                val previous=held.uiRows!!
                val formats=UiDateFormats(previous.zone) // Test-only sizing metadata; product constructs after planning admission.
                val plan=ActiveWorkspaceBudget.estimateUnits(WorkspaceUiRows.planningCost(candidate)).second
                val display=ActiveWorkspaceBudget.estimateUnits(WorkspaceUiRows.cost(candidate,previous,formats,main{m.selected})).second
                val required=Math.addExact(plan,display)
                val state=h.outstandingState()
                fun count(name:String)=Regex("\\b"+name+"=([0-9]+)").find(state)!!.groupValues[1].toLong()
                val over=if(scenario.endsWith("over"))1L else 0L
                val retained=scenario.startsWith("ui-r-")
                val padding=(if(retained)ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES-count("localRetained") else ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-count("localTransition"))-required+over
                check(main{h.p110PadUiBoundary(held.access!!,if(retained)padding else 0L,if(retained)0L else padding)})
                main{m.javaClass.getDeclaredMethod("setWorkspace",WorkspaceSnapshot::class.java).let{it.isAccessible=true;it.invoke(m,candidate)}}
                if(over==0L){
                    check(main{m.workspace===candidate && candidate.uiRows!=null && candidate.uiRows!!.identity!==previous.identity})
                    val exact=h.outstandingState()
                    check(exact.contains((if(retained)"localRetained=${ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES}," else "localTransition=${ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES},")))
                    check(main{!previous.tasks.values.first().click.live()})
                    await("admitted presentation visible"){visible(main{m.selected!!.title})}
                }else{
                    check(main{m.workspace===held && candidate.uiRows==null && m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED})
                }
                emit("UI_BOUNDARY,stage=original_VM_UI_representation,over=$over,planning=$plan,display=$display,constructed=${candidate.uiRows!=null},full_graphs=1,full_consumer_claim=false,record=${h.outstandingState()}")
            }else if(scenario in singleMembership){
                val title="Synthetic P110 $scenario"
                when(scenario){
                    "person-create"->{
                        main{m.preparationArea(PreparationArea.PERSONS);m.addPerson();m.savePerson(title)}
                        await("person create"){main{!m.personSaving && m.workspace?.persons?.any{it.pseudonym==title}==true}}
                        await("person value visible"){visible(title)}
                    }
                    "task-create","task-delete"->{
                        main{m.preparationArea(PreparationArea.TASKS)}
                        val old=main{m.workspace!!.tasks!!.tasks.first()}
                        if(scenario=="task-create")main{m.tasks.add();m.tasks.changeTitle(title);check(m.tasks.input.title==title);m.tasks.save()}
                        else main{m.tasks.open(old);m.tasks.delete()}
                        emit("TASK_SUBMITTED,form=${main{m.tasks.form}},busy=${main{m.tasks.busy}},commit=${main{m.tasks.commitConfirmed}},session=${main{m.snapshot}},record=${h.outstandingState()}")
                        var observed=false;val started=SystemClock.elapsedRealtime()
                        await("task membership"){main{
                            if(!observed && SystemClock.elapsedRealtime()-started>5000){observed=true
                                val raw=(field(m,"workspaceValue"+"$"+"delegate") as androidx.compose.runtime.MutableState<*>).value as? WorkspaceSnapshot
                                emit("TASK_PAYLOAD,workspace_visible=${m.workspace!=null},raw_rows=${raw?.tasks?.tasks?.size},raw_title_match=${raw?.tasks?.tasks?.any{it.title==title}},selected_title_match=${m.tasks.selected?.title==title},new_access=${raw?.access!==held.access},refusal=${m.workspaceRefusal}")
                                emit("TASK_PENDING,form=${m.tasks.form},busy=${m.tasks.busy},commit=${m.tasks.commitConfirmed},error=${m.tasks.error},session=${m.snapshot},record=${h.outstandingState()}")}
                            check(m.snapshot.state==SessionState.READY){"task session revoked: ${m.snapshot},error=${m.tasks.error},commit=${m.tasks.commitConfirmed},record=${h.outstandingState()}"}
                            if(!m.tasks.busy && m.tasks.error!=null)error("task error=${m.tasks.error},commit=${m.tasks.commitConfirmed},record=${h.outstandingState()}")
                            !m.tasks.busy && m.workspace?.tasks?.tasks?.let{rows->if(scenario=="task-create")rows.any{it.title==title} else rows.none{it.id==old.id}}==true
                        }}
                        if(scenario=="task-create")await("task value visible"){visible(title)}
                        else {
                            check(main{m.tasks.selected==null && held.tasks!!.tasks.any{it.id==old.id}})
                            val label=main{m.workspace!!.tasks!!.presentation.all.let{if(TaskRules.active(old.status))it.preparationSummary else it.preparationClosedCollapsed}}
                            await("updated task count visible"){reveal(label)}
                        }
                    }
                    "material-create","material-delete"->{
                        main{m.preparationArea(PreparationArea.MATERIAL)}
                        await("material supplement"){main{m.material.board!=null && !m.workspaceRefreshing}}
                        val old=main{m.material.board!!.items.first()}
                        if(scenario=="material-create")main{m.material.create();m.material.name(title);m.material.save()}
                        else main{m.material.edit(old);m.material.requestDelete();m.material.delete()}
                        await("material membership"){main{
                            if(!m.material.busy && m.material.error!=null)error("material error=${m.material.error},commit=${m.material.commitConfirmed},record=${h.outstandingState()}")
                            !m.material.busy && m.material.board?.items?.let{rows->if(scenario=="material-create")rows.any{it.name==title} else rows.none{it.id==old.id}}==true
                        }}
                        if(scenario=="material-create"){await("new value visible"){reveal(title)}}
                        else {
                            check(main{m.workspace!!.materialBoard!!.items.none{it.id==old.id} && m.material.selected==null})
                            val remaining=main{m.material.board!!.items}
                            await("material deletion visible"){reveal(if(remaining.isEmpty())"Noch kein Material." else remaining.first().name)}
                        }
                    }
                    "expense-create","expense-delete"->{
                        main{m.preparationArea(PreparationArea.COSTS)}
                        await("expense supplement"){main{m.expenses.board!=null && !m.workspaceRefreshing}}
                        val old=main{m.expenses.board!!.expenses.first()}
                        if(scenario=="expense-create")main{m.expenses.create();m.expenses.title(title);m.expenses.total("1,00");m.expenses.addPayment("Synthetic payer");m.expenses.amount(0,"1,00");m.expenses.save()}
                        else main{m.expenses.edit(old);m.expenses.requestDelete();m.expenses.delete()}
                        await("expense membership"){main{
                            if(!m.expenses.busy && m.expenses.error!=null)error("expense error=${m.expenses.error},commit=${m.expenses.commitConfirmed},record=${h.outstandingState()}")
                            !m.expenses.busy && m.expenses.board?.expenses?.let{rows->if(scenario=="expense-create")rows.any{it.title==title} else rows.none{it.id==old.id}}==true
                        }}
                        if(scenario=="expense-create"){await("new value visible"){reveal(title)}}
                        else {
                            check(main{m.workspace!!.expenseBoard!!.expenses.none{it.id==old.id} && m.expenses.selected==null})
                            val count=main{m.expenses.board!!.expenses.size}
                            await("expense deletion count visible"){reveal("$count Einträge")}
                        }
                    }
                    else->{
                        main{m.preparationArea(PreparationArea.ROLES)}
                        val old=main{m.workspace!!.operations!!.roles.firstOrNull()}
                        if(scenario=="role-create")main{m.operations.createRole(title)}
                        else if(scenario=="role-rename")main{m.operations.editRole(old!!);m.operations.changeRoleName(title);m.operations.saveRoleRename()}
                        else main{m.operations.editRole(old!!,delete=true);m.operations.confirmRoleDelete()}
                        await("role membership"){main{
                            if(!m.operations.busy && m.operations.error!=null)error("role error=${m.operations.error},commit=${m.operations.commitConfirmed},record=${h.outstandingState()}")
                            !m.operations.busy && m.workspace?.operations?.roles?.let{rows->if(scenario=="role-delete")rows.none{it.id==old!!.id} else rows.any{it.name==title}}==true
                        }}
                        if(scenario!="role-delete"){await("new value visible"){reveal(title)}}
                        else await("empty role membership visible"){reveal("Noch keine Rollen angelegt.")}
                    }
                }
                emit("SINGLE_MEMBERSHIP,case=$scenario,new_membership_bound=true,visible_positive=${!scenario.endsWith("delete")},old_snapshot_held=true,record=${h.outstandingState()}")
            }else if(scenario=="membership"){
                main{m.preparationArea(PreparationArea.PERSONS);m.addPerson();m.savePerson("Synthetic P110 new person")}
                await("person membership"){main{!m.personSaving && m.workspace?.persons?.any{it.pseudonym=="Synthetic P110 new person"}==true}}
                await("person visible"){visible("Synthetic P110 new person")}
                check(held.persons.none{it.pseudonym=="Synthetic P110 new person"})
                main{m.backToPersons();m.preparationArea(PreparationArea.TASKS);m.tasks.add();m.tasks.changeTitle("Synthetic P110 new task");m.tasks.save()}
                try{await("task membership"){main{!m.tasks.busy && m.workspace?.tasks?.tasks?.any{it.title=="Synthetic P110 new task"}==true}}}
                catch(t:Throwable){emit("TASK_STATE,busy=${main{m.tasks.busy}},form=${main{m.tasks.form}},error=${main{m.tasks.error}},commit=${main{m.tasks.commitConfirmed}},refusal=${main{m.workspaceRefusal}},record=${h.outstandingState()}");throw t}
                val created=main{m.workspace!!.tasks!!.tasks.first{it.title=="Synthetic P110 new task"}}
                await("new task visible"){visible("Synthetic P110 new task")}
                main{m.tasks.open(created);m.tasks.delete()}
                await("task deletion"){main{!m.tasks.busy && m.workspace?.tasks?.tasks?.none{it.id==created.id}==true}}
                check(main{m.tasks.selected==null})
                emit("MEMBERSHIP,person_create_visible=true,task_create_visible=true,task_delete_absent=true,old_snapshot_held=true")
            }else{
                if(status=="preparation")main{m.preparationArea(PreparationArea.TASKS)}
                if(status=="active")main{m.activeArea(ActiveArea.TASKS)}
                await("task list visible"){main{m.workspace?.tasks!=null}}
                repeat(8){scroll()};repeat(8){scroll(true)}
                val board=main{m.workspace!!.tasks!!}
                val projection=activeTaskContent(board,null)
                val aliases=List(32_768){activeTaskContent(board,null)}
                check(aliases.all{it===projection})
                val task=board.tasks.firstOrNull()
                val personId=held.persons.first().id
                val taskRow=main{task?.let{m.taskRow(it)}}
                val personRow=main{m.personRow(personId)!!}
                val row:Any=taskRow?:personRow
                val oldClick=taskRow?.click?:personRow.detail
                val before=main{a.p110RowRenders}
                val firstPulse=main{a.p110Composed}
                for(i in 1..1000){
                    if(i%100==1){
                        val at=SystemClock.uptimeMillis()
                        for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                            val event=android.view.MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,10f,200f,0)
                            try{sendPointerSync(event)}finally{event.recycle()}
                        }
                    }
                    main{a.p110Invalidate()}
                    await("frame $i"){main{a.p110Composed>=firstPulse+i}}
                    check(main{m.workspace===held && (if(task!=null)m.taskRow(task) else m.personRow(personId))===row && m.snapshot.state==SessionState.READY})
                    if(i==100 || i==1000)emit("REPEAT,frames=$i,rows=${main{a.p110RowRenders}-before},same_list=true,same_row=true,record=${h.outstandingState()}")
                }
                if(status=="active"){
                    val person=held.operations!!.persons.first()
                    main{m.tasks.showPerson(person.person.id)};main{m.tasks.showPerson(null)}
                    main{m.activeArea(ActiveArea.PERSONS)};await("person list"){visible(person.person.pseudonym)}
                    repeat(3){scroll()};repeat(3){scroll(true)}
                    main{m.activeArea(ActiveArea.TASKS)}
                }
                check(main{aliases.first()===projection && m.workspace===held})
                main{m.lock();oldClick()};check(main{m.tasks.selected==null && m.selectedPerson==null && m.workspace==null})
                emit("REVOKE,held_old_snapshot_and_row=true,late_click_no_publish=true")
            }
            check(h.observations.workspaceMaterializations.get()==1)
            main{m.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("PASS,full_graphs=1,fixture_cleanup=true,no_physical_memory_claim=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P110 PASS $scenario")})
        }catch(t:Throwable){emit("FAILURE,"+t.stackTraceToString());runCatching{main{activity?.finish()};harness?.let{h->await("failure cleanup"){h.cleanupConfirmed.get()}}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P110 FAIL $scenario")})}
    }
}
