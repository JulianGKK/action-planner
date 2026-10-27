package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject
import org.pag.data.product.ProductRuntime
import org.pag.data.product.SessionDbKeyOwner
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Normal ProductRuntime/DeviceCredential; manual PIN only, no credential UI inspection.
 * The positive F04 assertion must stay red until continuation actually works. */
class P117CredentialContinuationProbe:Instrumentation(){
 private var verifyFreshWriter=false
 private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
 private fun <T> main(work:()->T):T{val r=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{r.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return r.get()}
 private fun await(label:String,work:()->Boolean){val end=SystemClock.elapsedRealtime()+90_000;while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(50)};error("P117 timeout $label")}
 private fun <T> receive(label:String,work:((T)->Unit)->Unit):T{val r=AtomicReference<T>();main{work{r.set(it)}};await(label){r.get()!=null};return r.get()}
 private fun emit(kind:String,d:JSONObject=JSONObject())=sendStatus(0,Bundle().apply{putString("stream","P117 "+d.put("kind",kind)+"\n")})
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);verifyFreshWriter=arguments?.getString("p118VerifyWriter")=="true";start()}
 override fun onStart(){var activity:P115CredentialActivity?=null
  try{
   check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
   val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P115CredentialActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P115CredentialActivity;activity=a
   val runtime=field(a,"runtime") as ProductRuntime;val model=field(a,"model") as ProductViewModel
   check(runtime.authentication.javaClass.name=="org.pag.security.ProductCredentialAuthentication")
   emit("REAL_ROOT",JSONObject().put("authentication",runtime.authentication.javaClass.name).put("inventory",runtime.inventory.state().name))
   fun auth():Long{
    await("focus"){main{a.hasWindowFocus()}}
    emit("MANUAL_CREDENTIAL_NOW");main{model.unlock()}
    await("real READY"){main{runtime.session.snapshot().state==SessionState.READY}}
    val generation=main{runtime.session.snapshot().generation};emit("REAL_READY",JSONObject().put("generation",generation));return generation
   }
   val generation=auth()
   val draft=receive<Action>("seed action"){runtime.actions.createAction(NewAction("Synthetic P117","Synthetic continuation fixture",0,1000),it)}
   check(receive<ActionChangeResult>("seed preparation"){runtime.actions.advanceActionStatus(draft.id,ActionStatus.DRAFT,it)} is ActionChangeResult.Applied)
   check(receive<OperationsResult<Task>>("seed task"){runtime.tasks.create(draft.id,TaskInput("Synthetic P117 task",TaskPriority.NORMAL),it)} is OperationsResult.Value)
   check(receive<ActionChangeResult>("seed active"){runtime.actions.advanceActionStatus(draft.id,ActionStatus.PREPARATION,it)} is ActionChangeResult.Applied)
   main{model.detail(draft.id)};await("first HUB"){main{model.compactHubPresentation!=null && !model.personsLoading}}
   val hub=main{model.compactHubPresentation!!};val header=main{hub.navigation(ActiveArea.TASKS)!!}
   main{model.activeArea(ActiveArea.TASKS)}
   var row:OriginalButtonControl?=null;await("row"){main{row=model.originalRowControl(0);row!=null}}
   main{row!!.click()};await("detail"){main{model.tasks.selected!=null && !model.detailLoading}}
   main{model.tasks.edit();model.tasks.changeTitle("Synthetic P117 unsaved draft")}
   val form=main{model.inputScreenKey() as OriginalUiFence};var writes=0
   val writer=main{check(form.reserve(InputControlCost.click));OriginalInputClick(form,FormInput{},model.uiPresentationRevision){writes++;model.tasks.save()}}
   val old=main{runtime.session.acquire(AccessScope.READY)!!}
   val oldOwner=main{(field(runtime,"owner") as AtomicReference<*>).get() as SessionDbKeyOwner}
   val before=field(runtime.activeCore,"outstanding").let{it.javaClass.getDeclaredMethod("state").invoke(it).toString()}
   main{model.lock()};check(!main{old.check(AccessPoint.PUBLICATION)})
   val next=auth();check(next>generation)
   val fresh=main{runtime.session.acquire(AccessScope.READY)!!};check(main{fresh.check(AccessPoint.PUBLICATION)})
   main{header();row!!.click();writer();check(writes==0 && !model.tasks.busy && !form.live());check(hub.value(0,0)==null)}
   var consumed=false
   check(runCatching{main{oldOwner.withDbKey(old){consumed=true}}}.isFailure && !consumed)
   check(runCatching{main{oldOwner.withDbKey(fresh){consumed=true}}}.isFailure && !consumed)
   check(!main{old.publish{error("old publication")}})
   emit("NEGATIVE_SAFETY_PASS",JSONObject().put("old_generation",generation).put("new_generation",next).put("writer_calls",writes).put("old_key_consumer_calls",0).put("real_device_credential",true))
   main{model.detail(draft.id)};await("explicit reopen settled"){main{!model.detailLoading && !model.personsLoading && !model.workspaceRefreshing}}
   val after=field(runtime.activeCore,"outstanding").let{it.javaClass.getDeclaredMethod("state").invoke(it).toString()}
   val usable=main{!model.workspaceError && model.compactHubPresentation!=null}
   emit("CONTINUATION_RESULT",JSONObject().put("usable",usable).put("before",before).put("after",after).put("real_device_credential",true))
   check(usable){"F04: fresh real READY cannot reopen original action"}
   if(verifyFreshWriter){
    main{model.activeArea(ActiveArea.TASKS)}
    var freshRow:OriginalButtonControl?=null;await("fresh row"){main{freshRow=model.originalRowControl(0);freshRow!=null}}
    main{freshRow!!.click()};await("fresh detail"){main{model.tasks.selected!=null && !model.detailLoading}}
    val previous=main{model.tasks.selected!!}
    main{model.tasks.edit();model.tasks.changeTitle("Synthetic P118 fresh writer");model.tasks.save()}
    await("fresh COMMITTED revision"){main{!model.tasks.busy && !model.tasks.form && model.tasks.selected?.editRevision==previous.editRevision+1}}
    check(main{model.tasks.selected?.title=="Synthetic P118 fresh writer"})
    emit("FRESH_WRITER_PASS",JSONObject().put("revision",main{model.tasks.selected!!.editRevision}).put("real_device_credential",true))
   }
   main{model.lock();a.finish()};activity=null
   finish(Activity.RESULT_OK,Bundle().apply{putString("result","P117 CONTINUATION PASS")})
  }catch(t:Throwable){emit("FAIL",JSONObject().put("error",t.stackTraceToString()).put("lifecycle",activity?.p115Events?.joinToString("|")));runCatching{main{activity?.finish()}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P117 CONTINUATION FAIL")})}
 }
}
