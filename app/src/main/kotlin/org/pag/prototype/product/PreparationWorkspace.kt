package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import org.pag.domain.*

/** Session-local navigation only; never persisted or restored after lock. */
enum class PreparationArea(val label: String) {
    OVERVIEW("Übersicht"), PERSONS("Personen"), TASKS("Aufgaben"), ROLES("Rollen"), MATERIAL("Material")
}
enum class PreparationContentState { LOADING, CONTENT, EMPTY, ERROR }
internal data class PreparationSummary(val persons: Int, val roles: Int?, val withoutPrimaryRole: Int?,
    val open: Int?, val inProgress: Int?, val high: Int?)
internal fun preparationSummary(snapshot: WorkspaceSnapshot) = PreparationSummary(
    snapshot.persons.size, snapshot.operations?.roles?.size,
    snapshot.operations?.persons?.count { it.plan.primaryRoleId == null },
    snapshot.tasks?.active?.count { it.status == TaskStatus.OPEN },
    snapshot.tasks?.active?.count { it.status == TaskStatus.IN_PROGRESS },
    snapshot.tasks?.active?.count { it.priority == TaskPriority.HIGH })
internal fun plannedRoles(person: OperationalPerson, board: OperationalBoard): String? {
    val roles = board.roles.filter { it.id in person.plan.roleIds }
        .sortedBy { it.id != person.plan.primaryRoleId }
    return roles.joinToString { it.name }.takeIf { it.isNotEmpty() }
}
@Composable internal fun PreparationHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
@Composable internal fun ActionWorkspaceHeader(model: ProductViewModel) {
    val action = model.selected ?: return
    FormOutlinedButton(model::back, enabled = !model.saving && !model.personSaving && !model.sensitiveBusy && !model.operations.busy && !model.tasks.busy && !model.protocol.busy && !model.material.busy) { Text("‹ Aktionen") }
    Text(action.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
    Text(statusLabel(action.status).uppercase(), style = MaterialTheme.typography.labelLarge)
}
@Composable internal fun DraftWorkspace(model: ProductViewModel) {
    val action = model.selected ?: return
    ActionWorkspaceHeader(model)
    Text("Entwurf · Hier legst du die Stammdaten fest.")
    Text(action.description)
    Text("Beginn: ${formattedTime(action.startAt)}")
    Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
    FormOutlinedButton(model::edit) { Text("Aktionsdetails bearbeiten") }
    PreparationLifecycle(model)
}
@Composable internal fun PreparationNavigation(model: ProductViewModel) {
    // Wrapping full controls instead of shrinking tabs or clipping large text.
    FlowRow(Modifier.fillMaxWidth().semantics { selectableGroup() },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PreparationArea.entries.filter { it != PreparationArea.MATERIAL || model.materialAvailable }.forEach { area ->
            val chosen = model.preparationArea == area
            FormOutlinedButton({ model.preparationArea(area) }, enabled = model.preparationNavigationEnabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { selected = chosen; role = androidx.compose.ui.semantics.Role.Tab }) {
                Text((if (chosen) "✓ " else "") + area.label)
            }
        }
    }
}
@Composable internal fun PreparationWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    PreparationNavigation(model)
    if (model.personDetailLoading) { PagLoadingState(); return }
    when (model.preparationContentState) {
        PreparationContentState.LOADING -> { PagLoadingState(); return }
        PreparationContentState.ERROR -> { PagErrorState(); return }
        else -> Unit
    }
    val content = model.workspace ?: return
    if (model.workspaceError) Text("Die Aktualisierung ist fehlgeschlagen. Die bisherige Ansicht bleibt erhalten.")
    when {
        model.sensitiveSetup -> SensitiveSection(model)
        model.operations.screen != OperationsScreen.OVERVIEW -> OperationsSection(model)
        model.personForm || model.selectedPerson != null -> PersonsSection(model)
        else -> when (model.preparationArea) {
            PreparationArea.OVERVIEW -> PreparationOverview(model, content)
            PreparationArea.PERSONS -> PreparationPersons(model, content)
            PreparationArea.TASKS -> content.operations?.let { TaskSection(model.tasks, it) }
            PreparationArea.ROLES -> content.operations?.let { PreparationRoles(model, it) }
            PreparationArea.MATERIAL -> MaterialSection(model.material, content.persons, content.tasks?.tasks.orEmpty())
        }
    }
}
@Composable private fun PreparationOverview(model: ProductViewModel, content: WorkspaceSnapshot) {
    val action = model.selected ?: return
    val summary = preparationSummary(content)
    PreparationHeading("Vorbereitung im Überblick")
    Text(action.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text("Beginn: ${formattedTime(action.startAt)}")
    Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
    FormOutlinedButton(model::edit) { Text("Aktionsdetails bearbeiten") }
    PreparationHeading("Planung")
    FormOutlinedButton({ model.preparationArea(PreparationArea.PERSONS) }, modifier = Modifier.fillMaxWidth()) {
        Text("Personen · ${summary.persons} insgesamt" + (summary.withoutPrimaryRole?.let { " · $it ohne geplante Hauptrolle" } ?: ""))
    }
    FormOutlinedButton({ model.preparationArea(PreparationArea.ROLES) }, modifier = Modifier.fillMaxWidth()) { Text(summary.roles?.let { "Rollen · $it angelegt" } ?: "Rollenstatus nicht verfügbar") }
    FormOutlinedButton({ model.preparationArea(PreparationArea.TASKS) }, modifier = Modifier.fillMaxWidth()) {
        Text(summary.open?.let { "Aufgaben · $it offen · ${summary.inProgress} in Arbeit · ${summary.high} hohe Priorität" } ?: "Aufgabenstatus nicht verfügbar")
    }
    content.materialSummary?.let { summary -> FormOutlinedButton({ model.preparationArea(PreparationArea.MATERIAL) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (summary.count == 0) "Material · Noch kein Material" else "Material · ${summary.count} Materialeinträge")
    } }
    PreparationHeading("Leitungsschutz")
    Text("Schützt sensible Identitäts- und Kontaktdaten dieser Aktion zusätzlich.")
    when (val result = content.leadershipState) {
        is SensitiveResult.Value -> when (result.value) {
            SensitiveState.UNINITIALIZED -> {
                Text("Leitungsschutz noch nicht eingerichtet")
                FormOutlinedButton(model::requestActionProtectionSetup) { Text("Leitungsschutz einrichten") }
            }
            SensitiveState.READY -> Text("Leitungsschutz eingerichtet")
            SensitiveState.PARTIAL_OR_INVALID -> Text("Leitungsschutz nicht verfügbar. Es erfolgt keine Reparatur oder Ersatzanlage.")
        }
        is SensitiveResult.Rejected, null -> Text("Leitungsschutzstatus nicht verfügbar.")
    }
    PreparationHeading("Nächster Schritt")
    PreparationLifecycle(model)
}
@Composable private fun PreparationPersons(model: ProductViewModel, content: WorkspaceSnapshot) {
    val summary = preparationSummary(content)
    PreparationHeading("Personen")
    Text("${summary.persons} insgesamt" + (summary.withoutPrimaryRole?.let { " · $it ohne geplante Hauptrolle" } ?: ""))
    FormButton(model::addPerson) { Text("Person hinzufügen") }
    if (content.persons.isEmpty()) Text("Noch keine Personen angelegt.")
    content.persons.forEach { person ->
        val board = content.operations
        val plan = board?.persons?.find { it.person.id == person.id }
        FormOutlinedButton({ model.personDetail(person.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(person.pseudonym, style = MaterialTheme.typography.titleLarge)
                Text(if (plan != null && board != null) plannedRoles(plan, board) ?: "Noch keine Rolle" else "Noch keine Rolle")
            }
        }
    }
    model.personError?.let { Text("Die Person ist nicht verfügbar. Bitte erneut aus der Übersicht öffnen.") }
}
@Composable private fun PreparationRoles(model: ProductViewModel, board: OperationalBoard) {
    val ops = model.operations
    var form by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var templates by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    LaunchedEffect(submitted, ops.busy) {
        if (submitted && !ops.busy) {
            if (ops.error == null) { form = false; name = ""; editing = null; deleting = null }
            submitted = false
        }
    }
    PreparationHeading("Rollen")
    Text("${board.roles.size} angelegt · Geplante Nutzung")
    ops.error?.let { Text(operationsError(it)) }
    if (form) {
        FormTextField(name, { ops.textInteraction(); name = it }, label = { Text("Rollenname") },
            autoFocus = editing == null, keyboardOptions = productKeyboard(ProductTextKind.ROLE),
            enabled = !ops.busy, modifier = Modifier.fillMaxWidth())
        FormButton({ submitted = true; if (editing == null) ops.createRole(name) else ops.renameRole(editing!!, name) },
            enabled = !ops.busy && OperationalRules.validName(name)) { Text("Speichern") }
        FormOutlinedButton({ ops.textInteraction(); form = false; name = ""; editing = null }, enabled = !ops.busy) { Text("Abbrechen") }
        return
    }
    FormButton({ ops.textInteraction(); form = true; editing = null; name = "" }, enabled = !ops.busy) { Text("Rolle anlegen") }
    FormOutlinedButton({ ops.textInteraction(); templates = !templates }, enabled = !ops.busy,
        modifier = Modifier.semantics { stateDescription = if (templates) "Geöffnet" else "Geschlossen" }) { Text("Vorlagenrollen ${if (templates) "▴" else "▾"}") }
    if (templates) {
        Text("Vorschläge · Auswahl legt eine Rolle für diese Aktion an.")
        OperationalRules.templates.forEach { template ->
            FormOutlinedButton({ ops.createRole(template) }, enabled = !ops.busy && board.roles.none {
                OperationalRules.normalizedName(it.name) == OperationalRules.normalizedName(template)
            }) { Text(template) }
        }
    }
    if (board.roles.isEmpty()) Text("Noch keine Rollen angelegt.")
    board.roles.forEach { role ->
        Text(role.name, style = MaterialTheme.typography.titleMedium)
        val planned = board.persons.count { role.id in it.plan.roleIds }
        Text("$planned geplant · ${board.persons.count { it.plan.primaryRoleId == role.id }} als Hauptrolle")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormOutlinedButton({ ops.textInteraction(); editing = role.id; name = role.name; form = true; deleting = null }, enabled = !ops.busy) { Text("Umbenennen") }
            FormOutlinedButton({ ops.textInteraction(); deleting = role.id }, enabled = !ops.busy) { Text("Löschen") }
        }
        if (deleting == role.id) {
            Text("Rolle löschen? Zugeordnete Rollen können nicht gelöscht werden.")
            FormButton({ submitted = true; ops.deleteRole(role.id) }, enabled = !ops.busy) { Text("Löschen bestätigen") }
            FormOutlinedButton({ ops.textInteraction(); deleting = null }, enabled = !ops.busy) { Text("Abbrechen") }
        }
    }
}
@Composable internal fun PreparationLifecycle(model: ProductViewModel) {
    val action = model.selected ?: return
    model.changeError?.let { Text(changeErrorLabel(it)) }
    if (model.confirming != null) {
        Text("${advanceLabel(action.status)}? Dieser Statusschritt kann nicht rückgängig gemacht werden.")
        FormButton(model::confirmAdvance, enabled = !model.saving) { Text("Bestätigen") }
        FormOutlinedButton(model::cancelAdvance, enabled = !model.saving) { Text("Abbrechen") }
    } else FormButton(model::requestAdvance, enabled = !model.saving) { Text(advanceLabel(action.status)) }
}
