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
@Database(entities = [ActionRow::class, PersonRow::class, LeadershipRow::class, SensitiveRow::class, RoleRow::class, PlannedRoleRow::class, RolePlanRow::class, OperationalRow::class, TaskRow::class, TaskEventRow::class, ProtocolEntryRow::class, ProtocolRevisionRow::class, ProtocolAffectedRow::class, ProtocolIdentifierRow::class, ProtocolReportedRow::class, MaterialRow::class, ExpenseRow::class, ExpensePaymentRow::class, MemoryReportRow::class, MemoryAnswerRow::class, MemoryAddendumRow::class, DocumentRow::class, DocumentJobRow::class], version = 12, exportSchema = true)
internal abstract class ActionDatabase : RoomDatabase() {
    abstract fun actions(): ActionDao
    abstract fun persons(): PersonDao
    abstract fun leadership(): LeadershipDao
    abstract fun operations(): OperationsDao
    abstract fun tasks(): TaskDao
    abstract fun protocol(): ProtocolDao
    abstract fun materials(): MaterialDao
    abstract fun memoryReports(): MemoryReportDao
    abstract fun expenses(): ExpenseDao
    abstract fun documents(): DocumentDao
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
internal enum class ActionDatabasePhase { INVENTORY, NATIVE_OPEN, NATIVE_PRE_KEY, NATIVE_POST_KEY,
    VERSION, CIPHER_INTEGRITY, SQLITE_INTEGRITY, FOREIGN_KEYS, NATIVE_CLOSE,
    ROOM_BUILDER, ROOM_NATIVE_OPEN, ROOM_PRE_KEY, ROOM_POST_KEY, ROOM_OPEN_SCHEMA,
    QUERY_TRANSACTION, CLEANUP }
/** Internal observations; resource decoration is supplied only by guarded P77 test composition. */
internal class ActionPreflightHooks(
    val completed: (ActionPreflightStep) -> Unit = {},
    val closed: (Boolean) -> Unit = {},
    val timing: ((ActionDatabasePhase,Long) -> Unit)? = null,
    val resources: B2ResourceHooks? = null,
    val roomClosed: () -> Unit = {},
    val driverHooks: B2Hooks = B2Hooks()
) {
    fun measured(phase:ActionDatabasePhase,start:Long) {try {timing?.invoke(phase,System.nanoTime()-start)}catch(_:RuntimeException){}}
    fun native(room:Boolean)=if(timing==null)null else B2OpenTiming {phase,nanos ->
        val mapped=when(phase) {
            B2OpenPhase.OPEN -> if(room)ActionDatabasePhase.ROOM_NATIVE_OPEN else ActionDatabasePhase.NATIVE_OPEN
            B2OpenPhase.PRE_KEY -> if(room)ActionDatabasePhase.ROOM_PRE_KEY else ActionDatabasePhase.NATIVE_PRE_KEY
            B2OpenPhase.POST_KEY -> if(room)ActionDatabasePhase.ROOM_POST_KEY else ActionDatabasePhase.NATIVE_POST_KEY
        }
        timing.invoke(mapped,nanos)
    }
}
internal class ActionDatabaseAccess(private val app: Context,
    private val preflight: ActionPreflightHooks = ActionPreflightHooks()) {
    fun <T> use(file: File, key: ByteArray, context: StorageContext, creates: Boolean = false,
        cleanupConfirmed:(Boolean)->Unit = {}, work: suspend (ActionDatabase) -> T): T {
        if (!context.check(AccessPoint.OPERATION)) throw StorageAccessRevoked()
        if (creates && context.scope != AccessScope.MAINTENANCE) throw ProductStorageFailure()
        var phaseStarted=if(preflight.timing!=null)System.nanoTime() else 0L
        inventory(file, creates, true)
        preflight.measured(ActionDatabasePhase.INVENTORY,phaseStarted)
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
            val native = B2Native.open(file, key, driverContext, true,preflight.native(false))
            AutoCloseable {
                val closing=if(preflight.timing!=null)System.nanoTime() else 0L
                try { native.close() } finally { preflight.measured(ActionDatabasePhase.NATIVE_CLOSE,closing) }
                preflight.closed(!native.isOpen)
            }.use {
                preflight.completed(ActionPreflightStep.OPEN)
                driverContext.requireB2(AccessPoint.STEP)
                phaseStarted=if(preflight.timing!=null)System.nanoTime() else 0L
                if (native.version !in 1..12) throw ProductStorageFailure()
                if(native.version<12 && context.scope!=AccessScope.MAINTENANCE)throw ProductStorageFailure()
                preflight.measured(ActionDatabasePhase.VERSION,phaseStarted)
                preflight.completed(ActionPreflightStep.VERSION)
                driverContext.requireB2(AccessPoint.STEP)
                phaseStarted=if(preflight.timing!=null)System.nanoTime() else 0L
                native.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use {
                    if (it.moveToFirst()) throw ProductStorageFailure()
                }
                preflight.measured(ActionDatabasePhase.CIPHER_INTEGRITY,phaseStarted)
                preflight.completed(ActionPreflightStep.CIPHER_INTEGRITY)
                driverContext.requireB2(AccessPoint.STEP)
                phaseStarted=if(preflight.timing!=null)System.nanoTime() else 0L
                native.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                    if (!it.moveToFirst() || it.getString(0) != "ok" || it.moveToNext()) throw ProductStorageFailure()
                }
                preflight.measured(ActionDatabasePhase.SQLITE_INTEGRITY,phaseStarted)
                preflight.completed(ActionPreflightStep.SQLITE_INTEGRITY)
                driverContext.requireB2(AccessPoint.STEP)
                phaseStarted=if(preflight.timing!=null)System.nanoTime() else 0L
                native.rawQuery("PRAGMA foreign_key_check", emptyArray<String>()).use {
                    if (it.moveToFirst()) throw ProductStorageFailure()
                }
                preflight.measured(ActionDatabasePhase.FOREIGN_KEYS,phaseStarted)
                preflight.completed(ActionPreflightStep.FOREIGN_KEYS)
            }
        }
        driverContext.requireB2(AccessPoint.STEP)
        val lifecycle = B4RoomLifecycle()
        val parent = SupervisorJob()
        val driver = B2Driver(file, key, lifecycle.guard(driverContext), preflight.driverHooks,preflight.native(true),preflight.resources)
        var room: ActionDatabase? = null
        var result: T
        // Standard use() preserves the work failure and suppresses each independent cleanup
        // failure. Inner scopes close first, including after revoke and coroutine failure.
        var cleaning=0L
        var roomEnded=false
        var coroutinesEnded=false
        AutoCloseable {
            try {
                driver.close()
                cleanupConfirmed(roomEnded && coroutinesEnded && driver.resourcesClosed())
            } finally { preflight.measured(ActionDatabasePhase.CLEANUP,cleaning) }
        }.use {
            AutoCloseable { runBlocking { parent.cancelAndJoin() };coroutinesEnded=true }.use {
                AutoCloseable {
                    cleaning=if(preflight.timing!=null)System.nanoTime() else 0L
                    lifecycle.beginClose(); room?.close();roomEnded=true; preflight.roomClosed()
                }.use {
                    phaseStarted=if(preflight.timing!=null)System.nanoTime() else 0L
                    room = Room.databaseBuilder(app, ActionDatabase::class.java, file.path).setDriver(driver)
                        .setQueryCoroutineContext(Dispatchers.IO + parent + lifecycle.handler { parent.cancel() })
                        .addMigrations(ACTION_MIGRATION_1_2, ACTION_MIGRATION_2_3, ACTION_MIGRATION_3_4, ACTION_MIGRATION_4_5, ACTION_MIGRATION_5_6, ACTION_MIGRATION_6_7, ACTION_MIGRATION_7_8, ACTION_MIGRATION_8_9, ACTION_MIGRATION_9_10, ACTION_MIGRATION_10_11, ACTION_MIGRATION_11_12)
                        .addCallback(TASK_DATABASE_CALLBACK)
                        .addCallback(PROTOCOL_DATABASE_CALLBACK)
                        .addCallback(MATERIAL_DATABASE_CALLBACK)
                        .addCallback(DOCUMENT_DATABASE_CALLBACK)
                        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
                    preflight.measured(ActionDatabasePhase.ROOM_BUILDER,phaseStarted)
                    result = runBlocking {
                        lifecycle.requireOpen()
                        if (!context.check(AccessPoint.STEP)) throw StorageAccessRevoked()
                        // Force Room's generated schema validation even for a maintenance-only open.
                        val opening=if(preflight.timing!=null)System.nanoTime() else 0L
                        room.actions().get("")
                        room.useReaderConnection { validateEditRevisions(it) }
                        preflight.measured(ActionDatabasePhase.ROOM_OPEN_SCHEMA,opening)
                        val querying=if(preflight.timing!=null)System.nanoTime() else 0L
                        work(room).also {preflight.measured(ActionDatabasePhase.QUERY_TRANSACTION,querying)}
                    }
                }
            }
        }
        lifecycle.requireHealthy()
        if (!context.check(AccessPoint.PUBLICATION)) throw StorageAccessRevoked()
        return result
    }
}
