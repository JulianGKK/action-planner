package org.pag.data.product

import androidx.sqlite.SQLiteStatement
import org.pag.data.prototype.b2.B2ResourceHooks
import org.pag.domain.*
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** P77-only measurement controls, no payload owner, budget registry or authority.
 * Native observations cover known handles, never allocator sizes. Key reference is diagnostic
 * overhead only, dropped after finally is verified. No GC, heap dump or rescue cleanup.
 */
class P83MemoryDiagnostics {
    @Volatile var enabled=false
    @Volatile var park=false
    @Volatile var rowReached=CountDownLatch(1)
    @Volatile var rowRelease=CountDownLatch(1)
    @Volatile var candidateReached=CountDownLatch(1)
    @Volatile var candidateRelease=CountDownLatch(1)
    @Volatile var retained=0L
    @Volatile var transition=0L
    @Volatile var captureConfiguration=false
    @Volatile var sqliteConfig="NOT_CAPTURED"
    val payloadRows=java.util.concurrent.atomic.AtomicInteger()
    @Volatile var decision="NONE"
    @Volatile var failure="none"
    @Volatile var failureDetail="none"
    @Volatile var candidate:WeakReference<ActiveCorePayload> = WeakReference(null)
    @Volatile private var lease:ByteArray?=null
    @Volatile private var roomClosed=false
    @Volatile private var driverClosed=false
    @Volatile private var preflightClosed=false
    private val cursors=java.util.Collections.synchronizedList(mutableListOf<net.zetetic.database.sqlcipher.SQLiteCursor>())
    private val windows=java.util.Collections.synchronizedList(mutableListOf<net.zetetic.database.CursorWindow>())
    private var statements=0
    private var closed=0
    fun begin(hold:Boolean) {
        check(lease==null)
        enabled=true;sqliteConfig="NOT_CAPTURED";payloadRows.set(0);park=hold;retained=0;transition=0;decision="NONE";failure="none";failureDetail="none"
        candidate=WeakReference(null);roomClosed=false;driverClosed=false;preflightClosed=false
        rowReached=CountDownLatch(1);rowRelease=CountDownLatch(1)
        candidateReached=CountDownLatch(1);candidateRelease=CountDownLatch(1)
        cursors.clear();windows.clear();statements=0;closed=0
    }
    fun release() {rowRelease.countDown();candidateRelease.countDown()}
    private fun capture(raw:SQLiteStatement) {
        val vendor=if(raw is net.zetetic.database.sqlcipher.driver.SQLCipherStatement)raw
            else if(raw.javaClass.name=="net.zetetic.database.sqlcipher.driver.JournalModeStatement")
                raw.javaClass.getDeclaredField("delegate").let{it.isAccessible=true;it.get(raw)} else return
        val c=vendor.javaClass.getDeclaredField("cursor").let{it.isAccessible=true;it.get(vendor)}
            as? net.zetetic.database.sqlcipher.SQLiteCursor ?: return
        synchronized(cursors) {if(cursors.none{it===c}){check(cursors.size<512);cursors.add(c)}}
        c.window?.let{w->synchronized(windows){if(windows.none{it===w}){check(windows.size<512);windows.add(w)}}}
    }
    internal val hooks=CoreReadTestHooks(
        transaction={connection->if(enabled && captureConfiguration){
            val values=mutableListOf<String>()
            for(name in listOf("page_size","cache_size","temp_store","wal_autocheckpoint"))connection.usePrepared("PRAGMA $name"){q->if(q.step())values.add("$name="+q.getLong(0))}
            sqliteConfig=values.joinToString(";")
        }},
        database=ActionPreflightHooks(closed={if(enabled)preflightClosed=it},roomClosed={if(enabled)roomClosed=true},
            resources=B2ResourceHooks(statement={raw->
                if(!enabled)raw else {
                    statements++
                    object:SQLiteStatement by raw {
                        override fun step():Boolean=try{raw.step()}finally{capture(raw)}
                        override fun reset(){capture(raw);raw.reset()}
                        override fun close(){raw.close();closed++}
                    }
                }
            },closed={if(enabled)driverClosed=it})),
        keyLease={if(enabled){check(lease==null);lease=it}},
        admission={if(enabled){retained=it.retained;transition=it.transition;decision=it.decision.name}},
        row={phase->if(enabled && (phase==ActiveCoreStore.Phase.PEOPLE_ROW || phase==ActiveCoreStore.Phase.TASK_ROW))payloadRows.incrementAndGet();if(enabled && park && phase==ActiveCoreStore.Phase.PEOPLE_ROW && rowReached.count>0){
            rowReached.countDown();check(rowRelease.await(20,TimeUnit.SECONDS)){"P83 row gate timeout"}
        }},
        candidate={if(enabled){candidate=WeakReference(it.core);candidateReached.countDown()
            if(park)check(candidateRelease.await(20,TimeUnit.SECONDS)){"P83 candidate gate timeout"}}},
        failure={if(enabled){failure=if(it is BoundedActiveRejected)it.status.name else it.javaClass.simpleName;if(captureConfiguration)failureDetail=it.stackTraceToString().take(4096)}}
    )
    fun nativeState(includeWindowRows:Boolean=true):String {
        val cs=synchronized(cursors){cursors.toList()};val ws=synchronized(windows){windows.toList()}
        return "statements=$statements,statements_closed=$closed,cursors=${cs.size},cursors_open=${cs.count{!it.isClosed}},windows=${ws.size},windows_live=${ws.count{it.mWindowPtr!=0L}},window_rows=${if(includeWindowRows)ws.filter{it.mWindowPtr!=0L}.maxOfOrNull{it.numRows} ?: 0 else -1},java_window_initial_size_min=${ws.minOfOrNull{it.windowSizeBytes}?:0},java_window_initial_size_max=${ws.maxOfOrNull{it.windowSizeBytes}?:0},sum_live_java_window_initial_sizes=${ws.filter{it.mWindowPtr!=0L}.sumOf{it.windowSizeBytes.toLong()}},sqlite_config=$sqliteConfig,room_closed=$roomClosed,driver_closed=$driverClosed,preflight_closed=$preflightClosed,keylease_live=${lease?.any{it!=0.toByte()}==true}"
    }
    fun finish():String {
        check(lease!=null && lease!!.all{it==0.toByte()}){"Lease finally not observed"}
        check(driverClosed && roomClosed && preflightClosed)
        check(statements>0 && statements==closed)
        check(cursors.all{it.isClosed} && windows.all{it.mWindowPtr==0L})
        return nativeState()+",lease_wiped=true".also{enabled=false;lease=null;cursors.clear();windows.clear()}
    }
}
