package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.pag.domain.*

/** Navigation is ephemeral UI state, never session authority. */
enum class ActiveArea(val label: String) { HUB("Lagebild"), PERSONS("Personen"), TASKS("Aufgaben"), FUNCTIONS("Funktionen"), PROTOCOL("Protokoll"), MATERIAL("Material"), COSTS("Kosten"), MEMORY_REPORTS("Gedächtnisprotokolle"), DETAILS("Aktionsdetails") }
internal fun activeWorkspaceShownArea(model:ProductViewModel):ActiveArea = when {
    model.compactActiveMode -> model.activeArea
    model.operations.screen in setOf(OperationsScreen.LIVE,OperationsScreen.FUNCTION,OperationsScreen.PERSON) || model.selectedPerson!=null -> ActiveArea.PERSONS
    model.operations.screen in setOf(OperationsScreen.ROLES,OperationsScreen.FUNCTION_DETAIL,OperationsScreen.NEW_FUNCTION) -> ActiveArea.FUNCTIONS
    model.protocol.nested -> ActiveArea.PROTOCOL
    model.material.form || model.material.confirmingDelete -> ActiveArea.MATERIAL
    model.expenses.form || model.expenses.confirmingDelete -> ActiveArea.COSTS
    model.tasks.form || model.tasks.selected!=null -> ActiveArea.TASKS
    else -> model.activeArea
}
internal data class HubCard(val area: ActiveArea, val summary: List<String>, val attention: String? = null, val documents: Boolean = false) {
    val label get() = if (documents) "Dokumente" else area.label
}
internal fun activeHubCards(action: Action, board: OperationalBoard, tasks: TaskBoard, protocol: ProtocolSummary? = null, material: MaterialSummary? = null, expenses: ExpenseSummary? = null, memory: MemoryReportSummary? = null, documents: DocumentSummary? = null): List<HubCard> {
    val missing = board.persons.count { it.state?.status == null || it.state?.status == OperationalStatus.NO_STATUS }
    val custody = board.count(OperationalStatus.IN_CUSTODY)
    val unreachable = board.count(OperationalStatus.NOT_REACHABLE)
    val people = if (board.persons.isEmpty()) listOf("Noch keine Personen") else buildList {
        add("${board.persons.size} insgesamt")
        listOf(board.count(OperationalStatus.ON_SITE) to "vor Ort", custody to "in Gewahrsam",
            unreachable to "nicht erreichbar", missing to "ohne Lage").forEach { (count, label) -> if (count > 0) add("$count $label") }
    }
    val attention = when {
        custody > 0 -> "$custody " + (if (custody == 1) "Person" else "Personen") + " in Gewahrsam"
        unreachable > 0 -> "$unreachable " + (if (unreachable == 1) "Person" else "Personen") + " nicht erreichbar"
        missing > 0 -> "$missing " + (if (missing == 1) "Person" else "Personen") + " ohne aktuelle Lage"
        else -> null
    }
    val activeTasks = tasks.active
    val open = activeTasks.count { it.status == TaskStatus.OPEN }
    val progress = activeTasks.count { it.status == TaskStatus.IN_PROGRESS }
    val high = activeTasks.count { it.priority == TaskPriority.HIGH }
    val taskSummary = if (activeTasks.isEmpty()) listOf("Keine offenen Aufgaben") else buildList {
        add("$open offen"); if (progress > 0) add("$progress in Arbeit"); if (high > 0) add("$high hohe Priorität")
    }
    val functions = board.roles.mapNotNull { role -> board.roleCount(role.id).takeIf { it > 0 }?.let { "$it ${role.name}" } }
    val functionSummary = if (functions.isEmpty()) listOf("Noch keine aktuellen Funktionen") else
        functions.take(3) + if (functions.size > 3) listOf("${functions.size - 3} weitere Funktionen") else emptyList()
    return listOf(HubCard(ActiveArea.PERSONS, people, attention),
        HubCard(ActiveArea.TASKS, taskSummary, if (high > 0) "$high " + (if (high == 1) "Aufgabe" else "Aufgaben") + " mit hoher Priorität" else null),
        HubCard(ActiveArea.FUNCTIONS, functionSummary)) +
        (protocol?.let { listOf(HubCard(ActiveArea.PROTOCOL, listOf("${it.keyEventCount} Schlüsselereignisse", it.lastEntryRecordedAt?.let { time -> "Letzter Eintrag erfasst: ${protocolSummaryTime(time)}" } ?: "Noch kein Eintrag"))) } ?: emptyList()) +
        (material?.let { listOf(HubCard(ActiveArea.MATERIAL, listOf(if (it.count == 0) "Noch kein Material" else "${it.count} Materialeinträge"))) } ?: emptyList()) +
        (expenses?.let { listOf(HubCard(ActiveArea.COSTS, listOf("${it.count} Einträge", "Gesamt: ${EuroMoney.format(it.totalCents)}"))) } ?: emptyList()) +
        (memory?.let { listOf(HubCard(ActiveArea.MEMORY_REPORTS, listOf(if (it.draftCount == 0 && it.completedCount == 0) "Noch keine Gedächtnisprotokolle" else "${it.draftCount} Entwürfe · ${it.completedCount} abgeschlossen"))) } ?: emptyList()) +
        (documents?.let { listOf(HubCard(ActiveArea.HUB, listOf("${it.count} Dokumente"), documents=true)) } ?: emptyList()) + listOf(HubCard(ActiveArea.DETAILS,
            listOf("Beginn: ${formattedTime(action.startAt)}", "Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}", "Aktiv")))
}
internal fun activeHubColumns(widthDp: Float, fontScale: Float) = if (widthDp >= 600f && fontScale <= 1.3f) 2 else 1

