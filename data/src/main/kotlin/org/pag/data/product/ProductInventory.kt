package org.pag.data.product

import org.pag.domain.ProductStorageState
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/** Fully reserved product namespace; no scanning/migrating/cleaning prototype directories. */
class ProductInventory(val root: File, private val aliasPresent: () -> Boolean,
    private val envelopeValid: () -> Boolean) {
    val database: File get() = File(root, "store/d")
    fun state(): ProductStorageState = try {
        val entries = entries(root)
        val alias = aliasPresent()
        if (entries.isEmpty() && !alias) ProductStorageState.EMPTY
        else if (alias && entries.keys == setOf("intent", "envelope", "public", "store", "complete") &&
            marker("intent") && marker("complete") && envelopeValid() && storeValid()) ProductStorageState.READY_TO_OPEN
        else ProductStorageState.PARTIAL_OR_INVALID
    } catch (_: Exception) { ProductStorageState.PARTIAL_OR_INVALID }
    fun reserve() {
        if (state() != ProductStorageState.EMPTY) throw ProductStorageFailure()
        if (!root.exists() && !root.mkdir()) throw ProductStorageFailure()
        writeMarker("intent")
    }
    fun prepareDatabaseDirectory() {
        if (entries(root).keys != setOf("intent", "public", "envelope") || !marker("intent") || !envelopeValid())
            throw ProductStorageFailure()
        if (!File(root, "store").mkdir()) throw ProductStorageFailure()
    }
    fun finish(context: StorageContext) {
        if (!context.check(AccessPoint.COMMIT) || entries(root).keys != setOf("intent", "public", "envelope", "store") ||
            !envelopeValid() || !storeValid()) throw ProductStorageFailure()
        writeMarker("complete")
        if (state() != ProductStorageState.READY_TO_OPEN || !context.check(AccessPoint.STEP)) throw ProductStorageFailure()
    }
    private fun storeValid(): Boolean {
        val files = entries(File(root, "store"))
        return files.keys.all { it in setOf("d", "d.lck", "d-journal") } &&
            files.values.all { it.isFile } && database.isFile && database.length() > 0 &&
            files.filterKeys { it != "d" }.values.all { it.length() == 0L }
    }
    private fun marker(name: String): Boolean {
        val f = File(root, name)
        return f.isFile && f.length() == 1L && f.readBytes().contentEquals(byteArrayOf(1))
    }
    private fun writeMarker(name: String) {
        val f = File(root, name)
        if (!f.createNewFile()) throw ProductStorageFailure()
        FileOutputStream(f).use { it.write(1); it.fd.sync() }
    }
    private fun entries(dir: File): Map<String, File> {
        if (Files.isSymbolicLink(dir.toPath()) || dir.absoluteFile != dir.canonicalFile) throw ProductStorageFailure()
        if (!dir.exists()) return emptyMap()
        if (!dir.isDirectory) throw ProductStorageFailure()
        val files = dir.listFiles() ?: throw ProductStorageFailure()
        if (files.any { Files.isSymbolicLink(it.toPath()) || it.absoluteFile != it.canonicalFile }) throw ProductStorageFailure()
        return files.associateBy { it.name }
    }
}
