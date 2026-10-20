package org.pag.data.product

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Static composition guard; SQLite transaction proofs are separate. */
class MemoryReportWriterWiringTest {
    private fun source(name:String)=listOf(File("src/main/kotlin/org/pag/data/product/$name.kt"),
        File("data/src/main/kotlin/org/pag/data/product/$name.kt")).first{it.isFile}.readText()
    @Test fun memoryReportMustUseCommonPreQueueAndConfirmedOuterTransaction() {
        val s=source("ProductMemoryReportRepository")
        assertTrue("MemoryReport needs the shared pre-queue boundary",s.contains("WorkspaceWriteBoundary("))
        assertTrue(s.contains("WorkspaceWriterFamily.MEMORY_REPORT"));assertTrue(s.contains("confirmedWorkspaceTransaction("))
        assertFalse(s.contains("withWriteTransaction"))
    }
    @Test fun sectionWritesFreezeBeforeTheSharedWritePath() {
        val s=source("ProductMemoryReportRepository")
        for(method in listOf("create","saveSection")) {
            val body=s.substringAfter("override fun $method(").substringBefore("override fun")
            assertTrue(body.contains("frozenWrite(actionId, publish,"))
        }
        val body=s.substringAfter("private fun frozenWrite(").substringBefore("private fun <T> write(")
        assertTrue(body.indexOf("freezeMemorySection(section)")<body.indexOf("write(actionId, publish)"))
    }
    @Test fun runtimeMustInjectItsCommonMutationPort() {
        val repository=source("ProductRuntime").substringAfter("val memoryReports: MemoryReportRepository").substringBefore("val expenses")
        assertTrue(repository.contains("mutations = workspaceMutations"))
    }
}
