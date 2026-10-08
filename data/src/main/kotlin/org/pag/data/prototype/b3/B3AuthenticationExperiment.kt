package org.pag.data.prototype.b3

import org.pag.data.prototype.B16Id
import org.pag.data.prototype.StorageGateOperation
import org.pag.domain.StorageContext
import org.pag.security.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Reuses the established credential/CryptoObject/unwrap path, with a separate file-key fixture. */
class B3AuthenticationExperiment(private val inventory: B3Inventory,
    private val delegate: AuthenticationExperiment, private val worker: Executor,
    private val main: Executor) : AuthenticationExperiment {
    override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
        if (mode !in setOf(KeyExperiment.B3_INITIALIZE, KeyExperiment.B3_REOPEN)) {
            complete(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR)); return Cancellation {}
        }
        val initializes = mode == KeyExperiment.B3_INITIALIZE
        val cancelled = AtomicBoolean(false)
        val pending = AtomicReference<Cancellation?>()
        worker.execute {
            try {
                if (cancelled.get()) return@execute
                if (initializes) inventory.reserve()
                else if (inventory.state() != B3State.COMPLETE_CANDIDATE) throw B3Exception(B3Id.BLOCKED)
            } catch (error: Exception) {
                val diagnostic = if (error is B3Exception && error.id == B3Id.NOT_EMPTY)
                    WrappingFailure.NOT_EMPTY else WrappingFailure.INVENTORY
                main.execute { if (!cancelled.get()) complete(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, diagnostic)) }
                return@execute
            }
            main.execute prompt@{
                if (cancelled.get()) return@prompt
                try {
                    pending.set(delegate.start(if (initializes) KeyExperiment.STORAGE_INITIALIZE else KeyExperiment.STORAGE_UNWRAP) { outcome ->
                        if (cancelled.get()) { if (outcome is AuthenticationOutcome.Granted) outcome.operation.release() }
                        else if (outcome !is AuthenticationOutcome.Granted) complete(outcome)
                        else {
                            val result = AtomicReference(B3Id.BLOCKED)
                            complete(AuthenticationOutcome.Granted(object : StorageGateOperation {
                                override val initializes = initializes
                                override val properties = outcome.operation.properties
                                override val publicMarker = "PAG_B3_SYNTHETIC_STREAM_ONLY"
                                override fun diagnosticId(id: B16Id) = when (id) {
                                    B16Id.CREATED -> B3Id.PUBLISH.testId
                                    B16Id.RESTART -> B3Id.RESTART.testId
                                    B16Id.UNWRAP -> B3Id.UNWRAP.testId
                                    B16Id.DB_PENDING -> B3Id.ENCRYPT.testId
                                    B16Id.OPENED -> B3Id.VALIDATE.testId
                                    else -> result.get().testId
                                }
                                override fun perform() = OperationResult.NOT_AUTHORIZED
                                override fun release() = outcome.operation.release()
                                override fun run(context: StorageContext, progress: (B16Id) -> Unit, complete: (B16Id) -> Unit) {
                                    worker.execute {
                                        result.set(B3StorageGate(inventory).run(context, outcome.operation, initializes) { id ->
                                            val mapped = when (id) {
                                                B3Id.UNWRAP -> B16Id.UNWRAP
                                                B3Id.ENCRYPT -> B16Id.DB_PENDING
                                                else -> B16Id.OPENED
                                            }
                                            main.execute { progress(mapped) }
                                        })
                                        val done = when (result.get()) {
                                            B3Id.PUBLISH -> B16Id.CREATED
                                            B3Id.RESTART -> B16Id.RESTART
                                            else -> B16Id.BLOCKED
                                        }
                                        main.execute { complete(done) }
                                    }
                                }
                            }, outcome.authenticatedAtMillis))
                        }
                    })
                    if (cancelled.get()) pending.getAndSet(null)?.cancel()
                } catch (_: Exception) {
                    complete(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR, WrappingFailure.PROMPT_START))
                }
            }
        }
        return Cancellation { cancelled.set(true); pending.getAndSet(null)?.cancel() }
    }
}
