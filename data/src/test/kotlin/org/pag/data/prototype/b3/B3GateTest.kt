package org.pag.data.prototype.b3

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom

class B3GateTest {
    private var time = 0L
    private var environment = true
    private var callback: ((AuthenticationOutcome) -> Unit)? = null
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callback = complete; return Cancellation {}
        }
    }, Maintenance { Subscription {} }, MonotonicTime { time }, SessionPolicy(100, 20), { environment })
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private fun context(): StorageContext {
        session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        callback!!(AuthenticationOutcome.Granted(operation(), time))
        return session.acquire(AccessScope.MAINTENANCE)!!
    }
    private fun operation() = object : SyntheticOperation {
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() {}
        override fun useUnwrappedKey(allowed: () -> Boolean, consume: (ByteArray) -> Unit): OperationResult {
            if (!allowed()) return OperationResult.NOT_AUTHORIZED
            val copy = key.copyOf()
            try { consume(copy); return if (allowed()) OperationResult.SUCCEEDED else OperationResult.NOT_AUTHORIZED }
            catch (_: Exception) { return OperationResult.FAILED }
            finally { copy.fill(0) }
        }
    }
    private fun inventory(): B3Inventory {
        val root = Files.createTempDirectory(File("build").also { it.mkdirs() }.toPath(), "b3-gate-").toFile().canonicalFile
        var keyState = BootstrapState.EMPTY
        return B3Inventory(root, { keyState }).also { it.reserve(); File(root, "k").mkdir(); keyState = BootstrapState.COMPLETE }
    }
    @Test fun initAndReopenSameCiphertextWithFreshContext() {
        val inventory = inventory()
        assertEquals(B3Id.PUBLISH, B3StorageGate(inventory, length = 1048701).run(context(), operation(), true))
        val before = inventory.root.walkTopDown().filter { it.isFile }.associate { it.name to it.readBytes().toList() }
        session.lock()
        assertEquals(B3Id.RESTART, B3StorageGate(inventory).run(context(), operation(), false))
        assertEquals(before, inventory.root.walkTopDown().filter { it.isFile }.associate { it.name to it.readBytes().toList() })
    }
    @Test fun deadlineAtEveryWorkBoundaryNeverPublishes() {
        B3Point.entries.filter { it != B3Point.READ }.forEach { point ->
            time = 0; session.lock(); val context = context(); val inventory = inventory()
            val result = B3StorageGate(inventory, { if (it == point) time = 100 }, 1024).run(context, operation(), true)
            assertEquals(point.name, B3Id.EXPIRED, result)
            assertFalse(point.name, inventory.manifest.exists())
            assertEquals(SessionState.LOCKED, session.snapshot().state)
        }
    }
    @Test fun generationChangeAtEveryBoundaryNeverPublishes() {
        B3Point.entries.filter { it != B3Point.READ }.forEach { point ->
            time = 0; session.lock(); val old = context(); val inventory = inventory()
            var revoked = false
            val result = B3StorageGate(inventory, { if (it == point && !revoked) { revoked = true; session.lock(); context() } }, 1024).run(old, operation(), true)
            assertEquals(point.name, B3Id.EXPIRED, result)
            assertFalse(inventory.manifest.exists()); assertFalse(old.check(AccessPoint.STEP))
            assertTrue(session.acquire(AccessScope.MAINTENANCE)!!.check(AccessPoint.STEP))
        }
    }
    @Test fun foregroundAndDisplayLockDenyFurtherWork() {
        listOf(false, true).forEach { display ->
            time = 0; environment = true; session.lock(); val old = context(); val inventory = inventory()
            val result = B3StorageGate(inventory, { if (it == B3Point.ENCRYPT) {
                if (display) environment = false else session.foreground(false)
            } }, 1024).run(old, operation(), true)
            assertEquals(B3Id.EXPIRED, result); assertFalse(inventory.manifest.exists())
        }
    }
    @Test fun oldStreamOwnerCannotUseNewGeneration() {
        val old = context()
        val stream = B3Streams(FileStreamingCipher.fromUnwrappedKey(key), old)
        session.lock(); context()
        try { stream.encrypt(B3Source(1), java.io.ByteArrayOutputStream(), byteArrayOf(1)); fail("B3-OLD-STREAM") }
        catch (error: B3Exception) { assertEquals(B3Id.EXPIRED, error.id) }
        finally { stream.close() }
    }
}
