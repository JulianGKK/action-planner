package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

/** Small session/action/phase-bound RAM workspace. Repository status remains authoritative. */
class ExpenseModel(private val repository: ExpenseRepository?, private val session: () -> SessionSnapshot,
    private val action: () -> Action?, private val interaction: () -> Unit, private val leaveSensitive: () -> Unit,
    private val refreshWorkspace: () -> Unit, private val feedback: (String) -> Unit) {
    internal val uiEditorEpoch:Long get()=readRequest
    private var personLookup:((String)->String?)?=null
    internal fun bindPersonLookup(lookup:(String)->String?){check(personLookup==null);personLookup=lookup}
    fun addPaymentPerson(id:String){val label=personLookup?.invoke(id)?:return;addPayment(label)}
    private var readGate:(()->Boolean)?=null
    internal fun bindReadGate(gate:()->Boolean){check(readGate==null);readGate=gate}
    private var feedbackFactory:WorkspaceFeedbackFactory?=null
    internal fun bindFeedbackFactory(factory:WorkspaceFeedbackFactory){check(feedbackFactory==null);feedbackFactory=factory}
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
    var draftFeedback by mutableStateOf("");private set
    var canSave by mutableStateOf(false);private set
    private fun derive(){val d=draft;draftFeedback=d?.feedback().orEmpty();canSave=d?.input()!=null}
    private fun deriveCost(d:ExpenseDraft):ActiveWorkspaceBudget.Units {
        val n=d.payments.size.toLong()
        var text=d.title.length.toLong()+d.total.length
        for(p in d.payments)text+=p.payerLabel.length+p.amount.length
        return ActiveWorkspaceBudget.Units(objects=64+n*8,strings=32+n*4,utf16Units=text*4+2048,
            lists=8,listCapacity=n*8,references=128+n*16)
    }
    fun totalCents():Long? {var sum=0L;for(e in board?.expenses.orEmpty()){sum=try{Math.addExact(sum,e.totalCents)}catch(_:ArithmeticException){return null}};return sum}
    private var presentation:ExpensePresentation?=null
    internal fun ui():ExpensePresentation? {
        val value=board?:return null
        if(!available || copyGate?.invoke(FollowPresentationCost.check){}!=null)return null
        presentation?.let{if(it.source===value)return it}
        if(!copyData(FollowPresentationCost.planning){})return null
        var n=value.expenses.size.toLong();var chars=0L
        for(v in value.expenses){chars=Math.addExact(chars,v.title.length.toLong());n=Math.addExact(n,v.payments.size.toLong());for(p in v.payments)chars=Math.addExact(chars,p.payerLabel.length.toLong())}
        val cost=try{FollowPresentationCost.units(n,chars)}catch(_:ArithmeticException){error=OperationsFailure.BUDGET_REFUSED;return null}
        var fresh:ExpensePresentation?=null
        if(!copyData(cost){fresh=ExpensePresentation(value)})return null
        presentation=fresh;return fresh
    }
    fun moneyLabel(cents:Long,prefix:String=""):String {
        val display=ui()
        if(display!=null){
            if(prefix=="Gesamt: " && display.total==cents)return display.summary
            if(prefix.isEmpty())for(row in display.rows)if(row.source.totalCents==cents)return row.money
        }
        var value="Betrag derzeit nicht verfügbar."
        copyData(ActiveWorkspaceBudget.Units(objects=32,strings=16,utf16Units=512+prefix.length.toLong()*4,references=32)){value=prefix+EuroMoney.format(cents)}
        return value
    }
    fun paymentLabel(payment:ExpensePayment):String {
        val display=ui()
        if(display!=null)for(row in display.rows){var i=0;while(i<row.source.payments.size){if(row.source.payments[i]===payment)return row.payments[i];i++}}
        var value="Zahlung derzeit nicht verfügbar."
        copyData(ActiveWorkspaceBudget.Units(objects=32,strings=16,utf16Units=512+payment.payerLabel.length.toLong()*4,references=32)){value="${payment.payerLabel} · ${EuroMoney.format(payment.amountCents)}"}
        return value
    }
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
        commitConfirmed=false;committedId=null
        epoch++; readRequest++; writeRequest++; opened = false
        boundAction = null; boundStatus = null; boundGeneration = null
        board = null;presentation=null; loading = false; discard(); busy = false; error = null
    }
    private fun discard() { selected = null; draft = null; expected = null; confirmingDelete = false }
    internal fun enter(deferRead:Boolean=false) {
        clear()
        val s = session(); val a = action() ?: return
        if (repository == null || s.state != SessionState.READY || !ExpenseRules.mayRead(a.status)) return
        boundAction = a.id; boundStatus = a.status; boundGeneration = s.generation; opened = true
        if(deferRead)loading=true else refresh()
    }
    internal fun acceptWorkspaceBoard(actionId:String,generation:Long,value:ExpenseBoard):Boolean {
        if(!available || boundAction!=actionId || boundGeneration!=generation || !loading || board!=null ||
            form || selected!=null || value.actionId!=actionId || value.expenses.any{it.actionId!=actionId})return false
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
        detailLoader?.let{loader->
            loader(WorkspaceDetailKind.EXPENSE_BOARD,id){result->
                if(!current() || request!=readRequest)return@loader
                readRequest++;loading=false
                when(result){
                    is WorkspaceDetailResult.Rejected->error=result.reason.operationFailure()
                    is WorkspaceDetailResult.Value->{val fresh=result.value.workspace?.expenseBoard;if(fresh==null)error=OperationsFailure.NOT_FOUND else {board=fresh;error=null}}
                }
            };return
        }
        repository?.board(id) { result ->
            if (!current() || request != readRequest) return@board
            readRequest++;loading = false
            when (result) {
                is OperationsResult.Value -> if (result.value.actionId == id && result.value.expenses.all { it.actionId == id }) {
                    board = result.value; error = null
                } else error = OperationsFailure.NOT_FOUND
                is OperationsResult.Rejected -> error = result.reason
            }
        }
    }
    fun reload() = act { current ->
        val id=committedId?:expected?.id
        val loader=if(commitConfirmed)membershipLoader else detailLoader
        if(loader!=null && id!=null){
            val request=++readRequest;busy=true
            var delivered=false
            loader(WorkspaceDetailKind.EXPENSE,id){result->
                if(delivered)return@loader
                delivered=true
                if(!current() || request!=readRequest)return@loader
                busy=false
                when(result){
                    is WorkspaceDetailResult.Rejected->error=result.reason.operationFailure()
                    is WorkspaceDetailResult.Value->{
                        if(commitConfirmed){
                            val freshBoard=result.value.workspace?.expenseBoard
                            if(freshBoard==null){error=OperationsFailure.NOT_FOUND;return@loader}
                            board=freshBoard;discard();commitConfirmed=false;committedId=null;error=null;return@loader
                        }
                        val fresh=result.value.expense
                        if(fresh==null || fresh.id!=id || fresh.actionId!=boundAction){error=OperationsFailure.NOT_FOUND;return@loader}
                        val n=fresh.payments.size.toLong();var chars=fresh.title.length.toLong()+64
                        for(p in fresh.payments)chars=Math.addExact(chars,p.payerLabel.length.toLong()+64)
                        val units=ActiveWorkspaceBudget.Units(objects=128+n*16,strings=64+n*8,utf16Units=4096+chars*8,lists=16,listCapacity=n*16,references=256+n*32)
                        if(!copyData(units){expected=fresh;selected=fresh;draft=ExpenseDraft.from(fresh);derive()})return@loader
                        commitConfirmed=false;committedId=null;error=null
                    }
                }
            }
        }else{discard();error=null;refresh()}
    }
    fun create() = act { current ->
        if (!writable || commitConfirmed) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; discard(); copyData(ActiveWorkspaceBudget.Units(objects=128,references=256,strings=64,utf16Units=4096,lists=16,listCapacity=256)){draft = ExpenseDraft();derive(); error = null} }
    }
    fun editId(id:String){if(readGate?.invoke()==false)return;board?.expenses?.findOriginal{it.id==id}?.let(::edit)}
    fun edit(item: Expense) = act { current ->
        if (commitConfirmed || !writable || board?.expenses?.contains(item) != true) return@act
        leaveSensitive()
        if (current()) { readRequest++; loading = false; selected = item; expected = item; copyData(ActiveWorkspaceBudget.Units(objects=128+item.payments.size.toLong()*16,strings=64+item.payments.size.toLong()*8,utf16Units=4096+item.title.length.toLong()*4+item.payments.sumOf{it.payerLabel.length.toLong()*4+256},lists=16,listCapacity=item.payments.size.toLong()*16,references=256+item.payments.size.toLong()*32)){draft = ExpenseDraft.from(item);derive(); confirmingDelete = false; error = null} }
    }
    internal fun change(value: ExpenseDraft) = act {
        if (draft == null) return@act
        if (writable) { copyData(deriveCost(value)){draft = value;derive()}; if (error == OperationsFailure.INVALID) error = null }
    }
    private fun alter(text:String?=null,produce:(ExpenseDraft)->ExpenseDraft)=act {
        val old=draft?:return@act
        if(writable && !commitConfirmed){val n=old.payments.size.toLong()+1
            val base=deriveCost(old)
            copyData(base.copy(objects=base.objects+n+1,lists=base.lists+1,listCapacity=base.listCapacity+n,
                strings=base.strings+1,utf16Units=base.utf16Units+(text?.length?.toLong()?:0)*4)){draft=produce(old);derive()}}
    }
    fun title(value:String)=alter(value){it.copy(title=value)}
    fun total(value:String)=alter(value){it.copy(total=value)}
    fun payer(index:Int,value:String)=alter(value){d->d.copy(payments=d.payments.mapIndexed{n,p->if(n==index)p.copy(payerLabel=value) else p})}
    fun amount(index:Int,value:String)=alter(value){d->d.copy(payments=d.payments.mapIndexed{n,p->if(n==index)p.copy(amount=value) else p})}
    fun addPayment(label:String="")=alter(label){it.add(label).also{readRequest++}}
    fun removePayment(index:Int)=alter{it.remove(index).also{readRequest++}}
    fun cancel() = act { val reload = error == OperationsFailure.STALE || board == null; discard(); error = null; if (reload) refresh() }
    fun requestDelete() = act { if (writable && expected != null && error != OperationsFailure.STALE) confirmingDelete = true }
    fun cancelDelete() = act { confirmingDelete = false }
    fun save() = act { current ->
        if (commitConfirmed || !writable || confirmingDelete || error == OperationsFailure.STALE) return@act
        val d=draft?:return@act
        var input:ExpenseInput?=null
        if(!copyData(deriveCost(d)){input=d.input()})return@act
        val accepted=input
        if (accepted == null) { error = OperationsFailure.INVALID; return@act }
        if (!ExpenseRules.valid(accepted)) { error = OperationsFailure.INVALID; return@act }
        val old = expected
        write(current, if (old == null) "Kosten eingetragen" else "Kosten gespeichert") { done ->
            if (old == null) repository?.create(boundAction!!, accepted) { done(unit(it)) }
            else repository?.edit(boundAction!!, old, accepted) { done(unit(it)) }
        }
    }
    fun delete() = act { current ->
        val old = expected ?: return@act
        if (!confirmingDelete || !writable || !ExpenseRules.mayDelete(action()!!.status) || error == OperationsFailure.STALE) return@act
        write(current, "Kostenposition entfernt") { repository?.delete(boundAction!!, old, it) }
    }
    private fun draftClearAfterCommit() {selected=null;draft = null;expected=null;confirmingDelete=false}
    private fun unit(result: OperationsResult<Expense>): OperationsResult<Unit> = when (result) {
        is OperationsResult.Value -> {committedId=result.value.id;OperationsResult.Value(Unit)}
        is OperationsResult.Rejected -> result
    }
    private fun write(current: () -> Boolean, message: String, work: ((OperationsResult<Unit>) -> Unit) -> Unit) {
        val emit=if(feedbackFactory==null)feedback else feedbackFactory!!.invoke()?:run{error=OperationsFailure.BUDGET_REFUSED;return}

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
                        loader(WorkspaceDetailKind.EXPENSE,id){refreshed->
                            if(!current())return@loader
                            busy=false
                            when(refreshed){
                                is WorkspaceDetailResult.Rejected->{error=refreshed.reason.operationFailure();emit("Gespeichert – Ansicht konnte noch nicht aktualisiert werden.")}
                                is WorkspaceDetailResult.Value->{
                                    board=refreshed.value.workspace?.expenseBoard ?: board
                                    draftClearAfterCommit()
                                    commitConfirmed=false;error=null;emit(message)
                                }
                            }
                        }
                        return@work
                    }
                    discard(); refresh()
                    if (current()) refreshWorkspace()
                    if (current()) emit(message)
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
        var sum=0L
        for(p in payments){val amount=EuroMoney.parse(p.amount)?:return "Bitte alle Zahlungsbeträge in Euro eingeben."
            sum=try{Math.addExact(sum,amount)}catch(_:ArithmeticException){return "Die Zahlungssumme ist zu groß."}}

        return if (sum > totalCents) "Zahlungen überschreiten den Gesamtbetrag um ${EuroMoney.format(sum - totalCents)}"
        else "Zahlungen: ${EuroMoney.format(sum)} von ${EuroMoney.format(totalCents)}"
    }
    companion object {
        fun from(e: Expense) = ExpenseDraft(e.title, EuroMoney.input(e.totalCents), e.payments.map { ExpensePaymentDraft(it.payerLabel, EuroMoney.input(it.amountCents)) })
    }
}
