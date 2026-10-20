package org.pag.data.product

import androidx.room3.withWriteTransaction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.B2Native
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor

/** Standalone data-test package, synthetic RAM keys; no main-app data or credential dialogs. */
class PersonStorageTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(app.noBackupFilesDir.canonicalFile, "p3a-test-${UUID.randomUUID()}")
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val owners = mutableListOf<SessionDbKeyOwner>()
    private var allowed = true
    private var generation = 1L
    private var denyCommit = false
    private fun context(scope: AccessScope = AccessScope.READY): StorageContext = object : StorageContext {
        private val captured = this@PersonStorageTest.generation
        override val generation get() = captured
        override val scope = scope
        override fun check(point: AccessPoint): Boolean {
            if (point == AccessPoint.COMMIT && denyCommit) allowed = false
            return allowed && captured == this@PersonStorageTest.generation
        }
        override fun publish(result: () -> Unit): Boolean {
            if (!check(AccessPoint.PUBLICATION)) return false
            result(); return true
        }
    }
    private val direct = Executor { it.run() }
    private fun inventory(): ProductInventory {
        check(root.mkdirs()); check(root.resolve("store").mkdir())
        root.resolve("intent").writeBytes(byteArrayOf(1)); root.resolve("complete").writeBytes(byteArrayOf(1))
        root.resolve("public").writeBytes(byteArrayOf(1)); root.resolve("envelope").writeBytes(ByteArray(256))
        return ProductInventory(root, { true }, { true })
    }
    private fun action(id: String, status: ActionStatus = ActionStatus.PREPARATION) =
        ActionRow(id, "synthetic $id", "synthetic description", 1, 2, 0, status.name,
            completedAt = if (status == ActionStatus.COMPLETED) 10 else null)
    private fun initialized(status: ActionStatus = ActionStatus.PREPARATION): ProductInventory = inventory().also { inv ->
        ActionDatabaseAccess(app).use(inv.database,key,context(AccessScope.MAINTENANCE),true) { db ->
            db.withWriteTransaction { db.actions().insert(action("synthetic-a",status)); db.actions().insert(action("synthetic-b")) }
        }
    }
    private fun repo(inv: ProductInventory, main: Executor = direct, worker: Executor = direct,
        blocked: () -> Unit = { fail("P3A-UNEXPECTED-BLOCK") }): PersonRepository {
        val c=context(); val owner=SessionDbKeyOwner.bind(c,key); owners.add(owner)
        return ProductPersonRepository(app, object : StorageAccess {
            override fun acquire(scope: AccessScope): StorageContext? {
                assertEquals(AccessScope.READY,scope); return c.takeIf { it.check(AccessPoint.OPERATION) }
            }
        }, inv, {owner}, worker, main, blocked)
    }
    private fun create(repo: PersonRepository, pseudonym: String = "synthetic-01", actionId: String = "synthetic-a"): Person {
        var result: PersonResult<Person>?=null
        repo.createPerson(actionId,pseudonym) { result=it }
        return (result as PersonResult.Value).value
    }
    private fun list(repo: PersonRepository, actionId: String = "synthetic-a"): List<Person> {
        var result: PersonResult<List<Person>>?=null
        repo.listPersons(actionId) { result=it }
        return (result as PersonResult.Value).value
    }
    private fun resetSession() { denyCommit=false; allowed=true; generation++; owners.forEach { it.invalidate() } }
    @After fun wipeTestKeys() { owners.forEach { it.invalidate() }; key.fill(0) }

    @Test fun createLoadAndRenamePreserveVisibleValueAndIdentity() {
        val inv=initialized(); val r=repo(inv); val first=create(r,"  Synthetic-01  ")
        assertEquals("  Synthetic-01  ",first.pseudonym)
        r.getPerson(first.actionId,first.id) { assertEquals(PersonResult.Value(first),it) }
        var updated: Person?=null
        r.updatePerson(first.actionId,first,"Synthetic-02") { updated=(it as PersonResult.Value).value }
        assertEquals(first.id,updated!!.id); assertEquals(first.createdAt,updated!!.createdAt)
        assertEquals(first.actionId,updated!!.actionId); assertEquals(listOf(updated),list(r))
        resetSession(); assertEquals(listOf(updated),list(repo(inv)))
    }
    @Test fun staleEditorCannotReplaceConfirmedRename() {
        val inv=initialized();val r=repo(inv);val original=create(r)
        val first=original.copy(pseudonym="synthetic-first")
        r.updatePerson(original.actionId,original,first.pseudonym) { assertEquals(PersonResult.Value(first),it) }
        r.updatePerson(original.actionId,original,"synthetic-stale") { assertEquals(PersonResult.Rejected(PersonRejection.STALE),it) }
        assertEquals(listOf(first),list(r))
    }
    @Test fun duplicatesAreScopedAndRejectedForCreateAndUpdate() {
        val inv=initialized(); val r=repo(inv); val first=create(r," Synthetic-01 "); val second=create(r,"synthetic-02")
        val other=create(r,"synthetic-01","synthetic-b")
        r.createPerson("synthetic-a","sYnThEtIc-01") { assertEquals(PersonResult.Rejected(PersonRejection.DUPLICATE_PSEUDONYM),it) }
        r.updatePerson("synthetic-a",second," SYNTHETIC-01 ") { assertEquals(PersonResult.Rejected(PersonRejection.DUPLICATE_PSEUDONYM),it) }
        r.updatePerson("synthetic-a",first,"synthetic-01") { assertTrue(it is PersonResult.Value) }
        assertEquals(2,list(r).size); assertEquals(listOf(other),list(r,"synthetic-b"))
    }
    @Test fun wrongActionCannotReadOrChangePerson() {
        val inv=initialized(); val r=repo(inv); val first=create(r)
        r.getPerson("synthetic-b",first.id) { assertEquals(PersonResult.Value<Person?>(null),it) }
        r.updatePerson("synthetic-b",first,"synthetic-forbidden") { assertEquals(PersonResult.Rejected(PersonRejection.STALE),it) }
        assertTrue(list(r,"synthetic-b").isEmpty()); assertEquals(listOf(first),list(r))
    }
    @Test fun blankAndUnknownIdsAreRejectedWithoutWrites() {
        val inv=initialized(); val r=repo(inv)
        r.createPerson("synthetic-a"," \t ") { assertEquals(PersonResult.Rejected(PersonRejection.INVALID_PSEUDONYM),it) }
        r.createPerson("synthetic-missing","synthetic") { assertEquals(PersonResult.Rejected(PersonRejection.ACTION_NOT_FOUND),it) }
        r.listPersons("synthetic-missing") { assertEquals(PersonResult.Rejected(PersonRejection.ACTION_NOT_FOUND),it) }
        r.getPerson("synthetic-missing","synthetic") { assertEquals(PersonResult.Rejected(PersonRejection.ACTION_NOT_FOUND),it) }
        r.updatePerson("synthetic-a",Person("synthetic-missing","synthetic-a","synthetic",0),"synthetic") { assertEquals(PersonResult.Rejected(PersonRejection.NOT_FOUND),it) }
        assertTrue(list(r).isEmpty())
    }
    @Test fun draftAndDeletionPendingDenyReadsAndWrites() {
        val inv=initialized(); val r=repo(inv)
        for (status in listOf(ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)) {
            ActionDatabaseAccess(app).use(inv.database,key,context()) { db -> db.withWriteTransaction { db.actions().update(action("synthetic-a",status)) } }
            r.listPersons("synthetic-a") { assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),it) }
            r.getPerson("synthetic-a","synthetic") { assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),it) }
            r.createPerson("synthetic-a","synthetic") { assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),it) }
            r.updatePerson("synthetic-a",Person("synthetic","synthetic-a","synthetic",0),"synthetic") { assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),it) }
        }
    }
    @Test fun activeAndCompletedRemainReadableButRejectWrites() {
        val inv=initialized(); val r=repo(inv); val person=create(r)
        for (status in listOf(ActionStatus.ACTIVE,ActionStatus.COMPLETED)) {
            ActionDatabaseAccess(app).use(inv.database,key,context()) { db -> db.withWriteTransaction { db.actions().update(action("synthetic-a",status)) } }
            assertEquals(listOf(person),list(r))
            r.getPerson(person.actionId,person.id) { assertEquals(PersonResult.Value(person),it) }
            r.createPerson(person.actionId,"synthetic-new") { assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),it) }
            r.updatePerson(person.actionId,person,"synthetic-changed") { assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),it) }
            assertEquals(listOf(person),list(r))
        }
    }
    @Test fun executionUsesCurrentPersistedStatusNotCallerSnapshot() {
        val inv=initialized(); val queue=mutableListOf<Runnable>(); val r=repo(inv,worker=Executor { queue.add(it) })
        var result: PersonResult<Person>?=null
        r.createPerson("synthetic-a","synthetic") { result=it }
        ActionDatabaseAccess(app).use(inv.database,key,context()) { db -> db.withWriteTransaction { db.actions().update(action("synthetic-a",ActionStatus.ACTIVE)) } }
        queue.single().run()
        assertEquals(PersonResult.Rejected(PersonRejection.NOT_ALLOWED),result)
        assertTrue(list(repo(inv)).isEmpty())
    }
    @Test fun changesDoNotAffectOtherActionsOrPersons() {
        val inv=initialized(); val r=repo(inv); val first=create(r); val second=create(r,"synthetic-02"); val other=create(r,"synthetic-03","synthetic-b")
        r.updatePerson(first.actionId,first,"synthetic-updated") {}
        assertEquals(second,list(r).single { it.id==second.id }); assertEquals(listOf(other),list(r,"synthetic-b"))
        ActionDatabaseAccess(app).use(inv.database,key,context()) { db ->
            assertEquals(action("synthetic-a"),db.actions().get("synthetic-a")); assertEquals(action("synthetic-b"),db.actions().get("synthetic-b"))
        }
    }
    @Test fun lockedAndStaleOperationsHaveNoPublicationOrWrites() {
        val inv=initialized(); val r=repo(inv); val person=create(r)
        allowed=false
        r.listPersons(person.actionId) { fail("P3A-NO-LOCKED-PUBLICATION") }
        r.createPerson(person.actionId,"synthetic-new") { fail("P3A-NO-LOCKED-PUBLICATION") }
        r.updatePerson(person.actionId,person,"synthetic-lost") { fail("P3A-NO-LOCKED-PUBLICATION") }
        resetSession()
        r.getPerson(person.actionId,person.id) { fail("P3A-NO-STALE-PUBLICATION") }
        assertEquals(listOf(person),list(repo(inv)))
    }
    @Test fun commitRevocationRollsBackCreateAndUpdate() {
        val inv=initialized(); val r=repo(inv); val person=create(r)
        for (updating in listOf(false,true)) {
            val c=context(); var published=false
            assertThrows(StorageAccessRevoked::class.java) {
                val result=ActionDatabaseAccess(app).use(inv.database,key,c) { db ->
                    denyCommit=true
                    db.changePerson(person.actionId,if(updating) person else null,"synthetic-lost",c)
                }
                c.publish { published=true; assertNotNull(result) }
            }
            assertFalse(published); resetSession(); assertEquals(listOf(person),list(repo(inv)))
        }
    }
    @Test fun delayedPublicationAfterExpiryIsSuppressed() {
        val inv=initialized(); val queue=mutableListOf<Runnable>(); val r=repo(inv,main=Executor { queue.add(it) })
        r.createPerson("synthetic-a","synthetic") { fail("P3A-NO-EXPIRED-PUBLICATION") }
        allowed=false; queue.forEach { it.run() }
        resetSession(); assertEquals(1,list(repo(inv)).size) // Physical commit completed before expiry; no UI success leaked.
    }
    @Test fun foreignKeyAndUniqueIndexAreEnforced() {
        val inv=initialized(); val r=repo(inv); create(r)
        assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(inv.database,key,context()) { db ->
            db.withWriteTransaction { db.persons().insert(PersonRow("synthetic-orphan","synthetic-missing","synthetic","synthetic",0)) }
        } }
        assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(inv.database,key,context()) { db ->
            db.withWriteTransaction { db.persons().insert(PersonRow("synthetic-duplicate","synthetic-a","SYNTHETIC-01","synthetic-01",0)) }
        } }
        assertEquals(1,list(r).size)
    }
    @Test fun inconsistentPersonIsBlockedNotRepairedByRename() {
        val inv=initialized(); val row=PersonRow("synthetic-invalid","synthetic-a","synthetic-visible","synthetic-wrong",0)
        ActionDatabaseAccess(app).use(inv.database,key,context()) { db -> db.withWriteTransaction { db.persons().insert(row) } }
        var blocked=false
        repo(inv,blocked={blocked=true}).updatePerson(row.actionId,Person(row.id,row.actionId,row.pseudonym,row.createdAt),"synthetic-repair") { fail("P3A-NO-INVALID-SUCCESS") }
        assertTrue(blocked)
        ActionDatabaseAccess(app).use(inv.database,key,context()) { db -> assertEquals(row,db.persons().get(row.actionId,row.id)) }
    }

    private fun legacyV2(): ProductInventory = inventory().also { inv ->
        check(inv.database.createNewFile())
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE)).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE actions (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, startAt INTEGER NOT NULL, expectedEndAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL, sensitivity TEXT NOT NULL, completedAt INTEGER DEFAULT NULL)")
                db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                db.execSQL("INSERT INTO room_master_table VALUES(42,'650e10496cd9b506fde8e1eb5a124bbd')")
                db.execSQL("INSERT INTO actions VALUES('synthetic-a','synthetic old','synthetic description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
                db.execSQL("INSERT INTO actions VALUES('synthetic-b','synthetic completed','synthetic description',1,2,0,'COMPLETED','SENSITIVE',10)")
                db.version=2; db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    @Test fun migrationPreservesV2ActionsAndCompletionAndStartsWithoutPersons() {
        val inv=legacyV2(); val r=repo(inv)
        assertTrue(list(r).isEmpty()); assertTrue(list(r,"synthetic-b").isEmpty())
        ActionDatabaseAccess(app).use(inv.database,key,context()) { db ->
            assertEquals(Action("synthetic-a","synthetic old","synthetic description",1,2,0,ActionStatus.PREPARATION),db.actions().get("synthetic-a")!!.model())
            assertEquals(Action("synthetic-b","synthetic completed","synthetic description",1,2,0,ActionStatus.COMPLETED,10),db.actions().get("synthetic-b")!!.model())
        }
        create(r)
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE),true).use { db ->
            assertEquals(11,db.version)
            db.rawQuery("PRAGMA table_info(persons)",emptyArray<String>()).use { cursor ->
                val columns=mutableListOf<String>(); while(cursor.moveToNext()) columns.add(cursor.getString(1))
                assertEquals(listOf("id","actionId","pseudonym","pseudonymNormalized","createdAt"),columns)
            }
        }
    }
    @Test fun revokedMigrationPreservesV2WithoutReplacement() {
        val inv=legacyV2(); denyCommit=true
        assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(inv.database,key,context()) {} }
        resetSession()
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE),true).use { assertEquals(2,it.version) }
        assertTrue(list(repo(inv)).isEmpty())
    }
    @Test fun unexpectedPreexistingPersonTableBlocksMigrationWithoutRepair() {
        val inv=legacyV2()
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE)).use { db ->
            db.execSQL("CREATE TABLE persons (id TEXT NOT NULL, actionId TEXT NOT NULL, pseudonym TEXT NOT NULL, pseudonymNormalized TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("INSERT INTO persons VALUES('synthetic-preserved','synthetic-a','synthetic-existing','synthetic-existing',0)")
        }
        assertThrows(RuntimeException::class.java) { ActionDatabaseAccess(app).use(inv.database,key,context()) {} }
        B2Native.open(inv.database,key,context(AccessScope.MAINTENANCE),true).use { db ->
            assertEquals(2,db.version)
            db.rawQuery("SELECT id FROM persons",emptyArray<String>()).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("synthetic-preserved",cursor.getString(0)); assertFalse(cursor.moveToNext())
            }
        }
    }
    @Test fun noPseudonymMarkerInPersistentCiphertextArtifacts() {
        val inv=initialized(); val r=repo(inv); val marker="P3A_SYNTHETIC_MARKER_73E8"; create(r,marker)
        for (file in root.walkTopDown().filter { it.isFile }) {
            val bytes=file.readBytes()
            for (pattern in listOf(marker.toByteArray(),PersonRules.normalized(marker).toByteArray(),marker.toByteArray(Charsets.UTF_16LE))) {
                assertFalse("P3A-NO-PERSISTENT-PLAINTEXT-MARKER",bytes.asList().windowed(pattern.size).any { it==pattern.asList() })
            }
        }
    }
}
