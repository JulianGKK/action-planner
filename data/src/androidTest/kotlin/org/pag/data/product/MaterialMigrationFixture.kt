package org.pag.data.product

/** Downgrade only synthetic fixtures. Parent-table triggers survive DROP TABLE materials. */
internal fun removeMaterialSchemaForLegacyFixture(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    removeExpenseSchemaForLegacyFixture(db)
    db.execSQL("DROP TRIGGER IF EXISTS materials_detach_person")
    db.execSQL("DROP TRIGGER IF EXISTS materials_detach_task")
    db.execSQL("DROP TABLE materials")
}

/** Expenses have no parent-table triggers; remove children before parent for old fixtures. */
internal fun removeExpenseSchemaForLegacyFixture(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    removeMemorySchemaForLegacyFixture(db)
    db.execSQL("DROP TABLE IF EXISTS expense_payments")
    db.execSQL("DROP TABLE IF EXISTS expenses")
}

internal fun removeMemorySchemaForLegacyFixture(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS memory_addenda")
    db.execSQL("DROP TABLE IF EXISTS memory_answers")
    db.execSQL("DROP TABLE IF EXISTS memory_reports")
}
