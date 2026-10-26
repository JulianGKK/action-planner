package org.pag.prototype.product

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.pag.domain.*

// Immutable CODE enum groups; no action payload, authority or per-render Set.
private val supportedOperationsScreenGroup1=setOf(OperationsScreen.PLAN,OperationsScreen.LIVE,OperationsScreen.FUNCTION)
private val supportedMemoryReportModeGroup2=setOf(MemoryReportMode.SECTION,MemoryReportMode.ADDENDUM)


/** No graph getter, new read ticket, board reconstruction or refreshed-data publication.
 * This only renders data ALREADY held by the editor under the original held-copy gate.
 * Source lists requiring the revoked read epoch are not substituted by an old graph.
 */
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun HeldFormSection(model:ProductViewModel):Boolean {
    if(!model.heldFormAllowed())return false
    val ops=model.operations
    val editing=model.personForm || model.tasks.form || model.material.form || model.expenses.form || model.protocol.form ||
        model.memoryReports.mode in heldMemoryModes || ops.editingRole!=null || ops.deletingRole!=null || ops.screen in heldOperationsModes
    if(!editing)return false
    Text("Der bisherige Entwurf bleibt erhalten. Der gespeicherte Stand ist erst nach erfolgreichem Neuladen wieder verfügbar.")
    when{
        model.personForm->PersonsSection(model)
        model.tasks.form->{model.tasks.error?.let{Text(taskFailureMessage(it))};TaskForm(model.tasks,null)}
        model.material.form->MaterialSection(model.material,emptyList(),emptyList())
        model.expenses.form->ExpenseSection(model.expenses,emptyList())
        model.protocol.form->ProtocolSection(model.protocol,emptyList())
        model.memoryReports.mode in heldMemoryModes->MemoryReportSection(model.memoryReports,emptyList())
        else->HeldOperationsEditor(model)
    }
    return true
}
private val heldOperationsModes=supportedOperationsScreenGroup1
private val heldMemoryModes=supportedMemoryReportModeGroup2
