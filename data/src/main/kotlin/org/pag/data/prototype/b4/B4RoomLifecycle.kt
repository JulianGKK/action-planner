package org.pag.data.prototype.b4

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CompletableDeferred
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.data.prototype.b2.B2Exception
import org.pag.data.prototype.b2.B2Failure
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class B4Background { NONE, EXPECTED_REVOCATION, UNEXPECTED_BACKGROUND_FAULT }
/** Preserve the B2 driver's typed failure path so a denied commit rolls back there. */
internal class B4DriverBackgroundFault : B2Exception(B2Failure.BLOCKED)

/** One Room instance only. Never stores the Throwable, paths, SQL, or key material. */
internal class B4RoomLifecycle {
    private val state = AtomicReference(B4Background.NONE)
    private val closing = AtomicBoolean(false)
    private val recorded = CompletableDeferred<B4Background>()
    val background: B4Background get() = state.get()
    suspend fun awaitBackground(): B4Background = recorded.await()
    fun handler(onUnexpected: () -> Unit): CoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
        if (error is StorageAccessRevoked) state.compareAndSet(B4Background.NONE, B4Background.EXPECTED_REVOCATION)
        else {
            state.set(B4Background.UNEXPECTED_BACKGROUND_FAULT)
            onUnexpected()
        }
        recorded.complete(state.get())
    }
    fun beginClose(): Boolean = closing.compareAndSet(false, true)
    fun requireHealthy() {
        when (state.get()) {
            B4Background.NONE -> Unit
            B4Background.EXPECTED_REVOCATION -> throw StorageAccessRevoked()
            B4Background.UNEXPECTED_BACKGROUND_FAULT -> throw B4Exception(B4Id.BACKGROUND)
        }
    }
    fun requireOpen() {
        requireHealthy()
        if (closing.get()) throw StorageAccessRevoked()
    }
    /** The original session still decides every access; this adds only fault-based denial. */
    fun guard(delegate: StorageContext, beforeCheck: () -> Unit = {}): StorageContext = object : StorageContext {
        override val scope = delegate.scope
        override fun check(point: AccessPoint): Boolean {
            if (state.get() == B4Background.UNEXPECTED_BACKGROUND_FAULT) throw B4DriverBackgroundFault()
            requireHealthy(); beforeCheck(); return delegate.check(point)
        }
        override fun publish(result: () -> Unit): Boolean {
            requireOpen()
            return delegate.publish { requireHealthy(); result() }
        }
    }
}
