package org.pag.prototype.product

/** Optional variant-local diagnostics, never authority or payload ownership.
 * Ordinary product builds install no observer. Times use System.nanoTime throughout.
 */
interface CompactContentFrameObserver {
    fun capture(area: ActiveArea): CompactContentTrace?
}
interface CompactContentTrace {
    fun composing(nanos: Long)
    fun committed(nanos: Long)
    fun laidOut(nanos: Long)
    fun drawStarted(nanos: Long)
    fun drawn(nanos: Long)
}

/** Implemented only in the isolated P77 source set; runner consumes metadata, never payloads. */
interface CompactNavigationFrames : CompactContentFrameObserver {
    fun begin(area: ActiveArea): CompactNavigationSample
    fun consume(sample: CompactNavigationSample)
}
interface CompactNavigationSample {
    val id: Long
    val area: ActiveArea
    val start: Long
    var navigationReturned: Long
    val compositionStart: Long
    val commit: Long
    val layout: Long
    val drawStart: Long
    val drawEnd: Long
    fun await()
}
