package org.pag.data.prototype

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.*
import org.pag.data.prototype.b4.*
import org.pag.domain.*
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** Complementary real DB/filesystem tests. No actual credential or persistent cleartext keys. */
class B4SafetyTest {
    private val fixture = B4TestFixture()
    @After fun releaseKeys() = fixture.close()
    private fun initialized() { fixture.run(creates = true) { _, _ -> } }
    private fun imported() { initialized(); fixture.run { protocol, _ -> protocol.importSynthetic(B4TestFixture.LENGTH) } }
    private fun interrupted(point: B4Point) {
        val error = runCatching { fixture.run(fault = { if (it == point) throw B4TestFault() }) { protocol, _ ->
            protocol.importSynthetic(B4TestFixture.LENGTH)
        } }.exceptionOrNull()
        assertNotNull(error)
    }
    private fun unchangedBlocked() {
        val before = fixture.ciphertext()
        lateinit var state: B4Snapshot
        fixture.run { _, store -> state = store.snapshot() }
        repeat(2) {
            assertNotNull(runCatching { fixture.run { protocol, _ -> protocol.reconcile() } }.exceptionOrNull())
            assertEquals(before, fixture.ciphertext())
            fixture.run { _, store -> assertEquals(state, store.snapshot()) }
        }
    }
    @Test fun completedImportAndDeleteRestartOnlyAfterMaintenance() {
        imported(); fixture.freshSession()
        assertEquals(SessionState.MAINTENANCE, fixture.session.snapshot().state)
        fixture.run { protocol, store ->
            assertEquals(B4Id.RESTART, protocol.reconcile()); assertEquals(1, store.snapshot().visibleAssets().size)
        }
        fixture.maintenanceDone!!(true)
        assertEquals(SessionState.READY, fixture.session.snapshot().state)
        fixture.freshSession()
        fixture.run { protocol, _ -> assertEquals(B4Id.DELETED, protocol.deleteSynthetic()) }
        fixture.freshSession()
        fixture.run { protocol, store -> assertEquals(B4Id.RESTART, protocol.reconcile()); assertTrue(store.snapshot().assets.isEmpty()) }
    }
    @Test fun unknownEntryPreventsEvenKnownDeleteRecovery() {
        imported()
        assertNotNull(runCatching { fixture.run(fault = { if (it == B4Point.BEFORE_UNLINK) throw B4TestFault() }) { p, _ -> p.deleteSynthetic() } }.exceptionOrNull())
        File(File(fixture.files, "f"), "unknown").writeBytes(byteArrayOf(99))
        unchangedBlocked()
    }
    @Test fun orphanWithoutJobIsNeverDeleted() {
        initialized()
        File(File(fixture.files, "f"), UUID.randomUUID().toString()).writeBytes(byteArrayOf(99))
        unchangedBlocked()
    }
    @Test fun unknownRootEntryBlocksBeforeAnyJobChange() {
        initialized(); File(fixture.files, "unknown").writeBytes(byteArrayOf(99)); unchangedBlocked()
    }
    @Test fun publishedMetadataWithoutFileIsNeverReplaced() {
        imported()
        // Explicit test-only fault injection, never application cleanup.
        File(fixture.files, "f").listFiles()!!.single().let { assertTrue(it.delete()) }
        unchangedBlocked()
    }
    @Test fun knownClosedStageMissingNeverRegenerated() {
        initialized(); interrupted(B4Point.BEFORE_VALIDATION)
        File(fixture.files, "s").listFiles()!!.single().let { assertTrue(it.delete()) }
        unchangedBlocked()
    }
    @Test fun truncatedKnownStageBlocksAndKeepsBytes() {
        initialized(); interrupted(B4Point.BEFORE_VALIDATION)
        RandomAccessFile(File(fixture.files, "s").listFiles()!!.single(), "rw").use { it.setLength(it.length() - 1) }
        unchangedBlocked()
    }
    @Test fun manipulatedPublishedFileBlocks() {
        imported()
        RandomAccessFile(File(fixture.files, "f").listFiles()!!.single(), "rw").use { file ->
            val first = file.read(); file.seek(0); file.write(first xor 1)
        }
        unchangedBlocked()
    }
    @Test fun wrongFileKeyCannotPublishOrRepair() {
        initialized(); interrupted(B4Point.BEFORE_VALIDATION)
        val before = fixture.ciphertext()
        val error = runCatching { fixture.run(wrongFileKey = true) { p, _ -> p.reconcile() } }.exceptionOrNull()
        assertTrue(error is B4Exception && error.id == B4Id.INTEGRITY)
        assertEquals(before, fixture.ciphertext())
        fixture.run { p, _ -> assertEquals(B4Id.IMPORT_RECOVERED, p.reconcile()) }
    }
    @Test fun swappedAssetAadCannotPassFullValidation() {
        imported()
        // A second genuine ciphertext with a different opaque AAD is an explicit test fault.
        fixture.run { p, _ -> p.importSynthetic(B4TestFixture.LENGTH) }
        val files = File(fixture.files, "f").listFiles()!!
        assertEquals(2, files.size)
        val first = files[0].readBytes(); val second = files[1].readBytes()
        files[0].writeBytes(second); files[1].writeBytes(first)
        unchangedBlocked()
    }
    @Test fun missingDatabaseNeverAutomaticallyCreated() {
        assertFalse(fixture.db.exists())
        assertNotNull(runCatching { fixture.run { _, _ -> fail("B4-MISSING-DB-OPENED") } }.exceptionOrNull())
        assertFalse(fixture.db.exists())
    }
    @Test fun existingDatabaseCannotBeCreatedAgain() {
        imported(); val before = fixture.db.readBytes()
        assertNotNull(runCatching { fixture.run(creates = true) { _, _ -> fail("B4-RECREATE") } }.exceptionOrNull())
        assertArrayEquals(before, fixture.db.readBytes())
    }
    @Test fun wrongDbKeyDoesNotReplaceDatabase() {
        imported(); val before = fixture.db.readBytes()
        assertNotNull(runCatching { fixture.run(wrongDbKey = true) { _, _ -> fail("B4-WRONG-KEY-OPEN") } }.exceptionOrNull())
        assertArrayEquals(before, fixture.db.readBytes())
        fixture.run { p, _ -> assertEquals(B4Id.RESTART, p.reconcile()) }
    }
    @Test fun damagedDatabaseDoesNotReplaceFiles() {
        imported(); RandomAccessFile(fixture.db, "rw").use { it.write(ByteArray(32)) }
        val before = fixture.db.readBytes(); val files = fixture.ciphertext()
        assertNotNull(runCatching { fixture.run { _, _ -> fail("B4-DAMAGED-DB-OPEN") } }.exceptionOrNull())
        assertArrayEquals(before, fixture.db.readBytes()); assertEquals(files, fixture.ciphertext())
    }
    @Test fun unknownDatabaseSidecarBlocksWithoutCleanup() {
        imported(); val unknown = File(fixture.db.parentFile, "unknown").also { it.writeBytes(byteArrayOf(99)) }
        val before = fixture.db.readBytes()
        assertNotNull(runCatching { fixture.run { _, _ -> fail("B4-UNKNOWN-DB-INVENTORY") } }.exceptionOrNull())
        assertArrayEquals(before, fixture.db.readBytes()); assertArrayEquals(byteArrayOf(99), unknown.readBytes())
    }
    @Test fun commitRevocationRollsBackOnSameConnection() {
        initialized()
        val error = runCatching { fixture.run(hooks = B2Hooks(beforeCommit = { fixture.time = 100 })) { p, _ ->
            p.importSynthetic(B4TestFixture.LENGTH)
        } }.exceptionOrNull()
        assertNotNull(error); fixture.freshSession()
        fixture.run { p, store -> assertTrue(store.snapshot().jobs.isEmpty()); assertEquals(B4Id.RESTART, p.reconcile()) }
        assertTrue(fixture.ciphertext().isEmpty())
    }
    @Test fun oldDatabaseHandleCannotSurviveReauthentication() {
        initialized(); lateinit var old: B4Store
        assertNotNull(runCatching { fixture.run { _, store -> old = store; fixture.freshSession()
            assertNotNull(runCatching { old.snapshot() }.exceptionOrNull())
        } }.exceptionOrNull())
        assertNotNull(runCatching { old.snapshot() }.exceptionOrNull())
        fixture.run { p, _ -> assertEquals(B4Id.RESTART, p.reconcile()) }
    }
    @Test fun maintenanceExpiryNeverGrantsReady() {
        initialized(); interrupted(B4Point.AFTER_RENAME)
        val error = runCatching { fixture.run(fault = { if (it == B4Point.DURING_VALIDATION) fixture.time = 100 }) { p, _ -> p.reconcile() } }.exceptionOrNull()
        assertNotNull(error); fixture.maintenanceDone!!(true)
        assertEquals(SessionState.LOCKED, fixture.session.snapshot().state)
        fixture.freshSession(); fixture.run { p, _ -> assertEquals(B4Id.IMPORT_RECOVERED, p.reconcile()) }
    }
    @Test fun homeRevokesEveryImportAndDeleteTransition() = foregroundMatrix(false)
    @Test fun deviceLockRevokesEveryImportAndDeleteTransition() = foregroundMatrix(true)
    private fun foregroundMatrix(display: Boolean) {
        B4Point.entries.forEach { point ->
            val f = B4TestFixture()
            try {
                f.run(creates = true) { _, _ -> }
                val deleting = point.ordinal >= B4Point.DELETE_BEFORE_JOB.ordinal
                if (deleting) f.run { p, _ -> p.importSynthetic(B4TestFixture.LENGTH) }
                var reached = false
                val failure = runCatching { f.run(fault = { if (it == point) {
                    reached = true; if (display) f.environment = false else f.session.foreground(false)
                } }) { p, _ -> if (deleting) p.deleteSynthetic() else p.importSynthetic(B4TestFixture.LENGTH) } }.exceptionOrNull()
                assertTrue("B4-BOUNDARY-REACHED", reached); assertNotNull(failure)
                assertFalse(f.context.check(AccessPoint.PUBLICATION)); assertEquals(SessionState.LOCKED, f.session.snapshot().state)
            } finally { f.close() }
        }
    }
}
