package org.pag.prototype.product

import androidx.compose.runtime.*
import org.pag.domain.*

enum class MemoryReportMode { LIST, SECTION, SUMMARY, DETAIL, ADDENDUM }
/** UI navigation and uncommitted input only. Saved sections are exclusively repository content. */
class MemoryReportModel(private val repository: MemoryReportRepository?, private val session: () -> SessionSnapshot,
    private val action: () -> Action?, private val interaction: () -> Unit,
    private val refreshWorkspace: () -> Unit, private val feedback: (String) -> Unit) {
    private var copyGate:((ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?)?=null
    internal fun bindCopyGate(gate:(ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?){check(copyGate==null);copyGate=gate}
    private fun copyData(extra:String?=null,producer:()->Unit):Boolean {
        val f=AppConsumerCopies.Footprint();f.text(authorLabel);f.text(addendum)
        for((key,value) in answers){f.text(key);f.answer(value)}
        for((key,value) in detail?.answers.orEmpty()){f.text(key);f.answer(value)}
        for(value in detail?.addenda.orEmpty())f.text(value.content)
        f.item(decisions.size.toLong())
        val failure=if(copyGate==null){producer();null}else copyGate!!.invoke(f.units(extra),producer)
        if(failure!=null){error=failure;return false};return true
    }
    private fun <T> copies(fallback:T,producer:()->T):T {var value=fallback;copyData{value=producer()};return value}
    fun textAnswer(key:String,typed:String)=act {
        copyData(typed){
            val q=MemoryQuestionnaireV1.question(key)?:return@copyData
            val count=if(q.type==MemoryAnswerType.OPTIONAL_COUNT && typed.isNotEmpty() && typed.all{it in '0'..'9'})typed.toIntOrNull() else null
            applyAnswer(key,if(typed.isEmpty())null else if(count!=null)MemoryAnswerValue.Count(count) else MemoryAnswerValue.Text(typed))
        }
    }
    fun optionAnswer(key:String,option:String)=act {
        copyData(option){
            val q=MemoryQuestionnaireV1.question(key)?:return@copyData
            val old=answers[key]
            if(q.type==MemoryAnswerType.MULTI_CHOICE){
                val previous=(old as? MemoryAnswerValue.Multi)?.keys.orEmpty()
                fun special(key:String)=key=="UNKNOWN" || key=="UNCLEAR" || key=="NO_ASSESSMENT"
                val next=if(option in previous)previous-option else if(special(option))listOf(option) else previous.filterNot(::special)+option
                applyAnswer(key,next.takeIf{it.isNotEmpty()}?.let{MemoryAnswerValue.Multi(it)})
            }else applyAnswer(key,if((old as? MemoryAnswerValue.Option)?.key==option)null else MemoryAnswerValue.Option(option))
        }
    }
    private var draftPresentation:MemoryDraftPresentation?=null
    private fun presentation():MemoryDraftPresentation? {
        val s=session()
        if(!opened || s.state!=SessionState.READY || s.generation!=generation || action()?.id!=boundAction || action()?.status!=boundStatus)return null
        if(copyGate?.invoke(ActiveWorkspaceBudget.Units()){}!=null)return null
        val saved=detail?.answers.orEmpty()
        draftPresentation?.let{if(it.matches(saved,answers,decisions,sectionOrdinal))return it}
        var result:MemoryDraftPresentation?=null
        if(copyData{result=MemoryDraftPresentation(saved,answers,decisions,sectionOrdinal)})draftPresentation=result
        return result
    }
    fun answerText(key:String):String=presentation()?.texts?.get(key).orEmpty()
    fun answerValid(key:String):Boolean=if(answers[key]==null)true else presentation()?.valid?.get(key)==true
    fun answerLabel(key:String):String=presentation()?.labels?.get(key)?:"Keine Angabe"
    var mode by mutableStateOf(MemoryReportMode.LIST); private set
    var headers by mutableStateOf<List<MemoryReportHeader>?>(null); private set
    var detail by mutableStateOf<MemoryReportDetail?>(null); private set
    var sectionOrdinal by mutableStateOf(0); private set
    var answers by mutableStateOf<Map<String, MemoryAnswerValue>>(emptyMap()); private set
    var authorLabel by mutableStateOf(""); private set
    var addendum by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set
    var decisions by mutableStateOf<Set<String>>(emptySet()); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<OperationsFailure?>(null); private set
    var confirmingComplete by mutableStateOf(false); private set
    var confirmingDiscard by mutableStateOf(false); private set
    private var pendingBack: (() -> Unit)? = null
    private var opened by mutableStateOf(false)
    private var boundAction: String? = null
    private var boundStatus: ActionStatus? = null
    private var generation: Long? = null
    private var epoch = 0L
    private var request = 0L
    val available get() = opened && repository != null && session().state == SessionState.READY &&
        session().generation == generation && action()?.id == boundAction && action()?.status == boundStatus
    val completedView get() = available && boundStatus == ActionStatus.COMPLETED
    val expected get() = detail?.header
    val preFirstSave get() = mode == MemoryReportMode.SECTION && expected == null
    val combinedAnswers get() = presentation()?.combined.orEmpty()
    val conflicts get():Set<String> = presentation()?.conflicts.orEmpty()
    val unresolved get():Set<String> = presentation()?.unresolved.orEmpty()
    val sectionValid get() = error!=OperationsFailure.BUDGET_REFUSED && error!=OperationsFailure.OWNER_UNCONFIRMED && mode == MemoryReportMode.SECTION && (sectionOrdinal != 0 || MemoryReportRules.authorValid(authorLabel)) &&
        presentation()?.valid?.values?.all{it}==true && unresolved.isEmpty()
    fun clear() {
        epoch++; request++; opened = false; boundAction = null; boundStatus = null; generation = null
        headers = null; draftPresentation=null; resetEditor(); loading = false; busy = false; error = null
    }
    private fun resetEditor() {
        mode = MemoryReportMode.LIST; detail = null; sectionOrdinal = 0; answers = emptyMap(); authorLabel = ""
        addendum = ""; dirty = false; decisions = emptySet(); confirmingComplete = false; confirmingDiscard = false; pendingBack = null
    }
    internal fun enter(deferList:Boolean=false) {
        clear(); val a = action() ?: return; val s = session()
        if (repository == null || s.state != SessionState.READY || !MemoryReportRules.mayRead(a.status)) return
        boundAction = a.id; boundStatus = a.status; generation = s.generation; opened = true; if(deferList)loading=true else readList()
    }
    /** Explicit selected-area batch only; no detail answers or cached previous list. */
    internal fun acceptWorkspaceHeaders(actionId:String,sessionGeneration:Long,values:List<MemoryReportHeader>):Boolean {
        if(!available || boundAction!=actionId || generation!=sessionGeneration || !loading || headers!=null || mode!=MemoryReportMode.LIST ||
            values.any{it.actionId!=actionId || !MemoryReportRules.headerValid(it)})return false
        request++;headers=values;loading=false;error=null
        return true
    }
    fun openCompleted(prepare: () -> Unit) {
        val a = action() ?: return; val s = session(); val token = epoch
        if (repository == null || s.state != SessionState.READY || a.status != ActionStatus.COMPLETED) return
        interaction()
        fun current() = session().state == SessionState.READY && session().generation == s.generation && action()?.id == a.id && action()?.status == a.status
        if (!current() || token != epoch) return
        prepare(); if (current()) enter()
    }
    private fun act(work: (() -> Boolean) -> Unit) {
        if (!available || busy || loading || confirmingDiscard) return
        val token = epoch; val current = { token == epoch && available }
        interaction(); if (current()) work(current)
    }
    private fun readList() {
        val token = epoch; val r = ++request; val a = boundAction ?: return; loading = true
        repository?.list(a) { result ->
            if (token != epoch || !available || request != r) return@list
            request++ // Consume once: a delayed duplicate must not replace newer UI input.
            loading = false
            when (result) {
                is OperationsResult.Value -> if (result.value.all { it.actionId == a && MemoryReportRules.headerValid(it) }) { headers = result.value; error = null } else error = OperationsFailure.NOT_FOUND
                is OperationsResult.Rejected -> error = result.reason
            }
        }
    }
    fun reload() = act { resetEditor(); error = null; readList() }
    fun create() = act { request++; loading = false; resetEditor(); mode = MemoryReportMode.SECTION; error = null }
    fun open(id: String) = act { readDetail(id, resume = true) }
    private fun readDetail(id: String, resume: Boolean, target: Int? = null) {
        val token = epoch; val r = ++request; val a = boundAction ?: return; loading = true
        repository?.detail(a, id) { result ->
            if (token != epoch || !available || request != r) return@detail
            request++
            loading = false
            when (result) {
                is OperationsResult.Rejected -> error = result.reason
                is OperationsResult.Value -> {
                    val d = result.value
                    if (d.header.id != id || d.header.actionId != a || !MemoryReportRules.headerValid(d.header)) { error = OperationsFailure.NOT_FOUND; return@detail }
                    detail = d; authorLabel = d.header.authorLabel; dirty = false; decisions = emptySet(); error = null; addendum = ""
                    if (d.header.status == MemoryReportStatus.COMPLETED) mode = MemoryReportMode.DETAIL
                    else if (target != null && target <= 16) showSection(target)
                    else if (resume && d.header.lastSavedSectionOrdinal < 16) showSection(d.header.lastSavedSectionOrdinal + 1)
                    else mode = MemoryReportMode.SUMMARY
                }
            }
        }
    }
    private fun showSection(ordinal: Int) {
        sectionOrdinal = ordinal; mode = MemoryReportMode.SECTION
        if(!copyData{answers = detail?.answers.orEmpty().filterKeys { MemoryQuestionnaireV1.question(it)?.sectionOrdinal == ordinal }})return
        authorLabel = detail?.header?.authorLabel ?: authorLabel
        dirty = false; decisions = emptySet(); confirmingComplete = false
    }
    fun editSection(ordinal: Int) = act {
        val h = expected ?: return@act
        if (h.status == MemoryReportStatus.DRAFT && ordinal in 0..h.lastSavedSectionOrdinal) showSection(ordinal)
    }
    fun changeAuthor(value: String) = act { if (mode == MemoryReportMode.SECTION && sectionOrdinal == 0) { copyData(value){authorLabel = value; dirty = true; invalidCleared()} } }
    fun removeAnswer(key:String)=act {copyData{applyAnswer(key,null)}}
    internal fun changeAnswer(key:String,value:MemoryAnswerValue?)=act {copyData{applyAnswer(key,value)}}
    private fun applyAnswer(key:String,value:MemoryAnswerValue?){
        if(mode!=MemoryReportMode.SECTION || MemoryQuestionnaireV1.question(key)?.sectionOrdinal!=sectionOrdinal)return
        request++;loading=false
        answers=if(value==null)answers-key else answers+(key to value)
        decisions=decisions.filterNot{it==key || MemoryQuestionnaireV1.question(it)?.dependency?.parentKey==key}.toSet()
        dirty=true;invalidCleared()
    }
    private fun invalidCleared() { if (error == OperationsFailure.INVALID) error = null }
    fun keepDetail(key: String) = act { if (key in conflicts) { copyData{decisions = decisions + key; dirty = true} } }
    fun removeDetail(key: String) = act { if (key in conflicts) { copyData{answers = answers - key; decisions = decisions - key; dirty = true} } }
    fun requestLeave(recordInteraction: Boolean = true, leave: () -> Unit) {
        if (!available || busy) return
        val token = epoch
        if (recordInteraction) interaction()
        if (!available || epoch != token) return
        if (dirty) { pendingBack = leave; confirmingDiscard = true } else leave()
    }
    fun cancelDiscard() {
        if (!available) return; val token = epoch; interaction()
        if (available && token == epoch) { confirmingDiscard = false; pendingBack = null }
    }
    fun confirmDiscard() {
        if (!available || !confirmingDiscard) return; val token = epoch; interaction()
        if (!available || token != epoch) return
        val back = pendingBack; pendingBack = null; confirmingDiscard = false; dirty = false; back?.invoke()
    }
    fun back() = act {
        requestLeave(recordInteraction = false) {
            error = null; confirmingComplete = false
            when (mode) {
                MemoryReportMode.SECTION -> if (sectionOrdinal > 0) showSection(sectionOrdinal - 1) else { resetEditor(); readList() }
                MemoryReportMode.ADDENDUM -> { addendum = ""; mode = MemoryReportMode.DETAIL; dirty = false }
                else -> { resetEditor(); readList() }
            }
        }
    }
    fun save() = act { current ->
        if (error == OperationsFailure.STALE || !sectionValid) { if (error !in setOf(OperationsFailure.STALE,OperationsFailure.BUDGET_REFUSED,OperationsFailure.OWNER_UNCONFIRMED)) error = OperationsFailure.INVALID; return@act }
        val conflicting=conflicts
        if(!copyData{})return@act
        val section = MemorySectionSave(sectionOrdinal, answers, conflicting.intersect(decisions), authorLabel.takeIf { sectionOrdinal == 0 })
        val h = expected; val next = sectionOrdinal + 1
        write(current, "Abschnitt gespeichert", next) { done ->
            if (h == null) repository?.create(boundAction!!, authorLabel, section, done)
            else repository?.saveSection(boundAction!!, h, section, done)
        }
    }
    fun requestComplete() = act { if (mode == MemoryReportMode.SUMMARY && expected?.let(MemoryReportRules::mayComplete) == true && error != OperationsFailure.STALE) confirmingComplete = true }
    fun cancelComplete() = act { confirmingComplete = false }
    fun complete() = act { current ->
        val h = expected ?: return@act
        if (!confirmingComplete || !MemoryReportRules.mayComplete(h) || error == OperationsFailure.STALE) return@act
        write(current, "Gedächtnisprotokoll abgeschlossen") { repository?.complete(boundAction!!, h, it) }
    }
    fun startAddendum() = act { if (expected?.status == MemoryReportStatus.COMPLETED) { mode = MemoryReportMode.ADDENDUM; addendum = ""; dirty = false; error = null } }
    fun changeAddendum(value: String) = act { if (mode == MemoryReportMode.ADDENDUM) { copyData(value){addendum = value; dirty = true; invalidCleared()} } }
    fun saveAddendum() = act { current ->
        val h = expected ?: return@act
        if (mode != MemoryReportMode.ADDENDUM || !MemoryReportRules.addendumValid(addendum) || error == OperationsFailure.STALE) return@act
        write(current, "Ergänzung gespeichert") { repository?.addAddendum(boundAction!!, h, addendum, it) }
    }
    private fun write(current: () -> Boolean, message: String, target: Int? = null, work: ((OperationsResult<MemoryReportHeader>) -> Unit) -> Unit) {
        val r = ++request; loading = false; busy = true; error = null
        work { result ->
            if (!current() || request != r) return@work
            request++; busy = false
            when (result) {
                is OperationsResult.Rejected -> error = result.reason
                is OperationsResult.Value -> {
                    if (result.value.actionId != boundAction || !MemoryReportRules.headerValid(result.value)) { error = OperationsFailure.NOT_FOUND; return@work }
                    dirty = false; confirmingComplete = false
                    readDetail(result.value.id, false, target)
                    if (current()) refreshWorkspace()
                    if (current()) feedback(message)
                }
            }
        }
    }
}
