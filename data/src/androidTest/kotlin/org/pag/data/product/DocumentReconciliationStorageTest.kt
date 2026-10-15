package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.UUID

/** Synthetic standalone data-test package only. Compile is not evidence of device execution. */
class DocumentReconciliationStorageTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private val base=File(app.noBackupFilesDir.canonicalFile,"p10c4-test-${UUID.randomUUID()}")
    private val key=ByteArray(32).also{SecureRandom().nextBytes(it)}
    private val actionId=UUID.randomUUID().toString()
    private class Access(override val scope:AccessScope,override val generation:Long):StorageContext {
        var allowed=true;var commit=true
        override fun check(point:AccessPoint)=allowed && (point!=AccessPoint.COMMIT || commit)
        override fun publish(result:()->Unit):Boolean=error("No product publication")
    }
    private val ready=Access(AccessScope.READY,1)
    private val maintenance=Access(AccessScope.MAINTENANCE,2)
    private fun initialized():ProductInventory {
        val root=File(base,"pag-db-v1");File(root,"store").mkdirs()
        for(name in listOf("intent","complete","public"))File(root,name).writeBytes(byteArrayOf(1))
        File(root,"envelope").writeBytes(ByteArray(256))
        val inv=ProductInventory(root,{true},{true})
        ActionDatabaseAccess(app).use(inv.database,key,maintenance,true){db->db.actions().insert(ActionRow(actionId,"synthetic","synthetic",1,2,0,"ACTIVE"))}
        return inv
    }
    private fun staged(inv:ProductInventory):String {
        val owner=SessionDbKeyOwner.bind(ready,key)
        try {
            val db=SqlCipherDocumentImportDatabase(app,inv,{owner})
            val r=DocumentImportStaging(db,base).stage(ready,actionId,"synthetic",DocumentFormat.TEXT){ByteArrayInputStream("synthetic".toByteArray())}
            return (r as OperationsResult.Value).value.jobId
        } finally {owner.invalidate()}
    }
    private fun reconcile(inv:ProductInventory,c:StorageContext=maintenance,ops:DocumentPublishFileOps=AndroidDocumentPublishFileOps) {
        withDocumentMaintenanceKey(c,key){keys->
            val steps=SqlCipherDocumentDeleteDatabase(app,inv,{null},keys)
            DocumentReconciliation(SqlCipherDocumentReconciliationDatabase(steps),steps,base,ops).run(c)
        }
    }
    @After fun cleanupSyntheticOnly(){key.fill(0);if(base.name.startsWith("p10c4-test-") && base.parentFile?.canonicalFile==app.noBackupFilesDir.canonicalFile)base.deleteRecursively()}
    @Test fun realSqlCipherClosedPublicationAndSecondUnlockAreStable() {
        val inv=initialized();staged(inv);reconcile(inv);reconcile(inv,Access(AccessScope.MAINTENANCE,3))
        ActionDatabaseAccess(app).use(inv.database,key,maintenance){db->
            assertEquals(1,db.documents().count(actionId));assertTrue(db.documents().reconciliationSnapshot(maintenance).jobs.isEmpty())
        }
    }
    @Test fun revokeAfterPhysicalMoveThenFreshMaintenanceFinishesImport() {
        val inv=initialized();staged(inv)
        val revoke=object:DocumentPublishFileOps by AndroidDocumentPublishFileOps {
            override fun unlink(source:Path){AndroidDocumentPublishFileOps.unlink(source);maintenance.allowed=false}
        }
        assertThrows(StorageAccessRevoked::class.java){reconcile(inv,maintenance,revoke)}
        val fresh=Access(AccessScope.MAINTENANCE,3);reconcile(inv,fresh)
        ActionDatabaseAccess(app).use(inv.database,key,fresh){db->assertEquals(1,db.documents().count(actionId));assertTrue(db.documents().reconciliationSnapshot(fresh).jobs.isEmpty())}
    }
    @Test fun deleteReservationCommitRollbackAndPendingRecovery() {
        val inv=initialized();staged(inv);reconcile(inv)
        val expected=ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().list(actionId).single().model()}
        val owner=SessionDbKeyOwner.bind(ready,key)
        try {
            val steps=SqlCipherDocumentDeleteDatabase(app,inv,{owner})
            ready.commit=false
            assertThrows(StorageAccessRevoked::class.java){steps.reserve(ready,actionId,expected,UUID.randomUUID().toString())}
            ready.commit=true
            val r=steps.reserve(ready,actionId,expected,UUID.randomUUID().toString());steps.detach(ready,r)
            reconcile(inv,Access(AccessScope.MAINTENANCE,4))
            ActionDatabaseAccess(app).use(inv.database,key,maintenance){db->assertEquals(0,db.documents().count(actionId));assertTrue(db.documents().reconciliationSnapshot(maintenance).jobs.isEmpty())}
        } finally {owner.invalidate()}
    }
    @Test fun androidExclusiveLinkNoFollowAndDirectorySync() {
        base.mkdirs();val source=File(base,"source");val target=File(base,"target");source.writeBytes(byteArrayOf(1));target.writeBytes(byteArrayOf(42))
        assertThrows(Exception::class.java){AndroidDocumentPublishFileOps.link(source.toPath(),target.toPath())}
        assertArrayEquals(byteArrayOf(42),target.readBytes());target.delete()
        AndroidDocumentPublishFileOps.link(source.toPath(),target.toPath());assertTrue(Files.isSameFile(source.toPath(),target.toPath()))
        AndroidDocumentPublishFileOps.syncDirectory(base.toPath())
        val symbolic=File(base,"symbolic");Files.createSymbolicLink(symbolic.toPath(),source.toPath());AndroidDocumentPublishFileOps.unlink(symbolic.toPath())
        assertTrue(source.exists());assertFalse(Files.exists(symbolic.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }
}
