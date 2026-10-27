package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject
import org.pag.data.product.ProductRuntime
import org.pag.domain.*
import org.pag.prototype.product.ProductViewModel
import java.util.concurrent.atomic.AtomicReference

/** Manual credentials only. Never reads, injects, records or automates the system credential UI. */
class P115CredentialProbe:Instrumentation(){
    private var phase="idle"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {val r=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{r.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return r.get()}
    private fun emit(kind:String,data:JSONObject=JSONObject())=sendStatus(0,Bundle().apply{putString("stream","P115 "+data.put("kind",kind).put("phase",phase).put("elapsed_ms",SystemClock.elapsedRealtime())+"\n")})
    private fun await(label:String,ms:Long=90_000,work:()->Boolean){val end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(50)};error("P115 timeout $label; manual credential required, not a PASS")}
    private fun <T> receive(label:String,start:((T)->Unit)->Unit):T {
        val value=AtomicReference<T>();main{start{value.set(it)}}
        await(label){value.get()!=null};return value.get()
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);phase=arguments?.getString("phase")?:phase;check(phase in setOf("idle","absolute","lifecycle"));start()}
    override fun onStart(){var a:P115CredentialActivity?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val activity=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P115CredentialActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P115CredentialActivity;a=activity
            val runtime=field(activity,"runtime") as ProductRuntime
            val model=field(activity,"model") as ProductViewModel
            check(runtime.authentication.javaClass.name=="org.pag.security.ProductCredentialAuthentication")
            emit("REAL_AUTH_ROOT",JSONObject().put("authentication",runtime.authentication.javaClass.name).put("inventory",runtime.inventory.state().name))
            fun auth():Long {
                await("foreground"){main{activity.hasWindowFocus()}}
                val before=main{runtime.session.snapshot().generation}
                emit("MANUAL_CREDENTIAL_NOW",JSONObject().put("previous_generation",before))
                main{model.unlock()}
                await("real credential READY"){main{runtime.session.snapshot().state==SessionState.READY}}
                val ready=main{runtime.session.snapshot()};check(ready.generation>before)
                emit("REAL_READY",JSONObject().put("generation",ready.generation).put("remaining_absolute_ms",main{runtime.session.remainingAbsoluteMillis()}))
                return ready.generation
            }
            val generation=auth()
            val old=main{checkNotNull(runtime.session.acquire(AccessScope.READY))}
            val oldOwner=main{(field(runtime,"owner") as AtomicReference<*>).get() as org.pag.data.product.SessionDbKeyOwner}
            fun deniedKey(){
                check(main{(field(runtime,"owner") as AtomicReference<*>).get()==null})
                var consumed=false
                val denied=runCatching{main{oldOwner.withDbKey(old){consumed=true}}}.isFailure
                check(denied && !consumed)
                emit("OLD_KEY_OWNER_DENIED")
            }
            val start=SystemClock.elapsedRealtime()
            val remainingAtStart=main{runtime.session.remainingAbsoluteMillis()}!!
            when(phase){
                "idle"->{
                    emit("IDLE_BEGIN",JSONObject().put("generation",generation))
                    await("30s idle lock",40_000){main{runtime.session.snapshot().state==SessionState.LOCKED}}
                    val elapsed=SystemClock.elapsedRealtime()-start
                    check(elapsed in 29_000..33_000)
                    check(!main{old.check(AccessPoint.PUBLICATION)});check(!main{old.publish{error("old publication")}})
                    deniedKey()
                    emit("IDLE_LOCK_PASS",JSONObject().put("since_ready_ms",elapsed))
                    val next=auth();check(next>generation)
                    check(!main{old.check(AccessPoint.STEP)});check(!main{old.publish{error("old generation publication")}})
                    emit("REAUTH_OLD_CONTEXT_DENIED",JSONObject().put("old_generation",generation).put("new_generation",next))
                }
                "absolute"->{
                    emit("ABSOLUTE_BEGIN",JSONObject().put("generation",generation).put("test_interaction_every_ms",10_000))
                    var next=start+10_000;var heartbeat=start+25_000
                    while(main{runtime.session.snapshot().state==SessionState.READY}){
                        val now=SystemClock.elapsedRealtime()
                        if(now>=next){main{runtime.session.userInteraction()};next+=10_000}
                        if(now>=heartbeat){emit("ABSOLUTE_PROGRESS",JSONObject().put("since_ready_ms",now-start));heartbeat+=25_000}
                        check(now-start<185_000);Thread.sleep(50)
                    }
                    val elapsed=SystemClock.elapsedRealtime()-start
                    check(main{runtime.session.snapshot().state==SessionState.LOCKED})
                    check(kotlin.math.abs(elapsed-remainingAtStart)<1000)
                    check(!main{old.publish{error("absolute old publication")}})
                    deniedKey()
                    emit("ABSOLUTE_LOCK_PASS",JSONObject().put("since_ready_ms",elapsed).put("remaining_at_start_ms",remainingAtStart).put("auth_to_deadline_ms",180_000))
                }
                "lifecycle"->{
                    // Synthetic content via normal repositories under REAL credential authority.
                    val action=receive<Action>("create action"){runtime.actions.createAction(NewAction("Synthetic P115", "Synthetic security fixture",0,1000),it)}
                    val prep=receive<ActionChangeResult>("prepare action"){runtime.actions.advanceActionStatus(action.id,ActionStatus.DRAFT,it)}
                    check(prep is ActionChangeResult.Applied)
                    check(receive<PersonResult<Person>>("create person"){runtime.persons.createPerson(action.id,"Synthetic P115 person",it)} is PersonResult.Value)
                    check(receive<OperationsResult<Task>>("create task"){runtime.tasks.create(action.id,TaskInput("Synthetic P115 task"),it)} is OperationsResult.Value)
                    check(receive<ActionChangeResult>("activate action"){runtime.actions.advanceActionStatus(action.id,ActionStatus.PREPARATION,it)} is ActionChangeResult.Applied)
                    main{model.detail(action.id)}
                    await("ACTIVE HUB"){main{model.compactHubPresentation!=null && !model.personsLoading}}
                    val hub=main{checkNotNull(model.compactHubPresentation)}
                    val header=main{checkNotNull(hub.navigation(org.pag.prototype.product.ActiveArea.TASKS))}
                    main{model.activeArea(org.pag.prototype.product.ActiveArea.TASKS)}
                    var row:org.pag.prototype.product.OriginalButtonControl?=null
                    await("original row"){main{row=model.originalRowControl(0);row!=null}}
                    main{row!!.click()}
                    await("task detail"){main{model.tasks.selected!=null && !model.detailLoading}}
                    main{model.tasks.edit();model.tasks.changeTitle("Synthetic P115 retained draft")}
                    val form=main{model.inputScreenKey() as org.pag.prototype.product.OriginalUiFence}
                    val scope=main{checkNotNull(model.workspace?.access)}
                    var writes=0;var plaintext=0
                    val writer=main{
                        check(form.reserve(org.pag.prototype.product.InputControlCost.click))
                        org.pag.prototype.product.OriginalInputClick(form,org.pag.prototype.product.FormInput{},model.uiPresentationRevision){writes++;model.tasks.save()}
                    }
                    fun oldCapturesDenied(){main{
                        val before=model.activeArea
                        header();row!!.click();writer()
                        check(model.activeArea==before && !model.tasks.busy && writes==0)
                        check(hub.value(0,0)==null && !form.live())
                        check(!scope.publish(action.id,ActionStatus.ACTIVE,generation){plaintext++})
                        check(!scope.consume(ActiveWorkspaceBudget.Units(objects=1)){plaintext++})
                        check(plaintext==0)
                    }}
                    emit("ACTIVE_CAPTURES_HELD",JSONObject().put("types","header,row,form,writer,workspace_access,storage_context,key_owner").put("synthetic_content",true).put("synthetic_authentication",false))
                    emit("HOME_BACKGROUND_NOW")
                    uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    await("background lock",5000){main{runtime.session.snapshot().state==SessionState.LOCKED}}
                    check(!main{old.publish{error("background old publication")}})
                    main{activity.startActivity(Intent().setClassName(targetContext,"org.pag.prototype.P115CredentialActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))}
                    await("return foreground"){main{activity.hasWindowFocus()}}
                    check(main{runtime.session.snapshot().state==SessionState.LOCKED})
                    deniedKey();oldCapturesDenied()
                    emit("BACKGROUND_FOREGROUND_LOCKED_PASS")
                    val next=auth();check(next>generation);oldCapturesDenied()
                    val freshContext=main{runtime.session.acquire(AccessScope.READY)!!}
                    var consumed=false
                    check(runCatching{main{oldOwner.withDbKey(freshContext){consumed=true}}}.isFailure && !consumed)
                    emit("OLD_KEY_OWNER_REJECTS_NEW_GENERATION")
                    emit("REAL_REAUTH_OLD_ACTIVE_CAPTURES_DENIED",JSONObject().put("old_generation",generation).put("new_generation",next))
                    emit("MANUAL_DEVICE_LOCK_NOW")
                    await("screen/device lock",90_000){main{runtime.session.snapshot().state==SessionState.LOCKED && activity.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked}}
                    emit("DEVICE_LOCK_DETECTED")
                    await("manual device unlock and foreground",90_000){main{activity.hasWindowFocus() && !activity.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked}}
                    check(main{runtime.session.snapshot().state==SessionState.LOCKED})
                    emit("DEVICE_UNLOCK_DOES_NOT_REAUTH_PASS")
                    auth();oldCapturesDenied()
                }
            }
            main{model.lock()};check(!main{old.publish{error("late callback")}})
            emit("PASS",JSONObject().put("real_device_credential",true).put("lifecycle",activity.p115Events.joinToString("|")))
            main{activity.finish()};a=null
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P115 PASS $phase")})
        }catch(t:Throwable){emit("FAIL",JSONObject().put("error",t.stackTraceToString()).put("lifecycle",a?.p115Events?.joinToString("|")));runCatching{main{a?.finish()}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P115 FAIL $phase")})}
    }
}
