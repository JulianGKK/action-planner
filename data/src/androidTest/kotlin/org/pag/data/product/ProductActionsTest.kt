package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.data.prototype.b2.StorageAccessRevoked
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor
import androidx.room3.withWriteTransaction

/** Real SQLCipher/Room; injected synthetic keys/context, NOT a DEVICE_CREDENTIAL proof. */
class ProductActionsTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(app.noBackupFilesDir.canonicalFile, "p1-test-${UUID.randomUUID()}")
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var generation = 1L
    private var allowed = true
    private fun context(scope: AccessScope): StorageContext = object : StorageContext {
        private val captured: Long = this@ProductActionsTest.generation
        override val generation get() = captured
        override val scope: AccessScope = scope
        override fun check(point: AccessPoint) = allowed && captured == this@ProductActionsTest.generation
        override fun publish(result: () -> Unit): Boolean { if (!check(AccessPoint.PUBLICATION)) return false; result(); return true }
    }
    private fun access(body: (AccessScope) -> StorageContext?) = object : StorageAccess {
        override fun acquire(scope: AccessScope) = body(scope)
    }
    private fun initialized(): ProductInventory {
        root.mkdir()
        root.resolve("intent").writeBytes(byteArrayOf(1)); root.resolve("envelope").writeBytes(ByteArray(256))
        root.resolve("public").writeBytes(byteArrayOf(1)); root.resolve("store").mkdir()
        val inventory = ProductInventory(root, { true }, { true })
        ActionDatabaseAccess(app).use(inventory.database, key, context(AccessScope.MAINTENANCE), true) { it.actions().list() }
        root.resolve("complete").writeBytes(byteArrayOf(1))
        assertEquals(ProductStorageState.READY_TO_OPEN, inventory.state())
        return inventory
    }
    @Test fun persistMultipleActionsAndUnknownId() {
        val inv = initialized(); val c = context(AccessScope.READY)
        val owner = SessionDbKeyOwner.bind(c, key)
        val direct = Executor { it.run() }
        val repository = ProductActionRepository(app, access { scope -> assertEquals(AccessScope.READY,scope); c },
            inv, { owner }, direct, direct) { fail("unexpected block") }
        var created: Action? = null
        repository.createAction(NewAction("synthetic action one", "synthetic description", 1, 2)) { created = it }
        val id = created!!.id
        repository.createAction(NewAction("synthetic action two", "synthetic description", 3, 4)) {}
        repository.listActions { assertEquals(2,it.size); assertTrue(it.all { row -> row.status == ActionStatus.DRAFT }) }
        repository.getAction(id) { assertEquals(created,it) }
        repository.getAction(UUID.randomUUID().toString()) { assertNull(it) }
        // Fresh owner/context simulates no retained connection across restart. Same persisted key is test-injected.
        owner.invalidate(); generation++
        val next = context(AccessScope.READY); val nextOwner = SessionDbKeyOwner.bind(next,key)
        nextOwner.withDbKey(next) { bytes ->
            ActionDatabaseAccess(app).use(inv.database,bytes,next) { db -> assertEquals(2,db.actions().list().size) }
        }
        nextOwner.invalidate(); key.fill(0)
    }
    @Test fun repositoryCannotUseKeyWhenReadyAccessDenied() {
        val inv = initialized(); val c = context(AccessScope.READY); val owner = SessionDbKeyOwner.bind(c,key)
        val direct = Executor { it.run() }
        val repo = ProductActionRepository(app, access { null },inv,{owner},direct,direct) { fail("unexpected block") }
        repo.listActions { fail("no publication outside READY") }
        repo.createAction(NewAction("synthetic", "synthetic",1,2)) { fail("no write outside READY") }
        owner.withDbKey(c) { bytes -> ActionDatabaseAccess(app).use(inv.database,bytes,c) { assertTrue(it.actions().list().isEmpty()) } }
        owner.invalidate(); key.fill(0)
    }
    @Test fun expiredOrStaleContextCannotOpenExistingDb() {
        val inv = initialized(); val c = context(AccessScope.READY); val before = inv.database.readBytes()
        generation++
        assertThrows(StorageAccessRevoked::class.java) { ActionDatabaseAccess(app).use(inv.database,key,c) { fail("stale query") } }
        assertArrayEquals(before,inv.database.readBytes())
        allowed = false
        assertThrows(StorageAccessRevoked::class.java) { ActionDatabaseAccess(app).use(inv.database,key,context(AccessScope.READY)) {} }
        key.fill(0)
    }
    @Test fun missingDbNeverRecreated() {
        val dir = File(root,"store"); dir.mkdirs(); val file = File(dir,"d")
        assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(file,key,context(AccessScope.READY)) {} }
        assertFalse(file.exists()); key.fill(0)
    }
    @Test fun wrongKeyDoesNotReplaceDb() {
        val inv = initialized(); val before = inv.database.readBytes(); val wrong = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try { assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(inv.database,wrong,context(AccessScope.READY)) {} } }
        finally { wrong.fill(0); key.fill(0) }
        assertArrayEquals(before,inv.database.readBytes())
    }
    @Test fun revokedBeforePublicationSuppressesResult() {
        val inv=initialized(); val c=context(AccessScope.READY); val owner=SessionDbKeyOwner.bind(c,key)
        val direct=Executor { it.run() }; val queue=mutableListOf<Runnable>()
        val repo=ProductActionRepository(app,access { c },inv,{owner},direct,Executor { queue.add(it) }) { fail("unexpected block") }
        repo.listActions { fail("expired result") }
        allowed=false; queue.forEach { it.run() }; owner.invalidate(); key.fill(0)
    }
    @Test fun rollbackBeforeCommitOnSameConnection() {
        val inv=initialized(); val c=context(AccessScope.READY)
        assertThrows(RuntimeException::class.java) {
            ActionDatabaseAccess(app).use(inv.database,key,c) { db ->
                db.withWriteTransaction {
                    db.actions().insert(ActionRow(UUID.randomUUID().toString(),"synthetic","synthetic",1,2,1,"DRAFT"))
                    allowed=false
                }
            }
        }
        allowed=true
        ActionDatabaseAccess(app).use(inv.database,key,context(AccessScope.READY)) { assertTrue(it.actions().list().isEmpty()) }
        key.fill(0)
    }
    private fun revokedPreflight(stop: ActionPreflightStep) {
        val inv = initialized()
        fun snapshot() = root.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(root).path to it.readBytes() }
        val before = snapshot()
        try {
            // Both deadline-style denial and generation invalidation at the same boundary.
            for (changeGeneration in listOf(false, true)) {
                allowed = true
                val c = context(AccessScope.READY)
                val owner = SessionDbKeyOwner.bind(c, key)
                val visited = mutableListOf<ActionPreflightStep>()
                var closed = false
                var published = false
                var lease: ByteArray? = null
                val db = ActionDatabaseAccess(app, ActionPreflightHooks(completed = { step ->
                    visited.add(step)
                    if (step == stop) {
                        if (changeGeneration) generation++ else allowed = false
                    }
                }, closed = { closed = it }))
                try {
                    assertThrows(StorageAccessRevoked::class.java) {
                        owner.withDbKey(c) { bytes ->
                            lease = bytes
                            db.use(inv.database, bytes, c) { fail("P1-ROOM-MUST-NOT-START") }
                        }
                        c.publish { published = true }
                    }
                    assertEquals(ActionPreflightStep.entries.take(stop.ordinal + 1), visited)
                    assertTrue("P1-NATIVE-CLEANUP", closed)
                    assertFalse("P1-NO-PUBLICATION", published)
                    assertTrue("P1-LEASE-WIPED", lease!!.all { it == 0.toByte() })
                    assertFalse(c.check(AccessPoint.PUBLICATION))
                    val after = snapshot()
                    assertEquals(before.keys, after.keys)
                    before.forEach { (path, bytes) -> assertTrue("P1-NO-REPLACEMENT", bytes.contentEquals(after[path])) }
                } finally { owner.invalidate() }
            }
        } finally { key.fill(0) }
    }
    @Test fun revokeAfterNativeOpenDeniesVersion() = revokedPreflight(ActionPreflightStep.OPEN)
    @Test fun revokeAfterVersionDeniesCipherIntegrity() = revokedPreflight(ActionPreflightStep.VERSION)
    @Test fun revokeAfterCipherIntegrityDeniesSqliteIntegrity() = revokedPreflight(ActionPreflightStep.CIPHER_INTEGRITY)
    @Test fun revokeAfterSqliteIntegrityDeniesForeignKeys() = revokedPreflight(ActionPreflightStep.SQLITE_INTEGRITY)
    @Test fun revokeAfterForeignKeysDeniesRoom() = revokedPreflight(ActionPreflightStep.FOREIGN_KEYS)
    @Test fun completePreflightStillAllowsAuthorizedActionOperation() {
        val inv = initialized()
        val visited = mutableListOf<ActionPreflightStep>()
        var closed = false
        val c = context(AccessScope.READY)
        val owner = SessionDbKeyOwner.bind(c, key)
        val db = ActionDatabaseAccess(app, ActionPreflightHooks({ visited.add(it) }, { closed = it }))
        val id = UUID.randomUUID().toString()
        try {
            val result = owner.withDbKey(c) { bytes ->
                db.use(inv.database, bytes, c) { room ->
                    assertTrue("P1-CLOSE-BEFORE-ROOM", closed)
                    room.withWriteTransaction {
                        room.actions().insert(ActionRow(id,"synthetic","synthetic",1,2,1,"DRAFT"))
                    }
                    room.actions().get(id)!!.model()
                }
            }
            assertEquals(ActionPreflightStep.entries, visited)
            var published = false
            assertTrue(c.publish { assertEquals(id,result.id); published = true })
            assertTrue(published)
            ActionDatabaseAccess(app).use(inv.database,key,c) { assertEquals(id,it.actions().list().single().id) }
        } finally { owner.invalidate(); key.fill(0) }
    }

}
