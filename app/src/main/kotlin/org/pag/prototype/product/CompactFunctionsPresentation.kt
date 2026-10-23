package org.pag.prototype.product

/** Original slot identity, revocable owner link and immutable scalar index bound only.
 * No role/card/list cache; no 32-role truncation. Previously exported texts cannot be recalled.
 */
internal class CompactFunctionsPresentation internal constructor(
    owner:ProductViewModel,private val identity:Any,private val capacity:Int
) {
    @Volatile private var owner:ProductViewModel?=owner
    internal fun invalidate(){owner=null}
    fun size():Int?=owner?.compactFunctionsSize(identity)
    fun onSite():Long?=owner?.compactFunctionsOnSite(identity)
    fun withoutFunction():Long?=owner?.compactFunctionsWithout(identity)
    fun name(index:Int):String? {
        if(index !in 0 until capacity)return null
        return owner?.compactFunctionName(identity,index)
    }
    fun count(index:Int):Long? {
        if(index !in 0 until capacity)return null
        return owner?.compactFunctionCount(identity,index)
    }
    fun line(index:Int):String? {
        if(index !in 0 until capacity)return null
        return owner?.compactFunctionLine(identity,index)
    }
    fun compositionObserved()=owner?.compactPresentationComposition(identity,true)
    fun compositionDisposed()=owner?.compactPresentationComposition(identity,false)
}
