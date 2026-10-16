package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.DocumentRules
import java.io.IOException
import java.nio.ByteBuffer

class CameraPhotoTest {
    private val jpeg=byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),1,0xff.toByte(),0xd9.toByte())
    private class Frame(bytes:ByteArray,override val rotation:Int=0,override val jpeg:Boolean=true):CameraFrame {
        var closes=0;var reads=0
        private val data=ByteBuffer.wrap(bytes)
        override val buffer get()=data.asReadOnlyBuffer().also { reads++ }
        override fun close(){closes++}
    }
    private fun capture(frame:Frame)=CameraPhoto.capture(frame,CameraCaptureTicket()){0}!!
    @Test fun unchangedBytesOneOpenAndCloseWipesOwnedCopy() {
        val f=Frame(jpeg);var owned:ByteArray?=null
        val photo=CameraPhoto.capture(f,CameraCaptureTicket()){owned=it;0}!!
        assertEquals(1,f.closes);assertEquals(jpeg.size,photo.size)
        val stream=photo.open();assertThrows(IOException::class.java){photo.open()}
        assertArrayEquals(jpeg,stream.readBytes());stream.close()
        assertEquals(0,photo.size);assertTrue(owned!!.all {it==0.toByte()})
        assertArrayEquals(jpeg,byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),1,0xff.toByte(),0xd9.toByte()))
    }
    @Test fun revokeInvalidatesAlreadyOpenedStream() {
        val photo=capture(Frame(jpeg));val stream=photo.open();assertEquals(255,stream.read())
        photo.close();assertThrows(IOException::class.java){stream.read()};assertThrows(IOException::class.java){photo.open()}
    }
    @Test fun cancelledTicketNeverAccessesImageBufferAndAlwaysClosesProxy() {
        val t=CameraCaptureTicket();t.cancel();val f=Frame(jpeg)
        assertNull(CameraPhoto.capture(f,t){error("No metadata read")});assertEquals(0,f.reads);assertEquals(1,f.closes)
    }
    @Test fun duplicateCallbackClosesWithoutCopyingSecondImage() {
        val t=CameraCaptureTicket();val first=CameraPhoto.capture(Frame(jpeg),t){0}!!
        val second=Frame(jpeg);assertNull(CameraPhoto.capture(second,t){error("No second read")})
        assertEquals(0,second.reads);assertEquals(1,second.closes);first.close()
    }
    @Test fun exactMaximumAndFirstByteOver() {
        val data=ByteArray(DocumentRules.MAX_DOCUMENT_BYTES.toInt());jpeg.copyInto(data,0,0,3)
        data[data.lastIndex-1]=0xff.toByte();data[data.lastIndex]=0xd9.toByte()
        capture(Frame(data)).close()
        val over=Frame(ByteArray(data.size+1));val error=assertThrows(CameraPhotoRejected::class.java){capture(over)}
        assertEquals(CameraFailure.TOO_LARGE,error.reason);assertEquals(1,over.closes)
    }
    @Test fun invalidHeaderTruncatedEndAndNonJpegRejected() {
        for(bytes in listOf(byteArrayOf(),jpeg.copyOf(3),jpeg.copyOf().also{it[0]=0},jpeg.copyOf().also{it[it.lastIndex]=0})) {
            val f=Frame(bytes);assertThrows(CameraPhotoRejected::class.java){capture(f)};assertEquals(1,f.closes)
        }
        val f=Frame(jpeg,jpeg=false);assertThrows(CameraPhotoRejected::class.java){capture(f)};assertEquals(1,f.closes)
    }
    @Test fun rotationMismatchAndMirrorAreRejectedWithWipe() {
        var data:ByteArray?=null;val f=Frame(jpeg,90)
        val failure=assertThrows(CameraPhotoRejected::class.java){CameraPhoto.capture(f,CameraCaptureTicket()){data=it;0}}
        assertEquals(CameraFailure.ORIENTATION,failure.reason);assertTrue(data!!.all{it==0.toByte()});assertEquals(1,f.closes)
        assertThrows(CameraPhotoRejected::class.java){CameraPhoto.capture(Frame(jpeg),CameraCaptureTicket()){-1}}
    }
    @Test fun eachQuarterRotationCanBePreservedWithoutReencoding() {
        for(rotation in listOf(0,90,180,270)) {
            val photo=CameraPhoto.capture(Frame(jpeg,rotation),CameraCaptureTicket()){rotation}!!
            photo.open().use {assertArrayEquals(jpeg,it.readBytes())}
        }
    }
    @Test fun revokeDuringMetadataDiscardsOwnedBuffer() {
        val t=CameraCaptureTicket();var data:ByteArray?=null;val f=Frame(jpeg)
        assertNull(CameraPhoto.capture(f,t){data=it;t.cancel();0})
        assertEquals(1,f.closes);assertTrue(data!!.all{it==0.toByte()})
    }
    @Test fun parserErrorClosesFrameAndWipesCopy() {
        var data:ByteArray?=null;val f=Frame(jpeg)
        assertThrows(IOException::class.java){CameraPhoto.capture(f,CameraCaptureTicket()){data=it;throw IOException()}}
        assertEquals(1,f.closes);assertTrue(data!!.all{it==0.toByte()})
    }
    @Test fun proxyCloseFailureAlsoWipesCopy() {
        var data:ByteArray?=null
        val f=object:CameraFrame {override val rotation=0;override val jpeg=true;override val buffer=ByteBuffer.wrap(this@CameraPhotoTest.jpeg);override fun close(){throw IOException()}}
        assertThrows(IOException::class.java){CameraPhoto.capture(f,CameraCaptureTicket()){data=it;0}}
        assertTrue(data!!.all{it==0.toByte()})
    }
    @Test fun partialReadsOffsetsEmptyReadAndEof() {
        val photo=capture(Frame(jpeg));val stream=photo.open();val bytes=ByteArray(20)
        assertEquals(0,stream.read(bytes,0,0));assertEquals(jpeg.size,stream.read(bytes,2,18))
        assertEquals(-1,stream.read(bytes,0,20));assertEquals(-1,stream.read())
        assertArrayEquals(jpeg,bytes.copyOfRange(2,2+jpeg.size));stream.close()
    }
}
