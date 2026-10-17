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

/** Debug benchmark fixture, guarded BEFORE any filesystem/key work. No product package allowed. */
class AssessmentSyntheticFixture(context:Context, private val people:Int, private val taskCount:Int, private val items:Int, worker:Executor=Executor{it.run()}, main:Executor=Executor{it.run()}):AutoCloseable {
    private val app=context.applicationContext.also {
        check(it.packageName in setOf("org.pag.data.unlockprobe","org.pag.perf.p11probe"))
    }
    private val parent=app.noBackupFilesDir.canonicalFile
    private val base=File(parent,"p11-assessment-${UUID.randomUUID()}").apply{check(mkdirs())}
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
                repeat(people){i->
                    val p="person-$i"
                    db.persons().insert(PersonRow("$id-$p",id,"Synthetic $i",PersonRules.normalized("Synthetic $i"),i.toLong()))
                    if(status!=ActionStatus.PREPARATION)db.operations().insertState(OperationalRow(id,"$id-$p",null,"NO_STATUS",null,1))
                }
                if(status!=ActionStatus.COMPLETED) {
                    repeat(taskCount){i->check(TaskStore(db,c){1}.create(id,TaskInput("Synthetic task $i")) is OperationsResult.Value)}
                    repeat(items){i->
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
    fun databaseRead(report:(String,Long)->Unit) {
        val c=access.acquire(AccessScope.READY) ?: error("SYNTHETIC_REVOKED")
        val start=System.nanoTime();check(inventory.state()==ProductStorageState.READY_TO_OPEN)
        report("PRODUCT_INVENTORY",System.nanoTime()-start)
        owner.get()!!.withDbKey(c){leased->
            ActionDatabaseAccess(wrapped,ActionPreflightHooks(timing={phase,nanos->report(phase.name,nanos)}))
                .use(inventory.database,leased,c){db->db.withReadTransaction{check(db.actions().list().size==3)}}
        }
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
    override fun close(){revoke();key.fill(0);check(base.parentFile==parent && base.name.startsWith("p11-assessment-"));base.deleteRecursively()}
}
