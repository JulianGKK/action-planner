package org.pag.data.product

import android.content.Context
import androidx.sqlite.SQLiteStatement
import org.pag.data.prototype.b2.B2ResourceHooks
import java.util.Collections

/** Isolated P77 diagnostics. No keys, payloads or bound identifiers are recorded.
 * Reflection decorates the existing repository DB seam, never a replacement read path.
 * EXPLAIN runs before the measured step, on the same vendor connection/transaction.
 */
class P85PagingDiagnostics(app:Context, repository:org.pag.domain.TaskRepository) {
    @Volatile private var enabled=false
    @Volatile var explain=false
    @Volatile var failEventRead=false
    private val samples=Collections.synchronizedList(mutableListOf<String>())
    private val plans=HashSet<String>()
    init {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        check(app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
        val hooks=ActionPreflightHooks(timing={p,n->if(enabled)samples.add("DB,$p,ns=$n")},
            resources=B2ResourceHooks(statement={raw->decorate(raw)}))
        ProductTaskRepository::class.java.getDeclaredField("database").also {
            it.isAccessible=true;it.set(repository,ActionDatabaseAccess(app.applicationContext,hooks))
        }
    }
    fun begin(){samples.clear();enabled=true}
    fun end():List<String>{enabled=false;return synchronized(samples){samples.toList()}}
    private fun decorate(raw:SQLiteStatement):SQLiteStatement {
        if(!enabled || raw !is net.zetetic.database.sqlcipher.driver.SQLCipherStatement)return raw
        fun field(n:String)=raw.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(raw)}
        val sql=field("sql") as String
        val started=System.nanoTime()
        var stepping=0L;var blobs=0;var bytes=0L;var first=true;var explaining=0L
        return object:SQLiteStatement by raw {
            override fun step():Boolean {
                if(first){
                    first=false
                    if(failEventRead && sql.contains("FROM task_events")){failEventRead=false;error("P85 synthetic statement failure")}
                    if(explain && sql.startsWith("SELECT") && plans.add(sql)) {
                        val start=System.nanoTime()
                        val db=field("database") as net.zetetic.database.sqlcipher.SQLiteDatabase
                        @Suppress("UNCHECKED_CAST") val bindings=field("bindings") as Map<Int,Any?>
                        val args=(1..(bindings.keys.maxOrNull()?:0)).map{bindings[it]?.toString()}.toTypedArray()
                        db.rawQuery("EXPLAIN QUERY PLAN $sql",args).use { c ->
                            while(c.moveToNext())samples.add("EQP,sql=$sql,detail=${c.getString(3)}")
                        }
                        explaining+=System.nanoTime()-start
                    }
                }
                val start=System.nanoTime()
                return try{raw.step()}finally{stepping+=System.nanoTime()-start}
            }
            override fun getBlob(index:Int):ByteArray=raw.getBlob(index).also{blobs++;bytes+=it.size}
            override fun close(){try{raw.close()}finally{
                samples.add("SQL,ns=${System.nanoTime()-started-explaining},step_ns=$stepping,blobs=$blobs,bytes=$bytes,sql=$sql")
            }}
        }
    }
}
