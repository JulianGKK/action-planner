package org.pag.prototype.product

import org.pag.domain.TaskStatus

/** Slot identity and revocable owner link only. No payload/page/TaskBoard/history/title cache.
 * Every scalar is freshly authorized; texts already delivered to Compose cannot be recalled.
 */
internal class CompactTasksPresentation internal constructor(owner:ProductViewModel, private val identity:Any) {
    @Volatile private var owner:ProductViewModel?=owner
    internal fun invalidate() {owner=null}
    fun shown():Int? = owner?.compactTasksShown(identity)
    fun activeCount():Long? = owner?.compactTasksActiveCount(identity)
    fun hasMore():Boolean? = owner?.compactTasksHasMore(identity)
    fun count(status:TaskStatus):Long? = owner?.compactTasksCount(identity,status)
    fun title(index:Int):String? = owner?.compactTaskTitle(identity,index)
    fun compositionObserved() = owner?.compactPresentationComposition(identity,true)
    fun compositionDisposed() = owner?.compactPresentationComposition(identity,false)
}
