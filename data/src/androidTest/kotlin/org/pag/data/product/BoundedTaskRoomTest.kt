package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Offline compile only. A later isolated runtime run requires separate approval/artifact gate. */
class BoundedTaskRoomTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "org.pag.data.unlockprobe")
    }
    @Test fun boundedTasksMatchLegacyBoardIncludingEventsAndDerivedViews() {
        P11SyntheticFixture(app).use { f ->
            var expected: TaskBoard? = null
            f.tasks.board(f.activeId) { expected = (it as OperationsResult.Value).value }
            val t = expected!!.tasks.first()
            f.tasks.transition(f.activeId, t, TaskStatus.IN_PROGRESS) { assertTrue(it is OperationsResult.Value) }
            f.tasks.board(f.activeId) { expected = (it as OperationsResult.Value).value }
            val legacy = checkNotNull(expected)
            val actual = (f.boundedTasks() as BoundedTaskResult.Value).board
            // Legacy list() has no ORDER BY. Compare identity/content and the public sort contracts.
            assertEquals(legacy.tasks.toSet(), actual.tasks.toSet())
            assertEquals(legacy.events, actual.events)
            assertEquals(legacy.active, actual.active)
            assertEquals(legacy.closed, actual.closed)
            assertEquals(100, actual.tasks.size)
            assertEquals(101, actual.events.size)
        }
    }
    @Test fun nonActiveAndMissingAreDenied() {
        P11SyntheticFixture(app).use { f ->
            for (id in listOf(f.preparationId, f.completedId, "missing-synthetic"))
                assertEquals(denied, f.boundedTasks(id))
        }
    }
    @Test fun publicationAndRevokeCannotReturnTaskPayload() {
        P11SyntheticFixture(app).use { f ->
            assertEquals(denied, f.boundedTasks(allowPublication = false))
            assertTrue(f.boundedTasks() is BoundedTaskResult.Value)
            f.revoke(); assertEquals(denied, f.boundedTasks())
        }
    }
    @Test fun oversizeTemplateRefusedAndRepeatedReadsRemainConsistent() {
        P11SyntheticFixture(app).use { f ->
            val first = f.boundedTasks()
            val before = java.io.File("/proc/self/fd").list()!!.size
            repeat(3) { assertEquals(first, f.boundedTasks()) }
            assertTrue(java.io.File("/proc/self/fd").list()!!.size <= before + 2)
            f.tasks.create(f.activeId, TaskInput("Synthetic oversized template", sourceTemplateKey = "x".repeat(200000))) {
                assertTrue(it is OperationsResult.Value)
            }
            assertEquals(BoundedTaskResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED), f.boundedTasks())
        }
    }
    private val denied get() = BoundedTaskResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED)
}
