package org.pag.data.product

import androidx.room3.Transactor
import org.pag.domain.*
import java.io.File
import java.nio.file.Path

/** One attempt, fixed-size metadata only. Never holds keys, names, paths, source/observer
 * closures or payloads. Sequential producer calls may cross Room coroutine threads.
 * This is evidence, not admission, session authority, a retry log or a Pending registry. */
internal class DocumentMutationEvidence(private val operation: Operation) {
    enum class Operation { IMPORT, DELETE }
    enum class Step { RESERVED, WRITING, INITIALIZE, CREATE, WRITE, CIPHER_CLOSE, FILE_SYNC,
        CLOSED, RENAME, SYNC_FILES, SYNC_STAGING, PUBLISHED, DOCUMENT_COMMIT,
        DELETE_RESERVED, DETACH, UNLINK, ABSENT, DELETE_SYNC, DELETE_FINISH }
    enum class Resource { DB_KEY, FILE_KEY, STREAM, SNAPSHOT, VERIFY }
    private var confirmed = 0L
    private var unknown = 0L
    private var effects = false
    private var failedCleanup = false
    private var unobservedFailure = false
    private var finalInventory = false
    private var claimed = false
    private var frozen:DocumentWriteCompletion?=null
    private val active = IntArray(Resource.entries.size)
    private fun bit(step:Step)=1L shl step.ordinal
    @Synchronized fun begin(expected:Operation) { check(!claimed && frozen==null && operation==expected);claimed=true }
    @Synchronized fun starting(step:Step) { check(frozen==null);unknown=unknown or bit(step);finalInventory=false }
    @Synchronized fun returned(step:Step, changed:Boolean=true) {
        check(frozen==null);confirmed=confirmed or bit(step);unknown=unknown and bit(step).inv()
        effects=effects || changed
    }
    @Synchronized fun acquired(resource:Resource) { check(frozen==null && active[resource.ordinal]<32);active[resource.ordinal]++ }
    @Synchronized fun closed(resource:Resource) { check(frozen==null && active[resource.ordinal]>0);active[resource.ordinal]-- }
    @Synchronized fun cleanupFailed() { check(frozen==null);failedCleanup=true }
    /** Executor/dispatch failure outside an observed producer boundary is never a clean reject. */
    @Synchronized fun markUnknown() { check(frozen==null);unobservedFailure=true }
    @Synchronized fun sealAfterUnexpectedFailure():DocumentWriteCompletion {
        if(frozen!=null)return frozen!!
        unobservedFailure=true
        return seal()
    }
    @Synchronized fun finalInventoryConfirmed() { check(frozen==null);finalInventory=true }
    @Synchronized fun isConfirmed(step:Step)=confirmed and bit(step)!=0L
    @Synchronized fun completion():DocumentWriteCompletion {
        frozen?.let{return it}
        val cleanup=if(failedCleanup || active.any{it!=0})WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED else WorkspaceWriteCleanup.COMPLETE
        val required=if(operation==Operation.IMPORT) listOf(Step.RESERVED,Step.WRITING,Step.CREATE,Step.CIPHER_CLOSE,
            Step.FILE_SYNC,Step.CLOSED,Step.RENAME,Step.SYNC_FILES,Step.SYNC_STAGING,Step.PUBLISHED,Step.DOCUMENT_COMMIT)
            else listOf(Step.DELETE_RESERVED,Step.DETACH,Step.DELETE_SYNC,Step.DELETE_FINISH)
        val end=when {
            unknown!=0L || unobservedFailure || cleanup!=WorkspaceWriteCleanup.COMPLETE -> DocumentWriteOutcome.UNKNOWN
            !effects -> DocumentWriteOutcome.NOT_EXECUTED
            finalInventory && required.all{confirmed and bit(it)!=0L} &&
                (operation==Operation.IMPORT || isConfirmed(Step.UNLINK) || isConfirmed(Step.ABSENT)) -> DocumentWriteOutcome.COMPLETE
            else -> DocumentWriteOutcome.RECOVERY_REQUIRED
        }
        return DocumentWriteCompletion(end,cleanup,end==DocumentWriteOutcome.COMPLETE)
    }
    @Synchronized fun seal():DocumentWriteCompletion = completion().also { frozen=it }
}

