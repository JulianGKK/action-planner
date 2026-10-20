package org.pag.data.product

import androidx.room3.withWriteTransaction
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.util.UUID

/** Legacy test/storage entrypoints keep their original transaction scope. Product writers
 * use these same bodies inside the shared confirmed OUTER transaction (no nested commit).
 */
internal suspend fun ActionDatabase.changeAction(expected:Action,changes:NewAction,context:StorageContext):ActionChangeResult =
    withWriteTransaction { ActionWriterStore(RoomCoreWriterData(this@changeAction),context).change(expected,changes) }
internal suspend fun ActionDatabase.advanceAction(id:String,expected:ActionStatus,context:StorageContext):ActionChangeResult =
    withWriteTransaction { ActionWriterStore(RoomCoreWriterData(this@advanceAction),context).advance(id,expected) }

internal class ActionWriterStore(private val data:CoreWriterData,private val context:StorageContext,
    private val now:()->Long=System::currentTimeMillis) {
    suspend fun create(action:NewAction):Action {
        requireOperations(context)
        if(!action.valid())throw IllegalArgumentException("P1-ACTION-INVALID")
        val row=ActionRow(UUID.randomUUID().toString(),action.title.trim(),action.description.trim(),
            action.startAt,action.expectedEndAt,now(),ActionStatus.DRAFT.name)
        data.actions.insert(row)
        return row.model()
    }
    suspend fun change(expected:Action,changes:NewAction):ActionChangeResult {
        if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
        val stored = data.actions.get(expected.id) ?: return rejected(ActionChangeRejection.NOT_FOUND)
        val current = stored.model()
        if (current != expected) return rejected(ActionChangeRejection.STALE)
        if (!ActionLifecycle.mayEdit(current.status)) return rejected(ActionChangeRejection.NOT_EDITABLE)
        if (!changes.valid()) return rejected(ActionChangeRejection.INVALID_FIELDS)
        if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
        val updated = stored.copy(title = changes.title.trim(), description = changes.description.trim(),
            startAt = changes.startAt, expectedEndAt = changes.expectedEndAt)
        if (data.actions.update(updated) != 1) throw ProductStorageFailure()
        if (!context.check(AccessPoint.COMMIT)) throw StorageAccessRevoked()
        return ActionChangeResult.Applied(updated.model())
    }
    suspend fun advance(id:String,expected:ActionStatus):ActionChangeResult {
        if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
        val stored = data.actions.get(id) ?: return rejected(ActionChangeRejection.NOT_FOUND)
        val current = stored.model()
        if (current.status != expected) return rejected(ActionChangeRejection.STALE)
        val next = ActionLifecycle.next(current.status) ?: return rejected(ActionChangeRejection.INVALID_TRANSITION)
        if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
        val updated = stored.copy(status = next.name, completedAt = if (next == ActionStatus.COMPLETED) now() else null)
        if (next == ActionStatus.ACTIVE) {
            val timestamp = now()
            if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
            val plans = data.operations.plans(id).associateBy { it.personId }
            if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
            val persons = data.persons.list(id)
            for (person in persons) {
                person.model()
                if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
                data.operations.insertState(OperationalRow(id, person.id, plans[person.id]?.primaryRoleId,
                    OperationalStatus.NO_STATUS.name, null, timestamp))
            }
            if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
        }
        if (data.actions.update(updated) != 1) throw ProductStorageFailure()
        if (!context.check(AccessPoint.COMMIT)) throw StorageAccessRevoked()
        return ActionChangeResult.Applied(updated.model())
    }
}
private fun rejected(reason:ActionChangeRejection)=ActionChangeResult.Rejected(reason)
