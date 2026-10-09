package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.data.SessionCoordinator
import org.pag.security.*
import java.nio.file.Files

class ProductSecurityTest {
    private class Context(override val generation: Long = 1, override val scope: AccessScope = AccessScope.READY) : StorageContext {
        var allowed = true
        override fun check(point: AccessPoint) = allowed
        override fun publish(result: () -> Unit): Boolean { if (!allowed) return false; result(); return true }
    }
    @Test fun ownerAllowsMultipleConsumersButWipesControlledLeases() {
        val context = Context(); val key = ByteArray(32) { 7 }; val owner = SessionDbKeyOwner.bind(context, key)
        var reference: ByteArray? = null
        repeat(3) { owner.withDbKey(context) { assertArrayEquals(key, it); reference = it } }
        assertTrue(reference!!.all { it == 0.toByte() })
        owner.invalidate()
        assertThrows(ProductStorageFailure::class.java) { owner.withDbKey(context) {} }
    }
    @Test fun oldOwnerCannotReactivateForNewGeneration() {
        val owner = SessionDbKeyOwner.bind(Context(), ByteArray(32))
        assertThrows(ProductStorageFailure::class.java) { owner.withDbKey(Context(2)) {} }
        assertThrows(ProductStorageFailure::class.java) { owner.withDbKey(Context()) {} }
    }
    @Test fun keyPresenceDoesNotOverrideGate() {
        val context = Context(); val owner = SessionDbKeyOwner.bind(context, ByteArray(32)); context.allowed = false
        assertThrows(ProductStorageFailure::class.java) { owner.withDbKey(context) { fail("consumer must not run") } }
        context.allowed = true
        assertThrows(ProductStorageFailure::class.java) { owner.withDbKey(context) {} }
    }
    @Test fun revokedDuringConsumerDeniesResult() {
        val context = Context(); val owner = SessionDbKeyOwner.bind(context, ByteArray(32))
        assertThrows(ProductStorageFailure::class.java) { owner.withDbKey(context) { context.allowed = false } }
    }
    @Test fun cannotBindWithoutActualSessionContext() {
        assertThrows(ProductStorageFailure::class.java) { SessionDbKeyOwner.bind(Context(-1), ByteArray(32)) }
        val c = Context(); c.allowed = false
        assertThrows(ProductStorageFailure::class.java) { SessionDbKeyOwner.bind(c, ByteArray(32)) }
    }
    @Test fun inventoryNeverRepairsPartialStates() {
        val root = Files.createTempDirectory("pag-product-inventory").toFile()
        var alias = false
        val inv = ProductInventory(root, { alias }, { true })
        assertEquals(ProductStorageState.EMPTY, inv.state())
        assertTrue(root.list()!!.isEmpty())
        alias = true
        assertEquals(ProductStorageState.PARTIAL_OR_INVALID, inv.state())
        assertThrows(ProductStorageFailure::class.java) { inv.reserve() }
        assertTrue(root.list()!!.isEmpty())
        alias = false; root.resolve("envelope").writeBytes(byteArrayOf(1))
        assertEquals(ProductStorageState.PARTIAL_OR_INVALID, inv.state())
        assertThrows(ProductStorageFailure::class.java) { inv.reserve() }
        assertEquals(1L,root.resolve("envelope").length())
    }
    @Test fun completeInventoryAndUnknownEntry() {
        val root = Files.createTempDirectory("pag-product-complete").toFile()
        listOf("intent","complete").forEach { root.resolve(it).writeBytes(byteArrayOf(1)) }
        root.resolve("public").writeBytes(byteArrayOf(1)); root.resolve("envelope").writeBytes(ByteArray(256))
        root.resolve("store").mkdir(); root.resolve("store/d").writeBytes(byteArrayOf(1))
        val inv = ProductInventory(root, { true }, { true })
        assertEquals(ProductStorageState.READY_TO_OPEN, inv.state())
        root.resolve("store/d.lck").writeBytes(byteArrayOf())
        assertEquals(ProductStorageState.READY_TO_OPEN, inv.state())
        root.resolve("store/d.lck").writeBytes(byteArrayOf(1))
        assertEquals(ProductStorageState.PARTIAL_OR_INVALID, inv.state())
        root.resolve("unknown").writeBytes(byteArrayOf(1))
        assertEquals(ProductStorageState.PARTIAL_OR_INVALID, inv.state())
    }
    @Test fun actionUsesSpecifiedValidationAndStatuses() {
        assertFalse(NewAction("", "synthetic", 1,2).valid())
        assertFalse(NewAction("synthetic", "", 1,2).valid())
        assertFalse(NewAction("synthetic", "synthetic", 2,1).valid())
        assertTrue(NewAction("synthetic", "synthetic", 1,1).valid())
        assertEquals(listOf("DRAFT","PREPARATION","ACTIVE","COMPLETED","DELETION_PENDING"), ActionStatus.entries.map { it.name })
    }
}
