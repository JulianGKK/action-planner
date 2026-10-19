package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.prototype.product.ActiveWorkspaceBudget.*

class ActiveWorkspaceBudgetTest {
    private val limit = ActiveWorkspaceBudget.LIMIT_BYTES

    @Test fun exactEightMiBBoundaryIsAdmittedButNextAllocationIsRefused() {
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
        assertEquals(Decision.ADMITTED, budget.admit(graph, Units(arrays = 1, arrayPayloadBytes = limit - 71)))
        assertEquals(limit, budget.accounting(graph)!!.chargedBytes)
        assertEquals(Decision.BUDGET_REFUSED, budget.admit(graph, Units(references = 1)))
        assertEquals(limit, budget.accounting(graph)!!.chargedBytes); assertFalse(budget.seal(graph))
    }

    @Test fun boundaryPlusOneByteCannotBeMaterializedOrSealed() {
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
        assertEquals(Decision.BUDGET_REFUSED, budget.admit(graph, Units(arrays = 1, arrayPayloadBytes = limit - 70)))
        assertEquals(0L, budget.accounting(graph)!!.chargedBytes); assertFalse(budget.seal(graph))
        assertNull(budget.beginTransition()); assertNull(budget.beginRetained())
    }

    @Test fun multiplicationAndAdditionOverflowNeverWrapToSmallCharge() {
        val shapes = listOf(Units(objects = Long.MAX_VALUE),
            Units(strings = 1, utf16Units = Long.MAX_VALUE),
            Units(arrays = 1, arrayPayloadBytes = Long.MAX_VALUE),
            Units(maps = 1, mapCapacity = Long.MAX_VALUE), Units(references = Long.MAX_VALUE))
        for (units in shapes) {
            val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
            assertEquals(Decision.BUDGET_REFUSED, budget.admit(graph, units))
            assertEquals(0L, budget.accounting(graph)!!.chargedBytes); assertFalse(budget.seal(graph))
        }
    }

    @Test fun negativeAggregatesAreInvalidDataEvenAfterBudgetRefusal() {
        val invalid = listOf(Units(objects = -1), Units(strings = -1), Units(utf16Units = -1),
            Units(arrays = -1), Units(arrayPayloadBytes = -1), Units(lists = -1),
            Units(listCapacity = -1), Units(maps = -1), Units(mapCapacity = -1),
            Units(indexes = -1), Units(indexCapacity = -1), Units(references = -1))
        for (units in invalid) {
            val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
            budget.admit(graph, Units(objects = Long.MAX_VALUE))
            assertEquals(Decision.INVALID_DATA, budget.admit(graph, units))
            assertEquals(Decision.INVALID_DATA, budget.admit(graph, Units()))
            assertFalse(budget.seal(graph))
        }
    }

    @Test fun inconsistentPayloadWithoutAllocationCountIsInvalidData() {
        for (units in listOf(Units(utf16Units = 1), Units(arrayPayloadBytes = 1),
            Units(listCapacity = 1), Units(mapCapacity = 1), Units(indexCapacity = 1))) {
            val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
            assertEquals(Decision.INVALID_DATA, budget.admit(graph, units))
        }
    }

    @Test fun enormousSingleTextIsRefusedUsingOnlyItsLengthBeforeFetch() {
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
        var fetches = 0
        if (budget.admit(graph, Units(strings = 1, utf16Units = Long.MAX_VALUE)) == Decision.ADMITTED) fetches++
        assertEquals(0, fetches); assertFalse(budget.seal(graph))
    }

    @Test fun supplementaryUnicodeIsChargedInUtf16UnitsWithBackingAndAlignment() {
        val value = "A\uD83E\uDD8B" // 2 codepoints, 3 UTF16 units, 6 UTF16 bytes
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
        assertEquals(Decision.ADMITTED, budget.admit(graph, Units(strings = 1, utf16Units = value.length.toLong())))
        assertEquals(71L + 4 * 3, budget.accounting(graph)!!.chargedBytes)
        assertTrue(budget.accounting(graph)!!.chargedBytes > value.toByteArray(Charsets.UTF_16LE).size)
    }

