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
}
data class WorkspaceSupplement(val actionId:String,val generation:Long,
    val material:MaterialBoard?=null,val expenses:ExpenseBoard?=null)
sealed interface WorkspaceSupplementResult {
    data class Value(val value:WorkspaceSupplement):WorkspaceSupplementResult
    data class Rejected(val reason:WorkspaceFailure):WorkspaceSupplementResult
}
enum class WorkspaceFailure { NOT_FOUND, NOT_ALLOWED, STALE, OWNER_UNCONFIRMED, BUDGET_REFUSED }
sealed interface WorkspaceResult {
    data class Value(val value:WorkspaceRead):WorkspaceResult
    data class Rejected(val reason:WorkspaceFailure):WorkspaceResult
}
interface WorkspaceRepository {
    /** One short, fully preflighted operation; no P3b payload or document content. */
    fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit)
    fun supplement(access:WorkspaceAccess,request:WorkspaceReadRequest,publish:(WorkspaceSupplementResult)->Unit) {
        publish(WorkspaceSupplementResult.Rejected(WorkspaceFailure.OWNER_UNCONFIRMED))
    }
}
