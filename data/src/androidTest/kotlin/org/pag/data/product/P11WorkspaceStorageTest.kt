package org.pag.data.product

import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** Real SQLCipher/Room, synthetic isolated store. Never touches product data. */
class P11WorkspaceStorageTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.also{check(it.packageName=="org.pag.data.unlockprobe")}
    private fun read(f:P11SyntheticFixture,action:String=f.activeId,status:ActionStatus=ActionStatus.ACTIVE,
        parts:Set<WorkspacePart> = WorkspacePart.entries.toSet()):WorkspaceResult {
        var result:WorkspaceResult?=null
        f.workspace.read(WorkspaceReadRequest(action,status,1,parts)){result=it}
        return requireNotNull(result)
    }
    @Test fun batchEqualsExistingRepositoriesAndOnlyRequestedData() {
        P11SyntheticFixture(app).use{f->
            val batch=(read(f) as WorkspaceResult.Value).value
            f.operations.board(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.operations)}
            f.tasks.board(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.tasks)}
            f.protocol.summary(f.activeId){assertEquals((it as ProtocolResult.Value).value,batch.protocol)}
            f.material.summary(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.material)}
            f.expenses.summary(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.expenses)}
            f.memoryReports.summary(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.memory)}
            f.memoryReports.list(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.memoryHeaders)}
            f.documents.summary(f.activeId){assertEquals((it as DocumentResult.Value).value,batch.documents)}
            f.protocol.listTimeline(f.activeId){assertEquals((it as ProtocolResult.Value).value,batch.protocolTimeline)}
            f.material.board(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.materialBoard)}
            f.expenses.board(f.activeId){assertEquals((it as OperationsResult.Value).value,batch.expenseBoard)}
            val minimal=(read(f,parts=emptySet()) as WorkspaceResult.Value).value
            assertNull(minimal.tasks);assertNull(minimal.protocol);assertNull(minimal.material)
            assertNull(minimal.expenses);assertNull(minimal.memory);assertNull(minimal.documents);assertNull(minimal.memoryHeaders)
            assertNull(minimal.protocolTimeline);assertNull(minimal.materialBoard);assertNull(minimal.expenseBoard)
            assertEquals(batch.operations,minimal.operations)
        }
    }
    @Test fun statusActionGenerationAndRevokeAreEnforced() {
        P11SyntheticFixture(app).use{f->
            assertEquals(WorkspaceResult.Rejected(WorkspaceFailure.NOT_FOUND),read(f,action="missing"))
            assertEquals(WorkspaceResult.Rejected(WorkspaceFailure.STALE),read(f,status=ActionStatus.PREPARATION))
            assertTrue(read(f,f.preparationId,ActionStatus.PREPARATION,emptySet()) is WorkspaceResult.Value)
            assertTrue(read(f,f.completedId,ActionStatus.COMPLETED,emptySet()) is WorkspaceResult.Value)
            var publications=0
            f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,0,emptySet())){publications++}
            f.revoke();f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet())){publications++}
            assertEquals(0,publications)
        }
    }
    @Test fun queuedPublicationIsDiscardedAfterRevoke() {
        val queue=ArrayDeque<Runnable>()
        P11SyntheticFixture(app,main=Executor{queue.add(it)}).use{f->
            var publications=0
            f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet())){publications++}
            assertEquals(1,queue.size);f.revoke();queue.removeFirst().run();assertEquals(0,publications)
        }
    }
    @Test fun revokedQueuedWorkerDoesNotOpenOrPublish() {
        val queue=ArrayDeque<Runnable>()
        P11SyntheticFixture(app,worker=Executor{queue.add(it)}).use{f->
            var publications=0
            f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet())){publications++}
            f.revoke();queue.removeFirst().run();assertEquals(0,publications)
        }
    }
    @Test fun warmBatchDescriptorsStayBounded() {
        P11SyntheticFixture(app).use{f->
            repeat(2){assertTrue(read(f) is WorkspaceResult.Value)}
            fun fds()=java.io.File("/proc/self/fd").list()!!.size
            fun pss():Int {val memory=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory);return memory.totalPss}
            val before=fds();val pssBefore=pss()
            repeat(20){assertTrue(read(f) is WorkspaceResult.Value);assertTrue(fds()<=before+2)}
            val after=fds();assertTrue(after<=before+2)
            InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                putString("stream","P11_RESOURCE,FD_BEFORE,$before\nP11_RESOURCE,FD_AFTER,$after\nP11_RESOURCE,PSS_KIB_BEFORE,$pssBefore\nP11_RESOURCE,PSS_KIB_AFTER,${pss()}\n")
            })
        }
    }

    @Test fun draftAndDeletionPendingAreDeniedWithoutPartialData() {
        P11SyntheticFixture(app).use{f->for(status in listOf(ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)) {
            f.setSyntheticStatus(status)
            assertEquals(WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED),read(f,status=status))
        }}
    }

    @Test fun explicitMemoryHeadersAreFreshAndForbiddenInPreparation() {
        P11SyntheticFixture(app).use{f->
            val parts=setOf(WorkspacePart.MEMORY_HEADERS)
            assertEquals(WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED),read(f,f.preparationId,ActionStatus.PREPARATION,parts))
            val before=(read(f,parts=parts) as WorkspaceResult.Value).value
            assertTrue(before.memoryHeaders!!.isEmpty());assertNull(before.tasks)
            f.seedSyntheticMemoryReports(20)
            val after=(read(f,parts=parts) as WorkspaceResult.Value).value
            assertEquals(20,after.memoryHeaders!!.size);assertTrue(before.memoryHeaders!!.isEmpty())
            assertNull(after.tasks);assertNull(after.memory);assertNull(after.documents)
        }
    }

    @Test fun expectedOpeningActionMustMatchEveryPersistedField() {
        P11SyntheticFixture(app).use{f->
            var action:Action?=null;f.actions.getAction(f.activeId){action=it}
            val expected=action!!
            val stale=listOf(expected.copy(title="changed"),expected.copy(description="changed"),expected.copy(startAt=0),expected.copy(createdAt=2))
            for(value in stale) {
                var result:WorkspaceResult?=null
                f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet(),value)){result=it}
                assertEquals(WorkspaceResult.Rejected(WorkspaceFailure.STALE),result)
            }
            var valid:WorkspaceResult?=null
            f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet(),expected)){valid=it}
            assertTrue(valid is WorkspaceResult.Value)
        }
    }

    @Test fun selectedPayloadsAreExplicitAndKeepExistingPhasePermissions() {
        P11SyntheticFixture(app).use{f->
            val timeline=(read(f,parts=setOf(WorkspacePart.PROTOCOL_TIMELINE)) as WorkspaceResult.Value).value
            assertEquals(10,timeline.protocolTimeline!!.size);assertNull(timeline.materialBoard);assertNull(timeline.expenseBoard);assertNull(timeline.tasks)
            val material=(read(f,parts=setOf(WorkspacePart.MATERIAL_BOARD)) as WorkspaceResult.Value).value
            assertEquals(20,material.materialBoard!!.items.size);assertNull(material.protocolTimeline);assertNull(material.expenseBoard)
            val costs=(read(f,parts=setOf(WorkspacePart.EXPENSE_BOARD)) as WorkspaceResult.Value).value
            assertEquals(20,costs.expenseBoard!!.expenses.size);assertNull(costs.protocolTimeline);assertNull(costs.materialBoard)
            assertEquals(WorkspaceResult.Rejected(WorkspaceFailure.NOT_ALLOWED),read(f,f.preparationId,ActionStatus.PREPARATION,setOf(WorkspacePart.PROTOCOL_TIMELINE)))
            assertTrue(read(f,f.preparationId,ActionStatus.PREPARATION,setOf(WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD)) is WorkspaceResult.Value)
            assertTrue(read(f,f.completedId,ActionStatus.COMPLETED,setOf(WorkspacePart.PROTOCOL_TIMELINE,WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD)) is WorkspaceResult.Value)
        }
    }
    @Test fun queuedSelectedPayloadCannotPublishAfterGenerationRevoke() {
        val queue=ArrayDeque<Runnable>()
        P11SyntheticFixture(app,main=Executor{queue.add(it)}).use{f->
            var publications=0
            f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,setOf(WorkspacePart.PROTOCOL_TIMELINE,WorkspacePart.MATERIAL_BOARD,WorkspacePart.EXPENSE_BOARD))){publications++}
            assertEquals(1,queue.size);f.revoke();queue.removeFirst().run();assertEquals(0,publications)
        }
    }
    @Test fun cancelledQueuedReadNeverInspectsInventoryBorrowsKeyOrPublishes() {
        val queue=ArrayDeque<Runnable>();var leases=0;var blocked=0;var publications=0
        val c=object:StorageContext {
            override val scope=AccessScope.READY;override val generation=1L
            override fun check(point:AccessPoint)=true
            override fun publish(result:()->Unit):Boolean {result();return true}
        }
        val access=object:StorageAccess{override fun acquire(scope:AccessScope)=c}
        val root=java.io.File(app.noBackupFilesDir,"p11-cancel-${java.util.UUID.randomUUID()}")
        // Deliberately absent inventory: touching it would fail closed and increment blocked.
        val inventory=ProductInventory(root,{false},{false})
        val repo=ProductWorkspaceRepository(app,access,inventory,{leases++;null},Executor{queue.add(it)},Executor{it.run()},{blocked++})
        val ticket=WorkspaceReadTicket()
        repo.read(WorkspaceReadRequest("synthetic",ActionStatus.ACTIVE,1,emptySet(),ticket=ticket)){publications++}
        ticket.cancel();queue.removeFirst().run()
        assertEquals(0,leases);assertEquals(0,blocked);assertEquals(0,publications);assertFalse(root.exists())
    }
    @Test fun cancelledQueuedPublicationDoesNotRevokeSessionOrPreventFreshRead() {
        val queue=ArrayDeque<Runnable>()
        P11SyntheticFixture(app,main=Executor{queue.add(it)}).use{f->
            var publications=0;val ticket=WorkspaceReadTicket()
            f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet(),ticket=ticket)){publications++}
            assertEquals(1,queue.size);ticket.cancel();queue.removeFirst().run();assertEquals(0,publications)
            f.workspace.read(WorkspaceReadRequest(f.activeId,ActionStatus.ACTIVE,1,emptySet())){publications++}
            assertEquals(1,queue.size);queue.removeFirst().run();assertEquals(1,publications)
        }
    }

}
