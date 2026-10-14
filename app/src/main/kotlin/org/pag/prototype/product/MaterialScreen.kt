package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.pag.domain.*

@Composable internal fun MaterialSection(model: MaterialModel, persons: List<Person>, tasks: List<Task>) {
    PreparationHeading("Material")
    model.error?.let { Text(when (it) {
        OperationsFailure.INVALID -> "Bitte Bezeichnung (1–120 Zeichen), Menge (max. 40) und Ort (max. 80) prüfen."
        OperationsFailure.STALE -> "Der Materialeintrag wurde inzwischen geändert. Die Eingaben wurden nicht gespeichert."
        OperationsFailure.NOT_ALLOWED -> "Im aktuellen Aktionsstatus ist diese Änderung nicht möglich."
        else -> "Das Material oder die Zuordnung ist in dieser Aktion nicht verfügbar."
    }) }
    if (model.error == OperationsFailure.STALE) FormOutlinedButton(model::reload, enabled = !model.busy) { Text("Zur Materialliste · Eingaben verwerfen") }
    if (model.confirmingDelete) {
        Text("Der Materialeintrag wird entfernt. Verknüpfte Person und Aufgabe bleiben bestehen.")
        FormButton(model::delete, enabled = !model.busy && model.error != OperationsFailure.STALE) { Text("Entfernen bestätigen") }
        FormOutlinedButton(model::cancelDelete, enabled = !model.busy) { Text("Eintrag behalten") }
        return
    }
    val input = model.input
    if (input != null) {
        PreparationHeading(if (model.expected == null) "Material hinzufügen" else "Material bearbeiten")
        FormTextField(input.name, { model.change(input.copy(name = it)) }, label = { Text("Bezeichnung") },
            supportingText = { Text("1–120 Zeichen") }, autoFocus = model.expected == null,
            keyboardOptions = productKeyboard(ProductTextKind.ACTION_TITLE), enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        FormTextField(input.quantityLabel.orEmpty(), { model.change(input.copy(quantityLabel = it)) },
            label = { Text("Menge / Anzahl · optional") }, supportingText = { Text("Maximal 40 Zeichen · z. B. 2 Rollen") },
            enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        FormTextField(input.placeLabel.orEmpty(), { model.change(input.copy(placeLabel = it)) },
            label = { Text("Ort · optional") }, supportingText = { Text("Maximal 80 Zeichen") },
            keyboardOptions = productKeyboard(ProductTextKind.PLACE), enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        PreparationHeading("Verantwortliche Person · optional")
        MaterialChoice("Keine verantwortliche Person", input.responsiblePersonId == null, !model.busy) { model.change(input.copy(responsiblePersonId = null)) }
        persons.forEach { person -> MaterialChoice(person.pseudonym, input.responsiblePersonId == person.id, !model.busy) { model.change(input.copy(responsiblePersonId = person.id)) } }
        PreparationHeading("Verknüpfte Aufgabe · optional")
        MaterialChoice("Keine verknüpfte Aufgabe", input.linkedTaskId == null, !model.busy) { model.change(input.copy(linkedTaskId = null)) }
        tasks.forEach { task -> MaterialChoice("${task.title} · ${taskStatusLabel(task.status)}", input.linkedTaskId == task.id, !model.busy) { model.change(input.copy(linkedTaskId = task.id)) } }
        FormButton(model::save, enabled = !model.busy && model.error != OperationsFailure.STALE) { Text(if (model.busy) "Wird gespeichert …" else "Speichern") }
        FormOutlinedButton(model::cancel, enabled = !model.busy) { Text("Abbrechen") }
        if (model.expected != null) FormOutlinedButton(model::requestDelete, enabled = !model.busy && model.error != OperationsFailure.STALE) { Text("Material entfernen") }
        return
    }
    if (model.writable) {
        FormButton({ model.create() }) { Text("+ Material hinzufügen") }
        PreparationHeading("Vorschläge")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialRules.templates.forEach { template -> FormOutlinedButton({ model.create(template) }) { Text(template.name) } }
        }
    }
    PreparationHeading("Materialliste")
    FormOutlinedButton(model::reload, enabled = !model.loading && !model.busy) { Text("Materialliste aktualisieren") }
    val board = model.board
    if (board == null) { if (model.error != null) PagErrorState() else PagLoadingState(); return }
    if (model.loading) Text("Materialliste wird aktualisiert …")
    if (board.items.isEmpty()) Text("Noch kein Material.")
    board.items.forEach { item ->
        if (model.writable) FormOutlinedButton({ model.edit(item) }, modifier = Modifier.fillMaxWidth().semantics { onClick(label = "${item.name} bearbeiten", action = null) }) {
            MaterialContent(item, persons, tasks)
        } else OutlinedCard(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(16.dp)) { MaterialContent(item, persons, tasks) }
        }
    }
}
@Composable private fun MaterialChoice(label: String, chosen: Boolean, enabled: Boolean, action: () -> Unit) {
    FormOutlinedButton(action, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics { selected = chosen }) { Text((if (chosen) "✓ " else "") + label) }
}
@Composable private fun MaterialContent(item: MaterialItem, persons: List<Person>, tasks: List<Task>) {
    Column(Modifier.fillMaxWidth()) {
        PreparationHeading(item.name)
        item.quantityLabel?.let { Text("Menge: $it") }
        item.placeLabel?.let { Text("Ort: $it") }
        item.responsiblePersonId?.let { id -> persons.find { it.id == id }?.let { Text("Verantwortlich: ${it.pseudonym}") } }
        item.linkedTaskId?.let { id -> tasks.find { it.id == id }?.let { Text("Aufgabe: ${it.title}") } }
    }
}
@Composable internal fun CompletedMaterialWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    FormOutlinedButton(model::closeCompletedMaterial, enabled = !model.material.busy) { Text("‹ Zur abgeschlossenen Aktion") }
    val content = model.workspace
    if (content == null) { if (model.workspaceError) PagErrorState() else PagLoadingState(); return }
    MaterialSection(model.material, content.persons, content.tasks?.tasks.orEmpty())
}
