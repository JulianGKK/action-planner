package org.pag.data.prototype.b2

import android.content.Context
import org.pag.data.prototype.B16Id
import org.pag.data.prototype.StorageGateOperation
import org.pag.domain.StorageContext
import org.pag.security.*
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Same B1.6 credential/key-consumer pipeline; only DB work varies. */
class B2AuthenticationExperiment(private val app: Context, private val inventory: B2Inventory,
    private val delegate: AuthenticationExperiment, private val variant: B2Variant,
    private val worker: Executor, private val main: Executor) : AuthenticationExperiment {
    override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
        val initializes = mode in setOf(KeyExperiment.B2_DIRECT_INITIALIZE, KeyExperiment.B2_ROOM_INITIALIZE)
        val expected = if (variant == B2Variant.DIRECT) setOf(KeyExperiment.B2_DIRECT_INITIALIZE, KeyExperiment.B2_DIRECT_REOPEN)
            else setOf(KeyExperiment.B2_ROOM_INITIALIZE, KeyExperiment.B2_ROOM_REOPEN)
        if (mode !in expected) {
            complete(AuthenticationOutcome.Rejected(SecurityFailure.PLATFORM_ERROR)); return Cancellation {}
        }
        val cancelled = AtomicBoolean(false)
        val pending = AtomicReference<Cancellation?>()
        worker.execute {
            try {
                if (cancelled.get()) return@execute
                if (initializes) inventory.reserve()
                else if (inventory.state() != BootstrapState.COMPLETE) throw B2Exception(B2Failure.INVENTORY)
            } catch (error: Exception) {
                val diagnostic = if (error is B2Exception && error.failure == B2Failure.NOT_EMPTY) WrappingFailure.NOT_EMPTY else WrappingFailure.INVENTORY
                main.execute { if (!cancelled.get()) complete(AuthenticationOutcome.Rejected(SecurityFailure.INVALID_KEY, diagnostic)) }
                return@execute
            }
            main.execute prompt@{
                if (cancelled.get()) return@prompt
                try {
                    pending.set(delegate.start(if (initializes) KeyExperiment.STORAGE_INITIALIZE else KeyExperiment.STORAGE_UNWRAP) { outcome ->
                        if (cancelled.get()) { if (outcome is AuthenticationOutcome.Granted) outcome.operation.release() }
                        else if (outcome !is AuthenticationOutcome.Granted) complete(outcome)
                        else complete(AuthenticationOutcome.Granted(object : StorageGateOperation {
                            override val initializes = initializes
                            override val properties = outcome.operation.properties
                            override val publicMarker = "PAG_B2_SYNTHETIC_ONLY"
                            override fun diagnosticId(id: B16Id) = id.testId.replace("B16-", "B2-${variant.name}-")
                            override fun perform() = OperationResult.NOT_AUTHORIZED
                            override fun release() = outcome.operation.release()
                            override fun run(context: StorageContext, progress: (B16Id) -> Unit, complete: (B16Id) -> Unit) {
                                worker.execute {
                                    val result = try {
                                        useB2Key(outcome.operation, context) { key ->
                                            main.execute { progress(B16Id.UNWRAP) }
                                            if (initializes) {
                                                inventory.pending(); context.requireB2()
                                                if (!inventory.store.mkdir()) throw B2Exception(B2Failure.INVENTORY)
                                            } else if (inventory.state() != BootstrapState.COMPLETE) throw B2Exception(B2Failure.INVENTORY)
                                            val id = if (initializes) UUID.randomUUID().toString() else inventory.identity()
                                            B2Database.open(app, inventory.database, key, context, variant, initializes).use { db ->
                                                if (initializes && !db.write(B2Record(id, publicMarker, 2))) throw B2Exception(B2Failure.SESSION)
                                                var rows: List<B2Record>? = null
                                                if (!db.read { rows = it }) throw B2Exception(B2Failure.SESSION)
                                                if (rows != listOf(B2Record(id, publicMarker, 2))) throw B2Exception(B2Failure.INTEGRITY)
                                            }
                                            context.requireB2()
                                            if (initializes) inventory.finish(id, context)
                                            if (initializes) B16Id.CREATED else B16Id.RESTART
                                        }
                                    } catch (error: B2Exception) { when (error.failure) {
                                        B2Failure.SESSION -> B16Id.EXPIRED
                                        B2Failure.KEY_OR_UNREADABLE -> B16Id.KEY_OR_DB
                                        B2Failure.INTEGRITY -> B16Id.INTEGRITY
                                        B2Failure.SCHEMA, B2Failure.MIGRATION -> B16Id.SCHEMA
                                        else -> B16Id.BLOCKED
                                    } } catch (_: Exception) { B16Id.BLOCKED }
                                    main.execute { complete(result) }
                                }
                            }
                        }, outcome.authenticatedAtMillis))
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
