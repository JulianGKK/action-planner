package org.pag.prototype.product

import org.pag.domain.DocumentPdfUnsupported
import org.pag.domain.DocumentFailure
import kotlin.math.sqrt
import kotlin.math.floor

internal object PdfRenderLimits {
    const val BYTES=8_388_608L
    const val PIXELS=2_097_152L
    fun dimensions(width:Int,height:Int):Pair<Int,Int> {
        if(width<=0 || height<=0 || width>1_000_000 || height>1_000_000 ||
            maxOf(width,height).toLong()>minOf(width,height).toLong()*100)throw DocumentPdfUnsupported(DocumentFailure.PDF_PAGE_TOO_LARGE)
        val pixels=Math.multiplyExact(width.toLong(),height.toLong())
        val scale=minOf(1.0,2048.0/maxOf(width,height),sqrt(PIXELS.toDouble()/pixels))
        val w=maxOf(1,floor(width*scale).toInt());val h=maxOf(1,floor(height*scale).toInt())
        validate(w,h);return w to h
    }
    fun validate(w:Int,h:Int) {
        if(w !in 1..2048 || h !in 1..2048 || Math.multiplyExact(w.toLong(),h.toLong())>PIXELS)throw DocumentPdfUnsupported(DocumentFailure.PDF_PAGE_TOO_LARGE)
    }
}
