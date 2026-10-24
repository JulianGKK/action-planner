package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import java.time.ZoneId

/** Only numeric UI anchors; no entity IDs, payload, SavedState or authorization. */
internal class ProductScrollPositions {
    class Anchor(var index:Int=0,var offset:Int=0,var pixel:Int=0)
    private val positions=mutableMapOf<String,Anchor>()
    fun at(destination:String):Anchor=positions.getOrPut(destination){if(positions.size>=64)positions.clear();Anchor()}
}
internal fun productEntityListRoute(m:ProductViewModel):Boolean =
    m.snapshot.state==SessionState.READY && !m.documents.available && !m.creating && m.editing==null &&
    !m.detailLoading && !m.personDetailLoading && m.selectedPerson==null && !m.personForm && !m.sensitiveSetup &&
    m.operations.screen==OperationsScreen.OVERVIEW && !m.tasks.form && m.tasks.selected==null &&
    ((m.selected?.status==ActionStatus.ACTIVE && !m.compactActiveMode && m.activeArea in setOf(ActiveArea.PERSONS,ActiveArea.TASKS)) ||
     (m.selected?.status==ActionStatus.PREPARATION && m.preparationArea in setOf(PreparationArea.PERSONS,PreparationArea.TASKS)))

/** Null outside isolated probes; observation carries UI scroll state only. */
internal val LocalProductListObservation=staticCompositionLocalOf<((String,LazyListState?)->Unit)?>{null}

internal fun entityRowKey(kind:String,id:String)="$kind:$id"

/** A bounded root scrolling container, never nested in verticalScroll.
 * Derived lists are the same display projections as before, unremembered and UI-scoped.
 * No workspace/result cache; dispose on loading, navigation, revoke or context change. */
