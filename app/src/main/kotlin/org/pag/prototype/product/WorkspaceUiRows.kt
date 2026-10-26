package org.pag.prototype.product

import org.pag.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections

internal enum class RowOperation { TASK, PERSON_DETAIL, PERSON_FOCUS, ROLE_EDIT, ROLE_DELETE }
internal interface RowActionPort {
    fun live(root:Any,access:WorkspaceAccess?):Boolean
    fun reserve(root:Any,access:WorkspaceAccess?,units:ActiveWorkspaceBudget.Units):Boolean
    fun invoke(root:Any,access:WorkspaceAccess?,operation:RowOperation,id:String)
}
/** Only original identity, existing authority metadata, ID and service endpoint. No row,
 * board, snapshot, presentation map, draft or UI payload in the callback. */
internal class OriginalRowAction(private val root:Any,private val access:WorkspaceAccess?,private val port:RowActionPort,
    private val operation:RowOperation,private val id:String):()->Unit,OriginalUiFence {
    override fun live()=port.live(root,access)
    override fun reserve(units:ActiveWorkspaceBudget.Units)=port.reserve(root,access,units)
    override fun invoke()=port.invoke(root,access,operation,id)
}
/** Code-format metadata built once AFTER planning admission, shared by all rows. No
 * per-row parser construction and no global payload/locale cache. */
