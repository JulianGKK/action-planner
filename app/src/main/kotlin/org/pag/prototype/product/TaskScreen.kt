package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.pag.domain.*
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable internal fun TaskSection(model:TaskModel,people:OperationalBoard) {
    val board=model.board?:return
    Text("Aufgaben",style=MaterialTheme.typography.titleLarge)
    model.error?.let{Text(when(it){
        OperationsFailure.INVALID->"Bitte Titel (1–120 Zeichen), Zuständigkeit und Fälligkeit prüfen."
        OperationsFailure.STALE->"Die Aufgabe wurde inzwischen geändert. Bitte neu öffnen."
        OperationsFailure.NOT_ALLOWED->"Im aktuellen Aktionsstatus ist diese Änderung nicht möglich."
        else->"Die Aufgabe oder Zuordnung ist in dieser Aktion nicht verfügbar."
    })}
    if(model.form){TaskForm(model,people);return}
    val selected=model.selected
    if(selected!=null){
        TaskSummary(selected,people)
        if(model.writable){
            if(selected.status==TaskStatus.OPEN)FormButton({model.transition(TaskStatus.IN_PROGRESS)},enabled=!model.busy){Text("In Arbeit")}
            if(TaskRules.active(selected.status)){
                FormButton({model.transition(TaskStatus.DONE)},enabled=!model.busy){Text("Erledigt")}
                FormOutlinedButton({model.transition(TaskStatus.CANCELLED)},enabled=!model.busy){Text("Entfällt")}
            }else FormButton({model.transition(TaskStatus.OPEN)},enabled=!model.busy){Text("Wieder öffnen")}
            FormOutlinedButton(model::edit,enabled=!model.busy){Text("Bearbeiten")}
            if(model.deletable)FormTextButton(model::delete,enabled=!model.busy){Text("Aufgabe löschen")}
        }
        Text("Chronik",style=MaterialTheme.typography.titleMedium)
        board.events.filter{it.taskId==selected.id}.forEach{Text("${taskTime(it.occurredAt)} · ${eventLabel(it.type)}")}
        FormTextButton(model::cancel,enabled=!model.busy){Text("Zurück zu Aufgaben")}
        return
    }
    if(model.writable)FormButton({model.add()},enabled=!model.busy){Text("+ Aufgabe")}
    Text("Dringende / offene Aufgaben",style=MaterialTheme.typography.titleMedium)
    Text("Hoch offen / in Arbeit: ${board.active.count{it.priority==TaskPriority.HIGH}} · Offen: ${board.active.count{it.status==TaskStatus.OPEN}} · In Arbeit: ${board.active.count{it.status==TaskStatus.IN_PROGRESS}}")
    model.filter?.let{id->Text("Betrifft: ${people.persons.find{it.person.id==id}?.person?.pseudonym.orEmpty()}");FormTextButton({model.showPerson(null)}){Text("Alle Aufgaben")}}
    val active=board.active.filter{model.filter==null || it.affectedPersonId==model.filter}
    active.forEach{t->FormOutlinedButton({model.open(t)},modifier=Modifier.fillMaxWidth(),enabled=!model.busy){TaskSummary(t,people)}}
    if(active.isEmpty())Text("Keine offenen Aufgaben.")
    var closed by remember{mutableStateOf(false)}
    FormTextButton({model.touch();closed=!closed}){Text("Erledigt / Entfällt (${board.closed.size}) ${if(closed)"▴" else "▾"}")}
    if(closed)board.closed.filter{model.filter==null || it.affectedPersonId==model.filter}.forEach{t->FormOutlinedButton({model.open(t)},modifier=Modifier.fillMaxWidth()){TaskSummary(t,people)}}
    if(people.status==ActionStatus.PREPARATION){
        var templates by remember{mutableStateOf(false)}
        FormTextButton({model.touch();templates=!templates}){Text("Aufgaben aus Vorlagen")}
        if(templates){Text("Organisatorische Vorschläge · Die Leitung entscheidet selbst.");TaskRules.templates.forEach{t->FormOutlinedButton({model.add(template=t)},enabled=!model.busy){Text(t.title)}}}
    }
}
@Composable private fun TaskSummary(t:Task,people:OperationalBoard) {
    Column {
        Text("${priorityLabel(t.priority)} · ${t.title}",style=MaterialTheme.typography.titleMedium)
        t.affectedPersonId?.let{id->Text("Betrifft: ${people.persons.find{it.person.id==id}?.person?.pseudonym.orEmpty()}")}
        val assignee=t.assigneeRoleId?.let{id->people.roles.find{it.id==id}?.name}?:t.assigneePersonId?.let{id->people.persons.find{it.person.id==id}?.person?.pseudonym}?:"Niemand zugeordnet"
        Text("$assignee · ${taskStatusLabel(t.status)}")
        t.dueAt?.let{Text("Fällig: ${taskTime(it)}")}
    }
}
@Composable private fun TaskForm(model:TaskModel,people:OperationalBoard) {
    val i=model.input
    FormTextField(i.title,{model.change(i.copy(title=it))},label={Text("Titel")},singleLine=true,enabled=!model.busy,modifier=Modifier.fillMaxWidth(),supportingText={Text("Kurzer Titel · keine sensiblen Identitäts- oder Kontaktdaten · ${Character.codePointCount(i.title.trim(),0,i.title.trim().length)} / 120 Zeichen")})
    Text("Priorität")
    TaskPriority.entries.forEach{p->FormOutlinedButton({model.change(i.copy(priority=p))},enabled=!model.busy){Text((if(i.priority==p)"✓ " else "")+priorityLabel(p))}}
    var assignments by remember{mutableStateOf(false)}
    FormTextButton({model.touch();assignments=!assignments}){Text("Zuständigkeit wählen (optional)")}
    if(assignments){
        FormOutlinedButton({model.change(i.copy(assigneePersonId=null,assigneeRoleId=null))},enabled=!model.busy){Text(if(i.assigneePersonId==null && i.assigneeRoleId==null)"✓ Niemand" else "Niemand")}
        people.roles.forEach{r->FormOutlinedButton({model.change(i.copy(assigneeRoleId=r.id,assigneePersonId=null))},enabled=!model.busy){Text((if(i.assigneeRoleId==r.id)"✓ " else "")+"Rolle: ${r.name}")}}
        people.persons.forEach{p->FormOutlinedButton({model.change(i.copy(assigneePersonId=p.person.id,assigneeRoleId=null))},enabled=!model.busy){Text((if(i.assigneePersonId==p.person.id)"✓ " else "")+"Person: ${p.person.pseudonym}")}}
    }
    val assignment=i.assigneeRoleId?.let{id->people.roles.find{it.id==id}?.name}?:i.assigneePersonId?.let{id->people.persons.find{it.person.id==id}?.person?.pseudonym}?:"Niemand"
    Text("Zuständig: $assignment")
    var affected by remember{mutableStateOf(false)}
    FormTextButton({model.touch();affected=!affected}){Text("Betroffene Person wählen (optional)")}
    if(affected){FormOutlinedButton({model.change(i.copy(affectedPersonId=null))},enabled=!model.busy){Text("Keine betroffene Person")};people.persons.forEach{p->FormOutlinedButton({model.change(i.copy(affectedPersonId=p.person.id))},enabled=!model.busy){Text((if(i.affectedPersonId==p.person.id)"✓ " else "")+p.person.pseudonym)}}}
    Text("Betrifft: "+(people.persons.find{it.person.id==i.affectedPersonId}?.person?.pseudonym?:"Niemand"))
    var due by remember{mutableStateOf(i.dueAt?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}.orEmpty())}
    val parsed=parseTaskDue(due)
    FormTextField(due,{due=it;model.change(model.input.copy(dueAt=parseTaskDue(it)))},label={Text("Fällig (optional): TT.MM.JJJJ HH:MM")},singleLine=true,enabled=!model.busy,supportingText={if(due.isNotBlank() && parsed==null)Text("Ungültiges Datum oder durch Zeitumstellung nicht eindeutige Uhrzeit. Bitte Datum oder Uhrzeit korrigieren.")})
    FormButton(model::save,enabled=!model.busy && TaskRules.valid(i) && (due.isBlank() || parsed!=null)){Text("Speichern")}
    FormTextButton(model::cancel,enabled=!model.busy){Text("Abbrechen")}
}
internal fun parseTaskDue(value:String,zone:ZoneId=ZoneId.systemDefault()):Long?=try{selectedInstant(LocalDateTime.parse(value.trim(),DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)),zone)}catch(_:Exception){null}
internal fun priorityLabel(p:TaskPriority)=when(p){TaskPriority.LOW->"Niedrig";TaskPriority.NORMAL->"Normal";TaskPriority.HIGH->"Hoch"}
internal fun taskStatusLabel(s:TaskStatus)=when(s){TaskStatus.OPEN->"Offen";TaskStatus.IN_PROGRESS->"In Arbeit";TaskStatus.DONE->"Erledigt";TaskStatus.CANCELLED->"Entfällt"}
internal fun eventLabel(t:TaskEventType)=when(t){TaskEventType.CREATED->"Erstellt";TaskEventType.STARTED->"In Arbeit";TaskEventType.COMPLETED->"Erledigt";TaskEventType.CANCELLED->"Entfällt";TaskEventType.REOPENED->"Wieder geöffnet"}
private fun taskTime(time:Long)=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
