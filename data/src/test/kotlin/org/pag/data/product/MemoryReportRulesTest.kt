package org.pag.data.product

import org.pag.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MemoryReportRulesTest {
    private fun source() = listOf(File("../docs/product-p9a-memory-protocol.md"),File("docs/product-p9a-memory-protocol.md")).first { it.isFile }.readText()
    @Test fun frozenCatalogMatchesEveryP9aRowExactly() {
        val rows = Regex("(?m)^\\| ([A-Q]_[A-Z_]+) \\| ([A-Z_]+) \\| (.*?) \\|$").findAll(source()).toList()
        assertEquals(1,MemoryQuestionnaireV1.version); assertEquals(85,rows.size)
        assertEquals(rows.map { it.groupValues[1] },MemoryQuestionnaireV1.questions.map { it.key })
        rows.zip(MemoryQuestionnaireV1.questions).forEach { (row,q) -> assertEquals(row.groupValues[2],q.type.name);assertEquals(row.groupValues[3],q.contractText);assertTrue(row.groupValues[3].startsWith(q.text)) }
        assertEquals(17,MemoryQuestionnaireV1.sections.size); assertEquals(('A'..'Q').map { it.toString() },MemoryQuestionnaireV1.sections.map { it.key })
        assertEquals((0..84).toList(),MemoryQuestionnaireV1.questions.map { it.order });assertEquals(85,MemoryQuestionnaireV1.questions.map { it.key }.distinct().size)
        assertEquals(MemoryAnswerType.entries.toSet(),MemoryQuestionnaireV1.questions.map { it.type }.toSet())
    }
    @Test fun publishedV1CollectionsCannotBeMutated() {
        assertThrows(UnsupportedOperationException::class.java) { (MemoryQuestionnaireV1.questions as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (MemoryQuestionnaireV1.sections as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (MemoryQuestionnaireV1.question("B_ENDING_MODE")!!.options as MutableList).clear() }
    }
    @Test fun optionsAndDependenciesAreStableAndBackwards() {
        MemoryQuestionnaireV1.questions.forEach { q ->
            assertEquals(q.options.size,q.options.map { it.key }.distinct().size)
            q.options.forEach { assertTrue(it.key.matches(Regex("[A-Z0-9_]+"))) }
            q.dependency?.let { dep -> val parent=MemoryQuestionnaireV1.question(dep.parentKey)!!;assertTrue(parent.order<q.order);assertTrue(dep.acceptedOptions.all { key -> parent.options.any { it.key==key } }) }
        }
        assertEquals(listOf("YES","NO","UNKNOWN","NOT_RELEVANT"),MemoryQuestionnaireV1.question("B_AGREEMENT_OR_CONDITION")!!.options.map { it.key })
        assertEquals(listOf("FACE_PHOTOS","FULL_BODY_PHOTO","OTHER_PHOTOS","HEIGHT_MEASURED","FINGERPRINTS_DIGITAL","FINGERPRINTS_ANALOG","PALMS_DIGITAL","PALMS_ANALOG","OTHER_BIOMETRIC","UNCLEAR"),MemoryQuestionnaireV1.question("L_ED_MEASURES")!!.options.map { it.key })
    }
    @Test fun unicodeLimitsAndNormalizationKeepInternalWhitespace() {
        assertTrue(MemoryReportRules.authorValid(" 🦋".trim().repeat(80)));assertFalse(MemoryReportRules.authorValid("🦋".repeat(81)));assertFalse(MemoryReportRules.authorValid(" "))
        for((key,max) in listOf("A_ALLEGATION" to 240,"B_AGREEMENT_DETAILS" to 4000,"A_FREE_NARRATIVE" to 8000,"C_WAIT_DURATION" to 80,"O_RELEASE_TIME" to 80)) {
            assertNotNull(MemoryAnswerCodec.encode(key,MemoryAnswerValue.Text("🦋".repeat(max))))
            assertNull(MemoryAnswerCodec.encode(key,MemoryAnswerValue.Text("🦋".repeat(max+1))))
            assertNull(MemoryAnswerCodec.encode(key,MemoryAnswerValue.Text(" ")))
        }
        assertEquals(MemoryAnswerValue.Text("a\n  b,c"),MemoryAnswerCodec.normalize("A_FREE_NARRATIVE",MemoryAnswerValue.Text(" a\n  b,c ")))
        assertTrue(MemoryReportRules.addendumValid("🦋".repeat(4000)));assertFalse(MemoryReportRules.addendumValid("🦋".repeat(4001)))
    }
    @Test fun lifecycleAndProgressDoNotOpenOtherAreas() {
        ActionStatus.entries.forEach { assertEquals(it in setOf(ActionStatus.ACTIVE,ActionStatus.COMPLETED),MemoryReportRules.mayWrite(it)) }
        assertFalse(MemoryReportRules.maySave(0,2));assertTrue(MemoryReportRules.maySave(0,1));assertTrue(MemoryReportRules.maySave(16,0));assertFalse(MemoryReportRules.maySave(16,17))
        val h=MemoryReportHeader("r","a","Motte",1,MemoryReportStatus.DRAFT,0,0,1,1,null)
        assertFalse(MemoryReportRules.mayComplete(h));assertTrue(MemoryReportRules.mayComplete(h.copy(lastSavedSectionOrdinal=16)))
        assertFalse(TaskRules.mayWrite(ActionStatus.COMPLETED));assertFalse(MaterialRules.mayWrite(ActionStatus.COMPLETED));assertFalse(ActionLifecycle.mayEdit(ActionStatus.COMPLETED));assertTrue(ExpenseRules.mayWrite(ActionStatus.COMPLETED))
        assertThrows(ArithmeticException::class.java){MemoryReportRules.nextRevision(Long.MAX_VALUE)}
    }
    @Test fun headerStatusTimeAndProgressStrict() {
        val h=MemoryReportHeader("r","a","Motte",1,MemoryReportStatus.DRAFT,0,0,100,90,null)
        assertTrue(MemoryReportRules.headerValid(h))
        listOf(h.copy(authorLabel=" Motte "),h.copy(revision=-1),h.copy(questionnaireVersion=2),h.copy(lastSavedSectionOrdinal=17),h.copy(createdAt=-1),h.copy(completedAt=100),h.copy(status=MemoryReportStatus.COMPLETED,completedAt=100),h.copy(status=MemoryReportStatus.COMPLETED,lastSavedSectionOrdinal=16,completedAt=99)).forEach{assertFalse(MemoryReportRules.headerValid(it))}
    }
}