@Composable internal fun ActiveWorkspace(model: ProductViewModel, renderContent:Boolean=true, contentFrames:CompactContentFrameObserver?=null) {
    ActionWorkspaceHeader(model)
    if (model.activeArea != ActiveArea.HUB || model.operations.screen != OperationsScreen.OVERVIEW ||
        model.selectedPerson != null || model.tasks.form || model.tasks.selected != null) {
        FormOutlinedButton({ model.activeArea(ActiveArea.HUB) }, enabled = model.activeNavigationEnabled) { Text("‹ Zum Lagebild") }
    }
    val shownArea = activeWorkspaceShownArea(model)
    if(!model.compactActiveMode) PreparationHeading(shownArea.label)
    if (model.protocol.nested || model.operations.screen != OperationsScreen.OVERVIEW || model.selectedPerson != null || model.tasks.form || model.tasks.selected != null) {
        FormOutlinedButton({
            when {
                model.protocol.nested -> model.protocol.back()
                model.operations.screen == OperationsScreen.PERSON && (model.selectedPerson != null || model.personDetailLoading) -> model.backToPersons()
                model.operations.screen == OperationsScreen.PERSON -> model.operations.backToList()
                model.operations.screen in setOf(OperationsScreen.LIVE, OperationsScreen.FUNCTION) -> model.operations.cancel()
                model.operations.screen in setOf(OperationsScreen.FUNCTION_DETAIL, OperationsScreen.NEW_FUNCTION) -> model.operations.backToFunctions()
                model.tasks.form -> model.tasks.cancel()
                model.tasks.selected != null -> model.tasks.backToTasks()
                else -> model.activeArea(shownArea)
            }
        }, enabled = model.activeNavigationEnabled) { Text(if (model.protocol.nested) (if (model.protocol.historical != null || (model.protocol.form && model.protocol.expected != null)) "‹ Zum aktuellen Eintrag" else "‹ Protokoll") else if ((model.selectedPerson != null || model.personDetailLoading) && model.operations.screen == OperationsScreen.PERSON) "‹ Zur Person" else if (model.operations.screen in setOf(OperationsScreen.LIVE, OperationsScreen.FUNCTION) && model.operations.hasPersonFocus) "‹ Zur Person" else if (model.operations.screen == OperationsScreen.PERSON && model.operations.functionFocus != null) "‹ ${activeFunctionName(board = model.operations.board, roleId = model.operations.functionFocus!!.roleId)}" else if (model.tasks.form && model.tasks.selected != null) "‹ Zur Aufgabe" else "‹ ${shownArea.label}") }
    }
    if (model.activeArea == ActiveArea.DETAILS) {
        val action = model.selected ?: return
        Text(action.description)
        Text("Beginn: ${formattedTime(action.startAt)}")
        Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
        return
    }
    if(!renderContent)return
    if(model.compactActiveMode) {
        if(model.activeArea==ActiveArea.HUB) {
            val hub=model.compactHubPresentation
            if(hub==null) {if(model.workspaceError)PagErrorState() else PagLoadingState();return}
            hub.compositionObserved()
            DisposableEffect(hub) { onDispose {hub.compositionDisposed()} }
            key(model.activeArea) { CompactActiveContent(hub,model.activeNavigationEnabled,contentFrames) }
        } else if(model.activeArea==ActiveArea.PERSONS) {
            val persons=model.compactPersonsPresentation
            if(persons==null) {if(model.workspaceError)PagErrorState() else PagLoadingState();return}
            persons.compositionObserved()
            DisposableEffect(persons) { onDispose {persons.compositionDisposed()} }
            key(model.activeArea) { CompactActiveContent(persons,contentFrames) }
        } else if(model.activeArea==ActiveArea.TASKS) {
            val tasks=model.compactTasksPresentation
            if(tasks==null) {if(model.workspaceError)PagErrorState() else PagLoadingState();return}
            tasks.compositionObserved()
            DisposableEffect(tasks) { onDispose {tasks.compositionDisposed()} }
            key(model.activeArea) { CompactActiveContent(tasks,contentFrames) }
        } else if(model.activeArea==ActiveArea.FUNCTIONS) {
            val functions=model.compactFunctionsPresentation
            if(functions==null) {if(model.workspaceError)PagErrorState() else PagLoadingState();return}
            functions.compositionObserved()
            DisposableEffect(functions) { onDispose {functions.compositionDisposed()} }
            key(model.activeArea) { CompactActiveContent(functions,contentFrames) }
        }
        FormOutlinedButton(model::useFullActiveView,enabled=model.activeNavigationEnabled) { Text("Alle anzeigen und bearbeiten") }
        if(model.activeArea==ActiveArea.HUB && model.documentsAvailable)
            FormOutlinedButton(model::openDocuments,enabled=model.activeNavigationEnabled) { Text("Dokumente öffnen") }
        return
    }
    if (model.personDetailLoading) { PagLoadingState(); return }
    val content = model.workspace
    if (content == null && model.workspaceRefusal in setOf(WorkspaceFailure.OWNER_UNCONFIRMED,WorkspaceFailure.BUDGET_REFUSED)) {
        return
    }
    if (content == null) { if (model.workspaceError) PagErrorState() else PagLoadingState(); return }
    if (model.workspaceError) Text("Die Aktualisierung ist fehlgeschlagen. Die bisherige Ansicht bleibt erhalten.")
    val board = content.operations
    if (board == null) { PagErrorState(); return }
    when {
        model.selectedPerson != null && model.operations.screen == OperationsScreen.PERSON -> PersonsSection(model)
        model.operations.screen != OperationsScreen.OVERVIEW -> OperationsSection(model)
        model.selectedPerson != null -> PersonsSection(model)
        model.tasks.form || model.tasks.selected != null -> TaskSection(model.tasks, board)
        else -> when (model.activeArea) {
            ActiveArea.HUB -> {
                val tasks = content.tasks
                if (tasks == null) { PagErrorState(); return }
                val cards = content.uiRows?.hub?:return
                ActiveHubGrid(cards, model.activeNavigationEnabled, onDocuments = model::openDocuments) { model.activeArea(it) }
                model.changeError?.let { Text(changeErrorLabel(it)) }
                if (model.confirming != null) {
                    Text("Aktion endgültig abschließen? Die Aktion bleibt abgeschlossen. Protokoll-Nachbereitung, Kostenverwaltung und Gedächtnisprotokolle bleiben möglich.")
                    FormButton(model::confirmAdvance, enabled = !model.saving) { Text("Bestätigen") }
                    FormOutlinedButton(model::cancelAdvance, enabled = !model.saving) { Text("Abbrechen") }
                } else FormOutlinedButton(model::requestAdvance, enabled = model.activeNavigationEnabled) { Text("Aktion abschließen") }
            }
            ActiveArea.PERSONS -> OperationalPersons(model, board)
            ActiveArea.TASKS -> if (content.tasks == null) PagErrorState() else TaskSection(model.tasks, board)
            ActiveArea.FUNCTIONS -> ActiveFunctionsOverview(model, board)
            ActiveArea.PROTOCOL -> ProtocolSection(model.protocol, content.persons)
            ActiveArea.MATERIAL -> MaterialSection(model.material, content.persons, content.tasks?.tasks.orEmpty())
            ActiveArea.COSTS -> ExpenseSection(model.expenses, content.persons)
            ActiveArea.MEMORY_REPORTS -> MemoryReportSection(model.memoryReports, content.persons,showHeading=false)
            ActiveArea.DETAILS -> Unit
        }
    }
}

