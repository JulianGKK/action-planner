package org.pag.data.product

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.pag.security.AuthenticationExperiment
import org.pag.security.Cancellation
import java.io.File

class ProductRuntimeTestAuthorityGuardTest {
    private val authority = ProductRuntimeTestAuthority(
        noBackupRoot = File("/isolated-test-only"),
        authentication = object : AuthenticationExperiment {
            override fun start(mode: org.pag.security.KeyExperiment,
                complete: (org.pag.security.AuthenticationOutcome) -> Unit): Cancellation = Cancellation {}
        },
        aliasPresent = { true },
        envelopeValid = { true },
        reuseEnabled = true
    )

    @Test fun p77AuthorityRequiresExactIsolatedDebugIdentity() {
        assertSame(authority, requireP77TestAuthority(
            "org.pag.prototype.a.p77runtime", ApplicationInfo.FLAG_DEBUGGABLE, authority
        ))
        assertThrows(IllegalStateException::class.java) {
            requireP77TestAuthority("org.pag.prototype.a", ApplicationInfo.FLAG_DEBUGGABLE, authority)
        }
        assertThrows(IllegalStateException::class.java) {
            requireP77TestAuthority("org.pag.prototype.a.p77runtime", 0, authority)
        }
    }
}
