package org.pag.data.product

import org.pag.data.prototype.b2.B2Native
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
    private fun staged(inv:ProductInventory,bytes:ByteArray="synthetic".toByteArray(),format:DocumentFormat=DocumentFormat.TEXT):String {
        val owner=SessionDbKeyOwner.bind(ready,key)
        try {
            val db=SqlCipherDocumentImportDatabase(app,inv,{owner})
            val r=DocumentImportStaging(db,base).stage(ready,actionId,"synthetic",format){ByteArrayInputStream(bytes)}
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
    @Test fun androidAppDirectoryAliasIsResolvedOnlyAtTrustedBoundary() {
        assertTrue(base.mkdirs())
        val platformPath=File(app.noBackupFilesDir,base.name)
        val canonical=productNoBackupRoot(platformPath)
        assertTrue("TRUSTED_ROOT_IDENTITY",canonical==base)
        if(platformPath.absoluteFile!=canonical) {
            assertThrows(ProductStorageFailure::class.java) {
                DocumentFileInventory(platformPath).inspectRecovery(maintenance,emptySet(),emptySet())
            }
        }
        assertFalse(DocumentFileInventory(canonical).inspectRecovery(maintenance,emptySet(),emptySet()).rootPresent)
        assertTrue("EMPTY_ROOT_UNCHANGED",base.listFiles()!!.isEmpty())
    }
    @Test fun realSqlCipherClosedPublicationAndSecondUnlockAreStable() {
        val inv=initialized();staged(inv);reconcile(inv);reconcile(inv,Access(AccessScope.MAINTENANCE,3))
        ActionDatabaseAccess(app).use(inv.database,key,maintenance){db->
            assertEquals(1,db.documents().count(actionId));assertTrue(db.documents().reconciliationSnapshot(maintenance).jobs.isEmpty())
        }
    }
    @Test fun revokeAfterPhysicalMoveThenFreshMaintenanceFinishesImport() {
        val inv=initialized();staged(inv)
        val revoke=object:DocumentPublishFileOps by AndroidDocumentPublishFileOps {
            override fun moveNoReplace(source:Path,destination:Path){AndroidDocumentPublishFileOps.moveNoReplace(source,destination);maintenance.allowed=false}
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
    @Test fun androidAtomicNoReplaceMoveNoFollowAndDirectorySync() {
        base.mkdirs();val source=File(base,"source");val target=File(base,"target");source.writeBytes(byteArrayOf(1));target.writeBytes(byteArrayOf(42))
        assertThrows(Exception::class.java){AndroidDocumentPublishFileOps.moveNoReplace(source.toPath(),target.toPath())}
        assertArrayEquals(byteArrayOf(42),target.readBytes());target.delete()
        AndroidDocumentPublishFileOps.moveNoReplace(source.toPath(),target.toPath());assertFalse(source.exists());assertArrayEquals(byteArrayOf(1),target.readBytes())
        AndroidDocumentPublishFileOps.syncDirectory(base.toPath())
        val symbolic=File(base,"symbolic");Files.createSymbolicLink(symbolic.toPath(),target.toPath());AndroidDocumentPublishFileOps.unlink(symbolic.toPath())
        assertTrue(target.exists());assertFalse(Files.exists(symbolic.toPath(),java.nio.file.LinkOption.NOFOLLOW_LINKS))
    }
    private fun job(inv:ProductInventory):DocumentRecoveryJob = ActionDatabaseAccess(app).use(inv.database,key,maintenance) {
        it.documents().reconciliationSnapshot(maintenance).jobs.single()
    }
    private fun stageFile(j:DocumentRecoveryJob)=File(base,"pag-documents-v1/staging/${j.stagingName}")
    private fun finalFile(j:DocumentRecoveryJob)=File(base,"pag-documents-v1/files/${j.blobName}")
    private fun corrupt(file:File) { java.io.RandomAccessFile(file,"rw").use {
        it.seek(it.length()-1);val b=it.read();it.seek(it.length()-1);it.write(b xor 1)
    } }
    @Test fun allFourFormatsRoundtripPersistAndDeleteThroughRealCoordinators() {
        val inv=initialized()
        val fixtures=listOf(
            DocumentFormat.PDF to "%PDF-1.4\nsynthetic\n%%EOF".toByteArray(),
            DocumentFormat.JPEG to byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xd9.toByte()),
            DocumentFormat.PNG to byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a),
            DocumentFormat.TEXT to "synthetic 🦋\nUTF-8".toByteArray())
        val owner=SessionDbKeyOwner.bind(ready,key)
        try {
            for((format,bytes) in fixtures) {
                val db=SqlCipherDocumentImportDatabase(app,inv,{owner});var opens=0
                val staged=DocumentImportStaging(db,base).stage(ready,actionId,"synthetic ${format.name}",format) {
                    opens++;ByteArrayInputStream(bytes)
                } as OperationsResult.Value
                assertEquals(1,opens)
                DocumentPublication(db,base).complete(ready,staged.value.jobId)
                assertThrows(ProductStorageFailure::class.java){DocumentPublication(db,base).complete(ready,staged.value.jobId)}
            }
            reconcile(inv,Access(AccessScope.MAINTENANCE,5))
            val rows=ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().list(actionId).map { row->row.model() }}
            assertEquals(DocumentFormat.entries.toSet(),rows.map{it.format}.toSet())
            assertEquals(4,rows.size)
            val deletion=DocumentDelete(SqlCipherDocumentDeleteDatabase(app,inv,{owner}),base)
            for(row in rows)deletion.delete(ready,actionId,row)
            reconcile(inv,Access(AccessScope.MAINTENANCE,6))
            assertEquals(0,ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().count(actionId)})
        } finally {owner.invalidate()}
    }
    @Test fun nativeRacingTargetCannotBeReplacedEvenAfterInventoryCheck() {
        val inv=initialized();staged(inv);val j=job(inv)
        val race=object:DocumentPublishFileOps by AndroidDocumentPublishFileOps {
            override fun moveNoReplace(source:Path,destination:Path) {
                Files.write(destination,byteArrayOf(42))
                AndroidDocumentPublishFileOps.moveNoReplace(source,destination)
            }
        }
        assertThrows(ProductStorageFailure::class.java){reconcile(inv,maintenance,race)}
        assertArrayEquals(byteArrayOf(42),finalFile(j).readBytes());assertTrue(stageFile(j).isFile)
        assertEquals("IMPORT_CLOSED",job(inv).phase)
    }
    @Test fun corruptStagingBlocksBeforeAnyMoveAndKeepsJob() {
        val inv=initialized();staged(inv);val j=job(inv);corrupt(stageFile(j))
        assertThrows(Exception::class.java){reconcile(inv)}
        assertEquals("IMPORT_CLOSED",job(inv).phase);assertTrue(stageFile(j).isFile);assertFalse(finalFile(j).exists())
    }
    @Test fun unknownBlobAndSymlinkBlockWithoutCleanup() {
        val inv=initialized();staged(inv);val j=job(inv)
        val unknown=File(base,"pag-documents-v1/files/${UUID.randomUUID()}");unknown.writeBytes(byteArrayOf(42))
        assertThrows(ProductStorageFailure::class.java){reconcile(inv)};assertArrayEquals(byteArrayOf(42),unknown.readBytes())
        assertTrue(unknown.delete())
        Files.createSymbolicLink(unknown.toPath(),stageFile(j).toPath())
        assertThrows(ProductStorageFailure::class.java){reconcile(inv)};assertTrue(Files.isSymbolicLink(unknown.toPath()))
        assertTrue(stageFile(j).isFile);assertEquals("IMPORT_CLOSED",job(inv).phase)
    }
    @Test fun revokeBeforePublishedCommitLeavesFinalOnlyAndFreshUnlockRecovers() {
        val inv=initialized();staged(inv);val j=job(inv)
        val revoke=object:DocumentPublishFileOps by AndroidDocumentPublishFileOps {
            override fun syncDirectory(directory:Path) { AndroidDocumentPublishFileOps.syncDirectory(directory);maintenance.commit=false }
        }
        assertThrows(StorageAccessRevoked::class.java){reconcile(inv,maintenance,revoke)}
        maintenance.commit=true
        assertEquals("IMPORT_CLOSED",job(inv).phase);assertFalse(stageFile(j).exists());assertTrue(finalFile(j).isFile)
        reconcile(inv,Access(AccessScope.MAINTENANCE,7))
        assertEquals(1,ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().count(actionId)})
    }
    @Test fun finalTransactionRollbackRetainsPublishedJobThenRecoverySucceeds() {
        val inv=initialized();staged(inv)
        // Synthetic transaction fault only in the isolated SQLCipher database.
        B2Native.open(inv.database,key,maintenance).use { it.execSQL("CREATE TRIGGER synthetic_reject_document BEFORE INSERT ON documents BEGIN SELECT RAISE(ABORT, 'synthetic'); END") }
        assertThrows(Exception::class.java){reconcile(inv)}
        val j=job(inv);assertEquals("FILE_PUBLISHED",j.phase);assertTrue(finalFile(j).isFile)
        ActionDatabaseAccess(app).use(inv.database,key,maintenance){db->
            assertEquals(0,db.documents().count(actionId))
        }
        B2Native.open(inv.database,key,maintenance).use { it.execSQL("DROP TRIGGER synthetic_reject_document") }
        reconcile(inv,Access(AccessScope.MAINTENANCE,8));reconcile(inv,Access(AccessScope.MAINTENANCE,9))
        assertEquals(1,ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().count(actionId)})
    }
    @Test fun corruptionAfterPublishedPreventsDocumentCommitAndIsPreserved() {
        val inv=initialized();staged(inv)
        B2Native.open(inv.database,key,maintenance).use { it.execSQL("CREATE TRIGGER synthetic_reject_document BEFORE INSERT ON documents BEGIN SELECT RAISE(ABORT, 'synthetic'); END") }
        assertThrows(Exception::class.java){reconcile(inv)}
        val j=job(inv);assertEquals("FILE_PUBLISHED",j.phase);corrupt(finalFile(j))
        assertThrows(Exception::class.java){reconcile(inv)}
        assertTrue(finalFile(j).isFile);assertEquals("FILE_PUBLISHED",job(inv).phase)
        assertEquals(0,ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().count(actionId)})
    }

    @Test fun revokeAfterFinalCommitKeepsRealDocumentWithoutPublication() {
        val inv=initialized();val id=staged(inv);val owner=SessionDbKeyOwner.bind(ready,key)
        try {
            val real=SqlCipherDocumentImportDatabase(app,inv,{owner})
            val revoke=object:DocumentPublicationDatabase by real {
                override fun finish(c:StorageContext,expected:DocumentJobRow) { real.finish(c,expected);ready.allowed=false }
            }
            assertThrows(StorageAccessRevoked::class.java){DocumentPublication(revoke,base).complete(ready,id)}
            ActionDatabaseAccess(app).use(inv.database,key,maintenance){db->
                assertEquals(1,db.documents().count(actionId));assertTrue(db.documents().reconciliationSnapshot(maintenance).jobs.isEmpty())
            }
            reconcile(inv,Access(AccessScope.MAINTENANCE,10))
        } finally { owner.invalidate() }
    }
    @Test fun reservedDeleteAndAlreadyUnlinkedPendingRecoverOnFreshMaintenance() {
        val inv=initialized();val owner=SessionDbKeyOwner.bind(ready,key)
        try {
            for(unlinked in listOf(false,true)) {
                staged(inv);reconcile(inv,Access(AccessScope.MAINTENANCE,11))
                val expected=ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().list(actionId).single().model()}
                val db=SqlCipherDocumentDeleteDatabase(app,inv,{owner})
                val reserved=db.reserve(ready,actionId,expected,UUID.randomUUID().toString())
                assertNull(reserved.job.fileKey);assertNull(reserved.job.displayName);assertNull(reserved.job.format)
                assertEquals(0,ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().count(actionId)})
                if(unlinked) {
                    db.detach(ready,reserved)
                    AndroidDocumentPublishFileOps.unlink(File(base,"pag-documents-v1/files/${reserved.job.blobName}").toPath())
                }
                reconcile(inv,Access(AccessScope.MAINTENANCE,12))
                assertEquals(0,ActionDatabaseAccess(app).use(inv.database,key,ready){it.documents().count(actionId)})
            }
        } finally { owner.invalidate() }
    }

    @Test fun productReopenBatchesActionValidationAndFirstInventoryButKeepsFreshFinalPreflight() {
        val inv=initialized();val phases=mutableMapOf<ActionDatabasePhase,Int>();var ready=false
        reconcileProductDocuments(app,inv,base,maintenance,key,false,timing={phase,_->phases[phase]=(phases[phase]?:0)+1}){ready=true}
        assertTrue(ready)
        for(phase in listOf(ActionDatabasePhase.NATIVE_OPEN,ActionDatabasePhase.VERSION,ActionDatabasePhase.CIPHER_INTEGRITY,
            ActionDatabasePhase.SQLITE_INTEGRITY,ActionDatabasePhase.FOREIGN_KEYS,ActionDatabasePhase.NATIVE_CLOSE,ActionDatabasePhase.CLEANUP)) {
            assertEquals("FULL_CHECK_PER_OPERATION",2,phases[phase])
        }
    }
    @Test fun invalidActionStillBlocksBeforeAnyRecoveryMutation() {
        val inv=initialized();staged(inv)
        ActionDatabaseAccess(app).use(inv.database,key,maintenance){db->val a=db.actions().get(actionId)!!;db.actions().update(a.copy(title=""))}
        fun encryptedFiles()=base.walkTopDown().filter{it.isFile && it.parentFile?.name in setOf("staging","files")}
            .associate{it.name to java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()}
        val before=encryptedFiles();var ready=false
        assertThrows(ProductStorageFailure::class.java){reconcileProductDocuments(app,inv,base,maintenance,key,false){ready=true}}
        assertFalse(ready);assertTrue("ENCRYPTED_FILES_UNCHANGED",before==encryptedFiles())
    }
    @Test fun revokeDuringCombinedPreflightClosesNativeAndCannotPublishReady() {
        val inv=initialized();var closed=0;var ready=false
        assertThrows(StorageAccessRevoked::class.java){
            reconcileProductDocuments(app,inv,base,maintenance,key,false,timing={phase,_->
                if(phase==ActionDatabasePhase.CIPHER_INTEGRITY)maintenance.allowed=false
                if(phase==ActionDatabasePhase.NATIVE_CLOSE)closed++
            }){ready=true}
        }
        assertFalse(ready);assertEquals(1,closed)
    }

}
