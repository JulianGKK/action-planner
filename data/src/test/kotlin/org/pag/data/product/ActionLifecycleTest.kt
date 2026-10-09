package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class ActionLifecycleTest {
    @Test fun exactMonotoneTransitions() {
        val allowed = setOf(ActionStatus.DRAFT to ActionStatus.PREPARATION,
            ActionStatus.PREPARATION to ActionStatus.ACTIVE, ActionStatus.ACTIVE to ActionStatus.COMPLETED)
        for (from in ActionStatus.entries) for (to in ActionStatus.entries)
            assertEquals(allowed.contains(from to to), ActionLifecycle.mayAdvance(from, to))
    }
    @Test fun completedHasNoReopen() {
        assertTrue(ActionLifecycle.terminal(ActionStatus.COMPLETED))
        assertNull(ActionLifecycle.next(ActionStatus.COMPLETED))
        assertFalse(ActionLifecycle.mayEdit(ActionStatus.COMPLETED))
    }
    @Test fun onlyDraftAndPreparationCanBeEdited() {
        assertEquals(listOf(ActionStatus.DRAFT,ActionStatus.PREPARATION),
            ActionStatus.entries.filter(ActionLifecycle::mayEdit))
    }
    @Test fun deletionIsNeverANormalAdvance() {
        assertNull(ActionLifecycle.next(ActionStatus.DELETION_PENDING))
        ActionStatus.entries.forEach { assertFalse(ActionLifecycle.mayAdvance(it,ActionStatus.DELETION_PENDING)) }
    }
}
