package org.pag.data.prototype

import org.pag.domain.AccessScope
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import org.pag.security.BootstrapState
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

enum class B16Id(val testId: String) {
    EMPTY("B16-BOOTSTRAP-EMPTY"), COMPLETE("B16-BOOTSTRAP-COMPLETE"),
    BLOCKED("B16-BOOTSTRAP-BLOCKED"), NOT_EMPTY("B16-BOOTSTRAP-NOT-EMPTY"), UNWRAP("B16-UNWRAP-SUCCEEDED"),
    CREATED("B16-DB-CREATE-SUCCEEDED"), DB_PENDING("B16-DB-CREATED-PENDING"), OPENED("B16-DB-OPEN-SUCCEEDED"),
    KEY_OR_DB("B16-DB-WRONG-KEY-OR-UNREADABLE"), INTEGRITY("B16-DB-INTEGRITY"),
    SCHEMA("B16-DB-SCHEMA"), EXPIRED("B16-SESSION-EXPIRED"),
    STALE("B16-STALE-GENERATION"), RESTART("B16-RESTART-SUCCEEDED"),
    AUTH_REQUIRED("B16-UNWRAP-AUTH-REQUIRED"), UNWRAP_FAILED("B16-UNWRAP-FAILED")
}
class B16Exception(val id: B16Id) : RuntimeException(id.testId)

/** Whole new namespace only. No recovery, cleanup or database creation from missing files. */
class B16Inventory(val root: File, private val keyState: () -> BootstrapState) {
    val database = File(root, "d")
    private val sidecars = setOf("d-wal", "d-shm", "d-journal")
    fun state(): BootstrapState = try {
        val entries = entries()
        val keys = keyState()
        if (entries.isEmpty() && keys == BootstrapState.EMPTY) BootstrapState.EMPTY
        else if (entries.keys.containsAll(setOf("0", "k", "d", "1")) &&
            (entries.keys - setOf("0", "k", "d", "1") - sidecars).isEmpty() &&
            keys == BootstrapState.COMPLETE && File(root, "k").isDirectory &&
            entries.filterKeys { it != "k" }.values.all { it.isFile } &&
            database.length() > 0 && intentValid()) {
            identity(); BootstrapState.COMPLETE
        } else BootstrapState.BLOCKED
    } catch (_: Exception) { BootstrapState.BLOCKED }

    fun reserveNew() {
        when (state()) {
            BootstrapState.EMPTY -> Unit
            BootstrapState.COMPLETE -> throw B16Exception(B16Id.NOT_EMPTY)
            BootstrapState.BLOCKED -> fail()
        }
        if (!root.exists() && !root.mkdir()) fail()
        writeNew(File(root, "0"), byteArrayOf(1))
        if (entries().keys != setOf("0") || keyState() != BootstrapState.EMPTY) fail()
    }
    fun requirePending() {
        if (entries().keys != setOf("0", "k") || keyState() != BootstrapState.COMPLETE ||
            !intentValid()) fail()
    }
    fun finish(identity: String, context: StorageContext) {
        val known = setOf("0", "k", "d")
        val entries = entries()
        if (!entries.keys.containsAll(known) || (entries.keys - known - sidecars).isNotEmpty() ||
            keyState() != BootstrapState.COMPLETE || !intentValid() || !File(root, "k").isDirectory ||
            entries.filterKeys { it != "k" }.values.any { !it.isFile } || !database.isFile || database.length() == 0L) fail()
        if (context.scope != AccessScope.MAINTENANCE || !context.check(AccessPoint.COMMIT)) throw B16Exception(B16Id.EXPIRED)
        writeNew(File(root, "1"), UUID.fromString(identity).toString().toByteArray(Charsets.US_ASCII))
        if (!context.check(AccessPoint.STEP)) throw B16Exception(B16Id.EXPIRED)
        if (state() != BootstrapState.COMPLETE) fail()
    }
    fun identity(): String {
        val file = File(root, "1")
        if (!file.isFile || file.length() != 36L) fail()
        val value = file.readText(Charsets.US_ASCII)
        if (UUID.fromString(value).toString() != value) fail()
        return value // Public synthetic fixture identity, never a secret/key.
    }
    private fun intentValid() = File(root, "0").isFile && File(root, "0").length() == 1L && File(root, "0").readBytes().contentEquals(byteArrayOf(1))
    private fun entries(): Map<String, File> {
        if (Files.isSymbolicLink(root.toPath()) || root.absoluteFile != root.canonicalFile) fail()
        if (!root.exists()) return emptyMap()
        if (!root.isDirectory) fail()
        val files = root.listFiles() ?: fail()
        if (files.any { Files.isSymbolicLink(it.toPath()) || it.absoluteFile != it.canonicalFile }) fail()
        return files.associateBy { it.name }
    }
    private fun writeNew(file: File, bytes: ByteArray) {
        if (!file.createNewFile()) fail()
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    private fun fail(): Nothing = throw B16Exception(B16Id.BLOCKED)
}
