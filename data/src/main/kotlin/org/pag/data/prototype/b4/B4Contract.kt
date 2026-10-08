package org.pag.data.prototype.b4

import org.pag.domain.AccessPoint
import org.pag.domain.AccessScope
import org.pag.domain.StorageContext

/** Technical synthetic work only. No persisted session grant or generation can authorize restart. */
enum class B4Phase {
    IMPORT_RESERVED, STAGING_WRITING, STAGING_CLOSED, VALIDATED, FILE_PUBLISHED, METADATA_COMMITTED,
    DELETE_RESERVED, METADATA_HIDDEN, FILE_DELETE_PENDING, FILE_REMOVED, METADATA_REMOVED
}
enum class B4Operation { IMPORT, DELETE }
data class B4Job(val id: String, val asset: String, val staging: String, val file: String,
    val length: Long, val operation: B4Operation, val phase: B4Phase)
data class B4Asset(val id: String, val file: String, val length: Long, val hidden: Boolean = false)
data class B4Snapshot(val jobs: List<B4Job>, val assets: List<B4Asset>) {
    /** A reserved deletion already excludes its target; MAINTENANCE exposes no contents. */
    fun visibleAssets(): List<B4Asset> = assets.filter { asset ->
        !asset.hidden && jobs.none { it.asset == asset.id && it.operation == B4Operation.DELETE }
    }
}
enum class B4Id(val testId: String) {
    IMPORTED("B4-IMPORT-COMMITTED"), IMPORT_RECOVERED("B4-IMPORT-RECOVERED"),
    ABORTED("B4-IMPORT-NOT-PUBLISHED"), DELETED("B4-DELETE-COMMITTED"),
    DELETE_RECOVERED("B4-DELETE-RECOVERED"), RESTART("B4-RESTART-SUCCEEDED"),
    UNKNOWN("B4-UNKNOWN-STATE"), INTEGRITY("B4-INTEGRITY-FAILED"),
    EXPIRED("B4-SESSION-EXPIRED"), STALE("B4-STALE-GENERATION"), STORAGE("B4-STORAGE-BLOCKED"),
    BACKGROUND("B4-BACKGROUND-FAULT")
}
class B4Exception(val id: B4Id) : RuntimeException(id.testId)
enum class B4Point {
    IMPORT_BEFORE_JOB, IMPORT_AFTER_JOB, BEFORE_STAGING_CREATE, DURING_WRITE, AFTER_STREAM_CLOSE,
    BEFORE_VALIDATION, DURING_VALIDATION, AFTER_VALIDATION, BEFORE_RENAME, AFTER_RENAME,
    BEFORE_METADATA_COMMIT, AFTER_METADATA_COMMIT, IMPORT_BEFORE_COMPLETION,
    DELETE_BEFORE_JOB, DELETE_AFTER_JOB, BEFORE_HIDE, AFTER_HIDE, BEFORE_UNLINK, AFTER_UNLINK,
    BEFORE_METADATA_REMOVE, AFTER_METADATA_REMOVE, DELETE_BEFORE_COMPLETION
}
internal fun StorageContext.requireB4(point: AccessPoint = AccessPoint.STEP) {
    if (scope != AccessScope.MAINTENANCE || !check(point)) throw B4Exception(B4Id.EXPIRED)
}
internal fun B4Job.next(next: B4Phase): B4Job {
    val path = if (operation == B4Operation.IMPORT) B4Phase.entries.take(6) else B4Phase.entries.drop(6)
    val index = path.indexOf(phase)
    if (index < 0 || path.getOrNull(index + 1) != next) throw B4Exception(B4Id.UNKNOWN)
    return copy(phase = next)
}
/** Implementations commit each change as one structured transaction; never include filesystem I/O. */
internal interface B4Store : AutoCloseable {
    fun snapshot(): B4Snapshot
    fun reserve(job: B4Job)
    fun advance(job: B4Job, next: B4Phase)
    fun commitAsset(job: B4Job)
    fun hide(job: B4Job)
    fun removeAsset(job: B4Job)
    fun finish(job: B4Job)
}
