package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class P104ConcurrentCasTest {
    @Test fun twoRealConnectionsRaceAllFiveOriginalRevisionsExactlyOneWins(){
        val directory=File("build/p104-cas").apply{mkdirs()}
        for(kind in EDIT_REVISION_TABLES){
            val file=File.createTempFile("synthetic-",".sqlite",directory)
            try{SqliteHostConnection(sharedTestFile=file).use{first->
                runBlocking{
                    first.execute("INSERT INTO actions VALUES('a','Synthetic action','Synthetic description',1,2,0,'ACTIVE','SENSITIVE',NULL)")
                    first.execute("INSERT INTO persons VALUES('p','a','Synthetic','synthetic',1,0)")
                    first.execute("INSERT INTO roles VALUES('r','a','Role','role',1,0)")
                    first.execute("INSERT INTO person_role_plans VALUES('a','p',NULL,0)")
                    first.execute("INSERT INTO person_operational_states VALUES('a','p',NULL,'NO_STATUS',NULL,42,0)")
                    first.execute("INSERT INTO tasks VALUES('t','a','Task','NORMAL','OPEN',NULL,NULL,NULL,NULL,NULL,1,42,NULL,0)")
                }
                SqliteHostConnection(sharedTestFile=file).use{second->
                    // Both connections genuinely read the same persisted original revision.
                    for(db in listOf(first,second))runBlocking{db.usePrepared("SELECT editRevision FROM $kind"){assertTrue(it.step());assertEquals(0L,it.getLong(0))}}
                    val ready=CountDownLatch(2);val start=CountDownLatch(1);val executor=Executors.newFixedThreadPool(2)
                    try{
                        val jobs=listOf(first,second).map{db->executor.submit<Int>{
                            ready.countDown();check(start.await(5,TimeUnit.SECONDS))
                            runBlocking{
                                db.execute("BEGIN IMMEDIATE")
                                try{
                                    val core=HostCoreWriterData(db)
                                    val changed=when(kind){
                                        "persons"->core.persons.rename("a","p","Changed","changed",0)
                                        "roles"->core.operations.renameRole("a","r",0,"Changed","changed")
                                        "person_role_plans"->core.operations.claimPlan("a","p",0)
                                        "person_operational_states"->core.operations.changeState("a","p",0,null,"LEFT",null,42)
                                        "tasks"->HostTaskData(db).tasks.change("a","t",0,"Changed","NORMAL","OPEN",null,null,null,null,42,null)
                                        else->error("kind")
                                    }
                                    db.execute("COMMIT");changed
                                }catch(t:Throwable){db.execute("ROLLBACK");throw t}
                            }
                        }}
                        assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown()
                        assertEquals(kind,listOf(0,1),jobs.map{it.get(15,TimeUnit.SECONDS)}.sorted())
                        runBlocking{first.usePrepared("SELECT editRevision FROM $kind"){assertTrue(it.step());assertEquals(1L,it.getLong(0))}}
                    }finally{executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS)}
                }
            }}finally{check(file.delete());File(file.path+"-journal").delete()}
        }
    }
}
