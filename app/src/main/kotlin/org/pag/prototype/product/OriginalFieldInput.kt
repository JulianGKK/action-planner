package org.pag.prototype.product

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.*
import org.pag.domain.ActiveWorkspaceBudget

/** One field's fixed app-controlled callbacks/state. The caller reserves BEFORE this
 * constructor, FocusRequester, KeyboardActions, modifier callbacks or work holders exist.
 * The String received from IME is only borrowed; model gates admit each retained draft copy.
 */
internal class OriginalFieldInput(private val screen:OriginalUiFence?,private val input:FormInput,
    private val manager:FocusManager,private val change:(String)->Unit,private val changedFocus:(Boolean)->Unit) {
    val id=Any()
    val requester=FocusRequester()
    private var rectangle:Rect? by mutableStateOf(null)
    private var active by mutableStateOf(false)
    private fun live()=screen?.live()!=false
    fun value(value:String){if(live() && screen?.reserve(InputControlCost.event)!=false)change(value)}
    fun finish(){if(live())input.finish()}
    fun dispose(){if(input.focused===id)input.finish()} // Cleanup only; never a cost release.
    private fun layout(value:LayoutCoordinates){
        if(!live() || screen?.reserve(geometryCost)==false)return
        rectangle=value.boundsInRoot()
        if(active)input.focus(id,rectangle,true)
    }
    private fun focus(value:FocusState){
        if(!live() || screen?.reserve(InputControlCost.event)==false)return
        active=value.hasFocus;input.focus(id,rectangle,active);changedFocus(active)
    }
    val valueCallback:(String)->Unit=::value
    val keyboard=KeyboardActions(onDone={finish()},onNext={if(live())manager.moveFocus(FocusDirection.Next)})
    val modifier=Modifier.onGloballyPositioned(::layout).onFocusChanged(::focus)
    companion object {
        val cost=ActiveWorkspaceBudget.Units(objects=128,references=512)
        val geometryCost=ActiveWorkspaceBudget.Units(objects=2,references=8)
        val modifierCost=ActiveWorkspaceBudget.Units(objects=8,references=32)
    }
}
internal class OriginalContent(val render:@Composable ()->Unit)

/** CODE literals only; no model, dataset, ID, grant or changing field value capture. */
internal class CodeContent(val render:@Composable ()->Unit)
