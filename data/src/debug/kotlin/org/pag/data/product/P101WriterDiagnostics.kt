package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.sqlite.SQLiteStatement
import org.pag.data.prototype.b2.B2ResourceHooks
import org.pag.domain.*
import org.pag.security.*
import java.util.concurrent.atomic.AtomicReference

/** P77 only: scalar persisted evidence + one-shot faults AFTER actual own-package key effects.
 * Not a new key/session authority. No key bytes, grants or payloads in observations. */
class P101WriterDiagnostics(private val app:Context,private val runtime:ProductRuntime) {
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private val repository=runtime.leadership
    private val device=field(repository,"device") as LeadershipDeviceKey
    @Volatile var failureMode="none"
    @Volatile var effectObserved="none"
    @Volatile var lockAfterSensitiveWrite=false
    @Volatile var creates=0
    @Volatile var deletes=0
    init {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        check(app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
        @Suppress("UNCHECKED_CAST") val afterWrite=field(repository,"afterSensitiveWrite") as ()->Unit
        repository.javaClass.getDeclaredField("afterSensitiveWrite").let{f->
            f.isAccessible=true;f.set(repository,{
                afterWrite()
                if(lockAfterSensitiveWrite){lockAfterSensitiveWrite=false;runtime.session.lock()}
            })
        }
        val observed=object:LeadershipDeviceKey by device {
            override fun create(alias:String) {
                device.create(alias);creates++
                if(failureMode=="create_after") {
                    failureMode="none";effectObserved=leadershipState()
                    throw LeadershipCryptoFailure() // Explicit injected post-effect failure, NOT a hardware error.
                }
            }
            override fun delete(alias:String) {
                device.delete(alias);deletes++
                if(failureMode=="delete_after") {
                    failureMode="none";effectObserved=leadershipState()
                    throw LeadershipCryptoFailure()
                }
            }
        }
        repository.javaClass.getDeclaredField("device").let{it.isAccessible=true;it.set(repository,observed)}
    }
    fun armDatabaseFault(mode:String) {
        check(mode in setOf("commit_receipt","cleanup_receipt"))
        var driverScopes=0;var fired=false;var reservationWritten=false;var readyWritten=false
        val hooks=ActionPreflightHooks(resources=B2ResourceHooks(statement={raw->
            val vendor=if(raw.javaClass.name=="net.zetetic.database.sqlcipher.driver.JournalModeStatement")field(raw,"delegate")!! else raw
            val sql=field(vendor,"sql") as String
            object:SQLiteStatement by raw {
                override fun step():Boolean {
                    val result=raw.step()
                    val command=sql.trimStart().uppercase(java.util.Locale.ROOT)
                    if(Regex("""^INSERT(?:\s+OR\s+\w+)?\s+INTO\s+["`]?LEADERSHIP["`]?\s*\(""").containsMatchIn(command))reservationWritten=true
                    if(command.startsWith("UPDATE") && command.contains("LEADERSHIP"))readyWritten=true
                    if(mode=="commit_receipt" && !fired && reservationWritten && command.startsWith("END TRANSACTION")) {
                        fired=true
                        val native=field(vendor,"database") as net.zetetic.database.sqlcipher.SQLiteDatabase
                        effectObserved="RESERVATION_END_STEP_RETURNED/native_transaction_live=${native.inTransaction()}/key_creates=$creates"
                        error("P101_INJECTED_AFTER_REAL_END_STEP")
                    }
                    return result
                }
            }
        },closed={knownClosed->
            check(knownClosed);driverScopes++
            if(mode=="cleanup_receipt" && driverScopes==2 && !fired) {
                check(readyWritten) { "P101 target READY write was not reached" }
                fired=true;effectObserved="FINAL_DRIVER_CLOSE_RETURNED"
                error("P101_INJECTED_AFTER_FINAL_DRIVER_CLOSE")
            }
        }))
        repository.javaClass.getDeclaredField("database").let{it.isAccessible=true;it.set(repository,ActionDatabaseAccess(app.applicationContext,hooks))}
    }
    fun writerCompletion(command:Runnable):String {
        val queue=java.util.ArrayDeque<Any>();val seen=java.util.IdentityHashMap<Any,Boolean>();queue.add(command)
        while(queue.isNotEmpty() && seen.size<32) {
            val next=queue.remove();if(next is WorkspaceWriteProgress)return next.completion().let{"outcome=${it.outcome},cleanup=${it.cleanup}"}
            if(seen.put(next,true)!=null)continue
            for(f in next.javaClass.declaredFields)if(!java.lang.reflect.Modifier.isStatic(f.modifiers) && !f.type.isPrimitive) {
                f.isAccessible=true;val value=f.get(next) ?: continue
                if(value is WorkspaceWriteProgress || value.javaClass.name.startsWith("org.pag.data.product.WorkspaceWriteBoundary$") || value.javaClass.name.startsWith("kotlin.jvm.internal.Ref$"))queue.add(value)
            }
        }
        error("P101 original writer progress not found in bounded known closure fields")
    }
    private fun <T> read(work:suspend(ActionDatabase)->T):T {
        val c=checkNotNull(runtime.session.acquire(AccessScope.READY))
        val owner=(field(runtime,"owner") as AtomicReference<*>).get() as SessionDbKeyOwner
        return owner.withDbKey(c){key->ActionDatabaseAccess(app.applicationContext).use(runtime.inventory.database,key,c){d->d.withReadTransaction{work(d)}}}
    }
    fun schemaVersion()=read{db->db.withReadTransaction{usePrepared("PRAGMA user_version"){s->check(s.step());s.getLong(0)}}}
    fun leadershipState()=read{it.leadership().get("p11-preparation")?.state?:"ABSENT"}
    fun personId()=read{it.persons().list("p11-preparation").minBy{p->p.id}.id}
    fun count(table:String,action:String="p11-preparation"):Long {
        check(table in setOf("tasks","task_events","persons","roles","material_items","expenses","protocol_entries","memory_reports","documents","document_jobs","sensitive_person_areas"))
        return read{d->d.withReadTransaction{usePrepared("SELECT COUNT(*) FROM $table WHERE actionId=?"){s->s.bindText(1,action);check(s.step());s.getLong(0)}}}
    }
    fun documentPhase()=read{d->d.withReadTransaction{usePrepared("SELECT phase FROM document_jobs ORDER BY id LIMIT 1"){s->if(s.step())s.getText(0) else "NONE"}}}
    fun cleanupOwnTestAlias(){val alias="org.pag.product.leadership.aes.v1.p11-preparation";device.delete(alias);check(!device.present(alias))}
}
