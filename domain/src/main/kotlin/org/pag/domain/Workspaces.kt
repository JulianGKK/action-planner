package org.pag.domain

/** Explicit read demand for one view, never a prefetch instruction or authorization token. */
enum class WorkspacePart { TASKS, PROTOCOL_SUMMARY, MATERIAL_SUMMARY, EXPENSE_SUMMARY, MEMORY_SUMMARY, DOCUMENT_SUMMARY, MEMORY_HEADERS,
    PROTOCOL_TIMELINE, MATERIAL_BOARD, EXPENSE_BOARD }
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
    val protocolTimeline:List<ProtocolTimelineItem>?=null,val materialBoard:MaterialBoard?=null,val expenseBoard:ExpenseBoard?=null)
enum class WorkspaceFailure { NOT_FOUND, NOT_ALLOWED, STALE, OWNER_UNCONFIRMED }
sealed interface WorkspaceResult {
    data class Value(val value:WorkspaceRead):WorkspaceResult
    data class Rejected(val reason:WorkspaceFailure):WorkspaceResult
}
interface WorkspaceRepository {
    /** One short, fully preflighted operation; no P3b payload or document content. */
    fun read(request:WorkspaceReadRequest,publish:(WorkspaceResult)->Unit)
}
