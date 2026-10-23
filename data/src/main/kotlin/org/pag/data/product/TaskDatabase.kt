package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.*

@Entity(tableName="tasks",foreignKeys=[
    ForeignKey(entity=ActionRow::class,parentColumns=["id"],childColumns=["actionId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=PersonRow::class,parentColumns=["actionId","id"],childColumns=["actionId","assigneePersonId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=RoleRow::class,parentColumns=["actionId","id"],childColumns=["actionId","assigneeRoleId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=PersonRow::class,parentColumns=["actionId","id"],childColumns=["actionId","affectedPersonId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["actionId","id"],unique=true),Index(value=["actionId","assigneePersonId"]),Index(value=["actionId","assigneeRoleId"]),Index(value=["actionId","affectedPersonId"])])
internal data class TaskRow(@PrimaryKey val id:String,val actionId:String,val title:String,val priority:String,val status:String,
    val assigneePersonId:String?,val assigneeRoleId:String?,val affectedPersonId:String?,val dueAt:Long?,val sourceTemplateKey:String?,
    val createdAt:Long,val updatedAt:Long,val completedAt:Long?) {
    fun model():Task {
        val t=Task(id,actionId,title,TaskPriority.valueOf(priority),TaskStatus.valueOf(status),assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey,createdAt,updatedAt,completedAt)
        if(!TaskRules.valid(t.input()) || title!=title.trim() || (t.status==TaskStatus.DONE)!=(completedAt!=null))throw ProductStorageFailure()
        return t
    }
}
internal fun Task.row()=TaskRow(id,actionId,title,priority.name,status.name,assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey,createdAt,updatedAt,completedAt)
@Entity(tableName="task_events",foreignKeys=[ForeignKey(entity=TaskRow::class,parentColumns=["actionId","id"],childColumns=["actionId","taskId"],onDelete=ForeignKey.CASCADE)],indices=[Index(value=["actionId","taskId"])])
internal data class TaskEventRow(@PrimaryKey val id:String,val actionId:String,val taskId:String,val type:String,val occurredAt:Long) {
    fun model()=TaskEvent(id,actionId,taskId,TaskEventType.valueOf(type),occurredAt)
}
@Dao internal interface TaskDao {
    @Query("SELECT * FROM tasks WHERE actionId=:actionId") suspend fun list(actionId:String):List<TaskRow>
    @Query("SELECT * FROM tasks WHERE actionId=:actionId AND id=:id") suspend fun get(actionId:String,id:String):TaskRow?
    // Legacy full-history diagnostic/test read; product boards use explicit paged history.
    @Query("SELECT * FROM task_events WHERE actionId=:actionId ORDER BY occurredAt,rowid") suspend fun events(actionId:String):List<TaskEventRow>
    @Query("SELECT COUNT(*) FROM task_events WHERE actionId=:actionId") suspend fun eventCount(actionId:String):Long
    @Query("SELECT COUNT(*) FROM tasks WHERE actionId=:actionId AND assigneeRoleId=:roleId") suspend fun roleReferences(actionId:String,roleId:String):Int
    @Insert suspend fun insert(row:TaskRow)
    @Update suspend fun update(row:TaskRow):Int
    @Insert suspend fun event(row:TaskEventRow)
    @Query("DELETE FROM tasks WHERE actionId=:actionId AND id=:id") suspend fun delete(actionId:String,id:String):Int
}
internal val ACTION_MIGRATION_5_6=object:Migration(5,6) {
    override suspend fun migrate(connection:SQLiteConnection) {
        listOf(
            "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,title TEXT NOT NULL,priority TEXT NOT NULL,status TEXT NOT NULL,assigneePersonId TEXT,assigneeRoleId TEXT,affectedPersonId TEXT,dueAt INTEGER,sourceTemplateKey TEXT,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,completedAt INTEGER,FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(actionId,assigneePersonId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(actionId,assigneeRoleId) REFERENCES roles(actionId,id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(actionId,affectedPersonId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE UNIQUE INDEX index_tasks_actionId_id ON tasks(actionId,id)",
            "CREATE INDEX index_tasks_actionId_assigneePersonId ON tasks(actionId,assigneePersonId)",
            "CREATE INDEX index_tasks_actionId_assigneeRoleId ON tasks(actionId,assigneeRoleId)",
            "CREATE INDEX index_tasks_actionId_affectedPersonId ON tasks(actionId,affectedPersonId)",
            "CREATE TABLE task_events (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,taskId TEXT NOT NULL,type TEXT NOT NULL,occurredAt INTEGER NOT NULL,FOREIGN KEY(actionId,taskId) REFERENCES tasks(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX index_task_events_actionId_taskId ON task_events(actionId,taskId)"
        ).forEach{sql->connection.prepare(sql).use{it.step()}}
        installTaskConstraints(connection)
    }
}

/** Room does not model CHECK clauses: identical triggers for fresh DBs and migration. */
internal suspend fun installTaskConstraints(connection:SQLiteConnection) {
    for(operation in listOf("INSERT","UPDATE")) {
        connection.prepare("CREATE TRIGGER tasks_exclusive_assignee_${operation.lowercase()} BEFORE $operation ON tasks WHEN NEW.assigneePersonId IS NOT NULL AND NEW.assigneeRoleId IS NOT NULL BEGIN SELECT RAISE(ABORT, 'TASK_ASSIGNMENT_INVALID'); END").use{it.step()}
    }
}
internal val TASK_DATABASE_CALLBACK=object:RoomDatabase.Callback() {
    override suspend fun onCreate(connection:SQLiteConnection) {installTaskConstraints(connection)}
}
