package org.pag.data.product

import androidx.room3.PooledConnection
import androidx.sqlite.SQLiteStatement
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.pag.domain.*

/** ONE existing authorized Room read transaction. No full editor graphs except the explicitly
 * selected detail. Metadata first, immutable units, synchronous shared admission, then text.
 * Schema 11, no OFFSET, no event queries, no cache. Native cursor risk remains #32.
 */
internal class CompactPeopleStore(private val sql: PooledConnection, private val context: StorageContext,
    private val current: () -> Boolean, private val observe: (Phase) -> Unit = {}) {
    enum class Phase { METADATA, ADMITTED, ROW, COMPLETE }
    data class Core(val action: Action, val people: CompactPeoplePage, val functions: CompactFunctions)
    private fun fail(s: BoundedActiveStatus): Nothing = throw BoundedActiveRejected(s)
    private fun invalid(): Nothing = fail(BoundedActiveStatus.INVALID_DATA)
    private fun gate() { requireOperations(context); if(!current()) fail(BoundedActiveStatus.ACCESS_REVOKED) }
    private suspend fun <T> query(text: String, args: List<Any?> = emptyList(), work: (SQLiteStatement)->T): T {
        gate()
        return sql.usePrepared(text) { s ->
            args.forEachIndexed { i,v -> when(v) { null->s.bindNull(i+1); is String->s.bindText(i+1,v); is Long->s.bindLong(i+1,v); else->error("Scalar binding") } }
            gate();work(s).also { gate() }
        }
    }
    private fun number(s: SQLiteStatement,i: Int): Long {
        if(s.isNull(i)||s.getColumnType(i)!=1) invalid()
        return s.getLong(i)
    }
    private suspend fun scalar(text: String,args: List<Any?>) = query(text,args) {
        if(!it.step()) invalid(); val n=number(it,0); if(it.step()) invalid(); n
    }
    private fun input(id: String) {
        if(id.length>256) fail(BoundedActiveStatus.BUDGET_REFUSED)
        var at=0
        while(at<id.length) { val c=id[at++]; if(Character.isHighSurrogate(c)) {
            if(at==id.length || !Character.isLowSurrogate(id[at++])) invalid()
        } else if(Character.isLowSurrogate(c)) invalid() }
        if(id.toByteArray(Charsets.UTF_8).size>256) fail(BoundedActiveStatus.BUDGET_REFUSED)
    }
    private suspend fun preflight(state: CompactTaskState) {
        input(state.binding.actionId)
        // Reuse the legacy metadata/relational proof WITHOUT admitting or materializing its
        // full graph. Its fixed six headers are transient metadata, NOT retained readerresults.
        BoundedActiveStore(sql,context,current).prepare(state.binding)
        CompactTaskStore(sql,context,current).preflight(state)
        for((table,fields) in listOf("persons" to listOf("id","actionId"), "roles" to listOf("id","actionId"))) {
            val args=listOf(state.binding.actionId)
            if(scalar("SELECT EXISTS(SELECT 1 FROM $table WHERE actionId=? AND (${fields.joinToString(" OR ") { "length(CAST($it AS BLOB))>256" }}))",args)!=0L)
                fail(BoundedActiveStatus.BUDGET_REFUSED)
            if(scalar("SELECT EXISTS(SELECT 1 FROM $table WHERE actionId=? AND (${fields.joinToString(" OR ") { "NOT (${CompactSqlText.validText(it,false)}) OR length(CAST($it AS BLOB))=0" }}))",args)!=0L) invalid()
        }
        observe(Phase.METADATA);gate()
    }
    private data class Selection(val table: String,val where: String,val args: List<Any?>,
        val texts: List<String>,val numbers: List<String>,val rows: Long,val bytes: Long)
    private suspend fun size(table: String,where: String,args: List<Any?>,texts: List<String>,numbers: List<String> = emptyList()): Selection {
        val rows=query("SELECT COUNT(*),${texts.joinToString { "COALESCE(MAX(length(CAST($it AS BLOB))),0)" }} FROM $table WHERE $where",args) { s ->
            if(!s.step()) invalid()
            val n=number(s,0)
            if(n !in 0..65536) fail(BoundedActiveStatus.BUDGET_REFUSED)
            texts.indices.forEach { if(number(s,it+1)>ActiveWorkspaceBudget.LIMIT_BYTES) fail(BoundedActiveStatus.BUDGET_REFUSED) }
            if(s.step()) invalid(); n
        }
        val bytes=scalar("SELECT COALESCE(SUM(${texts.joinToString("+") { "COALESCE(length(CAST($it AS BLOB)),0)" }}),0) FROM $table WHERE $where",args)
        if(bytes<0) invalid()
        return Selection(table,where,args,texts,numbers,rows,bytes)
    }
    private fun reserve(parts: List<Selection>): ActiveCoreReservation {
        // Same 4/16 overlap factors and weights as the bounded full reader. Rows/fields now
        // describe only actual selected objects. Includes decoder/arrays, page sort, role index,
        // metadata, cursors, lists/capacities and future UI/editor references. No second ledger.
        val rows=parts.sumOf{it.rows};val fields=parts.sumOf{it.rows*it.texts.size};val bytes=parts.sumOf{it.bytes}
        fun units(c: Long)=ActiveWorkspaceBudget.Units(objects=(rows+16)*c*8,
            strings=(fields+16)*c,utf16Units=(bytes+16384)*c,arrays=(fields+16)*c,arrayPayloadBytes=(bytes+16384)*c,
            lists=16,listCapacity=(rows+32)*c,maps=8,mapCapacity=rows*c,references=(rows+32)*c*16)
        return ActiveCoreReservation(units(4),units(16))
    }
    private suspend fun fetch(part: Selection,accept: (List<String?>,List<Long?>)->Unit) {
        var last: Long?=null;var rows=0L;var bytes=0L
        val fields=part.texts.flatMap { listOf("length(CAST($it AS BLOB))","CASE WHEN length(CAST($it AS BLOB))<=${ActiveWorkspaceBudget.LIMIT_BYTES} THEN CAST($it AS BLOB) ELSE NULL END") }+part.numbers
        do {
            var batch=0
            query("SELECT rowid,${fields.joinToString()} FROM ${part.table} WHERE (${part.where}) AND (? IS NULL OR rowid>?) ORDER BY rowid LIMIT 32",part.args+listOf(last,last)) { s ->
                while(s.step()) {
                    observe(Phase.ROW);gate()
                    val row=number(s,0);if(last!=null && row<=last!!) invalid();last=row
                    rows++;batch++;if(rows>part.rows || batch>32) invalid()
                    val t=part.texts.indices.map { i ->
                        val col=1+i*2
                        if(s.isNull(col)) {if(!s.isNull(col+1)) invalid();null} else {
                            val n=number(s,col);if(n !in 0..ActiveWorkspaceBudget.LIMIT_BYTES || s.isNull(col+1)) invalid()
                            bytes=Math.addExact(bytes,n);if(bytes>part.bytes) invalid()
                            gate();val b=s.getBlob(col+1);if(b.size.toLong()!=n) invalid()
                            try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString() }
                            catch(_:java.nio.charset.CharacterCodingException){invalid()}
                        }
                    }
                    val n=part.numbers.indices.map { i -> val col=1+part.texts.size*2+i; if(s.isNull(col)) null else number(s,col) }
                    try { accept(t,n) } catch(_:ProductStorageFailure){invalid()} catch(_:IllegalArgumentException){invalid()}
                    gate()
                }
            }
        } while(batch==32)
        if(rows!=part.rows || bytes!=part.bytes) invalid()
    }
    private suspend fun counts(action: String): CompactPeopleCounts = query("SELECT ${OperationalStatus.entries.joinToString { "COALESCE(SUM(operationalStatus='${it.name}'),0)" }} FROM person_operational_states WHERE actionId=?",listOf(action)) { s ->
        if(!s.step()) invalid(); val n=LongArray(8){number(s,it).also { v->if(v<0) invalid() }};if(s.step()) invalid()
        CompactPeopleCounts(n[0],n[1],n[2],n[3],n[4],n[5],n[6],n[7])
    }
    private fun op(field: String)="(SELECT $field FROM person_operational_states s WHERE s.actionId=persons.actionId AND s.personId=persons.id)"
    private val roleName get()="(SELECT name FROM roles r WHERE r.actionId=persons.actionId AND r.id=${op("currentRoleId")})"
    private val roleNormalized get()="(SELECT normalizedName FROM roles r WHERE r.actionId=persons.actionId AND r.id=${op("currentRoleId")})"
    private val cardTexts get()=listOf("id","actionId","pseudonym","pseudonymNormalized",op("operationalStatus"),op("currentRoleId"),roleName,roleNormalized,op("currentPlaceLabel"))
    private val affected get()="(SELECT COUNT(*) FROM tasks t WHERE t.actionId=persons.actionId AND t.affectedPersonId=persons.id AND t.status IN ('OPEN','IN_PROGRESS'))"
    private suspend fun idKey(action: String): String {
        if(scalar("SELECT NOT EXISTS(SELECT 1 FROM persons WHERE actionId=? AND length(CAST(id AS BLOB))!=length(id))",listOf(action))==1L) return "id"
        val cp="COALESCE(unicode(CAST(rest AS TEXT)),0)"
        val key="CASE WHEN $cp<=65535 THEN printf('%04X',$cp) ELSE printf('%04X%04X',55296+(($cp-65536)/1024),56320+(($cp-65536)%1024)) END"
        val width="CASE WHEN hex(substr(rest,1,1))<'80' THEN 1 WHEN hex(substr(rest,1,1))<'E0' THEN 2 WHEN hex(substr(rest,1,1))<'F0' THEN 3 ELSE 4 END"
        return "(WITH RECURSIVE chars(rest,k) AS (VALUES(CAST(id AS BLOB),'') UNION ALL SELECT substr(rest,($width)+1),k||($key) FROM chars WHERE length(rest)>0) SELECT k FROM chars WHERE length(rest)=0)"
    }
    private data class PagePlan(val selection: Selection,val counts: CompactPeopleCounts,val total: Long,
        val state: CompactTaskState,val filter: CompactPeopleQuery)
    private suspend fun pagePlan(state: CompactTaskState,filter: CompactPeopleQuery,after: CompactPeopleCursor?): PagePlan {
        if(after!=null && (after.state!=state || after.query!=filter)) fail(BoundedActiveStatus.ACCESS_REVOKED)
        after?.let{input(it.id)}
        val action=state.binding.actionId
        val args=mutableListOf<Any?>(action)
        var where="actionId=?"
        if(filter is CompactPeopleQuery.Function) {
            filter.roleId?.let { input(it)
                if(scalar("SELECT COUNT(*) FROM roles WHERE actionId=? AND id=?",listOf(action,it))!=1L) fail(BoundedActiveStatus.ACCESS_REVOKED)
            }
            where+=" AND ${op("operationalStatus")}='ON_SITE' AND ${op("currentRoleId")} IS ?";args.add(filter.roleId)
        }
        val total=scalar("SELECT COUNT(*) FROM persons WHERE $where",args)
        if(after!=null && scalar("SELECT COUNT(*) FROM persons WHERE $where AND id=? AND createdAt=?",args+listOf(after.id,after.createdAt))!=1L) fail(BoundedActiveStatus.ACCESS_REVOKED)
        val key=idKey(action)
        if(after!=null) {
            where+=" AND (createdAt,$key)>(?,?)"
            val value=if(key=="id") after.id else buildString(after.id.length*4){ after.id.forEach{append(it.code.toString(16).uppercase().padStart(4,'0'))} }
            args.add(after.createdAt);args.add(value)
        }
        val selected="id IN (SELECT id FROM persons WHERE $where ORDER BY createdAt,$key LIMIT 32)"
        return PagePlan(size("persons",selected,args,cardTexts,listOf("createdAt",op("updatedAt"),affected)),counts(action),total,state,filter)
    }
    private suspend fun materializePage(plan: PagePlan): CompactPeoplePage {
        val cards=ArrayList<CompactPersonCard>(32)
        fetch(plan.selection) { t,n ->
            val person=PersonRow(t[0]?:invalid(),t[1]?:invalid(),t[2]?:invalid(),t[3]?:invalid(),n[0]?:invalid()).model()
            val status=OperationalStatus.valueOf(t[4]?:invalid())
            val state=OperationalRow(person.actionId,person.id,t[5],status.name,t[8],n[1]?:invalid()).model()
            if(t[5]!=null && (t[6]==null || t[7]!=OperationalRules.normalizedName(t[6]!!) || !OperationalRules.validName(t[6]!!))) invalid()
            cards.add(CompactPersonCard(person.id,person.pseudonym,person.createdAt,state.status,state.currentRoleId,t[6],state.currentPlaceLabel,state.updatedAt,n[2]?:invalid()))
        }
        if(cards.size>32) invalid()
        cards.sortWith(compareBy<CompactPersonCard>{it.createdAt}.thenBy{it.id})
        val next=cards.lastOrNull()?.takeIf{cards.size==32}?.let{CompactPeopleCursor(plan.state,plan.filter,it.createdAt,it.id)}
        return CompactPeoplePage(plan.state,plan.filter,plan.counts,plan.total,cards,next)
    }
    suspend fun prepareCore(state: CompactTaskState): PreparedCoreRead<Core> {
        preflight(state)
        val args=listOf(state.binding.actionId)
        val action=size("actions","id=?",args,listOf("id","title","description","status","sensitivity"),listOf("startAt","expectedEndAt","createdAt","completedAt"))
        val people=pagePlan(state,CompactPeopleQuery.All,null)
        val roles=size("roles","actionId=?",args,listOf("id","actionId","name","normalizedName"),listOf("createdAt","(SELECT COUNT(*) FROM person_operational_states s WHERE s.actionId=roles.actionId AND s.currentRoleId=roles.id AND s.operationalStatus='ON_SITE')"))
        val without=scalar("SELECT COUNT(*) FROM person_operational_states WHERE actionId=? AND operationalStatus='ON_SITE' AND currentRoleId IS NULL",args)
        return PreparedCoreRead(reserve(listOf(action,people.selection,roles))) {
            observe(Phase.ADMITTED);gate()
            var a: Action?=null
            fetch(action){t,n->a=ActionRow(t[0]?:invalid(),t[1]?:invalid(),t[2]?:invalid(),n[0]?:invalid(),n[1]?:invalid(),n[2]?:invalid(),t[3]?:invalid(),t[4]?:invalid(),n[3]).model()}
            val page=materializePage(people)
            val functions=ArrayList<Pair<CompactFunction,String>>()
            fetch(roles){t,n->functions.add(CompactFunction(RoleRow(t[0]?:invalid(),t[1]?:invalid(),t[2]?:invalid(),t[3]?:invalid(),n[0]?:invalid()).model(),n[1]?:invalid()) to (t[3]?:invalid()))}
            functions.sortWith(compareBy<Pair<CompactFunction,String>>{it.second}.thenBy{it.first.role.id})
            val summary=CompactFunctions(functions.map{it.first},without,page.overall.onSite,functions.count{it.first.onSiteCount>0}.toLong())
            if(summary.items.sumOf{it.onSiteCount}+without!=page.overall.onSite) invalid()
            gate();observe(Phase.COMPLETE);gate()
            Core(a?:invalid(),page,summary)
        }
    }
    suspend fun page(state: CompactTaskState,filter: CompactPeopleQuery,after: CompactPeopleCursor?): CompactPeopleResult.Value {
        preflight(state);val plan=pagePlan(state,filter,after)
        val prepared=PreparedCoreRead(reserve(listOf(plan.selection))) { observe(Phase.ADMITTED);gate();materializePage(plan) }
        return finish(prepared)
    }
    suspend fun detail(state: CompactTaskState,personId: String): CompactPeopleResult.Value {
        input(personId);preflight(state)
        val args=listOf(state.binding.actionId,personId)
        val person=size("persons","actionId=? AND id=?",args,cardTexts,listOf("createdAt",op("updatedAt")))
        if(person.rows!=1L) fail(BoundedActiveStatus.ACCESS_REVOKED)
        val planned=size("person_planned_roles","actionId=? AND personId=?",args,listOf("roleId"))
        val primary=size("person_role_plans","actionId=? AND personId=?",args,listOf("primaryRoleId"))
        val prepared=PreparedCoreRead(reserve(listOf(person,planned,primary))) {
            observe(Phase.ADMITTED);gate()
            var p: Person?=null;var s: PersonOperationalState?=null
            fetch(person){t,n->
                p=PersonRow(t[0]?:invalid(),t[1]?:invalid(),t[2]?:invalid(),t[3]?:invalid(),n[0]?:invalid()).model()
                s=OperationalRow(t[1]?:invalid(),t[0]?:invalid(),t[5],t[4]?:invalid(),t[8],n[1]?:invalid()).model()
                if(t[5]!=null && (t[6]==null || t[7]!=OperationalRules.normalizedName(t[6]!!) || !OperationalRules.validName(t[6]!!))) invalid()
            }
            val ids=linkedSetOf<String>();fetch(planned){t,_->if(!ids.add(t[0]?:invalid())) invalid()}
            var main: String?=null;fetch(primary){t,_->main=t[0]}
            if(!OperationalRules.validPlan(ids,main)) invalid()
            CompactPersonDetail(state,OperationalPerson(p?:invalid(),PersonRolePlan(ids,main),s?:invalid()))
        }
        return finish(prepared)
    }
    private suspend fun finish(prepared: PreparedCoreRead<out CompactPeoplePayload>): CompactPeopleResult.Value {
        val admitted=CoreReadAdmission.admit(listOf(prepared));val payload=prepared.materialize(admitted)
        gate();observe(Phase.COMPLETE);gate()
        return CompactPeopleResult.Value(payload,admitted.reservation,admitted.retained,admitted.transition)
    }
}
