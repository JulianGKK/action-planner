package org.pag.data.product

import androidx.room3.PooledConnection
import org.pag.domain.*

/** Reuses only rows proven unchanged inside the detail's Room transaction. Scalar equality
 * checks return no database text/DTO. A different unrelated row refuses the entire handoff.
 * Old graph/Expected references and every validation scratch allocation remain charged.
 */
internal class WorkspaceConsistency(private val sql:PooledConnection,private val current:()->Boolean) {
    private fun gate(){if(!current())throw org.pag.data.prototype.b2.StorageAccessRevoked()}
    private suspend fun scalar(query:String,vararg values:Any?):Long {
        gate()
        return sql.usePrepared(query){s->
            values.forEachIndexed{i,v->when(v){null->s.bindNull(i+1);is String->s.bindText(i+1,v);is Long->s.bindLong(i+1,v);is Int->s.bindLong(i+1,v.toLong());else->throw ProductStorageFailure()}}
            gate();if(!s.step() || s.getColumnType(0)!=1)throw ProductStorageFailure()
            val n=s.getLong(0);if(n<0 || s.step())throw ProductStorageFailure();gate();n
        }
    }
    private suspend fun row(table:String,where:String,vararg values:Any?)=
        scalar("SELECT COUNT(*) FROM $table WHERE $where",*values)==1L
    private suspend fun count(table:String,a:String,n:Long,column:String?=null,id:String?=null)=
        if(column==null)scalar("SELECT COUNT(*) FROM $table WHERE actionId=?",a)==n
        else scalar("SELECT COUNT(*) FROM $table WHERE actionId=? AND $column!=?",a,id)==n

