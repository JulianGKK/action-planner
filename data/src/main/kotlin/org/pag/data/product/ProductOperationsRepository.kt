package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.useWriterConnection
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** Action-scoped port. No observer/retained Room instance or sensitive-profile access. */
class ProductOperationsRepository(app:Context,private val access:StorageAccess,
    private val inventory:ProductInventory,private val owner:()->SessionDbKeyOwner?,
    private val worker:Executor,private val main:Executor,private val blocked:()->Unit,
    mutations:WorkspaceMutationInvalidation=UncachedWorkspaceMutations(),
    private val now:()->Long=System::currentTimeMillis):OperationsRepository {
    private val readExclusion=FullviewReadExclusion(app,access,inventory,owner,worker,main,blocked,mutations,FullviewPreflight.operations)
    internal fun bindWorkspaceMemory(memory:OutstandingCoreRead){readExclusion.bind(memory)}
    private val database=ActionDatabaseAccess(app.applicationContext)
    private val mutationBoundary=WorkspaceWriteBoundary(access,worker,main,mutations,blocked)
    override fun board(actionId:String,publish:(OperationsResult<OperationalBoard>)->Unit)=run(actionId,publish){d,c->d.withReadTransaction{OperationsStore(d,c,now).board(actionId)}}
    override fun createRole(actionId:String,name:String,publish:(OperationsResult<Role>)->Unit)=write(actionId,publish){it.role(actionId,null,name)}
    override fun renameRole(actionId:String,expected:Role,name:String,publish:(OperationsResult<Role>)->Unit)=write(actionId,publish){it.role(actionId,expected,name)}
    override fun deleteRole(actionId:String,expected:Role,publish:(OperationsResult<Unit>)->Unit)=write(actionId,publish){it.deleteRole(actionId,expected)}
    override fun planRoles(actionId:String,personId:String,expected:ExpectedPersonRolePlan,plan:PersonRolePlan,publish:(OperationsResult<Unit>)->Unit) {
        val frozen=plan.copy(roleIds=plan.roleIds.toSet())
        write(actionId,publish){it.plan(actionId,personId,expected,frozen)}
    }
    override fun updateSnapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String,publish:(OperationsResult<Unit>)->Unit)=
        write(actionId,publish){it.snapshot(actionId,personId,expected,status,roleId,place)}
    private fun <T> write(actionId:String,publish:(OperationsResult<T>)->Unit,work:suspend(OperationsStore)->OperationsResult<T>) =
        mutationBoundary.submit<OperationsResult<T>>(WorkspaceMutation(WorkspaceWriterFamily.OPERATIONS,actionId),publish,
            {OperationsResult.Rejected(OperationsFailure.STALE)},{it is OperationsResult.Value}) { c,p ->
            if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
            (owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){db->
                db.useWriterConnection { tx -> confirmedWorkspaceTransaction(tx,c,p,{it is OperationsResult.Rejected}) {work(OperationsStore(db,c,now))} }
            }}
        }
    private fun <T> run(actionId:String,publish:(OperationsResult<T>)->Unit,work:suspend(ActionDatabase,StorageContext)->OperationsResult<T>) {
        readExclusion.read(actionId,publish,{budget->OperationsResult.Rejected(if(budget)OperationsFailure.BUDGET_REFUSED else OperationsFailure.OWNER_UNCONFIRMED)},work)
    }
}
internal fun requireOperations(c:StorageContext,point:AccessPoint=AccessPoint.STEP) {
    if(c.scope!=AccessScope.READY || !c.check(point))throw StorageAccessRevoked()
}
/** Shared internal transaction implementation, also used by deterministic storage tests. */
internal class OperationsStore(private val data:CoreWriterData,private val c:StorageContext,private val now:()->Long) {
    constructor(db:ActionDatabase,c:StorageContext,now:()->Long):this(RoomCoreWriterData(db),c,now)
    private suspend fun <T> step(work:suspend()->T):T {requireOperations(c);return work()}
    private fun denied(reason:OperationsFailure)=OperationsResult.Rejected(reason)
    private suspend fun actionStatus(actionId:String)=step{data.actions.get(actionId)?.model()?.status}
    suspend fun board(actionId:String):OperationsResult<OperationalBoard> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayRead(status))return denied(OperationsFailure.NOT_ALLOWED)
        val roles=step{data.operations.roles(actionId)}.map{it.model()}
        val people=step{data.persons.list(actionId)}.map{it.model()}
        val assignments=step{data.operations.planned(actionId)}
        val plans=step{data.operations.plans(actionId)}.associateBy{it.personId}
        val states=step{data.operations.states(actionId)}.associateBy{it.personId}
        val persons=people.map{p->
            val ids=assignments.filter{it.personId==p.id}.map{it.roleId}.toSet()
            val header=plans[p.id]?:throw ProductStorageFailure()
            if(header.editRevision<0)throw ProductStorageFailure()
            val plan=PersonRolePlan(ids,header.primaryRoleId,header.editRevision)
            if(!OperationalRules.validPlan(ids,plan.primaryRoleId) || ids.any{id->roles.none{it.id==id}})throw ProductStorageFailure()
            val state=states[p.id]?.model()
            if((status==ActionStatus.ACTIVE || status==ActionStatus.COMPLETED) && state==null)throw ProductStorageFailure()
            OperationalPerson(p,plan,state)
        }
        return OperationsResult.Value(OperationalBoard(actionId,status,roles,persons))
    }
    suspend fun role(actionId:String,expected:Role?,name:String):OperationsResult<Role> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!(if(expected==null)OperationalRules.mayCreateRole(status) else OperationalRules.mayPlan(status)))return denied(OperationsFailure.NOT_ALLOWED)
        if(!OperationalRules.validName(name))return denied(OperationsFailure.INVALID)
        val old=if(expected==null)null else {
            if(expected.actionId!=actionId)return denied(OperationsFailure.STALE)
            val row=step{data.operations.role(actionId,expected.id)}?:return denied(OperationsFailure.NOT_FOUND)
            if(row.model()!=expected)return denied(OperationsFailure.STALE)
            row
        }
        if(old?.editRevision==Long.MAX_VALUE)return denied(OperationsFailure.REVISION_EXHAUSTED)
        val normalized=OperationalRules.normalizedName(name)
        val existing=step{data.operations.roles(actionId)}.onEach{it.model()}
        if(existing.any{it.normalizedName==normalized && it.id!=expected?.id})return denied(OperationsFailure.DUPLICATE)
        val row=old?.copy(name=name,normalizedName=normalized)?:RoleRow(UUID.randomUUID().toString(),actionId,name,normalized,now(), editRevision = 0)
        if(old==null)step{data.operations.insertRole(row)}else if(step{data.operations.renameRole(actionId,row.id,old.editRevision,name,normalized)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value((step{data.operations.role(actionId,row.id)}?:throw ProductStorageFailure()).model())
    }
    suspend fun deleteRole(actionId:String,expected:Role):OperationsResult<Unit> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayPlan(status))return denied(OperationsFailure.NOT_ALLOWED)
        if(expected.actionId!=actionId)return denied(OperationsFailure.STALE)
        val roleId=expected.id
        val current=step{data.operations.role(actionId,roleId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(current!=expected)return denied(OperationsFailure.STALE)
        if(current.editRevision==Long.MAX_VALUE)return denied(OperationsFailure.REVISION_EXHAUSTED)
        if(step{data.operations.plannedReferences(actionId,roleId)}>0 || step{data.operations.liveReferences(actionId,roleId)}>0 || step{data.tasks.roleReferences(actionId,roleId)}>0)return denied(OperationsFailure.IN_USE)
        if(step{data.operations.claimRoleDelete(actionId,roleId,expected.editRevision)}!=1)return denied(OperationsFailure.STALE)
        if(step{data.operations.removeRole(actionId,roleId,expected.editRevision+1)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
    suspend fun plan(actionId:String,personId:String,expected:ExpectedPersonRolePlan,plan:PersonRolePlan):OperationsResult<Unit> {
        val status=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayPlan(status))return denied(OperationsFailure.NOT_ALLOWED)
        step{data.persons.get(actionId,personId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(expected.actionId!=actionId || expected.personId!=personId)return denied(OperationsFailure.STALE)
        val current=readPlan(actionId,personId)
        if(current!=expected.plan)return denied(OperationsFailure.STALE)
        if(current.editRevision==Long.MAX_VALUE)return denied(OperationsFailure.REVISION_EXHAUSTED)
        if(!OperationalRules.validPlan(plan.roleIds,plan.primaryRoleId))return denied(OperationsFailure.INVALID)
        for(id in plan.roleIds)step{data.operations.role(actionId,id)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(step{data.operations.claimPlan(actionId,personId,current.editRevision)}!=1)return denied(OperationsFailure.STALE)
        step{data.operations.removeAssignments(actionId,personId)}
        for(id in plan.roleIds)step{data.operations.assign(PlannedRoleRow(actionId,personId,id))}
        if(step{data.operations.finishPlan(actionId,personId,current.editRevision+1,plan.primaryRoleId)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
    /** Reads only the selected person's complete plan, before any deleting/inserting SQL. */
    private suspend fun readPlan(actionId:String,personId:String):PersonRolePlan {
        val ids=step{data.operations.personAssignments(actionId,personId)}.map{it.roleId}.toSet()
        val header=step{data.operations.personPlan(actionId,personId)}?:throw ProductStorageFailure()
        if(header.editRevision<0)throw ProductStorageFailure()
        val primary=header.primaryRoleId
        if(!OperationalRules.validPlan(ids,primary))throw ProductStorageFailure()
        for(id in ids)step{data.operations.role(actionId,id)}?.model()?:throw ProductStorageFailure()
        return PersonRolePlan(ids,primary,header.editRevision)
    }
    suspend fun snapshot(actionId:String,personId:String,expected:PersonOperationalState,status:OperationalStatus,roleId:String?,place:String):OperationsResult<Unit> {
        val actionStatus=actionStatus(actionId)?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.mayUpdateSnapshot(actionStatus))return denied(OperationsFailure.NOT_ALLOWED)
        step{data.persons.get(actionId,personId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        val old=step{data.operations.state(actionId,personId)}?:throw ProductStorageFailure()
        if(expected.actionId!=actionId || expected.personId!=personId || old.model()!=expected)return denied(OperationsFailure.STALE)
        if(old.editRevision==Long.MAX_VALUE)return denied(OperationsFailure.REVISION_EXHAUSTED)
        // Validate caller's role even when this status clears it, so foreign references never pass.
        if(roleId!=null)step{data.operations.role(actionId,roleId)}?.model()?:return denied(OperationsFailure.NOT_FOUND)
        if(!OperationalRules.validPlace(place))return denied(OperationsFailure.INVALID)
        val row=old.copy(currentRoleId=roleId.takeIf{OperationalRules.roleAllowed(status)},operationalStatus=status.name,
            currentPlaceLabel=OperationalRules.place(place,status),updatedAt=now())
        row.model()
        if(step{data.operations.changeState(actionId,personId,old.editRevision,row.currentRoleId,row.operationalStatus,row.currentPlaceLabel,row.updatedAt)}!=1)throw ProductStorageFailure()
        return OperationsResult.Value(Unit)
    }
}
