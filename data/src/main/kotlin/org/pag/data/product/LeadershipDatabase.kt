package org.pag.data.product

import androidx.room3.*
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.pag.domain.*
import java.io.*
import java.time.LocalDate

@Entity(tableName="leadership", foreignKeys=[ForeignKey(entity=ActionRow::class,parentColumns=["id"],childColumns=["actionId"],onDelete=ForeignKey.CASCADE)])
internal data class LeadershipRow(@PrimaryKey val actionId:String, val state:String, val epoch:String,
    val cryptoVersion:Int, val algorithm:String, val kdfVersion:Int, val memoryKiB:Int, val iterations:Int,
    val parallelism:Int, val salt:ByteArray, val envelope:ByteArray)
@Entity(tableName="sensitive_person_areas",primaryKeys=["actionId","personId","area"],
    foreignKeys=[ForeignKey(entity=PersonRow::class,parentColumns=["actionId","id"],childColumns=["actionId","personId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index(value=["actionId","personId"])])
internal data class SensitiveRow(val actionId:String,val personId:String,val area:String,val cryptoVersion:Int,
    val revision:Long,val keyEnvelope:ByteArray,val payload:ByteArray)
@Dao internal interface LeadershipDao {
    @Query("SELECT * FROM leadership WHERE actionId=:actionId") suspend fun get(actionId:String):LeadershipRow?
    @Insert suspend fun insert(row:LeadershipRow)
    @Update suspend fun update(row:LeadershipRow):Int
    @Query("DELETE FROM leadership WHERE actionId=:actionId") suspend fun remove(actionId:String)
    @Query("SELECT * FROM sensitive_person_areas WHERE actionId=:actionId") suspend fun all(actionId:String):List<SensitiveRow>
    @Query("SELECT * FROM sensitive_person_areas WHERE actionId=:actionId AND personId=:personId") suspend fun person(actionId:String,personId:String):List<SensitiveRow>
    @Query("SELECT * FROM sensitive_person_areas WHERE actionId=:actionId AND personId=:personId AND area=:area") suspend fun area(actionId:String,personId:String,area:String):SensitiveRow?
    @Insert suspend fun insertArea(row:SensitiveRow)
    @Update suspend fun updateArea(row:SensitiveRow):Int
    @Query("DELETE FROM sensitive_person_areas WHERE actionId=:actionId AND personId=:personId AND area=:area") suspend fun removeArea(actionId:String,personId:String,area:String)
    @Query("DELETE FROM sensitive_person_areas WHERE actionId=:actionId AND personId=:personId") suspend fun removePerson(actionId:String,personId:String)
    @Query("DELETE FROM sensitive_person_areas WHERE actionId=:actionId") suspend fun removeAction(actionId:String)
}
internal val ACTION_MIGRATION_3_4=object:Migration(3,4){
    override suspend fun migrate(connection:SQLiteConnection){
        listOf(
            "CREATE UNIQUE INDEX index_persons_actionId_id ON persons(actionId,id)",
            "CREATE TABLE leadership (actionId TEXT NOT NULL PRIMARY KEY, state TEXT NOT NULL, epoch TEXT NOT NULL, cryptoVersion INTEGER NOT NULL, algorithm TEXT NOT NULL, kdfVersion INTEGER NOT NULL, memoryKiB INTEGER NOT NULL, iterations INTEGER NOT NULL, parallelism INTEGER NOT NULL, salt BLOB NOT NULL, envelope BLOB NOT NULL, FOREIGN KEY(actionId) REFERENCES actions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE TABLE sensitive_person_areas (actionId TEXT NOT NULL, personId TEXT NOT NULL, area TEXT NOT NULL, cryptoVersion INTEGER NOT NULL, revision INTEGER NOT NULL, keyEnvelope BLOB NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(actionId,personId,area), FOREIGN KEY(actionId,personId) REFERENCES persons(actionId,id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX index_sensitive_person_areas_actionId_personId ON sensitive_person_areas(actionId,personId)"
        ).forEach { sql -> connection.prepare(sql).use { it.step() } }
    }
}
/** Versioned bounded structure, not a crypto primitive. No plaintext persistence. */
internal object SensitiveCodec {
    fun context(vararg values:String):ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out -> values.forEach { out.writeUTF(it) } }
    }.toByteArray()
    private class ClearingBytes:ByteArrayOutputStream(){fun clear(){buf.fill(0);reset()}}
    fun encode(data:SensitiveData):ByteArray {
        val bytes=ClearingBytes()
        try { DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            fun text(value:String?) { out.writeBoolean(value!=null); if(value!=null) out.writeUTF(value) }
            when(data){
                is SensitiveData.Identity->{text(data.fullName);text(data.birthDate?.toString())}
                is SensitiveData.Contact->text(data.phone)
                is SensitiveData.EmergencyContact->{text(data.name);text(data.phone);text(data.relationship)}
            }
        }
        return bytes.toByteArray()
        } finally {bytes.clear()}
    }
    fun decode(area:SensitiveArea,bytes:ByteArray):SensitiveData {
        if(bytes.size>200000) throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        val input=DataInputStream(ByteArrayInputStream(bytes))
        if(input.readInt()!=1) throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        fun text():String?=if(input.readBoolean()) input.readUTF() else null
        val result=when(area){
            SensitiveArea.IDENTITY->SensitiveData.Identity(text(),text()?.let(LocalDate::parse))
            SensitiveArea.CONTACT->SensitiveData.Contact(text())
            SensitiveArea.EMERGENCY_CONTACT->SensitiveData.EmergencyContact(text(),text(),text())
        }
        if(input.available()!=0) throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        return result
    }
    fun hasData(data:SensitiveData):Boolean=when(data){
        is SensitiveData.Identity->!data.fullName.isNullOrEmpty()||data.birthDate!=null
        is SensitiveData.Contact->!data.phone.isNullOrEmpty()
        is SensitiveData.EmergencyContact->!data.name.isNullOrEmpty()||!data.phone.isNullOrEmpty()||!data.relationship.isNullOrEmpty()
    }
    fun empty(area:SensitiveArea):SensitiveData=when(area){
        SensitiveArea.IDENTITY->SensitiveData.Identity()
        SensitiveArea.CONTACT->SensitiveData.Contact()
        SensitiveArea.EMERGENCY_CONTACT->SensitiveData.EmergencyContact()
    }
}
internal class SensitiveProblem(val reason:SensitiveFailure):RuntimeException("SENSITIVE-DENIED")
