package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.UUID

/** Production document transaction observer on REAL host SQLite/schema-11 entities.
 * Reuses only the existing outer-transaction harness. Room/SQLCipher/key lifecycle simulated;
 * no file pipeline, powerloss or generated Room DAO execution is claimed here. */
class DocumentEvidenceSqliteTest {
    private fun reserveSql():String {
        fun id()=UUID.randomUUID().toString()
        return "INSERT INTO document_jobs(id,actionId,documentId,operation,phase,stagingName,blobName,displayName,format,plaintextSize,fileKey) " +
            "VALUES('${id()}','a','${id()}','IMPORT','IMPORT_RESERVED','${id()}','${id()}','synthetic','TEXT',NULL,zeroblob(32))"
    }
    private fun phase(h:CoreWriterIntegrationTest.H)=runBlocking {
        h.db.usePrepared("SELECT phase FROM document_jobs"){assertTrue(it.step());it.getText(0)}
    }
    @Test fun returnedLocalCommitsSurviveLaterRealSqlRollbackWithoutGlobalRollbackClaim()=CoreWriterIntegrationTest.H().use { h ->
        val e=DocumentMutationEvidence(DocumentMutationEvidence.Operation.IMPORT)
        fun step(s:DocumentMutationEvidence.Step,sql:String)=documentEvidenceScope(e,DocumentMutationEvidence.Resource.DB_KEY) {
            runBlocking{confirmedDocumentTransaction(h.tx(h.context),h.context,e,s){h.db.execute(sql)}}
        }
        step(DocumentMutationEvidence.Step.RESERVED,reserveSql())
        step(DocumentMutationEvidence.Step.WRITING,"UPDATE document_jobs SET phase='IMPORT_WRITING'")
        h.failEndBefore=true
        assertThrows(IllegalStateException::class.java){step(DocumentMutationEvidence.Step.CLOSED,"UPDATE document_jobs SET phase='IMPORT_CLOSED',plaintextSize=1")}
        assertEquals("IMPORT_WRITING",phase(h));assertTrue(e.isConfirmed(DocumentMutationEvidence.Step.RESERVED))
        assertTrue(e.isConfirmed(DocumentMutationEvidence.Step.WRITING));assertFalse(e.isConfirmed(DocumentMutationEvidence.Step.CLOSED))
        assertEquals(DocumentWriteOutcome.UNKNOWN,e.completion().outcome)
        assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,e.completion().cleanup)
    }
    @Test fun realCommitWithLostTransactionReturnRemainsUnknownDespitePersistedRow()=CoreWriterIntegrationTest.H().use { h ->
        val e=DocumentMutationEvidence(DocumentMutationEvidence.Operation.IMPORT);h.failEndAfter=true
        assertThrows(IllegalStateException::class.java){documentEvidenceScope(e,DocumentMutationEvidence.Resource.DB_KEY) {
            runBlocking{confirmedDocumentTransaction(h.tx(h.context),h.context,e,DocumentMutationEvidence.Step.RESERVED){h.db.execute(reserveSql())}}
        }}
        assertEquals("IMPORT_RESERVED",phase(h));assertFalse(e.isConfirmed(DocumentMutationEvidence.Step.RESERVED))
        assertEquals(DocumentWriteOutcome.UNKNOWN,e.completion().outcome)
        assertFalse(e.completion().finalInventoryConfirmed)
    }
}
