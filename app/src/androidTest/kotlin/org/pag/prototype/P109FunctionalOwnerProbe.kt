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
class P109FunctionalOwnerProbe:Instrumentation(){
    private var p111:P111MemorySampler?=null
    override fun finish(resultCode:Int,results:Bundle?){p111?.close();super.finish(resultCode,results)}
    private var scenario="exposed"
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(30)};error("P109 timeout $label")}
    private fun emit(s:String){p111?.mark(s);sendStatus(0,Bundle().apply{putString("stream","P109,$scenario,$s\n")})}
    private fun <T> call(work:((T)->Unit)->Unit):T{val v=AtomicReference<T>();val n=AtomicInteger()
        main{work{v.set(it);n.incrementAndGet()}};await("one callback"){n.get()==1};return v.get()}
    private fun visible(text:String,fragment:Boolean=false):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun walk(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.isVisibleToUser && (n.text?.toString()==text || (fragment && n.text?.toString()?.contains(text)==true)))return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(walk(c))return true}finally{c.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && walk(root)}finally{root.recycle()}
    }
    private var revealForward=true
    private fun reveal(text:String):Boolean {
        if(visible(text,true))return true
        val root=uiAutomation.rootInActiveWindow?:return false
        fun scroll(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            val action=if(revealForward)android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if(n.isScrollable && n.actionList.any{it.id==action} && n.performAction(action))return true
            for(i in 0 until n.childCount){val child=n.getChild(i)?:continue;try{if(scroll(child))return true}finally{child.recycle()}}
            return false
        }
        try{if(root.packageName?.toString()==targetContext.packageName && !scroll(root)){revealForward=!revealForward;scroll(root)}}finally{root.recycle()}
        runCatching{uiAutomation.waitForIdle(100,1000)}
        return visible(text,true)
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
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);p111=P111MemorySampler.start(this,arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("preparation","completed","active","stale","large","person","plan","material","expense","lock-detail","lost-detail-main","duplicate-detail-main","unknown-detail"));start()}
    override fun onStart(){var activity:Activity?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture",when(scenario){"large"->"p86-stress32";"active"->"p87-4999";else->"p86-small"}).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a
            h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            p111?.bind(h,a,model)
            await("focus"){main{a.hasWindowFocus()}};main{model.unlock()};await("READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
            val id=when(scenario){"stale","person","plan","material","expense","lock-detail","lost-detail-main","duplicate-detail-main","unknown-detail"->"p11-preparation";"large"->"p11-active";else->"p11-$scenario"}
            if(scenario=="plan")check(call<OperationsResult<Role>>{h.runtime.operations.createRole(id,"Synthetic P109 role",it)} is OperationsResult.Value)
            main{model.detail(id)}
            if(scenario=="large"){
                await("large refusal"){main{!model.personsLoading && model.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED}}
                await("visible budget refusal"){visible("Die Vollansicht überschreitet das zulässige Speicherbudget.")}
                check(h.observations.workspaceMaterializations.get()==0)
                emit("LARGE,fixture=200_persons_1000_tasks_5000_events_32_roles,full_graphs=0,budget_refused_before_DTO=true")
                main{model.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
                emit("CASE,ANDROID_PASS,fixture_cleanup=true,closure=UNDER_TEST")
                finish(Activity.RESULT_OK,Bundle().apply{putString("result","P109 PASS $scenario")});return
            }
            await("initial workspace"){main{!model.personsLoading && model.workspace!=null}}
            val held=main{checkNotNull(model.workspace)} // Explicit TEST alias, never owner-end evidence.
            val scope=checkNotNull(held.access)
            check(h.observations.workspaceMaterializations.get()==1)
            await("visible title"){visible(main{model.selected!!.title})}
            emit("INITIAL,synthetic_auth=true,visible=true,persons=${held.persons.size},full_graphs=1,record=${h.outstandingState()}")
            val person=held.persons.first()
            if(scenario in setOf("preparation","stale","person","plan","material","expense"))main{model.preparationArea(PreparationArea.PERSONS)}
            if(scenario=="active")main{model.activeArea(ActiveArea.PERSONS)}
            main{model.personDetail(person.id)}
            await("person detail"){main{!model.personDetailLoading && model.selectedPerson===person}}
            await("visible person"){visible(person.pseudonym)}
            await("inventory returned"){main{!model.sensitiveInventoryLoading}}
            emit("PERSON,same_row_identity=true,visible=true,no_second_full_graph=true")
            if(scenario in setOf("lock-detail","lost-detail-main","duplicate-detail-main","unknown-detail")){
                val delivered=AtomicInteger();val values=AtomicInteger()
                val request=WorkspaceDetailRequest(held.actionId,held.status,held.generation,WorkspaceDetailKind.PERSON,person.id,WorkspaceReadTicket())
                if(scenario=="unknown-detail"){
                    main{
                        val pending=checkNotNull(h.mutations.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.PERSON,held.actionId),held.generation))
                        check(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)))
                    }
                    val rejected=call<WorkspaceDetailResult>{h.runtime.workspace.detail(scope,request,it)}
                    check(rejected==WorkspaceDetailResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                    emit("NEGATIVE_DETAIL,UNKNOWN_metadata_injection=true,actual_DB_write_NOT_claimed=true,new_authority=REFUSED,no_payload=true")
                }else{
                    if(scenario=="lock-detail")h.faults.worker.holdNext() else h.faults.main.holdNext()
                    main{h.runtime.workspace.detail(scope,request){delivered.incrementAndGet();if(it is WorkspaceDetailResult.Value)values.incrementAndGet()}}
                    if(scenario=="lock-detail"){
                        await("detail worker held"){h.faults.worker.isHeld()}
                        main{model.lock()};h.faults.worker.release()
                        Thread.sleep(300);main{}
                        check(delivered.get()==0)
                        emit("NEGATIVE_DETAIL,lock_before_worker=true,callbacks=0,old_snapshot_held=true")
                    }else{
                        await("detail Main held"){h.faults.main.isHeld()}
                        val task=h.faults.main.take()
                        if(scenario=="lost-detail-main"){
                            check(delivered.get()==0)
                            val denied=call<WorkspaceDetailResult>{h.runtime.workspace.detail(scope,request,it)}
                            check(denied==WorkspaceDetailResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                            main{model.lock();task.run();task.run()}
                            check(values.get()==0)
                            emit("NEGATIVE_DETAIL,lost_Main=true,second_projection=REFUSED,late_locked_duplicates_values=0,old_costs_kept=true")
                        }else{
                            main{task.run();task.run()}
                            check(delivered.get()==1 && values.get()==1)
                            emit("NEGATIVE_DETAIL,double_Main=true,callbacks=1,old_costs_kept=true")
                        }
                    }
                }
            }else if(scenario=="stale"){
                main{model.editPerson()}
                check(main{model.personForm && model.editingPerson===person})
                val draft="Synthetic P109 stale draft"
                await("editable draft"){setText(draft)}
                await("draft visible"){visible(draft)}
                val changed=call<PersonResult<Person>>{h.runtime.persons.updatePerson(held.actionId,person,"Synthetic P109 concurrent edit",it)}
                check(changed is PersonResult.Value && changed.value.editRevision==person.editRevision+1)
                main{model.savePerson(draft)}
                await("stale returned"){main{!model.personSaving && model.personError==PersonRejection.STALE}}
                check(main{model.personForm && model.editingPerson===person && model.feedback==null})
                emit("STALE,original_Expected_preserved=true,no_success=true,form_flag=true,draft_visible=${visible(draft)},workspace_available=${main{model.workspace!=null}},reload_not_yet_proven=true")
                main{model.reloadPersonEditor()}
                await("explicit reload success"){main{!model.personSaving && model.personError==null && model.editingPerson?.editRevision==person.editRevision+1}}
                await("fresh form text visible"){visible("Synthetic P109 concurrent edit")}
                check(main{model.editingPerson!==person && model.personForm && model.feedback==null})
                check(main{!scope.publish(held.actionId,held.status,held.generation){error("old ticket revived")}})
                emit("STALE_RELOAD,explicit_request=SUCCESS,revision=${main{model.editingPerson!!.editRevision}},new_text_visible=true,old_Expected_held=true,full_graphs=1,flow=PASS,record=${h.outstandingState()}")
            }else if(scenario=="person"){
                main{model.editPerson()}
                val title="Synthetic P109 saved person"
                await("person input"){setText(title)};await("person draft visible"){visible(title)}
                main{model.savePerson(title)}
                await("person postcommit bind"){main{!model.personSaving && !model.personForm && model.selectedPerson?.pseudonym==title}}
                await("updated person visible"){visible(title)};await("person revision visible"){visible("Revision: 1")}
                check(main{model.workspace!=null && !scope.publish(held.actionId,held.status,held.generation){error("old ticket")}})
                emit("PERSON_COMMITTED,new_value_visible=true,revision=1,old_snapshot_held=true,old_ticket_revoked=true,full_graphs=1,record=${h.outstandingState()}")
            }else if(scenario=="plan"){
                main{model.operations.plan(person.id)}
                val role=held.operations!!.roles.first()
                main{model.operations.toggleRole(role.id);model.operations.primary(role.id);model.operations.savePlan()}
                await("plan postcommit bind"){main{!model.operations.busy && model.workspace?.operations?.persons?.single{it.person.id==person.id}?.plan?.editRevision==1L}}
                main{model.operations.plan(person.id)}
                check(main{role.id in model.operations.roleIds && model.operations.primaryRoleId==role.id})
                await("new plan Expected visible"){visible("Revision: 1") && visible("✓ Synthetic P109 role")}
                emit("PLAN_COMMITTED,new_expected_revision=1,role_selection=true,full_graphs=1,record=${h.outstandingState()}")
            }else if(scenario=="material" || scenario=="expense"){
                main{model.backToPersons();model.preparationArea(if(scenario=="material")PreparationArea.MATERIAL else PreparationArea.COSTS)}
                val title="Synthetic P109 updated $scenario"
                if(scenario=="material"){
                    await("material board"){main{model.material.board!=null && !model.workspaceRefreshing}}
                    val old=main{model.material.board!!.items.first()}
                    await("material list rendered before edit"){reveal(old.name)}
                    main{model.material.edit(old);model.material.name(title);model.material.save()}
                    await("material postcommit bind"){main{!model.material.busy && !model.material.form && model.material.board?.items?.any{it.id==old.id && it.name==title}==true}}
                }else{
                    await("expense board"){main{model.expenses.board!=null && !model.workspaceRefreshing}}
                    val old=main{model.expenses.board!!.expenses.first()}
                    await("expense list rendered before edit"){reveal(old.title)}
                    main{model.expenses.edit(old);model.expenses.title(title);model.expenses.save()}
                    await("expense postcommit bind"){main{!model.expenses.busy && !model.expenses.form && model.expenses.board?.expenses?.any{it.id==old.id && it.title==title}==true}}
                }
                emit("MODULE_UI_STATE,"+main{"area=${model.preparationArea},form=${model.material.form},expenseForm=${model.expenses.form},error=${model.workspaceError},refusal=${model.workspaceRefusal},screenLive=${model.workspace?.uiRows?.inputScreen?.live()},record=${h.outstandingState()}"})
                uiAutomation.rootInActiveWindow?.let{root->
                    val texts=ArrayList<String>()
                    fun walk(n:android.view.accessibility.AccessibilityNodeInfo){
                        if(n.packageName?.toString()==targetContext.packageName)n.text?.toString()?.let{texts.add(it)}
                        for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{walk(c)}finally{c.recycle()}}
                    }
                    try{walk(root);emit("MODULE_UI_TEXT,"+texts.joinToString(" | "))}finally{root.recycle()}
                }
                await("updated module value visible"){reveal(title)}
                emit("MODULE_COMMITTED,kind=$scenario,new_value_visible=true,full_graphs=1,record=${h.outstandingState()}")
            }else if(scenario=="preparation"){
                main{model.operations.plan(person.id)}
                check(main{model.operations.screen==OperationsScreen.PLAN})
                emit("PLAN,opened=true,revision=${held.operations!!.persons.single{it.person.id==person.id}.plan.editRevision}")
                main{model.operations.cancel()}
                await("cancel inventory returned"){main{!model.sensitiveInventoryLoading}}
                main{model.backToPersons();model.preparationArea(PreparationArea.TASKS)}
                check(main{model.workspace!!.tasks===held.tasks})
                val task=held.tasks!!.tasks.first()
                main{model.tasks.open(task);model.tasks.edit();model.tasks.changeTitle("Synthetic P109 cancelled draft")}
                await("task draft visible"){visible("Synthetic P109 cancelled draft")}
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
                main{model.tasks.edit();model.tasks.changeTitle("Synthetic P109 confirmed edit")}
                await("edit visible"){visible("Synthetic P109 confirmed edit")}
                main{model.tasks.save()}
                await("writer returned"){main{!model.tasks.busy}}
                check(main{!scope.publish(held.actionId,held.status,held.generation){error("old epoch revived")}})
                val stored=h.p108TaskScalars(held.actionId,task.id,"Synthetic P109 confirmed edit")
                check(stored=="title_match=1,revision=1,events=${first.total}")
                emit("STORAGE,$stored,edit_has_no_new_TaskEvent=true")
                await("new task bound"){main{model.tasks.selected?.editRevision==task.editRevision+1 && !model.tasks.form}}
                await("updated task visible"){visible("Synthetic P109 confirmed edit")}
                await("revision visible"){visible("Revision: 1")}
                emit("AFTER_COMMIT,old_scope_revoked=true,workspace_available=${main{model.workspace!=null}},new_revision_visible=true,new_title_visible=true,flow=PASS,record=${h.outstandingState()}")
            }else emit("COMPLETED,readonly_person_detail=true,original_graph_used=true")
            if(main{model.snapshot.state!=SessionState.READY}){
                main{model.unlock()};await("negative case reauth"){main{model.snapshot.state==SessionState.READY}}
            }
            val request=WorkspaceReadRequest(held.actionId,held.status,main{model.snapshot.generation},setOf(WorkspacePart.TASKS))
            check(call<WorkspaceResult>{h.runtime.workspace.read(request,it)}==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
            check(h.observations.workspaceMaterializations.get()==1)
            main{model.lock()}
            check(main{!scope.publish(held.actionId,held.status,held.generation){error("locked access")}})
            main{model.unlock()};await("reauth READY"){main{model.snapshot.state==SessionState.READY}}
            check(main{!scope.publish(held.actionId,held.status,held.generation){error("old generation")}})
            emit("NEGATIVE,held_TEST_snapshot=true,second_full_graph=REFUSED,lock_and_new_generation=REFUSED,record=${h.outstandingState()}")
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            main{model.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,reuse=false,ramReuse=false,closure=UNDER_TEST")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P109 PASS $scenario")})
        }catch(t:Throwable){emit("FAILURE,"+t.stackTraceToString());runCatching{main{activity?.finish()};if(::h.isInitialized)await("failure fixture cleanup"){h.cleanupConfirmed.get()}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P109 FAIL $scenario")})}
    }
}
