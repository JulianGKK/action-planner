package org.pag.data.product

import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.File

/** Internal key-bearing lease, never a domain projection. */
internal class DocumentReadSnapshot(val asset:DocumentAsset, val blob:String, val key:ByteArray,
    val required:Set<String>, val optional:Set<String>, val staging:Set<String>):AutoCloseable {
    override fun close() { key.fill(0) }
}

/** Caller holds the one namespace lane. DB/key owner already closed before this call. */
internal class DocumentImageRead(root:File) {
    private val inventory=DocumentFileInventory(root)
    fun decode(c:StorageContext,s:DocumentReadSnapshot,decoder:DocumentImageDecoder,request:DocumentImageRequest?=null):DocumentImage {
        DocumentNamespaceLane.requireExclusive()
        requireOperations(c,AccessPoint.OPERATION)
        val files=inventory.inspect(c,s.staging,s.required+s.optional)
        if(!files.rootPresent || files.staging!=s.staging || !files.files.containsAll(s.required)) throw ProductStorageFailure()
        val reading=System.nanoTime()
        val bytes=inventory.readImage(c,s.blob,s.staging,files.files,s.asset.id,s.key,s.asset.plaintextSize,s.asset.format)
        request?.measured(DocumentImagePhase.READ_AND_AUTH,reading)
        s.close() // No controlled File-DEK copy survives into decoder work.
        var result:DocumentImage?=null
        try {
            requireOperations(c)
            // This is the first plaintext consumer, reached only after full authenticated EOF.
            val decoding=System.nanoTime()
            result=decoder.decode(bytes,s.asset.format) { requireOperations(c) }
            request?.measured(DocumentImagePhase.DECODE,decoding)
            requireOperations(c)
            return result
        } catch(failure:Throwable) {result?.close();throw failure}
        finally {bytes.fill(0)}
    }
}

/** Cancellation narrows an existing READY context; it never creates storage authority. */
internal interface DocumentReadContext:StorageContext {val sessionAuthority:StorageContext}
internal fun documentReadContext(original:StorageContext,request:DocumentReadRequest):StorageContext = object:DocumentReadContext {
    override val sessionAuthority=original
    override val generation=original.generation
    override val scope=original.scope
    override fun check(point:AccessPoint)=request.active && original.generation==generation && original.check(point)
    override fun publish(result:()->Unit):Boolean=original.publish {if(request.active && original.generation==generation) result()}
}

/** Request cancellation is not session revocation. DB/worker still use the narrower context;
 * the existing key owner validates the actual session, and its short lease always ends here. */
internal fun <T> withDocumentReadKey(owner:SessionDbKeyOwner,c:StorageContext,work:(ByteArray)->T):T {
    requireOperations(c)
    val result=owner.withDbKey((c as? DocumentReadContext)?.sessionAuthority ?: c) {key->
        requireOperations(c)
        work(key)
    }
    requireOperations(c)
    return result
}

/** Resource-aware variant of the established executor/publication boundary. */
internal class DocumentReadDispatch(private val access:StorageAccess,private val worker:java.util.concurrent.Executor,
    private val main:java.util.concurrent.Executor,private val blocked:()->Unit) {
    fun <T:AutoCloseable> run(request:DocumentReadRequest,publish:(DocumentResult<T>)->Unit,
        work:(StorageContext)->DocumentResult<T>) {
        val original=access.acquire(AccessScope.READY) ?: return
        if(request.generation!=null && request.generation!=original.generation)return
        val c=documentReadContext(original,request);val ticket=DocumentNamespaceLane.ticket()
        worker.execute {
            var image:T?=null
            try {
                val result=DocumentNamespaceLane.exclusive(ticket) {
                    requireOperations(c,AccessPoint.OPERATION)
                    work(c).also {if(it is DocumentResult.Value)image=it.value;requireOperations(c)}
                }
                main.execute {
                    var delivered=false
                    try {c.publish {if(request.active) {delivered=true;publish(result)}}}
                    finally {if(!delivered)image?.close()}
                }
            } catch (_:StorageAccessRevoked) {image?.close()}
              catch (_:DocumentImageUnsupported) {
                  image?.close()
                  main.execute {c.publish {if(request.active)publish(DocumentResult.Rejected(DocumentFailure.IMAGE_UNSUPPORTED))}}
              } catch (failure:DocumentPdfUnsupported) {
                  image?.close()
                  main.execute {c.publish {if(request.active)publish(DocumentResult.Rejected(failure.reason))}}
              } catch (_:DocumentTextUnavailable) {
                  image?.close()
                  main.execute {c.publish {if(request.active)publish(DocumentResult.Rejected(DocumentFailure.TEXT_UNSUPPORTED))}}
              } catch (_:DocumentTextFormatFailure) {
                  image?.close()
                  main.execute {c.publish {if(request.active)publish(DocumentResult.Rejected(DocumentFailure.TEXT_FORMAT))}}
              } catch (_:Exception) {
                  image?.close()
                  main.execute {if(c.check(AccessPoint.STEP))blocked()}
              }
        }
    }
}
