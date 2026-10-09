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
                            model.creating -> ActionForm(model)
                            model.selected != null -> {
                                val action = model.selected!!
                                Text(action.title, style = MaterialTheme.typography.headlineMedium)
                                Text(statusLabel(action.status))
                                Text(action.description)
                                Text("Beginn: ${formattedTime(action.startAt)}")
                                Text("Voraussichtliches Ende: ${formattedTime(action.expectedEndAt)}")
                                TextButton(onClick = model::back) { Text("Zur Action-Liste") }
                            }
                            else -> {
                                Text("Actions", style = MaterialTheme.typography.headlineMedium)
                                if (model.actions.isEmpty()) Text("Noch keine Actions vorhanden.")
                                model.actions.forEach { action ->
                                    OutlinedButton(onClick = { model.detail(action.id) }, modifier = Modifier.fillMaxWidth()) {
                                        Column { Text(action.title); Text(statusLabel(action.status)) }
                                    }
                                }
                                Button(onClick = model::createScreen) { Text("Action anlegen") }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ActionForm(model: ProductViewModel) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val zone = remember { ZoneId.systemDefault() }
    var start by remember { mutableStateOf<LocalDateTime?>(null) }
    var end by remember { mutableStateOf<LocalDateTime?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Text("Action anlegen", style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(title, { model.interaction(); title = it }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(description, { model.interaction(); description = it }, label = { Text("Beschreibung") }, modifier = Modifier.fillMaxWidth())
    Text("Zeitzone: ${zone.id}")
    DateTimeField("Beginn", start, zone, model::interaction) { start = it }
    DateTimeField("Voraussichtliches Ende", end, zone, model::interaction) { end = it }
    error?.let { Text(it) }
    Button(onClick = {
        model.interaction()
        val startMillis = selectedInstant(start, zone)
        val endMillis = selectedInstant(end, zone)
        val input = if (startMillis != null && endMillis != null) NewAction(title, description, startMillis, endMillis) else null
        error = when {
            title.isBlank() || description.isBlank() -> "Bitte Titel und Beschreibung angeben."
            start == null || end == null -> "Bitte Datum und Uhrzeit für Beginn und Ende auswählen."
            input == null -> "Diese Uhrzeit ist durch eine Zeitumstellung nicht eindeutig. Bitte eine andere Uhrzeit wählen."
            !input.valid() -> "Das Ende darf nicht vor dem Beginn liegen."
            else -> null
        }
        if (error == null) model.save(input!!)
    }) { Text("Speichern") }
    TextButton(onClick = model::back) { Text("Abbrechen") }
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
