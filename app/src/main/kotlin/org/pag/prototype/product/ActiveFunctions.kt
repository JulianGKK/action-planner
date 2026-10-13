package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import org.pag.domain.Role

internal data class FunctionGroup(val role: Role, val people: List<OperationalPerson>)
internal data class ActiveFunctionContent(val summary: List<String>, val groups: List<FunctionGroup>,
    val without: List<OperationalPerson>, val other: List<Role>)

/** Display projection of the existing P4 distribution, in existing role/person order. */
internal fun activeFunctionPeople(board: OperationalBoard, roleId: String?) = board.persons.filter {
    it.state?.status == OperationalStatus.ON_SITE && it.state?.currentRoleId == roleId
}
internal fun activeFunctionName(board: OperationalBoard?, roleId: String?) =
    if (roleId == null) "Ohne Funktion" else board?.roles?.find { it.id == roleId }?.name ?: "Funktion nicht verfügbar"
internal fun activeFunctionContent(board: OperationalBoard): ActiveFunctionContent {
    val onSite = board.persons.filter { it.state?.status == OperationalStatus.ON_SITE }
    val with = onSite.count { it.state?.currentRoleId != null }
    val used = onSite.mapNotNull { it.state?.currentRoleId }.distinct().size
    val summary = if (onSite.isEmpty()) listOf("Niemand aktuell vor Ort") else listOf(
        "${onSite.size} ${if (onSite.size == 1) "Person" else "Personen"} vor Ort", "$with mit Funktion", "${onSite.size - with} ohne Funktion", "$used aktuell genutzte Funktionen")
    val groups = board.roles.mapNotNull { role -> activeFunctionPeople(board, role.id).takeIf { it.isNotEmpty() }?.let { FunctionGroup(role, it) } }
    return ActiveFunctionContent(summary, groups, activeFunctionPeople(board, null), board.roles.filter { board.roleCount(it.id) == 0 })
}

@Composable internal fun ActiveFunctionsOverview(model: ProductViewModel, board: OperationalBoard) {
    val ops = model.operations
    val content = activeFunctionContent(board)
    PreparationHeading("Wer übernimmt gerade was?")
    content.summary.forEach { Text(it) }
    content.groups.forEach { group -> FunctionCard(group.role.name, group.people, model) { ops.focusFunction(group.role.id) } }
    if (content.without.isNotEmpty()) FunctionCard("Ohne Funktion", content.without, model) { ops.focusFunction(null) }
    if (content.other.isNotEmpty()) {
        FormOutlinedButton(ops::toggleMoreFunctions, enabled = model.activeNavigationEnabled,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (ops.moreFunctionsExpanded) "Ausgeklappt" else "Eingeklappt" }) {
            Text("Weitere Funktionen (${content.other.size}) ${if (ops.moreFunctionsExpanded) "▴" else "▾"}")
        }
        if (ops.moreFunctionsExpanded) content.other.forEach { Text(it.name) }
    }
    FormOutlinedButton(ops::newFunction, enabled = model.activeNavigationEnabled, modifier = Modifier.fillMaxWidth()) { Text("+ Neue Funktion") }
}

@Composable private fun FunctionCard(name: String, people: List<OperationalPerson>, model: ProductViewModel, open: () -> Unit) {
    FormOutlinedButton(open, enabled = model.activeNavigationEnabled,
        modifier = Modifier.fillMaxWidth().semantics { onClick(label = "$name öffnen", action = null) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$name · ${people.size}", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text(people.joinToString(" · ") { it.person.pseudonym })
        }
    }
}

@Composable internal fun ActiveFunctionDetail(model: ProductViewModel, board: OperationalBoard) {
    val focus = model.operations.functionFocus ?: return
    PreparationHeading(activeFunctionName(board, focus.roleId))
    val people = activeFunctionPeople(board, focus.roleId)
    if (people.isEmpty()) Text(if (focus.roleId == null) "Derzeit niemand ohne Funktion vor Ort." else "Derzeit niemand in dieser Funktion vor Ort.")
    else {
        Text("${people.size} ${if (people.size == 1) "Person" else "Personen"} aktuell vor Ort")
        people.forEach { person ->
            FormOutlinedButton({ model.operations.focus(person.person.id) }, enabled = model.activeNavigationEnabled,
                modifier = Modifier.fillMaxWidth().semantics { onClick(label = "${person.person.pseudonym} öffnen", action = null) }) {
                Text(person.person.pseudonym, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable internal fun NewActiveFunction(ops: OperationsModel) {
    PreparationHeading("Neue Funktion")
    FormTextField(ops.newFunctionName, ops::changeNewFunctionName, label = { Text("Funktionsname") },
        autoFocus = true, keyboardOptions = productKeyboard(ProductTextKind.ROLE), enabled = !ops.busy, modifier = Modifier.fillMaxWidth())
    FormButton(ops::saveNewFunction, enabled = !ops.busy && OperationalRules.validName(ops.newFunctionName)) { Text("Funktion anlegen") }
    FormOutlinedButton(ops::backToFunctions, enabled = !ops.busy) { Text("Abbrechen") }
}
