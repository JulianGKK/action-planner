package org.pag.data.prototype.b4

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.b3.*
import org.pag.data.prototype.b4.*
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/** In-memory job store + real Tink/filesystem. No Room or credential evidence.
 * PROCESS_MODEL discards owners after an injected Error; finally still runs. Not an OS kill.
 */
@RunWith(Parameterized::class)
class B4ProtocolTest(private val point: B4Point, private val mode: B4Fault) {
    private val fixture = B4MemoryFixture()
    @After fun releaseKeys() = fixture.close()

    @Test fun faultRestartAndIdempotence() {
        fixture.run(creates = true) { _, _ -> }
        val deleting = point.ordinal >= B4Point.DELETE_BEFORE_JOB.ordinal
        if (deleting) fixture.run { protocol, _ -> assertEquals(B4Id.IMPORTED, protocol.importSynthetic(B4MemoryFixture.LENGTH)) }
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
            if (deleting) protocol.deleteSynthetic() else protocol.importSynthetic(B4MemoryFixture.LENGTH)
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

internal class B4MemoryFixture : AutoCloseable {
    private val root = java.nio.file.Files.createTempDirectory(File("build").also { it.mkdirs() }.toPath(), "b4-unit-").toFile().canonicalFile
    val files = root
    private val fileKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var state = B4Snapshot(emptyList(), emptyList())
    var time = 0L
    private var callback: ((AuthenticationOutcome) -> Unit)? = null
    val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            callback = complete; return Cancellation {}
        }
    }, Maintenance { Subscription {} }, MonotonicTime { time }, SessionPolicy(100, 20))
    lateinit var context: StorageContext
        private set
    init { check(File(files, "s").mkdir()); check(File(files, "f").mkdir()); freshSession() }
    fun freshSession() {
        session.lock(); time = 0; session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        callback!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.SUCCEEDED
            override fun release() {}
        }, time))
        context = session.acquire(AccessScope.MAINTENANCE)!!
    }
    fun run(creates: Boolean = false, fault: (B4Point) -> Unit = {}, work: (B4Protocol, B4Store) -> Unit) {
        val access = context
        val store = object : B4Store {
            private var closed = false
            private fun checked() { check(!closed); access.requireB4() }
            private fun change(job: B4Job? = null, transform: (B4Snapshot) -> B4Snapshot) {
                checked()
                if (job != null) check(state.jobs.singleOrNull() == job)
                val next = transform(state); access.requireB4(AccessPoint.COMMIT); state = next
            }
            override fun snapshot(): B4Snapshot { checked(); return state }
            override fun reserve(job: B4Job) = change { check(it.jobs.isEmpty()); it.copy(jobs = listOf(job)) }
            override fun advance(job: B4Job, next: B4Phase) = change(job) { it.copy(jobs = listOf(job.next(next))) }
            override fun commitAsset(job: B4Job) = change(job) {
                it.copy(jobs = listOf(job.next(B4Phase.METADATA_COMMITTED)), assets = it.assets + B4Asset(job.asset, job.file, job.length))
            }
            override fun hide(job: B4Job) = change(job) {
                it.copy(jobs = listOf(job.next(B4Phase.METADATA_HIDDEN)), assets = it.assets.map { a -> if (a.id == job.asset) a.copy(hidden = true) else a })
            }
            override fun removeAsset(job: B4Job) = change(job) {
                it.copy(jobs = listOf(job.next(B4Phase.METADATA_REMOVED)), assets = it.assets.filter { a -> a.id != job.asset })
            }
            override fun finish(job: B4Job) = change(job) { it.copy(jobs = emptyList()) }
            override fun close() { closed = true }
        }
        store.use {
            B3Streams(FileStreamingCipher.fromUnwrappedKey(fileKey), access, B4Protocol.validationHook(access, fault)).use { streams ->
                work(B4Protocol(store, files, streams, access, fault), store)
            }
        }
    }
    fun ciphertext(): Map<String, List<Byte>> = files.walkTopDown().filter { it.isFile }.associate { it.name to it.readBytes().toList() }
    override fun close() { session.lock(); fileKey.fill(0) }
    companion object { const val LENGTH = 65537L }
}
