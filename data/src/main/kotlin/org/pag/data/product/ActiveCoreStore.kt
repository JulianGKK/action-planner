package org.pag.data.product

import androidx.room3.PooledConnection
import org.pag.domain.*

/** Called within ONE authorized Room transaction. Nothing is retained by this store.
 * Components prepare metadata first, then ONE synchronous aggregate admission seals/closes
 * its ledger before either materializer suspends. One action payload, no history/TaskBoard.
 */
internal class ActiveCoreStore(private val sql: PooledConnection, private val context: StorageContext,
    private val current: () -> Boolean, private val observe: (Phase) -> Unit = {},
    private val measured: (Metric, Long) -> Unit = { _, _ -> },
    private val report: (CoreReadAdmission.Report) -> Unit = {}) {
    enum class Phase { METADATA, ADMITTED, PEOPLE_ROW, TASK_ROW, COMPLETE }
    enum class Metric { METADATA, ADMISSION, MATERIALIZATION }
    private fun gate() {
        requireOperations(context)
        if (!current()) throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
    }
    suspend fun read(state: CompactTaskState, affectedPersonId: String? = null): ActiveCoreResult.Value {
        gate()
        if (context.generation != state.binding.generation) throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
        val start = System.nanoTime()
        val people = CompactPeopleStore(sql, context, current) {
            if (it == CompactPeopleStore.Phase.ROW) observe(Phase.PEOPLE_ROW)
        }.prepareCore(state)
        val tasks = CompactTaskStore(sql, context, current) {
            if (it == CompactTaskStore.Phase.ROW) observe(Phase.TASK_ROW)
        }.preparePage(state, CompactTaskQuery(affectedPersonId = affectedPersonId), null)
        measured(Metric.METADATA, System.nanoTime() - start)
        observe(Phase.METADATA); gate()

        val admissionStart = System.nanoTime()
        val admission = try { CoreReadAdmission.admit(listOf(people, tasks), envelope, report) }
            finally { measured(Metric.ADMISSION, System.nanoTime() - admissionStart) }
        observe(Phase.ADMITTED); gate()
        val materializationStart = System.nanoTime()
        val personPayload = people.materialize(admission)
        val taskPage = tasks.materialize(admission)
        gate()
        if (personPayload.action.id != state.binding.actionId || personPayload.action.status != ActionStatus.ACTIVE ||
            personPayload.people.state != state || personPayload.people.items.size > 32 ||
            taskPage.state != state || taskPage.query != CompactTaskQuery(affectedPersonId = affectedPersonId) || taskPage.items.size > 32)
            throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
        val corePeople=ActiveCorePeoplePage(state,personPayload.people.overall,personPayload.people.filteredCount,
            personPayload.people.items,personPayload.people.next!=null)
        val coreTasks=ActiveCoreTaskPage(state,taskPage.overall,taskPage.filtered,taskPage.items,taskPage.next!=null)
        val result = ActiveCoreResult.Value(ActiveCorePayload(state, personPayload.action, corePeople, personPayload.functions, coreTasks),
            admission.reservation, admission.retained, admission.transition)
        measured(Metric.MATERIALIZATION, System.nanoTime() - materializationStart)
        observe(Phase.COMPLETE); gate()
        return result
    }
    companion object {
        // Additional disjoint joint wrapper/plan/accounting reference capacity. The Action
        // itself belongs only to CompactPeopleStore's units, never the compact task units.
        // Includes bounded lifetime, revoke-listener and ViewModel slot metadata for §70.
        internal val envelope = ActiveCoreReservation(
            ActiveWorkspaceBudget.Units(objects = 24, lists = 2, listCapacity = 4, references = 64),
            ActiveWorkspaceBudget.Units(objects = 48, lists = 4, listCapacity = 16, references = 128))
    }
}
