package org.pag.prototype.product

import org.pag.domain.*

/** Bridge to the EXISTING metadata validity instance, not another snapshot owner. Construct
 * only beside the future trusted scope/permission adapter (#37); no UI boolean authority.
 * Not installed in product today. The live runtime uses UncachedWorkspaceMutations and has
 * no RAM-hit path. Remaining document/material/cost/protocol/leadership writers stay UNWIRED / NOT SAFE FOR RAM REUSE.
 */
internal class ActiveWorkspaceMutationAdapter(private val validity: ActiveWorkspaceValidity,
    private val binding: ActiveWorkspaceValidity.Binding) : WorkspaceMutationInvalidation {
    override fun beforeTaskQueue(actionId: String, generation: Long) =
        beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,actionId),generation)
    override fun beforeCoreQueue(mutation: WorkspaceMutation, generation: Long): WorkspaceMutationInvalidation.Pending? {
        if ((mutation.actionId != null && mutation.actionId != binding.actionId) || generation != binding.generation) {
            validity.revoke(); return null
        }
        val ticket = validity.beginMutation(affected(mutation.family)) ?: return null
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
        fun affected(family:WorkspaceWriterFamily):Set<ActiveWorkspaceValidity.Projection> =
            if(family==WorkspaceWriterFamily.TASK) parts else ActiveWorkspaceValidity.Projection.entries.toSet()

        val parts = setOf(ActiveWorkspaceValidity.Projection.TASKS,
            ActiveWorkspaceValidity.Projection.TASK_COUNTS, ActiveWorkspaceValidity.Projection.PEOPLE)
    }
}
