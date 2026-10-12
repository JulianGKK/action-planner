package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** Action-scoped port. No observer/retained Room instance or sensitive-profile access. */
class ProductOperationsRepository(app:Context,private val access:StorageAccess,
    private val inventory:ProductInventory,private val owner:()->SessionDbKeyOwner?,
    private val worker:Executor,private val main:Executor,private val blocked:()->Unit,
    private val now:()->Long=System::currentTimeMillis):OperationsRepository {
    private val database=ActionDatabaseAccess(app.applicationContext)
    override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit)=run(publish){d,c->d.withReadTransaction{OperationsStore(d,c,now).board(actionId)}}
    override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit)=write(publish){it.role(actionId,null,name)}
    override fun renameRole(actionId:String,roleId:String,name:String,publish:(OperationsResult<Role>)->Unit)=write(publish){it.role(actionId,roleId,name)}
    override fun deleteRole(actionId:String,roleId:String,publish:(OperationsResult<Unit>)->Unit)=write(publish){it.deleteRole(actionId,roleId)}
    override fun planRoles(actionId:String,personId:String,plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit)=write(publish){it.plan(actionId,personId,plan)}
    override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit)=
        write(publish){it.snapshot(actionId,personId,expected,status,roleId,place)}
    private fun <T> write(publish:(OperationsResult<T>)->Unit,work:suspend(OperationsStore)->OperationsResult<T>)=
        run(publish){d,c->d.withWriteTransaction{val result=work(OperationsStore(d,c,now));requireOperations(c,AccessPoint.COMMIT);result}}
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
internal fun requireOperations(c:StorageContext,point:AccessPoint=AccessPoint.STEP) {
    if(c.scope!=AccessScope.READY || !c.check(point))throw StorageAccessRevoked()
}
/** Shared internal transaction implementation, also used by deterministic storage tests. */
internal class OperationsStore(private val db:ActionDatabase,private val c:StorageContext,private val now:()->Long) {
    private suspend fun <T> step(work:suspend()->T):T {requireOperations(c);return work()}
    private fun denied(reason:OperationsFailure)=OperationsResult.Rejected(reason)
    private suspend fun actionStatus(actionId:String)=step{db.actions().get(actionId)?.model()?.status}
    suspend fun board(actionId:String):OperationsResult<OperationalBoard> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayRead(status))return denied(OperationsFailure.NOT_ALLOWED)
        val roles=step{db.operations().roles(actionId)}.map{it.model()}
        val people=step{db.persons().list(actionId)}.map{it.model()}
        val assignments=step{db.operations().planned(actionId)}
        val plans=step{db.operations().plans(actionId)}.associateBy{it.personId}
        val states=step{db.operations().states(actionId)}.associateBy{it.personId}
        val persons=people.map{p->
            val ids=assignments.filter{it.personId==p.id}.map{it.roleId}.toSet()
            val plan=PersonRolePlan(ids,plans[p.id]?.primaryRoleId)
            if(!OperationalRules.validPlan(ids,plan.primaryRoleId) || ids.any{id->roles.none{it.id==id}})throw ProductStorageFailure()
            val state=states[p.id]?.model()
            if((status==ActionStatus.ACTIVE || status==ActionStatus.COMPLETED) && state==null)throw ProductStorageFailure()
            OperationalPerson(p,plan,state)
        }
        return OperationsResult.Value(OperationalBoard(actionId,status,roles,persons))
    }
    suspend fun role(actionId:String,roleId:String?,name:String):OperationsResult<Role> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!(if(roleId==null)OperationalRules.mayCreateRole(status) else OperationalRules.mayPlan(status)))return denied(OperationsFailure.NOT_ALLOWED)
        if(!OperationalRules.validName(name))return denied(OperationsFailure.INVALID)
        val old=if(roleId==null)null else step{db.operations().role(actionId,roleId)}?:return denied(OperationsFailure.NOT_FOUND)
        val normalized=OperationalRules.normalizedName(name)
        val existing=step{db.operations().roles(actionId)}.onEach{it.model()}
        if(existing.any{it.normalizedName==normalized && it.id!=roleId})return denied(OperationsFailure.DUPLICATE)
        val row=old?.copy(name=name,normalizedName=normalized)?:RoleRow(UUID.randomUUID().toString(),actionId,name,normalized,now())
        if(old==null)step{db.operations().insertRole(row)}else if(step{db.operations().updateRole(row)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(row.model())
    }
    suspend fun deleteRole(actionId:String,roleId:String):OperationsResult<Unit> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayPlan(status))return denied(OperationsFailure.NOT_ALLOWED)
        step{db.operations().role(actionId,roleId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(step{db.operations().plannedReferences(actionId,roleId)}>0 || step{db.operations().liveReferences(actionId,roleId)}>0 || step{db.tasks().roleReferences(actionId,roleId)}>0)return denied(OperationsFailure.IN_USE)
        if(step{db.operations().removeRole(actionId,roleId)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
    suspend fun plan(actionId:String,personId:String,plan:PersonRolePlan):OperationsResult<Unit> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayPlan(status))return denied(OperationsFailure.NOT_ALLOWED)
        step{db.persons().get(actionId,personId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.validPlan(plan.roleIds,plan.primaryRoleId))return denied(OperationsFailure.INVALID)
        for(id in plan.roleIds)step{db.operations().role(actionId,id)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        step{db.operations().removePlan(actionId,personId)}
        step{db.operations().removeAssignments(actionId,personId)}
        for(id in plan.roleIds)step{db.operations().assign(PlannedRoleRow(actionId,personId,id))}
        step{db.operations().insertPlan(RolePlanRow(actionId,personId,plan.primaryRoleId))}
        return OperationsResult.Value(Unit)
    }
    suspend fun snapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String):OperationsResult<Unit> {
        val actionStatus=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayUpdateSnapshot(actionStatus))return denied(OperationsFailure.NOT_ALLOWED)
        step{db.persons().get(actionId,personId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        val old=step{db.operations().state(actionId,personId)}?:throw ProductStorageFailure()
        if(expected.actionId!=actionId || expected.personId!=personId || old.model()!=expected)return denied(OperationsFailure.STALE)
        // Validate caller's role even when this status clears it, so foreign references never pass.
        if(roleId!=null)step{db.operations().role(actionId,roleId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.validPlace(place))return denied(OperationsFailure.INVALID)
        val row=old.copy(currentRoleId=roleId.takeIf{OperationalRules.roleAllowed(status)},operationalStatus=status.name,
            currentPlaceLabel=OperationalRules.place(place,status),updatedAt=now())
        row.model()
        if(step{db.operations().updateState(row)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
}
