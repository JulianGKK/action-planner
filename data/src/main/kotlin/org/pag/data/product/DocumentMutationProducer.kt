package org.pag.data.product

import org.pag.domain.*
import java.io.File
import java.io.InputStream

/** Product path shared with host tests. No Pending/session owner; the caller's existing
 * context and namespace lane authorize every step. Evidence is local to this one attempt. */
internal class DocumentMutationProducer(private val base:File,val evidence:DocumentMutationEvidence,
    private val fileOps:DocumentPublishFileOps=AndroidDocumentPublishFileOps,
    private val sync:(java.nio.channels.FileChannel)->Unit={it.force(true)}) {
    fun import(c:StorageContext,db:DocumentImportDatabase,publication:DocumentPublicationDatabase,
        finalDb:DocumentFinalInventoryDatabase,actionId:String,name:String,format:DocumentFormat,
        source:()->InputStream):OperationsResult<DocumentStagingReceipt> = try {
        evidence.begin(DocumentMutationEvidence.Operation.IMPORT)
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            val result=DocumentImportStaging(db,base,sync).stageObserved(c,actionId,name,format,evidence,source)
            if(result is OperationsResult.Value) {
                DocumentPublication(publication,base,fileOps,evidence).complete(c,result.value.jobId)
                confirmDocumentFinalInventory(c,finalDb,base,evidence)
            }
            result
        }
    } finally { evidence.seal() }

    fun delete(c:StorageContext,db:DocumentDeleteDatabase,finalDb:DocumentFinalInventoryDatabase,
        actionId:String,expected:DocumentAsset) = try {
        evidence.begin(DocumentMutationEvidence.Operation.DELETE)
        DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            DocumentDelete(db,base,fileOps,evidence).delete(c,actionId,expected)
            confirmDocumentFinalInventory(c,finalDb,base,evidence)
        }
    } finally { evidence.seal() }
}
