package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable fun ProductScreen(model: ProductViewModel) {
    MaterialTheme {
        val screen=listOf(model.snapshot.state,model.snapshot.generation,model.selected?.id,model.selectedPerson?.id,
            model.actionListState::class,model.personDetailLoading,model.creating,model.editing?.id,model.personForm,model.sensitiveSetup,model.sensitiveArea,model.sensitiveOpened?.grant,model.operations.screen,model.operations.personId,model.tasks.form,model.tasks.selected?.id,model.tasks.filter)
        ProductInputScope(screen) {
            LaunchedEffect(model.snapshot.state, model.snapshot.generation) {
                if (model.snapshot.state == SessionState.READY) while (true) {
                    model.refreshCountdown()
                    delay(1000)
                }
            }
            Surface(Modifier.fillMaxSize()) {
                if (model.snapshot.state == SessionState.LOCKED) {
                    PagLockedScreen(model.bootstrap, model::unlock)
                } else {
                    Column(Modifier.safeDrawingPadding()) {
                        if (model.snapshot.state == SessionState.READY) {
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                PagSessionCountdown(model.remainingMillis)
                                PagSecondaryButton("Sperren", model::lock, modifier=Modifier)
                            }
                        }
                        PagWorkspace(model.feedback.takeIf { model.snapshot.state == SessionState.READY },
                            model::dismissFeedback, Modifier.fillMaxWidth().weight(1f)) {
                        val scroll=rememberScrollState()
                        LaunchedEffect(model.tasks.form,model.tasks.selected?.id,model.tasks.filter){scroll.scrollTo(0)}
                        Column(Modifier.padding(24.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (model.snapshot.state) {
                                SessionState.BLOCKED -> PagErrorState()
                                SessionState.LOCKED -> Unit
                                SessionState.AUTHENTICATING -> Text("Bitte bestätige den Gerätesperrcode im Android-Dialog.")
                                SessionState.MAINTENANCE -> PagLoadingState()
                                SessionState.READY -> {
                                    when {
                                        model.creating || model.editing != null -> ActionForm(model)
                                        model.detailLoading || model.personDetailLoading -> PagLoadingState()
                                        model.selected != null && model.workspaceError -> PagErrorState()
                                        model.selected != null && model.personsVisible && model.workspace == null -> PagLoadingState()
                                        model.selected != null -> {
                                            val action = model.selected!!
                                            Text(action.title, style = MaterialTheme.typography.headlineMedium)
                                            Text(statusLabel(action.status))
                                            var showActionFields by remember(action.id, action.status) { mutableStateOf(action.status != ActionStatus.ACTIVE) }
                                            if (action.status == ActionStatus.ACTIVE) FormTextButton({ model.interaction(); showActionFields = !showActionFields }) { Text("Aktionsdetails") }
                                            if (showActionFields) {
                                                Text(action.description)
                                                Text("Beginn: ${formattedTime(action.startAt)}")
                                                Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
                                                action.completedAt?.let { Text("Abgeschlossen: ${formattedTime(it)}") }
                                            }
                                            if (model.personsVisible && model.confirming == null) PersonsSection(model)
                                            model.changeError?.let { Text(changeErrorLabel(it)) }
                                            if (model.confirming != null) {
                                                Text(if (action.status == ActionStatus.ACTIVE)
                                                "Aktion endgültig abschließen? Danach sind Bearbeitung und Wiedereröffnung ausgeschlossen."
                                                else "${advanceLabel(action.status)}? Dieser Statusschritt kann nicht rückgängig gemacht werden.")
                                                FormButton(onClick = model::confirmAdvance, enabled = !model.saving) { Text("Bestätigen") }
                                                FormOutlinedButton(onClick = model::cancelAdvance, enabled = !model.saving) { Text("Abbrechen") }
                                            } else if (!model.personForm && model.selectedPerson == null && !model.personSaving && model.operations.screen == OperationsScreen.OVERVIEW && !model.tasks.form && model.tasks.selected==null) {
                                                if (ActionLifecycle.mayEdit(action.status)) {
                                                    FormOutlinedButton(onClick = model::edit) { Text("Bearbeiten") }
                                                }
                                                if (ActionLifecycle.next(action.status) != null) {
                                                    FormButton(onClick = model::requestAdvance) { Text(advanceLabel(action.status)) }
                                                }
                                            }
                                            FormOutlinedButton(onClick = model::back, enabled = !model.saving && !model.personSaving) { Text("Zur Aktionsliste") }
                                        }
                                        else -> {
                                            if (model.actionListState != ActionListState.Loading) Text("Aktionsliste", style = MaterialTheme.typography.headlineMedium)
                                            when (val state = model.actionListState) {
                                                ActionListState.Loading -> PagLoadingState()
                                                ActionListState.Empty -> PagEmptyState(model::createScreen)
                                                ActionListState.Error -> PagErrorState()
                                                is ActionListState.Content -> {
                                                    state.actions.forEach { action ->
                                                        PagSecondaryButton("${action.title} · ${statusLabel(action.status)}", { model.detail(action.id) }, modifier=Modifier.fillMaxWidth())
                                                    }
                                                    PagPrimaryButton("Aktion planen", model::createScreen)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}


@Composable private fun PersonsSection(model: ProductViewModel) {
    if (model.operations.screen != OperationsScreen.OVERVIEW) { OperationsSection(model); return }
    if (model.selected?.status != ActionStatus.ACTIVE) Text("Personen", style = MaterialTheme.typography.titleLarge)
    model.personError?.let { Text(personErrorLabel(it)) }
    when {
        model.personForm -> {
            val existing = model.editingPerson
            var pseudonym by remember(existing?.id) { mutableStateOf(existing?.pseudonym ?: "") }
            Text(if (existing == null) "Person hinzufügen" else "Person bearbeiten")
            FormTextField(pseudonym, { model.interaction(); pseudonym = it },
                label = { Text("Pseudonym") }, autoFocus=existing==null, keyboardOptions=productKeyboard(ProductTextKind.PERSON), modifier = Modifier.fillMaxWidth(), enabled = !model.personSaving)
            FormButton(onClick = { model.savePerson(pseudonym) }, enabled = !model.personSaving) { Text("Speichern") }
            FormOutlinedButton(onClick = model::cancelPerson, enabled = !model.personSaving) { Text("Abbrechen") }
        }
        model.selectedPerson != null -> {
            Text(model.selectedPerson!!.pseudonym, style = MaterialTheme.typography.titleMedium)
            if (model.personsEditable) FormOutlinedButton(onClick = model::editPerson) { Text("Bearbeiten") }
            if (model.personsEditable) FormOutlinedButton({ model.operations.plan(model.selectedPerson!!.id) }) { Text("Rollen planen") }
            SensitiveSection(model)
            FormOutlinedButton(onClick = model::backToPersons) { Text("Zur Personenliste") }
        }
        else -> {
            if (model.personsLoading) Text("Personen werden geladen …")
            else {
                Text("Anzahl: ${model.persons.size}")
                if (model.persons.isEmpty()) Text("Noch keine Personen erfasst.")
                if (model.operations.board != null) OperationsSection(model)
                else model.persons.forEach { person ->
                    FormOutlinedButton(onClick = { model.personDetail(person.id) }, enabled = !model.saving) { Text(person.pseudonym) }
                }
                if (model.personsEditable && !model.tasks.form && model.tasks.selected==null) FormButton(onClick = model::addPerson, enabled = !model.saving) { Text("Person hinzufügen") }
            }
        }
    }
}
private fun personErrorLabel(reason: PersonRejection) = when (reason) {
    PersonRejection.ACTION_NOT_FOUND -> "Die Aktion ist nicht mehr vorhanden."
    PersonRejection.NOT_FOUND -> "Die Person ist in dieser Aktion nicht vorhanden."
    PersonRejection.NOT_ALLOWED -> "In diesem Status ist dieser Personenzugriff nicht möglich."
    PersonRejection.INVALID_PSEUDONYM -> "Bitte ein Pseudonym eingeben."
    PersonRejection.DUPLICATE_PSEUDONYM -> "Dieses Pseudonym ist in dieser Aktion bereits vergeben."
}
@Composable private fun ActionForm(model: ProductViewModel) {
    val existing = model.editing
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var description by remember(existing?.id) { mutableStateOf(existing?.description ?: "") }
    val zone = remember { ZoneId.systemDefault() }
    var start by remember(existing?.id) { mutableStateOf(existing?.startAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }) }
    var end by remember(existing?.id) { mutableStateOf(existing?.expectedEndAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }) }
    var error by remember { mutableStateOf<String?>(null) }
    Text(if (existing == null) "Aktion planen" else "Aktion bearbeiten", style = MaterialTheme.typography.headlineMedium)
    FormTextField(title, { model.interaction(); title = it }, label = { Text("Titel") }, keyboardOptions=productKeyboard(ProductTextKind.ACTION_TITLE), imeAction = androidx.compose.ui.text.input.ImeAction.Next, modifier = Modifier.fillMaxWidth())
    FormTextField(description, { model.interaction(); description = it }, label = { Text("Beschreibung") }, keyboardOptions=productKeyboard(ProductTextKind.DESCRIPTION), modifier = Modifier.fillMaxWidth())
    Text("Zeitzone: ${zone.id}")
    DateTimeField("Beginn", start, zone, model::interaction) { start = it }
    DateTimeField("Voraussichtliches Ende", end, zone, model::interaction) { end = it }
    error?.let { Text(it) }
    model.changeError?.let { Text(changeErrorLabel(it)) }
    FormButton(onClick = {
        model.interaction()
        val startMillis = if (existing != null && start == Instant.ofEpochMilli(existing.startAt).atZone(zone).toLocalDateTime())
            existing.startAt else selectedInstant(start, zone)
        val endMillis = if (existing != null && end == Instant.ofEpochMilli(existing.expectedEndAt).atZone(zone).toLocalDateTime())
            existing.expectedEndAt else selectedInstant(end, zone)
        val input = if (startMillis != null && endMillis != null) NewAction(title, description, startMillis, endMillis) else null
        error = when {
            title.isBlank() || description.isBlank() -> "Bitte Titel und Beschreibung angeben."
            start == null || end == null -> "Bitte Datum und Uhrzeit für Beginn und Ende auswählen."
            input == null -> "Diese Uhrzeit ist durch eine Zeitumstellung nicht eindeutig. Bitte eine andere Uhrzeit wählen."
            !input.valid() -> "Das Ende darf nicht vor dem Beginn liegen."
            else -> null
        }
        if (error == null) model.save(input!!)
    }, enabled = !model.saving) { Text("Speichern") }
    FormOutlinedButton(onClick = { if (existing == null) model.back() else model.cancelEdit() }, enabled = !model.saving) { Text("Abbrechen") }
}

/** Inline selectors keep the existing foreground/focus contract; no secondary window.
 * Explicit state construction with remember, never rememberSaveable for draft values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateTimeField(label: String, value: LocalDateTime?, zone: ZoneId,
    interaction: () -> Unit, chosen: (LocalDateTime) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.titleMedium)
    FormOutlinedButton(onClick = { interaction(); expanded = !expanded }) {
        Text(value?.format(localTimeFormat()) ?: "Datum und Uhrzeit auswählen")
    }
    if (expanded) {
        val initial = remember { value ?: LocalDateTime.now(zone).withSecond(0).withNano(0) }
        val date = remember { DatePickerState(Locale.getDefault(),
            initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) }
        val time = remember { TimePickerState(initial.hour, initial.minute, true) }
        DatePicker(state = date, showModeToggle = false)
        TimePicker(state = time)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormOutlinedButton(onClick = {
                interaction()
                date.selectedDateMillis?.let { millis ->
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    chosen(day.atTime(time.hour, time.minute))
                    expanded = false
                }
            }, enabled = date.selectedDateMillis != null) { Text("Übernehmen") }
            FormOutlinedButton(onClick = { interaction(); expanded = false }) { Text("Abbrechen") }
        }
    }
}
private fun localTimeFormat() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
private fun formattedTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    return "${Instant.ofEpochMilli(millis).atZone(zone).format(localTimeFormat())} (${zone.id})"
}
private fun statusLabel(status: ActionStatus) = when (status) {
    ActionStatus.DRAFT -> "Entwurf"
    ActionStatus.PREPARATION -> "Vorbereitung"
    ActionStatus.ACTIVE -> "Aktiv"
    ActionStatus.COMPLETED -> "Abgeschlossen"
    ActionStatus.DELETION_PENDING -> "Löschung ausstehend"
}

private fun advanceLabel(status: ActionStatus): String = when (ActionLifecycle.next(status)) {
    ActionStatus.PREPARATION -> "Vorbereitung beginnen"
    ActionStatus.ACTIVE -> "Aktion starten"
    ActionStatus.COMPLETED -> "Aktion abschließen"
    else -> ""
}
private fun changeErrorLabel(reason: ActionChangeRejection) = when (reason) {
    ActionChangeRejection.NOT_FOUND -> "Die Aktion ist nicht mehr vorhanden."
    ActionChangeRejection.STALE -> "Die Aktion wurde zwischenzeitlich geändert. Bitte erneut aus der Liste öffnen."
    ActionChangeRejection.NOT_EDITABLE -> "In diesem Status kann die Aktion nicht bearbeitet werden."
    ActionChangeRejection.INVALID_TRANSITION -> "Dieser Statusschritt ist nicht möglich."
    ActionChangeRejection.INVALID_FIELDS -> "Bitte die eingegebenen Felder prüfen."
}
