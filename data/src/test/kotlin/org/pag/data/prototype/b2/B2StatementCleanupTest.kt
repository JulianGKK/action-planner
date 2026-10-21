package org.pag.data.prototype.b2

import androidx.sqlite.SQLiteStatement
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class B2StatementCleanupTest {
    private fun statement(call:(String)->Unit)=Proxy.newProxyInstance(SQLiteStatement::class.java.classLoader,
        arrayOf(SQLiteStatement::class.java)) { _,method,_ -> call(method.name);null } as SQLiteStatement
    @Test fun resetPrecedesCloseAndDoubleCloseDoesNothing() {
        val calls=mutableListOf<String>()
        val cleanup=B2StatementCleanup(statement { calls.add(it) })
        cleanup.close();cleanup.close();assertEquals(listOf("reset","close"),calls)
    }
    @Test fun resetFailureStillClosesAndRemainsFailure() {
        val calls=mutableListOf<String>();val failed=IllegalStateException("synthetic-reset")
        val cleanup=B2StatementCleanup(statement { calls.add(it);if(it=="reset")throw failed })
        assertSame(failed,assertThrows(IllegalStateException::class.java){cleanup.close()})
        assertEquals(listOf("reset","close"),calls)
    }
    @Test fun bothFailuresPreserveFirstAndSuppressSecond() {
        val reset=IllegalStateException("synthetic-reset");val close=IllegalStateException("synthetic-close")
        val cleanup=B2StatementCleanup(statement { if(it=="reset")throw reset else throw close })
        assertSame(reset,assertThrows(IllegalStateException::class.java){cleanup.close()})
        assertArrayEquals(arrayOf(close),reset.suppressed)
    }
    @Test fun closeFailureIsNotSuccess() {
        val close=IllegalStateException("synthetic-close")
        val cleanup=B2StatementCleanup(statement { if(it=="close")throw close })
        assertSame(close,assertThrows(IllegalStateException::class.java){cleanup.close()})
    }
}
