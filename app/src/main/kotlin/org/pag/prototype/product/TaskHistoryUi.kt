package org.pag.prototype.product

import org.pag.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Collections

/** One immutable <=32-row detail presentation, admitted on the original workspace.
 * Old labels/page aliases stay charged when this model member is replaced/cleared. */
internal class TaskHistoryUi(page:CompactTaskEvents,offset:Long){
    val range="Ereignisse ${offset+1}–${offset+page.events.size} von ${page.total}"
    val lastPage=if(page.total==0L)1L else (page.total-1)/32+1
    val pageLabel="Seite (1–$lastPage)"
    val currentPage=(offset/32+1).toString()
    private val formatter=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    private val zone=ZoneId.systemDefault()
    val events:List<String> = Collections.unmodifiableList(page.events.map{
        "${Instant.ofEpochMilli(it.occurredAt).atZone(zone).format(formatter)} · ${eventLabel(it.type)}"
    })
    companion object{
        fun cost(n:Int):ActiveWorkspaceBudget.Units {
            require(n in 0..32)
            return ActiveWorkspaceBudget.Units(objects=64+n.toLong()*32,strings=16+n.toLong()*8,
                utf16Units=2048+n.toLong()*512,lists=4,listCapacity=n.toLong()*4,arrays=4,arrayPayloadBytes=1024,references=64+n.toLong()*16)
        }
    }
}
