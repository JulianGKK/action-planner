package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

/** Small session/action/phase-bound RAM workspace. Repository status remains authoritative. */
class ExpenseModel(private val repository: ExpenseRepository?, private val session: () -> SessionSnapshot,
    private val action: () -> Action?, private val interaction: () -> Unit, private val leaveSensitive: () -> Unit,
    private val refreshWorkspace: () -> Unit, private val feedback: (String) -> Unit) {
    var board by mutableStateOf<ExpenseBoard?>(null); private set
    var loading by mutableStateOf(false); private set
    var selected by mutableStateOf<Expense?>(null); private set
    var draft by mutableStateOf<ExpenseDraft?>(null); private set
    var expected: Expense? = null; private set
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
    val form get() = draft != null
    val writable get() = available && action()?.let { ExpenseRules.mayWrite(it.status) } == true
    val completedView get() = available && boundStatus == ActionStatus.COMPLETED
    fun clear() {
        epoch++; readRequest++; writeRequest++; opened = false
        boundAction = null; boundStatus = null; boundGeneration = null
        board = null; loading = false; discard(); busy = false; error = null
    }
    private fun discard() { selected = null; draft = null; expected = null; confirmingDelete = false }
    internal fun enter() {
        clear()
        val s = session(); val a = action() ?: return
        if (repository == null || s.state != SessionState.READY || !ExpenseRules.mayRead(a.status)) return
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
                is OperationsResult.Value -> if (result.value.actionId == id && result.value.expenses.all { it.actionId == id }) {
                    board = result.value; error = null
                } else error = OperationsFailure.NOT_FOUND
                is OperationsResult.Rejected -> error = result.reason
            }
        }
    }
    fun reload() = act { discard(); error = null; refresh() }
    fun create() = act { current ->
        if (!writable) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; discard(); draft = ExpenseDraft(); error = null }
    }
    fun edit(item: Expense) = act { current ->
        if (!writable || board?.expenses?.contains(item) != true) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; selected = item; expected = item; draft = ExpenseDraft.from(item); confirmingDelete = false; error = null }
    }
    fun change(value: ExpenseDraft) = act {
        if (draft == null) return@act
        if (writable) { draft = value; if (error == OperationsFailure.INVALID) error = null }
    }
    fun cancel() = act { val reload = error == OperationsFailure.STALE || board == null; discard(); error = null; if (reload) refresh() }
    fun requestDelete() = act { if (writable && expected != null && error != OperationsFailure.STALE) confirmingDelete = true }
    fun cancelDelete() = act { confirmingDelete = false }
    fun save() = act { current ->
        if (!writable || confirmingDelete || error == OperationsFailure.STALE) return@act
        val input = draft?.input()
        if (input == null) { error = OperationsFailure.INVALID; return@act }
        if (!ExpenseRules.valid(input)) { error = OperationsFailure.INVALID; return@act }
        val old = expected
        write(current, if (old == null) "Kosten eingetragen" else "Kosten gespeichert") { done ->
            if (old == null) repository?.create(boundAction!!, input) { done(unit(it)) }
            else repository?.edit(boundAction!!, old, input) { done(unit(it)) }
        }
    }
    fun delete() = act { current ->
        val old = expected ?: return@act
        if (!confirmingDelete || !writable || !ExpenseRules.mayDelete(action()!!.status) || error == OperationsFailure.STALE) return@act
        write(current, "Kostenposition entfernt") { repository?.delete(boundAction!!, old, it) }
    }
    private fun unit(result: OperationsResult<Expense>): OperationsResult<Unit> = when (result) {
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

data class ExpensePaymentDraft(val payerLabel: String = "", val amount: String = "")
data class ExpenseDraft(val title: String = "", val total: String = "", val payments: List<ExpensePaymentDraft> = emptyList()) {
    fun input(): ExpenseInput? {
        val cents = EuroMoney.parse(total) ?: return null
        val rows = payments.map { ExpensePaymentInput(it.payerLabel, EuroMoney.parse(it.amount) ?: return null) }
        return ExpenseInput(title, cents, rows).takeIf(ExpenseRules::valid)
    }
    val canAdd get() = payments.size < 32
    fun add(label: String = "") = if (canAdd) copy(payments = payments + ExpensePaymentDraft(label)) else this
    fun remove(index: Int) = copy(payments = payments.filterIndexed { i, _ -> i != index })
    fun feedback(): String {
        val totalCents = EuroMoney.parse(total) ?: return "Bitte einen positiven Gesamtbetrag in Euro eingeben."
        if (payments.isEmpty()) return "Mindestens eine Zahlung erforderlich."
        val values = payments.map { EuroMoney.parse(it.amount) ?: return "Bitte alle Zahlungsbeträge in Euro eingeben." }
        val sum = ExpenseRules.sum(values) ?: return "Die Zahlungssumme ist zu groß."
        return if (sum > totalCents) "Zahlungen überschreiten den Gesamtbetrag um ${EuroMoney.format(sum - totalCents)}"
        else "Zahlungen: ${EuroMoney.format(sum)} von ${EuroMoney.format(totalCents)}"
    }
    companion object {
        fun from(e: Expense) = ExpenseDraft(e.title, EuroMoney.input(e.totalCents), e.payments.map { ExpensePaymentDraft(it.payerLabel, EuroMoney.input(it.amountCents)) })
    }
}
