package org.pag.domain

import java.util.Collections

/** Immutable derived member of ONE TaskBoard, constructed only with that board's admission.
 * No registry, cache admission, session authority or retirement claim. Filter views borrow
 * the same ordered rows; their metadata cardinality is bounded by affected IDs in the board.
 */
class TaskListPresentation internal constructor(val active:List<Task>,val closed:List<Task>) {
    val open=active.count{it.status==TaskStatus.OPEN}
    val inProgress=active.count{it.status==TaskStatus.IN_PROGRESS}
    val high=active.count{it.priority==TaskPriority.HIGH}
    val preparationSummary="Hoch offen / in Arbeit: $high · Offen: $open · In Arbeit: $inProgress"
    val activeClosedCollapsed="Erledigt / Entfallen (${closed.size}) ▾"
    val activeClosedExpanded="Erledigt / Entfallen (${closed.size}) ▴"
    val preparationClosedCollapsed="Erledigt / Entfällt (${closed.size}) ▾"
    val preparationClosedExpanded="Erledigt / Entfällt (${closed.size}) ▴"
    val summary:List<String> = Collections.unmodifiableList(if(active.isEmpty())listOf("Keine offenen Aufgaben") else buildList {
        add("$open offen · $inProgress in Arbeit")
        if(high>0)add("$high hohe Priorität")
    })
}
class TaskPresentation internal constructor(rows:List<Task>) {
    private class FilterRows(private val original:List<Task>,private val personId:String):AbstractList<Task>() {
        override val size=original.count{it.affectedPersonId==personId}
        override fun get(index:Int):Task {
            if(index<0 || index>=size)throw IndexOutOfBoundsException()
            var seen=0;var i=0
            while(i<original.size){val row=original[i++];if(row.affectedPersonId==personId && seen++==index)return row}
            throw IndexOutOfBoundsException()
        }
    }
    val all=TaskListPresentation(
        Collections.unmodifiableList(rows.filter{TaskRules.active(it.status)}.sortedWith(TaskRules.order)),
        Collections.unmodifiableList(rows.filterNot{TaskRules.active(it.status)}.sortedWith(TaskRules.closedOrder)))
    private val filtered:Map<String,TaskListPresentation> = Collections.unmodifiableMap(buildMap {
        for(row in rows){val id=row.affectedPersonId?:continue
            if(!containsKey(id))put(id,TaskListPresentation(FilterRows(all.active,id),FilterRows(all.closed,id)))
        }
    })
    val filterCount:Int get()=filtered.size
    fun forPerson(id:String?):TaskListPresentation=if(id==null)all else filtered[id]?:empty
    companion object {
        private val empty=TaskListPresentation(emptyList(),emptyList()) // code-only, no action payload
        /** Additional disjoint engineering cost BEFORE construction: ordered lists, bounded
         * filter metadata, maps/sets, summary texts and sorting scratch; no text limit. */
        fun cost(rows:Long,textUnits:Long=0,filters:Long=rows):ActiveWorkspaceBudget.Units {
            require(rows>=0 && textUnits>=0 && filters in 0..rows)
            fun mul(n:Long)=Math.multiplyExact(rows,n)
            fun views(n:Long)=Math.multiplyExact(filters,n)
            return ActiveWorkspaceBudget.Units(objects=Math.addExact(16,views(16)),strings=Math.addExact(16,views(16)),
                utf16Units=Math.addExact(Math.addExact(512,views(512)),Math.multiplyExact(textUnits,2)),
                arrays=Math.addExact(32,views(16)),arrayPayloadBytes=Math.addExact(8192,Math.addExact(mul(16),views(4096))),
                lists=Math.addExact(8,views(2)),listCapacity=Math.addExact(32,Math.addExact(mul(8),views(16))),
                maps=1,mapCapacity=Math.addExact(8,views(2)),references=Math.addExact(32,Math.addExact(mul(12),views(16))))
        }
    }
}
