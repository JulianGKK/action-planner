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

internal fun activePeopleSummary(board:OperationalBoard):List<String> = board.presentation.summary

/** Existing P5a affected count, OPEN + IN_PROGRESS; no count claimed without a board. */
internal fun activeAffectedTaskLabel(tasks: TaskBoard?, personId: String): String? =
    tasks?.affectedCount(personId)?.takeIf { it > 0 }?.let { "$it offene Aufgabe" + if (it == 1) "" else "n" }

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
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun peopleDisplayTime(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    return now
}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ActivePeopleList(model: ProductViewModel, board: OperationalBoard) {
    val now = peopleDisplayTime()
    val zone = ZoneId.systemDefault()
    activePeopleSummary(board).forOriginal { Text(it) }
    board.persons.forOriginal { person -> ActivePersonListRow(model,board,person,now,zone) }

}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ActivePersonListRow(model:ProductViewModel,board:OperationalBoard,person:OperationalPerson,now:Long,zone:ZoneId) {
        val row=model.personRow(person)?:return
    LocalProductObservation.current?.let{observer->androidx.compose.runtime.SideEffect{observer("PERSON_ROW")}}
        val card=row.card
        val semanticsLabel=row.semantics
        FormOutlinedButton(row.focus, contentEpoch=now/60_000,enabled = model.activeNavigationEnabled,
            modifier = Modifier.fillMaxWidth().originalSemantics { onClick(label = semanticsLabel, action = null) }) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.name, Modifier.then(productHeadingSemantics), style = MaterialTheme.typography.titleLarge)
                Text(card.situation)
                Text(card.function)
                row.updated(now)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                row.affected?.let { Text(it) }
            }
        }
}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ActivePersonWorkspace(model: ProductViewModel, board: OperationalBoard) {
    val person = model.operations.person ?: return
    val row=model.personRow(person)?:return
    val card=row.card
    val personId=card.id
    val now=peopleDisplayTime()
    PreparationHeading(card.name)
    Text(row.situation)
    Text(row.function)
    row.updated(now)?.let { Text(it) }
    FormButton({ model.operations.update(personId) }, enabled = model.activeNavigationEnabled) { Text("Lage ändern") }
    FormButton({model.operations.changeFunction()}, enabled = model.activeNavigationEnabled && person.state?.status == OperationalStatus.ON_SITE) { Text("Funktion ändern") }
    if (person.state?.status != OperationalStatus.ON_SITE) Text("Eine Funktion kann bei „Vor Ort“ zugeordnet werden.")
    FormOutlinedButton(row.detail, enabled = model.activeNavigationEnabled) { Text("Personendetails") }
    row.affected?.let { Text(it) }
    if (model.tasks.board != null) FormOutlinedButton({ model.activePersonTasks(personId, true) }, enabled = model.activeNavigationEnabled) { Text("Aufgabe hinzufügen") }
    if ((model.tasks.board?.affectedCount(card.id) ?: 0) > 0)
        FormOutlinedButton({ model.activePersonTasks(personId, false) }, enabled = model.activeNavigationEnabled) { Text("Aufgaben ansehen") }
}
