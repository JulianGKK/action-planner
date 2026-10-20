package org.pag.data.product

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Static composition proof; real transactions are covered separately by SQLite tests. */
class ProtocolWriterWiringTest {
    private fun source(name:String)=listOf(File("src/main/kotlin/org/pag/data/product/$name.kt"),
        File("data/src/main/kotlin/org/pag/data/product/$name.kt")).first{it.isFile}.readText()
    @Test fun protocolMustUseCommonPreQueueAndConfirmedOuterTransaction() {
        val s=source("ProductProtocolRepository")
        assertTrue("Protocol needs the shared pre-queue boundary",s.contains("WorkspaceWriteBoundary("))
        assertTrue(s.contains("WorkspaceWriterFamily.PROTOCOL"));assertTrue(s.contains("confirmedWorkspaceTransaction("))
        assertFalse(s.contains("withWriteTransaction"))
    }
    @Test fun allWritesFreezeSubmittedCollectionsBeforeTheWritePath() {
        val s=source("ProductProtocolRepository")
        for(method in listOf("createEntry","reviseEntry","markMistaken")) {
            val body=s.substringAfter("override fun $method(").substringBefore("\n    }")
            assertTrue(body.contains("freezeProtocol"));assertTrue(body.indexOf("freezeProtocol")<body.indexOf("write(actionId"))
            if(method!="createEntry")assertTrue(body.contains("freezeProtocolExpected(expectedRevision)"))
        }
    }
    @Test fun runtimeMustInjectItsCommonMutationPort() {
        val protocol=source("ProductRuntime").substringAfter("val protocol: ProtocolRepository").substringBefore("val material")
        assertTrue(protocol.contains("mutations = workspaceMutations"))
    }
}
