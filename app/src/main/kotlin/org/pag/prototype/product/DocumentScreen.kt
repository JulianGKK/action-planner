package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.pag.domain.*

@Composable internal fun DocumentWorkspace(model: ProductViewModel) {
    val d=model.documents
    ActionWorkspaceHeader(model)
    FormOutlinedButton(model::closeDocuments, enabled=!d.busy) {
        Text(when(model.selected?.status) { ActionStatus.COMPLETED -> "‹ Zur abgeschlossenen Aktion"; ActionStatus.ACTIVE -> "‹ Zum Lagebild"; else -> "‹ Zur Übersicht" })
    }
    PreparationHeading("Dokumente")
    d.error?.let { Text(documentFailure(it)) }
    val expected=d.confirmingDelete
    when {
        d.textViewed!=null -> DocumentTextPane(d)
        d.viewed!=null -> {
            PreparationHeading(d.viewed!!.displayName)
            FormOutlinedButton(d::closeImage) {Text("‹ Zur Dokumentenliste")}
            if(d.imageLoading) Text("Bild wird sicher gelesen …")
            (d.image as? BitmapDocumentImage)?.forDisplay()?.let {bitmap ->
                Image(bitmap.asImageBitmap(),contentDescription="Gespeichertes Bild",contentScale=ContentScale.Fit,
                    modifier=Modifier.fillMaxWidth().heightIn(max=480.dp).aspectRatio(bitmap.width.toFloat()/bitmap.height))
            }
        }
        d.camera.mode != CameraMode.CLOSED -> CameraCapturePane(d)
        expected!=null -> {
            Text(expected.displayName)
            Text("Die geschützte PaG-Kopie dieses Dokuments wird entfernt.\n\nDie ursprüngliche Datei außerhalb von PaG bleibt davon unberührt.")
            FormButton(d::confirmDelete, enabled=!d.busy) { Text("Löschen bestätigen") }
            FormOutlinedButton(d::cancelDelete, enabled=!d.busy) { Text("Abbrechen") }
        }
        d.importing -> {
            Text("PaG erstellt eine verschlüsselte Kopie. Die Originaldatei außerhalb von PaG bleibt bestehen.")
            FormTextField(d.name,d::changeName,label={Text("Dokumentname · 1–160 Zeichen")},enabled=!d.busy,
                modifier=Modifier.fillMaxWidth(),keyboardOptions=productKeyboard(ProductTextKind.DESCRIPTION))
            Text("Format bewusst auswählen. Die Dateibytes werden zusätzlich geprüft.")
            DocumentFormat.entries.forEach { format ->
                val chosen=d.format==format
                FormOutlinedButton({d.changeFormat(format)},enabled=!d.busy,modifier=Modifier.semantics { selected=chosen }) {
                    Text((if(chosen) "✓ " else "")+documentFormat(format))
                }
            }
            Text("Maximal 25 MiB pro Dokument · 100 Dokumente / 250 MiB pro Aktion")
            FormButton(d::save,enabled=d.canSave) { Text(if(d.busy) "Import läuft …" else "Import bestätigen") }
            FormOutlinedButton(d::cancelImport,enabled=!d.busy) { Text("Abbrechen") }
        }
        else -> {
            if(d.canContinueImport) {
                Text("Datei ausgewählt – Import noch nicht abgeschlossen.")
                FormButton(d::continueImport,enabled=!d.busy) { Text("Import fortsetzen") }
                FormOutlinedButton(d::cancelImport,enabled=!d.busy) { Text("Dateiauswahl verwerfen") }
            }
            if(d.selectionUnavailable) Text("Die Dateiauswahl ist nicht mehr verfügbar. Bitte die Datei erneut auswählen.")
            d.camera.error?.let { Text(cameraFailure(it)) }
            if(d.cameraSupported) FormButton(d::takePhoto,enabled=!d.busy) { Text("Foto aufnehmen") }
            FormButton(d::chooseFile,enabled=!d.busy) { Text("Dokument importieren") }
            Text("PaG erstellt eine verschlüsselte Kopie. Die Originaldatei außerhalb von PaG bleibt bestehen.")
            if(d.loading) Text("Dokumente werden geladen …")
            if(d.items?.isEmpty()==true) Text("Noch keine Dokumente gespeichert.")
            d.items.orEmpty().forEach { item ->
                PreparationHeading(item.displayName)
                Text("${documentFormat(item.format)} · ${item.plaintextSize} Bytes")
                Text("Importiert am: ${formattedTime(item.importedAt)}")
                if(item.format in setOf(DocumentFormat.JPEG,DocumentFormat.PNG)) {
                    FormOutlinedButton({d.viewImage(item)},enabled=!d.busy) {Text("Bild ansehen")}
                } else if(item.format==DocumentFormat.TEXT) {
                    FormOutlinedButton({d.viewText(item)},enabled=!d.busy) {Text("Text ansehen")}
                } else Text("Vorschau in dieser Version noch nicht verfügbar.")
                FormOutlinedButton({d.requestDelete(item)},enabled=!d.busy) { Text("Löschen") }
            }
            FormOutlinedButton({model.interaction();d.refresh()},enabled=!d.busy && !d.loading) { Text("Aktualisieren") }
        }
    }
}
internal fun documentFormat(format: DocumentFormat) = when(format) { DocumentFormat.TEXT -> "UTF-8-Text"; else -> format.name }
internal fun documentFailure(reason: DocumentFailure) = when(reason) {
    DocumentFailure.TEXT_FORMAT -> "Dieses Dokument enthält keinen gültigen UTF-8-Klartext und kann nicht angezeigt werden."
    DocumentFailure.TEXT_UNSUPPORTED -> "Diese Textseite kann nicht angezeigt werden. Bitte das Dokument erneut bewusst öffnen."
    DocumentFailure.IMAGE_UNSUPPORTED -> "Dieses Bild ist für die sichere Anzeige zu groß oder wird nicht unterstützt."
    DocumentFailure.INVALID -> "Bitte einen Dokumentnamen mit 1–160 Zeichen und ein unterstütztes Format angeben."
    DocumentFailure.CAPACITY -> "Die Dokumentanzahl oder Speichergrenze dieser Aktion ist erreicht."
    DocumentFailure.MAINTENANCE_REQUIRED -> "Bitte PaG sperren und erneut entsperren. Ein unterbrochener Import muss zuerst abgeglichen werden."
    DocumentFailure.TOO_LARGE -> "Die Datei überschreitet 25 MiB. Bitte eine kleinere Datei auswählen."
    DocumentFailure.FORMAT -> "Die Datei passt nicht zum gewählten Format. Unterstützt werden PDF, JPEG, PNG und UTF-8-Text."
    DocumentFailure.SOURCE_UNAVAILABLE -> "Die Dateiauswahl ist nicht mehr verfügbar. Bitte die Datei erneut auswählen."
    DocumentFailure.NOT_ALLOWED -> "Dokumente sind in diesem Aktionsstatus nicht verfügbar."
    DocumentFailure.NOT_FOUND -> "Das Dokument oder die Aktion ist nicht mehr vorhanden."
    DocumentFailure.STALE -> "Der Dokumentbestand hat sich geändert. Bitte die Liste aktualisieren."
}

