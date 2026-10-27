package org.pag.data.product

import androidx.room3.withReadTransaction
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/** One page, one original-owner supplement, one authorized metadata/payload transaction.
 * All bookings accumulate, including rejected publication and retained old pages/callbacks.
 */
internal class OriginalCorePages(private val access:StorageAccess,private val inventory:ProductInventory,
    private val owner:()->SessionDbKeyOwner?,private val worker:Executor,private val main:Executor,
    private val blocked:()->Unit,private val memory:OutstandingCoreRead,
    private val mutations:WorkspaceMutationInvalidation?,private val database:ActionDatabaseAccess) {
    fun read(original:WorkspaceAccess,r:WorkspacePageRequest,publish:(WorkspacePageResult)->Unit) {
        val scope=original as? OriginalWorkspaceAccess
        fun reject(f:WorkspaceFailure)=publish(WorkspacePageResult.Rejected(f))
        if(scope==null || scope.source!==access || Thread.currentThread()!==scope.thread ||
            scope.entry.parent==null || !memory.ownsFullview(scope.entry) || !scope.entry.readable() ||
            scope.status!=ActionStatus.ACTIVE || r.actionId!=scope.request.actionId || r.generation!=scope.original.generation ||
            scope.request.ticket?.active==false || !r.ticket.active || !scope.original.check(AccessPoint.STEP)) {reject(WorkspaceFailure.OWNER_UNCONFIRMED);return}
        val baseline=r.baseline
        if(baseline!=null && (baseline.access!==scope || baseline.tasks==null || !r.people)){reject(WorkspaceFailure.STALE);return}
        if((r.taskPage!=null || r.peoplePage!=null) && !scope.entry.reserve(ActiveWorkspaceBudget.Units(objects=512,arrays=64,arrayPayloadBytes=16384,references=1024))){reject(WorkspaceFailure.BUDGET_REFUSED);return}
        if(baseline!=null && !scope.entry.reserve(WorkspaceConsistency.cost(baseline))){reject(WorkspaceFailure.BUDGET_REFUSED);return}
        val c=access.acquire(AccessScope.READY) ?: return
        if(c.generation!=r.generation || !c.check(AccessPoint.OPERATION))return
        // Reserve the single in-flight slot BEFORE changing the writer read identity.
        val child=scope.entry.supplement(WorkspacePart.TASKS) ?: run{reject(scope.entry.additionalFailure);return}
        val authority=mutations?.beforeActiveRead(r.actionId,r.generation)
        if(authority==null){child.workerRejected();reject(WorkspaceFailure.OWNER_UNCONFIRMED);return}
        val fresh=OriginalWorkspaceAccess(scope.entry,access,c,authority,mutations,
            WorkspaceReadRequest(r.actionId,ActionStatus.ACTIVE,r.generation,emptySet(),ticket=r.ticket),scope.thread)
        val state=CompactTaskState(P3bFreeActiveCoreBinding(r.actionId,r.generation),authority.mutationEpoch)
        fun current()=fresh.liveOnWorker() && r.ticket.active
        fun gate(){requireOperations(c);if(!current())throw StorageAccessRevoked()}
        fun deliver(result:WorkspacePageResult) {
            val handoff=child.main(Runnable{fresh.publish(r.actionId,ActionStatus.ACTIVE,r.generation){if(current())publish(result)}})
            try{main.execute(handoff)}catch(_:RejectedExecutionException){child.mainRejected()}
        }
        val work=child.worker(Runnable {
            try {
                val result=DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                    gate()
                    if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                    (owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c,cleanupConfirmed={if(!it)child.unconfirmed()}){db->db.withReadTransaction {
                        gate()
                        if(baseline!=null && !WorkspaceConsistency(this,::current).matches(baseline,
                            WorkspaceDetailRequest(r.actionId,ActionStatus.ACTIVE,r.generation,WorkspaceDetailKind.TASK,"",r.ticket,expectedAction=r.expectedAction),
                            bounded=true,validateAllHeld=true))return@withReadTransaction WorkspacePageResult.Rejected(WorkspaceFailure.STALE)
                        if(!WorkspaceConsistency(this,::current).compactMatches(WorkspaceDetailRequest(r.actionId,ActionStatus.ACTIVE,r.generation,
                            WorkspaceDetailKind.TASK,"",r.ticket,taskPage=r.taskPage,peoplePage=r.peoplePage)))return@withReadTransaction WorkspacePageResult.Rejected(WorkspaceFailure.STALE)
                        if(r.overview){
                            val fixed=ActiveWorkspaceBudget.Units(objects=128,arrays=16,arrayPayloadBytes=4096,references=512)
                            if(!child.admit(ActiveCoreReservation(fixed,fixed)))return@withReadTransaction WorkspacePageResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
                            gate()
                            val people=CompactPeopleStore(this,c,::current)
                            people.preflight(state)
                            val hub=CompactHubCounts(people.counts(r.actionId),CompactTaskStore(this,c,::current).counts(r.actionId,null),people.roleCount(r.actionId))
                            gate();return@withReadTransaction WorkspacePageResult.Value(WorkspacePage(fresh,hub=hub,state=state))
                        }
                        if(r.functions){
                            val plan=CompactPeopleStore(this,c,::current).prepareFunctions(state)
                            val admission=CoreReadAdmission.admit(listOf(plan))
                            gate();if(!child.admit(admission.reservation))return@withReadTransaction WorkspacePageResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
                            gate();val functions=plan.materialize(admission)
                            return@withReadTransaction WorkspacePageResult.Value(WorkspacePage(fresh,functions=functions,state=state))
                        }
                        // Cursors retain their original epoch. A write requires an explicit first-page reload.
                        val taskPlan=if(r.people)null else CompactTaskStore(this,c,::current).preparePage(state,CompactTaskQuery(r.lane),r.tasksAfter)
                        val peoplePlan=if(r.people)CompactPeopleStore(this,c,::current).preparePage(state,CompactPeopleQuery.All,r.peopleAfter) else null
                        val admitted=CoreReadAdmission.admit(listOfNotNull(taskPlan,peoplePlan))
                        gate()
                        if(!child.admit(admitted.reservation))return@withReadTransaction WorkspacePageResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
                        gate()
                        val tasks=taskPlan?.materialize(admitted)
                        val people=peoplePlan?.materialize(admitted)
                        gate()
                        WorkspacePageResult.Value(WorkspacePage(fresh,tasks,people,if(r.taskPage!=null)CompactTaskStore(this,c,::current).counts(r.actionId,null) else null))
                    }}}
                }
                deliver(result)
            }catch(f:StorageAccessRevoked){if(f.suppressed.isNotEmpty())child.unconfirmed()}
             catch(f:BoundedActiveRejected){
                if(f.suppressed.isEmpty() && f.status==BoundedActiveStatus.BUDGET_REFUSED)deliver(WorkspacePageResult.Rejected(WorkspaceFailure.BUDGET_REFUSED))
                else if(f.suppressed.isEmpty() && f.status==BoundedActiveStatus.ACCESS_REVOKED)deliver(WorkspacePageResult.Rejected(WorkspaceFailure.STALE))
                else {child.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
             }catch(_:Exception){child.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(work)}catch(_:RejectedExecutionException){child.workerRejected();reject(WorkspaceFailure.OWNER_UNCONFIRMED)}
    }
}
