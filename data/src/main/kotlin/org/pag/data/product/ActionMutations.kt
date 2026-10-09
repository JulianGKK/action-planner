package org.pag.data.product

import androidx.room3.withWriteTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*

/** Persisted state is checked inside the same guarded transaction as the write. */
internal suspend fun ActionDatabase.changeAction(expected: Action, changes: NewAction,
    context: StorageContext): ActionChangeResult = withWriteTransaction {
    if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
    val stored = actions().get(expected.id) ?: return@withWriteTransaction rejected(ActionChangeRejection.NOT_FOUND)
    val current = stored.model()
    if (current != expected) return@withWriteTransaction rejected(ActionChangeRejection.STALE)
    if (!ActionLifecycle.mayEdit(current.status)) return@withWriteTransaction rejected(ActionChangeRejection.NOT_EDITABLE)
    if (!changes.valid()) return@withWriteTransaction rejected(ActionChangeRejection.INVALID_FIELDS)
    if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
    val updated = stored.copy(title = changes.title.trim(), description = changes.description.trim(),
        startAt = changes.startAt, expectedEndAt = changes.expectedEndAt)
    if (actions().update(updated) != 1) throw ProductStorageFailure()
    if (!context.check(AccessPoint.COMMIT)) throw StorageAccessRevoked()
    ActionChangeResult.Applied(updated.model())
}

internal suspend fun ActionDatabase.advanceAction(id: String, expected: ActionStatus,
    context: StorageContext): ActionChangeResult = withWriteTransaction {
    if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
    val stored = actions().get(id) ?: return@withWriteTransaction rejected(ActionChangeRejection.NOT_FOUND)
    val current = stored.model()
    if (current.status != expected) return@withWriteTransaction rejected(ActionChangeRejection.STALE)
    val next = ActionLifecycle.next(current.status)
        ?: return@withWriteTransaction rejected(ActionChangeRejection.INVALID_TRANSITION)
    if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
    val updated = stored.copy(status = next.name,
        completedAt = if (next == ActionStatus.COMPLETED) System.currentTimeMillis() else null)
    if (actions().update(updated) != 1) throw ProductStorageFailure()
    if (!context.check(AccessPoint.COMMIT)) throw StorageAccessRevoked()
    ActionChangeResult.Applied(updated.model())
}

private fun rejected(reason: ActionChangeRejection) = ActionChangeResult.Rejected(reason)
