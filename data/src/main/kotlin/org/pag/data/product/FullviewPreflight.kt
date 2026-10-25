package org.pag.data.product

import androidx.room3.PooledConnection
import androidx.sqlite.SQLiteStatement
import org.pag.domain.*

/** Scalar metadata only, called inside the SAME Room read transaction as all DTO reads.
 * No row DTO/list/text payload is read before the original entry's admission and consumption.
 * Fixed schema12 projection descriptors are not a second payload registry.
 */
internal class FullviewPreflight(private val sql:PooledConnection,private val c:StorageContext,
    private val current:()->Boolean) {
    private fun gate(){requireOperations(c);if(!current())throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)}
    private suspend fun scalar(query:String,a:String,id:String?=null):Long {
        gate()
        return sql.usePrepared(query){s->
            s.bindText(1,a);if(id!=null)s.bindText(2,id);gate()
            if(!s.step() || s.isNull(0) || s.getColumnType(0)!=1)throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
            val n=s.getLong(0)
            if(n<0 || s.step())throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
            gate();n
        }
    }
    suspend fun prepare(a:String,names:Set<String>,taskId:String?=null,detail:WorkspaceDetailRequest?=null,presentations:Boolean=true):ActiveCoreReservation {
        gate()
        sql.usePrepared("PRAGMA encoding"){s->
            if(!s.step() || s.getText(0)!="UTF-8" || s.step())throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
        }
        var rows=0L;var fields=0L;var bytes=0L;var taskRows=0L;var personRows=0L;var roleRows=0L
        for(t in tables.filter{it.name in names}) {
            gate()
            val column=when(t.name) {
                "persons","tasks","materials","expenses","roles"->"id"
                "person_role_plans","person_planned_roles","person_operational_states"->"personId"
                "expense_payments"->"expenseId"
                else->null
            }
            val id=if(detail!=null && column!=null)detail.id else if(t.name=="tasks")taskId else null
            val where=if(t.name=="actions")"id=?" else if(id!=null)"actionId=? AND ${column ?: "id"}=?" else "actionId=?"
            val n=scalar("SELECT COUNT(*) FROM ${t.name} WHERE $where",a,id)
            // Reject enormous counts BEFORE any SUM: each text SUM is then <= 2^39.
            if(n>ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES/128)throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)
            if(t.revision && scalar("SELECT COUNT(*) FROM ${t.name} WHERE $where AND (typeof(editRevision)!='integer' OR editRevision<0)",a,id)!=0L)
                throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
            if(t.name=="tasks")taskRows=n
            if(t.name=="persons")personRows=n
            if(t.name=="roles")roleRows=n
            rows=checked{Math.addExact(rows,n)}
            fields=checked{Math.addExact(fields,Math.multiplyExact(n,t.columns.toLong()))}
            for(f in t.text) {
                val max=scalar("SELECT COALESCE(MAX(length(CAST($f AS BLOB))),0) FROM ${t.name} WHERE $where",a,id)
                if(max>ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)
                val total=scalar("SELECT COALESCE(SUM(length(CAST($f AS BLOB))),0) FROM ${t.name} WHERE $where",a,id)
                bytes=checked{Math.addExact(bytes,total)}
            }
        }
        gate()
        // MemoryAnswerCodec may split payload tokens into additional objects. No zero-cost
        // decoder assumption: one extra logical row allowance per byte (conservative).
        val chargedRows=if("memory_answers" in names)checked{Math.addExact(rows,bytes)} else rows
        var base=reservation(chargedRows,fields,bytes)
        fun add(units:ActiveWorkspaceBudget.Units){base=ActiveCoreReservation(
            CoreReadAdmission.add(base.retained,units),CoreReadAdmission.add(base.transition,units))}
        if(presentations && "tasks" in names){
            val target=detail?.id?:taskId
            val where=if(target==null)"actionId=?" else "actionId=? AND id=?"
            val filters=scalar("SELECT COUNT(DISTINCT affectedPersonId) FROM tasks WHERE $where",a,target)
            if(filters>taskRows)throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
            add(checked{TaskPresentation.cost(taskRows,filters=filters)})
        }
        if(presentations && "roles" in names && "persons" in names && "person_planned_roles" in names){
            val joined=scalar("SELECT COALESCE(SUM(length(CAST(r.name AS BLOB))),0) FROM person_planned_roles p JOIN roles r ON r.actionId=p.actionId AND r.id=p.roleId WHERE p.actionId=?",a)
            add(checked{OperationalPresentation.cost(personRows,roleRows,joined)})
        }
        if(detail?.kind==WorkspaceDetailKind.ROLE){
            val dependents=scalar("SELECT COUNT(*) FROM person_planned_roles WHERE actionId=? AND roleId=?",a,detail.id)
            if(dependents>ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES/128)throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)
            val joined=scalar("SELECT COALESCE(SUM(length(CAST(r.name AS BLOB))),0) FROM person_planned_roles p JOIN roles r ON r.actionId=p.actionId AND r.id=p.roleId WHERE p.actionId=? AND p.roleId=?",a,detail.id)
            add(checked{OperationalPresentation.cost(dependents,roleRows,joined)})
        }
        return base
    }

    private data class Table(val name:String,val text:List<String>,val columns:Int,val revision:Boolean)
    companion object {
        private val tables=listOf(
            Table("leadership",listOf("actionId","state","epoch","algorithm","salt","envelope"),11,false),
            Table("sensitive_person_areas",listOf("actionId","personId","area","keyEnvelope","payload"),7,false),
            Table("actions",listOf("id","title","description","status","sensitivity"),9,false),
            Table("persons",listOf("id","actionId","pseudonym","pseudonymNormalized"),6,true),
            Table("roles",listOf("id","actionId","name","normalizedName"),6,true),
            Table("person_planned_roles",listOf("actionId","personId","roleId"),3,false),
            Table("person_role_plans",listOf("actionId","personId","primaryRoleId"),4,true),
            Table("person_operational_states",listOf("actionId","personId","currentRoleId","operationalStatus","currentPlaceLabel"),7,true),
            Table("tasks",listOf("id","actionId","title","priority","status","assigneePersonId","assigneeRoleId","affectedPersonId","sourceTemplateKey"),14,true),
            Table("task_events",listOf("id","actionId","taskId","type"),5,false),
            Table("protocol_entries",listOf("id","actionId"),3,false),
            Table("protocol_revisions",listOf("id","actionId","entryId","revisionType","classification","templateKey","title","description","sourceType","placeLabel","announcementText","announcementKind","statedLegalBasis"),19,false),
            Table("protocol_affected_persons",listOf("actionId","revisionId","personId"),3,false),
            Table("protocol_official_identifiers",listOf("actionId","revisionId","type","value"),5,false),
            Table("protocol_reported_by_persons",listOf("actionId","revisionId","personId"),3,false),
            Table("materials",listOf("id","actionId","name","quantityLabel","placeLabel","responsiblePersonId","linkedTaskId","sourceTemplateKey"),9,false),
            Table("expenses",listOf("id","actionId","title"),5,false),
            Table("expense_payments",listOf("actionId","expenseId","payerLabel"),5,false),
            Table("memory_reports",listOf("id","actionId","authorLabel","status"),10,false),
            Table("memory_answers",listOf("actionId","reportId","questionKey","valueKind","payload"),5,false),
            Table("memory_addenda",listOf("actionId","reportId","content"),5,false)
        )
        val operations=setOf("actions","persons","roles","person_planned_roles","person_role_plans","person_operational_states")
        val task=setOf("actions","tasks") // TaskBoard has NO event history; history remains <=32/page.
        val protocol=setOf("actions","persons","protocol_entries","protocol_revisions","protocol_affected_persons","protocol_official_identifiers","protocol_reported_by_persons")
        val material=setOf("actions","materials")
        val expense=setOf("actions","expenses","expense_payments")
        val memory=setOf("actions","memory_reports","memory_answers","memory_addenda")
        fun workspace(parts:Set<WorkspacePart>):Set<String> = operations + buildSet {
            if(WorkspacePart.TASKS in parts)add("tasks")
            if(WorkspacePart.PROTOCOL_TIMELINE in parts)addAll(protocol)
            if(WorkspacePart.MATERIAL_BOARD in parts)add("materials")
            if(WorkspacePart.EXPENSE_BOARD in parts)addAll(expense)
            if(WorkspacePart.MEMORY_HEADERS in parts)add("memory_reports")
            if(WorkspacePart.LEADERSHIP_STATE in parts)addAll(setOf("leadership","sensitive_person_areas"))
            // Summary methods produce only fixed-size scalar results, included in fixed overhead.
        }
        private fun <T> checked(work:()->T):T=try{work()}catch(_:ArithmeticException){throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)}
        /** Deliberately no dedup discount. Four held and sixteen transitional representations:
         * DTOs, normalized strings, plan sets/maps, sorted/filter lists, DAO rows/builders,
         * wrappers, worker/main closures and bounded scalar summary objects. UTF8 bytes bound
         * UTF16 units incl NUL/surrogates; all arithmetic checked. NOT a draft-size allowance.
         */
        internal fun reservation(rows:Long,fields:Long,bytes:Long):ActiveCoreReservation {
            if(rows<0 || fields<0 || bytes<0)throw BoundedActiveRejected(BoundedActiveStatus.INVALID_DATA)
            fun units(copies:Long)=checked {
                ActiveWorkspaceBudget.Units(objects=Math.addExact(64,Math.multiplyExact(rows,8*copies)),
                    strings=Math.addExact(64,Math.multiplyExact(fields,copies)),utf16Units=Math.addExact(4096,Math.multiplyExact(bytes,copies)),
                    arrays=64,arrayPayloadBytes=8192,lists=Math.addExact(64,Math.multiplyExact(rows,copies)),
                    listCapacity=Math.multiplyExact(fields,2*copies),maps=32,mapCapacity=Math.multiplyExact(rows,2*copies),
                    references=Math.addExact(256,Math.multiplyExact(fields,4*copies)))
            }
            return ActiveCoreReservation(units(4),units(16))
        }
    }
}

/** Shared final seam. The same original liveness must hold on BOTH sides of booking,
 * before consuming the right to allocate. No Session authority is created here. */
internal fun admitCurrentFullview(entry:OutstandingCoreRead.FullviewEntry,
    reservation:ActiveCoreReservation,current:()->Boolean):Boolean {
    if(!current())throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
    if(!entry.admit(reservation))return false
    if(!current())throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
    if(!entry.materialize())throw BoundedActiveRejected(BoundedActiveStatus.ACCESS_REVOKED)
    return true
}
