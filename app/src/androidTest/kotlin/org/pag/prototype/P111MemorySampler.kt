package org.pag.prototype

import android.app.Activity
import android.app.Instrumentation
import android.os.*
import org.json.JSONObject
import org.pag.data.product.P77ProductRuntimeHarness
import org.pag.data.product.P86WorkspaceDiagnostics
import org.pag.prototype.product.ProductViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** TEST measurement only. Streams scalar samples; never stores payloads, grants authority,
 * changes clocks/limits, invokes GC, or treats a memory decrease as reference retirement.
 * Sampling/JSON/FD enumeration and UiAutomation are explicit measurement overhead. */
internal class P111MemorySampler(private val instrument:Instrumentation,args:Bundle):AutoCloseable {
    init { check(instrument.targetContext.packageName=="org.pag.prototype.a.p77runtime") }
    private val scenario=args.getString("p111Id")?:error("Missing measurement ID")
    private val apk=args.getString("p111ApkHash")?:error("Missing measured APK hash")
    private val running=AtomicBoolean(true)
    private val phase=AtomicReference("BOOTSTRAP_NO_RUNTIME")
    private val ui=AtomicReference("{}")
    @Volatile private var harness:P77ProductRuntimeHarness?=null
    @Volatile private var activity:Activity?=null
    @Volatile private var model:ProductViewModel?=null
    @Volatile private var resources:P86WorkspaceDiagnostics?=null
    private val main=Handler(Looper.getMainLooper())
    private val pending=AtomicBoolean(false)
    private val thread=Thread({
        while(running.get()){
            val begin=SystemClock.elapsedRealtimeNanos()
            try{sample(begin)}catch(t:Throwable){output(JSONObject().put("kind","sample_error").put("error",t.javaClass.simpleName).put("elapsed_ns",begin))}
            try{Thread.sleep(500)}catch(_:InterruptedException){break}
        }
    },"P111-scalar-sampler").apply{start()}
    fun bind(h:P77ProductRuntimeHarness,a:Activity,m:ProductViewModel){check(!h.runtime.activeCore.reuseEnabled);resources=P86WorkspaceDiagnostics(instrument.targetContext,h.runtime,true);harness=h;activity=a;model=m;mark("BOUND_LOCKED")}
    fun mark(value:String){phase.set(value.substringBefore(',').take(96))}
    private fun output(value:JSONObject){
        value.put("scenario_id",scenario).put("apk_sha256",apk).put("pid",Process.myPid())
            .put("api",Build.VERSION.SDK_INT).put("model",Build.MODEL).put("utc_ms",System.currentTimeMillis())
        instrument.sendStatus(0,Bundle().apply{putString("stream","P111_JSON "+value.toString()+"\n")})
    }
    private fun sample(begin:Long){
        val h=harness
        val actual=h?.runtime?.session?.snapshot()
        val runtime=Runtime.getRuntime()
        val mem=Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}
        var fixture=0;var window=0;var graphics=0;var unresolved=0
        val fds=File("/proc/self/fd").listFiles().orEmpty()
        for(fd in fds)try{
            val target=java.nio.file.Files.readSymbolicLink(fd.toPath()).toString()
            if(target.contains("p11-fixture-"))fixture++
            if(target.contains("CursorWindow",true))window++
            if(target.contains("dmabuf",true)||target.contains("/dev/kgsl",true)||target.contains("/dev/dri",true))graphics++
        }catch(_:Exception){unresolved++}
        val stats=Debug.getRuntimeStats()
        val gc=JSONObject();for(k in listOf("art.gc.gc-count","art.gc.gc-time","art.gc.blocking-gc-count","art.gc.blocking-gc-time","art.gc.bytes-allocated","art.gc.bytes-freed"))gc.put(k,stats[k]?:JSONObject.NULL)
        val categories=JSONObject();for((k,v) in mem.memoryStats)categories.put(k,v)
        if(pending.compareAndSet(false,true)){
            val posted=SystemClock.elapsedRealtimeNanos()
            main.post{
                try{
                    val a=activity;val m=model
                    val state=JSONObject().put("main_queue_delay_ns",SystemClock.elapsedRealtimeNanos()-posted)
                        .put("observed_ns",SystemClock.elapsedRealtimeNanos()).put("focus",a?.hasWindowFocus()?:false)
                        .put("ui_session",m?.snapshot?.state?.name?:"NO_RUNTIME")
                        .put("workspace_error",m?.workspaceError?:false).put("detail_loading",m?.detailLoading?:false).put("persons_loading",m?.personsLoading?:false).put("workspace_refreshing",m?.workspaceRefreshing?:false)
                        .put("refusal",m?.workspaceRefusal?.name?:JSONObject.NULL)
                        .put("ime_visible",if(Build.VERSION.SDK_INT>=30)a?.window?.decorView?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())?:false else false)
                    if(a is P86FullWorkspaceActivity)state.put("composition_pulse_committed",a.p110Composed).put("row_renders",a.p110RowRenders)
                    ui.set(state.toString())
                }finally{pending.set(false)}
            }
        }
        output(JSONObject().put("kind","sample").put("elapsed_ns",begin).put("sample_end_ns",SystemClock.elapsedRealtimeNanos())
            .put("phase",phase.get()).put("session",actual?.state?.name?:"NO_RUNTIME")
            .put("generation",actual?.generation?:-1).put("remaining_absolute_ms",h?.runtime?.session?.remainingAbsoluteMillis()?:JSONObject.NULL)
            .put("ledger",h?.outstandingState()?:"NO_RUNTIME").put("known_repository_resources",resources?.sample()?:"UNBOUND")
            .put("full_materializations",h?.observations?.workspaceMaterializations?.get()?:0)
            .put("core_reader_starts",h?.observations?.reads?.get()?:0).put("core_payload_rows",h?.memory?.payloadRows?.get()?:0).put("core_requested_retained",h?.memory?.retained?:0).put("core_requested_transition",h?.memory?.transition?:0).put("core_admission_decision",h?.memory?.decision?:"UNBOUND").put("core_native",h?.memory?.nativeState(includeWindowRows=false)?:"UNBOUND")
            .put("art_used_bytes",runtime.totalMemory()-runtime.freeMemory()).put("art_committed_bytes",runtime.totalMemory()).put("art_max_bytes",runtime.maxMemory())
            .put("native_allocated_bytes",Debug.getNativeHeapAllocatedSize()).put("native_size_bytes",Debug.getNativeHeapSize()).put("native_free_bytes",Debug.getNativeHeapFreeSize())
            .put("pss_kib",mem.totalPss).put("private_dirty_kib",mem.totalPrivateDirty).put("private_clean_kib",mem.totalPrivateClean)
            .put("dalvik_pss_kib",mem.dalvikPss).put("native_pss_kib",mem.nativePss).put("other_pss_kib",mem.otherPss)
            .put("categories",categories).put("gc",gc).put("fd_count",fds.size).put("fixture_file_fd",fixture)
            .put("cursor_window_fd",window).put("graphics_fd",graphics).put("fd_unresolved",unresolved)
            .put("ui",JSONObject(ui.get())).put("physical_peak_bound",false))
    }
    override fun close(){mark("POST_PROBE_LOCK_CLEANUP");Thread.sleep(1500);running.set(false);thread.interrupt();thread.join(2000);harness=null;activity=null;model=null}
    companion object {
        fun start(i:Instrumentation,args:Bundle?):P111MemorySampler?=if(args?.getString("p111")=="true")P111MemorySampler(i,args) else null
    }
}
