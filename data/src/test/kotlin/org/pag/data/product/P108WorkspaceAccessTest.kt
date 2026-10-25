package org.pag.data.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

/** Real original record + writer port. No Android/SQLCipher or global owner-end inference. */
class P108WorkspaceAccessTest {
    private class H(r:Long=100_000,t:Long=200_000) {
        var allowed=true;var generation=1L;var acquires=0;var lateFalse=false
        val mutations=UncachedWorkspaceMutations()
        val source=object:StorageAccess {
            override fun acquire(scope:AccessScope):StorageContext? {
                acquires++;if(!allowed)return null
                val gen=generation
                return object:StorageContext {
                    override val scope=AccessScope.READY;override val generation=gen
                    override fun check(point:AccessPoint)=allowed && gen==this@H.generation
                    override fun publish(result:()->Unit):Boolean {if(!check(AccessPoint.PUBLICATION))return false;result();return !lateFalse && check(AccessPoint.PUBLICATION)}
                }
            }
        }
        val context=source.acquire(AccessScope.READY)!!
        val ticket=mutations.beforeActiveRead("a",1)!!
        val record=OutstandingCoreRead()
        val entry=record.beginFullview("a",1)!!
        val request=WorkspaceReadRequest("a",ActionStatus.PREPARATION,1,emptySet(),ticket=WorkspaceReadTicket())
        val access:OriginalWorkspaceAccess
        init {
            assertTrue(record.enterFullview(entry,request))
            fun bytes(n:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=n-71)
            assertTrue(entry.admit(ActiveCoreReservation(bytes(r),bytes(t))))
            entry.worker(Runnable{assertTrue(entry.materialize())}).run()
            access=OriginalWorkspaceAccess(entry,source,context,ticket,mutations,request,Thread.currentThread())
        }
    }
    @Test fun sameOriginalReferencePublicationReacquiresAndCannotOpenAnotherFullRead() {
        val h=H();val data=Any();var shown:Any?=null;val before=h.acquires
        assertTrue(h.access.publish("a",ActionStatus.PREPARATION,1){shown=data})
        assertSame(data,shown);assertTrue(h.acquires>before)
        assertNull(h.record.beginFullview("a",1));assertNull(h.record.begin())
        assertEquals(16_777_216L,h.record.state().transition)
    }
    @Test fun exactCumulativeRetainedAndTransitionPlusOnePreserveOldBookings() {
        for(which in listOf("r","t")) {
            val limit=if(which=="r")8_388_608L else 16_777_216L
            val h=H(if(which=="r")limit-71 else 100_000,if(which=="t")limit-71 else 200_000)
            assertTrue(h.access.reserve(ActiveWorkspaceBudget.Units(arrays=1)))
            val before=h.record.state()
            assertFalse(h.access.reserve(ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=1)))
            assertEquals(before,h.record.state())
            assertFalse(h.access.reserve(ActiveWorkspaceBudget.Units(objects=Long.MAX_VALUE)))
            assertEquals(before,h.record.state())
        }
    }
    @Test fun wrongBindingForeignThreadRevokeGenerationAndCancelledRequestCannotResolve() {
        val h=H();var calls=0
        assertFalse(h.access.publish("b",ActionStatus.PREPARATION,1){calls++})
        assertFalse(h.access.publish("a",ActionStatus.ACTIVE,1){calls++})
        assertFalse(h.access.publish("a",ActionStatus.PREPARATION,2){calls++})
        val foreign=AtomicInteger();Thread{if(h.access.publish("a",ActionStatus.PREPARATION,1){calls++})foreign.incrementAndGet()}.apply{start();join()}
        assertEquals(0,foreign.get());h.request.ticket!!.cancel()
        assertFalse(h.access.publish("a",ActionStatus.PREPARATION,1){calls++})
        assertFalse(h.access.reserve(ActiveWorkspaceBudget.Units(objects=1)))
        val g=H();g.generation=2;assertFalse(g.access.publish("a",ActionStatus.PREPARATION,1){calls++})
        val lock=H();lock.allowed=false;assertFalse(lock.access.publish("a",ActionStatus.PREPARATION,1){calls++})
        assertEquals(0,calls)
    }
    @Test fun pendingUnknownAndOldEpochAreTerminalForOriginalCapability() {
        for(outcome in WorkspaceWriteOutcome.entries) {
            val h=H();val p=h.mutations.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.PERSON,"a"),1)!!
            assertFalse(h.access.publish("a",ActionStatus.PREPARATION,1){fail("Pending")})
            p.finish(WorkspaceWriteCompletion(outcome,WorkspaceWriteCleanup.COMPLETE))
            assertFalse(h.access.publish("a",ActionStatus.PREPARATION,1){fail("Old epoch")})
            assertFalse(h.access.reserve(ActiveWorkspaceBudget.Units(objects=1)))
            assertNull(h.record.beginFullview("a",1))
        }
    }
    @Test fun supplementsShareOneQuotaPendingAndOldAcksNeverReleaseParentOrNewTransition() {
        val h=H();val a=h.entry.supplement(WorkspacePart.MATERIAL_BOARD)!!
        assertNull(h.entry.supplement(WorkspacePart.EXPENSE_BOARD))
        var main:Runnable?=null
        val work=a.worker(Runnable{
            assertTrue(a.admit(FullviewPreflight.reservation(1,4,10)))
            main=a.main(Runnable{assertNull(h.entry.supplement(WorkspacePart.EXPENSE_BOARD))})
        })
        work.run();assertNull(h.entry.supplement(WorkspacePart.EXPENSE_BOARD));main!!.run();main!!.run();work.run()
        assertNull(h.entry.supplement(WorkspacePart.MATERIAL_BOARD)) // no re-materialization of already issued board
        val b=h.entry.supplement(WorkspacePart.EXPENSE_BOARD)!!
        a.workerRejected();a.mainRejected();assertNull(h.entry.supplement(WorkspacePart.TASKS))
        b.worker(Runnable{}).run();assertNotNull(h.entry.supplement(WorkspacePart.TASKS))
        assertNull(h.record.beginFullview("a",1));assertFalse(h.record.state().retirementConfirmed)
    }
    @Test fun lostMainOrUnconfirmedCleanupNeverAuthorizesAnotherSupplement() {
        for(uncertain in listOf(false,true)){
            val h=H();val a=h.entry.supplement(WorkspacePart.TASKS)!!
            a.worker(Runnable{if(uncertain)a.unconfirmed() else a.main(Runnable{})}).run()
            assertNull(h.entry.supplement(WorkspacePart.MATERIAL_BOARD))
            if(uncertain)assertFalse(h.access.publish("a",ActionStatus.PREPARATION,1){fail("cleanup unknown")})
        }
    }
    @Test fun falseAfterCallbackIsNotAReusableAuthorizationOrUndoPromise() {
        val h=H();var calls=0;h.lateFalse=true
        assertFalse(h.access.publish("a",ActionStatus.PREPARATION,1){calls++})
        assertEquals(1,calls) // actual central Session must remain atomic; no false undo assertion.
    }
    @Test fun leadershipCiphertextAndSingleTaskMetadataAreIncludedBeforePayloadReads()=runBlocking {
        SqliteHostConnection().use { db->
            db.execute("INSERT INTO actions VALUES('a','Synthetic','Description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
            db.execute("INSERT INTO leadership VALUES('a','RESERVED','synthetic',1,'Argon2id',19,32768,3,1,zeroblob(16),zeroblob(88))")
            val h=H();val baseline=FullviewPreflight(db,h.context,{true}).prepare("a",FullviewPreflight.operations)
            val joined=FullviewPreflight(db,h.context,{true}).prepare("a",FullviewPreflight.workspace(setOf(WorkspacePart.LEADERSHIP_STATE)))
            assertTrue(ActiveWorkspaceBudget.estimateUnits(joined.retained).second>ActiveWorkspaceBudget.estimateUnits(baseline.retained).second)
            assertEquals(0,db.blobReads);assertEquals(2,db.textReads) // fixed PRAGMA only.
            db.execute("INSERT INTO tasks(id,actionId,title,priority,status,createdAt,updatedAt) VALUES('t','a','Synthetic','NORMAL','OPEN',0,0)")
            val one=FullviewPreflight(db,h.context,{true}).prepare("a",FullviewPreflight.task,"t")
            db.execute("WITH RECURSIVE n(x)AS(VALUES(1)UNION ALL SELECT x+1 FROM n WHERE x<1000)INSERT INTO tasks(id,actionId,title,priority,status,createdAt,updatedAt)SELECT 'extra-'||x,'a','Synthetic','NORMAL','OPEN',0,0 FROM n")
            val unchanged=FullviewPreflight(db,h.context,{true}).prepare("a",FullviewPreflight.task,"t")
            assertEquals(one,unchanged)
        }
    }
    @Test fun supplementaryBudgetRefusalIsBeforeProducerAndSingleAdmissionKeepsOldCharges() {
        val h=H(8_388_608-10000,200_000)
        val child=h.entry.supplement(WorkspacePart.TASKS)!!
        val old=h.record.state();var producers=0
        child.worker(Runnable{
            assertFalse(child.admit(FullviewPreflight.reservation(1000,10000,100000)))
            assertEquals(old,h.record.state())
            if(child.admit(ActiveCoreReservation(ActiveWorkspaceBudget.Units(objects=1),ActiveWorkspaceBudget.Units(objects=1))))producers++
            assertFalse(child.admit(ActiveCoreReservation(ActiveWorkspaceBudget.Units(objects=1),ActiveWorkspaceBudget.Units(objects=1))))
        }).run()
        assertEquals(1,producers);assertNull(h.record.beginFullview("a",1))
    }
    @Test fun boundedNestedPublicationReturnsFalseBeyondTwoAndExceptionRestoresDepth() {
        val h=H();var calls=0
        assertTrue(h.access.publish("a",ActionStatus.PREPARATION,1){
            assertTrue(h.access.publish("a",ActionStatus.PREPARATION,1){calls++
                assertFalse(h.access.publish("a",ActionStatus.PREPARATION,1){fail("Unbounded nested capture")})
            })
        })
        assertThrows(IllegalStateException::class.java){h.access.publish("a",ActionStatus.PREPARATION,1){error("Synthetic callback exception")}}
        assertTrue(h.access.publish("a",ActionStatus.PREPARATION,1){calls++});assertEquals(2,calls)
    }
    @Test fun heldDraftCopiesRequireFreshSessionButNeverReviveReadOrGrantWriterAuthority(){
        val h=H();val pending=h.mutations.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.PERSON,"a"),1)!!
        pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE))
        var copies=0;val before=h.record.state()
        assertTrue(h.access.consume(ActiveWorkspaceBudget.Units(objects=1)){copies++})
        assertEquals(1,copies);assertTrue(h.record.state().localRetained>before.localRetained)
        assertFalse(h.access.publish("a",ActionStatus.PREPARATION,1){fail("Old ticket")})
        h.allowed=false
        assertFalse(h.access.consume(ActiveWorkspaceBudget.Units(objects=1)){copies++})
        assertEquals(1,copies);assertNull(h.record.beginFullview("a",1))
    }

    @Test fun p110CapabilityKeepsCancellationIdentityButNoOpeningActionPayload(){
        val h=H()
        val enriched=h.request.copy(expectedAction=Action("a","Synthetic title","Synthetic description",1,2,0,ActionStatus.PREPARATION))
        assertTrue(h.access.reserve(OriginalWorkspaceAccess.cost))
        val scope=OriginalWorkspaceAccess(h.entry,h.source,h.context,h.ticket,h.mutations,enriched,Thread.currentThread())
        assertNull(scope.request.expectedAction)
        assertSame(enriched.ticket,scope.request.ticket);assertSame(enriched.parts,scope.request.parts)
        assertTrue(scope.publish("a",ActionStatus.PREPARATION,1){})
        enriched.ticket!!.cancel()
        assertFalse(scope.publish("a",ActionStatus.PREPARATION,1){fail("Cancelled")})
    }

}
