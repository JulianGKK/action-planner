package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.useWriterConnection
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** Action-scoped port. No observer/retained Room instance or sensitive-profile access. */
class ProductTaskRepository(app:Context,private val access:StorageAccess,
    private val inventory:ProductInventory,private val owner:()->SessionDbKeyOwner?,
    private val worker:Executor,private val main:Executor,private val blocked:()->Unit,
    mutations:WorkspaceMutationInvalidation=UncachedWorkspaceMutations(),
    private val now:()->Long=System::currentTimeMillis):TaskRepository {
    private val database=ActionDatabaseAccess(app.applicationContext)
    private val mutationBoundary=TaskMutationBoundary(access,worker,main,mutations,blocked)
    override fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit)=run(publish){d,c->d.withReadTransaction{TaskStore(d,c,now).board(actionId)}}
    override fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit)=write(actionId,publish){it.create(actionId,input)}
    override fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit)=write(actionId,publish){it.edit(actionId,expected,input)}
    override fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit)=write(actionId,publish){it.transition(actionId,expected,status)}
    override fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit)=write(actionId,publish){it.delete(actionId,expected)}
    private fun <T> write(actionId:String,publish:(OperationsResult<T>)->Unit,work:suspend(TaskStore)->OperationsResult<T>)=
        mutationBoundary.submit(actionId,publish) { c,progress ->
            if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
            (owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){d->
                d.useWriterConnection { tx -> confirmedTaskTransaction(tx,c,progress){work(TaskStore(d,c,now))} }
            }}
        }
    private fun <T> run(publish:(OperationsResult<T>)->Unit,work:suspend(ActionDatabase,StorageContext)->OperationsResult<T>) {
        val c=access.acquire(AccessScope.READY)?:return
        worker.execute {
            try {
                requireOperations(c,AccessPoint.OPERATION)
                if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                val result=(owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){work(it,c)}}
                main.execute{c.publish{publish(result)}}
            }catch(_:Exception){main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        }
    }
}
internal interface TaskStoreData {
    val tasks:TaskDao
    suspend fun action(id:String):Action?
    suspend fun personExists(action:String,id:String):Boolean
    suspend fun roleExists(action:String,id:String):Boolean
}
internal class RoomTaskStoreData(private val db:ActionDatabase):TaskStoreData {
    override val tasks get()=db.tasks()
    override suspend fun action(id:String)=db.actions().get(id)?.model()
    override suspend fun personExists(action:String,id:String)=db.persons().get(action,id)!=null
    override suspend fun roleExists(action:String,id:String)=db.operations().role(action,id)!=null
}
internal class TaskStore(private val data:TaskStoreData,private val c:StorageContext,private val now:()->Long) {
    constructor(db:ActionDatabase,c:StorageContext,now:()->Long):this(RoomTaskStoreData(db),c,now)
    private suspend fun <T> step(work:suspend()->T):T {requireOperations(c);return work()}
    private fun denied(reason:OperationsFailure)=OperationsResult.Rejected(reason)
    private suspend fun status(id:String)=step{data.action(id)?.status}
    suspend fun board(actionId:String):OperationsResult<TaskBoard> {
        val s=status(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!TaskRules.mayRead(s))return denied(OperationsFailure.NOT_ALLOWED)
        return OperationsResult.Value(TaskBoard(actionId,step{data.tasks.list(actionId)}.map{it.model()},step{data.tasks.events(actionId)}.map{it.model()}))
    }
    private suspend fun validReferences(a:String,i:TaskInput):Boolean {
        if(i.assigneePersonId!=null && !step{data.personExists(a,i.assigneePersonId!!)})return false
        if(i.affectedPersonId!=null && !step{data.personExists(a,i.affectedPersonId!!)})return false
        if(i.assigneeRoleId!=null && !step{data.roleExists(a,i.assigneeRoleId!!)})return false
        return true
    }
    private suspend fun event(t:Task,type:TaskEventType) {step{data.tasks.event(TaskEventRow(UUID.randomUUID().toString(),t.actionId,t.id,type.name,t.updatedAt))}}
    suspend fun create(a:String,i:TaskInput):OperationsResult<Task> {
        val s=status(a)?:return denied(OperationsFailure.NOT_FOUND)
        if(!TaskRules.mayWrite(s))return denied(OperationsFailure.NOT_ALLOWED)
        if(!TaskRules.valid(i))return denied(OperationsFailure.INVALID)
        if(!validReferences(a,i))return denied(OperationsFailure.NOT_FOUND)
        val time=now();val t=Task(UUID.randomUUID().toString(),a,i.title.trim(),i.priority,TaskStatus.OPEN,i.assigneePersonId,i.assigneeRoleId,i.affectedPersonId,i.dueAt,i.sourceTemplateKey,time,time,null)
        step{data.tasks.insert(t.row())};event(t,TaskEventType.CREATED)
        return OperationsResult.Value(t)
    }
    private suspend fun existing(a:String,t:Task):OperationsResult<Task> {
        val s=status(a)?:return denied(OperationsFailure.NOT_FOUND)
        if(!TaskRules.mayWrite(s))return denied(OperationsFailure.NOT_ALLOWED)
        val old=step{data.tasks.get(a,t.id)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(t.actionId!=a || t!=old)return denied(OperationsFailure.STALE)
        return OperationsResult.Value(old)
    }
    suspend fun edit(a:String,t:Task,i:TaskInput):OperationsResult<Task> {
        val found=existing(a,t);if(found is OperationsResult.Rejected)return found
        if(!TaskRules.valid(i))return denied(OperationsFailure.INVALID)
        if(!validReferences(a,i))return denied(OperationsFailure.NOT_FOUND)
        val updated=t.copy(title=i.title.trim(),priority=i.priority,assigneePersonId=i.assigneePersonId,assigneeRoleId=i.assigneeRoleId,affectedPersonId=i.affectedPersonId,dueAt=i.dueAt,updatedAt=now())
        if(step{data.tasks.update(updated.row())}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(updated)
    }
    suspend fun transition(a:String,t:Task,to:TaskStatus):OperationsResult<Task> {
        val found=existing(a,t);if(found is OperationsResult.Rejected)return found
        val type=TaskRules.event(t.status,to)?:return denied(OperationsFailure.INVALID)
        val updated=TaskRules.transition(t,to,now())?:return denied(OperationsFailure.INVALID)
        if(step{data.tasks.update(updated.row())}!=1)throw ProductStorageFailure()
        event(updated,type);return OperationsResult.Value(updated)
    }
    suspend fun delete(a:String,t:Task):OperationsResult<Unit> {
        val found=existing(a,t);if(found is OperationsResult.Rejected)return found
        if(!TaskRules.mayDelete(status(a)!!))return denied(OperationsFailure.NOT_ALLOWED)
        if(step{data.tasks.delete(a,t.id)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
}
