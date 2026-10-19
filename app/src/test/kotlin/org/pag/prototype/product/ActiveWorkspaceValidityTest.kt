package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.prototype.product.ActiveWorkspaceValidity.*

class ActiveWorkspaceValidityTest {
    private class Harness {
        val initial = Binding("synthetic-action", ActionStatus.ACTIVE, 1, 0)
        var binding: Binding? = initial
        var generation = 1L
        var allowed = true
        var publicationAllowed = true
        var beforePublication: () -> Unit = {}
        val access = object : StorageAccess {
            override fun acquire(scope: AccessScope): StorageContext? {
                if (!allowed) return null
                val admittedGeneration = generation
                return object : StorageContext {
                    override val generation = admittedGeneration
                    override val scope = AccessScope.READY
                    override fun check(point: AccessPoint) = allowed && generation == this@Harness.generation
                    override fun publish(result: () -> Unit): Boolean {
                        beforePublication()
                        if (!publicationAllowed || !check(AccessPoint.PUBLICATION)) return false
                        result(); return true
                    }
                }
            }
        }
        val contract = ActiveWorkspaceValidity(initial, access) { binding }
        val parts = setOf(Projection.PEOPLE, Projection.TASK_COUNTS)
        fun load() {
            val ticket = contract.beginRead(parts)!!
            assertTrue(contract.publishRead(ticket, parts, Cleanup.COMPLETE) {})
        }
        fun committed(): MutationTicket {
            val ticket = contract.beginMutation(parts)!!
            assertTrue(contract.finishMutation(ticket, Outcome.COMMITTED, Cleanup.COMPLETE))
            return ticket
        }
    }

    @Test fun correctScopeNeedsCompletedReadAndCentralHitPublication() {
        val h = Harness(); assertFalse(h.contract.canReuse(h.parts)); h.load()
        assertTrue(h.contract.canReuse(h.parts)); var calls = 0
        assertTrue(h.contract.publishCurrent(h.parts) { calls++ }); assertEquals(1, calls)
        assertFalse(h.contract.canReuse(setOf(Projection.TASKS)))
        assertFalse(h.contract.canReuse(emptySet()))
    }

    @Test fun wrongActionPermissionStatusOrMissingBindingTerminatesOwner() {
        for (change in listOf<(Binding) -> Binding?>(
            { it.copy(actionId = "other-synthetic") }, { it.copy(permissionRevision = 1) }, { null }
        )) {
            val h = Harness(); h.load(); h.binding = change(h.initial)
            assertFalse(h.contract.canReuse(h.parts)); assertEquals(State.REVOKED, h.contract.state)
            h.binding = h.initial; assertNull(h.contract.beginRead(h.parts))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Binding("synthetic", ActionStatus.COMPLETED, 1, 0)
        }
    }

    @Test fun wrongGenerationInBindingOrAuthorityCannotReuse() {
        for (bindingChange in listOf(false, true)) {
            val h = Harness(); h.load()
            if (bindingChange) h.binding = h.initial.copy(generation = 2) else h.generation = 2
            assertFalse(h.contract.canReuse(h.parts)); assertNull(h.contract.beginRead(h.parts))
        }
    }

    @Test fun pendingInvalidatesBeforeAnyWriteAndDeniesOverlappingWork() {
        val h = Harness(); h.load(); val mutation = h.contract.mutationEpoch
        h.contract.beginMutation(h.parts)!!
        assertTrue(h.contract.isPending()); assertTrue(h.contract.isDirty(Projection.PEOPLE))
        assertEquals(mutation + 1, h.contract.mutationEpoch)
        assertFalse(h.contract.canReuse(h.parts)); assertNull(h.contract.beginRead(h.parts))
        assertNull(h.contract.beginMutation(h.parts))
    }

    @Test fun commitAndCleanupDoNotImplicitlyPublishAndCallbacksAreOneShot() {
        val h = Harness(); h.load(); val write = h.contract.beginMutation(h.parts)!!; var calls = 0
        assertFalse(h.contract.publishMutation(write, h.parts) { calls++ })
        assertTrue(h.contract.finishMutation(write, Outcome.COMMITTED, Cleanup.COMPLETE))
        assertFalse(h.contract.canReuse(h.parts))
        assertFalse(h.contract.finishMutation(write, Outcome.COMMITTED, Cleanup.COMPLETE))
        assertTrue(h.contract.publishMutation(write, h.parts) { calls++ })
        assertFalse(h.contract.publishMutation(write, h.parts) { calls++ })
        assertEquals(1, calls); assertTrue(h.contract.canReuse(h.parts))
    }

