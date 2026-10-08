package org.pag.data.prototype.b2

import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import org.pag.security.OperationResult
import org.pag.security.SyntheticOperation

/** The same B1.6 synchronous, non-retaining key consumer. No separate unlock state. */
internal fun <T> useB2Key(operation: SyntheticOperation, context: StorageContext, work: (ByteArray) -> T): T {
    var completed = false
    var result: T? = null
    var failure: B2Exception? = null
    try {
        context.requireB2(AccessPoint.OPERATION)
        val outcome = operation.useUnwrappedKey({ context.check(AccessPoint.STEP) }) { key ->
            try { context.requireB2(); result = work(key); context.requireB2(); completed = true }
            catch (error: B2Exception) { failure = error; throw error }
        }
        failure?.let { throw it }
        context.requireB2()
        if (outcome != OperationResult.SUCCEEDED || !completed) throw B2Exception(B2Failure.BLOCKED)
        @Suppress("UNCHECKED_CAST") return result as T
    } finally { operation.release() }
}
