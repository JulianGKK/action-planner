package org.pag.domain

/** Explicit read demand for one view, never a prefetch instruction or authorization token. */
enum class WorkspacePart { TASKS, PROTOCOL_SUMMARY, MATERIAL_SUMMARY, EXPENSE_SUMMARY, MEMORY_SUMMARY, DOCUMENT_SUMMARY, MEMORY_HEADERS,
    PROTOCOL_TIMELINE, MATERIAL_BOARD, EXPENSE_BOARD, LEADERSHIP_STATE }
/** Admission/publication cancellation only. Never a session or storage authorization. */
class WorkspaceReadTicket {
    private val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
    val active get()=!cancelled.get()
    fun cancel(){cancelled.set(true)}
}
data class WorkspaceReadRequest(val actionId:String,val status:ActionStatus,val generation:Long,val parts:Set<WorkspacePart>,val expectedAction:Action?=null,
    val ticket:WorkspaceReadTicket?=null)
data class WorkspaceRead(val actionId:String,val status:ActionStatus,val generation:Long,val parts:Set<WorkspacePart>,
    val operations:OperationalBoard,val tasks:TaskBoard?=null,val protocol:ProtocolSummary?=null,
    val material:MaterialSummary?=null,val expenses:ExpenseSummary?=null,val memory:MemoryReportSummary?=null,
    val documents:DocumentSummary?=null,val memoryHeaders:List<MemoryReportHeader>?=null,
    val protocolTimeline:List<ProtocolTimelineItem>?=null,val materialBoard:MaterialBoard?=null,val expenseBoard:ExpenseBoard?=null,
    val access:WorkspaceAccess?=null,val leadershipState:SensitiveResult<SensitiveState>?=null)
/** Metadata capability on the ORIGINAL read, never a payload/session/key owner. No data getter.
 * Reusing a reference requires this gate; new owner allocations require disjoint prior booking.
 */
interface WorkspaceAccess {
    fun publish(actionId:String,status:ActionStatus,generation:Long,work:()->Unit):Boolean
    fun reserve(units:ActiveWorkspaceBudget.Units):Boolean
    /** Existing held draft/callback copies only. Grants no DB read, fresh snapshot or write.
     * Production reacquires the original session binding even after its read epoch ended. */
    fun heldAllowed():Boolean=true
    fun consume(units:ActiveWorkspaceBudget.Units,producer:()->Unit):Boolean {
        if(!reserve(units))return false
        producer();return true
    }
}
data class WorkspaceSupplement(val actionId:String,val generation:Long,
    val material:MaterialBoard?=null,val expenses:ExpenseBoard?=null,
    val protocolTimeline:List<ProtocolTimelineItem>?=null,val memoryHeaders:List<MemoryReportHeader>?=null)
sealed interface WorkspaceSupplementResult {
    data class Value(val value:WorkspaceSupplement):WorkspaceSupplementResult
    data class Rejected(val reason:WorkspaceFailure):WorkspaceSupplementResult
}
enum class WorkspaceFailure { NOT_FOUND, NOT_ALLOWED, STALE, OWNER_UNCONFIRMED, BUDGET_REFUSED }
sealed interface WorkspaceResult {
    data class Value(val value:WorkspaceRead):WorkspaceResult
    data class Rejected(val reason:WorkspaceFailure):WorkspaceResult
}
enum class WorkspaceDetailKind { PERSON, TASK, PLAN, MATERIAL, EXPENSE, ROLE, PROTOCOL, MEMORY, STATE, PROTOCOL_TIMELINE, MEMORY_HEADERS, MATERIAL_BOARD, EXPENSE_BOARD }
data class WorkspaceDetailRequest(val actionId:String,val status:ActionStatus,val generation:Long,
    val kind:WorkspaceDetailKind,val id:String,val ticket:WorkspaceReadTicket,
    val baseline:WorkspaceRead?=null,val expectedAction:Action?=null,
    val membership:Boolean=false,val taskPage:CompactTaskPage?=null,val peoplePage:CompactPeoplePage?=null)
data class WorkspaceDetail(val access:WorkspaceAccess,val person:Person?=null,val task:Task?=null,
    val plan:ExpectedPersonRolePlan?=null,val material:MaterialItem?=null,val expense:Expense?=null,
    val workspace:WorkspaceRead?=null,val removed:Boolean=false,val operationalPerson:OperationalPerson?=null,val role:Role?=null,
    val protocolDetail:ProtocolEntryDetail?=null,val memoryDetail:MemoryReportDetail?=null,val operationalState:PersonOperationalState?=null,
    val protocolTimeline:List<ProtocolTimelineItem>?=null,val memoryHeaders:List<MemoryReportHeader>?=null,
    val materialBoard:MaterialBoard?=null,val expenseBoard:ExpenseBoard?=null,val linkedPersons:List<OperationalPerson>?=null,val taskCounts:CompactTaskCounts?=null,val peopleCounts:CompactPeopleCounts?=null)
sealed interface WorkspaceDetailResult {
    data class Value(val value:WorkspaceDetail):WorkspaceDetailResult
    data class Rejected(val reason:WorkspaceFailure):WorkspaceDetailResult
}
data class WorkspacePageRequest(val actionId:String,val generation:Long,val people:Boolean,
    val ticket:WorkspaceReadTicket,val tasksAfter:CompactTaskCursor?=null,val peopleAfter:CompactPeopleCursor?=null,
    val baseline:WorkspaceRead?=null,val expectedAction:Action?=null,val lane:CompactTaskLane=CompactTaskLane.ACTIVE,
    val taskPage:CompactTaskPage?=null,val peoplePage:CompactPeoplePage?=null)
data class WorkspacePage(val access:WorkspaceAccess,val tasks:CompactTaskPage?=null,val people:CompactPeoplePage?=null,val taskCounts:CompactTaskCounts?=null)
sealed interface WorkspacePageResult {
    data class Value(val page:WorkspacePage):WorkspacePageResult
    data class Rejected(val reason:WorkspaceFailure):WorkspacePageResult
}
interface WorkspaceRepository {
    fun page(original:WorkspaceAccess,request:WorkspacePageRequest,publish:(WorkspacePageResult)->Unit) {
        publish(WorkspacePageResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
    }

    /** Explicit single-detail reprojection; old access is provenance, never renewed authority. */
    fun detail(original:WorkspaceAccess,request:WorkspaceDetailRequest,publish:(WorkspaceDetailResult)->Unit) {
        publish(WorkspaceDetailResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
    }

    /** One short, fully preflighted operation; no P3b payload or document content. */
    fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit)
    fun supplement(access:WorkspaceAccess,request:WorkspaceReadRequest,publish:(WorkspaceSupplementResult)->Unit) {
        publish(WorkspaceSupplementResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
    }
}

/** App bridge; repository still supplies all storage/publication authority. */
typealias WorkspaceDetailLoader = (WorkspaceDetailKind,String,(WorkspaceDetailResult)->Unit)->Unit