internal fun compactPageLabel(shown:Int,total:Long,hasMore:Boolean):String =
    "$shown von $total angezeigt" + if(hasMore) " · weitere Einträge verfügbar" else ""

/** Complete pre-admitted catalogue, index access only. No list/core loan or new paging. */
@Composable private fun CompactActiveContent(functions:CompactFunctionsPresentation,observer:CompactContentFrameObserver?=null) {
    val size=functions.size() ?: return
    val onSite=functions.onSite() ?: return
    val without=functions.withoutFunction() ?: return
    Column(compactFrameModifier(ActiveArea.FUNCTIONS,observer),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PreparationHeading(ActiveArea.FUNCTIONS.label)
        Text("$onSite Personen vor Ort · $without ohne Funktion")
        if(size==0)Text("Noch keine aktuellen Funktionen")
        // Independent publications, not an atomic whole-frame snapshot.
        repeat(size){index->functions.line(index)?.let{Text(it)}}
    }
}

internal fun compactHubSummary(index:Int,line:Int,value:Long):String=when(index) {
    0->if(line==0)"$value insgesamt" else "$value vor Ort"
    1->if(line==0)"$value Funktionen" else "$value Personen vor Ort"
    else->if(line==0)"$value offen" else "$value in Arbeit"
}

/** Indexed layout: one original metadata card list, no chunked/copied card lists. */
@Composable private fun CompactActiveContent(hub:CompactHubPresentation,enabled:Boolean,observer:CompactContentFrameObserver?=null) {
    Column(compactFrameModifier(ActiveArea.HUB,observer),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PreparationHeading(ActiveArea.HUB.label)
        val fontScale=LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=activeHubColumns(maxWidth.value,fontScale)
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                for(row in 0 until (hub.size()+columns-1)/columns) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        for(index in row*columns until minOf((row+1)*columns,hub.size())) {
                            val card=hub.card(index) ?: continue
                            val first=hub.value(index,0) ?: continue
                            val second=hub.value(index,1) ?: continue
                            val click=hub.navigation(card.area) ?: continue
                            FormOutlinedButton(click,enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=96.dp)) {
                                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    PreparationHeading(card.label)
                                    Text(compactHubSummary(index,0,first));Text(compactHubSummary(index,1,second))
                                    Text("${card.label} öffnen")
                                }
                            }
                        }
                    }
                }
            }
        }
        // Existing auxiliary routes; same original callbacks, no per-render owner capture.
        for(area in ActiveArea.entries) {
            if(area==ActiveArea.PROTOCOL || area==ActiveArea.MATERIAL || area==ActiveArea.COSTS || area==ActiveArea.MEMORY_REPORTS || area==ActiveArea.DETAILS) {
                val click=hub.navigation(area) ?: continue
                FormOutlinedButton(click,enabled=enabled){Text(area.label)}
            }
        }
    }
}

