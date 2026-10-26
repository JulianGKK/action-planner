package org.pag.prototype.product

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.pag.domain.ActiveWorkspaceBudget

/** Existing original owner/cancellation only; no independent registry, ledger or authority. */
internal interface OriginalUiFence {
    fun live():Boolean
    fun reserve(units:ActiveWorkspaceBudget.Units):Boolean
    fun presentationRevision():Long=0L
}
/** CODE-only refusal sentinel. It carries no data, owner, grant or service endpoint. */
internal object DeniedProductInputScope:OriginalUiFence {
    override fun live()=false
    override fun reserve(units:ActiveWorkspaceBudget.Units)=false
}
/** Primitive local UI identity only. No value/slot/DTO backreference and no end receipt. */
internal class OriginalUiEpoch {
    var version:Long=0;private set
    fun advance():Long {version=Math.incrementExact(version);return version}
}
internal class OriginalUiToken(private val parent:OriginalUiFence,private val identity:OriginalUiEpoch,private val version:Long):OriginalUiFence {
    override fun presentationRevision()=parent.presentationRevision()
    override fun live()=identity.version==version && parent.live()
    override fun reserve(units:ActiveWorkspaceBudget.Units)=live() && parent.reserve(units) && live()
}
internal class OriginalUiSlot<T:Any> {
    val identity=OriginalUiEpoch()
    var epoch:Long=Long.MIN_VALUE
    var extra:Any?=null
    var value:T?=null
}
internal object OriginalUiSlotCost {
    val holder=ActiveWorkspaceBudget.Units(objects=4,references=16)
    val sizing=ActiveWorkspaceBudget.Units(objects=2,references=8)
}
/** No Any?/vararg Long key boxing at every composition. The holder and each actual new
 * value are admitted BEFORE construction. Replacing it never refunds any old reference.
 * It is one composition's existing-owner member, not a payload registry or owner-end proof.
 */
@Composable internal inline fun <T:Any> rememberOriginalValue(fence:OriginalUiFence,epoch:Long,extra:Any?=null,
    crossinline cost:()->ActiveWorkspaceBudget.Units,crossinline producer:()->T):T? {
    val slot=remember(fence){if(!fence.reserve(OriginalUiSlotCost.holder))null else OriginalUiSlot<T>()}?:return null
    if(!fence.live())return null
    if(slot.epoch==epoch && slot.extra==extra)return slot.value
    if(!fence.reserve(OriginalUiSlotCost.sizing))return null
    val units=cost()
    if(!fence.reserve(units))return null
    val value=producer()
    if(!fence.live())return null
    slot.epoch=epoch;slot.extra=extra;slot.value=value
    return value
}
internal inline fun admitInputClick(fence:OriginalUiFence?,input:FormInput,crossinline work:()->Unit):OriginalInputClick? {
    if(fence?.reserve(InputControlCost.click)==false)return null
    return OriginalInputClick(fence,input){work()}
}

/** Reuse by actual display properties. An old token cannot follow A→B→A or reach a new
 * payload: it holds only the primitive identity, never this slot or its changing value. */
@Composable internal inline fun <T:Any> rememberOriginalPresentation(fence:OriginalUiFence,epoch:Long,extra:Any?,
    crossinline cost:()->ActiveWorkspaceBudget.Units,crossinline producer:(OriginalUiFence)->T):T? {
    val slot=remember(fence){if(!fence.reserve(OriginalUiSlotCost.holder))null else OriginalUiSlot<T>()}?:return null
    if(!fence.live())return null
    if(slot.epoch==epoch && slot.extra==extra)return slot.value
    val version=slot.identity.advance()
    slot.epoch=Long.MIN_VALUE;slot.extra=null;slot.value=null // Unpublish only; never refund old aliases.
    if(!fence.reserve(OriginalUiSlotCost.sizing))return null
    if(!fence.reserve(cost()))return null
    val token=OriginalUiToken(fence,slot.identity,version)
    val value=producer(token)
    if(!token.live())return null
    slot.epoch=epoch;slot.extra=extra;slot.value=value;return value
}
