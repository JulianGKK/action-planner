package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Runs the production SQL/decoder/materializer against real in-memory SQLite and schema 11. */
class BoundedActiveStoreTest {
    private class H(encoding: String = "UTF-8") : AutoCloseable {
        val db = SqliteHostConnection(encoding)
        var allowed = true
        var generation = 1L
        var actionId = "a"
        var bound = true
        var publications = 0
        var blocks = 0
        var onPublication: () -> Unit = {}
        var onCleanup: () -> Unit = {}
        val phases = mutableListOf<BoundedActiveStore.Phase>()
        var onPhase: (BoundedActiveStore.Phase) -> Unit = {}
        val c = object : StorageContext {
            override val scope = AccessScope.READY
            override val generation = 1L
            override fun check(point: AccessPoint) = allowed && this@H.generation == generation
            override fun publish(result: () -> Unit): Boolean {
                assertEquals(0, db.openStatements)
                onPublication()
                if (!check(AccessPoint.PUBLICATION)) return false
                result(); publications++; return true
            }
        }
        fun exec(sql: String) = runBlocking { db.execute(sql) }
        fun seed(people: Int = 1, roles: Int = 1) {
            exec("INSERT INTO actions VALUES('a','Synthetic action','Synthetic description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            if (people > 0) {
                exec("WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i+1 FROM n WHERE i<$people) " +
                    "INSERT INTO persons SELECT 'p'||i,'a','synthetic '||i,'synthetic '||i,i FROM n")
                exec("INSERT INTO person_operational_states SELECT 'a',id,NULL,'ON_SITE',NULL,0 FROM persons")
            }
            if (roles > 0) exec("WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i+1 FROM n WHERE i<$roles) " +
                "INSERT INTO roles SELECT 'r'||i,'a','synthetic role '||i,'synthetic role '||i,i FROM n")
        }
        fun read(id: String = "a", requestedGeneration: Long = 1): BoundedActiveResult = boundedActiveRead(
            BoundedActiveBinding(id, requestedGeneration),
            object : StorageAccess { override fun acquire(scope: AccessScope) = c.takeIf { allowed } },
            { if (bound) BoundedActiveBinding(actionId, generation) else null }, { blocks++ }) { context, current ->
            runBlocking {
                db.execute("BEGIN")
                try {
                    BoundedActiveStore(db, context, current, { phases.add(it); onPhase(it) })
                        .read(BoundedActiveBinding(id, requestedGeneration))
                } finally {
                    db.execute("ROLLBACK"); assertEquals(0, db.openStatements); onCleanup()
                }
            }
        }
        override fun close() { assertEquals(0, db.openStatements); db.close(); assertTrue(db.closed) }
    }
    private fun rejected(status: BoundedActiveStatus, result: BoundedActiveResult) =
        assertEquals(BoundedActiveResult.Rejected(status), result)

    @Test fun validSmallActiveReadHasExactExpectedDomainProjection() = H().use { h ->
        h.seed()
        h.exec("INSERT INTO person_planned_roles VALUES('a','p1','r1')")
        h.exec("INSERT INTO person_role_plans VALUES('a','p1','r1')")
        h.exec("UPDATE person_operational_states SET currentRoleId='r1' WHERE personId='p1'")
        val result = h.read() as BoundedActiveResult.Value
        assertEquals(Action("a", "Synthetic action", "Synthetic description", 1, 2, 0, ActionStatus.ACTIVE), result.action)
        assertEquals(OperationalBoard("a", ActionStatus.ACTIVE, listOf(Role("r1", "a", "synthetic role 1", 1)),
            listOf(OperationalPerson(Person("p1", "a", "synthetic 1", 1), PersonRolePlan(setOf("r1"), "r1"),
                PersonOperationalState("a", "p1", "r1", OperationalStatus.ON_SITE, null, 0)))), result.operations)
        assertTrue(result.retainedCharge <= ActiveWorkspaceBudget.LIMIT_BYTES)
        assertTrue(result.transitionCharge <= ActiveWorkspaceBudget.LIMIT_BYTES)
        assertTrue(h.phases.indexOf(BoundedActiveStore.Phase.ADMITTED) < h.phases.indexOf(BoundedActiveStore.Phase.TEXT))
        assertEquals(1, h.db.textReads) // Only fixed PRAGMA encoding, never payload getText().
    }

