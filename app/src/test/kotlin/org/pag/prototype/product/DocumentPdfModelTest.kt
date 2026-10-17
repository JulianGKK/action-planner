package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class DocumentPdfModelTest {
    private var state=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
    private val asset=DocumentAsset("t","a","synthetic",DocumentFormat.PDF,50,1)
    private var authority=true;private var interactions=0;private var onInteraction:()->Unit={}
    private data class Read(val page:Int,val request:DocumentPdfRequest,val done:(DocumentResult<DocumentPdfPage>)->Unit)
    private val reads=mutableListOf<Read>()
    private val repo=object:DocumentRepository {
        override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit)=publish(DocumentResult.Value(listOf(asset)))
        override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit)=publish(DocumentResult.Value(DocumentSummary(1)))
        override fun readPdf(actionId:String,expected:DocumentAsset,page:Int,request:DocumentPdfRequest,renderer:DocumentPdfRenderer,publish:(DocumentResult<DocumentPdfPage>)->Unit){assertEquals("a",actionId);assertEquals(asset,expected);reads+=Read(page,request,publish)}
        override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit)=error("No import")
        override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit)=error("No delete")
    }
    private val model=DocumentModel({repo},{null},{state},{action},{interactions++;onInteraction()},{},{},{authority})
    private fun open(){model.bindPdfRenderer(DocumentPdfRenderer{error("fake only")});model.enter();model.viewPdf(asset)}
    private var closed=0
    private fun loaded(index:Int=reads.last().page,count:Int=3){reads.last().done(DocumentResult.Value(DocumentPdfPage(index,count,object:DocumentImage{override fun close(){closed++}})))}
    @Test fun explicitOpenOnly(){model.enter();assertTrue(reads.isEmpty());open();assertEquals(1,reads.size);assertTrue(model.pdfLoading);loaded();assertNotNull(model.pdfPage)}
    @Test fun exactPdfOnly(){model.bindPdfRenderer(DocumentPdfRenderer{error("fake")});model.enter();for(item in DocumentFormat.entries.filter{it!=DocumentFormat.PDF}.map{asset.copy(format=it)}+listOf(asset.copy(id="x"),asset.copy(actionId="b"),asset.copy(displayName="changed")))model.viewPdf(item);assertTrue(reads.isEmpty())}
    @Test fun statuses(){for(status in ActionStatus.entries){action=action.copy(status=status);model.clear();open();assertEquals(DocumentRules.mayRead(status),model.pdfViewed!=null)}}
    @Test fun noAuthorityNoRead(){authority=false;open();assertTrue(reads.isEmpty())}
    @Test fun nextPreviousNoParallelJobs(){open();loaded(count=2);model.previousPdfPage();assertEquals(1,reads.size);model.nextPdfPage();assertEquals(1,closed);assertNull(model.pdfPage);model.nextPdfPage();assertEquals(2,reads.size);loaded(count=2);model.nextPdfPage();assertEquals(2,reads.size);model.previousPdfPage();assertEquals(0,reads.last().page)}
    @Test fun lockCancelsAndLatePageIsClosed(){open();state=SessionSnapshot(SessionState.LOCKED,2);model.clear();loaded();assertEquals(1,closed);assertNull(model.pdfPage);assertFalse(reads.last().request.active);state=SessionSnapshot(SessionState.READY,3);model.enter();assertNull(model.pdfViewed)}
    @Test fun closeVisibleAndLoading(){open();loaded();model.closePdf();assertEquals(1,closed);open();model.closePdf();loaded();assertEquals(2,closed);assertNull(model.pdfPage)}
    @Test fun actionGenerationAndAuthorityGateLateResults(){for(mode in 0..2){action=action.copy(id="a");state=SessionSnapshot(SessionState.READY,1);authority=true;open();when(mode){0->action=action.copy(id="b");1->state=SessionSnapshot(SessionState.READY,8);else->authority=false};loaded();assertNull(model.pdfPage);model.clear()};assertEquals(3,closed)}
    @Test fun wrongPageAndErrorDoNotRetry(){open();loaded(index=1);assertNull(model.pdfPage);assertEquals(DocumentFailure.PDF_UNSUPPORTED,model.error);assertEquals(1,closed);reads.last().done(DocumentResult.Rejected(DocumentFailure.STALE));assertFalse(model.pdfLoading);assertEquals(1,reads.size)}
    @Test fun resultIsNotInteraction(){open();val before=interactions;loaded();assertEquals(before,interactions)}
    @Test fun dimensionsStayWithinHardLimits(){for((w,h) in listOf(1 to 1,200 to 300,1000000 to 1000000,20000 to 200)){val(a,b)=PdfRenderLimits.dimensions(w,h);assertTrue(a<=2048 && b<=2048);assertTrue(a.toLong()*b<=PdfRenderLimits.PIXELS)}}
    @Test fun invalidDimensionsAndOverflowRejected(){for((w,h) in listOf(0 to 3,-1 to 1,Int.MAX_VALUE to Int.MAX_VALUE,1000000 to 1))assertThrows(DocumentPdfUnsupported::class.java){PdfRenderLimits.dimensions(w,h)}}
}
