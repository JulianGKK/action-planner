package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.data.SessionCoordinator
import org.pag.domain.*
import org.pag.security.*
import org.pag.prototype.product.ActiveWorkspaceValidity.*

/** Real unchanged coordinator; synthetic auth/clock, no Android credentials or database. */
class ActiveWorkspaceValiditySessionTest {
    private class Harness {
        var now = 0L
        var environment = true
        val authentication = object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                complete(AuthenticationOutcome.Granted(object : SyntheticOperation {
                    override fun perform() = OperationResult.SUCCEEDED
                    override fun release() {}
                }, now))
                return Cancellation {}
            }
        }
        val session = SessionCoordinator(authentication, Maintenance { it(true); Subscription {} },
            MonotonicTime { now }, ProductSessionTiming.policy, { environment })
        var binding: Binding? = null
        val contract: ActiveWorkspaceValidity
        val parts = setOf(Projection.PEOPLE, Projection.OPERATIONS, Projection.TASKS)
        init {
            ready()
            contract = ActiveWorkspaceValidity(binding!!, session) { binding }
            session.observe { if (it.state != SessionState.READY) contract.revoke() }
        }
        fun ready() {
            session.foreground(true); session.authenticate(Experiment.PER_OPERATION)
            assertEquals(SessionState.READY, session.snapshot().state)
            binding = Binding("synthetic", ActionStatus.ACTIVE, session.snapshot().generation, 0)
        }
        fun load() {
            val read = contract.beginRead(parts)!!
            assertTrue(contract.publishRead(read, parts, Cleanup.COMPLETE) {})
        }
    }

    @Test fun lockSynchronouslyRevokesBeforeCommitNotification() {
        val h = Harness(); val write = h.contract.beginMutation(h.parts)!!; h.session.lock()
        assertEquals(State.REVOKED, h.contract.state)
        assertFalse(h.contract.finishMutation(write, Outcome.COMMITTED, Cleanup.COMPLETE))
        assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
    }

    @Test fun committedBeforeLockCannotPublishAfterLock() {
        val h = Harness(); val write = h.contract.beginMutation(h.parts)!!
        h.contract.finishMutation(write, Outcome.COMMITTED, Cleanup.COMPLETE)
        h.session.lock(); assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
    }

    @Test fun blockedDeniesEvenPreviouslyCleanProjection() {
        val h = Harness(); h.load(); h.session.storageFailure()
        assertEquals(SessionState.BLOCKED, h.session.snapshot().state)
        assertFalse(h.contract.canReuse(h.parts)); assertNull(h.contract.beginRead(h.parts))
    }

    @Test fun readAndHitChecksNeverRenewThirtySecondIdle() {
        val h = Harness(); h.load()
        for (second in 1..29) {
            h.now = second * 1000L; assertTrue(h.contract.canReuse(h.parts))
        }
        h.now = 30_000
        assertFalse(h.contract.publishCurrent(h.parts) { fail() })
        assertEquals(SessionState.LOCKED, h.session.snapshot().state)
    }

    @Test fun interactionCannotExtendAbsoluteOneHundredEightySeconds() {
        val h = Harness(); h.load()
        for (second in 1..179) {
            h.now = second * 1000L; h.session.userInteraction()
            assertTrue(h.contract.canReuse(h.parts))
        }
        h.now = 180_000; h.session.userInteraction()
        assertFalse(h.contract.publishCurrent(h.parts) { fail() })
    }

    @Test fun foregroundAndScreenLockLossDenyLateRead() {
        for (screenLock in listOf(false, true)) {
            val h = Harness(); val read = h.contract.beginRead(h.parts)!!
            if (screenLock) { h.environment = false; h.session.checkExpiry() }
            else h.session.foreground(false)
            assertEquals(State.REVOKED, h.contract.state)
            assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        }
    }

    @Test fun reauthenticationCannotRebindOrRestoreOldOwnerOrTickets() {
        val h = Harness(); h.load(); val read = h.contract.beginRead(h.parts)!!
        val generation = h.binding!!.generation; h.session.lock(); h.ready()
        assertNotEquals(generation, h.binding!!.generation)
        assertNull(h.contract.beginRead(h.parts))
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        val fresh = ActiveWorkspaceValidity(h.binding!!, h.session) { h.binding }
        assertFalse(fresh.canReuse(h.parts)); assertNotNull(fresh.beginRead(h.parts))
    }

    @Test fun missingTrustedPermissionBindingDeniesTheInitialCoreRead() {
        val h = Harness()
        var acquisitions = 0
        val access = object : StorageAccess {
            override fun acquire(scope: AccessScope): StorageContext? {
                acquisitions++
                return h.session.acquire(scope)
            }
        }
        val noTrustedBinding = ActiveWorkspaceValidity(h.binding!!, access) { null }
        assertNull(noTrustedBinding.beginRead(h.parts))
        assertFalse(noTrustedBinding.canReuse(h.parts))
        assertEquals("Binding failure precedes READY acquisition", 0, acquisitions)
        assertEquals(State.REVOKED, noTrustedBinding.state)
    }

    @Test fun aNewReadySessionDoesNotClearUnknownRecoveryState() {
        val h = Harness(); val write = h.contract.beginMutation(h.parts)!!
        h.contract.finishMutation(write, Outcome.UNKNOWN, Cleanup.COMPLETE)
        h.session.lock(); h.ready()
        assertEquals(State.RECOVERY_REQUIRED, h.contract.state)
        assertNull(h.contract.beginRead(h.parts))
        // A future adapter must prove actual maintenance/recovery before creating a new owner.
    }
}