    @Test fun preparationCompletedAndMissingOrForeignActionAreDeniedWithoutPayload() {
        for (status in listOf("PREPARATION", "COMPLETED")) H().use { h ->
            h.seed(); h.exec("UPDATE actions SET status='$status',completedAt=${if (status == "COMPLETED") "3" else "NULL"}")
            rejected(BoundedActiveStatus.ACCESS_REVOKED, h.read()); assertEquals(0, h.db.blobReads)
        }
        H().use { h -> h.seed(); rejected(BoundedActiveStatus.ACCESS_REVOKED, h.read("foreign")); assertEquals(0, h.db.blobReads) }
        H().use { h -> h.seed(); h.bound = false; rejected(BoundedActiveStatus.ACCESS_REVOKED, h.read()); assertEquals(0, h.db.blobReads) }
    }

    @Test fun invalidRolePersonAndPrimaryRoleReferencesFailBeforeAdmission() {
        val changes = listOf(
            "UPDATE person_operational_states SET currentRoleId='foreign'",
            "INSERT INTO person_planned_roles VALUES('a','missing','r1')",
            "INSERT INTO person_role_plans VALUES('a','p1','r1')",
            "DELETE FROM person_operational_states")
        for (change in changes) H().use { h ->
            h.seed(); h.exec("PRAGMA foreign_keys=OFF"); h.exec(change)
            rejected(BoundedActiveStatus.INVALID_DATA, h.read())
            assertFalse(BoundedActiveStore.Phase.ADMITTED in h.phases); assertEquals(0, h.db.blobReads)
        }
    }

    @Test fun manyPeopleAreRefusedBeforeAnyFullRowOrTextMaterialization() = H().use { h ->
        h.seed(5000, 1)
        rejected(BoundedActiveStatus.BUDGET_REFUSED, h.read())
        assertEquals(0, h.db.blobReads); assertEquals(0L, h.db.payloadBytes)
        assertEquals(0, h.db.payloadStatements)
        assertFalse(BoundedActiveStore.Phase.ROW in h.phases)
    }

    @Test fun countAndMaximumGuardsDenyBeforePotentiallyLargeSqlSums() {
        for (hugeCount in listOf(false, true)) H().use { h ->
            h.seed(if (hugeCount) 33000 else 1)
            if (!hugeCount) h.exec("UPDATE actions SET description=printf('%08388609d',0)")
            var sums = 0
            h.db.beforePrepare = { if (it.contains("SUM(length")) sums++ }
            rejected(BoundedActiveStatus.BUDGET_REFUSED, h.read())
            assertEquals(0, sums); assertEquals(0, h.db.blobReads); assertEquals(0, h.db.payloadStatements)
        }
    }

    @Test fun manyRolesAndPlanningRelationsAreRefusedBeforeMaterialization() = H().use { h ->
        h.seed(1, 1000)
        h.exec("INSERT INTO person_planned_roles SELECT 'a','p1',id FROM roles")
        rejected(BoundedActiveStatus.BUDGET_REFUSED, h.read()); assertEquals(0, h.db.blobReads)
    }

    @Test fun longSingleActionPersonOrRoleTextIsRefusedBeforeBlobFetch() {
        for (change in listOf("UPDATE actions SET description=printf('%01000000d',0)",
            "UPDATE persons SET pseudonym=printf('%01000000d',0)",
            "UPDATE roles SET name=printf('%01000000d',0)")) H().use { h ->
            h.seed(); h.exec(change)
            rejected(BoundedActiveStatus.BUDGET_REFUSED, h.read()); assertEquals(0, h.db.blobReads)
            assertEquals(0, h.db.payloadStatements)
        }
    }

    @Test fun embeddedNulAndSupplementaryUnicodePreserveCompleteByteContent() = H().use { h ->
        h.seed()
        h.exec("UPDATE persons SET pseudonym='synthetic'||char(0)||char(129419),pseudonymNormalized='synthetic'||char(0)||char(129419)")
        h.exec("UPDATE actions SET title='Synthetic'||char(0)||char(129419)")
        val result = h.read() as BoundedActiveResult.Value
        assertEquals("synthetic\u0000\uD83E\uDD8B", result.operations.persons.single().person.pseudonym)
        assertEquals("Synthetic\u0000\uD83E\uDD8B", result.action.title)
    }

