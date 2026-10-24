package org.pag.prototype.product

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.pag.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable internal fun SensitiveSection(model:ProductViewModel) {
    Text("Sensible Personendaten",style=MaterialTheme.typography.titleMedium)
    model.sensitiveError?.let {
        Text(if(it==SensitiveFailure.INVALID_PASSPHRASE) "Bitte eine Passphrase mit 16 bis 128 Zeichen verwenden und identisch bestätigen."
            else "Der sensible Zugriff ist nicht möglich. Es wurden keine Ersatzschlüssel erzeugt.")
    }
    when {
        model.sensitiveSetup -> {
            NoSensitiveCapture()
            Text("Verwende eine lange Passphrase nur für diese Aktion. Mehrere nicht naheliegende Wörter sind besser als ein kurzes oder wiederverwendetes Passwort.")
            Text("Ein starkes Leitungsgeheimnis gilt für diese Aktion. Bei Verlust sind die sensiblen Daten nicht wiederherstellbar. Der Gerätecode ist kein Ersatz.")
            var password by remember { mutableStateOf("") }
            var confirmation by remember { mutableStateOf("") }
            PassphraseField("Leitungs-Passphrase",password,!model.sensitiveBusy,setupCounter=true,imeAction=androidx.compose.ui.text.input.ImeAction.Next) { model.interaction();password=it }
            PassphraseField("Passphrase wiederholen",confirmation,!model.sensitiveBusy) { model.interaction();confirmation=it }
            FormButton(onClick={model.setupSensitive(password.toCharArray(),confirmation.toCharArray());password="";confirmation=""},enabled=!model.sensitiveBusy){Text("Bewusst einrichten")}
            FormOutlinedButton(onClick=model::closeSensitive){Text("Abbrechen")}
        }
        model.sensitiveOpened != null -> {
            NoSensitiveCapture()
            val opened=model.sensitiveOpened!!
            if(opened.grant.valid()) key(opened.grant) {
                Text("Kurzzeitig freigegeben: ${areaLabel(opened.grant.area)}. Spätestens nach 60 Sekunden erneut geschützt.")
                SensitiveFields(model,opened.data)
                FormOutlinedButton(onClick=model::closeSensitive){Text("Sensible Ansicht schließen")}
            }
        }
        model.sensitiveArea != null -> {
            NoSensitiveCapture()
            Text("${areaLabel(model.sensitiveArea!!)} öffnen")
            var password by remember(model.sensitiveArea) { mutableStateOf("") }
            PassphraseField("Leitungs-Passphrase",password,!model.sensitiveBusy) {model.interaction();password=it}
            FormButton(onClick={model.openSensitive(password.toCharArray());password=""},enabled=!model.sensitiveBusy){Text("Bereich freigeben")}
            FormOutlinedButton(onClick=model::closeSensitive){Text("Abbrechen")}
        }
        else -> when(model.sensitiveInventory?.state) {
            SensitiveState.UNINITIALIZED -> if(model.personsEditable) FormButton(onClick=model::requestSensitiveSetup){Text("Sensible Personendaten einrichten")}
            SensitiveState.INCOMPLETE_SETUP, SensitiveState.DELETE_PENDING -> LeadershipRecoveryOffer(model,model.sensitiveInventory!!.state)
            SensitiveState.PARTIAL_OR_INVALID -> Text("Der sensible Speicher dieser Aktion ist nicht verfügbar. Pseudonyme bleiben nutzbar; es erfolgt keine Reparatur.")
            SensitiveState.READY -> SensitiveArea.entries.forEach { area ->
                val present=area in model.sensitiveInventory!!.areas
                if(present || model.personsEditable) FormOutlinedButton(onClick={model.requestSensitive(area)}) {
                    Text(if(present) when(area){SensitiveArea.IDENTITY->"Identitätsdaten anzeigen";SensitiveArea.CONTACT->"Telefonnummer anzeigen";SensitiveArea.EMERGENCY_CONTACT->"Notfallkontakt anzeigen"}
                        else "${areaLabel(area)} erfassen")
                }
            }
            null -> if(model.sensitiveInventoryLoading) Text("Sensible Bereiche werden geprüft …")
                else if(model.sensitiveError==null) Text("Status der sensiblen Bereiche nicht verfügbar.")
        }
    }
    if(model.sensitiveBusy) Text("Geschützter Vorgang läuft …")
}
/** Window subtree opt-out, no persistent credentials/drafts or cloud Autofill request. */
@Composable private fun NoSensitiveCapture() {
    val root=LocalView.current.rootView
    DisposableEffect(root) {
        val autofill=root.importantForAutofill;val capture=root.importantForContentCapture
        root.importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        root.importantForContentCapture=View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        onDispose {root.importantForAutofill=autofill;root.importantForContentCapture=capture}
    }
}
private fun areaLabel(area:SensitiveArea)=when(area){SensitiveArea.IDENTITY->"Identität";SensitiveArea.CONTACT->"Eigener Kontakt";SensitiveArea.EMERGENCY_CONTACT->"Notfallkontakt"}
@Composable private fun SensitiveFields(model:ProductViewModel,initial:SensitiveData) {
    var first by remember {mutableStateOf(when(initial){is SensitiveData.Identity->initial.fullName;is SensitiveData.Contact->initial.phone;is SensitiveData.EmergencyContact->initial.name}?:"")}
    var second by remember {mutableStateOf((initial as? SensitiveData.EmergencyContact)?.phone?:"")}
    var third by remember {mutableStateOf((initial as? SensitiveData.EmergencyContact)?.relationship?:"")}
    var date by remember {mutableStateOf((initial as? SensitiveData.Identity)?.birthDate)}
    val editing=model.personsEditable
    fun optional(text:String)=text.takeIf{it.isNotEmpty()}
    @Composable fun field(label:String,value:String,ime:androidx.compose.ui.text.input.ImeAction=androidx.compose.ui.text.input.ImeAction.Done,change:(String)->Unit){
        if(editing) FormTextField(value,{model.interaction();change(it)},label={Text("$label (optional)")},enabled=!model.sensitiveBusy,
            keyboardOptions=productKeyboard(if(label=="Telefonnummer")ProductTextKind.PHONE else if(label=="Beziehung")ProductTextKind.DESCRIPTION else ProductTextKind.PERSON).copy(autoCorrectEnabled=false),imeAction=ime,modifier=Modifier.fillMaxWidth())
        else Text("$label: ${value.ifEmpty{"Nicht angegeben"}}")
    }
    when(initial){
        is SensitiveData.Identity->{field("Vollständiger Name",first){first=it};if(editing) BirthDate(date,model::interaction){date=it}else Text("Geburtsdatum: ${date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))?:"Nicht angegeben"}")}
        is SensitiveData.Contact->field("Telefonnummer",first){first=it}
        is SensitiveData.EmergencyContact->{field("Name oder Pseudonym",first,androidx.compose.ui.text.input.ImeAction.Next){first=it};field("Telefonnummer",second,androidx.compose.ui.text.input.ImeAction.Next){second=it};field("Beziehung",third){third=it}}
    }
    if(editing) FormButton(onClick={
        model.saveSensitive(when(initial){is SensitiveData.Identity->SensitiveData.Identity(optional(first),date);is SensitiveData.Contact->SensitiveData.Contact(optional(first));is SensitiveData.EmergencyContact->SensitiveData.EmergencyContact(optional(first),optional(second),optional(third))})
    },enabled=!model.sensitiveBusy){Text("Speichern")}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BirthDate(value:LocalDate?,interaction:()->Unit,chosen:(LocalDate?)->Unit){
    var expanded by remember {mutableStateOf(false)}
    FormOutlinedButton(onClick={interaction();expanded=!expanded}){Text(value?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))?:"Geburtsdatum auswählen (optional)")}
    if(value!=null) FormOutlinedButton(onClick={interaction();chosen(null)}){Text("Geburtsdatum nicht angeben")}
    if(expanded){
        val state=remember {DatePickerState(Locale.getDefault(),initialSelectedDateMillis=value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())}
        DatePicker(state,showModeToggle=false)
        FormOutlinedButton(onClick={interaction();state.selectedDateMillis?.let{chosen(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate());expanded=false}},enabled=state.selectedDateMillis!=null){Text("Übernehmen")}
        FormOutlinedButton(onClick={interaction();expanded=false}){Text("Abbrechen")}
    }
}

