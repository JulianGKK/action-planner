package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class ProtocolFollowUpModelTest {
    private var session = SessionSnapshot(SessionState.READY, 1)
    private var action = Action("a", "synthetic", "", 0, 1, 0, ActionStatus.COMPLETED)
    private val port = ProtocolFake()
    private var onInteraction: () -> Unit = {}
    private var interactions = 0; private var refreshes = 0
    private var message: String? = null
    private val model = ProtocolModel(port, { session }, { action }, { interactions++; onInteraction() }, { refreshes++ }, { message = it }, { 1000 })
    private val input = ProtocolInput(ProtocolClassification.KEY_EVENT, "synthetic", 1000, officialIdentifiers = listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, "synthetic-id")))
    private fun detail(created: Boolean = false, recorded: Boolean = false, mistaken: Boolean = false): ProtocolEntryDetail {
        val r = ProtocolRevision("r", "a", "e", 1, ProtocolRevisionType.ORIGINAL, input, 2000, recorded, mistaken)
        return ProtocolEntryDetail(ProtocolEntry("e", "a", created), listOf(ProtocolRevisionDetail(r, emptyList(), null)))
    }
    private fun open(created: Boolean = false, recorded: Boolean = false, mistaken: Boolean = false) {
        model.openCompletedFollowUp(); model.open("e"); port.detailCallback!!(ProtocolResult.Value(detail(created, recorded, mistaken)))
    }
    private fun saved() = port.writeCallback!!(ProtocolResult.Value(ProtocolSaved("e", "next", 2)))
    @Test fun completedHasNoAccessBeforeExplicitEntryAndNoSummaryOrDetailPrefetch() {
        assertFalse(model.writable); model.refresh(); model.open("e"); model.create(); model.save()
        assertEquals(0, port.timelineReads); assertEquals(0, port.detailReads); assertEquals(0, port.writes)
        model.openCompletedFollowUp(); assertTrue(model.completedFollowUp); assertEquals(1, port.timelineReads)
        assertEquals(0, port.detailReads); assertEquals(0, port.summaryReads); assertEquals(1, interactions)
    }
    @Test fun modeCannotBeOpenedForAnyOtherStatus() {
        for (status in ActionStatus.entries.filter { it != ActionStatus.COMPLETED }) {
            action = action.copy(status = status); model.openCompletedFollowUp()
            assertEquals(ProtocolUiMode.ACTIVE, model.mode); assertEquals(0, port.timelineReads)
        }
    }
    @Test fun revokingInteractionAndCleanupCannotOpenFollowUp() {
        onInteraction = { session = SessionSnapshot(SessionState.LOCKED, 2); model.clear() }
        model.openCompletedFollowUp(); assertFalse(model.completedFollowUp); assertEquals(0, port.timelineReads)
        session = SessionSnapshot(SessionState.READY, 3); onInteraction = {}
        model.openCompletedFollowUp { session = SessionSnapshot(SessionState.LOCKED, 4); model.clear() }
        assertFalse(model.completedFollowUp); assertEquals(0, port.timelineReads)
    }
    @Test fun activeDraftCannotBecomeCompletedWriteAndExplicitEntryDiscardsIt() {
        action = action.copy(status = ActionStatus.ACTIVE); model.create("POLICE_ARRIVED")
        action = action.copy(status = ActionStatus.COMPLETED); model.save(); assertEquals(0, port.writes)
        assertFalse(model.writable); assertEquals(ProtocolUiMode.ACTIVE, model.mode)
        model.openCompletedFollowUp(); assertNull(model.input); assertNull(model.expected)
        model.save(); assertEquals(0, port.writes)
    }
    @Test fun completedCreateHasExplicitStatusNoInventedRecordedAtAndDifferentiatedFeedback() {
        model.openCompletedFollowUp(); model.create("POLICE_ARRIVED")
        assertNull(model.detail); assertEquals(0, port.writes); model.save()
        assertEquals(ActionStatus.COMPLETED, port.capturedStatus); saved()
        assertEquals("Nachtrag gespeichert", message); assertEquals(0, refreshes)
        assertEquals(1, port.detailReads); assertNull(model.detail)
    }
    @Test fun correctionUsesCompletedFullExpectedAndExplicitMarkerClear() {
        open(mistaken = true); model.revise(ProtocolRevisionType.CORRECTION)
        assertFalse(model.clearMistaken); model.toggleClearMistaken(); model.save()
        assertEquals(ActionStatus.COMPLETED, port.capturedStatus); assertEquals(detail(mistaken = true).current.revision, port.capturedExpected!!.revision)
        assertTrue(port.capturedClear); saved(); assertEquals("Korrektur gespeichert", message)
    }
    @Test fun addendumUsesCompletedAndCannotChangeClassificationOrClearMarker() {
        open(mistaken = true); model.revise(ProtocolRevisionType.ADDENDUM)
        model.change(model.input!!.copy(classification = ProtocolClassification.RELEVANT)); model.toggleClearMistaken(); model.save()
        assertEquals(ActionStatus.COMPLETED, port.capturedStatus); assertFalse(port.capturedClear)
        assertEquals(ProtocolClassification.KEY_EVENT, port.captured!!.classification); saved(); assertEquals("Ergänzung gespeichert", message)
    }
    @Test fun mistakenUsesCompletedAndConfirmationRemainsMandatory() {
        open(); model.markMistaken(); assertEquals(0, port.writes)
        model.requestMistaken(); model.markMistaken(); assertEquals(ActionStatus.COMPLETED, port.capturedStatus); saved()
        assertEquals("Irrtumsmarkierung gespeichert", message)
    }
    @Test fun staleKeepsDraftExpectedAndModeWithoutRetryOrStatusChange() {
        open(); model.revise(ProtocolRevisionType.CORRECTION); model.change(model.input!!.copy(title = "synthetic correction"))
        val expected = model.expected; model.save(); port.writeCallback!!(ProtocolResult.Rejected(ProtocolFailure.STALE))
        assertEquals("synthetic correction", model.input!!.title); assertSame(expected, model.expected)
        model.save(); assertEquals(1, port.writes); assertEquals(ActionStatus.COMPLETED, port.capturedStatus)
        model.currentVersion(); assertNull(model.input); assertTrue(model.completedFollowUp)
    }
    @Test fun deletionPendingAndAnotherCompletedActionCannotReuseMode() {
        open(); model.revise(ProtocolRevisionType.CORRECTION); action = action.copy(status = ActionStatus.DELETION_PENDING)
        model.save(); model.refresh(); assertFalse(model.writable); assertEquals(0, port.writes)
        action = action.copy(status = ActionStatus.COMPLETED, id = "b"); model.save(); model.create()
        assertFalse(model.writable); assertEquals(0, port.writes)
    }
    @Test fun lockClearAndReauthDoNotRestoreFollowUpOrLateResults() {
        model.openCompletedFollowUp(); val timeline = port.timelineCallback!!
        model.open("e"); val read = port.detailCallback!!; read(ProtocolResult.Value(detail()))
        model.toggleHistory(); model.changeFilter(ProtocolFilter.KEY_EVENTS); model.revise(ProtocolRevisionType.CORRECTION)
        model.toggleFields(); model.addIdentifier(); model.change(model.input!!.copy(officialIdentifiers = model.input!!.officialIdentifiers.map { it.copy(value = "synthetic-id") })); model.save(); val write = port.writeCallback!!
        session = SessionSnapshot(SessionState.LOCKED, 2); model.clear()
        timeline(ProtocolResult.Value(emptyList())); read(ProtocolResult.Value(detail())); write(ProtocolResult.Value(ProtocolSaved("e", "r2", 2)))
        session = SessionSnapshot(SessionState.READY, 3)
        assertEquals(ProtocolUiMode.ACTIVE, model.mode); assertFalse(model.completedFollowUp)
        assertNull(model.timeline); assertNull(model.detail); assertNull(model.input); assertNull(model.expected)
        assertFalse(model.moreFields); assertFalse(model.history); assertFalse(model.confirmingMistaken); assertFalse(model.busy)
        assertEquals(ProtocolFilter.ALL, model.filter); assertNull(message)
    }
    @Test fun generationAndActionChangesRejectLateReadAndWriteCallbacks() {
        model.openCompletedFollowUp(); val timeline = port.timelineCallback!!
        session = SessionSnapshot(SessionState.READY, 2); timeline(ProtocolResult.Value(emptyList())); assertNull(model.timeline)
        model.clear(); open(); model.revise(ProtocolRevisionType.ADDENDUM); model.save()
        action = action.copy(id = "b"); port.writeCallback!!(ProtocolResult.Value(ProtocolSaved("e", "r2", 2)))
        assertNull(message); assertEquals(1, port.detailReads)
    }
    @Test fun phaseLabelsUseFlagsNotTimesOrCurrentStatusAndHistoryKeepsOwnFlag() {
        val d = detail().let { it.copy(revisions = it.revisions + it.current.copy(revision = it.current.revision.copy(id = "r2", revisionNumber = 2, recordedAfterCompletion = true))) }
        assertNull(protocolEntryPhase(d.entry.createdAfterCompletion))
        assertNull(protocolRevisionPhase(d.revisions.first().revision.recordedAfterCompletion))
        assertEquals("Diese Fassung wurde nach Abschluss erfasst.", protocolRevisionPhase(d.current.revision.recordedAfterCompletion))
        val completed = detail(created = true, recorded = true)
        assertEquals("Nach Abschluss erfasst", protocolEntryPhase(completed.entry.createdAfterCompletion))
        assertEquals("Nach Abschluss erfasst", protocolEntryPhase(completed.entry.copy().createdAfterCompletion))
    }
    @Test fun timelinePhaseDistinguishesEntryAndRevisionWithOriginalDuringActive() {
        val item = ProtocolTimelineItem("e", "r", 2, ProtocolRevisionType.CORRECTION, ProtocolClassification.KEY_EVENT, null, "synthetic", 1000, true, 500, true, false, false, 2000)
        assertEquals("Aktuelle Fassung nach Abschluss erfasst", protocolTimelinePhase(item))
        assertEquals("Nach Abschluss erfasst", protocolTimelinePhase(item.copy(createdAfterCompletion = true)))
        assertNull(protocolTimelinePhase(item.copy(recordedAfterCompletion = false)))
    }
    @Test fun completedTemplateCancelNeverWritesAndTimestampIsNotUpdated() {
        model.openCompletedFollowUp(); model.create("POLICE_ARRIVED"); val draft = model.input!!
        model.toggleFields(); model.change(draft.copy(description = "synthetic")); assertEquals(1000, model.input!!.occurredAt)
        model.back(); assertNull(model.input); assertEquals(0, port.writes); assertTrue(model.completedFollowUp)
    }
    @Test fun switchingBackToActiveDoesNotReuseCompletedDraftOrExpectedStatus() {
        model.openCompletedFollowUp(); model.create("POLICE_ARRIVED"); action = action.copy(status = ActionStatus.ACTIVE)
        model.save(); assertFalse(model.writable); assertEquals(0, port.writes)
        model.clear(); model.create("POLICE_ARRIVED"); model.save(); assertEquals(ActionStatus.ACTIVE, port.capturedStatus)
    }
    @Test fun persistedInvalidStateClosesFollowUpAndClearsDraftWhileStaleRemainsEditable() {
        open(); model.revise(ProtocolRevisionType.CORRECTION); model.save()
        port.writeCallback!!(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE))
        assertFalse(model.completedFollowUp); assertNull(model.input); assertNull(model.expected); assertNull(model.detail)
        assertEquals(ProtocolFailure.INVALID_STATE, model.error)
    }

    @Test fun synchronousDetailRevokeAfterSaveStopsAllFurtherPublicationAndReads() {
        for (status in listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED)) {
            session = SessionSnapshot(SessionState.READY, session.generation + 1); action = action.copy(status = status)
            val fake = ProtocolFake(); var readsAfter = 0; var feedbackAfter = 0
            lateinit var own: ProtocolModel
            val adapter = object : ProtocolRepository by fake {
                override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) {
                    session = SessionSnapshot(SessionState.LOCKED, session.generation + 1); own.clear()
                    publish(ProtocolResult.Value(detail()))
                }
            }
            own = ProtocolModel(adapter, { session }, { action }, {}, { readsAfter++ }, { feedbackAfter++ }, { 1000 })
            if (status == ActionStatus.COMPLETED) own.openCompletedFollowUp()
            val timelineReads = fake.timelineReads
            own.create("POLICE_ARRIVED"); own.save(); fake.writeCallback!!(ProtocolResult.Value(ProtocolSaved("e", "r", 1)))
            assertEquals(timelineReads, fake.timelineReads); assertEquals(0, readsAfter); assertEquals(0, feedbackAfter)
            assertNull(own.detail); assertNull(own.input); assertFalse(own.completedFollowUp)
        }
    }

}
