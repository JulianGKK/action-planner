package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual product admission, original Runtime/Room/SQLCipher/keyowner/VM. Synthetic P77 auth.
 * The unchanged §83 reports remain the historical three-graph RED characterization.
 */
class P84OutstandingCoreProbe:Instrumentation() {
    private var scenario="multi_pending"
    private var fixture="roles32"
    private val roots=P83MemoryOverlapProbe()
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario") ?: scenario;fixture=arguments?.getString("fixture") ?: fixture;start()}
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())return work()
        val result=AtomicReference<T>();val error=AtomicReference<Throwable?>()
        runOnMainSync{try{result.set(work())}catch(t:Throwable){error.set(t)}}
        error.get()?.let{throw it};return result.get()
    }
    private fun waitFor(name:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(20)}
        error("P84 timeout $name")
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream",s+"\n")})
    private fun raw():String {
        val r=Runtime.getRuntime();val p=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}
        return "art_used=${r.totalMemory()-r.freeMemory()},native_heap=${Debug.getNativeHeapAllocatedSize()},pss_kib=${p.totalPss},fd=${java.io.File("/proc/self/fd").list()?.size},gc=${Debug.getRuntimeStat("art.gc.gc-count")}"
    }
    override fun onStart() {
        var a:MainActivity?=null;var handle:P77ProductRuntimeHarness?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).putExtra("p83Fixture",fixture).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            a=activity
            val h=field(activity,"p77Harness") as P77ProductRuntimeHarness;handle=h
            val runtime=h.runtime;val model=field(activity,"model") as ProductViewModel;val probe=h.memory
            fun state()=h.outstandingState()
            fun phase(name:String)=main {
                val rs=roots.composeRoots(activity);val qs=roots.cores(h.faults.main.heldTaskForObservation())
                emit("P84_PHASE,$fixture,$scenario,$name,t_ns=${SystemClock.elapsedRealtimeNanos()},vm_id=${model.activeCore?.let{System.identityHashCode(it)} ?: 0},compose_ids=${rs.keys.joinToString("|")},queued_ids=${qs.keys.joinToString("|")},state=${state()},${probe.nativeState()},${raw()}")
                rs.forEach{(id,path)->emit("P84_ROOT,$name,$id,$path")}
            }
            waitFor("focused LOCKED"){main{activity.hasWindowFocus() && runtime.session.snapshot().state==SessionState.LOCKED}}
            main{model.unlock()};waitFor("READY"){runtime.session.snapshot().state==SessionState.READY}
            waitFor("actions"){main{model.actions.any{it.id=="p11-active"}}}
            if(scenario=="cleanup_uncertain") {
                check(fixture=="default")
                h.cleanupFaults.arm("row_and_room_end",runtime.session::lock)
                main{model.detail("p11-active")}
                waitFor("uncertain cleanup fence"){state().contains("FAILED_OR_UNCONFIRMED") && !h.observations.originalTicketLive()}
                emit("P84_SCOPED_FAULT,wrapper_row_plus_post_room_close,${h.cleanupFaults.verify()},${state()}")
                main {
                    check(model.activeCore==null)
                    check(!runtime.activeCore.loadOwned("p11-active"){_,_->error("Unconfirmed cleanup cannot retry")})
                    check(h.observations.reads.get()==1 && h.observations.tickets.get()==1 && h.mutations.mutationEpoch==0L)
                    model.lock();activity.finish()
                }
                waitFor("fixture cleanup"){h.cleanupConfirmed.get()}
                emit("P84_CASE,$fixture,$scenario,ANDROID-GREEN,joint_memory_certification=false,fixture_cleanup=true")
                finish(Activity.RESULT_OK,Bundle().apply{putString("result","P84 PASS $fixture $scenario; uncertain cleanup stays reserved")})
                return
            }
            probe.begin(true)
            main{h.faults.holdNextCoreMain.set(true);model.detail("p11-active")}
            if(scenario=="budget") {
                waitFor("budget result held on Main"){h.faults.main.isHeld() && probe.decision=="BUDGET_REFUSED"}
                phase("budget_main_pending")
                main{h.faults.main.take().run()}
                waitFor("budget refusal cleanup"){main{model.workspaceError && state().contains("retirementConfirmed=true")}}
                check(probe.decision=="BUDGET_REFUSED" && probe.candidate.get()==null && probe.rowReached.count==1L)
                main{check(model.activeCore==null);check(!h.observations.originalTicketLive());check(h.observations.reads.get()==1 && h.observations.tickets.get()==1)}
                emit("P84_BUDGET_REFUSED,$fixture,requested_retained=${probe.retained},requested_transition=${probe.transition},before_materialization=true,${probe.finish()},${state()}")
            } else {
            check(probe.rowReached.await(20,TimeUnit.SECONDS)){"Native row not reached: ${probe.failure}"}
            phase("reader_window_open")
            if(scenario=="worker_revoke") {
                val done=CountDownLatch(1);Thread{try{runtime.session.lock()}finally{done.countDown()}}.start()
                check(done.await(5,TimeUnit.SECONDS))
                probe.release()
                waitFor("worker cleanup"){probe.failure!="none" && state().contains("retirementConfirmed=true")}
                emit("P84_CLEANUP,worker_revoke,${probe.finish()}")
                main{check(model.activeCore==null);check(!h.observations.originalTicketLive())}
                phase("retired_revoked_worker")
            } else {
                probe.rowRelease.countDown()
                check(probe.candidateReached.await(20,TimeUnit.SECONDS)){"Candidate not reached: ${probe.failure}"}
                phase("candidate_worker_complete")
                probe.candidateRelease.countDown()
                waitFor("main pending"){h.faults.main.isHeld() && state().contains("pendingCandidates=1")}
                phase("main_pending")
                val originalEpoch=main{h.mutations.mutationEpoch}
                // Explicit SECOND and THIRD requests: neither may mint a ticket or run SQL.
                main {
                    repeat(2){check(!runtime.activeCore.loadOwned("p11-active"){_,_->error("Unbudgeted candidate callback")})}
                    check(h.observations.reads.get()==1 && h.observations.tickets.get()==1)
                    check(h.observations.originalTicketLive());check(h.mutations.mutationEpoch==originalEpoch)
                }
                phase("pending_refusal_second_and_third")
                emit("P84_CLEANUP,first,${probe.finish()}")
                if(scenario=="pending_lost") {
                    h.faults.main.discard()
                    main {
                        check(!runtime.activeCore.loadOwned("p11-active"){_,_->error("Lost Main cannot release charge")})
                        runtime.activeCore.cancelPending()
                        check(!state().contains("retirementConfirmed=true"))
                        check(state().contains("pendingCandidates=0"))
                        repeat(2){check(!runtime.activeCore.loadOwned("p11-active"){_,_->error("No unacknowledged replacement")})}
                        check(model.activeCore==null)
                    }
                    phase("lost_main_no_retirement_ack")
                    main{model.lock();activity.finish()};waitFor("fixture cleanup"){h.cleanupConfirmed.get()}
                    emit("P84_CASE,$fixture,$scenario,ANDROID-GREEN,joint_memory_certification=false,fixture_cleanup=true")
                    finish(Activity.RESULT_OK,Bundle().apply{putString("result","P84 PASS $fixture $scenario; lost Main stays reserved")})
                    return
                }
                if(scenario in setOf("pending_discard","pending_generation")) {
                    val obsolete=h.faults.main.take()
                    val oldLife=field(runtime.activeCore,"lifetime") as ActiveCoreLifetime
                    main {
                        runtime.activeCore.cancelPending()
                        check(!state().contains("retirementConfirmed=true"))
                        check(roots.cores(obsolete).isEmpty())
                        check(!runtime.activeCore.loadOwned("p11-active"){_,_->error("Before Main acknowledgement")})
                        obsolete.run()
                        check(state().contains("retirementConfirmed=true"))
                    }
                    phase("retired_unpublished_candidate")
                    if(scenario=="pending_generation") {
                        val oldGeneration=runtime.session.snapshot().generation
                        main{model.lock();model.unlock()}
                        waitFor("new READY generation"){runtime.session.snapshot().state==SessionState.READY && runtime.session.snapshot().generation!=oldGeneration}
                        waitFor("new action list"){main{model.actions.any{it.id=="p11-active"}}}
                    }
                    probe.begin(true)
                    main{h.faults.holdNextCoreMain.set(true);model.detail("p11-active")}
                    check(probe.rowReached.await(20,TimeUnit.SECONDS));phase("second_reader_after_retirement")
                    main{obsolete.run();obsolete.run();check(model.activeCore==null)}
                    probe.rowRelease.countDown();check(probe.candidateReached.await(20,TimeUnit.SECONDS))
                    probe.candidateRelease.countDown();waitFor("second pending"){h.faults.main.isHeld() && state().contains("pendingCandidates=1")}
                    main{h.faults.main.take().run();check(model.activeCore!=null);oldLife.close();check(model.activeCore!=null)}
                    emit("P84_CLEANUP,second,${probe.finish()}")
                    check(h.observations.reads.get()==2 && h.observations.tickets.get()==2)
                } else {
                    val task=h.faults.main.take()
                    main{task.run();task.run();check(model.activeCore!=null)}
                    check(h.observations.reads.get()==1 && h.observations.tickets.get()==1)
                }
                val coreId=main{System.identityHashCode(checkNotNull(model.activeCore))}
                waitFor("actual Compose core root"){main{coreId in roots.composeRoots(activity)}}
                phase("old_composed")
                main {
                    val reads=h.observations.reads.get();val tickets=h.observations.tickets.get()
                    repeat(4){for(area in listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB))model.activeArea(area)}
                    check(h.observations.reads.get()==reads && h.observations.tickets.get()==tickets)
                    check(h.observations.hitAccepted.get()==16)
                    check(model.activeCore?.people?.overall?.total==200L && model.activeCore?.tasks?.overall?.total==1000L)
                }
                phase("new_composed")
                val generation=runtime.session.snapshot().generation
                var writerDone=false
                when(scenario) {
                    "multi_pending","normal_editor","pending_discard","pending_generation","pending_lost" -> main {
                        // SAME attempted §83 navigation: old Compose remains in this Main turn.
                        h.faults.falseAfterCallback.set(true);h.faults.holdNextCoreMain.set(true)
                        val reads=h.observations.reads.get();val tickets=h.observations.tickets.get()
                        model.activeArea(ActiveArea.TASKS)
                        check(model.activeCore==null && model.workspaceError)
                        check(coreId in roots.composeRoots(activity))
                        phase("transition_refusal_second")
                        model.activeArea(ActiveArea.HUB) // The THIRD navigation is not skipped.
                        check(!runtime.activeCore.loadOwned("p11-active"){_,_->error("Unbudgeted third result")})
                        check(h.observations.reads.get()==reads && h.observations.tickets.get()==tickets)
                        check(coreId in roots.composeRoots(activity))
                        check(!h.faults.main.isHeld())
                        phase("transition_refusal_third")
                    }
                    "core_writer" -> main {
                        h.faults.worker.holdNext()
                        runtime.tasks.create("p11-active",TaskInput("Synthetic P84 writer")){writerDone=true}
                        check(h.faults.worker.isHeld());check(h.mutations.beforeTaskQueue("p11-active",generation)==null)
                        check(h.mutations.beforeActiveRead("p11-active",generation)==null)
                        check(h.mutations.mutationEpoch==originalEpoch+1)
                    }
                    "document_writer" -> main {
                        h.faults.worker.holdNext()
                        runtime.documents.importDocument("p11-active","",DocumentFormat.TEXT,DocumentSource{error("Invalid source must not open")}){writerDone=true}
                        check(h.faults.worker.isHeld());check(h.mutations.beforeDocumentQueue("p11-active",generation)==null)
                        check(h.mutations.beforeActiveRead("p11-active",generation)==null)
                        check(h.mutations.mutationEpoch==originalEpoch+1)
                    }
                    "generation" -> {main{model.lock();model.unlock()};waitFor("reauth READY"){runtime.session.snapshot().state==SessionState.READY && runtime.session.snapshot().generation!=generation}}
                    "action" -> main{model.detail("p11-completed")}
                    "blocked" -> runtime.session.storageFailure()
                    "unknown" -> main {
                        val pending=checkNotNull(h.mutations.beforeDocumentQueue("p11-active",generation))
                        check(pending.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)))
                        check(h.mutations.beforeActiveRead("p11-active",generation)==null)
                    }
                    else->error("Unknown P84 case")
                }
                main {
                    check(model.activeCore==null)
                    check(!runtime.activeCore.loadOwned("p11-active"){_,_->error("No replacement authority")})
                    check(state().contains("pendingCandidates=0"))
                    check(!state().contains("retirementConfirmed=true"))
                    check(!h.mutations.ramReuseEnabled)
                }
                if(scenario in setOf("core_writer","document_writer")) {
                    h.faults.worker.release();waitFor("writer callback"){main{writerDone}}
                }
                phase("revoked_or_denied_no_new_candidate")
                if(scenario=="normal_editor") {
                    main{model.useFullActiveView()}
                    waitFor("normal full workspace"){main{model.workspace?.tasks?.tasks?.size==1000}}
                    val draw=CountDownLatch(1)
                    lateinit var listener:android.view.ViewTreeObserver.OnDrawListener
                    main {
                        check(model.activeCore==null)
                        listener=android.view.ViewTreeObserver.OnDrawListener{if(model.tasks.form)draw.countDown()}
                        activity.window.decorView.viewTreeObserver.addOnDrawListener(listener)
                        model.tasks.open(checkNotNull(model.workspace?.tasks?.tasks?.firstOrNull()));model.tasks.edit()
                        check(model.tasks.form)
                    }
                    check(draw.await(5,TimeUnit.SECONDS))
                    phase("normal_editor_draw")
                    main{activity.window.decorView.viewTreeObserver.removeOnDrawListener(listener);model.tasks.cancel();model.tasks.backToTasks();model.activeArea(ActiveArea.HUB);check(model.activeCore==null)}
                }
                emit("P84_CONTRACT,$fixture,$scenario,reads=${h.observations.reads.get()},tickets=${h.observations.tickets.get()},hits=${h.observations.hitAccepted.get()},max_pending_candidates=1,full_transition_reservation=${org.pag.domain.ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES},${state()}")
            }
            }
            main{model.lock();activity.finish()};waitFor("own fixture cleanup"){h.cleanupConfirmed.get()}
            emit("P84_CASE,$fixture,$scenario,ANDROID-GREEN,joint_memory_certification=false,fixture_cleanup=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P84 PASS $fixture $scenario; production backpressure; frame/native certification BLOCKED")})
        } catch(t:Throwable) {
            emit("P84_STATE_ON_FAILURE,${handle?.outstandingState()}")
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P84 FAIL $fixture $scenario: ${t.stackTraceToString()}")})
        } finally {handle?.memory?.release();main{a?.finish()}}
    }
}
