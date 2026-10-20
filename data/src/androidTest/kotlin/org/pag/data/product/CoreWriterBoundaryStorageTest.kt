package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor

/** Prepared/compiled OFFLINE ONLY in #35/#37. Own synthetic namespace/key. Future isolated
 * native proof of actual repositories, same port, Room transactions and post-cleanup results.
 */
class CoreWriterBoundaryStorageTest {
    @Test fun sharedNativeWritersAndFrozenPlansKeepConfirmedOutcomesAndOneEpoch() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val key=ByteArray(32).also{SecureRandom().nextBytes(it)}
        val ready=object:StorageContext {
            override val scope=AccessScope.READY
            override val generation=1L
            override fun check(point:AccessPoint)=true
            override fun publish(result:()->Unit):Boolean{result();return true}
        }
        val maintenance=object:StorageContext by ready {override val scope=AccessScope.MAINTENANCE}
        val root=File(app.noBackupFilesDir,"p11-corewriter-${UUID.randomUUID()}")
        check(root.mkdirs());check(root.resolve("store").mkdir())
        listOf("intent","complete","public").forEach{root.resolve(it).writeBytes(byteArrayOf(1))}
        root.resolve("envelope").writeBytes(ByteArray(256))
        val inventory=ProductInventory(root,{true},{true})
        val owner=SessionDbKeyOwner.bind(ready,key)
        try {
            ActionDatabaseAccess(app).use(inventory.database,key,maintenance,true){Unit}
            val access=object:StorageAccess{override fun acquire(scope:AccessScope)=ready.takeIf{scope==AccessScope.READY}}
            val epoch=UncachedWorkspaceMutations();val reports=ArrayList<WorkspaceWriteCompletion>()
            val sink=object:WorkspaceMutationInvalidation {
                override fun beforeTaskQueue(actionId:String,generation:Long)=beforeCoreQueue(WorkspaceMutation(WorkspaceWriterFamily.TASK,actionId),generation)
                override fun beforeCoreQueue(mutation:WorkspaceMutation,generation:Long):WorkspaceMutationInvalidation.Pending? {
                    val pending=epoch.beforeCoreQueue(mutation,generation)?:return null
                    return object:WorkspaceMutationInvalidation.Pending {
                        override fun finish(completion:WorkspaceWriteCompletion):Boolean{reports.add(completion);return pending.finish(completion)}
                        override fun isCurrent()=pending.isCurrent()
                    }
                }
            }
            val jobs=ArrayDeque<Runnable>();val worker=Executor{jobs.add(it)};val main=Executor{it.run()}
            val blocked={fail("P11-CORE-WRITER-UNEXPECTED-BLOCK")}
            val actions=ProductActionRepository(app,access,inventory,{owner},worker,main,sink,blocked)
            val persons=ProductPersonRepository(app,access,inventory,{owner},worker,main,sink,blocked)
            val ops=ProductOperationsRepository(app,access,inventory,{owner},worker,main,blocked,mutations=sink)
            fun run(){val job=jobs.removeFirst();job.run();job.run()}
            var action:Action?=null;actions.createAction(NewAction("Synthetic","Synthetic action",1,2)){action=it};run()
            val a=checkNotNull(action).id
            actions.advanceActionStatus(a,ActionStatus.DRAFT){assertTrue(it is ActionChangeResult.Applied)};run()
            var person:Person?=null;persons.createPerson(a,"Synthetic person"){person=(it as PersonResult.Value).value};run()
            var role:Role?=null;ops.createRole(a,"Synthetic role"){role=(it as OperationsResult.Value).value};run()
            val selected=linkedSetOf(checkNotNull(role).id)
            ops.planRoles(a,checkNotNull(person).id,ExpectedPersonRolePlan(a,checkNotNull(person).id,PersonRolePlan(emptySet(),null)),PersonRolePlan(selected,role!!.id)){assertTrue(it is OperationsResult.Value)}
            selected.clear() // queued work must use its frozen plan, not this mutable source
            run()
            actions.advanceActionStatus(a,ActionStatus.PREPARATION){assertTrue(it is ActionChangeResult.Applied)};run()
            ops.board(a){r->val board=(r as OperationsResult.Value).value;assertEquals(setOf(role!!.id),board.persons.single().plan.roleIds);assertEquals(role!!.id,board.persons.single().state!!.currentRoleId)};run()
            actions.advanceActionStatus(a,ActionStatus.ACTIVE){assertTrue(it is ActionChangeResult.Applied)};run()
            assertEquals(7L,epoch.mutationEpoch);assertEquals(7,reports.size)
            assertTrue(reports.all{it==WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)})
            assertFalse(epoch.ramReuseEnabled)
        } finally{owner.invalidate();key.fill(0)}
    }
}
