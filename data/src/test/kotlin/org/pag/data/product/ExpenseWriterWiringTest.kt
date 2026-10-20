package org.pag.data.product

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Static composition guard; not a SQLite transaction or native cleanup proof. */
class ExpenseWriterWiringTest {
    private fun source(name:String)=listOf(File("src/main/kotlin/org/pag/data/product/$name.kt"),
        File("data/src/main/kotlin/org/pag/data/product/$name.kt")).first{it.isFile}.readText()
    @Test fun expenseMustFenceBeforeQueueAndUseConfirmedOuterTransaction() {
        val repository=source("ProductExpenseRepository")
        assertTrue("Expense needs the common pre-queue boundary",repository.contains("WorkspaceWriteBoundary("))
        assertTrue(repository.contains("WorkspaceWriterFamily.EXPENSE"))
        assertTrue(repository.contains("confirmedWorkspaceTransaction("))
        assertFalse(repository.contains("withWriteTransaction"))
    }
    @Test fun submittedListsAreFrozenBeforeCallingTheWritePath() {
        val repository=source("ProductExpenseRepository")
        for(method in listOf("create","edit","delete")) {
            val body=repository.substringAfter("override fun $method(").substringBefore("\n    }")
            assertTrue(body.contains("freezeExpense"));assertTrue(body.indexOf("freezeExpense")<body.indexOf("write(actionId"))
            if(method!="create")assertTrue(body.contains("freezeExpenseExpected(expected)"))
            if(method!="delete")assertTrue(body.contains("freezeExpenseInput(input)"))
        }
    }
    @Test fun runtimeMustPassTheSameMutationPortToExpense() {
        val expense=source("ProductRuntime").substringAfter("val expenses: ExpenseRepository").substringBefore("val documents")
        assertTrue("Expense needs the runtime's shared mutation port",expense.contains("mutations = workspaceMutations"))
    }
}
