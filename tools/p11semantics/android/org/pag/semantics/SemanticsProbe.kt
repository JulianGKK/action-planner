package org.pag.semantics

import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.*

/** Test-only A–F ablation. A process crash is never caught/converted into a pass. */
class SemanticsProbe : Instrumentation() {
    private var variant="A";private var font=1f;private var runs=50;private var first=0;private var reuse=false;private var column=false;private var queryAutomation=true
    @Volatile private var trial=-1
    @Volatile private var phase="START"
    private val composed=AtomicInteger(-1);private val drawn=AtomicInteger(-1)
    private val queries=AtomicInteger();private val foreign=AtomicInteger()
    private val pollFailure=AtomicReference<Throwable?>()
    private var host:SemanticsProbeActivity?=null
    override fun onCreate(a:Bundle?){super.onCreate(a);variant=a?.getString("variant")?:"A";font=a?.getString("font")?.toFloat()?:1f;runs=a?.getString("runs")?.toInt()?:50;first=a?.getString("first")?.toInt()?:0;reuse=a?.getString("reuse")=="true";column=a?.getString("column")=="true";queryAutomation=a?.getString("automation")!="false";require(variant in listOf("A","B","C","D","E","F","G","H"));require(font in listOf(1f,2f,3.5f));require(runs in 1..100);start()}
    private fun emit(event:String,detail:String="")=sendStatus(0,Bundle().apply{putString("stream","SEM,$variant,$font,$trial,$phase,$event,$detail\n")})
    private fun uncaught(t:Throwable){emit("CRASH",Thread.currentThread().name+","+t.javaClass.simpleName+","+t.stackTrace.take(18).joinToString("|"){it.className+"."+it.methodName+":"+it.lineNumber})}
    override fun onException(obj:Any?,t:Throwable):Boolean{uncaught(t);return super.onException(obj,t)}
    // Synchronous test assertions are rethrown on the instrumentation thread. Asynchronous UI errors remain uncaught.
    private fun main(block:()->Unit){var failure:Throwable?=null;runOnMainSync{try{block()}catch(t:Throwable){failure=t}};failure?.let{throw it}}
    private fun await(test:()->Boolean){val end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){pollFailure.get()?.let{throw it};if(test())return;SystemClock.sleep(5)};error("PROBE_CONDITION_TIMEOUT")}
    private class State {
        var route by mutableStateOf("LIST");var revision by mutableIntStateOf(0);var enabled by mutableStateOf(true)
        var input by mutableStateOf("Synthetic");var version by mutableIntStateOf(0)
        var list:LazyListState?=null;var scroll:ScrollState?=null
        fun detail(){route="DETAIL";version++}
        fun form(){route="FORM";version++}
        fun save(){revision++;route="DETAIL";version++}
        fun back(){route="LIST";version++}
    }
    private fun frame(s:State){var wanted=-1;main{wanted=s.version};await{composed.get()>=wanted && drawn.get()>=wanted};waitForIdleSync()}
    private fun route(s:State,expected:String){await{var yes=false;main{yes=s.route==expected};yes};frame(s)}
    @Composable private fun Content(s:State,scale:Float){
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
            MaterialTheme {
                val version=s.version
                SideEffect{composed.set(version)}
                Box(Modifier.fillMaxSize().drawWithContent{val stamp=s.version;drawContent();drawn.set(stamp)}) {
                if(s.route=="LIST") {
                    @Composable fun row(i:Int){
                        val text="Synthetic row $i · ${s.revision}"
                        if(variant=="A")Text(text,Modifier.fillMaxWidth().heightIn(min=72.dp)) else {
                            val captured=if(s.enabled)"Expanded" else "Collapsed"
                            val semantic=if(variant=="B")Modifier else Modifier.semantics{
                                onClick(label="Open row $i",action=null)
                                if(variant=="G")stateDescription=if(s.enabled)"Expanded" else "Collapsed"
                                if(variant=="H")stateDescription=captured
                            }
                            OutlinedButton(s::detail,enabled=s.enabled,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp).then(semantic)) {
                                Column(Modifier.fillMaxWidth()){
                                    Text(text,if(variant=="B")Modifier else Modifier.semantics{heading()})
                                    Text("Synthetic status")
                                }
                            }
                        }
                    }
                    if(column){
                        val scroll=rememberScrollState();SideEffect{s.scroll=scroll};DisposableEffect(scroll){onDispose{s.scroll=null}}
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){repeat(32){key(it){row(it)}}}
                    }else{
                        val list=rememberLazyListState();SideEffect{s.list=list};DisposableEffect(list){onDispose{s.list=null}}
                        LazyColumn(Modifier.fillMaxSize().padding(12.dp),state=list,verticalArrangement=Arrangement.spacedBy(8.dp)){
                            items(32,key={"row-$it"},contentType={"row"}){row(it)}
                        }
                    }
                } else Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(s.route)
                    if(s.route=="DETAIL") {Button(s::form){Text("Edit synthetic")};OutlinedButton(s::back){Text("Back synthetic")}}
                    else {OutlinedTextField(s.input,{s.input=it},label={Text("Synthetic input")});Button(s::save){Text("Save synthetic")}}
                }
                }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun <T> tree(block:(List<AccessibilityNodeInfo>)->T):T? {
        val root=uiAutomation.rootInActiveWindow ?: return null
        if(root.packageName?.toString()!=targetContext.packageName){foreign.incrementAndGet();root.recycle();return null}
        val nodes=mutableListOf<AccessibilityNodeInfo>()
        fun walk(n:AccessibilityNodeInfo){nodes+=n;repeat(n.childCount){n.getChild(it)?.let(::walk)}}
        return try{walk(root);queries.incrementAndGet();block(nodes)}finally{nodes.forEach{it.recycle()}}
    }
    private fun click(prefix:String){await{tree{nodes->
        val n=nodes.firstOrNull{it.text?.toString()?.startsWith(prefix)==true} ?: return@tree false
        var control:AccessibilityNodeInfo?=n
        val parents=mutableListOf<AccessibilityNodeInfo>()
        try{while(control!=null&&!control.isClickable){control=control.parent;control?.let{parents+=it}}
            control?.let{it.isEnabled&&it.performAction(AccessibilityNodeInfo.ACTION_CLICK)}==true
        }finally{@Suppress("DEPRECATION") parents.forEach{it.recycle()}}
    }==true}}
    override fun onStart(){
        check(targetContext.packageName=="org.pag.test.p11semantics")
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler{thread,error->runCatching{uncaught(error)};previous?.uncaughtException(thread,error)}
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        var poll:Thread?=null;val polling=AtomicBoolean(false)
        val automation=variant!="E" && queryAutomation
        var complete=false
        try {
            val manager=targetContext.getSystemService(AccessibilityManager::class.java)
            emit("ENV","a11y=${manager.isEnabled};touch=${manager.isTouchExplorationEnabled};automation=$automation;reuse=$reuse;column=$column")
            // E never accesses uiAutomation, even to retrieve ServiceInfo.
            if(automation)uiAutomation.waitForIdle(100,2000)
            for(i in first until first+runs){
                trial=i;phase="CREATE";emit("BEGIN")
                val s=State();composed.set(-1);drawn.set(-1)
                if(host==null)host=startActivitySync(Intent(targetContext,SemanticsProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SemanticsProbeActivity
                val activity=host!!
                val listener=ViewTreeObserver.OnDrawListener{drawn.set(composed.get())}
                main {activity.window.decorView.viewTreeObserver.addOnDrawListener(listener);activity.setContent{Content(s,font)}}
                frame(s)
                polling.set(automation)
                if(automation){poll=Thread({try{while(polling.get()){tree{it.size};SystemClock.sleep(5)}}catch(t:Throwable){pollFailure.set(t)}},"synthetic-semantics-query").apply{start()}}
                phase="SCROLL_END";val moved=AtomicBoolean(false)
                main{scope.launch{if(column)s.scroll!!.scrollTo(s.scroll!!.maxValue) else s.list!!.scrollToItem(31);moved.set(true)}};await{moved.get()}
                phase="INVALIDATE";main{s.enabled=false;s.revision++;s.version++};frame(s)
                phase="SCROLL_START";moved.set(false);main{scope.launch{if(column)s.scroll!!.scrollTo(0) else s.list!!.scrollToItem(0);s.enabled=true;s.version++;moved.set(true)}};await{moved.get()};frame(s)
                if(variant in listOf("D","E","F","G","H")) {
                    phase="LIST_DETAIL";if(variant=="F")click("Synthetic row 0") else main{s.detail()};route(s,"DETAIL")
                    phase="DETAIL_FORM";if(variant=="F")click("Edit synthetic") else main{s.form()};route(s,"FORM")
                    phase="FORM_EDIT";main{s.input="Synthetic changed";s.version++};frame(s)
                    phase="FORM_SAVE";if(variant=="F")click("Save synthetic") else main{s.save()};route(s,"DETAIL")
                    phase="DETAIL_LIST";if(variant=="F")click("Back synthetic") else main{s.back()};route(s,"LIST")
                }
                phase="CLEANUP";polling.set(false);poll?.join(10000);check(poll?.isAlive!=true);poll=null
                pollFailure.get()?.let{throw it}
                main{activity.window.decorView.viewTreeObserver.removeOnDrawListener(listener)}
                if(!reuse){val destroyed=SemanticsProbeActivity.destroyed.get();main{activity.finish()};await{SemanticsProbeActivity.destroyed.get()>destroyed};host=null}
                emit("PASS","queries=${queries.get()};foreign=${foreign.get()}")
            }
            complete=true
        }catch(t:Throwable){emit("HARNESS_FAIL",t.javaClass.simpleName+","+t.stackTrace.take(8).joinToString("|"){it.className+"."+it.methodName+":"+it.lineNumber})}
        finally{polling.set(false);poll?.join(10000);scope.cancel();main{host?.finish()};waitForIdleSync()}
        finish(if(complete)-1 else 0,Bundle().apply{putString("stream",if(complete)"SEMANTICS_PASS\n" else "SEMANTICS_INCOMPLETE\n")})
    }
}
