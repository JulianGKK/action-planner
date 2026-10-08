package org.pag.data.prototype

import org.pag.domain.AccessPoint
import org.pag.domain.AccessScope
import org.pag.domain.StorageContext
import org.pag.security.OperationResult
import org.pag.security.SyntheticOperation
import java.util.UUID

/** Internal maintenance work, never a second session controller. */
interface StorageGateOperation : SyntheticOperation {
    val initializes: Boolean
    fun diagnosticId(id: B16Id): String = id.testId
    val publicMarker: String get() = B16StorageGate.MARKER
    fun run(context: StorageContext, progress: (B16Id) -> Unit, complete: (B16Id) -> Unit)
}
class B16Hooks(
    val afterUnwrap: () -> Unit = {}, val beforeOpen: () -> Unit = {},
    val afterCreate: () -> Unit = {}, val afterCommit: () -> Unit = {},
    val beforeComplete: () -> Unit = {}, val engine: EngineTestHooks = EngineTestHooks()
)

/** Existing B1 engine, one transient handle and key consumer; no persistent key registry. */
class B16StorageGate(private val inventory: B16Inventory, private val hooks: B16Hooks = B16Hooks()) {
    fun run(context: StorageContext, operation: SyntheticOperation, initializes: Boolean,
        progress: (B16Id) -> Unit = {}): B16Id {
        var failure = B16Id.UNWRAP_FAILED
        var result = B16Id.BLOCKED
        try {
            requireAccess(context)
            val unwrapped = operation.useUnwrappedKey({ context.check(AccessPoint.STEP) }) { key ->
                try {
                    hooks.afterUnwrap()
                    requireAccess(context)
                    progress(B16Id.UNWRAP)
                    hooks.beforeOpen()
                    requireAccess(context)
                    if (initializes) inventory.requirePending()
                    else if (inventory.state() != org.pag.security.BootstrapState.COMPLETE) throw B16Exception(B16Id.BLOCKED)
                    val identity = if (initializes) UUID.randomUUID().toString() else inventory.identity()
                    val engine = if (initializes)
                        SqlCipherEngine.createNew(inventory.database, key, context, hooks.engine, AccessScope.MAINTENANCE)
                    else SqlCipherEngine.openExisting(inventory.database, key, context, hooks.engine, AccessScope.MAINTENANCE)
                    engine.use { db ->
                        requireAccess(context)
                        progress(if (initializes) B16Id.DB_PENDING else B16Id.OPENED)
                        if (initializes) {
                            hooks.afterCreate(); requireAccess(context)
                            if (!db.write(SyntheticRow(identity, MARKER, 16))) throw B16Exception(B16Id.EXPIRED)
                            hooks.afterCommit(); requireAccess(context)
                        }
                        var rows: List<SyntheticRow>? = null
                        if (!db.read { rows = it }) throw B16Exception(B16Id.EXPIRED)
                        requireAccess(context)
                        if (rows != listOf(SyntheticRow(identity, MARKER, 16))) throw B16Exception(B16Id.INTEGRITY)
                    }
                    requireAccess(context)
                    if (initializes) {
                        hooks.beforeComplete(); requireAccess(context)
                        inventory.finish(identity, context)
                    }
                    requireAccess(context)
                    result = if (initializes) B16Id.CREATED else B16Id.RESTART
                } catch (error: EngineException) {
                    failure = map(error.failure)
                    throw B16Exception(failure)
                } catch (error: B16Exception) { failure = error.id; throw error }
            }
            requireAccess(context)
            return when (unwrapped) {
                OperationResult.SUCCEEDED -> result
                OperationResult.NOT_AUTHORIZED -> B16Id.AUTH_REQUIRED
                else -> failure
            }
        } catch (error: B16Exception) { return error.id }
        catch (_: Exception) { return B16Id.BLOCKED }
        finally { operation.release() }
    }
    companion object {
        const val MARKER = "PAG_B16_SYNTHETIC_DATABASE_ONLY"
        private fun requireAccess(context: StorageContext) {
            if (context.scope != AccessScope.MAINTENANCE || !context.check(AccessPoint.STEP))
                throw B16Exception(B16Id.EXPIRED)
        }
        private fun map(failure: EngineFailure): B16Id = when (failure) {
            EngineFailure.SESSION_INVALID -> B16Id.EXPIRED
            EngineFailure.KEY_OR_DATABASE_UNREADABLE -> B16Id.KEY_OR_DB
            EngineFailure.INTEGRITY_FAILED -> B16Id.INTEGRITY
            EngineFailure.UNKNOWN_SCHEMA -> B16Id.SCHEMA
            else -> B16Id.BLOCKED
        }
    }
}
