package org.pag.data.product

import android.content.pm.ApplicationInfo
import org.pag.data.prototype.b2.B2Exception
import org.pag.data.prototype.b2.B2Failure
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.data.prototype.b4.B4DriverBackgroundFault
import org.pag.data.prototype.b4.B4Exception
import org.pag.data.prototype.b4.B4Id
import java.io.IOException

/** Closed vocabulary only: never accept exception messages, paths, queries or user data. */
internal enum class MaintenancePhase {
    M01_AUTHORIZED_KEY, M02_DB_PREFLIGHT, M03_DB_DOCUMENT_SNAPSHOT,
    M04_FILE_INVENTORY, M05_JOB_RECOVERY, M06_FINAL_INVENTORY, M07_READY_OWNER
}
internal enum class MaintenanceFailureCode {
    SESSION_REVOKED, STORAGE_REJECTED, ROOM_BACKGROUND, DB_INVENTORY,
    DB_UNREADABLE, DB_INTEGRITY, DB_SCHEMA, DB_MIGRATION, DB_CONSTRAINT,
    IO, SPACE, SECURITY, UNEXPECTED, KEY_OPERATION_INCOMPLETE
}
internal fun maintenanceProbeEnabled(applicationFlags: Int) =
    applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** One attempt, worker-local; no key/context/Throwable retained. Diagnostic failure grants nothing. */
internal class MaintenanceProbe(private val enabled: Boolean, private val emit: (String) -> Unit) {
    companion object { const val TAG = "PaGMaintenanceProbe" }
    private var phase = MaintenancePhase.M01_AUTHORIZED_KEY
    private var reported = false
    fun enter(next: MaintenancePhase) { phase = next }
    fun failed(category: MaintenanceFailureCode) {
        if (!enabled || reported) return
        reported = true
        try { emit("${phase.name} ${category.name}") } catch (_: Exception) { /* observation only */ }
    }
}
internal fun maintenanceFailureCode(error: Exception): MaintenanceFailureCode = when (error) {
    is StorageAccessRevoked -> MaintenanceFailureCode.SESSION_REVOKED
    is B4DriverBackgroundFault -> MaintenanceFailureCode.ROOM_BACKGROUND
    is B4Exception -> if (error.id == B4Id.BACKGROUND) MaintenanceFailureCode.ROOM_BACKGROUND else MaintenanceFailureCode.STORAGE_REJECTED
    is ProductStorageFailure -> MaintenanceFailureCode.STORAGE_REJECTED
    is B2Exception -> when (error.failure) {
        B2Failure.SESSION -> MaintenanceFailureCode.SESSION_REVOKED
        B2Failure.INVENTORY, B2Failure.MISSING, B2Failure.NOT_EMPTY -> MaintenanceFailureCode.DB_INVENTORY
        B2Failure.KEY_MISSING, B2Failure.KEY_OR_UNREADABLE -> MaintenanceFailureCode.DB_UNREADABLE
        B2Failure.INTEGRITY -> MaintenanceFailureCode.DB_INTEGRITY
        B2Failure.SCHEMA -> MaintenanceFailureCode.DB_SCHEMA
        B2Failure.MIGRATION -> MaintenanceFailureCode.DB_MIGRATION
        B2Failure.CONSTRAINT -> MaintenanceFailureCode.DB_CONSTRAINT
        B2Failure.IO -> MaintenanceFailureCode.IO
        B2Failure.SPACE -> MaintenanceFailureCode.SPACE
        B2Failure.CLOSED, B2Failure.BLOCKED -> MaintenanceFailureCode.STORAGE_REJECTED
    }
    is SecurityException -> MaintenanceFailureCode.SECURITY
    is IOException -> MaintenanceFailureCode.IO
    else -> MaintenanceFailureCode.UNEXPECTED
}
