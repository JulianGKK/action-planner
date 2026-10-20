package org.pag.data.product

import androidx.sqlite.SQLiteStatement
import org.pag.domain.*

/** Test adapter over real SQLite, implementing the production TaskStore persistence port. */
internal class HostTaskData(private val sql: SqliteHostConnection, private val afterEvent:()->Unit = {}):TaskStoreData {
    private suspend fun <T> query(text:String,args:List<Any?> = emptyList(),work:(SQLiteStatement)->T):T=sql.usePrepared(text) { s ->
        args.forEachIndexed{i,v->when(v){null->s.bindNull(i+1);is String->s.bindText(i+1,v);is Long->s.bindLong(i+1,v);else->error("Scalar")}}
        work(s)
    }
    private suspend fun exists(table:String,action:String,id:String)=query("SELECT COUNT(*) FROM $table WHERE actionId=? AND id=?",listOf(action,id)){it.step();it.getLong(0)==1L}
    override suspend fun personExists(action:String,id:String)=exists("persons",action,id)
    override suspend fun roleExists(action:String,id:String)=exists("roles",action,id)
    override suspend fun action(id:String)=query("SELECT id,title,description,startAt,expectedEndAt,createdAt,status,sensitivity,completedAt FROM actions WHERE id=?",listOf(id)) { s ->
        if(!s.step()) null else ActionRow(s.getText(0),s.getText(1),s.getText(2),s.getLong(3),s.getLong(4),s.getLong(5),s.getText(6),s.getText(7),if(s.isNull(8)) null else s.getLong(8)).model()
    }
    private fun row(s:SQLiteStatement):TaskRow {
        fun text(i:Int)=if(s.isNull(i)) null else s.getText(i)
        fun number(i:Int)=if(s.isNull(i)) null else s.getLong(i)
        return TaskRow(s.getText(0),s.getText(1),s.getText(2),s.getText(3),s.getText(4),text(5),text(6),text(7),number(8),text(9),s.getLong(10),s.getLong(11),number(12))
    }
    private val fields="id,actionId,title,priority,status,assigneePersonId,assigneeRoleId,affectedPersonId,dueAt,sourceTemplateKey,createdAt,updatedAt,completedAt"
    private fun values(t:TaskRow)=listOf<Any?>(t.id,t.actionId,t.title,t.priority,t.status,t.assigneePersonId,t.assigneeRoleId,t.affectedPersonId,t.dueAt,t.sourceTemplateKey,t.createdAt,t.updatedAt,t.completedAt)
    private suspend fun change(statement:String,args:List<Any?>):Int {
        query(statement,args){it.step()}
        return query("SELECT changes()"){it.step();it.getLong(0).toInt()}
    }
    override val tasks=object:TaskDao {
        override suspend fun list(actionId:String)=query("SELECT $fields FROM tasks WHERE actionId=?",listOf(actionId)) { s ->buildList{while(s.step())add(row(s))} }
        override suspend fun get(actionId:String,id:String)=query("SELECT $fields FROM tasks WHERE actionId=? AND id=?",listOf(actionId,id)){if(it.step())row(it)else null}
        override suspend fun events(actionId:String)=query("SELECT id,actionId,taskId,type,occurredAt FROM task_events WHERE actionId=? ORDER BY occurredAt,rowid",listOf(actionId)) { s ->buildList{while(s.step())add(TaskEventRow(s.getText(0),s.getText(1),s.getText(2),s.getText(3),s.getLong(4)))} }
        override suspend fun roleReferences(actionId:String,roleId:String)=query("SELECT COUNT(*) FROM tasks WHERE actionId=? AND assigneeRoleId=?",listOf(actionId,roleId)){it.step();it.getLong(0).toInt()}
        override suspend fun insert(row:TaskRow) {change("INSERT INTO tasks($fields) VALUES(${List(13){"?"}.joinToString()})",values(row))}
        override suspend fun update(row:TaskRow)=change("UPDATE tasks SET ${fields.split(',').joinToString{"$it=?"}} WHERE id=?",values(row)+row.id)
        override suspend fun event(row:TaskEventRow) {change("INSERT INTO task_events VALUES(?,?,?,?,?)",listOf(row.id,row.actionId,row.taskId,row.type,row.occurredAt));afterEvent()}
        override suspend fun delete(actionId:String,id:String)=change("DELETE FROM tasks WHERE actionId=? AND id=?",listOf(actionId,id))
    }
}
