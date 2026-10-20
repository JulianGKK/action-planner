package org.pag.data.product

import androidx.sqlite.SQLiteStatement

/** Same writer stores as production, real schema-11 SQL; no mocked DAO outcomes. */
internal class HostCoreWriterData(private val sql:SqliteHostConnection):CoreWriterData {
    suspend fun <T> query(text:String,args:List<Any?> = emptyList(),work:(SQLiteStatement)->T):T=sql.usePrepared(text){s->
        args.forEachIndexed{i,v->when(v){null->s.bindNull(i+1);is String->s.bindText(i+1,v);is Long->s.bindLong(i+1,v);else->error("Scalar binding")}}
        work(s)
    }
    private suspend fun change(text:String,args:List<Any?>):Int {
        query(text,args){it.step()}
        return query("SELECT changes()"){it.step();it.getLong(0).toInt()}
    }
    private fun nullable(s:SQLiteStatement,i:Int)=if(s.isNull(i))null else s.getText(i)
    private fun action(s:SQLiteStatement)=ActionRow(s.getText(0),s.getText(1),s.getText(2),s.getLong(3),s.getLong(4),s.getLong(5),s.getText(6),s.getText(7),if(s.isNull(8))null else s.getLong(8))
    private fun person(s:SQLiteStatement)=PersonRow(s.getText(0),s.getText(1),s.getText(2),s.getText(3),s.getLong(4))
    private fun role(s:SQLiteStatement)=RoleRow(s.getText(0),s.getText(1),s.getText(2),s.getText(3),s.getLong(4))
    private fun state(s:SQLiteStatement)=OperationalRow(s.getText(0),s.getText(1),nullable(s,2),s.getText(3),nullable(s,4),s.getLong(5))
    private suspend fun <T> rows(text:String,args:List<Any?>,read:(SQLiteStatement)->T):List<T> = query(text,args){s->buildList{while(s.step())add(read(s))}}
    override val actions=object:ActionDao {
        override suspend fun list()=rows("SELECT * FROM actions ORDER BY createdAt DESC,id",emptyList(),::action)
        override suspend fun get(id:String)=query("SELECT * FROM actions WHERE id=?",listOf(id)){if(it.step())action(it)else null}
        override suspend fun insert(row:ActionRow){change("INSERT INTO actions VALUES(?,?,?,?,?,?,?,?,?)",listOf(row.id,row.title,row.description,row.startAt,row.expectedEndAt,row.createdAt,row.status,row.sensitivity,row.completedAt))}
        override suspend fun update(row:ActionRow)=change("UPDATE actions SET title=?,description=?,startAt=?,expectedEndAt=?,createdAt=?,status=?,sensitivity=?,completedAt=? WHERE id=?",listOf(row.title,row.description,row.startAt,row.expectedEndAt,row.createdAt,row.status,row.sensitivity,row.completedAt,row.id))
    }
    override val persons=object:PersonDao {
        override suspend fun list(actionId:String)=rows("SELECT * FROM persons WHERE actionId=? ORDER BY createdAt,id",listOf(actionId),::person)
        override suspend fun get(actionId:String,id:String)=query("SELECT * FROM persons WHERE actionId=? AND id=?",listOf(actionId,id)){if(it.step())person(it)else null}
        override suspend fun samePseudonym(actionId:String,normalized:String)=query("SELECT * FROM persons WHERE actionId=? AND pseudonymNormalized=?",listOf(actionId,normalized)){if(it.step())person(it)else null}
        override suspend fun insert(row:PersonRow){change("INSERT INTO persons VALUES(?,?,?,?,?)",listOf(row.id,row.actionId,row.pseudonym,row.pseudonymNormalized,row.createdAt))}
        override suspend fun rename(actionId:String,id:String,pseudonym:String,normalized:String)=change("UPDATE persons SET pseudonym=?,pseudonymNormalized=? WHERE actionId=? AND id=?",listOf(pseudonym,normalized,actionId,id))
    }
    override val operations=object:OperationsDao {
        override suspend fun roles(actionId:String)=rows("SELECT * FROM roles WHERE actionId=? ORDER BY normalizedName,id",listOf(actionId),::role)
        override suspend fun role(actionId:String,roleId:String)=query("SELECT * FROM roles WHERE actionId=? AND id=?",listOf(actionId,roleId)){if(it.step())role(it)else null}
        override suspend fun insertRole(row:RoleRow){change("INSERT INTO roles VALUES(?,?,?,?,?)",listOf(row.id,row.actionId,row.name,row.normalizedName,row.createdAt))}
        override suspend fun updateRole(row:RoleRow)=change("UPDATE roles SET actionId=?,name=?,normalizedName=?,createdAt=? WHERE id=?",listOf(row.actionId,row.name,row.normalizedName,row.createdAt,row.id))
        override suspend fun removeRole(actionId:String,roleId:String)=change("DELETE FROM roles WHERE actionId=? AND id=?",listOf(actionId,roleId))
        override suspend fun planned(actionId:String)=rows("SELECT * FROM person_planned_roles WHERE actionId=?",listOf(actionId)){PlannedRoleRow(it.getText(0),it.getText(1),it.getText(2))}
        override suspend fun personAssignments(actionId:String,personId:String)=rows("SELECT * FROM person_planned_roles WHERE actionId=? AND personId=?",listOf(actionId,personId)){PlannedRoleRow(it.getText(0),it.getText(1),it.getText(2))}
        override suspend fun personPlan(actionId:String,personId:String)=rows("SELECT * FROM person_role_plans WHERE actionId=? AND personId=?",listOf(actionId,personId)){RolePlanRow(it.getText(0),it.getText(1),nullable(it,2))}.singleOrNull()
        override suspend fun plans(actionId:String)=rows("SELECT * FROM person_role_plans WHERE actionId=?",listOf(actionId)){RolePlanRow(it.getText(0),it.getText(1),nullable(it,2))}
        override suspend fun states(actionId:String)=rows("SELECT * FROM person_operational_states WHERE actionId=?",listOf(actionId),::state)
        override suspend fun state(actionId:String,personId:String)=query("SELECT * FROM person_operational_states WHERE actionId=? AND personId=?",listOf(actionId,personId)){if(it.step())state(it)else null}
        private suspend fun references(table:String,key:String,a:String,r:String)=query("SELECT COUNT(*) FROM $table WHERE actionId=? AND $key=?",listOf(a,r)){it.step();it.getLong(0).toInt()}
        override suspend fun plannedReferences(actionId:String,roleId:String)=references("person_planned_roles","roleId",actionId,roleId)
        override suspend fun liveReferences(actionId:String,roleId:String)=references("person_operational_states","currentRoleId",actionId,roleId)
        override suspend fun removePlan(actionId:String,personId:String){change("DELETE FROM person_role_plans WHERE actionId=? AND personId=?",listOf(actionId,personId))}
        override suspend fun removeAssignments(actionId:String,personId:String){change("DELETE FROM person_planned_roles WHERE actionId=? AND personId=?",listOf(actionId,personId))}
        override suspend fun assign(row:PlannedRoleRow){change("INSERT INTO person_planned_roles VALUES(?,?,?)",listOf(row.actionId,row.personId,row.roleId))}
        override suspend fun insertPlan(row:RolePlanRow){change("INSERT INTO person_role_plans VALUES(?,?,?)",listOf(row.actionId,row.personId,row.primaryRoleId))}
        override suspend fun insertState(row:OperationalRow){change("INSERT INTO person_operational_states VALUES(?,?,?,?,?,?)",listOf(row.actionId,row.personId,row.currentRoleId,row.operationalStatus,row.currentPlaceLabel,row.updatedAt))}
        override suspend fun updateState(row:OperationalRow)=change("UPDATE person_operational_states SET currentRoleId=?,operationalStatus=?,currentPlaceLabel=?,updatedAt=? WHERE actionId=? AND personId=?",listOf(row.currentRoleId,row.operationalStatus,row.currentPlaceLabel,row.updatedAt,row.actionId,row.personId))
    }
    private fun material(s:SQLiteStatement)=MaterialRow(s.getText(0),s.getText(1),s.getText(2),nullable(s,3),nullable(s,4),nullable(s,5),nullable(s,6),nullable(s,7),s.getLong(8))
    override val materials=object:MaterialDao {
        override suspend fun count(actionId:String)=query("SELECT COUNT(*) FROM materials WHERE actionId=?",listOf(actionId)){it.step();it.getLong(0).toInt()}
        override suspend fun list(actionId:String)=rows("SELECT * FROM materials WHERE actionId=? ORDER BY createdAt,id",listOf(actionId),::material)
        override suspend fun get(actionId:String,id:String)=query("SELECT * FROM materials WHERE actionId=? AND id=?",listOf(actionId,id)){if(it.step())material(it)else null}
        override suspend fun insert(row:MaterialRow){change("INSERT INTO materials VALUES(?,?,?,?,?,?,?,?,?)",listOf(row.id,row.actionId,row.name,row.quantityLabel,row.placeLabel,row.responsiblePersonId,row.linkedTaskId,row.sourceTemplateKey,row.createdAt))}
        override suspend fun update(row:MaterialRow)=change("UPDATE materials SET actionId=?,name=?,quantityLabel=?,placeLabel=?,responsiblePersonId=?,linkedTaskId=?,sourceTemplateKey=?,createdAt=? WHERE id=?",listOf(row.actionId,row.name,row.quantityLabel,row.placeLabel,row.responsiblePersonId,row.linkedTaskId,row.sourceTemplateKey,row.createdAt,row.id))
        override suspend fun delete(actionId:String,id:String)=change("DELETE FROM materials WHERE actionId=? AND id=?",listOf(actionId,id))
    }
    override val tasks=HostTaskData(sql).tasks
}
