package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Inspect SQL actually emitted by Room/KSP, not only the DTO surface or hidden UI fields. */
class ProtocolProjectionTest {
    private fun generated(): String {
        val relative = "build/generated/ksp/debug/kotlin/org/pag/data/product/ProtocolDao_Impl.kt"
        val file = listOf(File(relative), File("data/$relative")).firstOrNull { it.isFile }
        assertNotNull("Room generated DAO must exist before this test", file)
        return file!!.readText()
    }
    private fun query(method: String): String {
        val block = generated().substringAfter("override suspend fun $method(").substringBefore("return performSuspending")
        assertTrue(block.contains("val _sql"))
        return block.substringAfter("val _sql: String = ")
    }
    @Test fun summaryNeverSelectsDetailOrPersonTables() {
        val sql = query("summary")
        for (forbidden in listOf("title", "description", "announcementText", "statedLegalBasis", "persons", "official_identifiers", "sensitive", "leadership", "SELECT *"))
            assertFalse(forbidden, sql.contains(forbidden))
        assertTrue(sql.contains("MAX(o.recordedAt)")); assertTrue(sql.contains("MAX(n.revisionNumber)"))
    }
    @Test fun timelineUsesOnlyApprovedExplicitColumns() {
        val sql = query("timeline")
        for (forbidden in listOf("description", "placeLabel", "sourceType", "announcementText", "statedLegalBasis", "official_identifiers", "affected_persons", "reported_by_persons", "sensitive", "leadership", "SELECT *"))
            assertFalse(forbidden, sql.contains(forbidden))
        assertTrue(sql.contains("ORDER BY r.occurredAt,o.recordedAt,e.id"))
    }
    @Test fun personProjectionHasOnlyIdAndPseudonym() {
        val block = generated().substringAfter("override suspend fun persons(").substringBefore("return performSuspending")
        assertTrue(block.contains("SELECT id,pseudonym FROM persons"))
        assertFalse(block.contains("SELECT *")); assertFalse(block.contains("sensitive")); assertFalse(block.contains("leadership"))
        assertFalse(generated().contains("FROM sensitive_person_areas"))
        assertFalse(generated().contains("FROM leadership"))
    }
}
