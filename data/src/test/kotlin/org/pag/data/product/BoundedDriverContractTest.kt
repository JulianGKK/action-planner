package org.pag.data.product

import android.database.Cursor
import java.lang.reflect.Proxy
import net.zetetic.database.sqlcipher.driver.SQLCipherStatement
import org.junit.Assert.*
import org.junit.Test

/** Actual dependency class, fake Cursor only: static close/binding contract, no native DB claim. */
class BoundedDriverContractTest {
    @Test fun actualSqlCipherCloseDoesNotExplicitlyCloseCursorButResetDoes() {
        var closes = 0
        val cursor = Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) { _, method, _ ->
            when (method.name) { "close" -> { closes++; null }; else -> error("Unexpected Cursor call") }
        } as Cursor
        val statement = SQLCipherStatement(null, "SELECT 1")
        SQLCipherStatement::class.java.getDeclaredField("cursor").apply { isAccessible = true }.set(statement, cursor)
        statement.close()
        assertEquals(0, closes) // Records the real dependency limitation; NOT desired product cleanup.
        statement.reset()
        assertEquals(1, closes)
    }

    @Test fun actualSqlCipherBindingMapPreservesExplicitNullAndLongTypes() {
        val statement = SQLCipherStatement(null, "SELECT ?,?,?")
        statement.bindText(1, "synthetic")
        statement.bindNull(2)
        statement.bindLong(3, Long.MIN_VALUE)
        val bindings = SQLCipherStatement::class.java.getDeclaredField("bindings").apply { isAccessible = true }
            .get(statement) as Map<*, *>
        assertEquals("synthetic", bindings[1]); assertTrue(bindings.containsKey(2)); assertNull(bindings[2])
        assertEquals(Long.MIN_VALUE, bindings[3])
        statement.clearBindings(); assertTrue(bindings.isEmpty()); statement.close()
    }
}
