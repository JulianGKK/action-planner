package org.pag.data.product

import androidx.room3.Transactor
import org.pag.domain.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Coroutine-local evidence. No optimistic commit marker inside a transaction body. */
internal class TaskWriteProgress {
    var outcome = WorkspaceWriteOutcome.ROLLED_BACK; private set // no transaction attempted
    var cleanup = WorkspaceWriteCleanup.COMPLETE; private set // no resources acquired
    fun opening() {
        // Open itself can perform Room migration writes. Never call an uncertain open a
        // confirmed rollback just because the requested TaskStore body was not reached.
        outcome = WorkspaceWriteOutcome.UNKNOWN
        cleanup = WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED
    }
    fun startingTransaction() { outcome = WorkspaceWriteOutcome.UNKNOWN }
    fun ended(value: WorkspaceWriteOutcome) { check(value != WorkspaceWriteOutcome.UNKNOWN); outcome = value }
    fun cleaned() { cleanup = WorkspaceWriteCleanup.COMPLETE }
    fun completion() = WorkspaceWriteCompletion(outcome, cleanup)
}

/** Supplied the actual Room writer connection, not a DAO callback. Explicit domain rejection
 * rolls back. A thrown BEGIN/body/END/ROLLBACK remains UNKNOWN, even when Room probably rolled
 * back. Refuse nesting: a released savepoint must never be called a durable outer commit.
 */
internal suspend fun <T> confirmedTaskTransaction(tx: Transactor, context: StorageContext,
    progress: TaskWriteProgress, work: suspend () -> OperationsResult<T>): OperationsResult<T> {
    if (tx.inTransaction()) throw ProductStorageFailure()
    progress.startingTransaction()
    val result = tx.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
        val value = work()
        if (value is OperationsResult.Rejected) rollback(value)
        requireOperations(context, AccessPoint.COMMIT)
        value
    }
    if (tx.inTransaction()) throw ProductStorageFailure()
    // This point is AFTER the outer END / explicit ROLLBACK succeeded, BEFORE pool/DB/key cleanup.
    progress.ended(if (result is OperationsResult.Value) WorkspaceWriteOutcome.COMMITTED else WorkspaceWriteOutcome.ROLLED_BACK)
    return result
}

/** Used by the real task repository. beforeTaskQueue/finish/publication on the owner thread;
 * SQL on worker. Default runtime port forbids RAM reuse. No retry, result cache or new authority.
 */
internal class TaskMutationBoundary(private val access: StorageAccess, private val worker: Executor,
    private val main: Executor, private val mutations: WorkspaceMutationInvalidation,
    private val blocked: () -> Unit) {
    fun <T> submit(actionId: String, publish: (OperationsResult<T>) -> Unit,
        work: (StorageContext, TaskWriteProgress) -> OperationsResult<T>) {
        val context = access.acquire(AccessScope.READY) ?: return
        if (context.scope != AccessScope.READY || !context.check(AccessPoint.OPERATION)) return
        val ticket = mutations.beforeTaskQueue(actionId, context.generation)
        if (ticket == null) {
            context.publish { publish(OperationsResult.Rejected(OperationsFailure.STALE)) }
            return
        }
        val started = AtomicBoolean()
        val delivered = AtomicBoolean()
        val progress = TaskWriteProgress()
        fun complete(result: OperationsResult<T>?, failed: Boolean) {
            if (!delivered.compareAndSet(false, true)) return
            val evidence = progress.completion()
            if (!ticket.finish(evidence)) return
            if (failed || evidence.outcome == WorkspaceWriteOutcome.UNKNOWN || evidence.cleanup != WorkspaceWriteCleanup.COMPLETE) {
                if (context.check(AccessPoint.STEP)) blocked()
                return
            }
            if (result == null || (result is OperationsResult.Value && evidence.outcome != WorkspaceWriteOutcome.COMMITTED)) {
                if (context.check(AccessPoint.STEP)) blocked()
                return
            }
            context.publish { if (ticket.isCurrent()) publish(result) }
        }
        try {
            worker.execute {
                if (started.compareAndSet(false, true)) {
                    var result: OperationsResult<T>? = null
                    var failed = false
                    try {
                        requireOperations(context, AccessPoint.OPERATION)
                        progress.opening()
                        // work MUST include actual inventory/open/transaction/DB/key cleanup.
                        result = work(context, progress)
                        if (result is OperationsResult.Value && progress.outcome != WorkspaceWriteOutcome.COMMITTED)
                            throw ProductStorageFailure()
                        progress.cleaned()
                    } catch (_: Exception) { failed = true }
                    // Queue failure leaves the ticket pending/dirty. Never finish owner state
                    // from a worker or fabricate a completed rollback after executor rejection.
                    main.execute { complete(result, failed) }
                }
            }
        } catch (_: Exception) {
            if (!started.get()) complete(null, true) // definitively never queued; no resources
            // If an executor ran work before throwing, keep its real outcome/completion path.
        }
    }
}
