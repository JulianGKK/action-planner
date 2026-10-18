package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated geometry proof, no ProductViewModel, repositories or credentials. Same scroll modifier order. */
class UiScrollViewportProbe:Instrumentation(){
    private fun main(block:()->Unit){runOnMainSync(block)}
    private fun await(test:()->Boolean){val end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){if(test())return;SystemClock.sleep(5)};error("VIEWPORT_PROBE_TIMEOUT")}
    private fun nodes():List<AccessibilityNodeInfo>{
        val root=uiAutomation.rootInActiveWindow ?: return emptyList()
        if(root.packageName?.toString()!=targetContext.packageName)return emptyList()
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun walk(n:AccessibilityNodeInfo){if(!n.refresh())return;result+=n;repeat(n.childCount){n.getChild(it)?.let(::walk)}}
        walk(root);return result
    }
    override fun onCreate(a:Bundle?){super.onCreate(a);start()}
    override fun onStart(){
        check(targetContext.packageName=="org.pag.perf.p11probe")
        var host:MainActivity?=null
        try{
            host=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            for(font in listOf(1f,2f,3.5f))for(explicit in listOf(false,true))repeat(5){trial->
                lateinit var state:ScrollState
                val viewport=AtomicInteger(0);val ready=AtomicBoolean(false)
                main{host!!.setContent{
                    val d=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(d.density,font)){
                        MaterialTheme{key(font,explicit,trial){
                            state=rememberScrollState();ready.set(true)
                            Box(Modifier.fillMaxSize().padding(top=200.dp).onSizeChanged{viewport.set(it.height)}){
                                val semantics=if(explicit)Modifier.semantics{getScrollViewportLength{state.viewportSize.toFloat()}}else Modifier
                                Column(Modifier.padding(24.dp).verticalScroll(state).heightIn(min=0.dp).then(semantics),verticalArrangement=Arrangement.spacedBy(16.dp)){
                                    repeat(40){OutlinedButton({}){Text("Synthetic row $it")}}
                                }
                            }
                        }}
                    }
                }}
                await{var measured=false;main{measured=ready.get()&&state.viewportSize>0&&state.maxValue>state.viewportSize};measured};waitForIdleSync()
                var scrollNode:AccessibilityNodeInfo?=null
                await{scrollNode=nodes().firstOrNull{it.isScrollable};scrollNode!=null}
                val accepted=scrollNode!!.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                check(accepted)
                await{var settled=false;main{settled=state.value>0&&!state.isScrollInProgress};settled}
                var delta=0;var max=0;var actualViewport=0
                main{delta=state.value;max=state.maxValue;actualViewport=state.viewportSize}
                sendStatus(0,Bundle().apply{putString("stream","VIEWPORT,$font,$explicit,$trial,delta=$delta;scrollViewport=$actualViewport;boxViewport=${viewport.get()};max=$max\n")})
            }
            main{host!!.finish()};finish(-1,Bundle().apply{putString("stream","VIEWPORT_GEOMETRY_PASS\n")})
        }catch(t:Throwable){main{host?.finish()};finish(0,Bundle().apply{putString("stream","VIEWPORT_GEOMETRY_FAIL,${t.javaClass.simpleName}\n")})}
    }
}
