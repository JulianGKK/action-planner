package org.pag.data.prototype.b4

import org.pag.data.prototype.b3.*
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

/** One serialized owner per isolated namespace. No file/DB work holds the session monitor. */
internal class B4Protocol(private val store: B4Store, private val root: File,
    private val streams: B3Streams, private val access: StorageContext,
    private val fault: (B4Point) -> Unit = {}) {
    private val staging = File(root, "s")
    private val published = File(root, "f")
    private fun at(point: B4Point) { access.requireB4(); fault(point); access.requireB4() }
    private fun entries(directory: File): List<File> {
        access.requireB4()
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath()) || directory.absoluteFile != directory.canonicalFile)
            throw B4Exception(B4Id.UNKNOWN)
        return (directory.listFiles() ?: throw B4Exception(B4Id.UNKNOWN)).toList().also { files ->
            if (files.any { !it.isFile || Files.isSymbolicLink(it.toPath()) || it.absoluteFile != it.canonicalFile || !B3Inventory.opaque(it.name) })
                throw B4Exception(B4Id.UNKNOWN)
        }
    }
    private fun checkedSnapshot(): B4Snapshot {
        access.requireB4()
        if (!root.isDirectory || Files.isSymbolicLink(root.toPath()) ||
            root.absoluteFile != root.canonicalFile ||
            root.listFiles()?.map { it.name }?.toSet() != setOf("s", "f"))
            throw B4Exception(B4Id.UNKNOWN)
        val state = store.snapshot()
        if (state.jobs.size > 1 || state.assets.map { it.id }.toSet().size != state.assets.size ||
            state.assets.map { it.file }.toSet().size != state.assets.size) throw B4Exception(B4Id.UNKNOWN)
        state.assets.forEach { if (!B3Inventory.opaque(it.id) || !B3Inventory.opaque(it.file) || it.length !in 0..B3StorageGate.MAX_SYNTHETIC_LENGTH) throw B4Exception(B4Id.UNKNOWN) }
        state.jobs.forEach { job ->
            if (listOf(job.id, job.asset, job.staging, job.file).any { !B3Inventory.opaque(it) } ||
                job.length !in 0..B3StorageGate.MAX_SYNTHETIC_LENGTH ||
                (job.operation == B4Operation.IMPORT) != (job.phase.ordinal < 6)) throw B4Exception(B4Id.UNKNOWN)
            val asset = state.assets.singleOrNull { it.id == job.asset }
            val needsAsset = job.phase == B4Phase.METADATA_COMMITTED ||
                (job.operation == B4Operation.DELETE && job.phase != B4Phase.METADATA_REMOVED)
            if ((asset != null) != needsAsset || (asset != null && (asset.file != job.file || asset.length != job.length))) throw B4Exception(B4Id.UNKNOWN)
            if (asset != null && job.operation == B4Operation.DELETE && job.phase != B4Phase.DELETE_RESERVED && !asset.hidden) throw B4Exception(B4Id.UNKNOWN)
        }
        val stageFiles = entries(staging)
        val finalFiles = entries(published)
        if (stageFiles.any { f -> state.jobs.none { it.operation == B4Operation.IMPORT && it.staging == f.name && it.phase.ordinal in 1..3 } } ||
            finalFiles.any { f -> state.assets.none { it.file == f.name } && state.jobs.none { it.file == f.name &&
                (it.operation == B4Operation.DELETE || it.phase.ordinal in 3..5) } }) throw B4Exception(B4Id.UNKNOWN)
        state.assets.forEach { asset ->
            val deletion = state.jobs.singleOrNull { it.asset == asset.id && it.operation == B4Operation.DELETE }
            if (asset.hidden && deletion == null) throw B4Exception(B4Id.UNKNOWN)
            if (!File(published, asset.file).isFile && deletion?.phase !in setOf(B4Phase.FILE_DELETE_PENDING, B4Phase.FILE_REMOVED)) throw B4Exception(B4Id.UNKNOWN)
        }
        return state
    }
    private fun verify(file: File, job: B4Job): B3Streams.Verified {
        access.requireB4()
        if (!file.isFile) throw B4Exception(B4Id.UNKNOWN)
        return try { streams.validate({ FileInputStream(file) }, job.asset.toByteArray(Charsets.US_ASCII), job.length) }
        catch (error: B3Exception) { throw B4Exception(if (error.id == B3Id.EXPIRED) B4Id.EXPIRED else B4Id.INTEGRITY) }
    }
    fun importSynthetic(length: Long): B4Id {
        if (length !in 0..B3StorageGate.MAX_SYNTHETIC_LENGTH || checkedSnapshot().jobs.isNotEmpty()) throw B4Exception(B4Id.UNKNOWN)
        val job = B4Job(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), length, B4Operation.IMPORT, B4Phase.IMPORT_RESERVED)
        at(B4Point.IMPORT_BEFORE_JOB); store.reserve(job); at(B4Point.IMPORT_AFTER_JOB)
        store.advance(job, B4Phase.STAGING_WRITING)
        val writing = job.next(B4Phase.STAGING_WRITING)
        at(B4Point.BEFORE_STAGING_CREATE)
        val file = File(staging, job.staging)
        if (!file.createNewFile()) throw B4Exception(B4Id.UNKNOWN)
        val source = object : java.io.InputStream() {
            private val delegate = B3Source(length)
            override fun read(): Int { at(B4Point.DURING_WRITE); return delegate.read() }
            override fun read(bytes: ByteArray, offset: Int, count: Int): Int { at(B4Point.DURING_WRITE); return delegate.read(bytes, offset, count) }
        }
        Synced(file).use { streams.encrypt(source, it, job.asset.toByteArray(Charsets.US_ASCII)) }
        at(B4Point.AFTER_STREAM_CLOSE); store.advance(writing, B4Phase.STAGING_CLOSED)
        finishImport(writing.next(B4Phase.STAGING_CLOSED))
        access.requireB4(AccessPoint.PUBLICATION); return B4Id.IMPORTED
    }
    private fun finishImport(initial: B4Job) {
        var job = initial
        if (job.phase == B4Phase.STAGING_WRITING) {
            // No source re-read or new ciphertext. A complete existing stream alone can progress.
            verify(File(staging, job.staging), job)
            store.advance(job, B4Phase.STAGING_CLOSED); job = job.next(B4Phase.STAGING_CLOSED)
        }
        if (job.phase == B4Phase.STAGING_CLOSED) {
            at(B4Point.BEFORE_VALIDATION)
            verify(File(staging, job.staging), job)
            at(B4Point.AFTER_VALIDATION); store.advance(job, B4Phase.VALIDATED); job = job.next(B4Phase.VALIDATED)
        }
        if (job.phase == B4Phase.VALIDATED) {
            val source = File(staging, job.staging); val target = File(published, job.file)
            if (source.exists() == target.exists()) throw B4Exception(B4Id.UNKNOWN)
            val proof = verify(if (source.exists()) source else target, job)
            at(B4Point.BEFORE_RENAME)
            streams.publish(proof) {
                access.requireB4(AccessPoint.COMMIT)
                if (source.exists()) Files.move(source.toPath(), target.toPath()) // No REPLACE_EXISTING.
            }
            at(B4Point.AFTER_RENAME); store.advance(job, B4Phase.FILE_PUBLISHED); job = job.next(B4Phase.FILE_PUBLISHED)
        }
        if (job.phase == B4Phase.FILE_PUBLISHED) {
            verify(File(published, job.file), job)
            at(B4Point.BEFORE_METADATA_COMMIT); store.commitAsset(job); at(B4Point.AFTER_METADATA_COMMIT)
            job = job.next(B4Phase.METADATA_COMMITTED)
        }
        if (job.phase != B4Phase.METADATA_COMMITTED) throw B4Exception(B4Id.UNKNOWN)
        verify(File(published, job.file), job)
        at(B4Point.IMPORT_BEFORE_COMPLETION); store.finish(job)
    }
    fun deleteSynthetic(): B4Id {
        val state = checkedSnapshot()
        if (state.jobs.isNotEmpty()) throw B4Exception(B4Id.UNKNOWN)
        val asset = state.assets.singleOrNull() ?: throw B4Exception(B4Id.UNKNOWN)
        val job = B4Job(UUID.randomUUID().toString(), asset.id, UUID.randomUUID().toString(), asset.file,
            asset.length, B4Operation.DELETE, B4Phase.DELETE_RESERVED)
        // Validate before reserving deletion; the job contains references, never content.
        verify(File(published, job.file), job)
        at(B4Point.DELETE_BEFORE_JOB); store.reserve(job); at(B4Point.DELETE_AFTER_JOB)
        finishDelete(job); access.requireB4(AccessPoint.PUBLICATION); return B4Id.DELETED
    }
    private fun finishDelete(initial: B4Job) {
        var job = initial
        if (job.phase == B4Phase.DELETE_RESERVED) {
            at(B4Point.BEFORE_HIDE); store.hide(job); at(B4Point.AFTER_HIDE); job = job.next(B4Phase.METADATA_HIDDEN)
        }
        if (job.phase == B4Phase.METADATA_HIDDEN) {
            // Missing here is ambiguous: no unlink was authorized yet.
            verify(File(published, job.file), job)
            store.advance(job, B4Phase.FILE_DELETE_PENDING); job = job.next(B4Phase.FILE_DELETE_PENDING)
        }
        if (job.phase == B4Phase.FILE_DELETE_PENDING) {
            val file = File(published, job.file)
            if (file.exists()) verify(file, job)
            at(B4Point.BEFORE_UNLINK); access.requireB4(AccessPoint.COMMIT)
            Files.deleteIfExists(file.toPath()) // Exactly this known, previously authorized target.
            at(B4Point.AFTER_UNLINK); store.advance(job, B4Phase.FILE_REMOVED); job = job.next(B4Phase.FILE_REMOVED)
        }
        if (job.phase == B4Phase.FILE_REMOVED) {
            if (File(published, job.file).exists()) throw B4Exception(B4Id.UNKNOWN)
            at(B4Point.BEFORE_METADATA_REMOVE); store.removeAsset(job); at(B4Point.AFTER_METADATA_REMOVE)
            job = job.next(B4Phase.METADATA_REMOVED)
        }
        if (job.phase != B4Phase.METADATA_REMOVED || File(published, job.file).exists()) throw B4Exception(B4Id.UNKNOWN)
        at(B4Point.DELETE_BEFORE_COMPLETION); store.finish(job)
    }
    fun reconcile(): B4Id {
        val state = checkedSnapshot() // Inspect the entire known file inventory before any mutation.
        val job = state.jobs.singleOrNull()
        val result = when {
            job == null -> B4Id.RESTART
            job.operation == B4Operation.DELETE -> { finishDelete(job); B4Id.DELETE_RECOVERED }
            job.phase == B4Phase.IMPORT_RESERVED -> {
                // No write was permitted by this phase. Abort explicitly; never report import success.
                if (File(staging, job.staging).exists() || File(published, job.file).exists()) throw B4Exception(B4Id.UNKNOWN)
                access.requireB4(AccessPoint.COMMIT); store.finish(job); B4Id.ABORTED
            }
            else -> { finishImport(job); B4Id.IMPORT_RECOVERED }
        }
        val final = checkedSnapshot()
        if (final.jobs.isNotEmpty()) throw B4Exception(B4Id.UNKNOWN)
        final.assets.forEach { asset -> verify(File(published, asset.file), B4Job(asset.id, asset.id,
            asset.id, asset.file, asset.length, B4Operation.IMPORT, B4Phase.METADATA_COMMITTED)) }
        access.requireB4(AccessPoint.PUBLICATION); return result
    }
    companion object {
        fun validationHook(access: StorageContext, fault: (B4Point) -> Unit): (B3Point) -> Unit = { point ->
            if (point == B3Point.VALIDATION) { access.requireB4(); fault(B4Point.DURING_VALIDATION); access.requireB4() }
        }
    }
}
private class Synced(file: File) : FileOutputStream(file) {
    private var finished = false
    override fun close() { if (!finished) { finished = true; try { fd.sync() } finally { super.close() } } }
}
