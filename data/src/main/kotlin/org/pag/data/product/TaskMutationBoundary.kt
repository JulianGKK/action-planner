package org.pag.data.product

import androidx.room3.Transactor
import org.pag.domain.*
import java.util.concurrent.Executor

/** Existing task proof seams delegate unchanged to the single shared implementation. */
internal typealias TaskWriteProgress = WorkspaceWriteProgress
internal suspend fun <T> confirmedTaskTransaction(tx: Transactor, context: StorageContext,
    progress: TaskWriteProgress, work: suspend () -> OperationsResult<T>): OperationsResult<T> =
    confirmedWorkspaceTransaction(tx,context,progress,{it is OperationsResult.Rejected},work)

internal class TaskMutationBoundary(access: StorageAccess, worker: Executor, main: Executor,
    mutations: WorkspaceMutationInvalidation, blocked: () -> Unit) {
    private val shared=WorkspaceWriteBoundary(access,worker,main,mutations,blocked)
    fun <T> submit(actionId:String,publish:(OperationsResult<T>)->Unit,
        work:(StorageContext,TaskWriteProgress)->OperationsResult<T>) =
        shared.submit(WorkspaceMutation(WorkspaceWriterFamily.TASK,actionId),publish,
            {OperationsResult.Rejected(OperationsFailure.STALE)},{it is OperationsResult.Value},work)
}
