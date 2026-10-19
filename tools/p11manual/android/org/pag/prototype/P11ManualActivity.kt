package org.pag.prototype

import org.pag.prototype.p11fixture.P11FunctionsFixture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.pag.domain.ProductStorageState
import org.pag.domain.Experiment
import org.pag.prototype.product.*

/** Only available through the explicit P11 manual init-script overlay. */
class P11ManualActivity : ComponentActivity() {
    private var syntheticStore = ViewModelStore()

    private fun freshModel(): ProductViewModel {
        syntheticStore.clear()
        syntheticStore = ViewModelStore()
        val operations = P11FunctionsFixture.Ops()
        val session = P11FunctionsFixture.Session(syntheticUnlock = true).apply { lock() }
        val model = ProductViewModel(
            session,
            P11FunctionsFixture.Actions(), ProductStorageState.READY_TO_OPEN,
            P11FunctionsFixture.People(operations), operationsRepository = operations,
            taskRepository = P11FunctionsFixture.Tasks()
        ) { session.authenticate(Experiment.PER_OPERATION) }
        // Give the model its normal onCleared lifecycle, including subscription release.
        val owned = ViewModelProvider(syntheticStore, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = model as T
        })[ProductViewModel::class.java]
        session.authenticate(Experiment.PER_OPERATION)
        owned.detail("synthetic")
        return owned
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        check(packageName == "org.pag.perf.p11probe")
        setContent {
            var model by remember { mutableStateOf(freshModel()) }
            var running by remember { mutableStateOf(false) }
            var scale by remember { mutableFloatStateOf(1f) }
            var leave by remember { mutableStateOf(false) }
            var run by remember { mutableIntStateOf(1) }
            BackHandler(running) { leave = true }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                if (running) {
                    // No observer, instrumentation, toolbar or synthetic banner in ProductScreen.
                    ProductScreen(model)
                } else MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("P11 – synthetischer UI-Test", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                            Text("Nur erfundene Daten im Arbeitsspeicher. Keine Datenbank oder Geräteauthentifizierung. Keine echten Daten eingeben.")
                            Text("Übungsaktion: Motte mit Funktion Support, Klee ohne Funktion. Weitere Funktion: Blockierende.")
                            Text("Schriftfaktor: $scale. Die Geräteeinstellung bleibt unverändert.")
                            listOf(1f, 2f, 3.5f).forEach { value ->
                                OutlinedButton(onClick = { scale = value }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Schriftfaktor ${if (value == 3.5f) "3,5" else value.toInt().toString()}${if (value == scale) " – ausgewählt" else ""}")
                                }
                            }
                            Button(onClick = { running = true }, modifier = Modifier.fillMaxWidth()) { Text("Oberfläche öffnen / fortsetzen") }
                            OutlinedButton(onClick = { model = freshModel(); run++ }, modifier = Modifier.fillMaxWidth()) { Text("Synthetische Daten zurücksetzen") }
                            Text("Durchlauf $run – synthetische Daten bereit", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Text("In der Oberfläche die sichtbaren Zurückknöpfe verwenden. Android-Zurück öffnet die Rückfrage zum Teststart. Dort Schrift wechseln oder Daten zurücksetzen.")
                            Text("Schriftwechsel erhält die Daten, öffnet die Composition aber neu. Activity-Neustart verwirft alle Testdaten. Sperren/Entsperren ist ausschließlich simuliert, ohne echte Fristen oder Credentials.")
                            Text("Nur Funktionsnavigation ist Gegenstand dieser Test-App. Andere Fachbereiche sind nicht vollständig simuliert. TalkBack bitte über Android einschalten.")
                        }
                    }
                }
                if (leave) MaterialTheme {
                    AlertDialog(onDismissRequest = { leave = false },
                        title = { Text("Zum Teststart zurück?") },
                        text = { Text("Die synthetischen Daten bleiben erhalten. Scrollposition und ungespeicherte Oberflächeneingaben können sich beim erneuten Öffnen ändern.") },
                        confirmButton = { TextButton(onClick = { leave = false; running = false }) { Text("Zum Teststart") } },
                        dismissButton = { TextButton(onClick = { leave = false }) { Text("Weiter testen") } })
                }
            }
        }
    }

    override fun onDestroy() {
        syntheticStore.clear()
        super.onDestroy()
    }
}
