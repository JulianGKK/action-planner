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

/** Real product overlap gate, original SQLCipher/Room/session; P77 auth is synthetic.
 * Clean legacy fullviews are regression evidence, NOT proof of a fresh budget receipt. */
class P106FullviewEntryProbe:Instrumentation(){
    private var scenario="exposed"
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(30)};error("P106 timeout $label")}
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream","P106,$scenario,$s\n")})
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
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("exposed","clean"));start()}
    override fun onStart(){var activity:Activity?=null;var replacement:ProductRuntime?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture","p86-small").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a
            h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            await("focus"){main{a.hasWindowFocus()}};main{model.unlock()};await("READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
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
                for((id,status) in listOf("p11-preparation" to ActionStatus.PREPARATION,"p11-completed" to ActionStatus.COMPLETED)){
                    main{model.detail(id)};await("legacy fullview $status"){main{model.workspace?.actionId==id && !model.personsLoading}}
                    check(main{model.workspace!!.persons.size==20 && model.workspace!!.operations!!.persons.all{it.person.editRevision==0L && it.plan.editRevision==0L}})
                    emit("LEGACY,$status,persons=20,revisions=0,accessible=true,budget_ADMITTED_NOT_CLAIMED=true")
                    main{model.back()}
                }
                check(h.observations.workspaceMaterializations.get()==2)
                emit("LIMIT,first_fullview_R8_T16_receipt=STILL_MISSING,unapproved_blanket_disable=NOT_IMPLEMENTED,product_decision=REQUIRED")
            }
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            main{model.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,reuse=false,ramReuse=false")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P106 PASS $scenario")})
        }catch(t:Throwable){emit("FAILURE,"+t.stackTraceToString());runCatching{main{replacement?.close();activity?.finish()};if(::h.isInitialized)await("failure fixture cleanup"){h.cleanupConfirmed.get()}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P106 FAIL $scenario")})}
    }
}
