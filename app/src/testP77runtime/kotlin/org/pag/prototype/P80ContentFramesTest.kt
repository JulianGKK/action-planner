package org.pag.prototype

import org.junit.Assert.*
import org.junit.Test
import org.pag.prototype.product.ActiveArea

class P80ContentFramesTest {
    @Test fun onlyMatchingCommittedLaidOutDrawCompletesNavigation() {
        val frames=P80ContentFrames()
        val sample=frames.begin(ActiveArea.PERSONS)
        assertNull(frames.capture(ActiveArea.TASKS))
        val trace=checkNotNull(frames.capture(ActiveArea.PERSONS))
        trace.drawStarted(sample.start+1);trace.drawn(sample.start+2)
        assertFalse(sample.finished) // Arbitrary draw without commit/layout is not content.
        trace.composing(sample.start+2)
        trace.committed(sample.start+3)
        trace.drawStarted(sample.start+4);trace.drawn(sample.start+5)
        assertFalse(sample.finished)
        trace.laidOut(sample.start+6)
        trace.drawStarted(sample.start+7);trace.drawn(sample.start+8)
        sample.await()
        assertEquals(sample.start+8,sample.drawEnd)
        frames.consume(sample)
        val next=frames.begin(ActiveArea.TASKS)
        trace.drawStarted(sample.start+9);trace.drawn(sample.start+10)
        assertFalse(next.finished) // Retained old trace cannot complete new navigation.
        assertEquals(sample.id+1,next.id)
    }
    @Test fun overlappingNavigationAndForeignConsumptionAreRejected() {
        val frames=P80ContentFrames()
        val sample=frames.begin(ActiveArea.HUB)
        assertThrows(IllegalStateException::class.java) {frames.begin(ActiveArea.TASKS)}
        assertThrows(IllegalStateException::class.java) {frames.consume(sample)}
        assertThrows(IllegalStateException::class.java) {P80ContentFrames().consume(sample)}
    }
}
