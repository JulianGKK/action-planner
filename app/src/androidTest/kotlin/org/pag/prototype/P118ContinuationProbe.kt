package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONObject
import org.pag.domain.*
import org.pag.data.product.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Real ProductScreen/Room/SQLCipher; ONLY authentication and content are synthetic. No TalkBack. */
class P118ContinuationProbe:Instrumentation(){
 private var scenario="phase"
 private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
 private fun <T> main(w:()->T):T{val r=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{r.set(w())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return r.get()}
 private fun await(label:String,w:()->Boolean){val end=SystemClock.elapsedRealtime()+25_000;while(SystemClock.elapsedRealtime()<end){if(w())return;Thread.sleep(50)};error("P118 timeout $label")}
 private fun uiText():String {
  val root=uiAutomation.rootInActiveWindow?:return "NO_WINDOW"
  if(root.packageName?.toString()!=targetContext.packageName)return "OTHER_PACKAGE"
  val out=StringBuilder();fun add(n:android.view.accessibility.AccessibilityNodeInfo){if(!n.refresh())return;n.text?.let{out.append(it).append("|")};repeat(n.childCount){n.getChild(it)?.let(::add)}};add(root);return out.toString()
 }
 private fun visible(text:String):Boolean {
  val root=uiAutomation.rootInActiveWindow?:return false
  if(root.packageName?.toString()!=targetContext.packageName)return false
  fun find(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
   if(!n.refresh())return false
   if(n.isVisibleToUser && n.text?.toString()?.contains(text)==true)return true
   repeat(n.childCount){n.getChild(it)?.let{if(find(it))return true}};return false
  }
  return find(root)
 }
 private fun reveal(text:String):Boolean {
  if(visible(text))return true
  val root=uiAutomation.rootInActiveWindow?:return false
  if(root.packageName?.toString()!=targetContext.packageName)return false
  fun scroll(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
   if(!n.refresh())return false
   if(n.isScrollable && n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true
   repeat(n.childCount){n.getChild(it)?.let{if(scroll(it))return true}};return false
  }
  scroll(root);return visible(text)
 }
 private fun emit(kind:String,data:JSONObject=JSONObject())=sendStatus(0,Bundle().apply{putString("stream","P118 "+data.put("kind",kind).put("scenario",scenario)+"\n")})
 override fun onCreate(a:Bundle?){super.onCreate(a);scenario=a?.getString("scenario")?:"phase";start()}
 override fun onStart(){var activity:P86FullWorkspaceActivity?=null;var harness:P77ProductRuntimeHarness?=null
  try{
   check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
   val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture","p86-small").putExtra("p112Probe",true).putExtra("p116ProductGeometry",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P86FullWorkspaceActivity;activity=a
   val h=field(a,"p77Harness") as P77ProductRuntimeHarness;harness=h
   val m=field(a,"model") as ProductViewModel
   await("focus"){main{a.hasWindowFocus()}};main{m.unlock()};await("READY"){main{m.snapshot.state==SessionState.READY && m.actions.isNotEmpty()}}
   main{m.bindActiveCore(h.runtime.activeCore){a.runOnUiThread(it)}}
   fun settled(){await("settled"){main{!m.detailLoading && !m.personsLoading && !m.workspaceRefreshing}}}
   fun open(id:String){main{m.detail(id)};settled();check(main{!m.workspaceError}){"Open refused ${main{m.workspaceRefusal}}"}}
   if(scenario=="budget"){
    open("p11-active");main{m.activeArea(ActiveArea.TASKS)};settled()
    val scope=main{field(m,"pageAccess") as WorkspaceAccess}
    val before=main{
     val state=h.outstandingState()
     val retained=Regex("localRetained=([0-9]+)").find(state)!!.groupValues[1].toLong()
     check(h.p110PadUiBoundary(scope,ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES-retained-300,0));state
    }
    val padded=h.outstandingState();val reads=h.observations.reads.get()
    h.memory.enabled=true
    main{m.back();m.detail("p11-active")};settled()
    check(main{m.workspaceError && m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED})
    check(h.observations.reads.get()==reads && h.memory.payloadRows.get()==0)
    check(h.observations.workspaceMaterializations.get()==0)
    emit("BUDGET_PASS",JSONObject().put("before",before).put("padded",padded).put("after",h.outstandingState()).put("additional_reader_calls",0).put("additional_payload_rows",0).put("synthetic_ledger_padding",true))
   }else if(scenario=="phase"){
    main{m.createScreen();m.save(NewAction("Synthetic P118 journey","Synthetic phase fixture",0,1000))}
    await("created"){main{!m.saving && m.actions.any{it.title=="Synthetic P118 journey"}}}
    val id=main{m.actions.single{it.title=="Synthetic P118 journey"}.id};open(id)
    main{m.requestAdvance();m.confirmAdvance()};await("PREPARATION"){main{!m.saving && m.selected?.status==ActionStatus.PREPARATION}};settled()
    check(main{m.workspace!=null && !m.workspaceError});val old=main{m.workspace!!.access!!}
    main{m.requestAdvance();m.confirmAdvance()};await("ACTIVE"){main{!m.saving && m.selected?.status==ActionStatus.ACTIVE}};settled()
    check(main{m.compactHubPresentation!=null && !m.workspaceError})
    check(!main{old.publish(id,ActionStatus.PREPARATION,m.snapshot.generation){error("old phase publication")}})
    for(area in listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB)){
     main{m.activeArea(area)};settled();check(main{!m.workspaceError && m.activeArea==area})
     emit("ROUTE",JSONObject().put("area",area.name).put("hub_present",main{m.compactHubPresentation!=null}).put("hub_value",main{m.compactHubPresentation?.value(0,0)}).put("bounded",main{m.boundedPage}).put("refusal",main{m.workspaceRefusal?.name}))
     await("visible ACTIVE $area"){reveal(when(area){ActiveArea.HUB->"Personen öffnen";ActiveArea.FUNCTIONS->"Personen vor Ort";else->"Einträge insgesamt"})}
    }
    check(h.observations.workspaceMaterializations.get()==1)
    emit("A_PASS",JSONObject().put("ledger",h.outstandingState()).put("reader_calls",h.observations.reads.get()))
   }else{
    open("p11-active");val hub=main{m.compactHubPresentation!!};val header=main{hub.navigation(ActiveArea.TASKS)!!}
    main{m.activeArea(ActiveArea.TASKS)};settled()
    var row:OriginalButtonControl?=null;await("row"){main{row=m.originalRowControl(0);row!=null}}
    main{row!!.click()};await("task detail"){main{m.tasks.selected!=null && !m.detailLoading}}
    val oldAccess=main{m.workspace!!.access!!};val oldGeneration=main{m.snapshot.generation}
    main{m.tasks.edit()};val oldForm=main{m.inputScreenKey() as OriginalUiFence}
    var calls=0
    val writer=main{check(oldForm.reserve(InputControlCost.click));OriginalInputClick(oldForm,FormInput{},m.uiPresentationRevision){calls++;m.tasks.save()}}
    main{m.back()};open("p11-preparation")
    await("PREPARATION content"){visible("Vorbereitung im Überblick")}
    val original=main{m.workspace!!};check(original.status==ActionStatus.PREPARATION && original.operations!=null && original.tasks!=null)
    for(area in listOf(PreparationArea.PERSONS,PreparationArea.ROLES,PreparationArea.TASKS,PreparationArea.OVERVIEW)){
     main{m.preparationArea(area)};settled();check(main{!m.workspaceError && m.workspace!=null && m.preparationArea==area})
    }
    main{header();row!!.click();writer();check(calls==0 && !oldForm.live() && m.selected?.id=="p11-preparation" && !m.tasks.busy);check(hub.value(0,0)==null)}
    check(h.observations.workspaceMaterializations.get()==0)
    check(!main{oldAccess.publish("p11-active",ActionStatus.ACTIVE,oldGeneration){error("cross-action publication")}})
    emit("B_PASS",JSONObject().put("people",original.persons.size).put("tasks",original.tasks!!.tasks.size).put("ledger",h.outstandingState()).put("old_writer_calls",calls))
    main{m.back();m.detail("p11-active")};settled()
    val refused=main{m.workspaceError}
    if(refused)check(main{m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED}) else check(main{m.compactHubPresentation!=null})
    main{header();row!!.click();writer();check(calls==0 && !oldForm.live());check(hub.value(0,0)==null)}
    check(!main{oldAccess.publish("p11-active",ActionStatus.ACTIVE,oldGeneration){error("ABA old publication")}})
    emit(if(refused)"ABA_BUDGET_SAFE" else "ABA_PASS",JSONObject().put("ledger",h.outstandingState()).put("reader_calls",h.observations.reads.get()))
   }
   emit("PASS",JSONObject().put("synthetic_authentication",true).put("full_workspaces",h.observations.workspaceMaterializations.get()).put("ledger",h.outstandingState()))
   check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
   main{m.lock();a.finish()};await("cleanup"){h.cleanupConfirmed.get()};activity=null;harness=null
   finish(Activity.RESULT_OK,Bundle().apply{putString("result","P118 CONTINUATION PASS")})
  }catch(t:Throwable){emit("FAIL",JSONObject().put("error",t.stackTraceToString()).put("ledger",harness?.outstandingState()).put("ui",uiText()));runCatching{main{activity?.finish()};harness?.let{h->await("cleanup"){h.cleanupConfirmed.get()}}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P118 CONTINUATION FAIL")})}
 }
}
