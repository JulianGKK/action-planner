package org.pag.data.prototype.b2

import org.junit.Test
import org.junit.Assert.*
import org.pag.domain.*
import org.pag.security.BootstrapState
import java.io.File
import java.nio.file.Files
import java.util.UUID

class B2InventoryTest {
    private val root = Files.createTempDirectory(File("build").also { it.mkdirs() }.toPath(), "b2-").toFile().canonicalFile
    private var keys = BootstrapState.EMPTY
    private val inventory = B2Inventory(root, { keys }, B2Variant.ROOM)
    private val context = object : StorageContext {
        override val scope = AccessScope.MAINTENANCE
        override fun check(point: AccessPoint) = true
        override fun publish(result: () -> Unit): Boolean { result(); return true }
    }
    private fun pending() { inventory.reserve(); File(root, "k").mkdir(); keys = BootstrapState.COMPLETE }
    private fun complete() { pending(); inventory.store.mkdir(); inventory.database.writeBytes(byteArrayOf(7)); inventory.finish(UUID.randomUUID().toString(), context) }
    private fun snapshot() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }
    private fun blocked() {
        assertEquals(BootstrapState.BLOCKED, inventory.state()); val before = snapshot()
        try { inventory.reserve(); fail("B2-NO-REPAIR") } catch (error: B2Exception) { assertEquals(B2Failure.INVENTORY, error.failure) }
        assertEquals(before, snapshot())
    }
    @Test fun emptyNeedsExplicitReservation() { assertEquals(BootstrapState.EMPTY, inventory.state()); inventory.reserve(); blocked() }
    @Test fun keyWithoutDatabaseBlocks() { pending(); blocked() }
    @Test fun databaseWithoutKeysBlocks() { inventory.store.mkdir(); inventory.database.writeBytes(byteArrayOf(7)); blocked() }
    @Test fun unknownRootEntryBlocks() { File(root, "unknown").writeBytes(byteArrayOf(7)); blocked() }
    @Test fun pendingFixtureWithoutCompletionBlocks() { pending(); inventory.store.mkdir(); inventory.database.writeBytes(byteArrayOf(7)); blocked() }
    @Test fun completeDoesNotAllowReinitialization() {
        complete(); assertEquals(BootstrapState.COMPLETE, inventory.state()); val before = snapshot()
        try { inventory.reserve(); fail("B2-NOT-EMPTY") } catch (error: B2Exception) { assertEquals(B2Failure.NOT_EMPTY, error.failure) }
        assertEquals(before, snapshot())
    }
    @Test fun unknownStoreEntryBlocks() { complete(); File(inventory.store, "unknown").writeBytes(byteArrayOf(7)); blocked() }
    @Test fun emptyRoomLockIsKnownTechnicalArtifact() { complete(); File(inventory.store, "d.lck").createNewFile(); assertEquals(BootstrapState.COMPLETE, inventory.state()) }
    @Test fun nonemptyRoomLockIsNeverTruncatedAsRepair() { complete(); File(inventory.store, "d.lck").writeBytes(byteArrayOf(7)); blocked() }
    @Test fun directDoesNotAcceptRoomLockArtifact() {
        complete(); File(inventory.store, "d.lck").createNewFile()
        assertEquals(BootstrapState.BLOCKED, B2Inventory(root, { keys }, B2Variant.DIRECT).state())
    }
    @Test fun completeWithLostKeyIsBlocked() { complete(); keys = BootstrapState.BLOCKED; blocked() }
    @Test fun expiredFinishCannotWriteCompletion() {
        pending(); inventory.store.mkdir(); inventory.database.writeBytes(byteArrayOf(7))
        val expired = object : StorageContext by context { override fun check(point: AccessPoint) = false }
        try { inventory.finish(UUID.randomUUID().toString(), expired); fail("B2-EXPIRED") } catch (error: B2Exception) { assertEquals(B2Failure.SESSION, error.failure) }
        assertFalse(File(root, "1").exists()); blocked()
    }
}
