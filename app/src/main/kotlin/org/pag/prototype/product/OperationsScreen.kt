package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable internal fun OperationsSection(model:ProductViewModel) {
    val ops=model.operations;val board=ops.board?:return
    if(ops.editingRole==null && ops.deletingRole==null && ops.createError==null)ops.error?.let{Text(if(ops.screen==OperationsScreen.PLAN && it==OperationsFailure.STALE)
        "Die Rollenplanung wurde inzwischen geändert. Dein Entwurf bleibt erhalten." else operationsError(it))}
    when(ops.screen) {
        OperationsScreen.ROLES->RoleManagement(model,ops,board)
        OperationsScreen.PLAN->{
            Text("Rollen planen",style=MaterialTheme.typography.titleLarge)
            Text("Revision: ${ops.planRevision}")
            Text(ops.person?.person?.pseudonym.orEmpty(),style=MaterialTheme.typography.headlineMedium)
            if(ops.planRoles.isEmpty())Text("Noch keine Rollen vorhanden. Rollen können in der Aktionsübersicht angelegt werden.")
            ops.planRoles.forEach{role->
                FormOutlinedButton({ops.toggleRole(role.id)},enabled=!ops.busy){Text((if(role.id in ops.roleIds)"✓ " else "")+role.name)}
                if(role.id in ops.roleIds)FormOutlinedButton({ops.primary(role.id)},enabled=!ops.busy){Text(if(role.id==ops.primaryRoleId)"Hauptfunktion ✓" else "Als Hauptfunktion wählen")}
            }
            FormOutlinedButton({ops.primary(null)},enabled=!ops.busy){Text(if(ops.primaryRoleId==null)"Keine Hauptfunktion ✓" else "Ohne Hauptfunktion planen")}
            if(ops.error==OperationsFailure.STALE) {
                Text("Beim Neuladen wird dein bisheriger Entwurf ersetzt.")
                FormOutlinedButton(ops::reloadPlan,enabled=!ops.busy){Text("Entwurf verwerfen und aktuellen Plan laden")}
            }
            FormButton(ops::savePlan,enabled=!ops.busy && !ops.commitConfirmed){Text("Speichern")}
            FormOutlinedButton(ops::cancel,enabled=!ops.busy){Text("Abbrechen")}
        }
        OperationsScreen.LIVE, OperationsScreen.FUNCTION->LiveForm(model,ops,board)
        OperationsScreen.PERSON->ActivePersonWorkspace(model, board)
        OperationsScreen.FUNCTION_DETAIL->ActiveFunctionDetail(model, board)
        OperationsScreen.NEW_FUNCTION->NewActiveFunction(model.operations)
        OperationsScreen.OVERVIEW->{
            if(board.status==ActionStatus.ACTIVE || board.status==ActionStatus.COMPLETED)
                Text(if(board.status==ActionStatus.ACTIVE)"Lagebild" else "Letztes Lagebild",style=MaterialTheme.typography.titleLarge)
            TaskSection(model.tasks,board)
            if(model.tasks.form || model.tasks.selected!=null)return
            if(board.status==ActionStatus.ACTIVE || board.status==ActionStatus.COMPLETED) {
                Text("Gesamt: ${board.persons.size} Personen")
                OperationalStatus.entries.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    pair.forEach{s->Text("${operationalLabel(s)}: ${board.count(s)}",modifier=Modifier.weight(1f))}
                }}
                Text("Aktuelle Funktionen · Vor Ort",style=MaterialTheme.typography.titleMedium)
                board.roles.forEach{Text("${it.name}: ${board.roleCount(it.id)}")}
                Text("Unzugeordnet: ${board.roleCount(null)}")
            }else {
                FormOutlinedButton(ops::roles){Text("Rollen")}
            }
            OperationalPersons(model, board)
            if(board.status==ActionStatus.ACTIVE)FormOutlinedButton(ops::roles){Text("Neue Funktion")}
        }
    }
}
@Composable internal fun OperationalPersons(model: ProductViewModel, board: OperationalBoard) {
    val ops = model.operations
    if (board.status == ActionStatus.ACTIVE) { ActivePeopleList(model, board); return }
    board.persons.forEach{p->
        val planned=model.formatPlannedRoles(p,board)?:"Keine geplanten Rollen"
        FormOutlinedButton(onClick={if(board.status==ActionStatus.ACTIVE)ops.update(p.person.id) else model.personDetail(p.person.id)},modifier=Modifier.fillMaxWidth(),enabled=!ops.busy){
            Column(Modifier.fillMaxWidth()){
                Text(p.person.pseudonym,style=MaterialTheme.typography.titleLarge)
                Text("Geplant: $planned")
                p.plan.primaryRoleId?.let{id->Text("Hauptfunktion: ${board.roles.find{it.id==id}?.name.orEmpty()}")}
                p.state?.let{s->
                    Text(operationalLabel(s.status))
                    s.currentRoleId?.let{id->Text("Aktuelle Funktion: ${board.roles.find{it.id==id}?.name.orEmpty()}")}
                    s.currentPlaceLabel?.let{Text("Ort / Ziel: $it")}
                    Text(updatedLabel(s.updatedAt))
                }
                val count=model.tasks.board?.affectedCount(p.person.id)?:0
                if(count>0)Text("$count offene Aufgabe"+(if(count==1)"" else "n"))
                if(board.status==ActionStatus.ACTIVE)Text("Lage aktualisieren")
            }
        }
        if(board.status==ActionStatus.ACTIVE){
            FormOutlinedButton({model.activePersonTasks(p.person.id, true)}){Text("Aufgabe hinzufügen · ${p.person.pseudonym}")}
            if((model.tasks.board?.affectedCount(p.person.id)?:0)>0)FormOutlinedButton({model.activePersonTasks(p.person.id, false)}){Text("Aufgaben ansehen")}
        }
    }
    if(board.persons.isEmpty())Text("Noch keine Personen erfasst.")
}
@Composable private fun RoleManagement(model:ProductViewModel,ops:OperationsModel,board:OperationalBoard) {
    Text(if(ops.planning)"Rollen" else "Neue Funktion",style=MaterialTheme.typography.titleLarge)
    if(ops.planning){
        Text("Vorlagen · Auswahl legt eine Rolle für diese Aktion an")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OperationalRules.templates.forEach{template->FormOutlinedButton({ops.createRole(template)},enabled=!ops.busy && board.roles.none{OperationalRules.normalizedName(it.name)==OperationalRules.normalizedName(template)}){Text(template)}}
        }
    }
    ops.templateCreateError?.let{Text(createRoleError(it))}
    if(ops.editingRole!=null || ops.deletingRole!=null){RoleEditor(ops);return}
    if(ops.createSurface==RoleCreateSurface.ROLES)RoleCreateForm(ops,RoleCreateSurface.ROLES,
        if(ops.planning)"Rollenname" else "Funktionsname",if(ops.planning)"Eigene Rolle hinzufügen" else "Neue Funktion hinzufügen")
    else FormButton({ops.beginRoleCreate(RoleCreateSurface.ROLES)},enabled=!ops.busy){Text("Rolle oder Funktion anlegen")}
    if(ops.planning)board.roles.forEach{r->
        val row=model.roleRow(r.id)?:return@forEach
        Text(r.name)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FormOutlinedButton(row.edit,enabled=!ops.busy){Text("Umbenennen")}
            FormOutlinedButton(row.delete,enabled=!ops.busy){Text("Löschen")}
        }
    }
    FormOutlinedButton(ops::cancel,enabled=!ops.busy){Text("Zurück")}
}
/** Every text create surface delegates draft lifetime and completion to the same model contract. */
@Composable internal fun RoleCreateForm(ops:OperationsModel,surface:RoleCreateSurface,label:String,saveLabel:String) {
    if(ops.createSurface!=surface)return
    ops.createError?.let{Text(createRoleError(it))}
    FormTextField(ops.createName,ops::changeRoleCreateName,label={Text(label)},autoFocus=true,
        keyboardOptions=productKeyboard(ProductTextKind.ROLE),enabled=!ops.busy,modifier=Modifier.fillMaxWidth())
    FormButton(ops::saveRoleCreate,enabled=!ops.busy && OperationalRules.validName(ops.createName)){Text(saveLabel)}
}
internal fun createRoleError(reason:OperationsFailure)=when(reason){
    OperationsFailure.INVALID->"Bitte einen Rollen- oder Funktionsnamen eingeben."
    OperationsFailure.NOT_FOUND->"Die Aktion ist nicht mehr verfügbar."
    OperationsFailure.REVISION_EXHAUSTED->"Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    OperationsFailure.STALE ->"Die Erstellung wurde nicht freigegeben. Der Entwurf bleibt erhalten."
    else->operationsError(reason)
}
/** Both productive role surfaces use the same snapshot-bound editor/confirmation. */
@Composable internal fun RoleEditor(ops:OperationsModel) {
    val editing=ops.editingRole;val deleting=ops.deletingRole
    ops.error?.let{Text(if(it==OperationsFailure.STALE)
        "Die Rolle wurde inzwischen geändert. Dein Entwurf bzw. die Löschbestätigung bleibt erhalten." else operationsError(it))}
    if(editing!=null){
        Text("Rolle umbenennen · ${editing.name}")
        FormTextField(ops.roleName,ops::changeRoleName,label={Text("Neuer Rollenname")},autoFocus=true,
            keyboardOptions=productKeyboard(ProductTextKind.ROLE),enabled=!ops.busy,modifier=Modifier.fillMaxWidth())
        FormButton(ops::saveRoleRename,enabled=!ops.busy && OperationalRules.validName(ops.roleName)){Text("Speichern")}
    }else if(deleting!=null){
        Text("Rolle „${deleting.name}“ löschen? Zugeordnete Rollen können nicht gelöscht werden.")
        FormButton(ops::confirmRoleDelete,enabled=!ops.busy){Text("Löschen bestätigen")}
    }
    if(ops.error==OperationsFailure.STALE){
        Text("Beim Neuladen wird der bisherige Entwurf bzw. die alte Löschbestätigung ersetzt. Danach erneut prüfen.")
        FormOutlinedButton(ops::reloadRoleEditor,enabled=!ops.busy){Text("Alten Stand verwerfen und Rolle neu laden")}
    }
    FormOutlinedButton(ops::cancelRoleEditor,enabled=!ops.busy){Text(if(deleting!=null)"Löschung abbrechen" else "Bearbeitung verwerfen")}
}
@Composable private fun LiveForm(model:ProductViewModel,ops:OperationsModel,board:OperationalBoard) {
    Text(ops.person?.person?.pseudonym.orEmpty(),style=MaterialTheme.typography.headlineLarge)
    val functionOnly = ops.screen == OperationsScreen.FUNCTION
    Text(if (functionOnly) "Funktion ändern" else "Lage aktualisieren",style=MaterialTheme.typography.titleLarge)
    if (!functionOnly) OperationalStatus.entries.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        pair.forEach{status->FormOutlinedButton({ops.status(status)},modifier=Modifier.weight(1f).heightIn(min=56.dp),enabled=!ops.busy){Text((if(status==ops.liveStatus)"✓ " else "")+operationalLabel(status))}}
    }}
    if(ops.liveStatus==OperationalStatus.ON_SITE){
        Text("Aktuelle Funktion",style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FormOutlinedButton({ops.function(null)},enabled=!ops.busy){Text((if(ops.liveRoleId==null)"✓ " else "")+"Unzugeordnet")}
            board.preferredRoles(ops.personId!!).forEach{r->FormOutlinedButton({ops.function(r.id)},enabled=!ops.busy){Text((if(ops.liveRoleId==r.id)"✓ " else "")+r.name)}}
        }
        FormOutlinedButton({if(ops.createSurface==RoleCreateSurface.INLINE)ops.cancelRoleCreate() else ops.beginRoleCreate(RoleCreateSurface.INLINE)},enabled=!ops.busy){Text(if(ops.createSurface==RoleCreateSurface.INLINE)"Neue Funktion abbrechen" else "Neue Funktion")}
        if(ops.createSurface==RoleCreateSurface.INLINE)RoleCreateForm(ops,RoleCreateSurface.INLINE,"Funktionsname","Hinzufügen")
    }
    if(!functionOnly && OperationalRules.placeRelevant(ops.liveStatus)){
        Text("Ort / Ziel · kurze lokale Bezeichnung, keine Notizen")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            board.placeSuggestions.forEach{place->FormOutlinedButton({ops.place(place)},enabled=!ops.busy){Text(place)}}
        }
        FormTextField(ops.livePlace,ops::place,label={Text("Ort / Ziel")},keyboardOptions=productKeyboard(ProductTextKind.PLACE),enabled=!ops.busy,singleLine=true,
            supportingText={Text("${Character.codePointCount(ops.livePlace,0,ops.livePlace.length)} / 80 Zeichen")},modifier=Modifier.fillMaxWidth())
    }
    if(ops.error==OperationsFailure.STALE){
        Text("Beim Neuladen wird dein bisheriger Entwurf ersetzt.")
        FormOutlinedButton(ops::reloadLive,enabled=!ops.busy){Text("Entwurf verwerfen und Lage neu laden")}
    }
    FormButton(ops::saveLive,enabled=!ops.busy){Text("Übernehmen")}
    FormOutlinedButton(ops::cancel,enabled=!ops.busy){Text("Abbrechen")}
    FormOutlinedButton(model::operationalPersonDetail,enabled=!ops.busy){Text("Personendetails")}
}
internal fun operationalLabel(status:OperationalStatus)=when(status){
    OperationalStatus.NO_STATUS->"Keine Lageangabe";OperationalStatus.ON_SITE->"Vor Ort"
    OperationalStatus.IDENTITY_CHECK->"Identitätsfeststellung";OperationalStatus.IN_CUSTODY->"In Gewahrsam"
    OperationalStatus.NOT_REACHABLE->"Nicht erreichbar";OperationalStatus.RELEASED->"Wieder frei"
    OperationalStatus.LEFT->"Nicht mehr vor Ort";OperationalStatus.UNCLEAR->"Unklar"
}
private fun updatedLabel(time:Long)=if(time==0L)"Aktualisierungszeit unbekannt" else "Aktualisiert: "+Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
internal fun operationsError(failure:OperationsFailure)=when(failure){
    OperationsFailure.TASK_EVENT_LIMIT_REACHED->taskFailureMessage(failure)
    OperationsFailure.NOT_FOUND->"Die Person oder Rolle ist in dieser Aktion nicht vorhanden."
    OperationsFailure.NOT_ALLOWED->"Diese Änderung ist im aktuellen Aktionsstatus nicht möglich."
    OperationsFailure.INVALID->"Bitte Rollenname, Hauptfunktion oder Ort prüfen (maximal 80 Zeichen)."
    OperationsFailure.DUPLICATE->"Dieser Rollenname ist in der Aktion bereits vergeben."
    OperationsFailure.IN_USE->"Die Rolle wird noch von einer Person oder Aufgabe verwendet und kann nicht gelöscht werden."
    OperationsFailure.REVISION_EXHAUSTED->"Dieser Datensatz kann nicht weiter geändert werden (Revisionsgrenze)."
    OperationsFailure.OWNER_UNCONFIRMED -> "Die Ansicht kann nicht geladen werden: Ein vorheriger Datenzugriff ist noch nicht nachweislich beendet."
    OperationsFailure.BUDGET_REFUSED->"Die Daten überschreiten das zulässige Speicherbudget."
    OperationsFailure.STALE ->"Die Lage wurde inzwischen geändert. Bitte neu öffnen."
}
