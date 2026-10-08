package org.pag.data.prototype.b4

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.AccessPoint
import org.pag.domain.AccessScope
import org.pag.domain.StorageContext

/** Contract tests only; these are not evidence for Room/filesystem restart behaviour. */
class B4ContractTest {
    private fun job(operation: B4Operation, phase: B4Phase) =
        B4Job("synthetic-job", "synthetic-asset", "synthetic-stage", "synthetic-file", 1, operation, phase)

    @Test fun onlyAdjacentImportTransitionsAreAccepted() {
        val path = B4Phase.entries.take(6)
        for (phase in path) for (next in B4Phase.entries) {
            val current = job(B4Operation.IMPORT, phase)
            if (path.getOrNull(path.indexOf(phase) + 1) == next)
                assertEquals(current.copy(phase = next), current.next(next))
            else denied { current.next(next) }
        }
    }

    @Test fun onlyAdjacentDeleteTransitionsAreAccepted() {
        val path = B4Phase.entries.drop(6)
        for (phase in path) for (next in B4Phase.entries) {
            val current = job(B4Operation.DELETE, phase)
            if (path.getOrNull(path.indexOf(phase) + 1) == next)
                assertEquals(current.copy(phase = next), current.next(next))
            else denied { current.next(next) }
        }
    }

    @Test fun wrongOperationPhaseCannotAdvance() {
        denied { job(B4Operation.IMPORT, B4Phase.DELETE_RESERVED).next(B4Phase.METADATA_HIDDEN) }
        denied { job(B4Operation.DELETE, B4Phase.IMPORT_RESERVED).next(B4Phase.STAGING_WRITING) }
    }

    @Test fun reservedDeletionAlreadyExcludesAsset() {
        val asset = B4Asset("synthetic-asset", "synthetic-file", 1)
        assertEquals(listOf(asset), B4Snapshot(emptyList(), listOf(asset)).visibleAssets())
        assertTrue(B4Snapshot(listOf(job(B4Operation.DELETE, B4Phase.DELETE_RESERVED)), listOf(asset)).visibleAssets().isEmpty())
        assertTrue(B4Snapshot(emptyList(), listOf(asset.copy(hidden = true))).visibleAssets().isEmpty())
    }

    @Test fun maintenanceValidityIsRecheckedAtEveryAccessPoint() {
        var valid = true
        val context = object : StorageContext {
            override val scope = AccessScope.MAINTENANCE
            override fun check(point: AccessPoint) = valid
            override fun publish(result: () -> Unit): Boolean {
                if (!valid) return false
                result(); return true
            }
        }
        AccessPoint.entries.forEach { context.requireB4(it) }
        valid = false
        AccessPoint.entries.forEach { point ->
            try { context.requireB4(point); fail("B4-REVOKED-CONTEXT") }
            catch (error: B4Exception) { assertEquals(B4Id.EXPIRED, error.id) }
        }
    }

    @Test fun readyPermitCannotAuthorizeMaintenance() {
        val context = object : StorageContext {
            override val scope = AccessScope.READY
            override fun check(point: AccessPoint) = true
            override fun publish(result: () -> Unit) = false
        }
        try { context.requireB4(); fail("B4-WRONG-SCOPE") }
        catch (error: B4Exception) { assertEquals(B4Id.EXPIRED, error.id) }
    }

    private fun denied(work: () -> Unit) {
        try { work(); fail("B4-INVALID-TRANSITION") }
        catch (error: B4Exception) { assertEquals(B4Id.UNKNOWN, error.id) }
    }
}
