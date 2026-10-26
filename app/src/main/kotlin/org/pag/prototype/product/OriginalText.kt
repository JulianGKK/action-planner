package org.pag.prototype.product

import androidx.compose.runtime.Composable
import org.pag.domain.ActiveWorkspaceBudget

/** Actual source lengths precede string construction. The fixed allowance covers numeric
 * scalars/code labels; callers pass sourceChars for variable user text. No text truncation.
 * Old immutable String aliases stay booked when a new presentation replaces the slot.
 */
@Composable internal inline fun originalText(sourceChars:Long=0,crossinline producer:()->String):String {
    val screen=LocalFormScreen.current?:return producer()
    return rememberOriginalValue(screen,screen.presentationRevision(),cost={
        ActiveWorkspaceBudget.Units(objects=16,strings=8,utf16Units=Math.addExact(256,Math.multiplyExact(sourceChars,4)),references=64)
    },producer={producer()})?:"Anzeige derzeit nicht verfügbar."
}
/** No new Iterator holder per list rendering; inline body keeps existing item identity. */
internal inline fun <T> List<T>.forOriginal(block:(T)->Unit){
    var i=0;while(i<size){block(this[i]);i++}
}
internal inline fun <T> List<T>.forOriginalIndexed(block:(Int,T)->Unit){
    var i=0;while(i<size){block(i,this[i]);i++}
}

internal inline fun <T> List<T>.findOriginal(predicate:(T)->Boolean):T? {
    var i=0;while(i<size){val value=this[i++];if(predicate(value))return value};return null
}
