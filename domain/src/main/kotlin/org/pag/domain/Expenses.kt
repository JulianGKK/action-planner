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
        val i = normalized(input)
        return bounded(i.title, 120) && i.totalCents > 0 && i.payments.size in 1..32 &&
            i.payments.all { bounded(it.payerLabel, 80) && it.amountCents > 0 } && sum(i.payments.map { it.amountCents }) == i.totalCents
    }
    fun mayRead(s: ActionStatus) = s in setOf(ActionStatus.PREPARATION, ActionStatus.ACTIVE, ActionStatus.COMPLETED)
    fun mayWrite(s: ActionStatus) = mayRead(s)
    fun mayDelete(s: ActionStatus) = mayRead(s)
    val order = compareBy<Expense> { it.createdAt }.thenBy { it.id }
}
/** EUR only. No floating point, grouping, rounding or ambiguous partial input. */
object EuroMoney {
    fun parse(text: String): Long? {
        val s = text.trim()
        if (!s.matches(Regex("[0-9]+([,.][0-9]{1,2})?"))) return null
        val parts = s.split(',', '.')
        return try {
            val euros = parts[0].toLong()
            val cents = if (parts.size == 1) 0L else parts[1].padEnd(2, '0').toLong()
            Math.addExact(Math.multiplyExact(euros, 100), cents).takeIf { it > 0 }
        } catch (_: NumberFormatException) { null } catch (_: ArithmeticException) { null }
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
