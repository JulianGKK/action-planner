package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.pag.domain.*

// Fixed CODE content, never an active-data presentation cache.
private val materialNameCode=CodeContent{Text("Bezeichnung")}
private val materialQuantityCode=CodeContent{Text("Menge / Anzahl · optional")}
private val materialPlaceCode=CodeContent{Text("Ort · optional")}
private val materialNameHelp=OriginalContent{Text("1–120 Zeichen")}
private val materialQuantityHelp=OriginalContent{Text("Maximal 40 Zeichen · z. B. 2 Rollen")}
private val materialPlaceHelp=OriginalContent{Text("Maximal 80 Zeichen")}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun MaterialSection(model: MaterialModel, persons: List<Person>, tasks: List<Task>) {
    PreparationHeading("Material")
    model.error?.let { Text(when (it) {
        OperationsFailure.INVALID -> "Bitte Bezeichnung (1–120 Zeichen), Menge (max. 40) und Ort (max. 80) prüfen."
        OperationsFailure.REVISION_EXHAUSTED -> "Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    OperationsFailure.STALE -> "Der Materialeintrag wurde inzwischen geändert. Die Eingaben wurden nicht gespeichert."
        OperationsFailure.NOT_ALLOWED -> "Im aktuellen Aktionsstatus ist diese Änderung nicht möglich."
        else -> "Das Material oder die Zuordnung ist in dieser Aktion nicht verfügbar."
    }) }
    if(model.commitConfirmed && !model.busy)FormOutlinedButton({model.reload()}){Text("Gespeicherte Ansicht erneut laden")}
    if (model.error == OperationsFailure.STALE) FormOutlinedButton({model.reload()}, enabled = !model.busy && !model.commitConfirmed) { Text("Zur Materialliste · Eingaben verwerfen") }
    if (model.confirmingDelete) {
        Text("Der Materialeintrag wird entfernt. Verknüpfte Person und Aufgabe bleiben bestehen.")
        FormButton({model.delete()}, enabled = !model.busy && !model.commitConfirmed && model.error != OperationsFailure.STALE) { Text("Entfernen bestätigen") }
        FormOutlinedButton({model.cancelDelete()}, enabled = !model.busy && !model.commitConfirmed) { Text("Eintrag behalten") }
        return
    }
    val input = model.input
    if (input != null) {
        PreparationHeading(if (model.expected == null) "Material hinzufügen" else "Material bearbeiten")
        FormTextField(input.name, { model.name(it) }, label = { Text("Bezeichnung") },codeLabel=materialNameCode,
            supportingText = materialNameHelp, autoFocus = model.expected == null,
            keyboardOptions = productKeyboard(ProductTextKind.ACTION_TITLE), enabled = !model.busy && !model.commitConfirmed, modifier = Modifier.fillMaxWidth())
        FormTextField(input.quantityLabel.orEmpty(), { model.quantity(it) },
            label = { Text("Menge / Anzahl · optional") },codeLabel=materialQuantityCode,supportingText = materialQuantityHelp,
            enabled = !model.busy && !model.commitConfirmed, modifier = Modifier.fillMaxWidth())
        FormTextField(input.placeLabel.orEmpty(), { model.place(it) },
            label = { Text("Ort · optional") },codeLabel=materialPlaceCode,supportingText = materialPlaceHelp,
            keyboardOptions = productKeyboard(ProductTextKind.PLACE), enabled = !model.busy && !model.commitConfirmed, modifier = Modifier.fillMaxWidth())
        PreparationHeading("Verantwortliche Person · optional")
        MaterialChoice("Keine verantwortliche Person", input.responsiblePersonId == null, !model.busy) { model.responsible(null) }
        persons.forOriginal { person -> val id=person.id;MaterialChoice(person.pseudonym, input.responsiblePersonId == person.id, !model.busy) { model.responsible(id) } }
        PreparationHeading("Verknüpfte Aufgabe · optional")
        MaterialChoice("Keine verknüpfte Aufgabe", input.linkedTaskId == null, !model.busy) { model.linkedTask(null) }
        tasks.forOriginal { task -> val id=task.id;val label=if(model.originalTaskLabelsBound)model.taskChoiceLabel(id)?:return@forOriginal else originalText(task.title.length.toLong()){"${task.title} · ${taskStatusLabel(task.status)}"};MaterialChoice(label, input.linkedTaskId == task.id, !model.busy) { model.linkedTask(id) } }
        FormChoiceButton(if(model.busy)"Wird gespeichert …" else "Speichern",false,enabled=!model.busy && !model.commitConfirmed && model.error!=OperationsFailure.STALE,selection=false,primary=true){model.save()}
        FormChoiceButton("Abbrechen",false,enabled=!model.busy && !model.commitConfirmed,fillWidth=false,selection=false){model.cancel()}
        if (model.expected != null) FormChoiceButton("Material entfernen",false,enabled=!model.busy && !model.commitConfirmed && model.error!=OperationsFailure.STALE,fillWidth=false,selection=false){model.requestDelete()}
        return
    }
    if (model.writable) {
        FormButton({ model.create() }) { Text("+ Material hinzufügen") }
        PreparationHeading("Vorschläge")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialRules.templates.forOriginal { template -> FormOutlinedButton({ model.create(template) }) { Text(template.name) } }
        }
    }
    PreparationHeading("Materialliste")
    FormOutlinedButton({model.reload()}, enabled = !model.loading && !model.busy) { Text("Materialliste aktualisieren") }
    val board = model.board
    if (board == null) { if (model.error != null) PagErrorState() else PagLoadingState(); return }
    if (model.loading) Text("Materialliste wird aktualisiert …")
    if (board.items.isEmpty()) Text("Noch kein Material.")
    board.items.forOriginal { item ->
        val id=item.id
        val semantics=originalText(item.name.length.toLong()){"${item.name} bearbeiten"}
        if(model.writable)FormOutlinedButton({model.editId(id)},modifier=Modifier.fillMaxWidth().originalSemantics{onClick(label=semantics,action=null)}) {
            MaterialContent(item, persons, tasks)
        } else OutlinedCard(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(16.dp)) { MaterialContent(item, persons, tasks) }
        }
    }
}
@Composable private inline fun MaterialChoice(label: String, chosen: Boolean, enabled: Boolean, crossinline action: () -> Unit) {
    FormChoiceButton(label,chosen,enabled,onClick=action)
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun MaterialContent(item: MaterialItem, persons: List<Person>, tasks: List<Task>) {
    Column(Modifier.fillMaxWidth()) {
        PreparationHeading(item.name)
        item.quantityLabel?.let { Text(originalText(it.length.toLong()){"Menge: $it"}) }
        item.placeLabel?.let { Text(originalText(it.length.toLong()){"Ort: $it"}) }
        item.responsiblePersonId?.let { id -> persons.findOriginal { it.id == id }?.let { Text(originalText(it.pseudonym.length.toLong()){"Verantwortlich: ${it.pseudonym}"}) } }
        item.linkedTaskId?.let { id -> tasks.findOriginal { it.id == id }?.let { Text(originalText(it.title.length.toLong()){"Aufgabe: ${it.title}"}) } }
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun CompletedMaterialWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    FormOutlinedButton({model.closeCompletedMaterial()}, enabled = !model.material.busy) { Text("‹ Zur abgeschlossenen Aktion") }
    val content = model.workspace
    if (content == null) { if (model.workspaceError) PagErrorState() else PagLoadingState(); return }
    MaterialSection(model.material, content.persons, content.tasks?.tasks.orEmpty())
}
