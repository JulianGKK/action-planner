package org.pag.data.prototype.b3

import org.junit.Assert.*
import org.junit.Test
import org.pag.security.BootstrapState
import java.io.File
import java.nio.file.Files
import java.util.UUID

class B3InventoryTest {
    private val root = Files.createTempDirectory(File("build").also { it.mkdirs() }.toPath(), "b3-").toFile().canonicalFile
    private var keys = BootstrapState.EMPTY
    private val inventory = B3Inventory(root, { keys })
    private fun pending() { inventory.reserve(); File(root, "k").mkdir(); keys = BootstrapState.COMPLETE }
    private fun complete() { pending(); val file = inventory.staging(); file.writeBytes(byteArrayOf(7)); inventory.manifest.writeBytes(byteArrayOf(7)) }
    private fun snapshot() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }
    private fun blocked() {
        assertEquals(B3State.BLOCKED, inventory.state()); val old = snapshot()
        try { inventory.reserve(); fail("B3-REPAIR") } catch (error: B3Exception) { assertEquals(B3Id.BLOCKED, error.id) }
        assertEquals(old, snapshot())
    }
    @Test fun emptyNeedsExplicitReserve() { assertEquals(B3State.EMPTY, inventory.state()); inventory.reserve(); blocked() }
    @Test fun keyOnlyNeverMeansComplete() { pending(); inventory.keyReady(); blocked() }
    @Test fun aliasWithoutFilesBlocks() { keys = BootstrapState.BLOCKED; blocked() }
    @Test fun unknownFileBlocksUnchanged() { File(root, "unknown").writeBytes(byteArrayOf(9)); blocked() }
    @Test fun missingManifestBlocksStaging() { pending(); inventory.staging().writeBytes(byteArrayOf(9)); blocked() }
    @Test fun completeCandidateIsNotCryptographicProofAndCannotReinitialize() {
        complete(); assertEquals(B3State.COMPLETE_CANDIDATE, inventory.state()); val old = snapshot()
        try { inventory.reserve(); fail("B3-REINIT") } catch (error: B3Exception) { assertEquals(B3Id.NOT_EMPTY, error.id) }
        assertEquals(old, snapshot())
    }
    @Test fun missingKeyBlocksExistingAsset() { complete(); keys = BootstrapState.BLOCKED; blocked() }
    @Test fun secondAssetBlocksWithoutCleanup() { complete(); File(inventory.assets, UUID.randomUUID().toString()).writeBytes(byteArrayOf(9)); blocked() }
    @Test fun unknownAssetFilenameBlocks() { complete(); File(inventory.assets, "unknown").writeBytes(byteArrayOf(9)); blocked() }
    @Test fun symlinkBlocked() {
        complete(); Files.createSymbolicLink(File(root, "link").toPath(), inventory.manifest.toPath()); blocked()
    }
}
