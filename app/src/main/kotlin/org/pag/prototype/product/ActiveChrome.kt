package org.pag.prototype.product

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.pag.domain.*

/** UI metadata only. The original capability still authorizes every construction/event.
 * Revocation is latched: a retired capture can never follow a replacement capability. */
internal class ActiveChromeFence(private val model:ProductViewModel,internal val source:WorkspaceAccess,
    private val action:String,private val generation:Long):OriginalUiFence {
    private var revoked=false
    fun invalidate(){revoked=true}
    override fun live():Boolean {
        if(revoked)return false
        if(!model.chromeCurrent(source,action,generation)){revoked=true;return false}
        return true
    }
    override fun reserve(units:ActiveWorkspaceBudget.Units)=live() && model.reserveChrome(source,action,generation,units) && live()
}
internal val LocalActiveChrome=staticCompositionLocalOf{false}
private val chromeInteractionCost=ActiveWorkspaceBudget.Units(objects=32,arrays=4,arrayPayloadBytes=1024,references=128)
private val chromeAreas=listOf(ActiveArea.HUB,ActiveArea.PERSONS,ActiveArea.FUNCTIONS,ActiveArea.TASKS)
private val chromeScrollCost=ActiveWorkspaceBudget.Units(objects=32,references=64,arrays=2,arrayPayloadBytes=512)

/** Common chrome lies outside the route key. Only route content is discarded. */
@Composable internal fun StableActiveWorkspace(model:ProductViewModel,positions:ProductScrollPositions,
    frames:CompactContentFrameObserver?) {
    val fence=model.activeChromeFence()
    if(fence==null){
        if(model.workspaceError)PagErrorState() else PagLoadingState()
        return
    }
    model.prepareOriginalRows(LocalFormInput.current)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val accessibleChrome=LocalDensity.current.fontScale>1.3f || maxHeight<400.dp
    val headerLimit=maxHeight/2
    Column(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFormScreen provides fence){
            if(accessibleChrome){
                val scroll=rememberOriginalValue(fence,0,cost={chromeScrollCost}){
                    ScrollState(0)
                }
                if(scroll==null)PagErrorState() else
                    Column(Modifier.heightIn(max=headerLimit).verticalScroll(scroll)){
                        StableActiveHeader(model,fence,true)
                    }
            }else StableActiveHeader(model,fence)
        }
        if(model.workspaceRefusal==WorkspaceFailure.BUDGET_REFUSED)
            Text("Die Seite überschreitet das verbleibende Speicherbudget.",Modifier.padding(horizontal=24.dp))
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalActiveChrome provides true){
                // Keep the lazy layout itself alive; only its keyed data items change.
                ProductEntityList(model,positions,frames)
            }
        }
    }
    }
}
@Composable private fun StableActiveHeader(model:ProductViewModel,fence:OriginalUiFence,wrap:Boolean=false){
    if(!fence.live())return
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        ActionWorkspaceHeader(model)
        if(wrap)FlowRow(Modifier.fillMaxWidth(),maxItemsInEachRow=2){
            for(area in chromeAreas)ChromeNavigationButton(model,fence,area)
        }else Row(Modifier.fillMaxWidth()){
            for(area in chromeAreas)ChromeNavigationButton(model,fence,area)
        }
    }
}

@Composable private fun RowScope.ChromeNavigationButton(model:ProductViewModel,fence:OriginalUiFence,area:ActiveArea){
    val interaction=remember(fence,area){if(fence.reserve(chromeInteractionCost))MutableInteractionSource() else null}?:return
    FormTextButton({model.activeArea(area)},enabled=model.activeNavigationEnabled && !model.workspaceRefreshing && !model.detailLoading,
        modifier=Modifier.weight(1f),interactionSource=interaction){Text(area.label)}
}

/** Canonical, immutable three-row UI projection. No workspace, grant or owner endpoint. */
internal class BoundedHubRow(val area:ActiveArea,val first:String,val second:String,val click:()->Unit)

/** Only original immutable first-page controls. Route inactivity is not revocation;
 * actor/read-ticket revocation is permanent. Every event still enters the original gate. */
internal class FirstPageFence(private val model:ProductViewModel,private val identity:Any,
    private val source:WorkspaceAccess,private val state:CompactTaskState,private val area:ActiveArea):OriginalUiFence {
    @Volatile private var revoked=false
    fun invalidate(){revoked=true}
    fun bindingLive():Boolean {
        if(revoked)return false
        if(!model.originalRowsBinding(identity,source,state)){revoked=true;return false}
        return true
    }
    override fun live()=bindingLive() && model.originalRowsVisible(identity,area)
    override fun reserve(units:ActiveWorkspaceBudget.Units)=live() && model.reserveOriginalRow(identity,area,source,state,units) && live()
}
/** Called only inside the original current publisher AFTER the complete control quote. */
internal fun admittedFirstRow(model:ProductViewModel,identity:Any,area:ActiveArea,index:Int,label:String,
    fence:FirstPageFence,input:FormInput):OriginalButtonControl {
    val click=OriginalInputClick(fence,input,0){model.openOriginalRow(identity,area,index)}
    val render:@Composable RowScope.()->Unit={if(fence.live())Text(label)}
    return OriginalButtonControl(click,render)
}

internal fun originalRowsCost(n:Int):ActiveWorkspaceBudget.Units {
    require(n in 0..64)
    return ActiveWorkspaceBudget.Units(objects=16+n*32L,references=128+n*128L,lists=2,listCapacity=n.toLong(),arrays=2,arrayPayloadBytes=n*16L)
}
