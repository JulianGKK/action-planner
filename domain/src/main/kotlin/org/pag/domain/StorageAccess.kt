package org.pag.domain

/** Monotonic milliseconds, including device sleep. Never wall-clock time. */
fun interface MonotonicTime { fun nowMillis(): Long }

data class SessionPolicy(val absoluteMillis: Long, val inactivityMillis: Long) {
    init { require(absoluteMillis > 0 && inactivityMillis > 0) }
}
enum class AccessScope { READY, MAINTENANCE }
enum class AccessPoint { OPERATION, STEP, COMMIT, PUBLICATION }
interface StorageAccess { fun acquire(scope: AccessScope = AccessScope.READY): StorageContext? }
/** Revocable context, not a key or a native-handle ownership grant. */
interface StorageContext {
    val generation: Long get() = -1L
    val scope: AccessScope
    fun check(point: AccessPoint): Boolean
    /** Only a short in-memory publication. Never execute I/O in this callback. */
    fun publish(result: () -> Unit): Boolean
    /** Short synchronous key acquisition, never crypto, I/O or consumer work.
     * The authority serializes with session revoke before acquire takes a key-owner
     * monitor. Call authorize inside that monitor immediately before copying.
     * It rechecks the authoritative session and validUntilMillis using one time
     * sample in the context's monotonic epoch. The guard is same-thread/scoped;
     * it must not escape. Providers without this atomic admission deny by default.
     */
    fun <T> admitKey(validUntilMillis: Long, acquire: (authorize: () -> Boolean) -> T?): T? = null
}
