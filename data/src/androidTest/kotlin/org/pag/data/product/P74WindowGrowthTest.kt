package org.pag.data.product

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.CursorWindow
import net.zetetic.database.sqlcipher.SQLiteCursor
import net.zetetic.database.sqlcipher.driver.SQLCipherStatement
import org.junit.Assert.*
import org.junit.Test

/** Replacement evidence for the unproved fixed-capacity premise of RED73.
 * SQLCipher 4.19.1 CursorWindow::alloc calls maybeInflate/realloc; startPosition
 * need not change. Neither the Java size getter nor mWindowPtr is the allocation size.
 * This test deliberately does NOT claim refill or an 8 MiB native-memory bound.
 */
class P74WindowGrowthTest {
    @Test fun growingSingleWindowRetainsAllRowsAndProductCloseReleasesHandle() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext
        check(app.packageName=="org.pag.data.unlockprobe")
        val previous=CursorWindow.PREFERRED_CURSOR_WINDOW_SIZE
        SQLiteCursor.setCursorWindowSize(65536)
        try {
            P11SyntheticFixture(app).use { fixture -> fixture.nativeDriverProbe { driver ->
                driver.open(fixture.databasePathForProbe()).use { connection ->
                    repeat(3) { cycle ->
                        val statement=connection.prepare("WITH RECURSIVE n(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM n WHERE x<4000) SELECT x,hex(zeroblob(2048)) FROM n")
                        val raw=statement.javaClass.declaredFields.map { it.isAccessible=true;it.get(statement) }
                            .filterIsInstance<SQLCipherStatement>().distinct().single()
                        val cursorField=raw.javaClass.getDeclaredField("cursor").apply { isAccessible=true }
                        var window:CursorWindow?=null
                        var heldCursor:SQLiteCursor?=null
                        var handle=0L
                        var rows=0
                        try {
                            while(statement.step()) {
                                rows++
                                assertEquals(rows.toLong(),statement.getLong(0))
                                assertEquals(4096,statement.getText(1).length)
                                val cursor=cursorField.get(raw) as SQLiteCursor
                                if(window==null) { heldCursor=cursor;window=cursor.window;handle=window!!.mWindowPtr }
                                assertSame(heldCursor,cursor);assertSame(window,cursor.window)
                                assertEquals(handle,window!!.mWindowPtr);assertTrue(handle!=0L)
                                assertEquals(0,window!!.startPosition)
                            }
                            assertEquals(4000,rows)
                            assertEquals(4000,window!!.numRows)
                            // This getter stays at the initial request despite >16 MB of cell text.
                            assertEquals(65536,window!!.windowSizeBytes)
                            assertTrue(rows.toLong()*4096>window!!.windowSizeBytes)
                            statement.close()
                            assertNull(cursorField.get(raw));assertTrue(heldCursor!!.isClosed)
                            assertEquals(0L,window!!.mWindowPtr)
                            instrumentation.sendStatus(0,Bundle().apply { putString("stream",
                                "P74_GROWTH,cycle=$cycle,rows=$rows,cell_text_bytes=${rows.toLong()*4096},java_initial_size=65536,start=0,window_objects=1,handle_after_close=0\n") })
                        } finally { statement.close() }
                    }
                }
            } }
        } finally { SQLiteCursor.setCursorWindowSize(previous) }
    }
}
