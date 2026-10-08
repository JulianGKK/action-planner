package org.pag.data.prototype.b2

import org.junit.Test
import org.junit.Assert.*
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import java.security.SecureRandom

class B2KeyContractTest {
    private var time = 0L
    private var allowedEnvironment = true
    private lateinit var auth: (AuthenticationOutcome) -> Unit
    private lateinit var maintenance: (Boolean) -> Unit
    private val session = SessionCoordinator(object : AuthenticationExperiment {
        override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
            auth = complete; return Cancellation {}
        }
    }, Maintenance { maintenance = it; Subscription {} }, MonotonicTime { time }, SessionPolicy(100, 20), { allowedEnvironment })
    private var unwrapped = 0
    private var releases = 0
    private var copy: ByteArray? = null
    private val operation = object : SyntheticOperation {
        override fun perform() = OperationResult.NOT_AUTHORIZED
        override fun release() { releases++ }
        override fun useUnwrappedKey(allowed: () -> Boolean, consume: (ByteArray) -> Unit): OperationResult {
            if (!allowed()) return OperationResult.NOT_AUTHORIZED
            unwrapped++
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }; copy = bytes
            return try { consume(bytes); if (allowed()) OperationResult.SUCCEEDED else OperationResult.NOT_AUTHORIZED }
            catch (_: B2Exception) { OperationResult.FAILED }
            finally { bytes.fill(0) }
        }
    }
    private fun context(): StorageContext {
        session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
        auth(AuthenticationOutcome.Granted(object : SyntheticOperation {
            override fun perform() = OperationResult.NOT_AUTHORIZED
            override fun release() {}
        }, time))
        return session.acquire(AccessScope.MAINTENANCE)!!
    }
    private fun rejects(work: () -> Unit) {
        try { work(); fail("B2-EXPECTED-SESSION-REJECTION") }
        catch (error: B2Exception) { assertEquals(B2Failure.SESSION, error.failure) }
    }
    @Test fun authorizedConsumerReturnsOnlyTechnicalResultAndReleases() {
        assertEquals(7, useB2Key(operation, context()) { 7 }); assertEquals(1, unwrapped); assertEquals(1, releases)
        assertTrue(copy!!.all { it == 0.toByte() })
    }
    @Test fun expiredBeforeUnwrapNeverInvokesConsumer() {
        val access = context(); time = 100; rejects { useB2Key(operation, access) { fail("B2-UNWRAP-LEAK") } }; assertEquals(0, unwrapped)
    }
    @Test fun expiryDuringConsumerCannotPublishResult() {
        rejects { useB2Key(operation, context()) { time = 100; 7 } }; assertEquals(1, releases); assertTrue(copy!!.all { it == 0.toByte() })
    }
    @Test fun lockDuringConsumerRevokesWithoutLongCoordinatorMonitor() {
        rejects { useB2Key(operation, context()) { session.lock(); 7 } }; assertEquals(SessionState.LOCKED, session.snapshot().state)
    }
    @Test fun oldGenerationCannotUseKeyAfterReauth() {
        val old = context(); session.lock(); context(); rejects { useB2Key(operation, old) { 7 } }; assertEquals(0, unwrapped)
    }
    @Test fun generationChangeDuringConsumerRejectsResult() {
        rejects { useB2Key(operation, context()) { session.lock(); context(); 7 } }; assertEquals(1, releases)
    }
    @Test fun foregroundLossRejectsKeyUse() {
        val access = context(); session.foreground(false); rejects { useB2Key(operation, access) { 7 } }; assertEquals(0, unwrapped)
    }
    @Test fun displayLockEnvironmentRejectsKeyUse() {
        val access = context(); allowedEnvironment = false; rejects { useB2Key(operation, access) { 7 } }; assertEquals(0, unwrapped)
    }
    @Test fun readyPermitIsNotMaintenancePermit() {
        context(); maintenance(true); val access = session.acquire()!!
        rejects { useB2Key(operation, access) { 7 } }; assertEquals(0, unwrapped)
    }
    @Test fun maintenancePermitExpiresOnReadyTransition() {
        val access = context(); maintenance(true); rejects { useB2Key(operation, access) { 7 } }
    }
    @Test fun commitAndPublicationBothUseCurrentDeadline() {
        val access = context(); assertTrue(access.check(AccessPoint.COMMIT)); time = 100
        assertFalse(access.check(AccessPoint.COMMIT)); assertFalse(access.publish { fail("B2-PUBLISH-LEAK") })
    }
    @Test fun maintenanceWorkDoesNotExtendAbsoluteDeadline() {
        val access = context(); time = 99; session.userInteraction(); assertTrue(access.check(AccessPoint.STEP)); time = 100
        assertFalse(access.check(AccessPoint.STEP))
    }
}
