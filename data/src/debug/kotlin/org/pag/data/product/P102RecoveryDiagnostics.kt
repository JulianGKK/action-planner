package org.pag.data.product

import android.content.Context
import androidx.room3.withWriteTransaction
import org.pag.data.prototype.b2.B2ResourceHooks
import org.pag.domain.*
import org.pag.security.*

/** Exact P77 package only. Synthetic corrupt-state/fault producer, NEVER an authority or
 * production recovery. Delegate native key operations; distinguish injected faults. */
class P102RecoveryDiagnostics(private val app:Context,private val runtime:ProductRuntime) {
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private val repository=runtime.leadership
    private val original=field(repository,"device") as LeadershipDeviceKey
    @Volatile var aliasUnknown=false
    @Volatile var aliasUnusable=false
    @Volatile var lockAfterDelete=false
    @Volatile var failAfterDelete=false
    @Volatile var deletes=0
    init {
        check(app.packageName=="org.pag.prototype.a.p77runtime" && app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
        val observed=object:LeadershipDeviceKey by original {
            override fun present(alias:String):Boolean {
                if(aliasUnknown)throw LeadershipCryptoFailure()
                return original.present(alias)
            }
            override fun usable(alias:String)=!aliasUnusable && original.usable(alias)
            override fun delete(alias:String) {
                original.delete(alias);deletes++
                if(lockAfterDelete){lockAfterDelete=false;runtime.session.lock()}
                if(failAfterDelete){failAfterDelete=false;throw LeadershipCryptoFailure()}
            }
        }
        repository.javaClass.getDeclaredField("device").let{it.isAccessible=true;it.set(repository,observed)}
    }
    fun aliasPresent()=original.present("org.pag.product.leadership.aes.v1.p11-preparation")
    fun abortAfterFirstReservedScope() {
        var fired=false
        val hooks=ActionPreflightHooks(resources=B2ResourceHooks(closed={ended->
            check(ended)
            if(!fired){fired=true;error("P102_INJECTED_AFTER_RESERVED_SCOPE_CLOSE")}
        }))
        repository.javaClass.getDeclaredField("database").let{it.isAccessible=true;it.set(repository,ActionDatabaseAccess(app,hooks))}
    }
    fun failFinalRecoveryCleanup() {
        var scopes=0
        val hooks=ActionPreflightHooks(resources=B2ResourceHooks(closed={ended->
            check(ended);scopes++
            if(scopes==2)error("P102_INJECTED_AFTER_FINAL_DRIVER_CLOSE")
        }))
        repository.javaClass.getDeclaredField("database").let{it.isAccessible=true;it.set(repository,ActionDatabaseAccess(app,hooks))}
    }
    fun syntheticSensitiveRowUnderReserved(personId:String) {
        val c=runtime.session.acquire(AccessScope.READY)?:error("No original READY")
        val owner=field(runtime,"owner") as java.util.concurrent.atomic.AtomicReference<*>
        (owner.get() as SessionDbKeyOwner).withDbKey(c){key->ActionDatabaseAccess(app).use(runtime.inventory.database,key,c){d->
            d.withWriteTransaction{check(d.leadership().get("p11-preparation")?.state=="RESERVED")
                d.leadership().insertArea(SensitiveRow("p11-preparation",personId,"IDENTITY",1,1,ByteArray(60),ByteArray(33)))}
        }}
    }
    companion object {
        /** Explicit test-runner cleanup of one RECORDED crashed own P77 fixture, no app reset.
         * Existing root guard verifies no links/root replacement; expected inode from run-as stat. */
        fun cleanupRecordedCrash(app:Context,name:String,inode:Long) {
            check(app.packageName=="org.pag.prototype.a.p77runtime" && app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0)
            check(name.matches(Regex("p11-fixture-[0-9a-f-]{36}")))
            val parent=app.noBackupFilesDir.canonicalFile
            val root=java.io.File(parent,name)
            check(parent.listFiles().orEmpty().filter{it.name.startsWith("p11-fixture-")}.map{it.name}==listOf(name))
            check(android.system.Os.lstat(root.path).st_ino==inode)
            val key=AndroidLeadershipDeviceKey();val alias="org.pag.product.leadership.aes.v1.p11-preparation"
            if(key.present(alias)){check(key.usable(alias));key.delete(alias)}
            check(!key.present(alias))
            P11FixtureRoot(parent.toPath(),root.toPath()).close()
            check(!root.exists())
        }
    }
}
