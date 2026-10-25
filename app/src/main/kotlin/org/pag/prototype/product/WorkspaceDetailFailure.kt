package org.pag.prototype.product

import org.pag.domain.*

internal fun WorkspaceFailure.operationFailure():OperationsFailure=when(this){
    WorkspaceFailure.NOT_FOUND->OperationsFailure.NOT_FOUND
    WorkspaceFailure.NOT_ALLOWED->OperationsFailure.NOT_ALLOWED
    WorkspaceFailure.STALE->OperationsFailure.STALE
    WorkspaceFailure.OWNER_UNCONFIRMED->OperationsFailure.OWNER_UNCONFIRMED
    WorkspaceFailure.BUDGET_REFUSED->OperationsFailure.BUDGET_REFUSED
}
