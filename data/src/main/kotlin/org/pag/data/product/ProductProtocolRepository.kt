package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** SENSITIVE DB-only protocol. Uses the existing READY gate, never a Leadership grant. */
class ProductProtocolRepository(
    app: Context, private val access: StorageAccess, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?, private val worker: Executor,
    private val main: Executor, private val blocked: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) : ProtocolRepository {
    private val database = ActionDatabaseAccess(app.applicationContext)
    override fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit) =
        read(publish) { it.summary(actionId) }
    override fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit) =
        read(publish) { it.timeline(actionId) }
    override fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit) =
        read(publish) { it.detail(actionId, entryId) }
    override fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput,
        publish: (ProtocolResult<ProtocolSaved>) -> Unit) {
        val copy = ProtocolRules.normalized(input)
        write(publish) { it.create(actionId, expectedActionStatus, copy) }
    }
    override fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision,
        revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean,
        publish: (ProtocolResult<ProtocolSaved>) -> Unit) {
        val copy = ProtocolRules.normalized(input)
        val expected = frozen(expectedRevision)
        write(publish) { it.revise(actionId, expectedActionStatus, expected, revisionType, copy, clearMistaken) }
    }
    override fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision,
        publish: (ProtocolResult<ProtocolSaved>) -> Unit) {
        val expected = frozen(expectedRevision)
        write(publish) { it.markMistaken(actionId, expectedActionStatus, expected) }
    }
    private fun frozen(e: ProtocolExpectedRevision) = e.copy(revision = e.revision.copy(input = e.revision.input.copy(
        affectedPersonIds = e.revision.input.affectedPersonIds.toSet(), officialIdentifiers = e.revision.input.officialIdentifiers.toList())))
    private fun <T> read(publish: (ProtocolResult<T>) -> Unit, work: suspend (ProtocolStore) -> ProtocolResult<T>) =
        run(publish) { d, c -> d.withReadTransaction { work(ProtocolStore(d, c, now)) } }
    private fun <T> write(publish: (ProtocolResult<T>) -> Unit, work: suspend (ProtocolStore) -> ProtocolResult<T>) =
        run(publish) { d, c -> d.withWriteTransaction {
            val result = work(ProtocolStore(d, c, now))
            requireOperations(c, AccessPoint.COMMIT)
            result
        } }
    private fun <T> run(publish: (ProtocolResult<T>) -> Unit, work: suspend (ActionDatabase, StorageContext) -> ProtocolResult<T>) {
        val c = access.acquire(AccessScope.READY) ?: return
        worker.execute {
            try {
                requireOperations(c, AccessPoint.OPERATION)
                if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
                val result = (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key ->
                    database.use(inventory.database, key, c) { work(it, c) }
                }
                main.execute { c.publish { publish(result) } }
            } catch (_: Exception) {
                main.execute { if (c.check(AccessPoint.STEP)) blocked() }
            }
        }
    }
}

