package org.pag.data.prototype.b3

import org.junit.Assert.*
import org.junit.Test
import org.pag.security.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

class B3AdapterTest {
    private val root = Files.createTempDirectory(File("build").also { it.mkdirs() }.toPath(), "b3-adapter-").toFile().canonicalFile
    private var keys = BootstrapState.EMPTY
    private val inventory = B3Inventory(root, { keys })
    private val modes = mutableListOf<KeyExperiment>()
    private var outcome: AuthenticationOutcome? = null
    private val direct = Executor { it.run() }
    private val adapter = B3AuthenticationExperiment(inventory, object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            modes.add(mode); complete(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED)); return Cancellation {}
        }
    }, direct, direct)
    private fun completeFixture() {
        inventory.reserve(); File(root, "k").mkdir(); keys = BootstrapState.COMPLETE
        inventory.staging().writeBytes(byteArrayOf(7)); inventory.manifest.writeBytes(byteArrayOf(7))
    }
    private fun snapshot() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }
    @Test fun emptyInitUsesExplicitInitializeDelegateOnly() {
        adapter.start(KeyExperiment.B3_INITIALIZE) { outcome = it }
        assertEquals(listOf(KeyExperiment.STORAGE_INITIALIZE), modes)
        assertTrue(File(root, "0").exists())
    }
    @Test fun completeInitDoesNotInvokeDelegateOrMutate() {
        completeFixture(); val before = snapshot()
        adapter.start(KeyExperiment.B3_INITIALIZE) { outcome = it }
        assertTrue(modes.isEmpty()); assertEquals(WrappingFailure.NOT_EMPTY, (outcome as AuthenticationOutcome.Rejected).diagnostic)
        assertTrue(before == snapshot())
    }
    @Test fun reopenUsesUnwrapOnlyWithoutMutation() {
        completeFixture(); val before = snapshot()
        adapter.start(KeyExperiment.B3_REOPEN) { outcome = it }
        assertEquals(listOf(KeyExperiment.STORAGE_UNWRAP), modes); assertTrue(before == snapshot())
    }
    @Test fun partialDoesNotInvokeDelegateOrRepair() {
        inventory.reserve(); val before = snapshot()
        adapter.start(KeyExperiment.B3_INITIALIZE) { outcome = it }
        assertTrue(modes.isEmpty()); assertEquals(WrappingFailure.INVENTORY, (outcome as AuthenticationOutcome.Rejected).diagnostic)
        assertTrue(before == snapshot())
    }
    @Test fun emptyReopenNeverInitializes() {
        adapter.start(KeyExperiment.B3_REOPEN) { outcome = it }
        assertTrue(modes.isEmpty()); assertTrue(root.listFiles()!!.isEmpty())
        assertEquals(WrappingFailure.INVENTORY, (outcome as AuthenticationOutcome.Rejected).diagnostic)
    }
}
