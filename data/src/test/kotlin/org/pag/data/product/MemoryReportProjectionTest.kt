package org.pag.data.product
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class MemoryReportProjectionTest {
    @Test fun repositoryHasNoPersonP3bOrOtherFeatureQueries() {
        val s=listOf(File("src/main/kotlin/org/pag/data/product/ProductMemoryReportRepository.kt"),File("data/src/main/kotlin/org/pag/data/product/ProductMemoryReportRepository.kt")).first{it.isFile}.readText()
        for(no in listOf(".persons()",".leadership()",".protocol()",".tasks()",".expenses()",".materials()"))assertFalse(no,s.contains(no))
    }
    @Test fun generatedSummaryAndListDoNotReadContent() {
        val s=listOf(File("build/generated/ksp/debug/kotlin/org/pag/data/product/MemoryReportDao_Impl.kt"),File("data/build/generated/ksp/debug/kotlin/org/pag/data/product/MemoryReportDao_Impl.kt")).first{it.isFile}.readText()
        for(method in listOf("summary","invalidHeaders","list")) {
            val sql=s.substringAfter("override suspend fun $method(").substringBefore("return performSuspending")
            assertTrue(sql.contains("memory_reports"))
            for(no in listOf("memory_answers","memory_addenda","persons","sensitive","protocol","expense","SELECT *"))assertFalse(no,sql.contains(no))
            if(method!="list")assertFalse(sql.contains("authorLabel"))
        }
    }
}