/** All methods run inside the caller's single guarded read/write transaction. */
internal class ProtocolStore(private val db: ActionDatabase, private val context: StorageContext, private val now: () -> Long) {
    private suspend fun <T> step(work: suspend () -> T): T { requireOperations(context); return work() }
    private fun denied(reason: ProtocolFailure) = ProtocolResult.Rejected(reason)
    private suspend fun status(a: String) = step { db.actions().get(a)?.model()?.status }
    private suspend fun permission(a: String, expected: ActionStatus? = null): ProtocolFailure? {
        val state = status(a) ?: return ProtocolFailure.NOT_FOUND
        if (!(if (expected == null) ProtocolRules.mayRead(state) else ProtocolRules.mayWrite(state))) return ProtocolFailure.INVALID_STATE
        if (expected != null && expected != state) return ProtocolFailure.STALE
        return null
    }
    private suspend fun checkOriginals(a: String) {
        if (step { db.protocol().missingOriginals(a) } != 0L) throw ProductStorageFailure()
    }
    suspend fun summary(a: String): ProtocolResult<ProtocolSummary> {
        permission(a)?.let { return denied(it) }
        checkOriginals(a)
        val row = step { db.protocol().summary(a) }
        return ProtocolResult.Value(ProtocolSummary(row.keyEventCount, row.lastEntryRecordedAt))
    }
    suspend fun timeline(a: String): ProtocolResult<List<ProtocolTimelineItem>> {
        permission(a)?.let { return denied(it) }
        checkOriginals(a)
        return ProtocolResult.Value(step { db.protocol().timeline(a) }.map { it.model() })
    }
    private suspend fun contents(a: String, rows: List<ProtocolRevisionRow>): List<ProtocolRevision> {
        if (rows.isEmpty()) throw ProductStorageFailure()
        val entryId = rows.first().entryId
        val affected = step { db.protocol().affected(a, entryId) }.groupBy { it.revisionId }
        val reported = step { db.protocol().reported(a, entryId) }.associateBy { it.revisionId }
        val identifiers = step { db.protocol().identifiers(a, entryId) }.groupBy { it.revisionId }
        return rows.map { row ->
            val ids = identifiers[row.id].orEmpty()
            if (ids.map { it.position } != ids.indices.toList()) throw ProductStorageFailure()
            row.model(affected[row.id].orEmpty().map { it.personId }.toSet(), reported[row.id]?.personId,
                ids.map { ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.valueOf(it.type), it.value) })
        }
    }
    private fun validateHistory(entry: ProtocolEntry, revisions: List<ProtocolRevision>) {
        revisions.forEachIndexed { index, r ->
            if (r.entryId != entry.id || r.actionId != entry.actionId || r.revisionNumber != index.toLong() + 1) throw ProductStorageFailure()
            if (index == 0) {
                if (r.mistaken || r.recordedAfterCompletion != entry.createdAfterCompletion) throw ProductStorageFailure()
            } else {
                val prev = revisions[index - 1]
                val clear = prev.mistaken && !r.mistaken && r.revisionType == ProtocolRevisionType.CORRECTION
                if (ProtocolRules.nextMistaken(prev.mistaken, r.revisionType, clear) != r.mistaken ||
                    r.input.templateKey != prev.input.templateKey) throw ProductStorageFailure()
            }
        }
    }
    suspend fun detail(a: String, entryId: String): ProtocolResult<ProtocolEntryDetail> {
        permission(a)?.let { return denied(it) }
        val entry = step { db.protocol().entry(a, entryId) }?.model() ?: return denied(ProtocolFailure.NOT_FOUND)
        val revisions = contents(a, step { db.protocol().revisions(a, entryId) })
        validateHistory(entry, revisions)
        val ids = revisions.flatMap { it.input.affectedPersonIds + listOfNotNull(it.input.reportedByPersonId) }.toSet()
        val people = if (ids.isEmpty()) emptyList() else step { db.protocol().persons(a, ids) }
        if (people.map { it.id }.toSet() != ids) throw ProductStorageFailure()
        val byId = people.associateBy { it.id }
        return ProtocolResult.Value(ProtocolEntryDetail(entry, revisions.map { r ->
            ProtocolRevisionDetail(r, people.filter { it.id in r.input.affectedPersonIds }, r.input.reportedByPersonId?.let { byId.getValue(it) })
        }))
    }
    private suspend fun references(a: String, input: ProtocolInput): Boolean {
        for (id in input.affectedPersonIds + listOfNotNull(input.reportedByPersonId)) {
            if (step { db.persons().get(a, id) } == null) return false
        }
        return true
    }
    suspend fun create(a: String, expectedStatus: ActionStatus, input: ProtocolInput): ProtocolResult<ProtocolSaved> {
        permission(a, expectedStatus)?.let { return denied(it) }
        val i = ProtocolRules.normalized(input)
        if (!ProtocolRules.valid(i) || (i.templateKey != null && i.classification != ProtocolClassification.KEY_EVENT)) return denied(ProtocolFailure.INVALID)
        if (!references(a, i)) return denied(ProtocolFailure.NOT_FOUND)
        val entry = ProtocolEntry(UUID.randomUUID().toString(), a, expectedStatus == ActionStatus.COMPLETED)
        val revision = ProtocolRevision(UUID.randomUUID().toString(), a, entry.id, 1,
            ProtocolRevisionType.ORIGINAL, i, now(), entry.createdAfterCompletion, false)
        step { db.protocol().insert(ProtocolEntryRow(entry.id, a, entry.createdAfterCompletion)) }
        insert(revision)
        return ProtocolResult.Value(ProtocolSaved(entry.id, revision.id, 1))
    }
    private suspend fun existing(a: String, expectedStatus: ActionStatus, expected: ProtocolExpectedRevision): ProtocolResult<ProtocolRevision> {
        permission(a, expectedStatus)?.let { return denied(it) }
        if (expected.entry.actionId != a || expected.revision.actionId != a || expected.revision.entryId != expected.entry.id)
            return denied(ProtocolFailure.WRONG_ACTION)
        val entry = step { db.protocol().entry(a, expected.entry.id) }?.model() ?: return denied(ProtocolFailure.NOT_FOUND)
        val currentRow = step { db.protocol().current(a, entry.id) } ?: throw ProductStorageFailure()
        val current = contents(a, listOf(currentRow)).single()
        if (entry != expected.entry || current != expected.revision) return denied(ProtocolFailure.STALE)
        return ProtocolResult.Value(current)
    }
    suspend fun revise(a: String, expectedStatus: ActionStatus, expected: ProtocolExpectedRevision, type: ProtocolRevisionType,
        input: ProtocolInput, clearMistaken: Boolean = false): ProtocolResult<ProtocolSaved> {
        val found = existing(a, expectedStatus, expected)
        if (found is ProtocolResult.Rejected) return found
        val old = (found as ProtocolResult.Value).value
        if (type != ProtocolRevisionType.CORRECTION && type != ProtocolRevisionType.ADDENDUM) return denied(ProtocolFailure.INVALID_STATE)
        val mistaken = ProtocolRules.nextMistaken(old.mistaken, type, clearMistaken) ?: return denied(ProtocolFailure.INVALID_STATE)
        val i = ProtocolRules.normalized(input)
        if (!ProtocolRules.valid(i) || i.templateKey != old.input.templateKey) return denied(ProtocolFailure.INVALID)
        if (!references(a, i)) return denied(ProtocolFailure.NOT_FOUND)
        return append(old, expectedStatus, type, i, mistaken)
    }
    suspend fun markMistaken(a: String, expectedStatus: ActionStatus, expected: ProtocolExpectedRevision): ProtocolResult<ProtocolSaved> {
        val found = existing(a, expectedStatus, expected)
        if (found is ProtocolResult.Rejected) return found
        val old = (found as ProtocolResult.Value).value
        val mistaken = ProtocolRules.nextMistaken(old.mistaken, ProtocolRevisionType.MARKED_MISTAKEN) ?: return denied(ProtocolFailure.INVALID_STATE)
        return append(old, expectedStatus, ProtocolRevisionType.MARKED_MISTAKEN, old.input, mistaken)
    }
    private suspend fun append(old: ProtocolRevision, state: ActionStatus, type: ProtocolRevisionType, input: ProtocolInput,
        mistaken: Boolean): ProtocolResult<ProtocolSaved> {
        if (old.revisionNumber == Long.MAX_VALUE) return denied(ProtocolFailure.INVALID_STATE)
        val next = old.copy(id = UUID.randomUUID().toString(), revisionNumber = old.revisionNumber + 1,
            revisionType = type, input = input, recordedAt = now(), recordedAfterCompletion = state == ActionStatus.COMPLETED, mistaken = mistaken)
        insert(next)
        return ProtocolResult.Value(ProtocolSaved(next.entryId, next.id, next.revisionNumber))
    }
    private suspend fun insert(r: ProtocolRevision) {
        val i = r.input
        step { db.protocol().insert(ProtocolRevisionRow(r.id, r.actionId, r.entryId, r.revisionNumber, r.revisionType.name,
            i.classification.name, i.templateKey, i.title, i.description, i.occurredAt, i.occurredAtApproximate,
            r.recordedAt, r.recordedAfterCompletion, r.mistaken, i.sourceType?.name, i.placeLabel,
            i.announcementText, i.announcementKind?.name, i.statedLegalBasis)) }
        for (id in i.affectedPersonIds) step { db.protocol().insert(ProtocolAffectedRow(r.actionId, r.id, id)) }
        i.reportedByPersonId?.let { id -> step { db.protocol().insert(ProtocolReportedRow(r.actionId, r.id, id)) } }
        i.officialIdentifiers.forEachIndexed { position, value ->
            step { db.protocol().insert(ProtocolIdentifierRow(r.actionId, r.id, position, value.type.name, value.value)) }
        }
    }
}
