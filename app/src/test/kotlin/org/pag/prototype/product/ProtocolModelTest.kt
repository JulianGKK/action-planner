package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.time.LocalDateTime
import java.time.ZoneId

internal class ProtocolFake : ProtocolRepository {
    var summaryReads = 0; var timelineReads = 0; var detailReads = 0; var writes = 0
    var timelineCallback: ((ProtocolResult<List<ProtocolTimelineItem>>) -> Unit)? = null
    var detailCallback: ((ProtocolResult<ProtocolEntryDetail>) -> Unit)? = null
    var writeCallback: ((ProtocolResult<ProtocolSaved>) -> Unit)? = null
    var captured: ProtocolInput? = null; var capturedExpected: ProtocolExpectedRevision? = null
    var capturedType: ProtocolRevisionType? = null; var capturedClear = false
    var capturedStatus: ActionStatus? = null
    override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) { summaryReads++; publish(ProtocolResult.Value(ProtocolSummary(0, null))) }
    override fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit) { timelineReads++; timelineCallback = publish }
    override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) { detailReads++; detailCallback = publish }
    override fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { writes++; captured = input; capturedStatus = expectedActionStatus; writeCallback = publish }
    override fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { writes++; captured = input; capturedExpected = expectedRevision; capturedType = revisionType; capturedClear = clearMistaken; capturedStatus = expectedActionStatus; writeCallback = publish }
    override fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision, publish: (ProtocolResult<ProtocolSaved>) -> Unit) { writes++; capturedStatus = expectedActionStatus; capturedExpected = expectedRevision; capturedType = ProtocolRevisionType.MARKED_MISTAKEN; writeCallback = publish }
}
class ProtocolModelTest {
    private var session = SessionSnapshot(SessionState.READY, 1)
    private var action = Action("a", "synthetic", "", 0, 1, 0, ActionStatus.ACTIVE)
    private var interactions = 0; private var refreshes = 0; private var feedbacks = 0; private var clock = 1000L
    private var onInteraction: () -> Unit = {}
    private val port = ProtocolFake()
    private val model = ProtocolModel(port, { session }, { action }, { interactions++; onInteraction() }, { refreshes++ }, { feedbacks++ }, { clock })
    private val input = ProtocolInput(ProtocolClassification.KEY_EVENT, "synthetic", 1000, affectedPersonIds = setOf("p"), officialIdentifiers = listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, "synthetic-id")))
    private fun detail(mistaken: Boolean = false): ProtocolEntryDetail {
        val revision = ProtocolRevision("r", "a", "e", 1, ProtocolRevisionType.ORIGINAL, input, 2000, false, mistaken)
        return ProtocolEntryDetail(ProtocolEntry("e", "a", false), listOf(ProtocolRevisionDetail(revision, listOf(ProtocolPersonReference("p", "Motte")), null)))
    }
    private fun open(mistaken: Boolean = false) { model.open("e"); port.detailCallback!!(ProtocolResult.Value(detail(mistaken))) }
    private fun saved() = port.writeCallback!!(ProtocolResult.Value(ProtocolSaved("e", "r", 1)))
    private fun row(id: String, c: ProtocolClassification) = ProtocolTimelineItem(id, "r", 1, ProtocolRevisionType.ORIGINAL, c, null, id, 1000, false, 2000, false, false, false, 2000)
    @Test fun templateOnlyCreatesDraftAndTimeIsCapturedOnce() {
        model.create("POLICE_ARRIVED"); clock = 9000
        assertEquals(1000, model.input!!.occurredAt); assertEquals("Polizei eingetroffen", model.input!!.title)
        assertEquals(0, port.writes); assertEquals(0, port.detailReads)
        model.change(model.input!!.copy(description = "synthetic")); assertEquals(1000, model.input!!.occurredAt)
    }
    @Test fun freeAndCustomHaveCorrectClassificationAndEmptyTitles() {
        model.create(); assertEquals(ProtocolClassification.RELEVANT, model.input!!.classification); assertNull(model.input!!.templateKey)
        model.back(); model.create("CUSTOM_KEY_EVENT"); assertEquals("", model.input!!.title); assertEquals(ProtocolClassification.KEY_EVENT, model.input!!.classification)
    }
    @Test fun allTemplatesHaveOnePlaceAndOnlySixAreQuick() {
        assertEquals(6, ProtocolRules.templates.count { it.key in protocolQuickKeys })
        assertEquals(12, ProtocolRules.templates.count { it.key !in protocolQuickKeys && it.key != "CUSTOM_KEY_EVENT" })
        model.toggleTemplates(); assertTrue(model.moreTemplates)
    }
    @Test fun filterIsLocalPreservesRepositoryOrderAndNeverPrefetchesDetails() {
        val rows = listOf(row("z", ProtocolClassification.RELEVANT), row("a", ProtocolClassification.KEY_EVENT))
        model.refresh(); port.timelineCallback!!(ProtocolResult.Value(rows))
        assertEquals(rows, model.filtered); model.changeFilter(ProtocolFilter.KEY_EVENTS)
        assertEquals(listOf(rows[1]), model.filtered); model.changeFilter(ProtocolFilter.ALL)
        assertEquals(1, port.timelineReads); assertEquals(0, port.detailReads)
    }
    @Test fun initialLoadIsNotEmptyAndRefreshRetainsContent() {
        model.refresh(); assertNull(model.timeline); assertTrue(model.loading)
        port.timelineCallback!!(ProtocolResult.Value(listOf(row("e", ProtocolClassification.KEY_EVENT))))
        val old = model.timeline; model.refresh(); assertSame(old, model.timeline)
        port.timelineCallback!!(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND)); assertSame(old, model.timeline)
    }
    @Test fun createSuccessReadsFullDetailRefreshesTimelineAndWorkspaceWithoutInteraction() {
        model.create("POLICE_ARRIVED"); model.save(); assertTrue(model.busy); assertEquals(ActionStatus.ACTIVE, port.capturedStatus)
        val count = interactions; saved(); assertFalse(model.form); assertTrue(model.detailLoading)
        assertEquals(1, port.detailReads); assertEquals(1, port.timelineReads); assertEquals(1, refreshes); assertEquals(1, feedbacks); assertEquals(count, interactions)
        port.detailCallback!!(ProtocolResult.Value(detail())); assertEquals("e", model.detail!!.entry.id)
    }
    @Test fun cancelCreateNeverWritesAndDiscardsAllDraftFields() {
        model.create(); model.toggleFields(); model.addIdentifier(); model.back()
        assertFalse(model.form); assertFalse(model.moreFields); assertNull(model.expected); assertEquals(0, port.writes)
    }
    @Test fun reportedSourceAndAffectedPeopleAreIndependentAndSourceSwitchClearsReference() {
        model.create(); model.change(model.input!!.copy(sourceType = ProtocolSourceType.REPORTED, reportedByPersonId = "p", affectedPersonIds = setOf("p", "p")))
        assertEquals(setOf("p"), model.input!!.affectedPersonIds)
        model.change(model.input!!.copy(sourceType = ProtocolSourceType.OWN_OBSERVATION))
        assertNull(model.input!!.reportedByPersonId); assertEquals(setOf("p"), model.input!!.affectedPersonIds)
    }
    @Test fun identifierLimitRemovalAndValidation() {
        model.create(); repeat(33) { model.addIdentifier() }; assertEquals(32, model.input!!.officialIdentifiers.size)
        model.save(); assertEquals(0, port.writes); assertNotNull(model.validation)
        model.removeIdentifier(0); assertEquals(31, model.input!!.officialIdentifiers.size)
    }
    @Test fun announcementMustHaveBothKindAndTextAndInvalidDraftRemains() {
        model.create("POLICE_ARRIVED"); model.change(model.input!!.copy(announcementKind = ProtocolAnnouncementKind.WORDING)); model.save()
        assertEquals(0, port.writes); assertNotNull(model.input); assertNotNull(model.validation)
        model.change(model.input!!.copy(announcementText = "synthetic announcement")); model.save(); assertEquals(1, port.writes)
    }
    @Test fun correctionFreezesFullSnapshotAllowsClassificationButPreservesTemplate() {
        open(); model.revise(ProtocolRevisionType.CORRECTION); val expected = model.expected
        model.change(model.input!!.copy(classification = ProtocolClassification.RELEVANT, templateKey = "SEARCH")); model.save()
        assertEquals(ProtocolExpectedRevision(detail().entry, detail().current.revision), port.capturedExpected)
        assertSame(expected, model.expected); assertEquals(ProtocolClassification.RELEVANT, port.captured!!.classification); assertNull(port.captured!!.templateKey)
    }
    @Test fun mistakenCorrectionRequiresExplicitClearAddendumCannotClearOrChangeClassification() {
        open(true); model.revise(ProtocolRevisionType.CORRECTION); assertFalse(model.clearMistaken); model.toggleClearMistaken(); model.save(); assertTrue(port.capturedClear)
        port.writeCallback!!(ProtocolResult.Rejected(ProtocolFailure.STALE)); model.currentVersion(); port.detailCallback!!(ProtocolResult.Value(detail(true))); model.revise(ProtocolRevisionType.ADDENDUM)
        model.toggleClearMistaken(); model.change(model.input!!.copy(classification = ProtocolClassification.RELEVANT)); model.save()
        assertFalse(port.capturedClear); assertEquals(ProtocolClassification.KEY_EVENT, port.captured!!.classification)
    }
    @Test fun markNeedsConfirmationCancelDoesNotWriteAlreadyMistakenCannotMarkAgain() {
        open(); model.markMistaken(); assertEquals(0, port.writes); model.requestMistaken(); model.cancelMistaken(); model.markMistaken(); assertEquals(0, port.writes)
        model.requestMistaken(); model.markMistaken(); assertEquals(1, port.writes)
        port.writeCallback!!(ProtocolResult.Rejected(ProtocolFailure.STALE)); model.open("e"); port.detailCallback!!(ProtocolResult.Value(detail(true)))
        model.requestMistaken(); assertFalse(model.confirmingMistaken)
    }
    @Test fun stalePreservesDraftAndExpectedWithoutAutomaticRetry() {
        open(); model.revise(ProtocolRevisionType.CORRECTION); model.change(model.input!!.copy(title = "synthetic correction")); model.save()
        port.writeCallback!!(ProtocolResult.Rejected(ProtocolFailure.STALE)); assertTrue(model.form); assertEquals("synthetic correction", model.input!!.title)
        assertNotNull(model.expected); assertEquals(1, port.writes); assertFalse(model.busy)
        model.save(); assertEquals(1, port.writes)
        model.currentVersion(); assertFalse(model.form); assertEquals(2, port.detailReads)
    }
    @Test fun lockCleanupRejectsLateTimelineDetailAndSaveAndRemovesAllDisclosures() {
        model.refresh(); val timeline = port.timelineCallback!!; open(); val detail = port.detailCallback!!
        model.toggleHistory(); model.changeFilter(ProtocolFilter.KEY_EVENTS); model.revise(ProtocolRevisionType.ADDENDUM); model.toggleFields(); model.save(); val write = port.writeCallback!!
        session = SessionSnapshot(SessionState.LOCKED, 2); model.clear()
        timeline(ProtocolResult.Value(listOf(row("e", ProtocolClassification.KEY_EVENT)))); detail(ProtocolResult.Value(detail())); write(ProtocolResult.Value(ProtocolSaved("e", "r", 2)))
        assertNull(model.timeline); assertNull(model.detail); assertNull(model.input); assertNull(model.expected); assertFalse(model.busy); assertFalse(model.history); assertFalse(model.moreFields); assertFalse(model.moreTemplates); assertEquals(ProtocolFilter.ALL, model.filter); assertEquals(0, feedbacks)
    }
    @Test fun actionAndGenerationChangesSuppressPublication() {
        model.refresh(); val old = port.timelineCallback!!; action = action.copy(id = "b"); old(ProtocolResult.Value(emptyList())); assertNull(model.timeline)
        action = action.copy(id = "a"); model.open("e"); session = SessionSnapshot(SessionState.READY, 2); port.detailCallback!!(ProtocolResult.Value(detail())); assertNull(model.detail)
    }
    @Test fun synchronouslyRevokingInteractionCannotCreateOrWrite() {
        onInteraction = { session = SessionSnapshot(SessionState.LOCKED, 2); model.clear() }
        model.create("POLICE_ARRIVED"); assertNull(model.input); assertEquals(0, port.writes)
    }
    @Test fun completedNeverOpensActiveProtocolOrSavesDraft() {
        model.create("POLICE_ARRIVED"); action = action.copy(status = ActionStatus.COMPLETED); model.save(); model.open("e"); model.refresh()
        assertEquals(0, port.writes); assertEquals(0, port.detailReads); assertEquals(0, port.timelineReads); assertFalse(model.writable)
    }
    @Test fun dstGapAndOverlapRejectWithoutLosingDraft() {
        model.create("POLICE_ARRIVED")
        val zone = ZoneId.of("Europe/Berlin")
        assertNull(selectedInstant(LocalDateTime.of(2026, 3, 29, 2, 30), zone))
        assertNull(selectedInstant(LocalDateTime.of(2026, 10, 25, 2, 30), zone))
        model.changeTime(null); model.save(); assertEquals(0, port.writes); assertTrue(model.form); assertNotNull(model.validation)
    }
    @Test fun oldRevisionIsReadOnlyAndBackUsesCurrentDetail() {
        model.open("e"); val d = detail(); val newer = d.current.copy(revision = d.current.revision.copy(id = "r2", revisionNumber = 2, revisionType = ProtocolRevisionType.CORRECTION))
        port.detailCallback!!(ProtocolResult.Value(d.copy(revisions = d.revisions + newer)))
        model.oldRevision(d.current); assertEquals(d.current, model.historical); model.revise(ProtocolRevisionType.CORRECTION); assertFalse(model.form); model.back(); assertNull(model.historical); assertEquals(newer, model.detail!!.current)
    }
    @Test fun hubTimestampUsesCalendarDateAndResponsiveLayoutRemains() {
        val zone = ZoneId.of("Europe/Berlin"); val date = java.time.LocalDate.of(2026, 10, 14)
        val time = date.atTime(18, 42).atZone(zone).toInstant().toEpochMilli()
        assertEquals("18:42", protocolSummaryTime(time, zone, date)); assertEquals("14.10.2026 18:42", protocolSummaryTime(time, zone, date.plusDays(1)))
        assertEquals(1, activeHubColumns(360f, 2f)); assertEquals(2, activeHubColumns(800f, 1f))
    }
    @Test fun leavingPendingRefreshDoesNotLeavePermanentLoadingAndOldCallbackCannotReplaceIt() {
        model.refresh(); port.timelineCallback!!(ProtocolResult.Value(listOf(row("old", ProtocolClassification.RELEVANT))))
        model.refresh(); val late = port.timelineCallback!!
        model.create("POLICE_ARRIVED"); model.back()
        late(ProtocolResult.Value(emptyList()))
        assertFalse(model.loading); assertEquals("old", model.timeline!!.single().entryId)
    }
    @Test fun eachNestedContextClearsAllReferencesAndConfirmationOnLock() {
        for (path in listOf("timeline", "detail", "create", "correction", "addendum", "confirmation", "save")) {
            model.clear(); session = SessionSnapshot(SessionState.READY, session.generation + 1)
            when (path) {
                "timeline" -> model.refresh()
                "detail" -> model.open("e")
                "create" -> model.create()
                "correction" -> { open(); model.revise(ProtocolRevisionType.CORRECTION) }
                "addendum" -> { open(); model.revise(ProtocolRevisionType.ADDENDUM) }
                "confirmation" -> { open(); model.requestMistaken() }
                "save" -> { model.create("POLICE_ARRIVED"); model.save() }
            }
            session = SessionSnapshot(SessionState.LOCKED, session.generation + 1); model.clear()
            assertNull(model.entryId); assertNull(model.input); assertNull(model.expected); assertNull(model.detail)
            assertNull(model.timeline); assertNull(model.historical); assertFalse(model.confirmingMistaken); assertFalse(model.busy)
        }
    }

}
