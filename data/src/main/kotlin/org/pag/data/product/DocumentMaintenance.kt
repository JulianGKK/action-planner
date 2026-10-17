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

/** Shared product entrypoint. The debug unlock probe calls this exact pipeline with its own store/key.
 * No authorization is supplied here: the caller must already own the maintenance context/key scope. */
internal fun reconcileProductDocuments(app:android.content.Context,inventory:ProductInventory,noBackupRoot:java.io.File,
    c:StorageContext,key:ByteArray,initializing:Boolean,phase:(MaintenancePhase)->Unit={},
    timing:((ActionDatabasePhase,Long)->Unit)?=null,ready:()->Unit) {
    val database=ActionDatabaseAccess(app,ActionPreflightHooks(timing=timing))
    documentMaintenanceBarrier(c,{
        withDocumentMaintenanceKey(c,key){keyStep->
            val steps=SqlCipherDocumentDeleteDatabase(app,inventory,{null},keyStep,ActionPreflightHooks(timing=timing))
            val recovery=SqlCipherDocumentReconciliationDatabase(steps,validateOpeningActions=!initializing)
            DocumentReconciliation(recovery,steps,noBackupRoot,observePhase=phase).run(c) {
                if(initializing)inventory.prepareDatabaseDirectory()
                else if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw ProductStorageFailure()
                fun preflight(creates:Boolean=false) {
                    val copy=key.copyOf()
                    try{database.use(inventory.database,copy,c,creates){db->db.actions().list().forEach{it.model()}}}
                    finally{copy.fill(0)}
                }
                // Reopen action validation is part of the first fully preflighted inventory
                // operation. The final fresh inventory and every recovery-step preflight remain.
                if(initializing){preflight(true);preflight();inventory.finish(c)}
            }
        }
    },ready)
}
