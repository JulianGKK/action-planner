package org.pag.data.prototype

import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.b2.*
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.UUID

/** Exact same native failure matrix for both variants; fake auth, NOT credential evidence. */
@RunWith(Parameterized::class)
class B2ComparisonTest(private val variant: B2Variant) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun variants() = B2Variant.entries.map { arrayOf(it) }
        const val MARKER = "PAG_B2_SYNTHETIC_ONLY_720419"
    }
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(app.noBackupFilesDir.canonicalFile, "b2-test-${UUID.randomUUID()}")
    private val file = File(root, "d")
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var now = 0L
    private lateinit var callback: (AuthenticationOutcome) -> Unit
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callback = complete; return Cancellation {}
        }
    }, Maintenance { Subscription {} }, MonotonicTime { now }, SessionPolicy(100, 20))
    private lateinit var access: StorageContext
    private fun reauth() {
        session.lock(); session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        callback(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.NOT_AUTHORIZED
            override fun release() {}
        }, now))
        access = session.acquire(AccessScope.MAINTENANCE)!!
    }
    @Before fun setup() { assertTrue(root.mkdir()); reauth() }
    @After fun finish() {
        session.lock(); key.fill(0)
        val patterns = listOf(Charsets.UTF_8, Charsets.US_ASCII, Charsets.UTF_16LE, Charsets.UTF_16BE).map { MARKER.toByteArray(it) }
        // Includes cache/temp/native sidecars; read failures fail the test, no cleanup.
        File(app.applicationInfo.dataDir).walkTopDown().filter { it.isFile }.forEach { stored ->
            val bytes = stored.readBytes()
            assertFalse("B2-MARKER-STOP", patterns.any { pattern ->
                containsPattern(bytes, pattern)
            })
        }
    }
    // Same exact substring predicate, avoiding per-byte iterator/boxing overhead on retained fixtures.
    private fun containsPattern(bytes: ByteArray, pattern: ByteArray): Boolean {
        var start = 0
        while (start <= bytes.size - pattern.size) {
            var offset = 0
            while (offset < pattern.size && bytes[start + offset] == pattern[offset]) offset++
            if (offset == pattern.size) return true
            start++
        }
        return false
    }
    @Test fun markerPredicateMatchesPreviousExactScan() {
        val random = kotlin.random.Random(17)
        repeat(100) {
            val bytes = random.nextBytes(it)
            val pattern = random.nextBytes(it % 9 + 1)
            if (it > pattern.size && it % 2 == 0) pattern.copyInto(bytes, it - pattern.size)
            val reference = (0..bytes.size - pattern.size).any { start ->
                pattern.indices.all { offset -> bytes[start + offset] == pattern[offset] }
            }
            assertEquals(reference, containsPattern(bytes, pattern))
        }
        assertTrue(containsPattern(byteArrayOf(1,2,1,2,3), byteArrayOf(1,2,3)))
        assertFalse(containsPattern(byteArrayOf(1,2), byteArrayOf(1,2,3)))
    }
    private fun open(creates: Boolean = false, version: Int = 2, migration: Boolean = true,
        hooks: B2Hooks = B2Hooks(), bytes: ByteArray = key) = B2Database.open(app, file, bytes, access, variant, creates, version, migration, hooks)
    private fun row() = B2Record("synthetic-id", MARKER, 0)
    private fun create(version: Int = 2) { open(true, version).use { assertTrue(it.write(row())) } }
    private fun rows(db: B2Database): List<B2Record> { var rows: List<B2Record>? = null; assertTrue(db.read { rows = it }); return rows!! }
    private fun reject(expected: B2Failure? = null, work: () -> Unit) {
        try { work(); fail("B2-EXPECTED-REJECTION") }
        catch (error: B2Exception) { if (expected != null) assertEquals(expected, error.failure); assertEquals("B2-${error.failure.name}", error.message) }
    }
    private fun unchanged(work: () -> Unit) {
        val before = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes() }
        reject(work = work)
        val after = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes() }
        assertEquals(before.keys, after.keys); before.forEach { (name, bytes) -> assertTrue("B2-UNCHANGED", bytes.contentEquals(after[name])) }
    }
    @Test fun explicitCreateReadCloseAndNewGenerationReopen() {
        val db = open(true); assertTrue(db.write(row())); assertEquals(listOf(row()), rows(db)); db.close()
        assertTrue(db.isClosed()); reject(B2Failure.CLOSED) { db.read {} }
        reauth(); open().use { assertEquals(listOf(row()), rows(it)) }
    }
    @Test fun missingDatabaseIsNeverCreated() { reject(B2Failure.MISSING) { open() }; assertFalse(file.exists()) }
    @Test fun existingDatabaseIsNeverRecreated() { create(); unchanged { open(true) } }
    @Test fun unknownInventoryIsNeverCleaned() {
        create(); File(root, "unknown").writeBytes(byteArrayOf(7)); unchanged { open() }
    }
    @Test fun sidecarWithoutMainDatabaseIsNeverInitialized() {
        File(root, "d-wal").writeBytes(byteArrayOf(7)); reject(B2Failure.MISSING) { open() }
        reject(B2Failure.NOT_EMPTY) { open(true) }; assertFalse(file.exists())
    }
    @Test fun nonemptyLockArtifactIsBlockedWithoutTruncation() {
        create(); File(root, "d.lck").writeBytes(byteArrayOf(7)); unchanged { open() }
    }
    @Test fun lockArtifactAloneNeverAuthorizesInitialization() {
        File(root, "d.lck").createNewFile(); reject { open(true) }; assertFalse(file.exists())
    }
    @Test fun wrongKeyPreservesExistingDatabase() {
        create(); val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try { unchanged { open(bytes = wrong) } } finally { wrong.fill(0) }
        open().use { assertEquals(listOf(row()), rows(it)) }
    }
    @Test fun missingKeyPreservesDatabase() { create(); unchanged { open(bytes = byteArrayOf()) } }
    @Test fun damagedHeaderIsNotReplaced() {
        create(); RandomAccessFile(file, "rw").use { it.seek(0); it.write(ByteArray(128)) }; unchanged { open() }
    }
    @Test fun truncatedDatabaseIsNotReplaced() {
        create(); RandomAccessFile(file, "rw").use { it.setLength(33) }; unchanged { open() }
    }
    @Test fun unknownNewerSchemaIsNeverDowngraded() {
        create(); raw { it.version = 99 }; unchanged { open() }
    }
    @Test fun constraintViolationRollsBack() {
        create(); open().use { reject(B2Failure.CONSTRAINT) { it.write(row()) }; assertEquals(listOf(row()), rows(it)) }
    }
    @Test fun foreignKeyViolationRollsBackRecordAndChild() {
        create(); open().use {
            reject(B2Failure.CONSTRAINT) { it.write(B2Record("child", MARKER), childParent = "missing-parent") }
            assertEquals(listOf(row()), rows(it))
        }
    }
    @Test fun validForeignKeySucceeds() {
        create(); open().use { assertTrue(it.write(B2Record("child", MARKER), childParent = "synthetic-id")); assertEquals(2, rows(it).size) }
    }
    @Test fun requestedRollbackPreservesRows() { create(); open().use { assertTrue(it.write(B2Record("rollback", MARKER), commit = false)); assertEquals(listOf(row()), rows(it)) } }
    @Test fun migrationPreservesDataAndAddsRevision() {
        create(1); reauth(); open(version = 2).use { assertEquals(listOf(row()), rows(it)); assertTrue(it.write(B2Record("v2", MARKER, 2))) }
        reauth(); open().use { assertEquals(2, rows(it).size) }
    }
    @Test fun missingMigrationPreservesDatabase() { create(1); unchanged { open(migration = false) }; open(version = 1).use { assertEquals(listOf(row()), rows(it)) } }
    @Test fun failedMigrationRollsBackSchemaAndData() {
        create(1); reject { open(hooks = B2Hooks(duringMigration = { throw B2Exception(B2Failure.MIGRATION) })) }
        open(version = 1).use { assertEquals(listOf(row()), rows(it)) }; raw { assertEquals(1, it.version) }
    }
    @Test fun expiredBeforeOpenDeniesAccess() { create(); now = 100; reject(B2Failure.SESSION) { open() } }
    @Test fun expiredAfterOpenClosesHandleAndDeniesAccess() { create(); reject(B2Failure.SESSION) { open(hooks = B2Hooks(afterOpen = { now = 100 })) }; reauth(); open().close() }
    @Test fun generationChangeBeforeQueryDeniesOldHandle() { create(); open().use { reauth(); reject(B2Failure.SESSION) { it.read {} } } }
    @Test fun generationChangeBeforeCommitRollsBack() {
        create(); open(hooks = B2Hooks(beforeCommit = { reauth() })).use { reject(B2Failure.SESSION) { it.write(B2Record("late", MARKER)) } }
        open().use { assertEquals(listOf(row()), rows(it)) }
    }
    @Test fun deadlineBeforeCommitRollsBack() {
        create(); open(hooks = B2Hooks(beforeCommit = { now = 100 })).use { reject(B2Failure.SESSION) { it.write(B2Record("late", MARKER)) } }
        reauth(); open().use { assertEquals(listOf(row()), rows(it)) }
    }
    @Test fun generationChangeBeforePublicationDiscardsResult() {
        create(); open(hooks = B2Hooks(afterQuery = { reauth() })).use { assertFalse(it.read { fail("B2-LATE-PUBLICATION") }) }
    }
    @Test fun deadlineBeforePublicationDiscardsResult() {
        create(); open(hooks = B2Hooks(afterQuery = { now = 100 })).use { assertFalse(it.read { fail("B2-LATE-PUBLICATION") }) }
    }
    @Test fun foregroundLossRevokesOldDatabase() { create(); open().use { session.foreground(false); reject(B2Failure.SESSION) { it.read {} } } }
    @Test fun repeatedShortLifetimesDoNotRequireSingleton() { create(); repeat(5) { reauth(); open().use { assertEquals(listOf(row()), rows(it)) } } }
    private fun raw(work: (SQLiteDatabase) -> Unit) {
        System.loadLibrary("sqlcipher")
        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READWRITE,
            DatabaseErrorHandler { _, _ -> }, null).use(work)
    }
}
