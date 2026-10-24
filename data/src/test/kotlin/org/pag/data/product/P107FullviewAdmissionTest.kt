package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Real production preflight SQL on SQLite; host shim is not SQLCipher/device evidence. */
class P107FullviewAdmissionTest {
    private val c=object:StorageContext {
        override val generation=1L;override val scope=AccessScope.READY
        override fun check(point:AccessPoint)=true
        override fun publish(result:()->Unit):Boolean {result();return true}
    }
    private fun bytes(n:Long)=ActiveWorkspaceBudget.Units(arrays=1,arrayPayloadBytes=n-71)
    private fun book(r:Long,t:Long):Boolean {
        val record=OutstandingCoreRead();val e=record.beginFullview("a",1)!!
        assertTrue(record.enterFullview(e,"a",1))
        return e.admit(ActiveCoreReservation(bytes(r),bytes(t)))
    }
    @Test fun exactIndependentLimitsAndOneByteOver() {
        val r=ActiveWorkspaceBudget.RETAINED_LIMIT_BYTES;val t=ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES
        assertTrue(book(r,t));assertFalse(book(r+1,t));assertFalse(book(r,t+1))
        assertTrue(book(r,t));assertFalse(book(t,t)) // transition must NEVER become retained.
    }
    @Test fun receiptMustBeOriginalStartedAndOnlyOneMaterializerMayConsumeIt() {
        val a=OutstandingCoreRead();val e=a.beginFullview("a",1)!!
        val receipt=FullviewPreflight.reservation(1,9,20)
        assertFalse(e.admit(receipt));assertFalse(e.materialize())
        assertFalse(OutstandingCoreRead().enterFullview(e,"a",1))
        assertTrue(a.enterFullview(e,"a",1));assertTrue(e.admit(receipt));assertFalse(e.admit(receipt))
        var calls=0
        val work=e.worker(Runnable{assertTrue(e.materialize());calls++;assertFalse(e.materialize());e.main(Runnable{}).run()})
        work.run();work.run();assertEquals(1,calls)
        assertNull(a.beginFullview("b",2));assertNull(a.begin());assertFalse(a.state().retirementConfirmed)
        assertEquals(ActiveWorkspaceBudget.TRANSITION_LIMIT_BYTES,a.state().transition)
        // Test deliberately retains an alias; Main/empty VM cannot release either snapshot/form.
        val retainedTestAlias=e
        retainedTestAlias.workerRejected();retainedTestAlias.mainRejected()
        assertNull(a.beginFullview("a",3))
    }
    @Test fun noPayloadBeforePreflightAndReceiptForAllReadableActionStatuses()=runBlocking {
        for(status in listOf("PREPARATION","ACTIVE","COMPLETED"))SqliteHostConnection().use { db->
            db.execute("INSERT INTO actions VALUES('a','Synthetic 長い 👩‍🔬','Description',1,2,0,'$status','SENSITIVE',${if(status=="COMPLETED")3 else "NULL"})")
            db.execute("INSERT INTO persons VALUES('p','a','Person 🧪','person 🧪',0,7)")
            db.execute("INSERT INTO person_role_plans VALUES('a','p',NULL,3)")
            val record=OutstandingCoreRead();val e=record.beginFullview("a",1)!!
            assertTrue(record.enterFullview(e,"a",1));assertFalse(e.materialize())
            db.execute("BEGIN")
            try {
                val receipt=FullviewPreflight(db,c,{true}).prepare("a",FullviewPreflight.workspace(setOf(WorkspacePart.TASKS)))
                assertEquals(1,db.textReads);assertEquals(0,db.blobReads)
                assertTrue(e.admit(receipt));assertTrue(e.materialize())
                assertTrue(e.retainedCharge>0);assertTrue(e.transitionCharge>e.retainedCharge)
            }finally {db.execute("ROLLBACK")}
            assertEquals(0,db.openStatements)
        }
    }
    @Test fun cancellationBeforeAndDuringMetadataNeverMaterializes()=runBlocking {
        for(during in listOf(false,true))SqliteHostConnection().use{db->
            var allowed=during
            db.beforePrepare={if(it.contains("MAX("))allowed=false}
            val record=OutstandingCoreRead();val e=record.beginFullview("a",1)!!;record.enterFullview(e,"a",1)
            val failure=try{FullviewPreflight(db,c,{allowed}).prepare("a",FullviewPreflight.operations);null}catch(f:BoundedActiveRejected){f}
            assertEquals(BoundedActiveStatus.ACCESS_REVOKED,failure?.status);assertFalse(e.materialize())
            assertEquals(0,db.blobReads);assertEquals(if(during)1 else 0,db.textReads);assertEquals(0,db.openStatements)
        }
    }
    @Test fun overlongMetadataAndMalformedRevisionRefuseBeforeDtoReads()=runBlocking {
        for(invalidRevision in listOf(false,true))SqliteHostConnection().use{db->
            db.execute("INSERT INTO actions VALUES('a','Synthetic','Description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
            db.execute("INSERT INTO persons VALUES('p','a','Synthetic','synthetic',0,${if(invalidRevision)-1 else 0})")
            if(!invalidRevision)db.execute("UPDATE persons SET pseudonym=printf('%0*d',8388609,0)")
            val failure=try{FullviewPreflight(db,c,{true}).prepare("a",FullviewPreflight.operations);null}catch(f:BoundedActiveRejected){f}
            assertEquals(if(invalidRevision)BoundedActiveStatus.INVALID_DATA else BoundedActiveStatus.BUDGET_REFUSED,failure?.status)
            assertEquals(0,db.blobReads);assertEquals(1,db.textReads)
        }
    }
    @Test fun overflowNegativeAndMalformedUnitsFailClosed() {
        for(values in listOf(Triple(Long.MAX_VALUE,1L,1L),Triple(1L,Long.MAX_VALUE,1L),Triple(1L,1L,Long.MAX_VALUE))) {
            val failure=try{FullviewPreflight.reservation(values.first,values.second,values.third);null}catch(f:BoundedActiveRejected){f}
            assertEquals(BoundedActiveStatus.BUDGET_REFUSED,failure?.status)
        }
        assertThrows(BoundedActiveRejected::class.java){FullviewPreflight.reservation(-1,0,0)}
        val record=OutstandingCoreRead();val e=record.beginFullview("a",1)!!;record.enterFullview(e,"a",1)
        assertFalse(e.admit(ActiveCoreReservation(ActiveWorkspaceBudget.Units(objects=-1),bytes(100))))
        assertFalse(e.materialize())
    }
    @Test fun failedPreflightAndLostMainHaveDifferentActualEndRequirements() {
        val record=OutstandingCoreRead();val e=record.beginFullview("a",1)!!
        val worker=e.worker(Runnable{record.enterFullview(e,"a",1);assertFalse(e.admit(ActiveCoreReservation(bytes(8388609),bytes(100))));e.main(Runnable{})})
        worker.run();assertNull(record.beginFullview("a",1)) // Lost Main has not returned.
        e.mainRejected();assertNotNull(record.beginFullview("a",1)) // Known never-executed callback; no graph existed.
    }
    @Test fun actualMaterializationFailureDoesNotEraseItsObligation() {
        val record=OutstandingCoreRead();val e=record.beginFullview("a",1)!!
        val worker=e.worker(Runnable{record.enterFullview(e,"a",1);assertTrue(e.admit(FullviewPreflight.reservation(1,1,10)));assertTrue(e.materialize());error("SYNTHETIC_AFTER_FIRST_DTO")})
        assertThrows(IllegalStateException::class.java){worker.run()};assertNull(record.beginFullview("a",1))
    }
    @Test fun preflightAndMaterializerKeepOneRealSqliteSnapshotAcrossConcurrentCommit()=runBlocking {
        val parent=java.io.File("build/tmp/p107-consistency").apply{mkdirs()}
        val directory=java.nio.file.Files.createTempDirectory(parent.toPath(),"synthetic-").toFile()
        val file=java.io.File(directory,"synthetic.db")
        try {
            SqliteHostConnection(sharedTestFile=file).use{first->
                first.execute("PRAGMA journal_mode=WAL")
                first.execute("INSERT INTO actions VALUES('a','Original','Description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
                SqliteHostConnection(sharedTestFile=file).use{second->
                    first.execute("BEGIN")
                    try {
                        val reservation=FullviewPreflight(first,c,{true}).prepare("a",FullviewPreflight.operations)
                        second.execute("UPDATE actions SET title=printf('%020000d',0) WHERE id='a'")
                        val record=OutstandingCoreRead();val e=record.beginFullview("a",1)!!
                        record.enterFullview(e,"a",1);assertTrue(e.admit(reservation));assertTrue(e.materialize())
                        first.usePrepared("SELECT title FROM actions WHERE id='a'"){q->assertTrue(q.step());assertEquals("Original",q.getText(0))}
                    }finally{first.execute("ROLLBACK")}
                    first.usePrepared("SELECT length(title) FROM actions WHERE id='a'"){q->assertTrue(q.step());assertEquals(20000L,q.getLong(0))}
                }
            }
        }finally{directory.walkBottomUp().forEach{check(it.delete())}}
    }

    @Test fun boundedHistoryJoinsOriginalReceiptBeforeFirstEventText()=runBlocking {
        for(deny in listOf(false,true))SqliteHostConnection().use{db->
            db.execute("INSERT INTO actions VALUES('a','Synthetic','Description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
            db.execute("INSERT INTO tasks(id,actionId,title,priority,status,createdAt,updatedAt,editRevision) VALUES('t','a','Synthetic','NORMAL','OPEN',0,0,0)")
            db.execute("WITH RECURSIVE n(x) AS(VALUES(0) UNION ALL SELECT x+1 FROM n WHERE x<63) INSERT INTO task_events SELECT 'event-'||x,'a','t',CASE WHEN x=0 THEN 'CREATED' WHEN x%2=1 THEN 'CANCELLED' ELSE 'REOPENED' END,x FROM n")
            val owner=OutstandingCoreRead();val e=owner.beginFullview("a",1)!!;owner.enterFullview(e,"a",1)
            val base=FullviewPreflight(db,c,{true}).prepare("a",FullviewPreflight.task)
            var admitted=false
            try {
                val result=CompactTaskStore(db,c,{true}).events(CompactTaskState(P3bFreeActiveCoreBinding("a",1),0),"t",null,fullTaskRead=true,admitScope={part->
                    assertEquals(0,db.blobReads)
                    if(deny)throw BoundedActiveRejected(BoundedActiveStatus.BUDGET_REFUSED)
                    assertTrue(e.admit(ActiveCoreReservation(CoreReadAdmission.add(base.retained,part.retained),CoreReadAdmission.add(base.transition,part.transition))))
                    assertTrue(e.materialize());admitted=true
                })
                assertFalse(deny);assertTrue(admitted)
                val page=result.payload as CompactTaskEvents
                assertEquals(32,page.events.size);assertEquals(64L,page.total)
                assertNull(owner.beginFullview("a",2))
            }catch(f:BoundedActiveRejected){assertTrue(deny);assertEquals(BoundedActiveStatus.BUDGET_REFUSED,f.status);assertFalse(admitted);assertEquals(0,db.blobReads)}
        }
    }

    @Test fun originalIdentityChangeAfterMetadataOrAfterBookingCannotReachMaterializer()=runBlocking {
        for(revokeOn in listOf(1,2))SqliteHostConnection().use{db->
            val owner=OutstandingCoreRead();val e=owner.beginFullview("a",1)!!;owner.enterFullview(e,"a",1)
            val reservation=FullviewPreflight(db,c,{true}).prepare("a",FullviewPreflight.operations)
            var checks=0;var materializers=0
            val task=e.worker(Runnable{
                admitCurrentFullview(e,reservation){++checks<revokeOn}
                materializers++
            })
            val failure=try{task.run();null}catch(f:BoundedActiveRejected){f}
            assertEquals(BoundedActiveStatus.ACCESS_REVOKED,failure?.status)
            assertEquals(0,materializers);assertEquals(0,db.blobReads)
            assertFalse(e.materialize());assertNotNull(owner.beginFullview("a",2))
        }
    }

    @Test fun terminalFullviewShellsCannotReachALaterReadThroughTheirEndCallback() {
        fun reaches(root:Any?,target:Any):Boolean {
            val seen=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any,Boolean>())
            fun visit(value:Any?):Boolean {
                if(value===target)return true
                if(value==null || !seen.add(value))return false
                if(value is java.util.concurrent.atomic.AtomicReference<*>)return visit(value.get())
                if(!value.javaClass.name.startsWith("org.pag.data.product."))return false
                return value.javaClass.declaredFields.filterNot{java.lang.reflect.Modifier.isStatic(it.modifiers)}.any{f->f.isAccessible=true;visit(f.get(value))}
            }
            return visit(root)
        }
        val owner=OutstandingCoreRead();val e=owner.beginFullview("a",1)!!
        var main:Runnable?=null
        e.mainRejected() // No Main registered: cannot pre-acknowledge a future callback.
        val worker=e.worker(Runnable{main=e.main(Runnable{})}) // No DTO ever materialized.
        assertTrue(reaches(worker,owner));worker.run()
        assertFalse("returned Worker still owns an end callback to the record",reaches(worker,owner))
        assertTrue(reaches(main,owner));assertNull(owner.beginFullview("a",1));main!!.run()
        val later=owner.begin()!! // Existing safe unmaterialized-scope contract; NOT exposed readmission.
        assertFalse("terminal Main can transitively reach later B",reaches(main,later))
        assertFalse(reaches(worker,later))
        val rejectedOwner=OutstandingCoreRead();val rejected=rejectedOwner.beginFullview("a",1)!!
        val neverQueued=rejected.worker(Runnable{})
        rejected.workerRejected();assertFalse(reaches(neverQueued,rejectedOwner))
    }

}
