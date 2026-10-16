package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class DocumentImageModelTest {
    private var state=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
    private val jpeg=DocumentAsset("j","a","synthetic",DocumentFormat.JPEG,3,1)
    private var calls=0;private var closed=0;private var authority=true
    private val callbacks=mutableListOf<(DocumentResult<DocumentImage>)->Unit>()
    private val attempts=mutableListOf<DocumentImageRequest>()
    private val repo=object:DocumentRepository {
        override fun summary(a:String,p:(DocumentResult<DocumentSummary>)->Unit)=p(DocumentResult.Value(DocumentSummary(1)))
        override fun list(a:String,p:(DocumentResult<List<DocumentAsset>>)->Unit)=p(DocumentResult.Value(listOf(jpeg)))
        override fun readImage(a:String,e:DocumentAsset,r:DocumentImageRequest,d:DocumentImageDecoder,p:(DocumentResult<DocumentImage>)->Unit){calls++;callbacks+=p;attempts+=r}
        override fun importDocument(a:String,n:String,f:DocumentFormat,s:DocumentSource,p:(DocumentResult<Unit>)->Unit)=error("No import")
        override fun deleteDocument(a:String,e:DocumentAsset,p:(DocumentResult<Unit>)->Unit)=error("No delete")
    }
    private val model=DocumentModel({repo},{null},{state},{action},{},{},{},{authority})
    private fun image()=object:DocumentImage{override fun close(){closed++}}
    @Test fun listDoesNotReadImageAndOnlyExplicitJpegCanOpen(){model.enter();assertEquals(0,calls);model.viewImage(jpeg.copy(format=DocumentFormat.PDF));assertEquals(0,calls);model.viewImage(jpeg);assertEquals(1,calls);assertTrue(model.imageLoading)}
    @Test fun invalidOrStaleSelectionNeverStartsReader(){model.enter();for(item in listOf(jpeg.copy(id="x"),jpeg.copy(actionId="b"),jpeg.copy(displayName="changed"),jpeg.copy(format=DocumentFormat.TEXT)))model.viewImage(item);assertEquals(0,calls)}
    @Test fun deniedStatusAndNonReadyCannotOpen(){for(status in listOf(ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)){action=action.copy(status=status);model.enter();model.viewImage(jpeg)};assertEquals(0,calls)
        action=action.copy(status=ActionStatus.ACTIVE);state=SessionSnapshot(SessionState.LOCKED,2);model.enter();model.viewImage(jpeg);assertEquals(0,calls)}
    @Test fun authoritativeSessionCheckedBeforeRead(){model.enter();authority=false;model.viewImage(jpeg);assertEquals(0,calls)}
    @Test fun closeWhileLoadingCancelsAndClosesLateImage(){model.enter();model.viewImage(jpeg);model.closeImage();assertFalse(attempts.single().active);callbacks.single()(DocumentResult.Value(image()));assertEquals(1,closed);assertNull(model.image);assertNull(model.viewed)}
    @Test fun closeVisibleImageReleasesAndReopenIsExplicit(){model.enter();model.viewImage(jpeg);callbacks.single()(DocumentResult.Value(image()));assertNotNull(model.image);model.closeImage();assertEquals(1,closed);assertNull(model.viewed);assertEquals(1,calls)}
    @Test fun lockClearsAndLateCallbackCannotRestore(){model.enter();model.viewImage(jpeg);state=SessionSnapshot(SessionState.LOCKED,2);model.clear();callbacks.single()(DocumentResult.Value(image()));assertNull(model.image);assertEquals(1,closed)
        state=SessionSnapshot(SessionState.READY,3);model.enter();assertNull(model.viewed);assertEquals(1,calls)}
    @Test fun actionOrGenerationChangeRejectsLateImage(){model.enter();model.viewImage(jpeg);action=action.copy(id="b");callbacks.single()(DocumentResult.Value(image()));assertEquals(1,closed);assertNull(model.image)}
    @Test fun repeatedOpenCancelsOldAndOnlyLatestCanPublish(){model.enter();model.viewImage(jpeg);model.viewImage(jpeg);assertFalse(attempts.first().active);callbacks.first()(DocumentResult.Value(image()));assertEquals(1,closed);assertNull(model.image);callbacks.last()(DocumentResult.Value(image()));assertNotNull(model.image);model.clear();assertEquals(2,closed)}
    @Test fun unsupportedImageStopsLoadingAndKeepsDocument(){model.enter();model.viewImage(jpeg);callbacks.single()(DocumentResult.Rejected(DocumentFailure.IMAGE_UNSUPPORTED));assertFalse(model.imageLoading);assertEquals(DocumentFailure.IMAGE_UNSUPPORTED,model.error);assertEquals(listOf(jpeg),model.items)}
    @Test fun boundsDownsampleBeforeAllocation(){assertEquals(1,ImageDecodeLimits.sample(1024,1024));assertEquals(2,ImageDecodeLimits.sample(2048,2048));assertEquals(8,ImageDecodeLimits.sample(8192,8192))}
    @Test fun extremePanoramaHasBoundedDisplayEdgesDespiteSmallPixelCount(){assertEquals(32,ImageDecodeLimits.sample(65535,1));assertEquals(32,ImageDecodeLimits.sample(1,65535))}
    @Test fun invalidDimensionsAndPixelBombRejected(){for((w,h) in listOf(0 to 1,-1 to 1,Int.MAX_VALUE to Int.MAX_VALUE,65535 to 65535))assertThrows(DocumentImageUnsupported::class.java){ImageDecodeLimits.sample(w,h)}}
}