/** PERSONS receives only an identity handle; no full core/page/list can enter this subtree. */
@Composable private fun CompactActiveContent(persons:CompactPersonsPresentation,observer:CompactContentFrameObserver?=null) {
    val shown=persons.shown() ?: return
    val total=persons.total() ?: return
    val more=persons.hasMore() ?: return
    Column(compactFrameModifier(ActiveArea.PERSONS,observer),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PreparationHeading(ActiveArea.PERSONS.label)
        Text(compactPageLabel(shown,total,more))
        OperationalStatus.entries.forEach { status ->
            val count=persons.count(status) ?: return@forEach
            Text("${operationalLabel(status)}: $count")
        }
        repeat(shown) { index -> persons.pseudonym(index)?.let { Text(it) } }
    }
}

/** TASKS exports only bounded scalar values through its original slot identity. */
@Composable private fun CompactActiveContent(tasks:CompactTasksPresentation,observer:CompactContentFrameObserver?=null) {
    val shown=tasks.shown() ?: return
    val active=tasks.activeCount() ?: return
    val more=tasks.hasMore() ?: return
    val open=tasks.count(TaskStatus.OPEN) ?: return
    val progress=tasks.count(TaskStatus.IN_PROGRESS) ?: return
    val done=tasks.count(TaskStatus.DONE) ?: return
    val cancelled=tasks.count(TaskStatus.CANCELLED) ?: return
    Column(compactFrameModifier(ActiveArea.TASKS,observer),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PreparationHeading(ActiveArea.TASKS.label)
        Text("$open offen · $progress in Arbeit · $done abgeschlossen · $cancelled abgebrochen")
        Text(compactPageLabel(shown,active,more))
        // Scalar accesses are independently authorized, never an atomic whole-frame receipt.
        repeat(shown) { index -> tasks.title(index)?.let { Text(it) } }
    }
}

