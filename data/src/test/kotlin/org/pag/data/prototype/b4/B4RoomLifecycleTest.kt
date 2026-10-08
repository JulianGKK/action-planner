package org.pag.data.prototype.b4

import kotlinx.coroutines.CoroutineName
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.*
import org.pag.domain.*
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext

class B4RoomLifecycleTest {
    @Test fun exactRevocationIsExpected() {
        val lifecycle = B4RoomLifecycle(); var fatal = false
        lifecycle.handler { fatal = true }.handleException(EmptyCoroutineContext, StorageAccessRevoked())
        assertEquals(B4Background.EXPECTED_REVOCATION, lifecycle.background); assertFalse(fatal)
        assertTrue(runCatching { lifecycle.requireOpen() }.exceptionOrNull() is StorageAccessRevoked)
    }
    @Test fun oldGenericSessionErrorIsNotSilentlyAccepted() = unexpected(B2Exception(B2Failure.SESSION))
    @Test fun ioErrorIsUnexpected() = unexpected(IOException("B4-INJECTED-IO"))
    @Test fun sqliteFailureIsUnexpected() = unexpected(B2Exception(B2Failure.KEY_OR_UNREADABLE))
    @Test fun programmingErrorIsUnexpected() = unexpected(IllegalStateException("B4-INJECTED-BUG"))
    @Test fun roomCoroutineNameDoesNotMakeErrorExpected() {
        val lifecycle = B4RoomLifecycle()
        lifecycle.handler {}.handleException(CoroutineName("Room Invalidation Tracker Refresh"), IllegalStateException())
        assertEquals(B4Background.UNEXPECTED_BACKGROUND_FAULT, lifecycle.background)
    }
    @Test fun unexpectedFaultCannotBeDowngradedByLaterRevocation() {
        val lifecycle = B4RoomLifecycle(); val handler = lifecycle.handler {}
        handler.handleException(EmptyCoroutineContext, IOException())
        handler.handleException(EmptyCoroutineContext, StorageAccessRevoked())
        assertEquals(B4Background.UNEXPECTED_BACKGROUND_FAULT, lifecycle.background)
    }
    @Test fun unexpectedFaultOverridesEarlierExpectedRevocation() {
        val lifecycle = B4RoomLifecycle(); val handler = lifecycle.handler {}
        handler.handleException(EmptyCoroutineContext, StorageAccessRevoked())
        handler.handleException(EmptyCoroutineContext, IOException())
        assertEquals(B4Background.UNEXPECTED_BACKGROUND_FAULT, lifecycle.background)
    }
    @Test fun closeIsIdempotentAndRejectsOldOwner() {
        val lifecycle = B4RoomLifecycle()
        assertTrue(lifecycle.beginClose()); assertFalse(lifecycle.beginClose())
        assertTrue(runCatching { lifecycle.requireOpen() }.exceptionOrNull() is StorageAccessRevoked)
        assertEquals(B4Background.NONE, lifecycle.background)
    }
    @Test fun healthyGuardNeverCreatesSessionValidity() {
        val lifecycle = B4RoomLifecycle(); var valid = true
        val delegate = context { valid }
        val guarded = lifecycle.guard(delegate)
        assertTrue(guarded.check(AccessPoint.COMMIT)); valid = false
        assertFalse(guarded.check(AccessPoint.COMMIT)); assertFalse(guarded.publish { fail("B4-STALE") })
    }
    @Test fun unexpectedFaultDeniesEveryCheckpointAndPublication() {
        val lifecycle = B4RoomLifecycle(); val guarded = lifecycle.guard(context { true })
        lifecycle.handler {}.handleException(EmptyCoroutineContext, IOException())
        AccessPoint.entries.forEach { assertTrue(runCatching { guarded.check(it) }.exceptionOrNull() is B4DriverBackgroundFault) }
        assertTrue(runCatching { guarded.publish { fail("B4-FAULT-PUBLICATION") } }.exceptionOrNull() is B4Exception)
    }
    @Test fun newInstanceDoesNotInheritOldFaultOrRestoreOldInstance() {
        val old = B4RoomLifecycle(); old.handler {}.handleException(EmptyCoroutineContext, StorageAccessRevoked())
        val fresh = B4RoomLifecycle(); fresh.requireOpen()
        assertEquals(B4Background.NONE, fresh.background)
        assertTrue(runCatching { old.requireOpen() }.exceptionOrNull() is StorageAccessRevoked)
    }
    private fun unexpected(error: Throwable) {
        val lifecycle = B4RoomLifecycle(); var fatal = false
        lifecycle.handler { fatal = true }.handleException(EmptyCoroutineContext, error)
        assertTrue(fatal); assertEquals(B4Background.UNEXPECTED_BACKGROUND_FAULT, lifecycle.background)
        val failure = runCatching { lifecycle.requireOpen() }.exceptionOrNull()
        assertTrue(failure is B4Exception && failure.id == B4Id.BACKGROUND)
    }
    private fun context(valid: () -> Boolean) = object : StorageContext {
        override val scope = AccessScope.MAINTENANCE
        override fun check(point: AccessPoint) = valid()
        override fun publish(result: () -> Unit): Boolean { if (!valid()) return false; result(); return true }
    }
}
