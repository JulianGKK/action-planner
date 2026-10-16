package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

class DocumentTextDispatchTest {
    private class Queue:Executor {val jobs=ArrayDeque<Runnable>();override fun execute(work:Runnable){jobs+=work};fun run(){jobs.removeFirst().run()}}
    private var allowed=true;private var blocked=0;private var published=0
    private val c=object:StorageContext {
        override val generation=1L;override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=allowed
        override fun publish(result:()->Unit):Boolean {if(!allowed)return false;result();return true}
    }
    private val access=object:StorageAccess{override fun acquire(scope:AccessScope)=c.takeIf{allowed}}
    private val worker=Queue();private val main=Queue();private val dispatch=DocumentReadDispatch(access,worker,main){blocked++}
    @Test fun cancelledQueuedPageIsWipedWithoutCreatingUiText(){
        val request=DocumentTextRequest(1);val chars="sensitive synthetic".toCharArray()
        dispatch.run(request,{_:DocumentResult<DocumentTextPage>->published++}){DocumentResult.Value(DocumentTextPage(0,1,chars))}
        worker.run();request.cancel();main.run();assertEquals(0,published);assertTrue(chars.all{it=='\u0000'})
    }
    @Test fun revokeAfterPageCompletionClosesResult(){
        val chars="synthetic".toCharArray()
        dispatch.run(DocumentTextRequest(1),{_:DocumentResult<DocumentTextPage>->published++}){DocumentResult.Value(DocumentTextPage(0,1,chars)).also{allowed=false}}
        worker.run();assertTrue(chars.all{it=='\u0000'});assertEquals(0,published);assertTrue(main.jobs.isEmpty())
    }
    @Test fun missingPageAndAuthenticatedFormatErrorAreDistinctFromStorageFault(){
        var reason:DocumentFailure?=null
        dispatch.run<DocumentTextPage>(DocumentTextRequest(1),{reason=(it as DocumentResult.Rejected).reason}){throw DocumentTextUnavailable()}
        worker.run();main.run();assertEquals(DocumentFailure.TEXT_UNSUPPORTED,reason);assertEquals(0,blocked)
        dispatch.run<DocumentTextPage>(DocumentTextRequest(1),{reason=(it as DocumentResult.Rejected).reason}){throw DocumentTextFormatFailure()}
        worker.run();main.run();assertEquals(DocumentFailure.TEXT_FORMAT,reason);assertEquals(0,blocked)
        dispatch.run<DocumentTextPage>(DocumentTextRequest(1),{published++}){throw ProductStorageFailure()}
        worker.run();main.run();assertEquals(1,blocked);assertEquals(0,published)
    }
}
