package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Actual SQLite metadata checks. No Android/SQLCipher claim from these host cases. */
class P110FollowProjectionTest {
    private val access=object:StorageContext {
        override val scope=AccessScope.READY;override val generation=1L
        override fun check(point:AccessPoint)=true
        override fun publish(result:()->Unit):Boolean{result();return true}
    }
    private fun request(kind:WorkspaceDetailKind,id:String)=WorkspaceDetailRequest("a",ActionStatus.ACTIVE,1,kind,id,WorkspaceReadTicket(),membership=true)
    private fun baseline()=WorkspaceRead("a",ActionStatus.ACTIVE,1,emptySet(),OperationalBoard("a",ActionStatus.ACTIVE,emptyList(),emptyList()))
    private suspend fun SqliteHostConnection.seed(){execute("INSERT INTO actions VALUES('a','Synthetic','',1,2,0,'ACTIVE','SENSITIVE',NULL)")}
    private suspend fun SqliteHostConnection.memory(id:String){execute("INSERT INTO memory_reports VALUES('$id','a','Synthetic',1,'DRAFT',0,0,1,1,NULL)")}
    private suspend fun SqliteHostConnection.protocol(id:String){
        execute("INSERT INTO protocol_entries VALUES('$id','a',0)")
        execute("INSERT INTO protocol_revisions(id,actionId,entryId,revisionNumber,revisionType,classification,title,occurredAt,occurredAtApproximate,recordedAt,recordedAfterCompletion,mistaken) VALUES('r-$id','a','$id',1,'ORIGINAL','RELEVANT','Synthetic',1,0,2,0,0)")
    }
    @Test fun memoryTargetMetadataDoesNotReadOrChargeAnotherReportsPayload()=runBlocking {
        SqliteHostConnection().use{db->db.seed();db.memory("one")
            val before=FullviewPreflight(db,access){true}.prepare("a",FullviewPreflight.memory,detail=request(WorkspaceDetailKind.MEMORY,"one"),presentations=false)
            db.memory("two");db.execute("INSERT INTO memory_answers VALUES('a','two','A_FREE_NARRATIVE','TEXT','Unrelated held payload')")
            val after=FullviewPreflight(db,access){true}.prepare("a",FullviewPreflight.memory,detail=request(WorkspaceDetailKind.MEMORY,"one"),presentations=false)
            assertEquals(before,after);assertEquals(0,db.blobReads)
        }
    }
    @Test fun protocolRelationsAreSizedByTargetEntryInsideSameMetadataTransaction()=runBlocking {
        SqliteHostConnection().use{db->db.seed();db.protocol("one")
            val before=FullviewPreflight(db,access){true}.prepare("a",FullviewPreflight.protocol,detail=request(WorkspaceDetailKind.PROTOCOL,"one"),presentations=false)
            db.protocol("two");db.execute("INSERT INTO protocol_official_identifiers VALUES('a','r-two',0,'OTHER_OFFICIAL_ID','Synthetic unrelated')")
            assertEquals(before,FullviewPreflight(db,access){true}.prepare("a",FullviewPreflight.protocol,detail=request(WorkspaceDetailKind.PROTOCOL,"one"),presentations=false))
            db.execute("INSERT INTO protocol_official_identifiers VALUES('a','r-one',0,'OTHER_OFFICIAL_ID','Synthetic target')")
            val after=FullviewPreflight(db,access){true}.prepare("a",FullviewPreflight.protocol,detail=request(WorkspaceDetailKind.PROTOCOL,"one"),presentations=false)
            assertTrue(ActiveWorkspaceBudget.estimateUnits(after.retained).second>ActiveWorkspaceBudget.estimateUnits(before.retained).second)
            assertEquals(0,db.blobReads)
        }
    }
    @Test fun memoryNeighborHeaderChangeRefusesBeforePayloadRead()=runBlocking {
        SqliteHostConnection().use{db->db.seed();db.memory("one")
            val h=MemoryReportHeader("one","a","Synthetic",1,MemoryReportStatus.DRAFT,0,0,1,1,null)
            val b=baseline().copy(memoryHeaders=listOf(h));val r=request(WorkspaceDetailKind.MEMORY,"new")
            assertTrue(WorkspaceConsistency(db){true}.matches(b,r))
            db.execute("UPDATE memory_reports SET authorLabel='Concurrent' WHERE id='one'")
            assertFalse(WorkspaceConsistency(db){true}.matches(b,r));assertEquals(0,db.textReads);assertEquals(0,db.blobReads)
        }
    }
    @Test fun protocolNeighborPayloadAndLatestRevisionMustMatchHeldTimeline()=runBlocking {
        SqliteHostConnection().use{db->db.seed();db.protocol("one")
            val v=ProtocolTimelineItem("one","r-one",1,ProtocolRevisionType.ORIGINAL,ProtocolClassification.RELEVANT,null,"Synthetic",1,false,2,false,false,false,2)
            val b=baseline().copy(protocolTimeline=listOf(v));val r=request(WorkspaceDetailKind.PROTOCOL,"new")
            assertTrue(WorkspaceConsistency(db){true}.matches(b,r))
            db.execute("UPDATE protocol_revisions SET title='Concurrent' WHERE id='r-one'")
            assertFalse(WorkspaceConsistency(db){true}.matches(b,r));assertEquals(0,db.textReads);assertEquals(0,db.blobReads)
        }
    }
    @Test fun explicitHeaderRefreshSizesAllNewMembersWithoutMaterializingAnswerPayload()=runBlocking {
        SqliteHostConnection().use{db->db.seed();db.memory("one")
            val r=request(WorkspaceDetailKind.MEMORY_HEADERS,"a")
            val names=setOf("actions","memory_reports")
            val before=FullviewPreflight(db,access){true}.prepare("a",names,detail=r,presentations=false)
            val b=baseline().copy(memoryHeaders=emptyList())
            assertTrue(WorkspaceConsistency(db){true}.matches(b,r))
            db.memory("two")
            db.execute("INSERT INTO memory_answers VALUES('a','two','A_FREE_NARRATIVE','TEXT','Synthetic payload remains excluded')")
            val after=FullviewPreflight(db,access){true}.prepare("a",names,detail=r,presentations=false)
            assertTrue(ActiveWorkspaceBudget.estimateUnits(after.retained).second>ActiveWorkspaceBudget.estimateUnits(before.retained).second)
            assertTrue(WorkspaceConsistency(db){true}.matches(b,r))
            db.execute("INSERT INTO persons VALUES('p','a','Synthetic unrelated','synthetic unrelated',0,0)")
            assertFalse(WorkspaceConsistency(db){true}.matches(b,r))
            assertEquals(2,db.textReads) // Two PRAGMA encoding reads; no payload getText.
            assertEquals(0,db.blobReads)
        }
    }

}
