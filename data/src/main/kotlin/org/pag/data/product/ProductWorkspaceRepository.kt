package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import java.util.concurrent.Executor
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/** Batches only explicitly requested reads. No connection/key lifetime crosses this call. */
class ProductWorkspaceRepository internal constructor(app:Context,private val access:StorageAccess,private val inventory:ProductInventory,
    private val owner:()->SessionDbKeyOwner?,private val worker:Executor,private val main:Executor,
    private val blocked:()->Unit,
    private val memory:OutstandingCoreRead,
    private val materializing:(()->Unit)?=null,
    private val mutations:WorkspaceMutationInvalidation?=null,
    private val testReservation:((ActiveCoreReservation)->ActiveCoreReservation)?=null,
    private val leadershipState:(suspend (ActionDatabase,StorageContext,String)->SensitiveResult<SensitiveState>)?=null,
    private val supplementMaterializing:((WorkspacePart)->Unit)?=null):WorkspaceRepository {
    constructor(app:Context,access:StorageAccess,inventory:ProductInventory,owner:()->SessionDbKeyOwner?,
        worker:Executor,main:Executor,blocked:()->Unit):this(app,access,inventory,owner,worker,main,blocked,OutstandingCoreRead.product,null,null)
    constructor(app:Context,access:StorageAccess,inventory:ProductInventory,owner:()->SessionDbKeyOwner?,
        worker:Executor,main:Executor,blocked:()->Unit,mutations:WorkspaceMutationInvalidation):this(app,access,inventory,owner,worker,main,blocked,OutstandingCoreRead.product,null,mutations)

    private val protocolOrder=Comparator<ProtocolTimelineItem>{a,b->
        val time=a.occurredAt.compareTo(b.occurredAt)
        if(time!=0)time else {val recorded=a.originalRecordedAt.compareTo(b.originalRecordedAt);if(recorded!=0)recorded else a.entryId.compareTo(b.entryId)}
    }
    override fun detail(original:WorkspaceAccess,request:WorkspaceDetailRequest,publish:(WorkspaceDetailResult)->Unit) {
        val scope=original as? OriginalWorkspaceAccess
        fun reject(reason:WorkspaceFailure){publish(WorkspaceDetailResult.Rejected(reason))}
        if(scope==null || scope.source!==access || Thread.currentThread()!==scope.thread ||
            !memory.ownsFullview(scope.entry) || !scope.entry.readable() || !scope.original.check(AccessPoint.STEP) ||
            scope.request.ticket?.active==false || !request.ticket.active || request.id.isBlank() ||
            request.actionId!=scope.request.actionId || request.status!=scope.status || request.generation!=scope.original.generation) {
            reject(WorkspaceFailure.OWNER_UNCONFIRMED);return
        }
        val baseline=request.baseline
        if(baseline!=null && baseline.access!==scope){reject(WorkspaceFailure.STALE);return}
        val control=try{if(baseline==null)OriginalWorkspaceAccess.cost else WorkspaceConsistency.cost(baseline,request.kind)}catch(_:ArithmeticException){reject(WorkspaceFailure.BUDGET_REFUSED);return}
        if(!scope.entry.reserve(control)){reject(WorkspaceFailure.BUDGET_REFUSED);return}
        val c=access.acquire(AccessScope.READY) ?: return
        if(c.generation!=request.generation || !c.check(AccessPoint.OPERATION))return
        // This asks the existing writer owner. Pending/UNKNOWN/uncertain cleanup deny here.
        val authority=mutations?.beforeActiveRead(request.actionId,c.generation)
            ?: run{reject(WorkspaceFailure.OWNER_UNCONFIRMED);return}
        val child=scope.entry.supplement(WorkspacePart.TASKS)
            ?: run{reject(scope.entry.additionalFailure);return}
        val read=WorkspaceReadRequest(request.actionId,request.status,request.generation,emptySet(),ticket=request.ticket)
        val fresh=OriginalWorkspaceAccess(scope.entry,access,c,authority,mutations!!,read,scope.thread)
        fun current()=fresh.liveOnWorker() && request.ticket.active
        fun deliver(result:WorkspaceDetailResult) {
            val handoff=child.main(Runnable{fresh.publish(request.actionId,request.status,request.generation){if(current())publish(result)}})
            try{main.execute(handoff)}catch(_:java.util.concurrent.RejectedExecutionException){child.mainRejected()}
        }
        val work=child.worker(Runnable {
            try {
                val result=DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                    if(!current())throw StorageAccessRevoked()
                    (owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){db->db.withReadTransaction {
                        val names=when(request.kind){
                            WorkspaceDetailKind.PERSON->if(request.membership)setOf("actions","persons","person_role_plans","person_planned_roles","person_operational_states") else setOf("actions","persons")
                            WorkspaceDetailKind.TASK->FullviewPreflight.task
                            WorkspaceDetailKind.PLAN->setOf("actions","persons","person_role_plans","person_planned_roles")
                            WorkspaceDetailKind.MATERIAL->FullviewPreflight.material
                            WorkspaceDetailKind.EXPENSE->FullviewPreflight.expense
                            WorkspaceDetailKind.STATE->setOf("actions","person_operational_states")
                            WorkspaceDetailKind.ROLE->setOf("actions","roles")
                            WorkspaceDetailKind.PROTOCOL->FullviewPreflight.protocol
                            WorkspaceDetailKind.MEMORY->FullviewPreflight.memory
                            WorkspaceDetailKind.PROTOCOL_TIMELINE->setOf("actions","protocol_entries","protocol_revisions")
                            WorkspaceDetailKind.MEMORY_HEADERS->setOf("actions","memory_reports")
                            WorkspaceDetailKind.MATERIAL_BOARD->FullviewPreflight.material
                            WorkspaceDetailKind.EXPENSE_BOARD->FullviewPreflight.expense
                        }
                        // Validate held unrelated rows before the target DTO is materialized.
                        if(baseline!=null && !WorkspaceConsistency(this,::current).matches(baseline,request))
                            return@withReadTransaction WorkspaceDetailResult.Rejected(WorkspaceFailure.STALE)
                        val allNames=if(baseline?.leadershipState!=null)names+setOf("leadership","sensitive_person_areas") else names
                        val reservation=FullviewPreflight(this,c,::current).prepare(request.actionId,allNames,detail=request,presentations=false)
                        if(!current())throw StorageAccessRevoked()
                        if(!child.admit(reservation))return@withReadTransaction WorkspaceDetailResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
                        if(!current())throw StorageAccessRevoked()
                        val action=db.actions().get(request.actionId)?.model()
                            ?: return@withReadTransaction WorkspaceDetailResult.Rejected(WorkspaceFailure.NOT_FOUND)
                        if(action.status!=request.status || !OperationalRules.mayRead(action.status))
                            return@withReadTransaction WorkspaceDetailResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                        val a=request.actionId;val id=request.id
                        val found=when(request.kind){
                            WorkspaceDetailKind.PERSON->db.persons().get(a,id)?.model()?.let{person->
                                if(!request.membership)WorkspaceDetail(fresh,person=person) else {
                                    val header=db.operations().personPlan(a,id)?:throw ProductStorageFailure()
                                    if(header.editRevision<0)throw ProductStorageFailure()
                                    val ids=db.operations().personAssignments(a,id).map{it.roleId}.toSet()
                                    if(!OperationalRules.validPlan(ids,header.primaryRoleId))throw ProductStorageFailure()
                                    val plan=PersonRolePlan(ids,header.primaryRoleId,header.editRevision)
                                    WorkspaceDetail(fresh,person=person,operationalPerson=OperationalPerson(person,plan,db.operations().state(a,id)?.model()))
                                }
                            }
                            WorkspaceDetailKind.MATERIAL_BOARD->{
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                when(val result=MaterialStore(db,guarded,System::currentTimeMillis).board(a)){
                                    is OperationsResult.Value->WorkspaceDetail(fresh,materialBoard=result.value)
                                    is OperationsResult.Rejected->null
                                }
                            }
                            WorkspaceDetailKind.EXPENSE_BOARD->{
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                when(val result=ExpenseStore(db,guarded,System::currentTimeMillis).board(a)){
                                    is OperationsResult.Value->WorkspaceDetail(fresh,expenseBoard=result.value)
                                    is OperationsResult.Rejected->null
                                }
                            }
                            WorkspaceDetailKind.PROTOCOL_TIMELINE->{
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                when(val result=ProtocolStore(db,guarded,System::currentTimeMillis).timeline(a)){
                                    is ProtocolResult.Value->WorkspaceDetail(fresh,protocolTimeline=result.value)
                                    is ProtocolResult.Rejected->null
                                }
                            }
                            WorkspaceDetailKind.MEMORY_HEADERS->{
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                when(val result=MemoryReportStore(db,guarded,System::currentTimeMillis).list(a)){
                                    is OperationsResult.Value->WorkspaceDetail(fresh,memoryHeaders=result.value)
                                    is OperationsResult.Rejected->null
                                }
                            }
                            WorkspaceDetailKind.STATE->db.operations().state(a,id)?.model()?.let{WorkspaceDetail(fresh,operationalState=it)}
                            WorkspaceDetailKind.PROTOCOL->{
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                when(val result=ProtocolStore(db,guarded,System::currentTimeMillis).detail(a,id)){
                                    is ProtocolResult.Value->WorkspaceDetail(fresh,protocolDetail=result.value)
                                    is ProtocolResult.Rejected->null
                                }
                            }
                            WorkspaceDetailKind.MEMORY->{
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                when(val result=MemoryReportStore(db,guarded,System::currentTimeMillis).detail(a,id)){
                                    is OperationsResult.Value->WorkspaceDetail(fresh,memoryDetail=result.value)
                                    is OperationsResult.Rejected->null
                                }
                            }
                            WorkspaceDetailKind.TASK->db.tasks().get(a,id)?.model()?.let{WorkspaceDetail(fresh,task=it)}
                            WorkspaceDetailKind.ROLE->db.operations().role(a,id)?.model()?.let{WorkspaceDetail(fresh,role=it)}
                            WorkspaceDetailKind.MATERIAL->db.materials().get(a,id)?.model()?.let{WorkspaceDetail(fresh,material=it)}
                            WorkspaceDetailKind.EXPENSE->{
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                when(val r=ExpenseStore(db,guarded,System::currentTimeMillis).detail(a,id)){
                                    is OperationsResult.Value->WorkspaceDetail(fresh,expense=r.value)
                                    is OperationsResult.Rejected->null
                                }
                            }
                            WorkspaceDetailKind.PLAN->{
                                val person=db.persons().get(a,id)?.model()
                                val header=db.operations().personPlan(a,id)
                                if(person==null || header==null)null else {
                                    if(header.editRevision<0)throw ProductStorageFailure()
                                    val ids=db.operations().personAssignments(a,id).map{it.roleId}.toSet()
                                    if(!OperationalRules.validPlan(ids,header.primaryRoleId))throw ProductStorageFailure()
                                    WorkspaceDetail(fresh,person=person,plan=ExpectedPersonRolePlan(a,id,PersonRolePlan(ids,header.primaryRoleId,header.editRevision)))
                                }
                            }
                        }
                        if(!current())throw StorageAccessRevoked()
                        val value=found ?: if(request.membership && request.kind!=WorkspaceDetailKind.PROTOCOL && request.kind!=WorkspaceDetailKind.MEMORY && request.kind!=WorkspaceDetailKind.PROTOCOL_TIMELINE && request.kind!=WorkspaceDetailKind.MEMORY_HEADERS && request.kind!=WorkspaceDetailKind.MATERIAL_BOARD && request.kind!=WorkspaceDetailKind.EXPENSE_BOARD)WorkspaceDetail(fresh,removed=true) else null
                        if(value==null)WorkspaceDetailResult.Rejected(WorkspaceFailure.NOT_FOUND) else {
                            val updated=baseline?.let{b->
                                // Explicit single-identity membership delta. Every unrelated row was
                                // compared above in this same transaction; never re-read a board.
                                fun <T> patch(old:List<T>,fresh:T?,key:(T)->String,order:Comparator<T>?):List<T> {
                                    if(!request.membership)return old.map{if(key(it)==id)fresh?:it else it}
                                    val changed=buildList{for(v in old)if(key(v)!=id)add(v);if(fresh!=null)add(fresh)}
                                    return if(order==null)changed else changed.sortedWith(order)
                                }
                                val people=if(request.kind==WorkspaceDetailKind.PERSON && request.membership)
                                    patch(b.operations.persons,value.operationalPerson,{it.person.id},compareBy<OperationalPerson>{it.person.createdAt}.thenBy{it.person.id})
                                else if(request.kind==WorkspaceDetailKind.STATE)b.operations.persons.map{p->if(p.person.id==id)p.copy(state=value.operationalState) else p}
                                else if(value.person!=null || value.plan!=null)b.operations.persons.map{p->
                                    if(p.person.id!=id)p else OperationalPerson(value.person?:p.person,value.plan?.plan?:p.plan,p.state)
                                }else b.operations.persons
                                val roles=if(request.kind==WorkspaceDetailKind.ROLE){
                                    val position=if(value.role==null)-1 else db.operations().rolePosition(a,id)
                                    buildList{
                                        var index=0
                                        for(role in b.operations.roles)if(role.id!=id){
                                            if(index==position)add(checkNotNull(value.role))
                                            add(role);index++
                                        }
                                        if(index==position)add(checkNotNull(value.role))
                                    }
                                }else b.operations.roles
                                val operations=if(people===b.operations.persons && roles===b.operations.roles)b.operations else b.operations.copy(persons=people,roles=roles)
                                val tasks=if(request.kind==WorkspaceDetailKind.TASK)b.tasks?.let{it.copy(tasks=patch(it.tasks,value.task,{t->t.id},null))} else b.tasks
                                val materials=if(request.kind==WorkspaceDetailKind.MATERIAL_BOARD)value.materialBoard else if(request.kind==WorkspaceDetailKind.MATERIAL)b.materialBoard?.let{it.copy(items=patch(it.items,value.material,{m->m.id},compareBy<MaterialItem>{m->m.createdAt}.thenBy{m->m.id}))} else b.materialBoard
                                val expenses=if(request.kind==WorkspaceDetailKind.EXPENSE_BOARD)value.expenseBoard else if(request.kind==WorkspaceDetailKind.EXPENSE)b.expenseBoard?.let{it.copy(expenses=patch(it.expenses,value.expense,{e->e.id},compareBy<Expense>{e->e.createdAt}.thenBy{e->e.id}))} else b.expenseBoard
                                val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                                val protection=if(b.leadershipState!=null)leadershipState?.invoke(db,guarded,a) else null
                                val materialSummary=if(b.material!=null)when(val x=MaterialStore(db,guarded,System::currentTimeMillis).summary(a)){is OperationsResult.Value->x.value;else->throw ProductStorageFailure()} else null
                                val expenseSummary=if(b.expenses!=null)when(val x=ExpenseStore(db,guarded,System::currentTimeMillis).summary(a)){is OperationsResult.Value->x.value;else->throw ProductStorageFailure()} else null
                                val protocol=if(b.protocol!=null)when(val x=ProtocolStore(db,guarded,System::currentTimeMillis).summary(a)){is ProtocolResult.Value->x.value;else->throw ProductStorageFailure()} else null
                                val memorySummary=if(b.memory!=null)when(val x=MemoryReportStore(db,guarded,System::currentTimeMillis).summary(a)){is OperationsResult.Value->x.value;else->throw ProductStorageFailure()} else null
                                val documents=if(b.documents!=null)DocumentSummary(db.documents().count(a)) else null
                                val timeline=if(request.kind==WorkspaceDetailKind.PROTOCOL_TIMELINE)value.protocolTimeline else if(request.kind==WorkspaceDetailKind.PROTOCOL && b.protocolTimeline!=null){
                                    val d=value.protocolDetail?:throw ProductStorageFailure()
                                    val v=d.current.revision;val i=v.input
                                    val row=ProtocolTimelineItem(d.entry.id,v.id,v.revisionNumber,v.revisionType,i.classification,i.templateKey,i.title,i.occurredAt,i.occurredAtApproximate,v.recordedAt,v.recordedAfterCompletion,d.entry.createdAfterCompletion,v.mistaken,d.revisions.first().revision.recordedAt)
                                    patch(checkNotNull(b.protocolTimeline),row,{it.entryId},protocolOrder)
                                }else b.protocolTimeline
                                val headers=if(request.kind==WorkspaceDetailKind.MEMORY_HEADERS)value.memoryHeaders else if(request.kind==WorkspaceDetailKind.MEMORY && b.memoryHeaders!=null)
                                    patch(checkNotNull(b.memoryHeaders),value.memoryDetail?.header,{it.id},MemoryReportRules.order) else b.memoryHeaders
                                b.copy(operations=operations,tasks=tasks,materialBoard=materials,expenseBoard=expenses,
                                    access=fresh,leadershipState=protection,material=materialSummary,expenses=expenseSummary,
                                    protocol=protocol,memory=memorySummary,documents=documents,protocolTimeline=timeline,memoryHeaders=headers)
                            }
                            WorkspaceDetailResult.Value(value.copy(workspace=updated))
                        }
                    }}}
                }
                deliver(result)
            }catch(f:StorageAccessRevoked){if(f.suppressed.isNotEmpty())child.unconfirmed()}
             catch(f:BoundedActiveRejected){
                if(f.suppressed.isNotEmpty() || f.status!=BoundedActiveStatus.BUDGET_REFUSED){child.unconfirmed()}
                else deliver(WorkspaceDetailResult.Rejected(WorkspaceFailure.BUDGET_REFUSED))
             }catch(_:Exception){child.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(work)}catch(_:java.util.concurrent.RejectedExecutionException){child.workerRejected()}
    }

    /** Missing modules only, never a second Operations/Task/Workspace board graph. */
    override fun supplement(access:WorkspaceAccess,request:WorkspaceReadRequest,publish:(WorkspaceSupplementResult)->Unit) {
        val scope=access as? OriginalWorkspaceAccess ?: return
        if(scope.source!==this.access || !memory.ownsFullview(scope.entry) || request.parts.size!=1)return
        val part=request.parts.single()
        if(part !in supplementParts)return
        var child:OutstandingCoreRead.FullviewEntry.FullviewSupplement?=null
        val accepted=scope.publish(request.actionId,request.status,request.generation){child=scope.entry.supplement(part)}
        if(!accepted)return
        val original=child ?: run {
            scope.publish(request.actionId,request.status,request.generation){if(request.ticket?.active!=false)publish(WorkspaceSupplementResult.Rejected(scope.entry.additionalFailure))};return
        }
        val c=scope.original
        fun current()=scope.liveOnWorker() && request.ticket?.active!=false
        val task=original.worker(Runnable {
            try {
                val result=DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                    if(!current())throw StorageAccessRevoked()
                    (owner()?:throw ProductStorageFailure()).withDbKey(c){key->database.use(inventory.database,key,c){db->db.withReadTransaction {
                        val names=when(part){
                            WorkspacePart.PROTOCOL_TIMELINE->setOf("actions","protocol_entries","protocol_revisions")
                            WorkspacePart.MEMORY_HEADERS->setOf("actions","memory_reports")
                            WorkspacePart.MATERIAL_BOARD->FullviewPreflight.material
                            else->FullviewPreflight.expense
                        }
                        val reservation=FullviewPreflight(this,c,::current).prepare(request.actionId,names)
                        if(!current())throw StorageAccessRevoked()
                        if(!original.admit(reservation))return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
                        if(!current())throw StorageAccessRevoked()
                        val guarded=object:StorageContext by c {override fun check(point:AccessPoint)=current() && c.check(point)}
                        supplementMaterializing?.invoke(part)
                        val data=if(part==WorkspacePart.PROTOCOL_TIMELINE)when(val r=ProtocolStore(db,guarded,System::currentTimeMillis).timeline(request.actionId)){
                            is ProtocolResult.Value->WorkspaceSupplement(request.actionId,c.generation,protocolTimeline=r.value)
                            is ProtocolResult.Rejected->return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                        }else if(part==WorkspacePart.MEMORY_HEADERS)when(val r=MemoryReportStore(db,guarded,System::currentTimeMillis).list(request.actionId)){
                            is OperationsResult.Value->WorkspaceSupplement(request.actionId,c.generation,memoryHeaders=r.value)
                            is OperationsResult.Rejected->return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                        }else if(part==WorkspacePart.MATERIAL_BOARD)when(val r=MaterialStore(db,guarded,System::currentTimeMillis).board(request.actionId)){
                            is OperationsResult.Value->WorkspaceSupplement(request.actionId,c.generation,material=r.value)
                            is OperationsResult.Rejected->return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                        }else when(val r=ExpenseStore(db,guarded,System::currentTimeMillis).board(request.actionId)){
                            is OperationsResult.Value->WorkspaceSupplement(request.actionId,c.generation,expenses=r.value)
                            is OperationsResult.Rejected->return@withReadTransaction WorkspaceSupplementResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                        }
                        WorkspaceSupplementResult.Value(data)
                    }}}
                }
                val delivery=original.main(Runnable{scope.publish(request.actionId,request.status,request.generation){if(current())publish(result)}})
                try{main.execute(delivery)}catch(_:java.util.concurrent.RejectedExecutionException){original.mainRejected()}
            }catch(f:StorageAccessRevoked){if(f.suppressed.isNotEmpty())original.unconfirmed()}
             catch(f:BoundedActiveRejected){
                if(f.suppressed.isNotEmpty()){original.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
                else if(f.status==BoundedActiveStatus.BUDGET_REFUSED){
                    val delivery=original.main(Runnable{scope.publish(request.actionId,request.status,request.generation){if(current())publish(WorkspaceSupplementResult.Rejected(WorkspaceFailure.BUDGET_REFUSED))}})
                    try{main.execute(delivery)}catch(_:java.util.concurrent.RejectedExecutionException){original.mainRejected()}
                }else if(f.status!=BoundedActiveStatus.ACCESS_REVOKED){original.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
             }
             catch(_:Exception){original.unconfirmed();main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(task)}catch(_:java.util.concurrent.RejectedExecutionException){original.workerRejected()}
    }
    private val supplementParts=setOf(WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD,WorkspacePart.PROTOCOL_TIMELINE,WorkspacePart.MEMORY_HEADERS)
    private val database=ActionDatabaseAccess(app.applicationContext)
    private class ReadCancelled:RuntimeException()
    override fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit) {
        val c=access.acquire(AccessScope.READY) ?: return
        if(request.generation!=c.generation)return
        fun refuse(){main.execute{c.publish{if(request.ticket?.active!=false)publish(WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))}}}
        if(memory.fullviewOverlapUnconfirmed()) {
            refuse()
            return
        }
        val entry=memory.beginFullview(request.actionId,request.generation) ?: run {
            refuse()
            return
        }
        val original=mutations?.beforeActiveRead(request.actionId,c.generation)
        if(original==null){entry.workerRejected();refuse();return}
        val publicationThread=Thread.currentThread()
        val frozen=request.copy(parts=request.parts.toSet())
        val ticket=DocumentNamespaceLane.ticket()
        fun requireRequest(){if(frozen.ticket?.active==false || !original.isLive())throw ReadCancelled()}
        val work=entry.worker(Runnable {
            try {
                requireRequest()
                val result=DocumentNamespaceLane.exclusive(ticket) {
                    requireRequest()
                    requireOperations(c,AccessPoint.OPERATION)
                    if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                    requireRequest()
                    (owner() ?: throw ProductStorageFailure()).withDbKey(c){key->
                        database.use(inventory.database,key,c){db->db.withReadTransaction {
                            WorkspaceStore(db,object:StorageContext by c { override fun check(point:AccessPoint)=original.isLive() && frozen.ticket?.active!=false && c.check(point) },memory,materializing,this,{original.isLive()},testReservation,leadershipState).read(frozen,entry)
                        }}
                    }.also{requireOperations(c)}
                }
                requireRequest()
                // Envelope/capability allocations are in OriginalWorkspaceAccess.cost.
                val delivered=if(result is WorkspaceResult.Value)WorkspaceResult.Value(result.value.copy(
                    access=OriginalWorkspaceAccess(entry,access,c,original,mutations!!,frozen,publicationThread))) else result
                val delivery=entry.main(Runnable{c.publish{if(frozen.ticket?.active!=false && mutations?.isCurrentActiveRead(original)==true)publish(delivered)}})
                try{main.execute(delivery)}catch(_:java.util.concurrent.RejectedExecutionException){entry.mainRejected()}

            }catch(_:ReadCancelled){/* Superseded before key/DB admission or after full cleanup. */}
             catch(_:StorageAccessRevoked){/* No stale publication. */}
             catch(_:Exception){main.execute{if(c.check(AccessPoint.STEP))blocked()}}
        })
        try{worker.execute(work)}catch(_:java.util.concurrent.RejectedExecutionException){entry.workerRejected()}
        // Uncertain queue acceptance deliberately retains the exclusion until actual dispatch.
    }
}

/** Same existing stores and permission checks, one atomic transaction. No LeadershipStore. */
internal class WorkspaceStore(private val db:ActionDatabase,private val c:StorageContext,
    private val memory:OutstandingCoreRead=OutstandingCoreRead.product,
    private val materializing:(()->Unit)?=null,
    private val sql:androidx.room3.PooledConnection?=null,private val original:()->Boolean={true},
    private val testReservation:((ActiveCoreReservation)->ActiveCoreReservation)?=null,
    private val leadershipState:(suspend (ActionDatabase,StorageContext,String)->SensitiveResult<SensitiveState>)?=null) {
    private fun <T> value(result:OperationsResult<T>):T=when(result) {
        is OperationsResult.Value->result.value
        is OperationsResult.Rejected->throw Rejected(result.reason)
    }
    private class Rejected(val reason:OperationsFailure):RuntimeException()
    suspend fun read(request:WorkspaceReadRequest,entry:OutstandingCoreRead.FullviewEntry?=null):WorkspaceResult {
        requireOperations(c,AccessPoint.OPERATION)
        if(request.generation!=c.generation)throw StorageAccessRevoked()
        if(request.ticket?.active==false)return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        if(entry==null || !memory.enterFullview(entry,request))return WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED)
        val connection=sql ?: return WorkspaceResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED)
        fun current()=original() && request.ticket?.active!=false && c.generation==request.generation && c.check(AccessPoint.STEP)
        try {
            val data=FullviewPreflight(connection,c,::current).prepare(request.actionId,FullviewPreflight.workspace(request.parts))
            // Common capability + bounded control scratch exactly ONCE, never again per supplement.
            val base=ActiveCoreReservation(CoreReadAdmission.add(data.retained,OriginalWorkspaceAccess.cost),CoreReadAdmission.add(data.transition,OriginalWorkspaceAccess.cost))
            val reservation=testReservation?.invoke(base) ?: base
            if(!admitCurrentFullview(entry,reservation,::current))return WorkspaceResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
        }catch(f:BoundedActiveRejected){
            if(f.status==BoundedActiveStatus.ACCESS_REVOKED)throw StorageAccessRevoked()
            if(f.status!=BoundedActiveStatus.BUDGET_REFUSED)throw ProductStorageFailure()
            return WorkspaceResult.Rejected(WorkspaceFailure.BUDGET_REFUSED)
        }
        materializing?.invoke()
        val action=db.actions().get(request.actionId)?.model() ?: return WorkspaceResult.Rejected(WorkspaceFailure.NOT_FOUND)
        requireOperations(c)
        if(request.expectedAction!=null && request.expectedAction!=action)return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        if(!OperationalRules.mayRead(action.status))return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
        if(action.status!=request.status)return WorkspaceResult.Rejected(WorkspaceFailure.STALE)
        val a=action.id;val parts=request.parts
        fun wants(part:WorkspacePart)=part in parts
        val now=System::currentTimeMillis
        return try {
            val operations=value(OperationsStore(db,c,now).board(a))
            val tasks=if(wants(WorkspacePart.TASKS))value(TaskStore(db,c,now).board(a)) else null
            val protocol=if(wants(WorkspacePart.PROTOCOL_SUMMARY))when(val summary=ProtocolStore(db,c,now).summary(a)) {
                is ProtocolResult.Value->summary.value
                is ProtocolResult.Rejected->return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
            } else null
            val material=if(wants(WorkspacePart.MATERIAL_SUMMARY))value(MaterialStore(db,c,now).summary(a)) else null
            val expenses=if(wants(WorkspacePart.EXPENSE_SUMMARY))value(ExpenseStore(db,c,now).summary(a)) else null
            val memory=if(wants(WorkspacePart.MEMORY_SUMMARY))value(MemoryReportStore(db,c,now).summary(a)) else null
            val documents=if(wants(WorkspacePart.DOCUMENT_SUMMARY)) {
                requireOperations(c)
                if(!DocumentRules.mayRead(action.status))return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                val count=db.documents().count(a)
                if(count !in 0..DocumentRules.MAX_DOCUMENTS)throw ProductStorageFailure()
                DocumentSummary(count)
            } else null
            val memoryHeaders=if(wants(WorkspacePart.MEMORY_HEADERS))value(MemoryReportStore(db,c,now).list(a)) else null
            val timeline=if(wants(WorkspacePart.PROTOCOL_TIMELINE))when(val read=ProtocolStore(db,c,now).timeline(a)) {
                is ProtocolResult.Value->read.value
                is ProtocolResult.Rejected->return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
            } else null
            val materialBoard=if(wants(WorkspacePart.MATERIAL_BOARD))value(MaterialStore(db,c,now).board(a)) else null
            val expenseBoard=if(wants(WorkspacePart.EXPENSE_BOARD))value(ExpenseStore(db,c,now).board(a)) else null
            val protection=if(wants(WorkspacePart.LEADERSHIP_STATE)) {
                val producer=leadershipState ?: return WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)
                producer(db,c,a)
            }else null
            requireOperations(c)
            WorkspaceResult.Value(WorkspaceRead(a,action.status,c.generation,parts,operations,tasks,protocol,material,expenses,memory,documents,memoryHeaders,
                timeline,materialBoard,expenseBoard,leadershipState=protection))
        }catch(_:Rejected){WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED)}
    }
}
