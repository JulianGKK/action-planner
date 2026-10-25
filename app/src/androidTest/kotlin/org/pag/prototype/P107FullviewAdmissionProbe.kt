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
class P107FullviewAdmissionProbe:Instrumentation(){
    private var scenario="exposed"
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(30)};error("P107 timeout $label")}
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream","P107,$scenario,$s\n")})
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
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("exposed","preparation","completed","active","large","r-exact","r-over","t-exact","t-over"));start()}
    override fun onStart(){var activity:Activity?=null;var replacement:ProductRuntime?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture",if(scenario=="large")"p86-stress32" else "p86-small").putExtra("p107Budget",scenario.takeIf{it.startsWith("r-") || it.startsWith("t-")}).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a
            h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            await("focus"){main{a.hasWindowFocus()}};main{model.unlock()};await("READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
            if(scenario in setOf("r-exact","r-over","t-exact","t-over")){
                // Data admission and a later UI representation are DISTINCT producers. An
                // exact-full data ledger has no headroom for the new §110 UI rows. Exercise
                // the real Room/SQLCipher admission directly; never claim a full UI PASS.
                val request=WorkspaceReadRequest("p11-active",ActionStatus.ACTIVE,main{model.snapshot.generation},setOf(WorkspacePart.TASKS),ticket=WorkspaceReadTicket())
                val result=call<WorkspaceResult>{h.runtime.workspace.read(request,it)}
                if(scenario.endsWith("over")){
                    check(result==WorkspaceResult.Rejected(WorkspaceFailure.BUDGET_REFUSED))
                    check(h.observations.workspaceMaterializations.get()==0)
                    emit("DATA_BOUNDARY,+1_byte=REFUSED,before_DTO=true,full_UI_claim=false,record=${h.outstandingState()}")
                }else{
                    check(result is WorkspaceResult.Value)
                    check(result.value.operations.persons.size==20 && result.value.tasks!!.tasks.size==40)
                    check(h.observations.workspaceMaterializations.get()==1)
                    val state=h.outstandingState()
                    if(scenario=="r-exact")check(state.contains("localRetained=${ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES},"))
                    else check(state.contains("localTransition=${ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES},"))
                    var constructed=0
                    main{if(result.value.access!!.reserve(ActiveWorkspaceBudget.Units(objects=1)))constructed++}
                    check(constructed==0 && main{model.workspace==null})
                    emit("DATA_BOUNDARY,exact=ADMITTED,held_TEST_alias=true,additional_UI_producer=REFUSED_before_construction,full_UI_claim=false,record=$state")
                }
                main{model.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
                finish(Activity.RESULT_OK,Bundle().apply{putString("result","P107 PASS $scenario")});return
            }
            if(scenario=="exposed"){
                main{model.bindActiveCore(h.runtime.activeCore){a.runOnUiThread(it)};model.detail("p11-active")}
                await("visible core"){visible("20 insgesamt")}
                check(h.observations.workspaceMaterializations.get()==0)
                main{model.useFullActiveView()}
                await("typed refusal"){main{model.workspaceRefusal==WorkspaceFailure.OWNER_UNCONFIRMED && !model.personsLoading}}
                await("visible refusal"){visible("Die Vollansicht kann derzeit nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet.")}
                check(main{model.workspace==null && model.workspaceError})
                val request=WorkspaceReadRequest("p11-active",ActionStatus.ACTIVE,main{model.snapshot.generation},setOf(WorkspacePart.TASKS))
                check(call<WorkspaceResult>{h.runtime.workspace.read(request,it)}==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                check(call<OperationsResult<OperationalBoard>>{h.runtime.operations.board("p11-active",it)}==OperationsResult.Rejected(OperationsFailure.OWNER_UNCONFIRMED))
                check(call<OperationsResult<TaskBoard>>{h.runtime.tasks.board("p11-active",it)}==OperationsResult.Rejected(OperationsFailure.OWNER_UNCONFIRMED))
                check(call<PersonResult<List<Person>>>{h.runtime.persons.listPersons("p11-active",it)}==PersonResult.Rejected(PersonRejection.OWNER_UNCONFIRMED))
                check(h.p106StoreWithoutEntry()==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                check(h.observations.workspaceMaterializations.get()==0 && h.observations.reads.get()==1)
                emit("BEFORE_AFTER,previous_P105_full_boards=20/40,current_fullview_materializations=0,direct_repository=REFUSED,direct_boards=REFUSED,missing_store_entry=REFUSED,visible_refusal=true")
                main{h.runtime.close()}
                val worker=(field(a,"worker\$delegate") as Lazy<*>).value as Executor
                replacement=main{h.p106Replacement(a,worker,a.mainExecutor)}
                val next=replacement!!
                emit("RECONSTRUCTION_INITIAL,original=${h.runtime.session.snapshot().state},replacement=${next.session.snapshot().state},focus=${main{a.hasWindowFocus()}},record=${h.outstandingState()}")
                val observed=next.session.observe{emit("RECONSTRUCTION_STATE,state=${it.state},generation=${it.generation}")}
                main{next.start(false)}
                check(next.session.snapshot().state==SessionState.LOCKED && next.session.snapshot().generation==0L)
                emit("REPRODUCTION,start_without_foreground=LOCKED,authentication_not_started=true,original_record_unchanged=true")
                val drained=java.util.concurrent.CountDownLatch(1)
                worker.execute{drained.countDown()};check(drained.await(10,java.util.concurrent.TimeUnit.SECONDS))
                emit("WORKER,queue_barrier_returned=true,original_runtime_LOCKED=true,old_pending_candidates=0")
                // Correct the omitted real lifecycle input, never force a READY state.
                main{next.session.foreground(a.hasWindowFocus());next.start(false)}
                await("replacement READY"){next.session.snapshot().state==SessionState.READY}
                observed.cancel()
                check(main{!next.activeCore.loadOwned("p11-active"){_,_->error("No readmission")}})
                val r=request.copy(generation=next.session.snapshot().generation)
                check(call<WorkspaceResult>{next.workspace.read(r,it)}==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                emit("RECONSTRUCTION,new_Runtime_and_session_READY=true,original_memory_record_preserved=true,fullview_and_core=REFUSED,no_old_end_claim=true")
                main{next.close()};replacement=null
            }else{
                val id=when(scenario){"preparation"->"p11-preparation";"completed"->"p11-completed";else->"p11-active"}
                val status=when(scenario){"preparation"->ActionStatus.PREPARATION;"completed"->ActionStatus.COMPLETED;else->ActionStatus.ACTIVE}
                if(scenario=="preparation"){
                    val originalGeneration=main{model.snapshot.generation}
                    val before=WorkspaceReadTicket();val refusal=WorkspaceReadTicket();val calls=AtomicInteger()
                    val cancelledRequest=WorkspaceReadRequest(id,status,originalGeneration,emptySet(),ticket=before)
                    main{h.faults.worker.holdNext();h.runtime.workspace.read(cancelledRequest){calls.incrementAndGet()}}
                    check(h.faults.worker.isHeld())
                    main{h.faults.main.holdNext();h.runtime.workspace.read(cancelledRequest.copy(ticket=refusal)){calls.incrementAndGet()}}
                    check(h.faults.main.isHeld());refusal.cancel()
                    main{h.faults.main.release()};main{} // Actual Main queue acknowledgement, not a frame.
                    check(calls.get()==0)
                    before.cancel();h.faults.worker.release()
                    val worker=(field(a,"worker\$delegate") as Lazy<*>).value as Executor
                    val ended=java.util.concurrent.CountDownLatch(1);worker.execute{ended.countDown()}
                    check(ended.await(10,java.util.concurrent.TimeUnit.SECONDS));main{}
                    check(calls.get()==0 && h.observations.workspaceMaterializations.get()==0)
                    emit("CANCEL,held_Worker_before_preflight=ended,held_Refusal_Main=returned,no_callbacks=true,materializations=0,no_record_reset=true")
                }
                main{model.detail(id)}
                await("first fullview or refusal"){main{!model.personsLoading && (model.workspace!=null || model.workspaceRefusal!=null)}}
                if(main{model.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED}){
                    check(scenario=="large" || scenario.endsWith("over"));check(h.observations.workspaceMaterializations.get()==0)
                    await("visible budget refusal"){visible("Die Vollansicht überschreitet das zulässige Speicherbudget.")}
                    emit("PREFLIGHT,BUDGET_REFUSED,materializations=0,no_EMPTY=true,record=${h.outstandingState()}")
                }else{
                    val testSnapshot=main{checkNotNull(model.workspace)} // Deliberate TEST alias.
                    check(testSnapshot.persons.size==(if(scenario=="large")200 else 20))
                    check(h.observations.workspaceMaterializations.get()==1)
                    check(testSnapshot.operations!!.persons.all{it.person.editRevision==0L && it.plan.editRevision==0L})
                    await("visible original action"){visible(main{model.selected!!.title})}
                    emit("PREFLIGHT,ADMITTED,visible=true,persons=${testSnapshot.persons.size},tasks=${testSnapshot.tasks?.tasks?.size},materializations=1,record=${h.outstandingState()}")
                    if(status==ActionStatus.ACTIVE && scenario=="active" && testSnapshot.tasks?.tasks?.isNotEmpty()==true){
                        val t=testSnapshot.tasks!!.tasks.first()
                        main{model.tasks.open(t);model.tasks.edit();model.tasks.change(model.tasks.input.copy(title="Synthetic P107 unsaved draft"))}
                        await("visible draft"){visible("Synthetic P107 unsaved draft")}
                        check(main{model.tasks.form && model.tasks.input.title=="Synthetic P107 unsaved draft"})
                        emit("FORM,task_draft_visible=true,Expected_revision=${t.editRevision},draft_end_NOT_claimed=true")
                    }
                    val r=WorkspaceReadRequest(id,status,main{model.snapshot.generation},setOf(WorkspacePart.TASKS))
                    check(call<WorkspaceResult>{h.runtime.workspace.read(r,it)}==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                    check(call<OperationsResult<TaskBoard>>{h.runtime.tasks.board(id,it)}==OperationsResult.Rejected(OperationsFailure.OWNER_UNCONFIRMED))
                    check(h.observations.workspaceMaterializations.get()==1)
                    check(testSnapshot.persons.size==(if(scenario=="large")200 else 20))
                    emit("OLD_SNAPSHOT,TEST_alias_retained=true,next_workspace_and_direct_board=REFUSED,additional_materializations=0")
                    if(scenario=="preparation"){
                        main{model.personDetail(testSnapshot.persons.first().id)}
                        await("person detail refusal"){main{!model.personDetailLoading}}
                        check(main{model.personError==PersonRejection.OWNER_UNCONFIRMED && model.selectedPerson==null})
                        emit("FUNCTIONAL_BLOCKER,person_detail_requires_another_read=REFUSED,first_overview_NOT_full_PREPARATION_acceptance=true")
                    }
                    main{model.lock()};check(main{model.workspace==null && !model.tasks.form})
                    main{model.unlock()};await("reauth READY"){main{model.snapshot.state==SessionState.READY}}
                    val nextGeneration=main{model.snapshot.generation}
                    check(call<WorkspaceResult>{h.runtime.workspace.read(r.copy(generation=nextGeneration),it)}==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                    val other=if(id=="p11-completed")"p11-active" else "p11-completed"
                    check(call<WorkspaceResult>{h.runtime.workspace.read(WorkspaceReadRequest(other,ActionStatus.COMPLETED,nextGeneration,emptySet()),it)}==WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
                    check(h.observations.workspaceMaterializations.get()==1)
                    emit("LOCK_REAUTH_ACTION,VM_cleared=true,original_TEST_snapshot_still_held=true,new_read=REFUSED,additional_materializations=0")
                }
            }
            if(scenario.startsWith("r-") || scenario.startsWith("t-")){
                val r=h.observations.p107Retained;val t=h.observations.p107Transition
                val target=if(scenario.startsWith("r-"))r else t
                val limit=if(scenario.startsWith("r-"))ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES else ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
                check(target==limit+(if(scenario.endsWith("over"))1 else 0))
                check((h.observations.p107Padding!=null)==scenario.endsWith("exact"))
                emit("BOUNDARY,R=$r,T=$t,disjoint_TEST_scratch_plan=true,actual_extra_array=${h.observations.p107Padding?.size ?: 0},materializations=${h.observations.workspaceMaterializations.get()}")
            }
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            main{model.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,reuse=false,ramReuse=false")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P107 PASS $scenario")})
        }catch(t:Throwable){emit("FAILURE,"+t.stackTraceToString());runCatching{main{replacement?.close();activity?.finish()};if(::h.isInitialized)await("failure fixture cleanup"){h.cleanupConfirmed.get()}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P107 FAIL $scenario")})}
    }
}
