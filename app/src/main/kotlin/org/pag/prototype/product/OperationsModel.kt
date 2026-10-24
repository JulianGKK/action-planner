package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

internal enum class OperationsScreen { OVERVIEW, ROLES, PLAN, LIVE, PERSON, FUNCTION, FUNCTION_DETAIL, NEW_FUNCTION }
enum class RoleCreateSurface { ROLES, PREPARATION, INLINE, NEW_FUNCTION }
internal data class FunctionFocus(val roleId: String?)
/** Short-lived UI/navigation state only; the repository enforces persisted status and session. */
class OperationsModel(private val repository:OperationsRepository?,private val session:()->SessionSnapshot,
    private val action:()->Action?,private val interaction:()->Unit,private val leaveSensitive:()->Unit,
    private val suppliedBoard: (() -> OperationalBoard?)? = null,
    private val refreshWorkspace: (() -> Unit)? = null,
    private val feedback: (String) -> Unit = {},
    private val onEditFinished: (String?) -> Unit = {}) {
    private var copyGate:((ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?)?=null
    internal fun bindCopyGate(gate:(ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?){check(copyGate==null);copyGate=gate}
    private fun copyData(units:ActiveWorkspaceBudget.Units,producer:()->Unit):Boolean {
        val failure=if(copyGate==null){producer();null}else copyGate!!.invoke(units,producer)
        if(failure!=null){error=failure;return false};return true
    }
    private var ownBoard by mutableStateOf<OperationalBoard?>(null)
    val board: OperationalBoard? get() = if (suppliedBoard != null) suppliedBoard.invoke() else ownBoard
    internal var screen by mutableStateOf(OperationsScreen.OVERVIEW);private set
    var personId by mutableStateOf<String?>(null);private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<OperationsFailure?>(null);private set
    var roleIds by mutableStateOf<Set<String>>(emptySet());private set
    var primaryRoleId by mutableStateOf<String?>(null);private set
    var liveStatus by mutableStateOf(OperationalStatus.NO_STATUS);private set
    var liveRoleId by mutableStateOf<String?>(null);private set
    var livePlace by mutableStateOf("");private set
    internal var functionFocus by mutableStateOf<FunctionFocus?>(null); private set
    var moreFunctionsExpanded by mutableStateOf(false); private set
    val newFunctionName get()=if(createSurface==RoleCreateSurface.NEW_FUNCTION)createName else ""
    var createSurface by mutableStateOf<RoleCreateSurface?>(null); private set
    var createName by mutableStateOf(""); private set
    var createError by mutableStateOf<OperationsFailure?>(null); private set
    var templateCreateError by mutableStateOf<OperationsFailure?>(null); private set
    private var createSerial=0L
    private var createGeneration=0L
    private var createActionId:String?=null
    private var createJob=0L
    private var pendingCreateJob:Long?=null
    private fun dropCreate(){createSerial++;pendingCreateJob=null;createActionId=null;createGeneration=0L;createSurface=null;createName="";createError=null;templateCreateError=null}
    private fun bindCreate(surface:RoleCreateSurface){dropCreate();createSurface=surface;createGeneration=session().generation;createActionId=action()?.id}
    private fun createAllowed(surface:RoleCreateSurface)=editingRole==null && deletingRole==null && when(surface){
        RoleCreateSurface.ROLES->(planning || live) && screen==OperationsScreen.ROLES
        RoleCreateSurface.PREPARATION->planning && screen==OperationsScreen.OVERVIEW
        RoleCreateSurface.INLINE->live && screen in setOf(OperationsScreen.LIVE,OperationsScreen.FUNCTION) && liveStatus==OperationalStatus.ON_SITE
        RoleCreateSurface.NEW_FUNCTION->live && screen==OperationsScreen.NEW_FUNCTION
    }
    fun beginRoleCreate(surface:RoleCreateSurface){
        val before=session();val a=action()?.id;val serial=createSerial
        interaction()
        if(busy || before.state!=SessionState.READY || session().generation!=before.generation || action()?.id!=a || serial!=createSerial || !createAllowed(surface))return
        bindCreate(surface);error=null
    }
    fun changeRoleCreateName(value:String){
        val serial=createSerial;val generation=session().generation;val a=action()?.id
        interaction()
        val surface=createSurface?:return
        if(!busy && createSerial==serial && createGeneration==generation && session().generation==generation &&
            createActionId==a && action()?.id==a && createAllowed(surface))copyData(ActiveWorkspaceBudget.Units(strings=1,utf16Units=value.length.toLong(),references=1)){createName=value}
    }
    fun cancelRoleCreate(){
        val serial=createSerial;val generation=session().generation;val a=action()?.id
        interaction()
        if(!busy && session().state==SessionState.READY && session().generation==generation && action()?.id==a && createSerial==serial){dropCreate();error=null}
    }
    fun saveRoleCreate(){
        val surface=createSurface?:return
        if(createAllowed(surface) && createActionId==action()?.id && createGeneration==session().generation)
            createRoleWrite(createName,surface)
    }
    private var focusedPersonId: String? = null
    internal val hasPersonFocus get() = focusedPersonId != null
    var planRoles by mutableStateOf<List<Role>>(emptyList()); private set
    private var planExpected:ExpectedPersonRolePlan?=null
    private var planGeneration=0L
    var editingRole by mutableStateOf<Role?>(null); private set
    var deletingRole by mutableStateOf<Role?>(null); private set
    var roleName by mutableStateOf(""); private set
    private var roleEditorSerial=0L
    private var roleEditorGeneration=0L
    private fun dropRoleEditor(){roleEditorSerial++;editingRole=null;deletingRole=null;roleName=""}
    private fun roleEditorCurrent()=planning && session().generation==roleEditorGeneration &&
        (editingRole?:deletingRole)?.actionId==action()?.id
    private var expected:PersonOperationalState?=null
    private var liveGeneration=0L
    private var request=0L
    val planning get()=session().state==SessionState.READY && action()?.let{OperationalRules.mayPlan(it.status)}==true
    val live get()=session().state==SessionState.READY && action()?.let{OperationalRules.mayUpdateSnapshot(it.status)}==true
    val person get()=board?.persons?.singleOrNull{it.person.id==personId}
    fun textInteraction() = interaction()
    fun clear(){dropCreate();dropRoleEditor();functionFocus=null;moreFunctionsExpanded=false;focusedPersonId=null;request++;ownBoard=null;screen=OperationsScreen.OVERVIEW;personId=null;busy=false;error=null;roleIds=emptySet();primaryRoleId=null;planExpected=null;planRoles=emptyList();expected=null;livePlace="";liveRoleId=null;liveStatus=OperationalStatus.NO_STATUS}
    fun refresh(){
        if (refreshWorkspace != null) { request++; refreshWorkspace.invoke(); return }
        val a=action()?.takeIf{session().state==SessionState.READY && OperationalRules.mayRead(it.status)}?:return
        val token=++request;val generation=session().generation
        repository?.board(a.id){result->
            if(token!=request || session().state!=SessionState.READY || session().generation!=generation || action()?.id!=a.id)return@board
            when(result){is OperationsResult.Value->ownBoard=result.value;is OperationsResult.Rejected->error=result.reason}
        }
    }
    fun roles(){
        val before=session();val a=action()?.id;val token=request
        interaction();if((!planning && !live) || busy)return
        leaveSensitive()
        if(session().state!=SessionState.READY || session().generation!=before.generation || action()?.id!=a || token!=request)return
        screen=OperationsScreen.ROLES;bindCreate(RoleCreateSurface.ROLES);error=null
    }
    fun plan(id:String) {
        val before=session();val a=action()?.id?:return;val token=request
        interaction();if(!planning || busy)return
        val b=board?.takeIf{it.actionId==a}?:return
        val p=b.persons.singleOrNull{it.person.id==id && it.person.actionId==a}?:return
        leaveSensitive()
        if(!planning || session().generation!=before.generation || action()?.id!=a || request!=token)return
        val n=p.plan.roleIds.size.toLong()
        copyData(ActiveWorkspaceBudget.Units(objects=2,maps=1,mapCapacity=n,lists=1,listCapacity=n,references=n+8)){
            val frozen=ExpectedPersonRolePlan(a,id,p.plan)
            dropCreate();planExpected=frozen;planRoles=b.roles;planGeneration=before.generation
            personId=id;roleIds=frozen.plan.roleIds;primaryRoleId=frozen.plan.primaryRoleId
            screen=OperationsScreen.PLAN;error=null
        }
    }
    /** Explicit confirmation button only: failed reads never replace expected or draft. */
    fun reloadPlan() {
        val old=planExpected?:return;val generation=planGeneration
        if(!planning || busy || screen!=OperationsScreen.PLAN || error!=OperationsFailure.STALE ||
            action()?.id!=old.actionId || session().generation!=generation)return
        val token=request
        interaction()
        if(!planning || session().generation!=generation || action()?.id!=old.actionId || request!=token)return
        busy=true
        var delivered=false
        repository?.board(old.actionId){result->
            if(delivered)return@board
            delivered=true
            if(token!=request || !planning || session().generation!=generation || action()?.id!=old.actionId ||
                screen!=OperationsScreen.PLAN || personId!=old.personId || planExpected!==old)return@board
            busy=false
            when(result) {
                is OperationsResult.Rejected->error=result.reason
                is OperationsResult.Value->{
                    val b=result.value
                    val p=b.persons.singleOrNull{it.person.id==old.personId && it.person.actionId==old.actionId}
                    if(b.actionId!=old.actionId || !OperationalRules.mayPlan(b.status) || p==null)error=OperationsFailure.NOT_FOUND
                    else {
                        val fresh=ExpectedPersonRolePlan(old.actionId,old.personId,p.plan)
                        planExpected=fresh;planRoles=b.roles;roleIds=fresh.plan.roleIds;primaryRoleId=fresh.plan.primaryRoleId;error=null
                    }
                }
            }
        }
    }
    /** Only an explicit user action may replace the live draft and its original revision. */
    fun reloadLive() {
        val old=expected?:return
        val generation=liveGeneration; val token=request; val route=screen
        if(!live || busy || session().generation!=generation || error!=OperationsFailure.STALE ||
            route !in setOf(OperationsScreen.LIVE,OperationsScreen.FUNCTION) || action()?.id!=old.actionId)return
        interaction()
        if(!live || session().generation!=generation || request!=token || expected!==old)return
        busy=true
        var delivered=false
        repository?.board(old.actionId){result->
            if(delivered)return@board
            delivered=true
            if(request!=token || !live || session().generation!=generation || action()?.id!=old.actionId ||
                screen!=route || expected!==old || personId!=old.personId)return@board
            busy=false
            when(result){
                is OperationsResult.Rejected->error=result.reason
                is OperationsResult.Value->{
                    val b=result.value
                    val fresh=b.persons.singleOrNull{it.person.id==old.personId && it.person.actionId==old.actionId}?.state
                    if(b.actionId!=old.actionId || !OperationalRules.mayUpdateSnapshot(b.status) || fresh==null)
                        error=OperationsFailure.NOT_FOUND
                    else {expected=fresh;liveStatus=fresh.status;liveRoleId=fresh.currentRoleId
                        livePlace=fresh.currentPlaceLabel.orEmpty();error=null}
                }
            }
        }
    }
    fun update(id: String) {
        interaction()
        if (!live || busy) return
        val state = board?.persons?.singleOrNull { it.person.id == id }?.state ?: return
        val before = session(); val token = request; val actionId = action()?.id
        leaveSensitive()
        if (!live || session().generation != before.generation || request != token || action()?.id != actionId) return
        dropCreate();personId = id; expected = state; liveGeneration=before.generation; liveStatus = state.status; liveRoleId = state.currentRoleId
        livePlace = state.currentPlaceLabel.orEmpty(); screen = OperationsScreen.LIVE; error = null
    }
    /** RAM-only focus on the already published complete board; no inventory or read. */
    fun focus(id: String) {
        val before = session(); val actionId = action()?.id; val token = request
        if (!live || busy || board?.persons?.none { it.person.id == id } != false) return
        interaction(); leaveSensitive()
        if (!live || session().generation != before.generation || action()?.id != actionId || request != token) return
        dropCreate();focusedPersonId = id; personId = id; screen = OperationsScreen.PERSON; error = null
    }
    private fun functionNavigation(change: () -> Unit) {
        val before = session(); val actionId = action()?.id; val token = request
        if (!live || busy) return
        fun current() = live && session().generation == before.generation && action()?.id == actionId && request == token
        interaction(); if (!current()) return
        leaveSensitive(); if (current()) change()
    }
    fun focusFunction(roleId: String?) = functionNavigation {
        if (roleId != null && board?.roles?.none { it.id == roleId } != false) return@functionNavigation
        dropCreate()
        functionFocus = FunctionFocus(roleId); focusedPersonId = null; personId = null
        screen = OperationsScreen.FUNCTION_DETAIL; error = null
    }
    fun backToFunctions() = functionNavigation {
        functionFocus = null; focusedPersonId = null; finishForm()
    }
    fun newFunction() = functionNavigation {
        functionFocus = null; focusedPersonId = null; personId = null
        screen = OperationsScreen.NEW_FUNCTION;bindCreate(RoleCreateSurface.NEW_FUNCTION);error = null
    }
    fun changeNewFunctionName(value:String){if(createSurface==RoleCreateSurface.NEW_FUNCTION)changeRoleCreateName(value)}
    fun toggleMoreFunctions() = functionNavigation { moreFunctionsExpanded = !moreFunctionsExpanded }
    fun saveNewFunction(){if(createSurface==RoleCreateSurface.NEW_FUNCTION)saveRoleCreate()}
    fun backToList() {
        if (!live || busy) return
        interaction(); if (!live) return
        focusedPersonId = null; finishForm()
    }
    fun changeFunction() {
        val id = personId ?: return
        if (person?.state?.status != OperationalStatus.ON_SITE) return
        update(id)
        if (live && screen == OperationsScreen.LIVE && liveStatus == OperationalStatus.ON_SITE) screen = OperationsScreen.FUNCTION
    }
    fun cancel(){
        val before=session();val actionId=action()?.id;val token=request;val id=personId
        interaction()
        if(busy || before.state!=SessionState.READY || session().state!=SessionState.READY ||
            session().generation!=before.generation || action()?.id!=actionId || request!=token)return
        val reload=error==OperationsFailure.STALE || board==null
        finishForm();if(reload)refresh()
        if(session().state==SessionState.READY && session().generation==before.generation && action()?.id==actionId)
            onEditFinished(id)
    }
    /** Discard the bound local edit for direct person navigation; no board read. */
    internal fun dismissEditForPersonDetail():String? {
        val before=session();val actionId=action()?.id?:return null;val token=request;val id=personId?:return null
        if(before.state!=SessionState.READY || busy || screen !in setOf(OperationsScreen.LIVE, OperationsScreen.FUNCTION))return null
        interaction()
        if(session().state!=SessionState.READY || session().generation!=before.generation ||
            action()?.id!=actionId || personId!=id || request!=token || busy)return null
        finishForm()
        return id
    }
    private fun finishForm(){dropCreate();dropRoleEditor();request++;screen=when {focusedPersonId!=null->OperationsScreen.PERSON;functionFocus!=null->OperationsScreen.FUNCTION_DETAIL;else->OperationsScreen.OVERVIEW};personId=focusedPersonId;roleIds=emptySet();primaryRoleId=null;planExpected=null;planRoles=emptyList();expected=null;livePlace="";liveRoleId=null;liveStatus=OperationalStatus.NO_STATUS;error=null}
    fun toggleRole(id:String){interaction();if(!planning || busy)return;val n=roleIds.size.toLong()+1;copyData(ActiveWorkspaceBudget.Units(objects=1,maps=1,mapCapacity=n,strings=1,utf16Units=id.length.toLong(),references=n+4)){roleIds=if(id in roleIds)roleIds-id else roleIds+id;if(primaryRoleId !in roleIds)primaryRoleId=null}}
    fun primary(id:String?){interaction();if(planning && !busy && (id==null || id in roleIds))primaryRoleId=id}
    fun status(status:OperationalStatus){interaction();if(!live || busy)return;if(status!=liveStatus){if(createSurface==RoleCreateSurface.INLINE)dropCreate();livePlace="";if(!OperationalRules.roleAllowed(status) || status==OperationalStatus.RELEASED)liveRoleId=null};liveStatus=status}
    fun function(id:String?){interaction();if(live && !busy && liveStatus==OperationalStatus.ON_SITE)liveRoleId=id}
    fun place(value:String){interaction();if(live && !busy)copyData(ActiveWorkspaceBudget.Units(strings=1,utf16Units=value.length.toLong(),references=1)){livePlace=value}}
    fun savePlan(){
        val a=action()?:return;val p=personId?:return;val old=planExpected?:return
        if(!planning || busy || screen!=OperationsScreen.PLAN || old.actionId!=a.id || old.personId!=p || session().generation!=planGeneration)return
        val draft=PersonRolePlan(roleIds.toSet(),primaryRoleId, editRevision = old.editRevision)
        write{publish->repository?.planRoles(a.id,p,old,draft,publish)}
    }
    fun saveLive(){val a=action()?:return;val p=personId?:return;val old=expected?:return;if(!live || busy || session().generation!=liveGeneration || a.id!=old.actionId)return;if(!OperationalRules.validPlace(livePlace)){error=OperationsFailure.INVALID;return};write{publish->repository?.updateSnapshot(a.id,p,old,liveStatus,liveRoleId,livePlace,publish)}}
    /** Template/no-text entry: never owns or resets another open form. */
    fun createRole(name:String)=createRoleWrite(name,null)
    private fun createRoleWrite(name:String,owner:RoleCreateSurface?){
        val a=action()?.id?:return;val generation=session().generation
        val serial=createSerial;val roleSerial=roleEditorSerial;val targetScreen=screen;val targetPerson=personId
        if(session().state!=SessionState.READY || (!planning && !live) || busy || repository==null)return
        fun current()=session().state==SessionState.READY && session().generation==generation && action()?.id==a &&
            createSerial==serial && roleEditorSerial==roleSerial && screen==targetScreen && personId==targetPerson &&
            (planning || live) && (owner==null || (createSurface==owner && createAllowed(owner)))
        interaction();if(!current())return
        val submitted=name // Immutable submitted input, separate from the model's draft.
        val job=++createJob;pendingCreateJob=job
        busy=true
        if(owner!=null){createError=null;error=null}else templateCreateError=null
        var delivered=false
        repository.createRole(a,submitted){result->
            if(delivered)return@createRole
            delivered=true
            if(!current() || pendingCreateJob!=job)return@createRole
            pendingCreateJob=null;busy=false
            when(result){
                is OperationsResult.Rejected->{
                    if(owner!=null){createError=result.reason;error=result.reason}else templateCreateError=result.reason
                }
                is OperationsResult.Value->{
                    if(result.value.actionId!=a){if(owner!=null){createError=OperationsFailure.NOT_ALLOWED;error=OperationsFailure.NOT_ALLOWED}else templateCreateError=OperationsFailure.NOT_ALLOWED;return@createRole}
                    if(owner==RoleCreateSurface.NEW_FUNCTION)finishForm()
                    else if(owner==RoleCreateSurface.ROLES)bindCreate(RoleCreateSurface.ROLES)
                    else if(owner!=null)dropCreate()
                    val completedSerial=createSerial;val completedRoleSerial=roleEditorSerial;val completedScreen=screen
                    fun completedCurrent()=session().state==SessionState.READY && session().generation==generation && action()?.id==a &&
                        createSerial==completedSerial && roleEditorSerial==completedRoleSerial && screen==completedScreen
                    refresh()
                    if(completedCurrent() && owner==RoleCreateSurface.NEW_FUNCTION)onEditFinished(targetPerson)
                    if(completedCurrent())feedback("${if(owner==RoleCreateSurface.NEW_FUNCTION) "Funktion" else "Rolle"} „${submitted.trim()}“ angelegt")
                }
            }
        }
    }
    fun editRole(role:Role,delete:Boolean=false) {
        val before=session();val a=action()?.id;val token=request
        interaction()
        if(!planning || busy || role.actionId!=a || session().generation!=before.generation || request!=token || action()?.id!=a)return
        dropRoleEditor();roleEditorGeneration=before.generation
        if(delete)deletingRole=role else {editingRole=role;roleName=role.name}
        error=null
    }
    fun changeRoleName(value:String){interaction();if(!busy && roleEditorCurrent() && editingRole!=null)copyData(ActiveWorkspaceBudget.Units(strings=1,utf16Units=value.length.toLong(),references=1)){roleName=value}}
    fun cancelRoleEditor(){interaction();if(!busy){dropRoleEditor();error=null}}
    fun saveRoleRename(){val old=editingRole?:return;if(roleEditorCurrent())renameRole(old,roleName)}
    fun confirmRoleDelete(){val old=deletingRole?:return;if(roleEditorCurrent())deleteRole(old)}
    fun renameRole(expected:Role,name:String){
        val a=action()?:return;if(!planning || busy || expected.actionId!=a.id)return
        val serial=roleEditorSerial;var delivered=false
        write(close=false,applied={if(serial==roleEditorSerial)dropRoleEditor()}){publish->
            repository?.renameRole(a.id,expected,name){result->
                if(!delivered){delivered=true;if(serial==roleEditorSerial)publish(when(result){is OperationsResult.Value->OperationsResult.Value(Unit);is OperationsResult.Rejected->result})}
            }
        }
    }
    fun deleteRole(expected:Role){
        val a=action()?:return;if(!planning || busy || expected.actionId!=a.id)return
        val serial=roleEditorSerial;var delivered=false
        write(close=false,applied={if(serial==roleEditorSerial)dropRoleEditor()}){publish->
            repository?.deleteRole(a.id,expected){if(!delivered){delivered=true;if(serial==roleEditorSerial)publish(it)}}
        }
    }
    /** Explicit draft-discard confirmation; never invoked by a failed write. */
    fun reloadRoleEditor(){
        val old=editingRole?:deletingRole?:return
        if(!roleEditorCurrent() || busy || error!=OperationsFailure.STALE)return
        val serial=roleEditorSerial;val generation=roleEditorGeneration;val token=request;val deleting=deletingRole!=null
        interaction()
        if(!roleEditorCurrent() || serial!=roleEditorSerial || token!=request)return
        busy=true;var delivered=false
        repository?.board(old.actionId){result->
            if(delivered)return@board
            delivered=true
            if(!roleEditorCurrent() || session().generation!=generation || serial!=roleEditorSerial || token!=request)return@board
            busy=false
            when(result){
                is OperationsResult.Rejected->error=result.reason
                is OperationsResult.Value->{
                    val b=result.value;val fresh=b.roles.singleOrNull{it.id==old.id && it.actionId==old.actionId}
                    if(b.actionId!=old.actionId || !OperationalRules.mayPlan(b.status) || fresh==null)error=OperationsFailure.NOT_FOUND
                    else {dropRoleEditor();if(deleting)deletingRole=fresh else {editingRole=fresh;roleName=fresh.name};error=null}
                }
            }
        }
    }
    private fun write(close:Boolean=true,message:String?=null,applied:()->Unit={},work:((OperationsResult<Unit>)->Unit)->Unit){
        val token=request;val a=action()?.id;val before=session();val generation=before.generation
        if(before.state!=SessionState.READY || repository==null || a==null)return
        interaction()
        if(token!=request || action()?.id!=a || session().state!=SessionState.READY || session().generation!=generation)return
        busy=true;error=null
        work{result->
            if(token!=request || action()?.id!=a || session().state!=SessionState.READY || session().generation!=generation)return@work
            busy=false
            when(result){is OperationsResult.Value->{
                val finishedPerson=personId
                applied()
                if(close)finishForm();refresh()
                if(close && session().state==SessionState.READY && session().generation==generation && action()?.id==a)
                    onEditFinished(finishedPerson)
                if(session().state==SessionState.READY && session().generation==generation && action()?.id==a && message!=null)feedback(message)
            };is OperationsResult.Rejected->error=result.reason}
        }
    }
}