internal class UiDateFormats(val zone:ZoneId){
    val locale:java.util.Locale=java.util.Locale.getDefault()
    val time=DateTimeFormatter.ofPattern("HH:mm",locale)
    val full=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm",locale)
    val day=DateTimeFormatter.ofPattern("dd.MM.",locale)
    val medium=DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM,java.time.format.FormatStyle.SHORT).withLocale(locale)
    val short=DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(locale)
}
private val taskCardStatuses=TaskPriority.entries.associateWith{p->TaskStatus.entries.associateWith{s->"${priorityLabel(p)} · ${taskStatusLabel(s)}"}} // code-only
internal class TaskUiDisplay(private val task:Task,people:OperationalBoard,private val zone:ZoneId,private val formats:UiDateFormats=UiDateFormats(zone)) {
    private val assigneePerson=people.personName(task.assigneePersonId)
    private val assigneeRole=people.roleName(task.assigneeRoleId)
    private val affectedPerson=people.personName(task.affectedPersonId)
    fun matches(value:Task,people:OperationalBoard,displayZone:ZoneId,locale:java.util.Locale=formats.locale)=task===value && zone==displayZone && formats.locale==locale &&
        assigneePerson===people.personName(value.assigneePersonId) &&
        assigneeRole===people.roleName(value.assigneeRoleId) &&
        affectedPerson===people.personName(value.affectedPersonId)
    private val dueDate=task.dueAt?.let{Instant.ofEpochMilli(it).atZone(zone)}
    private val dueDay=dueDate?.toLocalDate()
    private val start=dueDay?.atStartOfDay(zone)?.toInstant()?.epochSecond
    private val end=dueDay?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.epochSecond
    private val today=ActiveTaskCard(task.title,taskCardStatuses.getValue(task.priority).getValue(task.status),
        assigneePerson?:assigneeRole?:"Niemand zugeordnet",affectedPerson,dueDate?.format(formats.time))
    private val other=today.copy(due=dueDate?.format(formats.full))
    val key=entityRowKey("task",task.id)
    val semantics="Aufgabe ${task.title} öffnen"
    val revision="Revision: ${task.editRevision}"
    val materialChoiceLabel="${task.title} · ${taskStatusLabel(task.status)}"
    val heading="${priorityLabel(task.priority)} · ${task.title}"
    val assignee="Zuständig: ${today.assignee}"
    val affected=today.affected?.let{"Betrifft: $it"}
    val dueToday=today.due?.let{"Fällig: $it"}
    val dueOther=other.due?.let{"Fällig: $it"}
    val preparationAssignee=(task.assigneeRoleId?.let{id->people.roles.findOriginal{it.id==id}?.name}
        ?:task.assigneePersonId?.let{id->people.persons.findOriginal{it.person.id==id}?.person?.pseudonym}
        ?:"Niemand zugeordnet")+" · "+taskStatusLabel(task.status)
    val preparationAffected=task.affectedPersonId?.let{id->"Betrifft: ${people.persons.findOriginal{it.person.id==id}?.person?.pseudonym.orEmpty()}"}
    val preparationDue=dueDate?.let{"Fällig: ${it.format(formats.short)}"}
    private fun sameDay(now:Long):Boolean {val seconds=Math.floorDiv(now,1000L);return start!=null && end!=null && seconds>=start && seconds<end}
    fun card(now:Long)=if(sameDay(now))today else other
    fun due(now:Long)=if(sameDay(now))dueToday else dueOther
}
internal class TaskUiRow(val display:TaskUiDisplay,val click:OriginalRowAction){
    val key get()=display.key
    val semantics get()=display.semantics
    val revision get()=display.revision
    val heading get()=display.heading
    val assignee get()=display.assignee
    val affected get()=display.affected
    val preparationAssignee get()=display.preparationAssignee
    val preparationAffected get()=display.preparationAffected
    val preparationDue get()=display.preparationDue
    fun card(now:Long)=display.card(now)
    fun due(now:Long)=display.due(now)
}
private val minuteLabels=Array(60){if(it==0)"Gerade aktualisiert" else "Aktualisiert vor $it Min."} // code only
internal class PersonUiDisplay(private val person:OperationalPerson,board:OperationalBoard,tasks:TaskBoard?,private val zone:ZoneId,private val formats:UiDateFormats=UiDateFormats(zone)) {
    private val role=board.roleName(person.state?.currentRoleId)
    private val affectedCount=tasks?.affectedCount(person.person.id)
    fun matches(value:OperationalPerson,board:OperationalBoard,tasks:TaskBoard?,displayZone:ZoneId,locale:java.util.Locale=formats.locale)=
        person.person===value.person && person.state===value.state && zone==displayZone && formats.locale==locale &&
        role===board.roleName(value.state?.currentRoleId) && affectedCount==tasks?.affectedCount(value.person.id)
    val card=activePersonCard(person,board,0,zone)
    val key=entityRowKey("person",person.person.id)
    val activeFilter="Filter · Betrifft: ${card.name}"
    val preparationFilter="Betrifft: ${card.name}"
    val semantics="${card.name} öffnen"
    val situation="Aktuelle Lage: ${card.situation}"
    val function="Aktuelle Funktion: ${card.function}"
    val affected=activeAffectedTaskLabel(tasks,person.person.id)
    private val time=person.state?.takeIf{it.status!=OperationalStatus.NO_STATUS && it.updatedAt>0}?.updatedAt
    private val date=time?.let{Instant.ofEpochMilli(it).atZone(zone)}
    private val start=date?.toLocalDate()?.atStartOfDay(zone)?.toInstant()?.epochSecond
    private val end=date?.toLocalDate()?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.epochSecond
    private val clock=date?.format(formats.time)
    private val today=date?.let{"Zuletzt aktualisiert um $clock"}
    private val other=date?.let{"Zuletzt aktualisiert am ${it.format(formats.day)} um $clock"}
    fun updated(now:Long):String? {
        val at=time?:return null
        if(now<at)return null
        val seconds=Math.floorDiv(now,1000L)
        if(start==null || end==null || seconds<start || seconds>=end)return other
        val minutes=(now-at)/60_000
        return if(minutes<60)minuteLabels[minutes.toInt()] else today
    }
}
internal class PersonUiRow(val display:PersonUiDisplay,val detail:OriginalRowAction,val focus:OriginalRowAction){
    val card get()=display.card
    val key get()=display.key
    val activeFilter get()=display.activeFilter
    val preparationFilter get()=display.preparationFilter
    val semantics get()=display.semantics
    val situation get()=display.situation
    val function get()=display.function
    val affected get()=display.affected
    fun updated(now:Long)=display.updated(now)
}
private data class HubUiSource(val operations:Any,val open:Int,val progress:Int,val high:Int,
    val protocol:ProtocolSummary?,val material:MaterialSummary?,val expenses:ExpenseSummary?,val memory:MemoryReportSummary?,val documents:DocumentSummary?,
    val start:Long,val end:Long,val zone:ZoneId,val locale:java.util.Locale){
    companion object{
        fun from(source:WorkspaceSnapshot,action:Action?,formats:UiDateFormats):HubUiSource? {
            if(source.status!=ActionStatus.ACTIVE || action==null || source.operations==null || source.tasks==null)return null
            val counts=source.tasks.presentation.all
            return HubUiSource(source.operations.presentation.identity,counts.open,counts.inProgress,counts.high,
                source.protocolSummary,source.materialSummary,source.expenseSummary,source.memoryReportSummary,source.documentSummary,
                action.startAt,action.expectedEndAt,formats.zone,formats.locale)
        }
    }
}
private class HubPayload { var value:List<HubCard>?=null }
internal class RoleUiRow(val edit:OriginalRowAction,val delete:OriginalRowAction,val backLabel:String)
/** A single pre-admitted immutable derived member of the ORIGINAL Snapshot. This is no
 * independent cache/owner/registry. All lookups borrow; no receipts/refunds are issued. */
