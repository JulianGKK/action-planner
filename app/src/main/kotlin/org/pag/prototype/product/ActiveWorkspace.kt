package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.pag.domain.*

/** Navigation is ephemeral UI state, never session authority. */
enum class ActiveArea(val label: String) { HUB("Lagebild"), PERSONS("Personen"), TASKS("Aufgaben"), FUNCTIONS("Funktionen"), DETAILS("Aktionsdetails") }
internal data class HubCard(val area: ActiveArea, val summary: List<String>, val attention: String? = null)
internal fun activeHubCards(action: Action, board: OperationalBoard, tasks: TaskBoard): List<HubCard> {
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
    val open = tasks.active.count { it.status == TaskStatus.OPEN }
    val progress = tasks.active.count { it.status == TaskStatus.IN_PROGRESS }
    val high = tasks.active.count { it.priority == TaskPriority.HIGH }
    val taskSummary = if (tasks.active.isEmpty()) listOf("Keine offenen Aufgaben") else buildList {
        add("$open offen"); if (progress > 0) add("$progress in Arbeit"); if (high > 0) add("$high hohe Priorität")
    }
    val functions = board.roles.mapNotNull { role -> board.roleCount(role.id).takeIf { it > 0 }?.let { "$it ${role.name}" } }
    val functionSummary = if (functions.isEmpty()) listOf("Noch keine aktuellen Funktionen") else
        functions.take(3) + if (functions.size > 3) listOf("${functions.size - 3} weitere Funktionen") else emptyList()
    return listOf(HubCard(ActiveArea.PERSONS, people, attention),
        HubCard(ActiveArea.TASKS, taskSummary, if (high > 0) "$high " + (if (high == 1) "Aufgabe" else "Aufgaben") + " mit hoher Priorität" else null),
        HubCard(ActiveArea.FUNCTIONS, functionSummary), HubCard(ActiveArea.DETAILS,
            listOf("Beginn: ${formattedTime(action.startAt)}", "Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}", "Aktiv")))
}
internal fun activeHubColumns(widthDp: Float, fontScale: Float) = if (widthDp >= 600f && fontScale <= 1.3f) 2 else 1

@Composable internal fun ActiveWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    if (model.activeArea != ActiveArea.HUB || model.operations.screen != OperationsScreen.OVERVIEW ||
        model.selectedPerson != null || model.tasks.form || model.tasks.selected != null) {
        FormOutlinedButton({ model.activeArea(ActiveArea.HUB) }, enabled = model.activeNavigationEnabled) { Text("‹ Zum Lagebild") }
    }
    val shownArea = when {
        model.operations.screen in setOf(OperationsScreen.LIVE, OperationsScreen.FUNCTION, OperationsScreen.PERSON) || model.selectedPerson != null -> ActiveArea.PERSONS
        model.operations.screen == OperationsScreen.ROLES -> ActiveArea.FUNCTIONS
        model.tasks.form || model.tasks.selected != null -> ActiveArea.TASKS
        else -> model.activeArea
    }
    PreparationHeading(shownArea.label)
    if (model.operations.screen != OperationsScreen.OVERVIEW || model.selectedPerson != null || model.tasks.form || model.tasks.selected != null) {
        FormOutlinedButton({
            when {
                model.operations.screen == OperationsScreen.PERSON && (model.selectedPerson != null || model.personDetailLoading) -> model.backToPersons()
                model.operations.screen == OperationsScreen.PERSON -> model.operations.backToList()
                model.operations.screen in setOf(OperationsScreen.LIVE, OperationsScreen.FUNCTION) -> model.operations.cancel()
                else -> model.activeArea(shownArea)
            }
        }, enabled = model.activeNavigationEnabled) { Text(if ((model.selectedPerson != null || model.personDetailLoading) && model.operations.screen == OperationsScreen.PERSON) "‹ Zur Person" else "‹ ${shownArea.label}") }
    }
    if (model.activeArea == ActiveArea.DETAILS) {
        val action = model.selected ?: return
        Text(action.description)
        Text("Beginn: ${formattedTime(action.startAt)}")
        Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
        return
    }
    if (model.personDetailLoading) { PagLoadingState(); return }
    val content = model.workspace
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
                val cards = activeHubCards(model.selected ?: return, board, tasks)
                ActiveHubGrid(cards, model.activeNavigationEnabled) { model.activeArea(it) }
                model.changeError?.let { Text(changeErrorLabel(it)) }
                if (model.confirming != null) {
                    Text("Aktion endgültig abschließen? Danach sind Bearbeitung und Wiedereröffnung ausgeschlossen.")
                    FormButton(model::confirmAdvance, enabled = !model.saving) { Text("Bestätigen") }
                    FormOutlinedButton(model::cancelAdvance, enabled = !model.saving) { Text("Abbrechen") }
                } else FormOutlinedButton(model::requestAdvance, enabled = model.activeNavigationEnabled) { Text("Aktion abschließen") }
            }
            ActiveArea.PERSONS -> OperationalPersons(model, board)
            ActiveArea.TASKS -> if (content.tasks == null) PagErrorState() else TaskSection(model.tasks, board)
            ActiveArea.FUNCTIONS -> {
                PreparationHeading("Aktuelle Funktionen · Vor Ort")
                if (board.roles.none { board.roleCount(it.id) > 0 }) Text("Noch keine aktuellen Funktionen")
                board.roles.forEach { Text("${it.name}: ${board.roleCount(it.id)}") }
                Text("Ohne Funktion vor Ort: ${board.roleCount(null)}")
                FormOutlinedButton(model.operations::roles, enabled = model.activeNavigationEnabled) { Text("Neue Funktion") }
            }
            ActiveArea.DETAILS -> Unit
        }
    }
}

/** Shared stateless hub presentation; no session, repository or demo capability. */
@Composable internal fun ActiveHubGrid(cards: List<HubCard>, enabled: Boolean = true, onOpen: (ActiveArea) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = activeHubColumns(maxWidth.value, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        FormOutlinedButton({ onOpen(card.area) }, enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 96.dp)) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PreparationHeading(card.area.label)
                                card.summary.forEach { Text(it) }
                                card.attention?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                Text("${card.area.label} öffnen")
                            }
                        }
                    }
                }
            }
        }
    }
}
