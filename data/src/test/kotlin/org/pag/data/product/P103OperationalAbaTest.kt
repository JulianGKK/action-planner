package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Negative schema-11 evidence using the real store and outer SQLite transaction. The
 * injected constant WALL clock is not a vendor failure or a new revision authority. */
class P103OperationalAbaTest {
    @Test fun equalWallTimeCanRestoreWholeStateAndOldExpectedStillCommits()=CoreWriterIntegrationTest.H().use{h->
        h.advance(ActionStatus.PREPARATION)
        fun write(expected:PersonOperationalState,status:OperationalStatus,place:String="")=
            h.write<OperationsResult<Unit>>(WorkspaceWriterFamily.OPERATIONS,{it is OperationsResult.Rejected},
                {OperationsResult.Rejected(OperationsFailure.STALE)}){c->
                OperationsStore(h.data,c){42L}.snapshot("a","p0",expected,status,null,place)
            }
        assertTrue(write(h.state(),OperationalStatus.ON_SITE) is OperationsResult.Value)
        val a=h.state()
        assertTrue(write(a,OperationalStatus.UNCLEAR,"Synthetic place") is OperationsResult.Value)
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),write(a,OperationalStatus.LEFT))
        assertTrue(write(h.state(),OperationalStatus.ON_SITE) is OperationsResult.Value)
        assertEquals(a,h.state())
        assertTrue(write(a,OperationalStatus.LEFT) is OperationsResult.Value) // BLOCKER, not ABA safety
        assertEquals(WorkspaceWriteOutcome.COMMITTED,h.completions.last().outcome)
    }
}
