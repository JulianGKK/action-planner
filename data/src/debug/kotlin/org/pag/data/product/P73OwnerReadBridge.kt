package org.pag.data.product

import java.util.concurrent.Executor
import org.pag.domain.*

/** Debug-only bridge for an app instrumentation harness. Caller supplies synthetic candidate;
 * preserves actual loader/lifetime and the ONE supplied mutation port. No DB/E2E claim. */
fun p73OwnerReadBridge(access:StorageAccess,mutations:WorkspaceMutationInvalidation,
    worker:Executor,main:Executor,
    candidate:(WorkspaceMutationInvalidation.ActiveReadTicket)->ActiveCoreResult):ActiveCoreLoadPort =
    ProductActiveCoreLoader(access,mutations,worker,main) { _,ticket,_ -> candidate(ticket) }
