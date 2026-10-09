package org.pag.data.product

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/** Isolated synthetic prerequisite benchmark. Never logs inputs, salts or derived bytes. */
class LeadershipKdfBenchmarkTest {
    @Test fun minimumArgon2idOnDevice() {
        val password = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val samples = mutableListOf<Long>()
        try {
            repeat(6) { index ->
                val output = ByteArray(32)
                val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withMemoryAsKB(65536).withIterations(3).withParallelism(1).withSalt(salt).build()
                val started = SystemClock.elapsedRealtimeNanos()
                try {
                    Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, output)
                    val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
                    if (index > 0) samples.add(elapsed)
                } finally { output.fill(0); parameters.clear() }
            }
            assertTrue(samples.size == 5)
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("p3b_kdf_parameters", "Argon2id/v19 m=65536KiB t=3 p=1 output=32B")
                putString("p3b_kdf_ms", samples.joinToString(","))
                putString("p3b_kdf_median_ms", samples.sorted()[2].toString())
            })
        } finally { password.fill(0); salt.fill(0) }
    }
}
