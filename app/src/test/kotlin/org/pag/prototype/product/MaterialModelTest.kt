package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

internal class MaterialFake : MaterialRepository {
    var boardReads = 0; var summaryReads = 0; var writes = 0
    var input: MaterialInput? = null; var expected: MaterialItem? = null
    var boardCallback: ((OperationsResult<MaterialBoard>) -> Unit)? = null
    var summaryCallback: ((OperationsResult<MaterialSummary>) -> Unit)? = null
    var saved: ((OperationsResult<MaterialItem>) -> Unit)? = null
    var deleted: ((OperationsResult<Unit>) -> Unit)? = null
    var onBoard: () -> Unit = {}
    override fun summary(actionId: String, publish: (OperationsResult<MaterialSummary>) -> Unit) { summaryReads++; summaryCallback = publish }
    override fun board(actionId: String, publish: (OperationsResult<MaterialBoard>) -> Unit) { boardReads++; boardCallback = publish; onBoard() }
    override fun create(actionId: String, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit) { writes++; this.input = input; saved = publish }
    override fun edit(actionId: String, expected: MaterialItem, input: MaterialInput, publish: (OperationsResult<MaterialItem>) -> Unit) { writes++; this.expected = expected; this.input = input; saved = publish }
    override fun delete(actionId: String, expected: MaterialItem, publish: (OperationsResult<Unit>) -> Unit) { writes++; this.expected = expected; deleted = publish }
}
class MaterialModelTest {
    private var session = SessionSnapshot(SessionState.READY, 1)
    private var action = Action("a", "synthetic", "synthetic", 1, 2, 0, ActionStatus.ACTIVE)
    private val port = MaterialFake()
    private var interactions = 0; private var refreshes = 0; private var feedbacks = 0
    private var onInteraction: () -> Unit = {}; private var onLeave: () -> Unit = {}
    private val model = MaterialModel(port, { session }, { action }, { interactions++; onInteraction() }, { onLeave() }, { refreshes++ }, { feedbacks++ })
    private val item = MaterialItem("m", "a", "synthetic", "2 Rollen", "Kiste", "p", "t", "GLUE", 100)
    private fun start(phase: ActionStatus = ActionStatus.ACTIVE) {
        action = action.copy(status = phase)
        if (phase == ActionStatus.COMPLETED) model.openCompleted {} else model.enter()
        if (model.available) port.boardCallback!!(OperationsResult.Value(MaterialBoard("a", listOf(item))))
    }
    private fun empty() {
        assertNull(model.board); assertNull(model.selected); assertNull(model.input); assertNull(model.expected)
        assertFalse(model.form); assertFalse(model.busy); assertFalse(model.loading); assertFalse(model.confirmingDelete)
        assertNull(model.error); assertFalse(model.available)
    }
    @Test fun templateOnlyOpensDraftAndFreeEntryHasNoKey() {
        start(); model.create(MaterialRules.templates.first()); assertEquals(MaterialRules.templates.first().input(), model.input); assertEquals(0, port.writes)
        model.cancel(); model.create(); assertEquals(MaterialInput(""), model.input); assertNull(model.expected)
    }
    @Test fun editFreezesFullExpectedAndPreservesTemplateKey() {
        start(); model.edit(item); model.change(item.input().copy(name = "changed", sourceTemplateKey = "WATER")); model.save()
        assertEquals(item, port.expected); assertEquals("GLUE", port.input!!.sourceTemplateKey); assertEquals("changed", port.input!!.name)
    }
    @Test fun cancelAndDeleteConfirmationCancelNeverWrite() {
        start(); model.create(MaterialRules.templates.first()); model.cancel(); assertFalse(model.form)
        model.edit(item); model.delete(); assertEquals(0, port.writes)
        model.requestDelete(); assertTrue(model.confirmingDelete); model.cancelDelete(); model.delete(); assertEquals(0, port.writes)
        model.cancel(); assertNull(model.expected); assertNull(model.selected)
    }
    @Test fun deleteUsesFullSnapshotOnlyAfterConfirmationInBothWritablePhases() {
        for (phase in listOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE)) {
            start(phase); model.edit(item); model.requestDelete(); model.delete(); assertEquals(item, port.expected)
            port.deleted!!(OperationsResult.Value(Unit)); assertFalse(model.form); assertFalse(model.confirmingDelete)
        }
        assertEquals(2, port.writes); assertEquals(2, feedbacks)
    }
    @Test fun personAndTaskLinksCanBeSelectedAndRemovedIndependently() {
        start(); model.create(); model.change(MaterialInput("x", responsiblePersonId = "p", linkedTaskId = "done"))
        model.change(model.input!!.copy(responsiblePersonId = null)); assertEquals("done", model.input!!.linkedTaskId)
        model.change(model.input!!.copy(responsiblePersonId = "q", linkedTaskId = "cancelled")); model.save()
        assertEquals("q", port.input!!.responsiblePersonId); assertEquals("cancelled", port.input!!.linkedTaskId)
        port.saved!!(OperationsResult.Rejected(OperationsFailure.INVALID)); model.change(model.input!!.copy(linkedTaskId = null))
        assertEquals("q", model.input!!.responsiblePersonId); assertNull(model.input!!.linkedTaskId)
    }
    @Test fun completedRequiresExplicitEntryAndIsAlwaysReadOnly() {
        action = action.copy(status = ActionStatus.COMPLETED)
        model.refresh(); model.create(); model.save(); assertEquals(0, port.boardReads); assertEquals(0, port.writes)
        start(ActionStatus.COMPLETED); assertTrue(model.completedView); assertFalse(model.writable)
        model.create(); model.edit(item); model.requestDelete(); model.delete(); model.save()
        assertNull(model.input); assertEquals(0, port.writes); assertEquals(0, port.summaryReads)
    }
    @Test fun draftAndDeletionPendingHaveNoMaterialPath() {
        for (phase in listOf(ActionStatus.DRAFT, ActionStatus.DELETION_PENDING)) {
            start(phase); model.refresh(); model.create(); model.edit(item); model.save(); empty()
        }
        assertEquals(0, port.boardReads); assertEquals(0, port.writes)
    }
    @Test fun initialLoadingErrorRetryEmptyAndRefreshRetainHonestStates() {
        model.enter(); assertTrue(model.loading); assertNull(model.board)
        port.boardCallback!!(OperationsResult.Rejected(OperationsFailure.NOT_FOUND)); assertFalse(model.loading); assertNull(model.board)
        model.reload(); port.boardCallback!!(OperationsResult.Value(MaterialBoard("a", emptyList()))); assertTrue(model.board!!.items.isEmpty())
        model.refresh(); port.boardCallback!!(OperationsResult.Value(MaterialBoard("a", listOf(item)))); val old = model.board
        model.refresh(); assertSame(old, model.board)
        port.boardCallback!!(OperationsResult.Rejected(OperationsFailure.NOT_FOUND)); assertSame(old, model.board); assertNotNull(model.error)
    }
    @Test fun staleRetainsDraftBlocksBlindRetryAndExplicitReloadDiscardsExpected() {
        start(); model.edit(item); model.change(model.input!!.copy(name = "draft")); model.save()
        port.saved!!(OperationsResult.Rejected(OperationsFailure.STALE)); model.save(); assertEquals(1, port.writes)
        assertEquals("draft", model.input!!.name); assertEquals(item, model.expected)
        model.reload(); assertNull(model.input); assertNull(model.expected); assertTrue(model.loading)
        val next = item.copy(name = "current"); port.boardCallback!!(OperationsResult.Value(MaterialBoard("a", listOf(next))))
        model.edit(next); assertEquals(next, model.expected)
    }
    @Test fun pendingReadCannotClearStaleAfterOpeningEditor() {
        start(); model.refresh(); val oldRead = port.boardCallback!!; model.edit(item); model.save()
        port.saved!!(OperationsResult.Rejected(OperationsFailure.STALE)); oldRead(OperationsResult.Value(MaterialBoard("a", emptyList())))
        assertEquals(OperationsFailure.STALE, model.error); assertNotNull(model.input); assertFalse(model.loading)
    }
    @Test fun invalidDraftRemainsAndValidRetryWritesFullInput() {
        start(); model.create(); val input = MaterialInput("🦋".repeat(120), "🦋".repeat(40), "🦋".repeat(80))
        model.change(input.copy(quantityLabel = "🦋".repeat(41))); model.save(); assertEquals(0, port.writes); assertNotNull(model.input)
        model.change(input); model.save(); assertEquals(input, port.input)
    }
    @Test fun lockActionGenerationAndPhaseClearRejectAllLateReadsAndWrites() {
        for (exit in listOf("lock", "generation", "action", "phase")) {
            start(); val read = port.boardCallback!!; model.edit(item); model.save(); val write = port.saved!!
            when (exit) {
                "lock" -> session = SessionSnapshot(SessionState.LOCKED, session.generation + 1)
                "generation" -> session = SessionSnapshot(SessionState.READY, session.generation + 1)
                "action" -> action = action.copy(id = "b")
                else -> action = action.copy(status = ActionStatus.COMPLETED)
            }
            model.clear(); read(OperationsResult.Value(MaterialBoard("a", listOf(item)))); write(OperationsResult.Value(item)); empty()
            session = SessionSnapshot(SessionState.READY, session.generation + 1); action = action.copy(id = "a", status = ActionStatus.ACTIVE)
        }
        assertEquals(0, feedbacks); assertEquals(0, refreshes)
    }
    @Test fun revokedInteractionAndSensitiveCleanupCannotRepopulateDraft() {
        start(); onInteraction = { session = SessionSnapshot(SessionState.LOCKED, 2); model.clear() }; model.create(); empty()
        session = SessionSnapshot(SessionState.READY, 3); onInteraction = {}; start()
        onLeave = { session = SessionSnapshot(SessionState.LOCKED, 4); model.clear() }; model.edit(item); empty()
    }
    @Test fun synchronousRefreshRevokeAfterSaveStopsFeedbackAndWorkspaceRead() {
        start(); model.create(MaterialRules.templates.first()); model.save()
        port.onBoard = { session = SessionSnapshot(SessionState.LOCKED, 2); model.clear() }
        port.saved!!(OperationsResult.Value(item)); empty(); assertEquals(0, refreshes); assertEquals(0, feedbacks)
    }
    @Test fun readRefreshAndWriteCallbacksNeverCountAsInteractionsAndDuplicateResultIsIgnored() {
        start(); val before = interactions; model.refresh(); port.boardCallback!!(OperationsResult.Value(MaterialBoard("a", listOf(item))))
        assertEquals(before, interactions); model.create(MaterialRules.templates.first()); model.save(); val tapped = interactions
        val result = port.saved!!; result(OperationsResult.Value(item)); val reads = port.boardReads
        result(OperationsResult.Value(item)); assertEquals(reads, port.boardReads); assertEquals(1, feedbacks); assertEquals(tapped, interactions)
    }
    @Test fun wrongActionBoardNeverEntersReachableState() {
        model.enter(); port.boardCallback!!(OperationsResult.Value(MaterialBoard("b", listOf(item.copy(actionId = "b")))))
        assertNull(model.board); assertNotNull(model.error)
    }
}
