package org.pag.data.product

import java.util.concurrent.Executor
import org.pag.domain.*

/** Debug-only bridge for an app instrumentation harness. Caller supplies synthetic candidate;
 * preserves actual loader/lifetime and the ONE supplied mutation port. No DB/E2E claim. */
fun p73OwnerReadBridge(access:StorageAccess,mutations:WorkspaceMutationInvalidation,
    worker:Executor,main:Executor,
    candidate:(WorkspaceMutationInvalidation.ActiveReadTicket)->ActiveCoreResult):ActiveCoreLoadPort =
    ProductActiveCoreLoader(access,mutations,worker,main) { _,ticket,_,memory ->
        // This older owner probe is a synthetic DTO producer, not native admission evidence.
        candidate(ticket).also{if(it is org.pag.domain.ActiveCoreResult.Value)memory.admit(it.reservation,it.retainedCharge,it.transitionCharge)}
    }
