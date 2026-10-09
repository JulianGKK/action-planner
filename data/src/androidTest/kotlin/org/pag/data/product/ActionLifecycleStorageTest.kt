package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.B2Native
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor

/** Real encrypted Room operations in the standalone data-test package; no main-app data. */
class ActionLifecycleStorageTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(app.noBackupFilesDir.canonicalFile,"p2-test-${UUID.randomUUID()}")
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var generation = 1L
    private var allowed = true
    private var denyCommit = false
    private fun context(scope: AccessScope = AccessScope.READY): StorageContext = object : StorageContext {
        private val captured = this@ActionLifecycleStorageTest.generation
        override val scope = scope
        override val generation get() = captured
        override fun check(point: AccessPoint): Boolean {
            if (point == AccessPoint.COMMIT && denyCommit) allowed = false
            return allowed && captured == this@ActionLifecycleStorageTest.generation
        }
        override fun publish(result: () -> Unit): Boolean {
            if (!check(AccessPoint.PUBLICATION)) return false
            result(); return true
        }
    }
    private fun inventory(): ProductInventory {
        root.mkdirs(); root.resolve("store").mkdir()
        root.resolve("intent").writeBytes(byteArrayOf(1)); root.resolve("complete").writeBytes(byteArrayOf(1))
        root.resolve("public").writeBytes(byteArrayOf(1)); root.resolve("envelope").writeBytes(ByteArray(256))
        return ProductInventory(root,{true},{true})
    }
    private fun initialized(): ProductInventory = inventory().also {
        ActionDatabaseAccess(app).use(it.database,key,context(AccessScope.MAINTENANCE),true) { db -> db.actions().list() }
    }
    private val direct = Executor { it.run() }
    private fun repository(inv: ProductInventory, main: Executor = direct): ProductActionRepository {
        val c = context(); val owner = SessionDbKeyOwner.bind(c,key)
        return ProductActionRepository(app,object : StorageAccess {
            override fun acquire(scope: AccessScope): StorageContext? {
                assertEquals(AccessScope.READY,scope)
                return c.takeIf { it.check(AccessPoint.OPERATION) }
            }
        },inv,{owner},direct,main) { fail("P2-UNEXPECTED-BLOCK") }
    }
    private fun create(repo: ActionRepository, suffix: String = "one"): Action {
        var result: Action? = null
        repo.createAction(NewAction("synthetic $suffix","synthetic description",1,2)) { result = it }
        return result!!
    }
    private fun advance(repo: ActionRepository, action: Action): Action {
        var result: ActionChangeResult? = null
        repo.advanceActionStatus(action.id,action.status) { result=it }
        return (result as ActionChangeResult.Applied).action
    }
    private fun loaded(repo: ActionRepository, id: String): Action? {
        var result: Action? = null; repo.getAction(id) { result=it }; return result
    }
    @org.junit.After fun wipeSyntheticKey() { key.fill(0) }

    @Test fun editReloadPreservesIdentityAndOtherAction() {
        val inv=initialized(); val repo=repository(inv); val first=create(repo); val second=create(repo,"two")
        var result: ActionChangeResult?=null
        repo.updateAction(first,NewAction("synthetic changed","synthetic changed",3,4)) { result=it }
        val updated=(result as ActionChangeResult.Applied).action
        assertEquals(first.id,updated.id); assertEquals(first.createdAt,updated.createdAt); assertEquals(first.status,updated.status)
        assertEquals(updated,loaded(repo,first.id)); assertEquals(second,loaded(repo,second.id))
        repo.listActions { assertEquals(2,it.size) }
    }
    @Test fun staleEditAndInvalidFieldsDoNotOverwrite() {
        val inv=initialized(); val repo=repository(inv); val original=create(repo)
        val changes=NewAction("synthetic changed","synthetic changed",3,4)
        repo.updateAction(original,changes) { assertTrue(it is ActionChangeResult.Applied) }
        val updated=loaded(repo,original.id)!!
        repo.updateAction(original,NewAction("synthetic lost","synthetic lost",1,2)) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.STALE),it)
        }
        repo.updateAction(updated,NewAction("","synthetic",2,1)) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.INVALID_FIELDS),it)
        }
        assertEquals(updated,loaded(repo,original.id))
    }
    @Test fun lifecyclePersistsCompletionAndCannotReopen() {
        val inv=initialized(); val repo=repository(inv); var action=create(repo)
        for (next in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)) {
            action=advance(repo,action); assertEquals(next,action.status); assertEquals(action,loaded(repo,action.id))
        }
        assertNotNull(action.completedAt)
        repo.advanceActionStatus(action.id,ActionStatus.COMPLETED) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.INVALID_TRANSITION),it)
        }
        repo.advanceActionStatus(action.id,ActionStatus.DRAFT) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.STALE),it)
        }
        repo.updateAction(action,NewAction("synthetic edit","synthetic edit",3,4)) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.NOT_EDITABLE),it)
        }
        generation++ // Old context revoked, fresh operation/owner using same synthetic persisted key.
        assertEquals(action,loaded(repository(inv),action.id))
    }
    @Test fun preparationEditableButActiveIsReadOnly() {
        val inv=initialized(); val repo=repository(inv); var action=advance(repo,create(repo))
        repo.updateAction(action,NewAction("synthetic prepared","synthetic prepared",3,4)) {
            action=(it as ActionChangeResult.Applied).action
        }
        action=advance(repo,action)
        repo.updateAction(action,NewAction("synthetic forbidden","synthetic forbidden",5,6)) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.NOT_EDITABLE),it)
        }
        assertEquals(action,loaded(repo,action.id))
    }
    @Test fun wrongExpectedStatusAndUnknownActionAreRejected() {
        val inv=initialized(); val repo=repository(inv); val action=create(repo)
        repo.advanceActionStatus(action.id,ActionStatus.PREPARATION) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.STALE),it)
        }
        repo.advanceActionStatus("synthetic-absent",ActionStatus.DRAFT) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.NOT_FOUND),it)
        }
        repo.updateAction(action.copy(id="synthetic-absent"),NewAction("synthetic","synthetic",1,2)) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.NOT_FOUND),it)
        }
        assertEquals(action,loaded(repo,action.id))
    }
    @Test fun deletionPendingHasNoProductAdvanceOrEdit() {
        val inv=initialized(); val id=UUID.randomUUID().toString()
        ActionDatabaseAccess(app).use(inv.database,key,context()) { db ->
            db.actions().insert(ActionRow(id,"synthetic","synthetic",1,2,1,"DELETION_PENDING"))
        }
        val repo=repository(inv); val row=loaded(repo,id)!!
        repo.advanceActionStatus(id,ActionStatus.DELETION_PENDING) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.INVALID_TRANSITION),it)
        }
        repo.updateAction(row,NewAction("synthetic","synthetic",1,2)) {
            assertEquals(ActionChangeResult.Rejected(ActionChangeRejection.NOT_EDITABLE),it)
        }
        assertEquals(row,loaded(repo,id))
    }
    @Test fun noRepositoryUpdateOrAdvanceOutsideReady() {
        val inv=initialized(); val repo=repository(inv); val action=create(repo)
        allowed=false
        repo.updateAction(action,NewAction("synthetic","synthetic",1,2)) { fail("P2-NO-LOCKED-PUBLICATION") }
        repo.advanceActionStatus(action.id,action.status) { fail("P2-NO-LOCKED-PUBLICATION") }
        allowed=true; generation++
        assertEquals(action,loaded(repository(inv),action.id))
    }
    @Test fun expiryBeforeMutationCommitRollsBackBothKinds() {
        val inv=initialized(); val repo=repository(inv); val action=create(repo)
        for (statusChange in listOf(false,true)) {
            val c=context()
            var published=false
            assertThrows(StorageAccessRevoked::class.java) {
                val result=ActionDatabaseAccess(app).use(inv.database,key,c) { db ->
                    denyCommit=true // Armed after Room's open/schema work; reject the mutation's COMMIT check.
                    if (statusChange) db.advanceAction(action.id,action.status,c)
                    else db.changeAction(action,NewAction("synthetic lost","synthetic lost",3,4),c)
                }
                c.publish { published=true; result }
            }
            assertFalse(published); assertFalse(allowed)
            denyCommit=false; allowed=true; generation++
            assertEquals(action,loaded(repository(inv),action.id))
        }
    }
    @Test fun expiredPublicationNeverReachesUi() {
        val inv=initialized(); val directRepo=repository(inv); val action=create(directRepo)
        for (statusChange in listOf(false,true)) {
            val queue=mutableListOf<Runnable>(); val repo=repository(inv,Executor { queue.add(it) })
            val current=loaded(directRepo,action.id)!!
            if (statusChange) repo.advanceActionStatus(current.id,current.status) { fail("P2-STALE-RESULT") }
            else repo.updateAction(current,NewAction("synthetic updated","synthetic updated",3,4)) { fail("P2-STALE-RESULT") }
            allowed=false; queue.forEach { it.run() }; allowed=true
        }
    }

    private fun legacyV1(): ProductInventory = inventory().also { inv ->
        inv.database.createNewFile()
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE)).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE actions (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, startAt INTEGER NOT NULL, expectedEndAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL, sensitivity TEXT NOT NULL)")
                db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                // Exact committed P1 schema identity, not a fresh v2 fixture.
                db.execSQL("INSERT INTO room_master_table VALUES(42,'29db739c191222a7cc06d82811bffe43')")
                db.execSQL("INSERT INTO actions VALUES('synthetic-v1','synthetic old','synthetic description',1,2,0,'DRAFT','SENSITIVE')")
                db.version=1; db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    @Test fun migrationPreservesP1RowAndSupportsLifecycle() {
        val inv=legacyV1(); val repo=repository(inv); val old=loaded(repo,"synthetic-v1")!!
        assertEquals("synthetic old",old.title); assertNull(old.completedAt)
        var action=old; repeat(3) { action=advance(repo,action) }
        assertEquals(ActionStatus.COMPLETED,action.status); assertNotNull(action.completedAt)
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE),true).use { assertEquals(4,it.version) }
    }
    @Test fun revokedMigrationRollsBackWithoutReplacement() {
        val inv=legacyV1(); val c=context()
        denyCommit=true
        assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(inv.database,key,c) { fail("P2-NO-MIGRATION-PUBLICATION") } }
        denyCommit=false; allowed=true; generation++
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE),true).use { assertEquals(1,it.version) }
        assertNotNull(loaded(repository(inv),"synthetic-v1"))
    }
    @Test fun unknownVersionBlocksWithoutDestructiveMigration() {
        val inv=legacyV1()
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE)).use { it.version=99 }
        val before=inv.database.readBytes()
        assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(inv.database,key,context()) {} }
        assertTrue("P2-NO-REPLACEMENT",before.contentEquals(inv.database.readBytes()))
    }
}
