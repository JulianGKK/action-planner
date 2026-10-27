package org.pag.data.product

import androidx.room3.PooledConnection
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked

/** A complete, finite PREPARATION section, never an ACTIVE projection or workspace fallback.
 * Families exceeding this entry's scope are refused BEFORE any row DTO. No truncated board
 * is passed to consumers which require complete membership. This is a read admission limit,
 * not a new restriction on persisted business data. Metadata and payload share one transaction.
 */
internal class PreparationEntryStore(private val sql:PooledConnection,private val db:ActionDatabase,
    private val context:StorageContext,private val current:()->Boolean,
    private val leadership:(suspend(ActionDatabase,StorageContext,String)->SensitiveResult<SensitiveState>)?) {
    private fun gate(){requireOperations(context);if(!current())throw StorageAccessRevoked()}
    private suspend fun within(table:String,action:String,limit:Long):Boolean {
        gate()
        return sql.usePrepared("SELECT COUNT(*) FROM $table WHERE actionId=?"){s->
            s.bindText(1,action);gate();check(s.step());val n=s.getLong(0);check(!s.step());n in 0..limit
        }
    }
    suspend fun read(r:WorkspaceReadRequest,entry:OutstandingCoreRead.FullviewEntry):WorkspaceResult {
        gate()
        if(r.status!=ActionStatus.PREPARATION || r.generation!=context.generation || !r.explicitOpening)
            return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
        // Fixed-schema status metadata, not an Action payload. No ACTIVE status checks changed.
        val status=sql.usePrepared("SELECT status FROM actions WHERE id=?"){s->
            s.bindText(1,r.actionId);gate();if(!s.step())null else s.getText(0).also{check(!s.step())}
        }
        if(status!=ActionStatus.PREPARATION.name)return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        if(!r.parts.all{it in parts})return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
        if(!within("persons",r.actionId,32) || !within("roles",r.actionId,32) ||
            (WorkspacePart.TASKS in r.parts && !within("tasks",r.actionId,128)))
            return WorkspaceResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
        val plan=FullviewPreflight(sql,context,::currentGate).prepare(r.actionId,FullviewPreflight.workspace(r.parts))
        val reservation=ActiveCoreReservation(CoreReadAdmission.add(plan.retained,OriginalWorkspaceAccess.cost),
            CoreReadAdmission.add(plan.transition,OriginalWorkspaceAccess.cost))
        if(!admitCurrentFullview(entry,reservation,::currentGate))return WorkspaceResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
        gate()
        val action=db.actions().get(r.actionId)?.model() ?: return WorkspaceResult.Rejected(WorkspaceFailure.NOT_FOUND)
        if(action.status!=r.status || (r.expectedAction!=null && r.expectedAction!=action))return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        val c=object:StorageContext by context{override fun check(point:AccessPoint)=currentGate() && context.check(point)}
        fun <T> value(result:OperationsResult<T>):T=when(result){is OperationsResult.Value->result.value;is OperationsResult.Rejected->throw StorageAccessRevoked()}
        val now=System::currentTimeMillis
        val operations=value(OperationsStore(db,c,now).board(action.id))
        val tasks=if(WorkspacePart.TASKS in r.parts)value(TaskStore(db,c,now).board(action.id)) else null
        val material=if(WorkspacePart.MATERIAL_SUMMARY in r.parts)value(MaterialStore(db,c,now).summary(action.id)) else null
        val expenses=if(WorkspacePart.EXPENSE_SUMMARY in r.parts)value(ExpenseStore(db,c,now).summary(action.id)) else null
        val documents=if(WorkspacePart.DOCUMENT_SUMMARY in r.parts)DocumentSummary(db.documents().count(action.id)) else null
        val protection=if(WorkspacePart.LEADERSHIP_STATE in r.parts){
            val read=leadership ?: return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
            read(db,c,action.id)
        }else null
        gate()
        return WorkspaceResult.Value(WorkspaceRead(action.id,r.status,r.generation,r.parts,operations,tasks,
            material=material,expenses=expenses,documents=documents,leadershipState=protection))
    }
    private fun currentGate()=current() && context.check(AccessPoint.STEP)
    companion object {
        private val parts=setOf(WorkspacePart.TASKS,WorkspacePart.MATERIAL_SUMMARY,WorkspacePart.EXPENSE_SUMMARY,
            WorkspacePart.DOCUMENT_SUMMARY,WorkspacePart.LEADERSHIP_STATE)
    }
}
