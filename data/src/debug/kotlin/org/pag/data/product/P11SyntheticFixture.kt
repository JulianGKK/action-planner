package org.pag.data.product

import android.content.Context
import android.content.ContextWrapper
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import org.pag.domain.*

/** Debug benchmark fixture, guarded BEFORE any filesystem/key work. No product package allowed.
 * Task count is explicit for bounded tests; existing benchmark/manual callers keep default 100.
 */
class P11SyntheticFixture(context:Context, worker:Executor=Executor{it.run()}, main:Executor=Executor{it.run()},
    private val taskCountPerAction:Int=100, personCountPerAction:Int=20, baseDirectory:File?=null):AutoCloseable {
    private val app=context.applicationContext.also {
        require(taskCountPerAction>=0)
        require(personCountPerAction in 0..200)
        check(it.packageName in setOf("org.pag.data.unlockprobe","org.pag.perf.p11probe","org.pag.prototype.a.p77runtime"))
        if(baseDirectory!=null) check(it.packageName=="org.pag.prototype.a.p77runtime")
    }
    private val parent=app.noBackupFilesDir.canonicalFile
    internal val base=baseDirectory?.canonicalFile ?: File(parent,"p11-fixture-${UUID.randomUUID()}").apply{check(mkdirs())}
    init {
        check(base.parentFile?.canonicalFile==parent)
        if(baseDirectory!=null) check(base.name.startsWith("p11-fixture-") && base.mkdir())
    }
    private val fixtureRoot=P11FixtureRoot(parent.toPath(),base.toPath())
    private val wrapped=object:ContextWrapper(app) {
        override fun getApplicationContext():Context=this
        override fun getNoBackupFilesDir()=base
    }
    private val key=ByteArray(32).also{SecureRandom().nextBytes(it)}
    private var allowed=true
    private var epoch=1L
    private val owner=AtomicReference<SessionDbKeyOwner?>()
    private val access=object:StorageAccess {
        override fun acquire(scope:AccessScope):StorageContext? {
            if(!allowed || scope!=AccessScope.READY)return null
            val generation=epoch
            return object:StorageContext {
                override val scope=AccessScope.READY
                override val generation=generation
                override fun check(point:AccessPoint)=allowed && epoch==generation
                override fun publish(result:()->Unit):Boolean {if(!check(AccessPoint.PUBLICATION))return false;result();return true}
            }
        }
    }
    private val maintenance=object:StorageContext {
        override val generation=0L
        override val scope=AccessScope.MAINTENANCE
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit)=false
    }
    private val root=File(base,"pag-db-v1")
    private val inventory=ProductInventory(root,{true},{true})
    val preparationId="p11-preparation"
    val activeId="p11-active"
    val completedId="p11-completed"
    init {
        File(root,"store").mkdirs()
        for(name in listOf("intent","complete","public"))File(root,name).writeBytes(byteArrayOf(1))
        File(root,"envelope").writeBytes(ByteArray(256))
        val c=access.acquire(AccessScope.READY)!!
        ActionDatabaseAccess(wrapped).use(inventory.database,key,maintenance,true){db->db.withWriteTransaction {
            for((id,status) in listOf(preparationId to ActionStatus.PREPARATION,activeId to ActionStatus.ACTIVE,completedId to ActionStatus.COMPLETED)) {
                db.actions().insert(ActionRow(id,"Synthetic action","Synthetic benchmark",1,2,0,status.name,completedAt=if(status==ActionStatus.COMPLETED)3 else null))
                repeat(personCountPerAction){i->
                    val p="person-$i"
                    db.persons().insert(PersonRow("$id-$p",id,"Synthetic $i",PersonRules.normalized("Synthetic $i"),i.toLong()))
                    if(status!=ActionStatus.PREPARATION)db.operations().insertState(OperationalRow(id,"$id-$p",null,"NO_STATUS",null,1))
                }
                if(status!=ActionStatus.COMPLETED) {
                    repeat(taskCountPerAction){i->check(TaskStore(db,c){1}.create(id,TaskInput("Synthetic task $i")) is OperationsResult.Value)}
                    repeat(20){i->
                        check(MaterialStore(db,c){1}.create(id,MaterialInput("Synthetic material $i")) is OperationsResult.Value)
                        check(ExpenseStore(db,c){1}.create(id,ExpenseInput("Synthetic expense $i",100,listOf(ExpensePaymentInput("Synthetic",100)))) is OperationsResult.Value)
                    }
                }
                if(status==ActionStatus.ACTIVE)repeat(10){i->
                    check(ProtocolStore(db,c){1}.create(id,status,ProtocolInput(ProtocolClassification.RELEVANT,"Synthetic event $i",1)) is ProtocolResult.Value)
                }
            }
        }}
        owner.set(SessionDbKeyOwner.bind(c,key))
    }
    /** Synthetic data only, before Runtime construction. No production writer bypass. */
    internal fun seedMemoryAcceptance(profile:String,budgetRefusal:Boolean=false) {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        val roles=when(profile){"roles32","p86-stress32"->32;"roles100","p86-stress100"->100;else->6}
        val stress=profile=="stress" || profile.startsWith("p86-stress")
        ActionDatabaseAccess(wrapped).use(inventory.database,key,maintenance){db->db.withWriteTransaction {
            suspend fun exec(sql:String,vararg args:String) { usePrepared(sql){s->args.forEachIndexed{sIndex,v->s.bindText(sIndex+1,v)};s.step()} }
            if(profile.startsWith("p86-"))for((id,label) in listOf(preparationId to "P86 Preparation",activeId to "P86 Active",completedId to "P86 Completed"))
                exec("UPDATE actions SET title=? WHERE id=?",label,id)
            repeat(roles){i->val name=if(profile.startsWith("p86-stress"))"Ä界🦋".repeat(15)+" $i" else "Funktion Ä界🦋 $i"
                db.operations().let { exec("INSERT INTO roles VALUES(?,?,?,?,?)","p83-r$i",activeId,name,OperationalRules.normalizedName(name),"1") }
            }
            // UTF8 byte lengths differ from UTF16 units. Preserve every requested character.
            val personText=if(stress)"Ä界🦋".repeat(15) else "Ä界🦋"
            val taskText=if(stress)"Ä界🦋".repeat(25) else "Ä界🦋"
            exec("UPDATE persons SET pseudonym=?||pseudonym,pseudonymNormalized=?||pseudonymNormalized WHERE actionId=?",personText,PersonRules.normalized(personText),activeId)
            exec("UPDATE tasks SET title=?||title WHERE actionId=?",taskText,activeId)
            exec("UPDATE person_operational_states SET currentRoleId='p83-r0',operationalStatus='ON_SITE' WHERE actionId=?",activeId)
            if(stress)exec("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<4000) INSERT INTO task_events SELECT 'p83-e'||x,?,(SELECT id FROM tasks WHERE actionId=? ORDER BY id LIMIT 1),CASE WHEN x%2=1 THEN 'CANCELLED' ELSE 'REOPENED' END,x+100 FROM n",activeId,activeId)
            // Explicit small boundary/legacy fixtures, never the normal stress profile.
            if(profile.startsWith("p87-")) {
                val total=profile.removePrefix("p87-").toInt()
                check(total in 4999..5001)
                for(id in listOf(preparationId,activeId)) {
                    val extra=total-taskCountPerAction
                    val type="CASE WHEN $extra%2=1 AND x<=3 THEN CASE x WHEN 1 THEN 'STARTED' WHEN 2 THEN 'COMPLETED' ELSE 'REOPENED' END ELSE CASE (x-$extra%2*3)%2 WHEN 1 THEN 'CANCELLED' ELSE 'REOPENED' END END"
                    exec("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<$extra) INSERT INTO task_events SELECT ?||'-limit-'||x,?,(SELECT id FROM tasks WHERE actionId=? ORDER BY id LIMIT 1),$type,x+100 FROM n",id,id,id)
                }
            }
            if(budgetRefusal)exec("UPDATE actions SET description=printf('%0250000d',0) WHERE id=?",activeId)
            if(stress)usePrepared("SELECT COUNT(*) FROM task_events WHERE actionId=?") { s ->
                s.bindText(1,activeId);check(s.step() && s.getLong(0)==TaskRules.MAX_TASK_EVENTS_PER_ACTION && !s.step())
            }
        }}
    }
    internal var p101SeededVersion=11;private set
    /** Exact synthetic legacy seed, before Runtime/Session construction; never product data. */
    internal fun p101LegacyTen() {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        org.pag.data.prototype.b2.B2Native.open(inventory.database,key,maintenance).use{db->
            db.beginTransaction()
            try {
                db.execSQL("DROP TABLE document_jobs");db.execSQL("DROP TABLE documents")
                db.version=10;check(db.version==10);db.setTransactionSuccessful()
            }finally{db.endTransaction()}
        }
        p101SeededVersion=10
    }
    private fun fail():Nothing=error("SYNTHETIC_STORAGE_FAILURE")
    val actions:ActionRepository=ProductActionRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val workspace:WorkspaceRepository=ProductWorkspaceRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val persons:PersonRepository=ProductPersonRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val operations:OperationsRepository=ProductOperationsRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val tasks:TaskRepository=ProductTaskRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val protocol:ProtocolRepository=ProductProtocolRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val material:MaterialRepository=ProductMaterialRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val expenses:ExpenseRepository=ProductExpenseRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val memoryReports:MemoryReportRepository=ProductMemoryReportRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    val documents:DocumentRepository=ProductDocumentRepository(wrapped,access,inventory,owner::get,worker,main,::fail)
    /** Explicit test entry only; not part of the runtime/navigation fixture setup. */
    fun boundedActive(actionId:String=activeId,allowPublication:Boolean=true):BoundedActiveResult {
        val binding=BoundedActiveBinding(actionId,epoch)
        val gated=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                val c=access.acquire(scope) ?: return null
                return object:StorageContext by c {
                    override fun publish(result:()->Unit)=allowPublication && c.publish(result)
                }
            }
        }
        return ProductBoundedActiveReader(wrapped,gated,inventory,owner::get,
            {if(allowed)BoundedActiveBinding(actionId,epoch)else null},::fail).read(binding)
    }
    /** Explicit test entry only; not part of the runtime/navigation fixture setup. */
    fun boundedTasks(actionId:String=activeId,allowPublication:Boolean=true,
        onPublicationAttempt:()->Unit={}):BoundedTaskResult {
        val binding=BoundedActiveBinding(actionId,epoch)
        val gated=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                val c=access.acquire(scope) ?: return null
                return object:StorageContext by c {
                    override fun publish(result:()->Unit):Boolean {
                        onPublicationAttempt()
                        return allowPublication && c.publish(result)
                    }
                }
            }
        }
        return ProductBoundedTaskReader(wrapped,gated,inventory,owner::get,
            {if(allowed)BoundedActiveBinding(actionId,epoch)else null},::fail).read(binding)
    }
    fun databaseRead(report:(String,Long)->Unit) {
        val c=access.acquire(AccessScope.READY) ?: error("SYNTHETIC_REVOKED")
        val start=System.nanoTime();check(inventory.state()==ProductStorageState.READY_TO_OPEN)
        report("PRODUCT_INVENTORY",System.nanoTime()-start)
        owner.get()!!.withDbKey(c){leased->
            ActionDatabaseAccess(wrapped,ActionPreflightHooks(timing={phase,nanos->report(phase.name,nanos)}))
                .use(inventory.database,leased,c){db->db.withReadTransaction{check(db.actions().list().size==3)}}
        }
    }
    /** Real SessionCoordinator and exact product maintenance; authentication/key are synthetic. */
    fun unlockProbe(worker:Executor,main:Executor,report:(String,Long)->Unit):P11UnlockProbe {
        val sessionOwner=AtomicReference<SessionDbKeyOwner?>()
        val authNanos=java.util.concurrent.atomic.AtomicLong()
        val auth=object:org.pag.security.AuthenticationExperiment {
            override fun start(mode:org.pag.security.KeyExperiment,complete:(org.pag.security.AuthenticationOutcome)->Unit):org.pag.security.Cancellation {
                val released=java.util.concurrent.atomic.AtomicBoolean(false)
                val operation=object:org.pag.security.SyntheticOperation {
                    override fun perform()=org.pag.security.OperationResult.SUCCEEDED
                    override fun release(){released.set(true)}
                    override fun useUnwrappedKey(allowed:()->Boolean,consume:(ByteArray)->Unit):org.pag.security.OperationResult {
                        check(!released.get() && allowed())
                        val lease=key.copyOf()
                        try{consume(lease)}finally{lease.fill(0)}
                        return org.pag.security.OperationResult.SUCCEEDED
                    }
                }
                authNanos.set(System.nanoTime())
                complete(org.pag.security.AuthenticationOutcome.Granted(operation,android.os.SystemClock.elapsedRealtime()))
                // Granted synchronously: ownership has transferred to the coordinator.
                return org.pag.security.Cancellation{}
            }
        }
        val session=org.pag.data.SessionCoordinator(auth,Maintenance{Subscription{}},MonotonicTime{android.os.SystemClock.elapsedRealtime()},ProductSessionTiming.policy,{true},
            authorizedMaintenance={c,operation,done->
                val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
                worker.execute {
                    var success=false
                    try {
                        success=operation.useUnwrappedKey({!cancelled.get() && c.check(AccessPoint.STEP)}){leased->
                            reconcileProductDocuments(wrapped,inventory,base,c,leased,false,
                                timing={p,ns->report("MAINTENANCE_${p.name}",ns)}) {
                                val next=SessionDbKeyOwner.bind(c,leased)
                                if(!c.publish{sessionOwner.getAndSet(next)?.invalidate()}){next.invalidate();throw ProductStorageFailure()}
                            }
                        }==org.pag.security.OperationResult.SUCCEEDED && c.check(AccessPoint.STEP)
                    }catch(_:Exception){success=false}
                    finally{operation.release();if(!success)sessionOwner.getAndSet(null)?.invalidate()}
                    main.execute{if(!cancelled.get())done(success)}
                }
                Subscription{cancelled.set(true);sessionOwner.getAndSet(null)?.invalidate()}
            })
        val observer=session.observe{if(it.state in setOf(SessionState.LOCKED,SessionState.BLOCKED))sessionOwner.getAndSet(null)?.invalidate()}
        val actions=ProductActionRepository(wrapped,session,inventory,sessionOwner::get,worker,main,session::storageFailure).also{it.timing={p,ns->report("ACTION_$p",ns)}}
        return P11UnlockProbe(session,actions,authNanos){session.lock();observer.cancel();sessionOwner.getAndSet(null)?.invalidate()}
    }
    fun seedSyntheticMemoryReports(count:Int) {
        require(count in 0..40)
        val c=access.acquire(AccessScope.READY) ?: error("SYNTHETIC_REVOKED")
        owner.get()!!.withDbKey(c){leased->ActionDatabaseAccess(wrapped).use(inventory.database,leased,c){db->
            db.withWriteTransaction{repeat(count){i->check(MemoryReportStore(db,c){1}.create(activeId,"Synthetic report $i",MemorySectionSave(0,emptyMap())) is OperationsResult.Value)}}
        }}
    }
    fun setSyntheticStatus(status:ActionStatus) {
        val c=access.acquire(AccessScope.READY) ?: error("SYNTHETIC_REVOKED")
        owner.get()!!.withDbKey(c){leased->ActionDatabaseAccess(wrapped).use(inventory.database,leased,c){db->
            db.withWriteTransaction {val old=requireNotNull(db.actions().get(activeId));check(db.actions().update(old.copy(status=status.name,completedAt=if(status==ActionStatus.COMPLETED)3 else null))==1)}
        }}
    }
    fun revoke(){allowed=false;epoch++;owner.getAndSet(null)?.invalidate()}
    override fun close(){revoke();key.fill(0);fixtureRoot.close()}

    /** Test-only bridge through the actual product loader/reader/Room/key scopes. No cache. */
    fun activeCore(worker:Executor=Executor{it.run()},main:Executor=Executor{it.run()}):ProductActiveCoreLoader {
        check(app.packageName=="org.pag.data.unlockprobe")
        val mutations=UncachedWorkspaceMutations()
        val reader=ProductActiveCoreReader(wrapped,access,inventory,owner::get,blocked=::fail)
        return ProductActiveCoreLoader(access,mutations,worker,main,readCandidate={ c,t,l,m -> reader.readForTicket(c,t,l,outstanding=m) })
    }
    /** The test authority is fed into the real ProductRuntime only by the isolated P77 debug factory.
     * SessionCoordinator, Maintenance, key owner, Room and core reader remain ProductRuntime's.
     */
    internal fun runtimeAuthority(reuseEnabled:Boolean, observation:ActiveCoreLoadObservation,
        coreAccess:((StorageAccess)->StorageAccess)?=null):ProductRuntimeTestAuthority {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        val synthetic=object:org.pag.security.AuthenticationExperiment {
            override fun start(mode:org.pag.security.KeyExperiment,
                complete:(org.pag.security.AuthenticationOutcome)->Unit):org.pag.security.Cancellation {
                check(mode==org.pag.security.KeyExperiment.STORAGE_UNWRAP)
                val released=java.util.concurrent.atomic.AtomicBoolean(false)
                val operation=object:org.pag.security.SyntheticOperation {
                    override val properties="P77 ISOLATED SYNTHETIC TEST KEY"
                    override fun perform()=org.pag.security.OperationResult.NOT_AUTHORIZED
                    override fun release(){released.set(true)}
                    override fun useUnwrappedKey(allowed:()->Boolean,consume:(ByteArray)->Unit):org.pag.security.OperationResult {
                        if(released.get() || !allowed())return org.pag.security.OperationResult.NOT_AUTHORIZED
                        val lease=key.copyOf()
                        return try {consume(lease);if(allowed())org.pag.security.OperationResult.SUCCEEDED else org.pag.security.OperationResult.NOT_AUTHORIZED}
                            finally {lease.fill(0)}
                    }
                }
                complete(org.pag.security.AuthenticationOutcome.Granted(operation,android.os.SystemClock.elapsedRealtime()))
                return org.pag.security.Cancellation{}
            }
        }
        return ProductRuntimeTestAuthority(base,synthetic,{true},{true},reuseEnabled,observation,coreAccess)
    }
    /** Isolated native fault harness. Same reader/DB; supplied session is not product authentication. */
    internal fun coreForSession(session:StorageAccess,mutations:WorkspaceMutationInvalidation,
        worker:Executor,main:Executor,observe:(ActiveCoreStore.Phase)->Unit={},
        measured:(ActiveCoreStore.Metric,Long)->Unit={_,_->},blocked:()->Unit=::fail):ProductActiveCoreLoader {
        check(app.packageName=="org.pag.data.unlockprobe")
        val c=requireNotNull(session.acquire(AccessScope.READY))
        owner.getAndSet(SessionDbKeyOwner.bind(c,key))?.invalidate()
        val reader=ProductActiveCoreReader(wrapped,session,inventory,owner::get,blocked=blocked)
        return ProductActiveCoreLoader(session,mutations,worker,main,readCandidate={ context,ticket,lane,memory ->
            reader.readForTicket(context,ticket,lane,outstanding=memory,observe=observe,measured=measured)
        })
    }
    internal fun <T> nativeDriverProbe(work:(org.pag.data.prototype.b2.B2Driver)->T):T {
        check(app.packageName=="org.pag.data.unlockprobe")
        val c=requireNotNull(access.acquire(AccessScope.READY))
        return requireNotNull(owner.get()).withDbKey(c) { leased ->
            val dc=object:StorageContext by c { override val scope=AccessScope.MAINTENANCE }
            val driver=org.pag.data.prototype.b2.B2Driver(inventory.database,leased,dc,org.pag.data.prototype.b2.B2Hooks())
            driver.use { work(it) }.also { check(driver.resourcesClosed()) }
        }
    }
    internal fun databasePathForProbe():String {
        check(app.packageName=="org.pag.data.unlockprobe");return inventory.database.path
    }
    internal fun keyLeaseAfterNativeFailureProbe(fail:Boolean):Boolean {
        check(app.packageName=="org.pag.data.unlockprobe")
        val c=requireNotNull(access.acquire(AccessScope.READY))
        var borrowed:ByteArray?=null
        var driver:org.pag.data.prototype.b2.B2Driver?=null
        val expected=IllegalStateException("SYNTHETIC_AFTER_QUERY")
        val result=runCatching {
            requireNotNull(owner.get()).withDbKey(c) { leased ->
                borrowed=leased
                val dc=object:StorageContext by c { override val scope=AccessScope.MAINTENANCE }
                val native=org.pag.data.prototype.b2.B2Driver(inventory.database,leased,dc,org.pag.data.prototype.b2.B2Hooks())
                driver=native
                native.use {
                    it.open(inventory.database.path).use { connection ->
                        connection.prepare("SELECT 1").use { statement ->
                            check(statement.step());check(statement.getLong(0)==1L)
                            if(fail)throw expected
                        }
                    }
                }
            }
        }
        check(if(fail)result.exceptionOrNull()===expected else result.isSuccess)
        return borrowed?.all{it==0.toByte()}==true && driver?.resourcesClosed()==true
    }
    /** Diagnostic RED probe for the pinned adapter. Reflection is confined to debug tests. */
    fun statementCloseReleasesCursor():Boolean {
        check(app.packageName=="org.pag.data.unlockprobe")
        val c=requireNotNull(access.acquire(AccessScope.READY))
        return requireNotNull(owner.get()).withDbKey(c) { leased ->
            val driverContext=object:StorageContext by c { override val scope=AccessScope.MAINTENANCE }
            org.pag.data.prototype.b2.B2Native.open(inventory.database,leased,driverContext).use { native ->
                val statement=net.zetetic.database.sqlcipher.driver.SQLCipherConnection(native).prepare("SELECT 1")
                p11DiagnosticCleanup({
                    check(statement.step())
                    val field=statement.javaClass.getDeclaredField("cursor").apply{isAccessible=true}
                    val cursor=field.get(statement) as android.database.Cursor
                    statement.close()
                    val closed=cursor.isClosed
                    // Retain the native finding even if reset-after-close fails.
                    check(closed) { "VENDOR_CURSOR_OPEN_AFTER_CLOSE" }
                    closed
                }, { statement.reset() }, { statement.close() })
            }
        }
    }
    /** Actual B2Driver query wrapper, not the direct vendor adapter. No cursor escapes. */
    fun productStatementCleanupProbe() {
        check(app.packageName=="org.pag.data.unlockprobe")
        val c=requireNotNull(access.acquire(AccessScope.READY))
        requireNotNull(owner.get()).withDbKey(c) { leased ->
            var denied=false
            val driverContext=object:StorageContext by c {
                override val scope=AccessScope.MAINTENANCE
                override fun check(point:AccessPoint)=!denied && c.check(point)
            }
            val driver=org.pag.data.prototype.b2.B2Driver(inventory.database,leased,driverContext,
                org.pag.data.prototype.b2.B2Hooks())
            driver.use {
                val connection=driver.open(inventory.database.path)
                fun probe(sql:String,read:(androidx.sqlite.SQLiteStatement)->Unit) {
                    val statement=connection.prepare(sql)
                    val vendor=statement.javaClass.declaredFields.map { field ->
                        field.isAccessible=true;field.get(statement)
                    }.filterIsInstance<net.zetetic.database.sqlcipher.driver.SQLCipherStatement>().distinct().single()
                    var cursor:android.database.Cursor?=null
                    p11DiagnosticCleanup({
                        read(statement)
                    }, {
                        val field=vendor.javaClass.getDeclaredField("cursor").apply{isAccessible=true}
                        cursor=field.get(vendor) as android.database.Cursor?
                        check(cursor!=null && !cursor!!.isClosed)
                    }, { statement.close() }, {
                        check(cursor?.isClosed==true) { "PRODUCT_CURSOR_NOT_CLOSED" }
                        statement.close()
                    })
                }
                repeat(5) {
                    probe("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<500) SELECT x FROM n") { s ->
                        var rows=0;while(s.step()){check(s.getLong(0)==(++rows).toLong())};check(rows==500)
                    }
                    probe("SELECT 1 WHERE 0") { check(!it.step()) }
                }
                probe("SELECT 1") { s ->
                    check(s.step());denied=true
                    try { s.getLong(0);error("REVOKE_NOT_ENFORCED") }
                    catch(_:org.pag.data.prototype.b2.StorageAccessRevoked) { }
                }
                denied=false
                probe("SELECT 1") { s ->
                    check(s.step())
                    var failed=false
                    try { s.getLong(99) } catch(_:org.pag.data.prototype.b2.B2Exception) { failed=true }
                    check(failed)
                }
                probe("SELECT 'synthetic'") { s ->
                    check(s.step())
                    var failed=false
                    try { s.getText(99) } catch(_:org.pag.data.prototype.b2.B2Exception) { failed=true }
                    check(failed)
                }
                probe("SELECT abs(-9223372036854775808)") { s ->
                    var failed=false
                    try { s.step() } catch(_:org.pag.data.prototype.b2.B2Exception) { failed=true }
                    check(failed)
                }
                connection.prepare("BEGIN TRANSACTION").use{it.step()}
                connection.prepare("ROLLBACK TRANSACTION").use{it.step()}
                connection.close()
            }
            check(driver.resourcesClosed())
        }
    }
}

class P11UnlockProbe(val session:SessionPort,val actions:ActionRepository,private val authNanos:java.util.concurrent.atomic.AtomicLong,private val cleanup:()->Unit):AutoCloseable {
    val authenticatedNanos get()=authNanos.get()
    override fun close()=cleanup()
}
