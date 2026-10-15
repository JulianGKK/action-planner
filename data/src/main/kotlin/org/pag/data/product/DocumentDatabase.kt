package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.*
import java.util.UUID

internal enum class DocumentOperation { IMPORT, DELETE }
internal enum class DocumentPhase { IMPORT_RESERVED, IMPORT_WRITING, IMPORT_CLOSED, FILE_PUBLISHED, DELETE_RESERVED, DELETE_FILE_PENDING }

/** Never return this row through a domain port or log its contents. */
@Entity(tableName = "documents", foreignKeys = [ForeignKey(entity = ActionRow::class,
    parentColumns = ["id"], childColumns = ["actionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["actionId", "id"], unique = true), Index(value = ["blobName"], unique = true)])
internal class DocumentRow(@PrimaryKey val id: String, val actionId: String, val displayName: String,
    val format: String, val plaintextSize: Long, val importedAt: Long, val blobName: String, val fileKey: ByteArray)

@Entity(tableName = "document_jobs", foreignKeys = [ForeignKey(entity = ActionRow::class,
    parentColumns = ["id"], childColumns = ["actionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["actionId"]), Index(value = ["documentId"], unique = true),
        Index(value = ["blobName"], unique = true), Index(value = ["stagingName"], unique = true)])
internal class DocumentJobRow(@PrimaryKey val id: String, val actionId: String, val documentId: String,
    val operation: String, val phase: String, val stagingName: String?, val blobName: String,
    val displayName: String?, val format: String?, val plaintextSize: Long?, val fileKey: ByteArray?)

/** Explicit key-free Room projection, also used for delete expected snapshots. */
internal data class DocumentMetadataRow(val id: String, val actionId: String, val displayName: String,
    val format: String, val plaintextSize: Long, val importedAt: Long) {
    fun model(): DocumentAsset {
        val f = DocumentFormat.entries.firstOrNull { it.name == format } ?: throw ProductStorageFailure()
        val asset = DocumentAsset(id, actionId, displayName, f, plaintextSize, importedAt)
        if (!DocumentRules.valid(asset)) throw ProductStorageFailure()
        return asset
    }
}
internal data class DocumentCapacity(val count: Long, val plaintextBytes: Long) {
    fun admitsImport(): Boolean = count in 0 until DocumentRules.MAX_DOCUMENTS.toLong() &&
        plaintextBytes in 0..(DocumentRules.MAX_ACTION_BYTES - DocumentRules.MAX_DOCUMENT_BYTES)
}

internal object DocumentStorageRules {
    fun opaque(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    fun valid(job: DocumentJobRow): Boolean {
        if (!opaque(job.id) || !opaque(job.documentId) || !opaque(job.blobName) || job.actionId.isBlank()) return false
        return when (job.operation) {
            "IMPORT" -> opaque(job.stagingName ?: "") && job.stagingName != job.blobName &&
                job.displayName?.let { DocumentRules.validName(it) && it == it.trim() } == true &&
                DocumentFormat.entries.any { it.name == job.format } && job.fileKey?.size == 32 &&
                when (job.phase) {
                    "IMPORT_RESERVED", "IMPORT_WRITING" -> job.plaintextSize == null
                    "IMPORT_CLOSED", "FILE_PUBLISHED" -> job.plaintextSize?.let(DocumentRules::validSize) == true
                    else -> false
                }
            "DELETE" -> job.phase in setOf("DELETE_RESERVED", "DELETE_FILE_PENDING") && job.stagingName == null &&
                job.displayName == null && job.format == null && job.fileKey == null &&
                job.plaintextSize?.let(DocumentRules::validSize) == true
            else -> false
        }
    }
    fun validateBindings(documents: List<DocumentRow>, jobs: List<DocumentJobRow>) {
        val owners = mutableMapOf<String,Pair<String,String>>()
        fun claim(name: String, document: String, action: String) {
            val owner = document to action
            if (owners.putIfAbsent(name,owner)?.let { it != owner } == true) throw ProductStorageFailure()
        }
        documents.forEach { claim(it.blobName,it.id,it.actionId) }
        jobs.forEach { j ->
            val d = documents.firstOrNull { it.id == j.documentId }
            if (d != null && (j.operation != "DELETE" || j.phase != "DELETE_RESERVED" || d.actionId != j.actionId || d.blobName != j.blobName)) throw ProductStorageFailure()
            claim(j.blobName,j.documentId,j.actionId)
            j.stagingName?.let { claim(it,j.documentId,j.actionId) }
        }
    }
    fun capacity(documents: List<DocumentRow>, jobs: List<DocumentJobRow>): DocumentCapacity {
        val ids = mutableSetOf<String>(); val names = mutableSetOf<String>()
        var bytes = 0L; var count = 0L
        fun add(size: Long) { bytes = Math.addExact(bytes, size); count = Math.addExact(count, 1L) }
        try {
            documents.forEach { d ->
                DocumentMetadataRow(d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt).model()
                if (!opaque(d.id) || !opaque(d.blobName) || d.fileKey.size != 32 || !ids.add(d.id) || !names.add(d.blobName)) throw ProductStorageFailure()
                add(d.plaintextSize)
            }
            val jobIds = mutableSetOf<String>(); val jobDocuments = mutableSetOf<String>()
            jobs.forEach { j ->
                if (!valid(j) || !jobIds.add(j.id) || !jobDocuments.add(j.documentId)) throw ProductStorageFailure()
                val d = documents.firstOrNull { it.id == j.documentId }
                if (j.operation == "IMPORT") {
                    if (d != null || !ids.add(j.documentId) || !names.add(j.blobName) || !names.add(j.stagingName!!)) throw ProductStorageFailure()
                    add(j.plaintextSize ?: DocumentRules.MAX_DOCUMENT_BYTES)
                } else if (j.phase == "DELETE_RESERVED") {
                    if (d == null || d.actionId != j.actionId || d.blobName != j.blobName || d.plaintextSize != j.plaintextSize) throw ProductStorageFailure()
                } else {
                    if (d != null || !ids.add(j.documentId) || !names.add(j.blobName)) throw ProductStorageFailure()
                    add(j.plaintextSize!!)
                }
            }
            if (count > DocumentRules.MAX_DOCUMENTS || bytes > DocumentRules.MAX_ACTION_BYTES) throw ProductStorageFailure()
            return DocumentCapacity(count,bytes)
        } catch (_: ArithmeticException) { throw ProductStorageFailure() }
    }
}

@Dao internal abstract class DocumentDao {
    @Query("SELECT COUNT(*) FROM documents d WHERE d.actionId=:a AND NOT EXISTS (SELECT 1 FROM document_jobs j WHERE j.documentId=d.id AND j.operation='DELETE')")
    abstract suspend fun count(a: String): Int
    @Query("SELECT d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt FROM documents d WHERE d.actionId=:a AND NOT EXISTS (SELECT 1 FROM document_jobs j WHERE j.documentId=d.id AND j.operation='DELETE') ORDER BY d.importedAt,d.id")
    abstract suspend fun list(a: String): List<DocumentMetadataRow>
    @Query("SELECT d.id,d.actionId,d.displayName,d.format,d.plaintextSize,d.importedAt FROM documents d WHERE d.actionId=:a AND d.id=:id AND NOT EXISTS (SELECT 1 FROM document_jobs j WHERE j.documentId=d.id AND j.operation='DELETE')")
    abstract suspend fun get(a: String, id: String): DocumentMetadataRow?
    @Query("SELECT id,actionId,displayName,format,plaintextSize,importedAt,blobName,fileKey FROM documents")
    protected abstract suspend fun inventoryDocuments(): List<DocumentRow>
    @Query("SELECT id,actionId,documentId,operation,phase,stagingName,blobName,displayName,format,plaintextSize,fileKey FROM document_jobs")
    protected abstract suspend fun inventoryJobs(): List<DocumentJobRow>
    @Query("SELECT status FROM actions WHERE id=:a") protected abstract suspend fun actionStatus(a: String): String?
    @Insert protected abstract suspend fun insertJob(job: DocumentJobRow)

    @Query("UPDATE document_jobs SET phase=:next,plaintextSize=:size WHERE id=:id AND phase=:old")
    protected abstract suspend fun changeImportPhase(id: String, old: String, next: String, size: Long?): Int

    @Insert protected abstract suspend fun insertPublishedDocument(row: DocumentRow)
    @Query("DELETE FROM document_jobs WHERE id=:id AND phase='FILE_PUBLISHED'")
    protected abstract suspend fun deletePublishedJob(id: String): Int

    /** Only internal coordination receives the scoped DEK; never a product projection. */
    @Transaction open suspend fun publicationSnapshot(id: String, phase: String, expected: DocumentJobRow?, c: StorageContext): DocumentPublicationSnapshot {
        requireOperations(c,AccessPoint.OPERATION)
        if (c.scope != AccessScope.READY || !DocumentStorageRules.opaque(id) || phase !in setOf("IMPORT_CLOSED","FILE_PUBLISHED")) throw ProductStorageFailure()
        val docs = inventoryDocuments()
        var jobs = emptyList<DocumentJobRow>()
        try {
            jobs = inventoryJobs()
            DocumentStorageRules.validateBindings(docs,jobs)
            (docs.map { it.actionId } + jobs.map { it.actionId }).toSet().forEach { a ->
                DocumentStorageRules.capacity(docs.filter { it.actionId == a },jobs.filter { it.actionId == a })
            }
            // Other interrupted phases require later reconciliation, never guessing a file layout.
            if (jobs.any { it.operation != "IMPORT" || it.phase !in setOf("IMPORT_CLOSED","FILE_PUBLISHED") }) throw ProductStorageFailure()
            val job = jobs.singleOrNull { it.id == id } ?: throw ProductStorageFailure()
            if (job.phase != phase || docs.any { it.id == job.documentId } ||
                (expected != null && !sameDocumentImport(job,expected)) ||
                actionStatus(job.actionId) !in setOf("PREPARATION","ACTIVE","COMPLETED")) throw ProductStorageFailure()
            val files = docs.map { it.blobName }.toSet() + jobs.filter { it.phase == "FILE_PUBLISHED" }.map { it.blobName }
            val staging = jobs.filter { it.phase == "IMPORT_CLOSED" }.map { it.stagingName!! }.toSet()
            requireOperations(c)
            val copy = DocumentJobRow(job.id,job.actionId,job.documentId,job.operation,job.phase,job.stagingName,job.blobName,
                job.displayName,job.format,job.plaintextSize,job.fileKey!!.copyOf())
            return DocumentPublicationSnapshot(copy,DocumentImportInventory(files,staging))
        } finally { docs.forEach { it.fileKey.fill(0) }; jobs.forEach { it.fileKey?.fill(0) } }
    }

    @Transaction open suspend fun markFilePublished(expected: DocumentJobRow, c: StorageContext) {
        publicationSnapshot(expected.id,"IMPORT_CLOSED",expected,c).use { snapshot ->
            requireOperations(c)
            if (changeImportPhase(snapshot.job.id,"IMPORT_CLOSED","FILE_PUBLISHED",snapshot.job.plaintextSize) != 1) throw ProductStorageFailure()
            requireOperations(c,AccessPoint.COMMIT)
        }
    }

    /** Parent insert and key transfer/job removal are a single Room write transaction. */
    @Transaction open suspend fun commitPublishedDocument(expected: DocumentJobRow, importedAt: Long, c: StorageContext) {
        publicationSnapshot(expected.id,"FILE_PUBLISHED",expected,c).use { snapshot ->
            val j = snapshot.job
            if (importedAt < 0) throw ProductStorageFailure()
            requireOperations(c,AccessPoint.COMMIT)
            val row = DocumentRow(j.documentId,j.actionId,j.displayName!!,j.format!!,j.plaintextSize!!,importedAt,j.blobName,j.fileKey!!.copyOf())
            try {
                insertPublishedDocument(row) // Existing documents_import_parent trigger remains active.
                requireOperations(c)
                if (deletePublishedJob(j.id) != 1) throw ProductStorageFailure()
                requireOperations(c,AccessPoint.COMMIT)
            } finally { row.fileKey.fill(0) }
        }
    }

    /** Key-free, fully checked namespace snapshot. No file I/O inside this transaction. */
    @Transaction open suspend fun importInventory(c: StorageContext): DocumentImportInventory {
        requireOperations(c)
        val docs = inventoryDocuments()
        var jobs = emptyList<DocumentJobRow>()
        try {
            jobs = inventoryJobs()
            DocumentStorageRules.validateBindings(docs,jobs)
            (docs.map { it.actionId } + jobs.map { it.actionId }).toSet().forEach { a ->
                DocumentStorageRules.capacity(docs.filter { it.actionId == a },jobs.filter { it.actionId == a })
            }
            // Interrupted work must be reconciled first; this slice never resumes it.
            if (jobs.any { it.operation != "IMPORT" || it.phase != "IMPORT_CLOSED" }) throw ProductStorageFailure()
            requireOperations(c)
            return DocumentImportInventory(docs.map { it.blobName }.toSet(),jobs.map { it.stagingName!! }.toSet())
        } finally { docs.forEach { it.fileKey.fill(0) }; jobs.forEach { it.fileKey?.fill(0) } }
    }

    @Transaction open suspend fun advanceImport(expected: DocumentJobRow, closedSize: Long?, c: StorageContext) {
        val key = expected.fileKey?.copyOf()
        val frozen = DocumentJobRow(expected.id,expected.actionId,expected.documentId,expected.operation,expected.phase,
            expected.stagingName,expected.blobName,expected.displayName,expected.format,expected.plaintextSize,key)
        try { advanceFrozenImport(frozen,closedSize,c) } finally { key?.fill(0) }
    }
    private suspend fun advanceFrozenImport(expected: DocumentJobRow, closedSize: Long?, c: StorageContext) {
        requireOperations(c,AccessPoint.OPERATION)
        if (c.scope != AccessScope.READY || !DocumentStorageRules.valid(expected) ||
            expected.operation != "IMPORT" || expected.phase !in setOf("IMPORT_RESERVED","IMPORT_WRITING") ||
            (expected.phase == "IMPORT_RESERVED" && closedSize != null) ||
            (expected.phase == "IMPORT_WRITING" && (closedSize == null || !DocumentRules.validSize(closedSize)))) throw ProductStorageFailure()
        val status = actionStatus(expected.actionId)
        if (status !in setOf("PREPARATION","ACTIVE","COMPLETED")) throw ProductStorageFailure()
        val docs = inventoryDocuments()
        var jobs = emptyList<DocumentJobRow>()
        try {
            jobs = inventoryJobs()
            DocumentStorageRules.validateBindings(docs,jobs)
            (docs.map { it.actionId } + jobs.map { it.actionId }).toSet().forEach { a ->
                DocumentStorageRules.capacity(docs.filter { it.actionId == a },jobs.filter { it.actionId == a })
            }
            val current = jobs.singleOrNull { it.id == expected.id } ?: throw ProductStorageFailure()
            if (!sameDocumentImport(current,expected)) throw ProductStorageFailure()
            requireOperations(c)
            val next = if (expected.phase == "IMPORT_RESERVED") "IMPORT_WRITING" else "IMPORT_CLOSED"
            if (changeImportPhase(expected.id,expected.phase,next,closedSize) != 1) throw ProductStorageFailure()
            requireOperations(c,AccessPoint.COMMIT)
        } finally { docs.forEach { it.fileKey.fill(0) }; jobs.forEach { it.fileKey?.fill(0) } }
    }

    /** Internal reservation only; cannot create a READY document. Writer transaction serializes capacity checks. */
    @Transaction open suspend fun reserveImport(job: DocumentJobRow, c: StorageContext): OperationsResult<Unit> {
        val key = job.fileKey?.copyOf()
        val frozen = DocumentJobRow(job.id,job.actionId,job.documentId,job.operation,job.phase,job.stagingName,
            job.blobName,job.displayName,job.format,job.plaintextSize,key)
        try { return reserveFrozenImport(frozen,c) } finally { key?.fill(0) }
    }
    private suspend fun reserveFrozenImport(job: DocumentJobRow, c: StorageContext): OperationsResult<Unit> {
        requireOperations(c, AccessPoint.OPERATION)
        if (c.scope != AccessScope.READY) return OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED)
        if (!DocumentStorageRules.valid(job) || job.operation != "IMPORT" || job.phase != "IMPORT_RESERVED") return OperationsResult.Rejected(OperationsFailure.INVALID)
        val status = actionStatus(job.actionId) ?: return OperationsResult.Rejected(OperationsFailure.NOT_FOUND)
        if (status !in setOf("PREPARATION","ACTIVE","COMPLETED")) return OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED)
        val docs = inventoryDocuments()
        var jobs = emptyList<DocumentJobRow>()
        try {
            jobs = inventoryJobs()
            DocumentStorageRules.validateBindings(docs,jobs)
            requireOperations(c)
            // Validate all bindings, then calculate this action's capacity. Never hide corrupt foreign bindings.
            (docs.map { it.actionId } + jobs.map { it.actionId }).toSet().forEach { a ->
                DocumentStorageRules.capacity(docs.filter { it.actionId == a }, jobs.filter { it.actionId == a })
            }
            val boundNames = docs.map { it.blobName } + jobs.flatMap { listOfNotNull(it.blobName,it.stagingName) }
            if (docs.any { it.id == job.documentId } || jobs.any { it.documentId == job.documentId || it.id == job.id } ||
                job.blobName in boundNames || job.stagingName in boundNames) return OperationsResult.Rejected(OperationsFailure.DUPLICATE)
            val capacity = DocumentStorageRules.capacity(docs.filter { it.actionId == job.actionId }, jobs.filter { it.actionId == job.actionId })
            if (!capacity.admitsImport()) return OperationsResult.Rejected(OperationsFailure.IN_USE)
            val copy = DocumentJobRow(job.id,job.actionId,job.documentId,job.operation,job.phase,job.stagingName,job.blobName,
                job.displayName,job.format,job.plaintextSize,job.fileKey!!.copyOf())
            try { requireOperations(c); insertJob(copy); requireOperations(c, AccessPoint.COMMIT) }
            finally { copy.fileKey?.fill(0) }
            return OperationsResult.Value(Unit)
        } finally { docs.forEach { it.fileKey.fill(0) }; jobs.forEach { it.fileKey?.fill(0) } }
    }
}

/** Shared exact DDL for additive migration and focused host-SQLite checks. */
internal val DOCUMENT_SCHEMA_SQL = listOf(
    "CREATE TABLE documents (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,displayName TEXT NOT NULL,format TEXT NOT NULL,plaintextSize INTEGER NOT NULL,importedAt INTEGER NOT NULL,blobName TEXT NOT NULL,fileKey BLOB NOT NULL,FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    "CREATE UNIQUE INDEX index_documents_actionId_id ON documents(actionId,id)",
    "CREATE UNIQUE INDEX index_documents_blobName ON documents(blobName)",
    "CREATE TABLE document_jobs (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,documentId TEXT NOT NULL,operation TEXT NOT NULL,phase TEXT NOT NULL,stagingName TEXT,blobName TEXT NOT NULL,displayName TEXT,format TEXT,plaintextSize INTEGER,fileKey BLOB,FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    "CREATE INDEX index_document_jobs_actionId ON document_jobs(actionId)",
    "CREATE UNIQUE INDEX index_document_jobs_documentId ON document_jobs(documentId)",
    "CREATE UNIQUE INDEX index_document_jobs_blobName ON document_jobs(blobName)",
    "CREATE UNIQUE INDEX index_document_jobs_stagingName ON document_jobs(stagingName)"
)
internal val ACTION_MIGRATION_10_11 = object : Migration(10,11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        DOCUMENT_SCHEMA_SQL.forEach { sql -> connection.prepare(sql).use { it.step() } }
        installDocumentConstraints(connection)
    }
}

internal val DOCUMENT_CONSTRAINT_SQL = listOf(
    "CREATE TRIGGER documents_valid_insert BEFORE INSERT ON documents WHEN typeof(NEW.fileKey)!='blob' OR length(NEW.fileKey)!=32 OR NEW.format NOT IN ('PDF','JPEG','PNG','TEXT') OR typeof(NEW.plaintextSize)!='integer' OR NEW.plaintextSize NOT BETWEEN 0 AND 26214400 OR typeof(NEW.importedAt)!='integer' OR NEW.importedAt<0 OR length(NEW.displayName) NOT BETWEEN 1 AND 160 OR NEW.displayName!=trim(NEW.displayName) BEGIN SELECT RAISE(ABORT,'DOCUMENT_INVALID'); END",
    "CREATE TRIGGER documents_valid_update BEFORE UPDATE ON documents WHEN typeof(NEW.fileKey)!='blob' OR length(NEW.fileKey)!=32 OR NEW.format NOT IN ('PDF','JPEG','PNG','TEXT') OR typeof(NEW.plaintextSize)!='integer' OR NEW.plaintextSize NOT BETWEEN 0 AND 26214400 OR typeof(NEW.importedAt)!='integer' OR NEW.importedAt<0 OR length(NEW.displayName) NOT BETWEEN 1 AND 160 OR NEW.displayName!=trim(NEW.displayName) BEGIN SELECT RAISE(ABORT,'DOCUMENT_INVALID'); END",
    "CREATE TRIGGER documents_import_parent BEFORE INSERT ON documents WHEN NOT EXISTS (SELECT 1 FROM document_jobs j WHERE j.documentId=NEW.id AND j.actionId=NEW.actionId AND j.operation='IMPORT' AND j.phase='FILE_PUBLISHED' AND j.blobName=NEW.blobName AND j.displayName=NEW.displayName AND j.format=NEW.format AND j.plaintextSize=NEW.plaintextSize AND j.fileKey=NEW.fileKey) BEGIN SELECT RAISE(ABORT,'DOCUMENT_IMPORT_REQUIRED'); END"
)
internal const val DOCUMENT_JOB_UPDATE_INVALID_SQL = "NEW.id!=OLD.id OR NEW.actionId!=OLD.actionId OR NEW.documentId!=OLD.documentId OR NEW.operation!=OLD.operation OR NEW.stagingName IS NOT OLD.stagingName OR NEW.blobName!=OLD.blobName OR NEW.displayName IS NOT OLD.displayName OR NEW.format IS NOT OLD.format OR NEW.fileKey IS NOT OLD.fileKey OR (OLD.phase='IMPORT_RESERVED' AND (NEW.phase NOT IN ('IMPORT_RESERVED','IMPORT_WRITING') OR NEW.plaintextSize IS NOT OLD.plaintextSize)) OR (OLD.phase='IMPORT_WRITING' AND NEW.phase NOT IN ('IMPORT_WRITING','IMPORT_CLOSED')) OR (OLD.phase='IMPORT_CLOSED' AND (NEW.phase NOT IN ('IMPORT_CLOSED','FILE_PUBLISHED') OR NEW.plaintextSize IS NOT OLD.plaintextSize)) OR (OLD.phase='FILE_PUBLISHED' AND (NEW.phase!='FILE_PUBLISHED' OR NEW.plaintextSize IS NOT OLD.plaintextSize)) OR (OLD.phase='DELETE_RESERVED' AND (NEW.phase NOT IN ('DELETE_RESERVED','DELETE_FILE_PENDING') OR NEW.plaintextSize IS NOT OLD.plaintextSize)) OR (OLD.phase='DELETE_FILE_PENDING' AND (NEW.phase!='DELETE_FILE_PENDING' OR NEW.plaintextSize IS NOT OLD.plaintextSize))"
internal const val DOCUMENT_RESERVED_CAPACITY_SQL = "SELECT COUNT(*) AS count,COALESCE(SUM(bytes),0) AS bytes FROM (SELECT plaintextSize AS bytes FROM documents WHERE actionId=NEW.actionId UNION ALL SELECT CASE WHEN operation='IMPORT' AND phase IN ('IMPORT_RESERVED','IMPORT_WRITING') THEN 26214400 ELSE plaintextSize END AS bytes FROM document_jobs WHERE actionId=NEW.actionId AND (operation='IMPORT' OR phase='DELETE_FILE_PENDING'))"
internal const val DOCUMENT_JOB_BINDING_SQL = "EXISTS (SELECT 1 FROM documents d WHERE (d.id=NEW.documentId AND d.actionId!=NEW.actionId) OR (d.blobName IN (NEW.blobName,NEW.stagingName) AND (NEW.operation!='DELETE' OR d.id!=NEW.documentId))) OR EXISTS (SELECT 1 FROM document_jobs j WHERE j.id!=NEW.id AND (j.blobName IN (NEW.blobName,NEW.stagingName) OR j.stagingName IN (NEW.blobName,NEW.stagingName)))"
internal const val DOCUMENT_JOB_INVALID_SQL = "NEW.operation NOT IN ('IMPORT','DELETE') OR (NEW.operation='IMPORT' AND (NEW.phase NOT IN ('IMPORT_RESERVED','IMPORT_WRITING','IMPORT_CLOSED','FILE_PUBLISHED') OR NEW.stagingName IS NULL OR NEW.stagingName=NEW.blobName OR NEW.displayName IS NULL OR length(NEW.displayName) NOT BETWEEN 1 AND 160 OR NEW.displayName!=trim(NEW.displayName) OR NEW.format IS NULL OR NEW.format NOT IN ('PDF','JPEG','PNG','TEXT') OR NEW.fileKey IS NULL OR typeof(NEW.fileKey)!='blob' OR length(NEW.fileKey)!=32 OR (NEW.phase IN ('IMPORT_RESERVED','IMPORT_WRITING') AND NEW.plaintextSize IS NOT NULL) OR (NEW.phase IN ('IMPORT_CLOSED','FILE_PUBLISHED') AND (NEW.plaintextSize IS NULL OR typeof(NEW.plaintextSize)!='integer' OR NEW.plaintextSize NOT BETWEEN 0 AND 26214400)) OR EXISTS (SELECT 1 FROM documents d WHERE d.id=NEW.documentId))) OR (NEW.operation='DELETE' AND (NEW.phase NOT IN ('DELETE_RESERVED','DELETE_FILE_PENDING') OR NEW.stagingName IS NOT NULL OR NEW.displayName IS NOT NULL OR NEW.format IS NOT NULL OR NEW.fileKey IS NOT NULL OR NEW.plaintextSize IS NULL OR typeof(NEW.plaintextSize)!='integer' OR NEW.plaintextSize NOT BETWEEN 0 AND 26214400 OR (NEW.phase='DELETE_RESERVED' AND NOT EXISTS (SELECT 1 FROM documents d WHERE d.id=NEW.documentId AND d.actionId=NEW.actionId AND d.blobName=NEW.blobName AND d.plaintextSize=NEW.plaintextSize)) OR (NEW.phase='DELETE_FILE_PENDING' AND EXISTS (SELECT 1 FROM documents d WHERE d.id=NEW.documentId))))"
internal suspend fun installDocumentConstraints(connection: SQLiteConnection) {
    DOCUMENT_CONSTRAINT_SQL.forEach { sql -> connection.prepare(sql).use { it.step() } }
    connection.prepare("CREATE TRIGGER document_jobs_transition BEFORE UPDATE ON document_jobs WHEN $DOCUMENT_JOB_UPDATE_INVALID_SQL BEGIN SELECT RAISE(ABORT,'DOCUMENT_PHASE'); END").use { it.step() }
    connection.prepare("CREATE TRIGGER documents_identity BEFORE UPDATE ON documents WHEN NEW.id!=OLD.id OR NEW.actionId!=OLD.actionId OR NEW.format!=OLD.format OR NEW.plaintextSize!=OLD.plaintextSize OR NEW.importedAt!=OLD.importedAt OR NEW.blobName!=OLD.blobName OR NEW.fileKey IS NOT OLD.fileKey BEGIN SELECT RAISE(ABORT,'DOCUMENT_IDENTITY'); END").use { it.step() }
    connection.prepare("CREATE TRIGGER document_jobs_reservation BEFORE INSERT ON document_jobs WHEN NEW.operation='IMPORT' AND (NEW.phase!='IMPORT_RESERVED' OR (SELECT count>=100 OR bytes>235929600 FROM ($DOCUMENT_RESERVED_CAPACITY_SQL))) BEGIN SELECT RAISE(ABORT,'DOCUMENT_CAPACITY'); END").use { it.step() }
    for (operation in listOf("INSERT","UPDATE")) {
        connection.prepare("CREATE TRIGGER document_jobs_valid_${operation.lowercase()} BEFORE $operation ON document_jobs WHEN $DOCUMENT_JOB_INVALID_SQL BEGIN SELECT RAISE(ABORT,'DOCUMENT_JOB_INVALID'); END").use { it.step() }
        connection.prepare("CREATE TRIGGER document_jobs_binding_${operation.lowercase()} BEFORE $operation ON document_jobs WHEN $DOCUMENT_JOB_BINDING_SQL BEGIN SELECT RAISE(ABORT,'DOCUMENT_BINDING'); END").use { it.step() }
    }
}
internal val DOCUMENT_DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) { installDocumentConstraints(connection) }
}
