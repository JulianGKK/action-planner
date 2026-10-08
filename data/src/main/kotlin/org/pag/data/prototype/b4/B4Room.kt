package org.pag.data.prototype.b4

import android.content.Context
import androidx.room3.*
import kotlinx.coroutines.*
import org.pag.data.prototype.b2.*
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.io.File

@Entity(tableName = "b4_job")
internal data class JobRow(@PrimaryKey val id: String, val asset: String, val staging: String,
    val file: String, val length: Long, val operation: String, val phase: String) {
    fun model() = try { B4Job(id, asset, staging, file, length, B4Operation.valueOf(operation), B4Phase.valueOf(phase)) }
    catch (_: IllegalArgumentException) { throw B4Exception(B4Id.UNKNOWN) }
}
@Entity(tableName = "b4_asset")
internal data class AssetRow(@PrimaryKey val id: String, val file: String, val length: Long, val hidden: Boolean)
@Dao internal interface B4Dao {
    @Query("SELECT * FROM b4_job ORDER BY id") suspend fun jobs(): List<JobRow>
    @Query("SELECT * FROM b4_asset ORDER BY id") suspend fun assets(): List<AssetRow>
    @Insert suspend fun insertJob(row: JobRow)
    @Update suspend fun updateJob(row: JobRow): Int
    @Delete suspend fun deleteJob(row: JobRow): Int
    @Insert suspend fun insertAsset(row: AssetRow)
    @Update suspend fun updateAsset(row: AssetRow): Int
    @Delete suspend fun deleteAsset(row: AssetRow): Int
}
@Database(entities = [JobRow::class, AssetRow::class], version = 1, exportSchema = true)
internal abstract class B4Room : RoomDatabase() { abstract fun jobs(): B4Dao }