internal class WorkspaceUiRows(source:WorkspaceSnapshot,private val port:RowActionPort,previous:WorkspaceUiRows?=null,action:Action?=null,formatContext:UiDateFormats?=null) {
    internal var inputScreen:ProductInputScreenKey?=null
    val identity=Any()
    private val access=source.access
    private val operations=source.operations
    private val taskSource=source.tasks
    internal fun live()=port.live(identity,access)
    internal fun reserve(units:ActiveWorkspaceBudget.Units)=port.reserve(identity,access,units)
    private fun action(operation:RowOperation,id:String)=OriginalRowAction(identity,access,port,operation,id)
    val listFence=OriginalRowAction(identity,access,port,RowOperation.TASK,"")
    internal val formats=formatContext?:UiDateFormats(ZoneId.systemDefault())
    val zone:ZoneId=formats.zone
    val header=action?.let{WorkspaceHeaderUi(it,source,formats)}
    val personSummary="${source.persons.size} insgesamt"+(source.operations?.persons?.count{it.plan.primaryRoleId==null}?.let{" · $it ohne geplante Hauptrolle"}?:"")
    private val taskDisplays:Map<String,TaskUiDisplay> =
        if(previous!=null && operations===previous.operations && taskSource===previous.taskSource && formats.zone==previous.formats.zone && formats.locale==previous.formats.locale)previous.taskDisplays
        else Collections.unmodifiableMap(buildMap {
            source.operations?.let{people->for(t in source.tasks?.tasks.orEmpty()){
                val old=previous?.taskDisplays?.get(t.id)
                put(t.id,if(old!=null && old.matches(t,people,zone,formats.locale))old else TaskUiDisplay(t,people,zone,formats))
            }}
        })
    private val personDisplays:Map<String,PersonUiDisplay> =
        if(previous!=null && operations===previous.operations && taskSource===previous.taskSource && formats.zone==previous.formats.zone && formats.locale==previous.formats.locale)previous.personDisplays
        else Collections.unmodifiableMap(buildMap {
            source.operations?.let{board->for(p in source.operations.persons){
                val old=previous?.personDisplays?.get(p.person.id)
                put(p.person.id,if(old!=null && old.matches(p,board,taskSource,zone,formats.locale))old else PersonUiDisplay(p,board,taskSource,zone,formats))
            }}
        })
    private val roleLabels:Map<String,String> = if(previous!=null && operations?.roles===previous.operations?.roles)previous.roleLabels else
        Collections.unmodifiableMap(buildMap{for(r in operations?.roles.orEmpty())put(r.id,"‹ ${r.name}")})
    val tasks=OriginalTaskRows(this)
    val persons=OriginalPersonRows(this)
    val roles=OriginalRoleRows(this)
    internal fun taskChoiceLabel(id:String):String?=if(live())taskDisplays[id]?.materialChoiceLabel else null
    internal fun createTaskRow(id:String):TaskUiRow?=taskDisplays[id]?.let{TaskUiRow(it,action(RowOperation.TASK,id))}
    internal fun createPersonRow(id:String):PersonUiRow?=personDisplays[id]?.let{PersonUiRow(it,action(RowOperation.PERSON_DETAIL,id),action(RowOperation.PERSON_FOCUS,id))}
    internal fun createRoleRow(id:String):RoleUiRow?=roleLabels[id]?.let{RoleUiRow(action(RowOperation.ROLE_EDIT,id),action(RowOperation.ROLE_DELETE,id),it)}
    private val hubSource=HubUiSource.from(source,action,formats)
    private val hubAction=action
    private val hubPayload:HubPayload=if(hubSource!=null && hubSource==previous?.hubSource)previous.hubPayload else HubPayload()
    val hub:List<HubCard>? get(){
        if(!live())return null
        hubPayload.value?.let{return it}
        if(hubSource==null || hubAction==null || operations==null || taskSource==null)return null
        if(!reserve(hubCost))return null
        val result=activeHubCards(hubAction,operations,taskSource,hubSource.protocol,hubSource.material,hubSource.expenses,hubSource.memory,hubSource.documents)
        if(!live())return null
        hubPayload.value=result;return result
    }
    companion object {
        fun planningCost(source:WorkspaceSnapshot,previous:WorkspaceUiRows?=null):ActiveWorkspaceBudget.Units {
            // Matching immutable source identities does not construct per-row planning
            // representations. A shared formatter is created only when it really changes.
            return if(previous==null)formatPlanning else incrementalPlanning
        }
        val incrementalPlanning=ActiveWorkspaceBudget.Units(objects=32,arrays=4,arrayPayloadBytes=1024,references=128)
        val formatPlanning=ActiveWorkspaceBudget.Units(objects=320,strings=64,utf16Units=4096,arrays=40,arrayPayloadBytes=6144,references=256)
        val hubCost=ActiveWorkspaceBudget.Units(objects=1024,strings=256,utf16Units=16384,arrays=32,arrayPayloadBytes=8192,lists=64,listCapacity=256,references=512)

        fun cost(source:WorkspaceSnapshot,previous:WorkspaceUiRows?,formats:UiDateFormats,action:Action?):ActiveWorkspaceBudget.Units {
            val p=source.operations?.persons?.size?.toLong()?:0;val t=source.tasks?.tasks?.size?.toLong()?:0
            val r=source.operations?.roles?.size?.toLong()?:0;val n=Math.addExact(Math.addExact(p,t),r)
            var chars=0L;var changed=0L
            val hubInput=HubUiSource.from(source,action,formats)
            val hub=false // Constructed and booked on first actual hub use, never on unrelated module refresh.
            val zone:ZoneId=formats.zone
            val same=previous!=null && source.operations===previous.operations && source.tasks===previous.taskSource && formats.zone==previous.formats.zone && formats.locale==previous.formats.locale
            val mapped=if(same)0L else Math.addExact(p,t)
            // No business text limit or 120-character assumption for borrowed role/place/name.
            for(v in source.operations?.persons.orEmpty()){
                val old=previous?.personDisplays?.get(v.person.id)
                if(old==null || !old.matches(v,source.operations!!,source.tasks,zone,formats.locale)){
                    changed=Math.addExact(changed,1)
                    chars=Math.addExact(chars,v.person.id.length.toLong())
                    chars=Math.addExact(chars,Math.addExact(v.person.pseudonym.length.toLong(),v.state?.currentPlaceLabel?.length?.toLong()?:0))
                    chars=Math.addExact(chars,source.operations?.roles?.findOriginal{it.id==v.state?.currentRoleId}?.name?.length?.toLong()?:0)
                }
            }
            for(v in source.operations?.roles.orEmpty())chars=Math.addExact(chars,v.name.length.toLong())
            for(v in source.tasks?.tasks.orEmpty()){
                val old=previous?.taskDisplays?.get(v.id)
                if(source.operations!=null && old!=null && old.matches(v,source.operations,zone,formats.locale))continue
                changed=Math.addExact(changed,1)
                chars=Math.addExact(chars,v.id.length.toLong())
                chars=Math.addExact(chars,v.title.length.toLong())
                val people=source.operations
                chars=Math.addExact(chars,people?.persons?.findOriginal{it.person.id==v.assigneePersonId}?.person?.pseudonym?.length?.toLong()?:0)
                chars=Math.addExact(chars,people?.persons?.findOriginal{it.person.id==v.affectedPersonId}?.person?.pseudonym?.length?.toLong()?:0)
                chars=Math.addExact(chars,people?.roles?.findOriginal{it.id==v.assigneeRoleId}?.name?.length?.toLong()?:0)
            }
            // Format parsers are already planned once; this adds date scratch, builders, due cards,
            // callbacks and map nodes/capacity. Additive in BOTH R8 and T16 before constructor.
            return ActiveWorkspaceBudget.Units(objects=Math.addExact(if(hub)1088 else 64,Math.addExact(Math.multiplyExact(changed,64),Math.multiplyExact(mapped,4))),
                strings=Math.addExact(if(hub)320 else 64,Math.addExact(Math.multiplyExact(changed,32),Math.multiplyExact(r,2))),
                utf16Units=Math.addExact(if(hub)18432 else 2048,Math.addExact(Math.multiplyExact(chars,16),Math.multiplyExact(changed,512))),
                lists=8,listCapacity=Math.addExact(32,Math.multiplyExact(mapped,4)),
                maps=6,mapCapacity=Math.addExact(16,Math.multiplyExact(mapped,2)),
                arrays=Math.addExact(8,Math.multiplyExact(changed,16)),arrayPayloadBytes=Math.addExact(1024,Math.multiplyExact(changed,2048)),references=Math.addExact(128,Math.multiplyExact(mapped,16)))
        }
    }
}

/** The only construction seam for the additional UI representation. Refusal runs no
 * producer. The original capability reacquires/checks actual authority around publication. */
internal inline fun <T:Any> admitOriginalUi(access:WorkspaceAccess,action:String,status:ActionStatus,generation:Long,
    units:ActiveWorkspaceBudget.Units,crossinline producer:()->T):T? {
    if(!access.publish(action,status,generation){} || !access.reserve(units))return null
    var value:T?=null
    val accepted=access.publish(action,status,generation){value=producer()}
    return if(accepted)value else null
}

private fun OperationalBoard.personName(id:String?):String? {
    if(id==null)return null
    var i=0;while(i<persons.size){val p=persons[i++].person;if(p.id==id)return p.pseudonym};return null
}
private fun OperationalBoard.roleName(id:String?):String? {
    if(id==null)return null
    var i=0;while(i<roles.size){val r=roles[i++];if(r.id==id)return r.name};return null
}