enum class LeadershipRecoveryPhase { CONFIRM_REAUTH, AUTHENTICATING, CONFIRM_RESET, WRITING }

@Composable internal fun LeadershipRecoveryOffer(model:ProductViewModel,state:SensitiveState) {
    Text(if(state==SensitiveState.INCOMPLETE_SETUP)
        "Die Einrichtung des Leitungsschutzes wurde nicht abgeschlossen. Sensible Bereiche können derzeit nicht geöffnet werden."
        else "Die Entfernung des Leitungsschutzes wurde nicht abgeschlossen. Sensible Bereiche können derzeit nicht geöffnet werden.")
    if(model.personsEditable)model.selected?.id?.let { actionId ->
        val generation=model.snapshot.generation
        FormOutlinedButton({model.requestLeadershipRecovery(actionId,generation)},enabled=!model.sensitiveBusy) {
            Text(if(state==SensitiveState.INCOMPLETE_SETUP) "Unvollständige Einrichtung zurücksetzen" else "Entfernung des Leitungsschutzes fortsetzen")
        }
    }
}

/** Same Activity window: an additional Dialog window would violate the ORIGINAL strict
 * foreground gate. No special focus allowance or alternate session authorization. */
@Composable internal fun LeadershipRecoverySection(model:ProductViewModel) {
    val intent=model.leadershipRecovery
    val phase=model.leadershipRecoveryPhase
    val generation=model.snapshot.generation
    if(intent!=null && model.snapshot.state==SessionState.READY && phase!=LeadershipRecoveryPhase.AUTHENTICATING) {
        val reset=intent.state==SensitiveState.INCOMPLETE_SETUP
        Text(if(reset) "Unvollständige Einrichtung zurücksetzen" else "Entfernung des Leitungsschutzes fortsetzen",style=MaterialTheme.typography.titleMedium)
        Text("Aktion: ${model.leadershipRecoveryAction?.title ?: "wird geprüft …"}")
        Text(when(phase) {
            LeadershipRecoveryPhase.CONFIRM_REAUTH -> "Dafür ist eine erneute Bestätigung mit dem Gerätesperrcode nötig. Es wird noch nichts zurückgesetzt."
            LeadershipRecoveryPhase.WRITING -> "Geschützter Vorgang läuft …"
            else -> if(reset) "Nur die nachweislich leere, unvollständige Einrichtung wird zurückgesetzt. Jetzt ausdrücklich bestätigen."
                else "Die bereits begonnene Entfernung wird abgeschlossen. Jetzt ausdrücklich bestätigen."
        })
        FormButton(onClick={if(phase==LeadershipRecoveryPhase.CONFIRM_REAUTH)model.authenticateLeadershipRecovery(intent,generation) else model.confirmLeadershipRecovery(intent,generation)},
            enabled=phase==LeadershipRecoveryPhase.CONFIRM_REAUTH || (phase==LeadershipRecoveryPhase.CONFIRM_RESET && model.leadershipRecoveryAction?.status==ActionStatus.PREPARATION)) {
            Text(if(phase==LeadershipRecoveryPhase.CONFIRM_REAUTH) "Mit Gerätesperrcode bestätigen" else "Jetzt bestätigen")
        }
        FormOutlinedButton({model.cancelLeadershipRecovery(intent)},enabled=phase!=LeadershipRecoveryPhase.WRITING){Text("Abbrechen")}
    }
    if(model.leadershipRecoveryError && model.snapshot.state==SessionState.READY) {
        Text("Wiederherstellung nicht abgeschlossen",style=MaterialTheme.typography.titleMedium)
        Text("Der Zustand konnte nicht sicher bestätigt werden. Es wurde kein erfolgreicher Abschluss gemeldet.")
        FormButton(model::dismissLeadershipRecoveryError){Text("Schließen")}
    }
}
