package org.pag.prototype.product

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import org.pag.domain.ActiveWorkspaceBudget

/** One immutable checkbox presentation. Borrow label; build display/callbacks only after
 * cost() admission. The deferred semantics producer reserves before each materialization. */
internal class OriginalCheckbox(private val screen:OriginalUiFence?,input:FormInput,
    private val label:String,private val checked:Boolean,private val enabled:Boolean,work:()->Unit) {
    val text=if(checked)"✓ "+label else label
    val click=OriginalInputClick(screen,input){if(enabled)work()}
    private val action:()->Boolean={if(enabled && screen?.live()!=false){click();true}else false}
    private val semantics=OriginalSemantics(screen,Long.MIN_VALUE,clear=true){
        contentDescription=label;role=Role.Checkbox
        toggleableState=if(checked)ToggleableState.On else ToggleableState.Off
        if(!enabled)disabled()
        onClick(label=label,action=action)
    }
    val modifier=semantics.modifier
    companion object {
        fun cost(chars:Long)=ActiveWorkspaceBudget.Units(objects=64,strings=4,utf16Units=Math.addExact(32,Math.multiplyExact(chars,4)),arrays=4,arrayPayloadBytes=1024,lists=2,listCapacity=8,maps=2,mapCapacity=16,references=256)
    }
}

/** Material ID choices have no changing draft capture. The admitted child token expires
 * when label/selection/enabled properties change, independently of unrelated text edits. */
internal class OriginalChoice(private val fence:OriginalUiFence,input:FormInput,label:String,chosen:Boolean,enabled:Boolean,tab:Boolean=false,selection:Boolean=true,work:()->Unit) {
    val text=if(chosen)"✓ "+label else label
    val click=OriginalInputClick(fence,input){if(enabled)work()}
    val semantics=OriginalSemantics(fence,Long.MIN_VALUE){if(selection)selected=chosen;if(tab)role=Role.Tab}
}
