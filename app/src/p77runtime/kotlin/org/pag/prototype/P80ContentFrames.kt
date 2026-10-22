package org.pag.prototype

import org.pag.prototype.product.ActiveArea
import org.pag.prototype.product.CompactNavigationFrames
import org.pag.prototype.product.CompactNavigationSample
import org.pag.prototype.product.CompactContentTrace
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** P77 only. One metadata-only request, owner-thread writes, latch hands immutable times to runner.
 * Observes Compose drawContent completion, not GPU presentation or physical display scanout.
 */
class P80ContentFrames : CompactNavigationFrames {
    private var nextId=0L
    private var pending: Sample?=null
    override fun begin(area:ActiveArea):Sample {
        check(pending==null) { "Previous navigation not consumed" }
        return Sample(++nextId,area,System.nanoTime()).also {pending=it}
    }
    override fun consume(sample:CompactNavigationSample) {
        check(pending===sample && sample.drawEnd!=0L) { "Foreign or incomplete navigation" }
        pending=null
    }
    override fun capture(area:ActiveArea):CompactContentTrace? = pending?.takeIf {it.area==area}
    class Sample(override val id:Long,override val area:ActiveArea,override val start:Long):CompactContentTrace, CompactNavigationSample {
        private val done=CountDownLatch(1)
        override var navigationReturned=0L
        override var compositionStart=0L;private set
        override var commit=0L;private set
        override var layout=0L;private set
        override var drawStart=0L;private set
        override var drawEnd=0L;private set
        val finished get()=drawEnd!=0L
        override fun composing(nanos:Long) { if(compositionStart==0L)compositionStart=nanos }
        override fun committed(nanos:Long) { if(commit==0L)commit=nanos }
        override fun laidOut(nanos:Long) { if(layout==0L)layout=nanos }
        override fun drawStarted(nanos:Long) { if(!finished && compositionStart>=start && commit>=compositionStart && layout>=commit && drawStart==0L && nanos>=layout)drawStart=nanos }
        override fun drawn(nanos:Long) {
            if(!finished && drawStart>=layout && drawStart>=commit && drawStart>0L && nanos>=drawStart) {
                drawEnd=nanos;done.countDown()
            }
        }
        override fun await() { check(done.await(5,TimeUnit.SECONDS)) { "No correlated Compose draw: id=$id area=$area commit=$commit layout=$layout draw=$drawStart" } }
    }
}
