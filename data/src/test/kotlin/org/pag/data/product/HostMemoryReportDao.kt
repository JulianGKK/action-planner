package org.pag.data.product

import androidx.sqlite.SQLiteStatement

/** Real schema-11 SQL equivalent to the Room DAO, including pre-materialization type guards.
 * Numeric coercion models native getLong so the guards, not a stricter parser, protect Ints. */
internal class HostMemoryReportDao(private val data:HostCoreWriterData):MemoryReportDao {
    private val sql=mapOf(
        "summary" to "SELECT COALESCE(SUM(CASE WHEN status='DRAFT' THEN 1 ELSE 0 END),0) AS draftCount, COALESCE(SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END),0) AS completedCount FROM memory_reports WHERE actionId=?",
        "invalidHeaders" to "SELECT COUNT(*) FROM memory_reports WHERE actionId=? AND (status NOT IN ('DRAFT','COMPLETED') OR typeof(questionnaireVersion)!='integer' OR questionnaireVersion!=1 OR typeof(revision)!='integer' OR revision<0 OR typeof(lastSavedSectionOrdinal)!='integer' OR lastSavedSectionOrdinal NOT BETWEEN 0 AND 16 OR typeof(createdAt)!='integer' OR createdAt<0 OR typeof(updatedAt)!='integer' OR updatedAt<0 OR (status='DRAFT' AND completedAt IS NOT NULL) OR (status='COMPLETED' AND (lastSavedSectionOrdinal!=16 OR completedAt IS NULL OR typeof(completedAt)!='integer' OR completedAt<createdAt)))",
        "list" to "SELECT id,actionId,authorLabel,questionnaireVersion,status,revision,lastSavedSectionOrdinal,createdAt,updatedAt,completedAt FROM memory_reports WHERE actionId=? ORDER BY createdAt,id",
        "get" to "SELECT id,actionId,authorLabel,questionnaireVersion,status,revision,lastSavedSectionOrdinal,createdAt,updatedAt,completedAt FROM memory_reports WHERE actionId=? AND id=?",
        "answers" to "SELECT actionId,reportId,questionKey,valueKind,payload FROM memory_answers WHERE actionId=? AND reportId=? ORDER BY questionKey",
        "addenda" to "SELECT actionId,reportId,sequence,content,recordedAt FROM memory_addenda WHERE actionId=? AND reportId=? ORDER BY sequence",
        "invalidAddendumTypes" to "SELECT COUNT(*) FROM memory_addenda WHERE actionId=? AND reportId=? AND (typeof(sequence)!='integer' OR sequence<0 OR sequence>2147483647 OR typeof(recordedAt)!='integer' OR recordedAt<0)",
        "clearSection" to "DELETE FROM memory_answers WHERE actionId=? AND reportId=? AND questionKey IN (:keys)"
    )
    private fun long(s:SQLiteStatement,i:Int):Long=when(s.getColumnType(i)) {
        1->s.getLong(i);2->s.getDouble(i).toLong();3->s.getText(i).toLongOrNull()?:0L;else->0L
    }
    private fun header(s:SQLiteStatement)=MemoryReportRow(s.getText(0),s.getText(1),s.getText(2),long(s,3).toInt(),s.getText(4),long(s,5),long(s,6).toInt(),long(s,7),long(s,8),if(s.isNull(9))null else long(s,9))
    private suspend fun <T> rows(name:String,args:List<Any?>,read:(SQLiteStatement)->T)=data.query(sql.getValue(name),args){s->buildList{while(s.step())add(read(s))}}
    override suspend fun summary(a:String)=data.query(sql.getValue("summary"),listOf(a)){it.step();MemorySummaryRow(it.getLong(0).toInt(),it.getLong(1).toInt())}
    override suspend fun invalidHeaders(a:String)=data.query(sql.getValue("invalidHeaders"),listOf(a)){it.step();it.getLong(0).toInt()}
    override suspend fun list(a:String)=rows("list",listOf(a),::header)
    override suspend fun get(a:String,id:String)=rows("get",listOf(a,id),::header).singleOrNull()
    override suspend fun answers(a:String,id:String)=rows("answers",listOf(a,id)){MemoryAnswerRow(it.getText(0),it.getText(1),it.getText(2),it.getText(3),it.getText(4))}
    override suspend fun addenda(a:String,id:String)=rows("addenda",listOf(a,id)){MemoryAddendumRow(it.getText(0),it.getText(1),long(it,2).toInt(),it.getText(3),long(it,4))}
    override suspend fun invalidAddendumTypes(a:String,id:String)=data.query(sql.getValue("invalidAddendumTypes"),listOf(a,id)){it.step();it.getLong(0).toInt()}
    override suspend fun insert(row:MemoryReportRow){data.change("INSERT INTO memory_reports VALUES(?,?,?,?,?,?,?,?,?,?)",listOf(row.id,row.actionId,row.authorLabel,row.questionnaireVersion.toLong(),row.status,row.revision,row.lastSavedSectionOrdinal.toLong(),row.createdAt,row.updatedAt,row.completedAt))}
    override suspend fun insertAnswers(rows:List<MemoryAnswerRow>){for(row in rows)data.change("INSERT INTO memory_answers VALUES(?,?,?,?,?)",listOf(row.actionId,row.reportId,row.questionKey,row.valueKind,row.payload))}
    override suspend fun insertAddendum(row:MemoryAddendumRow){data.change("INSERT INTO memory_addenda VALUES(?,?,?,?,?)",listOf(row.actionId,row.reportId,row.sequence.toLong(),row.content,row.recordedAt))}
    override suspend fun update(row:MemoryReportRow)=data.change("UPDATE memory_reports SET actionId=?,authorLabel=?,questionnaireVersion=?,status=?,revision=?,lastSavedSectionOrdinal=?,createdAt=?,updatedAt=?,completedAt=? WHERE id=?",listOf(row.actionId,row.authorLabel,row.questionnaireVersion.toLong(),row.status,row.revision,row.lastSavedSectionOrdinal.toLong(),row.createdAt,row.updatedAt,row.completedAt,row.id))
    override suspend fun clearSection(a:String,id:String,keys:List<String>){
        if(keys.isNotEmpty())data.change(sql.getValue("clearSection").replace(":keys",keys.joinToString(","){"?"}),listOf(a,id)+keys)
    }
}
