package org.pag.data.product

import android.content.Context
import androidx.sqlite.SQLiteStatement
import org.pag.data.prototype.b2.B2ResourceHooks
import java.util.concurrent.atomic.AtomicLong

/** P77 observation only: decorate existing DB seams; no payload/key/connection registry.
 * Counts known handle-close returns, never physical allocator or presentation retirement.
 */
class P86WorkspaceDiagnostics(app:Context, runtime:ProductRuntime,allProductRepositories:Boolean=false) {
    private val statements=AtomicLong();private val closed=AtomicLong();private val rows=AtomicLong()
    private val cursorChecks=AtomicLong();private val cursorFailures=AtomicLong()
    private val preflightClosed=AtomicLong();private val roomClosed=AtomicLong();private val driverClosed=AtomicLong()
    private val closeFailures=AtomicLong();private val dbNanos=AtomicLong()
    init {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        check(app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
        val repositories=listOf(runtime.workspace,runtime.actions,runtime.tasks,runtime.operations,runtime.persons)+
            if(allProductRepositories)listOf(runtime.material,runtime.expenses,runtime.protocol,runtime.memoryReports) else emptyList()
        for(repo in repositories) {
            val hooks=ActionPreflightHooks(
                timing={_,ns->dbNanos.addAndGet(ns)},
                closed={if(it)preflightClosed.incrementAndGet() else closeFailures.incrementAndGet()},
                roomClosed={roomClosed.incrementAndGet()},
                resources=B2ResourceHooks(statement={raw->decorate(raw)},closed={if(it)driverClosed.incrementAndGet() else closeFailures.incrementAndGet()}))
            repo.javaClass.getDeclaredField("database").also {
                it.isAccessible=true;it.set(repo,ActionDatabaseAccess(app.applicationContext,hooks))
            }
        }
    }
    private fun decorate(raw:SQLiteStatement):SQLiteStatement {
        statements.incrementAndGet()
        return object:SQLiteStatement by raw {
            override fun step():Boolean=raw.step().also{if(it)rows.incrementAndGet()}
            private fun ending(work:()->Unit) {
                val vendor=if(raw is net.zetetic.database.sqlcipher.driver.SQLCipherStatement)raw else null
                val cursor=vendor?.javaClass?.getDeclaredField("cursor")?.let{it.isAccessible=true;it.get(vendor)} as? net.zetetic.database.sqlcipher.SQLiteCursor
                val window=cursor?.window
                try {
                    work()
                    if(cursor!=null){cursorChecks.incrementAndGet();if(!cursor.isClosed || (window!=null && window.mWindowPtr!=0L))cursorFailures.incrementAndGet()}
                }catch(t:Throwable){closeFailures.incrementAndGet();throw t}
            }
            override fun reset()=ending{raw.reset()}
            override fun close()=ending{raw.close();closed.incrementAndGet()}
        }
    }
    fun sample()="statements=${statements.get()},closed=${closed.get()},rows=${rows.get()},cursor_checks=${cursorChecks.get()},cursor_failures=${cursorFailures.get()},preflight_closed=${preflightClosed.get()},room_closed=${roomClosed.get()},driver_closed=${driverClosed.get()},close_failures=${closeFailures.get()},db_phase_sum_ns=${dbNanos.get()}"
    fun assertIdle(){check(statements.get()==closed.get());check(closeFailures.get()==0L && cursorFailures.get()==0L)}
}
