package org.pag.data.product

import java.io.File
import org.pag.domain.*

/** Existing namespace lane and read-only inventory; parser never sees partial plaintext. */
internal class DocumentPdfRead(root:File) {
    private val inventory=DocumentFileInventory(root)
    fun render(c:StorageContext,s:DocumentReadSnapshot,page:Int,renderer:DocumentPdfRenderer,request:DocumentPdfRequest?=null):DocumentPdfPage {
        DocumentNamespaceLane.requireExclusive();requireOperations(c)
        if(s.asset.format!=DocumentFormat.PDF || page !in 0 until 10000)throw DocumentPdfUnsupported()
        val inspecting=System.nanoTime()
        val files=inventory.inspect(c,s.staging,s.required+s.optional)
        if(!files.rootPresent || files.staging!=s.staging || !files.files.containsAll(s.required))throw ProductStorageFailure()
        request?.measured(DocumentPdfPhase.INVENTORY,inspecting)
        renderer.create(s.asset.plaintextSize).use {input ->
            val reading=System.nanoTime()
            inventory.readPdf(c,s.blob,s.staging,files.files,s.asset.id,s.key,s.asset.plaintextSize,input)
            request?.measured(DocumentPdfPhase.AUTH_AND_MEMFD,reading)
            s.close();requireOperations(c)
            var result:DocumentPdfPage?=null
            try {result=input.render(page){requireOperations(c)};requireOperations(c);return result}
            catch(failure:Throwable){result?.close();throw failure}
        }
    }
}
