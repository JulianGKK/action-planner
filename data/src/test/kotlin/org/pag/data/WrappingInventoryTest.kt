package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.security.*
import java.io.File
import java.nio.file.Files

/** Only inventory transitions. Fake ciphertext is NOT a crypto/authentication proof. */
class WrappingInventoryTest {
    private class Backend : BootstrapKeyBackend {
        var exists = false
        var creations = 0
        override fun aliasPresent() = exists
        override fun publicIdentity() = byteArrayOf(7, 8, 9)
        override fun createWrappedKey(): ByteArray { creations++; exists = true; return ByteArray(256) { 42 } }
    }
    private val parent = Files.createTempDirectory(File("build/b15-unit").apply { mkdirs() }.toPath(), "fixture-").toFile()
    private val root = File(parent, "reserved")
    private val backend = Backend()
    private val inventory = WrappingInventory(root, backend)
    private fun blocked(work: () -> Unit) {
        try { work(); fail("B15-EXPECTED-BLOCK") }
        catch (_: WrappingException) { }
    }
    private fun put(name: String, bytes: ByteArray = byteArrayOf(1)) {
        root.mkdirs(); File(root, name).writeBytes(bytes)
    }
    @Test fun emptyDoesNotInitializeAutomatically() {
        assertEquals(BootstrapState.EMPTY, inventory.state())
        blocked { inventory.readWrapped() }; assertEquals(0, backend.creations)
        assertFalse(root.exists())
    }
    @Test fun explicitEmptyInitializationAndNewInventoryInstanceWork() {
        inventory.initializeNew { true }
        assertEquals(BootstrapState.COMPLETE, inventory.state())
        assertArrayEquals(ByteArray(256) { 42 }, WrappingInventory(root, backend).readWrapped())
        assertEquals(1, backend.creations)
        blocked { inventory.initializeNew { true } }; assertEquals(1, backend.creations)
    }
    @Test fun existingAliasWithoutFilesBlocks() {
        backend.exists = true; blocked { inventory.initializeNew { true } }
        assertFalse(root.exists()); assertEquals(0, backend.creations)
    }
    @Test fun wrappedWithoutAliasBlocks() {
        put("1", ByteArray(256)); blocked { inventory.initializeNew { true } }
        assertEquals(0, backend.creations); assertEquals(256L, File(root, "1").length())
    }
    @Test fun markerWithoutAliasBlocks() {
        put("0"); blocked { inventory.initializeNew { true } }; assertEquals(0, backend.creations)
    }
    @Test fun databaseSidecarsAssetsStagingAndWorkStatesAllBlock() {
        for (name in listOf("db", "db-wal", "db-shm", "db-journal", "assets", "staging", "import", "delete", "maintenance")) {
            val candidate = File(parent, name); assertTrue(candidate.mkdir())
            File(candidate, name).writeBytes(byteArrayOf(99))
            val probe = WrappingInventory(candidate, backend)
            assertEquals(BootstrapState.BLOCKED, probe.state())
            blocked { probe.initializeNew { true } }
            assertEquals(0, backend.creations)
            assertArrayEquals(byteArrayOf(99), File(candidate, name).readBytes())
        }
    }
    @Test fun unknownFileNeverDeletedOrReplaced() {
        put("unknown", byteArrayOf(11)); blocked { inventory.initializeNew { true } }
        assertArrayEquals(byteArrayOf(11), File(root, "unknown").readBytes())
    }
    @Test fun truncatedWrappedIsBlockedWithoutReplacement() {
        inventory.initializeNew { true }; File(root, "1").writeBytes(byteArrayOf(3))
        assertEquals(BootstrapState.BLOCKED, inventory.state())
        blocked { inventory.readWrapped() }; blocked { inventory.initializeNew { true } }
        assertEquals(1, backend.creations); assertArrayEquals(byteArrayOf(3), File(root, "1").readBytes())
    }
    @Test fun changedAliasIdentityBlocks() {
        inventory.initializeNew { true }; File(root, "2").writeBytes(byteArrayOf(5))
        assertEquals(BootstrapState.BLOCKED, inventory.state()); blocked { inventory.readWrapped() }
    }
    @Test fun crashAfterReservationNeverRetriesAsNew() {
        var checks = 0
        blocked { inventory.initializeNew { ++checks < 2 } }
        assertTrue(File(root, "0").exists()); assertEquals(0, backend.creations)
        blocked { inventory.initializeNew { true } }; assertEquals(0, backend.creations)
    }
    @Test fun crashAfterAliasCreationNeverCreatesReplacement() {
        var checks = 0
        blocked { inventory.initializeNew { ++checks < 3 } }
        assertTrue(backend.exists); assertEquals(1, backend.creations)
        assertEquals(BootstrapState.BLOCKED, inventory.state())
        blocked { inventory.initializeNew { true } }; assertEquals(1, backend.creations)
    }
    @Test fun crashAfterWrappedPersistenceNeverCreatesReplacement() {
        var checks = 0
        blocked { inventory.initializeNew { ++checks < 4 } }
        assertTrue(File(root, "1").isFile); assertFalse(File(root, "2").exists())
        assertEquals(BootstrapState.BLOCKED, inventory.state())
        blocked { inventory.initializeNew { true } }; assertEquals(1, backend.creations)
    }
    @Test fun newSeparateEmptyNamespaceCanInitialize() {
        put("unknown")
        val fresh = WrappingInventory(File(parent, "separate"), backend)
        fresh.initializeNew { true }; assertEquals(BootstrapState.COMPLETE, fresh.state())
        assertTrue(File(root, "unknown").exists())
    }
    @Test fun danglingSymlinkBlocks() {
        val other = File(parent, "other"); other.mkdir()
        Files.createSymbolicLink(root.toPath(), other.toPath())
        assertEquals(BootstrapState.BLOCKED, inventory.state())
        blocked { inventory.initializeNew { true } }; assertEquals(0, backend.creations)
    }

    @Test fun existingSymlinkAndNamespaceFileBlock() {
        val target = File(parent, "target"); target.mkdir()
        Files.createSymbolicLink(root.toPath(), target.toPath().toAbsolutePath())
        assertEquals(BootstrapState.BLOCKED, inventory.state())
        blocked { inventory.initializeNew { true } }; assertEquals(0, backend.creations)
        val fileRoot = File(parent, "file"); fileRoot.writeBytes(byteArrayOf(1))
        assertEquals(BootstrapState.BLOCKED, WrappingInventory(fileRoot, backend).state())
    }
}