    @Test fun lockBeforeCommitNotificationCannotMakeLocalStateCommitted() {
        val h = Harness(); val write = h.contract.beginMutation(h.parts)!!
        h.allowed = false
        assertFalse(h.contract.finishMutation(write, Outcome.COMMITTED, Cleanup.COMPLETE))
        assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
        assertEquals(State.REVOKED, h.contract.state)
        // Actual DB commit/rollback remains the existing driver's responsibility.
    }

    @Test fun commitBeforeLockButPublicationAfterLockDeniesSuccess() {
        val h = Harness(); val write = h.committed(); h.allowed = false
        assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
        h.allowed = true; assertNull(h.contract.beginRead(h.parts))
    }

    @Test fun revokeImmediatelyDiscardsReadAndMutationTickets() {
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        h.contract.revoke(); h.contract.revoke()
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        val other = Harness(); val write = other.committed(); other.contract.revoke()
        assertFalse(other.contract.publishMutation(write, other.parts) { fail() })
    }

    @Test fun unknownIsTerminalEvenIfSameScopeAndStorageBecomeAvailableAgain() {
        val h = Harness(); h.load(); val write = h.contract.beginMutation(h.parts)!!
        assertTrue(h.contract.finishMutation(write, Outcome.UNKNOWN, Cleanup.COMPLETE))
        h.contract.revoke() // cannot downgrade recovery to a reusable state
        assertEquals(State.RECOVERY_REQUIRED, h.contract.state)
        repeat(3) {
            assertNull(h.contract.beginRead(h.parts)); assertNull(h.contract.beginMutation(h.parts))
            assertFalse(h.contract.canReuse(h.parts))
            assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
        }
    }

