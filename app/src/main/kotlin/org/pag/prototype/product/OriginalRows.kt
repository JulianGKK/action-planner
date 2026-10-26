package org.pag.prototype.product

import org.pag.domain.ActiveWorkspaceBudget

/** Metadata holder within ONE original WorkspaceUiRows. No separate registry or budget.
 * Get validates current original read authority; each actual row/action/node construction
 * reserves first. The factory is a method on the original projection, not a DTO-capturing
 * callback. Old row aliases remain charged and keep their old revoked source identity.
 */
internal class OriginalTaskRows(private val original:WorkspaceUiRows){
    private val held=LinkedHashMap<String,TaskUiRow>()
    operator fun get(id:String):TaskUiRow? {
        if(!original.live())return null
        held[id]?.let{return it}
        if(!original.reserve(rowCost))return null
        val value=original.createTaskRow(id)?:return null
        if(!original.live())return null
        held[id]=value;return value
    }
}
internal class OriginalPersonRows(private val original:WorkspaceUiRows){
    private val held=LinkedHashMap<String,PersonUiRow>()
    operator fun get(id:String):PersonUiRow? {
        if(!original.live())return null
        held[id]?.let{return it}
        if(!original.reserve(rowCost))return null
        val value=original.createPersonRow(id)?:return null
        if(!original.live())return null
        held[id]=value;return value
    }
}
internal class OriginalRoleRows(private val original:WorkspaceUiRows){
    private val held=LinkedHashMap<String,RoleUiRow>()
    operator fun get(id:String):RoleUiRow? {
        if(!original.live())return null
        held[id]?.let{return it}
        if(!original.reserve(rowCost))return null
        val value=original.createRoleRow(id)?:return null
        if(!original.live())return null
        held[id]=value;return value
    }
}
// Map growth, nodes, action/body holders and references are cumulative per actual entry.
private val rowCost=ActiveWorkspaceBudget.Units(objects=16,maps=1,mapCapacity=4,lists=1,listCapacity=8,references=64)
