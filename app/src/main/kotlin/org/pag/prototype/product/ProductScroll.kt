package org.pag.prototype.product

import androidx.compose.runtime.*
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.pag.domain.*

/** Fixed UI destination names only: no content, document/person IDs, credentials or result cache. */
internal fun productNavigationKey(m:ProductViewModel):String = when {
    m.documents.available -> when {
        m.documents.pdfViewed!=null->"PDF"
        m.documents.textViewed!=null->"TEXT"
        m.documents.viewed!=null->"IMAGE"
        m.documents.camera.mode!=CameraMode.CLOSED->"CAMERA"
        m.documents.importing->"IMPORT"
        else->"DOCUMENTS"
    }
    m.creating || m.editing!=null->"ACTION_FORM"
    m.selected==null->"ACTIONS"
    m.personDetailLoading || m.selectedPerson!=null->"PERSON"
    m.personForm->"PERSON_FORM"
    m.sensitiveSetup || m.sensitiveArea!=null->"SENSITIVE"
    m.operations.screen!=OperationsScreen.OVERVIEW->"OPS_${m.operations.screen}"
    m.tasks.form->"TASK_FORM"
    m.tasks.selected!=null->"TASK"
    m.memoryReports.available && m.memoryReports.mode!=MemoryReportMode.LIST->"MEMORY_${m.memoryReports.mode}_${m.memoryReports.sectionOrdinal}"
    m.protocol.nested->"PROTOCOL_${m.protocol.mode}_${m.protocol.form}"
    m.material.form || m.material.selected!=null->"MATERIAL_EDITOR"
    m.expenses.form || m.expenses.selected!=null->"EXPENSE_EDITOR"
    m.selected?.status==ActionStatus.ACTIVE->"ACTIVE_${m.activeArea}"
    m.selected?.status==ActionStatus.PREPARATION->"PREPARATION_${m.preparationArea}"
    m.completedMemoryReportsOpen->"COMPLETED_MEMORY"
    m.completedProtocolOpen->"COMPLETED_PROTOCOL"
    m.completedMaterialOpen->"COMPLETED_MATERIAL"
    m.completedExpensesOpen->"COMPLETED_EXPENSES"
    else->"ACTION"
}
internal fun productReadPending(m:ProductViewModel):Boolean = when {
    m.documents.available->with(m.documents){loading || imageLoading || textLoading || pdfLoading}
    m.detailLoading || m.personDetailLoading->true
    m.selected==null->m.actionListState==ActionListState.Loading
    m.personsVisible && m.workspace==null->true
    else->m.memoryReports.loading || m.protocol.loading || m.material.loading || m.expenses.loading
}
/** Opt-in test observation only; no content/IDs, no authority, null in the product. */
internal val LocalScrollDiagnostic = staticCompositionLocalOf<((Int, Constraints) -> Unit)?> { null }


/** Generation/action-scoped pixels only. Read results are always loaded through existing gates.
 * Keep the old viewport extent while blank/loading, never the old sensitive display. */
internal fun loadingViewportMinimum(viewportHeightPx:Int, verticalPaddingPx:Int):Int =
    if(viewportHeightPx==Constraints.Infinity)0 else (viewportHeightPx-verticalPaddingPx).coerceAtLeast(0)

@Composable internal fun productScrollGeometry(model:ProductViewModel,scroll:ScrollState,viewportHeightPx:Int,positions:ProductScrollPositions):Modifier {
    val destination=productNavigationKey(model)
    val waiting=productReadPending(model)
    // Overview return positions are useful. A deliberate editor/section entry starts
    // at its context/heading, rather than restoring the old submission footer.
    val retain=destination.startsWith("ACTIVE_") || destination.startsWith("PREPARATION_") ||
        destination in setOf("ACTIONS","DOCUMENTS","COMPLETED_MEMORY","COMPLETED_PROTOCOL","COMPLETED_MATERIAL","COMPLETED_EXPENSES")
    val transient=remember(destination,model.snapshot.generation,model.selected?.id){ProductScrollPositions.Anchor()}
    val geometry=if(!retain)transient else positions.at(destination)
    DisposableEffect(geometry,waiting) {
        val savePosition = !waiting
        onDispose{if(savePosition)geometry.pixel=scroll.value}
    }
    LaunchedEffect(geometry,waiting){
        if(!waiting){withFrameNanos{};scroll.scrollTo(geometry.pixel)}
    }
    val minimum=if(waiting)with(LocalDensity.current){viewportHeightPx.toDp()} else 0.dp
    val observe=LocalScrollDiagnostic.current
    val diagnostic=if(observe==null)Modifier else Modifier.layout { measurable,constraints ->
        observe(with(this){minimum.roundToPx()},constraints)
        val child=measurable.measure(constraints)
        layout(child.width,child.height){child.placeRelative(0,0)}
    }
    return diagnostic.heightIn(min=minimum)
}
