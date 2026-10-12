package org.pag.domain

enum class TaskPriority { LOW, NORMAL, HIGH }
enum class TaskStatus { OPEN, IN_PROGRESS, DONE, CANCELLED }
enum class TaskEventType { CREATED, STARTED, COMPLETED, CANCELLED, REOPENED }
data class TaskInput(val title:String, val priority:TaskPriority=TaskPriority.NORMAL,
    val assigneePersonId:String?=null, val assigneeRoleId:String?=null, val affectedPersonId:String?=null,
    val dueAt:Long?=null, val sourceTemplateKey:String?=null)
data class Task(val id:String,val actionId:String,val title:String,val priority:TaskPriority,val status:TaskStatus,
    val assigneePersonId:String?,val assigneeRoleId:String?,val affectedPersonId:String?,val dueAt:Long?,
    val sourceTemplateKey:String?,val createdAt:Long,val updatedAt:Long,val completedAt:Long?) {
    fun input()=TaskInput(title,priority,assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey)
}
data class TaskEvent(val id:String,val actionId:String,val taskId:String,val type:TaskEventType,val occurredAt:Long)
data class TaskTemplate(val key:String,val title:String) { fun input()=TaskInput(title,sourceTemplateKey=key) }
data class TaskBoard(val actionId:String,val tasks:List<Task>,val events:List<TaskEvent>) {
    val active get()=tasks.filter{TaskRules.active(it.status)}.sortedWith(TaskRules.order)
    val closed get()=tasks.filterNot{TaskRules.active(it.status)}.sortedByDescending{it.updatedAt}
    fun affectedCount(personId:String)=active.count{it.affectedPersonId==personId}
}
object TaskRules {
    fun valid(input:TaskInput)=input.title.trim().let{it.isNotEmpty() && Character.codePointCount(it,0,it.length)<=120} &&
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
    val order=compareByDescending<Task>{it.priority.ordinal}.thenBy{it.dueAt==null}.thenBy{it.dueAt}.thenBy{it.createdAt}.thenBy{it.id}
    val templates=listOf(
        TaskTemplate("RALLY_AT_ACTION","Eilkundgebung gegenüber der Aktion vorbereiten/anmelden"),
        TaskTemplate("RALLY_AT_CUSTODY_SUPPORT","Eilkundgebung für Gesa-Support vorbereiten/anmelden"),
        TaskTemplate("PREPARE_ACTION_PHONE","Aktionshandy vorbereiten"),
        TaskTemplate("CHECK_COMMUNICATION","Kommunikationswege prüfen"),
        TaskTemplate("ASSIGN_PRESS","Pressezuständigkeit klären"))
}
interface TaskRepository {
    fun board(actionId:String,publish:(OperationsResult<TaskBoard>)->Unit)
    fun create(actionId:String,input:TaskInput,publish:(OperationsResult<Task>)->Unit)
    fun edit(actionId:String,expected:Task,input:TaskInput,publish:(OperationsResult<Task>)->Unit)
    fun transition(actionId:String,expected:Task,status:TaskStatus,publish:(OperationsResult<Task>)->Unit)
    fun delete(actionId:String,expected:Task,publish:(OperationsResult<Unit>)->Unit)
}
