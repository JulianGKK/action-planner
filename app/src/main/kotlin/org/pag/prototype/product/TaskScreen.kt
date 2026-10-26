package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.pag.domain.*
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun taskFailureMessage(reason:OperationsFailure):String = when(reason) {
    OperationsFailure.TASK_EVENT_LIMIT_REACHED -> "Die Grenze von 5.000 Aufgabenereignissen je Aktion ist erreicht. Neue Aufgaben und Statuswechsel sind nicht möglich. Bestehende Aufgaben können weiter gelesen und bearbeitet werden."
    OperationsFailure.INVALID->"Bitte Titel (1–120 Zeichen), Zuständigkeit und Fälligkeit prüfen."
    OperationsFailure.REVISION_EXHAUSTED->"Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    OperationsFailure.STALE ->"Die Aufgabe wurde inzwischen geändert. Dein Entwurf bleibt erhalten; bitte bewusst neu laden."
    OperationsFailure.NOT_ALLOWED->"Im aktuellen Aktionsstatus ist diese Änderung nicht möglich."
    else->"Die Aufgabe oder Zuordnung ist in dieser Aktion nicht verfügbar."
}

@androidx.compose.runtime.NonRestartableComposable

@Composable internal fun TaskSection(model:TaskModel,people:OperationalBoard) {
    val board=model.board?:return
    if (people.status == ActionStatus.ACTIVE) { ActiveTaskSection(model, people, board); return }
    Text("Aufgaben",Modifier.then(productHeadingSemantics),style=MaterialTheme.typography.titleLarge)
    model.error?.let{Text(taskFailureMessage(it))}
    if(model.form){TaskForm(model,people);return}
    val selected=model.selected
    if(selected!=null){
        TaskSummary(model.uiRow(selected)?:return)
        if(model.writable){
            if(selected.status==TaskStatus.OPEN)FormButton({model.transition(TaskStatus.IN_PROGRESS)},enabled=!model.busy && !model.commitConfirmed){Text("In Arbeit")}
            if(TaskRules.active(selected.status)){
                FormButton({model.transition(TaskStatus.DONE)},enabled=!model.busy && !model.commitConfirmed){Text("Erledigt")}
                FormOutlinedButton({model.transition(TaskStatus.CANCELLED)},enabled=!model.busy && !model.commitConfirmed){Text("Entfällt")}
            }else FormButton({model.transition(TaskStatus.OPEN)},enabled=!model.busy && !model.commitConfirmed){Text("Wieder öffnen")}
            FormOutlinedButton({model.edit()},enabled=!model.busy && !model.commitConfirmed){Text("Bearbeiten")}
            if(model.deletable)FormOutlinedButton({model.delete()},enabled=!model.busy && !model.commitConfirmed){Text("Aufgabe löschen")}
        }
        Text("Chronik",style=MaterialTheme.typography.titleMedium)
        TaskHistory(model)
        FormOutlinedButton({model.cancel()},enabled=!model.busy && !model.commitConfirmed){Text("Zurück zu Aufgaben")}
        return
    }
    if(model.writable)FormButton({model.add()},enabled=!model.busy && !model.commitConfirmed){Text("+ Aufgabe")}
    Text("Dringende / offene Aufgaben",style=MaterialTheme.typography.titleMedium)
    val activeTasks=board.active
    val closedTasks=board.closed
    Text(board.presentation.all.preparationSummary)
    model.filter?.let{id->val name=people.persons.findOriginal{it.person.id==id}?.person?.pseudonym.orEmpty();Text(originalText(name.length.toLong()){"Betrifft: $name"});FormOutlinedButton({model.showPerson(null)}){Text("Alle Aufgaben")}}
    val presentation=activeTaskContent(board,model.filter)
    val active=presentation.active
    active.forOriginal{t->val row=model.uiRow(t)?:return@forOriginal;FormOutlinedButton(row.click,modifier=Modifier.fillMaxWidth(),enabled=!model.busy && !model.commitConfirmed){TaskSummary(row)}}
    if(active.isEmpty())Text(if(board.tasks.isEmpty() && people.status==ActionStatus.PREPARATION)"Noch keine Aufgaben." else "Keine offenen Aufgaben.")
    var closed by remember{mutableStateOf(false)}
    FormTextButton({model.touch();closed=!closed}){Text(originalText{"Erledigt / Entfällt (${closedTasks.size}) ${if(closed)"▴" else "▾"}"})}
    if(closed)presentation.closed.forOriginal{t->val row=model.uiRow(t)?:return@forOriginal;FormOutlinedButton(row.click,modifier=Modifier.fillMaxWidth()){TaskSummary(row)}}
    if(people.status==ActionStatus.PREPARATION){
        var templates by remember{mutableStateOf(false)}
        FormTextButton({model.touch();templates=!templates}){Text("Aufgaben aus Vorlagen")}
        if(templates){Text("Organisatorische Vorschläge · Die Leitung entscheidet selbst.");TaskRules.templates.forOriginal{t->FormOutlinedButton({model.add(template=t)},enabled=!model.busy && !model.commitConfirmed){Text(t.title)}}}
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun TaskSummary(row:TaskUiRow) {
    if(!row.click.live())return
    LocalProductObservation.current?.let{observer->androidx.compose.runtime.SideEffect{observer("TASK_ROW")}}
    Column {
        Text(row.revision)
        Text(row.heading,style=MaterialTheme.typography.titleMedium)
        row.preparationAffected?.let{Text(it)}
        Text(row.preparationAssignee)
        row.preparationDue?.let{Text(it)}
    }
}
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun TaskForm(model:TaskModel,people:OperationalBoard?) {
    key(model.formRevision){TaskFormFields(model,people)}
}
@androidx.compose.runtime.NonRestartableComposable
@Composable private fun TaskFormFields(model:TaskModel,people:OperationalBoard?) {
    val i=model.input
    FormTextField(i.title,{model.changeTitle(it)},label={Text("Titel")},autoFocus=!model.editingExisting,keyboardOptions=productKeyboard(ProductTextKind.TASK_TITLE),singleLine=true,enabled=!model.busy && !model.commitConfirmed,modifier=Modifier.fillMaxWidth(),supportingText=originalContent{Text("Kurzer Titel · keine sensiblen Identitäts- oder Kontaktdaten · maximal 120 Zeichen")})
    Text("Priorität")
    TaskPriority.entries.forOriginal{p->FormOutlinedButton({model.priority(p)},enabled=!model.busy && !model.commitConfirmed){Text(originalText{(if(i.priority==p)"✓ " else "")+priorityLabel(p)})}}
    if(people==null){Text("Zuordnungen werden nach dem bewussten Neuladen wieder verfügbar. Dein Entwurf bleibt erhalten.")}
    else {
    var assignments by remember{mutableStateOf(false)}
    FormTextButton({model.touch();assignments=!assignments}){Text("Zuständigkeit wählen (optional)")}
    if(assignments){
        FormOutlinedButton({model.assigneePerson(null)},enabled=!model.busy && !model.commitConfirmed){Text(if(i.assigneePersonId==null && i.assigneeRoleId==null)"✓ Niemand" else "Niemand")}
        people.roles.forOriginal{r->val id=r.id;FormOutlinedButton({model.assigneeRole(id)},enabled=!model.busy && !model.commitConfirmed){Text(originalText(r.name.length.toLong()){(if(i.assigneeRoleId==r.id)"✓ " else "")+"Rolle: ${r.name}"})}}
        people.persons.forOriginal{p->val id=p.person.id;FormOutlinedButton({model.assigneePerson(id)},enabled=!model.busy && !model.commitConfirmed){Text(originalText(p.person.pseudonym.length.toLong()){(if(i.assigneePersonId==p.person.id)"✓ " else "")+"Person: ${p.person.pseudonym}"})}}
    }
    val assignment=i.assigneeRoleId?.let{id->people.roles.findOriginal{it.id==id}?.name}?:i.assigneePersonId?.let{id->people.persons.findOriginal{it.person.id==id}?.person?.pseudonym}?:"Niemand"
    Text(originalText(assignment.length.toLong()){"Zuständig: $assignment"})
    var affected by remember{mutableStateOf(false)}
    FormTextButton({model.touch();affected=!affected}){Text("Betroffene Person wählen (optional)")}
    if(affected){FormOutlinedButton({model.affectedPerson(null)},enabled=!model.busy && !model.commitConfirmed){Text("Keine betroffene Person")};people.persons.forOriginal{p->val id=p.person.id;FormOutlinedButton({model.affectedPerson(id)},enabled=!model.busy && !model.commitConfirmed){Text(originalText(p.person.pseudonym.length.toLong()){(if(i.affectedPersonId==id)"✓ " else "")+p.person.pseudonym})}}}
    val affectedName=people.persons.findOriginal{it.person.id==i.affectedPersonId}?.person?.pseudonym?:"Niemand"
    Text(originalText(affectedName.length.toLong()){"Betrifft: $affectedName"})
    }
    val due=model.dueText
    FormTextField(due,model::changeDue,label={Text("Fällig (optional): TT.MM.JJJJ HH:MM")},keyboardOptions=productKeyboard(ProductTextKind.DATE),singleLine=true,enabled=!model.busy && !model.commitConfirmed,supportingText=originalContent{if(!model.dueValid)Text("Ungültiges Datum oder durch Zeitumstellung nicht eindeutige Uhrzeit. Bitte Datum oder Uhrzeit korrigieren.")})
    if(model.error==OperationsFailure.STALE && model.editingExisting){
        Text("Beim Neuladen wird dein bisheriger Entwurf einschließlich Fälligkeit und Zuordnungen ersetzt.")
        FormOutlinedButton({model.reloadEditor()},enabled=!model.busy && !model.commitConfirmed){Text("Entwurf verwerfen und Aufgabe neu laden")}
    }
    FormButton({model.save()},enabled=!model.busy && !model.commitConfirmed && TaskRules.valid(i) && model.dueValid){Text("Speichern")}
    FormOutlinedButton({model.cancel()},enabled=!model.busy && !model.commitConfirmed){Text("Abbrechen")}
}
internal fun parseTaskDue(value:String,zone:ZoneId=ZoneId.systemDefault()):Long?=try{selectedInstant(LocalDateTime.parse(value.trim(),DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)),zone)}catch(_:Exception){null}
internal fun priorityLabel(p:TaskPriority)=when(p){TaskPriority.LOW->"Niedrig";TaskPriority.NORMAL->"Normal";TaskPriority.HIGH->"Hoch"}
internal fun taskStatusLabel(s:TaskStatus)=when(s){TaskStatus.OPEN->"Offen";TaskStatus.IN_PROGRESS->"In Arbeit";TaskStatus.DONE->"Erledigt";TaskStatus.CANCELLED->"Entfällt"}
internal fun eventLabel(t:TaskEventType)=when(t){TaskEventType.CREATED->"Erstellt";TaskEventType.STARTED->"In Arbeit";TaskEventType.COMPLETED->"Erledigt";TaskEventType.CANCELLED->"Entfällt";TaskEventType.REOPENED->"Wieder geöffnet"}
internal fun taskTime(time:Long)=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))

