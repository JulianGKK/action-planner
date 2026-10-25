package org.pag.domain

enum class TaskPriority { LOW, NORMAL, HIGH }
enum class TaskStatus { OPEN, IN_PROGRESS, DONE, CANCELLED }
enum class TaskEventType { CREATED, STARTED, COMPLETED, CANCELLED, REOPENED }
data class TaskInput(val title:String, val priority:TaskPriority=TaskPriority.NORMAL,
    val assigneePersonId:String?=null, val assigneeRoleId:String?=null, val affectedPersonId:String?=null,
    val dueAt:Long?=null, val sourceTemplateKey:String?=null)
data class Task(val id:String,val actionId:String,val title:String,val priority:TaskPriority,val status:TaskStatus,
    val assigneePersonId:String?,val assigneeRoleId:String?,val affectedPersonId:String?,val dueAt:Long?,
    val sourceTemplateKey:String?,val createdAt:Long,val updatedAt:Long,val completedAt:Long?,val editRevision:Long) {
    fun input()=TaskInput(title,priority,assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey)
}
data class TaskEvent(val id:String,val actionId:String,val taskId:String,val type:TaskEventType,val occurredAt:Long)
data class TaskTemplate(val key:String,val title:String) { fun input()=TaskInput(title,sourceTemplateKey=key) }
data class TaskBoard(val actionId:String,val tasks:List<Task>,val events:List<TaskEvent>,
    /** False means history was not requested; an empty list is NOT an empty chronicle. */
    val eventsComplete:Boolean=true) {
    val presentation=TaskPresentation(tasks)
    val active get()=presentation.all.active
    val closed get()=presentation.all.closed
    fun affectedCount(personId:String)=tasks.count{TaskRules.active(it.status) && it.affectedPersonId==personId}
}
object TaskRules {
    /** Stored CREATED/STARTED/COMPLETED/CANCELLED/REOPENED, across all tasks of one action.
     * Legacy excess remains readable; this limits new events, never trims history.
     */
    const val MAX_TASK_EVENTS_PER_ACTION = 5_000L
    fun valid(input:TaskInput)=trimmedCodePoints(input.title) in 1..120 &&
        (input.assigneePersonId==null || input.assigneeRoleId==null)
    fun mayRead(status:ActionStatus)=status in setOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)
    fun mayWrite(status:ActionStatus)=status==ActionStatus.PREPARATION || status==ActionStatus.ACTIVE
    fun mayDelete(status:ActionStatus)=status==ActionStatus.PREPARATION
    fun active(status:TaskStatus)=status==TaskStatus.OPEN || status==TaskStatus.IN_PROGRESS
    fun event(from:TaskStatus,to:TaskStatus):TaskEventType?=when {
        from==TaskStatus.OPEN && to==TaskStatus.IN_PROGRESS->TaskEventType.STARTED
        active(from) && to==TaskStatus.DONE->TaskEventType.COMPLETED
        active(from) && to==TaskStatus.CANCELLED->TaskEventType.CANCELLED
        !active(from) && to==TaskStatus.OPEN->TaskEventType.REOPENED
        else->null
    }
    fun transition(task:Task,to:TaskStatus,now:Long):Task?=event(task.status,to)?.let {
        task.copy(status=to,updatedAt=now,completedAt=now.takeIf{to==TaskStatus.DONE})
    }
    // Primitive comparisons avoid a new boxed timestamp at every sort comparison.
    val order=Comparator<Task>{a,b->
        var result=b.priority.ordinal.compareTo(a.priority.ordinal)
        if(result==0){val ad=a.dueAt;val bd=b.dueAt;result=when{ad==null && bd==null->0;ad==null->1;bd==null->-1;else->ad.compareTo(bd)}}
        if(result==0)result=a.createdAt.compareTo(b.createdAt)
        if(result==0)result=a.id.compareTo(b.id)
        result
    }
    val closedOrder=Comparator<Task>{a,b->b.updatedAt.compareTo(a.updatedAt)}
    val templates=listOf(
        TaskTemplate("RALLY_AT_ACTION","Eilkundgebung gegenüber der Aktion vorbereiten/anmelden"),
        TaskTemplate("RALLY_AT_CUSTODY_SUPPORT","Eilkundgebung für Gesa-Support vorbereiten/anmelden"),
        TaskTemplate("PREPARE_ACTION_PHONE","Aktionshandy vorbereiten"),
        TaskTemplate("CHECK_COMMUNICATION","Kommunikationswege prüfen"),
        TaskTemplate("ASSIGN_PRESS","Pressezuständigkeit klären"))
}
interface TaskRepository {
    fun workspaceHistory(access:WorkspaceAccess,expected:Task,after:CompactEventCursor?,page:Long?,publish:(CompactTaskResult)->Unit) {
        publish(CompactTaskResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED))
    }

    fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit)
    /** One <=32-event page replaces the previous page. Cursor is metadata, never authority. */
    fun history(expected:Task,after:CompactEventCursor?,publish:(CompactTaskResult)->Unit) {
        publish(CompactTaskResult.Rejected(BoundedActiveStatus.STORAGE_FAILURE))
    }
    /** Explicit random entry; does not retain a position or authority across session revoke. */
    fun historyPage(expected:Task,page:Long,publish:(CompactTaskResult)->Unit) {
        publish(CompactTaskResult.Rejected(BoundedActiveStatus.STORAGE_FAILURE))
    }
    fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit)
    fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit)
    fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit)
    fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit)
}
