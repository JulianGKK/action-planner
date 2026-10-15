package org.pag.data.product

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

class DocumentFileInventoryTest {
    @get:Rule val temporary=TemporaryFolder()
    private var allowed=true
    private val c=object:StorageContext {
        override val scope=AccessScope.MAINTENANCE;override val generation=1L
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean=error("No publication")
    }
    private fun base()=temporary.newFolder().canonicalFile
    private fun prepare(base:File):File=File(base,"pag-documents-v1").also { root ->
        // Test fixture only. Production primitive deliberately has no root creation API.
        check(File(root,"staging").mkdirs());check(File(root,"files").mkdir())
    }
    private fun id()=UUID.randomUUID().toString()
    private fun invalid(work:()->Unit){assertThrows(ProductStorageFailure::class.java,work)}
    @Test fun missingRootIsReadOnlyAndKnownMissingOrPartialRootBlocks() {
        val base=base();val inventory=DocumentFileInventory(base)
        assertFalse(inventory.inspect(c,emptySet(),emptySet()).rootPresent)
        assertTrue(base.listFiles()!!.isEmpty())
        invalid{inventory.inspect(c,setOf(id()),emptySet())}
        check(File(base,"pag-documents-v1/staging").mkdirs())
        invalid{inventory.inspect(c,emptySet(),emptySet())}
    }
    @Test fun unknownFilesDirectoriesAndCrossAreaNamesBlockWithoutCleanup() {
        val base=base();val root=prepare(base);val inventory=DocumentFileInventory(base);val name=id()
        val file=File(root,"staging/$name");file.writeBytes(byteArrayOf(4))
        invalid{inventory.inspect(c,emptySet(),emptySet())};assertTrue(file.exists())
        assertEquals(setOf(name),inventory.inspect(c,setOf(name),emptySet()).staging)
        invalid{inventory.inspect(c,setOf(name),setOf(name))}
        val unknown=File(root,"unexpected");check(unknown.mkdir())
        invalid{inventory.inspect(c,setOf(name),emptySet())};assertTrue(unknown.isDirectory)
        check(unknown.delete());check(file.delete());check(file.mkdir())
        invalid{inventory.inspect(c,setOf(name),emptySet())};assertTrue(file.isDirectory)
    }
    @Test fun traversalAndNonOpaqueNamesNeverReachAnOpen() {
        val base=base();prepare(base);val inventory=DocumentFileInventory(base)
        for(name in listOf("../outside","/absolute","filename.pdf",".","..","00000000-0000-0000-0000-00000000000X")) {
            invalid{inventory.inspect(c,setOf(name),emptySet())}
            invalid{inventory.verifyExisting(c,DocumentFileArea.STAGING,name,emptySet(),emptySet(),id(),ByteArray(32),0,DocumentFormat.TEXT)}
        }
    }
    @Test fun symlinksAtRootDirectoryAndFileAreRejectedAndTargetsUntouched() {
        val base=base();val outside=temporary.newFolder();val inventory=DocumentFileInventory(base)
        val root=File(base,"pag-documents-v1").toPath()
        Files.createSymbolicLink(root,outside.toPath())
        invalid{inventory.inspect(c,emptySet(),emptySet())};assertTrue(outside.listFiles()!!.isEmpty())
        Files.delete(root);val actual=prepare(base)
        Files.delete(File(actual,"staging").toPath());Files.createSymbolicLink(File(actual,"staging").toPath(),outside.toPath())
        invalid{inventory.inspect(c,emptySet(),emptySet())}
        Files.delete(File(actual,"staging").toPath());check(File(actual,"staging").mkdir())
        val target=File(outside,"synthetic");target.writeBytes(byteArrayOf(1,2,3));val name=id()
        Files.createSymbolicLink(File(actual,"staging/$name").toPath(),target.toPath())
        invalid{inventory.inspect(c,setOf(name),emptySet())}
        assertEquals(3,target.length());assertTrue(Files.isSymbolicLink(File(actual,"staging/$name").toPath()))
    }
    @Test fun symlinkedBaseAndCanonicalEscapeAreRejected() {
        val actual=base();prepare(actual)
        val linked=File(temporary.root,"linked");Files.createSymbolicLink(linked.toPath(),actual.toPath())
        invalid{DocumentFileInventory(linked).inspect(c,emptySet(),emptySet())}
        invalid{DocumentFileInventory(File(actual,"../${actual.name}")).inspect(c,emptySet(),emptySet())}
    }
    @Test fun existingCiphertextVerificationIsReadOnlyAndCreatesNoPlaintextSidecars() {
        val base=base();val root=prepare(base);val inventory=DocumentFileInventory(base)
        val name=id();val documentId=id();val path=File(root,"staging/$name").toPath()
        val marker="SYNTHETIC-DOCUMENT-PLAINTEXT-ONLY-IN-RAM".toByteArray()
        DocumentFileStreams.withNewKey(c){key ->
            // Exclusive no-follow creation belongs only to this isolated test fixture, not product API.
            Files.newOutputStream(path,CREATE_NEW,WRITE,NOFOLLOW_LINKS).use { out ->
                DocumentFileStreams.encrypt(c,documentId,key,ByteArrayInputStream(marker),out)
            }
            val original=Files.readAllBytes(path)
            assertThrows(java.nio.file.FileAlreadyExistsException::class.java){Files.newOutputStream(path,CREATE_NEW,WRITE,NOFOLLOW_LINKS).use{error("Must not overwrite")}}
            val proof=inventory.verifyExisting(c,DocumentFileArea.STAGING,name,setOf(name),emptySet(),documentId,key,marker.size.toLong(),DocumentFormat.TEXT)
            proof.requireCurrent(c)
            assertTrue(original.contentEquals(Files.readAllBytes(path)))
            assertEquals(setOf("staging","files"),root.listFiles()!!.map{it.name}.toSet())
            assertEquals(listOf(name),File(root,"staging").listFiles()!!.map{it.name});assertTrue(File(root,"files").listFiles()!!.isEmpty())
            assertFalse(String(original,Charsets.ISO_8859_1).contains(String(marker,Charsets.US_ASCII)))
        }
    }
    @Test fun maximumSizedCiphertextVerifiesFromDiskWithoutWholeFilePlaintextRam() {
        val base=base();val root=prepare(base);val inventory=DocumentFileInventory(base);val name=id();val documentId=id()
        val size=DocumentRules.MAX_DOCUMENT_BYTES
        val source=object:InputStream(){var remaining=size;override fun read():Int=error("Bulk read");override fun read(b:ByteArray,o:Int,n:Int):Int{
            if(remaining==0L)return -1;val count=minOf(n.toLong(),remaining).toInt();b.fill(120,o,o+count);remaining-=count;return count
        }}
        DocumentFileStreams.withNewKey(c){key ->
            File(root,"staging/$name").outputStream().use { out -> assertEquals(size,DocumentFileStreams.encrypt(c,documentId,key,source,out)) }
            assertEquals(size,inventory.verifyExisting(c,DocumentFileArea.STAGING,name,setOf(name),emptySet(),documentId,key,size,DocumentFormat.TEXT).plaintextSize)
        }
    }
    @Test fun revokeRejectsInventoryWithoutMutation() {
        val base=base();val root=prepare(base);val file=File(root,"files/${id()}");file.writeBytes(byteArrayOf(3));allowed=false
        assertThrows(StorageAccessRevoked::class.java){DocumentFileInventory(base).inspect(c,emptySet(),setOf(file.name))}
        assertTrue(file.exists());assertEquals(1,file.length())
    }
}
