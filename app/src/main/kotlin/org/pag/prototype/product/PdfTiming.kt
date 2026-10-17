package org.pag.prototype.product

import android.util.Log
import org.pag.domain.DocumentPdfPhase

/** Explicit debug opt-in only. No user data or exception text. */
internal fun pdfTiming(phase:DocumentPdfPhase,millis:Long){
    if(org.pag.prototype.BuildConfig.DEBUG && Log.isLoggable("PaGPdfTiming",Log.DEBUG))
        Log.i("PaGPdfTiming","${phase.name} ms=$millis")
}
