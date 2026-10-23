package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.data.product.P86WorkspaceDiagnostics
import org.pag.domain.*
import org.pag.prototype.product.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.IdentityHashMap
import java.lang.reflect.Modifier

/** Isolated full-workspace candidate, real Runtime/repositories/SQLCipher/Compose; synthetic auth.
 * No core binding, no extra payload owner. Samples/census never grant budget or retirement.
 */
class P86FullWorkspaceProbe:Instrumentation() {
    private var fixture="p86-small"
    private var ownActivity:Activity?=null
    private lateinit var model:ProductViewModel
    private lateinit var h:P77ProductRuntimeHarness
    private lateinit var diagnostics:P86WorkspaceDiagnostics
    @Volatile private var lastTapNanos=0L
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);fixture=arguments?.getString("fixture") ?: fixture;start()}
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun <T> main(work:()->T):T {
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())return work()
        val result=AtomicReference<T>();val error=AtomicReference<Throwable?>()
        runOnMainSync{try{result.set(work())}catch(t:Throwable){error.set(t)}}
        error.get()?.let{throw it};return result.get()
    }
    private fun waitFor(name:String,work:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+60_000
        while(SystemClock.elapsedRealtime()<end){if(work())return;Thread.sleep(20)}
        error("P86 timeout $name; session=${h.runtime.session.snapshot().state}")
    }
    private fun emit(s:String)=sendStatus(0,Bundle().apply{putString("stream",s+"\n")})
    private fun memory():String {
        val r=Runtime.getRuntime();val p=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}
        return "art_used=${r.totalMemory()-r.freeMemory()},art_committed=${r.totalMemory()},native_heap=${Debug.getNativeHeapAllocatedSize()},pss_kib=${p.totalPss},fd=${java.io.File("/proc/self/fd").list()?.size},gc=${Debug.getRuntimeStat("art.gc.gc-count")}"
    }
    private fun idle()=model.workspace!=null && !model.personsLoading && !model.workspaceRefreshing && !model.tasks.busy
    private fun committed():Boolean {
        val a=checkNotNull(ownActivity)
        return field(a,"p86Committed")==a.javaClass.getMethod("p86StateToken").invoke(a)
    }
    private fun measure(label:String,ready:()->Boolean,trigger:()->Unit) {
        val a=checkNotNull(ownActivity);val drawn=CountDownLatch(1);val draw=AtomicLong();val delivery=AtomicLong()
        lastTapNanos=0
        lateinit var listener:android.view.ViewTreeObserver.OnDrawListener
        main{listener=android.view.ViewTreeObserver.OnDrawListener {
            if(lastTapNanos>0 && ready() && committed()) {draw.compareAndSet(0,System.nanoTime());drawn.countDown()}
        };a.window.decorView.viewTreeObserver.addOnDrawListener(listener)}
        val sampling=AtomicBoolean(true)
        val sampler=Thread {while(sampling.get()){emit("P86_SAMPLE,$fixture,$label,${memory()}");Thread.sleep(500)}}
        sampler.start()
        try {
            trigger()
            waitFor(label){main{ready().also{if(it)delivery.compareAndSet(0,System.nanoTime())}}}
            check(drawn.await(8,TimeUnit.SECONDS)){"No committed OnDraw $label"}
            emit("P86_TOUCH,$fixture,$label,tap_to_ondraw_ns=${draw.get()-lastTapNanos},poll_delivery_ns=${delivery.get()-lastTapNanos},physical_tap=true,commit_before_draw=true")
            emit("P86_RESOURCES,$label,${diagnostics.sample()}")
        }finally{sampling.set(false);sampler.join(2000);main{a.window.decorView.viewTreeObserver.removeOnDrawListener(listener)}}
    }
    /** Partial DTO census, identity-deduplicated within the supplied roots only.
     * This excludes capacities, Compose, Room rows, native, maps and hidden stacks.
     */
    private fun census(label:String,vararg roots:Any?) {
        val seen=IdentityHashMap<Any,Boolean>();val q=java.util.ArrayDeque<Any>();roots.filterNotNull().forEach{q.add(it)}
        var objects=0L;var strings=0L;var units=0L;var lists=0L;var entries=0L
        while(q.isNotEmpty()) {
            val o=q.removeFirst();if(seen.put(o,true)!=null)continue
            check(seen.size<100000)
            when(o) {
                is String->{strings++;units+=o.length}
                is Collection<*>->{lists++;entries+=o.size;o.filterNotNull().forEach{q.add(it)}}
                is Enum<*>->Unit
                else->if(o is WorkspaceSnapshot || o.javaClass.name.startsWith("org.pag.domain.")) {
                    objects++;o.javaClass.declaredFields.filter{!Modifier.isStatic(it.modifiers) && !it.type.isPrimitive}.forEach{f->f.isAccessible=true;f.get(o)?.let{q.add(it)}}
                }
            }
        }
        val estimate=ActiveWorkspaceBudget.estimateUnits(ActiveWorkspaceBudget.Units(objects=objects,strings=strings,utf16Units=units,lists=lists,listCapacity=entries)).second
        emit("P86_CENSUS,$label,objects=$objects,strings=$strings,utf16=$units,collections=$lists,entries=$entries,partial_engineering_bytes=$estimate,upper_bound=UNKNOWN")
    }
    private fun workspaceCensus(label:String)=main {
        check(field(model,"activeCorePort")==null && model.activeCore==null && !model.compactActiveMode)
        val w=checkNotNull(model.workspace)
        check(w.tasks?.events?.isEmpty()!=false && w.tasks?.eventsComplete!=true)
        emit("P86_WORKSPACE,$label,persons=${w.operations?.persons?.size},roles=${w.operations?.roles?.size},tasks=${w.tasks?.tasks?.size},eager_events=${w.tasks?.events?.size},core_reads=${h.observations.reads.get()}")
        emit("P86_TEXT_UNITS,$label,person_max=${w.persons.maxOfOrNull{it.pseudonym.length}},role_max=${w.operations?.roles?.maxOfOrNull{it.name.length}},task_max=${w.tasks?.tasks?.maxOfOrNull{it.title.length}}")
        census(label,w,model.tasks.input,model.tasks.selected)
    }
    private fun unlock() {
        measure("unlock",{model.snapshot.state==SessionState.READY && model.actions.isNotEmpty()}){tap("Entsperren")}
    }
    private fun openActive()=measure("open_active",{model.selected?.id=="p11-active" && idle()}){tap("P86 Active · Aktiv")}
    private fun hub()=measure("hub",{model.activeArea==ActiveArea.HUB && idle()}){tap("‹ Zum Lagebild")}
    private fun nav(area:ActiveArea)=measure("nav_${area.name}",{model.activeArea==area && idle()}){tap("${area.label} öffnen")}
    private fun page(number:Long,trigger:()->Unit) {
        measure("page_$number",{!model.tasks.historyBusy && model.tasks.history!=null && model.tasks.historyOffset==(number-1)*32},trigger)
        main {
            val p=checkNotNull(model.tasks.history);check(p.events.size<=32)
            check(p.total==128001L);p.events.forEachIndexed{i,e->val n=(number-1)*32+i;if(n==0L)check(e.type==TaskEventType.CREATED)else check(e.id=="p83-e$n" && e.occurredAt==n+100)}
            census("page_$number",model.workspace,p,model.tasks.selected)
            emit("P86_PAGE,number=$number,total=${p.total},rows=${p.events.size},order=true")
        }
    }
    override fun onStart() {
        try {
            check(targetContext.packageName=="org.pag.prototype.a.p77runtime" && context.packageName=="org.pag.prototype.a.p77runtime.test")
            val service=uiAutomation.serviceInfo;service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;uiAutomation.serviceInfo=service
            val a=startActivitySync(Intent().setClassName(targetContext,"org.pag.prototype.P86FullWorkspaceActivity").putExtra("p83Fixture",fixture).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ownActivity=a;h=field(a,"p77Harness") as P77ProductRuntimeHarness;model=field(a,"model") as ProductViewModel
            diagnostics=P86WorkspaceDiagnostics(targetContext,h.runtime)
            check(!h.runtime.activeCore.reuseEnabled && !h.mutations.ramReuseEnabled)
            waitFor("focus"){main{a.hasWindowFocus()}}
            emit("P86_BASELINE,$fixture,${memory()}");unlock()
            if(fixture=="p86-small") {
                measure("open_preparation",{model.selected?.id=="p11-preparation" && idle()}){tap("P86 Preparation · Vorbereitung")}
                for(area in listOf(PreparationArea.PERSONS,PreparationArea.ROLES,PreparationArea.TASKS,PreparationArea.OVERVIEW))
                    measure("prep_${area.name}",{model.preparationArea==area && idle()}){tap(area.label)}
                workspaceCensus("preparation")
                tap("Aktion starten")
                measure("preparation_to_active",{model.selected?.status==ActionStatus.ACTIVE && idle()}){tap("Bestätigen")}
                nav(ActiveArea.PERSONS);hub();nav(ActiveArea.FUNCTIONS);hub();nav(ActiveArea.TASKS);hub()
                tap("Aktion abschließen")
                measure("active_to_completed",{model.selected?.status==ActionStatus.COMPLETED && !model.saving && idle()}){tap("Bestätigen")}
                check(main{!model.tasks.writable});workspaceCensus("completed")
                emit("P86_LIFECYCLE,real_status_writes=true,PREPARATION_ACTIVE_COMPLETED=true")
                tap("Zur Aktionsliste");tap("Sperren");unlock();openActive()
            } else {
                openActive();workspaceCensus("initial")
                check(main{model.workspace!!.operations!!.persons.size==200 && model.workspace!!.operations!!.roles.size==(if(fixture.endsWith("100"))100 else 32) && model.workspace!!.tasks!!.tasks.size==1000})
            }
            repeat(2){rep->
                nav(ActiveArea.PERSONS);workspaceCensus("persons_$rep");hub()
                nav(ActiveArea.FUNCTIONS);workspaceCensus("functions_$rep")
                if(fixture=="p86-stress100" && rep==0) {
                    measure("roles100_expand",{model.operations.moreFunctionsExpanded}){tap("Weitere Funktionen (99) ▾")}
                    measure("roles100_collapse",{!model.operations.moreFunctionsExpanded}){tap("Weitere Funktionen (99) ▴")}
                }
                hub()
                nav(ActiveArea.TASKS);workspaceCensus("tasks_$rep");hub()
            }
            // Explicit physical lock/reauth, no timer renewal or automatic auth in the harness.
            tap("Sperren");waitFor("locked"){main{model.snapshot.state==SessionState.LOCKED && model.workspace==null}}
            unlock();openActive();nav(ActiveArea.TASKS)
            val taskTitle=main{model.workspace!!.tasks!!.tasks.minBy{it.id}.title}
            measure("task_detail",{model.tasks.selected!=null}){tap(taskTitle)}
            if(fixture!="p86-small") {
                page(1){tap("Chronik laden")};page(2){tap("Weitere Ereignisse")};page(4001){tap("Letzte Seite")}
                pageInput("2001");page(2001){tap("Seite öffnen")}
            }
            measure("editor",{model.tasks.form}){tap("Aufgabe bearbeiten")}
            workspaceCensus("editor")
            measure("editor_cancel",{!model.tasks.form && model.tasks.selected!=null}){tap("Abbrechen")}
            tap("Aufgabe bearbeiten")
            measure("editor_save",{!model.tasks.busy && !model.tasks.form && idle()}){tap("Speichern")}
            check(main{model.tasks.history==null})
            hub()
            // Existing queue gate holds one real full-workspace callback, no extra payload cell.
            h.faults.main.holdNext();tap("Personen öffnen")
            waitFor("held workspace Main"){h.faults.main.isHeld()}
            emit("P86_HELD,$fixture,${memory()},${diagnostics.sample()}")
            tap("‹ Zum Lagebild");waitFor("replacement while old Main held"){main{idle() && model.activeArea==ActiveArea.HUB}}
            main {
                val held=h.faults.main.heldTaskForObservation()!!
                // Inspect direct lambda captures only; never traverse contexts/keys/session.
                val values=held.javaClass.declaredFields.filter{!Modifier.isStatic(it.modifiers)}.mapNotNull{f->f.isAccessible=true;f.get(held)}.filterIsInstance<WorkspaceResult.Value>()
                check(values.size==1){"Expected real captured WorkspaceResult"}
                census("held_old_plus_current",model.workspace,values.single().value)
                emit("P86_OVERLAP,held_workspace=1,current_workspace=1,common_admission=false")
                val current=model.workspace;h.faults.main.take().run();check(model.workspace===current)
            }
            h.faults.main.holdNext();tap("Personen öffnen");waitFor("held before lock"){h.faults.main.isHeld()}
            tap("Sperren");waitFor("locked pixels"){containsText("PaG ist gesperrt.")}
            main{h.faults.main.take().run();check(model.workspace==null && model.tasks.selected==null)}
            unlock();openActive();workspaceCensus("reauth_final")
            if(fixture=="p86-stress100") {
                tap("Aktion abschließen")
                measure("large_active_to_completed",{model.selected?.status==ActionStatus.COMPLETED && !model.saving && idle()}){tap("Bestätigen")}
                check(main{!model.tasks.writable});workspaceCensus("large_completed")
            }
            check(h.observations.reads.get()==0 && h.observations.tickets.get()==0)
            diagnostics.assertIdle();emit("P86_FINAL,$fixture,${memory()},${diagnostics.sample()},late_publication=false,core_port_unbound=true")
            main{model.lock();a.finish()};waitFor("cleanup"){h.cleanupConfirmed.get()}
            emit("P86_CASE,$fixture,FUNCTIONAL_PASS,budget=UNPROVEN,fixture_cleanup=true")
            finish(Activity.RESULT_OK,Bundle().apply{putString("result","P86 PASS $fixture; memory admission UNPROVEN")})
        }catch(t:Throwable){
            emit("P86_FAILURE,$fixture,${memory()}")
            main{ownActivity?.finish()}
            if(::h.isInitialized)runCatching{waitFor("failure cleanup"){h.cleanupConfirmed.get()}}.onFailure{emit("P86_CLEANUP_UNCONFIRMED")}
            finish(Activity.RESULT_CANCELED,Bundle().apply{putString("result","P86 FAIL $fixture: ${t.stackTraceToString()}")})
        }
        finally{main{ownActivity?.finish()}}
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
        showText("Seite (1–4001)")
        waitForIdleSync();Thread.sleep(700)
        val input=checkNotNull(ownNode{it.isEditable}){"No page input"}
        val bounds=android.graphics.Rect()
        try{input.getBoundsInScreen(bounds);emit("P86b_FIELD,bounds=$bounds,focused=${input.isFocused},visible=${input.isVisibleToUser}")}
        finally{input.recycle()}
        // Put the complete field inside the viewport before tapping its text area.
        if(bounds.top<main{ownActivity!!.window.decorView.height}*0.2f){swipe(false);Thread.sleep(250)}
        val centered=checkNotNull(ownNode{it.isEditable})
        try{centered.getBoundsInScreen(bounds);tapAt(bounds.exactCenterX(),bounds.exactCenterY())}finally{centered.recycle()}
        val end=SystemClock.elapsedRealtime()+8_000
        while(SystemClock.elapsedRealtime()<end && uiAutomation.windows.none{it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD})Thread.sleep(100)
        val focused=ownNode{it.isEditable}
        try{emit("P86b_FIELD_AFTER,bounds=$bounds,focused=${focused?.isFocused},enabled=${focused?.isEnabled},session=${main{(field(checkNotNull(ownActivity),"model") as ProductViewModel).snapshot.state}},ime_insets=${main{ownActivity!!.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())}},window_types=${uiAutomation.windows.map{it.type}}")}
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
        emit("P86b_IME,value=$value,class=NUMBER,physical_digit_taps=true")
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
        // Return from the bottom of the same long function card to the real header.
        repeat(if(text=="‹ Zum Lagebild")80 else 4){if(containsText(text))return;swipe(false);Thread.sleep(200)}
        // The real function card contains all 200 long pseudonyms before this button.
        // Keep real touch scrolling and production content; allow that known large card.
        repeat(if(text.startsWith("Weitere Funktionen"))80 else if(text=="Zur Aktionsliste")40 else 12){if(containsText(text))return;swipe(true);Thread.sleep(200)}
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
