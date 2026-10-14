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
class OperationsStorageTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private val key=ByteArray(32).also{SecureRandom().nextBytes(it)}
    private var allowed=true;private var generation=1L;private var denyCommit=false;private var time=1000L
    private val owners=mutableListOf<SessionDbKeyOwner>()
    private val direct=Executor{it.run()}
    private fun context(scope:AccessScope=AccessScope.READY):StorageContext=object:StorageContext {
        private val captured:Long=this@OperationsStorageTest.generation
        override val generation=captured;override val scope=scope
        override fun check(point:AccessPoint):Boolean {if(denyCommit && point==AccessPoint.COMMIT)allowed=false;return allowed && captured==this@OperationsStorageTest.generation}
        override fun publish(result:()->Unit):Boolean {if(!check(AccessPoint.PUBLICATION))return false;result();return true}
    }
    private fun fixture():ProductInventory {
        val root=File(app.noBackupFilesDir.canonicalFile,"p4-test-${UUID.randomUUID()}");check(root.mkdirs());check(root.resolve("store").mkdir())
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
    private fun <T> write(i:ProductInventory,work:suspend(OperationsStore)->OperationsResult<T>):OperationsResult<T> {
        val c=context();return ActionDatabaseAccess(app).use(i.database,key,c){d->d.withWriteTransaction{val result=work(OperationsStore(d,c){++time});requireOperations(c,AccessPoint.COMMIT);result}}
    }
    private fun <T> value(result:OperationsResult<T>):T {assertTrue(result is OperationsResult.Value);return (result as OperationsResult.Value).value}
    private fun rejected(result:OperationsResult<*>,reason:OperationsFailure){assertEquals(OperationsResult.Rejected(reason),result)}
    private fun role(i:ProductInventory,name:String="Blockierende",a:String="a")=value(write(i){it.role(a,null,name)})
    private fun plan(i:ProductInventory,p:String,ids:Set<String>,primary:String?=null)=value(write(i){it.plan("a",p,PersonRolePlan(ids,primary))})
    private fun board(i:ProductInventory,a:String="a")=value(use(i){d->d.withReadTransaction{OperationsStore(d,context()){++time}.board(a)}})
    private fun advance(i:ProductInventory,a:String="a",from:ActionStatus=ActionStatus.PREPARATION)=use(i){it.advanceAction(a,from,context())}
    private fun state(i:ProductInventory,p:String="Motte")=board(i).persons.single{it.person.id==p}.state!!
    private fun update(i:ProductInventory,p:String="Motte",s:OperationalStatus,role:String?=null,place:String="") {
        val expected=state(i,p);value(write(i){it.snapshot("a",p,expected,s,role,place)})
    }
    private fun repo(i:ProductInventory,main:Executor=direct):OperationsRepository {
        val c=context();val owner=SessionDbKeyOwner.bind(c,key);owners.add(owner)
        return ProductOperationsRepository(app,object:StorageAccess{override fun acquire(scope:AccessScope)=c.takeIf{it.check(AccessPoint.OPERATION)}},i,{owner},direct,main,{fail("P4-UNEXPECTED-STORAGE-BLOCK")}){++time}
    }
    @After fun wipe(){owners.forEach{it.invalidate()};key.fill(0)}
    @Test fun templatesCustomNamesAndActionLocalUniqueness(){
        val i=fixture();OperationalRules.templates.forEach{assertEquals(it,role(i,it).name)}
        val custom=role(i," Eigene Rolle ");assertEquals(" Eigene Rolle ",custom.name)
        rejected(write(i){it.role("a",null," blockierende ")},OperationsFailure.DUPLICATE)
        rejected(write(i){it.role("a",null," ")},OperationsFailure.INVALID)
        assertEquals("Blockierende",role(i,"Blockierende","b").name)
    }
    @Test fun plansAreMultipleMainIsSingleAndReferencesActionLocal(){
        val i=fixture();val support=role(i,"Support");val gesa=role(i,"Gesa-Support");val foreign=role(i,"Other","b")
        plan(i,"Supporter",setOf(support.id,gesa.id),support.id)
        assertEquals(PersonRolePlan(setOf(support.id,gesa.id),support.id),board(i).persons.single{it.person.id=="Supporter"}.plan)
        rejected(write(i){it.plan("a","Supporter",PersonRolePlan(setOf(support.id),gesa.id))},OperationsFailure.INVALID)
        rejected(write(i){it.plan("a","Supporter",PersonRolePlan(setOf(foreign.id),foreign.id))},OperationsFailure.NOT_FOUND)
        rejected(write(i){it.plan("b","Supporter",PersonRolePlan(emptySet(),null))},OperationsFailure.NOT_FOUND)
        assertTrue(board(i,"b").persons.single().plan.roleIds.isEmpty())
    }
    @Test fun renameAndDeleteCannotCascadeAssignments(){
        val i=fixture();val r=role(i);plan(i,"Motte",setOf(r.id),r.id)
        value(write(i){it.role("a",r.id,"Geänderte Rolle")});rejected(write(i){it.deleteRole("a",r.id)},OperationsFailure.IN_USE)
        assertEquals(r.id,board(i).persons.single{it.person.id=="Motte"}.plan.primaryRoleId)
        plan(i,"Motte",emptySet());value(write(i){it.deleteRole("a",r.id)});assertTrue(board(i).roles.isEmpty())
    }
    @Test fun startInitializesEveryPersonAtomicallyWithoutInventingPresence(){
        val i=fixture();val r=role(i);plan(i,"Motte",setOf(r.id),r.id)
        assertTrue(advance(i) is ActionChangeResult.Applied)
        val b=board(i);assertEquals(3,b.persons.size);assertEquals(3,b.count(OperationalStatus.NO_STATUS));assertEquals(0,b.roleCount(r.id))
        assertEquals(r.id,b.persons.single{it.person.id=="Motte"}.state!!.currentRoleId)
        assertNull(b.persons.single{it.person.id=="Meise"}.state!!.currentRoleId)
        use(i){assertEquals(3,it.operations().states("a").size)}
    }
    @Test fun startRollbackLeavesPreparationAndNoHalfSnapshots(){
        val i=fixture();role(i);denyCommit=true
        try{advance(i);fail("P4-COMMIT-MUST-DENY")}catch(_:StorageAccessRevoked){}
        denyCommit=false;allowed=true;generation++
        use(i){assertEquals("PREPARATION",it.actions().get("a")!!.status);assertTrue(it.operations().states("a").isEmpty())}
    }
    @Test fun custodyExampleAndSupportSwitchKeepPlanAndPeopleSeparated(){
        val i=fixture();val block=role(i);val support=role(i,"Support");val gesa=role(i,"Gesa-Support")
        plan(i,"Motte",setOf(block.id),block.id);plan(i,"Meise",setOf(block.id),block.id);plan(i,"Supporter",setOf(support.id,gesa.id),support.id);advance(i)
        update(i,s=OperationalStatus.ON_SITE,role=block.id)
        update(i,s=OperationalStatus.IN_CUSTODY,role=block.id,place=" Gesa 1 ")
        update(i,"Meise",OperationalStatus.IN_CUSTODY,block.id,"Gesa 2")
        update(i,"Supporter",OperationalStatus.ON_SITE,gesa.id)
        val b=board(i);assertEquals(2,b.count(OperationalStatus.IN_CUSTODY));assertEquals(0,b.roleCount(block.id));assertEquals(1,b.roleCount(gesa.id));assertEquals(listOf("Gesa 1","Gesa 2"),b.placeSuggestions)
        val motte=b.persons.single{it.person.id=="Motte"};assertNull(motte.state!!.currentRoleId);assertEquals("Gesa 1",motte.state!!.currentPlaceLabel);assertEquals(block.id,motte.plan.primaryRoleId)
        val supporter=b.persons.single{it.person.id=="Supporter"};assertEquals(support.id,supporter.plan.primaryRoleId);assertEquals(setOf(support.id,gesa.id),supporter.plan.roleIds)
        assertTrue(board(i,"b").placeSuggestions.isEmpty())
    }
    @Test fun unavailableStatesClearRolesAndPlaceDoesNotBecomeHistory(){
        val i=fixture();val r=role(i);advance(i)
        for(s in listOf(OperationalStatus.IDENTITY_CHECK,OperationalStatus.IN_CUSTODY,OperationalStatus.NOT_REACHABLE,OperationalStatus.LEFT,OperationalStatus.UNCLEAR,OperationalStatus.RELEASED)){
            update(i,s=OperationalStatus.ON_SITE,role=r.id);val before=state(i)
            update(i,s=s,role=r.id,place="Gesa 1");val after=state(i);assertNull(after.currentRoleId);assertTrue(after.updatedAt>before.updatedAt)
            assertEquals(if(OperationalRules.placeRelevant(s))"Gesa 1" else null,after.currentPlaceLabel)
        }
        update(i,s=OperationalStatus.ON_SITE,role=r.id,place="Gesa 1");assertNull(state(i).currentPlaceLabel)
        use(i){assertEquals(3,it.operations().states("a").size)}
    }
    @Test fun statusBoundariesAndActiveAdHocRoleLeavePlanFrozen(){
        val i=fixture();val r=role(i)
        val fake=PersonOperationalState("a","Motte",null,OperationalStatus.NO_STATUS,null,0)
        rejected(write(i){it.snapshot("a","Motte",fake,OperationalStatus.ON_SITE,r.id,"")},OperationsFailure.NOT_ALLOWED)
        plan(i,"Motte",setOf(r.id),r.id);advance(i)
        val liveRole=role(i,"Ad hoc")
        rejected(write(i){it.plan("a","Motte",PersonRolePlan(emptySet(),null))},OperationsFailure.NOT_ALLOWED)
        rejected(write(i){it.role("a",r.id,"Must not rename")},OperationsFailure.NOT_ALLOWED)
        rejected(write(i){it.deleteRole("a",r.id)},OperationsFailure.NOT_ALLOWED)
        update(i,s=OperationalStatus.ON_SITE,role=liveRole.id);advance(i,from=ActionStatus.ACTIVE)
        val completed=state(i)
        rejected(write(i){it.snapshot("a","Motte",completed,OperationalStatus.LEFT,null,"")},OperationsFailure.NOT_ALLOWED)
        rejected(write(i){it.role("a",null,"Must not create")},OperationsFailure.NOT_ALLOWED)
        assertEquals(r.id,board(i).persons.single{it.person.id=="Motte"}.plan.primaryRoleId)
    }
    @Test fun crossActionSnapshotsForeignRoleAndStaleWritesAreRejected(){
        val i=fixture();val foreign=role(i,"foreign","b");advance(i);advance(i,"b");val before=state(i)
        rejected(write(i){it.snapshot("b","Motte",before,OperationalStatus.ON_SITE,null,"")},OperationsFailure.NOT_FOUND)
        rejected(write(i){it.snapshot("a","Motte",before,OperationalStatus.ON_SITE,foreign.id,"")},OperationsFailure.NOT_FOUND)
        update(i,s=OperationalStatus.IN_CUSTODY,place="Gesa 1")
        rejected(write(i){it.snapshot("a","Motte",before,OperationalStatus.LEFT,null,"")},OperationsFailure.STALE)
        assertEquals("Gesa 1",state(i).currentPlaceLabel);assertEquals(OperationalStatus.NO_STATUS,board(i,"b").persons.single().state!!.status)
    }
    @Test fun placeLengthAndCommitRevokePreserveSnapshot(){
        val i=fixture();advance(i);val before=state(i)
        rejected(write(i){it.snapshot("a","Motte",before,OperationalStatus.IN_CUSTODY,null,"😀".repeat(81))},OperationsFailure.INVALID)
        denyCommit=true
        try{write(i){it.snapshot("a","Motte",before,OperationalStatus.IN_CUSTODY,null,"Gesa 1")};fail("P4-MUST-ROLLBACK")}catch(_:StorageAccessRevoked){}
        denyCommit=false;allowed=true;generation++;assertEquals(before,state(i))
    }
    @Test fun repositoryGenerationRevokeSuppressesPublicationAndOutsideReadyDenied(){
        val i=fixture();val queued=mutableListOf<Runnable>();val r=repo(i,Executor{queued.add(it)});var published=0
        r.board("a"){published++};generation++;queued.forEach{it.run()};assertEquals(0,published)
        allowed=false;r.createRole("a","must not create"){published++};assertEquals(0,published)
    }
    @Test fun migrationFourToFivePreservesBasicAndSensitiveCiphertexts(){
        val i=fixture();val root=LeadershipRow("a","READY","synthetic-epoch",1,"Argon2id",19,65536,3,1,ByteArray(16){2},ByteArray(88){3})
        val area=SensitiveRow("a","Motte","IDENTITY",1,1,ByteArray(60){4},ByteArray(40){5})
        use(i){d->d.withWriteTransaction{d.leadership().insert(root);d.leadership().insertArea(area);d.actions().update(d.actions().get("a")!!.copy(status="ACTIVE"))}}
        B2Native.open(i.database,key,context(AccessScope.MAINTENANCE)).use{d->d.beginTransaction();try{
            removeMaterialSchemaForLegacyFixture(d)
            listOf("protocol_official_identifiers","protocol_affected_persons","protocol_reported_by_persons","protocol_revisions","protocol_entries","task_events","tasks","person_role_plans","person_operational_states","person_planned_roles","roles").forEach{d.execSQL("DROP TABLE $it")}
            d.execSQL("UPDATE room_master_table SET identity_hash='c78d1afc42f01358bdf04e85f2948fac' WHERE id=42");d.version=4;d.setTransactionSuccessful()
        }finally{d.endTransaction()}}
        val b=board(i);assertEquals(3,b.count(OperationalStatus.NO_STATUS));assertTrue(b.roles.isEmpty());assertTrue(b.persons.all{it.state!!.updatedAt==0L && it.state!!.currentRoleId==null})
        use(i){d->assertEquals("Motte",d.persons().get("a","Motte")!!.pseudonym);assertTrue(root.envelope.contentEquals(d.leadership().get("a")!!.envelope));assertTrue(area.payload.contentEquals(d.leadership().area("a","Motte","IDENTITY")!!.payload));assertTrue(area.keyEnvelope.contentEquals(d.leadership().area("a","Motte","IDENTITY")!!.keyEnvelope))}
        B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{assertEquals(9,it.version)}
    }
    @Test fun compositeForeignKeysRejectDirectCrossActionRelations(){
        val i=fixture();val foreign=role(i,"foreign","b")
        try{use(i){d->d.withWriteTransaction{d.operations().assign(PlannedRoleRow("a","Motte",foreign.id))}};fail("P4-FK-MUST-DENY")}catch(_:Exception){}
        assertTrue(board(i).persons.single{it.person.id=="Motte"}.plan.roleIds.isEmpty())
    }
}
