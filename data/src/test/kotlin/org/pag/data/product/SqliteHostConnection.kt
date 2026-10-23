package org.pag.data.product

import androidx.room3.PooledConnection
import androidx.sqlite.SQLiteStatement
import java.io.File
import java.util.Base64

/** Real SQLite via Python stdlib in a separate host process (normally in memory).
 * An explicit synthetic shared file supports the two-connection contention test.
 * Not an Android driver proof.
 */
internal class SqliteHostConnection(encoding: String = "UTF-8", sharedTestFile: File? = null) : PooledConnection, AutoCloseable {
    private val script = checkNotNull(javaClass.getResourceAsStream("/bounded_active_sqlite.py"))
        .bufferedReader().use { it.readText() }
    private val process = ProcessBuilder("python3", "-u", "-c", script,
        File("schemas/org.pag.data.product.ActionDatabase/11.json").absolutePath, encoding, sharedTestFile?.absolutePath ?: ":memory:").start()
    private val input = process.inputStream.bufferedReader()
    private val output = process.outputStream.bufferedWriter()
    private var sequence = 0
    var openStatements = 0; private set
    var blobReads = 0; private set
    var payloadBytes = 0L; private set
    var textReads = 0; private set
    var payloadStatements = 0; private set
    var beforePrepare: (String) -> Unit = {}
    var closed = false; private set
    private fun b64(value: ByteArray) = Base64.getEncoder().encodeToString(value)
    private fun send(value: String): String {
        check(!closed)
        output.write(value); output.newLine(); output.flush()
        val reply = input.readLine() ?: error("SQLITE_HOST_EXIT")
        check(!reply.startsWith("ERROR:")) { reply }
        return reply
    }
    override suspend fun <R> usePrepared(sql: String, block: suspend (SQLiteStatement) -> R): R {
        beforePrepare(sql)
        if (sql.startsWith("SELECT rowid,")) payloadStatements++
        val s = Statement(sql, ++sequence)
        openStatements++
        return try { block(s) } finally { s.close(); openStatements-- }
    }
    suspend fun execute(sql: String) { usePrepared(sql) { it.step(); Unit } }
    private inner class Statement(private val sql: String, private val id: Int) : SQLiteStatement {
        private val bindings = sortedMapOf<Int, String>()
        private var started = false
        private var released = false
        private var row = emptyList<String>()
        override fun bindText(index: Int, value: String) { bindings[index] = "S:" + b64(value.toByteArray(Charsets.UTF_8)) }
        override fun bindLong(index: Int, value: Long) { bindings[index] = "I:$value" }
        override fun bindNull(index: Int) { bindings[index] = "N" }
        override fun bindBlob(index: Int, value: ByteArray) { bindings[index] = "B:" + b64(value) }
        override fun bindDouble(index: Int, value: Double) { bindings[index] = "F:$value" }
        override fun step(): Boolean {
            check(!released)
            if (!started) {
                send(listOf("Q", id.toString(), b64(sql.toByteArray(Charsets.UTF_8)))
                    .plus((1..(bindings.lastKeyOrNull() ?: 0)).map { bindings[it] ?: "N" }).joinToString("\t"))
                started = true
            }
            val reply = send("S\t$id")
            row = if (reply == "END") emptyList() else reply.split('\t').drop(1)
            return reply != "END"
        }
        override fun getLong(index: Int) = row[index].removePrefix("I:").toLong()
        override fun getDouble(index: Int) = row[index].substringAfter(':').toDouble()
        override fun getBlob(index: Int): ByteArray {
            check(row[index].startsWith("B:"))
            return Base64.getDecoder().decode(row[index].substring(2)).also { blobReads++; payloadBytes += it.size }
        }
        override fun getText(index: Int): String {
            check(row[index].startsWith("S:")); textReads++
            return String(Base64.getDecoder().decode(row[index].substring(2)), Charsets.UTF_8)
        }
        override fun getColumnType(index: Int) = when (row[index].first()) {
            'I' -> 1; 'F' -> 2; 'S' -> 3; 'B' -> 4; 'N' -> 5; else -> error("SQLITE_HOST_TYPE")
        }
        override fun isNull(index: Int) = row[index] == "N"
        override fun getColumnCount() = row.size
        override fun getColumnName(index: Int) = "column$index"
        override fun reset() { if (started) send("C\t$id"); started = false; row = emptyList() }
        override fun clearBindings() { bindings.clear() }
        override fun close() { if (!released) { if (started) send("C\t$id"); released = true } }
    }
    override fun close() {
        if (!closed) {
            try { send("X") } finally {
                closed = true; output.close(); input.close()
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
                check(process.exitValue() == 0) { "SQLITE_HOST_FAILED" }
            }
        }
    }
}

private fun <K, V> java.util.SortedMap<K, V>.lastKeyOrNull(): K? = if (isEmpty()) null else lastKey()
