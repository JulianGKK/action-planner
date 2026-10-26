package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject
import org.pag.data.product.*
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Calibration of existing production paths, not a fallback, capacity policy or new owner. */
class P111CapacityProbe:Instrumentation(){
    private lateinit var args:Bundle
    private var sampler:P111MemorySampler?=null
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val until=SystemClock.elapsedRealtime()+60_000;while(SystemClock.elapsedRealtime()<until){if(work())return;Thread.sleep(10)};error("P111 timeout $label")}
    private fun emit(kind:String,values:JSONObject=JSONObject()){
        sampler?.mark(kind)
        values.put("kind",kind).put("scenario_id",args.getString("p111Id")).put("elapsed_ns",SystemClock.elapsedRealtimeNanos())
            .put("utc_ms",System.currentTimeMillis()).put("apk_sha256",args.getString("p111ApkHash"))
        sendStatus(0,Bundle().apply{putString("stream","P111_EVENT "+values+"\n")})
    }
    private fun visible(text:String):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(n.isVisibleToUser && n.text?.toString()?.contains(text)==true)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}};return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    override fun onException(obj:Any?,e:Throwable):Boolean{emit("ASYNC_FAILURE",JSONObject().put("error",e.stackTraceToString()));return super.onException(obj,e)}
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);args=checkNotNull(arguments);sampler=P111MemorySampler.start(this,args);start()}
    override fun onStart(){var activity:P86FullWorkspaceActivity?=null;var harness:P77ProductRuntimeHarness?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val profile=checkNotNull(args.getString("fixture"));val spec=checkNotNull(P111FixtureSpec.find(profile))
            val path=args.getString("path")?:"core";check(path in setOf("core","full"))
            emit("FIXTURE_BEGIN",JSONObject().put("fixture",profile).put("people",spec.people).put("tasks",spec.tasks).put("roles",spec.roles).put("events",spec.events).put("text_factor",spec.textFactor).put("filled_and_empty_plans",spec.filledPlans))
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity")
                .putExtra("p83Fixture",profile).putExtra("p111Probe",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P86FullWorkspaceActivity
            activity=a;val h=field(a,"p77Harness") as P77ProductRuntimeHarness;harness=h
            val m=field(a,"model") as ProductViewModel
            sampler?.bind(h,a,m);check(!h.runtime.activeCore.reuseEnabled)
            await("focus"){main{a.hasWindowFocus()}};main{m.unlock()}
            await("READY"){main{m.snapshot.state==SessionState.READY && m.actions.isNotEmpty()}}
            emit("READY_BASELINE",JSONObject().put("path",path).put("fixture",profile).put("session",h.runtime.session.snapshot().state.name).put("ledger",h.outstandingState()))
            Thread.sleep(1200)
            if(path=="core"){
                val d=h.memory;d.begin(true);d.captureConfiguration=true
                val initial=args.getString("initialArea")?.let(ActiveArea::valueOf)?:ActiveArea.HUB
                check(initial in setOf(ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.TASKS,ActiveArea.FUNCTIONS))
                // TEST initial viewport only, selected BEFORE the first payload publication.
                // No navigation hit/reuse flag is enabled and no data/authority is replaced.
                val port=if(initial==ActiveArea.HUB)h.runtime.activeCore else object:ActiveCoreLoadPort{
                    override val reuseEnabled=false
                    override fun cancelPending()=h.runtime.activeCore.cancelPending()
                    override fun fullViewStarting()=h.runtime.activeCore.fullViewStarting()
                    override fun loadOwned(actionId:String,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit)=
                        h.runtime.activeCore.loadOwned(actionId){result,life->
                            if(result is ActiveCoreResult.Value){
                                @Suppress("UNCHECKED_CAST")
                                val area=field(m,"activeArea\$delegate") as androidx.compose.runtime.MutableState<ActiveArea>
                                area.value=initial
                            }
                            callback(result,life)
                        }
                }
                main{m.bindActiveCore(port){a.runOnUiThread(it)};m.detail("p11-active")}
                emit("CORE_REQUEST")
                await("row or typed rejection"){d.rowReached.count==0L || d.failure!="none" || main{!m.personsLoading && m.workspaceError}}
                if(d.rowReached.count==0L){
                    emit("CORE_MATERIALIZING_PARKED",JSONObject().put("native",d.nativeState()).put("retained",d.retained).put("local_transition",d.transition))
                    Thread.sleep(1200);d.rowRelease.countDown()
                    await("candidate or rejection"){d.candidateReached.count==0L || d.failure!="none" || main{!m.personsLoading && m.workspaceError}}
                    if(d.candidateReached.count==0L){emit("CORE_CANDIDATE_PARKED",JSONObject().put("native",d.nativeState()));Thread.sleep(1200);d.candidateRelease.countDown()}
                }
                await("core final"){main{!m.personsLoading}}
                val slot=main{(field(m,"coreSlot") as AtomicReference<*>).get()}
                if(slot!=null){
                    val value=field(slot,"value") as ActiveCoreResult.Value // Deliberate TEST alias, retained across lock.
                    val life=field(slot,"lifetime") as ActiveCoreLifetime
                    check(main{life.isCurrent()})
                    check(value.core.people.overall.total==spec.people.toLong() && value.core.tasks.overall.open==spec.tasks.toLong())
                    check(value.core.people.items.size==minOf(32,spec.people) && value.core.tasks.items.size==minOf(32,spec.tasks) && value.core.functions.items.size==spec.roles)
                    val visibleLabel=when(initial){
                        ActiveArea.PERSONS->value.core.people.items.first().pseudonym
                        ActiveArea.TASKS->value.core.tasks.items.first().title
                        ActiveArea.FUNCTIONS->value.core.functions.items.first().role.name
                        else->"${spec.people} insgesamt"
                    }
                    await("original initial compact viewport visible"){visible(visibleLabel)}
                    emit("INITIAL_COMPACT_VIEWPORT",JSONObject().put("area",initial.name).put("test_selection_before_publication",true).put("normal_navigation_activated",false).put("reuse_enabled",false))
                    emit("CORE_VISIBLE",JSONObject().put("people_total",value.core.people.overall.total).put("task_total",value.core.tasks.overall.open)
                        .put("people_cards",value.core.people.items.size).put("task_cards",value.core.tasks.items.size).put("roles",value.core.functions.items.size)
                        .put("retained",value.retainedCharge).put("local_transition",value.transitionCharge).put("payload_rows",d.payloadRows.get()).put("native_cleanup",d.finish())
                        .put("reuse_enabled",false).put("ram_reuse_enabled",false).put("full_materializations",h.observations.workspaceMaterializations.get()))
                    Thread.sleep(1500)
                    main{m.useFullActiveView()}
                    await("exposed fullview denied"){main{!m.personsLoading && m.workspaceError}}
                    check(main{m.workspaceRefusal==WorkspaceFailure.OWNER_UNCONFIRMED && m.workspace==null})
                    check(h.observations.workspaceMaterializations.get()==0 && h.observations.reads.get()==1)
                    emit("EXPOSED_READMISSION_DENIED",JSONObject().put("ledger",h.outstandingState()))
                    main{m.lock()};check(!main{life.isCurrent()})
                }else{
                    if(spec.id=="p111-single-long-person" && d.failure!=BoundedActiveStatus.BUDGET_REFUSED.name){
                        check(h.runtime.session.snapshot().state==SessionState.BLOCKED)
                        check(d.candidateReached.count==1L && d.retained>0 && d.transition>0)
                        val native=runCatching{d.finish()}.getOrElse{"UNCONFIRMED:"+it.javaClass.simpleName+":"+d.nativeState()}
                        emit("NATIVE_ROW_CAPACITY_BLOCKED",JSONObject().put("reason",d.failure).put("detail",d.failureDetail)
                            .put("retained_receipt",d.retained).put("transition_receipt",d.transition).put("payload_rows",d.payloadRows.get())
                            .put("native_cleanup",native).put("session",h.runtime.session.snapshot().state.name).put("capacity","BLOCKED").put("silent_empty",false))
                    }else{
                        check(d.failure==BoundedActiveStatus.BUDGET_REFUSED.name && d.payloadRows.get()==0 && h.observations.workspaceMaterializations.get()==0)
                        emit("CORE_REFUSED_BEFORE_PAYLOAD",JSONObject().put("reason",d.failure).put("requested_retained",d.retained).put("requested_transition",d.transition).put("decision",d.decision).put("payload_rows",d.payloadRows.get()).put("native_cleanup",d.finish()).put("ledger",h.outstandingState()))
                    }
                    main{m.lock()}
                }
            }else{
                main{m.detail("p11-active")};emit("FULL_REQUEST")
                await("full final"){main{!m.detailLoading && !m.personsLoading && !m.workspaceRefreshing && (m.workspace!=null || m.workspaceError || m.snapshot.state!=SessionState.READY)}}
                val held=main{m.workspace} // TEST alias remains intentionally held across lock.
                if(held==null && spec.id=="p111-single-long-person" && h.runtime.session.snapshot().state==SessionState.BLOCKED){
                    emit("NATIVE_ROW_CAPACITY_BLOCKED",JSONObject().put("session","BLOCKED").put("capacity","BLOCKED").put("materializations",h.observations.workspaceMaterializations.get()).put("ledger",h.outstandingState()).put("silent_empty",false))
                }else if(held==null){
                    check(main{m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED})
                    check(h.observations.workspaceMaterializations.get()==0)
                    await("visible fullview refusal"){visible("Die Vollansicht überschreitet das zulässige Speicherbudget.")}
                    emit("FULL_REFUSED_BEFORE_PAYLOAD",JSONObject().put("materializations",0).put("ledger",h.outstandingState()).put("false_empty",false))
                }else{
                    check(held.persons.size==spec.people && held.operations!!.roles.size==spec.roles && held.tasks!!.tasks.size==spec.tasks)
                    check(held.tasks!!.events.isEmpty() && !held.tasks!!.eventsComplete)
                    await("full action visible"){visible("Synthetic action")}
                    emit("FULL_VISIBLE",JSONObject().put("people",held.persons.size).put("tasks",held.tasks!!.tasks.size).put("eager_events",0)
                        .put("history_complete",false).put("materializations",h.observations.workspaceMaterializations.get()).put("ledger",h.outstandingState()))
                    Thread.sleep(1500)
                    main{m.activeArea(ActiveArea.TASKS)}
                    val task=held.tasks!!.tasks.minBy{it.id}
                    main{m.tasks.open(task);m.tasks.loadHistory(true)}
                    await("first history page"){main{!m.tasks.historyBusy && m.tasks.history!=null}}
                    val first=main{m.tasks.history!!}
                    check(first.events.size<=32)
                    emit("BOUNDED_HISTORY",JSONObject().put("rows",first.events.size).put("stored_events",spec.events).put("old_snapshot_held",true).put("ledger",h.outstandingState()))
                    if(first.next!=null){
                        main{m.tasks.loadHistory()}
                        await("additional bounded history"){main{!m.tasks.historyBusy && m.tasks.history!=null && m.tasks.history!==first}}
                        check(main{m.tasks.history!!.events.size<=32})
                        emit("BOUNDED_HISTORY_NEXT",JSONObject().put("rows",main{m.tasks.history!!.events.size}).put("old_page_held",true).put("ledger",h.outstandingState()))
                    }
                }
                Thread.sleep(1500);main{m.lock()}
                check(h.observations.workspaceMaterializations.get()<=1)
            }
            emit("LOCKED_WITH_TEST_ALIASES",JSONObject().put("session",h.runtime.session.snapshot().state.name).put("ledger",h.outstandingState()))
            Thread.sleep(2000);main{a.finish()};await("fixture cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("PASS",JSONObject().put("fixture",profile).put("path",path).put("fixture_cleanup",true).put("physical_bound",false))
            sampler?.close();sampler=null
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P111 PASS $profile $path")})
        }catch(t:Throwable){emit("FAILURE",JSONObject().put("error",t.stackTraceToString()));runCatching{harness?.memory?.release();main{activity?.finish()};harness?.let{h->await("failure cleanup"){h.cleanupConfirmed.get()}}};sampler?.close();sampler=null;finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P111 FAIL")})}
    }
}
