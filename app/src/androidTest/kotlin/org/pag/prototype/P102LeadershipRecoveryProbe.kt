package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.pag.data.product.*
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Original Runtime/Session/Room/SQLCipher/Keystore + actual product UI. Authentication
 * provider remains explicitly synthetic P77; NEVER claim a real DeviceCredential prompt. */
class P102LeadershipRecoveryProbe:Instrumentation() {
    private var p111:P111MemorySampler?=null
    override fun finish(resultCode:Int,results:Bundle?){p111?.close();super.finish(resultCode,results)}
    private var scenario="present_ui"
    private var cleanupRoot:String?=null
    private var cleanupInode=0L
    private lateinit var uiActivity:Activity
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private lateinit var d:P101WriterDiagnostics
    private lateinit var fault:P102RecoveryDiagnostics
    private val action="p11-preparation"
    private fun pass()="synthetic-test-passphrase-only".toCharArray()
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}}
        e.get()?.let{throw it};return v.get()
    }
    private fun emit(s:String){p111?.mark(s);sendStatus(0,Bundle().apply{putString("stream","P102,$scenario,$s\n")})}
    private fun waitFor(label:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(40)}
        error("P102 timeout $label")
    }
    private fun <T> call(work:((T)->Unit)->Unit):T {
        val v=AtomicReference<T>();val n=AtomicInteger()
        main{h.runtime.session.userInteraction();work{v.set(it);n.incrementAndGet()}}
        waitFor("one result"){n.get()>0};check(n.get()==1);return v.get()
    }
    private fun unlock() {
        main{if(h.runtime.session.snapshot().state!=SessionState.LOCKED)model.lock();model.unlock()}
        waitFor("original READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}
    }
    private fun reauth(){main{model.lock()};unlock()}
    private fun setup() {check(call<SensitiveResult<Unit>>{h.runtime.leadership.setup(action,pass(),pass(),it)} is SensitiveResult.Value);check(d.leadershipState()=="READY")}
    private fun partial(absent:Boolean=false) {
        if(absent)fault.abortAfterFirstReservedScope() else d.failureMode="create_after"
        val callbacks=AtomicInteger()
        main{h.runtime.leadership.setup(action,pass(),pass()){callbacks.incrementAndGet()}}
        waitFor("fail-closed partial setup"){h.runtime.session.snapshot().state==SessionState.BLOCKED}
        check(callbacks.get()==0);unlock()
        check(d.leadershipState()=="RESERVED" && fault.aliasPresent()!=absent)
        emit("PARTIAL,reserved_durable=true,alias_present=${fault.aliasPresent()},injected_posteffect_fault=true,no_success=true")
    }
    private fun prepared():LeadershipRecoveryRequest = (call<SensitiveResult<LeadershipRecoveryRequest>>{h.runtime.leadership.prepareRecovery(action,it)} as SensitiveResult.Value).value
    private fun recover(intent:LeadershipRecoveryRequest):SensitiveResult<Unit> =call{h.runtime.leadership.recover(intent,it)}
    private fun visible(text:String,click:Boolean=false):Boolean {
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:AccessibilityNodeInfo):Boolean {
            if(!n.refresh())return false
            if(n.isVisibleToUser && n.text?.toString()==text) {
                if(!click)return true
                var target:AccessibilityNodeInfo?=AccessibilityNodeInfo.obtain(n)
                try {
                    repeat(5) {
                        val current=target?:return false
                        if(current.isClickable)return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        val parent=current.parent;current.recycle();target=parent
                    }
                }finally{target?.recycle()}
                return false
            }
            for(i in 0 until n.childCount){val child=n.getChild(i)?:continue
                try{if(visit(child))return true}finally{child.recycle()}}
            return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    private fun show(text:String) {
        repeat(12) {
            if(visible(text))return
            val metrics=targetContext.resources.displayMetrics
            val start=SystemClock.uptimeMillis()
            for(step in 0..6) {
                val e=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),
                    if(step==0)0 else if(step==6)1 else 2,metrics.widthPixels/2f,metrics.heightPixels*(0.75f-step*0.045f),0)
                e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try{check(uiAutomation.injectInputEvent(e,true))}finally{e.recycle()};Thread.sleep(70)
            };Thread.sleep(200)
        }
        error("P102 text not visible: $text")
    }
    private fun uiRecovery(delete:Boolean=false) {
        val offer=if(delete)"Entfernung des Leitungsschutzes fortsetzen" else "Unvollständige Einrichtung zurücksetzen"
        main{model.detail(action)}
        waitFor("metadata route"){main{model.workspace?.leadershipState is SensitiveResult.Value && !model.personsLoading}}
        show(offer);check(visible(offer,true))
        waitFor("first confirmation"){main{model.leadershipRecoveryPhase==LeadershipRecoveryPhase.CONFIRM_REAUTH}}
        check(d.leadershipState()==if(delete)"DELETE_PENDING" else "RESERVED")
        waitFor("credential button visible"){visible("Mit Gerätesperrcode bestätigen")}
        val old=main{model.snapshot.generation}
        check(visible("Mit Gerätesperrcode bestätigen",true))
        waitFor("fresh original READY and second confirmation"){main{model.leadershipRecoveryPhase==LeadershipRecoveryPhase.CONFIRM_RESET && model.snapshot.generation>old && model.actions.isNotEmpty()}}
        // Merely reauth and display did not reset anything.
        check(d.leadershipState()==if(delete)"DELETE_PENDING" else "RESERVED")
        waitFor("second visible confirmation"){visible("Jetzt bestätigen")}
        check(visible("Jetzt bestätigen",true))
        waitFor("confirmed UI success"){main{model.feedback?.text==if(delete)"Entfernung des Leitungsschutzes abgeschlossen" else "Unvollständige Einrichtung zurückgesetzt"}}
        val success=if(delete)"Entfernung des Leitungsschutzes abgeschlossen" else "Unvollständige Einrichtung zurückgesetzt"
        waitFor("actual visible success"){visible("✓ $success")}
        check(d.leadershipState()=="ABSENT" && !fault.aliasPresent() && main{uiActivity.hasWindowFocus()})
        emit("UI,same_activity_focus=true,offer_visible=true,two_explicit_clicks_and_reauth=true,fresh_READY=true,no_reset_on_reauth=true,confirmed_feedback=true,visible_feedback=true,synthetic_auth_provider=true")
    }
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);p111=P111MemorySampler.start(this,arguments);scenario=arguments?.getString("scenario")?:scenario
        cleanupRoot=arguments?.getString("cleanupRoot");cleanupInode=arguments?.getString("cleanupInode")?.toLongOrNull()?:0
        check(scenario=="cleanup_crash" || scenario in setOf("normal_ready","present_ui","absent_ui","unknown_alias","content_refusal","crash_retry","lock_before","lock_during","lock_after","status_change","delete_pending_ui","cleanup_receipt"));start()}
    override fun onStart() {
        var activity:Activity?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            if(scenario=="cleanup_crash") {
                P102RecoveryDiagnostics.cleanupRecordedCrash(targetContext,checkNotNull(cleanupRoot),cleanupInode)
                emit("RECORDED_CRASH_CLEANUP,root_absent=true,key_alias_absent=true,exact_inode_guard=true")
                finish(Activity.RESULT_OK,Bundle().apply{putString("result","P102 PASS cleanup_crash")});return
            }
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture","p86-small").putExtra("p101Probe",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a;uiActivity=a
            h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            p111?.bind(h,a,model)
            waitFor("focus"){main{a.hasWindowFocus()}};unlock()
            d=P101WriterDiagnostics(targetContext,h.runtime);fault=P102RecoveryDiagnostics(targetContext,h.runtime)
            val r=h.runtime.leadership
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            when(scenario) {
                "normal_ready"->{setup();check(call<SensitiveResult<LeadershipRecoveryRequest>>{r.prepareRecovery(action,it)} is SensitiveResult.Rejected)
                    check(d.leadershipState()=="READY" && fault.deletes==0);emit("READY,no_downgrade=true,normal_setup=true")}
                "present_ui","absent_ui"->{partial(scenario=="absent_ui");uiRecovery();check(fault.deletes==if(scenario=="absent_ui")0 else 1)}
                "unknown_alias","content_refusal"->{partial();val intent=prepared();reauth()
                    if(scenario=="content_refusal") {
                        fault.syntheticSensitiveRowUnderReserved(d.personId())
                        check(call<SensitiveResult<LeadershipRecoveryRequest>>{r.prepareRecovery(action,it)} is SensitiveResult.Rejected)
                        check(recover(intent) is SensitiveResult.Rejected)
                    }else {
                        fault.aliasUnusable=true
                        check(call<SensitiveResult<LeadershipRecoveryRequest>>{r.prepareRecovery(action,it)} is SensitiveResult.Rejected)
                        fault.aliasUnusable=false;fault.aliasUnknown=true
                        val callbacks=AtomicInteger()
                        main{h.faults.main.holdNext();r.recover(intent){callbacks.incrementAndGet()}}
                        waitFor("unknown original alias lookup"){h.faults.main.isHeld()}
                        val late=h.faults.main.take();val proof=d.writerCompletion(late)
                        check(proof.startsWith("outcome=UNKNOWN,"));main{late.run();late.run()}
                        waitFor("unknown fail closed"){h.runtime.session.snapshot().state==SessionState.BLOCKED}
                        check(callbacks.get()==0);fault.aliasUnknown=false;unlock()
                        emit("UNKNOWN_ALIAS,$proof,lookup_exception_not_absence=true,no_success=true")
                    }
                    check(d.leadershipState()=="RESERVED" && fault.aliasPresent() && fault.deletes==0)
                    emit("REFUSAL,actual_root_and_alias_unchanged=true,keydelete_calls=0,synthetic_ambiguous_or_content_fixture=true,checked_after_preparation=true")}
                "delete_pending_ui"->{setup()
                    d.failureMode="delete_after";val callbacks=AtomicInteger()
                    main{r.deleteAction(action,pass()){callbacks.incrementAndGet()}}
                    waitFor("partial delete"){h.runtime.session.snapshot().state==SessionState.BLOCKED};check(callbacks.get()==0)
                    unlock();check(d.leadershipState()=="DELETE_PENDING" && !fault.aliasPresent());uiRecovery(true)}
                else->{partial();val intent=prepared();val callbacks=AtomicInteger();reauth()
                    when(scenario) {
                        "status_change"->{check(call<ActionChangeResult>{h.runtime.actions.advanceActionStatus(action,ActionStatus.PREPARATION,it)} is ActionChangeResult.Applied)
                            check(recover(intent) is SensitiveResult.Rejected);check(fault.deletes==0 && fault.aliasPresent());emit("STATUS,actual_ACTIVE_commit_denies_recovery=true")}
                        "lock_before"->{main{h.faults.worker.holdNext();r.recover(intent){callbacks.incrementAndGet()};model.lock()};h.faults.worker.release()
                            unlock();check(d.leadershipState()=="RESERVED" && fault.aliasPresent() && fault.deletes==0 && callbacks.get()==0);emit("LOCK,before_dispatch=true,no_keyeffect=true,no_success=true")}
                        "lock_after"->{main{h.faults.main.holdNext();r.recover(intent){callbacks.incrementAndGet()}}
                            waitFor("final completion"){h.faults.main.isHeld()};val late=h.faults.main.take()
                            check(d.leadershipState()=="ABSENT");main{model.lock()};unlock();main{late.run();late.run()}
                            check(callbacks.get()==0 && d.leadershipState()=="ABSENT");emit("LOCK,confirmed_commit_before_lock=true,late_duplicate_no_success=true")}
                        "cleanup_receipt"->{fault.failFinalRecoveryCleanup();main{h.faults.main.holdNext();r.recover(intent){callbacks.incrementAndGet()}}
                            waitFor("failed cleanup receipt"){h.faults.main.isHeld()};val late=h.faults.main.take();val proof=d.writerCompletion(late)
                            check(proof=="outcome=COMMITTED,cleanup=FAILED_OR_UNCONFIRMED");main{late.run();late.run()}
                            waitFor("BLOCKED"){h.runtime.session.snapshot().state==SessionState.BLOCKED};unlock()
                            check(callbacks.get()==0 && d.leadershipState()=="ABSENT");emit("CLEANUP,$proof,injected_after_actual_driver_close=true,no_success=true")}
                        else->{if(scenario=="lock_during")fault.lockAfterDelete=true else fault.failAfterDelete=true
                            main{r.recover(intent){callbacks.incrementAndGet()}}
                            waitFor("interrupted real key effect"){h.runtime.session.snapshot().state in setOf(SessionState.LOCKED,SessionState.BLOCKED)}
                            unlock();check(callbacks.get()==0 && d.leadershipState()=="RESERVED" && !fault.aliasPresent())
                            val fresh=prepared();reauth();check(recover(fresh) is SensitiveResult.Value)
                            check(d.leadershipState()=="ABSENT" && fault.deletes==1)
                            check(recover(fresh)==SensitiveResult.Rejected(SensitiveFailure.DENIED))
                            emit("RETRY,original_reserved_survived_actual_keydelete=true,new_explicit_intent_and_reauth=true,no_second_keydelete=true,old_intent_denied=true")}
                    }
                }
            }
            d.cleanupOwnTestAlias();main{model.lock();a.finish()};waitFor("fixture cleanup"){h.cleanupConfirmed.get()}
            check(targetContext.noBackupFilesDir.listFiles().orEmpty().none{it.name.startsWith("p11-fixture-")})
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,key_alias_absent=true,reuse=false,ram_reuse=false")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P102 PASS $scenario")})
        } catch(t:Throwable) {
            emit("FAILURE,${t.stackTraceToString()}")
            if(::d.isInitialized)runCatching{d.cleanupOwnTestAlias()}
            runCatching{main{activity?.finish()}}
            if(::h.isInitialized)runCatching{waitFor("failure cleanup"){h.cleanupConfirmed.get()}}
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P102 FAIL $scenario")})
        }
    }
}
