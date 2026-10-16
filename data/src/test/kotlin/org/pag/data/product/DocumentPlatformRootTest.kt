package org.pag.data.product

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.domain.*
import java.io.File
import java.nio.file.Files

/** Android may return an app-directory alias. Only that trusted boundary is resolved. */
class DocumentPlatformRootTest {
    @get:Rule val temp=TemporaryFolder()
    private val c=object:StorageContext {
        override val generation=1L;override val scope=AccessScope.MAINTENANCE
        override fun check(point:AccessPoint)=true
        override fun publish(result:()->Unit):Boolean=error("No publication")
    }
    @Test fun platformAliasReproducesOldRejectionAndCanonicalCompositionAllowsEmptyMissingRoot() {
        val actual=temp.newFolder("private-app-root").canonicalFile
        val alias=File(temp.root,"platform-alias")
        Files.createSymbolicLink(alias.toPath(),actual.toPath())
        assertNotEquals(alias.absoluteFile,alias.canonicalFile)
        assertThrows(ProductStorageFailure::class.java) { DocumentFileInventory(alias).inspectRecovery(c,emptySet(),emptySet()) }
        val root=productNoBackupRoot(alias)
        assertEquals(actual,root)
        assertFalse(DocumentFileInventory(root).inspectRecovery(c,emptySet(),emptySet()).rootPresent)
        assertTrue(actual.listFiles()!!.isEmpty())
    }
    @Test fun canonicalPlatformRootIsUnchangedAndDocumentSymlinksRemainBlocked() {
        val actual=temp.newFolder("private-app-root").canonicalFile
        assertEquals(actual,productNoBackupRoot(actual))
        val outside=temp.newFolder("outside").canonicalFile
        Files.createSymbolicLink(File(actual,"pag-documents-v1").toPath(),outside.toPath())
        assertThrows(ProductStorageFailure::class.java) { DocumentFileInventory(productNoBackupRoot(actual)).inspectRecovery(c,emptySet(),emptySet()) }
        assertTrue(outside.listFiles()!!.isEmpty())
    }
}
