package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.pag.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ActivePersonCard(val id: String, val name: String, val situation: String, val function: String, val updated: String?)

internal fun activePeopleSummary(board: OperationalBoard): List<String> = if (board.persons.isEmpty()) listOf("Noch keine Personen") else buildList {
    add("${board.persons.size} insgesamt")
    listOf(OperationalStatus.ON_SITE to "vor Ort", OperationalStatus.IN_CUSTODY to "in Gewahrsam",
        OperationalStatus.NOT_REACHABLE to "nicht erreichbar", OperationalStatus.NO_STATUS to "ohne Lage").forEach { (status, label) ->
        val count = board.persons.count { (it.state?.status ?: OperationalStatus.NO_STATUS) == status }
        if (count > 0) add("$count $label")
    }
}

/** Calendar boundaries take precedence; NO_STATUS cannot prove a human observation. */
internal fun activeUpdatedLabel(state: PersonOperationalState?, now: Long, zone: ZoneId): String? {
    if (state == null || state.status == OperationalStatus.NO_STATUS || state.updatedAt <= 0 || state.updatedAt > now) return null
    val updated = Instant.ofEpochMilli(state.updatedAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = updated.format(DateTimeFormatter.ofPattern("HH:mm"))
    if (updated.toLocalDate() != today) return "Zuletzt aktualisiert am ${updated.format(DateTimeFormatter.ofPattern("dd.MM."))} um $time"
    val minutes = (now - state.updatedAt).coerceAtLeast(0) / 60_000
    return when {
        minutes == 0L -> "Gerade aktualisiert"
        minutes < 60 -> "Aktualisiert vor $minutes Min."
        else -> "Zuletzt aktualisiert um $time"
    }
}

internal fun activePersonCard(person: OperationalPerson, board: OperationalBoard, now: Long, zone: ZoneId): ActivePersonCard {
    val state = person.state
    val situation = operationalLabel(state?.status ?: OperationalStatus.NO_STATUS) +
        state?.currentPlaceLabel?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    val function = board.roles.find { it.id == state?.currentRoleId }?.name ?: "Keine Funktion"
    return ActivePersonCard(person.person.id, person.person.pseudonym, situation, function, activeUpdatedLabel(state, now, zone))
}

/** Display clock only. Composition disposal cancels the minute job; no model/port calls. */
@Composable private fun peopleDisplayTime(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    return now
}

@Composable internal fun ActivePeopleList(model: ProductViewModel, board: OperationalBoard) {
    val now = peopleDisplayTime()
    val zone = ZoneId.systemDefault()
    activePeopleSummary(board).forEach { Text(it) }
    board.persons.forEach { person ->
        val card = activePersonCard(person, board, now, zone)
        FormOutlinedButton({ model.operations.focus(card.id) }, enabled = model.activeNavigationEnabled,
            modifier = Modifier.fillMaxWidth().semantics { onClick(label = "${card.name} öffnen", action = null) }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                Text(card.situation)
                Text(card.function)
                card.updated?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable internal fun ActivePersonWorkspace(model: ProductViewModel, board: OperationalBoard) {
    val person = model.operations.person ?: return
    val card = activePersonCard(person, board, peopleDisplayTime(), ZoneId.systemDefault())
    PreparationHeading(card.name)
    Text("Aktuelle Lage: ${card.situation}")
    Text("Aktuelle Funktion: ${card.function}")
    card.updated?.let { Text(it) }
    FormButton({ model.operations.update(card.id) }, enabled = model.activeNavigationEnabled) { Text("Lage ändern") }
    FormButton(model.operations::changeFunction, enabled = model.activeNavigationEnabled && person.state?.status == OperationalStatus.ON_SITE) { Text("Funktion ändern") }
    if (person.state?.status != OperationalStatus.ON_SITE) Text("Eine Funktion kann bei „Vor Ort“ zugeordnet werden.")
    FormOutlinedButton({ model.personDetail(card.id) }, enabled = model.activeNavigationEnabled) { Text("Personendetails") }
    if (model.tasks.board != null) FormOutlinedButton({ model.activePersonTasks(card.id, true) }, enabled = model.activeNavigationEnabled) { Text("Aufgabe hinzufügen") }
    if ((model.tasks.board?.affectedCount(card.id) ?: 0) > 0)
        FormOutlinedButton({ model.activePersonTasks(card.id, false) }, enabled = model.activeNavigationEnabled) { Text("Aufgaben ansehen") }
}
