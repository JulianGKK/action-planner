package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

private typealias ExpenseHarness = CoreWriterIntegrationTest.H

/** Real SQLite/schema 11, production ExpenseStore, outer transaction and common boundary.
 * Trusted authority and resource-end fault simulations; never a native Room/SQLCipher proof. */
class ExpenseWriterIntegrationTest {
    private fun input(title:String="Synthetic expense")=ExpenseInput(title,30,
        listOf(ExpensePaymentInput("Synthetic payer A",10),ExpensePaymentInput("Synthetic payer B",20)))
    private fun <T> value(r:OperationsResult<T>)=(r as OperationsResult.Value).value
    private fun <T> ExpenseHarness.writeExpense(work:suspend(ExpenseStore)->OperationsResult<T>)=
        write<OperationsResult<T>>(WorkspaceWriterFamily.EXPENSE,{it is OperationsResult.Rejected},
            {OperationsResult.Rejected(OperationsFailure.STALE)}){work(ExpenseStore(data,it){++time})}
    private fun ExpenseHarness.queueExpense(publish:(OperationsResult<*>)->Unit={},work:suspend(ExpenseStore)->OperationsResult<*>)=
        queue<OperationsResult<*>>(WorkspaceWriterFamily.EXPENSE,{it is OperationsResult.Rejected},
            {OperationsResult.Rejected(OperationsFailure.STALE)},publish){work(ExpenseStore(data,it){++time})}
    private fun ExpenseHarness.create(i:ExpenseInput=input()):Expense {
        val submitted=freezeExpenseInput(i)
        return value(writeExpense{it.create("a",submitted)})
    }
    private fun ExpenseHarness.edit(old:Expense,i:ExpenseInput):OperationsResult<Expense> {
        val expected=freezeExpenseExpected(old);val submitted=freezeExpenseInput(i)
        return writeExpense{it.edit("a",expected,submitted)}
    }
    private fun ExpenseHarness.delete(old:Expense):OperationsResult<Unit> {
        val expected=freezeExpenseExpected(old)
        return writeExpense{it.delete("a",expected)}
    }
    private fun ExpenseHarness.board()=runBlocking{value(ExpenseStore(data,context){0}.board("a"))}
    private fun ExpenseHarness.snapshot()=runBlocking{data.expenses.list("a").map{it to data.expenses.payments("a",it.id)}}
    private val committed=WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)
    private val rolledBack=WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)

    @Test fun createEditDeleteAndExactSummaryKeepAllPaymentsAtomicAcrossAllowedPhases() {
        for(status in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED))ExpenseHarness().use{h->
            h.exec("UPDATE actions SET status='${status.name}',completedAt=${if(status==ActionStatus.COMPLETED)1 else "NULL"} WHERE id='a'")
            val a=h.create(input(" Synthetic 🦋 expense "))
            assertEquals("Synthetic 🦋 expense",a.title);assertEquals(30L,a.totalCents);assertEquals(2,a.payments.size)
            val b=value(h.edit(a,ExpenseInput("Changed",6,List(3){ExpensePaymentInput("Synthetic payer $it",2)})))
            assertEquals(a.createdAt,b.createdAt);assertEquals(listOf(b),h.board().expenses)
            assertEquals(listOf(0,1,2),h.snapshot().single().second.map{it.position})
            val epoch=h.epoch.mutationEpoch
            runBlocking{assertEquals(ExpenseSummary(1,6),value(ExpenseStore(h.data,h.context){0}.summary("a")))}
            assertEquals(epoch,h.epoch.mutationEpoch)
            assertEquals(OperationsResult.Value(Unit),h.delete(b));assertEquals(0L,h.count("expense_payments"))
            assertEquals(0L,h.count("expenses"));assertEquals(listOf(committed,committed,committed),h.completions)
            assertEquals(3L,h.epoch.mutationEpoch);assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun fullExpectedCasIncludesParentAllPaymentsOrderAndCount()=ExpenseHarness().use{h->
        val a=h.create();val original=h.snapshot();var writes=0
        h.db.beforePrepare={if(it.startsWith("UPDATE expenses") || it.startsWith("DELETE FROM expense"))writes++}
        for(old in listOf(a.copy(actionId="b"),a.copy(title="Old"),a.copy(totalCents=31),a.copy(createdAt=999),
            a.copy(payments=a.payments.reversed()),a.copy(payments=a.payments.take(1)),
            a.copy(payments=listOf(a.payments[0].copy(payerLabel="Changed"),a.payments[1])),
            a.copy(payments=listOf(a.payments[0].copy(amountCents=11),a.payments[1].copy(amountCents=19))))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.edit(old,input()))
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.delete(old))
        }
        assertEquals(0,writes);assertEquals(original,h.snapshot());assertTrue(h.completions.drop(1).all{it==rolledBack})
    }
    @Test fun twoStaleEditorsAndOldDeleteKeepFirstCommittedPayments()=ExpenseHarness().use{h->
        val a=h.create();val b=value(h.edit(a,input().copy(payments=listOf(ExpensePaymentInput("New A",15),ExpensePaymentInput("New B",15)))))
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.edit(a,input("Stale editor")))
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.delete(a));assertEquals(listOf(b),h.board().expenses)
        assertTrue(h.edit(b,input("Fresh editor")) is OperationsResult.Value)
    }
    @Test fun invalidInputOverflowAndUnicodeLimitsRollbackWithoutChangingPayments()=ExpenseHarness().use{h->
        val a=h.create();val original=h.snapshot()
        for(i in listOf(input().copy(totalCents=31),input().copy(totalCents=0),input().copy(payments=emptyList()),
            ExpenseInput("X",1,listOf(ExpensePaymentInput("A",0),ExpensePaymentInput("B",1))),
            ExpenseInput("X",1,listOf(ExpensePaymentInput("A",-1),ExpensePaymentInput("B",2))),
            ExpenseInput("X",33,List(33){ExpensePaymentInput("A",1)}),
            ExpenseInput("X",Long.MAX_VALUE,listOf(ExpensePaymentInput("A",Long.MAX_VALUE),ExpensePaymentInput("B",1))),
            input(" "),input("🦋".repeat(121)),input().copy(payments=listOf(ExpensePaymentInput("🦋".repeat(81),30))),
            input().copy(payments=listOf(ExpensePaymentInput(" ",30))))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.writeExpense{it.create("a",i)})
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.edit(a,i))
        }
        assertEquals(original,h.snapshot());assertTrue(h.completions.drop(1).all{it==rolledBack})
        val boundary=h.create(ExpenseInput("🦋".repeat(120),32,List(32){ExpensePaymentInput("🦋".repeat(80),1)}))
        assertEquals(32,boundary.payments.size);assertEquals(2,h.board().expenses.size)
        assertEquals(123L,EuroMoney.parse("1,23"));assertNull(EuroMoney.parse("9223372036854775807"))
    }
    @Test fun draftDeletionPendingForeignMissingActionAndMissingExpenseAreIsolated()=ExpenseHarness().use{h->
        val a=h.create();val original=h.snapshot()
        for(status in listOf(ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)){
            h.exec("UPDATE actions SET status='${status.name}' WHERE id='a'")
            for(r in listOf(h.writeExpense{it.create("a",input())},h.edit(a,input()),h.delete(a)))
                assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),r)
        }
        h.exec("UPDATE actions SET status='PREPARATION' WHERE id='a'")
        for(action in listOf("b","missing")){
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.writeExpense{it.edit(action,a,input())})
            assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.writeExpense{it.delete(action,a)})
        }
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.writeExpense{it.create("missing",input())})
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.delete(a.copy(id="missing")))
        assertEquals(original,h.snapshot());assertEquals(0L,runBlocking{h.data.query("SELECT COUNT(*) FROM expenses WHERE actionId='b'"){it.step();it.getLong(0)}})
    }
    @Test fun everyParentChildWriteFailureRollsBackCompleteOldState() {
        for(mode in listOf("after-parent-insert","during-payment-insert","after-parent-update","after-clear","during-reinsert","after-parent-delete"))ExpenseHarness().use{h->
            val a=h.create();val original=h.snapshot();var payments=0;var injected=false
            h.data.afterChange={sql->
                val fail=when(mode){
                    "after-parent-insert"->sql.startsWith("INSERT INTO expenses ")
                    "after-parent-update"->sql.startsWith("UPDATE expenses ")
                    "after-clear"->sql.startsWith("DELETE FROM expense_payments ")
                    "after-parent-delete"->sql.startsWith("DELETE FROM expenses ")
                    else->sql.startsWith("INSERT INTO expense_payments ") && ++payments==2
                }
                if(fail){injected=true;error("SYNTHETIC_EXPENSE_SQL_FAILURE")}
            }
            var calls=0
            h.queueExpense({calls++}){when(mode){
                "after-parent-insert","during-payment-insert"->it.create("a",input("New"))
                "after-parent-delete"->it.delete("a",a)
                else->it.edit("a",a,ExpenseInput("Changed",6,List(3){ExpensePaymentInput("Changed $it",2)}))
            }}
            h.worker.drain();h.main.drain();assertTrue(mode,injected);assertEquals(mode,0,calls)
            h.data.afterChange={};assertEquals(mode,original,h.snapshot());assertEquals(2L,h.count("expense_payments"))
            assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.last().outcome);assertEquals(1,h.blocks)
        }
    }
    @Test fun createFailureLeavesNeitherParentNorPayments() {
        for(afterParent in listOf(true,false))ExpenseHarness().use{h->
            var payments=0;h.data.afterChange={sql->if((afterParent && sql.startsWith("INSERT INTO expenses ")) ||
                (!afterParent && sql.startsWith("INSERT INTO expense_payments ") && ++payments==2))error("SYNTHETIC_FAILURE")}
            var calls=0;h.queueExpense({calls++}){it.create("a",input())};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(0L,h.count("expenses"));assertEquals(0L,h.count("expense_payments"))
        }
    }
    @Test fun actualSqlTriggerFailureAfterFirstPaymentRollsBackParentAndChildren()=ExpenseHarness().use{h->
        val a=h.create();val original=h.snapshot()
        h.exec("CREATE TRIGGER synthetic_expense_failure BEFORE INSERT ON expense_payments WHEN NEW.position=1 BEGIN SELECT RAISE(ABORT,'SYNTHETIC_FAILURE'); END")
        for(create in listOf(true,false)){
            var calls=0;h.queueExpense({calls++}){if(create)it.create("a",input())else it.edit("a",a,input("Changed"))}
            h.worker.drain();h.main.drain();assertEquals(0,calls);assertEquals(original,h.snapshot())
            assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.last().outcome)
            // UNKNOWN fences this generation; only a new trusted READY generation resumes uncached writes.
            h.generation++
        }
    }
    @Test fun explicitRejectionNeedsConfirmedRollbackAndFailedRollbackStaysUnknown() {
        for(mode in listOf("normal","rollback","db-cleanup","key-cleanup"))ExpenseHarness().use{h->
            if(mode=="rollback")h.failRollback=true
            if(mode=="db-cleanup")h.dbCleanup={error("SYNTHETIC_CLEANUP")}
            if(mode=="key-cleanup")h.keyCleanup={error("SYNTHETIC_CLEANUP")}
            var calls=0;h.queueExpense({calls++;assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),it)}){
                value(it.create("a",input()));OperationsResult.Rejected(OperationsFailure.INVALID)
            }
            h.worker.drain();h.main.drain();assertEquals(if(mode=="normal")1 else 0,calls)
            assertEquals(if(mode=="rollback")1L else 0L,h.count("expenses"));assertEquals(if(mode=="rollback")2L else 0L,h.count("expense_payments"))
            assertEquals(if(mode=="rollback")WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.ROLLED_BACK,h.completions.single().outcome)
            assertEquals(if(mode=="normal")WorkspaceWriteCleanup.COMPLETE else WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
        }
    }
    @Test fun parentSqlTypesCannotBeHiddenByNativeStyleGetLongCoercion() {
        for(field in listOf("totalCents=30.5","totalCents='invalid-text'","createdAt=1.5","createdAt='invalid-text'"))ExpenseHarness().use{h->
            val a=h.create();h.exec("UPDATE expenses SET $field WHERE id='${a.id}'")
            var guard=false;h.db.beforePrepare={if(it.contains("typeof(totalCents)='integer'"))guard=true}
            assertThrows(ProductStorageFailure::class.java){h.board()};assertTrue("integerParent guard must run",guard)
            var calls=0;h.queueExpense({calls++}){it.edit("a",a,input())};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.last().outcome)
        }
    }
    @Test fun paymentTypesPositionsAndFullMaterializationRemainStrict() {
        for(change in listOf("amountCents=10.5","amountCents='invalid-text'","position=0.5","position='invalid-text'",
            "position=5","amountCents=0","amountCents=-1","amountCents=11","payerLabel=' '","payerLabel=' Synthetic '","payerLabel='${"x".repeat(81)}'"))ExpenseHarness().use{h->
            val a=h.create();h.exec("UPDATE expense_payments SET $change WHERE expenseId='${a.id}' AND position=0")
            var types=false;h.db.beforePrepare={if(it.contains("typeof(amountCents)!='integer'"))types=true}
            assertThrows(ProductStorageFailure::class.java){h.board()};assertTrue(types)
            var calls=0;h.queueExpense({calls++}){it.delete("a",a)};h.worker.drain();h.main.drain();assertEquals(0,calls)
            assertEquals(1L,h.count("expenses"));assertEquals(2L,h.count("expense_payments"))
        }
        for(mode in listOf("empty","many","overflow"))ExpenseHarness().use{h->
            val a=h.create()
            when(mode){"empty"->h.exec("DELETE FROM expense_payments");"many"->for(i in 2..32)h.exec("INSERT INTO expense_payments VALUES('a','${a.id}',$i,'Synthetic',1)");else->h.exec("UPDATE expense_payments SET amountCents=9223372036854775807")}
            assertThrows(ProductStorageFailure::class.java){h.board()}
        }
    }
    @Test fun integerPaymentPositionsMustNotWrapIntoAValidIntIndex() {
        for(position in listOf("4294967296","-4294967296"))ExpenseHarness().use{h->
            val a=h.create(ExpenseInput("Single",30,listOf(ExpensePaymentInput("Synthetic",30))))
            h.exec("UPDATE expense_payments SET position=$position WHERE expenseId='${a.id}'")
            assertThrows(ProductStorageFailure::class.java){h.board()}
        }
    }
    @Test fun summaryRejectsCorruptTotalsAndExactSqlSumOverflow() {
        for(change in listOf("totalCents=30.5","totalCents='invalid-text'","totalCents=0","totalCents=-1"))ExpenseHarness().use{h->
            val a=h.create();h.exec("UPDATE expenses SET $change WHERE id='${a.id}'")
            assertThrows(ProductStorageFailure::class.java){runBlocking{ExpenseStore(h.data,h.context){0}.summary("a")}}
        }
        ExpenseHarness().use{h->
            h.create(ExpenseInput("Maximum",Long.MAX_VALUE,listOf(ExpensePaymentInput("Synthetic",Long.MAX_VALUE))))
            runBlocking{assertEquals(ExpenseSummary(1,Long.MAX_VALUE),value(ExpenseStore(h.data,h.context){0}.summary("a")))}
            h.create(ExpenseInput("One",1,listOf(ExpensePaymentInput("Synthetic",1))))
            assertThrows(IllegalStateException::class.java){runBlocking{ExpenseStore(h.data,h.context){0}.summary("a")}}
        }
    }
    @Test fun schema11CompositeFkAndCascadeDoNotLeaveForeignOrOrphanPayments()=ExpenseHarness().use{h->
        val a=h.create()
        assertThrows(IllegalStateException::class.java){h.exec("INSERT INTO expense_payments VALUES('b','${a.id}',2,'Synthetic',1)")}
        assertEquals(2L,h.count("expense_payments"));assertEquals(OperationsResult.Value(Unit),h.delete(a));assertEquals(0L,h.count("expense_payments"))
        h.create();h.exec("DELETE FROM actions WHERE id='a'")
        assertEquals(0L,h.count("expenses"));assertEquals(0L,h.count("expense_payments"));assertEquals(1L,h.count("actions"))
    }
    @Test fun callerOwnedMutableListsReallyAliasWithoutFreezing() {
        val rows=mutableListOf(ExpensePaymentInput("Original",1));val i=ExpenseInput("X",1,rows)
        val oldRows=mutableListOf(ExpensePayment("Original",1));val old=Expense("e","a","X",1,oldRows,0)
        val queuedDraft=i;val queuedExpected=old
        rows[0]=ExpensePaymentInput("Changed",1);oldRows.clear()
        assertEquals("Changed",queuedDraft.payments.single().payerLabel);assertTrue(queuedExpected.payments.isEmpty())
    }
    @Test fun queuedCreateUsesFrozenSubmittedPayments()=ExpenseHarness().use{h->
        val rows=mutableListOf(ExpensePaymentInput("Original",10),ExpensePaymentInput("Other",20));val i=ExpenseInput("X",30,rows)
        val submitted=freezeExpenseInput(i);var created:Expense?=null
        h.queueExpense({created=(it as OperationsResult.Value).value as Expense}){it.create("a",submitted)}
        rows.clear();h.worker.drain();h.main.drain()
        assertEquals(listOf(ExpensePayment("Original",10),ExpensePayment("Other",20)),created!!.payments)
        assertThrows(UnsupportedOperationException::class.java){(submitted.payments as MutableList).clear()}
        Unit
    }
    @Test fun queuedEditAndDeleteUseFrozenExpectedAndDraftLists() {
        for(delete in listOf(true,false))ExpenseHarness().use{h->
            val a=h.create();val expectedRows=a.payments.toMutableList();val draftRows=input("Changed").payments.toMutableList()
            val original=freezeExpenseExpected(a.copy(payments=expectedRows));val submitted=freezeExpenseInput(ExpenseInput("Changed",30,draftRows))
            var calls=0;h.queueExpense({calls++}){if(delete)it.delete("a",original)else it.edit("a",original,submitted)}
            expectedRows.clear();draftRows.clear();h.worker.drain();h.main.drain();assertEquals(1,calls)
            if(delete)assertEquals(0L,h.count("expense_payments"))else{assertEquals(2,h.snapshot().single().second.size);assertEquals("Changed",h.board().expenses.single().title)}
            assertThrows(UnsupportedOperationException::class.java){(original.payments as MutableList).clear()}
        }
    }
    @Test fun aliasMutationCannotConvertStaleExpectedIntoCurrentSnapshot()=ExpenseHarness().use{h->
        val a=h.create();val b=value(h.edit(a,input().copy(payments=listOf(ExpensePaymentInput("Current",30)))))
        val rows=a.payments.toMutableList();val old=freezeExpenseExpected(a.copy(payments=rows));var result:OperationsResult<*>?=null
        h.queueExpense({result=it}){it.delete("a",old)}
        rows.clear();rows.addAll(b.payments);h.worker.drain();h.main.drain()
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),result);assertEquals(listOf(b),h.board().expenses)
    }
    @Test fun allExpenseWritesSeparateSqlOutcomeCleanupAndPublicationAcrossRaces() {
        for(operation in listOf("create","edit","delete"))for(mode in listOf("normal","initial","queued","during-write","commit","after-commit","publication","permission","generation","action","blocked","end-before","end-after","db-cleanup","key-cleanup"))ExpenseHarness().use{h->
            val a=h.create();val original=h.snapshot();h.completions.clear()
            when(mode){"initial"->h.ready=false;"blocked"->h.storageReady=false;"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;
                "db-cleanup"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};"key-cleanup"->h.keyCleanup={error("SYNTHETIC_CLEANUP")};
                "commit"->h.beforeEnd={h.ready=false};"after-commit"->h.afterEnd={h.ready=false};"publication"->h.beforePublication={h.ready=false}}
            if(mode=="during-write")h.data.afterChange={h.ready=false}
            var calls=0
            h.queueExpense({calls++}){when(operation){"create"->it.create("a",input("New"));"edit"->it.edit("a",a,ExpenseInput("Changed",6,List(3){ExpensePaymentInput("New $it",2)}));else->it.delete("a",a)}}
            if(mode=="queued")h.ready=false
            val worker=h.worker.jobs.firstOrNull();h.worker.drain();worker?.run()
            when(mode){"permission"->h.permission++;"generation"->h.generation++;"action"->h.action="b"}
            val main=h.main.jobs.firstOrNull();h.main.drain();main?.run()
            assertEquals("$operation/$mode",if(mode=="normal")1 else 0,calls)
            val committed=mode in listOf("normal","after-commit","publication","permission","generation","action","end-after","db-cleanup","key-cleanup")
            if(!committed)assertEquals("$operation/$mode",original,h.snapshot())
            else when(operation){
                "create"->{assertEquals(2L,h.count("expenses"));assertEquals(4L,h.count("expense_payments"))}
                "edit"->{assertEquals("Changed",h.snapshot().single().first.title);assertEquals(3,h.snapshot().single().second.size)}
                else->{assertEquals(0L,h.count("expenses"));assertEquals(0L,h.count("expense_payments"))}
            }
            if(mode=="initial")assertTrue(h.completions.isEmpty())
            else if(mode.startsWith("end"))assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
            else if(committed)assertEquals(WorkspaceWriteOutcome.COMMITTED,h.completions.single().outcome)
            if(mode.endsWith("cleanup"))assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
            if(mode=="normal")assertEquals(this.committed,h.completions.single())
            assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun allFourFamiliesSharePendingEpochAndCannotBypassExpenseOrEachOther() {
        val families=listOf(WorkspaceWriterFamily.EXPENSE,WorkspaceWriterFamily.MATERIAL,WorkspaceWriterFamily.TASK,WorkspaceWriterFamily.OPERATIONS)
        for(first in families)ExpenseHarness().use{h->
            h.queue<OperationsResult<*>>(first,{it is OperationsResult.Rejected},{null},{}){c->when(first){
                WorkspaceWriterFamily.EXPENSE->ExpenseStore(h.data,c){0}.create("a",input())
                WorkspaceWriterFamily.MATERIAL->MaterialStore(h.data,c){0}.create("a",MaterialInput("Synthetic"))
                WorkspaceWriterFamily.TASK->TaskStore(HostTaskData(h.db),c){0}.create("a",TaskInput("Synthetic"))
                else->OperationsStore(h.data,c){0}.role("a",null,"New role")
            }}
            for(other in families){
                var result:OperationsResult<Unit>?=null
                h.queue<OperationsResult<Unit>>(other,{it is OperationsResult.Rejected},{OperationsResult.Rejected(OperationsFailure.STALE)},{result=it}){error("No busy bypass")}
                assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),result)
            }
            assertEquals(1L,h.epoch.mutationEpoch);assertEquals(1,h.worker.jobs.size);assertTrue(h.completions.isEmpty())
            h.worker.drain();h.main.drain();assertEquals(listOf(committed),h.completions);assertEquals(1L,h.epoch.mutationEpoch)
            assertEquals(if(first==WorkspaceWriterFamily.EXPENSE)2L else 0L,h.count("expense_payments"));assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun duplicateWorkerAndMainCannotDuplicateParentPaymentsOrCallback()=ExpenseHarness().use{h->
        var calls=0;h.queueExpense({calls++;assertEquals(committed,h.completions.single())}){it.create("a",input())}
        val work=h.worker.jobs.removeFirst();work.run();work.run();assertEquals(0,calls)
        assertEquals(1L,h.count("expenses"));assertEquals(2L,h.count("expense_payments"))
        val publish=h.main.jobs.removeFirst();publish.run();publish.run()
        assertEquals(1,calls);assertEquals(1,h.completions.size);assertEquals(1L,h.epoch.mutationEpoch)
    }
    @Test fun lostOrFailedMainDispatchLeavesPendingWithoutRetry() {
        for(fail in listOf(true,false))ExpenseHarness().use{h->
            if(fail)h.main.onQueue={error("SYNTHETIC_DISPATCH")}
            var calls=0;h.queueExpense({calls++}){it.create("a",input())}
            if(fail)assertThrows(IllegalStateException::class.java){h.worker.drain()}else{h.worker.drain();h.main.jobs.clear()}
            assertEquals(0,calls);assertEquals(1L,h.count("expenses"));assertEquals(2L,h.count("expense_payments"));assertTrue(h.completions.isEmpty())
            var busy:OperationsResult<*>?=null;h.queueExpense({busy=it}){error("No automatic retry")}
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),busy);assertTrue(h.worker.jobs.isEmpty());assertEquals(1L,h.epoch.mutationEpoch)
        }
    }
    @Test fun failedWorkerDispatchIsNonexecutionWithoutSuccessOrFakeDbRollback()=ExpenseHarness().use{h->
        h.worker.onQueue={error("SYNTHETIC_DISPATCH")};var calls=0
        h.queueExpense({calls++}){it.create("a",input())}
        assertEquals(0,calls);assertEquals(0L,h.count("expenses"));assertEquals(0L,h.count("expense_payments"))
        assertEquals(listOf(rolledBack),h.completions) // existing nonexecution status, no DB transaction ran
        assertEquals(1,h.blocks)
    }
    @Test fun valueWithoutConfirmedOuterCommitCannotPublishSuccess()=ExpenseHarness().use{h->
        var calls=0;h.boundary.submit<OperationsResult<Unit>>(WorkspaceMutation(WorkspaceWriterFamily.EXPENSE,"a"),{calls++},
            {null},{it is OperationsResult.Value}){_,_->OperationsResult.Value(Unit)}
        h.worker.drain();h.main.drain();assertEquals(0,calls);assertEquals(0L,h.count("expenses"))
        assertEquals(WorkspaceWriteCompletion(WorkspaceWriteOutcome.UNKNOWN,WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED),h.completions.single())
    }
    @Test fun schema11ExpenseFullValueCasDoesNotDetectAba()=ExpenseHarness().use{h->
        val a=h.create();val b=value(h.edit(a,input("B")));val again=value(h.edit(b,a.input()))
        assertEquals(a,again);assertTrue(h.edit(a,input("Old editor after ABA")) is OperationsResult.Value)
    }
}
