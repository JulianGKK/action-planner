package org.pag.prototype.product

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** P6-only explicit follow-up. No ACTIVE hub and no other domain editors. */
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun CompletedProtocolWorkspace(model: ProductViewModel) {
    ActionWorkspaceHeader(model)
    PreparationHeading("Protokoll nachbereiten")
    Text("Die Aktion ist abgeschlossen. Neue Einträge und neue Fassungen werden dauerhaft als nach Abschluss erfasst gekennzeichnet.")
    FormOutlinedButton({model.closeCompletedProtocol()}, enabled = !model.protocol.busy) { Text("‹ Zur abgeschlossenen Aktion") }
    val protocol = model.protocol
    if (protocol.nested) FormOutlinedButton({protocol.back()}, enabled = !protocol.busy) {
        Text(if (protocol.historical != null || (protocol.form && protocol.expected != null)) "‹ Zum aktuellen Eintrag" else "‹ Protokoll")
    }
    val content = model.workspace
    if (content == null) { if (model.workspaceError) PagErrorState() else PagLoadingState(); return }
    ProtocolSection(protocol, content.persons)
}
