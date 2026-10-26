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
    private var p111:P111MemorySampler?=null
    override fun finish(resultCode:Int,results:Bundle?){p111?.close();super.finish(resultCode,results)}
    private var scenario="repeat-active"
    private val uiBoundaries=setOf("ui-r-exact","ui-r-over","ui-t-exact","ui-t-over")
    private val controlBoundaries=setOf("control-r-exact","control-r-over","control-t-exact","control-t-over")
    private val followCases=setOf("protocol-create","memory-create","state-edit")
    private val singleMembership=setOf("person-create","task-create","task-delete","material-create","material-delete","expense-create","expense-delete","role-create","role-rename","role-delete")
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val value=AtomicReference<T>();val error=AtomicReference<Throwable?>();runOnMainSync{try{value.set(work())}catch(t:Throwable){error.set(t)}};error.get()?.let{throw it};return value.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+60_000;while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(5)};error("P110 timeout $label")}
    private fun emit(value:String){p111?.mark(value);sendStatus(0,Bundle().apply{putString("stream","P110,$scenario,$value\n")})}
    private fun visible(text:String):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.isVisibleToUser && n.text?.toString()?.contains(text)==true)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    private fun editVisibleText(value:String):Boolean {
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(n.isVisibleToUser && n.isEditable)return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply{putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}};return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    private fun clickLabel(label:String):Boolean {
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(n.isVisibleToUser && n.text?.toString()==label){
                if(n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return true
                val parent=n.parent
                if(parent!=null)try{if(parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return true}finally{parent.recycle()}
            }
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}};return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    private var revealForward=true
    private fun reveal(text:String):Boolean {
        if(visible(text))return true
        if(!scroll(!revealForward)){revealForward=!revealForward;scroll(!revealForward)}
        runCatching{uiAutomation.waitForIdle(100,1000)}
        return visible(text)
    }
    private fun scroll(back:Boolean=false):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            val action=if(back)android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            if(n.isScrollable && n.actionList.any{it.id==action} && n.performAction(action))return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    override fun onException(obj:Any?,e:Throwable):Boolean { emit("ASYNC_FAILURE,"+e.stackTraceToString());return super.onException(obj,e) }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);p111=P111MemorySampler.start(this,arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario=="postcommit-ui-refusal" || scenario=="material-input" || scenario=="long-role-input" || scenario=="form-repeat" || scenario=="action-revoke" || scenario in controlBoundaries || scenario in followCases || scenario in uiBoundaries || scenario in singleMembership || scenario in setOf("repeat-active","repeat-preparation","repeat-completed","repeat-completed-overview","membership"));start()}
    override fun onStart(){var activity:P86FullWorkspaceActivity?=null;var harness:P77ProductRuntimeHarness?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture","p86-small").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P86FullWorkspaceActivity
            activity=a
            val h=field(a,"p77Harness") as P77ProductRuntimeHarness;harness=h
            val m=field(a,"model") as ProductViewModel
            p111?.bind(h,a,m)
            await("focus"){main{a.hasWindowFocus()}};main{m.unlock()}
            await("READY"){main{m.snapshot.state==SessionState.READY && m.actions.isNotEmpty()}}
            val status=if(scenario=="repeat-completed-overview")"completed" else if(scenario in uiBoundaries || scenario in controlBoundaries || scenario=="postcommit-ui-refusal" || scenario=="action-revoke" || scenario=="form-repeat" || scenario in followCases)"active" else if(scenario=="material-input" || scenario=="long-role-input" || scenario=="membership" || scenario in singleMembership)"preparation" else scenario.removePrefix("repeat-")
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
            if(scenario=="postcommit-ui-refusal"){
                val expected=held.tasks!!.tasks.first();val value="Synthetic committed before UI refusal"
                main{m.activeArea(ActiveArea.TASKS);m.tasks.open(expected);m.tasks.edit();m.tasks.changeTitle(value);m.tasks.save()}
                await("confirmed commit and refreshed model"){main{!m.tasks.busy && !m.tasks.form && m.workspace?.tasks?.tasks?.any{it.id==expected.id && it.title==value && it.editRevision==expected.editRevision+1}==true}}
                await("committed detail visible"){reveal(value)}
                val bound=main{m.workspace!!}
                main{
                    val state=h.outstandingState();val charge=Regex("\\blocalRetained=([0-9]+)").find(state)!!.groupValues[1].toLong()
                    check(h.p110PadUiBoundary(bound.access!!,ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES-charge,0))
                    m.tasks.backToTasks()
                }
                await("deferred UI refusal"){main{m.workspaceError && m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED}}
                await("saved outcome distinct from refused view"){visible(OriginalFeedbackCredit.savedNotRefreshed)}
                check(main{m.workspace?.tasks?.tasks?.single{it.id==expected.id}?.editRevision==expected.editRevision+1})
                emit("POSTCOMMIT_UI_REFUSAL,committed=true,new_view_refused=true,exact_saved_warning_visible=true,no_second_write=true,old_snapshot_held=true,record=${h.outstandingState()}")
            }else if(scenario=="material-input"){
                main{m.preparationArea(PreparationArea.MATERIAL)}
                await("material module"){main{m.material.board!=null && !m.workspaceRefreshing}}
                val expected=main{m.material.board!!.items.first()}
                await("material row rendered"){reveal(expected.name)}
                main{m.material.edit(expected)}
                await("material editor rendered"){main{m.material.form} && reveal(expected.name)}
                val aliases=ArrayList<MaterialInput>()
                for(i in 1..100){
                    val value="Synthetic material input $i"
                    await("material accessibility input $i"){editVisibleText(value)}
                    await("material typed value visible $i"){main{m.material.input?.name==value} && visible(value)}
                    aliases.add(main{m.material.input!!})
                    if(i==1 || i==100)emit("MATERIAL_INPUT,updates=$i,old_drafts_held=${aliases.size},record=${h.outstandingState()}")
                }
                await("save material through UI"){reveal("Speichern") && clickLabel("Speichern")}
                await("material committed and rebound"){main{if(m.material.error!=null)error("material error=${m.material.error},commit=${m.material.commitConfirmed},record=${h.outstandingState()}");!m.material.busy && !m.material.form && m.material.board?.items?.any{it.id==expected.id && it.name==aliases.last().name}==true}}
                await("material committed value visible"){reveal(aliases.last().name)}
                check(aliases.first().name=="Synthetic material input 1")
                emit("MATERIAL_INPUT_COMMITTED,new_value_visible=true,old_expected_and_100_drafts_held=true,record=${h.outstandingState()}")
            }else if(scenario=="long-role-input"){
                check(p111!=null)
                main{m.preparationArea(PreparationArea.ROLES);m.operations.roles();m.operations.beginRoleCreate(RoleCreateSurface.ROLES)}
                await("role text field"){visible("Rollenname")}
                val aliases=ArrayList<String>();var accepted=0;var refused=false
                for(i in 1..100){
                    val old=main{m.operations.createName}
                    val value="Ä界🦋".repeat(i*32)+" synthetic role $i"
                    await("long real SET_TEXT $i"){editVisibleText(value)}
                    await("long draft or typed refusal $i"){main{m.operations.createName==value || m.operations.error==OperationsFailure.BUDGET_REFUSED || m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED}}
                    if(main{m.operations.createName!=value}){
                        check(main{m.operations.createName===old && !m.operations.busy && !m.operations.commitConfirmed})
                        refused=true;emit("LONG_FORM_REFUSED,input_attempt=$i,accepted=$accepted,utf16=${value.length},old_draft_preserved=true,no_write_attempted=true,record=${h.outstandingState()}");break
                    }
                    aliases.add(main{m.operations.createName});accepted++
                    if(i==1 || i%10==0)emit("LONG_FORM_INPUT,accepted=$accepted,utf16=${value.length},held_old_app_strings=${aliases.size},record=${h.outstandingState()}")
                }
                check(accepted>0 && (accepted==100 || refused))
                emit("LONG_FORM_DONE,accepted=$accepted,refused=$refused,held_original_strings=${aliases.size},old_snapshot_held=true,no_write_attempted=true")
            }else if(scenario=="form-repeat"){
                val expected=held.tasks!!.tasks.first()
                main{m.activeArea(ActiveArea.TASKS);m.tasks.open(expected);m.tasks.edit()}
                await("productive title field"){main{m.tasks.form} && visible(expected.title)}
                val aliases=ArrayList<TaskInput>()
                for(i in 1..32){
                    val value="Synthetic UI title $i"
                    await("IME input $i"){editVisibleText(value)}
                    await("app draft $i"){main{m.tasks.input.title==value}}
                    aliases.add(main{m.tasks.input})
                }
                val first=main{a.p110Composed}
for(i in 1..1000){
                    if(i%10==1 && status=="completed")main{h.runtime.session.userInteraction()} // Synthetic session input only: no UI revision; production recomposition never renews idle.
                    if(i%100==1 && status!="completed"){
                        val at=SystemClock.uptimeMillis()
                        for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                            val event=android.view.MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,10f,200f,0)
                            try{sendPointerSync(event)}finally{event.recycle()}
                        }
                    }
                    main{a.p110Invalidate()}
                    await("form composition $i"){main{a.p110Composed>=first+i}}
                    if(!main{m.tasks.input===aliases.last() && m.tasks.form}){
                        emit("FORM_REPEAT_DIVERGENCE,i=$i,same_identity=${main{m.tasks.input===aliases.last()}},same_value=${main{m.tasks.input==aliases.last()}},same_title=${main{m.tasks.input.title==aliases.last().title}},form=${main{m.tasks.form}},busy=${main{m.tasks.busy}},session=${h.runtime.session.snapshot().state},remaining_absolute_ms=${h.runtime.session.remainingAbsoluteMillis()},workspace_error=${main{m.workspaceError}},record=${h.outstandingState()}")
                        error("Form draft identity/form changed at composition $i")
                    }
                    if(i==100 || i==1000)emit("FORM_REPEAT,compositions=$i,held_drafts=${aliases.size},same_current_draft=true,record=${h.outstandingState()}")
                }
                await("save through productive UI"){reveal("Speichern") && clickLabel("Speichern")}
                await("UI commit and original reprojection"){main{!m.tasks.busy && !m.tasks.form && m.workspace?.tasks?.tasks?.any{it.id==expected.id && it.title==aliases.last().title && it.editRevision==expected.editRevision+1}==true}}
                await("UI committed title visible"){reveal(aliases.last().title)}
                await("UI revision visible"){reveal("Revision: ${expected.editRevision+1}")}
                check(aliases.first().title=="Synthetic UI title 1")
                emit("FORM_COMMITTED,new_value_and_revision_visible=true,old_expected_and_32_drafts_held=true,record=${h.outstandingState()}")
            }else if(scenario in controlBoundaries){
                val task=held.tasks!!.tasks.first()
                val action=main{m.taskRow(task)!!.click}
                val input=FormInput{} // Test driver only; the product uses its admitted field scope.
                val required=ActiveWorkspaceBudget.estimateUnits(InputControlCost.click).second
                val state=h.outstandingState()
                fun count(name:String)=Regex("\\b"+name+"=([0-9]+)").find(state)!!.groupValues[1].toLong()
                val retained=scenario.startsWith("control-r-")
                val over=if(scenario.endsWith("over"))1L else 0L
                val limit=if(retained)ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES else ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
                val prior=count(if(retained)"localRetained" else "localTransition")
                val padding=limit-prior-required+over
                check(main{h.p110PadUiBoundary(held.access!!,if(retained)padding else 0,if(retained)0 else padding)})
                var invoked=0
                val callback=main{admitInputClick(action,input){invoked++}}
                check((callback!=null)==(over==0L));check(invoked==0)
                emit("CONTROL_BOUNDARY,required=$required,over=$over,constructed=${callback!=null},old_snapshot_held=true,record=${h.outstandingState()}")
            }else if(scenario=="action-revoke"){
                val row=main{m.taskRow(held.tasks!!.tasks.first())!!}
                main{m.back();row.click();row.click()}
                check(main{m.selected==null && m.workspace==null && m.tasks.selected==null})
                emit("ACTION_REVOKE,old_snapshot_and_callback_held=true,duplicate_late_callback_no_use=true")
            }else if(scenario in uiBoundaries){
                val oldRow=main{held.tasks?.tasks?.firstOrNull()?.let{m.taskRow(it)}}
                val candidate=held.copy()
                val previous=held.uiRows!!
                val formats=UiDateFormats(previous.zone) // Test-only sizing metadata; product constructs after planning admission.
                val plan=ActiveWorkspaceBudget.estimateUnits(WorkspaceUiRows.planningCost(candidate,previous)).second
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
                    check(main{oldRow==null || !oldRow.click.live()})
                    await("admitted presentation visible"){visible(main{m.selected!!.title})}
                }else{
                    check(main{m.workspace===held && candidate.uiRows==null && m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED})
                }
                emit("UI_BOUNDARY,stage=original_VM_UI_representation,over=$over,planning=$plan,display=$display,constructed=${candidate.uiRows!=null},full_graphs=1,full_consumer_claim=false,record=${h.outstandingState()}")
            }else if(scenario in followCases){
                val title="Synthetic P110 follow value"
                if(scenario=="state-edit"){
                    val old=held.operations!!.persons.first()
                    main{m.activeArea(ActiveArea.PERSONS);m.operations.update(old.person.id);m.operations.status(OperationalStatus.IDENTITY_CHECK);m.operations.place(title);m.operations.saveLive()}
                    await("state committed and bound"){main{
                        if(m.operations.error!=null)error("state follow=${m.operations.error},record=${h.outstandingState()}")
                        !m.operations.busy && m.workspace?.operations?.persons?.any{it.person.id==old.person.id && it.state?.currentPlaceLabel==title}==true
                    }}
                    main{m.operations.focus(old.person.id)}
                    emit("STATE_BIND,${main{"screen=${m.operations.screen},area=${m.activeArea},workspaceError=${m.workspaceError},refusal=${m.workspaceRefusal},error=${m.operations.error}"}}")
                    await("state value visible"){reveal(title)}
                    emit("FOLLOW,state_committed=true,new_state_visible=true,old_snapshot_held=true,record=${h.outstandingState()}")
                }else if(scenario=="protocol-create"){
                    main{m.activeArea(ActiveArea.PROTOCOL)}
                    await("original timeline"){main{check(!m.workspaceError){"timeline refusal=${m.workspaceRefusal},record=${h.outstandingState()}"};!m.protocol.loading && m.protocol.timeline!=null}}
                    main{m.protocol.create();m.protocol.title(title);m.protocol.save()}
                    await("committed protocol follow"){main{
                        if(m.protocol.error!=null)error("protocol follow=${m.protocol.error},commit=${m.protocol.commitConfirmed},record=${h.outstandingState()}")
                        !m.protocol.busy && !m.protocol.detailLoading && !m.protocol.form && m.protocol.detail?.current?.revision?.input?.title==title
                    }}
                    check(main{m.workspace!!.protocolTimeline!!.any{it.title==title}})
                    await("protocol value visible"){reveal(title)}
                    emit("FOLLOW,protocol_committed=true,new_detail_and_membership_visible=true,old_snapshot_held=true,record=${h.outstandingState()}")
                }else{
                    main{m.activeArea(ActiveArea.MEMORY_REPORTS)}
                    await("original headers"){main{check(!m.workspaceError){"headers refusal=${m.workspaceRefusal},record=${h.outstandingState()}"};!m.memoryReports.loading && m.memoryReports.headers!=null}}
                    main{m.memoryReports.create();m.memoryReports.changeAuthor(title);m.memoryReports.save()}
                    await("committed memory follow"){main{
                        if(m.memoryReports.error!=null)error("memory follow=${m.memoryReports.error},commit=${m.memoryReports.commitConfirmed},record=${h.outstandingState()}")
                        !m.memoryReports.busy && !m.memoryReports.loading && m.memoryReports.expected?.authorLabel==title
                    }}
                    check(main{m.workspace!!.memoryHeaders!!.any{it.authorLabel==title}})
                    await("memory value visible"){reveal(title)}
                    emit("FOLLOW,memory_committed=true,new_revision_and_membership_visible=true,old_snapshot_held=true,record=${h.outstandingState()}")
                }
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
                if(status=="completed" && scenario!="repeat-completed-overview"){
                    main{m.personDetail(personId)}
                    await("completed original person detail"){main{m.selectedPerson?.id==personId && !m.personDetailLoading} && reveal(held.persons.first().pseudonym)}
                    emit("COMPLETED_SCOPE,read_only_person_detail=true,fixture_unchanged=true,old_snapshot_and_32768_aliases_held=true")
                }
                if(scenario=="repeat-completed-overview"){
                    emit("OVERVIEW_VIEWPORT,person_visible_before_reveal=${visible(held.persons.first().pseudonym)},same_original_snapshot=${main{m.workspace===held}}")
                    await("completed overview initial person visible"){reveal(held.persons.first().pseudonym)}
                    emit("OVERVIEW_VIEWPORT,person_visible_after_reveal=true,normal_scroll_only=true")
                }
                val firstPulse=main{a.p110Composed}
                val composeBegin=SystemClock.elapsedRealtimeNanos()
                val mainCpuBegin=main{SystemClock.currentThreadTimeMillis()}
                if(status=="completed"){
                    val rendered=java.util.concurrent.atomic.AtomicInteger()
                    val failure=AtomicReference<Throwable?>()
                    val finished=java.util.concurrent.CountDownLatch(1)
                    main{
                        val frames=android.view.Choreographer.getInstance()
                        var requested=firstPulse;var pending=false
                        val driver=object:android.view.Choreographer.FrameCallback{
                            override fun doFrame(time:Long){
                                try{
                                    if(pending && a.p110Composed>=requested){
                                        check(!m.workspaceError && m.workspace===held && m.personRow(personId)===row && m.snapshot.state==SessionState.READY){"completed composition=${rendered.get()},workspace_same=${m.workspace===held},session=${m.snapshot.state},refusal=${m.workspaceRefusal}"}
                                        val count=rendered.incrementAndGet();pending=false
                                        if((p111!=null || scenario=="repeat-completed-overview") && count%100==0)emit("COMPOSE_PROGRESS,actual=$count,elapsed_ns=${SystemClock.elapsedRealtimeNanos()-composeBegin},main_cpu_ms=${SystemClock.currentThreadTimeMillis()-mainCpuBegin},gc=${android.os.Debug.getRuntimeStat("art.gc.gc-count")},gc_time_ms=${android.os.Debug.getRuntimeStat("art.gc.gc-time")},blocking_gc_time_ms=${android.os.Debug.getRuntimeStat("art.gc.blocking-gc-time")},overview=${scenario=="repeat-completed-overview"}")
                                        if(count==100 || count==1000)emit("REPEAT,frames=$count,rows=${a.p110RowRenders-before},same_list=true,same_row=true,record=${h.outstandingState()}")
                                        if(count==1000){finished.countDown();return}
                                    }
                                    if(!pending){
                                        if(rendered.get()%10==0)h.runtime.session.userInteraction() // Explicit synthetic input; no UI revision.
                                        a.p110Invalidate();requested++;pending=true
                                    }
                                    frames.postFrameCallback(this)
                                }catch(t:Throwable){failure.set(t);finished.countDown()}
                            }
                        }
                        frames.postFrameCallback(driver)
                    }
                    await("completed 100 actual compositions"){failure.get()!=null || rendered.get()>=100}
                    failure.get()?.let{throw it}
                    check(visible(held.persons.first().pseudonym)){"Completed person disappeared at 100"}
                    try{await("completed 1000 actual compositions"){finished.count==0L}}
                    catch(t:Throwable){emit("COMPOSE_TIMEOUT,actual=${rendered.get()},elapsed_ns=${SystemClock.elapsedRealtimeNanos()-composeBegin},session=${h.runtime.session.snapshot().state},remaining_absolute_ms=${h.runtime.session.remainingAbsoluteMillis()},gc=${android.os.Debug.getRuntimeStat("art.gc.gc-count")},gc_time_ms=${android.os.Debug.getRuntimeStat("art.gc.gc-time")},blocking_gc_time_ms=${android.os.Debug.getRuntimeStat("art.gc.blocking-gc-time")}");throw t}
                    failure.get()?.let{throw it}
                    check(rendered.get()==1000 && visible(held.persons.first().pseudonym))
                }else for(i in 1..1000){
                    if(i%10==1 && status=="completed")main{h.runtime.session.userInteraction()} // Synthetic session input only: no UI revision; production recomposition never renews idle.
                    if(i%100==1 && status!="completed"){
                        val at=SystemClock.uptimeMillis()
                        for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                            val event=android.view.MotionEvent.obtain(at,SystemClock.uptimeMillis(),action,10f,200f,0)
                            try{sendPointerSync(event)}finally{event.recycle()}
                        }
                    }
                    main{a.p110Invalidate()}
                    await("frame $i"){main{
                        if(a.p110Composed<firstPulse+i)false else {
                            check(!m.workspaceError && m.workspace===held && (if(task!=null)m.taskRow(task) else m.personRow(personId))===row && m.snapshot.state==SessionState.READY){"iteration=$i,workspace_same=${m.workspace===held},selected=${m.selected?.id},session=${m.snapshot.state},workspaceError=${m.workspaceError},refusal=${m.workspaceRefusal}"}
                            true
                        }
                    }}
                    if(status=="completed" && (i==100 || i==1000))check(visible(held.persons.first().pseudonym)){"Completed person disappeared at $i"}
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
