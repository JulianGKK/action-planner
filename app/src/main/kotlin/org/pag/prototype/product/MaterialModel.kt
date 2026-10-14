package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

/** Small session/action/phase-bound RAM workspace. Repository status remains authoritative. */
class MaterialModel(private val repository: MaterialRepository?, private val session: () -> SessionSnapshot,
    private val action: () -> Action?, private val interaction: () -> Unit, private val leaveSensitive: () -> Unit,
    private val refreshWorkspace: () -> Unit, private val feedback: (String) -> Unit) {
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
        epoch++; readRequest++; writeRequest++; opened = false
        boundAction = null; boundStatus = null; boundGeneration = null
        board = null; loading = false; discard(); busy = false; error = null
    }
    private fun discard() { selected = null; input = null; expected = null; confirmingDelete = false }
    internal fun enter() {
        clear()
        val s = session(); val a = action() ?: return
        if (repository == null || s.state != SessionState.READY || !MaterialRules.mayRead(a.status)) return
        boundAction = a.id; boundStatus = a.status; boundGeneration = s.generation; opened = true
        refresh()
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
        if (!writable) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; discard(); input = template?.input() ?: MaterialInput(""); error = null }
    }
    fun edit(item: MaterialItem) = act { current ->
        if (!writable || board?.items?.contains(item) != true) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; selected = item; expected = item; input = item.input(); confirmingDelete = false; error = null }
    }
    fun change(value: MaterialInput) = act {
        val previous = input ?: return@act
        if (writable) { input = value.copy(sourceTemplateKey = previous.sourceTemplateKey); if (error == OperationsFailure.INVALID) error = null }
    }
    fun cancel() = act { val reload = error == OperationsFailure.STALE || board == null; discard(); error = null; if (reload) refresh() }
    fun requestDelete() = act { if (writable && expected != null && error != OperationsFailure.STALE) confirmingDelete = true }
    fun cancelDelete() = act { confirmingDelete = false }
    fun save() = act { current ->
        val draft = input ?: return@act
        if (!writable || confirmingDelete || error == OperationsFailure.STALE) return@act
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
    private fun unit(result: OperationsResult<MaterialItem>): OperationsResult<Unit> = when (result) {
        is OperationsResult.Value -> OperationsResult.Value(Unit)
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
                    discard(); refresh()
                    if (current()) refreshWorkspace()
                    if (current()) feedback(message)
                }
            }
        }
    }
}
