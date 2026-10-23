package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.sqlite.SQLiteStatement
import net.zetetic.database.CursorWindow
import net.zetetic.database.sqlcipher.SQLiteCursor
import net.zetetic.database.sqlcipher.driver.SQLCipherConnection
import net.zetetic.database.sqlcipher.driver.SQLCipherStatement
import org.pag.data.prototype.b2.*
import org.pag.domain.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** P77 observations only. Bounded cursor/window references live only until their reset.
 * Actual Runtime authority/keyowner/DB; no replacement session, key or payload registry.
 */
class P88StatementDiagnostics(private val app:Context,private val runtime:ProductRuntime) {
    private val prepared=AtomicLong();private val resets=AtomicLong();private val closed=AtomicLong()
    private val cursors=AtomicLong();private val cursorEnds=AtomicLong()
    private val windows=AtomicLong();private val windowEnds=AtomicLong();private val liveWindowsSeen=AtomicLong()
    private val resetErrors=AtomicLong();private val closeErrors=AtomicLong();private val sqlErrors=AtomicLong()
    private val preflightEnds=AtomicLong();private val roomEnds=AtomicLong();private val driverEnds=AtomicLong()
    private val roomWithLiveStatements=AtomicLong();private val driverFailures=AtomicLong()
    private val noteCount=AtomicInteger();private val lifecycleCount=AtomicInteger();private val notes=ConcurrentLinkedQueue<String>()
    private val hooks:ActionPreflightHooks
    init {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        check(app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
        hooks=ActionPreflightHooks(closed={check(it);preflightEnds.incrementAndGet()},
            roomClosed={roomEnds.incrementAndGet();if(prepared.get()!=closed.get())roomWithLiveStatements.incrementAndGet();lifecycle("ROOM_CLOSE_RETURN,live_statements=${prepared.get()-closed.get()}")},
            resources=B2ResourceHooks(statement={decorate(it)},closed={if(!it)driverFailures.incrementAndGet();driverEnds.incrementAndGet();lifecycle("DRIVER_CLOSE_RETURN,resources_closed=$it")}))
        ProductTaskRepository::class.java.getDeclaredField("database").also {
            it.isAccessible=true;it.set(runtime.tasks,ActionDatabaseAccess(app.applicationContext,hooks))
        }
    }
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun vendor(raw:SQLiteStatement):SQLCipherStatement = when(raw) {
        is SQLCipherStatement -> raw
        else -> {
            check(raw.javaClass.name=="net.zetetic.database.sqlcipher.driver.JournalModeStatement")
            field(raw,"delegate") as SQLCipherStatement
        }
    }
    private fun cursor(raw:SQLCipherStatement)=field(raw,"cursor") as? android.database.Cursor
    private fun note(s:String){if(noteCount.incrementAndGet()<=8)notes.add(s)}
    private fun lifecycle(s:String){if(lifecycleCount.incrementAndGet()<=24)notes.add(s)}
    private fun decorate(raw:SQLiteStatement):SQLiteStatement {
        val v=vendor(raw);prepared.incrementAndGet()
        var observedCursor:android.database.Cursor?=null;var observedWindow:CursorWindow?=null
        fun capture() {
            val c=cursor(v)
            if(c!=null && c!==observedCursor){check(observedCursor==null);observedCursor=c;cursors.incrementAndGet()}
            val w=(c as? SQLiteCursor)?.window
            if(w!=null && w!==observedWindow){check(observedWindow==null);observedWindow=w;windows.incrementAndGet();if(w.mWindowPtr!=0L)liveWindowsSeen.incrementAndGet()}
        }
        return object:SQLiteStatement by raw {
            override fun step():Boolean {
                try{return raw.step()}
                catch(t:Throwable){sqlErrors.incrementAndGet();note("VENDOR_STEP_EXCEPTION,type=${t.javaClass.name}");throw t}
                finally{capture()}
            }
            override fun reset() {
                capture();val c=observedCursor;val w=observedWindow
                try{raw.reset()}catch(t:Throwable){resetErrors.incrementAndGet();throw t}
                resets.incrementAndGet()
                if(c!=null){check(c.isClosed && cursor(v)==null);cursorEnds.incrementAndGet()}
                if(w!=null){check(w.mWindowPtr==0L);windowEnds.incrementAndGet()}
                if(c!=null)note("RESET_RETURN,cursor_closed=true,window_zero=${w?.mWindowPtr==0L}")
                observedCursor=null;observedWindow=null
            }
            override fun close() {
                // This is downstream of the existing B2StatementCleanup reset, not a new fix.
                check(cursor(v)==null && observedCursor==null && observedWindow==null)
                try{raw.close()}catch(t:Throwable){closeErrors.incrementAndGet();throw t}
                check(field(v,"closed")==true);closed.incrementAndGet()
            }
        }
    }
    /** Deliberately omit the PROJECT wrapper in this negative control only, on an already
     * authorized native connection. reset in finally cleans every control cursor/window.
     */
    private fun compareVendor(db:net.zetetic.database.sqlcipher.SQLiteDatabase) {
        for(mode in listOf("close_only","reset_then_close")) {
            val s=SQLCipherConnection(db).prepare("SELECT id FROM task_events WHERE actionId='p11-active' ORDER BY occurredAt,rowid LIMIT 32") as SQLCipherStatement
            var c:SQLiteCursor?=null;var w:CursorWindow?=null
            try {
                s.use {
                    check(it.step());c=cursor(s) as SQLiteCursor;w=checkNotNull(c!!.window)
                    check(!c!!.isClosed && w!!.mWindowPtr!=0L)
                    if(mode=="reset_then_close")it.reset()
                }
                val cursorOpen=!c!!.isClosed;val windowLive=w!!.mWindowPtr!=0L
                check(field(s,"closed")==true)
                check(cursorOpen==(mode=="close_only") && windowLive==cursorOpen)
                notes.add("VENDOR_CONTROL,$mode,statement_closed=true,cursor_open=$cursorOpen,window_live=$windowLive")
            } finally {
                try{s.reset()}finally{s.close()}
                check(c?.isClosed!=false && (w==null || w!!.mWindowPtr==0L))
                notes.add("VENDOR_CONTROL_TEARDOWN,$mode,cursor_closed=true,window_zero=true")
            }
        }
    }
    /** A controlled reader through the real ActionDatabaseAccess/Room/B2/keyowner path.
     * Only this scope's borrowed Java array is observed; never a native key-wipe claim.
     */
    fun ownedScope(scenario:String):String {
        check(scenario in setOf("paging","revoke","statement_error"))
        val c=checkNotNull(runtime.session.acquire(AccessScope.READY))
        val owner=(field(runtime,"owner") as AtomicReference<*>).get() as SessionDbKeyOwner
        var lease:ByteArray?=null;var failure:Throwable?=null;var compared=false;var rowOpened=false
        try {
            owner.withDbKey(c){key->
                lease=key
                ActionDatabaseAccess(app.applicationContext,hooks).use(runtime.inventory.database,key,c){db->db.withReadTransaction {
                    usePrepared("SELECT COUNT(*) FROM task_events WHERE actionId=?") {s->s.bindText(1,"p11-active");check(s.step() && s.getLong(0)==5000L)}
                    usePrepared("SELECT id,type,occurredAt FROM task_events WHERE actionId=? ORDER BY occurredAt,rowid LIMIT 32") {s->
                        s.bindText(1,"p11-active");check(s.step());rowOpened=true
                        if(scenario=="paging") {
                            // The observer never grants this access: the outer real scope already did.
                            val raw=findVendor(s)
                            compareVendor(field(raw,"database") as net.zetetic.database.sqlcipher.SQLiteDatabase);compared=true
                            var rows=1;while(s.step())rows++;check(rows==32)
                        } else if(scenario=="revoke") {
                            runtime.session.lock()
                            s.getText(0) // Real post-revoke B2 getter must deny, while finally still cleans.
                            error("P88 revoke did not deny")
                        } else {
                            usePrepared("SELECT abs(-9223372036854775808)"){it.step()}
                            error("P88 vendor SQL overflow did not fail")
                        }
                    }
                }}
            }
        }catch(t:Throwable){failure=t}
        val zero=lease?.all{it==0.toByte()}==true
        lease=null
        check(rowOpened && zero);assertIdle()
        check(failure?.suppressed?.isEmpty()!=false){"P88 cleanup added suppressed failures"}
        when(scenario) {
            "paging"->check(failure==null && compared)
            "revoke"->{check(failure is StorageAccessRevoked);var entered=false;check(runCatching{owner.withDbKey(c){entered=true}}.isFailure && !entered)}
            else->check(failure is B2Exception && failure.failure==B2Failure.KEY_OR_UNREADABLE && sqlErrors.get()>0)
        }
        return "scope=$scenario,row_opened=$rowOpened,borrowed_java_key_zero=$zero,expected_failure=${failure?.javaClass?.name?:"none"},suppressed_failures=${failure?.suppressed?.size?:0},new_lease_after_revoke_denied=${if(scenario=="revoke")"true" else "not_applicable"}"
    }
    // Room passes the B2 wrapper into usePrepared. Walk only known statement wrappers,
    // never a context/key/payload graph; bounded identity detection prevents cycles.
    private fun findVendor(s:SQLiteStatement):SQLCipherStatement {
        var current:Any=s;val seen=java.util.IdentityHashMap<Any,Boolean>()
        repeat(8) {
            if(current is SQLCipherStatement)return current as SQLCipherStatement
            check(seen.put(current,true)==null)
            val next=current.javaClass.declaredFields.filter{!java.lang.reflect.Modifier.isStatic(it.modifiers)}.mapNotNull{f->
                if(SQLiteStatement::class.java.isAssignableFrom(f.type)){f.isAccessible=true;f.get(current)}else null
            }.filterIsInstance<SQLiteStatement>()
            check(next.isNotEmpty());current=next.first()
        }
        error("P88 unsupported statement wrapper")
    }
    fun assertIdle() {
        check(prepared.get()==closed.get() && cursors.get()==cursorEnds.get() && windows.get()==windowEnds.get())
        check(resetErrors.get()==0L && closeErrors.get()==0L && driverFailures.get()==0L)
    }
    fun evidence()="prepared=${prepared.get()},reset_returns=${resets.get()},statement_closed=${closed.get()},cursor_observed=${cursors.get()},cursor_closed=${cursorEnds.get()},window_observed=${windows.get()},window_nonzero_seen=${liveWindowsSeen.get()},window_zero=${windowEnds.get()},vendor_reset_exceptions=${resetErrors.get()},vendor_close_exceptions=${closeErrors.get()},vendor_step_exceptions=${sqlErrors.get()},preflight_ends=${preflightEnds.get()},room_ends=${roomEnds.get()},driver_ends=${driverEnds.get()},room_return_with_live_statements=${roomWithLiveStatements.get()},driver_failures=${driverFailures.get()}"
    fun trace():List<String> = notes.toList()
}
