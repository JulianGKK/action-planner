package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.*
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import org.pag.prototype.product.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** Synthetic geometry ablation, NOT a complete Tasks regression or a product navigation fix. */
class UiFeedbackOcclusionProbe:Instrumentation(){
    private var phase="ENTRY"
    private fun report(error:Throwable){sendStatus(0,Bundle().apply{putString("stream","OCCLUSION_UNCAUGHT,$phase,${error.javaClass.simpleName},"+error.stackTrace.take(12).joinToString("|"){it.className+"."+it.methodName+":"+it.lineNumber}+"\n")})}
    override fun onException(obj:Any?,error:Throwable):Boolean{report(error);return super.onException(obj,error)}
    override fun onCreate(a:Bundle?){super.onCreate(a);start()}
    private fun await(test:()->Boolean){val end=SystemClock.uptimeMillis()+5000;while(SystemClock.uptimeMillis()<end){if(test())return;SystemClock.sleep(5)};error("OCCLUSION_TIMEOUT")}
    private fun nodes():List<AccessibilityNodeInfo>{
        val root=uiAutomation.rootInActiveWindow?:return emptyList()
        check(root.packageName?.toString()==targetContext.packageName)
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun walk(n:AccessibilityNodeInfo){if(!n.refresh())return;result+=n;repeat(n.childCount){n.getChild(it)?.let(::walk)}}
        walk(root);return result
    }
    override fun onStart(){
        val originalHandler=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler{thread,error->runCatching{report(error)};originalHandler?.uncaughtException(thread,error)}
        check(targetContext.packageName=="org.pag.perf.p11probe")
        var host:MainActivity?=null
        try{
            host=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            for(overlay in listOf(false,true))repeat(5){trial->
                phase="RENDER_${overlay}_$trial"
                sendStatus(0,Bundle().apply{putString("stream","OCCLUSION_BEGIN,$phase\n")})
                lateinit var state:ScrollState
                val target=AtomicReference("none")
                val composed=AtomicBoolean(false)
                runOnMainSync{host!!.setContent{
                    val d=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(d.density,3.5f)){
                        MaterialTheme{key(overlay,trial){
                            state=rememberScrollState()
                            SideEffect{composed.set(true)}
                            Box(Modifier.fillMaxSize().padding(top=86.dp)){
                                // Constant synthetic feedback deliberately remains for the entire ablation.
                                PagWorkspace(if(overlay)StatusFeedback(1,"Aufgabe gespeichert")else null,{}){
                                    Column(Modifier.padding(24.dp).verticalScroll(state)){
                                        Spacer(Modifier.height(750.dp))
                                        OutlinedButton({},Modifier.onGloballyPositioned{target.set(it.boundsInRoot().toString())}){Text("Target")}
                                        Spacer(Modifier.height(2000.dp))
                                    }
                                }
                            }
                        }}
                    }
                }}
                phase="MEASURE_${overlay}_$trial"
                await{var ready=false;runOnMainSync{ready=composed.get() && state.viewportSize>0 && state.maxValue>state.viewportSize};ready}
                waitForIdleSync()
                var scroll:AccessibilityNodeInfo?=null
                await{scroll=nodes().firstOrNull{it.isScrollable};scroll!=null}
                phase="SCROLL_${overlay}_$trial"
                check(scroll!!.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                await{var settled=false;runOnMainSync{settled=state.value>0 && !state.isScrollInProgress};settled}
                waitForIdleSync()
                val found=nodes().any{it.text?.toString()=="Target"}
                var delta=0;var viewport=0
                runOnMainSync{delta=state.value;viewport=state.viewportSize}
                sendStatus(0,Bundle().apply{putString("stream","OCCLUSION,$overlay,$trial,delta=$delta;viewport=$viewport;target=$target;node=$found\n")})
                phase="TOUCH_${overlay}_$trial"
                // Small normal touch scrolls backwards from the page-step endpoint, no model command.
                val rect=Rect().also(scroll!!::getBoundsInScreen)
                repeat(2){
                    val x=rect.centerX().toFloat();val from=rect.top+rect.height()*0.7f
                    val distance=100*targetContext.resources.displayMetrics.density
                    val down=SystemClock.uptimeMillis()
                    fun inject(action:Int,y:Float){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(e,true));e.recycle()}
                    inject(MotionEvent.ACTION_DOWN,from)
                    repeat(5){i->SystemClock.sleep(30);inject(MotionEvent.ACTION_MOVE,from+distance*(i+1)/5)}
                    SystemClock.sleep(150);inject(MotionEvent.ACTION_MOVE,from+distance)
                    SystemClock.sleep(100);inject(MotionEvent.ACTION_UP,from+distance);waitForIdleSync()
                }
                val reached=nodes().any{it.text?.toString()=="Target"}
                sendStatus(0,Bundle().apply{putString("stream","OCCLUSION_TOUCH,$overlay,$trial,node=$reached\n")})
                check(found || reached)
            }
            runOnMainSync{host!!.finish()};finish(-1,Bundle().apply{putString("stream","OCCLUSION_GEOMETRY_PASS\n")})
        }catch(t:Throwable){runOnMainSync{host?.finish()};finish(0,Bundle().apply{putString("stream","OCCLUSION_GEOMETRY_FAIL,$phase,${t.javaClass.simpleName}\n")})}
    }
}
