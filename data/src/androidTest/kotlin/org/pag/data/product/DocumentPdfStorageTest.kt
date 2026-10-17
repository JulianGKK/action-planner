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
class DocumentPdfStorageTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private val base=File(app.noBackupFilesDir.canonicalFile,"p10c3-${UUID.randomUUID()}")
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
    private fun add(inv:ProductInventory,text:String,format:DocumentFormat=DocumentFormat.PDF):DocumentAsset {
        val bytes=when(format) {
            DocumentFormat.PDF->("%PDF-1.4\n"+text).toByteArray()
            DocumentFormat.JPEG->byteArrayOf(-1,-40,-1,0)
            DocumentFormat.PNG->byteArrayOf(-119,80,78,71,13,10,26,10,0)
            DocumentFormat.TEXT->text.toByteArray()
        }
        val owner=SessionDbKeyOwner.bind(c,key)
        try {
            val steps=SqlCipherDocumentImportDatabase(app,inv,{owner})
            val staged=DocumentImportStaging(steps,base).stage(c,action,"synthetic",format){ByteArrayInputStream(bytes)} as OperationsResult.Value
            DocumentPublication(steps,base).complete(c,staged.value.jobId)
        } finally {owner.invalidate();bytes.fill(0)}
        return access.use(inv.database,key,c){it.documents().list(action).map{r->r.model()}.last{r->r.format==format}}
    }
    private fun snapshot(inv:ProductInventory,a:DocumentAsset)=access.use(inv.database,key,c){db->db.withReadTransaction{db.documents().pdfSnapshot(action,a,c)}}
    @After fun cleanup(){allowed=true;key.fill(0);if(base.name.startsWith("p10c3-") && base.parentFile==app.noBackupFilesDir.canonicalFile)base.deleteRecursively()}
    private var rendered=0
    private var closed=0
    private fun read(inv:ProductInventory,asset:DocumentAsset):DocumentPdfPage {
        val requested=System.nanoTime()
        val snapshot=(snapshot(inv,asset) as DocumentResult.Value).value
        android.util.Log.i("PaGPdfStorageTiming","DB_SNAPSHOT ms=${(System.nanoTime()-requested)/1000000}")
        val timing=DocumentPdfRequest(c.generation){p,ms->android.util.Log.i("PaGPdfStorageTiming","${p.name} ms=$ms")}
        return snapshot.use{s->DocumentNamespaceLane.exclusive(DocumentNamespaceLane.ticket()){
            DocumentPdfRead(base).render(c,s,0,DocumentPdfRenderer{size->object:DocumentPdfInput{
                var count=0L
                override fun write(bytes:ByteArray,offset:Int,n:Int){count+=n}
                override fun render(page:Int,check:()->Unit):DocumentPdfPage{check();assertEquals(size,count);assertTrue(s.key.all{it==0.toByte()});rendered++;return DocumentPdfPage(page,1,object:DocumentImage{override fun close(){}})}
                override fun close(){closed++}
            }},timing)
        }}
    }
    @Test fun authenticPdfReadAndReopenPreserveCiphertext(){val inv=init();val a=add(inv,"synthetic".repeat(300000));val blob=(snapshot(inv,a) as DocumentResult.Value).value.use{it.blob};val f=File(base,"pag-documents-v1/files/$blob");val before=f.readBytes();read(inv,a).close();read(inv,a).close();assertEquals(2,rendered);assertEquals(2,closed);assertArrayEquals(before,f.readBytes())}
    @Test fun lateTamperNeverReachesRenderer(){val inv=init();val a=add(inv,"synthetic".repeat(300000));val blob=(snapshot(inv,a) as DocumentResult.Value).value.use{it.blob};val f=File(base,"pag-documents-v1/files/$blob");RandomAccessFile(f,"rw").use{val p=it.length()-3;it.seek(p);val v=it.read();it.seek(p);it.write(v xor 1)};assertThrows(ProductStorageFailure::class.java){read(inv,a)};assertEquals(0,rendered);assertEquals(1,closed);assertTrue(f.exists())}
    @Test fun staleWrongActionAndDeletionHidePdf(){val inv=init();val a=add(inv,"synthetic");assertEquals(DocumentFailure.STALE,(snapshot(inv,a.copy(displayName="changed")) as DocumentResult.Rejected).reason);assertEquals(DocumentFailure.INVALID,(snapshot(inv,a.copy(actionId="other")) as DocumentResult.Rejected).reason);access.use(inv.database,key,c){it.documents().reserveDelete(action,a,UUID.randomUUID().toString(),c)};assertEquals(DocumentFailure.NOT_FOUND,(snapshot(inv,a) as DocumentResult.Rejected).reason)}
    @Test fun nonPdfDenied(){val inv=init();for(format in listOf(DocumentFormat.TEXT,DocumentFormat.JPEG,DocumentFormat.PNG)){val a=add(inv,"synthetic",format);assertEquals(DocumentFailure.NOT_ALLOWED,(snapshot(inv,a) as DocumentResult.Rejected).reason)}}
    @Test fun persistedActionStatusesAndMissingDocumentAreEnforced(){
        val inv=init();val a=add(inv,"synthetic")
        for(status in listOf("PREPARATION","ACTIVE","COMPLETED","DRAFT","DELETION_PENDING")){
            access.use(inv.database,key,c){db->val row=db.actions().get(action)!!;db.actions().update(row.copy(status=status,completedAt=if(status=="COMPLETED")2L else null))}
            val result=snapshot(inv,a)
            if(status in setOf("PREPARATION","ACTIVE","COMPLETED"))(result as DocumentResult.Value).value.close()
            else assertEquals(DocumentFailure.NOT_ALLOWED,(result as DocumentResult.Rejected).reason)
        }
    }

    @Test fun realReadPortNeedsNoDbKeyDuringFileAndRenderWork(){
        val inv=init();val asset=add(inv,"synthetic".repeat(300000))
        val authority=object:StorageContext by c {
            override fun publish(result:()->Unit):Boolean {if(!allowed)return false;result();return true}
        }
        val owner=SessionDbKeyOwner.bind(authority,key)
        val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
        val finished=java.util.concurrent.CountDownLatch(1)
        val value=java.util.concurrent.atomic.AtomicReference<DocumentResult<DocumentPdfPage>>()
        val denied=java.util.concurrent.atomic.AtomicBoolean(false)
        val wrapped=object:android.content.ContextWrapper(app) {
            override fun getApplicationContext():android.content.Context=this
            override fun getNoBackupFilesDir()=base
        }
        val storage=object:StorageAccess {override fun acquire(scope:AccessScope)=authority.takeIf{scope==AccessScope.READY && allowed}}
        val repository=ProductDocumentRepository(wrapped,storage,inv,{owner},worker,java.util.concurrent.Executor{it.run()}){denied.set(true);finished.countDown()}
        val started=System.nanoTime()
        val request=DocumentPdfRequest(1){phase,ms->android.util.Log.i("PaGPdfStorageTiming","${phase.name} ms=$ms")}
        try {
            repository.readPdf(action,asset,0,request,DocumentPdfRenderer{size->
                // Snapshot/Room/DB-key lease must already have ended; all subsequent work
                // succeeds even after the DB-key owner becomes unavailable.
                owner.invalidate()
                object:DocumentPdfInput {
                    var count=0L
                    override fun write(bytes:ByteArray,offset:Int,count:Int){this.count+=count}
                    override fun render(page:Int,check:()->Unit):DocumentPdfPage {
                        check();assertEquals(size,count)
                        return DocumentPdfPage(page,1,object:DocumentImage{override fun close(){}})
                    }
                    override fun close(){closed++}
                }
            }){result->request.measured(DocumentPdfPhase.PUBLICATION,started);value.set(result);finished.countDown()}
            assertTrue(finished.await(30,java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(denied.get());(value.get() as DocumentResult.Value).value.close();assertEquals(1,closed)
        }finally {request.cancel();owner.invalidate();worker.shutdownNow()}
    }

}
