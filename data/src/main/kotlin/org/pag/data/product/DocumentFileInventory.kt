package org.pag.data.product

import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import org.pag.domain.DocumentFormat
import java.io.File
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes

internal enum class DocumentFileArea { STAGING, FILES }
internal data class DocumentNamespaceSnapshot(val rootPresent: Boolean, val staging: Set<String>, val files: Set<String>)

/** Read-only namespace boundary. No mkdir/create/write/move/unlink, including at missing root.
 * Expected names come from an already authorized, consistent DB snapshot in P10b2c.
 */
internal class DocumentFileInventory(noBackupFilesDir: File) {
    private val base = noBackupFilesDir.toPath().toAbsolutePath()
    private val root = base.resolve("pag-documents-v1")

    private fun checked(c: StorageContext, generation: Long, path: Path, directory: Boolean): BasicFileAttributes {
        documentFileGate(c,generation)
        if (path.normalize() != path || path.toFile().canonicalFile.toPath() != path) throw ProductStorageFailure()
        documentFileGate(c,generation)
        val attributes = Files.readAttributes(path,BasicFileAttributes::class.java,NOFOLLOW_LINKS)
        documentFileGate(c,generation)
        if (attributes.isSymbolicLink || (if (directory) !attributes.isDirectory else !attributes.isRegularFile)) throw ProductStorageFailure()
        return attributes
    }
    private fun unchanged(c: StorageContext, generation: Long, path: Path, directory: Boolean, before: BasicFileAttributes) {
        val after = checked(c,generation,path,directory)
        if (before.fileKey() != after.fileKey() || before.size() != after.size() || before.lastModifiedTime() != after.lastModifiedTime()) throw ProductStorageFailure()
    }
    private fun knownNames(staging: Set<String>, files: Set<String>) {
        if ((staging + files).any { !DocumentStorageRules.opaque(it) } || staging.intersect(files).isNotEmpty()) throw ProductStorageFailure()
    }
    fun inspect(c: StorageContext, knownStaging: Set<String>, knownFiles: Set<String>): DocumentNamespaceSnapshot = inspectInternal(c,knownStaging,knownFiles,false)
    internal fun inspectRecovery(c:StorageContext, knownStaging:Set<String>, knownFiles:Set<String>):DocumentNamespaceSnapshot {
        if(c.scope!=org.pag.domain.AccessScope.MAINTENANCE)throw ProductStorageFailure()
        val g=c.generation
        val result=inspectInternal(c,knownStaging,knownFiles,true)
        val identities=mutableSetOf<Any>()
        if(result.rootPresent) for((area,names) in listOf("staging" to result.staging,"files" to result.files)) {
            for(name in names) {
                val attributes=checked(c,g,root.resolve(area).resolve(name),false)
                val identity=attributes.fileKey()?:throw ProductStorageFailure()
                if(!identities.add(identity))throw ProductStorageFailure()
            }
        }
        documentFileGate(c,g)
        return result
    }
    private fun inspectInternal(c: StorageContext, knownStaging: Set<String>, knownFiles: Set<String>, allowMissingRoot:Boolean): DocumentNamespaceSnapshot = documentFileBoundary {
        val generation = c.generation
        documentFileGate(c,generation,AccessPoint.OPERATION)
        val expectedStaging = knownStaging.toSet(); val expectedFiles = knownFiles.toSet()
        knownNames(expectedStaging,expectedFiles)
        checked(c,generation,base,true)
        val rootAttributes = try { checked(c,generation,root,true) } catch (_: NoSuchFileException) { null }
        if (rootAttributes == null) {
            if (!allowMissingRoot && (expectedStaging.isNotEmpty() || expectedFiles.isNotEmpty())) throw ProductStorageFailure()
            documentFileGate(c,generation)
            DocumentNamespaceSnapshot(false,emptySet(),emptySet())
        } else {
            val children = mutableSetOf<String>()
            Files.newDirectoryStream(root).use { entries -> entries.forEach { path ->
                documentFileGate(c,generation)
                if (path.fileName.toString() !in setOf("staging","files")) throw ProductStorageFailure()
                checked(c,generation,path,true); children += path.fileName.toString()
                documentFileGate(c,generation)
            } }
            if (children != setOf("staging","files")) throw ProductStorageFailure()
            val staging = entries(c,generation,root.resolve("staging"),expectedStaging)
            val files = entries(c,generation,root.resolve("files"),expectedFiles)
            unchanged(c,generation,root,true,rootAttributes)
            documentFileGate(c,generation)
            DocumentNamespaceSnapshot(true,staging,files)
        }
    }
    private fun entries(c: StorageContext, generation: Long, directory: Path, known: Set<String>): Set<String> {
        documentFileGate(c,generation)
        val attributes = checked(c,generation,directory,true)
        val names = mutableSetOf<String>()
        Files.newDirectoryStream(directory).use { entries -> entries.forEach { path ->
            documentFileGate(c,generation)
            val name = path.fileName.toString()
            if (!DocumentStorageRules.opaque(name) || name !in known || !names.add(name)) throw ProductStorageFailure()
            checked(c,generation,path,false)
            documentFileGate(c,generation)
        } }
        unchanged(c,generation,directory,true,attributes)
        documentFileGate(c,generation)
        return names.toSet()
    }

    /** Opens existing ciphertext read-only with NOFOLLOW; no path or plaintext escapes. */
    fun verifyExisting(c: StorageContext, area: DocumentFileArea, name: String, knownStaging: Set<String>,
        knownFiles: Set<String>, documentId: String, key: ByteArray, expectedSize: Long,
        format: DocumentFormat): DocumentVerification = documentFileBoundary {
        val generation = c.generation
        val ticket = DocumentNamespaceLane.ticket()
        DocumentNamespaceLane.exclusive(ticket) {
            documentFileGate(c,generation,AccessPoint.OPERATION)
            val staging = knownStaging.toSet(); val files = knownFiles.toSet()
            val before = inspect(c,staging,files)
            val available = if (area == DocumentFileArea.STAGING) before.staging else before.files
            if (!DocumentStorageRules.opaque(name) || name !in available) throw ProductStorageFailure()
            val path = root.resolve(if (area == DocumentFileArea.STAGING) "staging" else "files").resolve(name)
            val attributes = checked(c,generation,path,false)
            documentFileGate(c,generation)
            val result = Files.newByteChannel(path,setOf(READ,NOFOLLOW_LINKS)).use { channel ->
                unchanged(c,generation,path,false,attributes)
                documentFileGate(c,generation)
                DocumentFileStreams.verify(c,documentId,key,Channels.newInputStream(channel),expectedSize,format)
            }
            documentFileGate(c,generation)
            unchanged(c,generation,path,false,attributes)
            if (inspect(c,staging,files) != before) throw ProductStorageFailure()
            result.requireCurrent(c)
            result
        }
    }
}
