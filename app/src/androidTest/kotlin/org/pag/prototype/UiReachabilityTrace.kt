package org.pag.prototype

import android.app.Instrumentation
import android.app.Activity
import android.view.ViewTreeObserver
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Scalar, fixed-phase diagnostics for isolated synthetic fixtures only. Never logs node text/IDs. */
internal class UiReachabilityTrace(private val probe:Instrumentation,private val suite:String){
    var phase="ENTRY"
    var refreshAncestors=false
    var refreshTree=false
    var detailed=false
    var enabled=true
    var summaryObserved=false;private set
    var font=1f
    var model:ProductViewModel?=null
    private var list:LazyListState?=null
    private val compositions=AtomicInteger();private val draws=AtomicInteger()
    private val extent=AtomicReference("0x0")
    private val scroll=AtomicReference("none")
    fun start(){
        check(probe.targetContext.packageName=="org.pag.perf.p11probe")
        if(!enabled)return
        val info=probe.uiAutomation.serviceInfo
        info.eventTypes=info.eventTypes or AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        probe.uiAutomation.serviceInfo=info
        probe.uiAutomation.setOnAccessibilityEventListener { e ->
            if(e.packageName?.toString()==probe.targetContext.packageName && e.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED)
                scroll.set("${e.scrollY}/${e.maxScrollY}")
        }
    }
    private var activity:Activity?=null
    private val drawListener=ViewTreeObserver.OnDrawListener{draws.incrementAndGet()}
    fun attach(host:Activity){if(!enabled)return;activity=host;probe.runOnMainSync{host.window.decorView.viewTreeObserver.addOnDrawListener(drawListener)}}
    fun stop(){probe.runOnMainSync{activity?.window?.decorView?.viewTreeObserver?.removeOnDrawListener(drawListener)};if(enabled)probe.uiAutomation.setOnAccessibilityEventListener(null);model=null;activity=null;list=null}
    @Composable fun Content(m:ProductViewModel){
        if(!enabled){ProductScreen(m);return}
        CompositionLocalProvider(LocalProductObservation provides {compositions.incrementAndGet()},LocalProductListObservation provides {_,state->list=state}){
            Box(Modifier.fillMaxSize().onGloballyPositioned{extent.set("${it.size.width}x${it.size.height}")}){ProductScreen(m)}
        }
    }
    fun event(kind:String,detail:String=""){
        if(!enabled)return
        var state="none"
        probe.runOnMainSync{
            activity?.window?.decorView?.let{v->state="focus=${v.findFocus()!=null};ime=${v.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())}"}
            model?.let{m->
                val board=m.tasks.board
                state+=";route=${productNavigationKey(m)};feedback=${m.feedback!=null};opsBusy=${m.operations.busy};ops=${m.operations.screen};group=${m.operations.functionFocus!=null};personFocus=${m.operations.personId!=null};taskSelected=${m.tasks.selected!=null};taskForm=${m.tasks.form};area=${m.activeArea};workspace=${m.workspace!=null};board=${board!=null};open=${board?.active?.count{it.status==org.pag.domain.TaskStatus.OPEN}};progress=${board?.active?.count{it.status==org.pag.domain.TaskStatus.IN_PROGRESS}};memory=${m.memoryReports.mode};busy=${m.memoryReports.busy};loading=${m.memoryReports.loading};completed=${m.memoryReports.expected?.status==org.pag.domain.MemoryReportStatus.COMPLETED};addenda=${m.memoryReports.detail?.addenda?.size}"
            }
            list?.let{l->
                val info=l.layoutInfo
                val summary=info.visibleItemsInfo.find{it.key=="task-summary"}
                if(summary!=null)summaryObserved=true
                state+=";lazy=${l.firstVisibleItemIndex}/${l.firstVisibleItemScrollOffset};moving=${l.isScrollInProgress};viewport=${info.viewportStartOffset}/${info.viewportEndOffset};summary=${summary?.offset}/${summary?.size}"
            }
        }
        probe.sendStatus(0,Bundle().apply{putString("stream","REACH,$suite,$font,$phase,$kind,$detail;$state;rootComposition=${compositions.get()};draw=${draws.get()};extent=${extent.get()};eventScroll=${scroll.get()}\n")})
    }
    fun node(kind:String,node:AccessibilityNodeInfo?,refresh:Boolean=false):Boolean {
        if(node==null){if(detailed)event(kind,"present=false");return false}
        val before=Rect().also(node::getBoundsInScreen)
        val oldEnabled=node.isEnabled;val oldClick=node.isClickable
        val current=if(refresh)node.refresh() else true
        if(detailed && kind!="TREE_NODE")event(kind,"current=${if(refresh)current.toString() else "unverified"};refreshed=$refresh;enabled=$oldEnabled/${node.isEnabled};clickable=$oldClick/${node.isClickable};bounds=$before/${Rect().also(node::getBoundsInScreen)};visible=${node.isVisibleToUser};focus=${node.isFocused};back=${node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD}};forward=${node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD}}")
        return current
    }
    fun scroll(node:AccessibilityNodeInfo?,direction:Int):Boolean {
        node("SCROLL_BEFORE",node)
        val accepted=node?.performAction(direction)==true
        if(detailed)event("SCROLL_ACCEPTED","back=${direction==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD};accepted=$accepted")
        return accepted
    }
    fun search(nodes:List<AccessibilityNodeInfo>,label:String,backwards:Boolean,attempt:Int){
        if(!detailed)return
        fun rect(n:AccessibilityNodeInfo)=Rect().also(n::getBoundsInScreen)
        val raw=nodes.filter{it.text?.toString()==label}
        val normalized=nodes.filter{it.text?.toString()?.replace(Regex("\\s+")," ")?.trim()==label}
        val scrollers=nodes.filter{it.isScrollable}
        val markers=listOf("‹ Aufgaben","‹ Zur Aufgabe","‹ Zum Lagebild","Aufgaben","Chronik","Speichern","Aufgabe bearbeiten","Übungsaufgabe geändert")
        val fixed=markers.mapIndexed{i,m->"$i:"+nodes.filter{it.text?.toString()?.replace(Regex("\\s+")," ")?.trim()==m}.joinToString("/"){rect(it).toString()}}.joinToString("|")
        // Label contents are deliberately excluded. The phase names identify the fixed assertion.
        event("SEARCH","attempt=$attempt;back=$backwards;raw=${raw.size};normalized=${normalized.size};rawBounds=${raw.firstOrNull()?.let(::rect)};normBounds=${normalized.firstOrNull()?.let(::rect)};markers=$fixed;scrolls=${scrollers.size};scrollBounds=${scrollers.firstOrNull()?.let(::rect)}")
    }
}
