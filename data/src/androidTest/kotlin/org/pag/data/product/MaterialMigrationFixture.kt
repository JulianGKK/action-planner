package org.pag.data.product

/** Downgrade only synthetic fixtures. Parent-table triggers survive DROP TABLE materials. */
internal fun removeMaterialSchemaForLegacyFixture(db: net.zetetic.database.sqlcipher.SQLiteDatabase) {
    db.execSQL("DROP TRIGGER IF EXISTS materials_detach_person")
    db.execSQL("DROP TRIGGER IF EXISTS materials_detach_task")
    db.execSQL("DROP TABLE materials")
}
