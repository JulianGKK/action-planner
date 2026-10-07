package org.pag.data

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.security.*

/** Same preparation dispatcher and stage wrapper as the Android adapter; no platform claims. */
class WrappingDiagnosisTest {
    private fun rejected(expected: WrappingFailure, work: () -> Unit) {
        try { work(); fail("B15-EXPECTED-FAIL-CLOSED") }
        catch (failure: WrappingException) {
            assertEquals(expected, failure.failure)
            assertEquals(expected.testId, failure.message)
            assertNull(failure.cause)
        }
    }
    @Test fun initializeEmptyCallsInitializeThenPrepare() {
        val calls = mutableListOf<String>()
        assertEquals(7, WrappingPreparation.run(KeyExperiment.STORAGE_INITIALIZE,
            { calls.add("inventory"); BootstrapState.EMPTY }, { calls.add("initialize") },
            { calls.add("prepare"); 7 }))
        assertEquals(listOf("inventory", "initialize", "prepare"), calls)
    }
    @Test fun initializeCompleteDoesNotInitializeOrPrepare() {
        rejected(WrappingFailure.NOT_EMPTY) {
            WrappingPreparation.run(KeyExperiment.STORAGE_INITIALIZE, { BootstrapState.COMPLETE },
                { fail("B15-NO-REPLACEMENT") }, { fail("B15-NO-PREPARE") })
        }
    }
    @Test fun unwrapCompleteNeverInitializes() {
        assertEquals(9, WrappingPreparation.run(KeyExperiment.STORAGE_UNWRAP,
            { BootstrapState.COMPLETE }, { fail("B15-NO-INITIALIZATION") }, { 9 }))
    }
    @Test fun emptyOrBlockedUnwrapAndBlockedInitializationRejectBeforeWork() {
        for (mode in listOf(KeyExperiment.STORAGE_INITIALIZE, KeyExperiment.STORAGE_UNWRAP)) {
            val states = if (mode == KeyExperiment.STORAGE_UNWRAP)
                listOf(BootstrapState.EMPTY, BootstrapState.BLOCKED) else listOf(BootstrapState.BLOCKED)
            for (state in states) rejected(WrappingFailure.INVENTORY) {
                WrappingPreparation.run(mode, { state }, { fail("B15-NO-INIT") }, { fail("B15-NO-PREPARE") })
            }
        }
    }
    @Test fun inventoryExceptionHasFixedIdWithoutPlatformText() {
        rejected(WrappingFailure.INVENTORY) {
            WrappingPreparation.run(KeyExperiment.STORAGE_UNWRAP,
                { throw IllegalStateException("synthetic untrusted detail") }, {}, {})
        }
    }
    @Test fun policyAndInvalidationSurvivePreparationDispatcher() {
        for (failure in listOf(WrappingFailure.KEY_POLICY, WrappingFailure.KEY_INVALIDATED)) rejected(failure) {
            WrappingPreparation.run(KeyExperiment.STORAGE_UNWRAP, { BootstrapState.COMPLETE },
                { fail("B15-NO-REPLACEMENT") }, { throw WrappingException(failure) })
        }
    }
    @Test fun stageFailuresRemainDistinctAndNeverPreserveExceptionDetails() {
        for (stage in listOf(WrappingFailure.WRAPPED_READ, WrappingFailure.CIPHER_LOOKUP,
            WrappingFailure.CIPHER_INIT, WrappingFailure.CRYPTOOBJECT, WrappingFailure.PROMPT_START))
            rejected(stage) { wrappingStep(stage) { throw IllegalStateException("synthetic untrusted detail") } }
    }
    @Test fun diagnosisReachesCoordinatorAsBlockedWithoutMarker() {
        for (failure in WrappingFailure.entries) {
            val session = SessionCoordinator(object : AuthenticationExperiment {
                override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                    complete(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, failure))
                    return Cancellation { }
                }
            }, Maintenance { fail("B15-NO-MAINTENANCE"); Subscription {} },
                MonotonicTime { 0 }, SessionPolicy(100, 20))
            session.foreground(true); session.authenticate(Experiment.STORAGE_UNWRAP)
            assertEquals(SessionState.BLOCKED, session.snapshot().state)
            assertEquals(failure.testId, session.snapshot().technicalId)
            assertNull(session.snapshot().marker)
            assertNull(session.acquire())
        }
    }
    @Test fun promptCancellationRemainsLockedAndDeniedWithDiagnosticId() {
        val session = SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                complete(AuthenticationOutcome.Rejected(SecurityFailure.CANCELLED, WrappingFailure.PROMPT_REJECTED))
                return Cancellation { }
            }
        }, Maintenance { fail("B15-NO-MAINTENANCE"); Subscription {} }, MonotonicTime { 0 }, SessionPolicy(100, 20))
        session.foreground(true); session.authenticate(Experiment.STORAGE_UNWRAP)
        assertEquals(SessionState.LOCKED, session.snapshot().state)
        assertEquals(Observation.DENIED, session.snapshot().platformAuthorization)
        assertEquals("B15-PROMPT-REJECTED", session.snapshot().technicalId)
    }
}
