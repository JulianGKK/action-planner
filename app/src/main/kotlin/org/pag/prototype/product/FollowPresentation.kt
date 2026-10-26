package org.pag.prototype.product

import org.pag.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Collections
import java.util.Locale

/** Disjoint preconstruction receipt for derived strings, formatter/builders, maps and
 * callbacks' borrowed display references. Old presentations never issue end receipts. */
internal object FollowPresentationCost {
    val check=ActiveWorkspaceBudget.Units()
    val planning=ActiveWorkspaceBudget.Units(objects=32,references=128)
    fun units(items:Long,chars:Long):ActiveWorkspaceBudget.Units {
        require(items>=0 && chars>=0)
        fun times(n:Long)=Math.multiplyExact(items,n)
        return ActiveWorkspaceBudget.Units(objects=Math.addExact(128,times(64)),strings=Math.addExact(64,times(32)),
            utf16Units=Math.addExact(4096,Math.addExact(Math.multiplyExact(chars,16),times(512))),
            arrays=32,arrayPayloadBytes=Math.addExact(8192,times(1024)),lists=16,listCapacity=Math.addExact(128,times(16)),
            maps=8,mapCapacity=Math.addExact(32,times(4)),references=Math.addExact(256,times(64)))
    }
}
internal class FollowDates(val zone:ZoneId=ZoneId.systemDefault(),val locale:Locale=Locale.getDefault()) {
    private val format=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).withLocale(locale)
    fun text(at:Long)="${Instant.ofEpochMilli(at).atZone(zone).format(format)} (${zone.id})"
}
internal class ProtocolTimelineUi(v:ProtocolTimelineItem,dates:FollowDates) {
    val event="Ereigniszeit: ${dates.text(v.occurredAt)}"
    val semantics="${v.title} öffnen"
}
internal class ProtocolRevisionUi(v:ProtocolRevisionDetail,dates:FollowDates) {
    private val r=v.revision
    val event="Ereigniszeit: ${dates.text(r.input.occurredAt)}"
    val recorded="Erfasst am ${dates.text(r.recordedAt)}"
    val recordedFollow="Erfasst am: ${dates.text(r.recordedAt)}"
    val heading="Fassung ${r.revisionNumber} · ${protocolRevisionLabel(r.revisionType)}"
    val place=r.input.placeLabel?.let{"Ort / lokale Bezeichnung: $it"}
    val source=r.input.sourceType?.let{"Quellenart: ${protocolSource(it)}"}
    val reported=v.reportedBy?.let{"Mitgeteilt von: ${it.pseudonym}"}
    val identifiers=Collections.unmodifiableList(r.input.officialIdentifiers.map{"${protocolIdentifier(it.type)}: ${it.value}"})
}
/** Immutable member of the existing ProtocolModel, never a second DB read or owner. */
internal class ProtocolPresentation(val timeline:List<ProtocolTimelineItem>?,val detail:ProtocolEntryDetail?,val dates:FollowDates=FollowDates()) {
    val rows=Collections.unmodifiableMap(buildMap{for(v in timeline.orEmpty())put(v.entryId,ProtocolTimelineUi(v,dates))})
    val revisions=Collections.unmodifiableMap(buildMap{for(v in detail?.revisions.orEmpty())put(v.revision.id,ProtocolRevisionUi(v,dates))})
    val history="Frühere Fassungen (${(detail?.revisions?.size?:1)-1})"
    fun matches(rows:List<ProtocolTimelineItem>?,value:ProtocolEntryDetail?)=timeline===rows && detail===value
}
internal class MemoryHeaderUi(v:MemoryReportHeader,dates:FollowDates) {
    val progress=if(v.status==MemoryReportStatus.DRAFT)"Entwurf · ${v.lastSavedSectionOrdinal+1} von 17 Abschnitten gespeichert" else "Abgeschlossen"
    val saved=if(v.status==MemoryReportStatus.DRAFT)"Gespeichert: ${dates.text(v.updatedAt)}" else "Abgeschlossen: ${dates.text(checkNotNull(v.completedAt))}"
    val open="${v.authorLabel} öffnen"
}
internal class MemoryAddendumUi(v:MemoryAddendum,dates:FollowDates) {
    val heading="Ergänzung ${v.sequence+1}"
    val recorded="Erfasst: ${dates.text(v.recordedAt)}"
}
internal class MemoryPresentation(val headers:List<MemoryReportHeader>?,val detail:MemoryReportDetail?,val dates:FollowDates=FollowDates()) {
    val rows=Collections.unmodifiableMap(buildMap{for(v in headers.orEmpty())put(v.id,MemoryHeaderUi(v,dates))})
    val labels=Collections.unmodifiableMap(buildMap{for((k,v) in detail?.answers.orEmpty())put(k,MemoryAnswerCodec.label(k,v))})
    val retained=Collections.unmodifiableMap(buildMap{for((k,_) in detail?.answers.orEmpty()){
        val dep=MemoryQuestionnaireV1.question(k)?.dependency?:continue
        if(!dep.active(checkNotNull(detail).answers))put(k,"Bewusst behaltene Detailangabe. Aktuelle Auswahl: ${labels[dep.parentKey]?:"Keine Angabe"}")
    }})
    val addenda=Collections.unmodifiableMap(buildMap{for(v in detail?.addenda.orEmpty())put(v.sequence,MemoryAddendumUi(v,dates))})
    val version=detail?.header?.let{"Fragebogen Version ${it.questionnaireVersion}"}
    val revision=detail?.header?.let{"Revision: ${it.revision}"}
    val completed=detail?.header?.completedAt?.let{"Abgeschlossen: ${dates.text(it)}"}
    fun matches(rows:List<MemoryReportHeader>?,value:MemoryReportDetail?)=headers===rows && detail===value
}
