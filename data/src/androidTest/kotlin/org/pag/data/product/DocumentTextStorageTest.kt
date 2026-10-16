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
class DocumentTextStorageTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private val base=File(app.noBackupFilesDir.canonicalFile,"p10c2-${UUID.randomUUID()}")
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
        check(app.packageName=="org.pag.data.unlockprobe")
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
    private fun add(inv:ProductInventory,text:String,format:DocumentFormat=DocumentFormat.TEXT):DocumentAsset {
        val bytes=when(format) {
            DocumentFormat.TEXT->text.toByteArray()
            DocumentFormat.JPEG->byteArrayOf(-1,-40,-1,0)
            DocumentFormat.PNG->byteArrayOf(-119,80,78,71,13,10,26,10,0)
            DocumentFormat.PDF->"%PDF-synthetic".toByteArray()
        }
        val owner=SessionDbKeyOwner.bind(c,key)
        try {
            val steps=SqlCipherDocumentImportDatabase(app,inv,{owner})
            val staged=DocumentImportStaging(steps,base).stage(c,action,"synthetic",format){ByteArrayInputStream(bytes)} as OperationsResult.Value
            DocumentPublication(steps,base).complete(c,staged.value.jobId)
        } finally {owner.invalidate();bytes.fill(0)}
        return access.use(inv.database,key,c){it.documents().list(action).map{r->r.model()}.last{r->r.format==format}}
    }
    private fun snapshot(inv:ProductInventory,a:DocumentAsset)=access.use(inv.database,key,c){db->db.withReadTransaction{db.documents().textSnapshot(action,a,c)}}
    @After fun cleanup(){allowed=true;key.fill(0);if(base.name.startsWith("p10c2-") && base.parentFile==app.noBackupFilesDir.canonicalFile)base.deleteRecursively()}
    private fun read(inv:ProductInventory,asset:DocumentAsset,index:Int,request:DocumentTextRequest=DocumentTextRequest(c.generation)):DocumentTextPage {
        val started=System.nanoTime()
        val snapshot=(snapshot(inv,asset) as DocumentResult.Value).value
        request.measured(DocumentTextPhase.DB_SNAPSHOT,started)
        return snapshot.use {s ->DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()) {
            val inventory=DocumentFileInventory(base);val inspecting=System.nanoTime()
            val files=inventory.inspect(c,s.staging,s.required+s.optional)
            check(files.files.containsAll(s.required));request.measured(DocumentTextPhase.INVENTORY,inspecting)
            val reading=System.nanoTime()
            inventory.readText(c,s.blob,s.staging,files.files,asset.id,s.key,asset.plaintextSize,index,request).also {
                request.measured(DocumentTextPhase.READ_AUTH_UTF8_PAGE,reading)
            }
        }}
    }
    @Test fun utf8PagesReopenLosslesslyAndKeepCiphertextUnchanged() {
        val inv=init();val text="\uFEFFsynthetic 🦋 e\u0301\t\r\n".repeat(3000)+"END"
        val asset=add(inv,text)
        val blob=(snapshot(inv,asset) as DocumentResult.Value).value.use {it.blob}
        val file=File(base,"pag-documents-v1/files/$blob");val before=file.readBytes()
        val request=DocumentTextRequest(c.generation){phase,ms->android.util.Log.i("PaGTextStorageTiming","${phase.name} ms=$ms")}
        val parts=mutableListOf<String>();val count=read(inv,asset,0,request).use{it.pageCount}
        for(index in 0 until count)read(inv,asset,index,request).use {assertEquals(index,it.index);parts+=it.text()!!}
        assertEquals(text,parts.joinToString(""));assertArrayEquals(before,file.readBytes())
        read(inv,asset,0,request).use {assertEquals(parts.first(),it.text())}
        for(f in base.walkTopDown().filter{it.isFile})f.inputStream().use {input->
            val prefix=ByteArray(9);input.read(prefix);assertFalse(prefix.contentEquals("synthetic".toByteArray()))
        }
    }
    @Test fun oldBindingWrongActionAndDeleteJobAreRejected() {
        val inv=init();val a=add(inv,"synthetic")
        assertEquals(DocumentFailure.STALE,(snapshot(inv,a.copy(displayName="changed")) as DocumentResult.Rejected).reason)
        assertEquals(DocumentFailure.INVALID,(snapshot(inv,a.copy(actionId="other")) as DocumentResult.Rejected).reason)
        assertEquals(DocumentFailure.STALE,(snapshot(inv,a.copy(format=DocumentFormat.JPEG)) as DocumentResult.Rejected).reason)
        access.use(inv.database,key,c){it.documents().reserveDelete(action,a,UUID.randomUUID().toString(),c)}
        assertEquals(DocumentFailure.NOT_FOUND,(snapshot(inv,a) as DocumentResult.Rejected).reason)
    }
    @Test fun genuineImageAndPdfRowsNeverEnterTextReader() {
        val inv=init()
        for(format in listOf(DocumentFormat.JPEG,DocumentFormat.PNG,DocumentFormat.PDF)) {
            val asset=add(inv,"synthetic",format)
            assertEquals(DocumentFailure.NOT_ALLOWED,(snapshot(inv,asset) as DocumentResult.Rejected).reason)
        }
    }
    @Test fun lateManipulationNeverReleasesFirstTextPage() {
        val inv=init();val a=add(inv,"x".repeat(2_100_000))
        val blob=(snapshot(inv,a) as DocumentResult.Value).value.use {it.blob};val file=File(base,"pag-documents-v1/files/$blob")
        RandomAccessFile(file,"rw").use {val offset=it.length()-3;it.seek(offset);val before=it.read();it.seek(offset);it.write(before xor 1)}
        assertThrows(ProductStorageFailure::class.java){read(inv,a,0)};assertTrue(file.exists())
    }
    @Test fun authenticatedInvalidTextIsFormatFailureButLateTamperingStillBlocks() {
        val inv=init();val a=add(inv,"x".repeat(2_100_001))
        val s=(snapshot(inv,a) as DocumentResult.Value).value
        val file=File(base,"pag-documents-v1/files/${s.blob}")
        // Deliberately malformed, but authenticated synthetic fixture; import rejects this.
        s.use {val bytes=byteArrayOf(0)+ByteArray(2_100_000){120}
            try {DocumentFileStreams.encrypt(c,a.id,s.key,ByteArrayInputStream(bytes),file.outputStream())}
            finally {bytes.fill(0)}
        }
        assertThrows(DocumentTextFormatFailure::class.java){read(inv,a,0)}
        RandomAccessFile(file,"rw").use {val p=it.length()-3;it.seek(p);val old=it.read();it.seek(p);it.write(old xor 1)}
        assertThrows(ProductStorageFailure::class.java){read(inv,a,0)};assertTrue(file.exists())
    }
    @Test fun maximumTextDocumentReadsLastPageWithoutWholeFileTextBuffer() {
        val inv=init();val owner=SessionDbKeyOwner.bind(c,key)
        try {
            val steps=SqlCipherDocumentImportDatabase(app,inv,{owner})
            val result=DocumentImportStaging(steps,base).stage(c,action,"synthetic",DocumentFormat.TEXT) {
                object:InputStream() {
                    var position=0L
                    override fun read():Int=error("Bulk fixture")
                    override fun read(b:ByteArray,o:Int,n:Int):Int {
                        if(position==DocumentRules.MAX_DOCUMENT_BYTES)return -1
                        val count=minOf(n.toLong(),DocumentRules.MAX_DOCUMENT_BYTES-position).toInt()
                        b.fill(120.toByte(),o,o+count);position+=count;return count
                    }
                }
            } as OperationsResult.Value
            DocumentPublication(steps,base).complete(c,result.value.jobId)
        }finally {owner.invalidate()}
        val asset=access.use(inv.database,key,c){it.documents().list(action).single().model()}
        val timing=DocumentTextRequest(c.generation){phase,ms->android.util.Log.i("PaGTextStorageTiming","${phase.name} ms=$ms")}
        read(inv,asset,799,timing).use {assertEquals(800,it.pageCount);assertEquals(DocumentTextLimits.PAGE_BYTES,it.text()!!.length)}
    }
    @Test fun revokeDuringRealFileReadClosesWithoutTextPublication() {
        val inv=init();val a=add(inv,"synthetic".repeat(20000));val s=(snapshot(inv,a) as DocumentResult.Value).value;var closed=false
        s.use {
            val stream=object:FilterInputStream(File(base,"pag-documents-v1/files/${s.blob}").inputStream()) {
                override fun read(b:ByteArray,o:Int,n:Int):Int=`in`.read(b,o,n).also{allowed=false}
                override fun read():Int=`in`.read().also {allowed=false}
                override fun close(){closed=true;super.close()}
            }
            assertThrows(StorageAccessRevoked::class.java){DocumentFileStreams.readText(c,a.id,s.key,stream,a.plaintextSize,0)}
        }
        assertTrue(closed)
    }
    @Test fun cancellingAfterRealReadTransactionKeepsTheAuthorizedSessionOwnerUsable() {
        val inv=init();add(inv,"synthetic")
        val owner=SessionDbKeyOwner.bind(c,key);val request=DocumentTextRequest(c.generation)
        val scoped=documentReadContext(c,request);var held:DocumentReadSnapshot?=null
        try {
            assertThrows(StorageAccessRevoked::class.java){withDocumentReadKey(owner,scoped){lease->
                access.use(inv.database,lease,scoped){db->db.withReadTransaction {
                    val asset=db.documents().list(action).single().model()
                    held=(db.documents().textSnapshot(action,asset,scoped) as DocumentResult.Value).value
                }}
                request.cancel()
            }}
            assertEquals(1,owner.withDbKey(c){lease->access.use(inv.database,lease,c){it.documents().count(action)}})
        }finally {held?.close();owner.invalidate()}
    }
}
