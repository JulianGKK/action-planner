package org.pag.data.product

import androidx.room3.PooledConnection
import androidx.sqlite.SQLiteStatement
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.pag.domain.*

/**
 * One consistent read transaction supplied by Room. No DAO full-list calls. Statements are
 * confined to usePrepared, no connection escapes. Only explicit test callers currently use this.
 * usePrepared statement scope does NOT prove SQLCipher Cursor close (known integration blocker).
 * UTF8 byte lengths include embedded NUL. Decoding is strict, never SQLite getText replacement.
 */
internal class BoundedTaskStore(private val sql: PooledConnection, private val context: StorageContext,
    private val current: () -> Boolean, private val observe: (Phase) -> Unit = {}) {
    enum class Phase { METADATA, ADMITTED, ROW, TEXT, COMPLETE }
    private data class Text(val name: String, val nullable: Boolean = false)
    private data class Number(val name: String, val nullable: Boolean = false)
    private data class Table(val name: String, val key: String, val texts: List<Text>, val numbers: List<Number>)
    private data class Size(val table: Table, val rows: Long, val maximum: List<Long>, val bytes: List<Long>)
    private fun reject(status: BoundedActiveStatus): Nothing = throw BoundedActiveRejected(status)
    private fun invalid(): Nothing = reject(BoundedActiveStatus.INVALID_DATA)
    private fun gate() {
        requireOperations(context)
        if (!current()) reject(BoundedActiveStatus.ACCESS_REVOKED)
    }
    private suspend fun <T> query(statement: String, action: String? = null,
        block: (SQLiteStatement) -> T): T {
        gate()
        return sql.usePrepared(statement) { s ->
            if (action != null) s.bindText(1, action)
            gate(); block(s).also { gate() }
        }
    }
    private fun integer(s: SQLiteStatement, index: Int): Long {
        if (s.isNull(index) || s.getColumnType(index) != 1) invalid()
        return s.getLong(index)
    }
    private suspend fun scalar(statement: String, action: String): Long = query(statement, action) {
        if (!it.step()) invalid()
        val result = integer(it, 0)
        if (it.step()) invalid()
        result
    }

    suspend fun read(binding: BoundedActiveBinding): BoundedTaskResult.Value {
        gate()
        if (context.generation != binding.generation) reject(BoundedActiveStatus.ACCESS_REVOKED)
        // CAST(TEXT AS BLOB) uses the DB encoding. Do not guess on UTF16 databases.
        query("PRAGMA encoding") { if (!it.step() || it.getText(0) != "UTF-8" || it.step()) invalid() }
        val actionId = binding.actionId
        val phase = scalar("SELECT COALESCE((SELECT CASE WHEN status='ACTIVE' THEN 1 " +
            "WHEN status IN (${ActionStatus.entries.joinToString { "'${it.name}'" }}) THEN 0 ELSE -1 END FROM actions WHERE id=?),0)", actionId)
        if (phase < 0) invalid()
        if (phase == 0L) reject(BoundedActiveStatus.ACCESS_REVOKED)

        // No payload strings/row lists: exactly three fixed-size metadata records.
        val heads = tables.map { t -> head(t, actionId) }
        if (heads[0].rows != 1L) invalid()
        validateRelations(actionId)
        val rows = checked { heads.fold(0L) { a, b -> Math.addExact(a, b.rows) } }
        // Lower bound prevents both huge result counts and unsafe subsequent SQL SUMs.
        if (rows > ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES / 128) reject(BoundedActiveStatus.BUDGET_REFUSED)
        heads.forEach { h ->
            if (h.maximum.any { it > ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES }) reject(BoundedActiveStatus.BUDGET_REFUSED)
        }
        val sizes = heads.map { h -> totals(h, actionId) }
        val byteCount = checked { sizes.fold(0L) { a, s -> s.bytes.fold(a, Math::addExact) } }
        val fieldCount = checked { sizes.fold(0L) { a, s ->
            Math.addExact(a, Math.multiplyExact(s.rows, s.table.texts.size.toLong()))
        } }

        val charges = reserve(rows, fieldCount, byteCount)
        observe(Phase.ADMITTED)
        gate()

        var actionChecked = false
        val tasks = ArrayList<Task>()
        val events = ArrayList<TaskEvent>()
        for (size in sizes) scan(size, actionId) { t, texts, numbers ->
            fun text(i: Int) = texts[i] ?: invalid()
            fun number(i: Int) = numbers[i] ?: invalid()
            try {
                when (t.name) {
                    "actions" -> {
                        if (text(0) != actionId || text(3) != "ACTIVE") invalid()
                        ActionRow(text(0), text(1), text(2), number(0), number(1), number(2),
                            text(3), text(4), numbers[3]).model()
                        actionChecked = true
                    }
                    "tasks" -> {
                        if (text(1) != actionId) invalid()
                        tasks.add(TaskRow(text(0), text(1), text(2), text(3), text(4), texts[5],
                            texts[6], texts[7], numbers[0], texts[8], number(1), number(2), numbers[3], editRevision = number(4)).model())
                    }
                    "task_events" -> {
                        if (text(1) != actionId) invalid()
                        events.add(TaskEventRow(text(0), text(1), text(2), text(3), number(0)).model())
                    }
                }
            } catch (_: ProductStorageFailure) { invalid() }
              catch (_: IllegalArgumentException) { invalid() }
        }
        // Scan is rowid ordered. Stable time sort preserves legacy occurredAt,rowid event order.
        events.sortBy { it.occurredAt }
        gate(); observe(Phase.COMPLETE); gate()
        if (!actionChecked) invalid()
        return BoundedTaskResult.Value(TaskBoard(actionId, tasks, events),
            BoundedTaskReservation(units(rows, fieldCount, byteCount, 4), units(rows, fieldCount, byteCount, 16)),
            charges.first, charges.second)
    }

    /**
     * Freeze both upper-bound reservations synchronously. Room can resume a coroutine on a
     * different IO thread, so its thread-confined budget ledger must NOT cross a suspension.
     * Seal only freezes charges here, never declares CONTENT. Subsequent scans must prove all
     * actual counts/byte lengths stay inside these reservations before any result can return.
     * No pre-existing retained payload is accepted by this test-only entry path.
     */
    private fun reserve(rows: Long, fields: Long, bytes: Long): Pair<Long, Long> {
        val budget = ActiveWorkspaceBudget()
        try {
            val retained = budget.beginRetained()!!
            // Task/event graph, board projections/sorting capacity and duplicate string identities.
            admit(budget.admit(retained, units(rows, fields, bytes, 4)))
            check(budget.seal(retained))
            val transition = budget.beginTransition()!!
            // Raw blobs, decoder buffers, trim work, row wrappers, stable sort and builders.
            admit(budget.admit(transition, units(rows, fields, bytes, 16)))
            check(budget.seal(transition))
            return budget.accounting(retained)!!.chargedBytes to budget.accounting(transition)!!.chargedBytes
        } finally { budget.close() }
    }

    private fun units(rows: Long, fields: Long, bytes: Long, copies: Long): ActiveWorkspaceBudget.Units = checked {
        ActiveWorkspaceBudget.Units(objects = Math.multiplyExact(rows, copies * 9),
            strings = Math.multiplyExact(fields, copies), utf16Units = Math.multiplyExact(bytes, copies),
            arrays = Math.multiplyExact(fields, copies), arrayPayloadBytes = Math.addExact(Math.multiplyExact(bytes,copies),Math.multiplyExact(rows,copies*8)),
            lists = 16, listCapacity = Math.multiplyExact(rows, copies),
            maps = 8, mapCapacity = Math.multiplyExact(rows, copies),
            references = Math.multiplyExact(rows, copies * 16))
    }
    private fun <T> checked(work: () -> T): T = try { work() }
        catch (_: ArithmeticException) { reject(BoundedActiveStatus.BUDGET_REFUSED) }
    private fun admit(decision: ActiveWorkspaceBudget.Decision) {
        when (decision) {
            ActiveWorkspaceBudget.Decision.ADMITTED -> Unit
            ActiveWorkspaceBudget.Decision.BUDGET_REFUSED -> reject(BoundedActiveStatus.BUDGET_REFUSED)
            ActiveWorkspaceBudget.Decision.INVALID_DATA -> invalid()
        }
    }

    private suspend fun head(t: Table, action: String): Size {
        val checks = t.texts.map { "(typeof(${it.name})!='text'${if (it.nullable) " AND ${it.name} IS NOT NULL" else ""})" } +
            t.numbers.map { "(typeof(${it.name})!='integer'${if (it.nullable) " AND ${it.name} IS NOT NULL" else ""})" } +
            when (t.name) {
                "actions" -> listOf("sensitivity!='SENSITIVE'", "expectedEndAt<startAt", "completedAt IS NOT NULL")
                "tasks" -> listOf("priority NOT IN (${TaskPriority.entries.joinToString { "'${it.name}'" }})",
                    "status NOT IN (${TaskStatus.entries.joinToString { "'${it.name}'" }})",
                    "(assigneePersonId IS NOT NULL AND assigneeRoleId IS NOT NULL)",
                    "((status='DONE') != (completedAt IS NOT NULL))")
                "task_events" -> listOf("type NOT IN (${TaskEventType.entries.joinToString { "'${it.name}'" }})")
                else -> emptyList()
            }
        val maxima = t.texts.joinToString { "COALESCE(MAX(length(CAST(${it.name} AS BLOB))),0)" }
        return query("SELECT COUNT(*),COALESCE(MAX(${checks.joinToString(" OR ")}),0),$maxima FROM ${t.name} WHERE ${t.key}=?", action) { s ->
            observe(Phase.METADATA)
            if (!s.step()) invalid()
            val count = integer(s, 0)
            if (count < 0 || integer(s, 1) != 0L) invalid()
            val max = t.texts.indices.map { integer(s, it + 2).also { n -> if (n < 0) invalid() } }
            if (s.step()) invalid()
            Size(t, count, max, emptyList())
        }
    }
    private suspend fun totals(h: Size, action: String): Size {
        // rows<=65536, each length<=8MiB, so each SQL SUM is <=2^39. Never TOTAL()/Double.
        val sums = h.table.texts.joinToString { "COALESCE(SUM(length(CAST(${it.name} AS BLOB))),0)" }
        return query("SELECT $sums FROM ${h.table.name} WHERE ${h.table.key}=?", action) { s ->
            if (!s.step()) invalid()
            val bytes = h.table.texts.indices.map { i -> integer(s, i).also {
                if (it < 0 || it > Math.multiplyExact(h.rows, h.maximum[i])) invalid()
            } }
            if (s.step()) invalid()
            h.copy(bytes = bytes)
        }
    }

    private suspend fun validateRelations(action: String) {
        val bad = scalar("SELECT EXISTS(SELECT 1 FROM tasks t WHERE t.actionId=? AND (" +
            "(t.assigneePersonId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM persons p WHERE p.actionId=t.actionId AND p.id=t.assigneePersonId)) OR " +
            "(t.affectedPersonId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM persons p WHERE p.actionId=t.actionId AND p.id=t.affectedPersonId)) OR " +
            "(t.assigneeRoleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM roles r WHERE r.actionId=t.actionId AND r.id=t.assigneeRoleId))))", action)
        if (bad != 0L) invalid()
        if (scalar("SELECT EXISTS(SELECT 1 FROM task_events e WHERE e.actionId=? AND " +
            "NOT EXISTS(SELECT 1 FROM tasks t WHERE t.actionId=e.actionId AND t.id=e.taskId))", action) != 0L) invalid()
    }

    private suspend fun scan(h: Size, action: String, accept: (Table, List<String?>, List<Long?>) -> Unit) {
        val t = h.table
        var last: Long? = null
        var rows = 0L
        val actual = LongArray(t.texts.size)
        val fields = t.texts.flatMapIndexed { i, f -> listOf("length(CAST(${f.name} AS BLOB))",
            "CASE WHEN length(CAST(${f.name} AS BLOB))<=${h.maximum[i]} THEN CAST(${f.name} AS BLOB) ELSE NULL END") } + t.numbers.map { it.name }
        do {
            var batch = 0
            query("SELECT rowid,${fields.joinToString()} FROM ${t.name} WHERE ${t.key}=? AND (? IS NULL OR rowid>?) ORDER BY rowid LIMIT 32", action) { s ->
                if (last == null) { s.bindNull(2); s.bindNull(3) } else { s.bindLong(2, last!!); s.bindLong(3, last!!) }
                while (s.step()) {
                    gate(); observe(Phase.ROW); gate()
                    val id = integer(s, 0)
                    if (last != null && id <= last!!) invalid()
                    last = id; rows++; batch++
                    if (rows > h.rows || batch > 32) invalid()
                    val text = t.texts.mapIndexed { i, f ->
                        val at = 1 + i * 2
                        if (s.isNull(at)) {
                            if (!f.nullable || !s.isNull(at + 1)) invalid()
                            null
                        } else {
                            val length = integer(s, at)
                            if (length < 0 || length > h.maximum[i] || length > Int.MAX_VALUE || s.isNull(at + 1)) invalid()
                            actual[i] = Math.addExact(actual[i], length)
                            if (actual[i] > h.bytes[i]) invalid()
                            gate(); observe(Phase.TEXT); gate()
                            val bytes = s.getBlob(at + 1)
                            if (bytes.size.toLong() != length) invalid()
                            val value = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
                                catch (_: java.nio.charset.CharacterCodingException) { invalid() }
                            // For valid UTF8, UTF16 units <= encoded bytes, including NUL/surrogate pairs.
                            if (value.length.toLong() > length) invalid()
                            value
                        }
                    }
                    val nums = t.numbers.mapIndexed { i, f ->
                        val at = 1 + t.texts.size * 2 + i
                        if (s.isNull(at)) { if (!f.nullable) invalid(); null } else integer(s, at)
                    }
                    accept(t, text, nums)
                }
            }
        } while (batch == 32)
        if (rows != h.rows || actual.toList() != h.bytes) invalid()
    }

    companion object {
        private val tables = listOf(
            Table("actions", "id", listOf(Text("id"), Text("title"), Text("description"), Text("status"), Text("sensitivity")),
                listOf(Number("startAt"), Number("expectedEndAt"), Number("createdAt"), Number("completedAt", true))),
            Table("tasks", "actionId", listOf(Text("id"), Text("actionId"), Text("title"), Text("priority"), Text("status"),
                Text("assigneePersonId", true), Text("assigneeRoleId", true), Text("affectedPersonId", true), Text("sourceTemplateKey", true)),
                listOf(Number("dueAt", true), Number("createdAt"), Number("updatedAt"), Number("completedAt", true),Number("editRevision"))),
            Table("task_events", "actionId", listOf(Text("id"), Text("actionId"), Text("taskId"), Text("type")), listOf(Number("occurredAt")))
        )
    }
}
