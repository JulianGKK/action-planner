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

internal typealias FunctionGroup=org.pag.domain.FunctionGroup
internal typealias ActiveFunctionContent=org.pag.domain.ActiveFunctionContent
internal fun activeFunctionPeople(board:OperationalBoard,roleId:String?)=board.presentation.functions.people(roleId)
internal fun activeFunctionName(board:OperationalBoard?,roleId:String?)=
    if(roleId==null)"Ohne Funktion" else board?.roles?.findOriginal{it.id==roleId}?.name?:"Funktion nicht verfügbar"
internal fun activeFunctionContent(board:OperationalBoard)=board.presentation.functions

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ActiveFunctionsOverview(model: ProductViewModel, board: OperationalBoard) {
    val ops = model.operations
    val content = activeFunctionContent(board)
    PreparationHeading("Wer übernimmt gerade was?")
    content.summary.forOriginal { Text(it) }
    content.groups.forOriginal { group -> val id=group.role.id;FunctionCard(group.heading,group.labels,group.semantics,model){ops.focusFunction(id)} }
    if (content.without.isNotEmpty()) FunctionCard(content.withoutHeading,content.withoutLabels,"Ohne Funktion öffnen",model) { ops.focusFunction(null) }
    if (content.other.isNotEmpty()) {
        FormOutlinedButton({ops.toggleMoreFunctions()}, enabled = model.activeNavigationEnabled,
            modifier = Modifier.fillMaxWidth().originalSemantics { stateDescription = if (ops.moreFunctionsExpanded) "Ausgeklappt" else "Eingeklappt" }) {
            Text(if(ops.moreFunctionsExpanded)content.moreExpanded else content.moreCollapsed)
        }
        if (ops.moreFunctionsExpanded) content.other.forOriginal { Text(it.name) }
    }
    FormOutlinedButton({ops.newFunction()}, enabled = model.activeNavigationEnabled, modifier = Modifier.fillMaxWidth()) { Text("+ Neue Funktion") }
}

@Composable private inline fun FunctionCard(heading:String,labels:String,semantics:String,model:ProductViewModel,crossinline open:()->Unit) {
    FormOutlinedButton(open, enabled = model.activeNavigationEnabled,
        modifier = Modifier.fillMaxWidth().originalSemantics { onClick(label=semantics,action=null) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(heading, Modifier.then(productHeadingSemantics), style = MaterialTheme.typography.titleLarge)
            Text(labels)
        }
    }
}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun ActiveFunctionDetail(model: ProductViewModel, board: OperationalBoard) {
    val focus = model.operations.functionFocus ?: return
    PreparationHeading(activeFunctionName(board, focus.roleId))
    val people = activeFunctionPeople(board, focus.roleId)
    if (people.isEmpty()) Text(if (focus.roleId == null) "Derzeit niemand ohne Funktion vor Ort." else "Derzeit niemand in dieser Funktion vor Ort.")
    else {
        Text(focus.roleId?.let{board.presentation.functions.group(it)?.count}?:board.presentation.functions.withoutCount)
        people.forOriginal { person ->
            val id=person.person.id
            val semantics=model.personRow(person)?.semantics?:return@forOriginal
            FormOutlinedButton({ model.operations.focus(id) }, enabled = model.activeNavigationEnabled,
                modifier = Modifier.fillMaxWidth().originalSemantics { onClick(label=semantics,action=null) }) {
                Text(person.person.pseudonym, Modifier.then(productHeadingSemantics), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun NewActiveFunction(ops: OperationsModel) {
    PreparationHeading("Neue Funktion")
    RoleCreateForm(ops,RoleCreateSurface.NEW_FUNCTION,"Funktionsname","Funktion anlegen")
    FormOutlinedButton({ops.backToFunctions()}, enabled = !ops.busy) { Text("Abbrechen") }
}
