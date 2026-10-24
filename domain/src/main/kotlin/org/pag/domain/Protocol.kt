package org.pag.domain

enum class ProtocolClassification { KEY_EVENT, RELEVANT }
enum class ProtocolRevisionType { ORIGINAL, CORRECTION, ADDENDUM, MARKED_MISTAKEN }
enum class ProtocolSourceType { OWN_OBSERVATION, REPORTED, OFFICIAL_ANNOUNCEMENT, UNCLEAR }
enum class ProtocolAnnouncementKind { WORDING, PARAPHRASE }
enum class ProtocolOfficialIdentifierType { SERVICE_OR_ID_NUMBER, DISPLAYED_NAME, UNIT_OR_VEHICLE_ID, OTHER_OFFICIAL_ID }
data class ProtocolOfficialIdentifier(val type: ProtocolOfficialIdentifierType, val value: String)

/** Complete desired content. Capture time, phase and revision identity are repository-owned. */
data class ProtocolInput(
    val classification: ProtocolClassification,
    val title: String,
    val occurredAt: Long,
    val occurredAtApproximate: Boolean = false,
    val templateKey: String? = null,
    val description: String? = null,
    val sourceType: ProtocolSourceType? = null,
    val reportedByPersonId: String? = null,
    val affectedPersonIds: Set<String> = emptySet(),
    val placeLabel: String? = null,
    val announcementText: String? = null,
    val announcementKind: ProtocolAnnouncementKind? = null,
    val statedLegalBasis: String? = null,
    val officialIdentifiers: List<ProtocolOfficialIdentifier> = emptyList()
)
data class ProtocolEntry(val id: String, val actionId: String, val createdAfterCompletion: Boolean)
/** Equality includes all normalized content/relations, never display pseudonyms. */
data class ProtocolRevision(
    val id: String, val actionId: String, val entryId: String, val revisionNumber: Long,
    val revisionType: ProtocolRevisionType, val input: ProtocolInput,
    val recordedAt: Long, val recordedAfterCompletion: Boolean, val mistaken: Boolean
)
data class ProtocolPersonReference(val id: String, val pseudonym: String)
data class ProtocolRevisionDetail(
    val revision: ProtocolRevision,
    val affectedPersons: List<ProtocolPersonReference>,
    val reportedBy: ProtocolPersonReference?
)
data class ProtocolEntryDetail(val entry: ProtocolEntry, val revisions: List<ProtocolRevisionDetail>) {
    val current: ProtocolRevisionDetail get() = revisions.last()
}
data class ProtocolExpectedRevision(val entry: ProtocolEntry, val revision: ProtocolRevision)
data class ProtocolSaved(val entryId: String, val revisionId: String, val revisionNumber: Long)
data class ProtocolSummary(val keyEventCount: Long, val lastEntryRecordedAt: Long?)
data class ProtocolTimelineItem(
    val entryId: String, val revisionId: String, val revisionNumber: Long,
    val revisionType: ProtocolRevisionType, val classification: ProtocolClassification,
    val templateKey: String?, val title: String, val occurredAt: Long,
    val occurredAtApproximate: Boolean, val recordedAt: Long,
    val recordedAfterCompletion: Boolean, val createdAfterCompletion: Boolean,
    val mistaken: Boolean, val originalRecordedAt: Long
) { val hasRevisions: Boolean get() = revisionNumber > 1 }
enum class ProtocolFailure { NOT_FOUND, WRONG_ACTION, INVALID_STATE, INVALID, STALE, OWNER_UNCONFIRMED, BUDGET_REFUSED }
sealed interface ProtocolResult<out T> {
    data class Value<T>(val value: T) : ProtocolResult<T>
    data class Rejected(val reason: ProtocolFailure) : ProtocolResult<Nothing>
}
data class ProtocolTemplate(val key: String, val title: String) {
    /** Opening a template only constructs RAM content; it performs no storage work. */
    fun input(occurredAt: Long) = ProtocolInput(ProtocolClassification.KEY_EVENT, title.takeUnless { key == "CUSTOM_KEY_EVENT" }.orEmpty(), occurredAt, templateKey = key)
}
object ProtocolRules {
    fun mayRead(status: ActionStatus) = status == ActionStatus.ACTIVE || status == ActionStatus.COMPLETED
    fun mayWrite(status: ActionStatus) = mayRead(status)
    private fun optional(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
    fun normalized(input: ProtocolInput) = input.copy(
        title = input.title.trim(), description = optional(input.description),
        placeLabel = optional(input.placeLabel), announcementText = optional(input.announcementText),
        statedLegalBasis = optional(input.statedLegalBasis),
        affectedPersonIds = input.affectedPersonIds.toSet(),
        officialIdentifiers = input.officialIdentifiers.map { it.copy(value = it.value.trim()) }
    )
    private fun length(value: String) = Character.codePointCount(value, 0, value.length)
    fun valid(input: ProtocolInput): Boolean {
        val i = normalized(input)
        return i.title.isNotEmpty() && length(i.title) <= 120 &&
            (i.description == null || length(i.description) <= 2000) &&
            (i.announcementText == null || length(i.announcementText) <= 2000) &&
            (i.statedLegalBasis == null || length(i.statedLegalBasis) <= 1000) &&
            (i.placeLabel == null || length(i.placeLabel) <= 80) &&
            ((i.announcementText == null) == (i.announcementKind == null)) &&
            (i.reportedByPersonId == null || i.sourceType == ProtocolSourceType.REPORTED) &&
            (i.templateKey == null || templates.any { it.key == i.templateKey }) &&
            i.officialIdentifiers.size <= 32 && i.officialIdentifiers.all { it.value.isNotEmpty() && length(it.value) <= 120 }
    }
    /** Null means an invalid transition, not a false mistaken state. */
    fun nextMistaken(previous: Boolean, type: ProtocolRevisionType, clearMistaken: Boolean = false): Boolean? = when (type) {
        ProtocolRevisionType.ORIGINAL -> null
        ProtocolRevisionType.CORRECTION -> if (clearMistaken && !previous) null else previous && !clearMistaken
        ProtocolRevisionType.ADDENDUM -> if (clearMistaken) null else previous
        ProtocolRevisionType.MARKED_MISTAKEN -> if (previous || clearMistaken) null else true
    }
    val templates = listOf(
        ProtocolTemplate("POLICE_ARRIVED", "Polizei eingetroffen"),
        ProtocolTemplate("ORDER_OR_ANNOUNCEMENT", "Anordnung / Ansage"),
        ProtocolTemplate("DEADLINE_SET", "Frist gesetzt"),
        ProtocolTemplate("EVICTION_ANNOUNCED", "Räumung angekündigt"),
        ProtocolTemplate("FORCE_THREATENED", "Zwang angedroht"),
        ProtocolTemplate("EVICTION_STARTED", "Räumung begonnen"),
        ProtocolTemplate("PHYSICAL_MEASURE_OR_FORCE", "Körperliche Maßnahme / Zwangsmittel angewendet"),
        ProtocolTemplate("EVICTION_ENDED", "Räumung beendet"),
        ProtocolTemplate("IDENTITY_CHECK_OR_ED", "Personalienfeststellung / ED"),
        ProtocolTemplate("EXCLUSION_ORDER", "Platzverweis"),
        ProtocolTemplate("SEARCH", "Durchsuchung"),
        ProtocolTemplate("SEIZURE_OR_CONFISCATION", "Sicherstellung / Beschlagnahme"),
        ProtocolTemplate("PERSON_TAKEN_AWAY", "Person abgeführt"),
        ProtocolTemplate("CUSTODY", "Gewahrsam"),
        ProtocolTemplate("PERSON_RELEASED", "Person freigelassen"),
        ProtocolTemplate("MEDICAL_CARE_OR_INJURY", "Medizinische Versorgung / Verletzung"),
        ProtocolTemplate("POLICE_OPERATION_ENDED", "Polizeieinsatz beendet / Kräfte abgerückt"),
        ProtocolTemplate("OTHER_POLICE_MEASURE", "Sonstige polizeiliche Maßnahme"),
        ProtocolTemplate("CUSTOM_KEY_EVENT", "Eigenes Schlüsselereignis")
    )
}
/** SENSITIVE action protocol, normal READY access without a Leadership grant. */
interface ProtocolRepository {
    fun summary(actionId: String, publish: (ProtocolResult<ProtocolSummary>) -> Unit)
    fun listTimeline(actionId: String, publish: (ProtocolResult<List<ProtocolTimelineItem>>) -> Unit)
    fun getEntry(actionId: String, entryId: String, publish: (ProtocolResult<ProtocolEntryDetail>) -> Unit)
    fun createEntry(actionId: String, expectedActionStatus: ActionStatus, input: ProtocolInput, publish: (ProtocolResult<ProtocolSaved>) -> Unit)
    fun reviseEntry(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision,
        revisionType: ProtocolRevisionType, input: ProtocolInput, clearMistaken: Boolean = false,
        publish: (ProtocolResult<ProtocolSaved>) -> Unit)
    fun markMistaken(actionId: String, expectedActionStatus: ActionStatus, expectedRevision: ProtocolExpectedRevision,
        publish: (ProtocolResult<ProtocolSaved>) -> Unit)
}
