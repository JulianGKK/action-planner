package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import androidx.room3.withReadTransaction
import java.io.*
import java.security.SecureRandom
import java.util.UUID

/** Synthetic isolated SQLCipher fixture; never the product DB. No plaintext files. */
class DocumentImageStorageTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private val base=File(app.noBackupFilesDir.canonicalFile,"p10c1-${UUID.randomUUID()}")
    private val key=ByteArray(32).also{SecureRandom().nextBytes(it)}
    private val action=UUID.randomUUID().toString()
    private var allowed=true
    private val c=object:StorageContext {
        override val scope=AccessScope.READY;override val generation=1L
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean=error("No publication")
    }
    private val access by lazy {ActionDatabaseAccess(app)}
    private fun init():ProductInventory {
        check(app.packageName!="org.pag.prototype.a")
        val root=File(base,"pag-db-v1");File(root,"store").mkdirs()
        for(n in listOf("intent","complete","public"))File(root,n).writeBytes(byteArrayOf(1))
        File(root,"envelope").writeBytes(ByteArray(256));val inv=ProductInventory(root,{true},{true})
        val initialize=object:StorageContext {
            override val scope=AccessScope.MAINTENANCE;override val generation=0L
            override fun check(point:AccessPoint)=allowed
            override fun publish(result:()->Unit):Boolean=error("No publication")
        }
        access.use(inv.database,key,initialize,true) {it.actions().insert(ActionRow(action,"synthetic","synthetic",1,2,0,"ACTIVE"))}
        return inv
    }
    private fun add(inv:ProductInventory,format:DocumentFormat):DocumentAsset {
        val bytes=when(format) {
            DocumentFormat.JPEG->byteArrayOf(-1,-40,-1)+ByteArray(2_100_000){42}
            DocumentFormat.PNG->byteArrayOf(-119,80,78,71,13,10,26,10)+ByteArray(100){42}
            DocumentFormat.PDF->"%PDF-synthetic".toByteArray()
            DocumentFormat.TEXT->"synthetic".toByteArray()
        }
        val owner=SessionDbKeyOwner.bind(c,key)
        try {
            val steps=SqlCipherDocumentImportDatabase(app,inv,{owner})
            val staged=DocumentImportStaging(steps,base).stage(c,action,"synthetic",format){ByteArrayInputStream(bytes)} as OperationsResult.Value
            DocumentPublication(steps,base).complete(c,staged.value.jobId)
        } finally {owner.invalidate();bytes.fill(0)}
        return access.use(inv.database,key,c){it.documents().list(action).map {r->r.model()}.last {r->r.format==format}}
    }
    private fun snapshot(inv:ProductInventory,a:DocumentAsset)=access.use(inv.database,key,c){db->db.withReadTransaction{db.documents().imageSnapshot(action,a,c)}}
    @After fun cleanup(){allowed=true;key.fill(0);if(base.name.startsWith("p10c1-") && base.parentFile==app.noBackupFilesDir.canonicalFile)base.deleteRecursively()}
    @Test fun jpegAndPngUseReadOnlySnapshotVerifyAndCanReopenWithoutBlobChanges() {
        val inv=init()
        for(f in listOf(DocumentFormat.JPEG,DocumentFormat.PNG)) {
            val a=add(inv,f)
            repeat(2) {
                val s=(snapshot(inv,a) as DocumentResult.Value).value
                val file=File(base,"pag-documents-v1/files/${s.blob}");val ciphertext=file.readBytes()
                s.use {DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
                    DocumentImageRead(base).decode(c,s,DocumentImageDecoder { bytes,format,check ->
                        check();assertEquals(f,format);assertEquals(a.plaintextSize,bytes.size.toLong());object:DocumentImage{override fun close(){}}
                    }).close()
                }}
                assertTrue(s.key.all{b->b==0.toByte()});assertArrayEquals(ciphertext,file.readBytes())
            }
        }
    }
    @Test fun staleWrongActionDeletedAndNonImageAreRejected() {
        val inv=init();val a=add(inv,DocumentFormat.JPEG)
        assertEquals(DocumentFailure.STALE,(snapshot(inv,a.copy(displayName="changed")) as DocumentResult.Rejected).reason)
        assertEquals(DocumentFailure.INVALID,(snapshot(inv,a.copy(actionId="wrong")) as DocumentResult.Rejected).reason)
        for(f in listOf(DocumentFormat.PDF,DocumentFormat.TEXT))assertEquals(DocumentFailure.NOT_ALLOWED,(snapshot(inv,add(inv,f)) as DocumentResult.Rejected).reason)
        access.use(inv.database,key,c){it.documents().reserveDelete(action,a,UUID.randomUUID().toString(),c)}
        assertEquals(DocumentFailure.NOT_FOUND,(snapshot(inv,a) as DocumentResult.Rejected).reason)
    }
    @Test fun tamperedCiphertextNeverReachesDecoder() {
        val inv=init();val a=add(inv,DocumentFormat.JPEG);val s=(snapshot(inv,a) as DocumentResult.Value).value
        val file=File(base,"pag-documents-v1/files/${s.blob}")
        java.io.RandomAccessFile(file,"rw").use {val offset=it.length()-3;it.seek(offset);val old=it.read();it.seek(offset);it.write(old xor 1)}
        s.use {DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            assertThrows(ProductStorageFailure::class.java){DocumentImageRead(base).decode(c,s,DocumentImageDecoder{_,_,_->error("Decoder forbidden")})}
        }}
        assertTrue(file.exists())
    }
    @Test fun revokeAfterVerifiedBytesBeforeDecodedPublicationDisposesResult() {
        val inv=init();val a=add(inv,DocumentFormat.PNG);val s=(snapshot(inv,a) as DocumentResult.Value).value;var closed=false
        s.use {DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            assertThrows(StorageAccessRevoked::class.java){DocumentImageRead(base).decode(c,s,DocumentImageDecoder{_,_,_->
                allowed=false;object:DocumentImage{override fun close(){closed=true}}
            })}
        }}
        assertTrue(closed)
    }
    @Test fun revokeDuringActualCiphertextReadClosesStreamAndDoesNotReleaseBytes() {
        val inv=init();val a=add(inv,DocumentFormat.JPEG);val s=(snapshot(inv,a) as DocumentResult.Value).value
        val file=File(base,"pag-documents-v1/files/${s.blob}");var closed=false
        s.use {
            val stream=object:FilterInputStream(file.inputStream()) {
                override fun read(bytes:ByteArray,offset:Int,length:Int):Int=`in`.read(bytes,offset,length).also {allowed=false}
                override fun read():Int=`in`.read().also {allowed=false}
                override fun close(){closed=true;super.close()}
            }
            assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.readImage(c,a.id,s.key,stream,a.plaintextSize,a.format)}
        }
        assertTrue(closed);assertTrue(file.exists());assertTrue(s.key.all{it==0.toByte()})
    }
}
