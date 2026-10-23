package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelStore
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.pag.data.SessionCoordinator
import org.pag.data.product.p73OwnerReadBridge
import org.pag.domain.*
import org.pag.prototype.product.*
import org.pag.security.*

/** Actual coordinator + loader/lifetime + ViewModel + Compose. Synthetic authentication,
 * maintenance and payload; separate from native SQL tests, NOT ProductRuntime/DB E2E. */
class P73ActiveOwnerProbe:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun verify(ok:Boolean,message:String){check(ok){message}}
    private fun core(ticket:WorkspaceMutationInvalidation.ActiveReadTicket):ActiveCoreResult {
        val state=CompactTaskState(P3bFreeActiveCoreBinding(ticket.actionId,ticket.generation),ticket.mutationEpoch)
        val counts=CompactTaskCounts(0,0,0,0,0)
        return ActiveCoreResult.Value(ActiveCorePayload(state,Action("a","Synthetic","Synthetic",0,0,0,ActionStatus.ACTIVE),
            ActiveCorePeoplePage(state,CompactPeopleCounts(0,0,0,0,0,0,0,0),0,emptyList(),false),
            CompactFunctions(emptyList(),0,0,0),ActiveCoreTaskPage(state,counts,counts,emptyList(),false)),
            ActiveCoreReservation(ActiveWorkspaceBudget.Units(),ActiveWorkspaceBudget.Units()),0,0)
    }
    private var action=Action("a","Synthetic","Synthetic",0,0,0,ActionStatus.ACTIVE)
    private val actions=object:ActionRepository {
        override fun listActions(publish:(List<Action>)->Unit)=publish(listOf(action))
        override fun getAction(id:String,publish:(Action?)->Unit)=publish(action.takeIf{it.id==id})
        override fun createAction(action:NewAction,publish:(Action)->Unit)=error("Unexpected write")
        override fun updateAction(expected:Action,changes:NewAction,publish:(ActionChangeResult)->Unit)=error("Unexpected write")
        override fun advanceActionStatus(id:String,expected:ActionStatus,publish:(ActionChangeResult)->Unit)=error("Unexpected write")
    }
    private val people=object:PersonRepository {
        override fun listPersons(actionId:String,publish:(PersonResult<List<Person>>)->Unit)=publish(PersonResult.Value(emptyList()))
        override fun getPerson(actionId:String,personId:String,publish:(PersonResult<Person?>)->Unit)=publish(PersonResult.Value(null))
        override fun createPerson(actionId:String,pseudonym:String,publish:(PersonResult<Person>)->Unit)=error("Unexpected write")
        override fun updatePerson(actionId:String,expected:Person,pseudonym:String,publish:(PersonResult<Person>)->Unit)=error("Unexpected write")
    }
    override fun onStart() {
        var host:MainActivity?=null
        var passed=0
        try {
            check(targetContext.packageName=="org.pag.validation.p73")
            check(context.packageName=="org.pag.validation.p73.test")
            host=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val activity=host
            for(mode in listOf("lock","worker_idle","absolute","blocked","foreground","core","document","unknown","late_main","false_after_callback","worker_install","action_change","status_change")) {
                val time=AtomicLong(0)
                val worker=ConcurrentLinkedQueue<Runnable>();val delivery=ConcurrentLinkedQueue<Runnable>()
                lateinit var session:SessionCoordinator;lateinit var mutations:UncachedWorkspaceMutations
                lateinit var model:ProductViewModel
                val store=ViewModelStore()
                lateinit var observer:Subscription
                var lifetime:ActiveCoreLifetime?=null
                var publicationCount=0
                runOnMainSync {
                    action=action.copy(status=ActionStatus.ACTIVE,completedAt=null)
                    val auth=object:AuthenticationExperiment {
                        override fun start(mode:KeyExperiment,complete:(AuthenticationOutcome)->Unit):Cancellation {
                            complete(AuthenticationOutcome.Granted(object:SyntheticOperation {
                                override fun perform()=OperationResult.SUCCEEDED
                                override fun release(){}
                            },time.get()));return Cancellation{}
                        }
                    }
                    session=SessionCoordinator(auth,Maintenance{it(true);Subscription{}},MonotonicTime{time.get()},ProductSessionTiming.policy,{true})
                    session.foreground(true);session.authenticate(Experiment.PER_OPERATION)
                    mutations=UncachedWorkspaceMutations()
                    observer=session.observe{if(it.state in setOf(SessionState.LOCKED,SessionState.BLOCKED))mutations.revokeActiveRead()}
                    val access=object:StorageAccess {
                        override fun acquire(scope:AccessScope):StorageContext? {
                            val c=session.acquire(scope) ?: return null
                            return object:StorageContext by c {
                                override fun publish(result:()->Unit):Boolean {
                                    publicationCount++
                                    val accepted=c.publish(result)
                                    if(mode=="worker_install" && publicationCount>=2) {
                                        val t=Thread{session.lock()};t.start();t.join(5000)
                                        check(!t.isAlive)
                                    }
                                    return accepted && !(mode=="false_after_callback" && publicationCount>=2)
                                }
                            }
                        }
                    }
                    val actual=p73OwnerReadBridge(access,mutations,{worker.add(it)},{delivery.add(it)},::core)
                    val port=object:ActiveCoreLoadPort by actual {
                        override fun loadOwned(actionId:String,callback:(ActiveCoreResult,ActiveCoreLifetime?)->Unit)=
                            actual.loadOwned(actionId){value,life->lifetime=life;callback(value,life)}
                    }
                    model=ProductViewModel(session,actions,ProductStorageState.READY_TO_OPEN,people){}
                    store.put("owner",model)
                    model.bindActiveCore(port){targetContext.mainExecutor.execute(it)}
                    model.detail("a")
                    activity.setContent{ProductScreen(model)}
                }
                try {
                    val failure=AtomicReference<Throwable?>()
                    val thread=Thread{try{worker.remove().run()}catch(e:Throwable){failure.set(e)}}
                    thread.start();thread.join(10000);check(!thread.isAlive);failure.get()?.let{throw it}
                    if(mode=="late_main")runOnMainSync{session.lock();session.authenticate(Experiment.PER_OPERATION)}
                    runOnMainSync {while(delivery.isNotEmpty())delivery.remove().run()}
                    if(mode in setOf("late_main","false_after_callback","worker_install")) {
                        runOnMainSync{verify(coreOwnerIdentity(model)==null,"Late/false result retained")}
                    } else {
                        runOnMainSync{verify(coreOwnerIdentity(model)!=null,"Core was not installed")}
                        if(mode=="worker_idle") {
                            val c=session.acquire(AccessScope.READY)!!;time.set(30000)
                            val t=Thread {try {
                                check(!c.check(AccessPoint.STEP))
                                val field=model.javaClass.getDeclaredField("coreSlot").apply{isAccessible=true}
                                check((field.get(model) as AtomicReference<*>).get()==null)
                            }catch(e:Throwable){failure.set(e)}}
                            t.start();t.join(5000);check(!t.isAlive);failure.get()?.let{throw it}
                        } else runOnMainSync {
                            when(mode) {
                                "lock"->session.lock()
                                "absolute"->{for(now in 29000L..174000L step 29000L){time.set(now);session.userInteraction()};time.set(180000);session.checkExpiry()}
                                "blocked"->session.storageFailure()
                                "foreground"->session.foreground(false)
                                "action_change"->model.detail("missing")
                                "status_change"->{action=action.copy(status=ActionStatus.COMPLETED,completedAt=1);model.detail("a")}
                                "core"->check(mutations.beforeTaskQueue("a",session.snapshot().generation)!=null)
                                "document"->check(mutations.beforeDocumentQueue("a",session.snapshot().generation)!=null)
                                "unknown"->{val p=mutations.beforeDocumentQueue("a",session.snapshot().generation)!!;check(p.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)))}
                            }
                        }
                        runOnMainSync{verify(coreOwnerIdentity(model)==null,"Core remains authorized");verify(lifetime?.isCurrent()==false,"Lifetime remains current")}
                    }
                    val draw=CountDownLatch(1)
                    lateinit var listener:android.view.ViewTreeObserver.OnDrawListener
                    runOnMainSync {
                        listener=android.view.ViewTreeObserver.OnDrawListener{if(coreOwnerIdentity(model)==null)draw.countDown()}
                        activity.window.decorView.viewTreeObserver.addOnDrawListener(listener)
                        activity.window.decorView.invalidate()
                    }
                    check(draw.await(5,TimeUnit.SECONDS)){"No actual draw with revoked core: $mode"}
                    runOnMainSync{activity.window.decorView.viewTreeObserver.removeOnDrawListener(listener)}
                    passed++
                    sendStatus(0,Bundle().apply{putString("stream","P73_OWNER,$mode,PASS,next_draw_has_no_valid_core\n")})
                } finally {runOnMainSync{store.clear();session.lock();observer.cancel();delivery.clear();worker.clear()}}
            }
            finish(Activity.RESULT_OK,Bundle().apply{putString("stream","P73_OWNER_OK cases=$passed; synthetic payload/auth; no Runtime/DB E2E\n")})
        } catch(failure:Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("stream","P73_OWNER_FAILED passed=$passed ${android.util.Log.getStackTraceString(failure)}")})
        } finally {host?.let{runOnMainSync{it.finish()}}}
    }
}
