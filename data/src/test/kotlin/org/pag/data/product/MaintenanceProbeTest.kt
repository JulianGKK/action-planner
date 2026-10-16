package org.pag.data.product

import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.pag.data.prototype.b2.*
import org.pag.data.prototype.b4.*
import org.pag.domain.*
import java.io.File
import java.io.IOException
import java.util.UUID

/** Isolated synthetic namespace, never the device/product DB. No maintenance decisions replaced. */
class MaintenanceProbeTest {
    @get:Rule val temp=TemporaryFolder()
    private val lines=mutableListOf<String>()
    private val probe=MaintenanceProbe(true,lines::add)
    private val c=object:StorageContext {
        override val generation=1L
        override val scope=AccessScope.MAINTENANCE
        override fun check(point:AccessPoint)=true
        override fun publish(result:()->Unit):Boolean=error("No publication")
    }
    private var reads=0
    private var onInventory:(Int)->DocumentRecoverySnapshot={DocumentRecoverySnapshot(emptySet(),emptyList())}
    private var onAbort:()->Unit={}
    private val db=object:DocumentReconciliationDatabase {
        override fun inventory(c:StorageContext)=onInventory(++reads)
        override fun abort(c:StorageContext,expected:DocumentRecoveryJob)=onAbort()
        override fun publication(c:StorageContext,id:String,phase:String,expected:DocumentJobRow?):DocumentPublicationSnapshot=error("No publication")
        override fun mark(c:StorageContext,expected:DocumentJobRow)=error("No mutation")
        override fun commit(c:StorageContext,expected:DocumentJobRow)=error("No mutation")
    }
    private val deletion=object:DocumentDeleteDatabase {
        override fun reserve(c:StorageContext,actionId:String,expected:DocumentAsset,jobId:String):DocumentDeleteSnapshot=error("No delete")
        override fun snapshot(c:StorageContext,id:String,expected:DocumentDeleteSnapshot?):DocumentDeleteSnapshot=error("No delete")
        override fun detach(c:StorageContext,expected:DocumentDeleteSnapshot)=error("No delete")
        override fun finish(c:StorageContext,expected:DocumentDeleteSnapshot)=error("No delete")
    }
    private fun run(preflight:()->Unit={}) = DocumentReconciliation(db,deletion,temp.root,observePhase=probe::enter).run(c,preflight)
    private fun failure(expected:String,work:()->Unit) {
        val error=assertThrows(Exception::class.java,work)
        probe.failed(maintenanceFailureCode(error))
        probe.failed(MaintenanceFailureCode.UNEXPECTED)
        assertEquals(listOf(expected),lines)
    }
    @Test fun nonDebuggableBuildNeverEmitsAndDebuggableFlagIsExplicit() {
        assertFalse(maintenanceProbeEnabled(0));assertFalse(maintenanceProbeEnabled(ApplicationInfo.FLAG_HAS_CODE))
        assertTrue(maintenanceProbeEnabled(ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_HAS_CODE))
        val disabled=MaintenanceProbe(false){error("No non-debug logging")}
        MaintenancePhase.entries.forEach { disabled.enter(it);disabled.failed(MaintenanceFailureCode.UNEXPECTED) }
    }
    @Test fun successfulEmptyInventoryAndMissingRootEmitNothing() { run();assertEquals(2,reads);assertTrue(lines.isEmpty());assertTrue(temp.root.listFiles()!!.isEmpty()) }
    @Test fun authorizedKeyFailureUsesInitialPhaseAndExactlyOneLine() {
        probe.failed(MaintenanceFailureCode.KEY_OPERATION_INCOMPLETE);probe.failed(MaintenanceFailureCode.IO)
        assertEquals(listOf("M01_AUTHORIZED_KEY KEY_OPERATION_INCOMPLETE"),lines)
    }
    @Test fun preflightFailureStopsBeforeDocumentOrFileAccess() {
        failure("M02_DB_PREFLIGHT DB_SCHEMA") { run {throw B2Exception(B2Failure.SCHEMA)} }
        assertEquals(0,reads);assertTrue(temp.root.listFiles()!!.isEmpty())
    }
    @Test fun documentSnapshotFailureIsDistinguishedFromFileInventory() {
        onInventory={throw B4Exception(B4Id.BACKGROUND)}
        failure("M03_DB_DOCUMENT_SNAPSHOT ROOM_BACKGROUND"){run()}
        assertTrue(temp.root.listFiles()!!.isEmpty())
    }
    @Test fun unknownFileInventoryBlocksWithoutCleanupAndOnlyEmitsCode() {
        val root=File(temp.root,"pag-documents-v1");assertTrue(root.mkdir())
        val unknown=File(root,"synthetic-private-name");unknown.writeText("synthetic-private-content")
        failure("M04_FILE_INVENTORY STORAGE_REJECTED"){run()}
        assertTrue(unknown.exists());assertEquals("synthetic-private-content",unknown.readText())
    }
    @Test fun jobRecoveryFailureIsNotOverwrittenByNestedInventoryPhase() {
        fun id()=UUID.randomUUID().toString()
        val job=DocumentRecoveryJob(id(),"synthetic",id(),"IMPORT","IMPORT_RESERVED",id(),id())
        onInventory={DocumentRecoverySnapshot(emptySet(),listOf(job))}
        onAbort={throw IOException("synthetic secret path and source")}
        failure("M05_JOB_RECOVERY IO"){run()}
    }
    @Test fun finalReadFailureIsMarkedAsFinalInventory() {
        onInventory={ if(it==2)throw ProductStorageFailure();DocumentRecoverySnapshot(emptySet(),emptyList()) }
        failure("M06_FINAL_INVENTORY STORAGE_REJECTED"){run()}
    }
    @Test fun readyOwnerFailureRemainsFailureAfterSuccessfulReconciliation() {
        failure("M07_READY_OWNER STORAGE_REJECTED") {
            documentMaintenanceBarrier(c,{run()},{probe.enter(MaintenancePhase.M07_READY_OWNER);throw ProductStorageFailure()})
        }
    }
    @Test fun exceptionTextCauseAndStackTraceNeverReachOutput() {
        val secret="synthetic private URI/path/credential/SQL value"
        val e=IllegalStateException(secret,IOException(secret))
        e.addSuppressed(SecurityException(secret))
        failure("M01_AUTHORIZED_KEY UNEXPECTED"){throw e}
        assertFalse(lines.single().contains(secret));assertFalse(lines.single().contains('\n'))
    }
    @Test fun errorCategoriesAreWhitelistedAndDoNotParseMessages() {
        assertEquals(MaintenanceFailureCode.ROOM_BACKGROUND,maintenanceFailureCode(B4DriverBackgroundFault()))
        assertEquals(MaintenanceFailureCode.SESSION_REVOKED,maintenanceFailureCode(StorageAccessRevoked()))
        assertEquals(MaintenanceFailureCode.SECURITY,maintenanceFailureCode(SecurityException("synthetic")))
        for(failure in B2Failure.entries) {
            val code=maintenanceFailureCode(B2Exception(failure))
            assertTrue(code in MaintenanceFailureCode.entries)
        }
    }
    @Test fun failingLogSinkCannotChangeOriginalFailureOrGrantReady() {
        val failed=MaintenanceProbe(true){throw IllegalStateException("synthetic logging fault")}
        failed.failed(MaintenanceFailureCode.STORAGE_REJECTED)
        failed.failed(MaintenanceFailureCode.UNEXPECTED)
    }
}
