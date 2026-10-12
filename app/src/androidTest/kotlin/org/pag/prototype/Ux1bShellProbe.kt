package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import org.pag.prototype.product.PagWorkspace
import org.pag.prototype.product.StatusFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.pag.domain.*
import org.pag.prototype.product.ProductScreen
import org.pag.prototype.product.ProductViewModel

/** Synthetic UI only: no authentication, product DB write or system setting change. */
class Ux1bShellProbe : Instrumentation() {
    private class Session:SessionPort {
        var current=SessionSnapshot()
        var observer:((SessionSnapshot)->Unit)?=null
        var interactions=0
        override fun snapshot()=current
        override fun observe(observer:(SessionSnapshot)->Unit):Subscription {this.observer=observer;observer(current);return Subscription{}}
        fun ready(){current=SessionSnapshot(SessionState.READY,current.generation+1);observer!!(current)}
        override fun remainingAbsoluteMillis()=102000L.takeIf{current.state==SessionState.READY}
        override fun authenticate(experiment:Experiment){}
        override fun testAccess(){}
        override fun userInteraction(){interactions++}
        override fun lock(){current=SessionSnapshot(SessionState.LOCKED,current.generation+1);observer!!(current)}
    }
    private class Actions:ActionRepository {
        var row=Action("synthetic","synthetic action","synthetic description",1,2,0,ActionStatus.PREPARATION)
        override fun listActions(publish:(List<Action>)->Unit){} // deliberately pending initial list
        override fun getAction(id:String,publish:(Action?)->Unit)=publish(row)
        override fun createAction(action:NewAction,publish:(Action)->Unit){}
        override fun updateAction(expected:Action,changes:NewAction,publish:(ActionChangeResult)->Unit){}
        override fun advanceActionStatus(id:String,expected:ActionStatus,publish:(ActionChangeResult)->Unit){}
    }
    private class People:PersonRepository {
        override fun listPersons(actionId:String,publish:(PersonResult<List<Person>>)->Unit)=publish(PersonResult.Value(emptyList()))
        override fun getPerson(actionId:String,personId:String,publish:(PersonResult<Person?>)->Unit)=publish(PersonResult.Value(Person(personId,actionId,"synthetic person",0)))
        override fun createPerson(actionId:String,pseudonym:String,publish:(PersonResult<Person>)->Unit)=publish(PersonResult.Value(Person("synthetic","synthetic",pseudonym,0)))
        override fun updatePerson(actionId:String,personId:String,pseudonym:String,publish:(PersonResult<Person>)->Unit){}
    }
    private class NavigationOps(private val actions:Actions):OperationsRepository {
        var reads=0
        override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit){
            reads++;publish(OperationsResult.Value(OperationalBoard(actionId,actions.row.status,listOf(Role("r",actionId,"Support",0)),
                listOf(OperationalPerson(Person("p",actionId,"synthetic person",0),PersonRolePlan(setOf("r"),"r"),
                    PersonOperationalState(actionId,"p","r",OperationalStatus.ON_SITE,null,0))))))
        }
        override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit){}
        override fun renameRole(actionId:String,roleId:String,name:String,publish:(OperationsResult<Role>)->Unit){}
        override fun deleteRole(actionId:String,roleId:String,publish:(OperationsResult<Unit>)->Unit){}
        override fun planRoles(actionId:String,personId:String,plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit)=publish(OperationsResult.Value(Unit))
        override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit){}
    }
    private class NavigationTasks:TaskRepository {
        var reads=0
        override fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit){reads++;publish(OperationsResult.Value(TaskBoard(actionId,emptyList(),emptyList())))}
        override fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit){}
        override fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit){}
        override fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit){}
        override fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit){}
    }
    private class NavigationMetadata:LeadershipRepository {
        var pending:((SensitiveResult<SensitiveInventory>)->Unit)?=null
        var reads=0;var opens=0;var defer=true
        fun complete(state:SensitiveState=SensitiveState.READY){pending!!(SensitiveResult.Value(SensitiveInventory(state,setOf(SensitiveArea.IDENTITY))))}
        override fun inventory(actionId:String,personId:String,publish:(SensitiveResult<SensitiveInventory>)->Unit){reads++;pending=publish;if(!defer)complete()}
        override fun setup(actionId:String,passphrase:CharArray,confirmation:CharArray,publish:(SensitiveResult<Unit>)->Unit){passphrase.fill('\u0000');confirmation.fill('\u0000')}
        override fun open(actionId:String,personId:String,area:SensitiveArea,passphrase:CharArray,publish:(SensitiveResult<SensitiveOpened>)->Unit){opens++;passphrase.fill('\u0000')}
        override fun save(grant:SensitiveGrant,data:SensitiveData,publish:(SensitiveResult<Unit>)->Unit){}
        override fun deleteArea(grant:SensitiveGrant,publish:(SensitiveResult<Unit>)->Unit){}
        override fun deletePerson(actionId:String,personId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit){passphrase.fill('\u0000')}
        override fun deleteAction(actionId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit){passphrase.fill('\u0000')}
        override fun revoke(){}
    }
    private fun verifyMinorNavigation(activity:MainActivity,density:Float){
        val session=Session();val actions=Actions();val ops=NavigationOps(actions);val tasks=NavigationTasks();val metadata=NavigationMetadata()
        lateinit var model:ProductViewModel
        runOnMainSync {
            actions.row=actions.row.copy(status=ActionStatus.ACTIVE)
            model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,People(),metadata,ops,tasks){}
            session.ready();model.detail("synthetic");model.operations.update("p")
            activity.setContent {CompositionLocalProvider(LocalDensity provides Density(density,1f)){ProductScreen(model)}}
        }
        waitForIdleSync();awaitNodes{tree->tree.any{it.text?.toString()=="Lage aktualisieren"}}
        val boards=ops.reads;val taskReads=tasks.reads
        runOnMainSync{model.operationalPersonDetail();check(ops.reads==boards && tasks.reads==taskReads && metadata.reads==1)}
        waitForIdleSync();awaitNodes{tree->tree.any{it.text?.toString()=="Geschützter Speicher wird geprüft"}}
        runOnMainSync{metadata.complete()}
        waitForIdleSync();awaitNodes{tree->tree.any{it.text?.toString()=="Identitätsdaten anzeigen"}}
        runOnMainSync {
            metadata.defer=false;actions.row=actions.row.copy(status=ActionStatus.PREPARATION)
            model.detail("synthetic");model.personDetail("p");metadata.defer=true
        }
        for(save in listOf(false,true)) {
            val before=metadata.reads
            runOnMainSync {model.operations.plan("p");if(save)model.operations.savePlan() else model.operations.cancel();check(metadata.reads==before+1 && model.sensitiveInventoryLoading)}
            waitForIdleSync();awaitNodes{tree->tree.any{it.text?.toString()=="Sensible Bereiche werden geprüft …"}}
            runOnMainSync{metadata.complete();check(!model.sensitiveInventoryLoading && model.sensitiveOpened==null)}
            waitForIdleSync();awaitNodes{tree->tree.any{it.text?.toString()=="Identitätsdaten anzeigen"}}
        }
        runOnMainSync {model.operations.plan("p");model.operations.cancel();metadata.complete(SensitiveState.PARTIAL_OR_INVALID)}
        waitForIdleSync()
        val partial=awaitNodes{tree->tree.any{it.text?.toString()?.startsWith("Der sensible Speicher dieser Aktion ist nicht verfügbar.")==true}}
        check(partial.none{it.text?.toString() in listOf("Identitätsdaten anzeigen","Sensible Personendaten einrichten","Sensible Bereiche werden geprüft …")})
        runOnMainSync {
            model.operations.plan("p");model.operations.cancel();val old=metadata.pending!!
            session.lock();old(SensitiveResult.Value(SensitiveInventory(SensitiveState.READY)))
            check(model.sensitiveInventory==null && !model.sensitiveInventoryLoading && model.sensitiveOpened==null && metadata.opens==0)
        }
        waitForIdleSync();awaitNodes{tree->tree.any{it.text?.toString()=="PaG"}}
    }
    private fun nodes():List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo){result.add(node);for(i in 0 until node.childCount)node.getChild(i)?.let(::visit)}
        var root:AccessibilityNodeInfo?=null
        repeat(30){if(root==null){root=uiAutomation.rootInActiveWindow;if(root==null)SystemClock.sleep(100)}}
        visit(checkNotNull(root));return result
    }
    private fun awaitNodes(predicate:(List<AccessibilityNodeInfo>)->Boolean):List<AccessibilityNodeInfo> {
        repeat(40) {
            val current=nodes()
            if(predicate(current))return current
            SystemClock.sleep(50)
        }
        error("Synthetic tree transition did not arrive")
    }
    private fun bounds(node:AccessibilityNodeInfo)=Rect().also{node.getBoundsInScreen(it)}
    private fun control(node:AccessibilityNodeInfo):AccessibilityNodeInfo {var n=node;while(!n.isClickable)n=n.parent?:break;check(n.isClickable);return n}
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        var activity:MainActivity?=null
        var scaleUnderTest=0f
        try {
            val launched=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity=launched
            val density=targetContext.resources.displayMetrics.density
            for(scale in listOf(1f,2f)) {
                scaleUnderTest=scale
                val session=Session();val actions=Actions()
                lateinit var model:ProductViewModel
                runOnMainSync {
                    model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,People()){}
                    session.ready()
                    check(model.remainingMillis==102000L) // before first composition, before body result
                    launched.setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density,scale)){ProductScreen(model)}
                    }
                }
                waitForIdleSync();SystemClock.sleep(300)
                val initial=awaitNodes{tree->tree.any{it.contentDescription?.toString()?.startsWith("Sitzung:")==true}}
                check(initial.none{it.text?.toString()=="PaG"})
                check(initial.any{it.text?.toString()=="Geschützter Speicher wird geprüft"})
                val countdown=checkNotNull(initial.find{it.contentDescription?.toString()?.startsWith("Sitzung:")==true})
                val lock=control(checkNotNull(initial.find{it.text?.toString()=="Sperren"}))
                val timerRect=bounds(countdown);val lockRect=bounds(lock)
                check(!Rect.intersects(timerRect,lockRect))
                check(lockRect.height()>=48*density && lockRect.width()>=48*density)
                check(timerRect.left>=0 && lockRect.right<=targetContext.resources.displayMetrics.widthPixels)
                runOnMainSync{model.detail("synthetic")}
                waitForIdleSync();SystemClock.sleep(200)
                awaitNodes{tree->tree.any{it.text?.toString()=="synthetic action"}}
                runOnMainSync{model.addPerson()}
                waitForIdleSync();SystemClock.sleep(300)
                awaitNodes{tree->tree.any{it.isEditable && it.isFocused}}
                // Compose virtual accessibility inputType does not expose EditorInfo capitalization.
                // KeyboardOptions are covered by the field mapping test and code audit.
                runOnMainSync{model.savePerson("synthetic lowercase");check(model.feedback?.text=="Person gespeichert")}
                waitForIdleSync();SystemClock.sleep(200)
                val withBanner=awaitNodes{tree->tree.any{it.text?.toString()=="✓ Person gespeichert"}}
                check(withBanner.any{it.text?.toString()=="✓ Person gespeichert"})
                val interactions=session.interactions
                SystemClock.sleep(300)
                check(session.interactions==interactions)
                runOnMainSync{
                    session.lock()
                    check(model.feedback==null && model.remainingMillis==null && model.workspace==null && model.selected==null)
                }
                waitForIdleSync();SystemClock.sleep(200)
                val locked=awaitNodes{tree->tree.any{it.text?.toString()=="PaG"}}
                check(locked.any{it.text?.toString()=="PaG"})
                check(locked.none{it.contentDescription?.toString()?.startsWith("Sitzung:")==true})
                check(locked.none{it.text?.toString() in listOf("Sperren","synthetic action","✓ Person gespeichert")})
                // Accessibility bounds can be clipped by an overlay. Measure actual layout instead.
                val feedback=mutableStateOf<StatusFeedback?>(null)
                var anchor:List<Float>?=null
                runOnMainSync {
                    launched.setContent {
                        CompositionLocalProvider(LocalDensity provides Density(density,scale)) {
                            MaterialTheme {
                                PagWorkspace(feedback.value,{feedback.value=null},Modifier.fillMaxSize().safeDrawingPadding()) {
                                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                        Text("synthetic layout anchor",Modifier.onGloballyPositioned {
                                            val position=it.positionInRoot()
                                            anchor=listOf(position.x,position.y,it.size.width.toFloat(),it.size.height.toFloat())
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                waitForIdleSync();SystemClock.sleep(200)
                val before=checkNotNull(anchor)
                runOnMainSync { feedback.value=StatusFeedback(1,"Synthetischer Status") }
                waitForIdleSync();SystemClock.sleep(200)
                check(before==anchor)
                runOnMainSync { feedback.value=null }
                waitForIdleSync();SystemClock.sleep(200)
                check(before==anchor)
            }
            verifyMinorNavigation(launched,density)
            runOnMainSync{launched.finish()}
            finish(-1,Bundle().apply{putString("stream","UX1b synthetic READY/header, font scales 1/2, touch/non-overlap, floating banner/no body reflow, entry focus, timer/no interaction, immediate lock cleanup, minor navigation/no extra boards, inventory Cancel/Save/Partial/Lock: OK\n")})
        }catch(failure:Exception){
            activity?.let{runOnMainSync{it.finish()}}
            finish(0,Bundle().apply{putString("stream","UX1b synthetic shell probe failed at test line ${failure.stackTrace.firstOrNull{it.className==Ux1bShellProbe::class.java.name}?.lineNumber}; fontScale=$scaleUnderTest; no operational payload logged\n")})
        }
    }
}
