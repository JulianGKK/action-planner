package org.pag.data.prototype

import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteException
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.LogTarget
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import org.pag.domain.AccessScope
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.io.File
import java.io.IOException

/** B1 only. Error codes deliberately contain neither SQL nor native exception messages. */
enum class EngineFailure {
    SESSION_INVALID, MISSING_DATABASE, MISSING_KEY, KEY_OR_DATABASE_UNREADABLE,
    DATABASE_UNREADABLE, INTEGRITY_FAILED, UNKNOWN_SCHEMA, IO, SPACE, BLOCKED
}
class EngineException(val failure: EngineFailure) : RuntimeException(failure.name)

/** Injectable test boundaries, not product import/recovery infrastructure. */
class EngineTestHooks(
    val afterQuery: () -> Unit = {},
    val beforeCommit: () -> Unit = {},
    val afterWrite: () -> Unit = {}
)
data class SyntheticRow(val id: String, val marker: String, val status: Int)

/** An isolated, single-owner B1 handle permanently bound to its original context.
 * No DB object, connection or cursor crosses this boundary. close() only releases resources.
 * This is NOT an application repository or a persistent production key model.
 */
class SqlCipherEngine private constructor(
    private val db: SQLiteDatabase,
    private val context: StorageContext,
    private val hooks: EngineTestHooks
) : AutoCloseable {
    private var closed = false
    private var blocked = false
    fun read(deliver: (List<SyntheticRow>) -> Unit): Boolean = operation {
        context.require(AccessPoint.OPERATION)
        val rows = db.rawQuery("SELECT id, marker, status FROM synthetic ORDER BY id", emptyArray<String>()).use { cursor ->
            buildList { while (cursor.moveToNext()) {
                context.require(AccessPoint.STEP)
                add(SyntheticRow(cursor.getString(0), cursor.getString(1), cursor.getInt(2)))
            } }
        }
        hooks.afterQuery()
        // Publication and lock share the short coordinator synchronization boundary.
        context.publish { deliver(rows) }
    }

    fun write(row: SyntheticRow, commit: Boolean = true, deliver: () -> Unit = {}): Boolean = operation {
        context.require(AccessPoint.OPERATION)
        db.beginTransaction()
        try {
            context.require(AccessPoint.STEP)
            db.execSQL("INSERT INTO synthetic(id, marker, status) VALUES (?, ?, ?)",
                arrayOf<Any>(row.id, row.marker, row.status))
            hooks.afterWrite()
            if (commit) {
                hooks.beforeCommit()
                context.require(AccessPoint.COMMIT)
                db.setTransactionSuccessful()
            }
        } finally {
            // Rollback / already-authorized native completion must remain possible after lock.
            db.endTransaction()
        }
        context.publish(deliver)
    }

    fun configuration(deliver: (Map<String, String>) -> Unit): Boolean = operation {
        context.require(AccessPoint.OPERATION)
        val values = linkedMapOf<String, String>()
        for ((name, query) in listOf("cipherVersion" to "PRAGMA cipher_version",
            "journalMode" to "PRAGMA journal_mode", "tempStore" to "PRAGMA temp_store",
            "sqliteVersion" to "SELECT sqlite_version()")) {
            context.require(AccessPoint.STEP)
            values[name] = db.rawQuery(query, emptyArray<String>()).use {
                if (!it.moveToFirst()) throw EngineException(EngineFailure.BLOCKED)
                it.getString(0)
            }
        }
        context.publish { deliver(values) }
    }

    override fun close() = sanitized { if (!closed) { closed = true; db.close() } }

    private inline fun <T> operation(work: () -> T): T {
        if (closed || blocked) throw EngineException(EngineFailure.BLOCKED)
        return try { sanitized(work) }
        catch (failure: EngineException) { blocked = true; throw failure }
    }

    companion object {
        private const val SCHEMA = 1
        private val noDeletion = DatabaseErrorHandler { _, _ -> /* Never replace/delete the existing file. */ }

        /** Explicit new-only creation. Atomic empty-file reservation is proof of new ownership.
         * A failed attempt remains on disk and is never retried as a new database.
         */
        fun createNew(file: File, key: ByteArray?, context: StorageContext,
            hooks: EngineTestHooks = EngineTestHooks()): SqlCipherEngine = sanitized {
            context.require(AccessPoint.OPERATION)
            requireKey(key)
            if (!file.parentFile.isDirectory || !file.createNewFile()) throw EngineException(EngineFailure.BLOCKED)
            val db = open(file, key!!, context, SQLiteDatabase.CREATE_IF_NECESSARY)
            try {
                context.require(AccessPoint.STEP)
                db.beginTransaction()
                try {
                    context.require(AccessPoint.STEP)
                    db.execSQL("CREATE TABLE synthetic(id TEXT PRIMARY KEY NOT NULL, marker TEXT NOT NULL, status INTEGER NOT NULL)")
                    context.require(AccessPoint.STEP)
                    db.execSQL("PRAGMA user_version = 1")
                    context.require(AccessPoint.COMMIT)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                verify(db, context)
                SqlCipherEngine(db, context, hooks)
            } catch (failure: RuntimeException) { db.close(); throw failure }
        }

        /** Read-only preflight before opening any existing database for writing. No create flag. */
        fun openExisting(file: File, key: ByteArray?, context: StorageContext,
            hooks: EngineTestHooks = EngineTestHooks()): SqlCipherEngine = sanitized {
            context.require(AccessPoint.OPERATION)
            requireKey(key)
            if (!file.exists()) throw EngineException(EngineFailure.MISSING_DATABASE)
            if (!file.isFile || file.length() == 0L) throw EngineException(EngineFailure.DATABASE_UNREADABLE)
            open(file, key!!, context, SQLiteDatabase.OPEN_READONLY).use { verify(it, context) }
            context.require(AccessPoint.STEP)
            val db = open(file, key, context, SQLiteDatabase.OPEN_READWRITE)
            try {
                verify(db, context)
                SqlCipherEngine(db, context, hooks)
            } catch (failure: RuntimeException) { db.close(); throw failure }
        }

        private fun requireKey(key: ByteArray?) {
            if (key == null || key.isEmpty()) throw EngineException(EngineFailure.MISSING_KEY)
        }
        private fun open(file: File, key: ByteArray, context: StorageContext, flags: Int): SQLiteDatabase {
            context.require(AccessPoint.STEP)
            // Disable wrapper SQL/exception logging before loading/opening the engine.
            Logger.setTarget(object : LogTarget {
                override fun isLoggable(tag: String?, priority: Int) = false
                override fun log(priority: Int, tag: String?, message: String?, throwable: Throwable?) { }
            })
            System.loadLibrary("sqlcipher")
            return SQLiteDatabase.openDatabase(file.absolutePath, key, null,
                flags or SQLiteDatabase.NO_LOCALIZED_COLLATORS, noDeletion, object : SQLiteDatabaseHook {
                    override fun preKey(connection: SQLiteConnection) {
                        context.require(AccessPoint.STEP)
                        connection.executeForLong("PRAGMA cipher_log_level = NONE", null, null)
                    }
                    override fun postKey(connection: SQLiteConnection) {
                        context.require(AccessPoint.STEP)
                        if (connection.executeForLong("PRAGMA cipher_status", null, null) != 1L)
                            throw EngineException(EngineFailure.BLOCKED)
                        context.require(AccessPoint.STEP)
                        // Per-connection, also for connections opened later by the pool.
                        connection.execute("PRAGMA temp_store = MEMORY", null, null)
                        if (connection.executeForLong("PRAGMA temp_store", null, null) != 2L)
                            throw EngineException(EngineFailure.BLOCKED)
                    }
                })
        }
        private fun verify(db: SQLiteDatabase, context: StorageContext) {
            context.require(AccessPoint.STEP)
            if (db.version != SCHEMA) throw EngineException(EngineFailure.UNKNOWN_SCHEMA)
            db.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use {
                if (it.moveToFirst()) throw EngineException(EngineFailure.INTEGRITY_FAILED)
            }
            context.require(AccessPoint.STEP)
            db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                if (!it.moveToFirst() || it.getString(0) != "ok" || it.moveToNext())
                    throw EngineException(EngineFailure.INTEGRITY_FAILED)
            }
            context.require(AccessPoint.STEP)
            // Unknown shape is blocked, not migrated or repaired.
            val columns = db.rawQuery("PRAGMA table_info(synthetic)", emptyArray<String>()).use {
                buildList { while (it.moveToNext()) add(it.getString(1)) }
            }
            if (columns != listOf("id", "marker", "status")) throw EngineException(EngineFailure.UNKNOWN_SCHEMA)
        }
        private fun StorageContext.require(point: AccessPoint) {
            if (scope != AccessScope.READY || !check(point)) throw EngineException(EngineFailure.SESSION_INVALID)
        }
        private inline fun <T> sanitized(work: () -> T): T = try { work() }
        catch (failure: EngineException) { throw failure }
        catch (_: SQLiteFullException) { throw EngineException(EngineFailure.SPACE) }
        catch (_: SQLiteDiskIOException) { throw EngineException(EngineFailure.IO) }
        // Engine errors cannot reliably distinguish a wrong key from damaged ciphertext.
        catch (_: SQLiteException) { throw EngineException(EngineFailure.KEY_OR_DATABASE_UNREADABLE) }
        catch (_: IOException) { throw EngineException(EngineFailure.IO) }
        catch (_: RuntimeException) { throw EngineException(EngineFailure.BLOCKED) }
        catch (_: LinkageError) { throw EngineException(EngineFailure.BLOCKED) }
    }
}
