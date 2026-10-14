package org.pag.data.product
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class ExpenseProjectionTest {
    @Test fun generatedSummaryReadsOnlyCountAndParentTotal() {
        val generated=listOf(File("build/generated/ksp/debug/kotlin/org/pag/data/product/ExpenseDao_Impl.kt"),File("data/build/generated/ksp/debug/kotlin/org/pag/data/product/ExpenseDao_Impl.kt")).first{it.isFile}.readText()
        val sql=generated.substringAfter("override suspend fun summary(").substringBefore("return performSuspending")
        assertTrue(sql.contains("SELECT COUNT(*) AS count, COALESCE(SUM(totalCents),0) AS totalCents FROM expenses WHERE actionId=?"))
        for(s in listOf("title","payerLabel","expense_payments","persons","sensitive","leadership","SELECT *"))assertFalse(s,sql.contains(s))
    }
}
