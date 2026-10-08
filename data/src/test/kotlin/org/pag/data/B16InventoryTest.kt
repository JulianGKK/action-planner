package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.*
import org.pag.domain.*
import org.pag.security.BootstrapState
import java.io.File
import java.nio.file.Files
import java.util.UUID

class B16InventoryTest {
    private var keys = BootstrapState.EMPTY
    private val root = Files.createTempDirectory(File("build").also { it.mkdirs() }.toPath(), "b16-").toFile().canonicalFile
    private val inventory = B16Inventory(root) { keys }
    private val context = object : StorageContext {
        override val scope = AccessScope.MAINTENANCE
        override fun check(point: AccessPoint) = true
        override fun publish(result: () -> Unit): Boolean { result(); return true }
    }
    private fun keyArtifacts() { File(root, "k").mkdir(); keys = BootstrapState.COMPLETE }
    private fun complete() {
        inventory.reserveNew(); keyArtifacts(); File(root, "d").writeBytes(byteArrayOf(7))
        inventory.finish(UUID.randomUUID().toString(), context)
    }
    private fun blocked() {
        assertEquals(BootstrapState.BLOCKED, inventory.state())
        val before = root.walkTopDown().filter { it.isFile }.associate { it.name to it.readBytes().toList() }
        try { inventory.reserveNew(); fail("B16-NO-REPAIR") } catch (_: B16Exception) {}
        assertEquals(before, root.walkTopDown().filter { it.isFile }.associate { it.name to it.readBytes().toList() })
    }
    @Test fun emptyRequiresExplicitReservation() { assertEquals(BootstrapState.EMPTY, inventory.state()); inventory.reserveNew(); blocked() }
    @Test fun reservedAliasWithNoWrappedKeyBlocks() {
        inventory.reserveNew(); File(root, "k").mkdir(); File(root, "k/0").writeBytes(byteArrayOf(1))
        keys = BootstrapState.BLOCKED; blocked()
    }
    @Test fun wrappedKeyWithNoAliasBlocks() {
        File(root, "k").mkdir(); File(root, "k/1").writeBytes(byteArrayOf(7))
        keys = BootstrapState.BLOCKED; blocked()
    }
    @Test fun aliasOnlyBlocks() { keys = BootstrapState.BLOCKED; blocked() }
    @Test fun databaseWithoutKeysBlocks() { File(root, "d").writeBytes(byteArrayOf(7)); blocked() }
    @Test fun keysWithoutDatabaseBlocks() { inventory.reserveNew(); keyArtifacts(); blocked() }
    @Test fun unknownFileBlocks() { File(root, "unexpected").writeBytes(byteArrayOf(7)); blocked() }
    @Test fun fixtureCommittedWithoutCompletionBlocks() { inventory.reserveNew(); keyArtifacts(); File(root, "d").writeBytes(byteArrayOf(7)); blocked() }
    @Test fun completeInventoryCanBeReadWithoutMutation() { complete(); assertEquals(BootstrapState.COMPLETE, inventory.state()); val id = inventory.identity(); assertEquals(id, inventory.identity()) }
    @Test fun completionWithMissingDatabaseBlocks() { complete(); assertTrue(File(root, "d").delete()); blocked() }
    @Test fun completionWithMissingKeysBlocks() { complete(); keys = BootstrapState.BLOCKED; blocked() }
    @Test fun completionWithMissingMarkerBlocks() { complete(); assertTrue(File(root, "0").delete()); blocked() }
    @Test fun unknownSidecarBlocks() { complete(); File(root, "d-other").writeBytes(byteArrayOf(7)); blocked() }
    @Test fun knownSidecarIsInventoried() { complete(); File(root, "d-wal").writeBytes(byteArrayOf(7)); assertEquals(BootstrapState.COMPLETE, inventory.state()) }
    @Test fun expiredCompletionDoesNotWriteMarker() {
        inventory.reserveNew(); keyArtifacts(); File(root, "d").writeBytes(byteArrayOf(7))
        val expired = object : StorageContext by context { override fun check(point: AccessPoint) = false }
        try { inventory.finish(UUID.randomUUID().toString(), expired); fail("B16-DEADLINE") } catch (_: B16Exception) {}
        assertFalse(File(root, "1").exists()); blocked()
    }
}
