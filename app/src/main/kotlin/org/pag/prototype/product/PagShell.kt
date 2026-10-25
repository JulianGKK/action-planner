package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.pag.domain.Action
import org.pag.domain.ProductStorageState
import org.pag.domain.SessionState

/** Display threshold only; the coordinator still decides validity at the tap. */
internal fun sessionRestartVisible(state: SessionState, remainingMillis: Long?): Boolean =
    state == SessionState.READY && remainingMillis != null && remainingMillis > 0 && remainingMillis <= 30_000

/** One UI publication, not a long-lived DB connection or new authorization. */
data class WorkspaceSnapshot(val actionId: String, val generation: Long, val persons: List<org.pag.domain.Person>,
    val operations: org.pag.domain.OperationalBoard?, val tasks: org.pag.domain.TaskBoard?, val status: org.pag.domain.ActionStatus,
    val leadershipState: org.pag.domain.SensitiveResult<org.pag.domain.SensitiveState>? = null,
    val protocolSummary: org.pag.domain.ProtocolSummary? = null,
    val materialSummary: org.pag.domain.MaterialSummary? = null, val expenseSummary: org.pag.domain.ExpenseSummary? = null,
    val memoryReportSummary: org.pag.domain.MemoryReportSummary? = null,
    val documentSummary: org.pag.domain.DocumentSummary? = null,
    val access:org.pag.domain.WorkspaceAccess?=null,
    val materialBoard:org.pag.domain.MaterialBoard?=null,val expenseBoard:org.pag.domain.ExpenseBoard?=null) {
    internal var uiRows:WorkspaceUiRows?=null; private set
    internal fun bindRows(rows:WorkspaceUiRows){check(uiRows==null);uiRows=rows}
}

sealed interface ActionListState {
    data object Loading : ActionListState
    data class Content(val actions: List<Action>) : ActionListState
    data object Empty : ActionListState
    /** Storage errors terminate the session as BLOCKED; there is no local retry. */
    data object Error : ActionListState
}

internal fun countdownText(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable internal fun PagPrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    FormButton(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(4.dp)) { Text(label) }
}
@Composable internal fun PagSecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FormOutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled,
        shape = RoundedCornerShape(4.dp)) { Text(label) }
}
@Composable internal fun PagTertiaryButton(label: String, onClick: () -> Unit) {
    FormTextButton(onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}

/** Reserve the actual label geometry immediately; reveal changes pixels/semantics only. */
@Composable internal fun PagReadStatus(loading:Boolean,label:String) {
    var visible by remember(loading,label){mutableStateOf(false)}
    LaunchedEffect(loading,label){if(loading){delay(200);visible=true}}
    Box(Modifier.fillMaxWidth().heightIn(min=48.dp)) {
        Text(label,style=MaterialTheme.typography.bodyMedium,
            modifier=Modifier.alpha(if(loading && visible)1f else 0f).clearAndSetSemantics {
                if(loading && visible){text=androidx.compose.ui.text.AnnotatedString(label);liveRegion=LiveRegionMode.Polite;heading()}
            })
    }
}
@Composable internal fun PagLoadingState(label:String="Inhalt wird geladen …")=PagReadStatus(true,label)
@Composable internal fun PagEmptyState(onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Noch keine Aktionen", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        Text("Plane deine erste Aktion und füge anschließend Personen, Rollen und Aufgaben hinzu.")
        PagPrimaryButton("Aktion planen", onCreate)
    }
}
@Composable internal fun PagErrorState() {
    Column(Modifier.fillMaxWidth().heightIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Geschützter Speicher nicht verfügbar", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        Text("Der geschützte Speicher kann nicht sicher verwendet werden. Es wurden keine Daten ersetzt oder repariert.")
    }
}

@Composable internal fun PagLockedScreen(bootstrap: ProductStorageState, unlock: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScrollForShell().padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PaG", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text("Aktionsplaner", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(40.dp))
            if (bootstrap == ProductStorageState.PARTIAL_OR_INVALID) PagErrorState()
            else {
                Text("PaG ist gesperrt.", style = MaterialTheme.typography.titleLarge)
                Text("Deine Aktionsdaten sind geschützt.")
                Spacer(Modifier.height(24.dp))
                if (bootstrap == ProductStorageState.EMPTY) {
                    Text("Richte den geschützten Speicher ein, um deine Aktionen auf diesem Gerät zu planen.")
                    Spacer(Modifier.height(16.dp))
                    PagPrimaryButton("Geschützten Speicher einrichten", unlock)
                } else PagPrimaryButton("Entsperren", unlock)
            }
        }
    }
}

@Composable private fun Modifier.verticalScrollForShell(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

/** No live region: the description is available on focus without announcing every tick. */
@Composable internal fun PagSessionCountdown(millis: Long?) {
    if (millis == null) return
    val seconds = (millis.coerceAtLeast(0) / 1000).coerceAtLeast(1)
    Text("🔒 ${countdownText(millis.coerceAtLeast(1000))}", fontFamily = FontFamily.Monospace,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Sitzung: maximal noch ${seconds / 60} ${if(seconds / 60 == 1L) "Minute" else "Minuten"} ${seconds % 60} Sekunden. Inaktivität kann früher sperren."
        })
}

data class StatusFeedback(val id: Long, val text: String)
internal const val FEEDBACK_MIN_DURATION_MILLIS = 5000L
internal fun bannerProgress(elapsedMillis: Long, duration: Long): Float =
    if (duration <= 0) 0f else (1.0 - elapsedMillis.coerceAtLeast(0).toDouble() / duration).coerceIn(0.0, 1.0).toFloat()

internal suspend fun expireFeedback(id: Long, duration: Long, dismiss: (Long) -> Unit, progress: ((Float) -> Unit)? = null) {
    if (progress == null) { delay(duration.coerceAtLeast(0)); dismiss(id); return }
    // UX lifetime only. This clock never supplies a session deadline or interaction.
    val start = System.nanoTime()
    progress(1f)
    var elapsed = 0L
    while (elapsed < duration) {
        delay(minOf(50L, duration - elapsed))
        elapsed = (System.nanoTime() - start) / 1_000_000
        progress(bannerProgress(elapsed, duration))
    }
    dismiss(id)
}

/** Content and overlay are measured independently; feedback cannot reflow content. */
@Composable internal fun PagWorkspace(feedback: StatusFeedback?, dismiss: (Long) -> Unit,
    modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        content()
        PagStatusBannerHost(feedback, dismiss)
    }
}

/** Overlay host is a sibling of the scrolling content, never part of its layout. */
@Composable internal fun BoxScope.PagStatusBannerHost(feedback: StatusFeedback?, dismiss: (Long) -> Unit) {
    if (feedback != null) key(feedback.id) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp)) {
            PagStatusBanner(feedback, dismiss)
        }
    }
}

/** Ephemeral result feedback, one announcement and one expiry wakeup. */
@Composable internal fun PagStatusBanner(feedback: StatusFeedback, dismiss: (Long) -> Unit) {
    val accessibility = LocalAccessibilityManager.current
    val duration = accessibility?.calculateRecommendedTimeoutMillis(FEEDBACK_MIN_DURATION_MILLIS,
        containsIcons = false, containsText = true, containsControls = false) ?: FEEDBACK_MIN_DURATION_MILLIS
    LaunchedEffect(feedback.id, duration) { expireFeedback(feedback.id, duration, dismiss) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp), shadowElevation=2.dp) {
        Column {
            Text("✓ ${feedback.text}", Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
