package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.sqlite.SQLiteStatement
import net.zetetic.database.CursorWindow
import net.zetetic.database.sqlcipher.SQLiteCursor
import net.zetetic.database.sqlcipher.driver.SQLCipherStatement
import org.pag.data.prototype.b2.*
import org.pag.domain.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** P77-only original-runtime resource observation. No payload registry or rescue cleanup.
 * At most one diagnostic borrowed Java key alias; per-statement cursor/window aliases end
 * at reset. Metadata counters are cumulative, not an admission or native memory budget.
 */
class P100ResourceDiagnostics {
    @Volatile var emit:(String)->Unit={}
    @Volatile var scenario="success"
    val rowReached=CountDownLatch(1)
    val rowRelease=CountDownLatch(1)
    @Volatile var completed=false
    @Volatile var candidateChecked=false
    @Volatile var failureKind="none"
    @Volatile var leaseEnds=0
    @Volatile private var lease:ByteArray?=null
    private var prepared=0;private var closed=0;private var resets=0
    private var cursors=0;private var cursorEnds=0;private var windows=0;private var windowEnds=0
    private var preflights=0;private var rooms=0;private var drivers=0
    private var sqlErrors=0;private var vendorResetErrors=0;private var vendorCloseErrors=0
    private var injected=false
    private var firstRow=false
    private var transactionEnds=0
    private var databasePath=""
    private val nativeDatabases=java.util.IdentityHashMap<net.zetetic.database.sqlcipher.SQLiteDatabase,Any>()
    private val nativeConnections=java.util.IdentityHashMap<Any,Boolean>()
    private var poolEnds=0;private var connectionEnds=0;private var cacheEnds=0
    fun bindDatabase(file:java.io.File){check(databasePath.isEmpty());databasePath=file.path}
    private fun captureDatabase(db:net.zetetic.database.sqlcipher.SQLiteDatabase) {
        val pool=synchronized(checkNotNull(field(db,"mLock"))) {checkNotNull(field(db,"mConnectionPoolLocked"))}
        check(nativeDatabases.size<16 || nativeDatabases.containsKey(db));nativeDatabases[db]=pool
        synchronized(checkNotNull(field(pool,"mLock"))) {
            val found=mutableListOf<Any>()
            field(pool,"mAvailablePrimaryConnection")?.let{found.add(it)}
            found.addAll((field(pool,"mAvailableNonPrimaryConnections") as List<*>).filterNotNull())
            found.addAll((field(pool,"mAcquiredConnections") as Map<*,*>).keys.filterNotNull())
            for(connection in found){check(nativeConnections.size<64 || nativeConnections.containsKey(connection));nativeConnections[connection]=true}
        }
    }
    private fun capturePreflight() {
        // The vendor's active map locates the exact already-authorized preflight handle.
        // Strong aliases + pointer/cache assertions below, NOT WeakMap disappearance, prove ends.
        val f=net.zetetic.database.sqlcipher.SQLiteDatabase::class.java.getDeclaredField("sActiveDatabases").apply{isAccessible=true}
        val active=f.get(null) as Map<*,*>
        val matches=synchronized(active){active.keys.filterIsInstance<net.zetetic.database.sqlcipher.SQLiteDatabase>().filter{it.path==databasePath}}
        check(matches.size==1);captureDatabase(matches.single())
    }
    private fun nativeEnds() {
        check(nativeDatabases.isNotEmpty() && nativeConnections.isNotEmpty())
        for((db,pool) in nativeDatabases) {
            check(!db.isOpen)
            synchronized(checkNotNull(field(pool,"mLock"))) {
                check(field(pool,"mIsOpen")==false && field(pool,"mAvailablePrimaryConnection")==null)
                check((field(pool,"mAvailableNonPrimaryConnections") as List<*>).isEmpty())
                check((field(pool,"mAcquiredConnections") as Map<*,*>).isEmpty())
            }
            poolEnds++
        }
        for(connection in nativeConnections.keys) {
            check(field(connection,"mConnectionPtr")==0L)
            val cache=checkNotNull(field(connection,"mPreparedStatementCache"))
            check(cache.javaClass.getMethod("size").invoke(cache)==0)
            connectionEnds++;cacheEnds++
        }
        nativeDatabases.clear();nativeConnections.clear()
    }
    private fun field(o:Any,n:String):Any?=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o)}
    private fun vendor(s:SQLiteStatement):SQLCipherStatement = if(s is SQLCipherStatement)s else {
        check(s.javaClass.name=="net.zetetic.database.sqlcipher.driver.JournalModeStatement")
        field(s,"delegate") as SQLCipherStatement
    }
    private fun decorate(raw:SQLiteStatement):SQLiteStatement {
        val v=vendor(raw);prepared++
        captureDatabase(field(v,"database") as net.zetetic.database.sqlcipher.SQLiteDatabase)
        val sql=field(v,"sql") as String
        var c:android.database.Cursor?=null;var w:CursorWindow?=null
        fun capture() {
            val next=field(v,"cursor") as? android.database.Cursor
            if(next!=null && c==null){c=next;cursors++}
            (next as? SQLiteCursor)?.window?.let{nextWindow->if(w==null){check(nextWindow.mWindowPtr!=0L);w=nextWindow;windows++}}
        }
        return object:SQLiteStatement by raw {
            override fun step():Boolean {
                try {
                    val result=raw.step()
                    if(sql.trimStart().startsWith("END TRANSACTION",true) || sql.trimStart().startsWith("ROLLBACK",true))transactionEnds++
                    return result
                } catch(t:Throwable){sqlErrors++;throw t} finally{capture()}
            }
            override fun reset() {
                capture()
                try{raw.reset()}catch(t:Throwable){vendorResetErrors++;throw t}
                resets++
                c?.let{check(it.isClosed && field(v,"cursor")==null);cursorEnds++}
                w?.let{check(it.mWindowPtr==0L);windowEnds++}
                c=null;w=null
            }
            override fun close() {
                check(c==null && w==null && field(v,"cursor")==null)
                try{raw.close()}catch(t:Throwable){vendorCloseErrors++;throw t}
                check(field(v,"closed")==true);closed++
            }
        }
    }
    internal val databaseHooks=ActionPreflightHooks(
        completed={if(it==ActionPreflightStep.OPEN)capturePreflight()},
        closed={check(it);nativeEnds();preflights++},
        roomClosed={
            check(prepared==closed);rooms++
            if(scenario=="cleanup_unknown" && !injected){injected=true;error("P100_INJECTED_AFTER_ROOM_CLOSE")}
        },
        resources=B2ResourceHooks(statement={decorate(it)},closed={check(it);nativeEnds();drivers++})
    )
    internal val hooks=CoreReadTestHooks(
        database=databaseHooks,
        keyLease={check(lease==null);lease=it},
        admission={emit("CORE_ADMISSION,retained=${it.retained},transition=${it.transition},decision=${it.decision}")},
        transaction={connection->if(scenario=="sql_error") {
            // Real native SQL-step error while another statement already owns an open cursor.
            connection.usePrepared("SELECT id FROM persons WHERE actionId='p11-active' LIMIT 32"){outer->
                check(outer.step())
                connection.usePrepared("SELECT abs(-9223372036854775808)"){it.step()}
            }
            error("P100 SQL overflow unexpectedly succeeded")
        }},
        row={phase->if(phase==ActiveCoreStore.Phase.PEOPLE_ROW && !firstRow){
            firstRow=true;emit("CORE_ROW,${evidence()},lease_live=${lease?.any{it!=0.toByte()}==true}")
            if(scenario in setOf("lock","cancel")){rowReached.countDown();check(rowRelease.await(60,TimeUnit.SECONDS))}
        }},
        candidate={value->
            check(value.core.people.overall.total==200L && value.core.tasks.overall.open==1000L)
            check(value.core.people.items.size==32 && value.core.tasks.items.size==32)
            check(value.core.people.items.all{it.pseudonym.startsWith("Ä界🦋".repeat(15))})
            check(value.core.tasks.items.all{it.title.startsWith("Ä界🦋".repeat(25))})
            endLease();assertIdle();candidateChecked=true;completed=true
            emit("CORE_CANDIDATE,retained=${value.retainedCharge},transition=${value.transitionCharge},unicode=true,${evidence()}")
        },
        failure={failure->
            failureKind=if(failure is BoundedActiveRejected)failure.status.name else failure.javaClass.simpleName
            endLease();assertIdle();completed=true
            emit("CORE_FAILURE,type=$failureKind,suppressed=${failure.suppressed.size},injected_after_room=$injected,${evidence()}")
        }
    )
    private fun endLease() {
        val borrowed=checkNotNull(lease)
        check(borrowed.all{it==0.toByte()});lease=null;leaseEnds++
    }
    fun rowWasReached()=firstRow
    fun assertIdle(){check(prepared==closed && cursors==cursorEnds && windows==windowEnds);check(vendorResetErrors==0 && vendorCloseErrors==0)}
    fun evidence()="prepared=$prepared,closed=$closed,reset=$resets,cursors=$cursors,cursor_ends=$cursorEnds,windows=$windows,window_ends=$windowEnds,preflights=$preflights,rooms=$rooms,drivers=$drivers,transaction_ends=$transactionEnds,sql_step_errors=$sqlErrors,vendor_reset_errors=$vendorResetErrors,vendor_close_errors=$vendorCloseErrors,lease_ends=$leaseEnds,native_pool_ends=$poolEnds,native_connection_ptr_zero=$connectionEnds,native_cache_empty=$cacheEnds"

    /** Separate non-exposing Room/key scopes, using the ORIGINAL runtime authority. Never
     * resets OutstandingCoreRead or publishes a Core; results are scalar test evidence only.
     */
    fun controlled(app:Context,runtime:ProductRuntime,mode:String,epoch:Long) {
        check(app.packageName=="org.pag.prototype.a.p77runtime")
        check(mode in setOf("batches","R_exact","R_plus1","T_exact","T_plus1"))
        val context=checkNotNull(runtime.session.acquire(AccessScope.READY))
        val owner=(field(runtime,"owner") as AtomicReference<*>).get() as SessionDbKeyOwner
        var borrowed:ByteArray?=null;var cleanup=false;var materializers=0;var refused=false
        try {
            owner.withDbKey(context){key->
                borrowed=key
                ActionDatabaseAccess(app.applicationContext,databaseHooks).use(runtime.inventory.database,key,context,cleanupConfirmed={cleanup=it}){db->
                    db.withReadTransaction {
                        usePrepared("SELECT COUNT(*) FROM task_events WHERE actionId='p11-active'"){check(it.step() && it.getLong(0)==5000L)}
                        if(mode=="batches") {
                            for((table,column,count,prefix) in listOf(
                                listOf("persons","pseudonym","200","Ä界🦋".repeat(15)),
                                listOf("tasks","title","1000","Ä界🦋".repeat(25)))) {
                                var last=0L;var rows=0;var batches=0
                                do {
                                    var batch=0
                                    usePrepared("SELECT rowid,$column FROM $table WHERE actionId=? AND rowid>? ORDER BY rowid LIMIT 32"){s->
                                        s.bindText(1,"p11-active");s.bindLong(2,last)
                                        while(s.step()){val id=s.getLong(0);check(id>last);last=id;check(s.getText(1).startsWith(prefix));batch++;rows++}
                                    }
                                    check(batch<=32);batches++
                                } while(batch==32)
                                check(rows==count.toInt() && batches>1)
                                emit("BATCH,table=$table,rows=$rows,batches=$batches,unicode=true")
                            }
                        } else {
                            val state=CompactTaskState(P3bFreeActiveCoreBinding("p11-active",context.generation),epoch)
                            fun current()=context.check(AccessPoint.STEP)
                            val people=CompactPeopleStore(this,context,::current).prepareCore(state)
                            val tasks=CompactTaskStore(this,context,::current).preparePage(state,CompactTaskQuery(),null)
                            val base=CoreReadAdmission.add(ActiveCoreStore.envelope.retained,CoreReadAdmission.add(people.reservation.retained,tasks.reservation.retained))
                            val trans=CoreReadAdmission.add(ActiveCoreStore.envelope.transition,CoreReadAdmission.add(people.reservation.transition,tasks.reservation.transition))
                            val isR=mode.startsWith("R_");val plus=if(mode.endsWith("plus1"))1 else 0
                            val limit=if(isR)ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES else ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
                            val cost=ActiveWorkspaceBudget.estimateUnits(if(isR)base else trans).second
                            check(cost<limit)
                            val arrayHeader=ActiveWorkspaceBudget.estimateUnits(ActiveWorkspaceBudget.Units(arrays=1)).second
                            val padding=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=limit-cost-arrayHeader+plus)
                            // Disjoint conservative test overhead, NOT a changed production budget.
                            val envelope=if(isR)ActiveCoreStore.envelope.copy(retained=CoreReadAdmission.add(ActiveCoreStore.envelope.retained,padding))
                                else ActiveCoreStore.envelope.copy(transition=CoreReadAdmission.add(ActiveCoreStore.envelope.transition,padding))
                            try {
                                val admitted=CoreReadAdmission.admit(listOf(people,tasks),envelope)
                                check(plus==0 && (if(isR)admitted.retained else admitted.transition)==limit)
                                check(people.materialize(admitted).people.items.size==32);materializers++
                                check(tasks.materialize(admitted).items.size==32);materializers++
                            }catch(failure:BoundedActiveRejected){check(plus==1 && failure.status==BoundedActiveStatus.BUDGET_REFUSED);refused=true}
                            check(materializers==if(plus==0)2 else 0)
                            emit("BOUNDARY,mode=$mode,requested=${limit+plus},materializers=$materializers,refused=$refused,real_plans=true,test_padding=true")
                        }
                    }
                }
            }
        } finally {
            check(borrowed!=null && borrowed!!.all{it==0.toByte()});borrowed=null
            check(cleanup);assertIdle();leaseEnds++
        }
        emit("CONTROLLED_END,mode=$mode,cleanup_confirmed=$cleanup,borrowed_java_key_zero=true,${evidence()}")
    }
}
