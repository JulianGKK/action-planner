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

internal typealias ActiveTaskContent = TaskListPresentation
internal data class ActiveTaskCard(val title: String, val status: String, val assignee: String, val affected: String?, val due: String?)

/** Keep the domain order, including tie-breakers. Filtering never becomes a new sort. */
internal fun activeTaskContent(board:TaskBoard,personId:String?):ActiveTaskContent = board.presentation.forPerson(personId)

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

private val taskActions = TaskStatus.entries.associateWith { status ->
    java.util.Collections.unmodifiableList(TaskStatus.entries.filter { TaskRules.event(status,it)!=null })
} // Code-only finite enum table; no action data or source authority.
internal fun activeTaskActions(status:TaskStatus):List<TaskStatus> = taskActions.getValue(status)

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ActiveTaskSection(model: TaskModel, people: OperationalBoard, board: TaskBoard) {
    model.error?.let { Text(taskFailureMessage(it)) }
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    model.filter?.let { id ->
        val name=people.persons.findOriginal{it.person.id==id}?.person?.pseudonym?:"Person nicht verfügbar"
        Text(originalText(name.length.toLong()){"Filter · Betrifft: $name"})
        FormOutlinedButton({ model.showPerson(null) }, enabled = !model.busy) { Text("Alle Aufgaben anzeigen") }
    }
    if (model.form) {
        PreparationHeading(if (!model.editingExisting) "Aufgabe hinzufügen" else "Aufgabe bearbeiten")
        TaskForm(model, people)
        return
    }
    val selected = model.selected
    if (selected != null) {
        val row=model.uiRow(selected)?:return
        val card=row.card(now)
        PreparationHeading(card.title)
        Text(row.revision)
        Text(card.status)
        Text(row.assignee)
        row.affected?.let { Text(it) }
        row.due(now)?.let { Text(it) }
        if (model.writable) {
            activeTaskActions(selected.status).forOriginal { status ->
                FormButton({ model.transition(status) }, enabled = !model.busy) {
                    Text(if (status == TaskStatus.OPEN) "Wieder öffnen" else taskStatusLabel(status))
                }
            }
            FormOutlinedButton({model.edit()}, enabled = !model.busy) { Text("Aufgabe bearbeiten") }
        }
        PreparationHeading("Chronik")
        TaskHistory(model)
        return
    }
    val content = activeTaskContent(board, model.filter)
    content.summary.forOriginal { Text(it) }
    if (model.writable) FormButton({ model.add(model.filter) }, enabled = !model.busy) { Text("+ Aufgabe") }
    content.active.forOriginal { task -> ActiveTaskListCard(task, people, now, zone, model) }
    if (content.closed.isNotEmpty()) {
        FormOutlinedButton({model.toggleClosed()}, enabled = !model.busy,
            modifier = Modifier.fillMaxWidth().originalSemantics { stateDescription = if (model.closedExpanded) "Ausgeklappt" else "Eingeklappt" }) {
            Text(if(model.closedExpanded)content.activeClosedExpanded else content.activeClosedCollapsed)
        }
        if (model.closedExpanded) content.closed.forOriginal { task -> ActiveTaskListCard(task, people, now, zone, model) }
    }
}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ActiveTaskListCard(task: Task, people: OperationalBoard, now: Long, zone: ZoneId, model: TaskModel) {
    val row=model.uiRow(task)?:return
    LocalProductObservation.current?.let{observer->androidx.compose.runtime.SideEffect{observer("TASK_ROW")}}
    val card=row.card(now)
    val semanticsLabel=row.semantics
    FormOutlinedButton(row.click, contentEpoch=now/60_000,enabled = !model.busy,
        modifier = Modifier.fillMaxWidth().originalSemantics { onClick(label = semanticsLabel, action = null) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, Modifier.then(productHeadingSemantics), style = MaterialTheme.typography.titleLarge)
            Text(card.status)
            Text(card.assignee)
            row.affected?.let { Text(it) }
            row.due(now)?.let { Text(it) }
        }
    }
}
