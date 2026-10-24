package org.pag.data.product

import androidx.room3.Transactor
import org.pag.domain.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal class WorkspaceWriteProgress {
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

/** Only a completed OUTER Room transaction proves commit/rollback. Body results merely
 * request commit or explicit rollback. Exceptions at any boundary remain UNKNOWN.
 */
internal suspend fun <R> confirmedWorkspaceTransaction(tx: Transactor, context: StorageContext,
    progress: WorkspaceWriteProgress, rejected: (R) -> Boolean, work: suspend () -> R): R {
    if (tx.inTransaction()) throw ProductStorageFailure()
    progress.startingTransaction()
    val result = tx.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
        val value = work()
        if (rejected(value)) rollback(value)
        requireOperations(context,AccessPoint.COMMIT)
        value
    }
    if (tx.inTransaction()) throw ProductStorageFailure()
    progress.ended(if (rejected(result)) WorkspaceWriteOutcome.ROLLED_BACK else WorkspaceWriteOutcome.COMMITTED)
    return result
}

/** Shared by task/action/person/operations/material writers, with ONE injected metadata port/epoch.
 * Every work closure includes actual DB/key cleanup; completions run only on the owner thread.
 * No cache, retry, payload owner, session authority or speculative rollback inference.
 */
internal class WorkspaceWriteBoundary(private val access: StorageAccess, private val worker: Executor,
    private val main: Executor, private val mutations: WorkspaceMutationInvalidation,
    private val blocked: () -> Unit) {
    fun <R : Any> submit(mutation: WorkspaceMutation, publish: (R)->Unit, busy: ()->R?,
        success: (R)->Boolean, work: (StorageContext,WorkspaceWriteProgress)->R) =
        submit(mutation,publish,busy,success,{},work)
    fun <R : Any> submit(mutation: WorkspaceMutation, publish: (R)->Unit, busy: ()->R?,
        success: (R)->Boolean, notStarted:()->Unit, work: (StorageContext,WorkspaceWriteProgress)->R) {
        val context=access.acquire(AccessScope.READY)?:run{notStarted();return}
        if(context.scope!=AccessScope.READY || !context.check(AccessPoint.OPERATION)) {notStarted();return}
        val ticket=if(mutation.family==WorkspaceWriterFamily.TASK)
            mutations.beforeTaskQueue(mutation.actionId!!,context.generation)
            else mutations.beforeCoreQueue(mutation,context.generation)
        if(ticket==null) {
            // Action.create's legacy callback has no rejection variant: deny without callback,
            // like existing authority denial. Never fabricate an Action or a storage-corruption error.
            notStarted();busy()?.let { result -> context.publish{publish(result)} };return
        }
        val started=AtomicBoolean();val delivered=AtomicBoolean();val progress=WorkspaceWriteProgress()
        fun complete(result:R?,failed:Boolean) {
            if(!delivered.compareAndSet(false,true))return
            val evidence=progress.completion()
            if(!ticket.finish(evidence))return
            if(failed || evidence.outcome==WorkspaceWriteOutcome.UNKNOWN || evidence.cleanup!=WorkspaceWriteCleanup.COMPLETE) {
                if(context.check(AccessPoint.STEP))blocked();return
            }
            if(result==null || (success(result) && evidence.outcome!=WorkspaceWriteOutcome.COMMITTED)) {
                if(context.check(AccessPoint.STEP))blocked();return
            }
            context.publish{if(ticket.isCurrent())publish(result)}
        }
        try { worker.execute {
            if(started.compareAndSet(false,true)) {
                var result:R?=null;var failed=false
                try {
                    requireOperations(context,AccessPoint.OPERATION);progress.opening()
                    result=work(context,progress)
                    if(success(result) && progress.outcome!=WorkspaceWriteOutcome.COMMITTED)throw ProductStorageFailure()
                    progress.cleaned()
                }catch(_:Exception){failed=true}
                ticket.recordWorkCompletion(progress.completion())
                main.execute{complete(result,failed)} // UI loss stays Pending in this generation.
            }
        }}catch(_:Exception){
            // Disarm a possibly accepted-but-not-yet-started runnable before declaring nonexecution.
            if(started.compareAndSet(false,true)){notStarted();complete(null,true)}
        }
    }
}
