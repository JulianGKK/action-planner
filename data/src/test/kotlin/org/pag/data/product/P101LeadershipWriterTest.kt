package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.util.concurrent.Executor

/** Real common boundary/progress/outer-transaction helper + SQLite/schema11. No Android
 * Keystore claim: external steps/faults and DB/key cleanup markers here are test controls. */
class P101LeadershipWriterTest {
    private fun begin(h:CoreWriterIntegrationTest.H,publish:(SensitiveResult<Unit>)->Unit={},
        work:(StorageContext,LeadershipWriteProgress)->Unit) {
        h.boundary.submit<SensitiveResult<Unit>>(WorkspaceMutation(WorkspaceWriterFamily.LEADERSHIP,"a"),publish,
            {SensitiveResult.Rejected(SensitiveFailure.DENIED)},{it is SensitiveResult.Value}){c,p->
            val e=LeadershipWriteProgress(p)
            try{work(c,e);e.complete();SensitiveResult.Value(Unit)}
            catch(problem:SensitiveProblem){e.rejectedWithoutEffect();SensitiveResult.Rejected(problem.reason)}
        }
    }
    private fun scope(h:CoreWriterIntegrationTest.H,e:LeadershipWriteProgress,body:suspend()->Unit) {
        e.opening();h.handle=true;h.lease=true
        try{runBlocking{body()}}
        finally {
            h.handle=false;h.dbCleanup();h.lease=false;h.keyCleanup()
            assertFalse(h.active);assertEquals(0,h.db.openStatements);e.closed(true)
        }
    }
    private suspend fun reserve(h:CoreWriterIntegrationTest.H,c:StorageContext,e:LeadershipWriteProgress)=
        e.transaction(h.tx(c),c){h.db.execute("INSERT INTO leadership VALUES('a','RESERVED','synthetic',1,'Argon2id',19,65536,3,1,X'00',X'00')")}
    @Test fun beginRevokesOriginalReadBeforeQueueAndTwoCommitsPlusExternalStepPublishOnce()=CoreWriterIntegrationTest.H().use{h->
        val read=h.epoch.beforeActiveRead("a",1)!!;var patches=0;var external=0
        h.worker.onQueue={assertFalse(read.isLive());assertNull(h.epoch.beforeActiveRead("a",1))}
        begin(h,{assertTrue(it is SensitiveResult.Value);patches++}){c,e->
            scope(h,e){reserve(h,c,e)}
            assertEquals(1,e.committedPhases);e.external{external++}
            scope(h,e){e.transaction(h.tx(c),c){h.db.execute("UPDATE leadership SET state='READY'")}}
            assertEquals(2,e.committedPhases)
        }
        val worker=h.worker.jobs.removeFirst();worker.run();worker.run()
        assertEquals(0,patches);assertEquals(1,h.count("leadership"));assertEquals(1,external)
        val main=h.main.jobs.removeFirst();main.run();main.run();assertEquals(1,patches)
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),h.completions.single())
    }
    @Test fun partialExternalEffectPreservesKnownCommitButWholeAttemptStaysUnknownAndNoRetry()=CoreWriterIntegrationTest.H().use{h->
        var patches=0;var external=0
        begin(h,{patches++}){c,e->scope(h,e){reserve(h,c,e)};e.external{external++;error("SYNTHETIC_KEY_STEP_AFTER_EFFECT")}}
        val worker=h.worker.jobs.removeFirst();worker.run();worker.run();h.main.drain()
        assertEquals(1,h.count("leadership"));assertEquals(1,external);assertEquals(0,patches)
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
        assertEquals(1,h.blocks);assertNull(h.epoch.beforeTaskQueue("a",1));assertNull(h.epoch.beforeActiveRead("a",1))
    }
    @Test fun controlledRejectionReturnsOnlyAfterOuterRollbackAndNeverInsertsRow()=CoreWriterIntegrationTest.H().use{h->
        var result:SensitiveResult<Unit>?=null
        begin(h,{result=it}){c,e->scope(h,e){e.transaction(h.tx(c),c){
            h.db.execute("INSERT INTO leadership VALUES('a','RESERVED','synthetic',1,'Argon2id',19,65536,3,1,X'00',X'00')")
            throw SensitiveProblem(SensitiveFailure.CONFLICT)
        }}}
        h.worker.drain();assertNull(result);h.main.drain()
        assertEquals(0,h.count("leadership"));assertEquals(SensitiveResult.Rejected(SensitiveFailure.CONFLICT),result)
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),h.completions.single())
    }
    @Test fun laterControlledRollbackCannotRelabelEarlierCommitAsRollback()=CoreWriterIntegrationTest.H().use{h->
        begin(h){c,e->scope(h,e){reserve(h,c,e)};scope(h,e){e.transaction(h.tx(c),c){throw SensitiveProblem(SensitiveFailure.CONFLICT)}}}
        h.worker.drain();h.main.drain();assertEquals(1,h.count("leadership"))
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome);assertEquals(1,h.blocks)
    }
    @Test fun lostOuterEndReceiptIsUnknownEvenWhenRealSqliteCommitAlreadyHappened()=CoreWriterIntegrationTest.H().use{h->
        h.failEndAfter=true;begin(h){c,e->scope(h,e){reserve(h,c,e)}}
        h.worker.drain();h.main.drain();assertEquals(1,h.count("leadership"))
        assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        assertNull(h.epoch.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.PERSON,"a"),1))
    }
    @Test fun committedButCleanupFailureRetainsCommittedEvidenceAndSuppressesUi()=CoreWriterIntegrationTest.H().use{h->
        var patches=0;h.keyCleanup={error("SYNTHETIC_KEY_CLEANUP")}
        begin(h,{patches++}){c,e->scope(h,e){reserve(h,c,e)}}
        h.worker.drain();h.main.drain();assertEquals(1,h.count("leadership"));assertEquals(0,patches)
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED),h.completions.single())
    }
    @Test fun lockBeforeCommitAndCommitBeforeLockCannotPublishSaved() {
        for(before in listOf(true,false))CoreWriterIntegrationTest.H().use{h->
            var patches=0
            if(before)h.beforeEnd={h.ready=false} else h.afterEnd={h.ready=false}
            begin(h,{patches++}){c,e->scope(h,e){reserve(h,c,e)}};h.worker.drain();h.main.drain()
            assertEquals(if(before)0 else 1,h.count("leadership"));assertEquals(0,patches)
            assertEquals(if(before)WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.COMMITTED,h.completions.single().outcome)
        }
    }
    @Test fun latePatchAfterPermissionActionOrGenerationChangeOrNewMutationIsDenied() {
        for(mode in listOf("permission","action","generation","mutation"))CoreWriterIntegrationTest.H().use{h->
            var patches=0;begin(h,{patches++}){c,e->scope(h,e){reserve(h,c,e)}};h.worker.drain()
            when(mode){"permission"->h.permission++;"action"->h.action="b";"generation"->h.generation++;else->Unit}
            if(mode=="mutation") {
                val first=h.main.jobs.removeFirst();first.run();assertEquals(1,patches)
                val next=h.epoch.beforeTaskQueue("a",1)!!;first.run();assertEquals(1,patches)
                assertTrue(next.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
            }else {h.main.drain();assertEquals(0,patches)}
        }
    }
    @Test fun lostMainKeepsSameGenerationPendingButCleanWorkRebindsOnlyOnNewReadyGeneration()=CoreWriterIntegrationTest.H().use{h->
        begin(h){c,e->scope(h,e){reserve(h,c,e)}};h.worker.drain();h.main.jobs.clear()
        assertNull(h.epoch.beforeTaskQueue("a",1))
        val rebound=h.epoch.beforeTaskQueue("a",2)!! // Test-supplied fresh binding; product requires actual authorized READY.
        assertTrue(rebound.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)))
        // Separate test scope: completed UNKNOWN is terminal until new authorized generation.
        val port=UncachedWorkspaceMutations();val pending=port.beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.LEADERSHIP,"a"),1)!!
        assertTrue(pending.finish(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.COMPLETE)))
        assertNull(port.beforeActiveRead("a",1));assertNotNull(port.beforeActiveRead("a",2))
        assertFalse(port.ramReuseEnabled)
    }
    @Test fun acceptedThenThrowingWorkerQueueCannotExecuteLaterAndReleasesResourcesOnce()=CoreWriterIntegrationTest.H().use{h->
        var late:Runnable?=null;var effects=0;var releases=0
        val writer=WorkspaceWriteBoundary(h.access,Executor{late=it;throw IllegalStateException("SYNTHETIC_QUEUE_ACCEPT_THEN_THROW")},h.main,h.sink,{h.blocks++})
        writer.submit<SensitiveResult<Unit>>(WorkspaceMutation(WorkspaceWriterFamily.LEADERSHIP,"a"),{},
            {SensitiveResult.Rejected(SensitiveFailure.DENIED)},{it is SensitiveResult.Value},notStarted={releases++}){_,p->effects++;p.ended(WorkspaceWriteOutcome.COMMITTED);SensitiveResult.Value(Unit)}
        assertEquals(1,releases);late!!.run();late!!.run();assertEquals(0,effects);assertEquals(1,h.blocks)
        assertEquals(WorkspaceWriteOutcome.ROLLED_BACK,h.completions.single().outcome)
    }
}
