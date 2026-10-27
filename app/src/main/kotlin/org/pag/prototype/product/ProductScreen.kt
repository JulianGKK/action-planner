package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal val LocalProductObservation=staticCompositionLocalOf<((String)->Unit)?>{null}

@androidx.compose.runtime.NonRestartableComposable

@Composable fun ProductScreen(model: ProductViewModel, testAuthorityBanner: Boolean = false, contentFrames: CompactContentFrameObserver? = null) {
    val observation=LocalProductObservation.current
    SideEffect { observation?.invoke("COMPOSITION") }
    MaterialTheme {
        val screen=model.inputScreenKey()
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
                        if (testAuthorityBanner) Text(
                            "P77 ISOLIERTER TEST – synthetischer Schlüssel und synthetische Testdaten; keine Produktanmeldung",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (model.snapshot.state == SessionState.READY) {
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                PagSessionCountdown(model.remainingMillis)
                                CompositionLocalProvider(LocalFormScreen provides null){
                                if (sessionRestartVisible(model.snapshot.state, model.remainingMillis)) {
                                    PagSecondaryButton("Sitzung neu starten", model::restartSession,
                                        modifier = Modifier.originalSemantics {
                                            contentDescription = "Sitzung neu starten. Ungespeicherte Eingaben werden verworfen."
                                        })
                                }
                                PagSecondaryButton("Sperren", model::lock, modifier=Modifier)
                                }
                            }
                        }
                        if(model.snapshot.state==SessionState.READY)model.savedRefreshMessage?.let{Text(it)}
                        PagWorkspace(model.feedback.takeIf { model.snapshot.state == SessionState.READY },
                            model::dismissFeedback, Modifier.fillMaxWidth().weight(1f)) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                        val viewport=loadingViewportMinimum(constraints.maxHeight,with(LocalDensity.current){48.dp.roundToPx()})
                        val listPositions=remember(model.snapshot.generation,model.selected?.id){ProductScrollPositions()}
                        key(model.selected?.id,model.selected?.status,model.activeArea,model.preparationArea,
                            model.compactActiveMode,productEntityListRoute(model),screen) {
                        if(model.snapshot.state==SessionState.READY && (model.leadershipRecovery!=null || model.leadershipRecoveryError)) {
                            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                LeadershipRecoverySection(model)
                            }
                        } else if(productEntityListRoute(model)) ProductEntityList(model,listPositions,contentFrames) else {
                        val scroll=rememberScrollState()
                        val geometry=productScrollGeometry(model,scroll,viewport,listPositions)
                        Column(Modifier.padding(24.dp).verticalScroll(scroll).then(geometry), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (model.snapshot.state) {
                                SessionState.BLOCKED -> PagErrorState()
                                SessionState.LOCKED -> Unit
                                SessionState.AUTHENTICATING -> Text("Bitte bestätige den Gerätesperrcode im Android-Dialog.")
                                SessionState.MAINTENANCE -> PagLoadingState("Geschützter Speicher wird geprüft")
                                SessionState.READY -> {
                                    if(model.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED)
                                        Text("Die Vollansicht überschreitet das zulässige Speicherbudget.")
                                    if(model.workspaceRefusal==WorkspaceFailure.OWNER_UNCONFIRMED)
                                        Text("Die Vollansicht kann derzeit nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet.")
                                    when {
                                        model.documents.available -> DocumentWorkspace(model)
                                        model.creating || model.editing != null -> ActionForm(model)
                                        model.detailLoading -> PagLoadingState()
                                        model.selected?.status == ActionStatus.DRAFT -> DraftWorkspace(model)
                                        model.selected?.status == ActionStatus.PREPARATION -> PreparationWorkspace(model)
                                        model.selected?.status == ActionStatus.ACTIVE -> ActiveWorkspace(model, contentFrames = contentFrames)
                                        model.completedMemoryReportsOpen -> CompletedMemoryReportWorkspace(model)
                                        model.completedProtocolOpen -> CompletedProtocolWorkspace(model)
                                        model.completedMaterialOpen -> CompletedMaterialWorkspace(model)
                                        model.completedExpensesOpen -> CompletedExpenseWorkspace(model)
                                        model.personDetailLoading -> PagLoadingState()
                                        model.selected != null && model.workspaceError -> PagErrorState()
                                        model.selected != null && model.personsVisible && model.workspace == null -> PagLoadingState()
                                        model.selected != null -> {
                                            val action = model.selected!!
                                            Text(action.title, style = MaterialTheme.typography.headlineMedium)
                                            Text(statusLabel(action.status))
                                            var showActionFields by remember(action.id, action.status) { mutableStateOf(action.status != ActionStatus.ACTIVE) }
                                            if (action.status == ActionStatus.ACTIVE) FormTextButton({ model.interaction(); showActionFields = !showActionFields }) { Text("Aktionsdetails") }
                                            if(showActionFields){
                                                Text(action.description)
                                                val header=model.workspace?.uiRows?.header
                                                header?.let{Text(it.start);Text(it.end);it.completed?.let{value->Text(value)}}
                                            }
                                            if (action.status == ActionStatus.COMPLETED) {
                                                model.protocol.error?.let { Text(protocolFailure(it)) }
                                                FormOutlinedButton({model.openCompletedProtocol()}) { Text("Protokoll ansehen / nachbereiten") }
                                                if (model.documentsAvailable) FormOutlinedButton({model.openDocuments()}) { Text("Dokumente verwalten") }
                                                if (model.expensesAvailable) FormOutlinedButton({model.openCompletedExpenses()}) { Text("Kosten verwalten") }
                                                if (model.memoryReportsAvailable) FormOutlinedButton({model.openCompletedMemoryReports()}) { Text("Gedächtnisprotokolle") }
                                                if (model.materialAvailable) FormOutlinedButton({model.openCompletedMaterial()}) { Text("Material ansehen") }
                                            }
                                            if (model.personsVisible && model.confirming == null) PersonsSection(model)
                                            model.changeError?.let { Text(changeErrorLabel(it)) }
                                            if (model.confirming != null) {
                                                Text(if (action.status == ActionStatus.ACTIVE)
                                                "Aktion endgültig abschließen? Danach sind Bearbeitung und Wiedereröffnung ausgeschlossen."
                                                else "${advanceLabel(action.status)}? Dieser Statusschritt kann nicht rückgängig gemacht werden.")
                                                FormButton(onClick = {model.confirmAdvance()}, enabled = !model.saving) { Text("Bestätigen") }
                                                FormOutlinedButton(onClick = {model.cancelAdvance()}, enabled = !model.saving) { Text("Abbrechen") }
                                            } else if (!model.personForm && model.selectedPerson == null && !model.personSaving && model.operations.screen == OperationsScreen.OVERVIEW && !model.tasks.form && model.tasks.selected==null) {
                                                if (ActionLifecycle.mayEdit(action.status)) {
                                                    FormOutlinedButton(onClick = {model.edit()}) { Text("Bearbeiten") }
                                                }
                                                if (ActionLifecycle.next(action.status) != null) {
                                                    FormButton(onClick = {model.requestAdvance()}) { Text(advanceLabel(action.status)) }
                                                }
                                            }
                                            FormOutlinedButton(onClick = {model.back()}, enabled = !model.saving && !model.personSaving) { Text("Zur Aktionsliste") }
                                        }
                                        else -> {
                                            if (model.actionListState != ActionListState.Loading) Text("Aktionsliste", style = MaterialTheme.typography.headlineMedium)
                                            when (val state = model.actionListState) {
                                                ActionListState.Loading -> PagLoadingState()
                                                ActionListState.Empty -> PagEmptyState(model::createScreen)
                                                ActionListState.Error -> PagErrorState()
                                                is ActionListState.Content -> {
                                                    SideEffect { observation?.invoke("LIST_COMMIT") }
                                                    state.actions.forOriginalIndexed { index,action ->
                                                        PagSecondaryButton("${action.title} · ${statusLabel(action.status)}", { model.detail(action.id) }, modifier=Modifier.fillMaxWidth().then(if(index==0 && observation!=null)Modifier.onGloballyPositioned{observation("LIST_LAYOUT")} else Modifier))
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
        }
    }
}


@androidx.compose.runtime.NonRestartableComposable


@Composable internal fun PersonsSection(model: ProductViewModel) {
    if (model.operations.screen != OperationsScreen.OVERVIEW && !(model.operations.screen == OperationsScreen.PERSON && model.selectedPerson != null)) { OperationsSection(model); return }
    if (model.selected?.status != ActionStatus.ACTIVE) Text("Personen", style = MaterialTheme.typography.titleLarge)
    model.personError?.let { Text(personErrorLabel(it)) }
    when {
        model.personForm -> {
            val existing = model.editingPerson
            var pseudonym by remember(existing?.id, model.personEditorRevision) { mutableStateOf(existing?.pseudonym ?: "") }
            PreparationHeading(if (existing == null) "Person hinzufügen" else "Person bearbeiten")
            FormTextField(pseudonym, { value -> model.acceptPersonDraft(value){pseudonym=it} },
                label = { Text("Pseudonym") }, autoFocus=existing==null, keyboardOptions=productKeyboard(ProductTextKind.PERSON), modifier = Modifier.fillMaxWidth(), enabled = !model.personSaving && !model.personCommitConfirmed)
            FormButton(onClick = { model.savePerson(pseudonym) }, enabled = !model.personSaving && !model.personCommitConfirmed) { Text("Speichern") }
            if (model.personError == PersonRejection.STALE && existing != null) {
                Text("Beim Neuladen wird dein Entwurf durch den aktuellen Stand ersetzt.")
                FormOutlinedButton(onClick = {model.reloadPersonEditor()}, enabled = !model.personSaving && !model.personCommitConfirmed) { Text("Aktuellen Stand neu laden") }
            }
            FormOutlinedButton(onClick = {model.cancelPerson()}, enabled = !model.personSaving && !model.personCommitConfirmed) { Text("Abbrechen") }
        }
        model.selectedPerson != null -> {
            PreparationHeading(model.selectedPerson!!.pseudonym)
            Text(originalText{"Revision: ${model.selectedPerson!!.editRevision}"})
            if (model.selected?.status == ActionStatus.PREPARATION) model.workspace?.operations?.let { board ->
                board.persons.find { it.person.id == model.selectedPerson!!.id }?.let { person ->
                    Text(model.formatPlannedRoles(person, board) ?: "Noch keine Rolle")
                }
            }
            if (model.personsEditable) FormOutlinedButton(onClick = {model.editPerson()}) { Text("Bearbeiten") }
            if (model.personsEditable) FormOutlinedButton({ model.operations.plan(model.selectedPerson!!.id) }) { Text("Rollen planen") }
            if (model.selected?.status == ActionStatus.ACTIVE) model.workspace?.operations?.let { board ->
                board.persons.find { it.person.id == model.selectedPerson!!.id }?.let { person ->
                    model.formatPlannedRoles(person, board)?.let { Text("Geplante Rollen: $it") }
                }
            }
            SensitiveSection(model)
            if (model.selected?.status != ActionStatus.ACTIVE) FormOutlinedButton(onClick = {model.backToPersons()}) { Text("Zur Personenliste") }
        }
        else -> {
            if (model.personsLoading) Text("Personen werden geladen …")
            else {
                Text(originalText{"Anzahl: ${model.persons.size}"})
                if (model.persons.isEmpty()) Text("Noch keine Personen erfasst.")
                if (model.operations.board != null) OperationsSection(model)
                else model.persons.forOriginal { person ->
                    val row=model.personRow(person.id)?:return@forOriginal
                    LocalProductObservation.current?.let{observer->SideEffect{observer("PERSON_ROW")}}
                    FormOutlinedButton(onClick = row.detail, enabled = !model.saving) { Text(row.card.name) }
                }
                if (model.personsEditable && !model.tasks.form && model.tasks.selected==null) FormButton(onClick = {model.addPerson()}, enabled = !model.saving) { Text("Person hinzufügen") }
            }
        }
    }
}
private fun personErrorLabel(reason: PersonRejection) = when (reason) {
    PersonRejection.REVISION_EXHAUSTED -> "Dieser Personenstand kann nicht weiter geändert werden (Revisionsgrenze)."
    PersonRejection.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    PersonRejection.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    PersonRejection.STALE -> "Die Person wurde inzwischen geändert. Dein Entwurf bleibt erhalten. Bitte den aktuellen Stand neu laden."
    PersonRejection.ACTION_NOT_FOUND -> "Die Aktion ist nicht mehr vorhanden."
    PersonRejection.NOT_FOUND -> "Die Person ist in dieser Aktion nicht vorhanden."
    PersonRejection.NOT_ALLOWED -> "In diesem Status ist dieser Personenzugriff nicht möglich."
    PersonRejection.INVALID_PSEUDONYM -> "Bitte ein Pseudonym eingeben."
    PersonRejection.DUPLICATE_PSEUDONYM -> "Dieses Pseudonym ist in dieser Aktion bereits vergeben."
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun ActionForm(model: ProductViewModel) {
    val existing = model.editing
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var description by remember(existing?.id) { mutableStateOf(existing?.description ?: "") }
    val zone = remember { ZoneId.systemDefault() }
    var start by remember(existing?.id) { mutableStateOf(existing?.startAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }) }
    var end by remember(existing?.id) { mutableStateOf(existing?.expectedEndAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }) }
    var error by remember { mutableStateOf<String?>(null) }
    FormOutlinedButton({ if (existing == null) model.back() else model.cancelEdit() }, enabled = !model.saving) {
        Text(if (existing == null) "‹ Aktionen" else "‹ Zur Aktion")
    }
    Text(if (existing == null) "Aktion planen" else "Aktion bearbeiten", modifier = Modifier.then(productHeadingSemantics), style = MaterialTheme.typography.headlineMedium)
    FormTextField(title, { model.interaction(); title = it }, label = { Text("Titel") }, autoFocus=existing==null, keyboardOptions=productKeyboard(ProductTextKind.ACTION_TITLE), imeAction = androidx.compose.ui.text.input.ImeAction.Next, modifier = Modifier.fillMaxWidth())
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

/** Compact inline Android/Material selectors retain foreground/focus and RAM-only drafts.
 * Date and time are explicitly selected; selecting only a date creates no instant.
 */
private val pickerDayFormat=DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val pickerTimeFormat=DateTimeFormatter.ofPattern("HH:mm")
private val pickerCost=ActiveWorkspaceBudget.Units(objects=128,arrays=8,arrayPayloadBytes=2048,references=256)
private class DateSelection(value:LocalDateTime?) {
    var part by mutableStateOf<String?>(null)
    var day by mutableStateOf(value?.toLocalDate())
    var time by mutableStateOf(value?.toLocalTime())
}
@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun DateTimeField(label: String, value: LocalDateTime?, zone: ZoneId,
    interaction: () -> Unit, chosen: (LocalDateTime) -> Unit) {
    val screen=LocalFormScreen.current
    val selection=if(screen==null)remember{DateSelection(value)} else
        rememberOriginalValue(screen,0,cost={pickerCost}){DateSelection(value)}?:return
    PreparationHeading(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormOutlinedButton({ interaction(); selection.part = if (selection.part == "date") null else "date" }) {
            Text(originalText{"Datum · ${selection.day?.format(pickerDayFormat) ?: "auswählen"}"})
        }
        FormOutlinedButton({ interaction(); selection.part = if (selection.part == "time") null else "time" }) {
            Text(originalText{"Uhrzeit · ${selection.time?.format(pickerTimeFormat) ?: "auswählen"}"})
        }
    }
    key(selection.part) {
        if (selection.part == "date") {
            val date=if(screen==null)remember{DatePickerState(Locale.getDefault(),initialSelectedDateMillis=selection.day?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())} else
                rememberOriginalValue(screen,0,cost={pickerCost}){DatePickerState(Locale.getDefault(),initialSelectedDateMillis=selection.day?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())}
            if(date!=null){
            DatePicker(state = date, showModeToggle = false)
            FormOutlinedButton({
                interaction()
                date.selectedDateMillis?.let { millis ->
                    selection.day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    selection.time?.let { chosen(selection.day!!.atTime(it)) }
                    selection.part = null
                }
            }, enabled = date.selectedDateMillis != null) { Text("Datum übernehmen") }
            }
        } else if (selection.part == "time") {
            val clock=if(screen==null)remember{val initial=selection.time?:LocalTime.now(zone);TimePickerState(initial.hour,initial.minute,true)} else
                rememberOriginalValue(screen,0,cost={pickerCost}){val initial=selection.time?:LocalTime.now(zone);TimePickerState(initial.hour,initial.minute,true)}
            if(clock!=null){
            TimePicker(state = clock)
            FormOutlinedButton({
                interaction(); selection.time = LocalTime.of(clock.hour, clock.minute)
                selection.day?.let { chosen(it.atTime(selection.time)) }; selection.part = null
            }) { Text("Uhrzeit übernehmen") }
        }
        }
        if (selection.part != null) FormOutlinedButton({ interaction(); selection.part = null }) { Text("Abbrechen") }
    }
}
private fun localTimeFormat() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
internal fun formattedTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    return "${Instant.ofEpochMilli(millis).atZone(zone).format(localTimeFormat())} (${zone.id})"
}
internal fun statusLabel(status: ActionStatus) = when (status) {
    ActionStatus.DRAFT -> "Entwurf"
    ActionStatus.PREPARATION -> "Vorbereitung"
    ActionStatus.ACTIVE -> "Aktiv"
    ActionStatus.COMPLETED -> "Abgeschlossen"
    ActionStatus.DELETION_PENDING -> "Löschung ausstehend"
}

internal fun advanceLabel(status: ActionStatus): String = when (ActionLifecycle.next(status)) {
    ActionStatus.PREPARATION -> "Vorbereitung beginnen"
    ActionStatus.ACTIVE -> "Aktion starten"
    ActionStatus.COMPLETED -> "Aktion abschließen"
    else -> ""
}
internal fun changeErrorLabel(reason: ActionChangeRejection) = when (reason) {
    ActionChangeRejection.NOT_FOUND -> "Die Aktion ist nicht mehr vorhanden."
    ActionChangeRejection.STALE -> "Die Aktion wurde zwischenzeitlich geändert. Bitte erneut aus der Liste öffnen."
    ActionChangeRejection.NOT_EDITABLE -> "In diesem Status kann die Aktion nicht bearbeitet werden."
    ActionChangeRejection.INVALID_TRANSITION -> "Dieser Statusschritt ist nicht möglich."
    ActionChangeRejection.INVALID_FIELDS -> "Bitte die eingegebenen Felder prüfen."
}

private val statusUpperLabels=ActionStatus.entries.associateWith{statusLabel(it).uppercase()} // fixed CODE only
internal fun statusUpperLabel(status:ActionStatus)=statusUpperLabels.getValue(status)
