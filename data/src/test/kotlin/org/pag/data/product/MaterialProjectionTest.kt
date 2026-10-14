package org.pag.data.product

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MaterialProjectionTest {
    private fun generated(): String = listOf(File("build/generated/ksp/debug/kotlin/org/pag/data/product/MaterialDao_Impl.kt"),
        File("data/build/generated/ksp/debug/kotlin/org/pag/data/product/MaterialDao_Impl.kt")).first { it.isFile }.readText()
    @Test fun generatedSummaryOnlyCountsMaterialRows() {
        val sql = generated().substringAfter("override suspend fun count(").substringBefore("return performSuspending")
        assertTrue(sql.contains("SELECT COUNT(*) FROM materials WHERE actionId=?"))
        for (word in listOf("name", "placeLabel", "quantityLabel", "persons", "tasks", "leadership", "sensitive", "SELECT *")) assertFalse(word, sql.contains(word))
    }
    @Test fun generatedBoardHasDeterministicOrderAndNoOtherBoardsOrP3b() {
        val sql = generated().substringAfter("override suspend fun list(").substringBefore("return performSuspending")
        assertTrue(sql.contains("ORDER BY createdAt,id"))
        for (table in listOf("persons", "tasks", "leadership", "sensitive_person_areas")) assertFalse(table, generated().contains("FROM $table"))
    }
}
