package org.pag.data.product

import androidx.sqlite.SQLiteStatement
import org.pag.domain.ProtocolPersonReference

/** Production DAO-equivalent SQL on schema 11. No authority or transaction ownership.
 * Long/Int conversion mirrors native affinity so raw position guards are tested, not bypassed. */
internal class HostProtocolDao(private val data:HostCoreWriterData):ProtocolDao {
    private val sql=mapOf(
        "missingOriginals" to "SELECT COUNT(*) FROM protocol_entries e WHERE e.actionId=? AND NOT EXISTS (SELECT 1 FROM protocol_revisions r WHERE r.actionId=e.actionId AND r.entryId=e.id AND r.revisionNumber=1)",
        "summary" to "SELECT COALESCE(SUM(CASE WHEN r.classification='KEY_EVENT' THEN 1 ELSE 0 END),0) AS keyEventCount, MAX(o.recordedAt) AS lastEntryRecordedAt FROM protocol_entries e JOIN protocol_revisions o ON o.actionId=e.actionId AND o.entryId=e.id AND o.revisionNumber=1 JOIN protocol_revisions r ON r.actionId=e.actionId AND r.entryId=e.id AND r.revisionNumber=(SELECT MAX(n.revisionNumber) FROM protocol_revisions n WHERE n.actionId=e.actionId AND n.entryId=e.id) WHERE e.actionId=?",
        "timeline" to "SELECT e.id AS entryId,r.id AS revisionId,r.revisionNumber,r.revisionType,r.classification,r.templateKey,r.title,r.occurredAt,r.occurredAtApproximate,r.recordedAt,r.recordedAfterCompletion,e.createdAfterCompletion,r.mistaken,o.recordedAt AS originalRecordedAt FROM protocol_entries e JOIN protocol_revisions o ON o.actionId=e.actionId AND o.entryId=e.id AND o.revisionNumber=1 JOIN protocol_revisions r ON r.actionId=e.actionId AND r.entryId=e.id AND r.revisionNumber=(SELECT MAX(n.revisionNumber) FROM protocol_revisions n WHERE n.actionId=e.actionId AND n.entryId=e.id) WHERE e.actionId=? ORDER BY r.occurredAt,o.recordedAt,e.id",
        "entry" to "SELECT * FROM protocol_entries WHERE actionId=? AND id=?",
        "revisions" to "SELECT * FROM protocol_revisions WHERE actionId=? AND entryId=? ORDER BY revisionNumber",
        "current" to "SELECT * FROM protocol_revisions WHERE actionId=? AND entryId=? ORDER BY revisionNumber DESC LIMIT 1",
        "affected" to "SELECT p.* FROM protocol_affected_persons p JOIN protocol_revisions r ON r.actionId=p.actionId AND r.id=p.revisionId WHERE p.actionId=? AND r.entryId=?",
        "reported" to "SELECT p.* FROM protocol_reported_by_persons p JOIN protocol_revisions r ON r.actionId=p.actionId AND r.id=p.revisionId WHERE p.actionId=? AND r.entryId=?",
        "identifiers" to "SELECT p.* FROM protocol_official_identifiers p JOIN protocol_revisions r ON r.actionId=p.actionId AND r.id=p.revisionId WHERE p.actionId=? AND r.entryId=? ORDER BY r.revisionNumber,p.position",
        "persons" to "SELECT id,pseudonym FROM persons WHERE actionId=? AND id IN (:ids) ORDER BY createdAt,id"
    )
    private fun text(s:SQLiteStatement,i:Int)=if(s.isNull(i))null else s.getText(i)
    private fun long(s:SQLiteStatement,i:Int):Long=when(s.getColumnType(i)) {
        1->s.getLong(i);2->s.getDouble(i).toLong();3->s.getText(i).toLongOrNull()?:0L;else->0L
    }
    private fun entry(s:SQLiteStatement)=ProtocolEntryRow(s.getText(0),s.getText(1),long(s,2)!=0L)
    private fun revision(s:SQLiteStatement)=ProtocolRevisionRow(s.getText(0),s.getText(1),s.getText(2),long(s,3),
        s.getText(4),s.getText(5),text(s,6),s.getText(7),text(s,8),long(s,9),long(s,10)!=0L,
        long(s,11),long(s,12)!=0L,long(s,13)!=0L,text(s,14),text(s,15),text(s,16),text(s,17),text(s,18))
    private suspend fun <T> rows(name:String,args:List<Any?>,read:(SQLiteStatement)->T)=data.query(sql.getValue(name),args){s->buildList{while(s.step())add(read(s))}}
    override suspend fun missingOriginals(a:String)=data.query(sql.getValue("missingOriginals"),listOf(a)){it.step();it.getLong(0)}
    override suspend fun summary(a:String)=data.query(sql.getValue("summary"),listOf(a)){it.step();ProtocolSummaryRow(it.getLong(0),if(it.isNull(1))null else it.getLong(1))}
    override suspend fun timeline(a:String)=rows("timeline",listOf(a)){s->ProtocolTimelineRow(s.getText(0),s.getText(1),long(s,2),s.getText(3),s.getText(4),text(s,5),s.getText(6),long(s,7),long(s,8)!=0L,long(s,9),long(s,10)!=0L,long(s,11)!=0L,long(s,12)!=0L,long(s,13))}
    override suspend fun entry(a:String,id:String)=rows("entry",listOf(a,id),::entry).singleOrNull()
    override suspend fun revisions(a:String,entryId:String)=rows("revisions",listOf(a,entryId),::revision)
    override suspend fun current(a:String,entryId:String)=rows("current",listOf(a,entryId),::revision).singleOrNull()
    override suspend fun affected(a:String,entryId:String)=rows("affected",listOf(a,entryId)){ProtocolAffectedRow(it.getText(0),it.getText(1),it.getText(2))}
    override suspend fun reported(a:String,entryId:String)=rows("reported",listOf(a,entryId)){ProtocolReportedRow(it.getText(0),it.getText(1),it.getText(2))}
    override suspend fun invalidIdentifierPositions(a:String,entryId:String)=data.query("SELECT COUNT(*) FROM protocol_official_identifiers p JOIN protocol_revisions r ON r.actionId=p.actionId AND r.id=p.revisionId WHERE p.actionId=? AND r.entryId=? AND (typeof(p.position)!='integer' OR p.position<0 OR p.position>31)",listOf(a,entryId)){it.step();it.getLong(0)}
    override suspend fun identifiers(a:String,entryId:String)=rows("identifiers",listOf(a,entryId)){ProtocolIdentifierRow(it.getText(0),it.getText(1),long(it,2).toInt(),it.getText(3),it.getText(4))}
    override suspend fun persons(a:String,ids:Set<String>):List<ProtocolPersonReference> {
        if(ids.isEmpty())return emptyList()
        return data.query(sql.getValue("persons").replace(":ids",ids.joinToString(","){"?"}),listOf(a)+ids){s->buildList{while(s.step())add(ProtocolPersonReference(s.getText(0),s.getText(1)))}}
    }
    override suspend fun insert(row:ProtocolEntryRow){data.change("INSERT INTO protocol_entries VALUES(?,?,?)",listOf(row.id,row.actionId,if(row.createdAfterCompletion)1L else 0L))}
    override suspend fun insert(row:ProtocolRevisionRow){data.change("INSERT INTO protocol_revisions VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",listOf(row.id,row.actionId,row.entryId,row.revisionNumber,row.revisionType,row.classification,row.templateKey,row.title,row.description,row.occurredAt,if(row.occurredAtApproximate)1L else 0L,row.recordedAt,if(row.recordedAfterCompletion)1L else 0L,if(row.mistaken)1L else 0L,row.sourceType,row.placeLabel,row.announcementText,row.announcementKind,row.statedLegalBasis))}
    override suspend fun insert(row:ProtocolAffectedRow){data.change("INSERT INTO protocol_affected_persons VALUES(?,?,?)",listOf(row.actionId,row.revisionId,row.personId))}
    override suspend fun insert(row:ProtocolReportedRow){data.change("INSERT INTO protocol_reported_by_persons VALUES(?,?,?)",listOf(row.actionId,row.revisionId,row.personId))}
    override suspend fun insert(row:ProtocolIdentifierRow){data.change("INSERT INTO protocol_official_identifiers VALUES(?,?,?,?,?)",listOf(row.actionId,row.revisionId,row.position.toLong(),row.type,row.value))}
}