/** Uses the already reviewed B2 driver, including same-connection rollback on denied commit. */
internal class B4RoomStore private constructor(internal val db: B4Room, internal val driver: B2Driver,
    private val access: StorageContext, private val lifecycle: B4RoomLifecycle,
    private val parent: Job) : B4Store {
    val background: B4Background get() = lifecycle.background
    suspend fun awaitBackground(): B4Background = lifecycle.awaitBackground()
    private fun check() { lifecycle.requireOpen(); access.requireB4() }
    private fun row(job: B4Job) = JobRow(job.id, job.asset, job.staging, job.file, job.length, job.operation.name, job.phase.name)
    override fun snapshot(): B4Snapshot = runBlocking {
        check()
        val state = B4Snapshot(db.jobs().jobs().map { it.model() }, db.jobs().assets().map { B4Asset(it.id, it.file, it.length, it.hidden) })
        check(); state
    }
    private fun change(work: suspend (B4Dao) -> Unit) = runBlocking {
        check()
        db.withWriteTransaction { access.requireB4(); work(db.jobs()); access.requireB4(AccessPoint.COMMIT) }
        check()
    }
    private suspend fun requireJob(dao: B4Dao, job: B4Job) {
        if (dao.jobs().singleOrNull()?.model() != job) throw B4Exception(B4Id.UNKNOWN)
    }
    override fun reserve(job: B4Job) = change { dao ->
        if (dao.jobs().isNotEmpty()) throw B4Exception(B4Id.UNKNOWN)
        if (job.operation == B4Operation.IMPORT && dao.assets().any { it.id == job.asset || it.file == job.file }) throw B4Exception(B4Id.UNKNOWN)
        if (job.operation == B4Operation.DELETE && dao.assets().none { it.id == job.asset && it.file == job.file && it.length == job.length && !it.hidden }) throw B4Exception(B4Id.UNKNOWN)
        dao.insertJob(row(job))
    }
    override fun advance(job: B4Job, next: B4Phase) = change { dao ->
        requireJob(dao, job); if (dao.updateJob(row(job.next(next))) != 1) throw B4Exception(B4Id.UNKNOWN)
    }
    override fun commitAsset(job: B4Job) = change { dao ->
        requireJob(dao, job)
        val next = job.next(B4Phase.METADATA_COMMITTED)
        dao.insertAsset(AssetRow(job.asset, job.file, job.length, false)); dao.updateJob(row(next))
    }
    override fun hide(job: B4Job) = change { dao ->
        requireJob(dao, job)
        val next = job.next(B4Phase.METADATA_HIDDEN)
        val asset = dao.assets().singleOrNull { it.id == job.asset } ?: throw B4Exception(B4Id.UNKNOWN)
        if (dao.updateAsset(asset.copy(hidden = true)) != 1) throw B4Exception(B4Id.UNKNOWN)
        dao.updateJob(row(next))
    }
    override fun removeAsset(job: B4Job) = change { dao ->
        requireJob(dao, job)
        val next = job.next(B4Phase.METADATA_REMOVED)
        val asset = dao.assets().singleOrNull { it.id == job.asset && it.hidden } ?: throw B4Exception(B4Id.UNKNOWN)
        if (dao.deleteAsset(asset) != 1) throw B4Exception(B4Id.UNKNOWN)
        dao.updateJob(row(next))
    }
    override fun finish(job: B4Job) = change { dao ->
        requireJob(dao, job)
        if (job.phase !in setOf(B4Phase.IMPORT_RESERVED, B4Phase.METADATA_COMMITTED, B4Phase.METADATA_REMOVED)) throw B4Exception(B4Id.UNKNOWN)
        if (dao.deleteJob(row(job)) != 1) throw B4Exception(B4Id.UNKNOWN)
    }
    override fun close() {
        if (lifecycle.beginClose()) {
            // Room.close's barrier finishes running refreshes. Joining the supplied parent also
            // drains cancelled queued children and their handlers before returning to the owner.
            try { db.close() } finally {
                try { runBlocking { parent.cancelAndJoin() } } finally { driver.close() }
            }
        }
    }
    fun resourcesClosed() = driver.resourcesClosed() && parent.isCompleted
    fun requireSuccessfulCompletion() { lifecycle.requireHealthy(); access.requireB4(AccessPoint.PUBLICATION) }
    companion object {
        fun open(app: Context, file: File, key: ByteArray, access: StorageContext, creates: Boolean,
            hooks: B2Hooks = B2Hooks(), roomHooks: B4RoomHooks = B4RoomHooks()): B4RoomStore {
            access.requireB4(AccessPoint.OPERATION)
            if (key.isEmpty()) throw B4Exception(B4Id.STORAGE)
            inventory(file, creates, true)
            if (creates) {
                if (!file.createNewFile()) throw B4Exception(B4Id.UNKNOWN)
            } else {
                B2Native.open(file, key, access, true).use { native ->
                    if (native.version != 1) throw B4Exception(B4Id.UNKNOWN)
                    native.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use {
                        if (it.moveToFirst()) throw B4Exception(B4Id.INTEGRITY)
                    }
                    native.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                        if (!it.moveToFirst() || it.getString(0) != "ok" || it.moveToNext()) throw B4Exception(B4Id.INTEGRITY)
                    }
                }
            }
            val lifecycle = B4RoomLifecycle()
            val guarded = lifecycle.guard(access, roomHooks.beforeCheck)
            val parent = SupervisorJob()
            val handler = lifecycle.handler { parent.cancel() }
            val driver = B2Driver(file, key, guarded, hooks)
            var room: B4Room? = null
            try {
                room = Room.databaseBuilder(app, B4Room::class.java, file.path).setDriver(driver)
                    .setQueryCoroutineContext(roomHooks.dispatcher + parent + handler)
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
                val result = B4RoomStore(room, driver, guarded, lifecycle, parent)
                result.snapshot() // Force encrypted open and generated Room schema validation.
                access.requireB4(); return result
            } catch (error: Throwable) {
                try { room?.close() } finally {
                    try { runBlocking { parent.cancelAndJoin() } } finally { driver.close() }
                }
                throw error
            }
        }
    }
}
/** Injection seam for deterministic tests, never a bypass. Defaults use existing coroutines. */
internal class B4RoomHooks(val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val beforeCheck: () -> Unit = {})
