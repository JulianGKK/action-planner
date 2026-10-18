package org.pag.prototype
import android.app.Instrumentation
import android.content.Intent
import android.os.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import org.pag.domain.*
import org.pag.data.product.AssessmentSyntheticFixture
import org.pag.prototype.product.*
import java.util.concurrent.Executors

/** Real product UI, synthetic storage/session. No device settings/credentials or product data. */
class ListStabilityProbe:Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun waitFor(test:()->Boolean){val until=SystemClock.elapsedRealtime()+45000;while(SystemClock.elapsedRealtime()<until){var yes=false;runOnMainSync{yes=test()};if(yes)return;SystemClock.sleep(10)};error("UI_WAIT")}
    override fun onStart(){val out=Bundle();var phase="guard";var host:MainActivity?=null;val worker=Executors.newSingleThreadExecutor();val ui=CoroutineScope(SupervisorJob()+Dispatchers.Main);var f:AssessmentSyntheticFixture?=null
        try{
            check(targetContext.packageName=="org.pag.perf.p11probe")
            f=AssessmentSyntheticFixture(targetContext,200,1000,200,worker,targetContext.mainExecutor)
            val fixture=f
            host=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            for(scale in listOf(1f,2f,3.5f)){
                phase="font-$scale"
                val session=ActiveWorkspaceAssessmentProbe.Session();lateinit var m:ProductViewModel;var list:LazyListState?=null
                runOnMainSync{
                    m=ProductViewModel(session,fixture.actions,ProductStorageState.READY_TO_OPEN,fixture.persons,null,fixture.operations,fixture.tasks,fixture.protocol,fixture.material,fixture.expenses,fixture.memoryReports){}
                    m.bindWorkspace(fixture.workspace)
                    host!!.setContent{
                        val density=LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density,scale),LocalProductListObservation provides {_,state->list=state}){ProductScreen(m)}
                    };session.ready()
                }
                phase="action-list-$scale";waitFor{m.actionListState is ActionListState.Content}
                phase="open-$scale";runOnMainSync{m.detail(fixture.activeId)};waitFor{m.workspace!=null&&!m.personsLoading}
                repeat(3){
                    phase="tasks-$scale";runOnMainSync{m.activeArea(ActiveArea.TASKS)};waitFor{m.workspace!=null&&!m.personsLoading&&list!=null}
                    val last=m.workspace!!.tasks!!.active.last()
                    phase="tail-$scale";runOnMainSync{ui.launch{list!!.scrollToItem(1001)}}
                    waitFor{list?.layoutInfo?.visibleItemsInfo?.any{it.key==entityRowKey("task",last.id)}==true}
                    val old=list!!.firstVisibleItemIndex;val offset=list!!.firstVisibleItemScrollOffset
                    check(old>900)
                    // Actual accessibility scroll action and labels remain present at the end of the list.
                    phase="semantics-$scale";waitForIdleSync()
                    fun walk(n:AccessibilityNodeInfo?,result:MutableList<AccessibilityNodeInfo>){if(n==null)return;result+=n;repeat(n.childCount){walk(n.getChild(it),result)}}
                    var scrollSemantics=false;var labelSemantics=false;var owned=false;var count=0
                    val semanticsDeadline=SystemClock.elapsedRealtime()+5000
                    do {
                        val root=uiAutomation.rootInActiveWindow
                        owned=root?.packageName?.toString()==targetContext.packageName
                        val nodes=mutableListOf<AccessibilityNodeInfo>()
                        if(owned)walk(root,nodes) // Never inspect another app's window.
                        count=nodes.size
                        scrollSemantics=nodes.any{it.isScrollable}
                        labelSemantics=nodes.any{it.text?.toString()?.contains(last.title)==true || it.contentDescription?.toString()?.contains(last.title)==true}
                        if(scrollSemantics && labelSemantics)break
                        SystemClock.sleep(50)
                    }while(SystemClock.elapsedRealtime()<semanticsDeadline)
                    sendStatus(0,Bundle().apply{putString("stream","SEMANTICS,$owned,$count,$scrollSemantics,$labelSemantics\n")})
                    phase="semantics-scroll-$scale";check(scrollSemantics)
                    phase="semantics-label-$scale";check(labelSemantics)
                    phase="hub-$scale";runOnMainSync{m.activeArea(ActiveArea.HUB)};waitFor{m.workspace!=null&&!m.personsLoading}
                    phase="tasks-$scale";runOnMainSync{m.activeArea(ActiveArea.TASKS)};waitFor{m.workspace!=null&&!m.personsLoading&&list!=null}
                    phase="restore-$scale";sendStatus(0,Bundle().apply{putString("stream","ANCHOR,$old,$offset,${list!!.firstVisibleItemIndex},${list!!.firstVisibleItemScrollOffset}\n")});waitFor{list!!.firstVisibleItemIndex==old && kotlin.math.abs(list!!.firstVisibleItemScrollOffset-offset)<=2}
                }
                phase="forms-$scale"
                val task=m.workspace!!.tasks!!.tasks.first()
                runOnMainSync{m.tasks.open(task);m.tasks.edit()};waitFor{m.tasks.form}
                runOnMainSync{m.tasks.cancel();m.tasks.cancel();m.activeArea(ActiveArea.PERSONS)};waitFor{m.workspace!=null&&!m.personsLoading&&list!=null}
                runOnMainSync{ui.launch{list!!.scrollToItem(201)}}
                waitFor{list?.layoutInfo?.visibleItemsInfo?.any{it.key==entityRowKey("person",m.workspace!!.operations!!.persons.last().person.id)}==true}
                phase="preparation-$scale"
                runOnMainSync{m.back();m.detail(fixture.preparationId)}
                waitFor{m.workspace!=null&&!m.personsLoading}
                runOnMainSync{m.preparationArea(PreparationArea.TASKS)}
                waitFor{m.workspace?.tasks!=null&&!m.personsLoading&&list!=null}
                val preparationLast=m.workspace!!.tasks!!.active.last()
                runOnMainSync{ui.launch{list!!.scrollToItem(1001)}}
                waitFor{list?.layoutInfo?.visibleItemsInfo?.any{it.key==entityRowKey("task",preparationLast.id)}==true}
                runOnMainSync{m.preparationArea(PreparationArea.PERSONS)}
                waitFor{m.workspace!=null&&!m.personsLoading&&list!=null}
                runOnMainSync{ui.launch{list!!.scrollToItem(201)}}
                waitFor{list?.layoutInfo?.visibleItemsInfo?.any{it.key==entityRowKey("person",m.workspace!!.persons.last().id)}==true}
                runOnMainSync{m.back();m.detail(fixture.activeId)}
                waitFor{m.workspace!=null&&!m.personsLoading}
                phase="rapid-lock-$scale"
                runOnMainSync{m.activeArea(ActiveArea.TASKS);m.activeArea(ActiveArea.PERSONS);session.lock()}
                waitFor{m.workspace==null && m.selected==null && !m.tasks.form && list==null}
                SystemClock.sleep(1500) // Allow a real already-admitted SQLCipher read to return late.
                runOnMainSync{check(m.workspace==null && m.selected==null);session.ready()}
                waitFor{m.actionListState is ActionListState.Content}
                runOnMainSync{check(m.workspace==null && m.selected==null && !m.tasks.form && m.sensitiveOpened==null);session.lock()}
                sendStatus(0,Bundle().apply{putString("stream","LIST_UI,font=$scale,PASS\n")})
            }
            out.putString("stream","LIST_STABILITY_PASS\n")
        }catch(e:Throwable){out.putString("stream","LIST_STABILITY_FAIL,$phase,${e.javaClass.simpleName},"+e.stackTrace.take(5).joinToString("|"){it.className+"."+it.methodName}+"\n")}
        finally{ui.cancel();runCatching{runOnMainSync{host?.finish()}};worker.shutdown();worker.awaitTermination(60,java.util.concurrent.TimeUnit.SECONDS);f?.close()}
        finish(0,out)
    }
}
