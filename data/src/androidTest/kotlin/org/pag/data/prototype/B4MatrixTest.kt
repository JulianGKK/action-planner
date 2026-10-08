package org.pag.data.prototype

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.b3.*
import org.pag.data.prototype.b4.*
import org.pag.data.prototype.b2.B2Hooks
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/** Actual Room/SQLCipher + Tink. Authentication and key consumers are explicitly synthetic.
 * PROCESS_MODEL discards owners after an injected Error; finally still runs. Not an OS kill.
 */
@RunWith(Parameterized::class)
class B4MatrixTest(private val point: B4Point, private val mode: B4Fault) {
    private val fixture = B4TestFixture()
    @After fun releaseKeys() = fixture.close()

    @Test fun faultRestartAndIdempotence() {
        fixture.run(creates = true) { _, _ -> }
        val deleting = point.ordinal >= B4Point.DELETE_BEFORE_JOB.ordinal
        if (deleting) fixture.run { protocol, _ -> assertEquals(B4Id.IMPORTED, protocol.importSynthetic(B4TestFixture.LENGTH)) }
        val old = fixture.context
        var hit = 0
        val fault: (B4Point) -> Unit = { current ->
            if (current == point) {
                hit++
                // Interrupt after one actual source chunk rather than only before any data.
                if (current != B4Point.DURING_WRITE || hit == 2) when (mode) {
                    B4Fault.EXCEPTION -> throw B4TestFault()
                    B4Fault.PROCESS_MODEL -> throw B4ProcessModel()
                    B4Fault.EXPIRY -> fixture.time = 100
                    B4Fault.GENERATION -> fixture.freshSession()
                }
            }
        }
        val failure = runCatching { fixture.run(fault = fault) { protocol, _ ->
            if (deleting) protocol.deleteSynthetic() else protocol.importSynthetic(B4TestFixture.LENGTH)
        } }.exceptionOrNull()
        assertNotNull("B4-FAULT-MUST-INTERRUPT", failure)
        assertTrue("B4-FAULT-POINT-REACHED", hit > 0)
        if (mode == B4Fault.EXPIRY || mode == B4Fault.GENERATION)
            assertFalse("B4-OLD-CONTEXT", old.check(AccessPoint.PUBLICATION))
        fixture.freshSession()
        val before = fixture.ciphertext()
        val blocked = point in setOf(B4Point.BEFORE_STAGING_CREATE, B4Point.DURING_WRITE)
        if (blocked) {
            lateinit var state: B4Snapshot
            fixture.run { _, store -> state = store.snapshot(); assertTrue(state.assets.isEmpty()) }
            repeat(2) {
                val error = runCatching { fixture.run { protocol, _ -> protocol.reconcile() } }.exceptionOrNull()
                assertTrue("B4-INCOMPLETE-BLOCKED", error is B4Exception)
                fixture.run { _, store -> assertEquals(state, store.snapshot()) }
                assertEquals(before, fixture.ciphertext())
            }
        } else {
            fixture.run { protocol, _ ->
                val expected = when {
                    point == B4Point.IMPORT_BEFORE_JOB || point == B4Point.DELETE_BEFORE_JOB -> B4Id.RESTART
                    point == B4Point.IMPORT_AFTER_JOB -> B4Id.ABORTED
                    deleting -> B4Id.DELETE_RECOVERED
                    else -> B4Id.IMPORT_RECOVERED
                }
                assertEquals(expected, protocol.reconcile())
            }
            lateinit var completed: B4Snapshot
            fixture.run { _, store -> completed = store.snapshot(); assertTrue(completed.jobs.isEmpty()) }
            val expectedAssets = if (deleting) {
                if (point == B4Point.DELETE_BEFORE_JOB) 1 else 0
            } else if (point in setOf(B4Point.IMPORT_BEFORE_JOB, B4Point.IMPORT_AFTER_JOB)) 0 else 1
            assertEquals(expectedAssets, completed.assets.size)
            val ciphertext = fixture.ciphertext()
            repeat(2) {
                fixture.freshSession()
                fixture.run { protocol, store ->
                    assertEquals(B4Id.RESTART, protocol.reconcile())
                    assertEquals(completed, store.snapshot())
                }
                assertEquals(ciphertext, fixture.ciphertext())
            }
            // Publish moves existing bytes; never regenerates them.
            if (!deleting && expectedAssets == 1) assertEquals(before.values.toList(), ciphertext.values.toList())
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-{1}")
        fun cases(): List<Array<Any>> = B4Point.entries.flatMap { point ->
            B4Fault.entries.map { mode -> arrayOf<Any>(point, mode) }
        }
    }
}

enum class B4Fault { EXCEPTION, PROCESS_MODEL, EXPIRY, GENERATION }
internal class B4TestFault : RuntimeException("B4-INJECTED-FAULT")
internal class B4ProcessModel : Error("B4-PROCESS-LOSS-MODEL")

/** Test-only keys survive owner reconstruction in this test process, never on disk.
 * This is NOT a Keystore, credential or real process-restart proof.
 */
internal class B4TestFixture : AutoCloseable {
    val app = InstrumentationRegistry.getInstrumentation().targetContext
    val root = File(app.noBackupFilesDir.canonicalFile, "b4-test-${UUID.randomUUID()}").also { check(it.mkdir()) }
    val files = File(root, "f").also { check(it.mkdir()) }
    val db = File(File(root, "q").also { check(it.mkdir()) }, "d")
    private val dbKey = B4TestKeyConsumer()
    private val fileKey = B4TestKeyConsumer()
    var time = 0L
    var environment = true
    private var callback: ((AuthenticationOutcome) -> Unit)? = null
    var maintenanceDone: ((Boolean) -> Unit)? = null
    val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callback = complete; return Cancellation {}
        }
    }, Maintenance { maintenanceDone = it; Subscription {} }, MonotonicTime { time }, SessionPolicy(100, 20), { environment })
    lateinit var context: StorageContext
        private set
    init {
        check(File(files, "s").mkdir()); check(File(files, "f").mkdir()); freshSession()
    }
    fun freshSession() {
        session.lock(); time = 0; environment = true; session.foreground(true)
        session.authenticate(Experiment.PER_OPERATION)
        callback!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() {}
        }, time))
        context = session.acquire(AccessScope.MAINTENANCE)!!
    }
    fun run(creates: Boolean = false, fault: (B4Point) -> Unit = {}, hooks: B2Hooks = B2Hooks(),
        wrongDbKey: Boolean = false, wrongFileKey: Boolean = false, work: (B4Protocol, B4Store) -> Unit) {
        val access = context // Capture this generation, not the fixture's possibly replaced context.
        fileKey.consume(access) { fileBytes ->
            if (wrongFileKey) fileBytes[0] = (fileBytes[0].toInt() xor 1).toByte()
            val consumer = B4DbKeyConsumer { work -> dbKey.consume(access) { bytes ->
                if (wrongDbKey) bytes[0] = (bytes[0].toInt() xor 1).toByte()
                work(bytes)
            } }
            B4StepStore(app, db, access, consumer, driverHooks = hooks).use { store ->
                if (creates) store.initializeEmpty()
                B3Streams(FileStreamingCipher.fromUnwrappedKey(fileBytes), access, B4Protocol.validationHook(access, fault)).use { streams ->
                    work(B4Protocol(store, files, streams, access, fault), store)
                }
            }
        }
    }
    /** Blocker-only fixture seam; no secrets or DAO/connection escape to app/UI. */
    fun room(creates: Boolean = false, hooks: B4RoomHooks = B4RoomHooks(), work: (B4RoomStore) -> Unit) {
        val access = context
        dbKey.consume(access) { bytes ->
            B4RoomStore.open(app, db, bytes, access, creates, roomHooks = hooks).use(work)
        }
    }
    fun ciphertext(): Map<String, List<Byte>> = files.walkTopDown().filter { it.isFile }
        .associate { it.name to it.readBytes().toList() }
    override fun close() { session.lock(); dbKey.close(); fileKey.close() }
    companion object { const val LENGTH = 65537L }
}

private class B4TestKeyConsumer : AutoCloseable {
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var closed = false
    fun consume(context: StorageContext, work: (ByteArray) -> Unit) {
        check(!closed); context.requireB4(AccessPoint.OPERATION)
        val bytes = key.copyOf()
        try { work(bytes); context.requireB4(AccessPoint.PUBLICATION) } finally { bytes.fill(0) }
    }
    override fun close() { closed = true; key.fill(0) }
}
