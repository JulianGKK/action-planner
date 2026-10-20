package org.pag.data.product

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.B2Native
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor

/** Own standalone data-test namespace and RAM-only synthetic keys; no auth/UI/device lifecycle mutation. */
class TaskStorageTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private val key=ByteArray(32).also{SecureRandom().nextBytes(it)}
    private var allowed=true;private var generation=1L;private var denyCommit=false;private var time=1000L
    private val owners=mutableListOf<SessionDbKeyOwner>()
    private val direct=Executor{it.run()}
    private fun context(scope:AccessScope=AccessScope.READY):StorageContext=object:StorageContext {
        private val captured:Long=this@TaskStorageTest.generation
        override val generation=captured;override val scope=scope
        override fun check(point:AccessPoint):Boolean {if(denyCommit && point==AccessPoint.COMMIT)allowed=false;return allowed && captured==this@TaskStorageTest.generation}
        override fun publish(result:()->Unit):Boolean {if(!check(AccessPoint.PUBLICATION))return false;result();return true}
    }
    private fun fixture():ProductInventory {
        val root=File(app.noBackupFilesDir.canonicalFile,"p5a-test-${UUID.randomUUID()}");check(root.mkdirs());check(root.resolve("store").mkdir())
        listOf("intent","complete","public").forEach{root.resolve(it).writeBytes(byteArrayOf(1))};root.resolve("envelope").writeBytes(ByteArray(256))
        val i=ProductInventory(root,{true},{true})
        ActionDatabaseAccess(app).use(i.database,key,context(AccessScope.MAINTENANCE),true){d->d.withWriteTransaction {
            listOf("a","b").forEach{id->d.actions().insert(ActionRow(id,"synthetic","synthetic",1,2,0,"PREPARATION"))}
            listOf("Motte","Meise","Supporter").forEach{id->d.persons().insert(PersonRow(id,"a",id,id.lowercase(),0))}
            d.persons().insert(PersonRow("other","b","synthetic other","synthetic other",0))
        }}
        return i
    }
    private fun <T> use(i:ProductInventory,work:suspend(ActionDatabase)->T)=ActionDatabaseAccess(app).use(i.database,key,context(),work=work)
    private fun <T> write(i:ProductInventory,work:suspend(TaskStore)->OperationsResult<T>):OperationsResult<T> {
        val c=context();return ActionDatabaseAccess(app).use(i.database,key,c){d->d.withWriteTransaction{val result=work(TaskStore(d,c){++time});requireOperations(c,AccessPoint.COMMIT);result}}
    }
    private fun <T> value(result:OperationsResult<T>):T {assertTrue(result is OperationsResult.Value);return (result as OperationsResult.Value).value}
    private fun rejected(result:OperationsResult<*>,reason:OperationsFailure){assertEquals(OperationsResult.Rejected(reason),result)}
    private fun create(i:ProductInventory,input:TaskInput=TaskInput(" EA contacter "),a:String="a")=value(write(i){it.create(a,input)})
    private fun board(i:ProductInventory,a:String="a")=value(use(i){d->d.withReadTransaction{TaskStore(d,context()){++time}.board(a)}})
    private fun role(i:ProductInventory,a:String="a")=use(i){d->d.withWriteTransaction{value(OperationsStore(d,context()){++time}.role(a,null,"Gesa-Support"))}}
    private fun advance(i:ProductInventory,from:ActionStatus=ActionStatus.PREPARATION)=use(i){it.advanceAction("a",from,context())}
    private fun repo(i:ProductInventory,main:Executor=direct):TaskRepository {
        val c=context();val owner=SessionDbKeyOwner.bind(c,key);owners.add(owner)
        return ProductTaskRepository(app,object:StorageAccess{override fun acquire(scope:AccessScope)=c.takeIf{it.check(AccessPoint.OPERATION)}},i,{owner},direct,main,{fail("P5A-UNEXPECTED-STORAGE-BLOCK")}){++time}
    }
    @After fun wipe(){owners.forEach{it.invalidate()};key.fill(0)}
    /** Prepared offline only in P11 #35; no device execution implied. Exercises the real
     * ProductTaskRepository -> Room/B2 transaction -> DB/key cleanup -> evidence callback.
     */
    @Test fun repositoryWriterEvidenceIsAfterCleanupAndNeverInventedByTaskCallback() {
        val i=fixture();val c=context();val owner=SessionDbKeyOwner.bind(c,key);owners.add(owner)
        val pending=mutableListOf<Runnable>();val completions=mutableListOf<WorkspaceWriteCompletion>()
        val uncached=UncachedWorkspaceMutations()
        val sink=object:WorkspaceMutationInvalidation {
            override fun beforeTaskQueue(actionId:String,generation:Long):WorkspaceMutationInvalidation.Pending? {
                val ticket=uncached.beforeTaskQueue(actionId,generation)?:return null
                return object:WorkspaceMutationInvalidation.Pending {
                    override fun finish(completion:WorkspaceWriteCompletion):Boolean {completions.add(completion);return ticket.finish(completion)}
                    override fun isCurrent()=ticket.isCurrent()
                }
            }
        }
        val r=ProductTaskRepository(app,object:StorageAccess {override fun acquire(scope:AccessScope)=c.takeIf{it.check(AccessPoint.OPERATION)}},
            i,{owner},direct,Executor{pending.add(it)},{fail("P11-WRITER-UNEXPECTED-BLOCK")},sink){++time}
        var task:Task?=null
        r.create("a",TaskInput("Synthetic writer proof")){task=value(it)}
        assertNull(task);assertTrue(completions.isEmpty());assertEquals(1L,uncached.mutationEpoch)
        assertEquals(1,board(i).events.size) // committed in SQL, callback still queued
        val callback=pending.removeAt(0);callback.run();callback.run()
        assertNotNull(task);assertEquals(listOf(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)),completions)
        r.create("a",TaskInput("")){rejected(it,OperationsFailure.INVALID)}
        pending.removeAt(0).run()
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),completions.last())
        r.transition("a",task!!,TaskStatus.DONE){task=value(it)};pending.removeAt(0).run()
        assertEquals(2,board(i).events.size);assertEquals(TaskStatus.DONE,task!!.status)
        r.transition("a",task!!,TaskStatus.DONE){rejected(it,OperationsFailure.INVALID)};pending.removeAt(0).run()
        assertEquals(2,board(i).events.size);assertFalse(uncached.ramReuseEnabled)
    }
    @Test fun integrationReopenAndSnapshotStaySeparate(){
        val i=fixture();val r=role(i);advance(i)
        use(i){d->d.withWriteTransaction{val old=d.operations().state("a","Motte")!!.model();value(OperationsStore(d,context()){++time}.snapshot("a","Motte",old,OperationalStatus.IN_CUSTODY,null,"Gesa 1"))}}
        val before=use(i){it.operations().state("a","Motte")}
        var t=create(i,TaskInput(" EA kontaktieren ",TaskPriority.HIGH,assigneeRoleId=r.id,affectedPersonId="Motte",sourceTemplateKey="CONTACT_EA"))
        assertEquals("EA kontaktieren",t.title);assertEquals(1,board(i).affectedCount("Motte"));assertEquals(TaskStatus.OPEN,t.status)
        t=value(write(i){it.transition("a",t,TaskStatus.IN_PROGRESS)})
        t=value(write(i){it.transition("a",t,TaskStatus.DONE)});assertNotNull(t.completedAt);assertEquals(0,board(i).affectedCount("Motte"))
        t=value(write(i){it.transition("a",t,TaskStatus.OPEN)});assertNull(t.completedAt)
        assertEquals(listOf(TaskEventType.CREATED,TaskEventType.STARTED,TaskEventType.COMPLETED,TaskEventType.REOPENED),board(i).events.map{it.type})
        use(i){assertEquals(before,it.operations().state("a","Motte"));assertEquals(3,it.operations().states("a").size)}
    }
    @Test fun editsAreSnapshotsAndStaleRetriesCannotDuplicateEvents(){
        val i=fixture();val t=create(i);val r=role(i)
        val edited=value(write(i){it.edit("a",t,TaskInput("Changed",TaskPriority.LOW,assigneeRoleId=r.id,affectedPersonId="Meise",dueAt=5000))})
        assertEquals(1,board(i).events.size)
        val done=value(write(i){it.transition("a",edited,TaskStatus.DONE)})
        rejected(write(i){it.transition("a",edited,TaskStatus.DONE)},OperationsFailure.STALE)
        rejected(write(i){it.transition("a",done,TaskStatus.DONE)},OperationsFailure.INVALID)
        assertEquals(2,board(i).events.size)
    }
    @Test fun cancellationCanReopen(){val i=fixture();var t=create(i);t=value(write(i){it.transition("a",t,TaskStatus.CANCELLED)});assertNull(t.completedAt);t=value(write(i){it.transition("a",t,TaskStatus.OPEN)});assertEquals(TaskStatus.OPEN,t.status);assertEquals(listOf(TaskEventType.CREATED,TaskEventType.CANCELLED,TaskEventType.REOPENED),board(i).events.map{it.type})}
    @Test fun statusPoliciesUsePersistedAction(){
        for(s in ActionStatus.entries){
            val i=fixture();val t=create(i)
            use(i){d->d.withWriteTransaction{d.actions().update(d.actions().get("a")!!.copy(status=s.name,completedAt=42L.takeIf{s==ActionStatus.COMPLETED}))}}
            val read=use(i){TaskStore(it,context()){++time}.board("a")}
            if(TaskRules.mayRead(s))assertTrue(read is OperationsResult.Value)else rejected(read,OperationsFailure.NOT_ALLOWED)
            val creation=write(i){it.create("a",TaskInput("x"))};val edit=write(i){it.edit("a",t,t.input().copy(title="edited"))}
            if(TaskRules.mayWrite(s)){assertTrue(creation is OperationsResult.Value);assertTrue(edit is OperationsResult.Value)}else{rejected(creation,OperationsFailure.NOT_ALLOWED);rejected(edit,OperationsFailure.NOT_ALLOWED)}
            val current=use(i){it.tasks().get("a",t.id)!!.model()}
            val change=write(i){it.transition("a",current,TaskStatus.DONE)}
            if(TaskRules.mayWrite(s))assertTrue(change is OperationsResult.Value)else rejected(change,OperationsFailure.NOT_ALLOWED)
            val latest=use(i){it.tasks().get("a",t.id)!!.model()}
            val deletion=write(i){it.delete("a",latest)}
            if(TaskRules.mayDelete(s)){assertTrue(deletion is OperationsResult.Value);use(i){assertTrue(it.tasks().events("a").none{e->e.taskId==t.id})}}else rejected(deletion,OperationsFailure.NOT_ALLOWED)
        }
    }
    @Test fun frozenEditorExpectedRejectsNewerTaskAndTransitionWithoutExtraEvents(){
        val i=fixture();val r=repo(i);val a=create(i)
        var b:Task?=null;r.edit("a",a,a.input().copy(title="New writer")){b=value(it)}
        r.edit("a",a,a.input().copy(title="Old form")){rejected(it,OperationsFailure.STALE)}
        assertEquals(b,board(i).tasks.single());assertEquals(1,board(i).events.size)
        var c:Task?=null;r.transition("a",b!!,TaskStatus.IN_PROGRESS){c=value(it)}
        r.edit("a",b!!,b!!.input().copy(title="Old edit after transition")){rejected(it,OperationsFailure.STALE)}
        r.transition("a",b!!,TaskStatus.DONE){rejected(it,OperationsFailure.STALE)}
        assertEquals(c,board(i).tasks.single());assertEquals(2,board(i).events.size)
    }
    @Test fun crossActionAndExclusiveAssignment(){
        val i=fixture();val r=role(i,"b");val t=create(i)
        for(input in listOf(TaskInput("x",assigneePersonId="other"),TaskInput("x",assigneeRoleId=r.id),TaskInput("x",affectedPersonId="other"))){
            rejected(write(i){it.create("a",input)},OperationsFailure.NOT_FOUND)
            rejected(write(i){it.edit("a",t,input)},OperationsFailure.NOT_FOUND)
        }
        rejected(write(i){it.create("a",TaskInput("x",assigneePersonId="Motte",assigneeRoleId=r.id))},OperationsFailure.INVALID)
        rejected(write(i){it.edit("b",t,t.input())},OperationsFailure.NOT_FOUND)
        rejected(write(i){it.transition("b",t,TaskStatus.DONE)},OperationsFailure.NOT_FOUND)
        assertTrue(board(i,"b").tasks.isEmpty());assertTrue(board(i,"b").events.isEmpty())
        for(row in listOf(t.copy(id="foreign-person",assigneePersonId="other"),t.copy(id="foreign-role",assigneeRoleId=r.id),t.copy(id="foreign-affected",affectedPersonId="other"))){
            var denied=false;try{use(i){d->d.withWriteTransaction{d.tasks().insert(row.row())}}}catch(_:Exception){denied=true};assertTrue(denied)
        }
        var denied=false;try{use(i){d->d.withWriteTransaction{d.tasks().event(TaskEventRow("bad","b",t.id,"CREATED",1))}}}catch(_:Exception){denied=true};assertTrue(denied)
    }
    @Test fun roleDeleteInUseThenUnassigned(){
        val i=fixture();val r=role(i);val t=create(i,TaskInput("x",assigneeRoleId=r.id))
        use(i){d->d.withWriteTransaction{rejected(OperationsStore(d,context()){++time}.deleteRole("a",r),OperationsFailure.IN_USE)}}
        assertEquals(r.id,board(i).tasks.single().assigneeRoleId)
        value(write(i){it.edit("a",t,t.input().copy(assigneeRoleId=null))})
        use(i){d->d.withWriteTransaction{value(OperationsStore(d,context()){++time}.deleteRole("a",r))}}
    }
    @Test fun templateKeysAreNotGloballyUniqueAndNothingAutomatic(){
        val i=fixture();assertTrue(board(i).tasks.isEmpty())
        val template=TaskRules.templates.first()
        val a=create(i,template.input().copy(affectedPersonId="Motte"));val b=create(i,template.input().copy(affectedPersonId="Meise"))
        assertEquals(template.key,a.sourceTemplateKey);assertEquals(a.sourceTemplateKey,b.sourceTemplateKey);assertNotEquals(a.id,b.id)
    }
    @Test fun commitRevokeRollsBackCreationAndTransitionEvents(){
        val i=fixture();denyCommit=true
        try{create(i);fail("P5A-CREATE-MUST-ROLLBACK")}catch(_:StorageAccessRevoked){}
        denyCommit=false;allowed=true;generation++;assertTrue(board(i).tasks.isEmpty());assertTrue(board(i).events.isEmpty())
        val t=create(i);denyCommit=true
        try{write(i){it.transition("a",t,TaskStatus.DONE)};fail("P5A-TRANSITION-MUST-ROLLBACK")}catch(_:StorageAccessRevoked){}
        denyCommit=false;allowed=true;generation++;assertEquals(t,board(i).tasks.single());assertEquals(1,board(i).events.size)
    }
    @Test fun dbRejectsBothAssignmentsOnInsertAndUpdate(){
        val i=fixture();val r=role(i);val t=create(i)
        val bad=t.copy(assigneePersonId="Motte",assigneeRoleId=r.id)
        for(insert in listOf(true,false)){
            var denied=false
            try{use(i){d->d.withWriteTransaction{if(insert)d.tasks().insert(bad.copy(id="both").row())else d.tasks().update(bad.row())}}}catch(_:Exception){denied=true}
            assertTrue(denied)
        }
        assertEquals(t,board(i).tasks.single())
    }
    @Test fun failedEventInsertionRollsBackTaskWithoutRetryDuplicates(){
        val i=fixture();val t=create(i)
        B2Native.open(i.database,key,context(AccessScope.MAINTENANCE)).use{it.execSQL("CREATE TRIGGER p5a_test_event_failure BEFORE INSERT ON task_events BEGIN SELECT RAISE(ABORT, 'SYNTHETIC_EVENT_FAILURE'); END")}
        var denied=false
        try{write(i){it.transition("a",t,TaskStatus.DONE)}}catch(_:Exception){denied=true};assertTrue(denied)
        denied=false;try{create(i)}catch(_:Exception){denied=true};assertTrue(denied)
        assertEquals(t,board(i).tasks.single());assertEquals(1,board(i).events.size)
        B2Native.open(i.database,key,context(AccessScope.MAINTENANCE)).use{it.execSQL("DROP TRIGGER p5a_test_event_failure")}
        value(write(i){it.transition("a",t,TaskStatus.DONE)})
        assertEquals(2,board(i).events.size)
    }
    @Test fun generationRevokeSuppressesPublication(){
        val i=fixture();val queued=mutableListOf<Runnable>();val r=repo(i,Executor{queued.add(it)});var published=0
        r.create("a",TaskInput("x")){published++};generation++;queued.forEach{it.run()};assertEquals(0,published)
        assertEquals(1,board(i).tasks.size);assertEquals(1,board(i).events.size)
        allowed=false;r.create("a",TaskInput("must not create")){published++};assertEquals(0,published)
    }
    @Test fun migrationFiveToSixPreservesAllExistingRowsAndCiphertexts(){
        val i=fixture();val r=role(i);advance(i)
        val leadership=LeadershipRow("a","READY","synthetic-epoch",1,"Argon2id",19,65536,3,1,ByteArray(16){2},ByteArray(88){3})
        val area=SensitiveRow("a","Motte","IDENTITY",1,1,ByteArray(60){4},ByteArray(40){5})
        use(i){d->d.withWriteTransaction{d.leadership().insert(leadership);d.leadership().insertArea(area)}}
        val before=use(i){it.operations().states("a")}
        B2Native.open(i.database,key,context(AccessScope.MAINTENANCE)).use{d->d.beginTransaction();try{
            removeMaterialSchemaForLegacyFixture(d)
            listOf("protocol_official_identifiers","protocol_affected_persons","protocol_reported_by_persons","protocol_revisions","protocol_entries").forEach{d.execSQL("DROP TABLE $it")}
            d.execSQL("DROP TABLE task_events");d.execSQL("DROP TABLE tasks")
            d.execSQL("UPDATE room_master_table SET identity_hash='${schemaFiveHash}' WHERE id=42");d.version=5;d.setTransactionSuccessful()
        }finally{d.endTransaction()}}
        assertTrue(board(i).tasks.isEmpty());assertTrue(board(i).events.isEmpty())
        use(i){d->assertEquals(before,d.operations().states("a"));assertEquals(r,d.operations().role("a",r.id)!!.model());assertEquals("Motte",d.persons().get("a","Motte")!!.pseudonym);assertTrue(leadership.envelope.contentEquals(d.leadership().get("a")!!.envelope));assertTrue(area.payload.contentEquals(d.leadership().area("a","Motte","IDENTITY")!!.payload));assertTrue(area.keyEnvelope.contentEquals(d.leadership().area("a","Motte","IDENTITY")!!.keyEnvelope))}
        B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{assertEquals(11,it.version)}
    }
    companion object { private const val schemaFiveHash="7399e9b523fe04f70ede81805a3ceffd" }
}
