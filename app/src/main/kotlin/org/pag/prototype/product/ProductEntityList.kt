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

// Immutable CODE enum groups; no action payload, authority or per-render Set.
private val supportedActiveAreaGroup1=setOf(ActiveArea.PERSONS,ActiveArea.TASKS)
private val supportedPreparationAreaGroup2=setOf(PreparationArea.PERSONS,PreparationArea.TASKS)


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
    ((m.selected?.status==ActionStatus.ACTIVE && !m.compactActiveMode && (m.activeArea in supportedActiveAreaGroup1 || (m.boundedSurfaceVisible && m.activeArea==ActiveArea.FUNCTIONS))) ||
     (m.selected?.status==ActionStatus.PREPARATION && m.preparationArea in supportedPreparationAreaGroup2))

/** Null outside isolated probes; observation carries UI scroll state only. */
internal val LocalProductListObservation=staticCompositionLocalOf<((String,LazyListState?)->Unit)?>{null}

internal fun entityRowKey(kind:String,id:String)="$kind:$id"
internal val listUiCost=ActiveWorkspaceBudget.Units(objects=64,strings=4,utf16Units=256,lists=4,listCapacity=32,references=256)
// Identical complete builder + holder/sizing cost; one original reservation, no refund.
internal val boundedListBuilderCost=ActiveWorkspaceBudget.Units(objects=70,strings=4,utf16Units=256,lists=4,listCapacity=32,references=280)

