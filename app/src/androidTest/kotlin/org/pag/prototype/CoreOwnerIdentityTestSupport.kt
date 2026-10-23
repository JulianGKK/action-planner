package org.pag.prototype

import org.pag.prototype.product.*

/** androidTest ONLY: route handle -> opaque original identity. No payload/slot/lifetime output.
 * This is not in any application APK. Historical raw heap-census probes retain their explicit
 * local private-field inspection, not a replacement borrowing API on the product ViewModel.
 */
internal fun coreOwnerIdentity(model:ProductViewModel):Any? {
    val handle:Any?=when(model.activeArea) {
        ActiveArea.HUB->model.compactHubPresentation
        ActiveArea.PERSONS->model.compactPersonsPresentation
        ActiveArea.TASKS->model.compactTasksPresentation
        ActiveArea.FUNCTIONS->model.compactFunctionsPresentation
        else->null
    }
    return handle?.javaClass?.getDeclaredField("identity")?.let{it.isAccessible=true;it.get(handle)}
}

/** Historical census correlation only: scalar object id, never a payload/slot return. */
internal fun coreOwnerPayloadIdForHistoricalCensus(model:ProductViewModel):Int {
    val field=ProductViewModel::class.java.getDeclaredField("coreSlot").also{it.isAccessible=true}
    val slot=(field.get(model) as java.util.concurrent.atomic.AtomicReference<*>).get() ?: return 0
    val value=slot.javaClass.getDeclaredField("value").also{it.isAccessible=true}.get(slot) as org.pag.domain.ActiveCoreResult.Value
    return System.identityHashCode(value.core)
}
