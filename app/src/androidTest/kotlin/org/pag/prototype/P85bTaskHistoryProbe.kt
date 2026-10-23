package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Actual product admission, original Runtime/Room/SQLCipher/keyowner/VM. Synthetic P77 auth.
 * The unchanged §83 reports remain the historical three-graph RED characterization.
 */
class P85bTaskHistoryProbe:Instrumentation() {
    private var scenario="timings"
    private var fixture="stress"
    private var ownActivity:MainActivity?=null
    @Volatile private var lastTapNanos=0L
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);scenario=arguments?.getString("scenario") ?: scenario;fixture=arguments?.getString("fixture") ?: fixture;start()}
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())return work()
        val result=AtomicReference<T>();val error=AtomicReference<Throwable?>()
        runOnMainSync{try{result.set(work())}catch(t:Throwable){error.set(t)}}
        error.get()?.let{throw it};return result.get()
    }
    private fun waitFor(name:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+90_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(20)}
        error("P85b timeout $name")
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream",s+"\n")})
    override fun onStart() {
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime")
            check(context.packageName=="org.pag.prototype.a.p77runtime.test")
            val service=uiAutomation.serviceInfo
            service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            uiAutomation.serviceInfo=service
            val a=startActivitySync(Intent(targetContext,MainActivity::class.java).putExtra("p83Fixture",fixture).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            ownActivity=a
            val h=field(a,"p77Harness") as P77ProductRuntimeHarness
            val model=field(a,"model") as ProductViewModel;val runtime=h.runtime
            waitFor("focused LOCKED"){main{a.hasWindowFocus() && runtime.session.snapshot().state==SessionState.LOCKED}}
            main{model.unlock()};waitFor("READY"){runtime.session.snapshot().state==SessionState.READY}
            waitFor("actions"){main{model.actions.any{it.id=="p11-active"}}}
            main{model.detail("p11-active")}
            waitFor("compact attempt"){main{coreOwnerIdentity(model)!=null || model.workspaceError}}
            main{model.activeArea(ActiveArea.TASKS);model.useFullActiveView()}
            waitFor("workspace"){main{!model.workspaceRefreshing && model.workspace?.tasks!=null}}
            val task=main{val b=checkNotNull(model.workspace?.tasks);check(b.tasks.size==if(fixture=="stress")1000 else 40);check(b.events.isEmpty() && !b.eventsComplete);check(b.tasks.all{it.status==TaskStatus.OPEN});check(b.active.size==b.tasks.size && b.closed.isEmpty());emit("P85b_COUNTERS,open=${b.active.size},in_progress=0,closed=0,eager_events=0,events_complete=false");b.tasks.minBy{it.id}.also{model.tasks.open(it)}}
            val diagnostic=org.pag.data.product.P85PagingDiagnostics(targetContext,runtime.tasks)
            if(scenario=="timings") {
                diagnostic.explain=true
                repeat(3){rep ->
                    for(number in listOf(1L,2L,63L,126L)) {
                        val drawn=CountDownLatch(1);val drawNanos=java.util.concurrent.atomic.AtomicLong()
                        val start=System.nanoTime()
                        lateinit var listener:android.view.ViewTreeObserver.OnDrawListener
                        main {
                            listener=android.view.ViewTreeObserver.OnDrawListener{
                                if(!model.tasks.historyBusy && model.tasks.history!=null){drawNanos.compareAndSet(0,System.nanoTime());drawn.countDown()}
                            }
                            a.window.decorView.viewTreeObserver.addOnDrawListener(listener)
                            diagnostic.begin()
                            if(number==2L)model.tasks.loadHistory() else model.tasks.loadHistoryPage(number)
                        }
                        waitFor("page $number"){main{!model.tasks.historyBusy}}
                        val delivered=System.nanoTime()-start
                        val page=main{check(model.tasks.historyError==null){"page error ${model.tasks.historyError}"};checkNotNull(model.tasks.history)}
                        check(page.total==4001L && page.events.size==if(number==126L)1 else 32)
                        val offset=(number-1)*32
                        check(main{model.tasks.historyOffset}==offset)
                        page.events.forEachIndexed{i,e ->
                            val n=offset+i
                            if(n==0L)check(e.type==TaskEventType.CREATED && e.occurredAt==1L) else check(e.id=="p83-e$n"){"order mismatch n=$n"}
                            if(n>0)check(e.occurredAt==n+100)
                        }
                        check(drawn.await(8,TimeUnit.SECONDS)){"no page OnDraw"}
                        main{a.window.decorView.viewTreeObserver.removeOnDrawListener(listener)}
                        diagnostic.end().forEach{emit("P85b_DIAG,rep=$rep,page=$number,$it")}
                        emit("P85b_TIME,rep=$rep,page=$number,delivery_ns=$delivered,ondraw_ns=${drawNanos.get()-start},rows=${page.events.size},total=${page.total}")
                    }
                }
            } else if(scenario=="touch") {
                pageDraw(model,1){tap("Chronik laden")}
                pageDraw(model,2){tap("Weitere Ereignisse")}
                val prior=main{model.tasks.history}
                for(value in listOf("","0","4002","9999999999999999999")) {
                    pageInput(value)
                    showText("Seite öffnen")
                    val label=checkNotNull(ownNode{it.text?.toString()=="Seite öffnen"})
                    try {
                        var n:android.view.accessibility.AccessibilityNodeInfo?=android.view.accessibility.AccessibilityNodeInfo.obtain(label)
                        var disabled=false
                        while(n!=null){val current=n;disabled=disabled || !current.isEnabled;n=current.parent;current.recycle()}
                        check(disabled){"Invalid page action not disabled"}
                        val b=android.graphics.Rect();label.getBoundsInScreen(b);tapAt(b.exactCenterX(),b.exactCenterY())
                    }finally{label.recycle()}
                    check(main{model.tasks.history===prior && !model.tasks.historyBusy && model.tasks.historyError==null})
                    emit("P85b_INVALID,value=$value,disabled=true,page_unchanged=true")
                }
                pageDraw(model,126){tap("Letzte Seite")}
                pageInput("63");pageDraw(model,63){tap("Seite öffnen")}
                pageInput("1");pageDraw(model,1){tap("Seite öffnen")}
                pageInput("2");pageDraw(model,2){tap("Seite öffnen")}
            } else if(scenario=="editor_lock") {
                pageDraw(model,1){tap("Chronik laden")}
                tap("Aufgabe bearbeiten");check(main{model.tasks.form && model.tasks.history==null})
                showText("Titel")
                tap("Abbrechen");check(main{!model.tasks.form && model.tasks.selected==task && model.tasks.history==null})
                pageDraw(model,1){tap("Chronik laden")}
                tap("Aufgabe bearbeiten");tap("Speichern")
                waitFor("real editor save"){main{!model.tasks.busy && !model.workspaceRefreshing && !model.tasks.form}}
                check(main{model.tasks.selected?.id==task.id && model.tasks.history==null && model.workspace!!.tasks!!.events.isEmpty() && !model.workspace!!.tasks!!.eventsComplete})
                emit("P85b_EDITOR,physical_open_cancel_save=true,history_cleared=true,eager_events=0")
                h.faults.main.holdNext();tap("Chronik laden")
                waitFor("held Main before touch lock"){h.faults.main.isHeld()}
                tap("Sperren")
                waitFor("LOCKED pixels"){containsText("PaG ist gesperrt.")}
                main{h.faults.main.take().run();check(runtime.session.snapshot().state==SessionState.LOCKED);check(model.tasks.history==null && model.tasks.selected==null)}
                emit("P85b_LOCK,physical_tap=true,held_main_released=true,late_publication=false,locked_pixels=true")
            } else if(scenario=="fences") {
                for(document in listOf(false,true)) {
                    h.faults.main.holdNext();tap(if(main{model.tasks.historyError!=null})"Chronik neu laden" else "Chronik laden")
                    waitFor("held Main fence"){h.faults.main.isHeld()}
                    main{
                        val g=runtime.session.snapshot().generation
                        val pending=checkNotNull(if(document)h.mutations.beforeDocumentQueue(task.actionId,g) else h.mutations.beforeTaskQueue(task.actionId,g))
                        h.faults.main.take().run()
                        check(model.tasks.history==null && model.tasks.historyError==BoundedActiveStatus.ACCESS_REVOKED)
                        check(if(document)pending.finishDocument(DocumentWriteCompletion(DocumentWriteOutcome.NOT_EXECUTED,WorkspaceWriteCleanup.COMPLETE))else pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
                    }
                    showText("Chronik ist nicht mehr aktuell. Bitte neu laden.")
                    emit("P85b_FENCE,document=$document,metadata_injection=true,physical_load=true,revoke_pixels=true,late_publication=false")
                }
                main{
                    val pending=checkNotNull(h.mutations.beforeTaskQueue(task.actionId,runtime.session.snapshot().generation))
                    check(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
                }
                tap("Chronik neu laden")
                check(main{model.tasks.history==null && model.tasks.historyError==BoundedActiveStatus.ACCESS_REVOKED})
                showText("Chronik ist nicht mehr aktuell. Bitte neu laden.")
                emit("P85b_UNKNOWN,metadata_injection=true,physical_restart=true,denied=true")
            } else if(scenario=="storage_error") {
                diagnostic.failEventRead=true;diagnostic.begin()
                tap("Chronik laden")
                waitFor("BLOCKED"){runtime.session.snapshot().state==SessionState.BLOCKED}
                showText("Geschützter Speicher nicht verfügbar")
                check(main{model.tasks.history==null && model.tasks.selected==null})
                diagnostic.end()
                emit("P85b_STORAGE_ERROR,statement_fault_injection=true,native_close_fault=false,blocked_pixels=true,no_payload=true")
            } else error("Unknown scenario $scenario")
            main{model.lock();check(model.tasks.history==null);a.finish()}
            waitFor("cleanup"){h.cleanupConfirmed.get()}
            emit("P85b_CASE,$fixture,$scenario,ANDROID-GREEN,fixture_cleanup=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P85b PASS $fixture $scenario")})
        }catch(t:Throwable){
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P85b FAIL $fixture $scenario: ${t.stackTraceToString()}")})
        }finally{main{ownActivity?.finish()}}
    }
    private fun ownNode(predicate:(android.view.accessibility.AccessibilityNodeInfo)->Boolean):android.view.accessibility.AccessibilityNodeInfo? {
        val root=uiAutomation.rootInActiveWindow ?: return null
        if(root.packageName?.toString()!=targetContext.packageName){root.recycle();return null}
        return try{findNode(root,predicate)}finally{root.recycle()}
    }
    private fun findNode(root:android.view.accessibility.AccessibilityNodeInfo,predicate:(android.view.accessibility.AccessibilityNodeInfo)->Boolean):android.view.accessibility.AccessibilityNodeInfo? {
        if(!root.refresh())return null
        if(root.isVisibleToUser && predicate(root))return android.view.accessibility.AccessibilityNodeInfo.obtain(root)
        for(i in 0 until root.childCount){val child=root.getChild(i)?:continue
            try{findNode(child,predicate)?.let{return it}}finally{child.recycle()}
        }
        return null
    }
    private fun tapAt(x:Float,y:Float) {
        val down=SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val e=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
            e.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            if(action==android.view.MotionEvent.ACTION_UP)lastTapNanos=System.nanoTime()
            try{check(uiAutomation.injectInputEvent(e,true))}finally{e.recycle()}
            Thread.sleep(60)
        }
    }
    private fun tap(text:String) {
        showText(text)
        waitForIdleSync();Thread.sleep(700)
        val node=checkNotNull(ownNode{it.text?.toString()==text}){"No tap node $text"}
        try {
            check(node.isEnabled)
            val r=android.graphics.Rect();node.getBoundsInScreen(r)
            check(!r.isEmpty);tapAt(r.exactCenterX(),r.exactCenterY())
        }finally{node.recycle()}
        waitForIdleSync()
    }
    private fun pageInput(value:String) {
        showText("Seite (1–126)")
        waitForIdleSync();Thread.sleep(700)
        val input=checkNotNull(ownNode{it.isEditable}){"No page input"}
        val bounds=android.graphics.Rect()
        try{input.getBoundsInScreen(bounds);emit("P85b_FIELD,bounds=$bounds,focused=${input.isFocused},visible=${input.isVisibleToUser}")}
        finally{input.recycle()}
        // Put the complete field inside the viewport before tapping its text area.
        if(bounds.top<main{ownActivity!!.window.decorView.height}*0.2f){swipe(false);Thread.sleep(250)}
        val centered=checkNotNull(ownNode{it.isEditable})
        try{centered.getBoundsInScreen(bounds);tapAt(bounds.exactCenterX(),bounds.exactCenterY())}finally{centered.recycle()}
        val end=SystemClock.elapsedRealtime()+8_000
        while(SystemClock.elapsedRealtime()<end && uiAutomation.windows.none{it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD})Thread.sleep(100)
        val focused=ownNode{it.isEditable}
        try{emit("P85b_FIELD_AFTER,bounds=$bounds,focused=${focused?.isFocused},enabled=${focused?.isEnabled},session=${main{(field(checkNotNull(ownActivity),"model") as ProductViewModel).snapshot.state}},ime_insets=${main{ownActivity!!.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())}},window_types=${uiAutomation.windows.map{it.type}}")}
        finally{focused?.recycle()}
        check(uiAutomation.windows.any{it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD}){"Numeric IME not exposed"}
        val imeState=android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("dumpsys input_method")).bufferedReader().use{it.readText()}
        check(Regex("inputType=0x[0-9a-fA-F]*2(?: |$)").containsMatchIn(imeState)){"Current numeric EditorInfo not observed"}
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MOVE_END)
        repeat(20){sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DEL)}
        value.forEach{digit ->
                val win=checkNotNull(uiAutomation.windows.firstOrNull{it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD})
            val root=checkNotNull(win.root)
            val key=try{findNode(root){it.text?.toString()==digit.toString() || it.contentDescription?.toString()==digit.toString()}}finally{root.recycle()}
            if(key==null) {
                // Samsung numeric keyboard exposes compound labels such as "2 ABC".
                val r=checkNotNull(win.root)
                val compound=try{findNode(r){it.text?.toString()?.startsWith("$digit ")==true || it.contentDescription?.toString()?.startsWith("$digit,")==true}}finally{r.recycle()}
                checkNotNull(compound){"No numeric keyboard key $digit"}.let{n->try{val b=android.graphics.Rect();n.getBoundsInScreen(b);tapAt(b.exactCenterX(),b.exactCenterY())}finally{n.recycle()}}
            } else try{val b=android.graphics.Rect();key.getBoundsInScreen(b);tapAt(b.exactCenterX(),b.exactCenterY())}finally{key.recycle()}
        }
        val actual=checkNotNull(ownNode{it.isEditable})
        try{check(actual.text?.toString()==value){"numeric entry mismatch"}}finally{actual.recycle()}
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitForIdleSync()
        emit("P85b_IME,value=$value,class=NUMBER,physical_digit_taps=true")
    }
    private fun pageDraw(model:ProductViewModel,number:Long,trigger:()->Unit) {
        val a=checkNotNull(ownActivity)
        val drawn=CountDownLatch(1);val start=System.nanoTime();val end=java.util.concurrent.atomic.AtomicLong()
        lateinit var listener:android.view.ViewTreeObserver.OnDrawListener
        main {listener=android.view.ViewTreeObserver.OnDrawListener{if(!model.tasks.historyBusy && model.tasks.history!=null && model.tasks.historyOffset==(number-1)*32){end.compareAndSet(0,System.nanoTime());drawn.countDown()}};a.window.decorView.viewTreeObserver.addOnDrawListener(listener)}
        trigger()
        waitFor("touch page $number"){main{!model.tasks.historyBusy && model.tasks.history!=null}}
        check(drawn.await(8,TimeUnit.SECONDS))
        main{a.window.decorView.viewTreeObserver.removeOnDrawListener(listener)}
        val p=main{checkNotNull(model.tasks.history)};val offset=(number-1)*32
        check(p.total==4001L && p.events.size==if(number==126L)1 else 32)
        check(main{model.tasks.historyOffset}==offset)
        p.events.forEachIndexed{i,e->val n=offset+i;if(n==0L)check(e.type==TaskEventType.CREATED)else check(e.id=="p83-e$n" && e.occurredAt==n+100)}
        showText("Ereignisse ${offset+1}–${offset+p.events.size} von ${p.total}")
        emit("P85b_TOUCH,page=$number,rows=${p.events.size},total=${p.total},ondraw_ns=${end.get()-start},tap_to_ondraw_ns=${end.get()-lastTapNanos},physical_tap=true,exact_order=true")
    }
    private fun uiText():String {
        val root=uiAutomation.rootInActiveWindow ?: return "no_root"
        if(root.packageName?.toString()!=targetContext.packageName){root.recycle();return "foreign_root"}
        val result=ArrayList<String>()
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo){
            if(result.size>=100)return
            if(n.text!=null)result.add(n.text.toString().take(160))
            if(n.isScrollable)result.add("SCROLLABLE:${n.className}")
            for(i in 0 until n.childCount){val child=n.getChild(i) ?: continue;try{visit(child)}finally{child.recycle()}}
        }
        return try{visit(root);result.joinToString("|")}finally{root.recycle()}
    }
    private fun showText(text:String) {
        waitForIdleSync();Thread.sleep(500)
        repeat(4){if(containsText(text))return;swipe(false);Thread.sleep(200)}
        repeat(12){if(containsText(text))return;swipe(true);Thread.sleep(200)}
        check(containsText(text)){"No actual accessibility content: $text; ${uiText()}"}
    }
    private fun swipe(forward:Boolean) {
        val rect=main {
            val a=checkNotNull(ownActivity)
            check(a.packageName==targetContext.packageName)
            if(!a.hasWindowFocus())return@main null
            val v=a.window.decorView;val origin=IntArray(2).also{v.getLocationOnScreen(it)}
            android.graphics.Rect(origin[0],origin[1],origin[0]+v.width,origin[1]+v.height)
        } ?: return
        val root=uiAutomation.rootInActiveWindow ?: return
        try{if(root.packageName?.toString()!=targetContext.packageName)return}finally{root.recycle()}
        val start=SystemClock.uptimeMillis()
        val from=rect.top+rect.height()*(if(forward)0.78f else 0.30f)
        val to=rect.top+rect.height()*(if(forward)0.30f else 0.78f)
        for(i in 0..10) {
            val action=when(i){0->android.view.MotionEvent.ACTION_DOWN;10->android.view.MotionEvent.ACTION_UP;else->android.view.MotionEvent.ACTION_MOVE}
            val event=android.view.MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,rect.exactCenterX(),from+(to-from)*minOf(i,8)/8,0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            try{check(uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
            Thread.sleep(if(i==9)200 else 60)
        }
    }
    private fun containsText(text:String):Boolean {
        val root=uiAutomation.rootInActiveWindow ?: return false
        if(root.packageName?.toString()!=targetContext.packageName){root.recycle();return false}
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo):Boolean {
            if(!n.refresh())return false
            if(n.isVisibleToUser && n.text?.toString()?.contains(text)==true)return true
            for(i in 0 until n.childCount){val child=n.getChild(i) ?: continue;try{if(visit(child))return true}finally{child.recycle()}}
            return false
        }
        return try{visit(root)}finally{root.recycle()}
    }
}
