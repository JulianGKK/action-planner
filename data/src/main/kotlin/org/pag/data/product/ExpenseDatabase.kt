package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

@Entity(tableName = "expenses", foreignKeys = [ForeignKey(entity = ActionRow::class,
    parentColumns = ["id"], childColumns = ["actionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["actionId", "id"], unique = true)])
internal data class ExpenseRow(@PrimaryKey val id: String, val actionId: String, val title: String,
    val totalCents: Long, val createdAt: Long)
@Entity(tableName = "expense_payments", primaryKeys = ["actionId", "expenseId", "position"],
    foreignKeys = [ForeignKey(entity = ExpenseRow::class, parentColumns = ["actionId", "id"],
        childColumns = ["actionId", "expenseId"], onDelete = ForeignKey.CASCADE)])
internal data class ExpensePaymentRow(val actionId: String, val expenseId: String, val position: Int,
    val payerLabel: String, val amountCents: Long)
internal data class ExpenseSummaryRow(val count: Int, val totalCents: Long)
@Dao internal interface ExpenseDao {
    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(totalCents),0) AS totalCents FROM expenses WHERE actionId=:actionId")
    suspend fun summary(actionId: String): ExpenseSummaryRow
    @Query("SELECT COUNT(*) FROM expenses WHERE actionId=:actionId AND (typeof(totalCents)!='integer' OR totalCents<=0)")
    suspend fun invalidTotals(actionId: String): Int
    @Query("SELECT COUNT(*) FROM expenses WHERE actionId=:actionId AND id=:id AND typeof(totalCents)='integer' AND typeof(createdAt)='integer'")
    suspend fun integerParent(actionId: String, id: String): Int
    @Query("SELECT COUNT(*) FROM expense_payments WHERE actionId=:actionId AND expenseId=:id AND (typeof(amountCents)!='integer' OR typeof(position)!='integer')")
    suspend fun invalidPaymentTypes(actionId: String, id: String): Int
    @Query("SELECT * FROM expenses WHERE actionId=:actionId ORDER BY createdAt,id") suspend fun list(actionId: String): List<ExpenseRow>
    @Query("SELECT * FROM expenses WHERE actionId=:actionId AND id=:id") suspend fun get(actionId: String, id: String): ExpenseRow?
    @Query("SELECT * FROM expense_payments WHERE actionId=:actionId AND expenseId=:id ORDER BY position") suspend fun payments(actionId: String, id: String): List<ExpensePaymentRow>
    @Insert suspend fun insert(row: ExpenseRow)
    @Insert suspend fun insertPayments(rows: List<ExpensePaymentRow>)
    @Update suspend fun update(row: ExpenseRow): Int
    @Query("DELETE FROM expense_payments WHERE actionId=:actionId AND expenseId=:id") suspend fun clearPayments(actionId: String, id: String)
    @Query("DELETE FROM expenses WHERE actionId=:actionId AND id=:id") suspend fun delete(actionId: String, id: String): Int
}
internal val ACTION_MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        listOf(
            "CREATE TABLE expenses (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,title TEXT NOT NULL,totalCents INTEGER NOT NULL,createdAt INTEGER NOT NULL,FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE UNIQUE INDEX index_expenses_actionId_id ON expenses(actionId,id)",
            "CREATE TABLE expense_payments (actionId TEXT NOT NULL,expenseId TEXT NOT NULL,position INTEGER NOT NULL,payerLabel TEXT NOT NULL,amountCents INTEGER NOT NULL,PRIMARY KEY(actionId,expenseId,position),FOREIGN KEY(actionId,expenseId) REFERENCES expenses(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        ).forEach { sql -> connection.prepare(sql).use { it.step() } }
    }
}
