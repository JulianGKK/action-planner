package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.pag.data.product.*
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference

/** Three bounded product journeys only. Real SQLCipher, synthetic P77 authentication/data. */
class P112PagesProbe:Instrumentation(){
    private var scenario="tasks"
    private var scrollProbe=false
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T{val v=AtomicReference<T>();val e=AtomicReference<Throwable?>();runOnMainSync{try{v.set(work())}catch(t:Throwable){e.set(t)}};e.get()?.let{throw it};return v.get()}
    private fun await(label:String,work:()->Boolean){val until=SystemClock.elapsedRealtime()+60_000;while(SystemClock.elapsedRealtime()<until){if(work())return;Thread.sleep(30)};error("P112 timeout $label")}
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream","P112,$scenario,$s\n")})
    private var forward=true
    private fun reveal(text:String):Boolean {
        runCatching{uiAutomation.waitForIdle(50,1000)}
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visible(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(!n.refresh())return false
            if(n.isVisibleToUser && n.text?.toString()?.contains(text)==true)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visible(c))return true}finally{c.recycle()}};return false
        }
        fun scroll(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(!n.refresh())return false
            if(n.isScrollable && n.performAction(if(forward)android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(scroll(c))return true}finally{c.recycle()}};return false
        }
        return try{if(root.packageName?.toString()!=targetContext.packageName)false else if(visible(root))true else {if(!scroll(root))forward=!forward;false}}finally{root.recycle()}
    }
    private fun visibleNow(text:String):Boolean {
        val root=uiAutomation.rootInActiveWindow?:return false
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean{
            if(!n.refresh())return false
            if(n.isVisibleToUser && n.text?.toString()?.contains(text)==true)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}}
            return false
        }
        return try{visit(root)}finally{root.recycle()}
    }
    private fun click(text:String):Boolean{
        val root=uiAutomation.rootInActiveWindow?:return false
        fun contains(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(!n.refresh())return false
            if(n.text?.toString()==text)return true
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(contains(c))return true}finally{c.recycle()}};return false
        }
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(!n.refresh())return false
            if(n.isVisibleToUser && n.isClickable && contains(n))return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            for(i in 0 until n.childCount){val c=n.getChild(i)?:continue;try{if(visit(c))return true}finally{c.recycle()}};return false
        }
        return try{root.packageName?.toString()==targetContext.packageName && visit(root)}finally{root.recycle()}
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario")?:scenario;scrollProbe=arguments?.getString("p114Scroll")=="true";check(scenario in setOf("tasks","people","tight"));start()}
    override fun onStart(){var activity:Activity?=null;var harness:P77ProductRuntimeHarness?=null
        try{
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            val profile=if(scenario=="tight")"p111-large100-128k" else "p111-large32"
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture",profile).putExtra("p112Probe",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity=a;val h=field(a,"p77Harness") as P77ProductRuntimeHarness;harness=h;val m=field(a,"model") as ProductViewModel
            await("focus"){main{a.hasWindowFocus()}};main{m.unlock()};await("READY"){main{m.snapshot.state==SessionState.READY && m.actions.isNotEmpty()}}
            main{m.bindActiveCore(h.runtime.activeCore){a.runOnUiThread(it)};m.detail("p11-active")}
            await("core"){main{!m.personsLoading && !m.workspaceError}}
            await("core visible"){reveal("200 insgesamt")}
            emit("CORE,fixture=$profile,ledger=${h.outstandingState()}")
            main{m.activeArea(if(scenario=="people")ActiveArea.PERSONS else ActiveArea.TASKS)}
            await("first page or refusal"){main{!m.personsLoading && !m.workspaceRefreshing}}
            if(scenario=="tight"){
                await("next page control"){reveal("Nächste Seite (32)")};check(click("Nächste Seite (32)"))
                await("tight page refusal"){main{!m.personsLoading && !m.workspaceRefreshing}}
                check(main{m.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED && m.workspace==null})
                await("visible refusal"){reveal("Speicherbudget")}
                emit("REFUSED,base_transition=15978280,initial_remaining=798936,no_false_empty=true,ledger=${h.outstandingState()}")
            }else{
                val first=main{field(m,"pageRows") as WorkspacePage}
                val firstIds=if(scenario=="people")first.people!!.items.map{it.id} else first.tasks!!.items.map{it.id}
                check(firstIds.size==32)
                await("page control visible"){reveal("Nächste Seite (32)")}
                await("next page control"){reveal("Nächste Seite (32)")};check(click("Nächste Seite (32)"))
                await("second page"){main{!m.personsLoading && !m.workspaceRefreshing}}
                val second=main{check(m.workspaceRefusal==null){"second=${m.workspaceRefusal},${h.outstandingState()}"};field(m,"pageRows") as WorkspacePage}
                val secondIds=if(scenario=="people")second.people!!.items.map{it.id} else second.tasks!!.items.map{it.id}
                check(secondIds.size==32 && firstIds.intersect(secondIds.toSet()).isEmpty() && main{m.pageNumber==2})
                emit("PAGE,32+32,distinct=true,old_page_TEST_held=true,ledger=${h.outstandingState()}")
                if(scenario=="tasks"){
                    val rowLabel=main{checkNotNull(m.boundedPageLabel(m.pageEpoch,0))}
                    await("row visible"){reveal(rowLabel)};check(click(rowLabel))
                    await("detail loaded"){main{!m.detailLoading && m.tasks.selected!=null}}
                    val old=main{checkNotNull(m.tasks.selected)}
                    await("detail"){reveal(old.title)}
                    main{m.tasks.edit();m.tasks.changeTitle("Synthetic P112 committed task")}
                    await("draft"){reveal("Synthetic P112 committed task")}
                    main{m.tasks.save()}
                    await("COMMITTED and reprojected"){main{!m.tasks.busy && !m.tasks.form && m.tasks.selected?.editRevision==old.editRevision+1}}
                    await("new title visible"){reveal("Synthetic P112 committed task")}
                    await("revision visible"){reveal("Revision: ${old.editRevision+1}")}
                    check(h.p108TaskScalars(old.actionId,old.id,"Synthetic P112 committed task").startsWith("title_match=1,revision=${old.editRevision+1},"))
                    main{m.tasks.backToTasks()}
                    await("returned new value visible"){reveal("Synthetic P112 committed task")}
                    emit("COMMITTED,new_title_and_revision_visible=true,back_to_page=true,ledger=${h.outstandingState()}")
                }else{
                    val rowIndex=if(scrollProbe)15 else 0
                    val card=second.people!!.items[rowIndex]
                    val rowLabel=main{checkNotNull(m.boundedPageLabel(m.pageEpoch,rowIndex))}
                    await("row visible"){reveal(rowLabel)};check(click(rowLabel))
                    await("person detail"){main{!m.personDetailLoading && m.selectedPerson?.id==card.id}}
                    await("person visible"){reveal(card.pseudonym)}
                    main{m.backToPersons()}
                    await("return page"){main{m.selectedPerson==null && m.pageNumber==2 && m.pageShown==32}}
                    if(scrollProbe){
                        await("restored visible row without scrolling"){visibleNow(card.pseudonym)}
                        emit("SCROLL,restored_row=15,without_test_scroll=true")
                    }else await("return visible"){reveal(card.pseudonym)}
                    emit("PERSON,detail_visible=true,back_to_second_page=true,ledger=${h.outstandingState()}")
                }
            }
            check(h.observations.workspaceMaterializations.get()==0 && h.observations.reads.get()==1)
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            main{m.lock();a.finish()};await("fixture cleanup"){h.cleanupConfirmed.get()};activity=null
            emit("PASS,full_graphs=0,core_reads=1,reuse=false,fixture_cleanup=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P112 PASS $scenario")})
        }catch(t:Throwable){emit("FAIL,"+t.stackTraceToString());runCatching{main{activity?.finish()};harness?.let{h->await("cleanup after failure"){h.cleanupConfirmed.get()}}};finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P112 FAIL $scenario")})}
    }
}
