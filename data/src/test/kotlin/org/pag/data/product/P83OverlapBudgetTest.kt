package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Characterization of the existing receipts, NOT joint UI/native acceptance.
 * Both values deliberately stay strongly live here. No extra production ledger is created.
 */
class P83OverlapBudgetTest {
    @Test fun individuallyAdmittedReadsCanExceedTransitionWhenOldGraphStillLives() = ActiveCoreStoreTest.H(200,1000).use { h ->
        val baseline=h.read() as ActiveCoreResult.Value
        val padding=(ActiveWorkspaceBudget.LIMIT_BYTES-baseline.transitionCharge-1024)/80
        h.exec("UPDATE actions SET description=description||printf('%0${padding}d',0)")
        val old=h.read() as ActiveCoreResult.Value
        val next=h.read() as ActiveCoreResult.Value
        assertNotSame(old.core,next.core)
        assertTrue(old.retainedCharge<=ActiveWorkspaceBudget.LIMIT_BYTES)
        assertTrue(next.transitionCharge<=ActiveWorkspaceBudget.LIMIT_BYTES)
        val overlappingTransition=Math.addExact(old.retainedCharge,next.transitionCharge)
        assertTrue(overlappingTransition>ActiveWorkspaceBudget.LIMIT_BYTES)
        // Existing admissions are reader-local, do not reserve old Compose/native references.
        // Promotion or GC would not justify dropping the still strongly reachable old charge.
        assertEquals(old.core.action.description,next.core.action.description)
        println("P83 HOST-CHARACTERIZATION oldR=${old.retainedCharge} newR=${next.retainedCharge} newT=${next.transitionCharge} oldPlusNewT=$overlappingTransition joint=BLOCKER")
    }

    @Test fun realisticSizesUnicodeAndRoleRefusalKeepCompleteCountsAndReasons() {
        for((people,tasks,roles) in listOf(Triple(20,40,6),Triple(100,250,6),Triple(200,1000,6),Triple(200,1000,32),Triple(200,1000,100))) {
            ActiveCoreStoreTest.H(people,tasks,roleCount=roles).use { h ->
                h.exec("UPDATE actions SET description=description||'Ä界🦋'")
                val result=h.read()
                if(roles==100){
                    assertEquals(ActiveCoreResult.Rejected(BoundedActiveStatus.BUDGET_REFUSED),result)
                    assertTrue(h.admission!!.transition>ActiveWorkspaceBudget.LIMIT_BYTES)
                    assertEquals(0,h.db.blobReads);assertEquals(0,h.db.payloadStatements)
                } else {
                    assertTrue("$people/$tasks/$roles: $result",result is ActiveCoreResult.Value)
                    val core=(result as ActiveCoreResult.Value).core
                    assertEquals(people.toLong(),core.people.overall.total);assertEquals(tasks.toLong(),core.tasks.overall.total)
                    assertEquals(roles,core.functions.items.size);assertTrue(core.action.description.endsWith("Ä界🦋"))
                }
                println("P83 HOST-FIXTURE $people/$tasks/$roles retained=${h.admission?.retained} transition=${h.admission?.transition} result=${h.admission?.decision}")
            }
        }
    }
}
