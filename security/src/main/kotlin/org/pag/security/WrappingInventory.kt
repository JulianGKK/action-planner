package org.pag.security

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files

enum class BootstrapState { EMPTY, COMPLETE, BLOCKED }
enum class WrappingFailure(val testId: String) {
    INVENTORY("B15-BOOTSTRAP-INVENTORY"), NOT_EMPTY("B15-BOOTSTRAP-NOT-EMPTY"),
    KEY_POLICY("B15-KEY-POLICY"), KEY_INVALIDATED("B15-KEY-INVALIDATED"),
    WRAPPED_READ("B15-WRAPPED-READ"), CIPHER_LOOKUP("B15-CIPHER-LOOKUP"),
    CIPHER_INIT("B15-CIPHER-INIT"), CRYPTOOBJECT("B15-CRYPTOOBJECT"),
    PROMPT_START("B15-PROMPT-START"), PROMPT_REJECTED("B15-PROMPT-REJECTED"),
    AUTH_REQUIRED("B15-UNWRAP-AUTH-REQUIRED"), UNWRAP("B15-UNWRAP-FAILED"),
    IO("B15-BOOTSTRAP-IO"), SESSION_INVALID("B15-SESSION-INVALID"), WRONG_CIPHER("B15-CRYPTOOBJECT")
}
class WrappingException(val failure: WrappingFailure) : RuntimeException(failure.testId)

/** Technical security port. publicIdentity is standard X.509 public-key encoding, never a secret. */
interface BootstrapKeyBackend {
    fun aliasPresent(): Boolean
    fun publicIdentity(): ByteArray
    fun createWrappedKey(): ByteArray
}

/** Whole reserved namespace is inventoried, not just the expected key files.
 * This is not a historical-installation proof or a general recovery protocol.
 */
class WrappingInventory(private val root: File, private val backend: BootstrapKeyBackend) {
    fun state(): BootstrapState = try { checkedState() } catch (_: Exception) { BootstrapState.BLOCKED }
    fun checkedState(): BootstrapState = sanitized { classify() }

    private fun children(): List<File> {
        if (Files.isSymbolicLink(root.toPath()) || root.absoluteFile != root.canonicalFile) fail(WrappingFailure.INVENTORY)
        if (!root.exists()) return emptyList()
        if (!root.isDirectory) fail(WrappingFailure.INVENTORY)
        val files = root.listFiles()?.toList() ?: fail(WrappingFailure.INVENTORY)
        if (files.any { Files.isSymbolicLink(it.toPath()) || it.absoluteFile != it.canonicalFile }) fail(WrappingFailure.INVENTORY)
        return files
    }

    private fun classify(): BootstrapState {
        val files = children()
        val alias = backend.aliasPresent()
        if (files.isEmpty() && !alias) return BootstrapState.EMPTY
        if (!alias || files.map { it.name }.toSet() != setOf("0", "1", "2") || files.any { !it.isFile })
            return BootstrapState.BLOCKED
        if (!readExact(File(root, "0"), 1).contentEquals(byteArrayOf(1))) return BootstrapState.BLOCKED
        readExact(File(root, "1"), 256)
        val publicKey = backend.publicIdentity() // Also validates algorithm/authentication policy.
        if (publicKey.isEmpty() || publicKey.size > 4096 ||
            !MessageDigest.isEqual(publicKey, readExact(File(root, "2"), publicKey.size))) return BootstrapState.BLOCKED
        // Ciphertext of the right length is only structurally complete, not yet decrypted/verified.
        return BootstrapState.COMPLETE
    }

    /** Explicit new-only entry. A reserved marker makes every partial attempt non-empty. */
    fun initializeNew(allowed: () -> Boolean) = sanitized {
        checkAllowed(allowed)
        when (classify()) {
            BootstrapState.COMPLETE -> fail(WrappingFailure.NOT_EMPTY)
            BootstrapState.BLOCKED -> fail(WrappingFailure.INVENTORY)
            BootstrapState.EMPTY -> Unit
        }
        if (!root.exists() && !root.mkdir()) fail(WrappingFailure.IO)
        // Exclusive create prevents simultaneous bootstrap owners even across processes.
        writeNew(File(root, "0"), byteArrayOf(1))
        checkAllowed(allowed)
        if (children().map { it.name }.toSet() != setOf("0") || backend.aliasPresent()) fail(WrappingFailure.INVENTORY)
        val wrapped = backend.createWrappedKey()
        if (wrapped.size != 256) fail(WrappingFailure.UNWRAP)
        checkAllowed(allowed)
        if (children().map { it.name }.toSet() != setOf("0")) fail(WrappingFailure.INVENTORY)
        writeNew(File(root, "1"), wrapped)
        checkAllowed(allowed)
        val publicKey = backend.publicIdentity()
        if (publicKey.isEmpty() || publicKey.size > 4096) fail(WrappingFailure.KEY_POLICY)
        writeNew(File(root, "2"), publicKey)
        if (classify() != BootstrapState.COMPLETE) fail(WrappingFailure.INVENTORY)
        checkAllowed(allowed)
    }

    fun readWrapped(): ByteArray = sanitized {
        if (classify() != BootstrapState.COMPLETE) fail(WrappingFailure.INVENTORY)
        wrappingStep(WrappingFailure.WRAPPED_READ) { readExact(File(root, "1"), 256) }
    }

    private fun writeNew(file: File, bytes: ByteArray) {
        if (!file.createNewFile()) fail(WrappingFailure.INVENTORY)
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    private fun readExact(file: File, size: Int): ByteArray {
        if (!file.isFile || file.length() != size.toLong()) fail(WrappingFailure.INVENTORY)
        return file.inputStream().use { stream ->
            val result = ByteArray(size)
            var at = 0
            while (at < size) {
                val read = stream.read(result, at, size - at)
                if (read <= 0) fail(WrappingFailure.INVENTORY)
                at += read
            }
            if (stream.read() != -1) fail(WrappingFailure.INVENTORY)
            result
        }
    }
    companion object {
        internal fun fail(reason: WrappingFailure): Nothing = throw WrappingException(reason)
        private fun checkAllowed(allowed: () -> Boolean) { if (!allowed()) fail(WrappingFailure.SESSION_INVALID) }
        private inline fun <T> sanitized(work: () -> T): T = try { work() }
        catch (failure: WrappingException) { throw failure }
        catch (_: IOException) { fail(WrappingFailure.IO) }
        catch (_: RuntimeException) { fail(WrappingFailure.INVENTORY) }
    }
}
