package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable fun ProductScreen(model: ProductViewModel) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("PaG", style = MaterialTheme.typography.headlineLarge)
                when (model.snapshot.state) {
                    SessionState.BLOCKED -> Text("Der geschützte Speicher kann nicht sicher verwendet werden. Es wurden keine Daten ersetzt oder repariert.")
                    SessionState.LOCKED -> {
                        Text("Gesperrt")
                        if (model.bootstrap == ProductStorageState.PARTIAL_OR_INVALID) {
                            Text("Der geschützte Speicher kann nicht sicher verwendet werden.")
                        } else if (model.bootstrap == ProductStorageState.EMPTY) {
                            Text("Es ist noch kein geschützter Speicher eingerichtet.")
                            Button(onClick = model::unlock) { Text("Geschützten Speicher einrichten") }
                        } else Button(onClick = model::unlock) { Text("Entsperren") }
                    }
                    SessionState.AUTHENTICATING -> Text("Bitte bestätige den Gerätesperrcode im Android-Dialog.")
                    SessionState.MAINTENANCE -> Text("Der geschützte Speicher wird geprüft …")
                    SessionState.READY -> {
                        TextButton(onClick = model::lock) { Text("Sperren") }
                        when {
                            model.creating || model.editing != null -> ActionForm(model)
                            model.selected != null -> {
                                val action = model.selected!!
                                Text(action.title, style = MaterialTheme.typography.headlineMedium)
                                Text(statusLabel(action.status))
                                Text(action.description)
                                Text("Beginn: ${formattedTime(action.startAt)}")
                                Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
                                action.completedAt?.let { Text("Abgeschlossen: ${formattedTime(it)}") }
                                model.changeError?.let { Text(changeErrorLabel(it)) }
                                if (model.confirming != null) {
                                    Text(if (action.status == ActionStatus.ACTIVE)
                                        "Aktion endgültig abschließen? Danach sind Bearbeitung und Wiedereröffnung ausgeschlossen."
                                        else "${advanceLabel(action.status)}? Dieser Statusschritt kann nicht rückgängig gemacht werden.")
                                    Button(onClick = model::confirmAdvance, enabled = !model.saving) { Text("Bestätigen") }
                                    TextButton(onClick = model::cancelAdvance, enabled = !model.saving) { Text("Abbrechen") }
                                } else {
                                    if (ActionLifecycle.mayEdit(action.status)) {
                                        OutlinedButton(onClick = model::edit) { Text("Bearbeiten") }
                                    }
                                    if (ActionLifecycle.next(action.status) != null) {
                                        Button(onClick = model::requestAdvance) { Text(advanceLabel(action.status)) }
                                    }
                                }
                                TextButton(onClick = model::back, enabled = !model.saving) { Text("Zur Aktionsliste") }
                            }
                            else -> {
                                Text("Aktionsliste", style = MaterialTheme.typography.headlineMedium)
                                if (model.actions.isEmpty()) Text("Noch keine Aktionen vorhanden.")
                                model.actions.forEach { action ->
                                    OutlinedButton(onClick = { model.detail(action.id) }, modifier = Modifier.fillMaxWidth()) {
                                        Column { Text(action.title); Text(statusLabel(action.status)) }
                                    }
                                }
                                Button(onClick = model::createScreen) { Text("Aktion planen") }
                            }
                        }
                    }
                }
            }
        }
    }
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
    OutlinedTextField(title, { model.interaction(); title = it }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(description, { model.interaction(); description = it }, label = { Text("Beschreibung") }, modifier = Modifier.fillMaxWidth())
    Text("Zeitzone: ${zone.id}")
    DateTimeField("Beginn", start, zone, model::interaction) { start = it }
    DateTimeField("Voraussichtliches Ende", end, zone, model::interaction) { end = it }
    error?.let { Text(it) }
    model.changeError?.let { Text(changeErrorLabel(it)) }
    Button(onClick = {
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
    TextButton(onClick = { if (existing == null) model.back() else model.cancelEdit() }, enabled = !model.saving) { Text("Abbrechen") }
}

/** Inline selectors keep the existing foreground/focus contract; no secondary window.
 * Explicit state construction with remember, never rememberSaveable for draft values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateTimeField(label: String, value: LocalDateTime?, zone: ZoneId,
    interaction: () -> Unit, chosen: (LocalDateTime) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = { interaction(); expanded = !expanded }) {
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
            TextButton(onClick = {
                interaction()
                date.selectedDateMillis?.let { millis ->
                    val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    chosen(day.atTime(time.hour, time.minute))
                    expanded = false
                }
            }, enabled = date.selectedDateMillis != null) { Text("Übernehmen") }
            TextButton(onClick = { interaction(); expanded = false }) { Text("Abbrechen") }
        }
    }
}
private fun selectedInstant(value: LocalDateTime?, zone: ZoneId): Long? {
    if (value == null) return null
    val offsets = zone.rules.getValidOffsets(value)
    // Do not silently shift nonexistent times or guess an offset at an overlap.
    if (offsets.size != 1) return null
    return value.toInstant(offsets.single()).toEpochMilli()
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
