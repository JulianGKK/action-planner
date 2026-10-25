package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

/** Small session/action/phase-bound RAM workspace. Repository status remains authoritative. */
class MaterialModel(private val repository: MaterialRepository?, private val session: () -> SessionSnapshot,
    private val action: () -> Action?, private val interaction: () -> Unit, private val leaveSensitive: () -> Unit,
    private val refreshWorkspace: () -> Unit, private val feedback: (String) -> Unit) {
    private var membershipLoader:WorkspaceDetailLoader?=null
    internal fun bindMembershipLoader(loader:WorkspaceDetailLoader){check(membershipLoader==null);membershipLoader=loader}
    private var detailLoader:WorkspaceDetailLoader?=null
    internal fun bindDetailLoader(loader:WorkspaceDetailLoader){check(detailLoader==null);detailLoader=loader}
    var commitConfirmed by mutableStateOf(false);private set
    private var committedId:String?=null
    private var copyGate:((ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?)?=null
    internal fun bindCopyGate(gate:(ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?){check(copyGate==null);copyGate=gate}
    private fun copyData(units:ActiveWorkspaceBudget.Units,producer:()->Unit):Boolean {
        val failure=if(copyGate==null){producer();null}else copyGate!!.invoke(units,producer)
        if(failure!=null){error=failure;return false};return true
    }
    var board by mutableStateOf<MaterialBoard?>(null); private set
    var loading by mutableStateOf(false); private set
    var selected by mutableStateOf<MaterialItem?>(null); private set
    var input by mutableStateOf<MaterialInput?>(null); private set
    var expected: MaterialItem? = null; private set
    var confirmingDelete by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<OperationsFailure?>(null); private set
    private var opened by mutableStateOf(false)
    private var boundAction: String? = null
    private var boundStatus: ActionStatus? = null
    private var boundGeneration: Long? = null
    private var epoch = 0L
    private var readRequest = 0L
    private var writeRequest = 0L
    val available get() = opened && repository != null && session().state == SessionState.READY &&
        session().generation == boundGeneration && action()?.id == boundAction && action()?.status == boundStatus
    val form get() = input != null
    val writable get() = available && action()?.let { MaterialRules.mayWrite(it.status) } == true
    val completedView get() = available && boundStatus == ActionStatus.COMPLETED
    fun clear() {
        commitConfirmed=false;committedId=null
        epoch++; readRequest++; writeRequest++; opened = false
        boundAction = null; boundStatus = null; boundGeneration = null
        board = null; loading = false; discard(); busy = false; error = null
    }
    private fun discard() { selected = null; input = null; expected = null; confirmingDelete = false }
    internal fun enter(deferRead:Boolean=false) {
        clear()
        val s = session(); val a = action() ?: return
        if (repository == null || s.state != SessionState.READY || !MaterialRules.mayRead(a.status)) return
        boundAction = a.id; boundStatus = a.status; boundGeneration = s.generation; opened = true
        if(deferRead)loading=true else refresh()
    }
    internal fun acceptWorkspaceBoard(actionId:String,generation:Long,value:MaterialBoard):Boolean {
        if(!available || boundAction!=actionId || boundGeneration!=generation || !loading || board!=null ||
            form || selected!=null || value.actionId!=actionId || value.items.any{it.actionId!=actionId})return false
        readRequest++;board=value;loading=false;error=null
        return true
    }
    fun openCompleted(prepare: () -> Unit) {
        val s = session(); val a = action() ?: return; val token = epoch
        if (repository == null || busy || s.state != SessionState.READY || a.status != ActionStatus.COMPLETED) return
        fun current() = session().state == SessionState.READY && session().generation == s.generation &&
            action()?.id == a.id && action()?.status == a.status
        interaction()
        if (!current() || token != epoch) return
        prepare()
        if (current()) enter()
    }
    private fun binding(): (() -> Boolean)? {
        if (!available) return null
        val token = epoch
        return { token == epoch && available }
    }
    private fun act(work: (() -> Boolean) -> Unit) {
        if (busy) return
        val current = binding() ?: return
        interaction()
        if (current()) work(current)
    }
    fun refresh() {
        val current = binding() ?: return; val request = ++readRequest; val id = boundAction!!
        loading = true
        repository?.board(id) { result ->
            if (!current() || request != readRequest) return@board
            loading = false
            when (result) {
                is OperationsResult.Value -> if (result.value.actionId == id && result.value.items.all { it.actionId == id }) {
                    board = result.value; error = null
                } else error = OperationsFailure.NOT_FOUND
                is OperationsResult.Rejected -> error = result.reason
            }
        }
    }
    fun reload() = act { discard(); error = null; refresh() }
    fun create(template: MaterialTemplate? = null) = act { current ->
        if (!writable || commitConfirmed) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; discard(); copyData(ActiveWorkspaceBudget.Units(objects=1,references=10)){input = template?.input() ?: MaterialInput(""); error = null} }
    }
    fun edit(item: MaterialItem) = act { current ->
        if (commitConfirmed || !writable || board?.items?.contains(item) != true) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; selected = item; expected = item; copyData(ActiveWorkspaceBudget.Units(objects=1,references=10)){input = item.input(); confirmingDelete = false; error = null} }
    }
    internal fun change(value: MaterialInput) = act {
        val previous = input ?: return@act
        if (writable) { input = value.copy(sourceTemplateKey = previous.sourceTemplateKey); if (error == OperationsFailure.INVALID) error = null }
    }
    private fun alter(text:String?=null,produce:(MaterialInput)->MaterialInput)=act {
        val old=input?:return@act
        if(writable)copyData(ActiveWorkspaceBudget.Units(objects=1,references=10,
            strings=if(text==null)0 else 1,utf16Units=text?.length?.toLong()?:0)){input=produce(old)}
    }
    fun name(value:String)=alter(value){it.copy(name=value)}
    fun quantity(value:String)=alter(value){it.copy(quantityLabel=value)}
    fun place(value:String)=alter(value){it.copy(placeLabel=value)}
    fun responsible(id:String?)=alter{it.copy(responsiblePersonId=id)}
    fun linkedTask(id:String?)=alter{it.copy(linkedTaskId=id)}
    fun cancel() = act { val reload = error == OperationsFailure.STALE || board == null; discard(); error = null; if (reload) refresh() }
    fun requestDelete() = act { if (writable && expected != null && error != OperationsFailure.STALE) confirmingDelete = true }
    fun cancelDelete() = act { confirmingDelete = false }
    fun save() = act { current ->
        val draft = input ?: return@act
        if (commitConfirmed || !writable || confirmingDelete || error == OperationsFailure.STALE) return@act
        if (!MaterialRules.valid(draft)) { error = OperationsFailure.INVALID; return@act }
        val old = expected
        write(current, if (old == null) "Material angelegt" else "Material gespeichert") { done ->
            if (old == null) repository?.create(boundAction!!, draft) { done(unit(it)) }
            else repository?.edit(boundAction!!, old, draft) { done(unit(it)) }
        }
    }
    fun delete() = act { current ->
        val old = expected ?: return@act
        if (!confirmingDelete || !writable || !MaterialRules.mayDelete(action()!!.status) || error == OperationsFailure.STALE) return@act
        write(current, "Material entfernt") { repository?.delete(boundAction!!, old, it) }
    }
    private fun draftClearAfterCommit() {input = null;expected=null;confirmingDelete=false}
    private fun unit(result: OperationsResult<MaterialItem>): OperationsResult<Unit> = when (result) {
        is OperationsResult.Value -> {committedId=result.value.id;OperationsResult.Value(Unit)}
        is OperationsResult.Rejected -> result
    }
    private fun write(current: () -> Boolean, message: String, work: ((OperationsResult<Unit>) -> Unit) -> Unit) {
        val request = ++writeRequest; busy = true; error = null
        work { result ->
            if (!current() || request != writeRequest) return@work
            writeRequest++; busy = false
            when (result) {
                is OperationsResult.Rejected -> error = result.reason
                is OperationsResult.Value -> {
                    val loader=if(confirmingDelete || expected==null)membershipLoader else detailLoader
                    val id=committedId ?: expected?.id
                    if(loader!=null && id!=null){
                        commitConfirmed=true;busy=true
                        loader(WorkspaceDetailKind.MATERIAL,id){refreshed->
                            if(!current())return@loader
                            busy=false
                            when(refreshed){
                                is WorkspaceDetailResult.Rejected->{error=refreshed.reason.operationFailure();feedback("Gespeichert – Ansicht konnte noch nicht aktualisiert werden.")}
                                is WorkspaceDetailResult.Value->{
                                    board=refreshed.value.workspace?.materialBoard ?: board
                                    selected=refreshed.value.material;draftClearAfterCommit()
                                    commitConfirmed=false;error=null;feedback(message)
                                }
                            }
                        }
                        return@work
                    }
                    discard(); refresh()
                    if (current()) refreshWorkspace()
                    if (current()) feedback(message)
                }
            }
        }
    }
}
