package org.pag.prototype.product

import org.pag.domain.*
import java.time.Instant

/** Built inside the original snapshot's admitted UI producer, never during rendering. */
internal class WorkspaceHeaderUi(action:Action,source:WorkspaceSnapshot,private val formats:UiDateFormats) {
    private fun at(value:Long)="${Instant.ofEpochMilli(value).atZone(formats.zone).format(formats.medium)} (${formats.zone.id})"
    val start="Beginn: ${at(action.startAt)}"
    val end="Voraussichtliches Ende: ${at(action.expectedEndAt)}"
    val completed=action.completedAt?.let{"Abgeschlossen: ${at(it)}"}
    val summary=calculatePreparationSummary(source)
    val persons="Personen · ${summary.persons} insgesamt"+(summary.withoutPrimaryRole?.let{" · $it ohne geplante Hauptrolle"}?:"")
    val roles=summary.roles?.let{"Rollen · $it angelegt"}?:"Rollenstatus nicht verfügbar"
    val tasks=summary.open?.let{"Aufgaben · $it offen · ${summary.inProgress} in Arbeit · ${summary.high} hohe Priorität"}?:"Aufgabenstatus nicht verfügbar"
    val costs=source.expenseSummary?.let{"Kosten · ${it.count} Einträge · ${EuroMoney.format(it.totalCents)}"}
    val material=source.materialSummary?.let{if(it.count==0)"Material · Noch kein Material" else "Material · ${it.count} Materialeinträge"}
    val documents=source.documentSummary?.let{"Dokumente · ${it.count} Dokumente"}
}
