package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Integration negative only: synthetic P77 auth, actual original owner and mutation port.
 * UNKNOWN is explicit protocol fault injection; native DB outcome/recovery evidence is reused. */
class P115AuthorityProbe:Instrumentation(){
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val r=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{r.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return r.get()}
    private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+45_000;while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(30)};error("P115 authority timeout $label")}
    private fun emit(kind:String,data:JSONObject=JSONObject())=sendStatus(0,Bundle().apply{putString("stream","P115_AUTHORITY "+data.put("kind",kind)+"\n")})
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){var a:P86FullWorkspaceActivity?=null;var h:P77ProductRuntimeHarness?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val activity=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture","p111-large32").putExtra("p112Probe",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P86FullWorkspaceActivity;a=activity
            val harness=field(activity,"p77Harness") as P77ProductRuntimeHarness;h=harness
            val model=field(activity,"model") as ProductViewModel
            await("focus"){main{activity.hasWindowFocus()}};main{model.unlock()}
            await("READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
            main{model.bindActiveCore(harness.runtime.activeCore){activity.runOnUiThread(it)};model.detail("p11-active")}
            await("HUB"){main{model.compactHubPresentation!=null && !model.personsLoading}}
            val hub=main{model.compactHubPresentation!!};val header=main{hub.navigation(ActiveArea.TASKS)!!}
            main{model.activeArea(ActiveArea.TASKS)}
            var row:OriginalButtonControl?=null;await("row"){main{row=model.originalRowControl(0);row!=null}}
            val access=main{field(model,"pageAccess") as WorkspaceAccess};val generation=main{model.snapshot.generation}
            val pending=main{harness.mutations.beforeTaskQueue("p11-active",generation)!!}
            var plaintext=0
            fun denied(){main{
                val area=model.activeArea;header();row!!.click()
                check(model.activeArea==area && !model.detailLoading)
                check(hub.value(0,0)==null)
                check(!access.publish("p11-active",ActionStatus.ACTIVE,generation){plaintext++})
                check(plaintext==0)
            }}
            denied();emit("PENDING_WRITER_DENIED")
            check(main{pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE))})
            denied();check(main{harness.mutations.beforeActiveRead("p11-active",generation)==null})
            emit("UNKNOWN_DENIED",JSONObject().put("injected_protocol_outcome",true).put("sql_write_performed",false))
            main{model.detail("p11-preparation")}
            await("action request settled"){main{!model.detailLoading}}
            val selected=main{model.selected?.id};main{header();row!!.click()};check(main{model.selected?.id==selected})
            check(harness.observations.reads.get()==1 && harness.observations.workspaceMaterializations.get()==0)
            check(!harness.runtime.activeCore.reuseEnabled && !harness.mutations.ramReuseEnabled)
            emit("ACTION_OLD_CAPTURES_DENIED",JSONObject().put("core_reads",1).put("full_workspaces",0).put("plaintext_callbacks",plaintext))
            main{model.lock();activity.finish()};await("cleanup"){harness.cleanupConfirmed.get()};a=null
            emit("PASS",JSONObject().put("synthetic_authentication",true).put("fixture_cleanup",true))
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P115 AUTHORITY PASS")})
        }catch(t:Throwable){emit("FAIL",JSONObject().put("error",t.stackTraceToString()));runCatching{main{a?.finish()};h?.let{hh->await("failure cleanup"){hh.cleanupConfirmed.get()}}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P115 AUTHORITY FAIL")})}
    }
}
