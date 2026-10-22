package org.pag.prototype

import org.junit.Assert.*
import org.junit.Test

class AppModeTest {
    @Test fun demoNeverInvokesProductConstructionFactory() {
        var demo = 0; var runtime = 0; var db = 0; var keystore = 0; var session = 0; var credential = 0
        launchAppMode(true, { demo++ }, { runtime++; db++; keystore++; session++; credential++ })
        assertEquals(1, demo); assertEquals(listOf(0, 0, 0, 0, 0), listOf(runtime, db, keystore, session, credential))
    }
    @Test fun productEntryStillInvokesExactlyItsFactory() {
        var product = 0; var demo = 0
        launchAppMode(false, { demo++ }, { product++ }); assertEquals(1, product); assertEquals(0, demo)
    }
    @Test fun compiledFlagMatchesSeparateApplicationId() {
        assertEquals(BuildConfig.APPLICATION_ID.endsWith(".demo"), BuildConfig.PAG_DEMO_ONLY)
        assertTrue(listOf(BuildConfig.PAG_DEMO_ONLY, BuildConfig.PAG_P76_PREVIEW, BuildConfig.PAG_P77_RUNTIME_E2E).count { it } <= 1)
        val expected = when {
            BuildConfig.PAG_DEMO_ONLY -> "org.pag.prototype.a.demo"
            BuildConfig.PAG_P76_PREVIEW -> "org.pag.prototype.a.p76demo"
            BuildConfig.PAG_P77_RUNTIME_E2E -> "org.pag.prototype.a.p77runtime"
            else -> "org.pag.prototype.a"
        }
        assertEquals(expected, BuildConfig.APPLICATION_ID)
    }
}
