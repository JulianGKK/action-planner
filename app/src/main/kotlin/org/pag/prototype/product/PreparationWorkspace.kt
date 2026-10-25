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
    OVERVIEW("Übersicht"), PERSONS("Personen"), TASKS("Aufgaben"), ROLES("Rollen"), MATERIAL("Material"), COSTS("Kosten")
}
enum class PreparationContentState { LOADING, CONTENT, EMPTY, ERROR }
internal data class PreparationSummary(val persons: Int, val roles: Int?, val withoutPrimaryRole: Int?,
    val open: Int?, val inProgress: Int?, val high: Int?)
internal fun preparationSummary(snapshot: WorkspaceSnapshot): PreparationSummary {
    val activeTasks = snapshot.tasks?.active
    return PreparationSummary(
    snapshot.persons.size, snapshot.operations?.roles?.size,
    snapshot.operations?.persons?.count { it.plan.primaryRoleId == null },
    activeTasks?.count { it.status == TaskStatus.OPEN },
    activeTasks?.count { it.status == TaskStatus.IN_PROGRESS },
    activeTasks?.count { it.priority == TaskPriority.HIGH })
}
internal fun plannedRoles(person: OperationalPerson, board: OperationalBoard): String? {
    var count=0
    for(role in board.roles)if(role.id in person.plan.roleIds)count++
    if(count==0)return null
    val text=StringBuilder()
    fun append(role:org.pag.domain.Role){if(text.isNotEmpty())text.append(", ");text.append(role.name)}
    for(role in board.roles)if(role.id==person.plan.primaryRoleId && role.id in person.plan.roleIds)append(role)
    for(role in board.roles)if(role.id!=person.plan.primaryRoleId && role.id in person.plan.roleIds)append(role)
    return text.toString()
}
@Composable internal fun PreparationHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
@Composable internal fun ActionWorkspaceHeader(model: ProductViewModel) {
    val action = model.selected ?: return
    if(model.hasCasEditorDraft)Text("Ungespeicherter Entwurf: Beim Verlassen der Bearbeitung oder Bereichswechsel wird er verworfen.")
    FormOutlinedButton(model::back, enabled = !model.saving && !model.personSaving && !model.sensitiveBusy && !model.operations.busy && !model.tasks.busy && !model.protocol.busy && !model.material.busy && !model.expenses.busy && !model.memoryReports.busy && !model.documents.busy) { Text(if(model.hasCasEditorDraft)"‹ Aktionen · Entwurf verwerfen" else "‹ Aktionen") }
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
        if (model.documentsAvailable) FormOutlinedButton(model::openDocuments, enabled=model.preparationNavigationEnabled) { Text("Dokumente") }
        PreparationArea.entries.filter { (it != PreparationArea.MATERIAL || model.materialAvailable) && (it != PreparationArea.COSTS || model.expensesAvailable) }.forEach { area ->
            val chosen = model.preparationArea == area
            FormOutlinedButton({ model.preparationArea(area) }, enabled = model.preparationNavigationEnabled,
                modifier = Modifier.heightIn(min = 48.dp).semantics { selected = chosen; role = androidx.compose.ui.semantics.Role.Tab }) {
                Text((if (chosen) "✓ " else "") + area.label)
            }
        }
    }
}
@Composable internal fun PreparationWorkspace(model: ProductViewModel, renderContent:Boolean=true) {
    ActionWorkspaceHeader(model)
    PreparationNavigation(model)
    if(!renderContent)return
    if (model.personDetailLoading) { PagLoadingState(); return }
    if(model.personForm) {
        PersonsSection(model);return
    }
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
            PreparationArea.COSTS -> ExpenseSection(model.expenses, content.persons)
        }
    }
}
@Composable private fun PreparationOverview(model: ProductViewModel, content: WorkspaceSnapshot) {
    val action = model.selected ?: return
    content.documentSummary?.let { summary ->
        FormOutlinedButton(model::openDocuments) { Text("Dokumente · ${summary.count} Dokumente") }
    }
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
    content.expenseSummary?.let { summary ->
        FormOutlinedButton({ model.preparationArea(PreparationArea.COSTS) }) { Text("Kosten · ${summary.count} Einträge · ${EuroMoney.format(summary.totalCents)}") }
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
            SensitiveState.INCOMPLETE_SETUP, SensitiveState.DELETE_PENDING -> LeadershipRecoveryOffer(model,result.value)
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
                Text(if (plan != null && board != null) model.formatPlannedRoles(plan, board) ?: "Noch keine Rolle" else "Noch keine Rolle")
            }
        }
    }
    model.personError?.let { Text("Die Person ist nicht verfügbar. Bitte erneut aus der Übersicht öffnen.") }
}
@Composable private fun PreparationRoles(model: ProductViewModel, board: OperationalBoard) {
    val ops = model.operations
    var templates by remember { mutableStateOf(false) }
    PreparationHeading("Rollen")
    Text("${board.roles.size} angelegt · Geplante Nutzung")
    if(ops.editingRole!=null || ops.deletingRole!=null){RoleEditor(ops);return}
    if(ops.createError==null)ops.error?.let { Text(operationsError(it)) }
    if(ops.createSurface==RoleCreateSurface.PREPARATION){
        RoleCreateForm(ops,RoleCreateSurface.PREPARATION,"Rollenname","Speichern")
        FormOutlinedButton(ops::cancelRoleCreate,enabled=!ops.busy){Text("Abbrechen")}
        return
    }
    FormButton({ops.beginRoleCreate(RoleCreateSurface.PREPARATION)},enabled=!ops.busy){Text("Rolle anlegen")}
    ops.templateCreateError?.let{Text(createRoleError(it))}
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
            FormOutlinedButton({ ops.editRole(role) }, enabled = !ops.busy) { Text("Umbenennen") }
            FormOutlinedButton({ ops.editRole(role,delete=true) }, enabled = !ops.busy) { Text("Löschen") }
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
