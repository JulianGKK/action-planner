package org.pag.data.product

import androidx.room3.withReadTransaction
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

/** Standalone synthetic data-test package, never product files, auth or device credentials. */
class MaterialStorageTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var allowed = true; private var generation = 1L; private var denyCommit = false; private var time = 1000L
    private val owners = mutableListOf<SessionDbKeyOwner>()
    private val direct = Executor { it.run() }
    private fun context(scope: AccessScope = AccessScope.READY): StorageContext = object : StorageContext {
        private val captured = this@MaterialStorageTest.generation
        override val generation = captured; override val scope = scope
        override fun check(point: AccessPoint): Boolean {
            if (denyCommit && point == AccessPoint.COMMIT) allowed = false
            return allowed && captured == this@MaterialStorageTest.generation
        }
        override fun publish(result: () -> Unit): Boolean { if (!check(AccessPoint.PUBLICATION)) return false; result(); return true }
    }
    private fun fixture(): ProductInventory {
        val root = File(app.noBackupFilesDir.canonicalFile, "p7-test-${UUID.randomUUID()}")
        check(root.mkdirs()); check(root.resolve("store").mkdir())
        listOf("intent", "complete", "public").forEach { root.resolve(it).writeBytes(byteArrayOf(1)) }
        root.resolve("envelope").writeBytes(ByteArray(256))
        val i = ProductInventory(root, { true }, { true })
        ActionDatabaseAccess(app).use(i.database, key, context(AccessScope.MAINTENANCE), true) { d -> d.withWriteTransaction {
            listOf("a", "b").forEach { d.actions().insert(ActionRow(it, "synthetic", "synthetic", 1, 2, 0, "PREPARATION")) }
            d.persons().insert(PersonRow("p", "a", "Motte", "motte", 0)); d.persons().insert(PersonRow("q", "b", "Meise", "meise", 0))
            val store = TaskStore(d, context()) { time }
            value(store.create("a", TaskInput("synthetic task"))); value(store.create("b", TaskInput("synthetic foreign task")))
        } }
        return i
    }
    private fun <T> use(i: ProductInventory, work: suspend (ActionDatabase) -> T): T = ActionDatabaseAccess(app).use(i.database, key, context(), work = work)
    private fun <T> write(i: ProductInventory, work: suspend (MaterialStore) -> OperationsResult<T>): OperationsResult<T> {
        val c = context()
        return ActionDatabaseAccess(app).use(i.database, key, c) { d -> d.withWriteTransaction {
            val result = work(MaterialStore(d, c) { time }); requireOperations(c, AccessPoint.COMMIT); result
        } }
    }
    private fun <T> value(r: OperationsResult<T>): T { assertTrue(r is OperationsResult.Value); return (r as OperationsResult.Value).value }
    private fun <T> protocolValue(r: ProtocolResult<T>): T { assertTrue(r is ProtocolResult.Value); return (r as ProtocolResult.Value).value }
    private fun reject(r: OperationsResult<*>, why: OperationsFailure) = assertEquals(OperationsResult.Rejected(why), r)
    private fun task(i: ProductInventory, a: String = "a") = use(i) { it.tasks().list(a).single().model() }
    private fun input(i: ProductInventory) = MaterialInput(" synthetic material ", " 2 Tuben ", " Materialkiste ", "p", task(i).id, "GLUE")
    private fun create(i: ProductInventory, input: MaterialInput = input(i)) = value(write(i) { it.create("a", input) })
    private fun board(i: ProductInventory) = value(use(i) { d -> d.withReadTransaction { MaterialStore(d, context()) { time }.board("a") } })
    private fun state(i: ProductInventory, phase: ActionStatus) = use(i) { d -> d.withWriteTransaction {
        d.actions().update(d.actions().get("a")!!.copy(status = phase.name, completedAt = time.takeIf { phase == ActionStatus.COMPLETED }))
    } }
    private fun native(i: ProductInventory, sql: String) = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE)).use { d -> d.execSQL("PRAGMA foreign_keys=ON"); d.execSQL(sql) }
    private fun repo(i: ProductInventory, main: Executor = direct, worker: Executor = direct): MaterialRepository {
        val owner = SessionDbKeyOwner.bind(context(), key); owners += owner
        val access = object : StorageAccess { override fun acquire(scope: AccessScope) = context(scope).takeIf { it.check(AccessPoint.OPERATION) } }
        return ProductMaterialRepository(app, access, i, { owner }, worker, main, { fail("P7 unexpected storage block") }) { time }
    }
    private fun reset() { allowed = true; denyCommit = false; generation++ }
    @After fun wipe() { owners.forEach { it.invalidate() }; key.fill(0) }
    @Test fun freshSchemaEightHasOnlyNineMaterialColumnsAndFourTriggers() {
        val i = fixture()
        B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { d ->
            assertEquals(9, d.version)
            d.rawQuery("PRAGMA table_info(materials)", emptyArray<String>()).use { c ->
                val columns = mutableListOf<String>(); while (c.moveToNext()) columns += c.getString(1)
                assertEquals(listOf("id", "actionId", "name", "quantityLabel", "placeLabel", "responsiblePersonId", "linkedTaskId", "sourceTemplateKey", "createdAt"), columns)
            }
        }
        assertEquals(4, materialTriggers(i).size)
    }
    @Test fun productionPortCreatesEditsDeletesAndCountsWithNoForeignMutation() {
        val i = fixture(); val r = repo(i); val before = task(i); val input = input(i)
        var item: MaterialItem? = null; var count: MaterialSummary? = null
        r.create("a", input) { item = value(it) }; assertEquals("synthetic material", item!!.name)
        r.summary("a") { count = value(it) }; assertEquals(MaterialSummary(1), count)
        val original = item!!; time = 9000
        r.edit("a", original, input.copy(name = "changed", sourceTemplateKey = "WATER")) { item = value(it) }
        assertEquals("GLUE", item!!.sourceTemplateKey); assertEquals(original.createdAt, item!!.createdAt)
        r.delete("a", item!!) { value(it) }; r.summary("a") { count = value(it) }; assertEquals(MaterialSummary(0), count)
        assertEquals(before, task(i)); assertEquals("Motte", use(i) { it.persons().get("a", "p")!!.pseudonym })
    }
    @Test fun createPreparationAndActiveNormalizeTextAndAllowRepeatedTemplates() {
        val i = fixture(); val first = create(i); assertEquals(MaterialRules.normalized(input(i)), first.input())
        state(i, ActionStatus.ACTIVE); val second = create(i)
        assertNotEquals(first.id, second.id); assertEquals(2, board(i).items.size)
        val empty = create(i, MaterialInput("x", " ", "\n")); assertNull(empty.quantityLabel); assertNull(empty.placeLabel)
    }
    @Test fun allPersistedLifecycleGatesIncludingCompletedReadOnly() {
        val i = fixture(); val old = create(i)
        for (phase in ActionStatus.entries) {
            state(i, phase)
            val read = use(i) { MaterialStore(it, context()) { time }.board("a") }
            val summary = use(i) { MaterialStore(it, context()) { time }.summary("a") }
            val created = write(i) { it.create("a", MaterialInput("synthetic")) }
            val edited = write(i) { it.edit("a", old, old.input()) }
            if (MaterialRules.mayRead(phase)) { assertTrue(read is OperationsResult.Value); assertTrue(summary is OperationsResult.Value) }
            else { reject(read, OperationsFailure.NOT_ALLOWED); reject(summary, OperationsFailure.NOT_ALLOWED) }
            if (MaterialRules.mayWrite(phase)) { assertTrue(created is OperationsResult.Value); assertTrue(edited is OperationsResult.Value) }
            else { reject(created, OperationsFailure.NOT_ALLOWED); reject(edited, OperationsFailure.NOT_ALLOWED); reject(write(i) { it.delete("a", old) }, OperationsFailure.NOT_ALLOWED) }
        }
    }
    @Test fun editAndDeleteCompareFullSnapshotAndPreserveProvenanceAndTime() {
        val i = fixture(); val old = create(i); time++
        val next = value(write(i) { it.edit("a", old, old.input().copy(name = "changed", quantityLabel = null, sourceTemplateKey = null)) })
        assertEquals(old.createdAt, next.createdAt); assertEquals("GLUE", next.sourceTemplateKey)
        reject(write(i) { it.edit("a", old, old.input()) }, OperationsFailure.STALE)
        reject(write(i) { it.delete("a", old) }, OperationsFailure.STALE)
        assertEquals(listOf(next), board(i).items)
    }
    @Test fun deleteAllowedInBothWritablePhasesAndLeavesReferencedObjects() {
        val i = fixture(); val before = task(i)
        for (phase in listOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE)) {
            state(i, phase); val item = create(i); value(write(i) { it.delete("a", item) }); assertTrue(board(i).items.isEmpty())
        }
        assertEquals(before, task(i)); assertNotNull(use(i) { it.persons().get("a", "p") })
    }
    @Test fun invalidUnicodeAndCrossActionReferencesWriteNothing() {
        val i = fixture(); val foreign = task(i, "b")
        for (bad in listOf(MaterialInput(" "), MaterialInput("🦋".repeat(121)), MaterialInput("x", "🦋".repeat(41)), MaterialInput("x", placeLabel = "🦋".repeat(81))))
            reject(write(i) { it.create("a", bad) }, OperationsFailure.INVALID)
        for (bad in listOf(MaterialInput("x", responsiblePersonId = "q"), MaterialInput("x", linkedTaskId = foreign.id), MaterialInput("x", responsiblePersonId = "missing"), MaterialInput("x", linkedTaskId = "missing")))
            reject(write(i) { it.create("a", bad) }, OperationsFailure.NOT_FOUND)
        assertTrue(board(i).items.isEmpty())
        val max = create(i, MaterialInput("🦋".repeat(120), "🦋".repeat(40), "🦋".repeat(80))); assertEquals(120, max.name.codePointCount(0, max.name.length))
    }
    @Test fun directInsertAndUpdateTriggersEnforceBothSameActionReferences() {
        val i = fixture(); val own = task(i); val foreign = task(i, "b")
        for (row in listOf(MaterialItem("bad-p", "a", "synthetic", null, null, "q", null, null, 0),
            MaterialItem("bad-t", "a", "synthetic", null, null, null, foreign.id, null, 0)))
            assertThrows(RuntimeException::class.java) { use(i) { d -> d.withWriteTransaction { d.materials().insert(row.row()) } } }
        val item = create(i, MaterialInput("valid", responsiblePersonId = "p", linkedTaskId = own.id))
        for (bad in listOf(item.copy(responsiblePersonId = "q"), item.copy(linkedTaskId = foreign.id)))
            assertThrows(RuntimeException::class.java) { use(i) { d -> d.withWriteTransaction { d.materials().update(bad.row()) } } }
        assertEquals(listOf(item), board(i).items)
    }
    @Test fun deletingPersonDetachesOnlyItsLinkAndMakesFullExpectedStale() {
        val i = fixture(); val old = create(i)
        native(i, "DELETE FROM persons WHERE actionId='a' AND id='p'")
        val next = board(i).items.single(); assertEquals(old.copy(responsiblePersonId = null), next)
        assertNotNull(task(i)); reject(write(i) { it.edit("a", old, old.input().copy(responsiblePersonId = null)) }, OperationsFailure.STALE)
    }
    @Test fun taskRepositoryDeleteDetachesOnlyTaskAndDoesNotBlockDeletion() {
        val i = fixture(); val old = create(i); val task = task(i)
        use(i) { d -> d.withWriteTransaction { value(TaskStore(d, context()) { time }.delete("a", task)) } }
        assertEquals(old.copy(linkedTaskId = null), board(i).items.single())
        assertTrue(use(i) { it.tasks().events("a").isEmpty() }); assertNotNull(use(i) { it.persons().get("a", "p") })
        reject(write(i) { it.delete("a", old) }, OperationsFailure.STALE)
    }
    @Test fun doneAndCancelledTasksKeepLinksAndMaterialNeverChangesTasksOrEvents() {
        val i = fixture(); var task = task(i)
        val old = create(i)
        for (status in listOf(TaskStatus.DONE, TaskStatus.OPEN, TaskStatus.CANCELLED)) {
            task = use(i) { d -> d.withWriteTransaction { value(TaskStore(d, context()) { time }.transition("a", task, status)) } }
            assertEquals(task.id, board(i).items.single().linkedTaskId)
        }
        val events = use(i) { it.tasks().events("a") }; val before = task
        value(write(i) { it.edit("a", old, old.input().copy(quantityLabel = "1 Kiste")) })
        assertEquals(before, task(i)); assertEquals(events, use(i) { it.tasks().events("a") })
    }
    @Test fun actionCascadeLeavesNoMaterialIslandAndOtherActionSurvives() {
        val i = fixture(); create(i)
        val foreignTask = task(i, "b")
        val other = value(write(i) { it.create("b", MaterialInput("other", responsiblePersonId = "q", linkedTaskId = foreignTask.id)) })
        native(i, "DELETE FROM actions WHERE id='a'")
        use(i) { d -> assertTrue(d.materials().list("a").isEmpty()); assertEquals(listOf(other), d.materials().list("b").map { it.model() }) }
    }
    @Test fun summaryCountsOnlyAndBoardOrdersByCreationThenId() {
        val i = fixture()
        val rows = listOf(MaterialItem("z", "a", "A", null, null, null, null, null, 1), MaterialItem("a", "a", "Z", null, null, null, null, null, 1), MaterialItem("old", "a", "X", null, null, null, null, null, 0))
        use(i) { d -> d.withWriteTransaction { rows.forEach { d.materials().insert(it.row()) } } }
        assertEquals(listOf("old", "a", "z"), board(i).items.map { it.id })
        assertEquals(MaterialSummary(3), value(use(i) { MaterialStore(it, context()) { time }.summary("a") }))
    }
    @Test fun commitRevokeRollsBackCreateEditAndDelete() {
        val i = fixture(); denyCommit = true
        assertThrows(StorageAccessRevoked::class.java) { create(i, MaterialInput("synthetic")) }; reset(); assertTrue(board(i).items.isEmpty())
        val old = create(i); denyCommit = true
        assertThrows(StorageAccessRevoked::class.java) { write(i) { it.edit("a", old, old.input().copy(name = "changed")) } }; reset(); assertEquals(listOf(old), board(i).items)
        denyCommit = true
        assertThrows(StorageAccessRevoked::class.java) { write(i) { it.delete("a", old) } }; reset(); assertEquals(listOf(old), board(i).items)
    }
    @Test fun generationRevokeBeforePublicationNeverCallsConsumer() {
        val i = fixture(); val queue = mutableListOf<Runnable>(); val r = repo(i, main = Executor { queue += it }); var calls = 0
        r.create("a", MaterialInput("synthetic")) { calls++ }; generation++; queue.forEach { it.run() }
        assertEquals(0, calls); assertEquals(1, board(i).items.size)
    }
    @Test fun revokeBeforeWorkerDoesNotWriteOrPublish() {
        val i = fixture(); val queue = mutableListOf<Runnable>(); val r = repo(i, worker = Executor { queue += it }); var calls = 0
        r.create("a", MaterialInput("synthetic")) { calls++ }; allowed = false; queue.forEach { it.run() }; reset()
        assertEquals(0, calls); assertTrue(board(i).items.isEmpty())
    }
    @Test fun migrationSevenToEightPreservesP6OldRowsAndInstallsIdenticalTriggers() {
        val i = fixture(); val fresh = materialTriggers(i)
        use(i) { d -> assertTrue(d.advanceAction("a", ActionStatus.PREPARATION, context()) is ActionChangeResult.Applied) }
        val c = context(); val saved = use(i) { d -> d.withWriteTransaction { protocolValue(ProtocolStore(d, c) { time }.create("a", ActionStatus.ACTIVE,
            ProtocolInput(ProtocolClassification.KEY_EVENT, "synthetic P6", 10, description = "synthetic preserved detail", affectedPersonIds = setOf("p")))) } }
        val before = use(i) { d -> protocolValue(ProtocolStore(d, context()) { time }.detail("a", saved.entryId)) }
        val oldTask = task(i); val events = use(i) { it.tasks().events("a") }
        val protected = LeadershipRow("a", "READY", "synthetic-epoch", 1, "Argon2id", 19, 65536, 3, 1, ByteArray(16) { 2 }, ByteArray(88) { 3 })
        use(i) { d -> d.withWriteTransaction { d.leadership().insert(protected) } }
        downgrade(i); val oldTables = tables(i)
        val nonMaterialTriggers = triggers(i).filterKeys { !it.startsWith("materials_") }
        repo(i).summary("a") { assertEquals(MaterialSummary(0), value(it)) }
        assertEquals(oldTables + setOf("materials", "expenses", "expense_payments"), tables(i)); assertEquals(fresh, materialTriggers(i))
        assertEquals(nonMaterialTriggers, triggers(i).filterKeys { !it.startsWith("materials_") })
        assertEquals(before, use(i) { d -> protocolValue(ProtocolStore(d, context()) { time }.detail("a", saved.entryId)) })
        assertEquals(oldTask, task(i)); assertEquals(events, use(i) { it.tasks().events("a") })
        assertArrayEquals(protected.envelope, use(i) { it.leadership().get("a")!!.envelope })
        assertEquals("Motte", use(i) { it.persons().get("a", "p")!!.pseudonym }); create(i)
    }
    @Test fun revokedMigrationKeepsSevenAndRetriesWithoutReplacement() {
        val i = fixture(); val oldTask = task(i); downgrade(i); denyCommit = true
        assertThrows(RuntimeException::class.java) { use(i) {} }; reset()
        B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { assertEquals(7, it.version) }
        assertFalse(tables(i).contains("materials")); assertEquals(oldTask, task(i)) // ordinary retry upgrades to 8
        B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { assertEquals(9, it.version) }
    }
    private fun downgrade(i: ProductInventory) = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE)).use { d ->
        d.beginTransaction(); try {
            removeMaterialSchemaForLegacyFixture(d)
            d.execSQL("UPDATE room_master_table SET identity_hash='224eb402d911910c0e6a646b1a43d735' WHERE id=42")
            d.version = 7; d.setTransactionSuccessful()
        } finally { d.endTransaction() }
    }
    private fun triggers(i: ProductInventory): Map<String, String> = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { d ->
        d.rawQuery("SELECT name,sql FROM sqlite_master WHERE type='trigger' ORDER BY name", emptyArray<String>()).use { c ->
            val result = linkedMapOf<String, String>(); while (c.moveToNext()) result[c.getString(0)] = c.getString(1); result
        }
    }
    private fun materialTriggers(i: ProductInventory) = triggers(i).filterKeys { it.startsWith("materials_") }
    private fun tables(i: ProductInventory): Set<String> = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE), true).use { d ->
        d.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", emptyArray<String>()).use { c ->
            val result = mutableSetOf<String>(); while (c.moveToNext()) result += c.getString(0); result
        }
    }
}
