package org.pag.data.product

import androidx.room3.withWriteTransaction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import org.pag.security.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executor

/** Synthetic standalone test package. No main-app data or authentication dialogs. */
class LeadershipStorageTest {
    private val app=InstrumentationRegistry.getInstrumentation().targetContext
    private val id=UUID.randomUUID().toString();private val other=UUID.randomUUID().toString()
    private val person="synthetic-person";private val second="synthetic-other-person"
    private val key=LeadershipCrypto.random(32)
    private val owners=mutableListOf<SessionDbKeyOwner>();private val repositories=mutableListOf<LeadershipRepository>()
    private val real=AndroidLeadershipDeviceKey()
    private val devices=mutableListOf<LeadershipDeviceKey>()
    private var allowed=true;private var generation=1L;private var now=0L;private var denyCommit=false
    private var beforePublication:()->Unit={}
    private fun pass()="synthetic leadership fixture only".toCharArray()
    private fun context(scopeValue:AccessScope=AccessScope.READY):StorageContext=object:StorageContext {
        private val captured:Long=this@LeadershipStorageTest.generation
        override val generation=captured;override val scope=scopeValue
        override fun check(point:AccessPoint):Boolean {if(denyCommit&&point==AccessPoint.COMMIT)allowed=false;return allowed&&captured==this@LeadershipStorageTest.generation}
        override fun <T> admitKey(validUntilMillis:Long,acquire:(()->Boolean)->T?):T? =
            synchronized(this){acquire{scope==AccessScope.READY && check(AccessPoint.STEP) && now<validUntilMillis}}
        override fun publish(result:()->Unit):Boolean{beforePublication();if(!check(AccessPoint.PUBLICATION))return false;result();return true}
    }
    private val direct=Executor{it.run()}
    private fun inv(withPersons:Boolean=true):ProductInventory {
        val root=File(app.noBackupFilesDir.canonicalFile,"p3b-test-${UUID.randomUUID()}");check(root.mkdirs());check(root.resolve("store").mkdir())
        listOf("intent","complete","public").forEach{root.resolve(it).writeBytes(byteArrayOf(1))};root.resolve("envelope").writeBytes(ByteArray(256))
        return ProductInventory(root,{true},{true}).also { i->ActionDatabaseAccess(app).use(i.database,key,context(AccessScope.MAINTENANCE),true){d->d.withWriteTransaction{
            d.actions().insert(ActionRow(id,"synthetic","synthetic",1,2,0,"PREPARATION"));d.actions().insert(ActionRow(other,"synthetic other","synthetic",1,2,0,"PREPARATION"))
            if(withPersons){
            d.persons().insert(PersonRow(person,id,"synthetic-01","synthetic-01",0));d.persons().insert(PersonRow(second,id,"synthetic-02","synthetic-02",0))
            d.persons().insert(PersonRow("synthetic-other-action-person",other,"synthetic-01","synthetic-01",0))
            }
        }}}
    }
    private class Device:LeadershipDeviceKey {
        val keys=mutableMapOf<String,ByteArray>();var failCreate=false;var failDelete=false
        var onPresent:()->Unit={};var encryptions=0;var decryptions=0
        override fun present(alias:String):Boolean {onPresent();return keys.containsKey(alias)}
        override fun create(alias:String){if(failCreate)throw LeadershipCryptoFailure();check(!present(alias));keys[alias]=LeadershipCrypto.random(32)}
        override fun encrypt(alias:String,bytes:ByteArray,aad:ByteArray):ByteArray {encryptions++;return LeadershipCrypto.encrypt(keys[alias]!!,bytes,aad)}
        override fun decrypt(alias:String,bytes:ByteArray,aad:ByteArray):ByteArray {decryptions++;return LeadershipCrypto.decrypt(keys[alias]!!,bytes,aad)}
        override fun delete(alias:String){if(failDelete)throw LeadershipCryptoFailure();keys.remove(alias)?.fill(0)}
    }
    private fun repo(i:ProductInventory,device:LeadershipDeviceKey=Device(),main:Executor=direct,worker:Executor=direct,afterWrite:()->Unit={}):LeadershipRepository {
        devices.add(device);val c=context();val owner=SessionDbKeyOwner.bind(c,key);owners.add(owner)
        return ProductLeadershipRepository(app,object:StorageAccess{override fun acquire(scope:AccessScope)=c.takeIf{it.check(AccessPoint.OPERATION)}},i,{owner},worker,main,MonotonicTime{now},device,afterWrite).also{repositories.add(it)}
    }
    private fun setup(r:LeadershipRepository){var result:SensitiveResult<Unit>?=null;r.setup(id,pass(),pass()){result=it};assertTrue(result is SensitiveResult.Value)}
    private fun open(r:LeadershipRepository,area:SensitiveArea=SensitiveArea.IDENTITY):SensitiveOpened {var result:SensitiveResult<SensitiveOpened>?=null;r.open(id,person,area,pass()){result=it};assertTrue(result is SensitiveResult.Value);return (result as SensitiveResult.Value).value}
    private fun save(r:LeadershipRepository,opened:SensitiveOpened,value:SensitiveData){var result:SensitiveResult<Unit>?=null;r.save(opened.grant,value){result=it};assertTrue(result is SensitiveResult.Value)}
    private fun inventory(r:LeadershipRepository):SensitiveInventory{var result:SensitiveResult<SensitiveInventory>?=null;r.inventory(id,person){result=it};assertTrue(result is SensitiveResult.Value);return (result as SensitiveResult.Value).value}
    @After fun cleanup(){repositories.forEach{it.revoke()};owners.forEach{it.invalidate()};key.fill(0);devices.forEach{d->listOf(id,other).forEach{a->d.delete("org.pag.product.leadership.aes.v1.$a")}}}

