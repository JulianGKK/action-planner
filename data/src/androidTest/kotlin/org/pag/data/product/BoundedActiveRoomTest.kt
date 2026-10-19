package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Prepared real SQLCipher/Room proof. Compile only in this slice; no device installation. */
class BoundedActiveRoomTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "org.pag.data.unlockprobe")
    }
    @Test fun boundedProjectionEqualsExistingOperationsAndActionReaders() {
        P11SyntheticFixture(app).use { f ->
            var expected: OperationalBoard? = null
            var action: Action? = null
            f.operations.board(f.activeId) { expected = (it as OperationsResult.Value).value }
            f.actions.getAction(f.activeId) { action = it }
            val value = f.boundedActive() as BoundedActiveResult.Value
            assertEquals(expected, value.operations); assertEquals(action, value.action)
        }
    }
    @Test fun preparationCompletedAndMissingAreRejected() {
        P11SyntheticFixture(app).use { f ->
            for (id in listOf(f.preparationId, f.completedId, "missing-synthetic")) {
                assertEquals(BoundedActiveStatus.ACCESS_REVOKED, f.boundedActive(id).status)
            }
        }
    }
    @Test fun finalPublicationDenialAndRevokedSessionReturnNoPayload() {
        P11SyntheticFixture(app).use { f ->
            assertEquals(BoundedActiveResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED),
                f.boundedActive(allowPublication = false))
            assertTrue(f.boundedActive() is BoundedActiveResult.Value)
            f.revoke()
            assertEquals(BoundedActiveResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED), f.boundedActive())
        }
    }
    @Test fun repeatReadClosesConnectionsAndDoesNotChangeProjection() {
        P11SyntheticFixture(app).use { f ->
            val first = f.boundedActive()
            val before = java.io.File("/proc/self/fd").list()!!.size
            repeat(3) { assertEquals(first, f.boundedActive()) }
            assertTrue(java.io.File("/proc/self/fd").list()!!.size <= before + 2)
        }
    }
}
