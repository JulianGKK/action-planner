package org.pag.prototype.product

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import org.pag.domain.ActiveWorkspaceBudget

// These immutable modifiers contain CODE flags only: no labels, IDs, callback or data fence.
internal val productHeadingSemantics=Modifier.semantics{heading()}
internal val productSelectableGroupSemantics=Modifier.semantics{selectableGroup()}

/** Snapshot property VALUES once after admission. Replaying setters such as onClick or
 * contentDescription would create new AccessibilityAction/List holders at every query.
 * Here the framework receiver borrows the SAME admitted values; no new app payload is
 * created by populate(). Old values/arrays/configurations never produce end receipts.
 */
internal class OriginalSemantics(private val fence:OriginalUiFence?,private val epoch:Long,clear:Boolean=false,
    properties:SemanticsPropertyReceiver.()->Unit) {
    private val keys=arrayOfNulls<SemanticsPropertyKey<Any>>(16)
    private val values=arrayOfNulls<Any>(16)
    private var count=0
    init {
        val config=SemanticsConfiguration()
        properties(config)
        for(entry in config){
            check(count<16) // Internal CODE invariant: current producers set at most five properties.
            @Suppress("UNCHECKED_CAST") val key=entry.key as SemanticsPropertyKey<Any>
            keys[count]=key;values[count]=entry.value;count++
        }
    }
    fun populate(receiver:SemanticsPropertyReceiver){
        // A synchronous authority check is not a subscription to old UI state. Registering
        // that read here can schedule Material3 ChildSemantics on a detached lazy node.
        // The inline guard allocates no per-query closure; the composable modifier binding
        // still observes source/revision changes and every actual query checks authority.
        val authorized=androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
            fence?.live()!=false && (fence !is ProductInputScreenKey || fence.presentationRevision()==epoch)
        }
        if(!authorized)return
        var i=0
        while(i<count){receiver[checkNotNull(keys[i])]=checkNotNull(values[i]);i++}
    }
    private val apply:SemanticsPropertyReceiver.()->Unit={populate(this)}
    val modifier=if(clear)Modifier.clearAndSetSemantics(properties=apply) else Modifier.semantics(properties=apply)
    companion object {
        val cost=ActiveWorkspaceBudget.Units(objects=32,arrays=4,arrayPayloadBytes=1024,lists=2,listCapacity=8,maps=2,mapCapacity=16,references=128)
    }
}
