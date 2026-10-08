package org.pag.data.prototype.b2

import org.pag.security.BootstrapState
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

/** Separate B2 namespace: never use/mutate the real p15/p16 fixtures. */
class B2Inventory(val root: File, private val keys: () -> BootstrapState, private val variant: B2Variant) {
    val store = File(root, "s")
    val database = File(store, "d")
    fun state(): BootstrapState = try {
        val entries = entries()
        val state = keys()
        if (entries.isEmpty() && state == BootstrapState.EMPTY) BootstrapState.EMPTY
        else if (entries == setOf("0", "k", "s", "1") && state == BootstrapState.COMPLETE && intent()) {
            identity(); inventory(database, false, variant == B2Variant.ROOM); BootstrapState.COMPLETE
        } else BootstrapState.BLOCKED
    } catch (_: Exception) { BootstrapState.BLOCKED }
    fun reserve() {
        when (state()) {
            BootstrapState.EMPTY -> Unit
            BootstrapState.COMPLETE -> throw B2Exception(B2Failure.NOT_EMPTY)
            BootstrapState.BLOCKED -> throw B2Exception(B2Failure.INVENTORY)
        }
        if (!root.exists() && !root.mkdir()) throw B2Exception(B2Failure.INVENTORY)
        writeNew(File(root, "0"), byteArrayOf(1))
    }
    fun pending() {
        if (entries() != setOf("0", "k") || !intent() || keys() != BootstrapState.COMPLETE)
            throw B2Exception(B2Failure.INVENTORY)
    }
    fun finish(id: String, context: StorageContext) {
        if (entries() != setOf("0", "k", "s") || !intent() || keys() != BootstrapState.COMPLETE)
            throw B2Exception(B2Failure.INVENTORY)
        inventory(database, false, variant == B2Variant.ROOM)
        context.requireB2(AccessPoint.COMMIT)
        writeNew(File(root, "1"), UUID.fromString(id).toString().toByteArray(Charsets.US_ASCII))
        context.requireB2()
        if (state() != BootstrapState.COMPLETE) throw B2Exception(B2Failure.INVENTORY)
    }
    fun identity(): String {
        val marker = File(root, "1")
        if (!marker.isFile || marker.length() != 36L) throw B2Exception(B2Failure.INVENTORY)
        val value = marker.readText(Charsets.US_ASCII)
        if (UUID.fromString(value).toString() != value) throw B2Exception(B2Failure.INVENTORY)
        return value // Public synthetic identity, never a key or product data.
    }
    private fun intent() = File(root, "0").isFile && File(root, "0").length() == 1L && File(root, "0").readBytes().contentEquals(byteArrayOf(1))
    private fun entries(): Set<String> {
        if (Files.isSymbolicLink(root.toPath()) || root.absoluteFile != root.canonicalFile) throw B2Exception(B2Failure.INVENTORY)
        if (!root.exists()) return emptySet()
        if (!root.isDirectory) throw B2Exception(B2Failure.INVENTORY)
        val entries = root.listFiles() ?: throw B2Exception(B2Failure.INVENTORY)
        if (entries.any { Files.isSymbolicLink(it.toPath()) || it.absoluteFile != it.canonicalFile }) throw B2Exception(B2Failure.INVENTORY)
        if (entries.any { if (it.name in setOf("k", "s")) !it.isDirectory else !it.isFile }) throw B2Exception(B2Failure.INVENTORY)
        return entries.map { it.name }.toSet()
    }
    private fun writeNew(file: File, value: ByteArray) {
        if (!file.createNewFile()) throw B2Exception(B2Failure.INVENTORY)
        FileOutputStream(file).use { it.write(value); it.fd.sync() }
    }
}
