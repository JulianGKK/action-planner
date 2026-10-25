package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P109WorkspaceConsistencyTest {
    private val person=Person("p","a","Synthetic",0,0)
    private val plan=PersonRolePlan(emptySet(),null,0)
    private val task=Task("t","a","Synthetic task",TaskPriority.NORMAL,TaskStatus.OPEN,null,null,null,null,null,0,0,null,0)
    private fun baseline()=WorkspaceRead("a",ActionStatus.PREPARATION,1,emptySet(),
        OperationalBoard("a",ActionStatus.PREPARATION,emptyList(),listOf(OperationalPerson(person,plan,null))),
        TaskBoard("a",listOf(task),emptyList(),false))
    private fun request(k:WorkspaceDetailKind,id:String)=WorkspaceDetailRequest("a",ActionStatus.PREPARATION,1,k,id,WorkspaceReadTicket())
    private suspend fun SqliteHostConnection.seed(){
        execute("INSERT INTO actions VALUES('a','Synthetic','Description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
        execute("INSERT INTO persons VALUES('p','a','Synthetic','synthetic',0,0)")
        execute("INSERT INTO person_role_plans VALUES('a','p',NULL,0)")
        execute("INSERT INTO tasks(id,actionId,title,priority,status,createdAt,updatedAt) VALUES('t','a','Synthetic task','NORMAL','OPEN',0,0)")
    }
    @Test fun targetedPersonCanChangeButUnrelatedTaskMustRemainExactlyEqual()=runBlocking {
        SqliteHostConnection().use{db->db.seed();val held=baseline();val checker=WorkspaceConsistency(db){true}
            assertTrue(checker.matches(held,request(WorkspaceDetailKind.PERSON,"p")))
            db.execute("UPDATE persons SET pseudonym='Synthetic new',editRevision=1 WHERE id='p'")
            assertTrue(checker.matches(held,request(WorkspaceDetailKind.PERSON,"p")))
            db.execute("UPDATE tasks SET title='Concurrent unrelated' WHERE id='t'")
            assertFalse(checker.matches(held,request(WorkspaceDetailKind.PERSON,"p")))
            assertEquals(0,db.textReads);assertEquals(0,db.blobReads)
        }
    }
    @Test fun unplannedMembershipDeltaCannotBePublishedAsACompleteUpdatedBoard()=runBlocking {
        SqliteHostConnection().use{db->db.seed()
            db.execute("INSERT INTO tasks(id,actionId,title,priority,status,createdAt,updatedAt) VALUES('new','a','Synthetic new','NORMAL','OPEN',0,0)")
            assertFalse(WorkspaceConsistency(db){true}.matches(baseline(),request(WorkspaceDetailKind.TASK,"new")))
            assertEquals(0,db.textReads)
        }
    }
    @Test fun insertedUnrelatedRowAndUnchangedRevisionWithDifferentValueRefuse()=runBlocking {
        SqliteHostConnection().use{db->db.seed();val b=baseline();val r=request(WorkspaceDetailKind.TASK,"t")
            assertTrue(WorkspaceConsistency(db){true}.matches(b,r))
            db.execute("UPDATE persons SET pseudonym='Different without revision' WHERE id='p'")
            assertFalse(WorkspaceConsistency(db){true}.matches(b,r))
            db.execute("UPDATE persons SET pseudonym='Synthetic' WHERE id='p'")
            db.execute("INSERT INTO persons VALUES('extra','a','Extra','extra',0,0)")
            assertFalse(WorkspaceConsistency(db){true}.matches(b,r))
        }
    }
    @Test fun sameTransactionMetadataFiltersOnlyOneTargetPersonOrPlan()=runBlocking {
        SqliteHostConnection().use{db->db.seed()
            val access=object:StorageContext{override val scope=AccessScope.READY;override val generation=1L;override fun check(point:AccessPoint)=true;override fun publish(result:()->Unit):Boolean{result();return true}}
            val r=request(WorkspaceDetailKind.PERSON,"p")
            val first=FullviewPreflight(db,access){true}.prepare("a",setOf("actions","persons"),detail=r)
            db.execute("INSERT INTO persons VALUES('extra','a','Extra','extra',0,0)")
            assertEquals(first,FullviewPreflight(db,access){true}.prepare("a",setOf("actions","persons"),detail=r))
            assertEquals(0,db.blobReads)
        }
    }
    @Test fun revocationStopsScalarValidationWithoutDtoReads()=runBlocking {
        SqliteHostConnection().use{db->db.seed();var live=true
            val b=baseline();live=false
            try{WorkspaceConsistency(db){live}.matches(b,request(WorkspaceDetailKind.TASK,"t"));fail("revoked")}
            catch(_:org.pag.data.prototype.b2.StorageAccessRevoked){}
            assertEquals(0,db.textReads)
        }
    }
    @Test fun validationAndReplacementScratchIsAdditionalAndNeverRetiresHeldGraph(){
        val memory=OutstandingCoreRead();val entry=memory.beginFullview("a",1)!!
        assertTrue(memory.enterFullview(entry,"a",1))
        val cost=ActiveCoreReservation(ActiveWorkspaceBudget.Units(objects=1),ActiveWorkspaceBudget.Units(objects=1))
        assertTrue(entry.admit(cost));entry.worker(Runnable{assertTrue(entry.materialize())}).run()
        val before=memory.state();val units=WorkspaceConsistency.cost(baseline())
        assertTrue(entry.reserve(units));val after=memory.state()
        val n=ActiveWorkspaceBudget.estimateUnits(units).second
        assertEquals(before.localRetained+n,after.localRetained);assertEquals(before.localTransition+n,after.localTransition)
        assertNull(memory.beginFullview("a",1));assertFalse(after.retirementConfirmed)
    }
    @Test fun p110ExplicitMembershipAdmitsOnlyOneKnownDeltaAndStillRejectsUnrelatedChange()=runBlocking {
        SqliteHostConnection().use{db->db.seed();val held=baseline();val checker=WorkspaceConsistency(db){true}
            db.execute("INSERT INTO tasks(id,actionId,title,priority,status,createdAt,updatedAt) VALUES('new','a','Synthetic new','NORMAL','OPEN',0,0)")
            val create=request(WorkspaceDetailKind.TASK,"new").copy(membership=true)
            assertTrue(checker.matches(held,create))
            assertFalse(checker.matches(held,create.copy(membership=false)))
            db.execute("UPDATE persons SET pseudonym='Other' WHERE id='p'")
            assertFalse(checker.matches(held,create))
            assertEquals(0,db.textReads);assertEquals(0,db.blobReads)
        }
    }
    @Test fun p110ExplicitPersonMembershipIncludesItsPlanAndStateAndNoUnrelatedMember()=runBlocking {
        SqliteHostConnection().use{db->db.seed();val held=baseline();val checker=WorkspaceConsistency(db){true}
            db.execute("INSERT INTO persons VALUES('new','a','New','new',1,0)")
            db.execute("INSERT INTO person_role_plans VALUES('a','new',NULL,0)")
            assertTrue(checker.matches(held,request(WorkspaceDetailKind.PERSON,"new").copy(membership=true)))
            db.execute("DELETE FROM persons WHERE id='new'")
            db.execute("DELETE FROM tasks WHERE id='t'")
            assertTrue(checker.matches(held,request(WorkspaceDetailKind.TASK,"t").copy(membership=true)))
            assertEquals(0,db.textReads)
        }
    }

    @Test fun p110OnlyActualBoardConstructionAddsBoardPresentationReceipt()=runBlocking {
        SqliteHostConnection().use{db->db.seed()
            val access=object:StorageContext{override val scope=AccessScope.READY;override val generation=1L;override fun check(point:AccessPoint)=true;override fun publish(result:()->Unit):Boolean{result();return true}}
            val metadata=FullviewPreflight(db,access){true}
            val scalarTask=metadata.prepare("a",FullviewPreflight.task,presentations=false)
            val board=metadata.prepare("a",FullviewPreflight.task)
            assertEquals(CoreReadAdmission.add(scalarTask.retained,TaskPresentation.cost(1,filters=0)),board.retained)
            assertEquals(CoreReadAdmission.add(scalarTask.transition,TaskPresentation.cost(1,filters=0)),board.transition)
            db.execute("UPDATE tasks SET affectedPersonId='p' WHERE id='t'")
            val scalarFiltered=metadata.prepare("a",FullviewPreflight.task,presentations=false)
            val filtered=metadata.prepare("a",FullviewPreflight.task)
            assertEquals(CoreReadAdmission.add(scalarFiltered.retained,TaskPresentation.cost(1,filters=1)),filtered.retained)
            assertEquals(0,db.blobReads)
        }
    }

}
