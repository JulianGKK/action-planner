package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class ProtocolRulesTest {
    private val sample = ProtocolInput(ProtocolClassification.RELEVANT, "synthetic", 42)
    private val supplementary = "\uD83E\uDD8B"
    @Test fun nineteenExactTemplatesDoNotPersistOrInterpret() {
        val expected = listOf(
            "POLICE_ARRIVED" to "Polizei eingetroffen", "ORDER_OR_ANNOUNCEMENT" to "Anordnung / Ansage",
            "DEADLINE_SET" to "Frist gesetzt", "EVICTION_ANNOUNCED" to "Räumung angekündigt",
            "FORCE_THREATENED" to "Zwang angedroht", "EVICTION_STARTED" to "Räumung begonnen",
            "PHYSICAL_MEASURE_OR_FORCE" to "Körperliche Maßnahme / Zwangsmittel angewendet",
            "EVICTION_ENDED" to "Räumung beendet", "IDENTITY_CHECK_OR_ED" to "Personalienfeststellung / ED",
            "EXCLUSION_ORDER" to "Platzverweis", "SEARCH" to "Durchsuchung",
            "SEIZURE_OR_CONFISCATION" to "Sicherstellung / Beschlagnahme", "PERSON_TAKEN_AWAY" to "Person abgeführt",
            "CUSTODY" to "Gewahrsam", "PERSON_RELEASED" to "Person freigelassen",
            "MEDICAL_CARE_OR_INJURY" to "Medizinische Versorgung / Verletzung",
            "POLICE_OPERATION_ENDED" to "Polizeieinsatz beendet / Kräfte abgerückt",
            "OTHER_POLICE_MEASURE" to "Sonstige polizeiliche Maßnahme", "CUSTOM_KEY_EVENT" to "Eigenes Schlüsselereignis")
        assertEquals(expected, ProtocolRules.templates.map { it.key to it.title })
        val draft = ProtocolRules.templates.last().input(100)
        assertEquals("", draft.title); assertFalse(ProtocolRules.valid(draft)); assertEquals(100L, draft.occurredAt)
        assertEquals(19, ProtocolRules.templates.map { it.key }.toSet().size)
    }
    @Test fun titleUsesTrimmedCodepoints() {
        assertFalse(ProtocolRules.valid(sample.copy(title = " \n ")))
        assertTrue(ProtocolRules.valid(sample.copy(title = " ${supplementary.repeat(120)} ")))
        assertFalse(ProtocolRules.valid(sample.copy(title = supplementary.repeat(121))))
    }
    @Test fun allOptionalTextLimitsUseCodepoints() {
        val fields: List<Pair<Int, (String) -> ProtocolInput>> = listOf(
            2000 to { s -> sample.copy(description = s) },
            2000 to { s -> sample.copy(announcementText = s, announcementKind = ProtocolAnnouncementKind.WORDING) },
            1000 to { s -> sample.copy(statedLegalBasis = s) },
            80 to { s -> sample.copy(placeLabel = s) })
        for ((max, field) in fields) {
            assertTrue(ProtocolRules.valid(field(" ${supplementary.repeat(max)} ")))
            assertFalse(ProtocolRules.valid(field(supplementary.repeat(max + 1))))
        }
    }
    @Test fun emptyOptionalFieldsBecomeNullWithoutUnicodeNormalization() {
        val i = ProtocolRules.normalized(sample.copy(title = " e\u0301 ", description = " ", placeLabel = "\n", announcementText = " ", statedLegalBasis = "\t"))
        assertEquals("e\u0301", i.title); assertNull(i.description); assertNull(i.placeLabel)
        assertNull(i.announcementText); assertNull(i.statedLegalBasis); assertTrue(ProtocolRules.valid(i))
    }
    @Test fun announcementRequiresItsKind() {
        assertFalse(ProtocolRules.valid(sample.copy(announcementText = "x")))
        assertFalse(ProtocolRules.valid(sample.copy(announcementKind = ProtocolAnnouncementKind.PARAPHRASE)))
        assertFalse(ProtocolRules.valid(sample.copy(announcementText = " ", announcementKind = ProtocolAnnouncementKind.WORDING)))
        assertTrue(ProtocolRules.valid(sample.copy(announcementText = " x ", announcementKind = ProtocolAnnouncementKind.PARAPHRASE)))
    }
    @Test fun reportedPersonOnlyForReportedSourceAndAlwaysOptional() {
        for (source in ProtocolSourceType.entries + listOf(null)) {
            assertTrue(ProtocolRules.valid(sample.copy(sourceType = source)))
            assertEquals(source == ProtocolSourceType.REPORTED, ProtocolRules.valid(sample.copy(sourceType = source, reportedByPersonId = "synthetic-id")))
        }
    }
    @Test fun identifiersAreBoundedOrderedAndNotDeduplicated() {
        val id = ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, " ${supplementary.repeat(120)} ")
        assertTrue(ProtocolRules.valid(sample.copy(officialIdentifiers = List(32) { id })))
        assertFalse(ProtocolRules.valid(sample.copy(officialIdentifiers = List(33) { id })))
        assertFalse(ProtocolRules.valid(sample.copy(officialIdentifiers = listOf(id.copy(value = supplementary.repeat(121))))))
        assertFalse(ProtocolRules.valid(sample.copy(officialIdentifiers = listOf(id.copy(value = " ")))))
        assertEquals(32, ProtocolRules.normalized(sample.copy(officialIdentifiers = List(32) { id })).officialIdentifiers.size)
    }
    @Test fun affectedPersonsAreASetWithNoSmallLimit() {
        val ids = (1..500).map { "synthetic-$it" }.toSet()
        assertTrue(ProtocolRules.valid(sample.copy(affectedPersonIds = ids)))
        assertEquals(2, sample.copy(affectedPersonIds = setOf("a", "a", "b")).affectedPersonIds.size)
    }
    @Test fun freeTypesAndKnownKeys() {
        for (kind in ProtocolClassification.entries) assertTrue(ProtocolRules.valid(sample.copy(classification = kind)))
        assertFalse(ProtocolRules.valid(sample.copy(templateKey = "UNKNOWN")))
        for (t in ProtocolRules.templates) assertTrue(ProtocolRules.valid(t.input(1).copy(title = "synthetic")))
    }
    @Test fun policyIsProtocolOnly() {
        for (state in ActionStatus.entries) {
            val allowed = state == ActionStatus.ACTIVE || state == ActionStatus.COMPLETED
            assertEquals(allowed, ProtocolRules.mayRead(state)); assertEquals(allowed, ProtocolRules.mayWrite(state))
        }
        assertFalse(TaskRules.mayWrite(ActionStatus.COMPLETED))
        assertFalse(OperationalRules.mayUpdateSnapshot(ActionStatus.COMPLETED))
        assertFalse(OperationalRules.mayCreateRole(ActionStatus.COMPLETED))
    }
    @Test fun mistakenTransitionsNeedExplicitCorrection() {
        assertNull(ProtocolRules.nextMistaken(false, ProtocolRevisionType.ORIGINAL))
        assertEquals(true, ProtocolRules.nextMistaken(false, ProtocolRevisionType.MARKED_MISTAKEN))
        assertNull(ProtocolRules.nextMistaken(true, ProtocolRevisionType.MARKED_MISTAKEN))
        assertEquals(true, ProtocolRules.nextMistaken(true, ProtocolRevisionType.ADDENDUM))
        assertEquals(false, ProtocolRules.nextMistaken(false, ProtocolRevisionType.ADDENDUM))
        assertEquals(true, ProtocolRules.nextMistaken(true, ProtocolRevisionType.CORRECTION))
        assertEquals(false, ProtocolRules.nextMistaken(true, ProtocolRevisionType.CORRECTION, true))
        assertNull(ProtocolRules.nextMistaken(true, ProtocolRevisionType.ADDENDUM, true))
    }
    @Test fun expectedSnapshotIncludesChildrenButNotPseudonyms() {
        val revision = ProtocolRevision("r", "a", "e", 1, ProtocolRevisionType.ORIGINAL, sample.copy(affectedPersonIds = setOf("p")), 100, false, false)
        assertNotEquals(revision, revision.copy(input = revision.input.copy(affectedPersonIds = emptySet())))
        assertNotEquals(revision, revision.copy(input = revision.input.copy(officialIdentifiers = listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID, "synthetic")))))
        assertEquals(revision, ProtocolRevisionDetail(revision, listOf(ProtocolPersonReference("p", "Motte")), null).revision)
    }
    @Test fun projectionsHaveNoSensitiveDetailFields() {
        val forbidden = setOf("description", "announcementText", "statedLegalBasis", "officialIdentifiers", "affectedPersonIds", "reportedByPersonId", "placeLabel")
        assertTrue(ProtocolTimelineItem::class.java.declaredFields.none { it.name in forbidden })
        assertEquals(setOf("keyEventCount", "lastEntryRecordedAt"), ProtocolSummary::class.java.declaredFields.map { it.name }.toSet())
        assertTrue(ProtocolRepository::class.java.methods.none { it.name.startsWith("delete") || it.name.startsWith("update") })
    }
}
