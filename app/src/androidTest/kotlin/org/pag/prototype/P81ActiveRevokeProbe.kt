package org.pag.prototype

import android.app.*
import android.content.*
import android.graphics.Rect
import android.os.*
import android.view.*
import android.widget.TextView
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.data.product.ProductRuntime
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.*

/** Custom runner: every scenario begins at real Runtime -> SQLCipher -> VM -> Compose -> hit.
 * Reflection READS diagnostics only; no session/key/VM fields are modified by reflection.
 * Controls are P77/debug only and can only deny/defer existing central authority.
 */
class P81ActiveRevokeProbe:Instrumentation() {
    private var scenario=""
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);scenario=arguments?.getString("scenario") ?: "lock";start()}
    private fun field(target:Any,name:String):Any?=target.javaClass.getDeclaredField(name).let {it.isAccessible=true;it.get(target)}
    private fun main(work:()->Unit) {
        val failure=AtomicReference<Throwable?>()
        runOnMainSync {try{work()}catch(t:Throwable){failure.set(t)}}
        failure.get()?.let{throw it}
    }
    private fun waitFor(label:String,seconds:Long=20,test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+seconds*1000
        while(SystemClock.elapsedRealtime()<end) {if(test())return;Thread.sleep(25)}
        error("Timed out: $label")
    }
    private fun <T> onMain(work:()->T):T {val value=AtomicReference<T>();main{value.set(work())};return value.get()}
    private fun texts():Set<String> {
        val root=uiAutomation.rootInActiveWindow ?: return emptySet()
        return try {
            if(root.packageName?.toString()!=targetContext.packageName)return emptySet()
            val viewport=Rect().also(root::getBoundsInScreen);val result=mutableSetOf<String>()
            fun visit(node:android.view.accessibility.AccessibilityNodeInfo) {
                if(node.isVisibleToUser && Rect.intersects(viewport,Rect().also(node::getBoundsInScreen)))
                    node.text?.toString()?.let(result::add)
                for(i in 0 until node.childCount)node.getChild(i)?.let{try{visit(it)}finally{it.recycle()}}
            };visit(root);result
        } finally {root.recycle()}
    }
    private fun touch(text:String) {
        val root=checkNotNull(uiAutomation.rootInActiveWindow)
        val point=try {
            check(root.packageName?.toString()==targetContext.packageName)
            fun find(node:android.view.accessibility.AccessibilityNodeInfo):Rect? {
                if(node.text?.toString()==text && node.isVisibleToUser)return Rect().also(node::getBoundsInScreen)
                for(i in 0 until node.childCount)node.getChild(i)?.let { child ->
                    try {find(child)?.let{return it}} finally{child.recycle()}
                };return null
            };checkNotNull(find(root)){"Missing touch target $text"}
        } finally{root.recycle()}
        val now=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,point.exactCenterX(),point.exactCenterY(),0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try{check(uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    private fun shell(command:String) {uiAutomation.executeShellCommand(command).use {p->java.io.FileInputStream(p.fileDescriptor).use{it.readBytes()}}}
    override fun onStart() {
        var activity:MainActivity?=null
        var application:Application?=null
        var lifecycle:Application.ActivityLifecycleCallbacks?=null
        val events=java.util.Collections.synchronizedList(mutableListOf<String>())
        val replacement=AtomicReference<MainActivity?>()
        var dialog:Dialog?=null
        var receiver:BroadcastReceiver?=null
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            check(context.packageName=="org.pag.prototype.a.p77runtime.test")
            val a=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity=a;application=a.application
            val runtime=field(a,"runtime") as ProductRuntime
            val harness=field(a,"p77Harness") as P77ProductRuntimeHarness
            val model=field(a,"model") as ProductViewModel
            lifecycle=object:Application.ActivityLifecycleCallbacks {
                override fun onActivityPaused(x:Activity){if(x===a)events.add("onPause")}
                override fun onActivityStopped(x:Activity){if(x===a)events.add("onStop")}
                override fun onActivityStarted(x:Activity){if(x===a)events.add("onStart")}
                override fun onActivityResumed(x:Activity){if(x===a)events.add("onResume") else if(x is MainActivity)replacement.set(x)}
                override fun onActivityDestroyed(x:Activity){if(x===a)events.add("onDestroy")}
                override fun onActivityCreated(x:Activity,b:Bundle?){if(x is MainActivity && x!==a) {
                    x.window.decorView.viewTreeObserver.addOnDrawListener {
                        if(coreOwnerIdentity(field(x,"model") as ProductViewModel)==null)events.add("replacement_invalid_core_draw")
                    }
                }}
                override fun onActivitySaveInstanceState(x:Activity,b:Bundle){}
            }
            application.registerActivityLifecycleCallbacks(lifecycle)
            main {a.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener {events.add("focus=$it")}}
            waitFor("focused LOCKED"){onMain{a.hasWindowFocus() && runtime.session.snapshot().state==SessionState.LOCKED}}
            main {model.unlock()}
            waitFor("READY",90){runtime.session.snapshot().state==SessionState.READY}
            waitFor("action list"){onMain{model.actions.any{it.id=="p11-active"}}}
            var interactionReturned=0L
            main {model.detail("p11-active");interactionReturned=SystemClock.elapsedRealtime()}
            waitFor("real core",90){onMain{coreOwnerIdentity(model)!=null}}
            val core=onMain{checkNotNull(coreOwnerIdentity(model))}
            val slot=onMain{(field(model,"coreSlot") as AtomicReference<*>).get()!!}
            val lifetime=field(slot,"lifetime") as ActiveCoreLifetime
            check(onMain{model.compactHubPresentation?.value(0,0)==200L && model.compactHubPresentation?.value(2,0)==1000L})
            check(harness.observations.reads.get()==1 && harness.observations.hasSingleOriginalTicket())
            main {model.activeArea(ActiveArea.PERSONS);check(coreOwnerIdentity(model)===core)}
            waitFor("real compact PERSONS"){texts().let{"Synthetic 0" in it && "32 von 200 angezeigt · weitere Einträge verfügbar" in it}}
            check(harness.observations.hitAccepted.get()==1)
            val generation=runtime.session.snapshot().generation
            val epoch=harness.mutations.mutationEpoch
            fun rawSlotEmpty()=(field(model,"coreSlot") as AtomicReference<*>).get()==null
            fun revoked() {main {
                check(!harness.observations.originalTicketLive()) {"Original readticket still live"}
                check(!lifetime.isCurrent()) {"Original lifetime still current"}
                check(rawSlotEmpty()) {"Atomic slot not synchronously cleared"}
                check(coreOwnerIdentity(model)==null)
                check(!lifetime.authorizeHit("p11-active")) {"Old lifetime authorized a hit"}
            }}
            val frame=CountDownLatch(1)
            val invalidated=AtomicBoolean(false)
            lateinit var draw:ViewTreeObserver.OnDrawListener
            main {
                draw=ViewTreeObserver.OnDrawListener {
                    if(invalidated.get() && rawSlotEmpty())frame.countDown()
                }
                a.window.decorView.viewTreeObserver.addOnDrawListener(draw)
            }
            fun coreWrite() {
                runtime.tasks.create("p11-active",TaskInput("Synthetic revoke task")) {
                    check(it is OperationsResult.Value);events.add("core_write_callback")
                }
            }
            fun documentWrite()=runtime.documents.importDocument("p11-active","",DocumentFormat.TEXT,
                DocumentSource{error("Invalid pre-reserve input must not open source")}) {
                    check(it==DocumentResult.Rejected(DocumentFailure.INVALID));events.add("document_rejected_before_reserve")
                }
            fun waitUntil(time:Long){while(SystemClock.elapsedRealtime()<time)Thread.sleep(minOf(250L,time-SystemClock.elapsedRealtime()).coerceAtLeast(1L))}
            sendStatus(0,Bundle().apply{putString("stream","P81_START,$scenario,real_reader=1,original_ticket=1,real_hit=1,visible_persons=true\n")})
            invalidated.set(true)
            when(scenario) {
                "lock" -> main {model.lock()}
                "blocked" -> main {runtime.session.storageFailure()}
                "worker_lock" -> {
                    val done=CountDownLatch(1);val failure=AtomicReference<Throwable?>()
                    main {val worker=Thread{try{runtime.session.lock();check(rawSlotEmpty())}catch(t:Throwable){failure.set(t)}finally{done.countDown()}}
                        worker.start();check(done.await(5,TimeUnit.SECONDS))}
                    failure.get()?.let{throw it}
                }
                "idle","worker_idle" -> {
                    val c=onMain{checkNotNull(runtime.session.acquire(AccessScope.READY))}
                    if(scenario=="worker_idle") {
                        val blocked=CountDownLatch(1);val unblock=CountDownLatch(1)
                        Handler(Looper.getMainLooper()).postDelayed({blocked.countDown();check(unblock.await(5,TimeUnit.SECONDS))},
                            (interactionReturned+29_600-SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                        check(blocked.await(35,TimeUnit.SECONDS))
                        try {waitUntil(interactionReturned+30_100);check(!c.check(AccessPoint.STEP));check(rawSlotEmpty());check(!harness.observations.originalTicketLive())}
                        finally{unblock.countDown()}
                    } else {waitUntil(interactionReturned+30_100);runtime.session.checkExpiry()}
                    check(runtime.session.snapshot().technicalId=="B0-IDLE-EXPIRED")
                }
                "absolute" -> {
                    val deadline=SystemClock.elapsedRealtime()+checkNotNull(runtime.session.remainingAbsoluteMillis())
                    while(SystemClock.elapsedRealtime()<deadline) {
                        val next=minOf(deadline+50,SystemClock.elapsedRealtime()+15_000)
                        waitUntil(next)
                        if(SystemClock.elapsedRealtime()<deadline) {touch("Personen");events.add("real_touch_keeps_idle_only")}
                    }
                    runtime.session.checkExpiry()
                    check(runtime.session.snapshot().technicalId=="B0-ABSOLUTE-EXPIRED")
                    events.add("real_device_180s_absolute")
                }
                "touch_idle" -> {
                    waitUntil(interactionReturned+15_000);touch("Personen")
                    val touched=SystemClock.elapsedRealtime();events.add("real_touch")
                    waitUntil(interactionReturned+30_100)
                    check(runtime.session.snapshot().state==SessionState.READY){"Legitimate Android touch did not renew idle"}
                    main {check(coreOwnerIdentity(model)===core);model.activeArea(ActiveArea.FUNCTIONS);check(coreOwnerIdentity(model)===core)}
                    waitUntil(touched+30_200);runtime.session.checkExpiry()
                    check(runtime.session.snapshot().technicalId=="B0-IDLE-EXPIRED")
                }
                "touch_navigation" -> {
                    main{model.activeArea(ActiveArea.HUB);check(coreOwnerIdentity(model)===core)}
                    waitFor("hub touch card"){"Personen öffnen" in texts()}
                    val before=harness.observations.hitAccepted.get()
                    touch("Personen öffnen")
                    waitFor("touch PERSONS"){onMain{model.activeArea==ActiveArea.PERSONS && coreOwnerIdentity(model)===core} && "Synthetic 0" in texts()}
                    check(harness.observations.hitAccepted.get()==before+1)
                    check(harness.observations.reads.get()==1 && harness.observations.tickets.get()==1)
                    events.add("real_navigation_touch_authorized_hit");main{model.lock()}
                }
                "touch_denied" -> {
                    main{harness.faults.worker.holdNext();coreWrite();check(rawSlotEmpty())}
                    val before=harness.observations.hitAccepted.get()
                    touch("‹ Zum Lagebild")
                    waitFor("denied touch navigation"){onMain{model.activeArea==ActiveArea.HUB}}
                    check(harness.observations.hitAccepted.get()==before)
                    events.add("real_touch_legitimate_interaction_but_no_authorized_hit")
                    harness.faults.worker.release();waitFor("writer result"){events.contains("core_write_callback")}
                }
                "reauth" -> {main{model.lock()};revoked();main{model.unlock()};waitFor("reauth READY",90){runtime.session.snapshot().state==SessionState.READY}
                    check(runtime.session.snapshot().generation!=generation);main{check(coreOwnerIdentity(model)==null)}}
                "action_change" -> main {model.detail("p11-completed")}
                "status_vm" -> {
                    main{model.requestAdvance();model.confirmAdvance()}
                    waitFor("ViewModel COMPLETED after real writer",90){onMain{model.selected?.status==ActionStatus.COMPLETED && !model.saving}}
                    events.add("product_vm_completed_after_committed_status_write")
                }
                "status_change" -> {main{runtime.actions.advanceActionStatus("p11-active",ActionStatus.ACTIVE){check(it is ActionChangeResult.Applied);events.add("status_committed")}}
                    waitFor("actual status commit"){events.contains("status_committed")}}
                "unknown","cleanup_uncertain" -> main {
                    val pending=checkNotNull(harness.mutations.beforeDocumentQueue("p11-active",generation))
                    check(pending.finishDocument(DocumentWriteCompletion(if(scenario=="unknown")DocumentWriteOutcome.UNKNOWN else DocumentWriteOutcome.NOT_EXECUTED,
                        if(scenario=="unknown")WorkspaceWriteCleanup.COMPLETE else WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED)))
                    check(harness.mutations.beforeTaskQueue("p11-active",generation)==null)
                    check(harness.mutations.beforeActiveRead("p11-active",generation)==null)
                    events.add("injected_metadata_completion_not_native_cleanup_fault")
                }
                "core_writer","document_writer" -> {
                    main {harness.faults.worker.holdNext();if(scenario=="core_writer")coreWrite() else documentWrite()
                        check(harness.faults.worker.isHeld());check(rawSlotEmpty());check(!harness.observations.originalTicketLive())
                        check(harness.mutations.mutationEpoch==epoch+1)
                        check(harness.mutations.beforeActiveRead("p11-active",generation)==null)
                        check(harness.mutations.beforeTaskQueue("p11-active",generation)==null)}
                    harness.faults.worker.release()
                    waitFor("writer result"){events.contains(if(scenario=="core_writer")"core_write_callback" else "document_rejected_before_reserve")}
                }
                "home" -> {
                    main {a.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))}
                    waitFor("OS onStop"){events.contains("onStop")};revoked()
                    targetContext.startActivity(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    waitFor("OS resume"){events.contains("onStart") && events.contains("onResume") && onMain{a.hasWindowFocus()}}
                    check(runtime.session.snapshot().state==SessionState.LOCKED)
                }
                "focus" -> {
                    main {dialog=Dialog(a).apply{setContentView(TextView(a).apply{text="Synthetic focus probe"});show()}}
                    waitFor("OS window focus lost"){events.contains("focus=false")};revoked()
                    main {dialog?.dismiss();dialog=null};waitFor("focus return"){onMain{a.hasWindowFocus()}}
                }
                "screen_keyguard" -> {
                    val keyguard=a.getSystemService(KeyguardManager::class.java)
                    events.add("keyguard_precheck_deviceSecure=${keyguard.isDeviceSecure},keyguardSecure=${keyguard.isKeyguardSecure},keyguardLocked=${keyguard.isKeyguardLocked},deviceLocked=${keyguard.isDeviceLocked}")
                    check(!keyguard.isDeviceSecure && !keyguard.isKeyguardSecure){"NOT EXECUTED: secure/unknown keyguard must not be changed"}
                    receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){events.add(i.action ?: "unknown")}}
                    targetContext.registerReceiver(receiver,IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_EXPORTED)
                    shell("input keyevent KEYCODE_SLEEP")
                    waitFor("OS SCREEN_OFF"){events.contains(Intent.ACTION_SCREEN_OFF)}
                    events.add("keyguardLocked=${keyguard.isKeyguardLocked},deviceLocked=${keyguard.isDeviceLocked},deviceSecure=${keyguard.isDeviceSecure}")
                    revoked();shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
                    waitFor("wake focused"){onMain{a.hasWindowFocus()}}
                }
                "recreate" -> {
                    main{a.recreate()};waitFor("replacement Activity",90){replacement.get()!=null}
                    activity=replacement.get();revoked()
                    main{check(coreOwnerIdentity(field(activity!!,"model") as ProductViewModel)==null)}
                    waitFor("replacement draw and content removal"){events.contains("replacement_invalid_core_draw") && "Synthetic 0" !in texts()}
                    events.add("new_runtime_locked_no_old_slot")
                }
                "lost_main","late_generation","late_action","worker_install" -> {
                    main {harness.faults.holdNextCoreMain.set(true);model.detail("p11-active")}
                    waitFor("held real core main",90){harness.faults.main.isHeld()}
                    if(scenario=="worker_install") {
                        // First publication is loader receipt, second is VM install. Both use the real context.
                        harness.faults.afterPublish.set {harness.faults.afterPublish.set {
                            check(!rawSlotEmpty()) {"Install point not reached"}
                            val done=CountDownLatch(1)
                            Thread{try{runtime.session.lock();check(rawSlotEmpty())}finally{done.countDown()}}.start()
                            check(done.await(5,TimeUnit.SECONDS))
                        }}
                        val task=harness.faults.main.take();main{task.run();task.run()}
                    } else {
                        main {if(scenario=="late_action")model.detail("p11-completed") else if(scenario=="late_generation")model.lock()}
                        if(scenario=="late_generation") {main{model.unlock()};waitFor("new READY",90){runtime.session.snapshot().state==SessionState.READY};check(runtime.session.snapshot().generation!=generation)}
                        if(scenario=="lost_main")harness.faults.main.discard()
                        else {val task=harness.faults.main.take();main{task.run();task.run()}}
                    }
                    main {check(coreOwnerIdentity(model)==null)}
                }
                "callback_false" -> main {
                    harness.faults.callbackEntered.set(false);harness.faults.falseAfterCallback.set(true)
                    check(!lifetime.authorizeHit("p11-active"));check(harness.faults.callbackEntered.get())
                    check(rawSlotEmpty());check(coreOwnerIdentity(model)==null)
                    events.add("injected_false_after_real_central_callback")
                }
                "writer_during_hit" -> {
                    main {harness.faults.beforePublish.set {harness.faults.worker.holdNext();coreWrite()}
                        model.activeArea(ActiveArea.FUNCTIONS);check(rawSlotEmpty());check(!harness.observations.originalTicketLive())}
                    harness.faults.worker.release();waitFor("race writer committed"){events.contains("core_write_callback")}
                }
                else -> error("Unknown scenario $scenario")
            }
            events.add("terminal_state=${runtime.session.snapshot().state},reason=${runtime.session.snapshot().technicalId},generation=${runtime.session.snapshot().generation}")
            revoked();invalidated.set(true)
            // Action-change data callback and Compose must settle; never force invalidate().
            if(scenario !in setOf("recreate")) {
                check(frame.await(10,TimeUnit.SECONDS)){"No subsequent invalid-core frame"}
                waitFor("old content removed") {
                    val visible=texts()
                    if(scenario=="status_vm") statusLabel(ActionStatus.COMPLETED) in visible &&
                        "32 von 200 angezeigt · weitere Einträge verfügbar" !in visible
                    else "Synthetic 0" !in visible
                }
                main {check(coreOwnerIdentity(model)==null)}
            }
            main {if(a.window.decorView.viewTreeObserver.isAlive)a.window.decorView.viewTreeObserver.removeOnDrawListener(draw)}
            check(!harness.observations.originalTicketLive())
            val closingHarness=onMain{field(checkNotNull(activity),"p77Harness") as P77ProductRuntimeHarness}
            main{activity?.finish()}
            waitFor("fixture cleanup") {harness.cleanupConfirmed.get() && closingHarness.cleanupConfirmed.get()}
            events.add("fixture_cleanup_confirmed")
            sendStatus(0,Bundle().apply{putString("stream","P81_CASE,$scenario,GREEN,real_initial_reader=1,initial_ticket=1,initial_hit=1,old_ticket_dead=true,old_lifetime_dead=true,slot_revoked=true,os_events=${synchronized(events){events.toList()}.joinToString("|")},reads_total=${harness.observations.reads.get()},tickets_total=${harness.observations.tickets.get()},hits_accepted=${harness.observations.hitAccepted.get()},hit_attempts=${harness.observations.hitAttempts.get()}\n")})
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P81 PASS $scenario; real Runtime/SQLCipher/VM/Compose, synthetic credentials; no product RAM gate")})
        } catch(t:Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P81 FAIL $scenario: ${t.stackTraceToString()}; events=${synchronized(events){events.toList()}.joinToString("|")}")})
        } finally {
            receiver?.let{targetContext.unregisterReceiver(it)}
            lifecycle?.let{application?.unregisterActivityLifecycleCallbacks(it)}
            main {dialog?.dismiss();activity?.finish()}
        }
    }
}
