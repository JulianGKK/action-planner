package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

/** Ephemeral drafts; persisted status and session authority remain in the repository. */
class TaskModel(private val repository:TaskRepository?,private val session:()->SessionSnapshot,
    private val action:()->Action?,private val interaction:()->Unit,private val leaveSensitive:()->Unit) {
    var board by mutableStateOf<TaskBoard?>(null);private set
    var selected by mutableStateOf<Task?>(null);private set
    var form by mutableStateOf(false);private set
    var input by mutableStateOf(TaskInput(""));private set
    var filter by mutableStateOf<String?>(null);private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<OperationsFailure?>(null);private set
    private var request=0L
    val writable get()=session().state==SessionState.READY && action()?.let{TaskRules.mayWrite(it.status)}==true
    val deletable get()=writable && action()?.status==ActionStatus.PREPARATION
    fun touch()=interaction()
    fun clear(){request++;board=null;selected=null;form=false;input=TaskInput("");filter=null;busy=false;error=null}
    fun refresh(){
        val a=action()?.takeIf{session().state==SessionState.READY && TaskRules.mayRead(it.status)}?:return
        val token=++request;val generation=session().generation
        repository?.board(a.id){r->if(token==request && session().state==SessionState.READY && session().generation==generation && action()?.id==a.id){
            when(r){is OperationsResult.Value->{board=r.value;selected=selected?.let{old->r.value.tasks.find{it.id==old.id}}};is OperationsResult.Rejected->error=r.reason}
        }}
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
        if(writable && !busy){leaveSensitive();if(current()){selected=null;input=(template?.input()?:TaskInput("")).copy(affectedPersonId=personId);form=true;error=null}}
    }
    fun open(t:Task)=guardedInteraction { id,current ->
        if(!busy && t.actionId==id){leaveSensitive();if(current()){selected=t;form=false;error=null}}
    }
    fun edit()=guardedInteraction { _,_ -> if(writable && !busy){selected?.let{input=it.input();form=true;error=null}} }
    fun change(i:TaskInput)=guardedInteraction { _,_ -> if(writable && !busy)input=i }
    fun showPerson(id:String?)=guardedInteraction { _,_ -> if(!busy){filter=id;selected=null;form=false;input=TaskInput("")} }
    fun cancel(){interaction();if(busy)return;form=false;selected=null;input=TaskInput("");error=null}
    fun save(){val a=action()?:return;if(!writable || busy)return;if(!TaskRules.valid(input)){error=OperationsFailure.INVALID;return}
        val old=selected;write{done->if(old==null)repository?.create(a.id,input){done(unit(it))}else repository?.edit(a.id,old,input){done(unit(it))}}
    }
    fun transition(status:TaskStatus){val a=action()?:return;val t=selected?:return;if(!writable || busy)return;write{done->repository?.transition(a.id,t,status){done(unit(it))}}}
    fun delete(){val a=action()?:return;val t=selected?:return;if(!deletable || busy)return;write{done->repository?.delete(a.id,t,done)}}
    private fun unit(r:OperationsResult<Task>):OperationsResult<Unit> = when(r){is OperationsResult.Value->OperationsResult.Value(Unit);is OperationsResult.Rejected->r}
    private fun write(work:((OperationsResult<Unit>)->Unit)->Unit)=guardedInteraction { _,current ->
        if(repository!=null && writable && !busy){
            busy=true;error=null
            work{r->if(current()){
                busy=false;when(r){is OperationsResult.Value->{form=false;input=TaskInput("");refresh()};is OperationsResult.Rejected->error=r.reason}
            }}
        }
    }
}
