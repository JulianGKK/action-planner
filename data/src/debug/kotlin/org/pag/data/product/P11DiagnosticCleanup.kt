package org.pag.data.product

/** Preserve the diagnostic failure, while attempting every independent resource release. */
internal fun <T> p11DiagnosticCleanup(work:()->T, vararg cleanup:()->Unit):T {
    var primary:Throwable?=null
    try { return work() }
    catch(failure:Throwable) { primary=failure;throw failure }
    finally {
        var cleanupFailure:Throwable?=null
        for(release in cleanup) try { release() } catch(failure:Throwable) {
            val previous=primary ?: cleanupFailure
            if(previous==null)cleanupFailure=failure else if(previous!==failure)previous.addSuppressed(failure)
        }
        if(primary==null)cleanupFailure?.let{throw it}
    }
}
