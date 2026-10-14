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
    db.execSQL("DROP TABLE IF EXISTS expense_payments")
    db.execSQL("DROP TABLE IF EXISTS expenses")
}