    private fun existingRow(i:ProductInventory,who:String=person,area:SensitiveArea=SensitiveArea.IDENTITY)=
        ActionDatabaseAccess(app).use(i.database,key,context()){it.leadership().area(id,who,area.name)!!}
    private fun alter(i:ProductInventory,row:SensitiveRow)=ActionDatabaseAccess(app).use(i.database,key,context()){it.leadership().updateArea(row)}
    private fun assertPreserved(i:ProductInventory,expected:SensitiveRow){val actual=existingRow(i,expected.personId,SensitiveArea.valueOf(expected.area));assertEquals(expected.cryptoVersion,actual.cryptoVersion);assertEquals(expected.revision,actual.revision);assertTrue(expected.keyEnvelope.contentEquals(actual.keyEnvelope));assertTrue(expected.payload.contentEquals(actual.payload))}
    private fun otherArea(r:LeadershipRepository){var opened:SensitiveOpened?=null;r.open(id,second,SensitiveArea.EMERGENCY_CONTACT,pass()){opened=(it as SensitiveResult.Value).value};save(r,opened!!,SensitiveData.EmergencyContact("synthetic other"))}
    private fun actionState(r:LeadershipRepository,action:String=id):SensitiveState {
        var result:SensitiveResult<SensitiveState>?=null
        r.actionState(action){result=it}
        assertTrue(result is SensitiveResult.Value)
        return (result as SensitiveResult.Value).value
    }
    @Test fun actionStateUninitializedWithoutAnyPerson() {
        val i=inv(false);val d=Device();val r=repo(i,d)
        assertEquals(SensitiveState.UNINITIALIZED,actionState(r))
        assertTrue(ActionDatabaseAccess(app).use(i.database,key,context()){it.persons().list(id)}.isEmpty())
        assertTrue(d.keys.isEmpty());assertEquals(0,d.encryptions);assertEquals(0,d.decryptions)
    }
    @Test fun actionStateReadyAfterExistingSetupWithoutAnyPerson() {
        val i=inv(false);val d=Device();val r=repo(i,d);setup(r)
        val encryptions=d.encryptions;val decryptions=d.decryptions
        assertEquals(SensitiveState.READY,actionState(r))
        assertTrue(ActionDatabaseAccess(app).use(i.database,key,context()){it.persons().list(id)}.isEmpty())
        assertEquals(encryptions,d.encryptions);assertEquals(decryptions,d.decryptions)
    }
    @Test fun actionStatePartialWithoutPersonDoesNotPretendUninitialized() {
        val i=inv(false);val d=Device();val r=repo(i,d)
        d.create("org.pag.product.leadership.aes.v1.$id")
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,actionState(r))
        assertEquals(0,d.encryptions);assertEquals(0,d.decryptions)
    }
    @Test fun actionStateMatchesPersonInventoryForAllThreeStates() {
        val i=inv();val d=Device();val r=repo(i,d)
        fun same(expected:SensitiveState) {assertEquals(expected,actionState(r));assertEquals(expected,inventory(r).state)}
        same(SensitiveState.UNINITIALIZED);setup(r);same(SensitiveState.READY)
        d.delete("org.pag.product.leadership.aes.v1.$id");same(SensitiveState.PARTIAL_OR_INVALID)
    }
    @Test fun actionStateMissingActionDeniedAndOtherActionNotBorrowed() {
        val i=inv(false);val r=repo(i);setup(r)
        assertEquals(SensitiveState.READY,actionState(r))
        assertEquals(SensitiveState.UNINITIALIZED,actionState(r,other))
        var result:SensitiveResult<SensitiveState>?=null
        r.actionState("synthetic-missing-action"){result=it}
        assertEquals(SensitiveResult.Rejected(SensitiveFailure.DENIED),result)
    }
    @Test fun actionStateOldGenerationQueuedReadCannotPublish() {
        val i=inv(false);val queue=mutableListOf<Runnable>();val r=repo(i,worker=Executor{queue.add(it)})
        r.actionState(id){fail("Stale metadata publication")};generation++;queue.single().run()
    }
    @Test fun actionStateLockDuringMetadataReadCannotPublish() {
        val i=inv(false);val d=Device();val r=repo(i,d)
        d.onPresent={allowed=false;generation++}
        r.actionState(id){fail("Locked metadata publication")}
    }
    @Test fun actionStateRepositoryRevokeDuringReadCannotPublish() {
        val i=inv(false);val d=Device();val r=repo(i,d)
        d.onPresent={r.revoke()}
        r.actionState(id){fail("Revoked metadata publication")}
    }
    @Test fun actionStateLockBeforeQueuedPublicationCannotPublish() {
        val i=inv(false);val queue=mutableListOf<Runnable>();val r=repo(i,main=Executor{queue.add(it)})
        r.actionState(id){fail("Late metadata publication")};allowed=false;generation++;queue.single().run()
    }
    @Test fun actionStateRevokeBeforeQueuedPublicationCannotPublish() {
        val i=inv(false);val queue=mutableListOf<Runnable>();val r=repo(i,main=Executor{queue.add(it)})
        r.actionState(id){fail("Late revoked metadata publication")};r.revoke();queue.single().run()
    }
    @Test fun actionStateRevokeBetweenOuterCheckAndPublicationCannotPublish() {
        val i=inv(false);val r=repo(i)
        beforePublication={r.revoke()}
        r.actionState(id){fail("Request changed at publication boundary")}
    }

    @Test fun realCoordinatorAndRepositoryConcurrentLockAndBackgroundCleanup() {
        val inventory = inv()
        var authResult: ((AuthenticationOutcome) -> Unit)? = null
        var maintenanceResult: ((Boolean) -> Unit)? = null
        val coordinator = org.pag.data.SessionCoordinator(object : AuthenticationExperiment {
            override fun start(mode: KeyExperiment, complete: (AuthenticationOutcome) -> Unit): Cancellation {
                authResult = complete; return Cancellation {}
            }
        }, Maintenance { maintenanceResult = it; Subscription {} }, MonotonicTime { 0 }, SessionPolicy(180000, 30000))
        var currentOwner: SessionDbKeyOwner? = null
        devices.add(real) // Cleanup only this isolated fixture's own P3b aliases.
        val repository = ProductLeadershipRepository(app, coordinator, inventory, { currentOwner }, direct, direct, MonotonicTime { 0 }, real)
        repositories.add(repository)
        coordinator.observe {
            if (it.state != SessionState.READY) { repository.revoke(); currentOwner?.invalidate(); currentOwner = null }
        }
        for (background in listOf(false, true)) {
            coordinator.foreground(true); coordinator.authenticate(Experiment.PER_OPERATION)
            authResult!!(AuthenticationOutcome.Granted(object : SyntheticOperation {
                override fun perform() = OperationResult.SUCCEEDED
                override fun release() {}
            }, 0)); maintenanceResult!!(true)
            val context = coordinator.acquire(AccessScope.READY)!!
            currentOwner = SessionDbKeyOwner.bind(context, key).also { owners.add(it) }
            if (!background) setup(repository)
            assertEquals(SensitiveState.READY, actionState(repository))
            val grant = open(repository).grant as LeadershipGrant
            assertEquals(context.generation, grant.context.generation)
            var admittedLease: ByteArray? = null
            grant.consume { bytes ->
                assertFalse(Thread.holdsLock(coordinator)); assertFalse(Thread.holdsLock(grant))
                admittedLease = bytes
            }
            assertTrue(admittedLease!!.all { it == 0.toByte() })
            val keyField = grant.javaClass.getDeclaredField("bytes").apply { isAccessible = true }
            val ownedKey = keyField.get(grant) as ByteArray
            val held = java.util.concurrent.CountDownLatch(1)
            val done = java.util.concurrent.CountDownLatch(2)
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
            lateinit var checker: Thread
            val locker = Thread {
                try {
                    synchronized(coordinator) {
                        held.countDown()
                        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
                        // Grant is uncontended; only the real coordinator can block this check.
                        while (checker.state != Thread.State.BLOCKED) {
                            check(System.nanoTime() < deadline); Thread.yield()
                        }
                        if (background) coordinator.foreground(false) else coordinator.lock()
                    }
                } catch (t: Throwable) { failure.compareAndSet(null, t) } finally { done.countDown() }
            }.apply { isDaemon = true }
            checker = Thread {
                try {
                    assertTrue(held.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    assertThrows(SensitiveProblem::class.java) { grant.consume { fail("Concurrent lock admitted key callback") } }
                }
                catch (t: Throwable) { failure.compareAndSet(null, t) } finally { done.countDown() }
            }.apply { isDaemon = true }
            locker.start(); checker.start()
            assertTrue("Real repository revoke must terminate", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError(it) }
            assertEquals(SessionState.LOCKED, coordinator.snapshot().state)
            assertThrows(SensitiveProblem::class.java) { grant.consume { fail("Key callback after session revoke") } }
            assertFalse(grant.valid()); assertNull(keyField.get(grant)); assertTrue(ownedKey.all { it == 0.toByte() })
            assertNull(currentOwner); assertFalse(context.check(AccessPoint.COMMIT))
            assertFalse(context.publish { fail("Old generation publication") })
            repository.actionState(id) { fail("Metadata publication after coordinator lock") }
        }
    }

    @Test fun unknownOtherPersonAreaBlocksEverySensitiveOpenAndExistingGrantSave() {
        val i=inv();val r=repo(i);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"));otherArea(r)
        val granted=open(r);val bad=existingRow(i,second,SensitiveArea.EMERGENCY_CONTACT).copy(cryptoVersion=99);alter(i,bad)
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
        var denied=0;r.save(granted.grant,SensitiveData.Identity("must not persist")){assertTrue(it is SensitiveResult.Rejected);denied++}
        for(area in SensitiveArea.entries)r.open(id,person,area,pass()){assertTrue(it is SensitiveResult.Rejected);denied++}
        assertEquals(4,denied);assertPreserved(i,bad)
        ActionDatabaseAccess(app).use(i.database,key,context()){assertEquals("synthetic-01",it.persons().get(id,person)!!.model().pseudonym);assertNotNull(it.actions().get(id))}
    }
    @Test fun unknownSelectedVersionCannotBeRepairedByExistingGrant() {
        val i=inv();val r=repo(i);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"));val granted=open(r)
        val bad=existingRow(i).copy(cryptoVersion=99);alter(i,bad)
        r.save(granted.grant,SensitiveData.Identity("must not repair")){assertTrue(it is SensitiveResult.Rejected)}
        assertPreserved(i,bad);assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
    }
    @Test fun malformedOtherEnvelopeAndRevisionBlockActionWithoutRepair() {
        val i=inv();val r=repo(i);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"));otherArea(r)
        val original=existingRow(i,second,SensitiveArea.EMERGENCY_CONTACT)
        for(bad in listOf(original.copy(keyEnvelope=byteArrayOf()),original.copy(payload=byteArrayOf()),original.copy(revision=0))){
            val granted=open(r);alter(i,bad)
            assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
            r.save(granted.grant,SensitiveData.Identity("must not repair")){assertTrue(it is SensitiveResult.Rejected)}
            r.open(id,person,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected)}
            assertPreserved(i,bad);alter(i,original) // Restore only this deliberate isolated fixture mutation, not product recovery.
        }
    }
    @Test fun changedEncryptedPayloadCannotBeOverwrittenEvenWithSameRevision() {
        val i=inv();val r=repo(i);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"));val opened=open(r)
        val row=existingRow(i);val root=ActionDatabaseAccess(app).use(i.database,key,context()){it.leadership().get(id)!!}
        val aad=SensitiveCodec.context("P3b-area",id,person,"IDENTITY","1",root.epoch,row.revision.toString())
        val unknown=java.nio.ByteBuffer.allocate(5).putInt(99).put(0).array()
        val changed=(opened.grant as LeadershipGrant).consume{LeadershipCrypto.encrypt(it,unknown,aad)}
        unknown.fill(0);val bad=row.copy(payload=changed);alter(i,bad)
        r.save(opened.grant,SensitiveData.Identity("must not overwrite")){assertTrue(it is SensitiveResult.Rejected)}
        assertPreserved(i,bad)
        r.open(id,person,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected)}
    }
    @Test fun uninitializedOpenAndForeignGrantDoNotProvisionAnything() {
        val i=inv();val d=Device();val r=repo(i,d)
        r.open(id,person,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected)}
        val foreign=LeadershipGrant(id,person,SensitiveArea.IDENTITY,context(),MonotonicTime{now},ByteArray(32),"synthetic",0,byteArrayOf(),true)
        r.save(foreign,SensitiveData.Identity("must not initialize")){assertTrue(it is SensitiveResult.Rejected)}
        assertEquals(SensitiveState.UNINITIALIZED,inventory(r).state);assertTrue(d.keys.isEmpty());foreign.cancel()
    }
    @Test fun normalDeletesRequireReadyButAuthorizedPendingCleanupStillCompletes() {
        val i=inv();val d=Device();val r=repo(i,d);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"));otherArea(r)
        val granted=open(r);val original=existingRow(i,second,SensitiveArea.EMERGENCY_CONTACT);val bad=original.copy(cryptoVersion=99);alter(i,bad)
        r.deleteArea(granted.grant){assertTrue(it is SensitiveResult.Rejected)}
        r.deletePerson(id,person,pass()){assertTrue(it is SensitiveResult.Rejected)}
        r.deleteAction(id,pass()){assertTrue(it is SensitiveResult.Rejected)}
        assertPreserved(i,bad);assertTrue(d.keys.isNotEmpty());alter(i,original)
        d.failDelete=true;r.deleteAction(id,pass()){assertTrue(it is SensitiveResult.Rejected)};d.failDelete=false
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
        alter(i,bad);d.delete("org.pag.product.leadership.aes.v1.$id")
        r.deleteAction(id,pass()){assertTrue(it is SensitiveResult.Value)}
        assertEquals(SensitiveState.UNINITIALIZED,inventory(r).state)
        ActionDatabaseAccess(app).use(i.database,key,context()){assertNotNull(it.persons().get(id,person));assertNotNull(it.actions().get(id))}
    }
    @Test fun migrationPreservesVersionThreeBasicRowsWithoutProvisioning() {
        val i=inv()
        // Recreate the historical v3 structure in this isolated synthetic fixture.
        org.pag.data.prototype.b2.B2Native.open(i.database,key,context(AccessScope.MAINTENANCE)).use { db ->
            db.beginTransaction()
            try {
                listOf("protocol_official_identifiers","protocol_affected_persons","protocol_reported_by_persons","protocol_revisions","protocol_entries").forEach{db.execSQL("DROP TABLE $it")}
                db.execSQL("DROP TABLE task_events")
                db.execSQL("DROP TABLE tasks")
                db.execSQL("DROP TABLE person_role_plans")
                db.execSQL("DROP TABLE person_operational_states")
                db.execSQL("DROP TABLE person_planned_roles")
                db.execSQL("DROP TABLE roles")
                db.execSQL("DROP TABLE sensitive_person_areas")
                db.execSQL("DROP TABLE leadership")
                db.execSQL("DROP INDEX index_persons_actionId_id")
                db.execSQL("UPDATE room_master_table SET identity_hash='d4be18204f1912f7c9ce9e6563f0e469' WHERE id=42")
                db.version=3;db.setTransactionSuccessful()
            } finally {db.endTransaction()}
        }
        val d=Device();val r=repo(i,d)
        assertEquals(SensitiveState.UNINITIALIZED,inventory(r).state);assertTrue(d.keys.isEmpty())
        ActionDatabaseAccess(app).use(i.database,key,context()){db ->
            assertEquals("synthetic",db.actions().get(id)!!.title)
            assertEquals("synthetic-01",db.persons().get(id,person)!!.pseudonym)
            assertEquals("synthetic other",db.actions().get(other)!!.title)
            assertNull(db.leadership().get(id));assertTrue(db.leadership().all(id).isEmpty())
        }
        org.pag.data.prototype.b2.B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{assertEquals(7,it.version)}
    }
    @Test fun explicitSetupAndAllThreeAreasPersistAcrossNewRuntime() {
        val i=inv();val device=Device();var r=repo(i,device)
        assertEquals(SensitiveState.UNINITIALIZED,inventory(r).state);assertTrue(device.keys.isEmpty())
        setup(r);assertEquals(SensitiveState.READY,inventory(r).state);assertTrue(inventory(r).areas.isEmpty())
        val entries=listOf(SensitiveData.Identity("synthetic identity",LocalDate.of(2000,1,2)),SensitiveData.Contact("synthetic phone"),SensitiveData.EmergencyContact("synthetic contact","synthetic phone","synthetic relationship"))
        entries.forEach{v->save(r,open(r,LeadershipRules.area(v)),v)}
        r.revoke();owners.forEach{it.invalidate()};generation++;r=repo(i,device)
        entries.forEach{v->assertEquals(v,open(r,LeadershipRules.area(v)).data)}
        assertEquals(SensitiveArea.entries.toSet(),inventory(r).areas)
    }
    @Test fun updateAndOptionalEmptyPayloadDoNotCreateExtraAreas() {
        val i=inv();val r=repo(i);setup(r)
        save(r,open(r),SensitiveData.Identity("synthetic original"))
        save(r,open(r),SensitiveData.Identity("synthetic changed"))
        assertEquals(SensitiveData.Identity("synthetic changed"),open(r).data)
        save(r,open(r),SensitiveData.Identity())
        assertTrue(inventory(r).areas.isEmpty())
    }
    @Test fun rootMetadataTamperingBlocksWithoutReplacingKeys() {
        val i=inv();val d=Device();val r=repo(i,d);setup(r)
        ActionDatabaseAccess(app).use(i.database,key,context()){db->val row=db.leadership().get(id)!!;db.leadership().update(row.copy(iterations=2))}
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
        r.open(id,person,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected)}
        r.setup(id,pass(),pass()){assertTrue(it is SensitiveResult.Rejected)}
        assertEquals(1,d.keys.size)
    }
    @Test fun queuedSetupCancellationWipesInputsWithoutInitialization() {
        val i=inv();val d=Device();val queue=mutableListOf<Runnable>();val r=repo(i,d,worker=Executor{queue.add(it)})
        val password=pass();val confirmation=pass()
        r.setup(id,password,confirmation){fail("P3B-NO-CANCELLED-PUBLICATION")}
        r.revoke();assertTrue(password.all{it=='\u0000'});assertTrue(confirmation.all{it=='\u0000'})
        queue.single().run();assertTrue(d.keys.isEmpty());assertEquals(SensitiveState.UNINITIALIZED,inventory(repo(i,d)).state)
    }
    @Test fun rejectedExecutorWipesOwnedInputsWithoutInitialization() {
        val i=inv();val d=Device();val r=repo(i,d,worker=Executor{throw java.util.concurrent.RejectedExecutionException()})
        val password=pass();val confirmation=pass();r.setup(id,password,confirmation){fail("P3B-NO-REJECTED-PUBLICATION")}
        assertTrue(password.all{it=='\u0000'});assertTrue(confirmation.all{it=='\u0000'});assertTrue(d.keys.isEmpty())
        assertEquals(SensitiveState.UNINITIALIZED,inventory(repo(i,d)).state)
    }
    @Test fun wrongSecretHasNoMutationAndInputArraysAreWiped() {
        val i=inv();val r=repo(i);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"))
        val before=ActionDatabaseAccess(app).use(i.database,key,context()){it.leadership().area(id,person,"IDENTITY")!!.payload.copyOf()}
        val password="different synthetic secret".toCharArray();var result:SensitiveResult<SensitiveOpened>?=null
        r.open(id,person,SensitiveArea.IDENTITY,password){result=it};assertTrue(result is SensitiveResult.Rejected);assertTrue(password.all{it=='\u0000'})
        val after=ActionDatabaseAccess(app).use(i.database,key,context()){it.leadership().area(id,person,"IDENTITY")!!.payload}
        assertTrue(before.contentEquals(after));assertEquals(SensitiveData.Identity("synthetic"),open(r).data)
    }
    @Test fun ownerAndAreaAADSwapsAreRejected() {
        val i=inv();val r=repo(i);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"))
        ActionDatabaseAccess(app).use(i.database,key,context()){d->d.withWriteTransaction{val row=d.leadership().area(id,person,"IDENTITY")!!;d.leadership().insertArea(row.copy(personId=second));d.leadership().insertArea(row.copy(area="CONTACT"))}}
        var bad=0
        r.open(id,second,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected);bad++}
        r.open(id,person,SensitiveArea.CONTACT,pass()){assertTrue(it is SensitiveResult.Rejected);bad++}
        r.open(other,person,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected);bad++}
        assertEquals(3,bad);assertEquals(SensitiveData.Identity("synthetic"),open(r).data)
    }
    @Test fun tamperedPayloadFailsClosed() {
        val i=inv();val r=repo(i);setup(r)
        r.save(open(r).grant,SensitiveData.Contact("synthetic phone")){assertTrue(it is SensitiveResult.Rejected)}
        assertTrue(inventory(r).areas.isEmpty())
        save(r,open(r),SensitiveData.Identity("synthetic"))
        ActionDatabaseAccess(app).use(i.database,key,context()){d->d.withWriteTransaction{
            val row=d.leadership().area(id,person,"IDENTITY")!!
            d.leadership().updateArea(row.copy(payload=row.payload.copyOf().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}))
        }}
        r.open(id,person,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected)}
    }
    @Test fun activeAndCompletedReadOnlyAndDraftDenied() {
        val i=inv();val r=repo(i);setup(r);save(r,open(r),SensitiveData.Identity("synthetic"))
        for(status in listOf(ActionStatus.ACTIVE,ActionStatus.COMPLETED,ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)) {
            ActionDatabaseAccess(app).use(i.database,key,context()){d->d.withWriteTransaction{val a=d.actions().get(id)!!;d.actions().update(a.copy(status=status.name,completedAt=if(status==ActionStatus.COMPLETED)10 else null))}}
            if(LeadershipRules.mayRead(status)){val opened=open(r);var result:SensitiveResult<Unit>?=null;r.save(opened.grant,SensitiveData.Identity("synthetic changed")){result=it};assertTrue(result is SensitiveResult.Rejected)}
            else r.open(id,person,SensitiveArea.IDENTITY,pass()){assertTrue(it is SensitiveResult.Rejected)}
        }
    }
    @Test fun partialSetupAndMissingRootPreserveBasic() {
        val i=inv();val d=Device().also{it.failCreate=true};val r=repo(i,d)
        r.setup(id,pass(),pass()){assertTrue(it is SensitiveResult.Rejected)}
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state);d.failCreate=false
        r.setup(id,pass(),pass()){assertTrue(it is SensitiveResult.Rejected)};assertTrue(d.keys.isEmpty())
        ActionDatabaseAccess(app).use(i.database,key,context()){assertEquals("synthetic-01",it.persons().get(id,person)!!.pseudonym)}
    }
    @Test fun aliasWithoutEnvelopeAndCiphertextWithoutEnvelopeAreNotEmpty() {
        val i=inv();val d=Device();val r=repo(i,d);d.create("org.pag.product.leadership.aes.v1.$id")
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
        d.delete("org.pag.product.leadership.aes.v1.$id")
        ActionDatabaseAccess(app).use(i.database,key,context()){it.leadership().insertArea(SensitiveRow(id,person,"IDENTITY",1,1,ByteArray(60),ByteArray(60)))}
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
    }
    @Test fun grantExpiresAndNavigationRevokesWithoutPublication() {
        val i=inv();val r=repo(i);setup(r);val opened=open(r)
        now=59999;assertTrue(opened.grant.valid());now=60000;assertFalse(opened.grant.valid())
        r.save(opened.grant,SensitiveData.Identity("synthetic")){fail("P3B-NO-EXPIRED-PUBLICATION")}
        now=0;assertFalse(opened.grant.valid());val fresh=open(r);r.revoke();assertFalse(fresh.grant.valid())
    }
    @Test fun normalRevocationAndStaleGenerationDeny() {
        val i=inv();val r=repo(i);setup(r);val g=open(r).grant
        allowed=false;assertFalse(g.valid());r.open(id,person,SensitiveArea.IDENTITY,pass()){fail("P3B-NO-LOCKED-PUBLICATION")}
        allowed=true;generation++;r.inventory(id,person){fail("P3B-NO-STALE-PUBLICATION")}
    }
    @Test fun commitRevocationRollsBackSensitiveWrite() {
        val i=inv();val d=Device();val queue=mutableListOf<Runnable>();val r=repo(i,d,worker=Executor{queue.add(it)},afterWrite={denyCommit=true})
        r.setup(id,pass(),pass()){assertTrue(it is SensitiveResult.Value)};queue.removeAt(0).run()
        var opened:SensitiveOpened?=null;r.open(id,person,SensitiveArea.IDENTITY,pass()){opened=(it as SensitiveResult.Value).value};queue.removeAt(0).run()
        r.save(opened!!.grant,SensitiveData.Identity("synthetic lost")){fail("P3B-NO-REVOKED-PUBLICATION")};queue.removeAt(0).run()
        allowed=true;denyCommit=false;generation++
        assertTrue(inventory(repo(i,d)).areas.isEmpty())
    }
    @Test fun delayedSensitivePublicationAndPendingNavigationAreSuppressed() {
        val i=inv();val d=Device();val r=repo(i,d);setup(r)
        val queue=mutableListOf<Runnable>();val delayed=repo(i,d,main=Executor{queue.add(it)})
        delayed.open(id,person,SensitiveArea.IDENTITY,pass()){fail("P3B-NO-DELAYED-PUBLICATION")};now=60000;queue.forEach{it.run()}
        now=0;queue.clear();delayed.open(id,person,SensitiveArea.IDENTITY,pass()){fail("P3B-NO-NAVIGATION-PUBLICATION")};delayed.revoke();queue.forEach{it.run()}
    }
    @Test fun logicalDeletionAndActionRootRemovalPreserveBasic() {
        val i=inv();val d=Device();val r=repo(i,d);setup(r)
        save(r,open(r),SensitiveData.Identity("synthetic"));save(r,open(r,SensitiveArea.CONTACT),SensitiveData.Contact("synthetic"))
        r.deleteArea(open(r).grant){assertTrue(it is SensitiveResult.Value)};assertEquals(setOf(SensitiveArea.CONTACT),inventory(r).areas)
        r.deletePerson(id,person,pass()){assertTrue(it is SensitiveResult.Value)};assertTrue(inventory(r).areas.isEmpty())
        save(r,open(r),SensitiveData.Identity("synthetic"));r.deleteAction(id,pass()){assertTrue(it is SensitiveResult.Value)}
        assertEquals(SensitiveState.UNINITIALIZED,inventory(r).state);assertTrue(d.keys.isEmpty())
        ActionDatabaseAccess(app).use(i.database,key,context()){assertNotNull(it.persons().get(id,person));assertNotNull(it.actions().get(id))}
    }
    @Test fun realKeystoreRootIsNonExportableAndNeedsNoCredentialPrompt() {
        val i=inv();val r=repo(i,real);setup(r)
        assertTrue(real.present("org.pag.product.leadership.aes.v1.$id"));assertTrue(real.usable("org.pag.product.leadership.aes.v1.$id"))
        val store=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val deviceKey=store.getKey("org.pag.product.leadership.aes.v1.$id",null) as javax.crypto.SecretKey
        assertNull(deviceKey.encoded)
        val info=javax.crypto.SecretKeyFactory.getInstance("AES","AndroidKeyStore").getKeySpec(deviceKey,android.security.keystore.KeyInfo::class.java) as android.security.keystore.KeyInfo
        assertFalse(info.isUserAuthenticationRequired)
        InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply{putString("P3B_DEVICE_KEY","securityLevel=${info.securityLevel}; authRequired=false; nonExportable=true")})
        save(r,open(r,SensitiveArea.EMERGENCY_CONTACT),SensitiveData.EmergencyContact("synthetic"))
        assertEquals(SensitiveData.EmergencyContact("synthetic"),open(r,SensitiveArea.EMERGENCY_CONTACT).data)
        real.delete("org.pag.product.leadership.aes.v1.$id")
        assertEquals(SensitiveState.PARTIAL_OR_INVALID,inventory(r).state)
        r.setup(id,pass(),pass()){assertTrue(it is SensitiveResult.Rejected)};assertFalse(real.present("org.pag.product.leadership.aes.v1.$id"))
    }
}
