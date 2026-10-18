package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.*

/** Actual production Lazy footer, observed through an existing test-only CompositionLocal. */
class SemanticsProductRepro:Instrumentation(){
    private var runs=50;private var first=0;private var font=2f;private var automation=false
    @Volatile private var trial=-1
    @Volatile private var phase="START"
    override fun onCreate(a:Bundle?){super.onCreate(a);runs=a?.getString("runs")?.toInt()?:50;first=a?.getString("first")?.toInt()?:0;font=a?.getString("font")?.toFloat()?:2f;automation=a?.getString("automation")=="true";require(runs in 1..100);require(font in listOf(1f,2f,3.5f));start()}
    private fun emit(event:String,detail:String="")=sendStatus(0,Bundle().apply{putString("stream","ACT,$font,$trial,$phase,$event,$detail\n")})
    private fun crash(t:Throwable){emit("CRASH",Thread.currentThread().name+","+t.javaClass.simpleName+","+t.stackTrace.take(18).joinToString("|"){it.className+"."+it.methodName+":"+it.lineNumber})}
    override fun onException(obj:Any?,t:Throwable):Boolean{crash(t);return super.onException(obj,t)}
    private fun main(f:()->Unit){var failure:Throwable?=null;runOnMainSync{try{f()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun await(f:()->Boolean){val end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){if(f())return;SystemClock.sleep(5)};error("PRODUCT_REPRO_TIMEOUT")}
    @Suppress("DEPRECATION") private fun description(expected:String):Boolean {
        val root=uiAutomation.rootInActiveWindow ?: return false
        val nodes=mutableListOf<AccessibilityNodeInfo>()
        fun walk(n:AccessibilityNodeInfo){nodes+=n;repeat(n.childCount){n.getChild(it)?.let(::walk)}}
        return try{if(root.packageName?.toString()!=targetContext.packageName){nodes+=root;false}else{walk(root);nodes.any{it.stateDescription?.toString()==expected}}}finally{nodes.forEach{it.recycle()}}
    }
    override fun onStart(){
        check(targetContext.packageName=="org.pag.perf.p11probe")
        val original=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler{thread,error->runCatching{crash(error)};original?.uncaughtException(thread,error)}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        var activity:MainActivity?=null;var complete=false
        try{
            // With automation=false this path never obtains UIAutomation. Actions remain model-driven.
            for(i in first until first+runs){
                trial=i;phase="CREATE";emit("BEGIN")
                val session=SemanticsActualProbe.Session();val port=SemanticsActualProbe.Tasks()
                val template=port.rows.first()
                port.rows=port.rows+(0 until 64).map{template.copy(id="synthetic-extra-$it",title="Synthetic task $it")}
                lateinit var model:ProductViewModel
                var list:LazyListState?=null
                val stamp=mutableIntStateOf(0);val drawn=AtomicInteger(-1)
                activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                main{
                    model=ProductViewModel(session,SemanticsActualProbe.Actions(),ProductStorageState.READY_TO_OPEN,SemanticsActualProbe.People(),operationsRepository=SemanticsActualProbe.Ops(),taskRepository=port){}
                    model.detail("synthetic");model.activeArea(ActiveArea.TASKS)
                    activity!!.setContent{
                        val density=LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density,font),LocalProductListObservation provides {_,state->list=state}){
                            Box(Modifier.fillMaxSize().drawWithContent{val v=stamp.intValue;drawContent();drawn.set(v)}){ProductScreen(model)}
                        }
                    }
                }
                fun frame(){var wanted=0;main{wanted=stamp.intValue};await{drawn.get()>=wanted};waitForIdleSync()}
                await{var yes=false;main{yes=list!=null&&list!!.layoutInfo.totalItemsCount>60};yes};frame()
                fun scroll(end:Boolean){val done=AtomicBoolean(false);main{scope.launch{val state=list!!;state.scrollToItem(if(end)state.layoutInfo.totalItemsCount-1 else 0);stamp.intValue++;done.set(true)}};await{done.get()};frame()}
                phase="COMPOSE_FOOTER";scroll(true);if(automation)await{description("Eingeklappt")}
                phase="DEACTIVATE_FOOTER";scroll(false)
                phase="INVALIDATE_FOOTER";main{model.tasks.toggleClosed();stamp.intValue++};frame()
                main{check(model.tasks.closedExpanded)}
                if(automation){phase="SEMANTICS_EXPANDED";scroll(true);await{description("Ausgeklappt")}}
                phase="LOCK";main{session.lock();stamp.intValue++};frame()
                main{check(model.workspace==null&&model.selected==null&&!model.tasks.closedExpanded)}
                phase="CLEANUP";val old=activity!!;main{old.finish()};await{var destroyed=false;main{destroyed=old.isDestroyed};destroyed};activity=null
                emit("PASS")
            }
            complete=true
        }catch(t:Throwable){emit("HARNESS_FAIL",t.javaClass.simpleName+","+t.stackTrace.take(8).joinToString("|"){it.className+"."+it.methodName+":"+it.lineNumber})}
        finally{scope.cancel();main{activity?.finish()};waitForIdleSync()}
        finish(if(complete)-1 else 0,Bundle().apply{putString("stream",if(complete)"PRODUCT_REPRO_PASS\n" else "PRODUCT_REPRO_INCOMPLETE\n")})
    }
}
