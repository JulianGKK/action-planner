package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Offline preparation only. A later isolated runtime run requires separate artifact approval. */
class BoundedTaskRoomTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "org.pag.data.unlockprobe")
    }
    private fun complete(f: P11SyntheticFixture): TaskBoard {
        val result = f.boundedTasks()
        assertTrue("Expected complete admitted TaskBoard", result is BoundedTaskResult.Value)
        return (result as BoundedTaskResult.Value).board
    }
    private fun legacy(f: P11SyntheticFixture): TaskBoard {
        var board: TaskBoard? = null
        f.tasks.board(f.activeId) { board = (it as OperationsResult.Value).value }
        return checkNotNull(board)
    }
    private fun same(expected: TaskBoard, actual: TaskBoard, eventCount: Int) {
        // Legacy list() has no ORDER BY. Keep content and all public projection contracts.
        assertEquals(expected.actionId, actual.actionId)
        assertEquals(expected.tasks.toSet(), actual.tasks.toSet())
        assertEquals(expected.events, actual.events) // Every event, including exact tie order.
        assertEquals(expected.active, actual.active)
        assertEquals(expected.closed, actual.closed)
        assertEquals(expected.affectedCount("${expected.actionId}-person-0"),
            actual.affectedCount("${actual.actionId}-person-0"))
        assertEquals(40, actual.tasks.size)
        assertEquals(eventCount, actual.events.size)
        assertTrue(actual.tasks.size > 32 && actual.events.size > 32)
        assertEquals(actual.events.size, actual.events.map { it.id }.toSet().size)
    }
    @Test fun boundedTasksMatchLegacyBoardIncludingEventsAndDerivedViews() {
        P11SyntheticFixture(app, taskCountPerAction = 40).use { f ->
            val before = legacy(f)
            same(before, complete(f), 40)
            val t = before.tasks.first()
            var transitions = 0
            f.tasks.transition(f.activeId, t, TaskStatus.IN_PROGRESS) {
                assertTrue(it is OperationsResult.Value); transitions++
            }
            assertEquals(1, transitions)
            val after = legacy(f)
            same(after, complete(f), 41)
            assertEquals(1, after.events.count { it.type == TaskEventType.STARTED && it.taskId == t.id })
            assertTrue(after.events.containsAll(before.events))
        }
    }
    @Test fun nonActiveAndMissingAreDenied() {
        P11SyntheticFixture(app, taskCountPerAction = 40).use { f ->
            for (id in listOf(f.preparationId, f.completedId, "missing-synthetic"))
                assertEquals(denied, f.boundedTasks(id))
        }
    }
    @Test fun publicationAndRevokeCannotReturnTaskPayload() {
        P11SyntheticFixture(app, taskCountPerAction = 40).use { f ->
            assertEquals(40, complete(f).tasks.size)
            var attempts = 0
            assertEquals(denied, f.boundedTasks(allowPublication = false, onPublicationAttempt = { attempts++ }))
            assertEquals(1, attempts) // Must reach publication AFTER successful materialization/cleanup.
            assertEquals(40, complete(f).events.size)
            f.revoke()
            assertEquals(denied, f.boundedTasks(onPublicationAttempt = { attempts++ }))
            assertEquals(1, attempts)
        }
    }
    @Test fun oversizeTemplateRefusedAndRepeatedReadsRemainConsistent() {
        P11SyntheticFixture(app, taskCountPerAction = 40).use { f ->
            val first = complete(f)
            assertEquals(40, first.tasks.size); assertEquals(40, first.events.size)
            val before = java.io.File("/proc/self/fd").list()!!.size
            repeat(3) { assertEquals(first, complete(f)) }
            assertTrue(java.io.File("/proc/self/fd").list()!!.size <= before + 2)
            var creations = 0
            f.tasks.create(f.activeId, TaskInput("Synthetic oversized template", sourceTemplateKey = "x".repeat(200000))) {
                assertTrue(it is OperationsResult.Value); creations++
            }
            assertEquals(1, creations)
            assertEquals(BoundedTaskResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED), f.boundedTasks())
        }
    }
    private val denied get() = BoundedTaskResult.Rejected(BoundedActiveStatus.ACCESS_REVOKED)
}
