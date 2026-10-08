package org.pag.data.prototype.b3

import org.pag.security.BootstrapState
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

/** Names/lengths leak filesystem metadata; all asset metadata is inside encrypted m.
 * Incomplete attempts block on restart. Full recovery belongs to B4.
 */
class B3Inventory(val root: File, private val keys: () -> BootstrapState) {
    val manifest = File(root, "m")
    val assets = File(root, "s")
    private fun entries(directory: File): List<File> {
        if (Files.isSymbolicLink(directory.toPath()) || directory.absoluteFile != directory.canonicalFile)
            throw B3Exception(B3Id.BLOCKED)
        if (!directory.exists()) return emptyList()
        if (!directory.isDirectory) throw B3Exception(B3Id.BLOCKED)
        return (directory.listFiles() ?: throw B3Exception(B3Id.BLOCKED)).toList().also { files ->
            if (files.any { Files.isSymbolicLink(it.toPath()) || it.absoluteFile != it.canonicalFile })
                throw B3Exception(B3Id.BLOCKED)
        }
    }
    fun state(): B3State = try {
        val entries = entries(root)
        if (entries.isEmpty() && keys() == BootstrapState.EMPTY) B3State.EMPTY
        else if (entries.map { it.name }.toSet() == setOf("0", "k", "s", "m") &&
            intentValid() && File(root, "k").isDirectory && keys() == BootstrapState.COMPLETE &&
            manifest.isFile && manifest.length() > 0 && entries(assets).size == 1 &&
            entries(assets).all { it.isFile && opaque(it.name) && it.length() > 0 }) B3State.COMPLETE_CANDIDATE
        else B3State.BLOCKED
    } catch (_: Exception) { B3State.BLOCKED }

    fun reserve() {
        when (state()) {
            B3State.EMPTY -> Unit
            B3State.COMPLETE_CANDIDATE -> throw B3Exception(B3Id.NOT_EMPTY)
            else -> throw B3Exception(B3Id.BLOCKED)
        }
        if (!root.exists() && !root.mkdir()) throw B3Exception(B3Id.BLOCKED)
        writeNew(File(root, "0"), byteArrayOf(1))
    }
    fun keyReady() {
        if (entries(root).map { it.name }.toSet() != setOf("0", "k") ||
            !intentValid() || keys() != BootstrapState.COMPLETE) throw B3Exception(B3Id.BLOCKED)
    }
    fun staging(): File {
        keyReady()
        if (!assets.mkdir()) throw B3Exception(B3Id.BLOCKED)
        return File(assets, UUID.randomUUID().toString()).also {
            if (!it.createNewFile()) throw B3Exception(B3Id.BLOCKED)
        }
    }
    fun file(name: String): File {
        if (!opaque(name)) throw B3Exception(B3Id.BLOCKED)
        val file = File(assets, name)
        if (entries(assets).singleOrNull() != file || !file.isFile) throw B3Exception(B3Id.BLOCKED)
        return file
    }
    private fun intentValid() = File(root, "0").isFile && File(root, "0").length() == 1L &&
        File(root, "0").readBytes().contentEquals(byteArrayOf(1))
    companion object {
        fun opaque(value: String) = try { UUID.fromString(value).toString() == value } catch (_: Exception) { false }
        fun writeNew(file: File, bytes: ByteArray) {
            if (!file.createNewFile()) throw B3Exception(B3Id.BLOCKED)
            FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
        }
    }
}
