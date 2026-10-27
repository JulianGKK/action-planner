package org.pag.prototype.product

/** No core or summary cache. One fixed metadata-only card list and eight original callbacks.
 * Revocation cuts the sole owner link, including all previously handed-out callbacks.
 */
internal class CompactHubPresentation internal constructor(owner:ProductViewModel,private val identity:Any) {
    @Volatile private var owner:ProductViewModel?=owner
    private val cards=listOf(HubCard(ActiveArea.PERSONS,emptyList()),
        HubCard(ActiveArea.FUNCTIONS,emptyList()),HubCard(ActiveArea.TASKS,emptyList()))
    private val persons:()->Unit={open(ActiveArea.PERSONS)}
    private val functions:()->Unit={open(ActiveArea.FUNCTIONS)}
    private val tasks:()->Unit={open(ActiveArea.TASKS)}
    private val protocol:()->Unit={open(ActiveArea.PROTOCOL)}
    private val material:()->Unit={open(ActiveArea.MATERIAL)}
    private val costs:()->Unit={open(ActiveArea.COSTS)}
    private val memory:()->Unit={open(ActiveArea.MEMORY_REPORTS)}
    private val details:()->Unit={open(ActiveArea.DETAILS)}
    internal fun invalidate(){owner=null}
    fun displayRows():List<BoundedHubRow>?=owner?.compactHubRows(identity)
    fun size()=3
    fun card(index:Int):HubCard? {
        if(index !in 0..2 || owner?.compactHubCurrent(identity)!=true)return null
        return cards[index] // Only enum + empty summary; never resolved data or a core reference.
    }
    fun value(index:Int,line:Int):Long? {
        if(index !in 0..2 || line !in 0..1)return null
        return owner?.compactHubValue(identity,index,line)
    }
    fun navigation(area:ActiveArea):(() -> Unit)?=when(area) {
        ActiveArea.PERSONS->persons;ActiveArea.FUNCTIONS->functions;ActiveArea.TASKS->tasks
        ActiveArea.PROTOCOL->protocol;ActiveArea.MATERIAL->material;ActiveArea.COSTS->costs
        ActiveArea.MEMORY_REPORTS->memory;ActiveArea.DETAILS->details;else->null
    }
    private fun open(area:ActiveArea){owner?.compactHubOpen(identity,area)}
    fun compositionObserved()=owner?.compactPresentationComposition(identity,true)
    fun compositionDisposed()=owner?.compactPresentationComposition(identity,false)
}
