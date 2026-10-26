package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import org.pag.domain.*

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ExpenseSection(model: ExpenseModel, persons: List<Person>) {
    PreparationHeading("Kosten")
    if (model.completedView) Text("Kosten können auch nach Abschluss ergänzt oder korrigiert werden. Die Aktion selbst bleibt abgeschlossen.")
    model.error?.let { Text(when (it) {
        OperationsFailure.REVISION_EXHAUSTED -> "Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    OperationsFailure.STALE -> "Der Kosteneintrag wurde inzwischen geändert. Die Eingaben wurden nicht gespeichert."
        OperationsFailure.INVALID -> "Bitte Bezeichnung, zahlende Bezeichnungen, positive Eurobeträge und die genaue Zahlungsaufteilung prüfen."
        OperationsFailure.NOT_ALLOWED -> "Kosten sind im aktuellen Aktionsstatus nicht verfügbar."
        else -> "Die Kosten konnten nicht geladen oder gespeichert werden."
    }) }
    if(model.commitConfirmed && !model.busy)FormOutlinedButton({model.reload()}){Text("Gespeicherte Ansicht erneut laden")}
    if (model.error == OperationsFailure.STALE) FormOutlinedButton({model.reload()}, enabled = !model.busy && !model.commitConfirmed) { Text("Zur aktuellen Kostenliste · Eingaben verwerfen") }
    if (model.confirmingDelete) {
        Text("Der Kosteneintrag und seine Zahlungsaufteilung werden entfernt.")
        FormButton({model.delete()}, enabled = !model.busy && !model.commitConfirmed && model.error != OperationsFailure.STALE) { Text("Entfernen bestätigen") }
        FormOutlinedButton({model.cancelDelete()}, enabled = !model.busy && !model.commitConfirmed) { Text("Eintrag behalten") }
        return
    }
    val draft = model.draft
    if (draft != null) {
        PreparationHeading(if (model.expected == null) "Kosten eintragen" else "Kosten bearbeiten")
        FormTextField(draft.title, { model.title(it) }, label = { Text("Bezeichnung") },
            supportingText = originalContent{ Text("1–120 Zeichen") }, autoFocus = model.expected == null, enabled = !model.busy && !model.commitConfirmed, modifier = Modifier.fillMaxWidth())
        FormTextField(draft.total, { model.total(it) }, label = { Text("Gesamtbetrag") },
            supportingText = originalContent{ Text("Euro · z. B. 120,50 · ohne Tausendertrennzeichen") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !model.busy && !model.commitConfirmed, modifier = Modifier.fillMaxWidth())
        PreparationHeading("Zahlungen")
        Text("Pseudonym oder kurze Bezeichnung genügt. Es wird nur Text übernommen, keine Person verknüpft.")
        draft.payments.forOriginalIndexed { index, payment ->
            PreparationHeading(originalText{"Zahlung ${index+1}"})
            // Per-field producers below run only after the original owner books the copy.
            FormTextField(payment.payerLabel, { model.payer(index,it) }, label = { Text(originalText{"Bezahlt von · Zahlung ${index+1}"}) },
                supportingText = originalContent{ Text("1–80 Zeichen") }, enabled = !model.busy && !model.commitConfirmed, modifier = Modifier.fillMaxWidth())
            FormTextField(payment.amount, { model.amount(index,it) }, label = { Text(originalText{"Betrag · Zahlung ${index+1}"}) },
                supportingText = originalContent{ Text("Euro") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !model.busy && !model.commitConfirmed, modifier = Modifier.fillMaxWidth())
            FormOutlinedButton({ model.removePayment(index) }, enabled = !model.busy && !model.commitConfirmed) { Text(originalText{"Zahlung ${index+1} entfernen"}) }
        }
        FormOutlinedButton({ model.addPayment() }, enabled = draft.canAdd && !model.busy) { Text("+ freie Person / Bezeichnung") }
        PreparationHeading("Aktionsperson übernehmen")
        persons.forOriginal { person -> val id=person.id;FormOutlinedButton({ model.addPaymentPerson(id) }, enabled = draft.canAdd && !model.busy) { Text(person.pseudonym) } }
        Text(originalText{"${draft.payments.size} von maximal 32 Zahlungen"})
        Text(model.draftFeedback)
        FormButton({model.save()}, enabled = !model.busy && !model.commitConfirmed && model.error != OperationsFailure.STALE && model.canSave) { Text(if (model.busy) "Wird gespeichert …" else "Speichern") }
        FormOutlinedButton({model.cancel()}, enabled = !model.busy && !model.commitConfirmed) { Text("Abbrechen") }
        if (model.expected != null) FormOutlinedButton({model.requestDelete()}, enabled = !model.busy && !model.commitConfirmed && model.error != OperationsFailure.STALE) { Text("Kostenposition entfernen") }
        return
    }
    val board = model.board
    val ui=if(board!=null)model.ui() else null
    if (board != null) {
        if(ui==null){PagErrorState();return}
        Text(ui.summary);Text(ui.count)
    }
    if (model.writable) FormButton({model.create()}) { Text("+ Kosten eintragen") }
    FormOutlinedButton({model.reload()}, enabled = !model.loading && !model.busy) { Text("Kostenliste aktualisieren") }
    if (board == null) { if (model.error != null) PagErrorState() else PagLoadingState(); return }
    if (model.loading) Text("Kostenliste wird aktualisiert …")
    if (board.expenses.isEmpty()) Text("Noch keine Kosten.")
    board.expenses.forOriginal { expense ->
        val display=ui?.row(expense)?:return@forOriginal
        val id=expense.id
        FormOutlinedButton({ model.editId(id) }, modifier = Modifier.fillMaxWidth().originalSemantics { onClick(label = display.semantics, action = null) }) {
            Column(Modifier.fillMaxWidth()) {
                PreparationHeading(expense.title); Text(display.money)
                display.payments.forOriginal{Text(it)}
            }
        }
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun CompletedExpenseWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    FormOutlinedButton({model.closeCompletedExpenses()}, enabled = !model.expenses.busy) { Text("‹ Zur abgeschlossenen Aktion") }
    val content = model.workspace
    if (content == null) { if (model.workspaceError) PagErrorState() else PagLoadingState(); return }
    ExpenseSection(model.expenses, content.persons)
}
