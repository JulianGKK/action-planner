package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ActiveTaskContent(val active: List<Task>, val closed: List<Task>, val summary: List<String>)
internal data class ActiveTaskCard(val title: String, val status: String, val assignee: String, val affected: String?, val due: String?)

/** Keep the domain order, including tie-breakers. Filtering never becomes a new sort. */
internal fun activeTaskContent(board: TaskBoard, personId: String?): ActiveTaskContent {
    val active = board.active.filter { personId == null || it.affectedPersonId == personId }
    val closed = board.closed.filter { personId == null || it.affectedPersonId == personId }
    val summary = if (active.isEmpty()) listOf("Keine offenen Aufgaben") else buildList {
        add("${active.count { it.status == TaskStatus.OPEN }} offen · ${active.count { it.status == TaskStatus.IN_PROGRESS }} in Arbeit")
        active.count { it.priority == TaskPriority.HIGH }.takeIf { it > 0 }?.let { add("$it hohe Priorität") }
    }
    return ActiveTaskContent(active, closed, summary)
}

internal fun activeTaskCard(task: Task, people: OperationalBoard, now: Long, zone: ZoneId): ActiveTaskCard {
    val assignee = task.assigneePersonId?.let { id -> people.persons.find { it.person.id == id }?.person?.pseudonym }
        ?: task.assigneeRoleId?.let { id -> people.roles.find { it.id == id }?.name } ?: "Niemand zugeordnet"
    val affected = task.affectedPersonId?.let { id -> people.persons.find { it.person.id == id }?.person?.pseudonym }
    val due = task.dueAt?.let { time ->
        val local = Instant.ofEpochMilli(time).atZone(zone)
        val sameDay = local.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        local.format(DateTimeFormatter.ofPattern(if (sameDay) "HH:mm" else "dd.MM.yyyy HH:mm"))
    }
    return ActiveTaskCard(task.title, "${priorityLabel(task.priority)} · ${taskStatusLabel(task.status)}", assignee, affected, due)
}

internal fun activeTaskActions(status: TaskStatus): List<TaskStatus> = TaskStatus.entries.filter { TaskRules.event(status, it) != null }

@Composable internal fun ActiveTaskSection(model: TaskModel, people: OperationalBoard, board: TaskBoard) {
    model.error?.let { Text(when (it) {
        OperationsFailure.INVALID -> "Bitte Titel (1–120 Zeichen), Zuständigkeit und Fälligkeit prüfen."
        OperationsFailure.STALE -> "Die Aufgabe wurde inzwischen geändert. Dein Entwurf bleibt erhalten; bitte bewusst neu laden."
        OperationsFailure.NOT_ALLOWED -> "Im aktuellen Aktionsstatus ist diese Änderung nicht möglich."
        else -> "Die Aufgabe oder Zuordnung ist in dieser Aktion nicht verfügbar."
    }) }
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    model.filter?.let { id ->
        Text("Filter · Betrifft: ${people.persons.find { it.person.id == id }?.person?.pseudonym ?: "Person nicht verfügbar"}")
        FormOutlinedButton({ model.showPerson(null) }, enabled = !model.busy) { Text("Alle Aufgaben anzeigen") }
    }
    if (model.form) {
        PreparationHeading(if (!model.editingExisting) "Aufgabe hinzufügen" else "Aufgabe bearbeiten")
        TaskForm(model, people)
        return
    }
    val selected = model.selected
    if (selected != null) {
        val card = activeTaskCard(selected, people, now, zone)
        PreparationHeading(card.title)
        Text(card.status)
        Text("Zuständig: ${card.assignee}")
        card.affected?.let { Text("Betrifft: $it") }
        card.due?.let { Text("Fällig: $it") }
        if (model.writable) {
            activeTaskActions(selected.status).forEach { status ->
                FormButton({ model.transition(status) }, enabled = !model.busy) {
                    Text(if (status == TaskStatus.OPEN) "Wieder öffnen" else taskStatusLabel(status))
                }
            }
            FormOutlinedButton(model::edit, enabled = !model.busy) { Text("Aufgabe bearbeiten") }
        }
        PreparationHeading("Chronik")
        board.events.filter { it.taskId == selected.id }.forEach { Text("${eventLabel(it.type)} · ${taskTime(it.occurredAt)}") }
        return
    }
    val content = activeTaskContent(board, model.filter)
    content.summary.forEach { Text(it) }
    if (model.writable) FormButton({ model.add(model.filter) }, enabled = !model.busy) { Text("+ Aufgabe") }
    content.active.forEach { task -> ActiveTaskListCard(task, people, now, zone, model) }
    if (content.closed.isNotEmpty()) {
        FormOutlinedButton(model::toggleClosed, enabled = !model.busy,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (model.closedExpanded) "Ausgeklappt" else "Eingeklappt" }) {
            Text("Erledigt / Entfallen (${content.closed.size}) ${if (model.closedExpanded) "▴" else "▾"}")
        }
        if (model.closedExpanded) content.closed.forEach { task -> ActiveTaskListCard(task, people, now, zone, model) }
    }
}

@Composable internal fun ActiveTaskListCard(task: Task, people: OperationalBoard, now: Long, zone: ZoneId, model: TaskModel) {
    val card = activeTaskCard(task, people, now, zone)
    FormOutlinedButton({ model.open(task) }, enabled = !model.busy,
        modifier = Modifier.fillMaxWidth().semantics { onClick(label = "Aufgabe ${card.title} öffnen", action = null) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text(card.status)
            Text(card.assignee)
            card.affected?.let { Text("Betrifft: $it") }
            card.due?.let { Text("Fällig: $it") }
        }
    }
}
