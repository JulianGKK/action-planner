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

private val protocolSources:List<ProtocolSourceType?> = listOf(null)+ProtocolSourceType.entries // code-only, never action payload
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
internal fun protocolEntryPhase(createdAfterCompletion: Boolean) = if (createdAfterCompletion) "Nach Abschluss erfasst" else null
internal fun protocolRevisionPhase(recordedAfterCompletion: Boolean) = if (recordedAfterCompletion) "Diese Fassung wurde nach Abschluss erfasst." else null
internal fun protocolTimelinePhase(item: ProtocolTimelineItem) = protocolEntryPhase(item.createdAfterCompletion)
    ?: if (item.recordedAfterCompletion) "Aktuelle Fassung nach Abschluss erfasst" else null

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ProtocolSection(model: ProtocolModel, persons: List<Person>) {
    model.error?.let { Text(protocolFailure(it)) }
    model.validation?.let { Text(it) }
    if (model.form) { ProtocolForm(model, persons); return }
    if (model.detailLoading && model.detail == null) { PagLoadingState(); return }
    if (model.entryId != null && model.detail == null) { PagErrorState(); FormOutlinedButton({ model.currentVersion() }) { Text("Erneut laden") }; return }
    val ui=model.ui()?:run{PagErrorState();return}
    val detail = model.detail
    if (detail != null) {
        if (model.detailLoading) Text("Eintrag wird aktualisiert …")
        val shown = model.historical ?: detail.current
        protocolEntryPhase(detail.entry.createdAfterCompletion)?.let { Text(it) }
        ProtocolRevisionContent(shown,ui.revisions[shown.revision.id]?:return, model.mode == ProtocolUiMode.COMPLETED_FOLLOW_UP)
        if (model.historical != null) return
        if (model.error == ProtocolFailure.STALE) FormOutlinedButton({model.currentVersion()}, enabled = !model.busy) { Text("Zur aktuellen Fassung") }
        if (model.writable) {
            FormButton({ model.revise(ProtocolRevisionType.CORRECTION) }, enabled = !model.busy && !model.detailLoading && model.error != ProtocolFailure.STALE) { Text("Korrigieren") }
            FormOutlinedButton({ model.revise(ProtocolRevisionType.ADDENDUM) }, enabled = !model.busy && !model.detailLoading && model.error != ProtocolFailure.STALE) { Text("Ergänzen") }
            if (!detail.current.revision.mistaken) {
                if (model.confirmingMistaken) {
                    Text("Der Eintrag wird nicht gelöscht. PaG fügt eine neue Fassung hinzu und markiert den Eintrag als irrtümlich.")
                    if (model.completedFollowUp) Text("Die neue Irrtumsmarkierung wird nach Abschluss erfasst.")
                    FormButton({model.markMistaken()}, enabled = !model.busy && model.error != ProtocolFailure.STALE) { Text("Irrtümlich markieren") }
                    FormOutlinedButton({model.cancelMistaken()}, enabled = !model.busy) { Text("Abbrechen") }
                } else FormOutlinedButton({model.requestMistaken()}, enabled = !model.busy && !model.detailLoading && model.error != ProtocolFailure.STALE) { Text("Als irrtümlich markieren") }
            }
        }
        val oldCount=detail.revisions.size-1
        if(oldCount>0){
            FormOutlinedButton({model.toggleHistory()},modifier=Modifier.originalSemantics {stateDescription=if(model.history)"Ausgeklappt" else "Eingeklappt"},enabled=!model.busy){Text(ui.history)}
            if(model.history)for(index in 0 until oldCount){
                val revision=detail.revisions[index];val id=revision.revision.id
                val display=ui.revisions[id]?:continue
                FormOutlinedButton({model.oldRevisionId(id)},enabled=!model.busy){
                    Column{Text(display.heading);Text(display.recorded);Text(display.event)}
                }
            }
        }
        return
    }
    if (model.writable) {
        Text("Einträge dokumentieren Beobachtungen und Angaben. Die Kategorien sind keine rechtliche Bewertung.")
        if (model.completedFollowUp) PreparationHeading("Schlüsselereignis nachtragen")
        ProtocolRules.templates.forOriginal { template -> if(template.key !in protocolQuickKeys)return@forOriginal; FormOutlinedButton({ model.create(template.key) }) { Text(template.title) } }
        FormButton({ model.create() }) { Text(if (model.completedFollowUp) "+ Nachtrag nach Abschluss" else "+ Relevanter Eintrag") }
        FormOutlinedButton({ model.create("CUSTOM_KEY_EVENT") }) { Text("Eigenes Schlüsselereignis") }
        FormOutlinedButton({model.toggleTemplates()}, modifier = Modifier.originalSemantics { stateDescription = if (model.moreTemplates) "Ausgeklappt" else "Eingeklappt" }) { Text("Weitere Schlüsselereignisse") }
        if (model.moreTemplates) ProtocolRules.templates.forOriginal { template -> if(template.key in protocolQuickKeys || template.key=="CUSTOM_KEY_EVENT")return@forOriginal; FormOutlinedButton({ model.create(template.key) }) { Text(template.title) } }
    }
    PreparationHeading("Chronologie")
    FormOutlinedButton({model.refreshFromInteraction()}, enabled = !model.loading) { Text("Chronologie aktualisieren") }
    ProtocolFilter.entries.forOriginal { filter -> FormOutlinedButton({ model.changeFilter(filter) }, modifier = Modifier.originalSemantics { selected = model.filter == filter }) {
        Text(originalText{(if(model.filter==filter)"✓ " else "")+if(filter==ProtocolFilter.ALL)"Alle" else "Schlüsselereignisse"})
    } }
    val timeline = model.filtered
    if (timeline == null) { if (model.error != null) PagErrorState() else PagLoadingState(); return }
    if (model.loading) Text("Chronologie wird aktualisiert …")
    if (timeline.isEmpty()) Text(if (model.filter == ProtocolFilter.ALL) "Noch keine Protokolleinträge." else "Keine Schlüsselereignisse erfasst.")
    timeline.forOriginal { item ->
        val id=item.entryId;val display=ui.rows[id]?:return@forOriginal
        FormOutlinedButton({ model.open(id) }, modifier = Modifier.fillMaxWidth().originalSemantics { onClick(label = display.semantics, action = null) }) {
        Column(Modifier.fillMaxWidth()) {
            Text(protocolClassification(item.classification))
            PreparationHeading(item.title)
            Text(display.event)
            protocolTimelinePhase(item)?.let { Text(it) }
            if (item.occurredAtApproximate) Text("Zeitpunkt ungefähr")
            if (item.mistaken) Text("Irrtümlich markiert")
            if (item.revisionType != ProtocolRevisionType.ORIGINAL && item.revisionType != ProtocolRevisionType.MARKED_MISTAKEN) Text(protocolRevisionLabel(item.revisionType))
        }
    } }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ProtocolRevisionContent(value: ProtocolRevisionDetail,display:ProtocolRevisionUi, followUp: Boolean) {
    val revision = value.revision; val input = revision.input
    Text(protocolClassification(input.classification)); PreparationHeading(input.title)
    Text(display.event)
    if (input.occurredAtApproximate) Text("Zeitpunkt ungefähr")
    Text(if(followUp)display.recordedFollow else display.recorded)
    protocolRevisionPhase(revision.recordedAfterCompletion)?.let { Text(it) }
    if (revision.revisionType != ProtocolRevisionType.ORIGINAL) Text(protocolRevisionLabel(revision.revisionType))
    if (revision.mistaken && revision.revisionType != ProtocolRevisionType.MARKED_MISTAKEN) Text("Irrtümlich markiert")
    input.description?.let { PreparationHeading("Beschreibung"); Text(it) }
    display.place?.let{Text(it)}
    display.source?.let{Text(it)}
    display.reported?.let{Text(it)}
    if (value.affectedPersons.isNotEmpty()) { PreparationHeading("Betroffene Personen"); value.affectedPersons.forOriginal { Text(it.pseudonym) } }
    input.announcementText?.let { PreparationHeading(if (input.announcementKind == ProtocolAnnouncementKind.WORDING) "Wortlaut laut Wahrnehmung" else "Sinngemäß"); Text(it) }
    input.statedLegalBasis?.let { PreparationHeading("Begründung / Rechtsgrundlage laut Polizei"); Text("Ungeprüfte Angabe; PaG nimmt keine rechtliche Bewertung vor."); Text(it) }
    if (input.officialIdentifiers.isNotEmpty()) { PreparationHeading("Beobachtete amtliche Kennungen"); display.identifiers.forOriginal{Text(it)} }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ProtocolForm(model: ProtocolModel, persons: List<Person>) {
    val input = model.input ?: return
    PreparationHeading(when (model.revisionType) { ProtocolRevisionType.CORRECTION -> "Eintrag korrigieren"; ProtocolRevisionType.ADDENDUM -> "Eintrag ergänzen"; else -> "${protocolClassification(input.classification)} erfassen" })
    if (model.completedFollowUp) {
        Text(when (model.revisionType) {
            ProtocolRevisionType.CORRECTION -> "Diese Korrektur wird als neue Fassung nach Abschluss gespeichert."
            ProtocolRevisionType.ADDENDUM -> "Diese Ergänzung wird als neue Fassung nach Abschluss gespeichert."
            else -> "Nach Abschluss erfasst"
        })
        Text("Ereigniszeit und Erfassungszeit können voneinander abweichen.")
    }
    if (model.revisionType == ProtocolRevisionType.CORRECTION) {
        ProtocolClassification.entries.forOriginal { c -> FormOutlinedButton({ model.classification(c) }, modifier = Modifier.originalSemantics { selected = input.classification == c }, enabled = !model.busy) { Text(originalText{(if(input.classification==c)"✓ " else "")+protocolClassification(c)}) } }
        if (model.expected?.revision?.mistaken == true) ProtocolToggle("Irrtumsmarkierung zurücknehmen", model.clearMistaken, !model.busy, model::toggleClearMistaken)
    }
    FormTextField(input.title, { model.title(it) }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth(), enabled = !model.busy,
        autoFocus = model.revisionType == ProtocolRevisionType.ORIGINAL, keyboardOptions = productKeyboard(ProductTextKind.ACTION_TITLE),
        supportingText = originalContent{ Text("Keine Namen oder Kennnummern im Titel. Maximal 120 Zeichen.") })
    key(model.expected?.revision?.id, model.revisionType) { DateTimeField("Ereigniszeit", model.eventTime, model.zone, model::touch, model::changeTime) }
    ProtocolToggle("Zeitpunkt ungefähr", input.occurredAtApproximate, !model.busy) { model.approximate() }
    FormTextField(input.description.orEmpty(), { model.description(it) }, label = { Text("Beschreibung · optional (max. 2000 Zeichen)") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.DESCRIPTION))
    ProtocolFormActions(model)
    if (model.error == ProtocolFailure.STALE) FormOutlinedButton({model.currentVersion()}, enabled = !model.busy) { Text("Zur aktuellen Fassung · Eingaben verwerfen") }
    FormOutlinedButton({model.toggleFields()}, modifier = Modifier.originalSemantics { stateDescription = if (model.moreFields) "Ausgeklappt" else "Eingeklappt" }, enabled = !model.busy) { Text("Weitere Angaben") }
    if (!model.moreFields) return
    FormTextField(input.placeLabel.orEmpty(), { model.place(it) }, label = { Text("Ort / lokale Bezeichnung · max. 80 Zeichen") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.PLACE))
    PreparationHeading("Quellenart")
    protocolSources.forOriginal { source -> FormOutlinedButton({ model.source(source) }, modifier = Modifier.originalSemantics { selected = input.sourceType == source }, enabled = !model.busy) { Text(originalText{(if(input.sourceType==source)"✓ " else "")+protocolSource(source)}) } }
    if (input.sourceType == ProtocolSourceType.REPORTED) {
        PreparationHeading("Mitgeteilt von · optional")
        FormOutlinedButton({ model.reportedBy(null) }, enabled = !model.busy) { Text("Keine Person") }
        persons.forOriginal { p -> val id=p.id;FormOutlinedButton({ model.reportedBy(id) }, modifier = Modifier.originalSemantics { selected = input.reportedByPersonId == p.id }, enabled = !model.busy) { Text(originalText(p.pseudonym.length.toLong()){(if(input.reportedByPersonId==p.id)"✓ " else "")+p.pseudonym}) } }
    }
    PreparationHeading("Betroffene Personen · optional")
    persons.forOriginal { p -> val id=p.id;ProtocolToggle(p.pseudonym, p.id in input.affectedPersonIds, !model.busy) {
        model.affected(id)
    } }
    PreparationHeading("Ansage · optional")
    FormOutlinedButton({ model.removeAnnouncement() }, enabled = !model.busy) { Text("Keine Ansage / Ansage entfernen") }
    ProtocolAnnouncementKind.entries.forOriginal { kind -> FormOutlinedButton({ model.announcementKind(kind) }, enabled = !model.busy, modifier = Modifier.originalSemantics { selected = input.announcementKind == kind }) { Text(if (kind == ProtocolAnnouncementKind.WORDING) "Wortlaut laut Wahrnehmung" else "Sinngemäß") } }
    if (input.announcementKind != null) FormTextField(input.announcementText.orEmpty(), { model.announcement(it) }, label = { Text("Ansagetext · max. 2000 Zeichen") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.DESCRIPTION))
    FormTextField(input.statedLegalBasis.orEmpty(), { model.legalBasis(it) }, label = { Text("Begründung / Rechtsgrundlage laut Polizei") }, supportingText = originalContent{ Text("Ungeprüfte Angabe; PaG nimmt keine rechtliche Bewertung vor. Maximal 1000 Zeichen.") }, enabled = !model.busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = productKeyboard(ProductTextKind.DESCRIPTION))
    PreparationHeading("Beobachtete amtliche Kennungen")
    input.officialIdentifiers.forOriginalIndexed { index, identifier ->
        PreparationHeading(originalText{"Kennung ${index+1}"})
        ProtocolOfficialIdentifierType.entries.forOriginal { type -> FormOutlinedButton({ model.identifierType(index,type) }, modifier = Modifier.originalSemantics { selected = identifier.type == type }, enabled = !model.busy) { Text(originalText{(if(identifier.type==type)"✓ " else "")+protocolIdentifier(type)}) } }
        FormTextField(identifier.value, { value -> model.identifierValue(index,value) }, label = { Text(originalText{"Wert der Kennung ${index+1} · max. 120 Zeichen"}) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        FormOutlinedButton({ model.removeIdentifier(index) }, enabled = !model.busy) { Text(originalText{"Kennung ${index+1} entfernen"}) }
    }
    FormOutlinedButton({model.addIdentifier()}, enabled = !model.busy && input.officialIdentifiers.size < 32) { Text("+ Kennung hinzufügen") }
    // Long optional sections need an equally reachable completion path at their end.
    ProtocolFormActions(model)
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ProtocolFormActions(model: ProtocolModel) {
    FormButton({model.save()}, enabled = !model.busy && model.error != ProtocolFailure.STALE) { Text(if (model.busy) "Wird gespeichert …" else "Speichern") }
    FormOutlinedButton({model.back()}, enabled = !model.busy) { Text("Abbrechen") }
}
@Composable private inline fun ProtocolToggle(label: String, checked: Boolean, enabled: Boolean, crossinline change: () -> Unit) {
    FormOutlinedButton(change, enabled = enabled, modifier = Modifier.fillMaxWidth().originalSemantics { toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }) { Text(originalText(label.length.toLong()){(if(checked)"✓ " else "□ ")+label}) }
}
