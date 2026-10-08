package org.pag.data.prototype.b2

import org.pag.domain.AccessPoint
import org.pag.domain.AccessScope
import org.pag.domain.StorageContext
import java.io.File
import java.nio.file.Files

/** Technical prototype only: no DB/DAO/key escapes to Domain/UI. */
enum class B2Variant { DIRECT, ROOM }
enum class B2Failure { SESSION, INVENTORY, MISSING, NOT_EMPTY, KEY_MISSING, KEY_OR_UNREADABLE, INTEGRITY, SCHEMA, MIGRATION, CONSTRAINT, IO, SPACE, CLOSED, BLOCKED }
open class B2Exception(val failure: B2Failure) : RuntimeException("B2-${failure.name}")
/** Exact access-denial type. No message parsing or Room coroutine-name classification. */
class StorageAccessRevoked : B2Exception(B2Failure.SESSION)
data class B2Record(val id: String, val value: String, val revision: Int = 0)
class B2Hooks(val afterOpen: () -> Unit = {}, val afterQuery: () -> Unit = {},
    val beforeCommit: () -> Unit = {}, val duringMigration: () -> Unit = {})

internal fun StorageContext.requireB2(point: AccessPoint = AccessPoint.STEP) {
    if (scope != AccessScope.MAINTENANCE || !check(point)) throw StorageAccessRevoked()
}

/** No recovery/cleanup: includes all entries, not merely the expected main file. */
internal fun inventory(file: File, creates: Boolean, room: Boolean = false) {
    val root = file.parentFile ?: throw B2Exception(B2Failure.INVENTORY)
    if (!root.isDirectory || Files.isSymbolicLink(root.toPath()) || root.absoluteFile != root.canonicalFile)
        throw B2Exception(B2Failure.INVENTORY)
    val entries = root.listFiles() ?: throw B2Exception(B2Failure.INVENTORY)
    val lockName = "${file.name}.lck"
    val allowed = setOf(file.name, "${file.name}-wal", "${file.name}-shm", "${file.name}-journal") +
        if (room) setOf(lockName) else emptySet()
    if (entries.any { it.name !in allowed || !it.isFile || Files.isSymbolicLink(it.toPath()) || it.absoluteFile != it.canonicalFile })
        throw B2Exception(B2Failure.INVENTORY)
    // Room's process-coordination artifact is empty, not a content/key file.
    if (entries.any { it.name == lockName && it.length() != 0L }) throw B2Exception(B2Failure.INVENTORY)
    if (creates && entries.isNotEmpty()) throw B2Exception(B2Failure.NOT_EMPTY)
    if (!creates && (!file.isFile || file.length() == 0L)) throw B2Exception(B2Failure.MISSING)
}

internal inline fun <T> sanitizedB2(work: () -> T): T = try { work() }
catch (error: B2Exception) { throw error }
catch (_: android.database.sqlite.SQLiteConstraintException) { throw B2Exception(B2Failure.CONSTRAINT) }
catch (_: android.database.sqlite.SQLiteFullException) { throw B2Exception(B2Failure.SPACE) }
catch (_: android.database.sqlite.SQLiteDiskIOException) { throw B2Exception(B2Failure.IO) }
catch (_: android.database.sqlite.SQLiteException) { throw B2Exception(B2Failure.KEY_OR_UNREADABLE) }
catch (_: java.io.IOException) { throw B2Exception(B2Failure.IO) }
catch (_: RuntimeException) { throw B2Exception(B2Failure.BLOCKED) }
catch (_: LinkageError) { throw B2Exception(B2Failure.BLOCKED) }
