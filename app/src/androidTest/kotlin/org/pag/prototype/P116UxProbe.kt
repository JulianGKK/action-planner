package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import org.pag.data.product.*
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Actual isolated ProductScreen + Room/SQLCipher. Synthetic P77 authentication/data. */
class P116UxProbe:Instrumentation(){
 private var flow="person";private var manual=false;private var font=1f;private var mode="active";private var profile="p111-small"
 private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
 private fun <T> main(w:()->T):T{val r=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{r.set(w())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return r.get()}
 private fun await(s:String,w:()->Boolean){val end=SystemClock.elapsedRealtime()+25_000;while(SystemClock.elapsedRealtime()<end){if(w())return;Thread.sleep(40)};error("timeout $s")}
 private fun emit(k:String,d:JSONObject=JSONObject())=sendStatus(0,Bundle().apply{putString("stream","P116 "+d.put("kind",k).put("mode",mode).put("font",font)+"\n")})
 private fun nodes():List<AccessibilityNodeInfo>{val r=uiAutomation.rootInActiveWindow?:return emptyList();if(r.packageName?.toString()!=targetContext.packageName)return emptyList();val out=mutableListOf<AccessibilityNodeInfo>();fun visit(n:AccessibilityNodeInfo){if(!n.refresh())return;out+=n;repeat(n.childCount){n.getChild(it)?.let(::visit)}};visit(r);return out}
 private fun shown(n:AccessibilityNodeInfo):Boolean {
  if(!n.isVisibleToUser)return false
  val bounds=Rect().also(n::getBoundsInScreen);if(bounds.isEmpty)return false
  var parent=n.parent
  while(parent!=null){val p=parent
   if(p.isScrollable || p.parent==null){val clip=Rect().also(p::getBoundsInScreen);if(!bounds.intersect(clip))return false}
   parent=p.parent
  };return !bounds.isEmpty
 }
 private fun visible(s:String)=nodes().any{shown(it) && it.text?.toString()?.contains(s)==true}
 private fun scroll(back:Boolean=false):Boolean=nodes().firstOrNull{it.isScrollable && it.performAction(if(back)AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}!=null
 private fun reveal(s:String):Boolean{repeat(24){attempt->
  if(visible(s))return true
  val target=nodes().firstOrNull{it.text?.toString()?.contains(s)==true}
  if(target!=null)target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
  else nodes().filter{it.isScrollable}.forEach{it.performAction(if(attempt<12)AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)}
  Thread.sleep(120)
 };return visible(s)}
 private fun click(s:String):Boolean{val n=nodes().firstOrNull{shown(it) && (it.text?.toString()==s || it.text?.toString()?.split("\n")?.contains(s)==true)}?:return false;var p:AccessibilityNodeInfo?=n;while(p!=null){if(p.isClickable && p.isEnabled && p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;p=p.parent};return false}
 private fun enterText(value:String,index:Int=0):Boolean=nodes().filter{it.isEditable}.getOrNull(index)?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})==true
 private fun enterLabeled(label:String,value:String):Boolean {
  if(!reveal(label))return false
  fun contains(n:AccessibilityNodeInfo):Boolean {if(n.text?.toString()?.contains(label)==true)return true;repeat(n.childCount){n.getChild(it)?.let{c->if(contains(c))return true}};return false}
  return nodes().firstOrNull{it.isEditable && contains(it)}?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)})==true
 }
 private fun geometry(){val a=org.json.JSONArray();nodes().filter{it.isScrollable || it.isClickable || it.text!=null}.forEach{n->val r=Rect().also(n::getBoundsInScreen);a.put(JSONObject().put("text",n.text?.toString()).put("class",n.className).put("bounds",r.toShortString()).put("visible",n.isVisibleToUser).put("scroll",n.isScrollable).put("enabled",n.isEnabled))};emit("GEOMETRY",JSONObject().put("nodes",a))}
 override fun onCreate(a:Bundle?){super.onCreate(a);font=a?.getString("font")?.toFloat()?:1f;mode=a?.getString("mode")?:"active";manual=a?.getString("manual")=="true";flow=a?.getString("flow")?:"person";profile=a?.getString("profile")?:"p111-small";start()}
 override fun onStart(){var a:P86FullWorkspaceActivity?=null;var h:P77ProductRuntimeHarness?=null
 try{
  check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
  val act=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture",profile).putExtra("p112Probe",true).putExtra("p116Font",font).putExtra("p116ProductGeometry",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as P86FullWorkspaceActivity;a=act
  val harness=field(act,"p77Harness") as P77ProductRuntimeHarness;h=harness;val m=field(act,"model") as ProductViewModel
  await("focus"){main{act.hasWindowFocus()}};main{m.unlock()};await("READY"){main{m.snapshot.state==SessionState.READY && m.actions.isNotEmpty()}}
  main{m.bindActiveCore(harness.runtime.activeCore){act.runOnUiThread(it)}}
  emit("READY",JSONObject().put("synthetic_authentication",true).put("profile",profile))
  if(mode=="journey"){
   main{m.createScreen()};await("action form"){visible("Titel")};emit("ACTION_FORM_VISIBLE")
   main{m.save(NewAction("Synthetic P116 journey","Synthetic fixture",0,1000))};await("created"){main{!m.saving && !m.creating && m.actions.any{it.title=="Synthetic P116 journey"}}}
   val id=main{m.actions.single{it.title=="Synthetic P116 journey"}.id};main{m.detail(id)};await("draft"){main{!m.detailLoading && m.selected?.status==ActionStatus.DRAFT}};emit("DRAFT_VISIBLE")
   main{m.requestAdvance();m.confirmAdvance()};await("preparation"){main{!m.saving && m.selected?.status==ActionStatus.PREPARATION}};await("preparation loaded"){main{!m.personsLoading && !m.workspaceRefreshing}}
   emit("PREPARATION",JSONObject().put("workspace",main{m.workspace!=null}).put("ledger",harness.outstandingState()).put("core_reads",harness.observations.reads.get()).put("refusal",main{m.workspaceRefusal?.name}));geometry()
   main{m.requestAdvance();m.confirmAdvance()};await("active"){main{!m.saving && m.selected?.status==ActionStatus.ACTIVE}};await("active settled"){main{!m.personsLoading && !m.workspaceRefreshing}}
   emit("ACTIVE",JSONObject().put("workspace",main{m.workspace!=null}).put("ledger",harness.outstandingState()).put("core_reads",harness.observations.reads.get()).put("refusal",main{m.workspaceRefusal?.name}));geometry()
   check(main{!m.workspaceError}){"ACTIVE workspace blocked: ${main{m.workspaceRefusal}}"}
   main{m.requestAdvance();m.confirmAdvance()};await("completed"){main{!m.saving && m.selected?.status==ActionStatus.COMPLETED}};emit("COMPLETED")
  }else{
   main{m.detail("p11-${if(mode in setOf("fullview","documents","action-switch","reauth"))"active" else mode}")};await("detail"){main{!m.detailLoading && !m.personsLoading && !m.workspaceRefreshing}}
   await("drawn initial surface"){nodes().any{it.isVisibleToUser && it.text!=null}};geometry()
   if(manual){
    getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    val done=java.io.File(targetContext.cacheDir,"p116-manual-finish");done.delete()
    emit("MANUAL_READY",JSONObject().put("session_limits_unchanged",true))
    val end=SystemClock.elapsedRealtime()+170_000
    while(!done.exists() && SystemClock.elapsedRealtime()<end)Thread.sleep(250)
    emit("MANUAL_END",JSONObject().put("explicit_finish",done.exists()).put("session",main{m.snapshot.state.name}));done.delete()
   }else if(mode=="fullview"){
    check(reveal("Alle anzeigen und bearbeiten") && click("Alle anzeigen und bearbeiten")){"UNREACHABLE fullview entry"}
    await("fullview settled"){main{!m.personsLoading && !m.workspaceRefreshing}}
    geometry();emit("FULLVIEW_RESULT",JSONObject().put("error",main{m.workspaceError}).put("refusal",main{m.workspaceRefusal?.name}).put("ledger",harness.outstandingState()))
    check(main{!m.workspaceError}){"Fullview refused ${main{m.workspaceRefusal}}"}
   }else if(mode=="documents"){
    main{m.openDocuments()};await("documents"){main{m.documents.available && !m.documents.loading}}
    check(reveal("Dokumente"));geometry();emit("DOCUMENT_LIST_VISIBLE")
   }else if(mode=="action-switch" || mode=="reauth"){
    if(mode=="reauth"){main{m.lock();m.unlock()};await("reauth"){main{m.snapshot.state==SessionState.READY}}}else main{m.back()}
    main{m.detail(if(mode=="reauth")"p11-active" else "p11-preparation")};await("new action settled"){main{!m.detailLoading && !m.personsLoading && !m.workspaceRefreshing}}
    geometry();emit("REOPEN_RESULT",JSONObject().put("error",main{m.workspaceError}).put("refusal",main{m.workspaceRefusal?.name}).put("ledger",harness.outstandingState()))
    check(main{!m.workspaceError}){"New action/reauth view refused ${main{m.workspaceRefusal}}"}
   }else if(mode=="preparation"){
    val area=when(flow){"person"->PreparationArea.PERSONS;"role"->PreparationArea.ROLES;"task"->PreparationArea.TASKS;"material"->PreparationArea.MATERIAL;else->PreparationArea.COSTS}
    check(reveal(area.label) && click(area.label)){"UNREACHABLE preparation ${area.label}"};await("preparation section"){main{m.preparationArea==area && !m.workspaceRefreshing && !m.personsLoading}}
    val create=when(flow){"person"->"Person hinzufügen";"role"->"Rolle anlegen";"task"->"+ Aufgabe";"material"->"+ Material hinzufügen";else->"+ Kosten eintragen"}
    check(reveal(create) && click(create)){"UNREACHABLE create $flow"};val fieldLabel=when(flow){"person"->"Pseudonym";"role"->"Rollenname";"task"->"Titel";else->"Bezeichnung"}
    check(reveal(fieldLabel)){"UNREACHABLE input $flow"};await("input"){nodes().any{it.isEditable}}
    val title="Synthetic P116 $flow";check(enterText(title));if(flow=="expense"){
     check(enterLabeled("Gesamtbetrag","12,50"))
     check(reveal("+ freie Person / Bezeichnung") && click("+ freie Person / Bezeichnung"))
     check(enterLabeled("Bezahlt von","Synthetic payer"));check(enterLabeled("Betrag · Zahlung","12,50"))
    }
    check(reveal("Speichern") && click("Speichern")){"UNREACHABLE save $flow"}
    await("save $flow"){main{when(flow){"person"->!m.personSaving && !m.personForm;"role"->!m.operations.busy && m.operations.createSurface==null;"task"->!m.tasks.busy && !m.tasks.form;"material"->!m.material.busy && !m.material.form;else->!m.expenses.busy && !m.expenses.form}}}
    emit("UI_SAVE_RESULT",JSONObject().put("flow",flow).put("ledger",harness.outstandingState()).put("commit",main{when(flow){"person"->m.personCommitConfirmed;"role"->m.operations.commitConfirmed;"task"->m.tasks.commitConfirmed;"material"->m.material.commitConfirmed;else->m.expenses.commitConfirmed}}).put("refusal",main{m.workspaceRefusal?.name}))
    check(reveal(title)){"Committed $flow value not reachable"};emit("UI_CREATE_VISIBLE",JSONObject().put("flow",flow));geometry()
   }else if(mode=="active"){
    for(area in listOf(ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS,ActiveArea.HUB)){
     check(reveal(area.label) && click(area.label)){"UNREACHABLE navigation ${area.label}"};await("route $area"){main{m.activeArea==area}}
     val expected=when(area){ActiveArea.PERSONS,ActiveArea.TASKS->"Einträge insgesamt";ActiveArea.FUNCTIONS->"Personen vor Ort";else->"Personen öffnen"}
     Thread.sleep(200);geometry();check(reveal(expected)){"UNREACHABLE content $expected"};emit("ROUTE_VISIBLE",JSONObject().put("area",area.name))
    }
   }else if(mode=="completed" && flow!="person"){
    val entry=when(flow){"protocol"->"Protokoll ansehen / nachbereiten";"memory"->"Gedächtnisprotokolle";"material"->"Material ansehen";"expense"->"Kosten verwalten";else->"Dokumente verwalten"}
    check(reveal(entry) && click(entry)){"UNREACHABLE completed $flow entry"}
    val expected=when(flow){"protocol"->"Protokoll nachbereiten";"memory"->"Gedächtnisprotokolle";"material"->"Material";"expense"->"Kosten";else->"Dokumente"}
    await("completed child load"){main{!m.workspaceRefreshing && !m.personsLoading && !m.protocol.loading && !m.memoryReports.loading && !m.material.loading && !m.expenses.loading && !m.documents.loading}}
    check(main{!m.workspaceError}){"Completed child refused ${main{m.workspaceRefusal}}"};check(reveal(expected));geometry();emit("COMPLETED_CHILD_VISIBLE",JSONObject().put("flow",flow))
    check(reveal("‹ Zur abgeschlossenen Aktion") && click("‹ Zur abgeschlossenen Aktion")){"UNREACHABLE completed back $flow"};await("completed back"){visible("Protokoll ansehen / nachbereiten")};emit("COMPLETED_BACK_VISIBLE")
   }else{check(main{!m.workspaceError});emit("DETAIL_VISIBLE")}
  }
  emit("OWNER_COUNTERS",JSONObject().put("core_reads",harness.observations.reads.get()).put("full_workspaces",harness.observations.workspaceMaterializations.get()).put("ledger",harness.outstandingState()))
  check(!harness.runtime.activeCore.reuseEnabled && !harness.mutations.ramReuseEnabled)
  main{m.lock();act.finish()};await("cleanup"){harness.cleanupConfirmed.get()};a=null;emit(if(manual)"MANUAL_UNASSESSED" else "PASS");finish(Activity.RESULT_OK,Bundle().apply{putString("result",if(manual)"P116 MANUAL UNASSESSED" else "P116 PASS")})
 }catch(t:Throwable){runCatching{geometry();h?.let{emit("FAIL_LEDGER",JSONObject().put("ledger",it.outstandingState()))}};emit("FAIL",JSONObject().put("error",t.stackTraceToString()));runCatching{main{a?.finish()};h?.let{hh->await("cleanup"){hh.cleanupConfirmed.get()}}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P116 FAIL")})}
 }
}
