package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real host SQLite; Android Room/SQLCipher evidence is separate. */
class P104MigrationTest {
    private suspend fun scalar(db:SqliteHostConnection,q:String)=db.usePrepared(q){check(it.step());it.getLong(0)}
    private suspend fun seed(db:SqliteHostConnection){
        db.execute("INSERT INTO actions VALUES('a','Synthetic action','Synthetic description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
        db.execute("INSERT INTO persons VALUES('p','a','Synthetic','synthetic',1),('q','a','Second','second',2)")
        db.execute("INSERT INTO roles VALUES('r','a','Role','role',1)")
        db.execute("INSERT INTO person_planned_roles VALUES('a','p','r')")
        db.execute("INSERT INTO person_role_plans VALUES('a','p','r')")
        db.execute("INSERT INTO person_operational_states VALUES('a','p','r','ON_SITE',NULL,42)")
        db.execute("INSERT INTO tasks VALUES('t','a','Task','NORMAL','OPEN','p',NULL,NULL,NULL,NULL,1,42,NULL)")
        db.execute("INSERT INTO task_events VALUES('e','a','t','CREATED',1)")
    }
    @Test fun elevenToTwelvePreservesValuesBackfillsStableHeadersAndChecksForeignKeys()=runBlocking {
        SqliteHostConnection(schemaVersion=11).use{db->
            seed(db)
            val counts=listOf("persons","roles","person_planned_roles","person_operational_states","tasks","task_events").associateWith{scalar(db,"SELECT COUNT(*) FROM $it")}
            db.execute("BEGIN IMMEDIATE")
            ACTION_MIGRATION_11_12.migrate(db.migrationConnection())
            db.execute("COMMIT")
            validateEditRevisions(db)
            for((table,count) in counts)assertEquals(count,scalar(db,"SELECT COUNT(*) FROM $table"))
            for(table in EDIT_REVISION_TABLES)assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM $table WHERE editRevision!=0"))
            assertEquals(2L,scalar(db,"SELECT COUNT(*) FROM person_role_plans"))
            assertEquals(1L,scalar(db,"SELECT COUNT(*) FROM person_role_plans WHERE personId='p' AND primaryRoleId='r'"))
            assertEquals(1L,scalar(db,"SELECT COUNT(*) FROM tasks WHERE title='Task' AND updatedAt=42"))
            db.usePrepared("PRAGMA foreign_key_check"){assertFalse(it.step())}
        }
    }
    @Test fun migratedAndFreshTwelveHaveTheSameRoomColumnContract()=runBlocking{
        SqliteHostConnection(schemaVersion=11).use{old->SqliteHostConnection().use{fresh->
            old.execute("BEGIN IMMEDIATE");ACTION_MIGRATION_11_12.migrate(old.migrationConnection());old.execute("COMMIT")
            suspend fun columns(db:SqliteHostConnection,table:String):List<String> = db.usePrepared("PRAGMA table_info('$table')"){s->
                buildList{while(s.step())add((0 until s.getColumnCount()).joinToString("|"){i->if(s.isNull(i))"NULL" else if(s.getColumnType(i)==1)s.getLong(i).toString() else s.getText(i)})}
            }
            for(table in EDIT_REVISION_TABLES)assertEquals(table,columns(fresh,table),columns(old,table))
        }}
    }
    @Test fun interruptedMigrationRollsBackAllFiveColumnsAndBackfill()=runBlocking {
        SqliteHostConnection(schemaVersion=11).use{db->
            seed(db);db.execute("BEGIN IMMEDIATE")
            ACTION_MIGRATION_11_12.migrate(db.migrationConnection())
            db.execute("ROLLBACK")
            for(table in EDIT_REVISION_TABLES)assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM pragma_table_info('$table') WHERE name='editRevision'"))
            assertEquals(1L,scalar(db,"SELECT COUNT(*) FROM person_role_plans"))
            db.execute("BEGIN IMMEDIATE");ACTION_MIGRATION_11_12.migrate(db.migrationConnection());db.execute("COMMIT")
            validateEditRevisions(db)
        }
    }
    @Test fun missingStableHeaderFailsClosedWithoutInventingRevisionZero()=runBlocking{
        SqliteHostConnection().use{db->
            db.execute("INSERT INTO actions VALUES('a','Synthetic action','Synthetic description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
            db.execute("INSERT INTO persons VALUES('p','a','Synthetic','synthetic',1,0)")
            try{validateEditRevisions(db);fail("Missing header is not revision zero")}catch(expected:ProductStorageFailure){}
        }
    }
    @Test fun invalidRevisionAffinityAndMissingHeaderCannotBecomeValidExpected()=runBlocking {
        for(value in listOf("-1","1.5","'invalid'"))SqliteHostConnection().use{db->
            db.execute("INSERT INTO actions VALUES('a','Synthetic action','Synthetic description',1,2,0,'PREPARATION','SENSITIVE',NULL)")
            db.execute("INSERT INTO persons VALUES('p','a','Synthetic','synthetic',1,$value)")
            db.execute("INSERT INTO person_role_plans VALUES('a','p',NULL,0)")
            try{validateEditRevisions(db);fail("Invalid revision must fail closed")}catch(expected:ProductStorageFailure){}
        }
    }
}
