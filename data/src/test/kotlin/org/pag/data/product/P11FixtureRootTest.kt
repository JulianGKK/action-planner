package org.pag.data.product

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class P11FixtureRootTest {
    private fun fixture(test:(java.nio.file.Path,P11FixtureRoot)->Unit) {
        val parent=Files.createTempDirectory("p11-host-").toRealPath()
        val root=Files.createDirectory(parent.resolve("p11-fixture-host"))
        try { test(root,P11FixtureRoot(parent,root)) }
        finally { parent.toFile().deleteRecursively() }
    }
    @Test fun failedDeletionIsReported()=fixture { _,guard ->
        assertThrows(IllegalStateException::class.java) { guard.close { false } }
    }
    @Test fun falseSuccessWithRemainingFilesIsReported()=fixture { root,guard ->
        Files.write(root.resolve("synthetic"),byteArrayOf(1))
        assertThrows(IllegalStateException::class.java) { guard.close { true } }
    }
    @Test fun successfulDeletionLeavesNoRoot()=fixture { root,guard ->
        Files.createDirectory(root.resolve("nested"))
        guard.close();assertFalse(Files.exists(root))
    }
    @Test fun replacedRootIsNotDeleted()=fixture { root,guard ->
        val old=root.resolveSibling("old")
        Files.move(root,old);Files.createDirectory(root)
        assertThrows(IllegalStateException::class.java) { guard.close() }
        assertTrue(Files.exists(root));assertTrue(Files.exists(old))
    }
}
