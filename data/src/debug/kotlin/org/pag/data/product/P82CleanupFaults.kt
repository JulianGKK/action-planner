package org.pag.data.product

import androidx.sqlite.SQLiteStatement
import net.zetetic.database.sqlcipher.driver.SQLCipherStatement
import org.pag.data.prototype.b2.*
import java.util.concurrent.atomic.AtomicReference

/** P77-only faults INSIDE the real Room transaction/statement/DB/key scopes.
 * Native SQL/getter errors and injected post-cleanup exceptions are reported separately.
 * References are bounded diagnostics, released by verify(); never logged as payload/keys.
 */
class P82CleanupFaults {
    @Volatile private var armed = false
    private var scenario = "success"
    private var revoke: () -> Unit = {}
    private var lease: ByteArray? = null
    private val failure = AtomicReference<Throwable?>()
    private val resetFailure = IllegalStateException("P82_INJECTED_RESET_AFTER_NATIVE_RESET")
    private val closeFailure = IllegalStateException("P82_INJECTED_CLOSE_AFTER_NATIVE_CLOSE")
    private val rowFailure = IllegalStateException("P82_INJECTED_ROOM_ROWLOOP")
    private val roomFailure = IllegalStateException("P82_INJECTED_AFTER_ROOM_CLOSE")
    private val rows = java.util.concurrent.atomic.AtomicInteger()
    private var statements = 0
    private var rollbacks = 0
    private var closedStatements = 0
    private var driverClosed = false
    private var roomClosed = false
    private var preflightClosed = false
    private var injected = false
    private val diagnosticFailure=AtomicReference<Throwable?>()
    @Volatile private var reached=false
    private val cursors = mutableListOf<net.zetetic.database.sqlcipher.SQLiteCursor>()
    private val windows = mutableListOf<net.zetetic.database.CursorWindow>()
    fun arm(name: String, revoke: () -> Unit) {
        check(name in setOf("success", "commit_revoke", "row_and_room_end", "native_step", "native_getter", "row_failure", "row_revoke",
            "reset_after", "close_after", "both_after", "room_end", "driver_end", "key_end", "cleanup_revoke"))
        scenario = name; this.revoke = revoke; armed=true
    }
    private fun capture(raw: SQLCipherStatement) {
        val cursor = raw.javaClass.getDeclaredField("cursor").let { it.isAccessible=true; it.get(raw) }
            as? net.zetetic.database.sqlcipher.SQLiteCursor ?: return
        if(cursors.none { it === cursor }) { check(cursors.size < 512); cursors.add(cursor) }
        cursor.window?.let { w -> if(windows.none { it === w }) { check(windows.size < 512); windows.add(w) } }
    }
    internal val hooks = CoreReadTestHooks(
        database = ActionPreflightHooks(closed = {if(armed)preflightClosed=it}, resources = B2ResourceHooks(statement = { raw ->
            if(!armed) return@B2ResourceHooks raw
            val vendor = when {
                raw is SQLCipherStatement -> raw
                raw.javaClass.name=="net.zetetic.database.sqlcipher.driver.JournalModeStatement" ->
                    raw.javaClass.getDeclaredField("delegate").let {it.isAccessible=true;it.get(raw) as SQLCipherStatement}
                else -> error("Unsupported vendor statement diagnostic")
            }
            if(vendor.javaClass.getDeclaredField("sql").let { it.isAccessible=true; it.get(vendor)=="ROLLBACK TRANSACTION" })rollbacks++
            statements++
            object: SQLiteStatement by raw {
                override fun step():Boolean = try {raw.step()}catch(t:Throwable){diagnosticFailure.compareAndSet(null,t);throw t}
                override fun getColumnType(index:Int):Int = try{raw.getColumnType(index)}catch(t:Throwable){diagnosticFailure.compareAndSet(null,t);throw t}
                override fun isNull(index:Int):Boolean = try{raw.isNull(index)}catch(t:Throwable){diagnosticFailure.compareAndSet(null,t);throw t}
                override fun getLong(index:Int):Long = try{raw.getLong(index)}catch(t:Throwable){diagnosticFailure.compareAndSet(null,t);throw t}
                override fun getText(index:Int):String = try{raw.getText(index)}catch(t:Throwable){diagnosticFailure.compareAndSet(null,t);throw t}
                override fun reset() {
                    try { capture(vendor); raw.reset() } catch(t:Throwable) {diagnosticFailure.compareAndSet(null,t);throw t}
                    if(!injected && scenario in setOf("reset_after", "both_after") && cursors.isNotEmpty()) {
                        reached=true
                        if(scenario=="reset_after")injected=true
                        throw resetFailure
                    }
                }
                override fun close() {
                    raw.close(); closedStatements++
                    if(!injected && scenario in setOf("close_after", "both_after") && cursors.isNotEmpty()) {
                        injected=true;reached=true;throw closeFailure
                    }
                }
            }
        }, closed = { closed ->
            if(armed)driverClosed=closed
            if(armed && scenario=="driver_end") {reached=true;error("P82_INJECTED_AFTER_DRIVER_CLOSE")}
            if(armed && scenario=="cleanup_revoke") {reached=true;revoke()}
        }), roomClosed = {
            if(armed)roomClosed=true
            if(armed && scenario in setOf("room_end", "row_and_room_end")) {reached=true;throw roomFailure}
        }, driverHooks=B2Hooks(beforeCommit={if(armed && scenario=="commit_revoke") {reached=true;revoke()}})),
        transaction = { connection ->
            // Real SQLCipher execution error, inside the actual Room read transaction.
            if(scenario=="native_step") { reached=true; connection.usePrepared("SELECT abs(-9223372036854775808)") { it.step() } }
            if(scenario=="native_getter") { reached=true; connection.usePrepared("SELECT 1") { check(it.step()); it.getLong(99) } }
        },
        row = { phase -> if(armed && phase==ActiveCoreStore.Phase.PEOPLE_ROW && rows.incrementAndGet()==1) {
            reached=true
            if(scenario in setOf("row_failure", "row_and_room_end"))throw rowFailure
            if(scenario=="row_revoke")revoke()
        } },
        keyLease = { if(armed) { check(lease==null); lease=it } },
        keyScopeEnd = { if(armed && scenario=="key_end") {reached=true;error("P82_INJECTED_INSIDE_KEY_SCOPE")} },
        failure = { if(armed)failure.set(it) }
    )
    fun nativeDiagnostic() = diagnosticFailure.get()?.stackTraceToString() ?: "none"
    fun diagnostic() = "scenario=$scenario,statements=$statements,rows=${rows.get()},reached=$reached,driverClosed=$driverClosed,roomClosed=$roomClosed,error=${failure.get()?.stackTraceToString()},diagnostic=${diagnosticFailure.get()?.stackTraceToString()}"
    fun finished() = failure.get()!=null
    /** Only after worker completion; no diagnostic rescue contributes to these assertions. */
    fun verify():String {
        try {
            check(lease!=null && lease!!.all { it==0.toByte() }) {"Borrowed lease not wiped"}
            check(driverClosed) {"Native DB/connection/driver key copy not confirmed closed"}
            check(roomClosed) {"Room close did not return"}
            check(preflightClosed) {"Preflight native DB close not confirmed"}
            check(statements>0 && statements==closedStatements) {"Statement close count mismatch"}
            check(cursors.isNotEmpty() && cursors.all { it.isClosed }) {"Cursor not closed"}
            check(windows.isNotEmpty() && windows.all { it.mWindowPtr==0L }) {"Window handle remains open"}
            val error=failure.get()
            if(scenario!="success") check(reached) {"HARNESS: target fault was never reached: ${diagnostic()}"}
            if(scenario=="success")check(error==null) else check(error!=null)
            if(scenario in setOf("native_step","native_getter"))check(diagnosticFailure.get()!=null) {"Native failure not observed at raw vendor call"}
            if(scenario=="commit_revoke")check(rollbacks>0) {"Denied transaction-end rollback not observed"}
            if(scenario=="row_and_room_end") {
                check(error===rowFailure) {"Room cleanup masked original row failure"}
                check(error.suppressed.any{it===roomFailure}) {"Room close failure not suppressed"}
            }
            if(scenario=="both_after") {
                // Room may add its transaction/connection failures too; the original reset
                // exception and its close suppression must remain reachable.
                fun contains(t:Throwable, wanted:Throwable):Boolean = t===wanted || t.suppressed.any{contains(it,wanted)} ||
                    (t.cause?.let{contains(it,wanted)} ?: false)
                check(contains(checkNotNull(error),resetFailure)) {"Primary reset failure lost"}
                check(resetFailure.suppressed.any { it===closeFailure }) {"Secondary close failure lost"}
            }
            return "scenario=$scenario,statements=$statements,rollbacks=$rollbacks,closed=$closedStatements,cursors=${cursors.size},windows=${windows.size},window_ptrs_zero=true,preflight_closed=$preflightClosed,room_closed=$roomClosed,driver_closed=$driverClosed,lease_wiped=true,failure=${error?.javaClass?.simpleName ?: "none"},vendor_error=${diagnosticFailure.get()?.javaClass?.simpleName ?: "none"},fault_reached=$reached,suppressed=${error?.suppressed?.size ?: 0},rows=${rows.get()}"
        } finally { armed=false;lease=null;cursors.clear();windows.clear();revoke={} }
    }
}
