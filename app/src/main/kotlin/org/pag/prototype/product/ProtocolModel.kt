package org.pag.prototype.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.pag.domain.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Immutable CODE enum groups; no action payload, authority or per-render Set.
private val supportedProtocolRevisionTypeGroup1=setOf(ProtocolRevisionType.CORRECTION,ProtocolRevisionType.ADDENDUM)


enum class ProtocolFilter { ALL, KEY_EVENTS }
enum class ProtocolUiMode { ACTIVE, COMPLETED_FOLLOW_UP }
/** Explicit phase presentation. All drafts and request bindings expire with the workspace. */
class ProtocolModel(private val repository: ProtocolRepository?, private val session: () -> SessionSnapshot,
    private val action: () -> Action?, private val interaction: () -> Unit,
    private val refreshWorkspace: () -> Unit, private val feedback: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis) {
    internal val uiEditorEpoch:Long get()=epoch
    private var feedbackFactory:WorkspaceFeedbackFactory?=null
    internal fun bindFeedbackFactory(factory:WorkspaceFeedbackFactory){check(feedbackFactory==null);feedbackFactory=factory}
    private var readPresentation:ProtocolPresentation?=null
    internal fun ui():ProtocolPresentation? {
        if(session().state!=SessionState.READY || action()?.status?.let(ProtocolRules::mayRead)!=true || copyGate?.invoke(FollowPresentationCost.check){}!=null)return null
        readPresentation?.let{if(it.matches(timeline,detail))return it}
        val plan=copyGate?.invoke(FollowPresentationCost.planning){}
        if(plan!=null){error=if(plan==OperationsFailure.BUDGET_REFUSED)ProtocolFailure.BUDGET_REFUSED else ProtocolFailure.OWNER_UNCONFIRMED;return null}
        var items=0L;var chars=0L
        fun item(){items=Math.addExact(items,1)}
        fun text(v:String?){chars=Math.addExact(chars,v?.length?.toLong()?:0)}
        for(v in timeline.orEmpty()){item();text(v.title);text(v.entryId)}
        for(v in detail?.revisions.orEmpty()){
            item();val f=AppConsumerCopies.Footprint();f.protocol(v.revision.input);chars=Math.addExact(chars,f.chars);items=Math.addExact(items,f.items)
            for(p in v.affectedPersons){item();text(p.pseudonym)};text(v.reportedBy?.pseudonym)
        }
        val units=try{FollowPresentationCost.units(items,chars)}catch(_:ArithmeticException){error=ProtocolFailure.BUDGET_REFUSED;return null}
        var value:ProtocolPresentation?=null
        val failure=if(copyGate==null){value=ProtocolPresentation(timeline,detail);null}else copyGate!!.invoke(units){value=ProtocolPresentation(timeline,detail)}
        if(failure!=null){error=if(failure==OperationsFailure.BUDGET_REFUSED)ProtocolFailure.BUDGET_REFUSED else ProtocolFailure.OWNER_UNCONFIRMED;return null}
        readPresentation=value;return value
    }
    private var detailLoader:WorkspaceDetailLoader?=null
    internal var formStructureEpoch:Long=0;private set
    internal fun bindDetailLoader(loader:WorkspaceDetailLoader){check(detailLoader==null);detailLoader=loader}
    var commitConfirmed by mutableStateOf(false);private set
    private var copyGate:((ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?)?=null
    internal fun bindCopyGate(gate:(ActiveWorkspaceBudget.Units,()->Unit)->OperationsFailure?){check(copyGate==null);copyGate=gate}
    private fun copyData(extra:String?=null,additional:ProtocolInput?=null,producer:()->Unit):Boolean {
        val f=AppConsumerCopies.Footprint();input?.let(f::protocol);expected?.revision?.input?.let(f::protocol);additional?.let(f::protocol)
        val failure=if(copyGate==null){producer();null}else copyGate!!.invoke(f.units(extra),producer)
        if(failure!=null){error=if(failure==OperationsFailure.BUDGET_REFUSED)ProtocolFailure.BUDGET_REFUSED else ProtocolFailure.OWNER_UNCONFIRMED;return false}
        return true
    }
    private fun alter(text:String?=null,produce:(ProtocolInput)->ProtocolInput)=act {
        val old=input?:return@act
        copyData(text){applyChange(produce(old))}
    }
    fun title(value:String)=alter(value){it.copy(title=value)}
    fun description(value:String)=alter(value){it.copy(description=value)}
    fun place(value:String)=alter(value){it.copy(placeLabel=value)}
    fun classification(value:ProtocolClassification)=alter{it.copy(classification=value)}
    fun approximate()=alter{it.copy(occurredAtApproximate=!it.occurredAtApproximate)}
    fun source(value:ProtocolSourceType?)=alter{old->old.copy(sourceType=value).also{if(old.sourceType!=value)formStructureEpoch++}}
    fun reportedBy(id:String?)=alter{it.copy(reportedByPersonId=id)}
    fun affected(id:String)=alter{it.copy(affectedPersonIds=if(id in it.affectedPersonIds)it.affectedPersonIds-id else it.affectedPersonIds+id)}
    fun removeAnnouncement()=alter{it.copy(announcementText=null,announcementKind=null)}
    fun announcementKind(value:ProtocolAnnouncementKind)=alter{it.copy(announcementKind=value,announcementText=it.announcementText.orEmpty())}
    fun announcement(value:String)=alter(value){it.copy(announcementText=value)}
    fun legalBasis(value:String)=alter(value){it.copy(statedLegalBasis=value)}
    fun identifierType(index:Int,value:ProtocolOfficialIdentifierType)=alter{it.copy(officialIdentifiers=it.officialIdentifiers.mapIndexed{n,row->if(n==index)row.copy(type=value) else row})}
    fun identifierValue(index:Int,value:String)=alter(value){it.copy(officialIdentifiers=it.officialIdentifiers.mapIndexed{n,row->if(n==index)row.copy(value=value) else row})}
    var mode by mutableStateOf(ProtocolUiMode.ACTIVE); private set
    private var followUpActionId: String? = null
    private var followUpGeneration: Long? = null
    private var draftStatus: ActionStatus? = null
    private val modeStatus get() = when (mode) {
        ProtocolUiMode.ACTIVE -> ActionStatus.ACTIVE
        ProtocolUiMode.COMPLETED_FOLLOW_UP -> ActionStatus.COMPLETED
    }
    val completedFollowUp get() = mode == ProtocolUiMode.COMPLETED_FOLLOW_UP && writable
    fun openCompletedFollowUp(prepare: () -> Unit = {}) {
        val before = session(); val a = action() ?: return; val token = epoch
        if (busy || repository == null || before.state != SessionState.READY || a.status != ActionStatus.COMPLETED) return
        fun current() = session().state == SessionState.READY && session().generation == before.generation &&
            action()?.id == a.id && action()?.status == ActionStatus.COMPLETED
        interaction()
        if (!current() || token != epoch) return
        prepare()
        if (!current()) return
        clear(); mode = ProtocolUiMode.COMPLETED_FOLLOW_UP
        followUpActionId = a.id; followUpGeneration = before.generation
        refresh()
    }
    var timeline by mutableStateOf<List<ProtocolTimelineItem>?>(null); private set
    var loading by mutableStateOf(false); private set
    var detail by mutableStateOf<ProtocolEntryDetail?>(null); private set
    var entryId by mutableStateOf<String?>(null); private set
    var detailLoading by mutableStateOf(false); private set
    var filter by mutableStateOf(ProtocolFilter.ALL); private set
    var input by mutableStateOf<ProtocolInput?>(null); private set
    var eventTime by mutableStateOf<LocalDateTime?>(null); private set
    var zone: ZoneId = ZoneId.systemDefault(); private set
    var revisionType by mutableStateOf(ProtocolRevisionType.ORIGINAL); private set
    var expected: ProtocolExpectedRevision? = null; private set
    var clearMistaken by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<ProtocolFailure?>(null); private set
    var validation by mutableStateOf<String?>(null); private set
    var moreTemplates by mutableStateOf(false); private set
    var moreFields by mutableStateOf(false); private set
    var history by mutableStateOf(false); private set
    var historical by mutableStateOf<ProtocolRevisionDetail?>(null); private set
    var confirmingMistaken by mutableStateOf(false); private set
    private var epoch = 0L
    private var timelineRequest = 0L
    private var detailRequest = 0L
    val form get() = input != null
    val nested get() = form || entryId != null
    val writable get() = session().state == SessionState.READY && action()?.status == modeStatus &&
        (mode == ProtocolUiMode.ACTIVE || (action()?.id == followUpActionId && session().generation == followUpGeneration))
    private class KeyEvents(private val original:List<ProtocolTimelineItem>):AbstractList<ProtocolTimelineItem>() {
        override val size=original.count{it.classification==ProtocolClassification.KEY_EVENT}
        override fun get(index:Int):ProtocolTimelineItem {
            if(index<0 || index>=size)throw IndexOutOfBoundsException()
            var seen=0;var i=0
            while(i<original.size){val v=original[i++];if(v.classification==ProtocolClassification.KEY_EVENT && seen++==index)return v}
            throw IndexOutOfBoundsException()
        }
    }
    private var keyEvents:List<ProtocolTimelineItem>?=null
    private fun acceptTimeline(value:List<ProtocolTimelineItem>):Boolean {
        val units=ActiveWorkspaceBudget.Units(objects=4,lists=1,references=8)
        val failure=if(copyGate==null){keyEvents=KeyEvents(value);null}else copyGate!!.invoke(units){keyEvents=KeyEvents(value)}
        if(failure!=null){error=when(failure){OperationsFailure.BUDGET_REFUSED->ProtocolFailure.BUDGET_REFUSED;else->ProtocolFailure.NOT_FOUND};return false}
        timeline=value;return true
    }
    val filtered get() = if(filter==ProtocolFilter.ALL)timeline else keyEvents
    private fun moveContext() { epoch++; loading = false }
    fun clear() {
        moveContext(); timelineRequest++; detailRequest++;commitConfirmed=false;readPresentation=null
        mode = ProtocolUiMode.ACTIVE; followUpActionId = null; followUpGeneration = null
        timeline = null; keyEvents=null; loading = false; detail = null; entryId = null; detailLoading = false; filter = ProtocolFilter.ALL
        discardDraft(); busy = false; error = null; moreTemplates = false; history = false
        historical = null; confirmingMistaken = false
    }
    private fun discardDraft() { draftStatus = null; input = null; eventTime = null; expected = null; clearMistaken = false; moreFields = false; validation = null; revisionType = ProtocolRevisionType.ORIGINAL }
    private fun binding(): (() -> Boolean)? {
        val s = session(); val a = action() ?: return null; val token = epoch
        val uiMode = mode; val status = modeStatus
        if (!writable) return null
        return { val n = session(); n.state == SessionState.READY && n.generation == s.generation &&
            action()?.id == a.id && action()?.status == status && mode == uiMode && writable && epoch == token }
    }
    private fun act(block: () -> Unit) {
        if (busy) return
        val current = binding() ?: return
        interaction()
        if (current()) block()
    }
    internal fun prepareWorkspaceTimeline() {
        if(binding()==null || mode!=ProtocolUiMode.ACTIVE || nested || timeline!=null)return
        timelineRequest++;loading=true
    }
    internal fun acceptWorkspaceTimeline(actionId:String,generation:Long,value:List<ProtocolTimelineItem>):Boolean {
        if(binding()==null || action()?.id!=actionId || session().generation!=generation ||
            mode!=ProtocolUiMode.ACTIVE || !loading || timeline!=null || nested)return false
        timelineRequest++;if(!acceptTimeline(value))return false;loading=false;error=null
        return true
    }
    fun refresh() {
        val current = binding() ?: return; val id = action()!!.id; val request = ++timelineRequest
        loading = true
        detailLoader?.let{loader->
            loader(WorkspaceDetailKind.PROTOCOL_TIMELINE,id){result->
                if(!current() || request!=timelineRequest)return@loader
                timelineRequest++;loading=false
                when(result){
                    is WorkspaceDetailResult.Rejected->reject(when(result.reason){WorkspaceFailure.BUDGET_REFUSED->ProtocolFailure.BUDGET_REFUSED;WorkspaceFailure.STALE->ProtocolFailure.STALE;else->ProtocolFailure.NOT_FOUND})
                    is WorkspaceDetailResult.Value->{val rows=result.value.workspace?.protocolTimeline;if(rows==null)reject(ProtocolFailure.NOT_FOUND) else if(acceptTimeline(rows))error=null}
                }
            };return
        }
        repository?.listTimeline(id) { result ->
            if (!current() || request != timelineRequest) return@listTimeline
            timelineRequest++;loading = false
            when (result) { is ProtocolResult.Value -> { if(acceptTimeline(result.value))error = null }
                is ProtocolResult.Rejected -> reject(result.reason) }
        }
    }
    fun refreshFromInteraction() = act { refresh() }
    fun touch() = act {}
    fun changeFilter(value: ProtocolFilter) = act { filter = value }
    fun toggleTemplates() = act { moreTemplates = !moreTemplates }
    fun toggleFields() = act { moreFields = !moreFields }
    fun toggleHistory() = act { history = !history }
    fun oldRevisionId(id:String)=act {detail?.revisions?.find{it.revision.id==id}?.let{historical=it}}
    fun oldRevision(value: ProtocolRevisionDetail?) = act {
        if (value == null || detail?.revisions?.any { it == value } == true) historical = value
    }
    fun create(templateKey: String? = null) = act {
        val time = now()
        val draft = if (templateKey == null) ProtocolInput(ProtocolClassification.RELEVANT, "", time)
            else ProtocolRules.templates.find { it.key == templateKey }?.input(time) ?: return@act
        moveContext(); detailRequest++; detail = null; entryId = null; detailLoading = false; beginDraft(draft, ProtocolRevisionType.ORIGINAL)
    }
    private fun beginDraft(value: ProtocolInput, type: ProtocolRevisionType) {
        if(!copyData(additional=value){beginDraftAdmitted(value,type)})return
    }
    private fun beginDraftAdmitted(value:ProtocolInput,type:ProtocolRevisionType){
        draftStatus = modeStatus
        input = value.copy(affectedPersonIds = value.affectedPersonIds.toSet(), officialIdentifiers = value.officialIdentifiers.toList())
        zone = ZoneId.systemDefault(); eventTime = Instant.ofEpochMilli(value.occurredAt).atZone(zone).toLocalDateTime()
        revisionType = type; clearMistaken = false; error = null; validation = null; moreFields = false
        historical = null; confirmingMistaken = false
    }
    internal fun change(value:ProtocolInput)=act {copyData{applyChange(value)}}
    private fun applyChange(value:ProtocolInput){
        val old=input?:return
        input=value.copy(templateKey=old.templateKey,
            classification=if(revisionType==ProtocolRevisionType.CORRECTION)value.classification else old.classification,
            reportedByPersonId=value.reportedByPersonId.takeIf{value.sourceType==ProtocolSourceType.REPORTED})
        validation=null
    }
    fun changeTime(value: LocalDateTime?) = act { eventTime = value; validation = null }
    fun toggleClearMistaken() = act {
        if (form && revisionType == ProtocolRevisionType.CORRECTION && expected?.revision?.mistaken == true) clearMistaken = !clearMistaken
    }
    fun addIdentifier() = act { val i = input ?: return@act
        if (i.officialIdentifiers.size < 32) copyData { input = i.copy(officialIdentifiers = i.officialIdentifiers + ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.SERVICE_OR_ID_NUMBER, ""));formStructureEpoch++ } }
    fun removeIdentifier(index:Int)=alter{it.copy(officialIdentifiers=it.officialIdentifiers.filterIndexed{n,_->n!=index}).also{formStructureEpoch++}}
    fun open(entryId: String) = act { moveContext(); discardDraft(); historical = null; history = false; confirmingMistaken = false; loadDetail(entryId) }
    private fun loadDetail(entryId: String,discardAfter:Boolean=false,projectionFeedback:((String)->Unit)?=null,completed:(()->Unit)?=null) {
        val current = binding() ?: return; val id = action()!!.id; val request = ++detailRequest
        this.entryId = entryId
        detailLoading = true; error = null
        if (detail?.entry?.id != entryId) detail = null
        var delivered=false
        fun accept(result:ProtocolResult<ProtocolEntryDetail>){
            if(delivered || !current() || request!=detailRequest)return
            delivered=true;detailLoading=false
            when(result){
                is ProtocolResult.Value->if(result.value.entry.actionId==id && result.value.entry.id==entryId){
                    if(discardAfter){discardDraft();historical=null;confirmingMistaken=false;commitConfirmed=false}
                    detail=result.value;error=null;completed?.invoke()
                }
                is ProtocolResult.Rejected->{reject(result.reason);if(commitConfirmed)projectionFeedback?.invoke("Gespeichert – Ansicht konnte noch nicht aktualisiert werden.")}
            }
        }
        val loader=detailLoader
        if(loader==null)repository?.getEntry(id,entryId,::accept) else loader(WorkspaceDetailKind.PROTOCOL,entryId){result->
            if(delivered || !current() || request!=detailRequest)return@loader
            when(result){
                is WorkspaceDetailResult.Rejected->accept(ProtocolResult.Rejected(result.reason.protocolFailure()))
                is WorkspaceDetailResult.Value->{
                    val value=result.value.protocolDetail
                    val rows=result.value.workspace?.protocolTimeline
                    if(value==null || rows==null)accept(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND))
                    else if(acceptTimeline(rows))accept(ProtocolResult.Value(value))
                    else {delivered=true;detailLoading=false;if(commitConfirmed)projectionFeedback?.invoke("Gespeichert – Ansicht konnte noch nicht aktualisiert werden.")}
                }
            }
        }
    }

    fun revise(type: ProtocolRevisionType) = act {
        if (historical != null || detailLoading || error == ProtocolFailure.STALE) return@act
        if (type !in supportedProtocolRevisionTypeGroup1) return@act
        val d = detail ?: return@act
        if(!copyData(additional=d.current.revision.input) { expected = ProtocolExpectedRevision(d.entry, d.current.revision.copy(input = d.current.revision.input.copy(affectedPersonIds = d.current.revision.input.affectedPersonIds.toSet(), officialIdentifiers = d.current.revision.input.officialIdentifiers.toList()))) })return@act
        moveContext()
        beginDraft(d.current.revision.input, type)
    }
    fun back() = act {
        val reload = error == ProtocolFailure.STALE && detail != null
        moveContext(); detailRequest++; confirmingMistaken = false; error = null
        detailLoading = false
        when { form -> discardDraft(); historical != null -> historical = null
            else -> { detail = null; entryId = null; detailLoading = false; history = false } }
        if (reload && detail != null) loadDetail(detail!!.entry.id)
        if (timeline == null) refresh()
    }
    fun currentVersion() = act { val id = expected?.entry?.id ?: detail?.entry?.id ?: entryId ?: return@act
        moveContext();loadDetail(id,discardAfter=true) }
    fun requestMistaken() = act { if (historical == null && !detailLoading && error != ProtocolFailure.STALE && detail?.current?.revision?.mistaken == false) confirmingMistaken = true }
    fun cancelMistaken() = act { confirmingMistaken = false }
    fun markMistaken() = act {
        val d = detail ?: return@act
        if (error == ProtocolFailure.STALE || !confirmingMistaken || d.current.revision.mistaken) return@act
        val status = modeStatus
        write(ProtocolRevisionType.MARKED_MISTAKEN) { publish -> repository?.markMistaken(action()!!.id, status, ProtocolExpectedRevision(d.entry, d.current.revision), publish) }
    }
    fun save() = act {
        if (error == ProtocolFailure.STALE) return@act
        val status = draftStatus?.takeIf { it == modeStatus } ?: return@act
        val draft = input ?: return@act
        val instant = selectedInstant(eventTime, zone)
        if (instant == null) { validation = "Diese Uhrzeit ist durch eine Zeitumstellung nicht eindeutig. Bitte eine andere Uhrzeit wählen."; return@act }
        if(!copyData{})return@act
        val value = draft.copy(occurredAt = instant)
        validation = protocolValidation(value)
        if (validation != null) return@act
        val e = expected
        write(revisionType) { publish -> if (revisionType == ProtocolRevisionType.ORIGINAL)
            repository?.createEntry(action()!!.id, status, value, publish)
            else if (e != null) repository?.reviseEntry(action()!!.id, status, e, revisionType, value, clearMistaken, publish) }
    }
    private fun reject(reason: ProtocolFailure) {
        // Persisted policy can reject a phase change before the action projection refreshes.
        if (reason == ProtocolFailure.INVALID_STATE && mode == ProtocolUiMode.COMPLETED_FOLLOW_UP) clear()
        error = reason
    }
    private fun write(type: ProtocolRevisionType, operation: ((ProtocolResult<ProtocolSaved>) -> Unit) -> Unit) {
        val current = binding() ?: return
        val message = if (mode == ProtocolUiMode.ACTIVE) "Protokolleintrag gespeichert" else when (type) {
            ProtocolRevisionType.ORIGINAL -> "Nachtrag gespeichert"
            ProtocolRevisionType.CORRECTION -> "Korrektur gespeichert"
            ProtocolRevisionType.ADDENDUM -> "Ergänzung gespeichert"
            ProtocolRevisionType.MARKED_MISTAKEN -> "Irrtumsmarkierung gespeichert"
        }
        if(commitConfirmed || detailLoading)return
        val emit=if(feedbackFactory==null)feedback else feedbackFactory!!.invoke()?:run{error=ProtocolFailure.BUDGET_REFUSED;return}
        busy = true; error = null
        operation { result ->
            if (!current()) return@operation
            busy = false
            when (result) {
                is ProtocolResult.Rejected -> reject(result.reason)
                is ProtocolResult.Value -> {
                    if(detailLoader==null){
                        discardDraft();confirmingMistaken=false;history=false;historical=null
                        loadDetail(result.value.entryId)
                        if(current())refresh()
                        if(current() && mode==ProtocolUiMode.ACTIVE)refreshWorkspace()
                        if(current())emit(message)
                    }else{
                        commitConfirmed=true
                        loadDetail(result.value.entryId,discardAfter=true,projectionFeedback=emit){
                            commitConfirmed=false;history=false
                            if(current())emit(message)
                        }
                    }
                }
            }
        }
    }
}
internal fun protocolValidation(input: ProtocolInput): String? {
    if (ProtocolRules.valid(input)) return null
    val i = ProtocolRules.normalized(input)
    fun length(s: String?) = s?.let { Character.codePointCount(it, 0, it.length) } ?: 0
    return when {
        i.title.isEmpty() || length(i.title) > 120 -> "Bitte einen Titel mit 1–120 Zeichen eingeben."
        length(i.description) > 2000 -> "Beschreibung: maximal 2000 Zeichen."
        length(i.placeLabel) > 80 -> "Ort: maximal 80 Zeichen."
        length(i.announcementText) > 2000 -> "Ansage: maximal 2000 Zeichen."
        (i.announcementText == null) != (i.announcementKind == null) -> "Bitte Ansagetext eingeben oder die Ansage entfernen."
        length(i.statedLegalBasis) > 1000 -> "Begründung / Rechtsgrundlage laut Polizei: maximal 1000 Zeichen."
        i.officialIdentifiers.size > 32 -> "Maximal 32 Kennungen."
        i.officialIdentifiers.any { it.value.isEmpty() || length(it.value) > 120 } -> "Kennungen: 1–120 Zeichen je Wert."
        else -> "Eingaben prüfen."
    }
}
internal fun protocolFailure(reason: ProtocolFailure) = when (reason) {
    ProtocolFailure.NOT_FOUND -> "Der Protokolleintrag ist nicht mehr verfügbar."
    ProtocolFailure.WRONG_ACTION -> "Der Eintrag ist in dieser Aktion nicht verfügbar."
    ProtocolFailure.INVALID_STATE -> "In diesem Aktionsstatus ist diese Änderung nicht möglich."
    ProtocolFailure.INVALID -> "Eingaben prüfen."
    ProtocolFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    ProtocolFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    ProtocolFailure.STALE -> "Der Eintrag oder Aktionsstatus hat sich inzwischen geändert. Die Eingaben wurden nicht gespeichert."
}

private fun WorkspaceFailure.protocolFailure():ProtocolFailure=when(this){
    WorkspaceFailure.BUDGET_REFUSED->ProtocolFailure.BUDGET_REFUSED
    WorkspaceFailure.OWNER_UNCONFIRMED->ProtocolFailure.OWNER_UNCONFIRMED
    WorkspaceFailure.STALE->ProtocolFailure.STALE
    else->ProtocolFailure.NOT_FOUND
}
