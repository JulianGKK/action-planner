package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.*

@Entity(tableName = "materials", foreignKeys = [ForeignKey(entity = ActionRow::class,
    parentColumns = ["id"], childColumns = ["actionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["actionId", "id"], unique = true), Index(value = ["actionId", "responsiblePersonId"]),
        Index(value = ["actionId", "linkedTaskId"])])
internal data class MaterialRow(@PrimaryKey val id: String, val actionId: String, val name: String,
    val quantityLabel: String?, val placeLabel: String?, val responsiblePersonId: String?, val linkedTaskId: String?,
    val sourceTemplateKey: String?, val createdAt: Long) {
    fun model(): MaterialItem {
        val m = MaterialItem(id, actionId, name, quantityLabel, placeLabel, responsiblePersonId, linkedTaskId, sourceTemplateKey, createdAt)
        if (!MaterialRules.valid(m.input()) || MaterialRules.normalized(m.input()) != m.input()) throw ProductStorageFailure()
        return m
    }
}
internal fun MaterialItem.row() = MaterialRow(id, actionId, name, quantityLabel, placeLabel, responsiblePersonId,
    linkedTaskId, sourceTemplateKey, createdAt)
@Dao internal interface MaterialDao {
    @Query("SELECT COUNT(*) FROM materials WHERE actionId=:actionId") suspend fun count(actionId: String): Int
    @Query("SELECT * FROM materials WHERE actionId=:actionId ORDER BY createdAt,id") suspend fun list(actionId: String): List<MaterialRow>
    @Query("SELECT * FROM materials WHERE actionId=:actionId AND id=:id") suspend fun get(actionId: String, id: String): MaterialRow?
    @Insert suspend fun insert(row: MaterialRow)
    @Update suspend fun update(row: MaterialRow): Int
    @Query("DELETE FROM materials WHERE actionId=:actionId AND id=:id") suspend fun delete(actionId: String, id: String): Int
}
internal val ACTION_MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        listOf(
            "CREATE TABLE materials (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,name TEXT NOT NULL,quantityLabel TEXT,placeLabel TEXT,responsiblePersonId TEXT,linkedTaskId TEXT,sourceTemplateKey TEXT,createdAt INTEGER NOT NULL,FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE UNIQUE INDEX index_materials_actionId_id ON materials(actionId,id)",
            "CREATE INDEX index_materials_actionId_responsiblePersonId ON materials(actionId,responsiblePersonId)",
            "CREATE INDEX index_materials_actionId_linkedTaskId ON materials(actionId,linkedTaskId)"
        ).forEach { sql -> connection.prepare(sql).use { it.step() } }
        installMaterialConstraints(connection)
    }
}
/** Same-action validation without a composite SET NULL that would null actionId. */
internal suspend fun installMaterialConstraints(connection: SQLiteConnection) {
    for (operation in listOf("INSERT", "UPDATE")) {
        connection.prepare("CREATE TRIGGER materials_references_${operation.lowercase()} BEFORE $operation ON materials WHEN (NEW.responsiblePersonId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM persons WHERE actionId=NEW.actionId AND id=NEW.responsiblePersonId)) OR (NEW.linkedTaskId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tasks WHERE actionId=NEW.actionId AND id=NEW.linkedTaskId)) BEGIN SELECT RAISE(ABORT,'MATERIAL_REFERENCE_INVALID'); END").use { it.step() }
    }
    connection.prepare("CREATE TRIGGER materials_detach_person AFTER DELETE ON persons BEGIN UPDATE materials SET responsiblePersonId=NULL WHERE actionId=OLD.actionId AND responsiblePersonId=OLD.id; END").use { it.step() }
    connection.prepare("CREATE TRIGGER materials_detach_task AFTER DELETE ON tasks BEGIN UPDATE materials SET linkedTaskId=NULL WHERE actionId=OLD.actionId AND linkedTaskId=OLD.id; END").use { it.step() }
}
internal val MATERIAL_DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) { installMaterialConstraints(connection) }
}
