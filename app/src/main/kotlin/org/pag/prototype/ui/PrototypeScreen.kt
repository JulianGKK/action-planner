package org.pag.prototype.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.pag.domain.Experiment
import org.pag.domain.SessionState

@Composable
fun PrototypeScreen(model: PrototypeViewModel) {
    val snapshot = model.snapshot
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Prototyp B2 / B0 / B1.6 / B1.5 / historischer A-Pfad · ausschließlich synthetische Tests")
            Text("B0-Testpolicy: absolut 60 s / READY-Inaktivität 15 s; keine Produktwerte")
            Text("Session: ${snapshot.state} · Generation: ${snapshot.generation}")
            Text("Variante: ${snapshot.experiment ?: "keine"}")
            Text("Keystore-Autorisierung: ${snapshot.platformAuthorization} (nur Operationsbeobachtung)")
            Text("Konkrete Schlüsseloperation: ${snapshot.keyOperation}")
            Text("Test-ID: ${snapshot.technicalId}")
            Text("Schlüsseleigenschaften: ${snapshot.keyProperties}")
            listOf(
                Experiment.B2_DIRECT_INITIALIZE to "B2 Direct · neuen Testbestand ausdrücklich initialisieren",
                Experiment.B2_DIRECT_REOPEN to "B2 Direct · bestehenden Testbestand authentifizieren",
                Experiment.B2_ROOM_INITIALIZE to "B2 Room · neuen Testbestand ausdrücklich initialisieren",
                Experiment.B2_ROOM_REOPEN to "B2 Room · bestehenden Testbestand authentifizieren"
            ).forEach { (experiment, label) ->
                Button(onClick = { model.authenticate(experiment) }, enabled = snapshot.state == SessionState.LOCKED) { Text(label) }
            }
            Button(onClick = { model.authenticate(Experiment.DATABASE_INITIALIZE) }, enabled = snapshot.state == SessionState.LOCKED) {
                Text("B1.6 · neuen Testbestand ausdrücklich initialisieren")
            }
            Button(onClick = { model.authenticate(Experiment.DATABASE_REOPEN) }, enabled = snapshot.state == SessionState.LOCKED) {
                Text("B1.6 · bestehende Testdatenbank authentifizieren")
            }
            Button(onClick = { model.authenticate(Experiment.STORAGE_INITIALIZE) }, enabled = snapshot.state == SessionState.LOCKED) {
                Text("B1.5 · leeren Namensraum ausdrücklich initialisieren + authentifizieren")
            }
            Button(onClick = { model.authenticate(Experiment.STORAGE_UNWRAP) }, enabled = snapshot.state == SessionState.LOCKED) {
                Text("B1.5 · bestehenden Schlüssel authentifizieren")
            }
            Button(onClick = { model.authenticate(Experiment.PER_OPERATION) }, enabled = snapshot.state == SessionState.LOCKED) {
                Text("Authentifizieren · operationsgebunden")
            }
            Button(onClick = { model.authenticate(Experiment.TIME_WINDOW) }, enabled = snapshot.state == SessionState.LOCKED) {
                Text("Authentifizieren · Testfenster 15 Sekunden")
            }
            Button(onClick = model::testAccess, enabled = snapshot.state == SessionState.READY && snapshot.experiment !in setOf(
                Experiment.DATABASE_REOPEN, Experiment.B2_DIRECT_REOPEN, Experiment.B2_ROOM_REOPEN)) { Text("Testzugriff") }
            Button(onClick = model::lock) { Text("Sperren") }
            snapshot.marker?.let { Text(it) }
            Text("B2 und B1.6 prüfen die Testdatenbank während MAINTENANCE und schließen sie vor READY. Andere Pfade verwenden den historischen Dummy. Keine produktive Storage-Architektur.")
        }
    }
}
