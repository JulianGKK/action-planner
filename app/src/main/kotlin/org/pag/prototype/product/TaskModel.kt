package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

/** Ephemeral drafts; persisted status and session authority remain in the repository. */
class TaskModel(private val repository:TaskRepository?,private val session:()->SessionSnapshot,
    private val action:()->Action?,private val interaction:()->Unit,private val leaveSensitive:()->Unit,
    private val suppliedBoard: (() -> TaskBoard?)? = null,
    private val refreshWorkspace: (() -> Unit)? = null,
    private val feedback: (String) -> Unit = {}) {
    private var ownBoard by mutableStateOf<TaskBoard?>(null)
    val board: TaskBoard? get() = if (suppliedBoard != null) suppliedBoard.invoke() else ownBoard
    var history by mutableStateOf<CompactTaskEvents?>(null);private set
    var historyBusy by mutableStateOf(false);private set
    var historyError by mutableStateOf<BoundedActiveStatus?>(null);private set
    var historyOffset by mutableStateOf(0L);private set
    private var historyRequest=0L
    private fun clearHistory(){historyRequest++;history=null;historyBusy=false;historyError=null;historyOffset=0}
    /** Replace one page; never accumulate a full chronicle in the model or Compose. */
    fun loadHistory(restart:Boolean=false)=requestHistory(restart,null)
    fun loadHistoryPage(page:Long)=requestHistory(true,page)
    private fun requestHistory(restart:Boolean,targetPage:Long?)=guardedInteraction { _,current ->
        val task=selected
        if(repository!=null && task!=null && !form && !busy && !historyBusy && current()) {
            val previous=history
            val after=if(restart) null else previous?.next
            if(!restart && previous!=null && after==null)return@guardedInteraction
            val offset=try {
                if(targetPage!=null){if(targetPage<1)return@guardedInteraction;Math.multiplyExact(targetPage-1,32L)}
                else if(after==null)0L else historyOffset+previous!!.events.size
            }catch(_:ArithmeticException){return@guardedInteraction}
            val token=++historyRequest;val generation=session().generation
            history=null;historyError=null;historyBusy=true
            var delivered=false
            val publish:(CompactTaskResult)->Unit={result->
                if(!delivered){delivered=true
                    if(token==historyRequest && current() && session().generation==generation && selected==task && !form){
                        historyBusy=false
                        when(result){
                            is CompactTaskResult.Rejected->{historyError=result.reason;historyOffset=0}
                            is CompactTaskResult.Value->{
                                val page=result.payload as? CompactTaskEvents
                                if(page==null || page.total<offset+page.events.size || page.taskId!=task.id || page.state.binding.actionId!=task.actionId || page.state.binding.generation!=generation || page.events.size>32 || page.events.any{it.taskId!=task.id || it.actionId!=task.actionId})
                                    historyError=BoundedActiveStatus.INVALID_DATA
                                else {history=page;historyOffset=offset}
                            }
                        }
                    }
                }
            }
            if(targetPage==null)repository.history(task,after,publish) else repository.historyPage(task,targetPage,publish)
        }
    }
    var selected by mutableStateOf<Task?>(null);private set
    var form by mutableStateOf(false);private set
    var input by mutableStateOf(TaskInput(""));private set
    var filter by mutableStateOf<String?>(null);private set
    var closedExpanded by mutableStateOf(false); private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<OperationsFailure?>(null);private set
    private var createdSelectionId: String? = null
    private var request=0L
    private var editorExpected:Task?=null
    private var editorGeneration=0L
    private var editorActionId:String?=null
    // UI identity only, never a persisted revision or session authority.
    var formRevision by mutableStateOf(0L);private set
    val editingExisting get()=editorExpected!=null
    private fun dropEditor(){formRevision++;editorExpected=null;editorActionId=null}
    private fun bindEditor(task:Task?){dropEditor();editorExpected=task;editorGeneration=session().generation;editorActionId=action()?.id}

    val writable get()=session().state==SessionState.READY && action()?.let{TaskRules.mayWrite(it.status)}==true
    val deletable get()=writable && action()?.status==ActionStatus.PREPARATION
    fun touch()=interaction()
    fun clear(){clearHistory();dropEditor();createdSelectionId=null;request++;ownBoard=null;selected=null;form=false;input=TaskInput("");filter=null;closedExpanded=false;busy=false;error=null}
    fun refresh(){
        clearHistory()
        if (refreshWorkspace != null) { request++; refreshWorkspace.invoke(); return }
        val a=action()?.takeIf{session().state==SessionState.READY && TaskRules.mayRead(it.status)}?:return
        val token=++request;val generation=session().generation
        repository?.board(a.id){r->if(token==request && session().state==SessionState.READY && session().generation==generation && action()?.id==a.id){
            when(r){is OperationsResult.Value->{if(r.value.actionId==a.id){ownBoard=r.value;reconcileSelection()}else error=OperationsFailure.NOT_FOUND};is OperationsResult.Rejected->error=r.reason}
        }}
    }
    internal fun reconcileSelection() {
        val id = createdSelectionId ?: selected?.id
        val previous=selected
        selected = id?.let { target -> board?.tasks?.find { it.id == target } }
        createdSelectionId = null
        if(previous!=selected)clearHistory()
    }
    // Capture the existing session/action binding before callbacks can synchronously revoke it.
    private fun guardedInteraction(publish: (String, () -> Boolean) -> Unit) {
        val before=session();val a=action()?:return;val token=request
        if(before.state!=SessionState.READY || !TaskRules.mayRead(a.status))return
        val current={val now=session();now.state==SessionState.READY && now.generation==before.generation && action()?.id==a.id && request==token}
        interaction()
        if(current())publish(a.id,current)
    }
    fun add(personId:String?=null,template:TaskTemplate?=null)=guardedInteraction { _,current ->
        if(writable && !busy){leaveSensitive();if(current()){clearHistory();bindEditor(null);createdSelectionId=null;selected=null;input=(template?.input()?:TaskInput("")).copy(affectedPersonId=personId);form=true;error=null}}
    }
    fun open(t:Task)=guardedInteraction { id,current ->
        if(!busy && t.actionId==id){leaveSensitive();if(current()){clearHistory();dropEditor();createdSelectionId=null;selected=t;form=false;error=null}}
    }
    fun edit()=guardedInteraction { _,_ -> if(writable && !busy){selected?.let{clearHistory();bindEditor(it);input=it.input();form=true;error=null}} }
    fun change(i:TaskInput)=guardedInteraction { _,_ -> if(writable && !busy)input=i }
    fun showPerson(id:String?)=guardedInteraction { _,_ -> if(!busy){clearHistory();dropEditor();createdSelectionId=null;filter=id;closedExpanded=false;selected=null;form=false;input=TaskInput("")} }
    fun cancel() = guardedInteraction { _, _ ->
        if (!busy) {
            clearHistory();createdSelectionId = null
            val returnToDetail = action()?.status == ActionStatus.ACTIVE && form && selected != null
            dropEditor();form = false; if (!returnToDetail) selected = null
            input = TaskInput(""); error = null
        }
    }
    fun backToTasks() = guardedInteraction { _, _ ->
        if (!busy) { clearHistory();dropEditor();createdSelectionId = null; selected = null; form = false; input = TaskInput(""); error = null }
    }
    fun toggleClosed() = guardedInteraction { _, _ -> if (!busy) closedExpanded = !closedExpanded }
    fun save() {
        val a = action() ?: return
        if (!writable || busy || !form || editorGeneration!=session().generation || editorActionId!=a.id) return
        if (!TaskRules.valid(input)) { error = OperationsFailure.INVALID; return }
        val old = editorExpected
        val draft = input
        var createdId: String? = null
        write(message = if (old == null) "Aufgabe angelegt" else "Aufgabe gespeichert",
            createdFocus = { if (old == null && action()?.status == ActionStatus.ACTIVE) createdId else null }) { done ->
            if (old == null) repository?.create(a.id, draft) { result ->
                createdId = (result as? OperationsResult.Value)?.value?.id
                done(unit(result))
            } else repository?.edit(a.id, old, draft) { done(unit(it)) }
        }
    }
    fun transition(status:TaskStatus){val a=action()?:return;val t=(if(form)editorExpected else selected)?:return;if(!writable || busy)return;write(message=if(status==TaskStatus.OPEN)"Aufgabe wieder geöffnet" else null){done->repository?.transition(a.id,t,status){done(unit(it))}}}
    fun delete(){val a=action()?:return;val t=(if(form)editorExpected else selected)?:return;if(!deletable || busy)return;write{done->repository?.delete(a.id,t,done)}}
    /** Explicit discard-and-reload only. A normal board/workspace refresh never calls this. */
    fun reloadEditor()=guardedInteraction { a,_ ->
        val old=editorExpected
        if(repository!=null && old!=null && form && writable && !busy && error==OperationsFailure.STALE &&
            editorGeneration==session().generation && editorActionId==a){
            val revision=formRevision;val generation=session().generation
            busy=true;var delivered=false
            repository.board(a){result->
                if(!delivered){
                    delivered=true
                    if(session().state==SessionState.READY && session().generation==generation && action()?.id==a &&
                        writable && form && revision==formRevision && editorExpected==old){
                        busy=false
                        when(result){
                            is OperationsResult.Rejected->error=result.reason
                            is OperationsResult.Value->{
                                val fresh=result.value.tasks.singleOrNull{it.id==old.id && it.actionId==a}
                                if(result.value.actionId!=a || fresh==null)error=OperationsFailure.NOT_FOUND
                                else {bindEditor(fresh);selected=fresh;input=fresh.input();error=null}
                            }
                        }
                    }
                }
            }
        }
    }
    private fun unit(r:OperationsResult<Task>):OperationsResult<Unit> = when(r){is OperationsResult.Value->OperationsResult.Value(Unit);is OperationsResult.Rejected->r}
    private fun write(message:String?=null,createdFocus:()->String?={null},work:((OperationsResult<Unit>)->Unit)->Unit)=guardedInteraction { _,_ ->
        if(repository!=null && writable && !busy){
            val revision=formRevision;val generation=session().generation;val actionId=action()?.id
            clearHistory();busy=true;error=null;var delivered=false
            work{r->if(!delivered && session().state==SessionState.READY && session().generation==generation &&
                action()?.id==actionId && formRevision==revision){
                delivered=true
                busy=false;when(r){is OperationsResult.Value->{
                    createdSelectionId = createdFocus()
                    dropEditor();form=false;input=TaskInput("")
                    val generation=session().generation;val actionId=action()?.id
                    refresh()
                    if(session().state==SessionState.READY && session().generation==generation && action()?.id==actionId && message!=null)feedback(message)
                };is OperationsResult.Rejected->error=r.reason}
            }}
        }
    }
}
