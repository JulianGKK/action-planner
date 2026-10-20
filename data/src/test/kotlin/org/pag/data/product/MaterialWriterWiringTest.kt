package org.pag.data.product

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring guard, complemented by real SQLite boundary/store tests and offline native fixtures. */
class MaterialWriterWiringTest {
    private fun source(name:String)=listOf(File("src/main/kotlin/org/pag/data/product/$name.kt"),
        File("data/src/main/kotlin/org/pag/data/product/$name.kt")).first{it.isFile}.readText()
    @Test fun materialMustFenceBeforeQueueAndUseConfirmedOuterTransaction() {
        val repository=source("ProductMaterialRepository")
        assertTrue("Material must use the common pre-queue boundary",repository.contains("WorkspaceWriteBoundary("))
        assertTrue(repository.contains("WorkspaceWriterFamily.MATERIAL"))
        assertTrue(repository.contains("confirmedWorkspaceTransaction("))
        assertFalse(repository.contains("withWriteTransaction"))
    }
    @Test fun runtimeMustPassTheSameMutationPortToMaterial() {
        val material=source("ProductRuntime").substringAfter("val material: MaterialRepository").substringBefore("val memoryReports")
        assertTrue("Material must receive the runtime's common mutation port",material.contains("mutations = workspaceMutations"))
    }
}
