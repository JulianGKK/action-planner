package org.pag.data.prototype.b2

import androidx.sqlite.SQLiteStatement
import java.util.concurrent.atomic.AtomicBoolean

/** SQLCipher 4.19.1 close only marks closed; reset releases its query cursor.
 * Cleanup is independent of session authorization and always attempts both operations.
 */
internal class B2StatementCleanup(private val statement: SQLiteStatement) {
    private val closing=AtomicBoolean(false)
    fun close() {
        if(!closing.compareAndSet(false,true))return
        var failure:Throwable?=null
        try { statement.reset() } catch(error:Throwable) { failure=error }
        try { statement.close() } catch(error:Throwable) {
            if(failure==null)failure=error else if(failure!==error)failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }
}
