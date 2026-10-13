package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import org.pag.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val protocolQuickKeys = setOf("POLICE_ARRIVED", "ORDER_OR_ANNOUNCEMENT", "DEADLINE_SET", "EVICTION_ANNOUNCED", "FORCE_THREATENED", "EVICTION_STARTED")
internal fun protocolClassification(value: ProtocolClassification) = if (value == ProtocolClassification.KEY_EVENT) "Schlüsselereignis" else "Relevanter Eintrag"
internal fun protocolRevisionLabel(value: ProtocolRevisionType) = when (value) {
    ProtocolRevisionType.ORIGINAL -> "Original"
    ProtocolRevisionType.CORRECTION -> "Korrigiert"
    ProtocolRevisionType.ADDENDUM -> "Ergänzt"
    ProtocolRevisionType.MARKED_MISTAKEN -> "Irrtümlich markiert"
}
internal fun protocolSource(value: ProtocolSourceType?) = when (value) {
    null -> "Keine Angabe"
    ProtocolSourceType.OWN_OBSERVATION -> "Eigene Beobachtung"
    ProtocolSourceType.REPORTED -> "Mitgeteilt"
    ProtocolSourceType.OFFICIAL_ANNOUNCEMENT -> "Behördliche Ansage"
    ProtocolSourceType.UNCLEAR -> "Unklar"
}
internal fun protocolIdentifier(value: ProtocolOfficialIdentifierType) = when (value) {
    ProtocolOfficialIdentifierType.SERVICE_OR_ID_NUMBER -> "Dienst-/Kennnummer"
    ProtocolOfficialIdentifierType.DISPLAYED_NAME -> "Name laut sichtbarer Kennzeichnung / eigener Wahrnehmung"
    ProtocolOfficialIdentifierType.UNIT_OR_VEHICLE_ID -> "Einheits-/Fahrzeugkennung"
    ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID -> "Sonstige sichtbare amtliche Kennung"
}
internal fun protocolSummaryTime(time: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    val local = Instant.ofEpochMilli(time).atZone(zone)
    return local.format(DateTimeFormatter.ofPattern(if (local.toLocalDate() == today) "HH:mm" else "dd.MM.yyyy HH:mm"))
}
@Composable internal fun ProtocolSection(model: ProtocolModel, persons: List<Person>) {
    model.error?.let { Text(protocolFailure(it)) }
    model.validation?.let { Text(it) }
    if (model.form) { ProtocolForm(model, persons); return }
    if (model.detailLoading && model.detail == null) { PagLoadingState(); return }
    if (model.entryId != null && model.detail == null) { PagErrorState(); FormOutlinedButton({ model.currentVersion() }) { Text("Erneut laden") }; return }
    val detail = model.detail
    if (detail != null) {
        if (model.detailLoading) Text("Eintrag wird aktualisiert …")
        val shown = model.historical ?: detail.current
        ProtocolRevisionContent(shown)
        if (model.historical != null) return
        if (model.error == ProtocolFailure.STALE) FormOutlinedButton(model::currentVersion, enabled = !model.busy) { Text("Zur aktuellen Fassung") }
        if (model.writable) {
            FormButton({ model.revise(ProtocolRevisionType.CORRECTION) }, enabled = !model.busy && !model.detailLoading && model.error != ProtocolFailure.STALE) { Text("Korrigieren") }
            FormOutlinedButton({ model.revise(ProtocolRevisionType.ADDENDUM) }, enabled = !model.busy && !model.detailLoading && model.error != ProtocolFailure.STALE) { Text("Ergänzen") }
            if (!detail.current.revision.mistaken) {
                if (model.confirmingMistaken) {
                    Text("Der Eintrag wird nicht gelöscht. PaG fügt eine neue Fassung hinzu und markiert den Eintrag als irrtümlich.")
                    FormButton(model::markMistaken, enabled = !model.busy && model.error != ProtocolFailure.STALE) { Text("Irrtümlich markieren") }
                    FormOutlinedButton(model::cancelMistaken, enabled = !model.busy) { Text("Abbrechen") }
                } else FormOutlinedButton(model::requestMistaken, enabled = !model.busy && !model.detailLoading && model.error != ProtocolFailure.STALE) { Text("Als irrtümlich markieren") }
            }
        }
        val old = detail.revisions.dropLast(1)
        if (old.isNotEmpty()) {
            FormOutlinedButton(model::toggleHistory, modifier = Modifier.semantics { stateDescription = if (model.history) "Ausgeklappt" else "Eingeklappt" }, enabled = !model.busy) { Text("Frühere Fassungen (${old.size})") }
            if (model.history) old.forEach { revision -> FormOutlinedButton({ model.oldRevision(revision) }, enabled = !model.busy) {
                Column { Text("Fassung ${revision.revision.revisionNumber} · ${protocolRevisionLabel(revision.revision.revisionType)}")
                    Text("Erfasst am ${formattedTime(revision.revision.recordedAt)}")
                    Text("Ereigniszeit: ${formattedTime(revision.revision.input.occurredAt)}") }
            } }
        }
        return
    }
    if (model.writable) {
        Text("Einträge dokumentieren Beobachtungen und Angaben. Die Kategorien sind keine rechtliche Bewertung.")
        ProtocolRules.templates.filter { it.key in protocolQuickKeys }.forEach { template -> FormOutlinedButton({ model.create(template.key) }) { Text(template.title) } }
        FormButton({ model.create() }) { Text("+ Relevanter Eintrag") }
        FormOutlinedButton({ model.create("CUSTOM_KEY_EVENT") }) { Text("Eigenes Schlüsselereignis") }
        FormOutlinedButton(model::toggleTemplates, modifier = Modifier.semantics { stateDescription = if (model.moreTemplates) "Ausgeklappt" else "Eingeklappt" }) { Text("Weitere Schlüsselereignisse") }
        if (model.moreTemplates) ProtocolRules.templates.filter { it.key !in protocolQuickKeys && it.key != "CUSTOM_KEY_EVENT" }.forEach { template -> FormOutlinedButton({ model.create(template.key) }) { Text(template.title) } }
    }
    PreparationHeading("Chronologie")
    FormOutlinedButton(model::refreshFromInteraction, enabled = !model.loading) { Text("Chronologie aktualisieren") }
    ProtocolFilter.entries.forEach { filter -> FormOutlinedButton({ model.changeFilter(filter) }, modifier = Modifier.semantics { selected = model.filter == filter }) {
        Text((if (model.filter == filter) "✓ " else "") + if (filter == ProtocolFilter.ALL) "Alle" else "Schlüsselereignisse")
    } }
    val timeline = model.filtered
    if (timeline == null) { if (model.error != null) PagErrorState() else PagLoadingState(); return }
    if (model.loading) Text("Chronologie wird aktualisiert …")
    if (timeline.isEmpty()) Text(if (model.filter == ProtocolFilter.ALL) "Noch keine Protokolleinträge." else "Keine Schlüsselereignisse erfasst.")
    timeline.forEach { item -> FormOutlinedButton({ model.open(item.entryId) }, modifier = Modifier.fillMaxWidth().semantics { onClick(label = "${item.title} öffnen", action = null) }) {
        Column(Modifier.fillMaxWidth()) {
            Text(protocolClassification(item.classification))
            PreparationHeading(item.title)
            Text("Ereigniszeit: ${formattedTime(item.occurredAt)}")
            if (item.occurredAtApproximate) Text("Zeitpunkt ungefähr")
            if (item.mistaken) Text("Irrtümlich markiert")
            if (item.revisionType != ProtocolRevisionType.ORIGINAL && item.revisionType != ProtocolRevisionType.MARKED_MISTAKEN) Text(protocolRevisionLabel(item.revisionType))
        }
    } }
}
@Composable private fun ProtocolRevisionContent(value: ProtocolRevisionDetail) {
    val revision = value.revision; val input = revision.input
    Text(protocolClassification(input.classification)); PreparationHeading(input.title)
    Text("Ereigniszeit: ${formattedTime(input.occurredAt)}")
    if (input.occurredAtApproximate) Text("Zeitpunkt ungefähr")
    Text("Erfasst am ${formattedTime(revision.recordedAt)}")
    if (revision.revisionType != ProtocolRevisionType.ORIGINAL) Text(protocolRevisionLabel(revision.revisionType))
    if (revision.mistaken && revision.revisionType != ProtocolRevisionType.MARKED_MISTAKEN) Text("Irrtümlich markiert")
    input.description?.let { PreparationHeading("Beschreibung"); Text(it) }
    input.placeLabel?.let { Text("Ort / lokale Bezeichnung: $it") }
    input.sourceType?.let { Text("Quellenart: ${protocolSource(it)}") }
    value.reportedBy?.let { Text("Mitgeteilt von: ${it.pseudonym}") }
    if (value.affectedPersons.isNotEmpty()) { PreparationHeading("Betroffene Personen"); value.affectedPersons.forEach { Text(it.pseudonym) } }
    input.announcementText?.let { PreparationHeading(if (input.announcementKind == ProtocolAnnouncementKind.WORDING) "Wortlaut laut Wahrnehmung" else "Sinngemäß"); Text(it) }
    input.statedLegalBasis?.let { PreparationHeading("Begründung / Rechtsgrundlage laut Polizei"); Text("Ungeprüfte Angabe; PaG nimmt keine rechtliche Bewertung vor."); Text(it) }
    if (input.officialIdentifiers.isNotEmpty()) { PreparationHeading("Beobachtete amtliche Kennungen"); input.officialIdentifiers.forEach { Text("${protocolIdentifier(it.type)}: ${it.value}") } }
}
@Composable private fun ProtocolForm(model: ProtocolModel, persons: List<Person>) {
    val input = model.input ?: return
    PreparationHeading(when (model.revisionType) { ProtocolRevisionType.CORRECTION -> "Eintrag korrigieren"; ProtocolRevisionType.ADDENDUM -> "Eintrag ergänzen"; else -> "${protocolClassification(input.classification)} erfassen" })
    fun change(value: ProtocolInput) = model.change(value)
    if (model.revisionType == ProtocolRevisionType.CORRECTION) {
        ProtocolClassification.entries.forEach { c -> FormOutlinedButton({ change(input.copy(classification = c)) }, modifier = Modifier.semantics { selected = input.classification == c }, enabled = !model.busy) { Text((if (input.classification == c) "✓ " else "") + protocolClassification(c)) } }
        if (model.expected?.revision?.mistaken == true) ProtocolToggle("Irrtumsmarkierung zurücknehmen", model.clearMistaken, !model.busy, model::toggleClearMistaken)
    }
    FormTextField(input.title, { change(input.copy(title = it)) }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth(), enabled = !model.busy,
        autoFocus = model.revisionType == ProtocolRevisionType.ORIGINAL, keyboardOptions = productKeyboard(ProductTextKind.ACTION_TITLE),
        supportingText = { Text("Keine Namen oder Kennnummern im Titel. Maximal 120 Zeichen.") })
    key(model.expected?.revision?.id, model.revisionType) { DateTimeField("Ereigniszeit", model.eventTime, model.zone, model::touch, model::changeTime) }
    ProtocolToggle("Zeitpunkt ungefähr", input.occurredAtApproximate, !model.busy) { change(input.copy(occurredAtApproximate = !input.occurredAtApproximate)) }
    FormTextField(input.description.orEmpty(), { change(input.copy(description = it)) }, label = { Text("Beschreibung · optional (max. 2000 Zeichen)") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.DESCRIPTION))
    FormButton(model::save, enabled = !model.busy && model.error != ProtocolFailure.STALE) { Text(if (model.busy) "Wird gespeichert …" else "Speichern") }
    FormOutlinedButton(model::back, enabled = !model.busy) { Text("Abbrechen") }
    if (model.error == ProtocolFailure.STALE) FormOutlinedButton(model::currentVersion, enabled = !model.busy) { Text("Zur aktuellen Fassung · Eingaben verwerfen") }
    FormOutlinedButton(model::toggleFields, modifier = Modifier.semantics { stateDescription = if (model.moreFields) "Ausgeklappt" else "Eingeklappt" }, enabled = !model.busy) { Text("Weitere Angaben") }
    if (!model.moreFields) return
    FormTextField(input.placeLabel.orEmpty(), { change(input.copy(placeLabel = it)) }, label = { Text("Ort / lokale Bezeichnung · max. 80 Zeichen") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.PLACE))
    PreparationHeading("Quellenart")
    (listOf<ProtocolSourceType?>(null) + ProtocolSourceType.entries).forEach { source -> FormOutlinedButton({ change(input.copy(sourceType = source)) }, modifier = Modifier.semantics { selected = input.sourceType == source }, enabled = !model.busy) { Text((if (input.sourceType == source) "✓ " else "") + protocolSource(source)) } }
    if (input.sourceType == ProtocolSourceType.REPORTED) {
        PreparationHeading("Mitgeteilt von · optional")
        FormOutlinedButton({ change(input.copy(reportedByPersonId = null)) }, enabled = !model.busy) { Text("Keine Person") }
        persons.forEach { p -> FormOutlinedButton({ change(input.copy(reportedByPersonId = p.id)) }, modifier = Modifier.semantics { selected = input.reportedByPersonId == p.id }, enabled = !model.busy) { Text((if (input.reportedByPersonId == p.id) "✓ " else "") + p.pseudonym) } }
    }
    PreparationHeading("Betroffene Personen · optional")
    persons.forEach { p -> ProtocolToggle(p.pseudonym, p.id in input.affectedPersonIds, !model.busy) {
        change(input.copy(affectedPersonIds = if (p.id in input.affectedPersonIds) input.affectedPersonIds - p.id else input.affectedPersonIds + p.id))
    } }
    PreparationHeading("Ansage · optional")
    FormOutlinedButton({ change(input.copy(announcementText = null, announcementKind = null)) }, enabled = !model.busy) { Text("Keine Ansage / Ansage entfernen") }
    ProtocolAnnouncementKind.entries.forEach { kind -> FormOutlinedButton({ change(input.copy(announcementKind = kind, announcementText = input.announcementText.orEmpty())) }, enabled = !model.busy, modifier = Modifier.semantics { selected = input.announcementKind == kind }) { Text(if (kind == ProtocolAnnouncementKind.WORDING) "Wortlaut laut Wahrnehmung" else "Sinngemäß") } }
    if (input.announcementKind != null) FormTextField(input.announcementText.orEmpty(), { change(input.copy(announcementText = it)) }, label = { Text("Ansagetext · max. 2000 Zeichen") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.DESCRIPTION))
    FormTextField(input.statedLegalBasis.orEmpty(), { change(input.copy(statedLegalBasis = it)) }, label = { Text("Begründung / Rechtsgrundlage laut Polizei") }, supportingText = { Text("Ungeprüfte Angabe; PaG nimmt keine rechtliche Bewertung vor. Maximal 1000 Zeichen.") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.DESCRIPTION))
    PreparationHeading("Beobachtete amtliche Kennungen")
    input.officialIdentifiers.forEachIndexed { index, identifier ->
        PreparationHeading("Kennung ${index + 1}")
        ProtocolOfficialIdentifierType.entries.forEach { type -> FormOutlinedButton({ change(input.copy(officialIdentifiers = input.officialIdentifiers.mapIndexed { n, row -> if (n == index) row.copy(type = type) else row })) }, modifier = Modifier.semantics { selected = identifier.type == type }, enabled = !model.busy) { Text((if (identifier.type == type) "✓ " else "") + protocolIdentifier(type)) } }
        FormTextField(identifier.value, { value -> change(input.copy(officialIdentifiers = input.officialIdentifiers.mapIndexed { n, row -> if (n == index) row.copy(value = value) else row })) }, label = { Text("Wert der Kennung ${index + 1} · max. 120 Zeichen") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        FormOutlinedButton({ model.removeIdentifier(index) }, enabled = !model.busy) { Text("Kennung ${index + 1} entfernen") }
    }
    FormOutlinedButton(model::addIdentifier, enabled = !model.busy && input.officialIdentifiers.size < 32) { Text("+ Kennung hinzufügen") }
}
@Composable private fun ProtocolToggle(label: String, checked: Boolean, enabled: Boolean, change: () -> Unit) {
    FormOutlinedButton(change, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics { toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }) { Text((if (checked) "✓ " else "□ ") + label) }
}
