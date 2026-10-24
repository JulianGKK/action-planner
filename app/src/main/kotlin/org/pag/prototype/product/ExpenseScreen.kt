package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import org.pag.domain.*

@Composable internal fun ExpenseSection(model: ExpenseModel, persons: List<Person>) {
    PreparationHeading("Kosten")
    if (model.completedView) Text("Kosten können auch nach Abschluss ergänzt oder korrigiert werden. Die Aktion selbst bleibt abgeschlossen.")
    model.error?.let { Text(when (it) {
        OperationsFailure.REVISION_EXHAUSTED -> "Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.STALE -> "Der Kosteneintrag wurde inzwischen geändert. Die Eingaben wurden nicht gespeichert."
        OperationsFailure.INVALID -> "Bitte Bezeichnung, zahlende Bezeichnungen, positive Eurobeträge und die genaue Zahlungsaufteilung prüfen."
        OperationsFailure.NOT_ALLOWED -> "Kosten sind im aktuellen Aktionsstatus nicht verfügbar."
        else -> "Die Kosten konnten nicht geladen oder gespeichert werden."
    }) }
    if (model.error == OperationsFailure.STALE) FormOutlinedButton(model::reload, enabled = !model.busy) { Text("Zur aktuellen Kostenliste · Eingaben verwerfen") }
    if (model.confirmingDelete) {
        Text("Der Kosteneintrag und seine Zahlungsaufteilung werden entfernt.")
        FormButton(model::delete, enabled = !model.busy && model.error != OperationsFailure.STALE) { Text("Entfernen bestätigen") }
        FormOutlinedButton(model::cancelDelete, enabled = !model.busy) { Text("Eintrag behalten") }
        return
    }
    val draft = model.draft
    if (draft != null) {
        PreparationHeading(if (model.expected == null) "Kosten eintragen" else "Kosten bearbeiten")
        FormTextField(draft.title, { model.change(draft.copy(title = it)) }, label = { Text("Bezeichnung") },
            supportingText = { Text("1–120 Zeichen") }, autoFocus = model.expected == null, enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        FormTextField(draft.total, { model.change(draft.copy(total = it)) }, label = { Text("Gesamtbetrag") },
            supportingText = { Text("Euro · z. B. 120,50 · ohne Tausendertrennzeichen") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        PreparationHeading("Zahlungen")
        Text("Pseudonym oder kurze Bezeichnung genügt. Es wird nur Text übernommen, keine Person verknüpft.")
        draft.payments.forEachIndexed { index, payment ->
            PreparationHeading("Zahlung ${index + 1}")
            fun update(p: ExpensePaymentDraft) = model.change(draft.copy(payments = draft.payments.mapIndexed { i, old -> if (i == index) p else old }))
            FormTextField(payment.payerLabel, { update(payment.copy(payerLabel = it)) }, label = { Text("Bezahlt von · Zahlung ${index + 1}") },
                supportingText = { Text("1–80 Zeichen") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth())
            FormTextField(payment.amount, { update(payment.copy(amount = it)) }, label = { Text("Betrag · Zahlung ${index + 1}") },
                supportingText = { Text("Euro") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !model.busy, modifier = Modifier.fillMaxWidth())
            FormOutlinedButton({ model.change(draft.remove(index)) }, enabled = !model.busy) { Text("Zahlung ${index + 1} entfernen") }
        }
        FormOutlinedButton({ model.change(draft.add()) }, enabled = draft.canAdd && !model.busy) { Text("+ freie Person / Bezeichnung") }
        PreparationHeading("Aktionsperson übernehmen")
        persons.forEach { person -> FormOutlinedButton({ model.change(draft.add(person.pseudonym)) }, enabled = draft.canAdd && !model.busy) { Text(person.pseudonym) } }
        Text("${draft.payments.size} von maximal 32 Zahlungen")
        Text(draft.feedback())
        FormButton(model::save, enabled = !model.busy && model.error != OperationsFailure.STALE && draft.input() != null) { Text(if (model.busy) "Wird gespeichert …" else "Speichern") }
        FormOutlinedButton(model::cancel, enabled = !model.busy) { Text("Abbrechen") }
        if (model.expected != null) FormOutlinedButton(model::requestDelete, enabled = !model.busy && model.error != OperationsFailure.STALE) { Text("Kostenposition entfernen") }
        return
    }
    val board = model.board
    if (board != null) {
        val total = ExpenseRules.sum(board.expenses.map { it.totalCents })
        Text(if (total == null) "Gesamtsumme nicht darstellbar." else "Gesamt: ${EuroMoney.format(total)}")
        Text("${board.expenses.size} Einträge")
    }
    if (model.writable) FormButton(model::create) { Text("+ Kosten eintragen") }
    FormOutlinedButton(model::reload, enabled = !model.loading && !model.busy) { Text("Kostenliste aktualisieren") }
    if (board == null) { if (model.error != null) PagErrorState() else PagLoadingState(); return }
    if (model.loading) Text("Kostenliste wird aktualisiert …")
    if (board.expenses.isEmpty()) Text("Noch keine Kosten.")
    board.expenses.forEach { expense ->
        FormOutlinedButton({ model.edit(expense) }, modifier = Modifier.fillMaxWidth().semantics { onClick(label = "${expense.title} bearbeiten", action = null) }) {
            Column(Modifier.fillMaxWidth()) {
                PreparationHeading(expense.title); Text(EuroMoney.format(expense.totalCents))
                expense.payments.forEach { Text("${it.payerLabel} · ${EuroMoney.format(it.amountCents)}") }
            }
        }
    }
}
@Composable internal fun CompletedExpenseWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    FormOutlinedButton(model::closeCompletedExpenses, enabled = !model.expenses.busy) { Text("‹ Zur abgeschlossenen Aktion") }
    val content = model.workspace
    if (content == null) { if (model.workspaceError) PagErrorState() else PagLoadingState(); return }
    ExpenseSection(model.expenses, content.persons)
}
