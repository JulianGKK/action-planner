package org.pag.data.product

import androidx.room3.PooledConnection
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Persisted entity consistency, never a session/cache authority. */
internal val EDIT_REVISION_TABLES=listOf("persons","roles","person_role_plans","person_operational_states","tasks")
internal val ACTION_MIGRATION_11_12=object:Migration(11,12) {
    override suspend fun migrate(connection:SQLiteConnection) {
        for(table in EDIT_REVISION_TABLES)connection.prepare("ALTER TABLE $table ADD COLUMN editRevision INTEGER NOT NULL DEFAULT 0").use{it.step()}
        connection.prepare("INSERT INTO person_role_plans(actionId,personId,primaryRoleId,editRevision) SELECT p.actionId,p.id,NULL,0 FROM persons p WHERE NOT EXISTS(SELECT 1 FROM person_role_plans h WHERE h.actionId=p.actionId AND h.personId=p.id)").use{it.step()}
        for(table in EDIT_REVISION_TABLES)connection.prepare("SELECT COUNT(*) FROM $table WHERE typeof(editRevision)!='integer' OR editRevision<0").use{if(!it.step() || it.getLong(0)!=0L)throw ProductStorageFailure()}
        connection.prepare("PRAGMA foreign_key_check").use{if(it.step())throw ProductStorageFailure()}
    }
}
/** Scalar-only proof before DAO affinity coercion or payload materialization. */
internal suspend fun validateEditRevisions(sql:PooledConnection) {
    for(table in EDIT_REVISION_TABLES)sql.usePrepared("SELECT COUNT(*) FROM $table WHERE typeof(editRevision)!='integer' OR editRevision<0"){s->
        if(!s.step() || s.getLong(0)!=0L)throw ProductStorageFailure()
    }
    sql.usePrepared("SELECT COUNT(*) FROM persons p WHERE NOT EXISTS(SELECT 1 FROM person_role_plans h WHERE h.actionId=p.actionId AND h.personId=p.id)"){s->
        if(!s.step() || s.getLong(0)!=0L)throw ProductStorageFailure()
    }
}
