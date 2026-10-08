package org.pag.data.prototype.b4

import android.content.Context
import org.pag.data.prototype.b2.B2Hooks
import org.pag.domain.StorageContext
import java.io.File

/** Prototype seam, not a product key manager. Consumer must be synchronous and non-retaining. */
internal fun interface B4DbKeyConsumer { fun consume(work: (ByteArray) -> Unit) }

/** Exactly one scoped Room operation per open. No database is held over file I/O or a prompt. */
internal class B4StepStore(private val app: Context, private val file: File,
    private val access: StorageContext, private val keys: B4DbKeyConsumer,
    private val roomHooks: B4RoomHooks = B4RoomHooks(), private val driverHooks: B2Hooks = B2Hooks()) : B4Store {
    private var closed = false
    private var faulted = false
    private fun <T> step(creates: Boolean = false, work: (B4RoomStore) -> T): T {
        if (closed || faulted) throw B4Exception(B4Id.STORAGE)
        access.requireB4()
        var outcome: Result<T>? = null
        try {
            keys.consume { bytes ->
                if (outcome != null) throw B4Exception(B4Id.STORAGE)
                val store = B4RoomStore.open(app, file, bytes, access, creates, driverHooks, roomHooks)
                val result = try { work(store) } finally { store.close() }
                // No success escapes a late background fault, revoked session, or unclosed DB.
                store.requireSuccessfulCompletion()
                if (!store.resourcesClosed()) throw B4Exception(B4Id.STORAGE)
                outcome = Result.success(result)
            }
            return outcome?.getOrThrow() ?: throw B4Exception(B4Id.STORAGE)
        } catch (error: Throwable) { faulted = true; throw error }
    }
    /** Separate explicitly triggered EMPTY-bootstrap only. Never invoked by an existing open. */
    fun initializeEmpty() { step(creates = true) { it.snapshot() } }
    override fun snapshot() = step { it.snapshot() }
    override fun reserve(job: B4Job) = step { it.reserve(job) }
    override fun advance(job: B4Job, next: B4Phase) = step { it.advance(job, next) }
    override fun commitAsset(job: B4Job) = step { it.commitAsset(job) }
    override fun hide(job: B4Job) = step { it.hide(job) }
    override fun removeAsset(job: B4Job) = step { it.removeAsset(job) }
    override fun finish(job: B4Job) = step { it.finish(job) }
    override fun close() { closed = true }
}
