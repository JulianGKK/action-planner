package org.pag.data.prototype.b2

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import net.zetetic.database.sqlcipher.driver.SQLCipherConnection
import org.pag.domain.AccessPoint
import org.pag.domain.StorageContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Stable SQLiteDriver seam + Zetetic's public connection adapter.
 * Unlike SQLCipherDriver.open(), this NEVER supplies a CREATE flag.
 * One owner; no public driver, DAO or statement leaves :data.
 */
internal class B2Driver(private val file: File, key: ByteArray,
    private val context: StorageContext, private val hooks: B2Hooks) : SQLiteDriver, AutoCloseable {
    private val bytes = key.copyOf()
    private val connections = CopyOnWriteArrayList<SQLiteConnection>()
    private val nativeConnections = CopyOnWriteArrayList<net.zetetic.database.sqlcipher.SQLiteDatabase>()
    private val closing = AtomicBoolean(false)
    private val closed get() = closing.get()
    override val hasConnectionPool = true // SQLCipher's native connection pool, as in Zetetic's driver.
    override fun open(fileName: String): SQLiteConnection = sanitizedB2 {
        if (closed || fileName != file.path) throw B2Exception(B2Failure.CLOSED)
        context.requireB2(AccessPoint.OPERATION)
        val native = B2Native.open(file, bytes, context)
        nativeConnections.add(native)
        val delegate = SQLCipherConnection(native)
        val connection = object : SQLiteConnection by delegate {
            private val released = AtomicBoolean(false)
            override fun prepare(sql: String): SQLiteStatement {
                if (released.get() || closed) throw B2Exception(B2Failure.CLOSED)
                val command = sql.trimStart().uppercase(java.util.Locale.ROOT)
                // Rollback is resource cleanup, not a new authorized application operation.
                val rollback = command.startsWith("ROLLBACK")
                val commit = command.startsWith("COMMIT") || command.startsWith("END TRANSACTION") || command.startsWith("RELEASE")
                if (!rollback) context.requireB2()
                val statement = delegate.prepare(sql)
                return object : SQLiteStatement by statement {
                    override fun step(): Boolean = sanitizedB2 {
                        if (released.get() || closed) throw B2Exception(B2Failure.CLOSED)
                        if (!rollback) {
                            try {
                                if (commit) hooks.beforeCommit()
                                context.requireB2(if (commit) AccessPoint.COMMIT else AccessPoint.STEP)
                            } catch (failure: B2Exception) {
                                // Room's passthrough pool does not rollback if END itself fails.
                                // Finish cleanup on this same connection; never execute the denied commit.
                                if (commit) delegate.prepare("ROLLBACK TRANSACTION").use { it.step() }
                                throw failure
                            }
                        }
                        statement.step()
                    }
                    override fun getText(index: Int): String { context.requireB2(); return statement.getText(index) }
                    override fun getLong(index: Int): Long { context.requireB2(); return statement.getLong(index) }
                }
            }
            override fun close() { if (released.compareAndSet(false, true)) delegate.close() }
        }
        connections.add(connection)
        connection
    }
    override fun close() {
        if (closing.compareAndSet(false, true)) {
            // Connection close must be possible even after revocation.
            var failure: Throwable? = null
            try { connections.forEach { connection ->
                try { connection.close() } catch (error: Throwable) { if (failure == null) failure = error }
            } } finally { connections.clear(); bytes.fill(0) }
            if (failure != null) throw B2Exception(B2Failure.BLOCKED)
        }
    }
    fun resourcesClosed() = closed && nativeConnections.none { it.isOpen } && bytes.all { it == 0.toByte() }
}
