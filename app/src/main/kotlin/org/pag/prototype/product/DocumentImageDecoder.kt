package org.pag.prototype.product

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.media.ExifInterface
import org.pag.domain.*
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference

/** Recycle unrendered results promptly; a displayed bitmap must not race the render thread. */
internal class BitmapDocumentImage(bitmap:Bitmap):DocumentImage {
    private val owned=AtomicReference<Bitmap?>(bitmap)
    private val monitor=Any()
    private var displayed=false
    val bitmap get()=owned.get()
    fun forDisplay():Bitmap?=synchronized(monitor) {displayed=true;owned.get()}
    override fun close() {
        val recycle=synchronized(monitor) {owned.getAndSet(null).takeUnless {displayed}}
        recycle?.recycle()
    }
}

internal object ImageDecodeLimits {
    const val PIXELS=2_097_152L
    const val BITMAP_BYTES=8_388_608L
    const val SOURCE_PIXELS=268_435_456L
    const val SIDE=2048L
    fun sample(width:Int,height:Int):Int {
        if(width<=0 || height<=0 || width>65_535 || height>65_535 ||
            Math.multiplyExact(width.toLong(),height.toLong())>SOURCE_PIXELS) throw DocumentImageUnsupported()
        var sample=1
        while(Math.multiplyExact((width.toLong()+sample-1)/sample,(height.toLong()+sample-1)/sample)>PIXELS ||
            (width.toLong()+sample-1)/sample>SIDE || (height.toLong()+sample-1)/sample>SIDE) sample=Math.multiplyExact(sample,2)
        return sample
    }
}

/** Invoked synchronously on the existing data worker, only after authenticated EOF. */
internal object AndroidDocumentImageDecoder:DocumentImageDecoder {
    override fun decode(bytes:ByteArray,format:DocumentFormat,check:()->Unit):DocumentImage {
        if(format !in setOf(DocumentFormat.JPEG,DocumentFormat.PNG))throw DocumentImageUnsupported()
        var bitmap:Bitmap?=null
        try {
            check()
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true;inScaled=false}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            check()
            val expected=if(format==DocumentFormat.JPEG) "image/jpeg" else "image/png"
            if(bounds.outMimeType!=expected)throw DocumentImageUnsupported()
            val sample=ImageDecodeLimits.sample(bounds.outWidth,bounds.outHeight)
            val orientation=if(format==DocumentFormat.JPEG) ByteArrayInputStream(bytes).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            } else ExifInterface.ORIENTATION_NORMAL
            check()
            val options=BitmapFactory.Options().apply {
                inSampleSize=sample;inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888
                inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
            }
            bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: throw DocumentImageUnsupported()
            validate(bitmap);check()
            val matrix=orientationMatrix(orientation)
            if(!matrix.isIdentity) {
                val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,false)
                if(rotated!==bitmap) {val old=bitmap;bitmap=rotated;old.recycle()}
                validate(bitmap);check()
            }
            return BitmapDocumentImage(bitmap).also {bitmap=null}
        } catch (_:OutOfMemoryError) {throw DocumentImageUnsupported()}
          catch (_:IllegalArgumentException) {throw DocumentImageUnsupported()}
          catch (_:java.io.IOException) {throw DocumentImageUnsupported()}
        finally {bitmap?.recycle()}
    }
    private fun validate(bitmap:Bitmap) {
        if(bitmap.config!=Bitmap.Config.ARGB_8888 || bitmap.width>ImageDecodeLimits.SIDE || bitmap.height>ImageDecodeLimits.SIDE || Math.multiplyExact(bitmap.width.toLong(),bitmap.height.toLong())>ImageDecodeLimits.PIXELS ||
            bitmap.allocationByteCount.toLong()>ImageDecodeLimits.BITMAP_BYTES)throw DocumentImageUnsupported()
    }
    internal fun orientationMatrix(value:Int)=Matrix().apply {
        when(value) {
            ExifInterface.ORIENTATION_UNDEFINED,ExifInterface.ORIENTATION_NORMAL -> Unit
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {setRotate(90f);postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {setRotate(270f);postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            else -> throw DocumentImageUnsupported()
        }
    }
}
