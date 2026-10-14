package org.pag.data.product
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
class ExpenseRulesTest {
    private fun input(title: String = "Zugtickets", cents: Long = 12000, payments: List<ExpensePaymentInput> = listOf(ExpensePaymentInput("Meise",5000),ExpensePaymentInput("Motte",7000))) = ExpenseInput(title,cents,payments)
    @Test fun normalizationAndRequiredTitle() { assertEquals("Zugtickets",ExpenseRules.normalized(input(" Zugtickets ")).title);assertFalse(ExpenseRules.valid(input(" ")));assertTrue(ExpenseRules.valid(input())) }
    @Test fun unicodeCodepointsNotUtf16() { assertTrue(ExpenseRules.valid(input("🦋".repeat(120))));assertFalse(ExpenseRules.valid(input("🦋".repeat(121)))) }
    @Test fun payerTrimRequiredAndBounded() { val p=ExpensePaymentInput(" 🦋 ",12000);assertEquals("🦋",ExpenseRules.normalized(input(payments=listOf(p))).payments.single().payerLabel);for(s in listOf(" ","🦋".repeat(81)))assertFalse(ExpenseRules.valid(input(payments=listOf(p.copy(payerLabel=s)))));assertTrue(ExpenseRules.valid(input(payments=listOf(p.copy(payerLabel="🦋".repeat(80)))))) }
    @Test fun paymentCountsAndDuplicates() { for(n in listOf(0,1,32,33))assertEquals(n in 1..32,ExpenseRules.valid(input(cents=n.toLong(),payments=List(n){ExpensePaymentInput("same",1)}))) }
    @Test fun positiveAmountsAndExactSum() { for(t in listOf(-1L,0L,11999L,12001L))assertFalse(ExpenseRules.valid(input(cents=t)));for(v in listOf(0L,-1L))assertFalse(ExpenseRules.valid(input(payments=listOf(ExpensePaymentInput("x",v)))));assertTrue(ExpenseRules.valid(input())) }
    @Test fun overflowDoesNotWrap() { assertNull(ExpenseRules.sum(listOf(Long.MAX_VALUE,1)));assertFalse(ExpenseRules.valid(input(cents=Long.MAX_VALUE,payments=listOf(ExpensePaymentInput("a",Long.MAX_VALUE),ExpensePaymentInput("b",1)))));assertTrue(ExpenseRules.valid(input(cents=Long.MAX_VALUE,payments=listOf(ExpensePaymentInput("a",Long.MAX_VALUE))))) }
    @Test fun lifecycleIsExpenseOnlyException() { for(s in ActionStatus.entries){val yes=s in setOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED);assertEquals(yes,ExpenseRules.mayRead(s));assertEquals(yes,ExpenseRules.mayWrite(s));assertEquals(yes,ExpenseRules.mayDelete(s))};assertFalse(ActionLifecycle.mayEdit(ActionStatus.COMPLETED));assertFalse(TaskRules.mayWrite(ActionStatus.COMPLETED));assertFalse(MaterialRules.mayWrite(ActionStatus.COMPLETED));assertFalse(OperationalRules.mayUpdateSnapshot(ActionStatus.COMPLETED)) }
    @Test fun deterministicOrderAndPaymentInputOrder() { val a=Expense("a","x","a",12000,listOf(ExpensePayment("Meise",5000),ExpensePayment("Motte",7000)),1);assertEquals(listOf("old","a","z"),listOf(a.copy(id="z"),a,a.copy(id="old",createdAt=0)).sortedWith(ExpenseRules.order).map{it.id});assertEquals(listOf("Meise","Motte"),a.input().payments.map{it.payerLabel}) }
}
