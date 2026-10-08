package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.*
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executor

/** Preflight dispatch only: no JCA/Android calls and no authorization claim. */
class B16AdapterTest {
    private var keys = BootstrapState.EMPTY
    private val root = Files.createTempDirectory(File("build").also { it.mkdirs() }.toPath(), "b16-adapter-").toFile().canonicalFile
    private val inventory = B16Inventory(root) { keys }
    private val calls = mutableListOf<KeyExperiment>()
    private val adapter = B16AuthenticationExperiment(inventory, object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            calls.add(mode); complete(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED)); return Cancellation {}
        }
    }, B16StorageGate(inventory), Executor { it.run() }, Executor { it.run() })
    private fun run(mode: KeyExperiment): AuthenticationOutcome {
        var result: AuthenticationOutcome? = null
        adapter.start(mode) { result = it }; return result!!
    }
    private fun completeInventory() {
        inventory.reserveNew(); File(root, "k").mkdir(); keys = BootstrapState.COMPLETE
        File(root, "d").writeBytes(byteArrayOf(7))
        inventory.finish(UUID.randomUUID().toString(), object : StorageContext {
            override val scope = AccessScope.MAINTENANCE
            override fun check(point: AccessPoint) = true
            override fun publish(result: () -> Unit): Boolean { result(); return true }
        })
    }
    @Test fun emptyInitializationExplicitlyDispatchesInitialize() {
        run(KeyExperiment.DATABASE_INITIALIZE)
        assertEquals(listOf(KeyExperiment.STORAGE_INITIALIZE), calls)
        assertTrue(File(root, "0").exists()); assertEquals(BootstrapState.BLOCKED, inventory.state())
    }
    private fun fileSnapshot() = root.walkTopDown().associate {
        it.relativeTo(root).path to Triple(it.isDirectory, it.lastModified(),
            if (it.isFile) it.readBytes().toList() else emptyList<Byte>())
    }
    @Test fun completeInitializationDoesNotDispatchOrModify() {
        completeInventory(); val before = fileSnapshot()
        val outcome = run(KeyExperiment.DATABASE_INITIALIZE) as AuthenticationOutcome.Rejected
        assertEquals(SecurityFailure.INVALID_KEY, outcome.reason)
        assertEquals(WrappingFailure.NOT_EMPTY, outcome.diagnostic)
        assertEquals(B16Id.NOT_EMPTY.testId, outcome.diagnostic!!.testId.replace("B15-", "B16-"))
        assertTrue(calls.isEmpty()); assertEquals(before, fileSnapshot())
        assertEquals(BootstrapState.COMPLETE, inventory.state())
    }
    @Test fun partialInitializationKeepsInventoryDiagnosisAndDoesNotModify() {
        inventory.reserveNew(); val before = fileSnapshot()
        val outcome = run(KeyExperiment.DATABASE_INITIALIZE) as AuthenticationOutcome.Rejected
        assertEquals(WrappingFailure.INVENTORY, outcome.diagnostic)
        assertEquals("B16-BOOTSTRAP-INVENTORY", outcome.diagnostic!!.testId.replace("B15-", "B16-"))
        assertTrue(calls.isEmpty()); assertEquals(before, fileSnapshot())
        assertEquals(BootstrapState.BLOCKED, inventory.state())
    }
    @Test fun completeReopenOnlyDispatchesUnwrap() {
        completeInventory(); run(KeyExperiment.DATABASE_REOPEN)
        assertEquals(listOf(KeyExperiment.STORAGE_UNWRAP), calls)
    }
    @Test fun missingDatabaseCannotDispatchInitializationOrUnwrap() {
        inventory.reserveNew(); File(root, "k").mkdir(); keys = BootstrapState.COMPLETE
        run(KeyExperiment.DATABASE_REOPEN); run(KeyExperiment.DATABASE_INITIALIZE)
        assertTrue(calls.isEmpty()); assertFalse(inventory.database.exists())
    }
    @Test fun emptyReopenDoesNotInitialize() {
        run(KeyExperiment.DATABASE_REOPEN); assertTrue(calls.isEmpty())
        assertEquals(BootstrapState.EMPTY, inventory.state())
    }
}
