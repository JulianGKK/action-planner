package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import org.pag.domain.*

@Composable internal fun MemoryReportSection(model: MemoryReportModel, persons: List<Person>,showHeading:Boolean=true) {
    if(showHeading)PreparationHeading("Gedächtnisprotokolle")
    if (model.confirmingDiscard) {
        Text("Ungespeicherte Änderungen verwerfen?")
        FormButton(model::confirmDiscard) { Text("Änderungen verwerfen") }
        FormOutlinedButton(model::cancelDiscard) { Text("Weiter bearbeiten") }
        return
    }
    model.error?.let {
        Text(when (it) {
            OperationsFailure.REVISION_EXHAUSTED -> "Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    OperationsFailure.STALE -> "Der gespeicherte Bericht wurde inzwischen geändert. Deine aktuellen Eingaben bleiben erhalten."
            OperationsFailure.INVALID -> "Bitte Eingabegrenzen und offene Detailentscheidungen prüfen."
            OperationsFailure.NOT_ALLOWED -> "Dieser Schritt ist im aktuellen Berichts- oder Aktionsstatus nicht möglich."
            else -> "Gedächtnisprotokoll konnte nicht geladen oder gespeichert werden."
        })
        FormOutlinedButton(model::reload, enabled = !model.busy && !model.loading) { Text(if (it == OperationsFailure.STALE) "Zur aktuellen Berichtsliste · Eingaben verwerfen" else "Berichtsliste erneut laden · Eingaben verwerfen") }
    }
    if (model.loading && (model.mode != MemoryReportMode.LIST || model.headers == null)) { PagLoadingState(); return }
    PagReadStatus(model.loading,"Berichtsliste wird aktualisiert …")
    val enabled = !model.busy && !model.loading && model.error != OperationsFailure.STALE
    when (model.mode) {
        MemoryReportMode.LIST -> {
            FormButton(model::create, enabled = enabled) { Text("Neues Gedächtnisprotokoll") }
            FormOutlinedButton(model::reload, enabled = !model.busy && !model.loading) { Text("Berichtsliste aktualisieren") }
            val headers = model.headers ?: return
            if (headers.isEmpty()) Text("Noch keine Gedächtnisprotokolle")
            listOf(MemoryReportStatus.DRAFT to "Entwürfe", MemoryReportStatus.COMPLETED to "Abgeschlossen").forEach { (status, label) ->
                PreparationHeading(label)
                headers.filter { it.status == status }.forEach { h ->
                    FormOutlinedButton({ model.open(h.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(h.authorLabel)
                            Text(if (status == MemoryReportStatus.DRAFT) "Entwurf · ${h.lastSavedSectionOrdinal + 1} von 17 Abschnitten gespeichert" else "Abgeschlossen")
                            Text(if (status == MemoryReportStatus.DRAFT) "Gespeichert: ${formattedTime(h.updatedAt)}" else "Abgeschlossen: ${formattedTime(h.completedAt!!)}")
                            Text("${h.authorLabel} öffnen")
                        }
                    }
                }
            }
        }
        MemoryReportMode.SECTION -> {
            val section = MemoryQuestionnaireV1.sections[model.sectionOrdinal]
            Text("Abschnitt ${section.ordinal + 1} von 17")
            PreparationHeading(section.title)
            Text("Ungespeicherte Eingaben können bei Sperren verloren gehen. Bereits gespeicherte Abschnitte bleiben erhalten.")
            if (section.ordinal == 0) {
                FormTextField(model.authorLabel, model::changeAuthor, label = { Text("Kürzel / Bericht von") },
                    supportingText = { Text("Ein Pseudonym oder kurzes Kürzel genügt. 1–80 Zeichen.") }, modifier = Modifier.fillMaxWidth(), autoFocus = model.expected == null, enabled = enabled)
                if (model.authorLabel.isNotEmpty() && !MemoryReportRules.authorValid(model.authorLabel)) Text("Bitte ein Kürzel mit 1–80 Zeichen eingeben.")
                PreparationHeading("Aktionsperson übernehmen")
                persons.forEach { p -> FormOutlinedButton({ model.changeAuthor(p.pseudonym) }, enabled = enabled) { Text(p.pseudonym) } }
                Text("Beschreibe zunächst in deinen eigenen Worten, was passiert ist. Du musst nicht alles wissen oder chronologisch erinnern.")
            }
            if (section.key == "G") Text("Hier geht es darum, welche Fragen dir gestellt wurden. Du musst keine eigenen Gesundheitsdaten oder Diagnosen angeben.")
            MemoryQuestionnaireV1.section(section.ordinal).forEach { q ->
                key(q.key) {
                    val value = model.answers[q.key]
                    val active = q.dependency?.active(model.combinedAnswers) != false
                    if (active || value != null) {
                        PreparationHeading(q.text)
                        if (q.hint.isNotEmpty()) Text(q.hint)
                        q.dependency?.contextHint?.let { Text(it) }
                        if (!active && value != null) {
                            Text("Diese Detailangabe passt nicht mehr zur aktuellen Auswahl.")
                            Text("Aktuelle Auswahl: ${MemoryAnswerCodec.label(q.dependency!!.parentKey, model.combinedAnswers[q.dependency!!.parentKey])}")
                            Text(MemoryAnswerCodec.label(q.key, value))
                            FormOutlinedButton({ model.keepDetail(q.key) }, enabled = enabled,
                                modifier = Modifier.semantics { selected = q.key in model.decisions }) { Text("Detail behalten") }
                            FormOutlinedButton({ model.removeDetail(q.key) }, enabled = enabled) { Text("Detail entfernen") }
                        } else MemoryQuestionInput(q, value, enabled) { model.changeAnswer(q.key, it) }
                    }
                }
            }
            if (model.unresolved.isNotEmpty()) Text("Bitte für jede nicht mehr passende Detailangabe Behalten oder Entfernen wählen.")
            FormOutlinedButton(model::back, enabled = !model.busy) { Text("‹ Zurück") }
            FormButton(model::save, enabled = enabled && model.sectionValid) { Text(if (model.busy) "Wird gespeichert …" else "Speichern und weiter") }
        }
        MemoryReportMode.SUMMARY, MemoryReportMode.DETAIL -> {
            val detail = model.detail ?: return
            PreparationHeading(if (model.mode == MemoryReportMode.SUMMARY) "Zusammenfassung" else "Gedächtnisprotokoll")
            Text(detail.header.authorLabel); Text("Fragebogen Version ${detail.header.questionnaireVersion}")
            detail.header.completedAt?.let { Text("Abgeschlossen: ${formattedTime(it)}") }
            MemoryQuestionnaireV1.sections.forEach { section ->
                PreparationHeading(section.title)
                MemoryQuestionnaireV1.section(section.ordinal).forEach { q ->
                    Text(q.text); Text(MemoryAnswerCodec.label(q.key, detail.answers[q.key]))
                    if (detail.answers[q.key] != null && q.dependency?.active(detail.answers) == false) {
                        Text("Bewusst behaltene Detailangabe. Aktuelle Auswahl: ${MemoryAnswerCodec.label(q.dependency!!.parentKey, detail.answers[q.dependency!!.parentKey])}")
                    }
                }
                if (detail.header.status == MemoryReportStatus.DRAFT) FormOutlinedButton({ model.editSection(section.ordinal) }, enabled = enabled) { Text("Abschnitt ${section.key} bearbeiten") }
            }
            detail.addenda.forEach { a -> PreparationHeading("Ergänzung ${a.sequence + 1}"); Text("Erfasst: ${formattedTime(a.recordedAt)}"); Text(a.content) }
            if (model.mode == MemoryReportMode.SUMMARY) {
                if (model.confirmingComplete) {
                    Text("Nach dem Abschluss können die bisherigen Antworten nicht mehr normal bearbeitet werden. Spätere Erinnerungen werden als Ergänzung hinzugefügt.")
                    FormButton(model::complete, enabled = enabled) { Text("Abschluss bestätigen") }
                    FormOutlinedButton(model::cancelComplete, enabled = !model.busy) { Text("Weiter prüfen") }
                } else FormButton(model::requestComplete, enabled = enabled) { Text("Gedächtnisprotokoll abschließen") }
            } else FormButton(model::startAddendum, enabled = enabled) { Text("Ergänzung hinzufügen") }
            FormOutlinedButton(model::back, enabled = !model.busy) { Text("‹ Zur Berichtsliste") }
        }
        MemoryReportMode.ADDENDUM -> {
            PreparationHeading("Ergänzung hinzufügen")
            FormTextField(model.addendum, model::changeAddendum, label = { Text("Ergänzung") }, supportingText = { Text("1–4000 Zeichen · bisherige Antworten bleiben unverändert") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
            FormButton(model::saveAddendum, enabled = enabled && MemoryReportRules.addendumValid(model.addendum)) { Text("Ergänzung speichern") }
            FormOutlinedButton(model::back, enabled = !model.busy) { Text("Abbrechen") }
        }
    }
}

@Composable private fun MemoryQuestionInput(q: MemoryQuestion, value: MemoryAnswerValue?, enabled: Boolean, change: (MemoryAnswerValue?) -> Unit) {
    if (q.type in setOf(MemoryAnswerType.SHORT_TEXT, MemoryAnswerType.LONG_TEXT, MemoryAnswerType.APPROX_DURATION, MemoryAnswerType.APPROX_TIME, MemoryAnswerType.OPTIONAL_COUNT)) {
        // Count text is kept as text while editing; invalid/partial input never reaches the repository.
        val text = when (value) { is MemoryAnswerValue.Text -> value.text; is MemoryAnswerValue.Count -> value.count.toString(); else -> "" }
        FormTextField(text, { typed ->
            val answer = if (typed.isEmpty()) null else if (q.type == MemoryAnswerType.OPTIONAL_COUNT && typed.matches(Regex("[0-9]+")) && typed.toIntOrNull() != null) MemoryAnswerValue.Count(typed.toInt()) else MemoryAnswerValue.Text(typed)
            change(answer)
        }, label = { Text(q.text) }, supportingText = { Text(if (q.type == MemoryAnswerType.OPTIONAL_COUNT) "0–99.999 oder keine Angabe" else "Maximal ${q.limit} Zeichen") },
            modifier = Modifier.fillMaxWidth(), enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = if (q.type == MemoryAnswerType.OPTIONAL_COUNT) KeyboardType.Number else KeyboardType.Text))
    }
    if (value != null && MemoryAnswerCodec.encode(q.key, value) == null) Text("Bitte Eingabegrenze und Antwortformat prüfen.")
    q.options.forEach { option ->
        val chosen = when (value) { is MemoryAnswerValue.Option -> value.key == option.key; is MemoryAnswerValue.Multi -> option.key in value.keys; else -> false }
        val select = {
            if (q.type == MemoryAnswerType.MULTI_CHOICE) {
                val previous = (value as? MemoryAnswerValue.Multi)?.keys.orEmpty()
                val special = setOf("UNKNOWN", "UNCLEAR", "NO_ASSESSMENT")
                val next = if (chosen) previous - option.key else if (option.key in special) listOf(option.key) else previous.filterNot { it in special } + option.key
                change(next.takeIf { it.isNotEmpty() }?.let { MemoryAnswerValue.Multi(it) })
            } else change(if (chosen) null else MemoryAnswerValue.Option(option.key))
        }
        if (q.type == MemoryAnswerType.MULTI_CHOICE) {
            FormCheckboxButton(option.label, chosen, select, enabled)
        } else FormOutlinedButton(select, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { selected = chosen; role = Role.RadioButton }) {
            Text((if (chosen) "✓ " else "") + option.label)
        }
    }
    if (value != null) FormOutlinedButton({ change(null) }, enabled = enabled) { Text("Angabe entfernen") }
    else Text("Keine Angabe")
}
@Composable internal fun CompletedMemoryReportWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    FormOutlinedButton(model::closeCompletedMemoryReports, enabled = !model.memoryReports.busy) { Text("‹ Zur abgeschlossenen Aktion") }
    MemoryReportSection(model.memoryReports, model.persons)
}
