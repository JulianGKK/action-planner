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
/** Internal, instance-scoped diagnostic seam. Only guarded test composition supplies hooks. */
internal class B2ResourceHooks(
    val statement: (SQLiteStatement) -> SQLiteStatement = { it },
    val closed: (Boolean) -> Unit = {}
)

internal class B2Driver(private val file: File, key: ByteArray,
    private val context: StorageContext, private val hooks: B2Hooks,
    private val timing:B2OpenTiming?=null, private val resources:B2ResourceHooks?=null) : SQLiteDriver, AutoCloseable {
    private val bytes = key.copyOf()
    private val connections = CopyOnWriteArrayList<SQLiteConnection>()
    private val nativeConnections = CopyOnWriteArrayList<net.zetetic.database.sqlcipher.SQLiteDatabase>()
    private val closing = AtomicBoolean(false)
    private val closed get() = closing.get()
    override val hasConnectionPool = true // SQLCipher's native connection pool, as in Zetetic's driver.
    override fun open(fileName: String): SQLiteConnection = sanitizedB2 {
        if (closed || fileName != file.path) throw B2Exception(B2Failure.CLOSED)
        context.requireB2(AccessPoint.OPERATION)
        val native = B2Native.open(file, bytes, context,timing=timing)
        nativeConnections.add(native)
        val delegate = SQLCipherConnection(native)
        val connection = object : SQLiteConnection by delegate {
            private val released = AtomicBoolean(false)
            override fun prepare(sql: String): SQLiteStatement = sanitizedB2 {
                if (released.get() || closed) throw B2Exception(B2Failure.CLOSED)
                val command = sql.trimStart().uppercase(java.util.Locale.ROOT)
                // Rollback is resource cleanup, not a new authorized application operation.
                val rollback = command.startsWith("ROLLBACK")
                val commit = command.startsWith("COMMIT") || command.startsWith("END TRANSACTION") || command.startsWith("RELEASE")
                if (!rollback) {
                    try { context.requireB2() }
                    catch (failure: B2Exception) {
                        // Revocation can precede statement preparation, not only step().
                        // The denied commit must never leave a transaction holding the connection.
                        if (commit) rollbackDeniedCommit(delegate, failure)
                        throw failure
                    }
                }
                val statement = delegate.prepare(sql).let { resources?.statement?.invoke(it) ?: it }
                val cleanup = B2StatementCleanup(statement)
                object : SQLiteStatement by statement {
                    override fun close() = cleanup.close()
                    override fun step(): Boolean = sanitizedB2 {
                        if (released.get() || closed) throw B2Exception(B2Failure.CLOSED)
                        if (!rollback) {
                            try {
                                if (commit) hooks.beforeCommit()
                                context.requireB2(if (commit) AccessPoint.COMMIT else AccessPoint.STEP)
                            } catch (failure: B2Exception) {
                                // Room's passthrough pool does not rollback if END itself fails.
                                // Finish cleanup on this same connection; never execute the denied commit.
                                if (commit) rollbackDeniedCommit(delegate, failure)
                                throw failure
                            }
                        }
                        statement.step()
                    }
                    override fun getText(index: Int): String = sanitizedB2 { context.requireB2(); statement.getText(index) }
                    override fun getLong(index: Int): Long = sanitizedB2 { context.requireB2(); statement.getLong(index) }
                }
            }
            override fun close() { if (released.compareAndSet(false, true)) delegate.close() }
        }
        connections.add(connection)
        connection
    }
    /** A denied END still needs the vendor cursor reset, including on rollback failure.
     * Preserve the access-denial primary; cleanup is not another application transaction.
     */
    private fun rollbackDeniedCommit(connection: SQLiteConnection, primary: B2Exception) {
        try {
            val statement=connection.prepare("ROLLBACK TRANSACTION").let { resources?.statement?.invoke(it) ?: it }
            val cleanup=B2StatementCleanup(statement)
            AutoCloseable { cleanup.close() }.use { statement.step() }
        } catch (cleanup: Throwable) {
            if(cleanup !== primary)primary.addSuppressed(cleanup)
        }
    }
    override fun close() {
        if (closing.compareAndSet(false, true)) {
            // Connection close must be possible even after revocation.
            var failure: Throwable? = null
            try { connections.forEach { connection ->
                try { connection.close() } catch (error: Throwable) { if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error) }
            } } finally { connections.clear(); bytes.fill(0) }
            // Keep the original connection failure and every later close failure.
            failure?.let { throw it }
            resources?.closed?.invoke(resourcesClosed())
        }
    }
    fun resourcesClosed() = closed && nativeConnections.none { it.isOpen } && bytes.all { it == 0.toByte() }
}
