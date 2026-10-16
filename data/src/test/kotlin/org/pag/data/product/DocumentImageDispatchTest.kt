package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

class DocumentImageDispatchTest {
    private class Queue:Executor {val jobs=ArrayDeque<Runnable>();override fun execute(work:Runnable){jobs.add(work)};fun run(){jobs.removeFirst().run()}}
    private var allowed=true;private var generation=1L;private var blocked=0;private var published=0;private var closed=0
    private val c=object:StorageContext {
        override val generation get()=this@DocumentImageDispatchTest.generation
        override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean {if(!allowed)return false;result();return true}
    }
    private val access=object:StorageAccess{override fun acquire(scope:AccessScope)=c.takeIf{allowed}}
    private val worker=Queue();private val main=Queue();private val dispatch=DocumentReadDispatch(access,worker,main){blocked++}
    private val request=DocumentImageRequest()
    private fun image()=object:DocumentImage{override fun close(){closed++}}
    private fun start(work:(StorageContext)->DocumentResult<DocumentImage> = {DocumentResult.Value(image())})=dispatch.run(request,{published++},work)
    @Test fun successOnlyThroughPublicationGate(){start();assertEquals(0,published);worker.run();assertEquals(0,published);main.run();assertEquals(1,published);assertEquals(0,closed)}
    @Test fun oldUiGenerationCannotBorrowFreshReadyContext(){
        dispatch.run<DocumentImage>(DocumentImageRequest(generation=0),{published++}){error("No admission")}
        assertTrue(worker.jobs.isEmpty());assertTrue(main.jobs.isEmpty());assertEquals(0,published)
    }
    @Test fun cancelBeforeWorkerDoesNoWork(){var calls=0;start{calls++;DocumentResult.Value(image())};request.cancel();worker.run();assertEquals(0,calls);assertTrue(main.jobs.isEmpty())}
    @Test fun revokeAfterReadClosesResultWithoutPublication(){start();worker.run();allowed=false;main.run();assertEquals(0,published);assertEquals(1,closed);assertEquals(0,blocked)}
    @Test fun cancelBeforePublicationClosesResult(){start();worker.run();request.cancel();main.run();assertEquals(1,closed);assertEquals(0,published)}
    @Test fun changedGenerationCannotPublish(){start();worker.run();generation++;main.run();assertEquals(0,published);assertEquals(1,closed)}
    @Test fun revokeAfterDecoderClosesResult(){start{allowed=false;DocumentResult.Value(image())};worker.run();assertEquals(1,closed);assertTrue(main.jobs.isEmpty())}
    @Test fun decoderUnsupportedIsViewerErrorAndStorageFaultBlocks(){start{throw DocumentImageUnsupported()};worker.run();main.run();assertEquals(1,published);assertEquals(0,blocked)
        start{throw ProductStorageFailure()};worker.run();main.run();assertEquals(1,blocked)}
    @Test fun maintenanceFenceDeniesOldQueuedReader(){start();DocumentNamespaceLane.drainAndFence{};worker.run();main.run();assertEquals(0,published);assertEquals(1,blocked)}
}