@Composable internal fun ProductEntityList(model:ProductViewModel,positions:ProductScrollPositions) {
    val active=model.selected?.status==ActionStatus.ACTIVE
    val persons=if(active)model.activeArea==ActiveArea.PERSONS else model.preparationArea==PreparationArea.PERSONS
    @Composable fun header(){Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        if(active)ActiveWorkspace(model,renderContent=false) else PreparationWorkspace(model,renderContent=false)
    }}
    val content=model.workspace
    val board=content?.operations
    if(content==null || board==null || (!persons && content.tasks==null)) {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            header();if(model.workspaceError)PagErrorState() else PagLoadingState()
        }
        return
    }
    val destination=productNavigationKey(model)
    val anchor=positions.at(destination)
    // No rememberSaveable: only this current generation/action's display metadata survives a return.
    val state=remember(destination){LazyListState(anchor.index,anchor.offset)}
    val observer=LocalProductListObservation.current
    if(observer!=null){
        SideEffect{observer(destination,state)}
        DisposableEffect(state){onDispose{observer(destination,null)}}
    }
    DisposableEffect(state){onDispose{anchor.index=state.firstVisibleItemIndex;anchor.offset=state.firstVisibleItemScrollOffset}}
    LaunchedEffect(state){snapshotFlow{state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset}.collect{(index,offset)->anchor.index=index;anchor.offset=offset}}
    val now=if(persons && active)peopleDisplayTime() else System.currentTimeMillis()
    val zone=ZoneId.systemDefault()
    val tasks=content.tasks
    // Deliberately no remembered index/map or longer-lived payload projection.
    val taskContent=if(!persons && tasks!=null)activeTaskContent(tasks,model.tasks.filter) else null
    var templates by remember(destination){mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),state=state,contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item(key="header",contentType="header"){header()}
        if(model.workspaceError)item(key="refresh-error"){Text("Die Aktualisierung ist fehlgeschlagen. Die bisherige Ansicht bleibt erhalten.")}
        if(persons) {
            item(key="people-summary"){
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(active)activePeopleSummary(board).forEach{Text(it)} else {
                        PreparationHeading("Personen")
                        val summary=preparationSummary(content)
                        Text("${summary.persons} insgesamt"+(summary.withoutPrimaryRole?.let{" · $it ohne geplante Hauptrolle"}?:""))
                        FormButton(model::addPerson){Text("Person hinzufügen")}
                    }
                    if(!active && content.persons.isEmpty())Text("Noch keine Personen angelegt.")
                }
            }
            if(active)items(board.persons,key={entityRowKey("person",it.person.id)},contentType={"person"}) { person ->
                ActivePersonListRow(model,board,person,now,zone)
            } else items(content.persons,key={entityRowKey("person",it.id)},contentType={"person"}) { person ->
                val plan=board.persons.find{it.person.id==person.id}
                FormOutlinedButton({model.personDetail(person.id)},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(person.pseudonym,style=MaterialTheme.typography.titleLarge)
                        Text(plan?.let{plannedRoles(it,board)}?:"Noch keine Rolle")
                    }
                }
            }
            if(model.personError!=null)item(key="person-error"){Text("Die Person ist nicht verfügbar. Bitte erneut aus der Übersicht öffnen.")}
        } else if(taskContent!=null && tasks!=null) {
            item(key="task-summary"){
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(!active)PreparationHeading("Aufgaben")
                    model.tasks.error?.let{Text(taskListError(it))}
                    model.tasks.filter?.let{id->
                        Text((if(active)"Filter · Betrifft: " else "Betrifft: ")+(board.persons.find{it.person.id==id}?.person?.pseudonym?:"Person nicht verfügbar"))
                        FormOutlinedButton({model.tasks.showPerson(null)},enabled=!model.tasks.busy){Text(if(active)"Alle Aufgaben anzeigen" else "Alle Aufgaben")}
                    }
                    if(active)taskContent.summary.forEach{Text(it)} else {
                        Text("Dringende / offene Aufgaben",style=MaterialTheme.typography.titleMedium)
                        // Single per-composition projection, never inside a person/row loop.
                        val open=tasks.active
                        Text("Hoch offen / in Arbeit: ${open.count{it.priority==TaskPriority.HIGH}} · Offen: ${open.count{it.status==TaskStatus.OPEN}} · In Arbeit: ${open.count{it.status==TaskStatus.IN_PROGRESS}}")
                    }
                    if(model.tasks.writable)FormButton({if(active)model.tasks.add(model.tasks.filter) else model.tasks.add()},enabled=!model.tasks.busy){Text("+ Aufgabe")}
                    if(!active && taskContent.active.isEmpty())Text(if(tasks.tasks.isEmpty())"Noch keine Aufgaben." else "Keine offenen Aufgaben.")
                }
            }
            items(taskContent.active,key={entityRowKey("task",it.id)},contentType={"task"}){task->
                if(active)ActiveTaskListCard(task,board,now,zone,model.tasks) else FormOutlinedButton({model.tasks.open(task)},modifier=Modifier.fillMaxWidth(),enabled=!model.tasks.busy){TaskSummary(task,board)}
            }
            if(taskContent.closed.isNotEmpty() || !active)item(key="closed-toggle") {
                // Read Snapshot state in composition, not from a semantics observer that can
                // outlive this Lazy item's attachment. Preserve the same accessibility state.
                val closedStateDescription=if(model.tasks.closedExpanded)"Ausgeklappt" else "Eingeklappt"
                FormOutlinedButton(model.tasks::toggleClosed,enabled=!model.tasks.busy,modifier=Modifier.fillMaxWidth().semantics{stateDescription=closedStateDescription}) {
                    Text((if(active)"Erledigt / Entfallen" else "Erledigt / Entfällt")+" (${if(active)taskContent.closed.size else tasks.tasks.count{!TaskRules.active(it.status)}}) ${if(model.tasks.closedExpanded)"▴" else "▾"}")
                }
            }
            if(model.tasks.closedExpanded)items(taskContent.closed,key={entityRowKey("task",it.id)},contentType={"task"}){task->
                if(active)ActiveTaskListCard(task,board,now,zone,model.tasks) else FormOutlinedButton({model.tasks.open(task)},modifier=Modifier.fillMaxWidth()){TaskSummary(task,board)}
            }
            if(!active)item(key="templates-toggle"){FormTextButton({model.tasks.touch();templates=!templates}){Text("Aufgaben aus Vorlagen")}}
            if(!active && templates){
                item(key="templates-label"){Text("Organisatorische Vorschläge · Die Leitung entscheidet selbst.")}
                items(TaskRules.templates,key={"template:${it.key}"},contentType={"template"}){t->FormOutlinedButton({model.tasks.add(template=t)},enabled=!model.tasks.busy){Text(t.title)}}
            }
        }
    }
}

private fun taskListError(f:OperationsFailure)=when(f){
    OperationsFailure.INVALID->"Bitte Titel (1–120 Zeichen), Zuständigkeit und Fälligkeit prüfen."
    OperationsFailure.REVISION_EXHAUSTED->"Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.STALE ->"Die Aufgabe wurde inzwischen geändert. Bitte neu öffnen."
    OperationsFailure.NOT_ALLOWED->"Im aktuellen Aktionsstatus ist diese Änderung nicht möglich."
    else->"Die Aufgabe oder Zuordnung ist in dieser Aktion nicht verfügbar."
}
