package org.pag.prototype.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

interface CameraPermission {
    fun granted(): Boolean
    fun request()
}
internal enum class CameraMode { CLOSED, PREVIEW, CAPTURING, REVIEW, NAMING, SAVING }

/** RAM-only child of the existing document workspace; no authorization or persisted navigation. */
internal class DocumentCameraModel(private val allowed: () -> Boolean, private val interaction: () -> Unit) {
    var mode by mutableStateOf(CameraMode.CLOSED); private set
    var error by mutableStateOf<CameraFailure?>(null); private set
    var ready by mutableStateOf(false); private set
    private var epoch = 0L
    private var ticket: CameraCaptureTicket? = null
    private var photo: CameraPhoto? = null
    private var release: (() -> Unit)? = null
    val size get() = photo?.size ?: 0
    fun live(e: Long) = e == epoch && allowed() && mode in setOf(CameraMode.PREVIEW,CameraMode.CAPTURING)
    fun currentEpoch() = epoch
    private fun act(work: () -> Unit) {
        if (!allowed()) return
        val before = epoch
        interaction()
        if (allowed() && before == epoch) work()
    }
    fun clear() {
        epoch++; ticket?.cancel(); ticket = null
        val close = release; release = null
        try { close?.invoke() } finally {
            photo?.close(); photo = null; mode = CameraMode.CLOSED; error = null; ready = false
        }
    }
    fun start() = act { clear(); mode = CameraMode.PREVIEW }
    fun permissionMissing() { clear(); error = CameraFailure.PERMISSION }
    fun attach(e: Long, close: () -> Unit) {
        if (!live(e)) { close(); return }
        release?.invoke(); release = close
    }
    fun previewReady(e: Long) { if (live(e)) ready = true }
    fun fail(e: Long, reason: CameraFailure) {
        if (!live(e)) return
        clear(); error = reason
    }
    fun beginCapture(): CameraCaptureTicket? {
        var result: CameraCaptureTicket? = null
        act { if (mode == CameraMode.PREVIEW && ready) {
            result = CameraCaptureTicket(); ticket = result; mode = CameraMode.CAPTURING
        } }
        return result
    }
    fun captured(e: Long, expected: CameraCaptureTicket, value: CameraPhoto?) {
        if (!live(e) || ticket !== expected || !expected.valid() || mode != CameraMode.CAPTURING || value == null) {
            value?.close(); return
        }
        expected.cancel(); ticket = null
        val close = release; release = null
        try { close?.invoke() } catch (failure: Exception) { value.close(); throw failure }
        ready = false
        photo = value; mode = CameraMode.REVIEW
    }
    fun accept() = act { if (mode == CameraMode.REVIEW && photo != null) mode = CameraMode.NAMING }
    fun retake() = act { if (mode in setOf(CameraMode.REVIEW,CameraMode.NAMING)) { clear(); mode = CameraMode.PREVIEW } }
    fun cancel() = act { clear() }
    fun sourceForSave(): CameraPhoto? {
        if (!allowed() || mode != CameraMode.NAMING || photo == null) return null
        mode = CameraMode.SAVING
        return photo
    }
}
