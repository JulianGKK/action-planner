package org.pag.data.product

import org.pag.domain.*

/** Borrows only the existing synchronous authorized-maintenance key scope.
 * No new owner. Callback cannot retain the key reference after the scope exits.
 */
internal fun <T> withDocumentMaintenanceKey(c:StorageContext,key:ByteArray,
    work:(((StorageContext,(ByteArray)->Unit)->Unit))->T):T {
    val g=c.generation;val caller=Thread.currentThread();var borrowed:ByteArray?=key
    if(c.scope!=AccessScope.MAINTENANCE || key.size!=32)throw ProductStorageFailure()
    documentFileGate(c,g,AccessPoint.OPERATION)
    try { return work { candidate,consume ->
        if(Thread.currentThread()!==caller || candidate!==c)throw ProductStorageFailure()
        val bytes=borrowed?:throw ProductStorageFailure()
        documentFileGate(c,g)
        consume(bytes)
        documentFileGate(c,g)
    } } finally { borrowed=null }
}

/** Exact runtime ordering seam, not a second session machine or READY grant. */
internal fun documentMaintenanceBarrier(c:StorageContext,reconcile:()->Unit,prepareReadyOwner:()->Unit) {
    val g=c.generation
    if(c.scope!=AccessScope.MAINTENANCE)throw ProductStorageFailure()
    documentFileGate(c,g,AccessPoint.OPERATION)
    reconcile()
    documentFileGate(c,g)
    prepareReadyOwner()
}
