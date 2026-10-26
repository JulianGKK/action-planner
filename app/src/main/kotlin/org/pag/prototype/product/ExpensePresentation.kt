package org.pag.prototype.product

import org.pag.domain.*
import java.util.Collections

internal class ExpenseUiRow(val source:Expense) {
    val money=EuroMoney.format(source.totalCents)
    val semantics="${source.title} bearbeiten"
    val payments:List<String> = Collections.unmodifiableList(source.payments.map{"${it.payerLabel} · ${EuroMoney.format(it.amountCents)}"})
}
/** Immutable admitted member of the existing ExpenseModel; no registry/budget of its own. */
internal class ExpensePresentation(val source:ExpenseBoard) {
    val rows:List<ExpenseUiRow> = Collections.unmodifiableList(source.expenses.map(::ExpenseUiRow))
    val total:Long?=run{var sum=0L;try{for(e in source.expenses)sum=Math.addExact(sum,e.totalCents);sum}catch(_:ArithmeticException){null}}
    val summary=total?.let{"Gesamt: ${EuroMoney.format(it)}"}?:"Gesamtsumme nicht darstellbar."
    val count="${source.expenses.size} Einträge"
    fun row(value:Expense)=rows.find{it.source===value}
}
