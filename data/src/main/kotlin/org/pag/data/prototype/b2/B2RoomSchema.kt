package org.pag.data.prototype.b2

import androidx.room3.*

@Entity(tableName = "synthetic_record")
internal data class RecordV1(@PrimaryKey val id: String, val value: String)
@Entity(tableName = "synthetic_record")
internal data class RecordV2(@PrimaryKey val id: String, val value: String,
    @ColumnInfo(defaultValue = "0") val revision: Int = 0)
@Entity(tableName = "synthetic_child", foreignKeys = [ForeignKey(entity = RecordV1::class,
    parentColumns = ["id"], childColumns = ["parentId"])], indices = [Index("parentId")])
internal data class ChildV1(@PrimaryKey val id: String, val parentId: String)
@Entity(tableName = "synthetic_child", foreignKeys = [ForeignKey(entity = RecordV2::class,
    parentColumns = ["id"], childColumns = ["parentId"])], indices = [Index("parentId")])
internal data class ChildV2(@PrimaryKey val id: String, val parentId: String)

@Dao internal interface DaoV1 {
    @Query("SELECT * FROM synthetic_record ORDER BY id") suspend fun read(): List<RecordV1>
    @Insert suspend fun insert(row: RecordV1)
    @Insert suspend fun child(row: ChildV1)
}
@Dao internal interface DaoV2 {
    @Query("SELECT * FROM synthetic_record ORDER BY id") suspend fun read(): List<RecordV2>
    @Insert suspend fun insert(row: RecordV2)
    @Insert suspend fun child(row: ChildV2)
}
@Database(entities = [RecordV1::class, ChildV1::class], version = 1, exportSchema = true)
internal abstract class RoomV1 : RoomDatabase() { abstract fun records(): DaoV1 }
@Database(entities = [RecordV2::class, ChildV2::class], version = 2, exportSchema = true)
internal abstract class RoomV2 : RoomDatabase() { abstract fun records(): DaoV2 }
