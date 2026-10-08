package org.pag.data.prototype

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.json.JSONObject
import org.pag.data.SessionCoordinator
import org.pag.data.prototype.b3.*
import org.pag.data.prototype.b4.*
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch

/** I1 only: host-provided synthetic keys; never a product unlock or key hierarchy. */
internal class I1Scenario(private val app: Context, session: String, private val case: String,
    private val keys: ByteArray) {
    init { require(session.matches(Regex("[0-9a-f]{32}"))); require(case in ("A".."H") || case == "R") }
    private val root = File(app.noBackupFilesDir.canonicalFile, "i1-$session-$case")
    private val witness = File(root, "checkpoint")
    private val normal = File(root, "normal-cleanup")
    // Unknown to the B4 job, in its enclosing test namespace, outside DB/file inventories.
    private val sentinel = File(root, "7de66a2f-2d5a-4ad0-85ae-d75e36b1eb68")
    private val point get() = when(case) {
        "A" -> B4Point.IMPORT_AFTER_JOB; "B" -> B4Point.AFTER_STREAM_CLOSE
        "C", "R" -> B4Point.AFTER_RENAME; "D" -> B4Point.AFTER_METADATA_COMMIT
        "E" -> B4Point.DELETE_AFTER_JOB; "F" -> B4Point.AFTER_HIDE
        "G" -> B4Point.AFTER_UNLINK; else -> B4Point.AFTER_METADATA_REMOVE
    }
    private val phase get() = when(case) {
        "A" -> B4Phase.IMPORT_RESERVED; "B" -> B4Phase.STAGING_WRITING
        "C", "R" -> B4Phase.VALIDATED; "D" -> B4Phase.METADATA_COMMITTED
        "E" -> B4Phase.DELETE_RESERVED; "F" -> B4Phase.METADATA_HIDDEN
        "G" -> B4Phase.FILE_DELETE_PENDING; else -> B4Phase.METADATA_REMOVED
    }
    private fun hashes(): Map<String, String> = File(root, "f").walkTopDown().filter { it.isFile }
        .associate { it.name to hash(it) }
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        // Ciphertext/sentinel digest only. Never keys or plaintext content hashes.
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun run(action: String) {
        val preparing = action == "prepare"
        if (preparing) {
            check(!root.exists()); check(root.mkdir()); check(File(root, "q").mkdir())
            check(File(root, "f").mkdir()); check(File(root, "f/s").mkdir()); check(File(root, "f/f").mkdir())
            if (case in setOf("C", "F")) { check(sentinel.createNewFile()); sentinel.writeBytes(byteArrayOf(19, 27, 35)) }
        } else { check(root.isDirectory); check(witness.isFile); assertFalse("I1-NO-NORMAL-CLEANUP", normal.exists()) }
        var callback: ((AuthenticationOutcome) -> Unit)? = null
        val session = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                callback = complete; return Cancellation {}
            }
        }, Maintenance { Subscription {} }, MonotonicTime { SystemClock.elapsedRealtime() },
            SessionPolicy(120000, 20000), { true })
        try {
            session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
            callback!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
                override fun perform() = OperationResult.SUCCEEDED
                override fun release() {}
            }, SystemClock.elapsedRealtime()))
            val access = session.acquire(AccessScope.MAINTENANCE)!!
            val consumer = B4DbKeyConsumer { work ->
                check(access.check(AccessPoint.OPERATION)); val copy = keys.copyOfRange(0, 32)
                try { work(copy) } finally { copy.fill(0) }
            }
            val fileKey = keys.copyOfRange(32, 64)
            try {
                B4StepStore(app, File(root, "q/d"), access, consumer).use { store ->
                    if (preparing) store.initializeEmpty()
                    B3Streams(FileStreamingCipher.fromUnwrappedKey(fileKey), access).use { streams ->
                        if (preparing && case in ("E".."H"))
                            assertEquals(B4Id.IMPORTED, B4Protocol(store, File(root, "f"), streams, access).importSynthetic(65537))
                        val protocol = B4Protocol(store, File(root, "f"), streams, access, { reached ->
                            if (preparing && reached == point) {
                                val snapshot = store.snapshot(); val job = snapshot.jobs.single()
                                assertEquals(phase, job.phase)
                                if (case in setOf("C", "R")) assertTrue(snapshot.assets.isEmpty())
                                if (case == "F") assertTrue(snapshot.visibleAssets().isEmpty())
                                val data = JSONObject().put("job", job.id).put("asset", job.asset)
                                    .put("phase", job.phase.name).put("files", JSONObject(hashes()))
                                    .put("sentinel", if (sentinel.exists()) hash(sentinel) else "")
                                check(witness.createNewFile())
                                witness.outputStream().use { output -> output.write(data.toString().toByteArray()); output.fd.sync() }
                                check(access.check(AccessPoint.STEP))
                                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                                    putString("i1", "READY_FOR_FORCE_STOP"); putString("case", case)
                                    putInt("pid", Process.myPid()); putString("phase", job.phase.name)
                                })
                                // No exception substitute and no normal return; host terminates Android process.
                                CountDownLatch(1).await()
                                error("I1-UNEXPECTED-RESUME")
                            }
                        })
                        if (preparing) {
                            if (case in ("E".."H")) protocol.deleteSynthetic() else protocol.importSynthetic(65537)
                            fail("I1-CHECKPOINT-NOT-REACHED")
                        } else {
                            val data = JSONObject(witness.readText())
                            val initial = store.snapshot(); val job = initial.jobs.single()
                            assertEquals(data.getString("job"), job.id); assertEquals(data.getString("asset"), job.asset)
                            assertEquals(data.getString("phase"), job.phase.name)
                            if (case == "F") assertTrue(initial.visibleAssets().isEmpty())
                            val before = hashes(); val expectedFiles = data.getJSONObject("files")
                            assertEquals(expectedFiles.keys().asSequence().associateWith { expectedFiles.getString(it) }, before)
                            val result = protocol.reconcile()
                            assertEquals(when { case == "A" -> B4Id.ABORTED; case in ("E".."H") -> B4Id.DELETE_RECOVERED; else -> B4Id.IMPORT_RECOVERED }, result)
                            val completed = store.snapshot(); assertTrue(completed.jobs.isEmpty())
                            if (case == "A" || case in ("E".."H")) { assertTrue(completed.assets.isEmpty()); assertTrue(hashes().isEmpty()) }
                            else {
                                assertEquals(1, completed.assets.size); assertEquals(job.asset, completed.assets.single().id)
                                assertEquals(before.values.sorted(), hashes().values.sorted())
                            }
                            val after = hashes()
                            assertEquals(B4Id.RESTART, protocol.reconcile()); assertEquals(completed, store.snapshot()); assertEquals(after, hashes())
                            if (data.getString("sentinel").isNotEmpty()) assertEquals(data.getString("sentinel"), hash(sentinel))
                            check(access.check(AccessPoint.PUBLICATION))
                        }
                    }
                }
            } finally { fileKey.fill(0) }
        } finally {
            session.lock()
            if (preparing) normal.writeText("I1-NORMAL-CLEANUP") // Non-secret evidence, never a security grant.
        }
    }
}
