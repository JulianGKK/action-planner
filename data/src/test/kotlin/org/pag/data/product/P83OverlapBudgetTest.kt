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
        val padding=(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-baseline.transitionCharge-1024)/80
        h.exec("UPDATE actions SET description=description||printf('%0${padding}d',0)")
        val old=h.read() as ActiveCoreResult.Value
        val next=h.read() as ActiveCoreResult.Value
        assertNotSame(old.core,next.core)
        assertTrue(old.retainedCharge<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
        assertTrue(next.transitionCharge<=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
        val overlappingTransition=Math.addExact(old.retainedCharge,next.transitionCharge)
        assertTrue(overlappingTransition>ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
        // Existing admissions are reader-local, do not reserve old Compose/native references.
        // Promotion or GC would not justify dropping the still strongly reachable old charge.
        assertEquals(old.core.action.description,next.core.action.description)
        println("P83 HOST-CHARACTERIZATION oldR=${old.retainedCharge} newR=${next.retainedCharge} newT=${next.transitionCharge} oldPlusNewT=$overlappingTransition joint=BLOCKER")
    }

    @Test fun realisticSizesUnicodeIncludingHundredRolesKeepCompleteCountsWithoutProductApproval() {
        for((people,tasks,roles) in listOf(Triple(20,40,6),Triple(100,250,6),Triple(200,1000,6),Triple(200,1000,32),Triple(200,1000,100))) {
            ActiveCoreStoreTest.H(people,tasks,roleCount=roles).use { h ->
                h.exec("UPDATE actions SET description=description||'Ä界🦋'")
                val result=h.read()
                assertTrue("$people/$tasks/$roles: $result",result is ActiveCoreResult.Value)
                val core=(result as ActiveCoreResult.Value).core
                assertEquals(people.toLong(),core.people.overall.total);assertEquals(tasks.toLong(),core.tasks.overall.total)
                assertEquals(roles,core.functions.items.size);assertTrue(core.action.description.endsWith("Ä界🦋"))
                println("P83 HOST-FIXTURE $people/$tasks/$roles retained=${h.admission?.retained} transition=${h.admission?.transition} result=${h.admission?.decision}")
            }
        }
    }
    @Test fun productLoaderRefusesOldComposePlusNewReadBeforeAnyNewSqlMaterialization() = ActiveCoreStoreTest.H(200,1000).use { h ->
        val baseline=h.read() as ActiveCoreResult.Value
        val padding=(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-baseline.transitionCharge-1024)/80
        h.exec("UPDATE actions SET description=description||printf('%0${padding}d',0)")
        val worker=java.util.ArrayDeque<Runnable>();val main=java.util.ArrayDeque<Runnable>()
        val mutations=UncachedWorkspaceMutations();var reads=0
        val loader=ProductActiveCoreLoader(object:StorageAccess{override fun acquire(scope:AccessScope)=h.context},
            mutations,{worker.add(it)},{main.add(it)}){_,_,_,memory->
                reads++;h.read(outstanding=memory::admit)
            }
        var lifetime:ActiveCoreLifetime?=null;var old:ActiveCoreResult.Value?=null
        assertTrue(loader.loadOwned("a"){value,life->old=value as ActiveCoreResult.Value;lifetime=life})
        worker.remove().run();main.remove().run()
        assertTrue(old!!.retainedCharge<=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
        assertTrue(old!!.transitionCharge in (ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES-1200)..ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
        lifetime!!.compositionObserved();lifetime!!.close();lifetime!!.compositionDisposed()
        val before=h.db.payloadStatements;val epoch=mutations.mutationEpoch
        repeat(2){assertFalse(loader.loadOwned("a"){_,_->fail("Unbudgeted delivery")})}
        assertEquals(1,reads);assertEquals(before,h.db.payloadStatements);assertEquals(epoch,mutations.mutationEpoch)
        assertTrue(worker.isEmpty());assertTrue(main.isEmpty())
        assertEquals(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES,loader.outstandingState().transition)
        assertFalse(loader.outstandingState().retirementConfirmed)
        println("P84 PRODUCT-REFUSAL oldR=${old!!.retainedCharge} plannedNewT=${old!!.transitionCharge} sum=${old!!.retainedCharge+old!!.transitionCharge} readCount=$reads beforeNewSQL=true")
    }

    @Test fun normalNativeShaped2001000PlanPassesProductAdmissionAgainAfterUnpublishedRetirement() = ActiveCoreStoreTest.H(200,1000).use {h->
        val worker=java.util.ArrayDeque<Runnable>();val main=java.util.ArrayDeque<Runnable>()
        val mutations=UncachedWorkspaceMutations();var reads=0;var delivered=0
        val loader=ProductActiveCoreLoader(object:StorageAccess{override fun acquire(scope:AccessScope)=h.context},
            mutations,{worker.add(it)},{main.add(it)}){_,_,_,memory->reads++;h.read(outstanding=memory::admit)}
        assertTrue(loader.loadOwned("a"){_,_->fail("Old canceled result")})
        worker.remove().run();val obsolete=main.remove()
        assertEquals(1,loader.outstandingState().pendingCandidates)
        assertFalse(loader.loadOwned("a"){_,_->fail()})
        loader.cancelPending()
        assertFalse(loader.outstandingState().retirementConfirmed)
        assertFalse(loader.loadOwned("a"){_,_->fail("Before Main acknowledgement")})
        obsolete.run() // Drained shell acknowledged; no result/callback can escape.
        assertTrue(loader.outstandingState().retirementConfirmed)
        assertEquals(0,loader.outstandingState().pendingCandidates)
        assertTrue(loader.loadOwned("a"){value,life->
            assertTrue(value is ActiveCoreResult.Value);assertNotNull(life);delivered++
        })
        obsolete.run();obsolete.run();assertEquals(0,delivered)
        worker.remove().run();main.remove().run()
        assertEquals(2,reads);assertEquals(1,delivered)
        assertTrue(loader.outstandingState().localRetained in 1..ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES)
        assertTrue(loader.outstandingState().localTransition in 1..ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES)
        assertEquals(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES,loader.outstandingState().transition)
        assertEquals(0L,mutations.mutationEpoch)
    }

}