/** Only the current bounded page is composed; replacing it does not accumulate history. */
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun TaskHistory(model:TaskModel) {
    when {
        model.historyBusy->Text("Chronik wird geladen …")
        model.historyError!=null->{
            Text(when(model.historyError){
                BoundedActiveStatus.BUDGET_REFUSED->"Chronikseite kann derzeit nicht geladen werden (Speichergrenze)."
                BoundedActiveStatus.ACCESS_REVOKED->"Chronik ist nicht mehr aktuell. Bitte neu laden."
                else->"Chronik konnte nicht geladen werden. Bitte erneut versuchen."
            })
            FormOutlinedButton({model.loadHistory(true)}){Text("Chronik neu laden")}
        }
        model.history==null->FormOutlinedButton({model.loadHistory(true)}){Text("Chronik laden")}
        else->{
            val page=model.history!!
            val display=model.historyUi?:return
            if(page.total==0L)Text("Noch keine Ereignisse.") else Text(display.range)
            if(display.lastPage>1){
                FormTextField(model.historyPageText,model::changeHistoryPageText,label={Text(display.pageLabel)},singleLine=true,
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
                FormOutlinedButton({model.openHistoryPage()},enabled=model.historyPageChoice!=null){Text("Seite öffnen")}
                FormTextButton({model.lastHistoryPage()}){Text("Letzte Seite")}
            }
            display.events.forOriginal{Text(it)}
            if(page.next!=null)FormOutlinedButton({model.loadHistory()}){Text("Weitere Ereignisse")}
            FormTextButton({model.loadHistory(true)}){Text("Chronik von Anfang an")}
        }
    }
}
