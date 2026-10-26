package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.data.product.P101WriterDiagnostics
import org.pag.domain.*
import org.pag.prototype.product.ProductViewModel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real Runtime/common port/Room/SQLCipher/AndroidKeystore. Synthetic own-package data only. */
class P101WriterAcceptanceProbe:Instrumentation() {
    private var p111:P111MemorySampler?=null
    override fun finish(resultCode:Int,results:Bundle?){p111?.close();super.finish(resultCode,results)}
    private var scenario="leadership"
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var model:ProductViewModel
    private lateinit var d:P101WriterDiagnostics
    private val action="p11-preparation"
    private fun pass()="synthetic-test-passphrase-only".toCharArray()
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        val v=AtomicReference<T>();val e=AtomicReference<Throwable?>()
        runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}}
        e.get()?.let{throw it};return v.get()
    }
    private fun emit(s:String){p111?.mark(s);sendStatus(0,Bundle().apply{putString("stream","P101,$scenario,$s\n")})}
    private fun waitFor(label:String,test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end){if(test())return;Thread.sleep(25)}
        error("P101 timeout $label; state=${h.runtime.session.snapshot().state}")
    }
    private fun unlock(){main{if(model.snapshot.state==SessionState.BLOCKED)model.lock();model.unlock()};waitFor("READY"){main{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}}}
    private fun <T> call(label:String,work:((T)->Unit)->Unit):T {
        val value=AtomicReference<T>();val calls=AtomicInteger()
        main{h.runtime.session.userInteraction();work{value.set(it);calls.incrementAndGet()}}
        waitFor(label){calls.get()>0};check(calls.get()==1)
        emit("CALL,label=$label,once=true,epoch=${main{h.mutations.mutationEpoch}}")
        return value.get()
    }
    private fun setup(){val p=pass();val q=pass();check(call<SensitiveResult<Unit>>("setup"){h.runtime.leadership.setup(action,p,q,it)} is SensitiveResult.Value);check(p.all{it=='\u0000'}&&q.all{it=='\u0000'})}
    private fun opened(person:String):SensitiveOpened {
        val before=main{h.mutations.mutationEpoch}
        val result=call<SensitiveResult<SensitiveOpened>>("open"){h.runtime.leadership.open(action,person,SensitiveArea.IDENTITY,pass(),it)}
        check(main{h.mutations.mutationEpoch}==before)
        return (result as SensitiveResult.Value).value
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);p111=P111MemorySampler.start(this,arguments);scenario=arguments?.getString("scenario")?:scenario;check(scenario in setOf("leadership","lost_main","partial_setup","partial_delete","documents","rollback","status_reject","commit_receipt","cleanup_receipt","migration","lost_main_recovery"));start()}
    override fun onStart() {
        var activity:Activity?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture","p86-small").putExtra("p101Probe",true).putExtra("p101Migration10",scenario=="migration").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));activity=a
            h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            p111?.bind(h,a,model)
            waitFor("focus"){main{a.hasWindowFocus()}}
            if(scenario=="migration")check(h.p101SeededVersion()==10 && main{h.runtime.session.acquire(AccessScope.READY)}==null)
            unlock()
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            d=P101WriterDiagnostics(targetContext,h.runtime)
            val r=h.runtime.leadership
            when(scenario) {
                "migration"->{
                    check(d.schemaVersion()==12L && d.count("persons")==20L && d.count("tasks")==40L)
                    check(main{h.mutations.mutationEpoch}==0L)
                    emit("MIGRATION,seed_version=10,READY_denied_before_auth=true,version_after_authorized_maintenance=11,persons=20,tasks=40,ready_writer_epoch=0")
                }
                "leadership"->{
                    val old=main{h.mutations.beforeActiveRead(action,model.snapshot.generation)!!}
                    val p=pass();val q=pass();val callbacks=AtomicInteger()
                    main{h.faults.worker.holdNext();r.setup(action,p,q){check(it is SensitiveResult.Value);callbacks.incrementAndGet()}}
                    check(h.faults.worker.isHeld() && !old.isLive() && d.creates==0)
                    main{check(h.mutations.beforeActiveRead(action,model.snapshot.generation)==null)}
                    h.faults.worker.release();waitFor("setup callback"){callbacks.get()==1}
                    check(p.all{it=='\u0000'}&&q.all{it=='\u0000'});check(d.leadershipState()=="READY")
                    val person=d.personId();var opened=opened(person)
                    check(call<SensitiveResult<Unit>>("save"){r.save(opened.grant,SensitiveData.Identity("Synthetic P101"),it)} is SensitiveResult.Value)
                    opened=opened(person)
                    check(call<SensitiveResult<Unit>>("delete_area"){r.deleteArea(opened.grant,it)} is SensitiveResult.Value)
                    opened=opened(person)
                    check(call<SensitiveResult<Unit>>("save_again"){r.save(opened.grant,SensitiveData.Identity("Synthetic P101"),it)} is SensitiveResult.Value)
                    check(call<SensitiveResult<Unit>>("delete_person"){r.deletePerson(action,person,pass(),it)} is SensitiveResult.Value)
                    check(call<SensitiveResult<Unit>>("delete_action"){r.deleteAction(action,pass(),it)} is SensitiveResult.Value)
                    check(d.leadershipState()=="ABSENT" && d.creates==1 && d.deletes==1)
                    emit("LEADERSHIP,setup_save_deletearea_deleteperson_deleteaction=true,read_open_epoch_unchanged=true,begin_before_queue=true,epochs=${main{h.mutations.mutationEpoch}}")
                }
                "lost_main"->{
                    val callbacks=AtomicInteger();val p=pass();val q=pass()
                    main{h.faults.main.holdNext();r.setup(action,p,q){callbacks.incrementAndGet()}}
                    waitFor("held committed main"){h.faults.main.isHeld()}
                    check(d.leadershipState()=="READY" && callbacks.get()==0)
                    val completion=d.writerCompletion(checkNotNull(h.faults.main.heldTaskForObservation()))
                    check(completion=="outcome=COMMITTED,cleanup=COMPLETE");emit("COMPLETION,$completion")
                    main{check(h.mutations.beforeActiveRead(action,model.snapshot.generation)==null);r.revoke()}
                    val late=h.faults.main.take();main{late.run();late.run()}
                    check(callbacks.get()==0 && p.all{it=='\u0000'}&&q.all{it=='\u0000'})
                    check(d.leadershipState()=="READY" && d.creates==1)
                    check(call<SensitiveResult<Unit>>("explicit_delete_after_late_main"){r.deleteAction(action,pass(),it)} is SensitiveResult.Value)
                    emit("LOST_MAIN,persisted_commit=READY,callback_count=0,late_duplicate_inert=true,subsequent_explicit_mutation=true")
                }
                "lost_main_recovery"->{
                    val callbacks=AtomicInteger();val nextCalls=AtomicInteger()
                    main{h.faults.main.holdNext();r.setup(action,pass(),pass()){callbacks.incrementAndGet()}}
                    waitFor("clean committed work without UI delivery"){h.faults.main.isHeld()}
                    check(d.writerCompletion(checkNotNull(h.faults.main.heldTaskForObservation()))=="outcome=COMMITTED,cleanup=COMPLETE")
                    main{check(h.mutations.beforeActiveRead(action,model.snapshot.generation)==null);model.lock()}
                    unlock();check(d.leadershipState()=="READY")
                    main{h.faults.worker.holdNext();r.deleteAction(action,pass()){check(it is SensitiveResult.Value);nextCalls.incrementAndGet()}}
                    check(h.faults.worker.isHeld()) // B was admitted BEFORE original A UI delivery.
                    val epoch=main{h.mutations.mutationEpoch};val old=h.faults.main.take()
                    main{old.run();old.run();check(h.mutations.beforeActiveRead(action,model.snapshot.generation)==null);check(h.mutations.mutationEpoch==epoch)}
                    check(callbacks.get()==0);h.faults.worker.release();waitFor("B cleanup"){nextCalls.get()==1}
                    check(d.leadershipState()=="ABSENT" && d.creates==1 && d.deletes==1)
                    emit("LOST_MAIN_RECOVERY,fresh_actual_READY=true,B_admitted_before_A_UI=true,late_A_cannot_ack_B=true,A_callbacks=0,B_callbacks=1,no_retry=true")
                }
                "partial_setup","partial_delete"->{
                    if(scenario=="partial_delete")setup()
                    val epoch=main{h.mutations.mutationEpoch};val originalGeneration=main{model.snapshot.generation};val callbacks=AtomicInteger()
                    d.failureMode=if(scenario=="partial_setup")"create_after" else "delete_after"
                    val p=pass();val q=pass()
                    main{h.faults.main.holdNext();if(scenario=="partial_setup")r.setup(action,p,q){callbacks.incrementAndGet()} else r.deleteAction(action,p){callbacks.incrementAndGet()}}
                    waitFor("held partial outcome"){h.faults.main.isHeld()}
                    val completion=d.writerCompletion(checkNotNull(h.faults.main.heldTaskForObservation()))
                    check(completion.startsWith("outcome=UNKNOWN,"));emit("COMPLETION,$completion")
                    val pending=h.faults.main.take();main{pending.run();pending.run()}
                    waitFor("fail-closed state"){h.runtime.session.snapshot().state==SessionState.BLOCKED}
                    check(callbacks.get()==0 && p.all{it=='\u0000'})
                    main{check(h.mutations.mutationEpoch==epoch+1);check(h.mutations.beforeActiveRead(action,originalGeneration)==null)}
                    check(d.effectObserved==if(scenario=="partial_setup")"RESERVED" else "DELETE_PENDING")
                    emit("PARTIAL,confirmed_db_state=${d.effectObserved},real_key_effect_then_injected_exception=true,callback_count=0,state=BLOCKED")
                    unlock()
                    if(scenario=="partial_setup") {
                        check(d.leadershipState()=="RESERVED")
                        val result=call<SensitiveResult<Unit>>("existing_reserved_delete_api"){r.deleteAction(action,pass(),it)}
                        check(result==SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE) && d.leadershipState()=="RESERVED")
                        emit("RESERVED_RECOVERY,BLOCKED,existing_delete_api=UNAVAILABLE,no_destructive_fallback=true")
                    }else {
                        check(d.leadershipState()=="DELETE_PENDING")
                        check(call<SensitiveResult<Unit>>("explicit_delete_pending_recovery"){r.deleteAction(action,pass(),it)} is SensitiveResult.Value)
                        check(d.leadershipState()=="ABSENT")
                        emit("DELETE_PENDING_RECOVERY,explicit_reauth_and_request=true,result=COMPLETED")
                    }
                }
                "commit_receipt","cleanup_receipt"->{
                    val gen=main{model.snapshot.generation};val callbacks=AtomicInteger()
                    d.armDatabaseFault(scenario)
                    main{h.faults.main.holdNext();r.setup(action,pass(),pass()){callbacks.incrementAndGet()}}
                    waitFor("held uncertain writer"){h.faults.main.isHeld()}
                    val completion=d.writerCompletion(checkNotNull(h.faults.main.heldTaskForObservation()))
                    check(completion==if(scenario=="commit_receipt")"outcome=UNKNOWN,cleanup=COMPLETE" else "outcome=COMMITTED,cleanup=FAILED_OR_UNCONFIRMED")
                    emit("COMPLETION,$completion,effect=${d.effectObserved},injection_after_actual_return=true")
                    val pending=h.faults.main.take();main{pending.run();pending.run()}
                    waitFor("uncertain BLOCKED"){h.runtime.session.snapshot().state==SessionState.BLOCKED}
                    check(callbacks.get()==0);main{check(h.mutations.beforeActiveRead(action,gen)==null)}
                    unlock();val stored=d.leadershipState()
                    emit("DURABLE_AFTER_UNCERTAINTY,state=$stored,callback_count=0,no_retry=true")
                    if(scenario=="commit_receipt") {
                        // An END statement step returning is NOT the OUTER Room end receipt.
                        // Unknown may have committed or rolled back; no retroactive relabeling.
                        check(d.effectObserved.startsWith("RESERVATION_END_STEP_RETURNED/") && d.creates==0)
                        check(stored in setOf("ABSENT","RESERVED"))
                    }else check(stored=="READY")
                }
                "rollback","status_reject"->{
                    setup();val opened=opened(d.personId());val callbacks=AtomicInteger()
                    if(scenario=="rollback") {
                        d.lockAfterSensitiveWrite=true
                        main{r.save(opened.grant,SensitiveData.Identity("Synthetic rolled back")){callbacks.incrementAndGet()}}
                        waitFor("lock inside actual DB write"){h.runtime.session.snapshot().state==SessionState.LOCKED}
                        unlock();check(callbacks.get()==0 && d.count("sensitive_person_areas")==0L)
                        check(d.leadershipState()=="READY")
                        emit("ROLLBACK,lock_after_real_DAO_write=true,persisted_sensitive_rows=0,success_callbacks=0")
                        check(call<SensitiveResult<Unit>>("cleanup_ready_leadership"){r.deleteAction(action,pass(),it)} is SensitiveResult.Value)
                    } else {
                        check(call<ActionChangeResult>("advance_action_status"){h.runtime.actions.advanceActionStatus(action,ActionStatus.PREPARATION,it)} is ActionChangeResult.Applied)
                        val result=call<SensitiveResult<Unit>>("deny_after_status_change"){r.save(opened.grant,SensitiveData.Identity("Synthetic denied"),it)}
                        check(result==SensitiveResult.Rejected(SensitiveFailure.DENIED) && d.count("sensitive_person_areas")==0L)
                        emit("STATUS,confirmed_action_commit=ACTIVE,leadership_save=DENIED,persisted_sensitive_rows=0")
                    }
                }
                "documents"->{
                    val docs=h.runtime.documents
                    check(call<DocumentResult<Unit>>("import"){docs.importDocument(action,"Synthetic P101",DocumentFormat.TEXT,DocumentSource{"Synthetic document\n".byteInputStream()},it)} is DocumentResult.Value)
                    val assets=call<DocumentResult<List<DocumentAsset>>>("list"){docs.list(action,it)} as DocumentResult.Value
                    check(assets.value.size==1 && d.count("documents")==1L && d.count("document_jobs")==0L)
                    val sourceCalls=AtomicInteger();val callbacks=AtomicInteger()
                    main{h.faults.main.holdNext();docs.importDocument(action,"Synthetic interrupted",DocumentFormat.TEXT,
                        DocumentSource{sourceCalls.incrementAndGet();throw java.io.IOException("SYNTHETIC_SOURCE_FAILURE")}){callbacks.incrementAndGet()}}
                    waitFor("partial import completion"){h.faults.main.isHeld()}
                    val phase=d.documentPhase();check(phase=="IMPORT_WRITING" && sourceCalls.get()==1)
                    val late=h.faults.main.take();main{late.run();late.run();check(h.mutations.beforeActiveRead(action,model.snapshot.generation)==null)}
                    check(callbacks.get()==0)
                    val oldEpoch=main{h.mutations.mutationEpoch};val p=pass();val q=pass()
                    check(call<SensitiveResult<Unit>>("p3b_denied_while_document_uncertain"){r.setup(action,p,q,it)}==SensitiveResult.Rejected(SensitiveFailure.DENIED))
                    check(p.all{it=='\u0000'} && q.all{it=='\u0000'} && d.creates==0 && main{h.mutations.mutationEpoch}==oldEpoch)
                    emit("DOCUMENT_PARTIAL,phase=$phase,source_calls=1,success_callbacks=0,common_port_denies=true")
                    main{model.lock()};unlock()
                    check(d.count("document_jobs")==0L && d.count("documents")==1L && sourceCalls.get()==1)
                    check(call<DocumentResult<Unit>>("delete_document"){docs.deleteDocument(action,assets.value.single(),it)} is DocumentResult.Value)
                    check(d.count("documents")==0L && d.count("document_jobs")==0L)
                    emit("DOCUMENT_RECOVERY,reauth_maintenance_before_READY=true,no_source_retry=true,import_delete_committed=true,jobs=0,documents=0")
                }
            }
            d.cleanupOwnTestAlias();main{model.lock();a.finish()};waitFor("fixture cleanup"){h.cleanupConfirmed.get()}
            check(targetContext.noBackupFilesDir.listFiles().orEmpty().none{it.name.startsWith("p11-fixture-")})
            emit("CASE,ANDROID_PASS,fixture_cleanup=true,key_alias_absent=true,reuse=false,ram_reuse=false")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P101 PASS $scenario")})
        }catch(t:Throwable){
            emit("FAILURE,${t.stackTraceToString()}")
            if(::d.isInitialized)runCatching{d.cleanupOwnTestAlias()}
            runCatching{main{activity?.finish()}}
            if(::h.isInitialized)runCatching{waitFor("failure cleanup"){h.cleanupConfirmed.get()}}
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P101 FAIL $scenario")})
        }
    }
}
