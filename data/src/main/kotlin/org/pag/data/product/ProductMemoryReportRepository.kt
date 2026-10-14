package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import java.util.UUID
import java.util.concurrent.Executor

/** Existing short-lived READY/OPERATION/STEP/COMMIT/publication pattern, no P3b reads. */
class ProductMemoryReportRepository(app: Context, private val access: StorageAccess, private val inventory: ProductInventory,
    private val owner: () -> SessionDbKeyOwner?, private val worker: Executor, private val main: Executor,
    private val blocked: () -> Unit, private val now: () -> Long = System::currentTimeMillis) : MemoryReportRepository {
    private val database = ActionDatabaseAccess(app.applicationContext)
    override fun summary(actionId: String, publish: (OperationsResult<MemoryReportSummary>) -> Unit) =
        run(publish) { d, c -> d.withReadTransaction { MemoryReportStore(d, c, now).summary(actionId) } }
    override fun list(actionId: String, publish: (OperationsResult<List<MemoryReportHeader>>) -> Unit) =
        run(publish) { d, c -> d.withReadTransaction { MemoryReportStore(d, c, now).list(actionId) } }
    override fun detail(actionId: String, reportId: String, publish: (OperationsResult<MemoryReportDetail>) -> Unit) =
        run(publish) { d, c -> d.withReadTransaction { MemoryReportStore(d, c, now).detail(actionId, reportId) } }
    override fun create(actionId: String, authorLabel: String, firstSection: MemorySectionSave, publish: (OperationsResult<MemoryReportHeader>) -> Unit) =
        frozenWrite(publish, firstSection) { store, section -> store.create(actionId, authorLabel, section) }
    override fun saveSection(actionId: String, expected: MemoryReportHeader, section: MemorySectionSave, publish: (OperationsResult<MemoryReportHeader>) -> Unit) =
        frozenWrite(publish, section) { store, snapshot -> store.saveSection(actionId, expected, snapshot) }
    override fun complete(actionId: String, expected: MemoryReportHeader, publish: (OperationsResult<MemoryReportHeader>) -> Unit) =
        write(publish) { it.complete(actionId, expected) }
    override fun addAddendum(actionId: String, expected: MemoryReportHeader, content: String, publish: (OperationsResult<MemoryReportHeader>) -> Unit) =
        write(publish) { it.addAddendum(actionId, expected, content) }
    private fun frozenWrite(publish: (OperationsResult<MemoryReportHeader>) -> Unit, section: MemorySectionSave,
        work: suspend (MemoryReportStore, MemorySectionSave) -> OperationsResult<MemoryReportHeader>) {
        val frozen = section.copy(answers = section.answers.mapValues { (_, value) ->
            if (value is MemoryAnswerValue.Multi) value.copy(keys = value.keys.toList()) else value
        }, retainedConflicts = section.retainedConflicts.toSet())
        write(publish) { work(it, frozen) }
    }
    private fun <T> write(publish: (OperationsResult<T>) -> Unit, work: suspend (MemoryReportStore) -> OperationsResult<T>) =
        run(publish) { d, c -> d.withWriteTransaction { val result = work(MemoryReportStore(d, c, now)); requireOperations(c, AccessPoint.COMMIT); result } }
    private fun <T> run(publish: (OperationsResult<T>) -> Unit, work: suspend (ActionDatabase, StorageContext) -> OperationsResult<T>) {
        val c = access.acquire(AccessScope.READY) ?: return
        worker.execute {
            try {
                requireOperations(c, AccessPoint.OPERATION)
                if (inventory.state() != ProductStorageState.READY_TO_OPEN) throw ProductStorageFailure()
                val result = (owner() ?: throw ProductStorageFailure()).withDbKey(c) { key -> database.use(inventory.database, key, c) { work(it, c) } }
                main.execute { c.publish { publish(result) } }
            } catch (_: Exception) { main.execute { if (c.check(AccessPoint.STEP)) blocked() } }
        }
    }
}
internal class MemoryReportStore(private val db: ActionDatabase, private val c: StorageContext, private val now: () -> Long) {
    private suspend fun <T> step(work: suspend () -> T): T { requireOperations(c); return work() }
    private fun denied(reason: OperationsFailure) = OperationsResult.Rejected(reason)
    private suspend fun permission(a: String): OperationsFailure? {
        val status = step { db.actions().get(a)?.model()?.status } ?: return OperationsFailure.NOT_FOUND
        return if (MemoryReportRules.mayRead(status)) null else OperationsFailure.NOT_ALLOWED
    }
    private suspend fun checkedHeaders(a: String) {
        if (step { db.memoryReports().invalidHeaders(a) } != 0) throw ProductStorageFailure()
    }
    suspend fun summary(a: String): OperationsResult<MemoryReportSummary> {
        permission(a)?.let { return denied(it) }; checkedHeaders(a)
        val row = step { db.memoryReports().summary(a) }
        if (row.draftCount < 0 || row.completedCount < 0) throw ProductStorageFailure()
        return OperationsResult.Value(MemoryReportSummary(row.draftCount, row.completedCount))
    }
    suspend fun list(a: String): OperationsResult<List<MemoryReportHeader>> {
        permission(a)?.let { return denied(it) }; checkedHeaders(a)
        return OperationsResult.Value(step { db.memoryReports().list(a) }.map { it.model() })
    }
    private suspend fun materialize(h: MemoryReportHeader): MemoryReportDetail {
        val rows = step { db.memoryReports().answers(h.actionId, h.id) }
        val values = rows.associate { row ->
            val q = MemoryQuestionnaireV1.question(row.questionKey) ?: throw ProductStorageFailure()
            if (q.sectionOrdinal > h.lastSavedSectionOrdinal || row.actionId != h.actionId || row.reportId != h.id) throw ProductStorageFailure()
            row.questionKey to (MemoryAnswerCodec.decode(row.questionKey, row.valueKind, row.payload) ?: throw ProductStorageFailure())
        }
        if (values.size != rows.size || step { db.memoryReports().invalidAddendumTypes(h.actionId, h.id) } != 0) throw ProductStorageFailure()
        val addenda = step { db.memoryReports().addenda(h.actionId, h.id) }
        if ((h.status == MemoryReportStatus.DRAFT && addenda.isNotEmpty()) || addenda.map { it.sequence } != addenda.indices.toList() ||
            addenda.any { it.actionId != h.actionId || it.reportId != h.id || !MemoryReportRules.addendumValid(it.content) || it.content != it.content.trim() || it.recordedAt < 0 }) throw ProductStorageFailure()
        return MemoryReportDetail(h, values, addenda.map { MemoryAddendum(it.sequence, it.content, it.recordedAt) })
    }
    suspend fun detail(a: String, id: String): OperationsResult<MemoryReportDetail> {
        permission(a)?.let { return denied(it) }; checkedHeaders(a)
        val h = step { db.memoryReports().get(a, id) }?.model() ?: return denied(OperationsFailure.NOT_FOUND)
        return OperationsResult.Value(materialize(h))
    }
    private fun validatedSection(section: MemorySectionSave, old: Map<String, MemoryAnswerValue>): Map<String, MemoryAnswerValue>? {
        if (section.sectionOrdinal !in 0..16 || section.answers.keys.any { MemoryQuestionnaireV1.question(it)?.sectionOrdinal != section.sectionOrdinal }) return null
        val normalized = section.answers.mapValues { (key, value) -> MemoryAnswerCodec.normalize(key, value) ?: return null }
        val combined = old.filterKeys { MemoryQuestionnaireV1.question(it)?.sectionOrdinal != section.sectionOrdinal } + normalized
        val conflicts = normalized.keys.filter { key -> MemoryQuestionnaireV1.question(key)!!.dependency?.active(combined) == false }.toSet()
        if (conflicts != section.retainedConflicts) return null
        return normalized
    }
    private fun rows(a: String, id: String, answers: Map<String, MemoryAnswerValue>) = answers.map { (key, value) ->
        val encoded = MemoryAnswerCodec.encode(key, value) ?: throw ProductStorageFailure()
        MemoryAnswerRow(a, id, key, encoded.valueKind, encoded.payload)
    }
    private fun revision(old: Long): Long = try { MemoryReportRules.nextRevision(old) } catch (_: ArithmeticException) { throw ProductStorageFailure() }
    private fun time(): Long = now().also { if (it < 0) throw ProductStorageFailure() }
    suspend fun create(a: String, author: String, section: MemorySectionSave): OperationsResult<MemoryReportHeader> {
        permission(a)?.let { return denied(it) }
        if (section.sectionOrdinal != 0 || !MemoryReportRules.authorValid(author) || section.authorLabel?.trim()?.let { it != author.trim() } == true) return denied(OperationsFailure.INVALID)
        val answers = validatedSection(section, emptyMap()) ?: return denied(OperationsFailure.INVALID)
        val time = time()
        val h = MemoryReportHeader(UUID.randomUUID().toString(), a, author.trim(), 1, MemoryReportStatus.DRAFT, 0, 0, time, time, null)
        step { db.memoryReports().insert(h.row()) }; step { db.memoryReports().insertAnswers(rows(a, h.id, answers)) }
        return OperationsResult.Value(h)
    }
    private suspend fun existing(a: String, expected: MemoryReportHeader): OperationsResult<MemoryReportDetail> {
        permission(a)?.let { return denied(it) }; checkedHeaders(a)
        val h = step { db.memoryReports().get(a, expected.id) }?.model() ?: return denied(OperationsFailure.NOT_FOUND)
        if (expected.actionId != a || h != expected) return denied(OperationsFailure.STALE)
        return OperationsResult.Value(materialize(h))
    }
    private suspend fun update(h: MemoryReportHeader) {
        if (!MemoryReportRules.headerValid(h) || step { db.memoryReports().update(h.row()) } != 1) throw ProductStorageFailure()
    }
    suspend fun saveSection(a: String, expected: MemoryReportHeader, section: MemorySectionSave): OperationsResult<MemoryReportHeader> {
        val result = existing(a, expected); if (result is OperationsResult.Rejected) return result
        val old = (result as OperationsResult.Value).value
        if (expected.status != MemoryReportStatus.DRAFT || !MemoryReportRules.maySave(expected.lastSavedSectionOrdinal, section.sectionOrdinal)) return denied(OperationsFailure.NOT_ALLOWED)
        if (section.sectionOrdinal != 0 && section.authorLabel != null) return denied(OperationsFailure.INVALID)
        val author = section.authorLabel?.trim() ?: expected.authorLabel
        if (!MemoryReportRules.authorValid(author)) return denied(OperationsFailure.INVALID)
        val answers = validatedSection(section, old.answers) ?: return denied(OperationsFailure.INVALID)
        val h = expected.copy(authorLabel = author, revision = revision(expected.revision),
            lastSavedSectionOrdinal = maxOf(expected.lastSavedSectionOrdinal, section.sectionOrdinal), updatedAt = time())
        step { db.memoryReports().clearSection(a, h.id, MemoryQuestionnaireV1.section(section.sectionOrdinal).map { it.key }) }
        step { db.memoryReports().insertAnswers(rows(a, h.id, answers)) }; update(h)
        return OperationsResult.Value(h)
    }
    suspend fun complete(a: String, expected: MemoryReportHeader): OperationsResult<MemoryReportHeader> {
        val old = existing(a, expected); if (old is OperationsResult.Rejected) return old
        if (!MemoryReportRules.mayComplete(expected)) return denied(OperationsFailure.NOT_ALLOWED)
        val time = time(); if (time < expected.createdAt) throw ProductStorageFailure()
        val h = expected.copy(status = MemoryReportStatus.COMPLETED, revision = revision(expected.revision), updatedAt = time, completedAt = time)
        update(h); return OperationsResult.Value(h)
    }
    suspend fun addAddendum(a: String, expected: MemoryReportHeader, content: String): OperationsResult<MemoryReportHeader> {
        val result = existing(a, expected); if (result is OperationsResult.Rejected) return result
        if (expected.status != MemoryReportStatus.COMPLETED) return denied(OperationsFailure.NOT_ALLOWED)
        if (!MemoryReportRules.addendumValid(content)) return denied(OperationsFailure.INVALID)
        val sequence = (result as OperationsResult.Value).value.addenda.size
        Math.addExact(sequence, 1)
        val time = time(); val h = expected.copy(revision = revision(expected.revision), updatedAt = time)
        step { db.memoryReports().insertAddendum(MemoryAddendumRow(a, h.id, sequence, content.trim(), time)) }
        update(h); return OperationsResult.Value(h)
    }
}
