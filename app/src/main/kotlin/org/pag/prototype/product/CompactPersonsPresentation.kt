package org.pag.prototype.product

import org.pag.domain.OperationalStatus

/** Identity only: no CoreSlot, payload, page, card, String cache, session or lifetime capture.
 * Every scalar resolution goes back through the sole owner and its original authority.
 * Texts already handed to Compose cannot be recalled or claimed physically wiped.
 */
internal class CompactPersonsPresentation internal constructor(
    owner:ProductViewModel, private val identity:Any
) {
    @Volatile private var owner:ProductViewModel?=owner
    internal fun invalidate() {owner=null}
    fun shown():Int? = owner?.compactPersonsShown(identity)
    fun total():Long? = owner?.compactPersonsTotal(identity)
    fun hasMore():Boolean? = owner?.compactPersonsHasMore(identity)
    fun count(status:OperationalStatus):Long? = owner?.compactPersonsCount(identity,status)
    fun pseudonym(index:Int):String? = owner?.compactPersonPseudonym(identity,index)
    fun compositionObserved() = owner?.compactPresentationComposition(identity,true)
    fun compositionDisposed() = owner?.compactPresentationComposition(identity,false)
}
