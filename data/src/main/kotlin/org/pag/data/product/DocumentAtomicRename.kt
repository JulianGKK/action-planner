package org.pag.data.product

import java.nio.file.Path

/** No reflected/hidden API, shell command, hardlink or overwrite/copy fallback. */
internal object DocumentAtomicRename {
    fun move(source: Path, target: Path) {
        val result = try { DocumentRenameNative.move(source.toString(),target.toString()) }
        catch (_: LinkageError) { throw ProductStorageFailure() }
        if (result != 0) throw ProductStorageFailure()
    }
}
private object DocumentRenameNative {
    init { System.loadLibrary("pag_document_fs") }
    external fun move(source: String, target: String): Int
}
