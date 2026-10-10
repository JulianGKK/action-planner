package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

internal enum class OperationsScreen { OVERVIEW, ROLES, PLAN, LIVE }
/** Short-lived UI/navigation state only; the repository enforces persisted status and session. */
class OperationsModel(private val repository:OperationsRepository?,private val session:()->SessionSnapshot,
    private val action:()->Action?,private val interaction:()->Unit,private val leaveSensitive:()->Unit) {
    var board by mutableStateOf<OperationalBoard?>(null);private set
    internal var screen by mutableStateOf(OperationsScreen.OVERVIEW);private set
    var personId by mutableStateOf<String?>(null);private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<OperationsFailure?>(null);private set
    var roleIds by mutableStateOf<Set<String>>(emptySet());private set
    var primaryRoleId by mutableStateOf<String?>(null);private set
    var liveStatus by mutableStateOf(OperationalStatus.NO_STATUS);private set
    var liveRoleId by mutableStateOf<String?>(null);private set
    var livePlace by mutableStateOf("");private set
    private var expected:PersonOperationalState?=null
    private var request=0L
    val planning get()=session().state==SessionState.READY && action()?.let{OperationalRules.mayPlan(it.status)}==true
    val live get()=session().state==SessionState.READY && action()?.let{OperationalRules.mayUpdateSnapshot(it.status)}==true
    val person get()=board?.persons?.singleOrNull{it.person.id==personId}
    fun textInteraction() = interaction()
    fun clear(){request++;board=null;screen=OperationsScreen.OVERVIEW;personId=null;busy=false;error=null;roleIds=emptySet();primaryRoleId=null;expected=null;livePlace="";liveRoleId=null;liveStatus=OperationalStatus.NO_STATUS}
    fun refresh(){
        val a=action()?.takeIf{session().state==SessionState.READY && OperationalRules.mayRead(it.status)}?:return
        val token=++request;val generation=session().generation
        repository?.board(a.id){result->
            if(token!=request || session().state!=SessionState.READY || session().generation!=generation || action()?.id!=a.id)return@board
            when(result){is OperationsResult.Value->board=result.value;is OperationsResult.Rejected->error=result.reason}
        }
    }
    fun roles(){interaction();if(!planning && !live)return;leaveSensitive();screen=OperationsScreen.ROLES;error=null}
    fun plan(id:String){interaction();if(!planning || busy)return;val p=board?.persons?.singleOrNull{it.person.id==id}?:return;leaveSensitive();personId=id;roleIds=p.plan.roleIds;primaryRoleId=p.plan.primaryRoleId;screen=OperationsScreen.PLAN;error=null}
    fun update(id:String){interaction();if(!live || busy)return;val p=board?.persons?.singleOrNull{it.person.id==id}?:return;val state=p.state?:return;leaveSensitive();personId=id;expected=state;liveStatus=state.status;liveRoleId=state.currentRoleId;livePlace=state.currentPlaceLabel.orEmpty();screen=OperationsScreen.LIVE;error=null}
    fun cancel(){interaction();if(!busy)finishForm()}
    private fun finishForm(){request++;screen=OperationsScreen.OVERVIEW;personId=null;roleIds=emptySet();primaryRoleId=null;expected=null;livePlace="";liveRoleId=null;liveStatus=OperationalStatus.NO_STATUS;error=null}
    fun toggleRole(id:String){interaction();if(!planning || busy)return;roleIds=if(id in roleIds)roleIds-id else roleIds+id;if(primaryRoleId !in roleIds)primaryRoleId=null}
    fun primary(id:String?){interaction();if(planning && !busy && (id==null || id in roleIds))primaryRoleId=id}
    fun status(status:OperationalStatus){interaction();if(!live || busy)return;if(status!=liveStatus){livePlace="";if(!OperationalRules.roleAllowed(status) || status==OperationalStatus.RELEASED)liveRoleId=null};liveStatus=status}
    fun function(id:String?){interaction();if(live && !busy && liveStatus==OperationalStatus.ON_SITE)liveRoleId=id}
    fun place(value:String){interaction();if(live && !busy)livePlace=value}
    fun savePlan(){val a=action()?:return;val p=personId?:return;if(!planning || busy)return;write{publish->repository?.planRoles(a.id,p,PersonRolePlan(roleIds,primaryRoleId),publish)}}
    fun saveLive(){val a=action()?:return;val p=personId?:return;val old=expected?:return;if(!live || busy)return;if(!OperationalRules.validPlace(livePlace)){error=OperationsFailure.INVALID;return};write{publish->repository?.updateSnapshot(a.id,p,old,liveStatus,liveRoleId,livePlace,publish)}}
    fun createRole(name:String){val a=action()?:return;if(!planning && !live || busy)return;write(close=false){publish->repository?.createRole(a.id,name){result->publish(when(result){is OperationsResult.Value->OperationsResult.Value(Unit);is OperationsResult.Rejected->result})}}}
    fun renameRole(id:String,name:String){val a=action()?:return;if(!planning || busy)return;write(close=false){publish->repository?.renameRole(a.id,id,name){result->publish(when(result){is OperationsResult.Value->OperationsResult.Value(Unit);is OperationsResult.Rejected->result})}}}
    fun deleteRole(id:String){val a=action()?:return;if(!planning || busy)return;write(close=false){publish->repository?.deleteRole(a.id,id,publish)}}
    private fun write(close:Boolean=true,work:((OperationsResult<Unit>)->Unit)->Unit){
        interaction();if(session().state!=SessionState.READY || repository==null)return
        val token=request;val a=action()?.id;val generation=session().generation;busy=true;error=null
        work{result->
            if(token!=request || action()?.id!=a || session().state!=SessionState.READY || session().generation!=generation)return@work
            busy=false
            when(result){is OperationsResult.Value->{if(close)finishForm();refresh()};is OperationsResult.Rejected->error=result.reason}
        }
    }
}
