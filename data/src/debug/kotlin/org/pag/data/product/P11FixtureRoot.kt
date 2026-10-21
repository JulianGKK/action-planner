package org.pag.data.product

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Exact, freshly created synthetic root only. Never follows links during deletion. */
internal class P11FixtureRoot(private val parent: Path, private val root: Path) {
    private val identity = attributes().fileKey() ?: error("SYNTHETIC_ROOT_IDENTITY_UNAVAILABLE")
    private fun attributes(): BasicFileAttributes {
        check(root.parent == parent && root.fileName.toString().startsWith("p11-fixture-"))
        check(parent.toRealPath() == parent && !Files.isSymbolicLink(root))
        return Files.readAttributes(root, BasicFileAttributes::class.java, NOFOLLOW_LINKS).also {
            check(it.isDirectory)
        }
    }
    fun close(delete: (Path) -> Boolean = { path ->
        Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        true
    }) {
        check(attributes().fileKey() == identity) { "SYNTHETIC_ROOT_CHANGED" }
        check(delete(root)) { "SYNTHETIC_CLEANUP_FAILED" }
        check(!Files.exists(root, NOFOLLOW_LINKS)) { "SYNTHETIC_CLEANUP_INCOMPLETE" }
    }
}
