package org.pag.domain

/** Typed wire values. The frozen question type constrains these variants at the boundary. */
sealed interface MemoryAnswerValue {
    data class Text(val text: String) : MemoryAnswerValue
    data class Option(val key: String) : MemoryAnswerValue
    data class Multi(val keys: List<String>) : MemoryAnswerValue
    data class Count(val count: Int) : MemoryAnswerValue
}
enum class MemoryReportStatus { DRAFT, COMPLETED }
data class MemoryReportHeader(val id: String, val actionId: String, val authorLabel: String,
    val questionnaireVersion: Int, val status: MemoryReportStatus, val revision: Long,
    val lastSavedSectionOrdinal: Int, val createdAt: Long, val updatedAt: Long, val completedAt: Long?)
data class MemoryAddendum(val sequence: Int, val content: String, val recordedAt: Long)
data class MemoryReportDetail(val header: MemoryReportHeader, val answers: Map<String, MemoryAnswerValue>, val addenda: List<MemoryAddendum>)
data class MemoryReportSummary(val draftCount: Int, val completedCount: Int)
data class MemorySectionSave(val sectionOrdinal: Int, val answers: Map<String, MemoryAnswerValue>,
    val retainedConflicts: Set<String> = emptySet(), val authorLabel: String? = null)
data class MemoryEncodedAnswer(val valueKind: String, val payload: String)
object MemoryReportRules {
    fun mayRead(s: ActionStatus) = s == ActionStatus.ACTIVE || s == ActionStatus.COMPLETED
    fun mayWrite(s: ActionStatus) = mayRead(s)
    fun bounded(text: String, max: Int) = text.isNotEmpty() && Character.codePointCount(text, 0, text.length) <= max
    fun authorValid(text: String) = bounded(text.trim(), 80)
    fun addendumValid(text: String) = bounded(text.trim(), 4000)
    fun maySave(lastSaved: Int, section: Int) = section in 0..16 && section <= lastSaved + 1
    fun mayComplete(h: MemoryReportHeader) = h.status == MemoryReportStatus.DRAFT && h.lastSavedSectionOrdinal == 16
    fun nextRevision(revision: Long): Long = Math.addExact(revision, 1)
    fun headerValid(h: MemoryReportHeader) = h.id.isNotBlank() && h.actionId.isNotBlank() && h.questionnaireVersion == 1 &&
        authorValid(h.authorLabel) && h.authorLabel == h.authorLabel.trim() && h.revision >= 0 &&
        h.lastSavedSectionOrdinal in 0..16 && h.createdAt >= 0 && h.updatedAt >= 0 &&
        if (h.status == MemoryReportStatus.DRAFT) h.completedAt == null else
            h.lastSavedSectionOrdinal == 16 && h.completedAt != null && h.completedAt >= h.createdAt
    val order = compareBy<MemoryReportHeader> { it.createdAt }.thenBy { it.id }
}
/** Canonical, delimiter-safe codec; all decoding is strict, including normalization and ordering. */
object MemoryAnswerCodec {
    fun encode(key: String, value: MemoryAnswerValue): MemoryEncodedAnswer? {
        val q = MemoryQuestionnaireV1.question(key) ?: return null
        return when (value) {
            is MemoryAnswerValue.Text -> {
                if (q.type !in setOf(MemoryAnswerType.SHORT_TEXT, MemoryAnswerType.LONG_TEXT, MemoryAnswerType.APPROX_DURATION, MemoryAnswerType.APPROX_TIME)) return null
                val text = value.text.trim()
                if (!MemoryReportRules.bounded(text, q.limit)) null else MemoryEncodedAnswer("TEXT", text)
            }
            is MemoryAnswerValue.Option -> if (q.options.any { it.key == value.key }) MemoryEncodedAnswer("OPTION", value.key) else null
            is MemoryAnswerValue.Multi -> {
                if (q.type != MemoryAnswerType.MULTI_CHOICE || value.keys.isEmpty() || value.keys.distinct().size != value.keys.size ||
                    value.keys.any { k -> q.options.none { it.key == k } }) return null
                if (value.keys.size > 1 && value.keys.any { it in setOf("UNKNOWN", "UNCLEAR", "NO_ASSESSMENT") }) return null
                MemoryEncodedAnswer("MULTI", q.options.filter { it.key in value.keys }.joinToString(",") { it.key })
            }
            is MemoryAnswerValue.Count -> if (q.type == MemoryAnswerType.OPTIONAL_COUNT && value.count in 0..99_999) MemoryEncodedAnswer("COUNT", value.count.toString()) else null
        }?.takeUnless { q.type == MemoryAnswerType.MULTI_CHOICE && it.valueKind != "MULTI" }
    }
    fun decode(key: String, kind: String, payload: String): MemoryAnswerValue? {
        val value = when (kind) {
            "TEXT" -> MemoryAnswerValue.Text(payload)
            "OPTION" -> MemoryAnswerValue.Option(payload)
            "MULTI" -> MemoryAnswerValue.Multi(payload.split(','))
            "COUNT" -> MemoryAnswerValue.Count(payload.toIntOrNull() ?: return null)
            else -> return null
        }
        return value.takeIf { encode(key, it) == MemoryEncodedAnswer(kind, payload) }
    }
    fun normalize(key: String, value: MemoryAnswerValue): MemoryAnswerValue? = encode(key, value)?.let { decode(key, it.valueKind, it.payload) }
    fun label(key: String, value: MemoryAnswerValue?): String {
        val q = MemoryQuestionnaireV1.question(key) ?: return "Keine Angabe"
        return when (value) {
            null -> "Keine Angabe"
            is MemoryAnswerValue.Text -> (if (q.type in setOf(MemoryAnswerType.APPROX_DURATION, MemoryAnswerType.APPROX_TIME)) "Ungefähr: " else "") + value.text
            is MemoryAnswerValue.Option -> q.options.firstOrNull { it.key == value.key }?.label ?: "Keine Angabe"
            is MemoryAnswerValue.Multi -> q.options.filter { it.key in value.keys }.joinToString { it.label }
            is MemoryAnswerValue.Count -> value.count.toString()
        }
    }
}
interface MemoryReportRepository {
    fun summary(actionId: String, publish: (OperationsResult<MemoryReportSummary>) -> Unit)
    fun list(actionId: String, publish: (OperationsResult<List<MemoryReportHeader>>) -> Unit)
    fun detail(actionId: String, reportId: String, publish: (OperationsResult<MemoryReportDetail>) -> Unit)
    fun create(actionId: String, authorLabel: String, firstSection: MemorySectionSave, publish: (OperationsResult<MemoryReportHeader>) -> Unit)
    fun saveSection(actionId: String, expected: MemoryReportHeader, section: MemorySectionSave, publish: (OperationsResult<MemoryReportHeader>) -> Unit)
    fun complete(actionId: String, expected: MemoryReportHeader, publish: (OperationsResult<MemoryReportHeader>) -> Unit)
    fun addAddendum(actionId: String, expected: MemoryReportHeader, content: String, publish: (OperationsResult<MemoryReportHeader>) -> Unit)
}
