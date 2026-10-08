package org.pag.data.prototype

import org.pag.security.*
import org.pag.domain.StorageContext
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Adapter only. Credentials and RSA primitives remain in :security. */
class B16AuthenticationExperiment(
    private val inventory: B16Inventory,
    private val delegate: AuthenticationExperiment,
    private val gate: B16StorageGate,
    private val worker: Executor,
    private val main: Executor
) : AuthenticationExperiment {
    override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
        val cancelled = AtomicBoolean(false)
        val inner = AtomicReference<Cancellation?>()
        val initializes = mode == KeyExperiment.DATABASE_INITIALIZE
        if (!initializes && mode != KeyExperiment.DATABASE_REOPEN) {
            complete(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR)); return Cancellation {}
        }
        worker.execute {
            try {
                if (cancelled.get()) return@execute
                if (initializes) inventory.reserveNew()
                else if (inventory.state() != BootstrapState.COMPLETE) throw B16Exception(B16Id.BLOCKED)
            } catch (failure: Exception) {
                val diagnosis = if (failure is B16Exception && failure.id == B16Id.NOT_EMPTY)
                    WrappingFailure.NOT_EMPTY else WrappingFailure.INVENTORY
                main.execute { if (!cancelled.get()) complete(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, diagnosis)) }
                return@execute
            }
            main.execute prompt@{
                if (cancelled.get()) return@prompt
                try {
                    inner.set(delegate.start(if (initializes) KeyExperiment.STORAGE_INITIALIZE else KeyExperiment.STORAGE_UNWRAP) { outcome ->
                        if (cancelled.get()) {
                            if (outcome is AuthenticationOutcome.Granted) outcome.operation.release()
                        } else if (outcome is AuthenticationOutcome.Granted) {
                            val op = outcome.operation
                            complete(AuthenticationOutcome.Granted(object : StorageGateOperation {
                                override val initializes = initializes
                                override val properties = op.properties
                                override fun perform() = OperationResult.NOT_AUTHORIZED
                                override fun release() = op.release()
                                override fun run(context: StorageContext, progress: (B16Id) -> Unit, complete: (B16Id) -> Unit) {
                                    worker.execute {
                                        val result = gate.run(context, op, initializes) { id -> main.execute { progress(id) } }
                                        main.execute { complete(result) }
                                    }
                                }
                            }, outcome.authenticatedAtMillis))
                        } else complete(outcome)
                    })
                    if (cancelled.get()) inner.getAndSet(null)?.cancel()
                } catch (_: Exception) {
                    complete(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR, WrappingFailure.PROMPT_START))
                }
            }
        }
        return Cancellation { cancelled.set(true); inner.getAndSet(null)?.cancel() }
    }
}