    suspend fun matches(b:WorkspaceRead,r:WorkspaceDetailRequest):Boolean {
        if(b.actionId!=r.actionId || b.generation!=r.generation || b.status!=r.status ||
            b.memoryHeaders!=null || b.protocolTimeline!=null || b.tasks?.events?.isNotEmpty()==true)return false
        // This reprojection replaces one existing member. Never label a partial list as
        // current after create/delete; those membership deltas need their own consumer plan.
        val represented=when(r.kind){
            WorkspaceDetailKind.PERSON,WorkspaceDetailKind.PLAN->b.operations.persons.any{it.person.id==r.id}
            WorkspaceDetailKind.TASK->b.tasks?.tasks?.any{it.id==r.id}==true
            WorkspaceDetailKind.MATERIAL->b.materialBoard?.items?.any{it.id==r.id}==true
            WorkspaceDetailKind.EXPENSE->b.expenseBoard?.expenses?.any{it.id==r.id}==true
        }
        if(!represented)return false
        val a=r.actionId;val id=r.id;val k=r.kind
        val action=r.expectedAction
        if(action!=null && !row("actions","id=? AND title IS ? AND description IS ? AND startAt IS ? AND expectedEndAt IS ? AND createdAt IS ? AND status IS ? AND completedAt IS ?",
            a,action.title,action.description,action.startAt,action.expectedEndAt,action.createdAt,action.status.name,action.completedAt))return false
        val ops=b.operations
        val personTarget=k==WorkspaceDetailKind.PERSON
        val planTarget=k==WorkspaceDetailKind.PLAN
        if(!count("persons",a,ops.persons.count{!personTarget || it.person.id!=id}.toLong(),if(personTarget)"id" else null,id))return false
        for(p in ops.persons){val v=p.person
            if(personTarget && v.id==id)continue
            if(!row("persons","actionId=? AND id=? AND pseudonym IS ? AND createdAt IS ? AND typeof(editRevision)='integer' AND editRevision IS ?",a,v.id,v.pseudonym,v.createdAt,v.editRevision))return false
        }
        if(!count("roles",a,ops.roles.size.toLong()))return false
        for(v in ops.roles)if(!row("roles","actionId=? AND id=? AND name IS ? AND createdAt IS ? AND typeof(editRevision)='integer' AND editRevision IS ?",a,v.id,v.name,v.createdAt,v.editRevision))return false
        if(!count("person_role_plans",a,ops.persons.count{!planTarget || it.person.id!=id}.toLong(),if(planTarget)"personId" else null,id))return false
        var assignments=0L
        for(p in ops.persons){
            if(planTarget && p.person.id==id)continue
            val v=p.plan
            if(!row("person_role_plans","actionId=? AND personId=? AND primaryRoleId IS ? AND typeof(editRevision)='integer' AND editRevision IS ?",a,p.person.id,v.primaryRoleId,v.editRevision))return false
            assignments+=v.roleIds.size
            for(role in v.roleIds)if(!row("person_planned_roles","actionId=? AND personId=? AND roleId=?",a,p.person.id,role))return false
        }
        if(!count("person_planned_roles",a,assignments,if(planTarget)"personId" else null,id))return false
        if(!count("person_operational_states",a,ops.persons.count{it.state!=null}.toLong()))return false
        for(p in ops.persons){val v=p.state?:continue
            if(!row("person_operational_states","actionId=? AND personId=? AND currentRoleId IS ? AND operationalStatus IS ? AND currentPlaceLabel IS ? AND updatedAt IS ? AND typeof(editRevision)='integer' AND editRevision IS ?",a,v.personId,v.currentRoleId,v.status.name,v.currentPlaceLabel,v.updatedAt,v.editRevision))return false
        }
        b.tasks?.let{board->
            val target=k==WorkspaceDetailKind.TASK
            if(!count("tasks",a,board.tasks.count{!target || it.id!=id}.toLong(),if(target)"id" else null,id))return false
            for(v in board.tasks){if(target && v.id==id)continue
                if(!row("tasks","actionId=? AND id=? AND title IS ? AND priority IS ? AND status IS ? AND assigneePersonId IS ? AND assigneeRoleId IS ? AND affectedPersonId IS ? AND dueAt IS ? AND sourceTemplateKey IS ? AND createdAt IS ? AND updatedAt IS ? AND completedAt IS ? AND typeof(editRevision)='integer' AND editRevision IS ?",a,v.id,v.title,v.priority.name,v.status.name,v.assigneePersonId,v.assigneeRoleId,v.affectedPersonId,v.dueAt,v.sourceTemplateKey,v.createdAt,v.updatedAt,v.completedAt,v.editRevision))return false
            }
        }
        b.materialBoard?.let{board->
            val target=k==WorkspaceDetailKind.MATERIAL
            if(!count("materials",a,board.items.count{!target || it.id!=id}.toLong(),if(target)"id" else null,id))return false
            for(v in board.items){if(target && v.id==id)continue
                if(!row("materials","actionId=? AND id=? AND name IS ? AND quantityLabel IS ? AND placeLabel IS ? AND responsiblePersonId IS ? AND linkedTaskId IS ? AND sourceTemplateKey IS ? AND createdAt IS ?",a,v.id,v.name,v.quantityLabel,v.placeLabel,v.responsiblePersonId,v.linkedTaskId,v.sourceTemplateKey,v.createdAt))return false
            }
        }
        b.expenseBoard?.let{board->
            val target=k==WorkspaceDetailKind.EXPENSE
            if(!count("expenses",a,board.expenses.count{!target || it.id!=id}.toLong(),if(target)"id" else null,id))return false
            var payments=0L
            for(v in board.expenses){if(target && v.id==id)continue
                if(!row("expenses","actionId=? AND id=? AND title IS ? AND totalCents IS ? AND createdAt IS ?",a,v.id,v.title,v.totalCents,v.createdAt))return false
                payments+=v.payments.size
                v.payments.forEachIndexed{i,p->if(!row("expense_payments","actionId=? AND expenseId=? AND position=? AND payerLabel IS ? AND amountCents IS ?",a,v.id,i,p.payerLabel,p.amountCents))return false}
            }
            if(!count("expense_payments",a,payments,if(target)"expenseId" else null,id))return false
        }
        gate();return true
    }
    companion object {
        /** Includes query/bind/vararg/boxing scratch, list replacement and UI headers. No
         * payload strings are copied by validation. Checked sizing precedes construction. */
        fun cost(b:WorkspaceRead):ActiveWorkspaceBudget.Units {
            var rows=32L
            fun add(n:Long){rows=Math.addExact(rows,n)}
            add(b.operations.roles.size.toLong());add(Math.multiplyExact(b.operations.persons.size.toLong(),4))
            for(p in b.operations.persons)add(p.plan.roleIds.size.toLong())
            add(b.tasks?.tasks?.size?.toLong()?:0);add(b.materialBoard?.items?.size?.toLong()?:0)
            for(e in b.expenseBoard?.expenses.orEmpty()){add(1);add(e.payments.size.toLong())}
            return ActiveWorkspaceBudget.Units(objects=Math.multiplyExact(rows,32),strings=Math.multiplyExact(rows,8),
                utf16Units=Math.multiplyExact(rows,2048),arrays=Math.multiplyExact(rows,4),arrayPayloadBytes=Math.multiplyExact(rows,256),
                lists=Math.multiplyExact(rows,2),listCapacity=Math.multiplyExact(rows,64),references=Math.multiplyExact(rows,128))
        }
    }
}