@Composable private fun compactFrameModifier(area:ActiveArea,observer:CompactContentFrameObserver?):Modifier {
    // Capture once for this area subtree. An old subtree cannot acquire a later navigation ID.
    val trace=remember(observer,area) { observer?.capture(area)?.also { it.composing(System.nanoTime()) } }
    if(trace!=null) SideEffect { trace.committed(System.nanoTime()) }
    return if(trace==null) Modifier else Modifier
        .onGloballyPositioned { if(it.isAttached && it.size.width>0 && it.size.height>0) trace.laidOut(System.nanoTime()) }
        .drawWithContent {
            trace.drawStarted(System.nanoTime())
            drawContent()
            trace.drawn(System.nanoTime())
        }
}

/** Shared stateless hub presentation; no session, repository or demo capability. */
@Composable internal fun ActiveHubGrid(cards: List<HubCard>, enabled: Boolean = true, onDocuments: () -> Unit = {}, onOpen: (ActiveArea) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = activeHubColumns(maxWidth.value, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        FormOutlinedButton({ if (card.documents) onDocuments() else onOpen(card.area) }, enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 96.dp)) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PreparationHeading(card.label)
                                card.summary.forEach { Text(it) }
                                card.attention?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                Text("${card.label} öffnen")
                            }
                        }
                    }
                }
            }
        }
    }
}
