package org.pag.data.prototype.b2

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.io.File

/** Identical application operations; short, exclusively owned DB/DAO lifetime. */
class B2Database private constructor(private val context: StorageContext, private val hooks: B2Hooks,
    private val version: Int, private val direct: SQLiteDatabase?, private val room: RoomDatabase?,
    private val driver: B2Driver?) : AutoCloseable {
    private var closed = false
    fun read(deliver: (List<B2Record>) -> Unit): Boolean = sanitizedB2 {
        requireOpen()
        val rows = if (direct != null) direct.rawQuery(
            if (version == 1) "SELECT id,value,0 FROM synthetic_record ORDER BY id" else "SELECT id,value,revision FROM synthetic_record ORDER BY id",
            emptyArray<String>()).use { cursor -> buildList { while (cursor.moveToNext()) {
                context.requireB2(); add(B2Record(cursor.getString(0), cursor.getString(1), cursor.getInt(2)))
            } } }
        else runBlocking {
            if (room is RoomV1) room.records().read().map { B2Record(it.id, it.value) }
            else (room as RoomV2).records().read().map { B2Record(it.id, it.value, it.revision) }
        }
        hooks.afterQuery()
        context.publish { deliver(rows) }
    }
    fun write(record: B2Record, childParent: String? = null, commit: Boolean = true): Boolean = sanitizedB2 {
        requireOpen()
        if (direct != null) {
            direct.beginTransaction()
            try {
                context.requireB2()
                if (version == 1) direct.execSQL("INSERT INTO synthetic_record(id,value) VALUES(?,?)", arrayOf(record.id, record.value))
                else direct.execSQL("INSERT INTO synthetic_record(id,value,revision) VALUES(?,?,?)", arrayOf<Any>(record.id, record.value, record.revision))
                context.requireB2()
                if (childParent != null) direct.execSQL("INSERT INTO synthetic_child(id,parentId) VALUES(?,?)", arrayOf(record.id, childParent))
                if (commit) { hooks.beforeCommit(); context.requireB2(AccessPoint.COMMIT); direct.setTransactionSuccessful() }
            } finally { direct.endTransaction() }
        } else {
            try {
                runBlocking { room!!.withWriteTransaction {
                    context.requireB2()
                    if (room is RoomV1) {
                        room.records().insert(RecordV1(record.id, record.value))
                        if (childParent != null) room.records().child(ChildV1(record.id, childParent))
                    } else {
                        (room as RoomV2).records().insert(RecordV2(record.id, record.value, record.revision))
                        if (childParent != null) room.records().child(ChildV2(record.id, childParent))
                    }
                    context.requireB2()
                    if (!commit) throw RollbackOnly()
                } }
            } catch (_: RollbackOnly) { /* Requested transaction rollback; no other exception swallowed. */ }
        }
        context.publish {}
    }
    fun isClosed() = closed && direct?.isOpen != true && (driver == null || driver.resourcesClosed())
    private fun requireOpen() {
        if (closed) throw B2Exception(B2Failure.CLOSED)
        context.requireB2(AccessPoint.OPERATION)
    }
    override fun close() {
        if (!closed) {
            closed = true
            try { room?.close(); direct?.close() } finally { driver?.close() }
        }
    }
    private class RollbackOnly : RuntimeException()
    companion object {
        private const val CREATE_RECORD = "CREATE TABLE synthetic_record(id TEXT NOT NULL PRIMARY KEY,value TEXT NOT NULL)"
        private const val CREATE_CHILD = "CREATE TABLE synthetic_child(id TEXT NOT NULL PRIMARY KEY,parentId TEXT NOT NULL,FOREIGN KEY(parentId) REFERENCES synthetic_record(id) ON UPDATE NO ACTION ON DELETE NO ACTION)"
        private const val CREATE_INDEX = "CREATE INDEX index_synthetic_child_parentId ON synthetic_child(parentId)"
        private const val MIGRATE = "ALTER TABLE synthetic_record ADD COLUMN revision INTEGER NOT NULL DEFAULT 0"
        fun open(app: Context, file: File, key: ByteArray, context: StorageContext, variant: B2Variant,
            creates: Boolean, version: Int = 2, migrates: Boolean = true, hooks: B2Hooks = B2Hooks()): B2Database = sanitizedB2 {
            context.requireB2(AccessPoint.OPERATION)
            if (key.isEmpty()) throw B2Exception(B2Failure.KEY_MISSING)
            if (version !in 1..2) throw B2Exception(B2Failure.SCHEMA)
            inventory(file, creates, variant == B2Variant.ROOM)
            if (creates) { if (!file.createNewFile()) throw B2Exception(B2Failure.NOT_EMPTY) }
            else B2Native.preflight(file, key, context, version, migrates)
            context.requireB2()
            var native: SQLiteDatabase? = null
            var database: RoomDatabase? = null
            var driver: B2Driver? = null
            try {
                if (variant == B2Variant.DIRECT) {
                    native = B2Native.open(file, key, context)
                    native.setForeignKeyConstraintsEnabled(true)
                    native.rawQuery("PRAGMA journal_mode = TRUNCATE", emptyArray<String>()).use {
                        if (!it.moveToFirst() || it.getString(0) != "truncate") throw B2Exception(B2Failure.BLOCKED)
                    }
                    if (creates || native.version != version) {
                        native.beginTransaction()
                        try {
                            context.requireB2()
                            if (creates) {
                                native.execSQL(CREATE_RECORD); native.execSQL(CREATE_CHILD); native.execSQL(CREATE_INDEX)
                                if (version == 2) native.execSQL(MIGRATE)
                            } else {
                                if (native.version != 1 || version != 2 || !migrates) throw B2Exception(B2Failure.MIGRATION)
                                native.execSQL(MIGRATE); hooks.duringMigration(); context.requireB2()
                            }
                            native.version = version
                            B2Native.verify(native, context, version)
                            hooks.beforeCommit(); context.requireB2(AccessPoint.COMMIT); native.setTransactionSuccessful()
                        } finally { native.endTransaction() }
                    }
                    B2Native.verify(native, context, version)
                } else {
                    driver = B2Driver(file, key, context, hooks)
                    val builder: RoomDatabase.Builder<out RoomDatabase> = if (version == 1) Room.databaseBuilder(app, RoomV1::class.java, file.path)
                        else Room.databaseBuilder(app, RoomV2::class.java, file.path)
                    builder.setDriver(driver).setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    // No multi-instance service, destructive fallback, auto-close, copy/prepackaged DB or query logger.
                    if (migrates) builder.addMigrations(object : Migration(1, 2) {
                        override suspend fun migrate(connection: SQLiteConnection) {
                            context.requireB2()
                            connection.prepare(MIGRATE).use { it.step() }
                            hooks.duringMigration(); context.requireB2()
                        }
                    })
                    val built: RoomDatabase = builder.build()
                    database = built
                    // Force actual open + Room schema validation while still in MAINTENANCE.
                    runBlocking {
                        if (built is RoomV1) built.records().read() else (built as RoomV2).records().read()
                    }
                    context.requireB2()
                }
                hooks.afterOpen(); context.requireB2()
                B2Database(context, hooks, version, native, database, driver)
            } catch (error: Throwable) {
                try { database?.close(); native?.close() } finally { driver?.close() }
                throw error
            }
        }
    }
}
