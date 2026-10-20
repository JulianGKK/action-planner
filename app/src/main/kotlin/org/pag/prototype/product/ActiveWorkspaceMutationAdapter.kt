package org.pag.prototype.product

import org.pag.domain.*

/** Bridge to the EXISTING metadata validity instance, not another snapshot owner. Construct
 * only beside the future trusted scope/permission adapter (#37); no UI boolean authority.
 * Not installed in product today. The live runtime uses UncachedWorkspaceMutations and has
 * no RAM-hit path. Other writer families remain UNWIRED / NOT SAFE FOR RAM REUSE.
 */
internal class ActiveWorkspaceMutationAdapter(private val validity: ActiveWorkspaceValidity,
    private val binding: ActiveWorkspaceValidity.Binding) : WorkspaceMutationInvalidation {
    override fun beforeTaskQueue(actionId: String, generation: Long): WorkspaceMutationInvalidation.Pending? {
        if (actionId != binding.actionId || generation != binding.generation) {
            validity.revoke(); return null
        }
        val ticket = validity.beginMutation(parts) ?: return null
        return object : WorkspaceMutationInvalidation.Pending {
            override fun finish(completion: WorkspaceWriteCompletion): Boolean = validity.finishMutation(ticket,
                when(completion.outcome) {
                    WorkspaceWriteOutcome.COMMITTED -> ActiveWorkspaceValidity.Outcome.COMMITTED
                    WorkspaceWriteOutcome.ROLLED_BACK -> ActiveWorkspaceValidity.Outcome.ROLLED_BACK
                    WorkspaceWriteOutcome.UNKNOWN -> ActiveWorkspaceValidity.Outcome.UNKNOWN
                }, if(completion.cleanup == WorkspaceWriteCleanup.COMPLETE) ActiveWorkspaceValidity.Cleanup.COMPLETE
                   else ActiveWorkspaceValidity.Cleanup.FAILED)
            override fun isCurrent() = validity.writerResultCurrent(ticket)
        }
    }
    companion object {
        val parts = setOf(ActiveWorkspaceValidity.Projection.TASKS,
            ActiveWorkspaceValidity.Projection.TASK_COUNTS, ActiveWorkspaceValidity.Projection.PEOPLE)
    }
}
