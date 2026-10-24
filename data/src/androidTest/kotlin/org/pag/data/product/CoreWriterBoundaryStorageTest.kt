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
            ops.planRoles(a,checkNotNull(person).id,ExpectedPersonRolePlan(a,checkNotNull(person).id,PersonRolePlan(emptySet(),null, editRevision = 0)),PersonRolePlan(selected,role!!.id, editRevision = 0)){assertTrue(it is OperationsResult.Value)}
            selected.clear() // queued work must use its frozen plan, not this mutable source
            run()
            actions.advanceActionStatus(a,ActionStatus.PREPARATION){assertTrue(it is ActionChangeResult.Applied)};run()
            ops.board(a){r->val board=(r as OperationsResult.Value).value;assertEquals(setOf(role!!.id),board.persons.single().plan.roleIds);assertEquals(role!!.id,board.persons.single().state!!.currentRoleId)};run()
            val coreReports=reports.toList() // six core writes before ACTIVE -> COMPLETED
            // Same runtime-style port, real Room/SQLCipher repository; OFFLINE preparation only.
            val material=ProductMaterialRepository(app,access,inventory,{owner},worker,main,blocked,mutations=sink)
            var item:MaterialItem?=null;var materialCallbacks=0
            material.create(a,MaterialInput(" Synthetic material ",responsiblePersonId=person!!.id)){
                materialCallbacks++;item=(it as OperationsResult.Value).value
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),reports.last())
            }
            assertEquals(7L,epoch.mutationEpoch);assertEquals(0,materialCallbacks);run()
            val original=checkNotNull(item)
            material.edit(a,original,original.input().copy(name="Changed")){materialCallbacks++;item=(it as OperationsResult.Value).value};run()
            material.delete(a,original){materialCallbacks++;assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),it)
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),reports.last())};run()
            material.board(a){assertEquals(listOf(checkNotNull(item)),(it as OperationsResult.Value).value.items)};run()
            material.delete(a,checkNotNull(item)){materialCallbacks++;assertEquals(OperationsResult.Value(Unit),it)};run()
            material.summary(a){assertEquals(MaterialSummary(0),(it as OperationsResult.Value).value)};run()
            assertEquals(4,materialCallbacks);assertEquals(10L,epoch.mutationEpoch);assertEquals(10,reports.size)
            // Expense parent + ordered payments, same injected port; prepared OFFLINE ONLY.
            val expenses=ProductExpenseRepository(app,access,inventory,{owner},worker,main,blocked,mutations=sink)
            val submitted=mutableListOf(ExpensePaymentInput("Synthetic A",10),ExpensePaymentInput("Synthetic B",20))
            var expense:Expense?=null;var expenseCallbacks=0
            expenses.create(a,ExpenseInput("Synthetic expense",30,submitted)){
                expenseCallbacks++;expense=(it as OperationsResult.Value).value
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),reports.last())
            }
            submitted.clear();assertEquals(11L,epoch.mutationEpoch);assertEquals(0,expenseCallbacks);run()
            val oldExpense=checkNotNull(expense)
            assertEquals(2,oldExpense.payments.size)
            expenses.edit(a,oldExpense,ExpenseInput("Changed",6,List(3){ExpensePaymentInput("Synthetic $it",2)})){
                expenseCallbacks++;expense=(it as OperationsResult.Value).value
            };run()
            expenses.delete(a,oldExpense){expenseCallbacks++;assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),it)
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),reports.last())};run()
            expenses.board(a){assertEquals(listOf(checkNotNull(expense)),(it as OperationsResult.Value).value.expenses)};run()
            expenses.delete(a,checkNotNull(expense)){expenseCallbacks++;assertEquals(OperationsResult.Value(Unit),it)};run()
            expenses.summary(a){assertEquals(ExpenseSummary(0,0),(it as OperationsResult.Value).value)};run()
            assertEquals(4,expenseCallbacks)
            // Protocol values never enter a retained ACTIVE core; only this isolated fixture scope.
            val protocol=ProductProtocolRepository(app,access,inventory,{owner},worker,main,blocked,mutations=sink)
            val affected=linkedSetOf(person!!.id)
            val identifiers=mutableListOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID,"Synthetic id"))
            var savedProtocol:ProtocolSaved?=null;var protocolDetail:ProtocolEntryDetail?=null;var protocolCallbacks=0
            val protocolInput=ProtocolInput(ProtocolClassification.KEY_EVENT,"Synthetic protocol",1,affectedPersonIds=affected,officialIdentifiers=identifiers)
            protocol.createEntry(a,ActionStatus.ACTIVE,protocolInput){protocolCallbacks++;savedProtocol=(it as ProtocolResult.Value).value
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),reports.last())}
            affected.clear();identifiers.clear();assertEquals(15L,epoch.mutationEpoch);assertEquals(0,protocolCallbacks);run()
            val entryId=checkNotNull(savedProtocol).entryId
            fun loadProtocol(){protocol.getEntry(a,entryId){protocolDetail=(it as ProtocolResult.Value).value};run()}
            loadProtocol();val firstProtocol=checkNotNull(protocolDetail)
            assertEquals(setOf(person!!.id),firstProtocol.current.revision.input.affectedPersonIds)
            assertEquals(1,firstProtocol.current.revision.input.officialIdentifiers.size)
            val initialExpected=ProtocolExpectedRevision(firstProtocol.entry,firstProtocol.current.revision)
            protocol.reviseEntry(a,ActionStatus.ACTIVE,initialExpected,ProtocolRevisionType.ADDENDUM,firstProtocol.current.revision.input.copy(title="Addendum"),false){protocolCallbacks++;assertTrue(it is ProtocolResult.Value)};run()
            loadProtocol();val revised=checkNotNull(protocolDetail)
            protocol.markMistaken(a,ActionStatus.ACTIVE,ProtocolExpectedRevision(revised.entry,revised.current.revision)){protocolCallbacks++;assertTrue(it is ProtocolResult.Value)};run()
            protocol.markMistaken(a,ActionStatus.ACTIVE,initialExpected){protocolCallbacks++;assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),it)
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),reports.last())};run()
            loadProtocol();assertEquals(3,checkNotNull(protocolDetail).revisions.size)
            assertEquals(firstProtocol.current,checkNotNull(protocolDetail).revisions.first());assertEquals(4,protocolCallbacks)
            // MemoryReport remains short-lived, sensitive fixture data; no retained ACTIVE payload.
            val memory=ProductMemoryReportRepository(app,access,inventory,{owner},worker,main,blocked,mutations=sink)
            val firstAnswers=linkedMapOf<String,MemoryAnswerValue>("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("Synthetic narrative"),"A_ALLEGATION" to MemoryAnswerValue.Option("UNKNOWN"))
            var report:MemoryReportHeader?=null;var memoryCallbacks=0
            memory.create(a,"Synthetic author",MemorySectionSave(0,firstAnswers)){
                memoryCallbacks++;report=(it as OperationsResult.Value).value
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE),reports.last())
            }
            firstAnswers.clear();assertEquals(19L,epoch.mutationEpoch);assertEquals(0,memoryCallbacks);run()
            val multiKeys=mutableListOf("AFTER_REQUEST","SELF_LEFT")
            memory.saveSection(a,checkNotNull(report),MemorySectionSave(1,mapOf("B_ENDING_MODE" to MemoryAnswerValue.Multi(multiKeys)))){
                memoryCallbacks++;report=(it as OperationsResult.Value).value
            }
            multiKeys.clear();run()
            for(n in 2..16){
                memory.saveSection(a,checkNotNull(report),MemorySectionSave(n,emptyMap())){memoryCallbacks++;report=(it as OperationsResult.Value).value};run()
            }
            memory.complete(a,checkNotNull(report)){memoryCallbacks++;report=(it as OperationsResult.Value).value};run()
            val beforeAddendum=checkNotNull(report)
            memory.addAddendum(a,beforeAddendum,"Synthetic addition"){memoryCallbacks++;report=(it as OperationsResult.Value).value};run()
            memory.addAddendum(a,beforeAddendum,"Must remain stale"){memoryCallbacks++;assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),it)
                assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE),reports.last())};run()
            memory.detail(a,checkNotNull(report).id){
                val detail=(it as OperationsResult.Value).value
                assertEquals(18L,detail.header.revision);assertEquals(MemoryReportStatus.COMPLETED,detail.header.status)
                assertEquals(MemoryAnswerValue.Text("Synthetic narrative"),detail.answers["A_FREE_NARRATIVE"])
                assertEquals(MemoryAnswerValue.Multi(listOf("SELF_LEFT","AFTER_REQUEST")),detail.answers["B_ENDING_MODE"])
                assertEquals(listOf("Synthetic addition"),detail.addenda.map{it.content})
            };run()
            assertEquals(20,memoryCallbacks)
            actions.advanceActionStatus(a,ActionStatus.ACTIVE){assertTrue(it is ActionChangeResult.Applied)};run()
            val completedCoreReports=coreReports+reports.last()
            assertEquals(7,completedCoreReports.size)
            assertTrue(completedCoreReports.all{it==WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)})
            assertEquals(39L,epoch.mutationEpoch);assertEquals(39,reports.size)
            assertEquals(35,reports.count{it==WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)})
            assertFalse(epoch.ramReuseEnabled)
        } finally{owner.invalidate();key.fill(0)}
    }
}
