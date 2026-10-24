package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.ProductViewModel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Original productive repositories, confirmed transactions and SQLCipher. No production
 * schema change; ABA acceptance is an intentional NEGATIVE evidence result. P77 auth synthetic. */
class P103EditorCasProbe:Instrumentation() {
    private var scenario="cas_aba"
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private val action="p11-preparation"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}}
        e.get()?.let{throw it};return v.get()
    }
    private fun waitFor(label:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(40)}
        error("P103 timeout $label")
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream","P103,$scenario,$s\n")})
    private fun <T> call(work:((T)->Unit)->Unit):T {
        val v=AtomicReference<T>();val n=AtomicInteger()
        main{h.runtime.session.userInteraction();work{v.set(it);n.incrementAndGet()}}
        waitFor("one original result"){n.get()>0};check(n.get()==1);return v.get()
    }
    private fun <T> value(result:OperationsResult<T>)=(result as OperationsResult.Value).value
    private fun person(id:String?=null):Person=if(id==null)
        (call<PersonResult<List<Person>>>{h.runtime.persons.listPersons(action,it)} as PersonResult.Value).value.first()
        else (call<PersonResult<Person?>>{h.runtime.persons.getPerson(action,id,it)} as PersonResult.Value).value!!
    private fun rename(p:Person,name:String)=call<PersonResult<Person>>{h.runtime.persons.updatePerson(action,p,name,it)}
    private fun board()=value(call<OperationsResult<OperationalBoard>>{h.runtime.operations.board(action,it)})
    private fun events(task:Task):Long {
        val page=(call<CompactTaskResult>{h.runtime.tasks.historyPage(task,1,it)} as CompactTaskResult.Value).payload as CompactTaskEvents
        check(page.taskId==task.id && page.events.size<=32 && page.events.size.toLong()==page.total && page.next==null)
        return page.total
    }
    private fun unlock(){main{if(h.runtime.session.snapshot().state!=SessionState.LOCKED)model.lock();model.unlock()}
        waitFor("fresh READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}}
    private fun node(text:String?=null,click:Boolean=false,set:String?=null,editable:String?=null):Boolean {
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:AccessibilityNodeInfo):Boolean {
            if(!n.refresh())return false
            if(n.isVisibleToUser && ((text!=null && n.text?.toString()==text) || (set!=null && n.isEditable) || (editable!=null && n.isEditable && n.text?.toString()==editable))) {
                if(set!=null)return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,set)})
                if(!click)return true
                var target:AccessibilityNodeInfo?=AccessibilityNodeInfo.obtain(n)
                try{repeat(5){val cur=target?:return false
                    if(cur.isClickable)return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    val parent=cur.parent;cur.recycle();target=parent}}
                finally{target?.recycle()}
                return false
            }
            for(i in 0 until n.childCount){val child=n.getChild(i)?:continue
                try{if(visit(child))return true}finally{child.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    private fun sweep(predicate:()->Boolean) {
        repeat(10) {
            if(predicate())return
            val m=targetContext.resources.displayMetrics;val start=SystemClock.uptimeMillis()
            for(step in 0..6){val e=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),if(step==0)0 else if(step==6)1 else 2,m.widthPixels/2f,m.heightPixels*(0.75f-step*0.045f),0)
                e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try{check(uiAutomation.injectInputEvent(e,true))}finally{e.recycle()};Thread.sleep(60)}
            Thread.sleep(150)
        }
        error("P103 target not visible in bounded scroll")
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario
        check(scenario in setOf("cas_aba","person_ui","publication"));start()}
    override fun onStart() {
        var activity:Activity?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture","p86-small").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a
            h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            waitFor("focus"){main{a.hasWindowFocus()}};unlock()
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            when(scenario) {
                "cas_aba"->{
                    val p=person();val secondEditor=person(p.id);check(secondEditor==p)
                    val b=(rename(p,"Synthetic editor B") as PersonResult.Value).value
                    check(rename(secondEditor,"Synthetic editor C")==PersonResult.Rejected(PersonRejection.STALE))
                    val restored=(rename(b,p.pseudonym) as PersonResult.Value).value;check(restored==p)
                    check(rename(secondEditor,"Synthetic old A after ABA") is PersonResult.Value)
                    emit("PERSON,two_open_editors_second_STALE=true,all_A_fields_restored=true,old_A_after_ABA=COMMITTED,BLOCKER=true")
                    val ops=h.runtime.operations
                    value(call<OperationsResult<Role>>{ops.createRole(action,"Synthetic editor role zero",it)})
                    value(call<OperationsResult<Role>>{ops.createRole(action,"Synthetic editor role one",it)})
                    val r0=board().roles.first()
                    val rb=value(call<OperationsResult<Role>>{ops.renameRole(action,r0,"Synthetic role B",it)})
                    check(call<OperationsResult<Role>>{ops.renameRole(action,r0,"Synthetic role C",it)}==OperationsResult.Rejected(OperationsFailure.STALE))
                    val ra=value(call<OperationsResult<Role>>{ops.renameRole(action,rb,r0.name,it)});check(ra==r0)
                    check(call<OperationsResult<Role>>{ops.renameRole(action,r0,"Synthetic role old A",it)} is OperationsResult.Value)
                    val ob=board();val op=ob.persons.first();val planA=op.plan
                    val role=if(planA.roleIds==setOf(ob.roles.first().id))ob.roles[1] else ob.roles.first();val planB=PersonRolePlan(setOf(role.id),role.id)
                    fun plan(expected:PersonRolePlan,next:PersonRolePlan)=call<OperationsResult<Unit>>{ops.planRoles(action,op.person.id,ExpectedPersonRolePlan(action,op.person.id,expected),next,it)}
                    check(plan(planA,planB) is OperationsResult.Value);check(plan(planA,planA)==OperationsResult.Rejected(OperationsFailure.STALE))
                    check(plan(planB,planA) is OperationsResult.Value);check(plan(planA,planB) is OperationsResult.Value)
                    emit("OPERATIONS,role_and_plan_changed_value_STALE=true,role_and_plan_ABA_accepted=true,BLOCKER=true")
                    val tr=h.runtime.tasks
                    val t=value(call<OperationsResult<Task>>{tr.create(action,TaskInput("Synthetic concurrent task"),it)})
                    val edited=value(call<OperationsResult<Task>>{tr.edit(action,t,TaskInput("Synthetic changed task"),it)})
                    val count=events(edited)
                    check(call<OperationsResult<Task>>{tr.transition(action,t,TaskStatus.IN_PROGRESS,it)}==OperationsResult.Rejected(OperationsFailure.STALE))
                    check(events(edited)==count)
                    val started=value(call<OperationsResult<Task>>{tr.transition(action,edited,TaskStatus.IN_PROGRESS,it)})
                    check(call<OperationsResult<Task>>{tr.transition(action,edited,TaskStatus.IN_PROGRESS,it)}==OperationsResult.Rejected(OperationsFailure.STALE))
                    check(events(started)==count+1)
                    emit("TASK,stale_transition_no_event=true,successful_transition_exactly_one=true,duplicate_old_transition_STALE=true")
                    val active="p11-active";val live=value(call<OperationsResult<OperationalBoard>>{ops.board(active,it)}).persons.first()
                    val old=checkNotNull(live.state)
                    val next=if(old.status==OperationalStatus.UNCLEAR)OperationalStatus.ON_SITE else OperationalStatus.UNCLEAR
                    check(call<OperationsResult<Unit>>{ops.updateSnapshot(active,live.person.id,old,next,null,"Synthetic place",it)} is OperationsResult.Value)
                    check(call<OperationsResult<Unit>>{ops.updateSnapshot(active,live.person.id,old,OperationalStatus.LEFT,null,"",it)}==OperationsResult.Rejected(OperationsFailure.STALE))
                    emit("CURRENT_STATE,changed_full_expected_STALE=true,timestamp_monotonicity_NOT_PROVEN=true")
                }
                "person_ui"->{
                    val p=person();main{model.detail(action)};waitFor("workspace"){main{model.workspace!=null && !model.personsLoading}}
                    main{model.personDetail(p.id)};waitFor("person detail"){main{model.selectedPerson?.id==p.id}}
                    main{model.editPerson()};Thread.sleep(400);sweep{node(set="Synthetic unsaved draft")}
                    waitFor("typed draft"){node(editable="Synthetic unsaved draft")}
                    check(rename(p,"Synthetic other editor committed") is PersonResult.Value)
                    main{model.operations.refresh()};waitFor("background refresh"){main{model.workspace?.persons?.any{it.id==p.id && it.pseudonym=="Synthetic other editor committed"}==true}}
                    check(node(editable="Synthetic unsaved draft"));sweep{node("Speichern",true)}
                    waitFor("visible STALE"){main{model.personError==PersonRejection.STALE && !model.personSaving}}
                    check(node(editable="Synthetic unsaved draft") && main{model.feedback==null})
                    sweep{node("Aktuellen Stand neu laden",true)}
                    waitFor("explicit draft replacement"){node(editable="Synthetic other editor committed")}
                    main{model.lock()};unlock();check(main{!model.personForm && model.editingPerson==null && model.selectedPerson==null})
                    emit("UI,real_editable_draft_survives_refresh_and_STALE=true,no_saved=true,explicit_reload_replaces=true,lock_reauth_does_not_restore_editor=true")
                }
                "publication"->{
                    val p=person();val oldCalls=AtomicInteger();main{h.faults.main.holdNext();h.runtime.persons.updatePerson(action,p,"Synthetic durable B"){oldCalls.incrementAndGet()}}
                    waitFor("held original Main"){h.faults.main.isHeld()};val late=h.faults.main.take()
                    val durable=person(p.id);check(durable.pseudonym=="Synthetic durable B")
                    main{model.lock()};unlock();val nextCalls=AtomicInteger()
                    main{h.faults.worker.holdNext();h.runtime.persons.updatePerson(action,durable,"Synthetic durable C"){check(it is PersonResult.Value);nextCalls.incrementAndGet()};late.run();late.run()}
                    check(oldCalls.get()==0 && nextCalls.get()==0);h.faults.worker.release();waitFor("new editor commit"){nextCalls.get()==1}
                    val c=person(p.id);check(c.pseudonym=="Synthetic durable C")
                    main{h.faults.worker.holdNext();h.runtime.persons.updatePerson(action,c,"Must not commit after lock"){oldCalls.incrementAndGet()};model.lock()}
                    h.faults.worker.release();unlock();check(person(p.id)==c && oldCalls.get()==0)
                    check(call<ActionChangeResult>{h.runtime.actions.advanceActionStatus(action,ActionStatus.PREPARATION,it)} is ActionChangeResult.Applied)
                    check(rename(c,"Must not write ACTIVE")==PersonResult.Rejected(PersonRejection.NOT_ALLOWED))
                    emit("PUBLICATION,commit_before_lock_preserved=true,lost_old_Main_and_double_callback_no_success=true,new_write_before_old_patch=true,lock_before_worker_no_write=true,actual_status_permission_change_denies=true")
                }
            }
            main{model.lock();a.finish()};waitFor("fixture cleanup"){h.cleanupConfirmed.get()}
            check(targetContext.noBackupFilesDir.listFiles().orEmpty().none{it.name.startsWith("p11-fixture-")})
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,reuse=false,ram_reuse=false,ABA_safety_NOT_CLAIMED=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P103 PASS $scenario")})
        }catch(t:Throwable){emit("FAILURE,${t.stackTraceToString()}");runCatching{main{activity?.finish()}}
            if(::h.isInitialized)runCatching{waitFor("failure cleanup"){h.cleanupConfirmed.get()}}
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P103 FAIL $scenario")})}
    }
}
