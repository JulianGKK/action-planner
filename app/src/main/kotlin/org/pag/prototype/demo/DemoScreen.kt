package org.pag.prototype.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import org.pag.prototype.product.*
import java.time.Instant
import java.time.ZoneId

@Composable fun DemoScreen(model: DemoState) {
    MaterialTheme {
        ProductInputScope(listOf(model.revision, model.page, model.editor, model.personId, model.taskId)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DEMO – nur Beispieldaten", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text("Bitte keine echten Daten eingeben.", style = MaterialTheme.typography.bodySmall)
                    if (model.action != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormOutlinedButton(model::reset) { Text("Demo zurücksetzen") }
                        FormOutlinedButton(model::end) { Text("Demo beenden") }
                    }
                }
                val scroll = rememberScrollState()
                LaunchedEffect(model.revision, model.page, model.editor, model.personId, model.taskId) { scroll.scrollTo(0) }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    key(model.revision, model.editor, model.personId, model.taskId) {
                        if (model.action == null) DemoHome(model) else DemoWorkspace(model)
                    }
                }
            }
        }
    }
}
@Composable private fun DemoHome(model: DemoState) {
    PreparationHeading("PaG Demo")
    Text("Nur Beispieldaten. Änderungen werden nicht gespeichert.")
    Text("Bitte keine echten Personen- oder Aktionsdaten eingeben.")
    FormButton({ model.begin(DemoScenario.PREPARATION) }) { Text("Vorbereitung ausprobieren") }
    FormButton({ model.begin(DemoScenario.ACTIVE) }) { Text("Direkt ins Lagebild") }
    FormOutlinedButton({ model.begin(DemoScenario.TOUR) }) { Text("Kurzer Rundgang") }
    Text("Die Demo verwendet Beispieldaten und bildet die Sicherheitsfunktionen nicht vollständig nach. Im Echtbetrieb gelten Geräteauthentisierung, kurze Sessions und separater Leitungsschutz.")
}
@Composable private fun DemoWorkspace(model: DemoState) {
    val action = model.action ?: return
    PreparationHeading(action.title)
    Text(statusLabel(action.status).uppercase())
    model.tourStep?.let { step ->
        PreparationHeading("Rundgang ${step + 1}/4")
        Text(listOf("Öffne Personen und aktualisiere eine Lage.", "Setze eine offene Aufgabe in Arbeit.", "Prüfe die aktuellen Funktionen.", "Kehre zum Lagebild zurück und sieh dir die aktualisierten Zahlen an.")[step])
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormOutlinedButton(model::nextTour) { Text(if (step == 3) "Rundgang abschließen" else "Weiter") }
            FormOutlinedButton(model::skipTour) { Text("Überspringen") }
        }
    }
    if (action.status == ActionStatus.PREPARATION) FlowRow(Modifier.semantics { selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DemoPage.OVERVIEW to "Übersicht", DemoPage.PERSONS to "Personen", DemoPage.TASKS to "Aufgaben", DemoPage.ROLES to "Rollen").forEach { (page, label) ->
            val chosen = model.page == page
            FormOutlinedButton({ model.navigate(page) }, modifier = Modifier.semantics { selected = chosen; role = androidx.compose.ui.semantics.Role.Tab }) { Text((if (chosen) "✓ " else "") + label) }
        }
    } else if (model.page != DemoPage.HUB) FormOutlinedButton({ model.navigate(DemoPage.HUB) }) { Text("‹ Zum Lagebild") }
    if (model.editor != null || model.personId != null || model.taskId != null) {
        FormOutlinedButton({ model.navigate(model.page) }) { Text("‹ Zur Bereichsübersicht") }
    }
    model.error?.let { Text(it) }
    when (model.editor) {
        DemoEditor.PERSON -> PersonEditor(model)
        DemoEditor.PLAN -> PlanEditor(model)
        DemoEditor.LIVE -> LiveEditor(model)
        DemoEditor.TASK -> TaskEditor(model)
        DemoEditor.ROLE -> RoleEditor(model)
        DemoEditor.ACTION -> ActionEditor(model)
        null -> when (model.page) {
            DemoPage.OVERVIEW -> DemoOverview(model)
            DemoPage.HUB -> {
                PreparationHeading("Lagebild")
                ActiveHubGrid(activeHubCards(action, model.board ?: return, model.taskBoard ?: return)) { area ->
                    model.navigate(when (area) { ActiveArea.PERSONS -> DemoPage.PERSONS; ActiveArea.TASKS -> DemoPage.TASKS; ActiveArea.FUNCTIONS -> DemoPage.FUNCTIONS; ActiveArea.DETAILS -> DemoPage.DETAILS; ActiveArea.HUB -> DemoPage.HUB })
                }
            }
            DemoPage.PERSONS -> DemoPersons(model)
            DemoPage.TASKS -> DemoTasks(model)
            DemoPage.ROLES, DemoPage.FUNCTIONS -> DemoRoles(model)
            DemoPage.DETAILS -> {
                PreparationHeading("Aktionsdetails")
                Text(action.description); Text("Beginn: ${formattedTime(action.startAt)}"); Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
            }
        }
    }
}
@Composable private fun ProtectionExplanation() {
    PreparationHeading("Leitungsschutz")
    Text("Im Echtbetrieb werden sensible Personendaten zusätzlich durch eine separate Leadership-Passphrase geschützt. In der Demo wird dieser Sicherheitsbereich nicht entsperrt.")
}
@Composable private fun DemoOverview(model: DemoState) {
    val action = model.action ?: return; val board = model.board ?: return; val tasks = model.taskBoard ?: return
    PreparationHeading("Vorbereitung im Überblick")
    Text(action.description); Text("Beginn: ${formattedTime(action.startAt)}"); Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
    FormOutlinedButton({ model.edit(DemoEditor.ACTION) }) { Text("Aktionsdetails bearbeiten") }
    PreparationHeading("Planung")
    FormOutlinedButton({ model.navigate(DemoPage.PERSONS) }) { Text("${board.persons.size} Personen") }
    FormOutlinedButton({ model.navigate(DemoPage.ROLES) }) { Text("${board.roles.size} Rollen") }
    FormOutlinedButton({ model.navigate(DemoPage.TASKS) }) { Text("${tasks.active.count { it.status == TaskStatus.OPEN }} offen · ${tasks.active.count { it.status == TaskStatus.IN_PROGRESS }} in Arbeit · ${tasks.active.count { it.priority == TaskPriority.HIGH }} hohe Priorität") }
    ProtectionExplanation()
    var confirming by remember { mutableStateOf(false) }
    PreparationHeading("Nächster Schritt")
    if (!confirming) FormButton({ confirming = true }) { Text("Aktion starten") } else {
        Text("Aktion starten? Planung und Stammdaten sind danach unveränderbar. Die Lage beginnt ohne Anwesenheitsangabe.")
        FormButton(model::startAction) { Text("Bestätigen") }
        FormOutlinedButton({ confirming = false }) { Text("Abbrechen") }
    }
}
@Composable private fun DemoPersons(model: DemoState) {
    val board = model.board ?: return
    val person = board.persons.find { it.person.id == model.personId }
    PreparationHeading("Personen")
    if (person != null) {
        PreparationHeading(person.person.pseudonym)
        if (board.status == ActionStatus.PREPARATION) {
            Text(plannedRoles(person, board) ?: "Noch keine Rolle")
            FormOutlinedButton({ model.edit(DemoEditor.PERSON, person.person.id) }) { Text("Person bearbeiten") }
            FormButton({ model.edit(DemoEditor.PLAN, person.person.id) }) { Text("Rollen planen") }
        } else {
            Text(operationalLabel(person.state?.status ?: OperationalStatus.NO_STATUS))
            person.state?.currentRoleId?.let { id -> Text("Aktuelle Funktion: ${board.roles.find { it.id == id }?.name.orEmpty()}") }
            person.state?.currentPlaceLabel?.let { Text("Ort / Ziel: $it") }
            FormButton({ model.edit(DemoEditor.LIVE, person.person.id) }) { Text("Lage aktualisieren") }
        }
        ProtectionExplanation(); return
    }
    Text("${board.persons.size} insgesamt")
    if (board.status == ActionStatus.PREPARATION) Text("${board.persons.count { it.plan.primaryRoleId == null }} ohne geplante Hauptrolle")
    board.persons.forEach { p ->
        FormOutlinedButton({ model.viewPerson(p.person.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(p.person.pseudonym, style = MaterialTheme.typography.titleLarge)
                if (board.status == ActionStatus.PREPARATION) Text(plannedRoles(p, board) ?: "Noch keine Rolle") else {
                    Text(operationalLabel(p.state?.status ?: OperationalStatus.NO_STATUS))
                    p.state?.currentRoleId?.let { id -> Text(board.roles.find { it.id == id }?.name.orEmpty()) }
                }
            }
        }
    }
    if (board.status == ActionStatus.PREPARATION) FormButton({ model.edit(DemoEditor.PERSON) }) { Text("Person hinzufügen") }
}
@Composable private fun DemoRoles(model: DemoState) {
    val board = model.board ?: return
    val planning = board.status == ActionStatus.PREPARATION
    PreparationHeading(if (planning) "Rollen" else "Aktuelle Funktionen · Vor Ort")
    board.roles.forEach { r ->
        Text(if (planning) "${r.name} · ${board.persons.count { r.id in it.plan.roleIds }} geplant" else "${r.name}: ${board.roleCount(r.id)}")
    }
    if (!planning) Text("Ohne Funktion vor Ort: ${board.roleCount(null)}")
    FormButton({ model.edit(DemoEditor.ROLE) }) { Text(if (planning) "Rolle anlegen" else "Neue Funktion") }
    if (planning) {
        PreparationHeading("Vorlagen · Vorschläge")
        OperationalRules.templates.forEach { name -> FormOutlinedButton({ model.addRole(name) }, enabled = board.roles.none { OperationalRules.normalizedName(it.name) == OperationalRules.normalizedName(name) }) { Text(name) } }
    }
}
@Composable private fun DemoTasks(model: DemoState) {
    val board = model.taskBoard ?: return
    PreparationHeading("Aufgaben")
    val task = board.tasks.find { it.id == model.taskId }
    if (task != null) {
        PreparationHeading(task.title); Text("${when (task.priority) { TaskPriority.HIGH -> "Hohe Priorität"; TaskPriority.NORMAL -> "Normale Priorität"; TaskPriority.LOW -> "Niedrige Priorität" }} · ${taskLabel(task.status)}")
        FormButton({ model.edit(DemoEditor.TASK, task.id) }) { Text("Aufgabe bearbeiten") }
        TaskStatus.entries.filter { TaskRules.event(task.status, it) != null }.forEach { status ->
            FormOutlinedButton({ model.transitionTask(task.id, status) }) { Text(if (status == TaskStatus.OPEN) "Wieder öffnen" else taskLabel(status)) }
        }
        board.events.filter { it.taskId == task.id }.forEach { Text("${eventLabel(it.type)} · ${formattedTime(it.occurredAt)}") }
        return
    }
    Text("${board.active.count { it.status == TaskStatus.OPEN }} offen · ${board.active.count { it.status == TaskStatus.IN_PROGRESS }} in Arbeit · ${board.active.count { it.priority == TaskPriority.HIGH }} hohe Priorität")
    FormButton({ model.edit(DemoEditor.TASK) }) { Text("+ Aufgabe") }
    if (board.active.isEmpty()) Text("Keine offenen Aufgaben")
    fun title(t: Task) = "${t.title} · ${taskLabel(t.status)}" + if (t.priority == TaskPriority.HIGH) " · Hohe Priorität" else ""
    board.active.forEach { t -> FormOutlinedButton({ model.viewTask(t.id) }, modifier = Modifier.fillMaxWidth()) { Text(title(t)) } }
    var closed by remember { mutableStateOf(false) }
    if (board.closed.isNotEmpty()) {
        FormOutlinedButton({ closed = !closed }) { Text("Erledigt / abgebrochen (${board.closed.size})") }
        if (closed) board.closed.forEach { t -> FormOutlinedButton({ model.viewTask(t.id) }) { Text(title(t)) } }
    }
    if (model.action?.status == ActionStatus.PREPARATION) {
        PreparationHeading("Aufgabenvorschläge")
        TaskRules.templates.forEach { template -> FormOutlinedButton({ model.edit(DemoEditor.TASK); model.saveTask(template.input()) }) { Text(template.title) } }
    }
}
private fun taskLabel(status: TaskStatus) = when (status) { TaskStatus.OPEN -> "Offen"; TaskStatus.IN_PROGRESS -> "In Arbeit"; TaskStatus.DONE -> "Erledigt"; TaskStatus.CANCELLED -> "Entfällt" }
private fun eventLabel(type: TaskEventType) = when (type) { TaskEventType.CREATED -> "Erstellt"; TaskEventType.STARTED -> "In Arbeit"; TaskEventType.COMPLETED -> "Erledigt"; TaskEventType.CANCELLED -> "Entfällt"; TaskEventType.REOPENED -> "Wieder geöffnet" }
@Composable private fun PersonEditor(model: DemoState) {
    PreparationHeading(if (model.personId == null) "Person hinzufügen" else "Person bearbeiten")
    var name by remember { mutableStateOf(model.board?.persons?.find { it.person.id == model.personId }?.person?.pseudonym.orEmpty()) }
    FormTextField(name, { name = it }, label = { Text("Pseudonym") }, autoFocus = model.personId == null, keyboardOptions = productKeyboard(ProductTextKind.PERSON), modifier = Modifier.fillMaxWidth())
    FormButton({ model.savePerson(name) }, enabled = PersonRules.valid(name)) { Text("Speichern") }
    FormOutlinedButton(model::closeEditor) { Text("Abbrechen") }
}
@Composable private fun RoleEditor(model: DemoState) {
    PreparationHeading("Rolle anlegen")
    var name by remember { mutableStateOf("") }
    FormTextField(name, { name = it }, label = { Text("Rollenname") }, autoFocus = true, keyboardOptions = productKeyboard(ProductTextKind.ROLE), modifier = Modifier.fillMaxWidth())
    FormButton({ model.addRole(name) }, enabled = OperationalRules.validName(name)) { Text("Speichern") }
    FormOutlinedButton(model::closeEditor) { Text("Abbrechen") }
}
@Composable private fun PlanEditor(model: DemoState) {
    val board = model.board ?: return; val p = board.persons.find { it.person.id == model.personId } ?: return
    PreparationHeading("Rollen planen · ${p.person.pseudonym}")
    var ids by remember { mutableStateOf(p.plan.roleIds) }; var primary by remember { mutableStateOf(p.plan.primaryRoleId) }
    board.roles.forEach { r ->
        FormOutlinedButton({ ids = if (r.id in ids) ids - r.id else ids + r.id; if (primary !in ids) primary = null }) { Text((if (r.id in ids) "✓ " else "") + r.name) }
        if (r.id in ids) FormOutlinedButton({ primary = r.id }) { Text(if (primary == r.id) "Hauptfunktion ✓" else "${r.name} als Hauptfunktion") }
    }
    FormOutlinedButton({ primary = null }) { Text("Ohne Hauptfunktion planen") }
    FormButton({ model.savePlan(ids, primary) }) { Text("Speichern") }; FormOutlinedButton(model::closeEditor) { Text("Abbrechen") }
}
@Composable private fun LiveEditor(model: DemoState) {
    val board = model.board ?: return; val p = board.persons.find { it.person.id == model.personId } ?: return
    PreparationHeading("Lage aktualisieren · ${p.person.pseudonym}")
    var status by remember { mutableStateOf(p.state?.status ?: OperationalStatus.NO_STATUS) }
    var roleId by remember { mutableStateOf(p.state?.currentRoleId) }; var place by remember { mutableStateOf(p.state?.currentPlaceLabel.orEmpty()) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OperationalStatus.entries.forEach { s -> FormOutlinedButton({ status = s; if (!OperationalRules.roleAllowed(s)) roleId = null }) { Text((if (status == s) "✓ " else "") + operationalLabel(s)) } }
    }
    if (status == OperationalStatus.ON_SITE) {
        PreparationHeading("Aktuelle Funktion")
        FormOutlinedButton({ roleId = null }) { Text("Keine Funktion") }
        board.preferredRoles(p.person.id).forEach { r -> FormOutlinedButton({ roleId = r.id }) { Text((if (roleId == r.id) "✓ " else "") + r.name) } }
    }
    if (OperationalRules.placeRelevant(status)) FormTextField(place, { place = it }, label = { Text("Ort / Ziel") }, keyboardOptions = productKeyboard(ProductTextKind.PLACE), modifier = Modifier.fillMaxWidth())
    FormButton({ model.saveLive(status, roleId, place) }) { Text("Übernehmen") }; FormOutlinedButton(model::closeEditor) { Text("Abbrechen") }
}
@Composable private fun TaskEditor(model: DemoState) {
    val ops = model.board ?: return
    val old = model.taskBoard?.tasks?.find { it.id == model.taskId }
    var input by remember { mutableStateOf(old?.input() ?: TaskInput("")) }
    PreparationHeading(if (old == null) "Aufgabe hinzufügen" else "Aufgabe bearbeiten")
    FormTextField(input.title, { input = input.copy(title = it) }, label = { Text("Titel") }, autoFocus = old == null, keyboardOptions = productKeyboard(ProductTextKind.TASK_TITLE), modifier = Modifier.fillMaxWidth())
    PreparationHeading("Priorität")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskPriority.entries.forEach { p -> FormOutlinedButton({ input = input.copy(priority = p) }) { Text((if (input.priority == p) "✓ " else "") + when (p) { TaskPriority.HIGH -> "Hoch"; TaskPriority.NORMAL -> "Normal"; TaskPriority.LOW -> "Niedrig" }) } }
    }
    PreparationHeading("Zuständigkeit · Person oder Funktion")
    FormOutlinedButton({ input = input.copy(assigneePersonId = null, assigneeRoleId = null) }) { Text("Unzugeordnet") }
    ops.persons.forEach { p -> FormOutlinedButton({ input = input.copy(assigneePersonId = p.person.id, assigneeRoleId = null) }) { Text((if (input.assigneePersonId == p.person.id) "✓ " else "") + p.person.pseudonym) } }
    ops.roles.forEach { r -> FormOutlinedButton({ input = input.copy(assigneeRoleId = r.id, assigneePersonId = null) }) { Text((if (input.assigneeRoleId == r.id) "✓ " else "") + r.name) } }
    PreparationHeading("Betroffene Person · unabhängig von Zuständigkeit")
    FormOutlinedButton({ input = input.copy(affectedPersonId = null) }) { Text("Keine betroffene Person") }
    ops.persons.forEach { p -> FormOutlinedButton({ input = input.copy(affectedPersonId = p.person.id) }) { Text((if (input.affectedPersonId == p.person.id) "✓ " else "") + p.person.pseudonym) } }
    val zone = remember { ZoneId.systemDefault() }
    var due by remember { mutableStateOf(input.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }) }
    var dueError by remember { mutableStateOf<String?>(null) }
    var hasDue by remember { mutableStateOf(input.dueAt != null) }
    if (hasDue) {
        DateTimeField("Fälligkeit · optional", due, zone, {}) { due = it }
        FormOutlinedButton({ due = null; hasDue = false }) { Text("Fälligkeit entfernen") }
    } else FormOutlinedButton({ hasDue = true }) { Text("Fälligkeit hinzufügen") }
    dueError?.let { Text(it) }
    FormButton({
        val millis = if (due == null) null else if (due == old?.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }) old?.dueAt else selectedInstant(due, zone)
        if (hasDue && due == null) dueError = "Bitte Datum und Uhrzeit auswählen." else if (due != null && millis == null) dueError = "Diese Uhrzeit ist durch eine Zeitumstellung nicht eindeutig." else model.saveTask(input.copy(dueAt = millis))
    }, enabled = TaskRules.valid(input)) { Text("Speichern") }
    FormOutlinedButton(model::closeEditor) { Text("Abbrechen") }
}
@Composable private fun ActionEditor(model: DemoState) {
    val a = model.action ?: return; val zone = remember { ZoneId.systemDefault() }
    PreparationHeading("Aktion bearbeiten")
    var title by remember { mutableStateOf(a.title) }; var description by remember { mutableStateOf(a.description) }
    var start by remember { mutableStateOf(Instant.ofEpochMilli(a.startAt).atZone(zone).toLocalDateTime()) }
    var end by remember { mutableStateOf(Instant.ofEpochMilli(a.expectedEndAt).atZone(zone).toLocalDateTime()) }
    var error by remember { mutableStateOf<String?>(null) }
    FormTextField(title, { title = it }, label = { Text("Titel") }, keyboardOptions = productKeyboard(ProductTextKind.ACTION_TITLE), modifier = Modifier.fillMaxWidth())
    FormTextField(description, { description = it }, label = { Text("Beschreibung") }, keyboardOptions = productKeyboard(ProductTextKind.DESCRIPTION), modifier = Modifier.fillMaxWidth())
    Text("Zeitzone: ${zone.id}")
    DateTimeField("Beginn", start, zone, {}) { start = it }; DateTimeField("Voraussichtliches Ende", end, zone, {}) { end = it }
    FormButton({
        val from = if (start == Instant.ofEpochMilli(a.startAt).atZone(zone).toLocalDateTime()) a.startAt else selectedInstant(start, zone)
        val to = if (end == Instant.ofEpochMilli(a.expectedEndAt).atZone(zone).toLocalDateTime()) a.expectedEndAt else selectedInstant(end, zone)
        if (from == null || to == null) error = "Diese Uhrzeit ist durch eine Zeitumstellung nicht eindeutig." else model.saveAction(NewAction(title, description, from, to))
    }) { Text("Speichern") }
    error?.let { Text(it) }; FormOutlinedButton(model::closeEditor) { Text("Abbrechen") }
}
