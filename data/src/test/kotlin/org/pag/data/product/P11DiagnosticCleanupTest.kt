package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test

class P11DiagnosticCleanupTest {
    @Test fun diagnosticFailureSurvivesBothCleanupFailures() {
        val primary=IllegalStateException("diagnostic")
        val reset=IllegalStateException("reset");val close=IllegalStateException("close")
        val thrown=assertThrows(IllegalStateException::class.java) {
            p11DiagnosticCleanup({throw primary},{throw reset},{throw close})
        }
        assertSame(primary,thrown);assertArrayEquals(arrayOf(reset,close),thrown.suppressed)
    }
    @Test fun cleanupFailureAfterSuccessfulWorkIsNotSuccess() {
        var closed=false
        val failure=IllegalStateException("reset")
        assertSame(failure,assertThrows(IllegalStateException::class.java) {
            p11DiagnosticCleanup({true},{throw failure},{closed=true})
        })
        assertTrue(closed)
    }
}