/** Full return from a DB/key or verification boundary, not just its work callback.
 * Exceptional return cannot certify cleanup, even when a local commit was already observed. */
internal inline fun <T> documentEvidenceScope(e:DocumentMutationEvidence?, r:DocumentMutationEvidence.Resource, work:()->T):T {
    e?.acquired(r)
    try {val value=work();e?.closed(r);return value}
    catch(t:Throwable){e?.cleanupFailed();throw t}
}

/** Kotlin use semantics, with close observed independently from an exceptional work body. */
internal inline fun <T:AutoCloseable,R> T.documentEvidenceUse(e:DocumentMutationEvidence?, r:DocumentMutationEvidence.Resource,
    work:(T)->R):R {
    e?.acquired(r);var failure:Throwable?=null
    try {return work(this)} catch(t:Throwable){failure=t;throw t}
    finally {
        try {close();e?.closed(r)} catch(close:Throwable) {
            e?.cleanupFailed()
            if(failure==null)throw close else failure.addSuppressed(close)
        }
    }
}

/** Confirm only after an OUTER transaction has returned and the transactor is no longer in
 * transaction. Exceptions at begin/body/end remain UNKNOWN; earlier step bits are preserved. */
internal suspend fun <T> confirmedDocumentTransaction(tx:Transactor,c:StorageContext,e:DocumentMutationEvidence,
    step:DocumentMutationEvidence.Step,changed:(T)->Boolean={true},work:suspend()->T):T {
    if(tx.inTransaction())throw ProductStorageFailure()
    e.starting(step)
    val result=tx.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) {
        work().also{requireDocumentDelete(c,AccessPoint.COMMIT)}
    }
    if(tx.inTransaction())throw ProductStorageFailure()
    e.returned(step,changed(result))
    return result
}

internal interface DocumentFinalInventoryDatabase {
    fun finalInventory(c:StorageContext):DocumentImportInventory
}
/** Read-only, same global DAO + namespace architecture; never recovery or a READY grant. */
internal fun confirmDocumentFinalInventory(c:StorageContext,db:DocumentFinalInventoryDatabase,base:File,e:DocumentMutationEvidence) {
    DocumentNamespaceLane.requireExclusive()
    documentFileGate(c,c.generation,AccessPoint.OPERATION)
    if(c.scope!=AccessScope.READY)throw ProductStorageFailure()
    val expected=db.finalInventory(c) // entire DB/key scope has returned before filesystem I/O
    documentEvidenceScope(e,DocumentMutationEvidence.Resource.VERIFY) {
        val actual=DocumentFileInventory(base).inspectFinal(c,expected.staging,expected.files)
        if(expected.staging.isNotEmpty() || actual.staging.isNotEmpty() || actual.files!=expected.files)throw ProductStorageFailure()
    }
    documentFileGate(c,c.generation)
    e.finalInventoryConfirmed()
}

/** Only delegates established operations; no retry, fallback, additional sync or unlink. */
internal class EvidenceDocumentFileOps(private val delegate:DocumentPublishFileOps,private val e:DocumentMutationEvidence,
    private val deletion:Boolean=false):DocumentPublishFileOps {
    private fun step(step:DocumentMutationEvidence.Step,work:()->Unit) {
        e.starting(step)
        documentEvidenceScope(e,DocumentMutationEvidence.Resource.STREAM){work()}
        e.returned(step)
    }
    override fun moveNoReplace(source:Path,destination:Path)=step(DocumentMutationEvidence.Step.RENAME){delegate.moveNoReplace(source,destination)}
    override fun unlink(source:Path)=step(DocumentMutationEvidence.Step.UNLINK){delegate.unlink(source)}
    override fun syncDirectory(directory:Path)=step(if(deletion)DocumentMutationEvidence.Step.DELETE_SYNC
        else if(directory.fileName.toString()=="files")DocumentMutationEvidence.Step.SYNC_FILES else DocumentMutationEvidence.Step.SYNC_STAGING) {
        delegate.syncDirectory(directory)
    }
}
