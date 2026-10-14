package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.*

@Entity(tableName = "memory_reports", foreignKeys = [ForeignKey(entity = ActionRow::class, parentColumns = ["id"], childColumns = ["actionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["actionId", "id"], unique = true), Index(value = ["actionId", "status"])])
internal data class MemoryReportRow(@PrimaryKey val id: String, val actionId: String, val authorLabel: String,
    val questionnaireVersion: Int, val status: String, val revision: Long, val lastSavedSectionOrdinal: Int,
    val createdAt: Long, val updatedAt: Long, val completedAt: Long?) {
    fun model(): MemoryReportHeader {
        val status = MemoryReportStatus.entries.firstOrNull { it.name == status } ?: throw ProductStorageFailure()
        val h = MemoryReportHeader(id, actionId, authorLabel, questionnaireVersion, status, revision, lastSavedSectionOrdinal, createdAt, updatedAt, completedAt)
        if (!MemoryReportRules.headerValid(h)) throw ProductStorageFailure()
        return h
    }
}
internal fun MemoryReportHeader.row() = MemoryReportRow(id, actionId, authorLabel, questionnaireVersion, status.name, revision, lastSavedSectionOrdinal, createdAt, updatedAt, completedAt)
@Entity(tableName = "memory_answers", primaryKeys = ["actionId", "reportId", "questionKey"], foreignKeys = [ForeignKey(entity = MemoryReportRow::class,
    parentColumns = ["actionId", "id"], childColumns = ["actionId", "reportId"], onDelete = ForeignKey.CASCADE)])
internal data class MemoryAnswerRow(val actionId: String, val reportId: String, val questionKey: String, val valueKind: String, val payload: String)
@Entity(tableName = "memory_addenda", primaryKeys = ["actionId", "reportId", "sequence"], foreignKeys = [ForeignKey(entity = MemoryReportRow::class,
    parentColumns = ["actionId", "id"], childColumns = ["actionId", "reportId"], onDelete = ForeignKey.CASCADE)])
internal data class MemoryAddendumRow(val actionId: String, val reportId: String, val sequence: Int, val content: String, val recordedAt: Long)
internal data class MemorySummaryRow(val draftCount: Int, val completedCount: Int)
@Dao internal interface MemoryReportDao {
    @Query("SELECT COALESCE(SUM(CASE WHEN status='DRAFT' THEN 1 ELSE 0 END),0) AS draftCount, COALESCE(SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END),0) AS completedCount FROM memory_reports WHERE actionId=:a")
    suspend fun summary(a: String): MemorySummaryRow
    @Query("SELECT COUNT(*) FROM memory_reports WHERE actionId=:a AND (status NOT IN ('DRAFT','COMPLETED') OR typeof(questionnaireVersion)!='integer' OR questionnaireVersion!=1 OR typeof(revision)!='integer' OR revision<0 OR typeof(lastSavedSectionOrdinal)!='integer' OR lastSavedSectionOrdinal NOT BETWEEN 0 AND 16 OR typeof(createdAt)!='integer' OR createdAt<0 OR typeof(updatedAt)!='integer' OR updatedAt<0 OR (status='DRAFT' AND completedAt IS NOT NULL) OR (status='COMPLETED' AND (lastSavedSectionOrdinal!=16 OR completedAt IS NULL OR typeof(completedAt)!='integer' OR completedAt<createdAt)))")
    suspend fun invalidHeaders(a: String): Int
    @Query("SELECT id,actionId,authorLabel,questionnaireVersion,status,revision,lastSavedSectionOrdinal,createdAt,updatedAt,completedAt FROM memory_reports WHERE actionId=:a ORDER BY createdAt,id")
    suspend fun list(a: String): List<MemoryReportRow>
    @Query("SELECT id,actionId,authorLabel,questionnaireVersion,status,revision,lastSavedSectionOrdinal,createdAt,updatedAt,completedAt FROM memory_reports WHERE actionId=:a AND id=:id")
    suspend fun get(a: String, id: String): MemoryReportRow?
    @Query("SELECT actionId,reportId,questionKey,valueKind,payload FROM memory_answers WHERE actionId=:a AND reportId=:id ORDER BY questionKey")
    suspend fun answers(a: String, id: String): List<MemoryAnswerRow>
    @Query("SELECT actionId,reportId,sequence,content,recordedAt FROM memory_addenda WHERE actionId=:a AND reportId=:id ORDER BY sequence")
    suspend fun addenda(a: String, id: String): List<MemoryAddendumRow>
    @Query("SELECT COUNT(*) FROM memory_addenda WHERE actionId=:a AND reportId=:id AND (typeof(sequence)!='integer' OR sequence<0 OR sequence>2147483647 OR typeof(recordedAt)!='integer' OR recordedAt<0)")
    suspend fun invalidAddendumTypes(a: String, id: String): Int
    @Insert suspend fun insert(row: MemoryReportRow)
    @Insert suspend fun insertAnswers(rows: List<MemoryAnswerRow>)
    @Insert suspend fun insertAddendum(row: MemoryAddendumRow)
    @Update suspend fun update(row: MemoryReportRow): Int
    @Query("DELETE FROM memory_answers WHERE actionId=:a AND reportId=:id AND questionKey IN (:keys)")
    suspend fun clearSection(a: String, id: String, keys: List<String>)
}
internal val ACTION_MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        listOf(
            "CREATE TABLE memory_reports (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,authorLabel TEXT NOT NULL,questionnaireVersion INTEGER NOT NULL,status TEXT NOT NULL,revision INTEGER NOT NULL,lastSavedSectionOrdinal INTEGER NOT NULL,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,completedAt INTEGER,FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE UNIQUE INDEX index_memory_reports_actionId_id ON memory_reports(actionId,id)",
            "CREATE INDEX index_memory_reports_actionId_status ON memory_reports(actionId,status)",
            "CREATE TABLE memory_answers (actionId TEXT NOT NULL,reportId TEXT NOT NULL,questionKey TEXT NOT NULL,valueKind TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(actionId,reportId,questionKey),FOREIGN KEY(actionId,reportId) REFERENCES memory_reports(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE TABLE memory_addenda (actionId TEXT NOT NULL,reportId TEXT NOT NULL,sequence INTEGER NOT NULL,content TEXT NOT NULL,recordedAt INTEGER NOT NULL,PRIMARY KEY(actionId,reportId,sequence),FOREIGN KEY(actionId,reportId) REFERENCES memory_reports(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        ).forEach { sql -> connection.prepare(sql).use { it.step() } }
    }
}
