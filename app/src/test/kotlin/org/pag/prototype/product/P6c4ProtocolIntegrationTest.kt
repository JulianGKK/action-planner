package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual ViewModel and navigation/publication gates, deferred existing repository ports. */
class P6c4ProtocolIntegrationTest {
    private class Session : SessionPort {
        var value = SessionSnapshot(SessionState.READY, 1)
        var observer: ((SessionSnapshot) -> Unit)? = null
        var onInteraction: () -> Unit = {}
        var interactions = 0
        override fun snapshot() = value
        override fun observe(observer: (SessionSnapshot) -> Unit): Subscription { this.observer = observer; observer(value); return Subscription {} }
        fun emit(state: SessionState, generation: Long) { value = SessionSnapshot(state, generation); observer!!(value) }
        override fun authenticate(experiment: Experiment) {}
        override fun testAccess() {}
        override fun userInteraction() { interactions++; onInteraction() }
        override fun lock() = emit(SessionState.LOCKED, value.generation + 1)
    }
    private class Actions : ActionRepository {
        var row = Action("a", "synthetic action", "synthetic description", 1, 2, 0, ActionStatus.COMPLETED)
        var transitions = 0; var edits = 0
        override fun listActions(publish: (List<Action>) -> Unit) = publish(listOf(row))
        override fun getAction(id: String, publish: (Action?) -> Unit) = publish(row.copy(id = id))
        override fun createAction(action: NewAction, publish: (Action) -> Unit) { error("No P6 action create") }
        override fun updateAction(expected: Action, changes: NewAction, publish: (ActionChangeResult) -> Unit) { edits++; error("No P6 action edit") }
        override fun advanceActionStatus(id: String, expected: ActionStatus, publish: (ActionChangeResult) -> Unit) {
            transitions++; row = row.copy(status = ActionLifecycle.next(expected)!!); publish(ActionChangeResult.Applied(row))
        }
    }
    private class People : PersonRepository {
        var lists = 0
        override fun listPersons(actionId: String, publish: (PersonResult<List<Person>>) -> Unit) { lists++ }
        override fun getPerson(actionId: String, personId: String, publish: (PersonResult<Person?>) -> Unit) = publish(PersonResult.Value(Person(personId, actionId, "synthetic", 0)))
        override fun createPerson(actionId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P6 person writes") }
        override fun updatePerson(actionId: String, personId: String, pseudonym: String, publish: (PersonResult<Person>) -> Unit) { error("No P6 person writes") }
    }
    private class Ops : OperationsRepository {
        val loads = mutableListOf<(OperationsResult<OperationalBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<OperationalBoard>) -> Unit) { loads.add(publish) }
        override fun createRole(actionId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun renameRole(actionId: String, roleId: String, name: String, publish: (OperationsResult<Role>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun deleteRole(actionId: String, roleId: String, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun planRoles(actionId: String, personId: String, plan: PersonRolePlan, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun updateSnapshot(actionId: String, personId: String, expected: PersonOperationalState, status: OperationalStatus, roleId: String?, place: String, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
    }
    private class Tasks : TaskRepository {
        val loads = mutableListOf<(OperationsResult<TaskBoard>) -> Unit>()
        override fun board(actionId: String, publish: (OperationsResult<TaskBoard>) -> Unit) { loads.add(publish) }
        override fun create(actionId: String, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun edit(actionId: String, expected: Task, input: TaskInput, publish: (OperationsResult<Task>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun transition(actionId: String, expected: Task, status: TaskStatus, publish: (OperationsResult<Task>) -> Unit) { error("No completed P4/P5a mutation") }
        override fun delete(actionId: String, expected: Task, publish: (OperationsResult<Unit>) -> Unit) { error("No completed P4/P5a mutation") }
    }
    private class Protection : LeadershipRepository {
        val states = mutableListOf<Pair<String, (SensitiveResult<SensitiveState>) -> Unit>>()
        var inventories = 0; var setups = 0; var opens = 0; var revokes = 0
        var setupResult: ((SensitiveResult<Unit>) -> Unit)? = null
        var onRevoke: (() -> Unit)? = null
        override fun actionState(actionId: String, publish: (SensitiveResult<SensitiveState>) -> Unit) { states.add(actionId to publish) }
        override fun inventory(actionId: String, personId: String, publish: (SensitiveResult<SensitiveInventory>) -> Unit) { inventories++; publish(SensitiveResult.Value(SensitiveInventory(SensitiveState.READY))) }
        override fun setup(actionId: String, passphrase: CharArray, confirmation: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { setups++; passphrase.fill('\u0000'); confirmation.fill('\u0000'); setupResult = publish }
        override fun open(actionId: String, personId: String, area: SensitiveArea, passphrase: CharArray, publish: (SensitiveResult<SensitiveOpened>) -> Unit) { opens++; passphrase.fill('\u0000') }
        override fun save(grant: SensitiveGrant, data: SensitiveData, publish: (SensitiveResult<Unit>) -> Unit) { error("No P6 P3b save") }
        override fun deleteArea(grant: SensitiveGrant, publish: (SensitiveResult<Unit>) -> Unit) { error("No P6 P3b delete") }
        override fun deletePerson(actionId: String, personId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P6 person delete") }
        override fun deleteAction(actionId: String, passphrase: CharArray, publish: (SensitiveResult<Unit>) -> Unit) { error("No P6 action delete") }
        override fun revoke() { revokes++; val callback = onRevoke; onRevoke = null; callback?.invoke() }
    }
    private val session = Session(); private val actions = Actions(); private val people = People()
    private val ops = Ops(); private val tasks = Tasks(); private val protection = Protection()
    private val protocolDelegate = ProtocolFake()
    private var summary = ProtocolSummary(2, 1000)
    private val protocolPort = object : ProtocolRepository by protocolDelegate {
        override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) { protocolDelegate.summaryReads++; publish(ProtocolResult.Value(summary)) }
    }
    private val model = ProductViewModel(session, actions, ProductStorageState.READY_TO_OPEN, people, protection, ops, tasks, protocolPort) {}
    private val person = OperationalPerson(Person("p", "a", "synthetic", 0), PersonRolePlan(setOf("r"), "r"), null)
    private val board = OperationalBoard("a", ActionStatus.ACTIVE, listOf(Role("r", "a", "Support", 0)), listOf(person))
    private val task = Task("t", "a", "synthetic task", TaskPriority.HIGH, TaskStatus.OPEN, null, "r", null, null, null, 0, 0, null)
    private val taskBoard = TaskBoard("a", listOf(task), emptyList())
    private fun open() = model.detail("a")
    private fun complete() {
        ops.loads.last()(OperationsResult.Value(board.copy(status = actions.row.status)))
        if (model.activeArea != ActiveArea.FUNCTIONS) tasks.loads.last()(OperationsResult.Value(taskBoard))
    }

    private fun start(phase: ActionStatus = ActionStatus.ACTIVE) {
        actions.row = actions.row.copy(status = phase); open(); complete()
        if (phase == ActionStatus.ACTIVE) { model.activeArea(ActiveArea.PROTOCOL); complete() }
        else model.openCompletedProtocol()
        protocolDelegate.timelineCallback!!(ProtocolResult.Value(listOf(item())))
    }
    private fun item() = ProtocolTimelineItem("e", "r24", 24, ProtocolRevisionType.CORRECTION,
        ProtocolClassification.KEY_EVENT, null, "synthetic title", 1, true, 24, true, false, false, 1)
    private fun entry(): ProtocolEntryDetail {
        val rows = (1..24).map { n ->
            val i = ProtocolInput(ProtocolClassification.KEY_EVENT, "synthetic title", n.toLong(),
                description = "DETAIL-SENTINEL-DESCRIPTION", affectedPersonIds = setOf("p"),
                officialIdentifiers = listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, "DETAIL-SENTINEL-IDENTIFIER-$n")))
            ProtocolRevisionDetail(ProtocolRevision("r$n", "a", "e", n.toLong(),
                if (n == 1) ProtocolRevisionType.ORIGINAL else ProtocolRevisionType.CORRECTION,
                i, n.toLong(), n > 12, false), listOf(ProtocolPersonReference("p", "DETAIL-SENTINEL-PSEUDONYM")), null)
        }
        return ProtocolEntryDetail(ProtocolEntry("e", "a", false), rows)
    }
    private fun nested(context: String) {
        when (context) {
            "timeline" -> { model.protocol.changeFilter(ProtocolFilter.KEY_EVENTS); model.protocol.toggleTemplates() }
            "create" -> { model.protocol.create("POLICE_ARRIVED"); model.protocol.toggleFields(); model.protocol.addIdentifier() }
            else -> {
                model.protocol.open("e"); protocolDelegate.detailCallback!!(ProtocolResult.Value(entry()))
                when (context) {
                    "correction" -> model.protocol.revise(ProtocolRevisionType.CORRECTION)
                    "addendum" -> model.protocol.revise(ProtocolRevisionType.ADDENDUM)
                    "history" -> { model.protocol.toggleHistory(); model.protocol.oldRevision(model.protocol.detail!!.revisions.first()) }
                    "confirmation" -> model.protocol.requestMistaken()
                }
            }
        }
    }
    private fun empty() {
        val p = model.protocol
        assertNull(p.timeline); assertNull(p.detail); assertNull(p.historical); assertNull(p.input); assertNull(p.expected)
        assertNull(p.entryId); assertNull(p.error); assertNull(p.validation)
        assertFalse(p.loading); assertFalse(p.detailLoading); assertFalse(p.busy)
        assertFalse(p.moreFields); assertFalse(p.moreTemplates); assertFalse(p.history); assertFalse(p.confirmingMistaken)
        assertFalse(p.completedFollowUp); assertEquals(ProtocolFilter.ALL, p.filter)
    }
    private val contexts = listOf("timeline", "detail", "create", "correction", "addendum", "history", "confirmation")
    @Test fun activeLifecycleClearsEveryNestedContextAndRejectsLatePublication() {
        contexts.forEach { c ->
            start(); nested(c); val lateTimeline = protocolDelegate.timelineCallback!!; val lateDetail = protocolDelegate.detailCallback
            val writes = protocolDelegate.writes
            model.requestAdvance(); model.confirmAdvance(); complete()
            assertEquals(ActionStatus.COMPLETED, model.selected!!.status); empty()
            model.protocol.save(); assertEquals(writes, protocolDelegate.writes)
            lateTimeline(ProtocolResult.Value(listOf(item()))); lateDetail?.invoke(ProtocolResult.Value(entry())); empty()
            model.openCompletedProtocol(); assertTrue(model.completedProtocolOpen); assertNull(model.protocol.expected)
            model.closeCompletedProtocol(); empty()
        }
    }
    @Test fun lockMatrixUsesRealViewModelCleanupAndNeverRestoresOnReauth() {
        listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED).forEach { phase -> contexts.forEach { c ->
            start(phase); nested(c); val late = protocolDelegate.timelineCallback!!; val detail = protocolDelegate.detailCallback
            session.lock(); assertEquals(SessionState.LOCKED, model.snapshot.state); empty()
            late(ProtocolResult.Value(listOf(item()))); detail?.invoke(ProtocolResult.Value(entry())); empty()
            session.emit(SessionState.READY, session.value.generation + 1); assertNull(model.selected); empty()
        } }
    }
    @Test fun crossAreaMatrixDiscardsSensitiveContextsAndOtherEditors() {
        listOf(ActiveArea.PERSONS, ActiveArea.TASKS, ActiveArea.FUNCTIONS, ActiveArea.DETAILS, ActiveArea.HUB).forEach { area ->
            contexts.forEach { c -> start(); nested(c); val late = protocolDelegate.timelineCallback!!
                model.activeArea(area); empty(); assertEquals(area, model.activeArea)
                late(ProtocolResult.Value(listOf(item()))); empty()
                assertNull(model.tasks.selected); assertFalse(model.tasks.form); assertNull(model.selectedPerson)
                assertEquals(OperationsScreen.OVERVIEW, model.operations.screen)
            }
        }
        assertEquals(0, actions.edits); assertEquals(0, protocolDelegate.writes); assertEquals(0, protection.opens)
    }
    @Test fun deletionPendingRemovesCompletedContextAndRejectsLateDraftOrReads() {
        contexts.forEach { c -> start(ActionStatus.COMPLETED); nested(c)
            val late = protocolDelegate.timelineCallback!!; val detail = protocolDelegate.detailCallback; val writes = protocolDelegate.writes
            actions.row = actions.row.copy(status = ActionStatus.DELETION_PENDING); model.detail("a")
            empty(); assertFalse(model.protocol.writable); model.protocol.save(); model.openCompletedProtocol()
            late(ProtocolResult.Value(listOf(item()))); detail?.invoke(ProtocolResult.Value(entry())); empty()
            assertEquals(writes, protocolDelegate.writes)
        }
    }
    @Test fun staleCrossLayerPreservesDraftThenRequiresExplicitCurrentRead() {
        listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED).forEach { phase ->
            start(phase); nested("correction"); model.protocol.change(model.protocol.input!!.copy(title = "synthetic changed"))
            val input = model.protocol.input; val expected = model.protocol.expected
            model.protocol.save(); val writes = protocolDelegate.writes
            protocolDelegate.writeCallback!!(ProtocolResult.Rejected(ProtocolFailure.STALE))
            assertSame(input, model.protocol.input); assertSame(expected, model.protocol.expected)
            model.protocol.save(); assertEquals(writes, protocolDelegate.writes)
            val reads = protocolDelegate.detailReads; model.protocol.currentVersion()
            assertNull(model.protocol.input); assertNull(model.protocol.expected); assertEquals(reads + 1, protocolDelegate.detailReads)
            protocolDelegate.detailCallback!!(ProtocolResult.Value(entry())); model.protocol.revise(ProtocolRevisionType.CORRECTION)
            assertEquals(entry().current.revision, model.protocol.expected!!.revision)
        }
    }
    @Test fun callbacksTimersAndRefreshNeverReportUserInteraction() {
        start(); val count = session.interactions
        protocolDelegate.timelineCallback!!(ProtocolResult.Value(listOf(item()))); model.refreshCountdown(); model.loadWorkspace(); complete()
        assertEquals(count, session.interactions)
        model.protocol.open("e"); val tapped = session.interactions
        protocolDelegate.detailCallback!!(ProtocolResult.Value(entry())); assertEquals(tapped, session.interactions)
        model.protocol.revise(ProtocolRevisionType.CORRECTION); model.protocol.change(model.protocol.input!!.copy(title = "changed"))
        assertTrue(session.interactions > tapped)
    }
    @Test fun delayedTimelineDetailAndRefreshDistinguishEmptyErrorAndValidOldContent() {
        listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED).forEach { phase ->
            start(phase); val old = model.protocol.timeline; model.protocol.refresh(); assertSame(old, model.protocol.timeline)
            protocolDelegate.timelineCallback!!(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND)); assertSame(old, model.protocol.timeline)
            model.protocol.open("e"); assertTrue(model.protocol.detailLoading); assertNull(model.protocol.detail)
            protocolDelegate.detailCallback!!(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND)); assertNull(model.protocol.detail)
            model.protocol.currentVersion(); assertTrue(model.protocol.detailLoading)
            protocolDelegate.detailCallback!!(ProtocolResult.Value(entry())); assertNotNull(model.protocol.detail)
            model.protocol.back(); model.protocol.refresh(); protocolDelegate.timelineCallback!!(ProtocolResult.Value(emptyList()))
            assertTrue(model.protocol.timeline!!.isEmpty()); assertNull(model.protocol.error)
        }
    }
    @Test fun largeTimelineKeepsPortOrderAndHistoryReferencesRemainRevisionLocal() {
        start(); val rows = (1..60).map { item().copy(entryId = "e$it", title = "Event $it", classification = if (it % 2 == 0) ProtocolClassification.KEY_EVENT else ProtocolClassification.RELEVANT) }.reversed()
        val reads = protocolDelegate.detailReads
        protocolDelegate.timelineCallback!!(ProtocolResult.Value(rows)); assertEquals(rows, model.protocol.filtered)
        model.protocol.changeFilter(ProtocolFilter.KEY_EVENTS); assertEquals(rows.filter { it.classification == ProtocolClassification.KEY_EVENT }, model.protocol.filtered)
        assertEquals(reads, protocolDelegate.detailReads)
        nested("history"); assertEquals("DETAIL-SENTINEL-IDENTIFIER-1", model.protocol.historical!!.revision.input.officialIdentifiers.single().value)
        val writes = protocolDelegate.writes; model.protocol.revise(ProtocolRevisionType.CORRECTION); assertFalse(model.protocol.form); assertEquals(writes, protocolDelegate.writes)
    }
    @Test fun successfulActiveWriteUsesOnlyNewRepositorySummaryAndPublishesAtomicReplacement() {
        start(); assertEquals(ProtocolSummary(2, 1000), model.workspace!!.protocolSummary)
        val before = model.workspace
        model.protocol.create("POLICE_ARRIVED"); model.protocol.save()
        summary = ProtocolSummary(9, 9999)
        val interactions = session.interactions
        protocolDelegate.writeCallback!!(ProtocolResult.Value(ProtocolSaved("e", "r24", 24)))
        assertSame(before, model.workspace)
        ops.loads.last()(OperationsResult.Value(board)); assertSame(before, model.workspace)
        tasks.loads.last()(OperationsResult.Value(taskBoard)); assertEquals(summary, model.workspace!!.protocolSummary)
        assertEquals(interactions, session.interactions)
        val card = activeHubCards(actions.row, board, taskBoard, model.workspace!!.protocolSummary).single { it.area == ActiveArea.PROTOCOL }
        assertEquals("9 Schlüsselereignisse", card.summary.first()); assertFalse(card.toString().contains("DETAIL-SENTINEL"))
    }
    @Test fun maximumUnicodeInputAndManyPeopleStayCompleteThroughSaveAndInvalidRetry() {
        fun text(n: Int) = "🐦".repeat(n)
        listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED).forEach { phase ->
            start(phase); model.protocol.create()
            val i = model.protocol.input!!.copy(title = text(120), description = text(2000), placeLabel = text(80),
                announcementText = text(2000), announcementKind = ProtocolAnnouncementKind.WORDING,
                statedLegalBasis = text(1000), sourceType = ProtocolSourceType.REPORTED, reportedByPersonId = "p1",
                affectedPersonIds = (1..32).map { "p$it" }.toSet(),
                officialIdentifiers = List(32) { ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.DISPLAYED_NAME, text(120)) })
            model.protocol.change(i.copy(description = text(2001))); val writes = protocolDelegate.writes
            model.protocol.save(); assertEquals(writes, protocolDelegate.writes); assertNotNull(model.protocol.validation)
            assertEquals(32, model.protocol.input!!.affectedPersonIds.size)
            model.protocol.change(i); model.protocol.save(); assertEquals(i, protocolDelegate.captured)
            assertEquals(phase, protocolDelegate.capturedStatus); assertEquals(32, protocolDelegate.captured!!.officialIdentifiers.size)
            model.protocol.clear()
        }
    }
    @Test fun dstGapOverlapAreRejectedInBothModesAndValidRetryRetainsApproximate() {
        val oldZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
            listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED).forEach { phase ->
                start(phase); model.protocol.create("POLICE_ARRIVED")
                model.protocol.change(model.protocol.input!!.copy(occurredAtApproximate = true))
                val writes = protocolDelegate.writes
                listOf(java.time.LocalDateTime.of(2026, 3, 29, 2, 30), java.time.LocalDateTime.of(2026, 10, 25, 2, 30)).forEach { t ->
                    model.protocol.changeTime(t); model.protocol.save(); assertEquals(writes, protocolDelegate.writes)
                    assertNotNull(model.protocol.validation); assertTrue(model.protocol.input!!.occurredAtApproximate)
                }
                val valid = java.time.LocalDateTime.of(2026, 10, 25, 3, 30)
                model.protocol.changeTime(valid); model.protocol.save()
                assertEquals(selectedInstant(valid, java.time.ZoneId.of("Europe/Berlin")), protocolDelegate.captured!!.occurredAt)
                model.protocol.clear()
            }
        } finally { java.util.TimeZone.setDefault(oldZone) }
    }
    @Test fun responsiveThresholdAndFiveCardOrderRemainDeterministic() {
        val expected = listOf(ActiveArea.PERSONS, ActiveArea.TASKS, ActiveArea.FUNCTIONS, ActiveArea.PROTOCOL, ActiveArea.DETAILS)
        assertEquals(expected, activeHubCards(actions.row.copy(status = ActionStatus.ACTIVE), board, taskBoard, summary).map { it.area })
        assertEquals(1, activeHubColumns(599f, 1f)); assertEquals(2, activeHubColumns(600f, 1f))
        assertEquals(2, activeHubColumns(600f, 1.3f)); assertEquals(1, activeHubColumns(600f, 2f))
        assertEquals(1, activeHubColumns(320f, 1f))
    }
    @Test fun initialTimelineLoadingErrorEmptyAndRetryUseCompleteResultsInBothPhases() {
        listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED).forEach { phase ->
            actions.row = actions.row.copy(status = phase); open(); complete()
            if (phase == ActionStatus.ACTIVE) { model.activeArea(ActiveArea.PROTOCOL); complete() }
            else model.openCompletedProtocol()
            assertTrue(model.protocol.loading); assertNull(model.protocol.timeline); assertNull(model.protocol.error)
            val interactions = session.interactions
            protocolDelegate.timelineCallback!!(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND))
            assertFalse(model.protocol.loading); assertNull(model.protocol.timeline)
            assertEquals(ProtocolFailure.NOT_FOUND, model.protocol.error); assertEquals(interactions, session.interactions)
            model.protocol.refreshFromInteraction(); assertTrue(model.protocol.loading)
            protocolDelegate.timelineCallback!!(ProtocolResult.Value(emptyList()))
            assertFalse(model.protocol.loading); assertNotNull(model.protocol.timeline); assertTrue(model.protocol.timeline!!.isEmpty())
            assertNull(model.protocol.error)
            model.protocol.refreshFromInteraction(); val empty = model.protocol.timeline
            protocolDelegate.timelineCallback!!(ProtocolResult.Value(listOf(item())))
            assertEquals(listOf(item()), model.protocol.timeline); assertNotSame(empty, model.protocol.timeline)
        }
    }
    @Test fun lateWriteResultsNeverPublishDetailRefreshOrFeedbackAfterContextExit() {
        val outcomes = listOf(ProtocolResult.Value(ProtocolSaved("e", "r25", 25)),
            ProtocolResult.Rejected(ProtocolFailure.STALE))
        listOf(ActionStatus.ACTIVE, ActionStatus.COMPLETED).forEach { phase ->
            listOf("lock", "other-action", "phase-exit").forEach { exit -> outcomes.forEach { result ->
                start(phase); nested("correction"); model.protocol.save()
                assertTrue(model.protocol.busy)
                val callback = protocolDelegate.writeCallback!!
                when (exit) {
                    "lock" -> session.lock()
                    "other-action" -> model.detail("other")
                    else -> {
                        actions.row = actions.row.copy(status = if (phase == ActionStatus.ACTIVE)
                            ActionStatus.COMPLETED else ActionStatus.DELETION_PENDING)
                        model.detail("a")
                    }
                }
                empty()
                val reads = listOf(protocolDelegate.detailReads, protocolDelegate.summaryReads)
                val boardReads = ops.loads.size to tasks.loads.size
                val interactions = session.interactions
                callback(result)
                empty(); assertNull(model.feedback)
                assertEquals(reads, listOf(protocolDelegate.detailReads, protocolDelegate.summaryReads))
                assertEquals(boardReads, ops.loads.size to tasks.loads.size)
                assertEquals(interactions, session.interactions)
                if (exit == "lock") session.emit(SessionState.READY, session.value.generation + 1)
            } }
        }
    }
    @Test fun enteringProtocolDiscardsTaskOperationalAndPersonEditorsWithoutWrites() {
        listOf("task", "function", "person").forEach { editor ->
            start()
            model.activeArea(if (editor == "task") ActiveArea.TASKS else if (editor == "function")
                ActiveArea.FUNCTIONS else ActiveArea.PERSONS)
            complete()
            when (editor) {
                "task" -> { model.tasks.add(); model.tasks.change(TaskInput("OTHER-AREA-SENTINEL")); assertTrue(model.tasks.form) }
                "function" -> { model.operations.newFunction(); model.operations.changeNewFunctionName("OTHER-AREA-SENTINEL"); assertEquals(OperationsScreen.NEW_FUNCTION, model.operations.screen) }
                else -> { model.personDetail("p"); assertNotNull(model.selectedPerson) }
            }
            model.activeArea(ActiveArea.PROTOCOL); complete()
            assertFalse(model.tasks.form); assertNull(model.tasks.selected); assertEquals("", model.tasks.input.title)
            assertEquals(OperationsScreen.OVERVIEW, model.operations.screen); assertEquals("", model.operations.newFunctionName)
            assertNull(model.selectedPerson); assertNull(model.sensitiveInventory); assertNull(model.sensitiveOpened)
            assertNull(model.protocol.input); assertNull(model.protocol.expected); assertNull(model.protocol.detail)
            assertEquals(0, protocolDelegate.writes); assertEquals(0, actions.edits); assertEquals(0, actions.transitions)
            assertEquals(0, protection.opens)
        }
    }
}
