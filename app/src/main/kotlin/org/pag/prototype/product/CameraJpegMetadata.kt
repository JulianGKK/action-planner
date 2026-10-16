package org.pag.prototype.product

import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayInputStream

/** Metadata/bounds only: no Bitmap allocation, recompression, file, saveAttributes or location source. */
internal fun cameraJpegRotation(bytes: ByteArray): Int {
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    if (bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outMimeType!="image/jpeg" ||
        bounds.outWidth.toLong()*bounds.outHeight>16_000_000L)
        throw CameraPhotoRejected(CameraFailure.INVALID_IMAGE)
    val exif=ExifInterface(ByteArrayInputStream(bytes))
    // CameraX is never supplied a location. Refuse unexpected embedded coordinates instead
    // of silently storing them or claiming that all EXIF has been sanitized.
    if (exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)!=null || exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)!=null)
        throw CameraPhotoRejected(CameraFailure.INVALID_IMAGE)
    return when(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> 0
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> -1
    }
}
