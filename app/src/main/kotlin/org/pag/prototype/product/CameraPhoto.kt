package org.pag.prototype.product

import org.pag.domain.DocumentRules
import org.pag.domain.DocumentSource
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

internal enum class CameraFailure {
    PERMISSION, NO_CAMERA, INITIALIZATION, CAPTURE, TOO_LARGE, INVALID_IMAGE, ORIENTATION
}
internal class CameraPhotoRejected(val reason: CameraFailure) : Exception()

/** Adapter owns an ImageProxy. Always closed, including denial before the first byte copy. */
internal interface CameraFrame : AutoCloseable {
    val jpeg: Boolean
    val rotation: Int
    val buffer: ByteBuffer
}
internal class CameraCaptureTicket {
    private val active = AtomicBoolean(true)
    private val claimed = AtomicBoolean(false)
    fun claim() = active.get() && claimed.compareAndSet(false, true)
    fun valid() = active.get()
    fun cancel() { active.set(false) }
}

/** One mutable owned JPEG, no URI/Bitmap/file. Close also revokes an already opened stream.
 * Lock ordering: no Session/Camera/DB calls while holding this buffer's monitor.
 */
internal class CameraPhoto private constructor(private var bytes: ByteArray?) : DocumentSource, AutoCloseable {
    private var opened = false
    val size: Int @Synchronized get() = bytes?.size ?: 0
    @Synchronized override fun open(): InputStream {
        if (opened || bytes == null) throw IOException()
        opened = true
        return object : InputStream() {
            private var offset = 0
            override fun read(): Int = synchronized(this@CameraPhoto) {
                val data = bytes ?: throw IOException()
                if (offset == data.size) -1 else data[offset++].toInt() and 255
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(this@CameraPhoto) {
                if (off < 0 || len < 0 || off > b.size - len) throw IndexOutOfBoundsException()
                val data = bytes ?: throw IOException()
                if (len == 0) return@synchronized 0
                if (offset == data.size) return@synchronized -1
                val count = minOf(len, data.size - offset)
                data.copyInto(b, off, offset, offset + count); offset += count; count
            }
            override fun close() = this@CameraPhoto.close()
        }
    }
    @Synchronized override fun close() { bytes?.fill(0); bytes = null }

    companion object {
        /** inspectOrientation must use an in-memory metadata reader, never saveAttributes. */
        fun capture(frame: CameraFrame, ticket: CameraCaptureTicket,
            inspectOrientation: (ByteArray) -> Int): CameraPhoto? {
            var owned: ByteArray? = null
            var transferred = false
            try {
                if (!ticket.claim()) return null
                if (!frame.jpeg) throw CameraPhotoRejected(CameraFailure.INVALID_IMAGE)
                val source = frame.buffer.duplicate()
                val size = source.remaining()
                if (size.toLong() > DocumentRules.MAX_DOCUMENT_BYTES) throw CameraPhotoRejected(CameraFailure.TOO_LARGE)
                if (size < 5) throw CameraPhotoRejected(CameraFailure.INVALID_IMAGE)
                if (!ticket.valid()) return null
                val data = ByteArray(size); owned = data
                var offset = 0
                while (source.hasRemaining()) {
                    if (!ticket.valid()) return null
                    val n = minOf(source.remaining(), 32 * 1024)
                    source.get(data, offset, n); offset += n
                }
                if (!ticket.valid()) return null
                if (data[0] != 0xff.toByte() || data[1] != 0xd8.toByte() || data[2] != 0xff.toByte() ||
                    data[size-2] != 0xff.toByte() || data[size-1] != 0xd9.toByte())
                    throw CameraPhotoRejected(CameraFailure.INVALID_IMAGE)
                // Back camera only: reject ambiguous/mirrored/incorrect orientation instead of
                // silently storing sideways pixels or re-encoding the entire JPEG.
                if (frame.rotation !in setOf(0,90,180,270) || inspectOrientation(data) != frame.rotation)
                    throw CameraPhotoRejected(CameraFailure.ORIENTATION)
                if (!ticket.valid()) return null
                transferred = true
                return CameraPhoto(data)
            } finally {
                try { frame.close() } catch (error: Exception) { owned?.fill(0);throw error }
                finally { if (!transferred) owned?.fill(0) }
            }
        }
    }
}
