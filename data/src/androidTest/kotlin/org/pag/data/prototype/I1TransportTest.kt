package org.pag.data.prototype

import android.net.LocalSocket
import android.net.LocalSocketAddress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.b3.*
import org.pag.data.prototype.b4.*
import org.pag.domain.*
import org.pag.security.*
import java.io.File

/** Test-only binary injection. No real credential or product key-restart claim. */
class I1TransportTest {
    private val args = InstrumentationRegistry.getArguments()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val sessionId = args.getString("i1_session") ?: "0".repeat(32)
    private val action = args.getString("i1_action") ?: "baseline"
    private val root get() = File(app.noBackupFilesDir.canonicalFile, "i1-$sessionId")

    internal fun receive(mode: Int = 0, session: String = sessionId, name: String = "pag_i1"): ByteArray {
        var packet: ByteArray? = null
        var stage = "CONNECT"
        try {
            require(session.matches(Regex("[0-9a-f]{32}")))
            LocalSocket().use { socket ->
                socket.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                socket.soTimeout = 5000
                stage = "WRITE"
                socket.outputStream.write("I1K1".toByteArray(Charsets.US_ASCII))
                socket.outputStream.write(session.toByteArray(Charsets.US_ASCII))
                socket.outputStream.write(mode)
                stage = "READ"
                val response = ByteArray(100)
                packet = response
                var offset = 0
                while (offset < response.size) {
                    val n = socket.inputStream.read(response, offset, response.size - offset)
                    check(n > 0)
                    offset += n
                }
                stage = "LENGTH"
                check(socket.inputStream.read() == -1)
                stage = "PROTOCOL"
                check(response.copyOfRange(0, 4).contentEquals("I1K1".toByteArray(Charsets.US_ASCII)))
                check(response.copyOfRange(4, 36).contentEquals(session.toByteArray(Charsets.US_ASCII)))
                return response.copyOfRange(36, 100)
            }
        } catch (_: Exception) {
            throw IllegalStateException("I1-TRANSPORT-BLOCKED-$stage")
        } finally { packet?.fill(0) }
    }

    @Test fun transportSpike() {
        require(sessionId.matches(Regex("[0-9a-f]{32}")))
        if (action == "baseline") {
            // Unfiltered regression: absence of explicit host context cannot bootstrap.
            denied { receive(name = "pag_i1_absent") }
            return
        }
        if (action == "prepare" || action == "recover") {
            val keys = receive()
            try { I1Scenario(app, sessionId, requireNotNull(args.getString("i1_case")), keys).run(action) }
            finally { keys.fill(0) }
            return
        }
        if (action == "negative" || action == "lost") {
            val before = inventory()
            if (action == "lost") denied { receive() }
            else {
                denied { receive(name = "pag_i1_absent") }
                denied { receive(session = "0".repeat(32)) }
                for (mode in 1..3) denied { receive(mode) }
                val first = receive()
                try {
                    val second = receive()
                    try { assertTrue("I1-SAME-KEYS", first.contentEquals(second)) }
                    finally { second.fill(0) }
                } finally { first.fill(0) }
            }
            assertEquals("I1-NO-MUTATION", before, inventory())
            return
        }
        require(action == "create" || action == "open")
        // Receive before any filesystem mutation. Transport denial cannot initialize.
        val keys = receive()
        var callback: ((AuthenticationOutcome) -> Unit)? = null
        val coordinator = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                callback = complete; return Cancellation {}
            }
        }, Maintenance { Subscription {} }, MonotonicTime { 0L }, SessionPolicy(100, 20), { true })
        try {
            coordinator.foreground(true)
            coordinator.authenticate(Experiment.PER_OPERATION)
            callback!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
                override fun perform() = OperationResult.SUCCEEDED
                override fun release() {}
            }, 0))
            val access = coordinator.acquire(AccessScope.MAINTENANCE)!!
            if (action == "create") {
                check(!root.exists())
                check(root.mkdir()); check(File(root, "q").mkdir())
                check(File(root, "f").mkdir()); check(File(root, "f/s").mkdir()); check(File(root, "f/f").mkdir())
            } else check(root.isDirectory && File(root, "q/d").isFile)
            val dbConsumer = B4DbKeyConsumer { work ->
                check(access.check(AccessPoint.OPERATION))
                val copy = keys.copyOfRange(0, 32)
                try { work(copy) } finally { copy.fill(0) }
            }
            val fileKey = keys.copyOfRange(32, 64)
            try {
                B4StepStore(app, File(root, "q/d"), access, dbConsumer).use { store ->
                    if (action == "create") store.initializeEmpty()
                    B3Streams(FileStreamingCipher.fromUnwrappedKey(fileKey), access).use { streams ->
                        val protocol = B4Protocol(store, File(root, "f"), streams, access)
                        if (action == "create") assertEquals(B4Id.IMPORTED, protocol.importSynthetic(65537))
                        val snapshot = store.snapshot()
                        assertEquals(1, snapshot.assets.size); assertTrue(snapshot.jobs.isEmpty())
                        val cipherBefore = File(root, "f/f").listFiles()!!.associate { it.name to it.readBytes().toList() }
                        repeat(2) { assertEquals(B4Id.RESTART, protocol.reconcile()); assertEquals(snapshot, store.snapshot()) }
                        assertEquals(cipherBefore, File(root, "f/f").listFiles()!!.associate { it.name to it.readBytes().toList() })
                    }
                }
            } finally { fileKey.fill(0) }
        } finally { coordinator.lock(); keys.fill(0) }
    }
    private fun denied(work: () -> ByteArray) {
        try { val bytes = work(); bytes.fill(0); fail("I1-DENIAL-REQUIRED") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.startsWith("I1-TRANSPORT-BLOCKED-")) }
    }
    private fun inventory(): Map<String, List<Byte>> = if (!root.exists()) emptyMap() else
        root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes().toList() }
}
