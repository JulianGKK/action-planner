package org.pag.data.prototype

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.DatabaseErrorHandler
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.UUID

/** Native engine tests. No real credentials or data; keys exist only in this process. */
class SqlCipherEngineTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var time = 0L
    private var auth: ((AuthenticationOutcome) -> Unit)? = null
    private var maintenance: ((Boolean) -> Unit)? = null
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            auth = complete; return Cancellation { }
        }
    }, Maintenance { maintenance = it; Subscription { } }, MonotonicTime { time }, SessionPolicy(100, 20))
    private lateinit var directory: File
    private lateinit var file: File
    private lateinit var access: StorageContext
    private val handles = mutableListOf<SqlCipherEngine>()
    private val marker = "PAG_B1_SYNTHETIC_MARKER_UTF8_ASCII_ONLY_739201"
    private fun ready() {
        session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        auth!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() { }
        }, time))
        maintenance!!(true); access = session.acquire()!!
    }
    @Before fun setup() {
        directory = File(app.noBackupFilesDir, UUID.randomUUID().toString())
        assertTrue("B1-NEW-DIRECTORY", directory.mkdir())
        file = File(directory, UUID.randomUUID().toString())
        ready()
    }
    @After fun finish() {
        try {
            handles.forEach { it.close() }
            // Scan all target app data (not only the DB directory), including temp/cache.
            scanInventory()
        } finally { key.fill(0); session.lock() }
        // Retain encrypted synthetic fixtures. No orphan cleanup or recovery implemented.
    }
    private fun create(hooks: EngineTestHooks = EngineTestHooks()): SqlCipherEngine =
        SqlCipherEngine.createNew(file, key, access, hooks).also { handles.add(it) }
    private fun row() = SyntheticRow(UUID.randomUUID().toString(), marker, 7)
    private fun rows(engine: SqlCipherEngine): List<SyntheticRow> {
        var rows: List<SyntheticRow>? = null
        assertTrue("B1-READ-PUBLICATION", engine.read { rows = it })
        return rows!!
    }
    private fun rejected(expected: EngineFailure? = null, work: () -> Unit) {
        try { work(); fail("B1-EXPECTED-REJECTION") }
        catch (failure: EngineException) {
            if (expected != null) assertEquals(expected, failure.failure)
            // Only a technical code, never a raw SQLite/native exception or SQL content.
            assertEquals(failure.failure.name, failure.message)
        }
    }
    @Test fun reportsEngineConfigurationWithoutSecrets() {
        create().configuration { values ->
            assertEquals("2", values["tempStore"])
            assertTrue(values["cipherVersion"]!!.startsWith("4.19.0"))
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("stream", "B1-CONFIG $values\n")
            })
        }
    }
    @Test fun createCommitReadCloseAndReopenWithVolatileKey() {
        val engine = create(); val row = row()
        assertTrue(engine.write(row)); assertTrue("B1-ROUNDTRIP", listOf(row) == rows(engine))
        engine.close()
        SqlCipherEngine.openExisting(file, key, access).use { assertTrue("B1-REOPEN-ROUNDTRIP", listOf(row) == rows(it)) }
    }
    @Test fun rollbackDoesNotPublishCommittedData() {
        val engine = create(); assertTrue(engine.write(row(), commit = false))
        assertTrue(rows(engine).isEmpty())
    }
    @Test fun wrongKeyPreservesExistingBytes() {
        create().close(); val before = file.readBytes()
        val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try { rejected(EngineFailure.KEY_OR_DATABASE_UNREADABLE) {
            SqlCipherEngine.openExisting(file, wrong, access)
        } } finally { wrong.fill(0) }
        assertArrayEquals("B1-UNCHANGED", before, file.readBytes())
        SqlCipherEngine.openExisting(file, key, access).close()
    }
    @Test fun missingKeyNeverCreatesOrReplaces() {
        rejected(EngineFailure.MISSING_KEY) { SqlCipherEngine.createNew(file, null, access) }
        assertFalse(file.exists())
        create().close(); val before = file.readBytes()
        rejected(EngineFailure.MISSING_KEY) { SqlCipherEngine.openExisting(file, null, access) }
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun missingExistingDatabaseNeverCreates() {
        rejected(EngineFailure.MISSING_DATABASE) { SqlCipherEngine.openExisting(file, key, access) }
        assertFalse(file.exists())
    }
    @Test fun corruptedHeaderAndUnclearZeroLengthRemainUntouched() {
        create().close()
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(ByteArray(128)) }
        val before = file.readBytes()
        rejected { SqlCipherEngine.openExisting(file, key, access) }
        assertArrayEquals(before, file.readBytes())
        val empty = File(directory, UUID.randomUUID().toString()); assertTrue(empty.createNewFile())
        rejected(EngineFailure.DATABASE_UNREADABLE) { SqlCipherEngine.openExisting(empty, key, access) }
        assertEquals(0L, empty.length()); assertTrue(empty.exists())
    }
    @Test fun unknownSchemaNeverMigratesOrReinitializes() {
        create().close()
        // Deliberate synthetic fixture mutation, not application migration behavior.
        assertTrue(access.check(AccessPoint.OPERATION))
        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READWRITE,
            DatabaseErrorHandler { _, _ -> }, null).use { it.version = 999 }
        val before = file.readBytes()
        rejected(EngineFailure.UNKNOWN_SCHEMA) { SqlCipherEngine.openExisting(file, key, access) }
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun newOnlyPathRefusesExistingFileEvenWithCorrectKey() {
        create().close(); val before = file.readBytes()
        rejected(EngineFailure.BLOCKED) { SqlCipherEngine.createNew(file, key, access) }
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun expiredSessionRejectsQueryWriteAndExistingOpen() {
        val engine = create(); time = 20
        rejected(EngineFailure.SESSION_INVALID) { engine.read { fail("B1-READ-LEAK") } }
        rejected(EngineFailure.BLOCKED) { engine.write(row()) }
        rejected(EngineFailure.SESSION_INVALID) { SqlCipherEngine.openExisting(file, key, access) }
    }
    @Test fun expirationAfterQueryRejectsPublication() {
        val engine = create(EngineTestHooks(afterQuery = { time = 20 }))
        engine.write(row())
        assertFalse(engine.read { fail("B1-LATE-READ") })
    }
    @Test fun expirationBeforeCommitRollsBack() {
        val engine = create(EngineTestHooks(beforeCommit = { time = 20 }))
        rejected(EngineFailure.SESSION_INVALID) { engine.write(row()) }
        session.lock(); ready()
        SqlCipherEngine.openExisting(file, key, access).use { assertTrue(rows(it).isEmpty()) }
    }
    @Test fun expiryAfterWriteBeforeNextStepRollsBack() {
        val engine = create(EngineTestHooks(afterWrite = { time = 100 }))
        rejected(EngineFailure.SESSION_INVALID) { engine.write(row()) }
        session.lock(); ready()
        SqlCipherEngine.openExisting(file, key, access).use { assertTrue(rows(it).isEmpty()) }
    }
    @Test fun oldHandleAndContextStayRevokedAfterReauthentication() {
        val engine = create(); val old = access
        session.lock(); ready()
        assertFalse(old.check(AccessPoint.COMMIT))
        rejected(EngineFailure.SESSION_INVALID) { engine.write(row()) }
        engine.close()
        SqlCipherEngine.openExisting(file, key, access).use { assertTrue(it.write(row())) }
    }
    @Test fun maintenancePermitCannotOpenRegularEngine() {
        session.lock(); session.authenticate(Experiment.PER_OPERATION)
        auth!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() { }
        }, time))
        val internal = session.acquire(AccessScope.MAINTENANCE)!!
        rejected(EngineFailure.SESSION_INVALID) { SqlCipherEngine.createNew(file, key, internal) }
        assertFalse(file.exists())
    }
    @Test fun rollbackJournalAndWalInventoryWhileLive() {
        create(EngineTestHooks(afterWrite = { scanInventory() })).use { it.write(row()) }
        // Separate engine-observation fixture, not a second application gate.
        assertTrue(access.check(AccessPoint.OPERATION))
        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READWRITE,
            DatabaseErrorHandler { _, _ -> }, null).use { db ->
            assertTrue("B1-WAL-ENABLE", db.enableWriteAheadLogging())
            db.execSQL("PRAGMA temp_store = MEMORY")
            db.rawQuery("PRAGMA wal_autocheckpoint = 0", emptyArray<String>()).use {
                assertTrue("B1-WAL-AUTOCHECKPOINT-RESULT", it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
            db.beginTransaction()
            try {
                db.execSQL("INSERT INTO synthetic VALUES (?, ?, ?)", arrayOf<Any>(UUID.randomUUID().toString(), marker, 3))
                scanInventory(); db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            // Exercise sorting, temp data, checkpoint and VACUUM using only synthetic rows.
            db.rawQuery("SELECT marker FROM synthetic ORDER BY substr(marker, 2)", emptyArray<String>()).use {
                while (it.moveToNext()) assertTrue("B1-SORT-RESULT", marker == it.getString(0))
            }
            scanInventory()
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray<String>()).use { it.moveToFirst() }
            db.execSQL("VACUUM"); scanInventory()
        }
    }
    private fun scanInventory() {
        val root = File(app.applicationInfo.dataDir)
        val patterns = listOf(marker.toByteArray(Charsets.UTF_8), marker.toByteArray(Charsets.US_ASCII),
            marker.toByteArray(Charsets.UTF_16LE), marker.toByteArray(Charsets.UTF_16BE))
        root.walkTopDown().filter { it.isFile }.forEach { stored ->
            val bytes = stored.readBytes()
            val found = patterns.any { pattern -> contains(bytes, pattern) }
            val relative = stored.relativeTo(root).path
            val message = "B1-INVENTORY $relative size=${bytes.size} markerHit=$found\n"
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", message) })
            assertFalse("B1-PERSISTENT-MARKER-STOP $relative", found)
        }
    }
    private fun contains(bytes: ByteArray, pattern: ByteArray): Boolean {
        for (start in 0..(bytes.size - pattern.size)) {
            var matches = true
            for (offset in pattern.indices) if (bytes[start + offset] != pattern[offset]) { matches = false; break }
            if (matches) return true
        }
        return false
    }
}
