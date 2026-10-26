package org.pag.prototype.product

import org.pag.domain.ActiveWorkspaceBudget

/** Booked before the callback and its body holder are constructed. No refund on callback
 * return, focus cleanup, disposal or owner/screen changes. No write/read authority here. */
internal class OriginalInputClick(private val screen:OriginalUiFence?,private val input:FormInput,private val epoch:Long=Long.MIN_VALUE,private val work:()->Unit):()->Unit {
    override fun invoke(){
        fun current()=screen?.live()!=false && (epoch==Long.MIN_VALUE || screen !is ProductInputScreenKey || screen.presentationRevision()==epoch)
        if(!current() || screen?.reserve(InputControlCost.event)==false)return
        input.finish()
        if(current())work()
    }
}
internal object InputControlCost {
    val event=ActiveWorkspaceBudget.Units(objects=16,references=64)
    val click=ActiveWorkspaceBudget.Units(objects=8,references=32)
    val content=ActiveWorkspaceBudget.Units(objects=8,references=32)
    val semantics=ActiveWorkspaceBudget.Units(objects=8,lists=2,listCapacity=4,references=32)
}
