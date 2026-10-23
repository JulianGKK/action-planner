package org.pag.data.product

import android.content.Context
import androidx.sqlite.SQLiteStatement
import org.pag.data.prototype.b2.B2ResourceHooks
import org.pag.domain.TaskRepository
import java.util.concurrent.CopyOnWriteArrayList

/** P77-only observation of the actual writer COUNT result, no extra query/key/authority.
 * Only synthetic action IDs and scalar counts are retained, never task/event contents.
 */
class P87TaskEventDiagnostics(app:Context,repository:TaskRepository) {
    private val results=CopyOnWriteArrayList<Pair<String,Long>>()
    init {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        check(app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
        val hooks=ActionPreflightHooks(resources=B2ResourceHooks(statement={raw->observe(raw)}))
        ProductTaskRepository::class.java.getDeclaredField("database").also {
            it.isAccessible=true;it.set(repository,ActionDatabaseAccess(app.applicationContext,hooks))
        }
    }
    private fun observe(raw:SQLiteStatement):SQLiteStatement {
        if(raw !is net.zetetic.database.sqlcipher.driver.SQLCipherStatement)return raw
        fun field(n:String)=raw.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(raw)}
        val sql=(field("sql") as String).replace("`", "").trim().replace(Regex("\\s+")," ")
        if(sql!="SELECT COUNT(*) FROM task_events WHERE actionId=?")return raw
        return object:SQLiteStatement by raw {
            override fun getLong(index:Int):Long=raw.getLong(index).also { count ->
                check(index==0)
                val bindings=field("bindings") as Map<*,*>
                results.add((bindings[1] as String) to count)
            }
        }
    }
    fun counts(actionId:String)=results.filter{it.first==actionId}.map{it.second}
}
