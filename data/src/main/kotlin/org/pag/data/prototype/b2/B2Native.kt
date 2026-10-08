package org.pag.data.prototype.b2

import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.Logger
import net.zetetic.database.LogTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SQLiteConnection
import org.pag.domain.StorageContext
import org.pag.domain.AccessPoint
import java.io.File

/** Both variants use exactly this native open/configuration/preflight. No openOrCreate. */
internal object B2Native {
    fun open(file: File, key: ByteArray, context: StorageContext, readOnly: Boolean = false): SQLiteDatabase {
        context.requireB2(AccessPoint.OPERATION)
        if (key.isEmpty()) throw B2Exception(B2Failure.KEY_MISSING)
        Logger.setTarget(object : LogTarget {
            override fun isLoggable(tag: String?, priority: Int) = false
            override fun log(priority: Int, tag: String?, message: String?, throwable: Throwable?) {}
        })
        System.loadLibrary("sqlcipher")
        return SQLiteDatabase.openDatabase(file.path, key, null,
            (if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE) or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            DatabaseErrorHandler { _, _ -> }, object : SQLiteDatabaseHook {
                override fun preKey(connection: SQLiteConnection) {
                    context.requireB2(); connection.executeForLong("PRAGMA cipher_log_level = NONE", null, null)
                }
                override fun postKey(connection: SQLiteConnection) {
                    context.requireB2()
                    if (connection.executeForLong("PRAGMA cipher_status", null, null) != 1L) throw B2Exception(B2Failure.BLOCKED)
                    connection.execute("PRAGMA temp_store = MEMORY", null, null)
                    if (connection.executeForLong("PRAGMA temp_store", null, null) != 2L) throw B2Exception(B2Failure.BLOCKED)
                }
            })
    }
    fun preflight(file: File, key: ByteArray, context: StorageContext, target: Int, migrates: Boolean) {
        open(file, key, context, true).use { db ->
            context.requireB2()
            val version = db.version
            if (version !in 1..target || target !in 1..2) throw B2Exception(B2Failure.SCHEMA)
            if (version != target && !migrates) throw B2Exception(B2Failure.MIGRATION)
            verify(db, context, version)
        }
    }
    fun verify(db: SQLiteDatabase, context: StorageContext, version: Int) {
        context.requireB2()
        db.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use {
            if (it.moveToFirst()) throw B2Exception(B2Failure.INTEGRITY)
        }
        context.requireB2()
        db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
            if (!it.moveToFirst() || it.getString(0) != "ok" || it.moveToNext()) throw B2Exception(B2Failure.INTEGRITY)
        }
        val expected = if (version == 1) listOf("id", "value") else listOf("id", "value", "revision")
        val columns = db.rawQuery("PRAGMA table_info(synthetic_record)", emptyArray<String>()).use {
            buildList { while (it.moveToNext()) {
                context.requireB2()
                if (it.getInt(3) != 1 || it.getInt(5) != (if (it.getString(1) == "id") 1 else 0) ||
                    (it.getString(1) == "revision" && it.getString(4) != "0") ||
                    (it.getString(1) == "revision" && it.getString(2) != "INTEGER") ||
                    (it.getString(1) != "revision" && it.getString(2) != "TEXT")) throw B2Exception(B2Failure.SCHEMA)
                add(it.getString(1))
            } }
        }
        if (columns != expected) throw B2Exception(B2Failure.SCHEMA)
        val child = db.rawQuery("PRAGMA table_info(synthetic_child)", emptyArray<String>()).use {
            buildList { while (it.moveToNext()) {
                context.requireB2()
                if (it.getString(2) != "TEXT" || it.getInt(3) != 1 ||
                    it.getInt(5) != (if (it.getString(1) == "id") 1 else 0)) throw B2Exception(B2Failure.SCHEMA)
                add(it.getString(1))
            } }
        }
        if (child != listOf("id", "parentId")) throw B2Exception(B2Failure.SCHEMA)
        db.rawQuery("PRAGMA foreign_key_list(synthetic_child)", emptyArray<String>()).use {
            if (!it.moveToFirst() || it.getString(2) != "synthetic_record" || it.getString(3) != "parentId" ||
                it.getString(4) != "id" || it.getString(5) != "NO ACTION" || it.getString(6) != "NO ACTION" ||
                it.moveToNext()) throw B2Exception(B2Failure.SCHEMA)
        }
        db.rawQuery("PRAGMA index_info(index_synthetic_child_parentId)", emptyArray<String>()).use {
            if (!it.moveToFirst() || it.getString(2) != "parentId" || it.moveToNext()) throw B2Exception(B2Failure.SCHEMA)
        }
        db.rawQuery("PRAGMA foreign_key_check", emptyArray<String>()).use {
            if (it.moveToFirst()) throw B2Exception(B2Failure.INTEGRITY)
        }
        context.requireB2()
    }
}
