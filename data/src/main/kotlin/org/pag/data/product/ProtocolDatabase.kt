package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.*

@Entity(tableName = "protocol_entries", foreignKeys = [
    ForeignKey(entity = ActionRow::class, parentColumns = ["id"], childColumns = ["actionId"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["actionId", "id"], unique = true)])
internal data class ProtocolEntryRow(@PrimaryKey val id: String, val actionId: String, val createdAfterCompletion: Boolean) {
    fun model() = ProtocolEntry(id, actionId, createdAfterCompletion)
}
@Entity(tableName = "protocol_revisions", foreignKeys = [
    ForeignKey(entity = ProtocolEntryRow::class, parentColumns = ["actionId", "id"], childColumns = ["actionId", "entryId"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["actionId", "id"], unique = true), Index(value = ["entryId", "revisionNumber"], unique = true), Index(value = ["actionId", "entryId"])])
internal data class ProtocolRevisionRow(
    @PrimaryKey val id: String, val actionId: String, val entryId: String, val revisionNumber: Long,
    val revisionType: String, val classification: String, val templateKey: String?, val title: String,
    val description: String?, val occurredAt: Long, val occurredAtApproximate: Boolean,
    val recordedAt: Long, val recordedAfterCompletion: Boolean, val mistaken: Boolean,
    val sourceType: String?, val placeLabel: String?, val announcementText: String?,
    val announcementKind: String?, val statedLegalBasis: String?
) {
    fun model(affected: Set<String>, reportedBy: String?, identifiers: List<ProtocolOfficialIdentifier>): ProtocolRevision {
        val input = ProtocolInput(ProtocolClassification.valueOf(classification), title, occurredAt,
            occurredAtApproximate, templateKey, description, sourceType?.let(ProtocolSourceType::valueOf),
            reportedBy, affected, placeLabel, announcementText, announcementKind?.let(ProtocolAnnouncementKind::valueOf),
            statedLegalBasis, identifiers)
        if (!ProtocolRules.valid(input) || ProtocolRules.normalized(input) != input || revisionNumber < 1 ||
            ((revisionNumber == 1L) != (revisionType == ProtocolRevisionType.ORIGINAL.name))) throw ProductStorageFailure()
        return ProtocolRevision(id, actionId, entryId, revisionNumber, ProtocolRevisionType.valueOf(revisionType),
            input, recordedAt, recordedAfterCompletion, mistaken)
    }
}
@Entity(tableName = "protocol_affected_persons", primaryKeys = ["actionId", "revisionId", "personId"], foreignKeys = [
    ForeignKey(entity = ProtocolRevisionRow::class, parentColumns = ["actionId", "id"], childColumns = ["actionId", "revisionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = PersonRow::class, parentColumns = ["actionId", "id"], childColumns = ["actionId", "personId"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["actionId", "personId"])])
internal data class ProtocolAffectedRow(val actionId: String, val revisionId: String, val personId: String)
@Entity(tableName = "protocol_official_identifiers", primaryKeys = ["actionId", "revisionId", "position"], foreignKeys = [
    ForeignKey(entity = ProtocolRevisionRow::class, parentColumns = ["actionId", "id"], childColumns = ["actionId", "revisionId"], onDelete = ForeignKey.CASCADE)
])
internal data class ProtocolIdentifierRow(val actionId: String, val revisionId: String, val position: Int, val type: String, val value: String)
@Entity(tableName = "protocol_reported_by_persons", primaryKeys = ["actionId", "revisionId"], foreignKeys = [
    ForeignKey(entity = ProtocolRevisionRow::class, parentColumns = ["actionId", "id"], childColumns = ["actionId", "revisionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = PersonRow::class, parentColumns = ["actionId", "id"], childColumns = ["actionId", "personId"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["actionId", "personId"])])
internal data class ProtocolReportedRow(val actionId: String, val revisionId: String, val personId: String)

internal data class ProtocolSummaryRow(val keyEventCount: Long, val lastEntryRecordedAt: Long?)
internal data class ProtocolTimelineRow(
    val entryId: String, val revisionId: String, val revisionNumber: Long, val revisionType: String,
    val classification: String, val templateKey: String?, val title: String, val occurredAt: Long,
    val occurredAtApproximate: Boolean, val recordedAt: Long, val recordedAfterCompletion: Boolean,
    val createdAfterCompletion: Boolean, val mistaken: Boolean, val originalRecordedAt: Long
) {
    fun model(): ProtocolTimelineItem {
        if (revisionNumber < 1 || title != title.trim() || title.isEmpty() || Character.codePointCount(title, 0, title.length) > 120 ||
            (templateKey != null && ProtocolRules.templates.none { it.key == templateKey })) throw ProductStorageFailure()
        return ProtocolTimelineItem(entryId, revisionId, revisionNumber, ProtocolRevisionType.valueOf(revisionType),
            ProtocolClassification.valueOf(classification), templateKey, title, occurredAt, occurredAtApproximate,
            recordedAt, recordedAfterCompletion, createdAfterCompletion, mistaken, originalRecordedAt)
    }
}
/** Explicit selected columns: summary/timeline never read long content or child tables. */
@Dao internal interface ProtocolDao {
    @Query("SELECT COUNT(*) FROM protocol_entries e WHERE e.actionId=:a AND NOT EXISTS (SELECT 1 FROM protocol_revisions r WHERE r.actionId=e.actionId AND r.entryId=e.id AND r.revisionNumber=1)")
    suspend fun missingOriginals(a: String): Long
    @Query("SELECT COALESCE(SUM(CASE WHEN r.classification='KEY_EVENT' THEN 1 ELSE 0 END),0) AS keyEventCount, MAX(o.recordedAt) AS lastEntryRecordedAt FROM protocol_entries e JOIN protocol_revisions o ON o.actionId=e.actionId AND o.entryId=e.id AND o.revisionNumber=1 JOIN protocol_revisions r ON r.actionId=e.actionId AND r.entryId=e.id AND r.revisionNumber=(SELECT MAX(n.revisionNumber) FROM protocol_revisions n WHERE n.actionId=e.actionId AND n.entryId=e.id) WHERE e.actionId=:a")
    suspend fun summary(a: String): ProtocolSummaryRow
    @Query("SELECT e.id AS entryId,r.id AS revisionId,r.revisionNumber,r.revisionType,r.classification,r.templateKey,r.title,r.occurredAt,r.occurredAtApproximate,r.recordedAt,r.recordedAfterCompletion,e.createdAfterCompletion,r.mistaken,o.recordedAt AS originalRecordedAt FROM protocol_entries e JOIN protocol_revisions o ON o.actionId=e.actionId AND o.entryId=e.id AND o.revisionNumber=1 JOIN protocol_revisions r ON r.actionId=e.actionId AND r.entryId=e.id AND r.revisionNumber=(SELECT MAX(n.revisionNumber) FROM protocol_revisions n WHERE n.actionId=e.actionId AND n.entryId=e.id) WHERE e.actionId=:a ORDER BY r.occurredAt,o.recordedAt,e.id")
    suspend fun timeline(a: String): List<ProtocolTimelineRow>
    @Query("SELECT * FROM protocol_entries WHERE actionId=:a AND id=:id") suspend fun entry(a: String, id: String): ProtocolEntryRow?
    @Query("SELECT * FROM protocol_revisions WHERE actionId=:a AND entryId=:entryId ORDER BY revisionNumber") suspend fun revisions(a: String, entryId: String): List<ProtocolRevisionRow>
    @Query("SELECT * FROM protocol_revisions WHERE actionId=:a AND entryId=:entryId ORDER BY revisionNumber DESC LIMIT 1") suspend fun current(a: String, entryId: String): ProtocolRevisionRow?
    @Query("SELECT p.* FROM protocol_affected_persons p JOIN protocol_revisions r ON r.actionId=p.actionId AND r.id=p.revisionId WHERE p.actionId=:a AND r.entryId=:entryId") suspend fun affected(a: String, entryId: String): List<ProtocolAffectedRow>
    @Query("SELECT p.* FROM protocol_reported_by_persons p JOIN protocol_revisions r ON r.actionId=p.actionId AND r.id=p.revisionId WHERE p.actionId=:a AND r.entryId=:entryId") suspend fun reported(a: String, entryId: String): List<ProtocolReportedRow>
    @Query("SELECT p.* FROM protocol_official_identifiers p JOIN protocol_revisions r ON r.actionId=p.actionId AND r.id=p.revisionId WHERE p.actionId=:a AND r.entryId=:entryId ORDER BY r.revisionNumber,p.position") suspend fun identifiers(a: String, entryId: String): List<ProtocolIdentifierRow>
    @Query("SELECT id,pseudonym FROM persons WHERE actionId=:a AND id IN (:ids) ORDER BY createdAt,id") suspend fun persons(a: String, ids: Set<String>): List<ProtocolPersonReference>
    @Insert suspend fun insert(row: ProtocolEntryRow)
    @Insert suspend fun insert(row: ProtocolRevisionRow)
    @Insert suspend fun insert(row: ProtocolAffectedRow)
    @Insert suspend fun insert(row: ProtocolIdentifierRow)
    @Insert suspend fun insert(row: ProtocolReportedRow)
}

internal val ACTION_MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        PROTOCOL_SCHEMA.forEach { sql -> connection.prepare(sql).use { it.step() } }
        installProtocolConstraints(connection)
    }
}
private val PROTOCOL_SCHEMA = listOf(
    "CREATE TABLE protocol_entries (id TEXT NOT NULL PRIMARY KEY, actionId TEXT NOT NULL, createdAfterCompletion INTEGER NOT NULL, FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE UNIQUE INDEX index_protocol_entries_actionId_id ON protocol_entries(actionId,id)",
    "CREATE TABLE protocol_revisions (id TEXT NOT NULL PRIMARY KEY, actionId TEXT NOT NULL, entryId TEXT NOT NULL, revisionNumber INTEGER NOT NULL, revisionType TEXT NOT NULL, classification TEXT NOT NULL, templateKey TEXT, title TEXT NOT NULL, description TEXT, occurredAt INTEGER NOT NULL, occurredAtApproximate INTEGER NOT NULL, recordedAt INTEGER NOT NULL, recordedAfterCompletion INTEGER NOT NULL, mistaken INTEGER NOT NULL, sourceType TEXT, placeLabel TEXT, announcementText TEXT, announcementKind TEXT, statedLegalBasis TEXT, FOREIGN KEY(actionId,entryId) REFERENCES protocol_entries(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE UNIQUE INDEX index_protocol_revisions_actionId_id ON protocol_revisions(actionId,id)",
    "CREATE UNIQUE INDEX index_protocol_revisions_entryId_revisionNumber ON protocol_revisions(entryId,revisionNumber)",
    "CREATE INDEX index_protocol_revisions_actionId_entryId ON protocol_revisions(actionId,entryId)",
    "CREATE TABLE protocol_affected_persons (actionId TEXT NOT NULL, revisionId TEXT NOT NULL, personId TEXT NOT NULL, PRIMARY KEY(actionId,revisionId,personId), FOREIGN KEY(actionId,revisionId) REFERENCES protocol_revisions(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(actionId,personId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX index_protocol_affected_persons_actionId_personId ON protocol_affected_persons(actionId,personId)",
    "CREATE TABLE protocol_official_identifiers (actionId TEXT NOT NULL, revisionId TEXT NOT NULL, position INTEGER NOT NULL, type TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(actionId,revisionId,position), FOREIGN KEY(actionId,revisionId) REFERENCES protocol_revisions(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE TABLE protocol_reported_by_persons (actionId TEXT NOT NULL, revisionId TEXT NOT NULL, personId TEXT NOT NULL, PRIMARY KEY(actionId,revisionId), FOREIGN KEY(actionId,revisionId) REFERENCES protocol_revisions(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(actionId,personId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX index_protocol_reported_by_persons_actionId_personId ON protocol_reported_by_persons(actionId,personId)"
)

/** Same limited invariants for CREATE and upgrade, matching the accepted P5a pattern. */
internal suspend fun installProtocolConstraints(connection: SQLiteConnection) {
    val previous = "(SELECT mistaken FROM protocol_revisions WHERE actionId=NEW.actionId AND entryId=NEW.entryId ORDER BY revisionNumber DESC LIMIT 1)"
    val rules = listOf(
        "CREATE TRIGGER protocol_entry_flags BEFORE INSERT ON protocol_entries WHEN typeof(NEW.createdAfterCompletion)!='integer' OR NEW.createdAfterCompletion NOT IN (0,1) BEGIN SELECT RAISE(ABORT,'PROTOCOL_INVALID'); END",
        """CREATE TRIGGER protocol_revision_rules BEFORE INSERT ON protocol_revisions WHEN
            typeof(NEW.revisionNumber)!='integer' OR NEW.revisionNumber<1 OR
            NEW.revisionNumber != COALESCE((SELECT MAX(revisionNumber) FROM protocol_revisions WHERE actionId=NEW.actionId AND entryId=NEW.entryId),0)+1 OR
            (NEW.revisionNumber=1) != (NEW.revisionType='ORIGINAL') OR
            NEW.revisionType NOT IN ('ORIGINAL','CORRECTION','ADDENDUM','MARKED_MISTAKEN') OR
            NEW.classification NOT IN ('KEY_EVENT','RELEVANT') OR
            (NEW.sourceType IS NOT NULL AND NEW.sourceType NOT IN ('OWN_OBSERVATION','REPORTED','OFFICIAL_ANNOUNCEMENT','UNCLEAR')) OR
            typeof(NEW.occurredAtApproximate)!='integer' OR NEW.occurredAtApproximate NOT IN (0,1) OR
            typeof(NEW.recordedAfterCompletion)!='integer' OR NEW.recordedAfterCompletion NOT IN (0,1) OR
            typeof(NEW.mistaken)!='integer' OR NEW.mistaken NOT IN (0,1) OR
            (NEW.announcementText IS NULL) != (NEW.announcementKind IS NULL) OR
            (NEW.announcementKind IS NOT NULL AND NEW.announcementKind NOT IN ('WORDING','PARAPHRASE')) OR
            (NEW.revisionType='ORIGINAL' AND (NEW.mistaken!=0 OR NEW.recordedAfterCompletion!=(SELECT createdAfterCompletion FROM protocol_entries WHERE actionId=NEW.actionId AND id=NEW.entryId))) OR
            (NEW.revisionType='ADDENDUM' AND NEW.mistaken!=$previous) OR
            (NEW.revisionType='CORRECTION' AND NEW.mistaken>$previous) OR
            (NEW.revisionType='MARKED_MISTAKEN' AND (NEW.mistaken!=1 OR $previous!=0))
            BEGIN SELECT RAISE(ABORT,'PROTOCOL_INVALID'); END""",
        "CREATE TRIGGER protocol_reported_source BEFORE INSERT ON protocol_reported_by_persons WHEN COALESCE((SELECT sourceType FROM protocol_revisions WHERE actionId=NEW.actionId AND id=NEW.revisionId),'')!='REPORTED' BEGIN SELECT RAISE(ABORT,'PROTOCOL_SOURCE_INVALID'); END",
        "CREATE TRIGGER protocol_identifier_rules BEFORE INSERT ON protocol_official_identifiers WHEN typeof(NEW.position)!='integer' OR NEW.position NOT BETWEEN 0 AND 31 OR NEW.type NOT IN ('SERVICE_OR_ID_NUMBER','DISPLAYED_NAME','UNIT_OR_VEHICLE_ID','OTHER_OFFICIAL_ID') BEGIN SELECT RAISE(ABORT,'PROTOCOL_IDENTIFIER_INVALID'); END"
    )
    rules.forEach { sql -> connection.prepare(sql).use { it.step() } }
    for (table in listOf("protocol_entries", "protocol_revisions", "protocol_affected_persons", "protocol_official_identifiers", "protocol_reported_by_persons")) {
        connection.prepare("CREATE TRIGGER ${table}_immutable BEFORE UPDATE ON $table BEGIN SELECT RAISE(ABORT,'PROTOCOL_IMMUTABLE'); END").use { it.step() }
    }
}
internal val PROTOCOL_DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) { installProtocolConstraints(connection) }
}
