package org.pag.data.product

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class DocumentPdfDispatchTest {
    private class Queue:Executor {
        val jobs=ArrayDeque<Runnable>()
        override fun execute(work:Runnable){jobs+=work}
        fun run(){jobs.removeFirst().run()}
    }
    private var allowed=true
    private var blocked=0
    private var published=0
    private var closed=0
    private val context=object:StorageContext {
        override val generation=7L
        override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean {if(!allowed)return false;result();return true}
    }
    private val access=object:StorageAccess {override fun acquire(scope:AccessScope)=context.takeIf{allowed}}
    private val worker=Queue()
    private val main=Queue()
    private val dispatch=DocumentReadDispatch(access,worker,main){blocked++}
    private fun page()=DocumentResult.Value(DocumentPdfPage(0,1,object:DocumentImage {override fun close(){closed++}}))

    @Test fun staleGenerationNeverQueuesWork(){
        dispatch.run<DocumentPdfPage>(DocumentPdfRequest(6),{published++}){error("No old session work")}
        assertTrue(worker.jobs.isEmpty());assertEquals(0,published)
    }
    @Test fun cancelBeforeWorkerNeverRenders(){
        val request=DocumentPdfRequest(7)
        dispatch.run<DocumentPdfPage>(request,{published++}){error("No cancelled work")}
        request.cancel();worker.run();assertTrue(main.jobs.isEmpty());assertEquals(0,blocked)
    }
    @Test fun cancelBeforeUiClosesLateBitmap(){
        val request=DocumentPdfRequest(7)
        dispatch.run(request,{_:DocumentResult<DocumentPdfPage>->published++}){page()}
        worker.run();request.cancel();main.run();assertEquals(1,closed);assertEquals(0,published)
    }
    @Test fun lockBeforeUiClosesLateBitmap(){
        dispatch.run(DocumentPdfRequest(7),{_:DocumentResult<DocumentPdfPage>->published++}){page()}
        worker.run();allowed=false;main.run();assertEquals(1,closed);assertEquals(0,published)
    }
    @Test fun lockAtRenderReturnClosesResult(){
        dispatch.run(DocumentPdfRequest(7),{_:DocumentResult<DocumentPdfPage>->published++}){page().also{allowed=false}}
        worker.run();assertEquals(1,closed);assertTrue(main.jobs.isEmpty());assertEquals(0,blocked)
    }
    @Test fun viewerFailuresAreDistinctAndStorageFaultBlocks(){
        for(reason in listOf(DocumentFailure.PDF_UNSUPPORTED,DocumentFailure.PDF_PASSWORD_PROTECTED,
            DocumentFailure.PDF_PAGE_TOO_LARGE,DocumentFailure.PDF_MEMORY_UNAVAILABLE,DocumentFailure.PDF_RENDERER_ERROR)) {
            var actual:DocumentFailure?=null
            dispatch.run<DocumentPdfPage>(DocumentPdfRequest(7),{actual=(it as DocumentResult.Rejected).reason}){throw DocumentPdfUnsupported(reason)}
            worker.run();main.run();assertEquals(reason,actual);assertEquals(0,blocked)
        }
        dispatch.run<DocumentPdfPage>(DocumentPdfRequest(7),{published++}){throw ProductStorageFailure()}
        worker.run();main.run();assertEquals(1,blocked);assertEquals(0,published)
    }
}
