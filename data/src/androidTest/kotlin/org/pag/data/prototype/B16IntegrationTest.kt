package org.pag.data.prototype

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/** Engine/protocol tests with an injected key consumer. NOT a credential/Keystore proof. */
class B16IntegrationTest {
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir.canonicalFile,
        "b16-test-${UUID.randomUUID()}")
    private var keys = BootstrapState.EMPTY
    private val inventory = B16Inventory(root) { keys }
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var time = 0L
    private lateinit var callback: (AuthenticationOutcome) -> Unit
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callback = complete; return Cancellation {}
        }
    }, Maintenance { Subscription {} }, MonotonicTime { time }, SessionPolicy(100, 20))
    private fun context(): StorageContext {
        session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        callback(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() {}
        }, time))
        return session.acquire(AccessScope.MAINTENANCE)!!
    }
    private fun operation(bytes: ByteArray = key) = object : SyntheticOperation {
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() {}
        override fun useUnwrappedKey(allowed: () -> Boolean, consume: (ByteArray) -> Unit): OperationResult {
            if (!allowed()) return OperationResult.NOT_AUTHORIZED
            val local = bytes.copyOf()
            return try { consume(local); if (allowed()) OperationResult.SUCCEEDED else OperationResult.NOT_AUTHORIZED }
            catch (_: RuntimeException) { OperationResult.FAILED }
            finally { local.fill(0) }
        }
    }
    private fun pending() {
        inventory.reserveNew(); assertTrue(File(root, "k").mkdir()); keys = BootstrapState.COMPLETE
    }
    private fun create() {
        pending(); assertEquals(B16Id.CREATED, B16StorageGate(inventory).run(context(), operation(), true))
        assertEquals(BootstrapState.COMPLETE, inventory.state())
    }
    private fun unchangedAfter(expected: B16Id, work: () -> B16Id) {
        val before = inventory.database.readBytes()
        assertEquals(expected, work()); assertArrayEquals(before, inventory.database.readBytes())
    }
    @After fun finish() {
        key.fill(0); session.lock()
        // Retain encrypted test fixtures. Scan only database/sidecars, not public identity markers.
        if (root.exists()) root.listFiles()!!.filter { it.name.startsWith("d") && it.isFile }.forEach {
            val bytes = it.readBytes()
            val marker = B16StorageGate.MARKER.toByteArray(Charsets.UTF_8)
            assertFalse("B16-MARKER-SCAN", bytes.asList().windowed(marker.size).any { part -> part == marker.asList() })
        }
    }
    @Test fun createAndReopenSameFixtureInFreshGeneration() {
        create(); val id = inventory.identity(); session.lock()
        assertEquals(B16Id.RESTART, B16StorageGate(inventory).run(context(), operation(), false))
        assertEquals(id, inventory.identity())
    }
    @Test fun wrongKeyPreservesDatabase() {
        create(); session.lock(); val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try { unchangedAfter(B16Id.KEY_OR_DB) { B16StorageGate(inventory).run(context(), operation(wrong), false) } }
        finally { wrong.fill(0) }
    }
    @Test fun damagedDatabaseIsNotReinitialized() {
        create(); session.lock(); inventory.database.writeBytes(byteArrayOf(1, 2, 3))
        unchangedAfter(B16Id.KEY_OR_DB) { B16StorageGate(inventory).run(context(), operation(), false) }
    }
    @Test fun deadlineBeforeUnwrapRejectsConsumer() {
        pending(); val access = context(); time = 100
        assertEquals(B16Id.EXPIRED, B16StorageGate(inventory).run(access, operation(), true))
        assertFalse(inventory.database.exists())
    }
    @Test fun deadlineAfterUnwrapPreventsOpen() {
        pending(); assertEquals(B16Id.EXPIRED, B16StorageGate(inventory, B16Hooks(afterUnwrap = { time = 100 })).run(context(), operation(), true))
        assertFalse(inventory.database.exists())
    }
    @Test fun deadlineBeforeOpenPreventsOpen() {
        pending(); assertEquals(B16Id.EXPIRED, B16StorageGate(inventory, B16Hooks(beforeOpen = { time = 100 })).run(context(), operation(), true))
        assertFalse(inventory.database.exists())
    }
    @Test fun deadlineDuringOpenRejectsHandle() {
        create(); session.lock()
        assertEquals(B16Id.EXPIRED, B16StorageGate(inventory, B16Hooks(engine = EngineTestHooks(afterOpen = { time = 100 }))).run(context(), operation(), false))
    }
    @Test fun deadlineAfterQueryPreventsResult() {
        create(); session.lock()
        assertEquals(B16Id.EXPIRED, B16StorageGate(inventory, B16Hooks(engine = EngineTestHooks(afterQuery = { time = 100 }))).run(context(), operation(), false))
    }
    @Test fun generationChangeDuringOpenPreventsResult() {
        create(); session.lock()
        assertEquals(B16Id.EXPIRED, B16StorageGate(inventory, B16Hooks(engine = EngineTestHooks(afterOpen = { session.lock(); context() }))).run(context(), operation(), false))
    }
    @Test fun foregroundLossAfterUnwrapPreventsOpen() {
        pending(); assertEquals(B16Id.EXPIRED, B16StorageGate(inventory, B16Hooks(afterUnwrap = { session.foreground(false) })).run(context(), operation(), true))
        assertFalse(inventory.database.exists())
    }
    @Test fun deadlineBeforeCommitLeavesPartialBlocked() {
        pending(); assertEquals(B16Id.EXPIRED, B16StorageGate(inventory, B16Hooks(engine = EngineTestHooks(beforeCommit = { time = 100 }))).run(context(), operation(), true))
        assertEquals(BootstrapState.BLOCKED, inventory.state()); assertFalse(File(root, "1").exists())
    }
    @Test fun faultAfterDatabaseCreationDoesNotRepair() {
        pending(); assertEquals(B16Id.BLOCKED, B16StorageGate(inventory, B16Hooks(afterCreate = { throw B16Exception(B16Id.BLOCKED) })).run(context(), operation(), true))
        assertEquals(BootstrapState.BLOCKED, inventory.state()); assertFalse(File(root, "1").exists())
    }
    @Test fun faultAfterCommitDoesNotRepair() {
        pending(); assertEquals(B16Id.BLOCKED, B16StorageGate(inventory, B16Hooks(afterCommit = { throw B16Exception(B16Id.BLOCKED) })).run(context(), operation(), true))
        assertEquals(BootstrapState.BLOCKED, inventory.state()); assertFalse(File(root, "1").exists())
    }
    @Test fun faultBeforeCompletionDoesNotRepair() {
        pending(); assertEquals(B16Id.BLOCKED, B16StorageGate(inventory, B16Hooks(beforeComplete = { throw B16Exception(B16Id.BLOCKED) })).run(context(), operation(), true))
        assertEquals(BootstrapState.BLOCKED, inventory.state()); assertFalse(File(root, "1").exists())
    }
    @Test fun oldDatabaseHandleCannotBeUsedInNewGeneration() {
        create(); session.lock(); val old = context()
        SqlCipherEngine.openExisting(inventory.database, key, old, accessScope = AccessScope.MAINTENANCE).use { db ->
            session.lock(); context()
            try { db.read {}; fail("B16-OLD-HANDLE") } catch (error: EngineException) { assertEquals(EngineFailure.SESSION_INVALID, error.failure) }
        }
    }
    @Test fun unknownSchemaIsNotMigrated() {
        create(); session.lock(); System.loadLibrary("sqlcipher")
        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(inventory.database.path, key, null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE, net.zetetic.database.DatabaseErrorHandler { _, _ -> }, null).use { it.execSQL("PRAGMA user_version = 99") }
        unchangedAfter(B16Id.SCHEMA) { B16StorageGate(inventory).run(context(), operation(), false) }
    }
}
