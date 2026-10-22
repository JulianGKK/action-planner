package org.pag.data.product

import androidx.room3.PooledConnection
import androidx.sqlite.SQLiteStatement
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.pag.domain.*

/** One authorized Room read transaction per request. No DAO list or retained connection.
 * SQLite may sort scalar keys on schema 11 (no schema/index migration here). The existing
 * SQLCipher statement/cursor/native-memory blocker is NOT resolved by usePrepared (#32).
 */
internal class CompactTaskStore(private val sql: PooledConnection, private val context: StorageContext,
    private val current: () -> Boolean, private val observe: (Phase) -> Unit = {}) {
    enum class Phase { METADATA, ADMITTED, ROW, COMPLETE }
    private fun reject(s: BoundedActiveStatus): Nothing = throw BoundedActiveRejected(s)
    private fun invalid(): Nothing = reject(BoundedActiveStatus.INVALID_DATA)
    private fun gate() {
        requireOperations(context)
        if (!current()) reject(BoundedActiveStatus.ACCESS_REVOKED)
    }
    private suspend fun <T> query(text: String, args: List<Any?> = emptyList(), block: (SQLiteStatement) -> T): T {
        gate()
        return sql.usePrepared(text) { s ->
            args.forEachIndexed { i, v -> when(v) {
                null -> s.bindNull(i+1)
                is String -> s.bindText(i+1,v)
                is Long -> s.bindLong(i+1,v)
                else -> error("Unsupported scalar binding")
            } }
            gate(); block(s).also { gate() }
        }
    }
    private fun integer(s: SQLiteStatement, i: Int): Long {
        if (s.isNull(i) || s.getColumnType(i) != 1) invalid()
        return s.getLong(i)
    }
    private suspend fun scalar(text: String, args: List<Any?>): Long = query(text,args) { s ->
        if (!s.step()) invalid()
        val n=integer(s,0)
        if (s.step()) invalid()
        n
    }
    private fun checkInput(value: String) {
        // No copies of unbounded caller-supplied identifiers before admission.
        if (value.length > ID_BYTES) reject(BoundedActiveStatus.BUDGET_REFUSED)
        if (value.any { Character.isSurrogate(it) } && !validSurrogates(value)) invalid()
        if (value.toByteArray(Charsets.UTF_8).size > ID_BYTES) reject(BoundedActiveStatus.BUDGET_REFUSED)
    }
    private fun validSurrogates(value: String): Boolean {
        var i=0
        while(i<value.length) {
            val c=value[i++]
            if(Character.isHighSurrogate(c)) {
                if(i==value.length || !Character.isLowSurrogate(value[i++])) return false
            } else if(Character.isLowSurrogate(c)) return false
        }
        return true
    }

    private fun validText(column: String, title: Boolean) = CompactSqlText.validText(column, title)

    internal suspend fun preflight(state: CompactTaskState, fullTaskRead:Boolean=false) {
        gate();checkInput(state.binding.actionId)
        if(context.generation!=state.binding.generation) reject(BoundedActiveStatus.ACCESS_REVOKED)
        query("PRAGMA encoding") { s -> if(!s.step() || s.getText(0)!="UTF-8" || s.step()) invalid() }
        val a=listOf(state.binding.actionId)
        val readable=if(fullTaskRead) "status IN ('PREPARATION','ACTIVE','COMPLETED')" else "status='ACTIVE'"
        val active=scalar("SELECT COALESCE((SELECT CASE WHEN $readable THEN 1 WHEN status IN ('DRAFT','PREPARATION','COMPLETED','DELETION_PENDING') THEN 0 ELSE -1 END FROM actions WHERE id=?),0)",a)
        if(active<0) invalid()
        if(active==0L) reject(BoundedActiveStatus.ACCESS_REVOKED)
        // Scalar validation of all task rows, not payload fetching. Provenance may be huge:
        // it is checked for storage type here, but only sized/materialized for one detail.
        val texts=listOf("id","actionId","title","priority","status")
        val optional=listOf("assigneePersonId","assigneeRoleId","affectedPersonId","sourceTemplateKey")
        val checks=texts.map { "typeof(t.$it)!='text'" } + optional.map { "(t.$it IS NOT NULL AND typeof(t.$it)!='text')" } +
            listOf("typeof(t.createdAt)!='integer'","typeof(t.updatedAt)!='integer'",
                "(t.dueAt IS NOT NULL AND typeof(t.dueAt)!='integer')", "(t.completedAt IS NOT NULL AND typeof(t.completedAt)!='integer')",
                "t.priority NOT IN ('HIGH','NORMAL','LOW')", "t.status NOT IN ('OPEN','IN_PROGRESS','DONE','CANCELLED')",
                "((t.status='DONE')!=(t.completedAt IS NOT NULL))", "(t.assigneePersonId IS NOT NULL AND t.assigneeRoleId IS NOT NULL)",
                "(t.assigneePersonId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM persons p WHERE p.actionId=t.actionId AND p.id=t.assigneePersonId))",
                "(t.affectedPersonId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM persons p WHERE p.actionId=t.actionId AND p.id=t.affectedPersonId))",
                "(t.assigneeRoleId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM roles r WHERE r.actionId=t.actionId AND r.id=t.assigneeRoleId))")
        if(scalar("SELECT EXISTS(SELECT 1 FROM tasks t WHERE t.actionId=? AND (${checks.joinToString(" OR ")}))",a)!=0L) invalid()
        // Global key bounds are required before SQLite key ordering, including Unicode keys.
        val boundedIds=listOf("id","actionId","assigneePersonId","assigneeRoleId","affectedPersonId")
        if(scalar("SELECT EXISTS(SELECT 1 FROM tasks WHERE actionId=? AND (${boundedIds.joinToString(" OR ") { "length(CAST($it AS BLOB))>$ID_BYTES" }}))",a)!=0L)
            reject(BoundedActiveStatus.BUDGET_REFUSED)
        if(scalar("SELECT EXISTS(SELECT 1 FROM tasks WHERE actionId=? AND length(CAST(title AS BLOB))>480)",a)!=0L)
            reject(BoundedActiveStatus.BUDGET_REFUSED)
        // Counts must not count malformed or domain-invalid tasks hidden on a later page.
        val textChecks=listOf("NOT (${validText("title",true)})")+
            boundedIds.map { "($it IS NOT NULL AND NOT (${validText(it,false)}))" }
        if(scalar("SELECT EXISTS(SELECT 1 FROM tasks WHERE actionId=? AND (${textChecks.joinToString(" OR ")}))",a)!=0L) invalid()
        observe(Phase.METADATA);gate()
    }

    private suspend fun counts(action: String, person: String?): CompactTaskCounts = query(
        "SELECT COALESCE(SUM(status='OPEN'),0),COALESCE(SUM(status='IN_PROGRESS'),0),"+
            "COALESCE(SUM(status IN ('OPEN','IN_PROGRESS') AND priority='HIGH'),0),"+
            "COALESCE(SUM(status='DONE'),0),COALESCE(SUM(status='CANCELLED'),0) FROM tasks WHERE actionId=?"+
            if(person==null) "" else " AND affectedPersonId=?",
        if(person==null) listOf(action) else listOf(action,person)) { s ->
        if(!s.step()) invalid()
        val v=LongArray(5) { integer(s,it).also { n -> if(n<0) invalid() } }
        if(s.step()) invalid()
        CompactTaskCounts(v[0],v[1],v[2],v[3],v[4])
    }

    /** Exact Java/Kotlin UTF16 ID order, including supplementary characters and embedded NUL.
     * ASCII IDs use the SQLite BINARY fast path. Rare non-ASCII IDs use bounded recursive
     * scalar key construction in SQLite; no IDs from other actions and no Kotlin full-list sort.
     * BLOB tails avoid SQLite TEXT length/substr truncation at NUL. Each tail <=256 bytes.
     */
    private suspend fun source(action: String): String {
        val ascii=scalar("SELECT NOT EXISTS(SELECT 1 FROM tasks WHERE actionId=? AND length(CAST(id AS BLOB))!=length(id))",listOf(action))==1L
        if(ascii) return "id"
        val cp="COALESCE(unicode(CAST(rest AS TEXT)),0)"
        val key="CASE WHEN $cp<=65535 THEN printf('%04X',$cp) ELSE printf('%04X%04X',55296+(($cp-65536)/1024),56320+(($cp-65536)%1024)) END"
        val width="CASE WHEN hex(substr(rest,1,1))<'80' THEN 1 WHEN hex(substr(rest,1,1))<'E0' THEN 2 WHEN hex(substr(rest,1,1))<'F0' THEN 3 ELSE 4 END"
        // Correlated scalar recursion, one bounded ID at a time. A global recursive keys
        // table would materialize every ID, defeating the bounded keyset memory contract.
        return "(WITH RECURSIVE chars(rest,k) AS (VALUES(CAST(id AS BLOB),'') UNION ALL "+
            "SELECT substr(rest,($width)+1),k||($key) FROM chars WHERE length(rest)>0) "+
            "SELECT k FROM chars WHERE length(rest)=0)"
    }
    private fun utf16Key(id: String) = buildString(id.length*4) { id.forEach { append(it.code.toString(16).uppercase().padStart(4,'0')) } }

    suspend fun page(state: CompactTaskState, filter: CompactTaskQuery, after: CompactTaskCursor?): CompactTaskResult.Value {
        val prepared = preparePage(state, filter, after)
        val admission = CoreReadAdmission.admit(listOf(prepared))
        val payload = prepared.materialize(admission)
        return CompactTaskResult.Value(payload,
            BoundedTaskReservation(prepared.reservation.retained, prepared.reservation.transition),
            admission.retained, admission.transition)
    }

    /** Metadata only. The caller must admit ALL scope parts before this plan can fetch text. */
    suspend fun preparePage(state: CompactTaskState, filter: CompactTaskQuery, after: CompactTaskCursor?): PreparedCoreRead<CompactTaskPage> {
        filter.affectedPersonId?.let(::checkInput)
        if(after!=null) {
            checkInput(after.id)
            if(after.state!=state || after.query!=filter) reject(BoundedActiveStatus.ACCESS_REVOKED)
        }
        preflight(state)
        val action=state.binding.actionId
        if(after!=null) {
            val lane=if(filter.lane==CompactTaskLane.ACTIVE) "IN ('OPEN','IN_PROGRESS')" else "IN ('DONE','CANCELLED')"
            val person=if(filter.affectedPersonId==null) "" else " AND affectedPersonId=?"
            val cursorArgs=listOf<Any?>(action,after.id,after.priority.name,after.dueAt,after.createdAt,after.updatedAt)+
                if(filter.affectedPersonId==null) emptyList() else listOf(filter.affectedPersonId)
            if(scalar("SELECT COUNT(*) FROM tasks WHERE actionId=? AND id=? AND priority=? AND dueAt IS ? AND createdAt=? AND updatedAt=? AND status $lane$person",cursorArgs)!=1L)
                reject(BoundedActiveStatus.ACCESS_REVOKED)
        }
        val overall=counts(action,null)
        val filtered=if(filter.affectedPersonId==null) overall else counts(action,filter.affectedPersonId)
        val idKey=source(action)
        val args=mutableListOf<Any?>()
        args.add(action)
        val where=StringBuilder("actionId=? AND status ${if(filter.lane==CompactTaskLane.ACTIVE) "IN ('OPEN','IN_PROGRESS')" else "IN ('DONE','CANCELLED')"}")
        filter.affectedPersonId?.let { where.append(" AND affectedPersonId=?");args.add(it) }
        val priority="CASE priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2 END"
        val order=if(filter.lane==CompactTaskLane.ACTIVE) "$priority,(dueAt IS NULL),COALESCE(dueAt,0),createdAt,$idKey" else "updatedAt DESC,$idKey"
        if(after!=null) {
            val key=if(idKey=="id") after.id else utf16Key(after.id)
            if(filter.lane==CompactTaskLane.ACTIVE) {
                where.append(" AND ($priority,(dueAt IS NULL),COALESCE(dueAt,0),createdAt,$idKey)>(?,?,?,?,?)")
                args.addAll(listOf((2-after.priority.ordinal).toLong(),if(after.dueAt==null) 1L else 0L,after.dueAt?:0L,after.createdAt,key))
            } else {
                where.append(" AND (updatedAt<? OR (updatedAt=? AND $idKey>?))")
                args.addAll(listOf(after.updatedAt,after.updatedAt,key))
            }
        }
        // Only scalar keys in the LIMIT subquery. Large provenance/title fields never enter
        // the SQLite sorter, decoder or JVM until the selected page is admitted.
        val candidates="SELECT id FROM tasks WHERE $where ORDER BY $order LIMIT $PAGE_SIZE"
        val selected="id IN ($candidates)"
        val texts=listOf("id","title","priority","status","assigneePersonId","assigneeRoleId","affectedPersonId")
        val sized=size("tasks",selected,args,texts)
        val reservation=pageUnits(sized.first,texts.size,sized.second)
        return PreparedCoreRead(reservation) {
            observe(Phase.ADMITTED);gate()
            // Reusing the same bounded key selection in the SAME transaction; SQLite output order
            // of IN is irrelevant. Sort only the admitted <=32 objects with TaskRules equivalent.
            val items=ArrayList<CompactTaskItem>(PAGE_SIZE)
            fetch("tasks",selected,args,texts,listOf("dueAt","createdAt","updatedAt"),sized) { t,n,_ ->
                val item=CompactTaskItem(t[0]?:invalid(),t[1]?:invalid(),TaskPriority.valueOf(t[2]?:invalid()),TaskStatus.valueOf(t[3]?:invalid()),t[4],t[5],t[6],n[0],n[1]?:invalid(),n[2]?:invalid())
                if(!TaskRules.valid(TaskInput(item.title,item.priority,item.assigneePersonId,item.assigneeRoleId,item.affectedPersonId,item.dueAt)) || item.title!=item.title.trim()) invalid()
                items.add(item)
            }
            if(items.size.toLong()!=sized.first) invalid()
            items.sortWith(if(filter.lane==CompactTaskLane.ACTIVE)
                compareByDescending<CompactTaskItem>{it.priority.ordinal}.thenBy{it.dueAt==null}.thenBy{it.dueAt}.thenBy{it.createdAt}.thenBy{it.id}
                else compareByDescending<CompactTaskItem>{it.updatedAt}.thenBy{it.id})
            // Existence probe beyond the last key is unnecessary: a full final page returns a
            // continuation and the next empty page explicitly marks end. No false completeness.
            val next=items.lastOrNull()?.takeIf { items.size==PAGE_SIZE }?.let {
                CompactTaskCursor(state,filter,it.id,it.priority,it.dueAt,it.createdAt,it.updatedAt)
            }
            gate();observe(Phase.COMPLETE);gate()
            CompactTaskPage(state,filter,overall,filtered,items,next)
        }
    }

    suspend fun detail(state: CompactTaskState, taskId: String): CompactTaskResult.Value {
        checkInput(taskId);preflight(state)
        val args=listOf(state.binding.actionId,taskId)
        val where="actionId=? AND id=?"
        val texts=listOf("id","actionId","title","priority","status","assigneePersonId","assigneeRoleId","affectedPersonId","sourceTemplateKey")
        val sized=size("tasks",where,args,texts)
        if(sized.first!=1L) reject(BoundedActiveStatus.ACCESS_REVOKED)
        val reservation=reserve(1,texts.size,sized.second)
        observe(Phase.ADMITTED);gate()
        var task: Task?=null
        fetch("tasks",where,args,texts,listOf("dueAt","createdAt","updatedAt","completedAt"),sized) { t,n,_ ->
            task=TaskRow(t[0]?:invalid(),t[1]?:invalid(),t[2]?:invalid(),t[3]?:invalid(),t[4]?:invalid(),t[5],t[6],t[7],n[0],t[8],n[1]?:invalid(),n[2]?:invalid(),n[3]).model()
        }
        return finish(CompactTaskDetail(state,task?:invalid()),reservation)
    }

    /** Direct entry reads ONE scalar anchor, never an OFFSET event payload list. The following
     * materializer is the same admitted keyset reader, in this SAME authorized transaction.
     * SQLite key sorting/temp allocation is still not a calibrated native-memory bound. */
    suspend fun eventsAtPage(state:CompactTaskState,taskId:String,page:Long,fullTaskRead:Boolean=false):CompactTaskResult.Value {
        if(page<1)reject(BoundedActiveStatus.ACCESS_REVOKED)
        if(page==1L)return events(state,taskId,null,fullTaskRead)
        checkInput(taskId);preflight(state,fullTaskRead)
        val args=listOf(state.binding.actionId,taskId)
        val offset=try{Math.multiplyExact(page-1,PAGE_SIZE.toLong())}catch(_:ArithmeticException){reject(BoundedActiveStatus.BUDGET_REFUSED)}
        val total=scalar("SELECT COUNT(*) FROM task_events WHERE actionId=? AND taskId=?",args)
        if(offset>=total)reject(BoundedActiveStatus.ACCESS_REVOKED)
        val anchor=query("SELECT occurredAt,rowid FROM task_events WHERE actionId=? AND taskId=? ORDER BY occurredAt,rowid LIMIT 1 OFFSET ?",args+listOf(offset-1)) { s ->
            if(!s.step())invalid()
            CompactEventCursor(state,taskId,integer(s,0),integer(s,1)).also{if(s.step())invalid()}
        }
        // The anchor and page share this read transaction. Reuse only its validated
        // preflight/count; all event/cursor checks and payload admission still follow.
        return eventsAfterPreflight(state,taskId,anchor,total)
    }

    suspend fun events(state: CompactTaskState, taskId: String, after: CompactEventCursor?, fullTaskRead:Boolean=false): CompactTaskResult.Value {
        checkInput(taskId)
        if(after!=null && (after.state!=state || after.taskId!=taskId)) reject(BoundedActiveStatus.ACCESS_REVOKED)
        preflight(state,fullTaskRead)
        return eventsAfterPreflight(state,taskId,after)
    }

    /** Private to the two entry points above: no preflight result survives a request. */
    private suspend fun eventsAfterPreflight(state:CompactTaskState,taskId:String,after:CompactEventCursor?,
        knownTotal:Long?=null):CompactTaskResult.Value {
        gate()
        val args=listOf(state.binding.actionId,taskId)
        if(scalar("SELECT COUNT(*) FROM tasks WHERE actionId=? AND id=?",args)!=1L) reject(BoundedActiveStatus.ACCESS_REVOKED)
        val checks="typeof(id)!='text' OR typeof(actionId)!='text' OR typeof(taskId)!='text' OR typeof(type)!='text' OR typeof(occurredAt)!='integer' OR type NOT IN ('CREATED','STARTED','COMPLETED','CANCELLED','REOPENED')"
        if(scalar("SELECT EXISTS(SELECT 1 FROM task_events WHERE actionId=? AND taskId=? AND ($checks))",args)!=0L) invalid()
        val total=knownTotal?:scalar("SELECT COUNT(*) FROM task_events WHERE actionId=? AND taskId=?",args)
        if(after!=null && scalar("SELECT COUNT(*) FROM task_events WHERE actionId=? AND taskId=? AND occurredAt=? AND rowid=?",
            args+listOf(after.occurredAt,after.rowId))!=1L) reject(BoundedActiveStatus.ACCESS_REVOKED)
        val seek=if(after==null) "" else " AND (occurredAt,rowid)>(?,?)"
        val bind=if(after==null) args else args+listOf(after.occurredAt,after.rowId)
        val where="rowid IN (SELECT rowid FROM task_events WHERE actionId=? AND taskId=?$seek ORDER BY occurredAt,rowid LIMIT $PAGE_SIZE)"
        val texts=listOf("id","actionId","taskId","type")
        val sized=size("task_events",where,bind,texts)
        val reservation=reserve(sized.first,texts.size,sized.second)
        observe(Phase.ADMITTED);gate()
        val rows=ArrayList<Pair<Long,TaskEvent>>(PAGE_SIZE)
        fetch("task_events",where,bind,texts,listOf("occurredAt"),sized) { t,n,r ->
            rows.add(r to TaskEvent(t[0]?:invalid(),t[1]?:invalid(),t[2]?:invalid(),TaskEventType.valueOf(t[3]?:invalid()),n[0]?:invalid()))
        }
        if(rows.size.toLong()!=sized.first) invalid()
        rows.sortWith(compareBy<Pair<Long,TaskEvent>>{it.second.occurredAt}.thenBy{it.first})
        val last=rows.lastOrNull()
        val next=last?.takeIf { scalar("SELECT EXISTS(SELECT 1 FROM task_events WHERE actionId=? AND taskId=? AND (occurredAt,rowid)>(?,?))",
            args+listOf(it.second.occurredAt,it.first))!=0L }?.let { CompactEventCursor(state,taskId,it.second.occurredAt,it.first) }
        return finish(CompactTaskEvents(state,taskId,total,rows.map{it.second},next),reservation)
    }

    /** Fixed aggregate row: refuse a single oversized value before SUM or BLOB extraction. */
    private suspend fun size(table: String, where: String, args: List<Any?>, fields: List<String>): Pair<Long,Long> {
        val lengths=fields.joinToString(",") { "COALESCE(MAX(length(CAST($it AS BLOB))),0)" }
        val count=query("SELECT COUNT(*),$lengths FROM $table WHERE $where",args) { s ->
            if(!s.step()) invalid()
            val n=integer(s,0)
            if(n !in 0..PAGE_SIZE.toLong()) invalid()
            fields.indices.forEach { if(integer(s,it+1)>ActiveWorkspaceBudget.LIMIT_BYTES) reject(BoundedActiveStatus.BUDGET_REFUSED) }
            if(s.step()) invalid()
            n
        }
        val bytes=scalar("SELECT COALESCE(SUM(${fields.joinToString("+") { "COALESCE(length(CAST($it AS BLOB)),0)" }}),0) FROM $table WHERE $where",args)
        if(bytes<0) invalid()
        return count to bytes
    }
    private suspend fun fetch(table: String, where: String, args: List<Any?>, texts: List<String>, numbers: List<String>, expected: Pair<Long,Long>, accept: (List<String?>,List<Long?>,Long)->Unit) {
        val fields=texts.flatMap { listOf("length(CAST($it AS BLOB))","CASE WHEN length(CAST($it AS BLOB))<=${ActiveWorkspaceBudget.LIMIT_BYTES} THEN CAST($it AS BLOB) ELSE NULL END") }
        query("SELECT rowid,${(fields+numbers).joinToString()} FROM $table WHERE $where",args) { s ->
            var rows=0
            var actualBytes=0L
            while(s.step()) {
                gate();observe(Phase.ROW);gate()
                if(++rows>PAGE_SIZE || rows.toLong()>expected.first) invalid()
                val t=texts.indices.map { i ->
                    val at=1+i*2
                    if(s.isNull(at)) { if(!s.isNull(at+1)) invalid();null } else {
                        val length=integer(s,at)
                        if(length !in 0..ActiveWorkspaceBudget.LIMIT_BYTES || s.isNull(at+1)) invalid()
                        actualBytes=Math.addExact(actualBytes,length)
                        if(actualBytes>expected.second) invalid()
                        gate()
                        val bytes=s.getBlob(at+1)
                        if(bytes.size.toLong()!=length) invalid()
                        try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
                        catch(_:java.nio.charset.CharacterCodingException) { invalid() }
                    }
                }
                val n=numbers.indices.map { i -> val at=1+texts.size*2+i; if(s.isNull(at)) null else integer(s,at) }
                accept(t,n,integer(s,0));gate()
            }
            if(rows.toLong()!=expected.first || actualBytes!=expected.second) invalid()
        }
    }
    private data class Reservation(val units: BoundedTaskReservation, val retained: Long, val transition: Long)
    private fun pageUnits(rows: Long, fields: Int, bytes: Long): ActiveCoreReservation {
        fun units(copies: Long) = ActiveWorkspaceBudget.Units(
            objects=(rows+16)*copies*8, strings=(rows*fields+16)*copies,
            utf16Units=(bytes+16384)*copies, arrays=(rows*fields+16)*copies,
            arrayPayloadBytes=(bytes+16384)*copies, lists=16, listCapacity=(rows+32)*copies,
            indexes=2,indexCapacity=rows*copies,references=(rows+32)*copies*16)
        // Overlap of old/new page and future UI/form references charged conservatively.
        // No entire-board map/index; sorting is limited to one page. Fixed allowance includes
        // metadata, bound IDs/UTF16 sort key, counters, cursors and SQLite binding copies.
        return ActiveCoreReservation(units(4),units(16))
    }
    private fun reserve(rows: Long, fields: Int, bytes: Long): Reservation {
        val planned=pageUnits(rows,fields,bytes)
        val reservation=BoundedTaskReservation(planned.retained,planned.transition)
        val budget=ActiveWorkspaceBudget()
        fun admit(d: ActiveWorkspaceBudget.Decision) { when(d) {
            ActiveWorkspaceBudget.Decision.ADMITTED -> Unit
            ActiveWorkspaceBudget.Decision.BUDGET_REFUSED -> reject(BoundedActiveStatus.BUDGET_REFUSED)
            ActiveWorkspaceBudget.Decision.INVALID_DATA -> invalid()
        } }
        try {
            val r=budget.beginRetained()!!;admit(budget.admit(r,reservation.retained));check(budget.seal(r))
            val t=budget.beginTransition()!!;admit(budget.admit(t,reservation.transition));check(budget.seal(t))
            return Reservation(reservation,budget.accounting(r)!!.chargedBytes,budget.accounting(t)!!.chargedBytes)
        } finally { budget.close() }
    }
    private fun finish(payload: CompactTaskPayload, reservation: Reservation): CompactTaskResult.Value {
        gate();observe(Phase.COMPLETE);gate()
        return CompactTaskResult.Value(payload,reservation.units,reservation.retained,reservation.transition)
    }
    companion object {
        const val PAGE_SIZE=32
        private const val ID_BYTES=256
    }
}