    @Test fun cleanupFailureAfterCommitOrRollbackIsTerminal() {
        for (outcome in listOf(Outcome.COMMITTED, Outcome.ROLLED_BACK)) {
            val h = Harness(); val write = h.contract.beginMutation(h.parts)!!
            assertTrue(h.contract.finishMutation(write, outcome, Cleanup.FAILED))
            assertEquals(State.RECOVERY_REQUIRED, h.contract.state)
            assertNull(h.contract.beginRead(h.parts))
        }
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.FAILED) { fail() })
        assertEquals(State.RECOVERY_REQUIRED, h.contract.state)
    }

    @Test fun definiteRollbackStaysDirtyUntilAuthoritativeReload() {
        val h = Harness(); h.load(); val write = h.contract.beginMutation(h.parts)!!
        assertTrue(h.contract.finishMutation(write, Outcome.ROLLED_BACK, Cleanup.COMPLETE))
        assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
        assertFalse(h.contract.canReuse(h.parts)); h.load(); assertTrue(h.contract.canReuse(h.parts))
    }

    @Test fun newMutationFencesReadAndNewMutationFencesOldPatch() {
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        val write = h.contract.beginMutation(h.parts)!!
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        h.contract.finishMutation(write, Outcome.COMMITTED, Cleanup.COMPLETE)
        h.contract.beginMutation(h.parts)!!
        assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
    }

    @Test fun newReloadFencesCommittedPatchAndDuplicateOldRead() {
        val h = Harness(); val write = h.committed(); val read = h.contract.beginRead(h.parts)!!
        assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
        assertTrue(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) {})
        val next = h.contract.beginRead(h.parts)!!
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        assertTrue(h.contract.publishRead(next, h.parts, Cleanup.COMPLETE) {})
    }

    @Test fun partialCompletionIsDeniedAndConsumesItsTicket() {
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        assertFalse(h.contract.publishRead(read, setOf(Projection.PEOPLE), Cleanup.COMPLETE) { fail() })
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        val write = h.committed()
        assertFalse(h.contract.publishMutation(write, setOf(Projection.PEOPLE)) { fail() })
        assertFalse(h.contract.publishMutation(write, h.parts) { fail() })
    }

    @Test fun readsCoalesceOnlyEqualDemandAndFreezeCallerSet() {
        val h = Harness(); val parts = h.parts.toMutableSet()
        val read = h.contract.beginRead(parts)!!; val epoch = h.contract.loadEpoch
        parts.clear()
        assertSame(read, h.contract.beginRead(h.parts)); assertEquals(epoch, h.contract.loadEpoch)
        assertNull(h.contract.beginRead(setOf(Projection.TASKS)))
        assertTrue(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) {})
        assertNull(h.contract.beginRead(emptySet())); assertNull(h.contract.beginMutation(emptySet()))
    }

    @Test fun mutationFreezesCallerSetAndForeignTicketsNeverMatch() {
        val h = Harness(); val other = Harness(); val parts = h.parts.toMutableSet()
        val write = h.contract.beginMutation(parts)!!; parts.clear()
        assertFalse(other.contract.finishMutation(write, Outcome.UNKNOWN, Cleanup.FAILED))
        assertTrue(h.contract.finishMutation(write, Outcome.COMMITTED, Cleanup.COMPLETE))
        assertTrue(h.contract.publishMutation(write, h.parts) {})
        val read = h.contract.beginRead(h.parts)!!
        assertFalse(other.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        assertTrue(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) {})
    }

    @Test fun authorityRechecksBetweenLocalAdmissionAndPublication() {
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        h.beforePublication = { h.allowed = false }
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        val other = Harness(); other.load(); other.publicationAllowed = false
        assertFalse(other.contract.publishCurrent(other.parts) { fail() })
    }

    @Test fun permissionChangeInsideFinalPublicationGateDeniesCallback() {
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        h.beforePublication = { h.binding = h.initial.copy(permissionRevision = 2) }
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { fail() })
        assertEquals(State.REVOKED, h.contract.state)
    }

    @Test fun newReadBetweenAdmissionAndFinalGateFencesOldResult() {
        val h = Harness(); val old = h.contract.beginRead(h.parts)!!
        var next: ReadTicket? = null
        h.beforePublication = { next = h.contract.beginRead(h.parts)!! }
        assertFalse(h.contract.publishRead(old, h.parts, Cleanup.COMPLETE) { fail() })
        assertEquals(State.ACTIVE, h.contract.state)
        h.beforePublication = {}
        assertTrue(h.contract.publishRead(next!!, h.parts, Cleanup.COMPLETE) {})
    }

    @Test fun finishedNewMutationAtFinalGateStillFencesOldPatch() {
        val h = Harness(); val old = h.committed()
        h.beforePublication = {
            val next = h.contract.beginMutation(h.parts)!!
            h.contract.finishMutation(next, Outcome.ROLLED_BACK, Cleanup.COMPLETE)
        }
        assertFalse(h.contract.publishMutation(old, h.parts) { fail() })
        assertFalse(h.contract.canReuse(h.parts))
    }

    @Test fun failingPublicationIsTerminalAndCannotBeRetried() {
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        assertThrows(IllegalStateException::class.java) {
            h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) { error("synthetic") }
        }
        assertEquals(State.RECOVERY_REQUIRED, h.contract.state)
        assertNull(h.contract.beginRead(h.parts))
        val other = Harness(); val ticket = other.contract.beginRead(other.parts)!!
        assertThrows(AssertionError::class.java) {
            other.contract.publishRead(ticket, other.parts, Cleanup.COMPLETE) { throw AssertionError("synthetic") }
        }
        assertEquals(State.RECOVERY_REQUIRED, other.contract.state)
    }

    @Test fun reentrantWorkIsDeniedAndRevokeCannotBeUndoneByPublication() {
        val h = Harness(); val read = h.contract.beginRead(h.parts)!!
        assertFalse(h.contract.publishRead(read, h.parts, Cleanup.COMPLETE) {
            assertNull(h.contract.beginRead(h.parts)); assertNull(h.contract.beginMutation(h.parts))
            h.contract.revoke()
        })
        assertFalse(h.contract.canReuse(h.parts))
    }

    @Test fun crossThreadCallsFailInsteadOfRacingMetadata() {
        val h = Harness(); var error: Throwable? = null
        val worker = Thread { try { h.contract.beginRead(h.parts) } catch (e: Throwable) { error = e } }
        worker.start(); worker.join(); assertTrue(error is IllegalStateException)
    }
}
