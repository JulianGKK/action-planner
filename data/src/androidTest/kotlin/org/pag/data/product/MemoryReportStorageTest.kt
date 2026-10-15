package org.pag.data.product

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.pag.data.prototype.b2.B2Native
import org.pag.data.prototype.b2.StorageAccessRevoked
import org.pag.domain.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executor

/** Standalone synthetic data-test package, never product files, auth or device credentials. */
class MemoryReportStorageTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var allowed = true; private var generation = 1L; private var denyCommit = false; private var time = 1000L
    private val owners = mutableListOf<SessionDbKeyOwner>()
    private val direct = Executor { it.run() }
    private fun context(scope: AccessScope = AccessScope.READY): StorageContext = object : StorageContext {
        private val captured = this@MemoryReportStorageTest.generation
        override val generation = captured; override val scope = scope
        override fun check(point: AccessPoint): Boolean {
            if (denyCommit && point == AccessPoint.COMMIT) allowed = false
            return allowed && captured == this@MemoryReportStorageTest.generation
        }
        override fun publish(result: () -> Unit): Boolean { if (!check(AccessPoint.PUBLICATION)) return false; result(); return true }
    }
    private fun fixture(): ProductInventory {
        val root = File(app.noBackupFilesDir.canonicalFile, "p9-test-${UUID.randomUUID()}")
        check(root.mkdirs()); check(root.resolve("store").mkdir())
        listOf("intent", "complete", "public").forEach { root.resolve(it).writeBytes(byteArrayOf(1)) }
        root.resolve("envelope").writeBytes(ByteArray(256))
        val i = ProductInventory(root, { true }, { true })
        ActionDatabaseAccess(app).use(i.database, key, context(AccessScope.MAINTENANCE), true) { d -> d.withWriteTransaction {
            listOf("a", "b").forEach { d.actions().insert(ActionRow(it, "synthetic", "synthetic", 1, 2, 0, "PREPARATION")) }
            d.persons().insert(PersonRow("p", "a", "Motte", "motte", 0)); d.persons().insert(PersonRow("q", "b", "Meise", "meise", 0))
            val store = TaskStore(d, context()) { time }
            value(store.create("a", TaskInput("synthetic task"))); value(store.create("b", TaskInput("synthetic foreign task")))
            d.actions().update(d.actions().get("a")!!.copy(status="ACTIVE"))
        } }
        return i
    }
    private fun <T> use(i: ProductInventory, work: suspend (ActionDatabase) -> T): T = ActionDatabaseAccess(app).use(i.database, key, context(), work = work)
    private fun <T> write(i: ProductInventory, work: suspend (MemoryReportStore) -> OperationsResult<T>): OperationsResult<T> {
        val c = context()
        return ActionDatabaseAccess(app).use(i.database, key, c) { d -> d.withWriteTransaction {
            val result = work(MemoryReportStore(d, c) { time }); requireOperations(c, AccessPoint.COMMIT); result
        } }
    }
    private fun <T> value(r: OperationsResult<T>): T { assertTrue(r is OperationsResult.Value); return (r as OperationsResult.Value).value }
    private fun <T> protocolValue(r: ProtocolResult<T>): T { assertTrue(r is ProtocolResult.Value); return (r as ProtocolResult.Value).value }
    private fun reject(r: OperationsResult<*>, why: OperationsFailure) = assertEquals(OperationsResult.Rejected(why), r)
    private fun task(i: ProductInventory, a: String = "a") = use(i) { it.tasks().list(a).single().model() }
    private fun first(answers:Map<String,MemoryAnswerValue> = emptyMap()) = MemorySectionSave(0,answers)
    private fun create(i:ProductInventory, answers:Map<String,MemoryAnswerValue> = emptyMap()) = value(write(i){it.create("a","Motte",first(answers))})
    private fun detail(i:ProductInventory,h:MemoryReportHeader) = value(use(i){d->d.withReadTransaction{MemoryReportStore(d,context()){time}.detail("a",h.id)}})
    private fun finish(i:ProductInventory,initial:MemoryReportHeader):MemoryReportHeader = value(write(i){ store -> var h=initial; for(section in 1..16)h=value(store.saveSection("a",h,MemorySectionSave(section,emptyMap()))); store.complete("a",h) })
    private fun state(i: ProductInventory, phase: ActionStatus) = use(i) { d -> d.withWriteTransaction {
        d.actions().update(d.actions().get("a")!!.copy(status = phase.name, completedAt = time.takeIf { phase == ActionStatus.COMPLETED }))
    } }
    private fun native(i: ProductInventory, sql: String) = B2Native.open(i.database, key, context(AccessScope.MAINTENANCE)).use { d -> d.execSQL("PRAGMA foreign_keys=ON"); d.execSQL(sql) }
    private fun repo(i: ProductInventory, main: Executor = direct, worker: Executor = direct): MemoryReportRepository {
        val owner = SessionDbKeyOwner.bind(context(), key); owners += owner
        val access = object : StorageAccess { override fun acquire(scope: AccessScope) = context(scope).takeIf { it.check(AccessPoint.OPERATION) } }
        return ProductMemoryReportRepository(app, access, i, { owner }, worker, main, { fail("P9 unexpected storage block") }) { time }
    }
    private fun reset() { allowed = true; denyCommit = false; generation++ }
    @After fun wipe() { owners.forEach { it.invalidate() }; key.fill(0) }
    @Test fun freshTenExactlyThreeEntitiesAndActionBoundForeignKeys(){val i=fixture();B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{d->assertEquals(11,d.version);for(table in listOf("memory_reports","memory_answers","memory_addenda")){d.rawQuery("PRAGMA foreign_key_list($table)",emptyArray<String>()).use{c->while(c.moveToNext())assertEquals(if(table=="memory_reports")"actions" else "memory_reports",c.getString(2))}}};assertEquals(MemoryReportSummary(0,0),value(use(i){MemoryReportStore(it,context()){time}.summary("a")}))}
    @Test fun activeAndCompletedCreateAtomicEmptyAAndPersonSnapshot(){val i=fixture();for(s in listOf(ActionStatus.ACTIVE,ActionStatus.COMPLETED)){state(i,s);val h=create(i);assertEquals(0L,h.revision);assertEquals(0,h.lastSavedSectionOrdinal);assertTrue(detail(i,h).answers.isEmpty());assertEquals("Motte",h.authorLabel)};native(i,"UPDATE persons SET pseudonym='New',pseudonymNormalized='new' WHERE id='p'");native(i,"DELETE FROM persons WHERE id='p'");assertEquals(listOf("Motte","Motte"),value(use(i){MemoryReportStore(it,context()){time}.list("a")}).map{it.authorLabel})}
    @Test fun forbiddenPhasesRejectEveryAccessAndWrite(){val i=fixture();val h=create(i);for(s in listOf(ActionStatus.DRAFT,ActionStatus.PREPARATION,ActionStatus.DELETION_PENDING)){state(i,s);use(i){d->val st=MemoryReportStore(d,context()){time};reject(st.summary("a"),OperationsFailure.NOT_ALLOWED);reject(st.list("a"),OperationsFailure.NOT_ALLOWED);reject(st.detail("a",h.id),OperationsFailure.NOT_ALLOWED)};reject(write(i){it.create("a","Motte",first())},OperationsFailure.NOT_ALLOWED);reject(write(i){it.saveSection("a",h,MemorySectionSave(1,emptyMap()))},OperationsFailure.NOT_ALLOWED);reject(write(i){it.complete("a",h)},OperationsFailure.NOT_ALLOWED);reject(write(i){it.addAddendum("a",h,"x")},OperationsFailure.NOT_ALLOWED)}}
    @Test fun invalidCreateLeavesNoReport(){val i=fixture();for(section in listOf(MemorySectionSave(1,emptyMap()),first(mapOf("BOGUS" to MemoryAnswerValue.Text("x"))),first(mapOf("B_ENDING_MODE" to MemoryAnswerValue.Multi(listOf("SELF_LEFT")))),first(mapOf("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("x".repeat(8001)))))){reject(write(i){it.create("a","Motte",section)},OperationsFailure.INVALID)};reject(write(i){it.create("a"," ",first())},OperationsFailure.INVALID);assertTrue(value(use(i){MemoryReportStore(it,context()){time}.list("a")}).isEmpty())}
    @Test fun progressCannotSkipAndEmptySectionCounts(){val i=fixture();var h=create(i);reject(write(i){it.saveSection("a",h,MemorySectionSave(2,emptyMap()))},OperationsFailure.NOT_ALLOWED);reject(write(i){it.complete("a",h)},OperationsFailure.NOT_ALLOWED);for(n in 1..16){h=value(write(i){it.saveSection("a",h,MemorySectionSave(n,emptyMap()))});assertEquals(n,h.lastSavedSectionOrdinal)};h=value(write(i){it.saveSection("a",h,first())});assertEquals(16,h.lastSavedSectionOrdinal);assertEquals(17L,h.revision);val done=value(write(i){it.complete("a",h)});assertEquals(18L,done.revision);assertEquals(time,done.completedAt)}
    @Test fun sectionReplacementPreservesOtherSectionsAndLabelOnlyInA(){val i=fixture();val a=create(i,mapOf("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("synthetic, 🦋\n  memory")));val b=value(write(i){it.saveSection("a",a,MemorySectionSave(1,mapOf("B_THREATS_OR_PRESSURE" to MemoryAnswerValue.Option("UNKNOWN"))))});val changed=value(write(i){it.saveSection("a",b,MemorySectionSave(0,emptyMap(),authorLabel=" Meise "))});assertEquals("Meise",changed.authorLabel);assertEquals(1,changed.lastSavedSectionOrdinal);assertEquals(mapOf("B_THREATS_OR_PRESSURE" to MemoryAnswerValue.Option("UNKNOWN")),detail(i,changed).answers);reject(write(i){it.saveSection("a",changed,MemorySectionSave(1,emptyMap(),authorLabel="other"))},OperationsFailure.INVALID)}
    @Test fun inactiveDetailNeedsExactRetainedDecisionAndExplicitRemoval(){val i=fixture();val a=create(i);val section=MemorySectionSave(1,mapOf("B_AGREEMENT_OR_CONDITION" to MemoryAnswerValue.Option("YES"),"B_AGREEMENT_DETAILS" to MemoryAnswerValue.Text("synthetic")));val b=value(write(i){it.saveSection("a",a,section)});val changed=section.copy(answers=section.answers+("B_AGREEMENT_OR_CONDITION" to MemoryAnswerValue.Option("NO")));reject(write(i){it.saveSection("a",b,changed)},OperationsFailure.INVALID);val kept=value(write(i){it.saveSection("a",b,changed.copy(retainedConflicts=setOf("B_AGREEMENT_DETAILS")))});assertTrue(detail(i,kept).answers.containsKey("B_AGREEMENT_DETAILS"));val removed=value(write(i){it.saveSection("a",kept,changed.copy(answers=changed.answers-"B_AGREEMENT_DETAILS"))});assertFalse(detail(i,removed).answers.containsKey("B_AGREEMENT_DETAILS"))}
    @Test fun staleUsesFullHeaderForSaveCompleteAndAddendum(){val i=fixture();val h=create(i);for(old in listOf(h.copy(authorLabel="Meise"),h.copy(revision=1),h.copy(updatedAt=0),h.copy(actionId="b"),h.copy(lastSavedSectionOrdinal=1))){reject(write(i){it.saveSection("a",old,MemorySectionSave(1,emptyMap()))},OperationsFailure.STALE);reject(write(i){it.complete("a",old)},OperationsFailure.STALE);reject(write(i){it.addAddendum("a",old,"x")},OperationsFailure.STALE)};val updated=value(write(i){it.saveSection("a",h,MemorySectionSave(1,emptyMap()))});reject(write(i){it.saveSection("a",h,MemorySectionSave(1,emptyMap()))},OperationsFailure.STALE);assertEquals(updated,detail(i,updated).header)}
    @Test fun completionImmutableAnswersAndAppendOrderDespiteClock(){val i=fixture();var h=finish(i,create(i,mapOf("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("synthetic original"))));val original=detail(i,h).answers;reject(write(i){it.saveSection("a",h,first())},OperationsFailure.NOT_ALLOWED);for(t in listOf(1000L,1000L,900L)){time=t;h=value(write(i){it.addAddendum("a",h," synthetic addition ")})};val d=detail(i,h);assertEquals(listOf(0,1,2),d.addenda.map{it.sequence});assertEquals(listOf(1000L,1000L,900L),d.addenda.map{it.recordedAt});assertEquals(original,d.answers);reject(write(i){it.addAddendum("a",h," ")},OperationsFailure.INVALID)}
    @Test fun draftRejectsAddendumAndRevisionOverflowNeverWraps(){val i=fixture();val h=create(i);reject(write(i){it.addAddendum("a",h,"x")},OperationsFailure.NOT_ALLOWED);native(i,"UPDATE memory_reports SET revision=9223372036854775807 WHERE id='${h.id}'");val max=detail(i,h).header;assertThrows(ProductStorageFailure::class.java){write(i){it.saveSection("a",max,first())}};assertEquals(Long.MAX_VALUE,detail(i,h).header.revision)}
    @Test fun malformedAnswersFailClosed(){for((k,kind,payload) in listOf(Triple("UNKNOWN_KEY","TEXT","x"),Triple("B_ENDING_MODE","MULTI","SELF_LEFT"),Triple("A_ALLEGATION","OPTION","YES"),Triple("A_FREE_NARRATIVE","BOGUS","x"),Triple("A_FREE_NARRATIVE","TEXT"," x "),Triple("A_FREE_NARRATIVE","COUNT","1"))){val i=fixture();val h=create(i);native(i,"INSERT INTO memory_answers VALUES('a','${h.id}','$k','$kind','$payload')");assertThrows(ProductStorageFailure::class.java){detail(i,h)}}}
    @Test fun malformedMultiAndCountFailClosed(){for((k,kind,payload) in listOf(Triple("L_ED_MEASURES","MULTI","FACE_PHOTOS,FACE_PHOTOS"),Triple("L_ED_MEASURES","MULTI","OTHER_PHOTOS,FACE_PHOTOS"),Triple("L_ED_MEASURES","MULTI","BOGUS"),Triple("L_PHOTO_COUNT","COUNT","01"),Triple("L_PHOTO_COUNT","COUNT","100000"),Triple("L_PHOTO_COUNT","COUNT","-1"))){val i=fixture();val h=finish(i,create(i));native(i,"INSERT INTO memory_answers VALUES('a','${h.id}','$k','$kind','$payload')");assertThrows(ProductStorageFailure::class.java){detail(i,h)}}}
    @Test fun malformedHeadersIncludingSqliteRealReject(){for(sql in listOf("questionnaireVersion=2","status='UNKNOWN'","revision=-1","revision=0.5","lastSavedSectionOrdinal=17","authorLabel=' Motte '","createdAt=-1","updatedAt=0.5","completedAt=1000","status='COMPLETED',completedAt=1000","status='COMPLETED',lastSavedSectionOrdinal=16,completedAt=999")){val i=fixture();val h=create(i);native(i,"UPDATE memory_reports SET $sql WHERE id='${h.id}'");assertThrows(ProductStorageFailure::class.java){detail(i,h)}}}
    @Test fun addendumGapDraftChildAndInvalidContentFail(){for(mode in listOf("gap","draft","blank","real","overflow")){val i=fixture();val h=if(mode=="draft")create(i)else finish(i,create(i));native(i,"INSERT INTO memory_addenda VALUES('a','${h.id}',${if(mode=="gap")1 else if(mode=="real")0.5 else if(mode=="overflow")4294967296L else 0},'${if(mode=="blank")" " else "synthetic"}',1000)");assertThrows(ProductStorageFailure::class.java){detail(i,h)}}}
    @Test fun summaryAndListReadOnlyHeaderEvenWhenChildrenMalformed(){val i=fixture();val draft=create(i);val done=finish(i,create(i));native(i,"INSERT INTO memory_answers VALUES('a','${draft.id}','BOGUS','TEXT','SENTINEL')");assertEquals(MemoryReportSummary(1,1),value(use(i){MemoryReportStore(it,context()){time}.summary("a")}));assertEquals(listOf(draft,done).sortedWith(MemoryReportRules.order),value(use(i){MemoryReportStore(it,context()){time}.list("a")}));assertThrows(ProductStorageFailure::class.java){detail(i,draft)}}
    @Test fun actionCascadeRemovesAllAndSameActionChildFk(){val i=fixture();val h=finish(i,create(i));value(write(i){it.addAddendum("a",h,"synthetic")});assertThrows(RuntimeException::class.java){use(i){d->d.withWriteTransaction{d.memoryReports().insertAnswers(listOf(MemoryAnswerRow("b",h.id,"A_FREE_NARRATIVE","TEXT","x")))}}};native(i,"DELETE FROM actions WHERE id='a'");use(i){d->assertTrue(d.memoryReports().list("a").isEmpty());assertTrue(d.memoryReports().answers("a",h.id).isEmpty());assertTrue(d.memoryReports().addenda("a",h.id).isEmpty())}}
    @Test fun commitRevokeRollsBackAllFourMutations(){for(op in listOf("create","section","complete","addendum")){val i=fixture();var h=create(i);if(op in listOf("complete","addendum")){for(n in 1..16)h=value(write(i){it.saveSection("a",h,MemorySectionSave(n,emptyMap()))});if(op=="addendum")h=value(write(i){it.complete("a",h)})};val before=detail(i,h);denyCommit=true;assertThrows(RuntimeException::class.java){write(i){when(op){"create"->it.create("a","Meise",first());"section"->it.saveSection("a",h,first(mapOf("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("new"))));"complete"->it.complete("a",h);else->it.addAddendum("a",h,"new")}}};reset();assertEquals(before,detail(i,h));assertEquals(1,value(use(i){MemoryReportStore(it,context()){time}.list("a")}).size)}}
    @Test fun committedBeforePublicationSurvivesWithNoCallback(){val i=fixture();val q=mutableListOf<Runnable>();var calls=0;repo(i,Executor{q+=it}).create("a","Motte",first()){calls++};generation++;q.forEach{it.run()};assertEquals(0,calls);val h=value(use(i){MemoryReportStore(it,context()){time}.list("a")}).single();assertEquals(0,h.lastSavedSectionOrdinal)}
    @Test fun childFailureRollsBackCreateAndSectionReplacement(){val i=fixture();val h=create(i,mapOf("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("old")));native(i,"CREATE TRIGGER synthetic_memory_failure BEFORE INSERT ON memory_answers BEGIN SELECT RAISE(ABORT,'SYNTHETIC_CHILD_FAILURE'); END");assertThrows(RuntimeException::class.java){create(i,mapOf("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("new")))};assertThrows(RuntimeException::class.java){write(i){it.saveSection("a",h,first(mapOf("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("new"))))}};assertEquals(MemoryAnswerValue.Text("old"),detail(i,h).answers["A_FREE_NARRATIVE"])}
    @Test fun migrationNineToTenPreservesExistingTablesTriggersAndP6P7P8(){val i=fixture();val material=value(writeMaterial(i));val expense=value(use(i){d->d.withWriteTransaction{ExpenseStore(d,context()){time}.create("a",ExpenseInput("synthetic cost",1,listOf(ExpensePaymentInput("Motte",1))))}});val p6=use(i){d->d.withWriteTransaction{protocolValue(ProtocolStore(d,context()){time}.create("a",ActionStatus.ACTIVE,ProtocolInput(ProtocolClassification.RELEVANT,"synthetic P6",1)))}};val before=use(i){protocolValue(ProtocolStore(it,context()){time}.detail("a",p6.entryId))};downgrade(i);val old=tables(i);val oldTriggers=triggers(i);create(i);assertEquals(old+setOf("memory_reports","memory_answers","memory_addenda","documents","document_jobs"),tables(i));assertEquals(oldTriggers,triggers(i).filterKeys { it in oldTriggers });assertEquals(material,value(use(i){MaterialStore(it,context()){time}.board("a")}).items.single());assertEquals(expense,value(use(i){ExpenseStore(it,context()){time}.board("a")}).expenses.single());assertEquals(before,use(i){protocolValue(ProtocolStore(it,context()){time}.detail("a",p6.entryId))})}
    private fun writeMaterial(i:ProductInventory)=use(i){d->d.withWriteTransaction{MaterialStore(d,context()){time}.create("a",MaterialInput("synthetic material"))}}
    @Test fun migrationRevokeRetryDoesNotReplaceNine(){val i=fixture();val task=task(i);downgrade(i);denyCommit=true;assertThrows(RuntimeException::class.java){use(i){}};reset();B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{assertEquals(9,it.version)};assertFalse(tables(i).contains("memory_reports"));assertEquals(task,task(i));B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{assertEquals(11,it.version)}}
    private fun downgrade(i:ProductInventory)=B2Native.open(i.database,key,context(AccessScope.MAINTENANCE)).use{d->d.beginTransaction();try{removeMemorySchemaForLegacyFixture(d);d.execSQL("UPDATE room_master_table SET identity_hash='b1cfaee1d851c288902d8a10b7fbfa62' WHERE id=42");d.version=9;d.setTransactionSuccessful()}finally{d.endTransaction()}}
    private fun tables(i:ProductInventory):Set<String> = B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{d->val values=mutableSetOf<String>();d.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",emptyArray<String>()).use{while(it.moveToNext())values+=it.getString(0)};values}
    private fun triggers(i:ProductInventory):Map<String,String> = B2Native.open(i.database,key,context(AccessScope.MAINTENANCE),true).use{d->val values=mutableMapOf<String,String>();d.rawQuery("SELECT name,sql FROM sqlite_master WHERE type='trigger'",emptyArray<String>()).use{while(it.moveToNext())values[it.getString(0)]=it.getString(1)};values}
}