    @Test fun malformedUtf8AndDomainInvalidRowsAreNotBudgetPressure() {
        for (change in listOf("UPDATE persons SET pseudonym=CAST(x'80' AS TEXT)",
            "UPDATE persons SET pseudonym=CAST(x'EDA080' AS TEXT)", // UTF8 surrogate is forbidden
            "UPDATE persons SET pseudonym=CAST(x'F0808080' AS TEXT)", // overlong NUL
            "UPDATE persons SET pseudonym=CAST(x'F4908080' AS TEXT)", // outside Unicode range
            "UPDATE roles SET normalizedName='mismatch'", "UPDATE persons SET pseudonym=' '",
            "UPDATE actions SET expectedEndAt=0", "UPDATE person_operational_states SET operationalStatus='BROKEN'")) H().use { h ->
            h.seed(); h.exec(change); rejected(BoundedActiveStatus.INVALID_DATA, h.read())
        }
    }

    @Test fun wrongSqlTypesFailBeforePayloadAndDbFailureStaysStorageFailure() = H().use { h ->
        h.seed(); h.exec("UPDATE persons SET createdAt=x'00'")
        rejected(BoundedActiveStatus.INVALID_DATA, h.read()); assertEquals(0, h.db.blobReads)
        h.exec("UPDATE persons SET createdAt=1")
        h.exec("DROP TABLE person_role_plans")
        rejected(BoundedActiveStatus.STORAGE_FAILURE, h.read())
    }

    @Test fun wrongDatabaseEncodingIsNotGuessed() = H("UTF-16le").use { h ->
        h.seed()
        rejected(BoundedActiveStatus.INVALID_DATA, h.read()); assertEquals(0, h.db.blobReads)
    }

    @Test fun nulCannotHideAnOversizedSuffixFromAdmission() = H().use { h ->
        h.seed(); h.exec("UPDATE actions SET title='Synthetic'||char(0)||printf('%01000000d',0)")
        rejected(BoundedActiveStatus.BUDGET_REFUSED, h.read()); assertEquals(0, h.db.blobReads)
    }

    @Test fun unicodeNormalizationExpansionAndContextKeepDomainRules() = H().use { h ->
        h.seed()
        h.exec("UPDATE persons SET pseudonym=char(304),pseudonymNormalized='i'||char(775)")
        h.exec("UPDATE roles SET name=char(927)||char(931),normalizedName=char(959)||char(962)")
        val value = h.read() as BoundedActiveResult.Value
        assertEquals("\u0130", value.operations.persons.single().person.pseudonym)
        assertEquals("\u039F\u03A3", value.operations.roles.single().name)
    }

    @Test fun exactReaderBudgetAndNextByteAreCheckedBeforeMaterializing() = H().use { h ->
        h.seed(0, 3) // This cardinality makes the fixed charge congruent with 8MiB mod 80.
        val first = h.read() as BoundedActiveResult.Value
        val difference = ActiveWorkspaceBudget.LIMIT_BYTES - first.transitionCharge
        assertEquals(0L, difference % 80)
        val padding = difference / 80
        h.exec("UPDATE actions SET description=description||printf('%0${padding}d',0)")
        val exact = h.read() as BoundedActiveResult.Value
        assertEquals(ActiveWorkspaceBudget.LIMIT_BYTES, exact.transitionCharge)
        val blobs = h.db.blobReads; val statements = h.db.payloadStatements
        h.exec("UPDATE actions SET description=description||'x'")
        rejected(BoundedActiveStatus.BUDGET_REFUSED, h.read()); assertEquals(blobs, h.db.blobReads)
        assertEquals(statements, h.db.payloadStatements)
    }

