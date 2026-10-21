package org.pag.data.product

import androidx.sqlite.SQLiteStatement
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.B2StatementCleanup
import net.zetetic.database.sqlcipher.driver.SQLCipherStatement

/** Real native cursor, synthetic reset/close exceptions; not a hardware fault simulation. */
class P73NativeFaultTest {
    @Test fun realKeyLeaseArrayAndDriverEndAfterNativeQueryAndInjectedException() {
        P11SyntheticFixture(app).use { f ->
            assertTrue(f.keyLeaseAfterNativeFailureProbe(false))
            assertTrue(f.keyLeaseAfterNativeFailureProbe(true))
        }
    }
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName=="org.pag.data.unlockprobe")
    }
    private fun vendor(statement:SQLiteStatement)=statement.javaClass.declaredFields.map {
        it.isAccessible=true;it.get(statement)
    }.filterIsInstance<SQLCipherStatement>().distinct().single()
    private fun cursor(statement:SQLCipherStatement)=statement.javaClass.getDeclaredField("cursor").let {
        it.isAccessible=true;it.get(statement) as net.zetetic.database.sqlcipher.SQLiteCursor
    }
    /** Diagnostic RED: requested initial size is not demonstrated to cap/gate refills. */
    @Test fun actualWindowRefillRequired_RED73() {
        val previous=net.zetetic.database.CursorWindow.PREFERRED_CURSOR_WINDOW_SIZE
        net.zetetic.database.sqlcipher.SQLiteCursor.setCursorWindowSize(65536)
        try {
        P11SyntheticFixture(app).use { f -> f.nativeDriverProbe { driver ->
            driver.open(f.databasePathForProbe()).use { connection ->
                repeat(3) {
                    val statement=connection.prepare("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<4000) SELECT x,hex(zeroblob(2048)) FROM n")
                    val raw=vendor(statement)
                    val starts=mutableSetOf<Int>()
                    val windows=mutableListOf<net.zetetic.database.CursorWindow>()
                    var count=0
                    try {
                        while(statement.step()) {
                            count++;assertEquals(count.toLong(),statement.getLong(0))
                            assertEquals(4096,statement.getText(1).length)
                            val window=cursor(raw).window
                            starts.add(window.startPosition)
                            if(windows.none{it===window})windows.add(window)
                            assertTrue(window.mWindowPtr!=0L)
                        }
                        assertEquals(4000,count);assertTrue("No actual refill observed",starts.size>1)
                        statement.close();assertTrue(cursorClosed(raw))
                        windows.forEach { assertEquals(0L,it.mWindowPtr) }
                        InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                            putString("stream","P73_WINDOW,cycle=$it,rows=$count,refill_positions=${starts.size},windows=${windows.size},pointers_after_close=0\n")
                        })
                    } finally { statement.close() }
                }
            }
        }}
        } finally { net.zetetic.database.sqlcipher.SQLiteCursor.setCursorWindowSize(previous) }
    }
    private fun cursorClosed(raw:SQLCipherStatement):Boolean = raw.javaClass.getDeclaredField("cursor").let {
        it.isAccessible=true;it.get(raw)==null
    }
    @Test fun nativeWindowPointersCloseAcrossIndependentSqlBatches() {
        P11SyntheticFixture(app).use { f -> f.nativeDriverProbe { driver ->
            driver.open(f.databasePathForProbe()).use { connection ->
                repeat(4) { batch ->
                    val statement=connection.prepare("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<1000) SELECT x,hex(zeroblob(2048)) FROM n")
                    val raw=vendor(statement)
                    var held:net.zetetic.database.CursorWindow?=null
                    try {
                        var rows=0
                        while(statement.step()) {
                            rows++;assertEquals(rows.toLong(),statement.getLong(0));assertEquals(4096,statement.getText(1).length)
                            val window=cursor(raw).window
                            if(held==null)held=window else assertSame(held,window)
                            assertTrue(window.mWindowPtr!=0L)
                        }
                        assertEquals(1000,rows)
                        statement.close();assertTrue(cursorClosed(raw));assertEquals(0L,held!!.mWindowPtr)
                        InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                            putString("stream","P73_BATCH,$batch,rows=$rows,native_window_ptr_after_close=0\n")
                        })
                    }finally{statement.close()}
                }
            }
        }}
    }
    @Test fun injectedResetAndCloseFailuresRemainErrorsAndNativeResourcesAreExplicitlyReclaimed() {
        P11SyntheticFixture(app).use { f -> f.nativeDriverProbe { driver ->
            driver.open(f.databasePathForProbe()).use { connection ->
                for(resetFails in listOf(false,true))for(closeFails in listOf(false,true)) {
                    val wrapper=connection.prepare("SELECT 1")
                    assertTrue(wrapper.step())
                    val raw=vendor(wrapper);val heldCursor=cursor(raw);val window=heldCursor.window
                    val reset=IllegalStateException("SYNTHETIC_RESET")
                    val close=IllegalStateException("SYNTHETIC_CLOSE")
                    val calls=mutableListOf<String>()
                    val injected=object:SQLiteStatement by raw {
                        override fun reset() { calls.add("reset");if(resetFails)throw reset;raw.reset() }
                        override fun close() { calls.add("close");raw.close();if(closeFails)throw close }
                    }
                    val cleanup=B2StatementCleanup(injected)
                    try {
                        val failure=runCatching{cleanup.close()}.exceptionOrNull()
                        assertEquals(listOf("reset","close"),calls)
                        if(resetFails)assertSame(reset,failure) else if(closeFails)assertSame(close,failure) else assertNull(failure)
                        if(resetFails && closeFails)assertArrayEquals(arrayOf(close),failure!!.suppressed)
                        assertEquals(!resetFails,heldCursor.isClosed)
                        cleanup.close();assertEquals(2,calls.size)
                    } finally {
                        // Explicit diagnostic rescue, never counted as successful product cleanup.
                        heldCursor.close();wrapper.close()
                    }
                    assertTrue(heldCursor.isClosed);assertEquals(0L,window.mWindowPtr)
                }
            }
        }}
    }
}
