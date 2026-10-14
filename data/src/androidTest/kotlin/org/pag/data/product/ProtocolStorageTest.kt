package org.pag.data.product

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.B2Native
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** SQLCipher/Room only in standalone synthetic data-test package; no product auth, UI or keys. */
class ProtocolStorageTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    @Volatile private var allowed = true
    @Volatile private var generation = 1L
    private var denyCommit = false
    private var time = 1000L
    private val direct = Executor { it.run() }
    private val owners = mutableListOf<SessionDbKeyOwner>()
    private fun context(scope: AccessScope = AccessScope.READY): StorageContext = object : StorageContext {
        private val captured = this@ProtocolStorageTest.generation
        override val generation = captured
        override val scope = scope
        override fun check(point: AccessPoint): Boolean {
            if (denyCommit && point == AccessPoint.COMMIT) allowed = false
            return allowed && captured == this@ProtocolStorageTest.generation
        }
        override fun publish(result: () -> Unit): Boolean {
            if (!check(AccessPoint.PUBLICATION)) return false
            result(); return true
        }
    }
    private fun fixture(): ProductInventory {
        val root = File(app.noBackupFilesDir.canonicalFile, "p6c1-test-${UUID.randomUUID()}")
        check(root.mkdirs()); check(root.resolve("store").mkdir())
        listOf("intent", "complete", "public").forEach { root.resolve(it).writeBytes(byteArrayOf(1)) }
        root.resolve("envelope").writeBytes(ByteArray(256))
        val i = ProductInventory(root, { true }, { true })
        ActionDatabaseAccess(app).use(i.database, key, context(AccessScope.MAINTENANCE), true) { d -> d.withWriteTransaction {
            for (a in listOf("a", "b")) d.actions().insert(ActionRow(a, "synthetic", "synthetic", 1, 2, 0, "ACTIVE"))
            for ((id, a, label) in listOf(Triple("p", "a", "Motte"), Triple("q", "a", "Meise"), Triple("foreign", "b", "Klee"))) {
                d.persons().insert(PersonRow(id, a, label, label.lowercase(), 0))
            }
        } }
        return i
    }
    private fun input(title: String = "synthetic") = ProtocolInput(ProtocolClassification.KEY_EVENT, title, 10,
        description = "synthetic detail", sourceType = ProtocolSourceType.REPORTED, reportedByPersonId = "p",
        affectedPersonIds = setOf("p", "q"), placeLabel = "synthetic place",
        announcementText = "synthetic announcement", announcementKind = ProtocolAnnouncementKind.PARAPHRASE,
        statedLegalBasis = "synthetic attributed statement",
        officialIdentifiers = listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, "synthetic-id")))
    private fun <T> use(i: ProductInventory, work: suspend (ActionDatabase) -> T): T = ActionDatabaseAccess(app).use(i.database, key, context(), work = work)
    private fun <T> write(i: ProductInventory, work: suspend (ProtocolStore) -> ProtocolResult<T>): ProtocolResult<T> {
        val c = context()
        return ActionDatabaseAccess(app).use(i.database, key, c) { d -> d.withWriteTransaction {
            val r = work(ProtocolStore(d, c) { time }); requireOperations(c, AccessPoint.COMMIT); r
        } }
    }
    private fun <T> value(r: ProtocolResult<T>): T { assertTrue(r is ProtocolResult.Value); return (r as ProtocolResult.Value).value }
    private fun reject(r: ProtocolResult<*>, reason: ProtocolFailure) = assertEquals(ProtocolResult.Rejected(reason), r)
    private fun create(i: ProductInventory, content: ProtocolInput = input(), phase: ActionStatus = ActionStatus.ACTIVE): ProtocolSaved = value(write(i) { it.create("a", phase, content) })
    private fun detail(i: ProductInventory, id: String) = value(use(i) { d -> d.withReadTransaction { ProtocolStore(d, context()) { time }.detail("a", id) } })
    private fun expected(i: ProductInventory, id: String): ProtocolExpectedRevision = detail(i, id).let { ProtocolExpectedRevision(it.entry, it.current.revision) }
    private fun state(i: ProductInventory, s: ActionStatus) = use(i) { d -> d.withWriteTransaction {
        d.actions().update(d.actions().get("a")!!.copy(status = s.name, completedAt = 42L.takeIf { s == ActionStatus.COMPLETED }))
    } }
    private fun sql(i: ProductInventory, statement: String) = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE)).use { d -> d.execSQL("PRAGMA foreign_keys=ON"); d.execSQL(statement) }
    private fun count(i: ProductInventory, table: String): Long = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { d ->
        d.rawQuery("SELECT COUNT(*) FROM $table", emptyArray<String>()).use { c -> check(c.moveToFirst()); c.getLong(0) }
    }
    private fun fails(work: () -> Unit) { assertThrows(RuntimeException::class.java, work) }
    private fun repo(i: ProductInventory, worker: Executor = direct, main: Executor = direct): ProtocolRepository {
        val owner = SessionDbKeyOwner.bind(context(), key); owners.add(owner)
        val access = object : StorageAccess { override fun acquire(scope: AccessScope) = context(scope).takeIf { it.check(AccessPoint.OPERATION) } }
        return ProductProtocolRepository(app, access, i, { owner }, worker, main, { fail("P6-UNEXPECTED-STORAGE-FAILURE") }) { time }
    }
    @After fun wipe() { owners.forEach { it.invalidate() }; key.fill(0) }
    @Test fun productionRepositoryPortCreatesRevisesMarksAndReads() {
        val i = fixture(); val r = repo(i)
        var saved: ProtocolSaved? = null; var loaded: ProtocolEntryDetail? = null
        r.createEntry("a", ActionStatus.ACTIVE, input()) { saved = value(it) }
        r.getEntry("a", saved!!.entryId) { loaded = value(it) }
        var e = ProtocolExpectedRevision(loaded!!.entry, loaded!!.current.revision)
        r.reviseEntry("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.CORRECTION, input("changed")) { saved = value(it) }
        assertEquals(2L, saved!!.revisionNumber)
        r.getEntry("a", saved!!.entryId) { loaded = value(it) }
        e = ProtocolExpectedRevision(loaded!!.entry, loaded!!.current.revision)
        r.markMistaken("a", ActionStatus.ACTIVE, e) { saved = value(it) }
        r.getEntry("a", saved!!.entryId) { loaded = value(it) }
        assertTrue(loaded!!.current.revision.mistaken); assertEquals(3, loaded!!.revisions.size)
        assertEquals("changed", loaded!!.current.revision.input.title)
    }
    @Test fun createPersistsFullNormalizedContentAndOwnedTimes() {
        val i = fixture(); val saved = create(i, input(" synthetic ").copy(officialIdentifiers = List(32) { input().officialIdentifiers.single() }))
        val d = detail(i, saved.entryId)
        assertEquals(1L, saved.revisionNumber); assertEquals(1000L, d.current.revision.recordedAt)
        assertEquals(10L, d.current.revision.input.occurredAt); assertFalse(d.entry.createdAfterCompletion)
        assertEquals("synthetic", d.current.revision.input.title); assertEquals(32, d.current.revision.input.officialIdentifiers.size)
        assertEquals(listOf("Motte", "Meise"), d.current.affectedPersons.map { it.pseudonym })
        assertEquals("Motte", d.current.reportedBy!!.pseudonym)
        assertEquals(input().description, d.current.revision.input.description)
    }
    @Test fun completedFlagsAndLateDraftArePersistedPolicy() {
        val i = fixture(); val original = create(i); val e = expected(i, original.entryId)
        state(i, ActionStatus.COMPLETED)
        reject(write(i) { it.revise("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.CORRECTION, input()) }, ProtocolFailure.STALE)
        reject(write(i) { it.create("a", ActionStatus.ACTIVE, input()) }, ProtocolFailure.STALE)
        time = 1
        value(write(i) { it.revise("a", ActionStatus.COMPLETED, e, ProtocolRevisionType.CORRECTION, input()) })
        val d = detail(i, original.entryId)
        assertFalse(d.entry.createdAfterCompletion); assertTrue(d.current.revision.recordedAfterCompletion)
        assertEquals(1L, d.current.revision.recordedAt); assertEquals(1000L, d.revisions.first().revision.recordedAt)
        val late = detail(i, create(i, phase = ActionStatus.COMPLETED).entryId)
        assertTrue(late.entry.createdAfterCompletion); assertTrue(late.current.revision.recordedAfterCompletion)
    }
    @Test fun allStatusGatesAndCompletedDoesNotOpenP4P5a() {
        val i = fixture(); val saved = create(i)
        for (s in ActionStatus.entries) {
            state(i, s)
            val read = use(i) { ProtocolStore(it, context()) { time }.timeline("a") }
            val creation = write(i) { it.create("a", s, input()) }
            if (ProtocolRules.mayRead(s)) { assertTrue(read is ProtocolResult.Value); assertTrue(creation is ProtocolResult.Value) }
            else { reject(read, ProtocolFailure.INVALID_STATE); reject(creation, ProtocolFailure.INVALID_STATE) }
        }
        state(i, ActionStatus.COMPLETED)
        val p6 = expected(i, saved.entryId)
        value(write(i) { it.markMistaken("a", ActionStatus.COMPLETED, p6) })
        use(i) { d -> d.withWriteTransaction {
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED), TaskStore(d, context()) { time }.create("a", TaskInput("synthetic")))
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED), OperationsStore(d, context()) { time }.role("a", null, "synthetic"))
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED), OperationsStore(d, context()) { time }.snapshot("a", "p", PersonOperationalState("a", "p", null, OperationalStatus.NO_STATUS, null, 0), OperationalStatus.ON_SITE, null, ""))
        } }
    }
    @Test fun revisionsKeepFullOldSnapshotsAndRejectStale() {
        val i = fixture(); val saved = create(i); val before = detail(i, saved.entryId); val e = expected(i, saved.entryId)
        time = 900
        value(write(i) { it.revise("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.CORRECTION,
            input("changed").copy(affectedPersonIds = setOf("q"), reportedByPersonId = null, officialIdentifiers = emptyList())) })
        assertEquals(before.revisions.first(), detail(i, saved.entryId).revisions.first())
        reject(write(i) { it.revise("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.ADDENDUM, input()) }, ProtocolFailure.STALE)
        assertEquals(2L, detail(i, saved.entryId).current.revision.revisionNumber)
        assertEquals(setOf("q"), detail(i, saved.entryId).current.revision.input.affectedPersonIds)
    }
    @Test fun mistakenFullCopyAddendumAndExplicitRestore() {
        val i = fixture(); val saved = create(i); val e = expected(i, saved.entryId)
        value(write(i) { it.markMistaken("a", ActionStatus.ACTIVE, e) })
        var current = expected(i, saved.entryId)
        assertEquals(e.revision.input, current.revision.input); assertTrue(current.revision.mistaken)
        reject(write(i) { it.markMistaken("a", ActionStatus.ACTIVE, current) }, ProtocolFailure.INVALID_STATE)
        value(write(i) { it.revise("a", ActionStatus.ACTIVE, current, ProtocolRevisionType.ADDENDUM, input()) })
        current = expected(i, saved.entryId); assertTrue(current.revision.mistaken)
        value(write(i) { it.revise("a", ActionStatus.ACTIVE, current, ProtocolRevisionType.CORRECTION, input(), true) })
        assertFalse(detail(i, saved.entryId).current.revision.mistaken)
        assertEquals(4, detail(i, saved.entryId).revisions.size)
    }
    @Test fun invalidInputAndForeignReferencesWriteNothing() {
        val i = fixture()
        for (bad in listOf(input("").copy(title = " "), input().copy(templateKey = "UNKNOWN"), input().copy(officialIdentifiers = List(33) { input().officialIdentifiers.single() })))
            reject(write(i) { it.create("a", ActionStatus.ACTIVE, bad) }, ProtocolFailure.INVALID)
        for (bad in listOf(input().copy(affectedPersonIds = setOf("foreign")), input().copy(reportedByPersonId = "foreign")))
            reject(write(i) { it.create("a", ActionStatus.ACTIVE, bad) }, ProtocolFailure.NOT_FOUND)
        reject(write(i) { it.create("missing", ActionStatus.ACTIVE, input()) }, ProtocolFailure.NOT_FOUND)
        assertEquals(0L, count(i, "protocol_entries"))
    }
    @Test fun directDbCrossActionAndSourceConstraints() {
        val i = fixture(); val saved = create(i); val r = use(i) { it.protocol().current("a", saved.entryId)!! }
        fails { use(i) { d -> d.withWriteTransaction { d.protocol().insert(r.copy(id = "bad-entry", actionId = "b", revisionNumber = 1)) } } }
        fails { use(i) { d -> d.withWriteTransaction { d.protocol().insert(ProtocolAffectedRow("a", r.id, "foreign")) } } }
        fails { use(i) { d -> d.withWriteTransaction { d.protocol().insert(ProtocolReportedRow("b", r.id, "foreign")) } } }
        val other = create(i, input().copy(sourceType = ProtocolSourceType.OWN_OBSERVATION, reportedByPersonId = null))
        fails { use(i) { d -> d.withWriteTransaction { d.protocol().insert(ProtocolReportedRow("a", other.revisionId, "p")) } } }
        fails { use(i) { d -> d.withWriteTransaction { d.protocol().insert(ProtocolIdentifierRow("b", r.id, 0, "OTHER_OFFICIAL_ID", "synthetic")) } } }
    }
    @Test fun dbSequencingImmutabilityAndSlotLimit() {
        val i = fixture(); val s = create(i); val r = use(i) { it.protocol().current("a", s.entryId)!! }
        for (bad in listOf(r.copy(id = "bad-gap", revisionNumber = 3, revisionType = "CORRECTION"),
            r.copy(id = "bad-original", revisionNumber = 2), r.copy(id = "bad-number", revisionNumber = 1, revisionType = "CORRECTION"),
            r.copy(id = "bad-mistaken", revisionNumber = 2, revisionType = "CORRECTION", mistaken = true)))
            fails { use(i) { d -> d.withWriteTransaction { d.protocol().insert(bad) } } }
        fails { sql(i, "UPDATE protocol_revisions SET title='synthetic change' WHERE id='${r.id}'") }
        fails { sql(i, "UPDATE protocol_entries SET createdAfterCompletion=1 WHERE id='${s.entryId}'") }
        fails { use(i) { d -> d.withWriteTransaction { d.protocol().insert(ProtocolIdentifierRow("a", r.id, 32, "OTHER_OFFICIAL_ID", "synthetic")) } } }
        assertEquals(1, detail(i, s.entryId).revisions.size)
    }
    @Test fun failureOfEachChildRollsBackCreateAndAppend() {
        val i = fixture(); val original = create(i)
        for (table in listOf("protocol_affected_persons", "protocol_reported_by_persons", "protocol_official_identifiers")) {
            val before = detail(i, original.entryId); val e = expected(i, original.entryId)
            sql(i, "CREATE TRIGGER synthetic_child_failure BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT,'SYNTHETIC_FAILURE'); END")
            fails { create(i) }
            fails { write(i) { it.revise("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.ADDENDUM, input()) } }
            sql(i, "DROP TRIGGER synthetic_child_failure")
            assertEquals(1L, count(i, "protocol_entries")); assertEquals(before, detail(i, original.entryId))
        }
    }
    @Test fun personDeletionOnlyCleansRelationsAndMakesOldDraftStale() {
        val i = fixture(); val saved = create(i); val e = expected(i, saved.entryId)
        sql(i, "DELETE FROM persons WHERE actionId='a' AND id='p'")
        val after = detail(i, saved.entryId)
        assertEquals(1, after.revisions.size); assertEquals(e.revision.input.description, after.current.revision.input.description)
        assertEquals(setOf("q"), after.current.revision.input.affectedPersonIds); assertNull(after.current.reportedBy)
        assertEquals(1, after.current.revision.input.officialIdentifiers.size)
        reject(write(i) { it.revise("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.CORRECTION, input()) }, ProtocolFailure.STALE)
    }
    @Test fun actionCascadeLeavesNoProtocolIsland() {
        val i = fixture(); val s = create(i)
        val e = expected(i, s.entryId)
        value(write(i) { it.markMistaken("a", ActionStatus.ACTIVE, e) })
        sql(i, "DELETE FROM actions WHERE id='a'")
        for (t in tables) assertEquals(0L, count(i, t))
    }
    @Test fun summaryTimelineOrderAndCurrentClassification() {
        val i = fixture()
        time = 30; val later = create(i, input("later").copy(occurredAt = 20))
        time = 10; val earlier = create(i, input("earlier").copy(occurredAt = 10))
        time = 5; val tied = create(i, input("tied").copy(occurredAt = 10))
        val r = repo(i); var summary: ProtocolSummary? = null; var timeline: List<ProtocolTimelineItem>? = null
        r.summary("a") { summary = value(it) }; r.listTimeline("a") { timeline = value(it) }
        assertEquals(ProtocolSummary(3, 30), summary); assertEquals(listOf(tied.entryId, earlier.entryId, later.entryId), timeline!!.map { it.entryId })
        val e = expected(i, earlier.entryId)
        value(write(i) { it.revise("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.CORRECTION, input("not key").copy(classification = ProtocolClassification.RELEVANT, occurredAt = 10)) })
        r.summary("a") { summary = value(it) }; assertEquals(ProtocolSummary(2, 30), summary)
        assertFalse(timeline.first().hasRevisions)
    }
    @Test fun commitRevokeRollsBackOriginalAndAppend() {
        val i = fixture(); denyCommit = true
        assertThrows(StorageAccessRevoked::class.java) { create(i) }
        denyCommit = false; allowed = true; generation++
        assertEquals(0L, count(i, "protocol_entries"))
        val s = create(i); val e = expected(i, s.entryId); denyCommit = true
        assertThrows(StorageAccessRevoked::class.java) { write(i) { it.markMistaken("a", ActionStatus.ACTIVE, e) } }
        denyCommit = false; allowed = true; generation++
        assertEquals(e.revision, detail(i, s.entryId).current.revision)
    }
    @Test fun generationRevokeBeforeQueuedPublicationNeverCallsConsumer() {
        val i = fixture(); val queue = mutableListOf<Runnable>(); val r = repo(i, main = Executor { queue.add(it) })
        var calls = 0
        r.createEntry("a", ActionStatus.ACTIVE, input()) { calls++ }
        generation++; queue.forEach { it.run() }; assertEquals(0, calls)
        assertEquals(1L, count(i, "protocol_entries"))
        allowed = false; r.summary("a") { calls++ }; assertEquals(0, calls)
    }
    @Test fun concurrentExpectedAppendsHaveExactlyOneWinner() {
        val i = fixture(); val s = create(i); val e = expected(i, s.entryId)
        val pool = Executors.newFixedThreadPool(2); val start = CountDownLatch(1)
        try {
            val writes = (1..2).map { pool.submit<ProtocolResult<ProtocolSaved>> {
                check(start.await(10, TimeUnit.SECONDS))
                write(i) { it.revise("a", ActionStatus.ACTIVE, e, ProtocolRevisionType.ADDENDUM, input("synthetic append")) }
            } }
            start.countDown(); val result = writes.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, result.count { it is ProtocolResult.Value }); assertEquals(1, result.count { it == ProtocolResult.Rejected(ProtocolFailure.STALE) })
            assertEquals(2, detail(i, s.entryId).revisions.size)
        } finally { pool.shutdownNow() }
    }
    @Test fun migrationSixToSevenPreservesRowsCiphertextsAndInstallsSameTriggers() {
        val i = fixture()
        val leadership = LeadershipRow("a", "READY", "synthetic-epoch", 1, "Argon2id", 19, 65536, 3, 1, ByteArray(16) { 2 }, ByteArray(88) { 3 })
        val area = SensitiveRow("a", "p", "IDENTITY", 1, 1, ByteArray(60) { 4 }, ByteArray(40) { 5 })
        use(i) { d -> d.withWriteTransaction { d.leadership().insert(leadership); d.leadership().insertArea(area) } }
        val freshTriggers = triggerNames(i)
        B2Native.open(i.database, key, context(AccessScope.MAINTENANCE)).use { d -> d.beginTransaction(); try {
            removeMaterialSchemaForLegacyFixture(d)
            tables.forEach { d.execSQL("DROP TABLE $it") }
            d.execSQL("UPDATE room_master_table SET identity_hash='a6428bd5e4667c49b847632f1d851ad2' WHERE id=42")
            d.version = 6; d.setTransactionSuccessful()
        } finally { d.endTransaction() } }
        val r = repo(i); r.summary("a") { assertEquals(ProtocolSummary(0, null), value(it)) }
        use(i) { d ->
            assertEquals("Motte", d.persons().get("a", "p")!!.pseudonym)
            assertArrayEquals(leadership.envelope, d.leadership().get("a")!!.envelope)
            assertArrayEquals(area.payload, d.leadership().area("a", "p", "IDENTITY")!!.payload)
        }
        assertEquals(freshTriggers, triggerNames(i)); create(i)
        B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { assertEquals(10, it.version) }
    }
    @Test fun revokedMigrationSixToSevenRetainsSchemaSixForRetry() {
        val i = fixture()
        B2Native.open(i.database, key, context(AccessScope.MAINTENANCE)).use { d -> d.beginTransaction(); try {
            removeMaterialSchemaForLegacyFixture(d)
            tables.forEach { d.execSQL("DROP TABLE $it") }
            d.execSQL("UPDATE room_master_table SET identity_hash='a6428bd5e4667c49b847632f1d851ad2' WHERE id=42")
            d.version = 6; d.setTransactionSuccessful()
        } finally { d.endTransaction() } }
        denyCommit = true
        assertThrows(RuntimeException::class.java) { use(i) { fail("P6-MIGRATION-MUST-NOT-PUBLISH") } }
        denyCommit = false; allowed = true; generation++
        B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { d ->
            assertEquals(6, d.version)
            d.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE 'protocol_%'", emptyArray<String>()).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(0L, c.getLong(0))
            }
        }
        repo(i).summary("a") { assertEquals(ProtocolSummary(0, null), value(it)) }
    }
    private fun triggerNames(i: ProductInventory): Set<String> = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { d ->
        d.rawQuery("SELECT sql FROM sqlite_master WHERE type='trigger' AND name LIKE 'protocol_%' ORDER BY name", emptyArray<String>()).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
    }
    companion object {
        private val tables = listOf("protocol_official_identifiers", "protocol_affected_persons", "protocol_reported_by_persons", "protocol_revisions", "protocol_entries")
    }
}