    @Test fun objectsArraysCollectionsIndexesReferencesAndCopiesAreAllCharged() {
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
        val units = Units(objects = 1, strings = 1, utf16Units = 3, arrays = 1, arrayPayloadBytes = 5,
            lists = 1, listCapacity = 2, maps = 1, mapCapacity = 4, indexes = 1, indexCapacity = 2, references = 3)
        assertEquals(Decision.ADMITTED, budget.admit(graph, units))
        val expected = 128L + 71 + 12 + 71 + 5 + 128 + 16 + 128 + 512 + 128 + 256 + 24
        assertEquals(expected, budget.accounting(graph)!!.chargedBytes)
        assertEquals(Decision.ADMITTED, budget.admit(graph, units))
        assertEquals(expected * 2, budget.accounting(graph)!!.chargedBytes) // no dedup assumption
    }

    @Test fun retainedAndExactlyOneTransitionHaveIndependentLimits() {
        val budget = ActiveWorkspaceBudget(); val old = budget.beginRetained()!!
        val full = Units(arrays = 1, arrayPayloadBytes = limit - 71)
        assertNull(budget.beginTransition()); assertEquals(Decision.ADMITTED, budget.admit(old, full))
        assertTrue(budget.seal(old)); assertNull(budget.beginRetained())
        val next = budget.beginTransition()!!; assertNull(budget.beginTransition())
        assertEquals(Decision.ADMITTED, budget.admit(next, full))
        assertEquals(limit * 2, budget.accounting(old)!!.chargedBytes + budget.accounting(next)!!.chargedBytes)
        assertFalse(budget.promote(next)); assertTrue(budget.seal(next)); assertTrue(budget.promote(next))
        assertEquals(Decision.INVALID_DATA, budget.admit(old, Units()))
        assertFalse(budget.promote(next)); assertNotNull(budget.beginTransition())
    }

    @Test fun refusedTransitionCannotPromoteResetOrCreateAnotherTransition() {
        val budget = ActiveWorkspaceBudget(); val old = budget.beginRetained()!!; budget.seal(old)
        val next = budget.beginTransition()!!
        assertEquals(Decision.BUDGET_REFUSED, budget.admit(next, Units(objects = Long.MAX_VALUE)))
        assertFalse(budget.seal(next)); assertFalse(budget.promote(next))
        assertNull(budget.beginTransition()); assertNull(budget.beginRetained())
    }

    @Test fun stickyRefusalStopsFurtherBoundedMaterializationAndDoesNotIncreaseBudget() {
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!; var fetched = 0
        val shapes = listOf(Units(objects = 1), Units(objects = Long.MAX_VALUE), Units(objects = 1))
        for (shape in shapes) {
            if (budget.admit(graph, shape) == Decision.ADMITTED) fetched++
        }
        assertEquals(1, fetched); assertEquals(128L, budget.accounting(graph)!!.chargedBytes)
        assertFalse(budget.seal(graph)); assertEquals(8L * 1024 * 1024, ActiveWorkspaceBudget.LIMIT_BYTES)
    }

    @Test fun sealedOrForeignRepresentationCannotBeChargedAndCloseIsTerminal() {
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
        val other = ActiveWorkspaceBudget()
        assertEquals(Decision.INVALID_DATA, other.admit(graph, Units()))
        assertFalse(other.seal(graph)); assertFalse(other.promote(graph))
        assertTrue(budget.seal(graph)); assertFalse(budget.seal(graph))
        assertEquals(Decision.INVALID_DATA, budget.admit(graph, Units(objects = 1)))
        budget.close(); budget.close()
        assertNull(budget.beginRetained()); assertNull(budget.beginTransition()); assertFalse(budget.promote(graph))
    }

    @Test fun budgetRefusalCannotConferValidityOrStorageRights() {
        val budget = ActiveWorkspaceBudget(); val graph = budget.beginRetained()!!
        var acquisitions = 0
        val validity = ActiveWorkspaceValidity(
            ActiveWorkspaceValidity.Binding("synthetic", org.pag.domain.ActionStatus.ACTIVE, 1, 0),
            object : org.pag.domain.StorageAccess {
                override fun acquire(scope: org.pag.domain.AccessScope): org.pag.domain.StorageContext? {
                    acquisitions++; return null
                }
            }) { ActiveWorkspaceValidity.Binding("synthetic", org.pag.domain.ActionStatus.ACTIVE, 1, 0) }
        assertEquals(Decision.BUDGET_REFUSED, budget.admit(graph, Units(objects = Long.MAX_VALUE)))
        assertFalse(validity.canReuse(setOf(ActiveWorkspaceValidity.Projection.PEOPLE)))
        assertEquals(1, acquisitions); assertNull(validity.beginRead(setOf(ActiveWorkspaceValidity.Projection.PEOPLE)))
    }
}
