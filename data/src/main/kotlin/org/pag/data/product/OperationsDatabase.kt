package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.*

@Entity(tableName="roles", foreignKeys=[ForeignKey(entity=ActionRow::class,parentColumns=["id"],childColumns=["actionId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index(value=["actionId","id"],unique=true),Index(value=["actionId","normalizedName"],unique=true)])
internal data class RoleRow(@PrimaryKey val id:String,val actionId:String,val name:String,val normalizedName:String,val createdAt:Long) {
    fun model():Role { if(id.isBlank() || actionId.isBlank() || !OperationalRules.validName(name) || normalizedName!=OperationalRules.normalizedName(name))throw ProductStorageFailure();return Role(id,actionId,name,createdAt) }
}
@Entity(tableName="person_planned_roles",primaryKeys=["actionId","personId","roleId"],
    foreignKeys=[ForeignKey(entity=PersonRow::class,parentColumns=["actionId","id"],childColumns=["actionId","personId"],onDelete=ForeignKey.CASCADE),
        ForeignKey(entity=RoleRow::class,parentColumns=["actionId","id"],childColumns=["actionId","roleId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["actionId","roleId"])])
internal data class PlannedRoleRow(val actionId:String,val personId:String,val roleId:String)
@Entity(tableName="person_role_plans",primaryKeys=["actionId","personId"],
    foreignKeys=[ForeignKey(entity=PersonRow::class,parentColumns=["actionId","id"],childColumns=["actionId","personId"],onDelete=ForeignKey.CASCADE),
        ForeignKey(entity=PlannedRoleRow::class,parentColumns=["actionId","personId","roleId"],childColumns=["actionId","personId","primaryRoleId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["actionId","personId","primaryRoleId"])])
internal data class RolePlanRow(val actionId:String,val personId:String,val primaryRoleId:String?)
@Entity(tableName="person_operational_states",primaryKeys=["actionId","personId"],
    foreignKeys=[ForeignKey(entity=PersonRow::class,parentColumns=["actionId","id"],childColumns=["actionId","personId"],onDelete=ForeignKey.CASCADE),
        ForeignKey(entity=RoleRow::class,parentColumns=["actionId","id"],childColumns=["actionId","currentRoleId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["actionId","currentRoleId"])])
internal data class OperationalRow(val actionId:String,val personId:String,val currentRoleId:String?,val operationalStatus:String,val currentPlaceLabel:String?,val updatedAt:Long) {
    fun model():PersonOperationalState {
        val status=OperationalStatus.valueOf(operationalStatus)
        if(!OperationalRules.roleAllowed(status) && currentRoleId!=null || currentPlaceLabel!=null && (!OperationalRules.placeRelevant(status) || !OperationalRules.validPlace(currentPlaceLabel) || currentPlaceLabel!=OperationalRules.place(currentPlaceLabel,status)))throw ProductStorageFailure()
        return PersonOperationalState(actionId,personId,currentRoleId,status,currentPlaceLabel,updatedAt)
    }
}
@Dao internal interface OperationsDao {
    @Query("SELECT * FROM roles WHERE actionId=:actionId ORDER BY normalizedName,id") suspend fun roles(actionId:String):List<RoleRow>
    @Query("SELECT * FROM roles WHERE actionId=:actionId AND id=:roleId") suspend fun role(actionId:String,roleId:String):RoleRow?
    @Insert suspend fun insertRole(row:RoleRow)
    @Update suspend fun updateRole(row:RoleRow):Int
    @Query("DELETE FROM roles WHERE actionId=:actionId AND id=:roleId") suspend fun removeRole(actionId:String,roleId:String):Int
    @Query("SELECT * FROM person_planned_roles WHERE actionId=:actionId") suspend fun planned(actionId:String):List<PlannedRoleRow>
    @Query("SELECT * FROM person_role_plans WHERE actionId=:actionId") suspend fun plans(actionId:String):List<RolePlanRow>
    @Query("SELECT * FROM person_operational_states WHERE actionId=:actionId") suspend fun states(actionId:String):List<OperationalRow>
    @Query("SELECT * FROM person_operational_states WHERE actionId=:actionId AND personId=:personId") suspend fun state(actionId:String,personId:String):OperationalRow?
    @Query("SELECT COUNT(*) FROM person_planned_roles WHERE actionId=:actionId AND roleId=:roleId") suspend fun plannedReferences(actionId:String,roleId:String):Int
    @Query("SELECT COUNT(*) FROM person_operational_states WHERE actionId=:actionId AND currentRoleId=:roleId") suspend fun liveReferences(actionId:String,roleId:String):Int
    @Query("DELETE FROM person_role_plans WHERE actionId=:actionId AND personId=:personId") suspend fun removePlan(actionId:String,personId:String)
    @Query("DELETE FROM person_planned_roles WHERE actionId=:actionId AND personId=:personId") suspend fun removeAssignments(actionId:String,personId:String)
    @Insert suspend fun assign(row:PlannedRoleRow)
    @Insert suspend fun insertPlan(row:RolePlanRow)
    @Insert suspend fun insertState(row:OperationalRow)
    @Update suspend fun updateState(row:OperationalRow):Int
}
internal val ACTION_MIGRATION_4_5=object:Migration(4,5) {
    override suspend fun migrate(connection:SQLiteConnection) {
        listOf(
            "CREATE TABLE roles (id TEXT NOT NULL PRIMARY KEY,actionId TEXT NOT NULL,name TEXT NOT NULL,normalizedName TEXT NOT NULL,createdAt INTEGER NOT NULL,FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE UNIQUE INDEX index_roles_actionId_id ON roles(actionId,id)",
            "CREATE UNIQUE INDEX index_roles_actionId_normalizedName ON roles(actionId,normalizedName)",
            "CREATE TABLE person_planned_roles (actionId TEXT NOT NULL,personId TEXT NOT NULL,roleId TEXT NOT NULL,PRIMARY KEY(actionId,personId,roleId),FOREIGN KEY(actionId,personId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(actionId,roleId) REFERENCES roles(actionId,id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE INDEX index_person_planned_roles_actionId_roleId ON person_planned_roles(actionId,roleId)",
            "CREATE TABLE person_role_plans (actionId TEXT NOT NULL,personId TEXT NOT NULL,primaryRoleId TEXT,PRIMARY KEY(actionId,personId),FOREIGN KEY(actionId,personId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(actionId,personId,primaryRoleId) REFERENCES person_planned_roles(actionId,personId,roleId) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE INDEX index_person_role_plans_actionId_personId_primaryRoleId ON person_role_plans(actionId,personId,primaryRoleId)",
            "CREATE TABLE person_operational_states (actionId TEXT NOT NULL,personId TEXT NOT NULL,currentRoleId TEXT,operationalStatus TEXT NOT NULL,currentPlaceLabel TEXT,updatedAt INTEGER NOT NULL,PRIMARY KEY(actionId,personId),FOREIGN KEY(actionId,personId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE,FOREIGN KEY(actionId,currentRoleId) REFERENCES roles(actionId,id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            "CREATE INDEX index_person_operational_states_actionId_currentRoleId ON person_operational_states(actionId,currentRoleId)",
            // Existing running/completed actions have no historical observation. 0 means unknown update time.
            "INSERT INTO person_operational_states SELECT p.actionId,p.id,NULL,'NO_STATUS',NULL,0 FROM persons p JOIN actions a ON p.actionId=a.id WHERE a.status IN ('ACTIVE','COMPLETED')"
        ).forEach { sql -> connection.prepare(sql).use { it.step() } }
    }
}