/** Lazy callbacks hold the VM service, original identity and scalar metadata only. Each
 * actual builder construction is admitted before construction; remember is an allocation
 * optimization, never an end receipt. Revoked builders cannot resolve another payload. */
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun ProductEntityList(model:ProductViewModel,positions:ProductScrollPositions,frames:CompactContentFrameObserver?=null) {
    if(model.boundedPage || LocalActiveChrome.current){BoundedEntityList(model,positions,frames);return}
    val active=model.selected?.status==ActionStatus.ACTIVE
    val persons=if(active)model.activeArea==ActiveArea.PERSONS else model.preparationArea==PreparationArea.PERSONS
    val source=model.workspace
    val root=source?.uiRows?.identity
    if(root==null || source.operations==null || (!persons && source.tasks==null)){
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            ProductListHeader(model,active)
            if(model.workspaceError)PagErrorState() else PagLoadingState()
        };return
    }
    val destination=remember(root,persons,active){if(model.reserveListUi(root))productNavigationKey(model) else null}?:return
    val anchor=positions.at(destination)
    val state=remember(destination){LazyListState(anchor.index,anchor.offset)}
    val observer=LocalProductListObservation.current
    if(observer!=null){SideEffect{observer(destination,state)};DisposableEffect(state){onDispose{observer(destination,null)}}}
    DisposableEffect(state){onDispose{anchor.index=state.firstVisibleItemIndex;anchor.offset=state.firstVisibleItemScrollOffset}}
    val now=peopleDisplayTime()/60_000*60_000
    val zone=source.uiRows!!.zone
    val projection=if(!persons)source.tasks?.presentation?.forPerson(model.tasks.filter) else null
    val count=if(persons)source.operations.persons.size else projection?.active?.size?:0
    val closedCount=projection?.closed?.size?:0
    var templates by remember(destination){mutableStateOf(false)}
    val expanded=model.tasks.closedExpanded
    // No DTO, source, board or projection is captured by the retained Lazy DSL closure.
    val renderKey=now/60_000*8+(if(persons)4 else 0)+(if(expanded)2 else 0)+(if(templates)1 else 0)
    val filter=model.tasks.filter // Immutable view identity; never resolve old indexes through a newer filter.
    val builder=rememberOriginalValue(source.uiRows!!.listFence,renderKey,extra=filter,cost={listUiCost}){
            val build:LazyListScope.()->Unit = {
                if(model.liveUi(root)){
                    item(key="header",contentType="header"){if(model.liveUi(root))ProductListHeader(model,active)}
                    item(key="summary",contentType="summary"){
                        if(model.liveUi(root))ProductListSummary(model,active,persons)
                    }
                    items(count=count,key={index->
                        if(persons)model.listPerson(root,index)?.let{model.personRow(it)?.key}?:index
                        else model.listTask(root,index,false,filter)?.let{model.taskRow(it)?.key}?:index
                    },contentType={if(persons)"person" else "task"}){index->
                        if(persons){
                            val person=model.listPerson(root,index)
                            val board=if(person!=null)model.workspace?.operations else null
                            if(person!=null && board!=null){
                                if(active)ActivePersonListRow(model,board,person,now,zone)
                                else PreparationPersonRow(model,person,board)
                            }
                        }else ProductTaskRow(model,root,index,false,active,now,zone,filter)
                    }
                    if(!persons && (closedCount>0 || !active))item(key="closed-toggle"){
                        if(model.liveUi(root))ProductClosedToggle(model,active)
                    }
                    if(!persons && expanded)items(count=closedCount,key={index->model.listTask(root,index,true,filter)?.let{model.taskRow(it)?.key}?:index},contentType={"task"}){index->
                        ProductTaskRow(model,root,index,true,active,now,zone,filter)
                    }
                    if(!active && !persons){
                        item(key="templates-toggle"){if(model.liveUi(root))FormTextButton({if(model.liveUi(root)){model.tasks.touch();templates=!templates}}){Text("Aufgaben aus Vorlagen")}}
                        if(templates){
                            item(key="templates-label"){if(model.liveUi(root))Text("Organisatorische Vorschläge · Die Leitung entscheidet selbst.")}
                            items(TaskRules.templates,key={it.key},contentType={"template"}){template->
                                if(model.liveUi(root))FormOutlinedButton({if(model.liveUi(root))model.tasks.add(template=template)},enabled=!model.tasks.busy){Text(template.title)}
                            }
                        }
                    }
                }
            }
            build
    }?:return
    LazyColumn(Modifier.fillMaxSize(),state=state,contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=builder)
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ProductListHeader(model:ProductViewModel,active:Boolean){
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        if(active)ActiveWorkspace(model,renderContent=false) else PreparationWorkspace(model,renderContent=false)
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun PreparationPersonRow(model:ProductViewModel,person:OperationalPerson,board:OperationalBoard){
    val row=model.personRow(person)?:return
    val name=row.card.name
    val plan=model.formatPlannedRoles(person,board)?:"Noch keine Rolle"
    FormOutlinedButton(row.detail,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.fillMaxWidth()){
            Text(name,style=MaterialTheme.typography.titleLarge)
            Text(plan)
        }
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ProductTaskRow(model:ProductViewModel,root:Any,index:Int,closed:Boolean,active:Boolean,now:Long,zone:ZoneId,filter:String?){
    val task=model.listTask(root,index,closed,filter)?:return
    val board=model.workspace?.operations?:return
    if(active)ActiveTaskListCard(task,board,now,zone,model.tasks)
    else {
        val row=model.taskRow(task)?:return
        FormOutlinedButton(row.click,modifier=Modifier.fillMaxWidth(),enabled=!model.tasks.busy){TaskSummary(row)}
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ProductListSummary(model:ProductViewModel,active:Boolean,persons:Boolean){
    val content=model.workspace?:return
    val board=content.operations?:return
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        if(model.workspaceError)Text("Die Aktualisierung ist fehlgeschlagen. Die bisherige Ansicht bleibt erhalten.")
        if(persons){
            if(active)activePeopleSummary(board).forOriginal{Text(it)} else {
                PreparationHeading("Personen");Text(content.uiRows?.personSummary?:return@Column)
                FormButton({model.addPerson()}){Text("Person hinzufügen")}
                if(content.persons.isEmpty())Text("Noch keine Personen angelegt.")
            }
            if(model.personError!=null)Text("Die Person ist nicht verfügbar. Bitte erneut aus der Übersicht öffnen.")
        }else{
            val tasks=content.tasks?:return@Column
            val list=activeTaskContent(tasks,model.tasks.filter)
            if(!active)PreparationHeading("Aufgaben")
            model.tasks.error?.let{Text(taskListError(it))}
            model.tasks.filter?.let{id->
                val row=model.personRow(id)
                Text((if(active)row?.activeFilter else row?.preparationFilter)?:"Person nicht verfügbar")
                FormOutlinedButton({model.tasks.showPerson(null)},enabled=!model.tasks.busy){Text(if(active)"Alle Aufgaben anzeigen" else "Alle Aufgaben")}
            }
            if(active)list.summary.forOriginal{Text(it)} else {
                Text("Dringende / offene Aufgaben",style=MaterialTheme.typography.titleMedium)
                Text(tasks.presentation.all.preparationSummary)
            }
            if(model.tasks.writable)FormButton({if(active)model.tasks.add(model.tasks.filter) else model.tasks.add()},enabled=!model.tasks.busy){Text("+ Aufgabe")}
            if(!active && list.active.isEmpty())Text(if(tasks.tasks.isEmpty())"Noch keine Aufgaben." else "Keine offenen Aufgaben.")
        }
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ProductClosedToggle(model:ProductViewModel,active:Boolean){
    val board=model.tasks.board?:return
    val list=if(active)activeTaskContent(board,model.tasks.filter) else board.presentation.all
    val expanded=model.tasks.closedExpanded
    val label=if(active){if(expanded)list.activeClosedExpanded else list.activeClosedCollapsed}
        else if(expanded)list.preparationClosedExpanded else list.preparationClosedCollapsed
    val description=if(expanded)"Ausgeklappt" else "Eingeklappt"
    FormOutlinedButton({model.tasks.toggleClosed()},enabled=!model.tasks.busy,modifier=Modifier.fillMaxWidth().originalSemantics {stateDescription=description}){Text(label)}
}

private fun taskListError(f:OperationsFailure)=when(f){
    OperationsFailure.INVALID->"Bitte Titel (1–120 Zeichen), Zuständigkeit und Fälligkeit prüfen."
    OperationsFailure.REVISION_EXHAUSTED->"Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    OperationsFailure.STALE ->"Die Aufgabe wurde inzwischen geändert. Bitte neu öffnen."
    OperationsFailure.NOT_ALLOWED->"Im aktuellen Aktionsstatus ist diese Änderung nicht möglich."
    else->"Die Aufgabe oder Zuordnung ist in dieser Aktion nicht verfügbar."
}

/** Original-owner card page. Builders borrow its admitted immutable display strings;
 * row callbacks retain only the VM endpoint, epoch and index, never a workspace. */
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun BoundedEntityList(model:ProductViewModel,positions:ProductScrollPositions,frames:CompactContentFrameObserver?){
    val area=model.activeArea
    val epoch=model.pageEpoch
    val fence=(model.inputScreenKey() as? OriginalUiFence)?:model.activeChromeFence()?:return
    val hub=area==ActiveArea.HUB
    val destination=remember(fence,area){if(fence.reserve(listUiCost))"bounded-${area.name}" else null}?:return
    val anchor=positions.at(destination)
    val state=remember(destination){LazyListState(anchor.index,anchor.offset)}
    DisposableEffect(state){onDispose{anchor.index=state.firstVisibleItemIndex;anchor.offset=state.firstVisibleItemScrollOffset}}
    val functions=if(area==ActiveArea.FUNCTIONS)model.compactFunctionsPresentation else null
    val lines=if(area==ActiveArea.FUNCTIONS)functions?.displayLines() else null
    if(area==ActiveArea.FUNCTIONS && lines==null){
        if(model.workspaceRefreshing || model.personsLoading)PagLoadingState() else PagErrorState()
        return
    }
    val count=lines?.size?:model.pageShown
    val builder=remember(fence,epoch){
        if(!fence.reserve(boundedListBuilderCost))null else {
        val labels=if(!hub && lines==null)model.boundedDisplayLabels(epoch) else null
        val content:LazyListScope.()->Unit={
            item(key="header"){if(fence.live())Column(verticalArrangement=Arrangement.spacedBy(16.dp)){ActiveWorkspace(model,renderContent=hub,contentFrames=frames)}}
            if(!hub){
            if(functions!=null)item(key="function-summary"){
                if(fence.live()){
                    val onSite=functions.onSite();val without=functions.withoutFunction()
                    if(onSite!=null && without!=null)Text("$onSite Personen vor Ort · $without ohne Funktion")
                }
            }
            if(model.workspaceError)item(key="error"){PagErrorState()}
            else if(model.workspaceRefreshing || model.personsLoading)item(key="loading"){PagLoadingState()}
            else if(count==0)item(key="empty"){
                Text("Keine weiteren Einträge auf dieser Seite.",modifier=compactFrameModifier(area,frames))
            }else items(count,key={"$epoch-$it"}){index->
                if(fence.live()){
                    val frame=compactFrameModifier(area,frames)
                    if(lines!=null)Text(lines[index],modifier=frame)
                    else labels?.getOrNull(index)?.let{label->
                        BoundedCard(label,model.activeNavigationEnabled,frame,admitted=model.originalRowControl(index)){model.openBoundedRow(epoch,index)}
                    }
                }
            }
            }
        }
        content
        }
    }?:return
    CompositionLocalProvider(LocalFormScreen provides fence){
        LazyColumn(Modifier.fillMaxSize(),state=state,contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=builder)
    }
}
