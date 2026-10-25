package org.pag.domain

data class ExpensePaymentInput(val payerLabel: String, val amountCents: Long)
data class ExpenseInput(val title: String, val totalCents: Long, val payments: List<ExpensePaymentInput>)
data class ExpensePayment(val payerLabel: String, val amountCents: Long)
data class Expense(val id: String, val actionId: String, val title: String, val totalCents: Long,
    val payments: List<ExpensePayment>, val createdAt: Long) {
    fun input() = ExpenseInput(title, totalCents, payments.map { ExpensePaymentInput(it.payerLabel, it.amountCents) })
}
data class ExpenseSummary(val count: Int, val totalCents: Long)
data class ExpenseBoard(val actionId: String, val expenses: List<Expense>)
object ExpenseRules {
    fun normalized(i: ExpenseInput) = i.copy(title = i.title.trim(), payments = i.payments.map { it.copy(payerLabel = it.payerLabel.trim()) })
    fun sum(values: List<Long>): Long? = try { values.fold(0L) { a, b -> Math.addExact(a, b) } } catch (_: ArithmeticException) { null }
    private fun bounded(s: String, max: Int) = s.isNotEmpty() && Character.codePointCount(s, 0, s.length) <= max
    fun valid(input: ExpenseInput): Boolean {
        if(trimmedCodePoints(input.title) !in 1..120 || input.totalCents<=0 || input.payments.size !in 1..32)return false
        var sum=0L
        for(p in input.payments){
            if(trimmedCodePoints(p.payerLabel) !in 1..80 || p.amountCents<=0)return false
            sum=try{Math.addExact(sum,p.amountCents)}catch(_:ArithmeticException){return false}
        }
        return sum==input.totalCents
    }
    fun mayRead(s: ActionStatus) = s in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)
    fun mayWrite(s: ActionStatus) = mayRead(s)
    fun mayDelete(s: ActionStatus) = mayRead(s)
    val order = compareBy<Expense> { it.createdAt }.thenBy { it.id }
}
/** EUR only. No floating point, grouping, rounding or ambiguous partial input. */
object EuroMoney {
    fun parse(text: String): Long? {
        var start=0;var end=text.length
        while(start<end && text[start].isWhitespace())start++
        while(end>start && text[end-1].isWhitespace())end--
        if(start==end)return null
        var euros=0L;var cents=0L;var decimals=-1;var digits=0
        try {
            for(i in start until end){val c=text[i]
                if(c in '0'..'9'){
                    val n=(c-'0').toLong()
                    if(decimals<0){euros=Math.addExact(Math.multiplyExact(euros,10),n);digits++}
                    else {if(decimals==2)return null;cents=cents*10+n;decimals++}
                }else if((c==',' || c=='.') && decimals<0 && digits>0)decimals=0 else return null
            }
            if(digits==0 || decimals==0)return null
            if(decimals==1)cents*=10
            return Math.addExact(Math.multiplyExact(euros,100),cents).takeIf{it>0}
        }catch(_:ArithmeticException){return null}
    }
    fun input(cents: Long): String { require(cents >= 0); return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}" }
    fun format(cents: Long) = "${input(cents)} €"
}
/** SENSITIVE, exclusively action-local; payerLabel is independent text, never a Person FK. */
interface ExpenseRepository {
    fun summary(actionId: String, publish: (OperationsResult<ExpenseSummary>) -> Unit)
    fun board(actionId: String, publish: (OperationsResult<ExpenseBoard>) -> Unit)
    fun create(actionId: String, input: ExpenseInput, publish: (OperationsResult<Expense>) -> Unit)
    fun edit(actionId: String, expected: Expense, input: ExpenseInput, publish: (OperationsResult<Expense>) -> Unit)
    fun delete(actionId: String, expected: Expense, publish: (OperationsResult<Unit>) -> Unit)
}
