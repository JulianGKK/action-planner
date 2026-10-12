package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** Action-scoped port. No observer/retained Room instance or sensitive-profile access. */
class ProductTaskRepository(app:Context,private val access:StorageAccess,
    private val inventory:ProductInventory,private val owner:()->SessionDbKeyOwner?,
    private val worker:Executor,private val main:Executor,private val blocked:()->Unit,
    private val now:()->Long=System::currentTimeMillis):TaskRepository {
    private val database=ActionDatabaseAccess(app.applicationContext)
    override fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit)=run(publish){d,c->d.withReadTransaction{TaskStore(d,c,now).board(actionId)}}
    override fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit)=write(publish){it.create(actionId,input)}
    override fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit)=write(publish){it.edit(actionId,expected,input)}
    override fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit)=write(publish){it.transition(actionId,expected,status)}
    override fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit)=write(publish){it.delete(actionId,expected)}
    private fun <T> write(publish:(OperationsResult<T>)->Unit,work:suspend(TaskStore)->OperationsResult<T>)=
        run(publish){d,c->d.withWriteTransaction{val result=work(TaskStore(d,c,now));requireOperations(c,AccessPoint.COMMIT);result}}
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
internal class TaskStore(private val db:ActionDatabase,private val c:StorageContext,private val now:()->Long) {
    private suspend fun <T> step(work:suspend()->T):T {requireOperations(c);return work()}
    private fun denied(reason:OperationsFailure)=OperationsResult.Rejected(reason)
    private suspend fun status(id:String)=step{db.actions().get(id)?.model()?.status}
    suspend fun board(actionId:String):OperationsResult<TaskBoard> {
        val s=status(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!TaskRules.mayRead(s))return denied(OperationsFailure.NOT_ALLOWED)
        return OperationsResult.Value(TaskBoard(actionId,step{db.tasks().list(actionId)}.map{it.model()},step{db.tasks().events(actionId)}.map{it.model()}))
    }
    private suspend fun validReferences(a:String,i:TaskInput):Boolean {
        if(i.assigneePersonId!=null && step{db.persons().get(a,i.assigneePersonId!!)}==null)return false
        if(i.affectedPersonId!=null && step{db.persons().get(a,i.affectedPersonId!!)}==null)return false
        if(i.assigneeRoleId!=null && step{db.operations().role(a,i.assigneeRoleId!!)}==null)return false
        return true
    }
    private suspend fun event(t:Task,type:TaskEventType) {step{db.tasks().event(TaskEventRow(UUID.randomUUID().toString(),t.actionId,t.id,type.name,t.updatedAt))}}
    suspend fun create(a:String,i:TaskInput):OperationsResult<Task> {
        val s=status(a)?:return denied(OperationsFailure.NOT_FOUND)
        if(!TaskRules.mayWrite(s))return denied(OperationsFailure.NOT_ALLOWED)
        if(!TaskRules.valid(i))return denied(OperationsFailure.INVALID)
        if(!validReferences(a,i))return denied(OperationsFailure.NOT_FOUND)
        val time=now();val t=Task(UUID.randomUUID().toString(),a,i.title.trim(),i.priority,TaskStatus.OPEN,i.assigneePersonId,i.assigneeRoleId,i.affectedPersonId,i.dueAt,i.sourceTemplateKey,time,time,null)
        step{db.tasks().insert(t.row())};event(t,TaskEventType.CREATED)
        return OperationsResult.Value(t)
    }
    private suspend fun existing(a:String,t:Task):OperationsResult<Task> {
        val s=status(a)?:return denied(OperationsFailure.NOT_FOUND)
        if(!TaskRules.mayWrite(s))return denied(OperationsFailure.NOT_ALLOWED)
        val old=step{db.tasks().get(a,t.id)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(t.actionId!=a || t!=old)return denied(OperationsFailure.STALE)
        return OperationsResult.Value(old)
    }
    suspend fun edit(a:String,t:Task,i:TaskInput):OperationsResult<Task> {
        val found=existing(a,t);if(found is OperationsResult.Rejected)return found
        if(!TaskRules.valid(i))return denied(OperationsFailure.INVALID)
        if(!validReferences(a,i))return denied(OperationsFailure.NOT_FOUND)
        val updated=t.copy(title=i.title.trim(),priority=i.priority,assigneePersonId=i.assigneePersonId,assigneeRoleId=i.assigneeRoleId,affectedPersonId=i.affectedPersonId,dueAt=i.dueAt,updatedAt=now())
        if(step{db.tasks().update(updated.row())}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(updated)
    }
    suspend fun transition(a:String,t:Task,to:TaskStatus):OperationsResult<Task> {
        val found=existing(a,t);if(found is OperationsResult.Rejected)return found
        val type=TaskRules.event(t.status,to)?:return denied(OperationsFailure.INVALID)
        val updated=TaskRules.transition(t,to,now())?:return denied(OperationsFailure.INVALID)
        if(step{db.tasks().update(updated.row())}!=1)throw ProductStorageFailure()
        event(updated,type);return OperationsResult.Value(updated)
    }
    suspend fun delete(a:String,t:Task):OperationsResult<Unit> {
        val found=existing(a,t);if(found is OperationsResult.Rejected)return found
        if(!TaskRules.mayDelete(status(a)!!))return denied(OperationsFailure.NOT_ALLOWED)
        if(step{db.tasks().delete(a,t.id)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
}
