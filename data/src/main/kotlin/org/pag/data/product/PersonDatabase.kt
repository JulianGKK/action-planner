package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.Person
import org.pag.domain.PersonRules

@Entity(tableName = "persons", foreignKeys = [ForeignKey(entity = ActionRow::class,
    parentColumns = ["id"], childColumns = ["actionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["actionId"]), Index(value = ["actionId", "id"], unique = true), Index(value = ["actionId", "pseudonymNormalized"], unique = true)])
internal data class PersonRow(@PrimaryKey val id: String, val actionId: String,
    val pseudonym: String, val pseudonymNormalized: String, val createdAt: Long) {
    fun model(): Person {
        if (id.isBlank() || actionId.isBlank() || !PersonRules.valid(pseudonym) ||
            pseudonymNormalized != PersonRules.normalized(pseudonym)) throw ProductStorageFailure()
        return Person(id, actionId, pseudonym, createdAt)
    }
}
@Dao internal interface PersonDao {
    @Query("SELECT * FROM persons WHERE actionId = :actionId ORDER BY createdAt, id")
    suspend fun list(actionId: String): List<PersonRow>
    @Query("SELECT * FROM persons WHERE actionId = :actionId AND id = :id")
    suspend fun get(actionId: String, id: String): PersonRow?
    @Query("SELECT * FROM persons WHERE actionId = :actionId AND pseudonymNormalized = :normalized")
    suspend fun samePseudonym(actionId: String, normalized: String): PersonRow?
    @Insert suspend fun insert(row: PersonRow)
    @Query("UPDATE persons SET pseudonym = :pseudonym, pseudonymNormalized = :normalized WHERE actionId = :actionId AND id = :id")
    suspend fun rename(actionId: String, id: String, pseudonym: String, normalized: String): Int
}
internal val ACTION_MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("CREATE TABLE persons (id TEXT NOT NULL, actionId TEXT NOT NULL, pseudonym TEXT NOT NULL, pseudonymNormalized TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)").use { it.step() }
        connection.prepare("CREATE INDEX index_persons_actionId ON persons(actionId)").use { it.step() }
        connection.prepare("CREATE UNIQUE INDEX index_persons_actionId_pseudonymNormalized ON persons(actionId, pseudonymNormalized)").use { it.step() }
    }
}