    @Test fun batchesAreDeterministicAndDoNotMixSecondAction() = H().use { h ->
        h.seed(40, 2)
        h.exec("INSERT INTO actions VALUES('b','Other synthetic','description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
        h.exec("INSERT INTO persons VALUES('other','b',printf('%01000000d',0),'other',0)")
        val result = h.read() as BoundedActiveResult.Value
        assertEquals(40, result.operations.persons.size)
        assertEquals((1..40).map { "p$it" }, result.operations.persons.map { it.person.id })
        assertTrue(result.operations.persons.all { it.person.actionId == "a" })
    }

    @Test fun revokeAtEveryReadBoundaryReturnsNoPartialContentAndClosesStatements() {
        for (at in BoundedActiveStore.Phase.entries) H().use { h ->
            h.seed(); h.onPhase = { if (it == at) h.allowed = false }
            rejected(BoundedActiveStatus.ACCESS_REVOKED, h.read())
        }
    }

    @Test fun generationAndBindingChangesDuringMaterializationDenyResult() {
        for (changeGeneration in listOf(false, true)) H().use { h ->
            h.seed(); h.onPhase = { if (it == BoundedActiveStore.Phase.ROW) {
                if (changeGeneration) h.generation = 2 else h.bound = false
            } }
            rejected(BoundedActiveStatus.ACCESS_REVOKED, h.read())
        }
        H().use { h -> h.seed(); rejected(BoundedActiveStatus.ACCESS_REVOKED, h.read(requestedGeneration = 2)); assertEquals(0, h.db.blobReads) }
    }

    @Test fun realSqlIntegerSumOverflowIsAnErrorNotFloatOrWrappedBudget() = H().use { h ->
        h.exec("CREATE TABLE overflow_fixture(value INTEGER)")
        h.exec("INSERT INTO overflow_fixture VALUES(9223372036854775807),(1)")
        assertThrows(IllegalStateException::class.java) { runBlocking {
            h.db.usePrepared("SELECT SUM(value) FROM overflow_fixture") { it.step() }
        } }
        assertEquals(0, h.db.openStatements)
    }

    @Test fun noLatePublicationAfterCleanupLockGenerationOrActionChange() {
        for (kind in 0..2) H().use { h ->
            h.seed(); h.onPublication = {
                when (kind) { 0 -> h.allowed = false; 1 -> h.generation = 2; else -> h.actionId = "other" }
            }
            rejected(BoundedActiveStatus.ACCESS_REVOKED, h.read())
            assertEquals(0, h.db.openStatements)
            assertFalse(h.phases.isEmpty()) // real query path did run before final denial
        }
    }

    @Test fun cleanupFailureOverridesSuccessfulReadAndBudgetRefusal() {
        for (tooLarge in listOf(false, true)) H().use { h ->
            h.seed(if (tooLarge) 1000 else 1)
            h.onCleanup = { error("synthetic cleanup failure") }
            rejected(BoundedActiveStatus.STORAGE_FAILURE, h.read())
            assertEquals(1, h.blocks); assertEquals(0, h.publications)
        }
    }

    @Test fun budgetRefusalDoesNotBlockStorageButInvalidDataDoes() = H().use { h ->
        h.seed(); h.exec("UPDATE actions SET description=printf('%01000000d',0)")
        rejected(BoundedActiveStatus.BUDGET_REFUSED, h.read()); assertEquals(0, h.blocks)
        h.exec("UPDATE actions SET description='valid',sensitivity='BROKEN'")
        rejected(BoundedActiveStatus.INVALID_DATA, h.read()); assertEquals(1, h.blocks)
    }

    @Test fun coroutineCanResumeOnDifferentThreadsWithoutReusingThreadConfinedLedger() = H().use { h ->
        h.seed(); h.exec("BEGIN")
        val dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                Thread(block, "bounded-reader-host").start()
            }
        }
        val sql = object : androidx.room3.PooledConnection {
            override suspend fun <R> usePrepared(sql: String, block: suspend (androidx.sqlite.SQLiteStatement) -> R): R {
                kotlinx.coroutines.yield()
                return h.db.usePrepared(sql, block)
            }
        }
        try {
            val value = runBlocking(dispatcher) {
                BoundedActiveStore(sql, h.c, { true }).read(BoundedActiveBinding("a", 1))
            }
            assertEquals(1, value.operations.persons.size)
        } finally { h.exec("ROLLBACK"); assertEquals(0, h.db.openStatements) }
    }
}
