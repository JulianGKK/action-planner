package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class DocumentCameraModelTest {
    private var allowed=true
    private var interactions=0
    private var releases=0
    private val model=DocumentCameraModel({allowed},{interactions++})
    private fun photo():CameraPhoto {
        val frame=object:CameraFrame {
            override val jpeg=true;override val rotation=0
            override val buffer=ByteBuffer.wrap(byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xff.toByte(),0xd9.toByte()))
            override fun close(){}
        }
        return CameraPhoto.capture(frame,CameraCaptureTicket()){0}!!
    }
    private fun preview():Long {
        model.start();val epoch=model.currentEpoch();model.attach(epoch){releases++};model.previewReady(epoch);return epoch
    }
    private fun captured():CameraPhoto {
        val e=preview();val ticket=model.beginCapture()!!;val photo=photo();model.captured(e,ticket,photo);return photo
    }
    @Test fun noCaptureBeforeReady() {model.start();assertNull(model.beginCapture());assertEquals(CameraMode.PREVIEW,model.mode)}
    @Test fun doubleTapOnlyCreatesOneTicket() {preview();val t=model.beginCapture();assertNotNull(t);assertNull(model.beginCapture());assertEquals(CameraMode.CAPTURING,model.mode)}
    @Test fun captureRequiresTwoConsciousConfirmationsBeforeSourceHandoff() {
        val photo=captured();assertEquals(CameraMode.REVIEW,model.mode);assertNull(model.sourceForSave());assertEquals(1,releases)
        model.accept();assertEquals(CameraMode.NAMING,model.mode);assertSame(photo,model.sourceForSave());assertNull(model.sourceForSave())
        model.clear();assertEquals(0,photo.size)
    }
    @Test fun lockWhilePreviewReleasesExactlyOnce() {preview();allowed=false;model.clear();model.clear();assertEquals(1,releases);assertEquals(CameraMode.CLOSED,model.mode)}
    @Test fun lockDuringCaptureCancelsTicketAndDropsLatePhoto() {
        val e=preview();val t=model.beginCapture()!!;allowed=false;model.clear();assertFalse(t.valid())
        val late=photo();model.captured(e,t,late);assertEquals(0,late.size);assertEquals(CameraMode.CLOSED,model.mode)
    }
    @Test fun lockAfterCaptureWipesOwnedPhoto() {val p=captured();allowed=false;model.clear();assertEquals(0,p.size);assertNull(model.sourceForSave())}
    @Test fun lockDuringImportRevokesAlreadyOpenedSource() {
        captured();model.accept();val p=model.sourceForSave()!!;val stream=p.open();allowed=false;model.clear()
        assertThrows(java.io.IOException::class.java){stream.read()};assertEquals(0,p.size)
    }
    @Test fun retakeWipesPreviousPhotoAndAdvancesEpoch() {
        val p=captured();val before=model.currentEpoch();model.retake();assertEquals(0,p.size)
        assertEquals(CameraMode.PREVIEW,model.mode);assertFalse(model.live(before));assertFalse(model.ready)
    }
    @Test fun cancelAfterCaptureDoesNotSaveAnything() {val p=captured();model.cancel();assertEquals(0,p.size);assertNull(model.sourceForSave());assertEquals(CameraMode.CLOSED,model.mode)}
    @Test fun authorityChangeBlocksReadyCaptureAcceptAndSave() {
        val e=preview();allowed=false;assertFalse(model.live(e));assertNull(model.beginCapture());model.accept();assertNull(model.sourceForSave());model.clear()
    }
    @Test fun interactionThatRevokesAuthorityNeverStartsCamera() {
        val c=DocumentCameraModel({allowed},{allowed=false});c.start();assertEquals(CameraMode.CLOSED,c.mode)
    }
    @Test fun obsoleteResourceIsReleasedWithoutBinding() {
        val old=preview();model.clear();model.attach(old){releases++};assertEquals(2,releases)
    }
    @Test fun lateInitializationCannotReopenAfterRetakeOrCancel() {
        val old=preview();model.cancel();model.start();model.previewReady(old);assertFalse(model.ready)
        model.fail(old,CameraFailure.INITIALIZATION);assertNull(model.error)
    }
    @Test fun duplicateResultCannotReplaceReviewPhoto() {
        val e=preview();val t=model.beginCapture()!!;val p=photo();model.captured(e,t,p)
        val second=photo();model.captured(e,t,second);assertEquals(0,second.size);assertEquals(p.size,model.size);model.clear()
    }
    @Test fun failureReleasesResourcesAndDoesNotAutoRetry() {
        val e=preview();model.fail(e,CameraFailure.CAPTURE);assertEquals(CameraMode.CLOSED,model.mode)
        assertEquals(CameraFailure.CAPTURE,model.error);assertEquals(1,releases);assertNull(model.beginCapture())
    }
    @Test fun permissionMissingNeverStartsPreview() {model.permissionMissing();assertEquals(CameraFailure.PERMISSION,model.error);assertEquals(CameraMode.CLOSED,model.mode)}
    @Test fun callbacksAreNotUserInteraction() {
        model.start();val e=model.currentEpoch();val before=interactions
        model.attach(e){};model.previewReady(e);assertEquals(before,interactions)
        val t=model.beginCapture()!!;val count=interactions;model.captured(e,t,photo());assertEquals(count,interactions);model.clear()
    }
    @Test fun releaseFailureCannotLeaveAcceptedPhotoOwnedByNobody() {
        val e=preview();model.attach(e){throw IllegalStateException()};val t=model.beginCapture()!!;val p=photo()
        assertThrows(IllegalStateException::class.java){model.captured(e,t,p)}
        assertEquals(0,p.size);assertNull(model.sourceForSave());model.clear()
    }
}
