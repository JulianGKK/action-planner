package org.pag.data.product

import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext

/** Instance-local, single generation. Presence never authorizes access. No key getter. */
class SessionDbKeyOwner private constructor(private val generation: Long, key: ByteArray) {
    private var bytes: ByteArray? = key.copyOf()
    fun invalidate() = synchronized(this) { bytes?.fill(0); bytes = null }
    fun <T> withDbKey(context: StorageContext, consume: (ByteArray) -> T): T {
        requireContext(context)
        val lease = synchronized(this) { bytes?.copyOf() ?: throw ProductStorageFailure() }
        try {
            requireContext(context)
            val result = consume(lease)
            requireContext(context)
            return result
        } finally { lease.fill(0) }
    }
    private fun requireContext(context: StorageContext) {
        if (context.generation != generation || !context.check(AccessPoint.STEP)) {
            invalidate(); throw ProductStorageFailure()
        }
    }
    companion object {
        fun bind(context: StorageContext, key: ByteArray): SessionDbKeyOwner {
            if (context.generation < 0 || key.size != 32 || !context.check(AccessPoint.STEP)) throw ProductStorageFailure()
            return SessionDbKeyOwner(context.generation, key)
        }
    }
}
class ProductStorageFailure : RuntimeException("P1-STORAGE-DENIED")