@Composable internal fun DocumentTextPane(d:DocumentModel) {
    PreparationHeading(d.textViewed?.displayName.orEmpty())
    FormOutlinedButton(d::closeText) {Text("‹ Zur Dokumentenliste")}
    Text(if(d.textPageCount>0) "Seite ${d.textPageIndex+1} von ${d.textPageCount}" else "Seite ${d.textPageIndex+1}")
    @Composable fun navigation() {
        FormOutlinedButton(d::previousTextPage,enabled=!d.textLoading && d.textPageIndex>0) {Text("‹ Seite zurück")}
        FormOutlinedButton(d::nextTextPage,enabled=!d.textLoading && d.textPageIndex+1<d.textPageCount) {Text("Seite weiter ›")}
    }
    navigation()
    if(d.textLoading) Text("Text wird sicher gelesen …")
    d.text?.let {content ->
        LaunchedEffect(d.textViewed?.id,d.textPageIndex,content) {withFrameNanos {};d.textFrame()}
        if(content.isEmpty())Text("Dieses Textdokument ist leer.") else {
            val rows=remember(content) {documentTextRows(content)}
            androidx.compose.runtime.key(d.textPageIndex) {
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(rows.size) {index->Text(rows[index],modifier=Modifier.fillMaxWidth())}
                }
            }
        }
        navigation()
        FormOutlinedButton(d::closeText) {Text("‹ Zur Dokumentenliste")}
    }
}
