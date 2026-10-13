package org.pag.data.product

import android.content.Context
import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.*
import org.pag.data.prototype.b2.*
import org.pag.data.prototype.b4.B4RoomLifecycle
import org.pag.domain.*
import java.io.File

@Entity(tableName = "actions")
internal data class ActionRow(@PrimaryKey val id: String, val title: String, val description: String,
    val startAt: Long, val expectedEndAt: Long, val createdAt: Long, val status: String,
    val sensitivity: String = "SENSITIVE", @ColumnInfo(defaultValue = "NULL") val completedAt: Long? = null) {
    fun model(): Action {
        if (title.isBlank() || description.isBlank() || expectedEndAt < startAt || sensitivity != "SENSITIVE")
            throw ProductStorageFailure()
        val parsed = ActionStatus.valueOf(status)
        if ((parsed == ActionStatus.COMPLETED && completedAt == null) ||
            (parsed != ActionStatus.COMPLETED && parsed != ActionStatus.DELETION_PENDING && completedAt != null))
            throw ProductStorageFailure()
        return Action(id, title, description, startAt, expectedEndAt, createdAt, parsed, completedAt)
    }
}
@Dao internal interface ActionDao {
    @Query("SELECT * FROM actions ORDER BY createdAt DESC, id") suspend fun list(): List<ActionRow>
    @Query("SELECT * FROM actions WHERE id = :id") suspend fun get(id: String): ActionRow?
    @Insert suspend fun insert(row: ActionRow)
    @Update suspend fun update(row: ActionRow): Int
}
@Database(entities = [ActionRow::class, PersonRow::class, LeadershipRow::class, SensitiveRow::class, RoleRow::class, PlannedRoleRow::class, RolePlanRow::class, OperationalRow::class, TaskRow::class, TaskEventRow::class, ProtocolEntryRow::class, ProtocolRevisionRow::class, ProtocolAffectedRow::class, ProtocolIdentifierRow::class, ProtocolReportedRow::class], version = 7, exportSchema = true)
internal abstract class ActionDatabase : RoomDatabase() {
    abstract fun actions(): ActionDao
    abstract fun persons(): PersonDao
    abstract fun leadership(): LeadershipDao
    abstract fun operations(): OperationsDao
    abstract fun tasks(): TaskDao
    abstract fun protocol(): ProtocolDao
}
internal val ACTION_MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.prepare("ALTER TABLE actions ADD COLUMN completedAt INTEGER DEFAULT NULL").use { it.step() }
    }
}

/** Reuses the accepted B2 low-level driver, not the synthetic schema or repositories.
 * The scope adapter preserves ALL checks/publication on the original READY/MAINTENANCE
 * context. It only adapts the historical driver's maintenance-only type assertion.
 */
internal enum class ActionPreflightStep { OPEN, VERSION, CIPHER_INTEGRITY, SQLITE_INTEGRITY, FOREIGN_KEYS }
/** Internal observation seam: no keys/handles escape, no authority or exported hook. */
internal class ActionPreflightHooks(
    val completed: (ActionPreflightStep) -> Unit = {},
    val closed: (Boolean) -> Unit = {}
)
internal class ActionDatabaseAccess(private val app: Context,
    private val preflight: ActionPreflightHooks = ActionPreflightHooks()) {
    fun <T> use(file: File, key: ByteArray, context: StorageContext, creates: Boolean = false,
        work: suspend (ActionDatabase) -> T): T {
        if (!context.check(AccessPoint.OPERATION)) throw StorageAccessRevoked()
        if (creates && context.scope != AccessScope.MAINTENANCE) throw ProductStorageFailure()
        inventory(file, creates, true)
        val driverContext = object : StorageContext {
            override val scope = AccessScope.MAINTENANCE
            override val generation = context.generation
            override fun check(point: AccessPoint) = context.check(point)
            override fun publish(result: () -> Unit) = context.publish(result)
        }
        if (creates) {
            if (!file.createNewFile()) throw ProductStorageFailure()
        } else {
            driverContext.requireB2(AccessPoint.STEP)
            val native = B2Native.open(file, key, driverContext, true)
            try {
                preflight.completed(ActionPreflightStep.OPEN)
                driverContext.requireB2(AccessPoint.STEP)
                if (native.version !in 1..7) throw ProductStorageFailure()
                preflight.completed(ActionPreflightStep.VERSION)
                driverContext.requireB2(AccessPoint.STEP)
                native.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use {
                    if (it.moveToFirst()) throw ProductStorageFailure()
                }
                preflight.completed(ActionPreflightStep.CIPHER_INTEGRITY)
                driverContext.requireB2(AccessPoint.STEP)
                native.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                    if (!it.moveToFirst() || it.getString(0) != "ok" || it.moveToNext()) throw ProductStorageFailure()
                }
                preflight.completed(ActionPreflightStep.SQLITE_INTEGRITY)
                driverContext.requireB2(AccessPoint.STEP)
                native.rawQuery("PRAGMA foreign_key_check", emptyArray<String>()).use {
                    if (it.moveToFirst()) throw ProductStorageFailure()
                }
                preflight.completed(ActionPreflightStep.FOREIGN_KEYS)
            } finally {
                // Cleanup is unconditional, even if the gate or a test observation rejects.
                native.close()
                preflight.closed(!native.isOpen)
            }
        }
        driverContext.requireB2(AccessPoint.STEP)
        val lifecycle = B4RoomLifecycle()
        val parent = SupervisorJob()
        val driver = B2Driver(file, key, lifecycle.guard(driverContext), B2Hooks())
        var room: ActionDatabase? = null
        var result: T
        try {
            room = Room.databaseBuilder(app, ActionDatabase::class.java, file.path).setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO + parent + lifecycle.handler { parent.cancel() })
                .addMigrations(ACTION_MIGRATION_1_2, ACTION_MIGRATION_2_3, ACTION_MIGRATION_3_4, ACTION_MIGRATION_4_5, ACTION_MIGRATION_5_6, ACTION_MIGRATION_6_7)
                .addCallback(TASK_DATABASE_CALLBACK)
                .addCallback(PROTOCOL_DATABASE_CALLBACK)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
            result = runBlocking {
                lifecycle.requireOpen()
                if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
                // Force Room's generated schema validation even for a maintenance-only open.
                room.actions().get("")
                work(room)
            }
        } finally {
            lifecycle.beginClose()
            try { room?.close() } finally {
                try { runBlocking { parent.cancelAndJoin() } } finally { driver.close() }
            }
        }
        lifecycle.requireHealthy()
        if (!context.check(AccessPoint.PUBLICATION)) throw StorageAccessRevoked()
        return result
    }
}
