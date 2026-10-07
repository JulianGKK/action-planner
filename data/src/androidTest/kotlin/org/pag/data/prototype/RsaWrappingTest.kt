package org.pag.data.prototype

import android.app.KeyguardManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.pag.security.*
import java.io.File
import java.util.UUID
import android.os.Bundle
import javax.crypto.Cipher

/** No credential injection. Positive DEVICE_CREDENTIAL tests require the manual app flow. */
class RsaWrappingTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun diagnosePlatformOaepProviderLookupWithoutKeys() {
        val namedLookup = try {
            Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStore"); "AVAILABLE"
        } catch (failure: java.security.GeneralSecurityException) { failure.javaClass.simpleName }
        val platform = Cipher.getInstance("RSA/ECB/OAEPPadding")
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "B15-CIPHER-LOOKUP named=$namedLookup platform=${platform.algorithm}\n")
        })
        assertEquals("RSA/ECB/OAEPPadding", platform.algorithm)
    }
    private fun fixture(): RsaWrappingFixture {
        assumeTrue("B15-DEVICE-CREDENTIAL-MUST-EXIST", app.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val id = UUID.randomUUID().toString()
        return RsaWrappingFixture(File(app.noBackupFilesDir.canonicalFile, id), "pag.prototype.b15.test.$id")
    }
    @Test fun explicitInitializationPersistsOnlyCiphertextAndPublicInventory() {
        val fixture = fixture(); fixture.initializeNew { true }
        assertEquals(BootstrapState.COMPLETE, fixture.state())
        val attempt = fixture.prepare()
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "B15-KEY-POLICY ${attempt.properties}\n")
        })
        assertTrue(attempt.properties.contains("DEVICE_CREDENTIAL; PER_OPERATION"))
        val operation = attempt.operation(attempt.cipher)
        assertEquals(OperationResult.NOT_AUTHORIZED, operation.performAuthorized { false })
        operation.release()
        assertEquals(BootstrapState.COMPLETE, fixture.state())
    }
    @Test fun unlockedDeviceAloneDoesNotAuthorizePreparedUnwrap() {
        val fixture = fixture(); fixture.initializeNew { true }
        val prepared = fixture.prepare()
        val operation = prepared.operation(prepared.cipher)
        assertEquals("B15-NO-FRESH-PROMPT", OperationResult.NOT_AUTHORIZED, operation.performAuthorized { true })
        operation.release()
        assertEquals(BootstrapState.COMPLETE, fixture.state())
    }
    @Test fun completeInitializationHasDistinctDiagnosisAndLeavesFixtureUnchanged() {
        val fixture = fixture(); fixture.initializeNew { true }
        val before = fixture.directory.listFiles()!!.associate { it.name to it.readBytes() }
        try {
            WrappingPreparation.run(KeyExperiment.STORAGE_INITIALIZE, fixture::checkedState,
                { fail("B15-NO-REPLACEMENT") }, { fail("B15-NO-PREPARE") })
            fail("B15-EXPECTED-NOT-EMPTY")
        } catch (failure: WrappingException) {
            assertEquals("B15-BOOTSTRAP-NOT-EMPTY", failure.failure.testId)
        }
        assertEquals(before.keys, fixture.directory.listFiles()!!.map { it.name }.toSet())
        before.forEach { (name, bytes) -> assertArrayEquals(bytes, File(fixture.directory, name).readBytes()) }
        val prepared = WrappingPreparation.run(KeyExperiment.STORAGE_UNWRAP, fixture::checkedState,
            { fail("B15-NO-INITIALIZATION") }, fixture::prepare)
        assertTrue(prepared.properties.contains("DEVICE_CREDENTIAL; PER_OPERATION"))
    }
    @Test fun missingWrappedKeyDoesNotCreateReplacement() {
        val fixture = fixture(); fixture.initializeNew { true }
        // Authorized mutation of a NEW exclusively synthetic fixture; never a repair path.
        assertTrue(File(fixture.directory, "1").delete())
        assertEquals(BootstrapState.BLOCKED, fixture.state())
        rejected { fixture.initializeNew { true } }; rejected { fixture.prepare() }
        assertFalse(File(fixture.directory, "1").exists())
    }
    @Test fun truncatedBlobRemainsUntouchedAndBlocked() {
        val fixture = fixture(); fixture.initializeNew { true }
        File(fixture.directory, "1").writeBytes(byteArrayOf(23))
        rejected { fixture.prepare() }; rejected { fixture.initializeNew { true } }
        assertArrayEquals(byteArrayOf(23), File(fixture.directory, "1").readBytes())
    }
    @Test fun wrongCipherCannotBeAcceptedAsAuthenticationContext() {
        val fixture = fixture(); fixture.initializeNew { true }
        val first = fixture.prepare(); val second = fixture.prepare()
        rejected { first.operation(second.cipher) }
    }
    private fun rejected(work: () -> Unit) {
        try { work(); fail("B15-EXPECTED-REJECTION") } catch (_: WrappingException) { }
    }
}
