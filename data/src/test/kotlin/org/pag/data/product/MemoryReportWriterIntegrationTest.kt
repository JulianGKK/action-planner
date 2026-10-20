package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

private typealias MemoryHarness = CoreWriterIntegrationTest.H

/** Actual store/boundary/outer transaction on real SQLite/schema 11. Authority, outer Room
 * end and key/database cleanup faults are simulations, never native cleanup evidence. */
class MemoryReportWriterIntegrationTest {
    private fun harness()=MemoryHarness().also{h->
        h.exec("UPDATE actions SET status='ACTIVE' WHERE id='a'")
        runBlocking{h.db.installHostProtocolConstraints()} // cross-family Protocol uses its actual triggers too
    }
    private fun first(text:String="Synthetic narrative")=MemorySectionSave(0,linkedMapOf(
        "A_FREE_NARRATIVE" to MemoryAnswerValue.Text(text),"A_ALLEGATION" to MemoryAnswerValue.Option("UNKNOWN")))
    private fun second()=MemorySectionSave(1,linkedMapOf(
        "B_ENDING_MODE" to MemoryAnswerValue.Multi(listOf("AFTER_REQUEST","SELF_LEFT")),
        "B_AGREEMENT_OR_CONDITION" to MemoryAnswerValue.Option("YES"),
        "B_AGREEMENT_DETAILS" to MemoryAnswerValue.Text("Synthetic detail")))
    private fun <T> value(r:OperationsResult<T>)=(r as OperationsResult.Value).value
    private fun <T> MemoryHarness.writeMemory(work:suspend(MemoryReportStore)->OperationsResult<T>)=
        write<OperationsResult<T>>(WorkspaceWriterFamily.MEMORY_REPORT,{it is OperationsResult.Rejected},
            {OperationsResult.Rejected(OperationsFailure.STALE)}){work(MemoryReportStore(data,it){++time})}
    private fun MemoryHarness.queueMemory(publish:(OperationsResult<MemoryReportHeader>)->Unit={},work:suspend(MemoryReportStore)->OperationsResult<MemoryReportHeader>)=
        queue<OperationsResult<MemoryReportHeader>>(WorkspaceWriterFamily.MEMORY_REPORT,{it is OperationsResult.Rejected},
            {OperationsResult.Rejected(OperationsFailure.STALE)},publish){work(MemoryReportStore(data,it){++time})}
    private fun MemoryHarness.create(section:MemorySectionSave=first()):MemoryReportHeader {
        val frozen=freezeMemorySection(section);return value(writeMemory{it.create("a","Synthetic author",frozen)})
    }
    private fun MemoryHarness.save(expected:MemoryReportHeader,section:MemorySectionSave):OperationsResult<MemoryReportHeader> {
        val frozen=freezeMemorySection(section);return writeMemory{it.saveSection("a",expected,frozen)}
    }
    private fun MemoryHarness.detail(id:String)=runBlocking{value(MemoryReportStore(data,context){0}.detail("a",id))}
    private fun MemoryHarness.through16(initial:MemoryReportHeader)=value(writeMemory{store->
        var h=initial
        for(n in (initial.lastSavedSectionOrdinal+1)..16)h=value(store.saveSection("a",h,MemorySectionSave(n,emptyMap())))
        OperationsResult.Value(h)
    })
    private fun MemoryHarness.prepare(operation:String):MemoryReportHeader {
        val h=create()
        return when(operation){"complete"->through16(h);"addendum"->{val ready=through16(h);value(writeMemory{it.complete("a",ready)})};else->h}
    }
    private suspend fun MemoryReportStore.mutate(operation:String,expected:MemoryReportHeader)=when(operation){
        "create"->create("a","Synthetic new author",first("New narrative"))
        "section"->saveSection("a",expected,first("Changed narrative"))
        "complete"->complete("a",expected)
        else->addAddendum("a",expected,"Synthetic addition")
    }
    private val committed=WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)
    private val rolledBack=WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)
    private val tables=listOf("memory_reports","memory_answers","memory_addenda")
    private fun MemoryHarness.snapshot():List<List<List<Any?>>> = tables.map{table->runBlocking{
        data.query("SELECT * FROM $table ORDER BY rowid"){s->buildList{while(s.step())add((0 until s.getColumnCount()).map{i->
            when(s.getColumnType(i)){1->s.getLong(i);2->s.getDouble(i);3->s.getText(i);else->null}
        })}}
    }}

    @Test fun createAndSectionReplacementKeepCompleteHeaderAndOtherAnswers()=harness().use{h->
        val a=h.create();assertEquals(0L,a.revision);assertEquals(0,a.lastSavedSectionOrdinal)
        assertEquals(first().answers,h.detail(a.id).answers)
        val b=value(h.save(a,second()));assertEquals(1L,b.revision);assertEquals(1,b.lastSavedSectionOrdinal)
        val values=h.detail(a.id).answers
        assertEquals(MemoryAnswerValue.Multi(listOf("SELF_LEFT","AFTER_REQUEST")),values["B_ENDING_MODE"])
        assertEquals(first().answers,values.filterKeys{it.startsWith("A_")})
        val c=value(h.save(b,first("Replaced").copy(authorLabel=" Synthetic renamed author ")))
        assertEquals("Synthetic renamed author",c.authorLabel);assertEquals(2L,c.revision);assertEquals(1,c.lastSavedSectionOrdinal)
        assertEquals(second().answers.keys,h.detail(a.id).answers.keys.filter{it.startsWith("B_")}.toSet())
        assertEquals(MemoryAnswerValue.Text("Replaced"),h.detail(a.id).answers["A_FREE_NARRATIVE"])
        val epoch=h.epoch.mutationEpoch
        runBlocking{val store=MemoryReportStore(h.data,h.context){0}
            assertEquals(MemoryReportSummary(1,0),value(store.summary("a")));assertEquals(listOf(c),value(store.list("a")))}
        assertEquals(epoch,h.epoch.mutationEpoch);assertEquals(listOf(committed,committed,committed),h.completions)
        assertFalse(h.epoch.ramReuseEnabled)
    }
    @Test fun inactiveDependentAnswersRequireExactConflictDecision()=harness().use{h->
        val a=h.create();val b=value(h.save(a,second()));val before=h.snapshot()
        val changed=second().copy(answers=second().answers+("B_AGREEMENT_OR_CONDITION" to MemoryAnswerValue.Option("NO")))
        for(conflicts in listOf(emptySet(),setOf("BOGUS"),setOf("B_AGREEMENT_DETAILS","B_THREATS_DETAILS")))
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.save(b,changed.copy(retainedConflicts=conflicts)))
        assertEquals(before,h.snapshot())
        val kept=value(h.save(b,changed.copy(retainedConflicts=setOf("B_AGREEMENT_DETAILS"))))
        assertTrue(h.detail(a.id).answers.containsKey("B_AGREEMENT_DETAILS"))
        val removed=value(h.save(kept,changed.copy(answers=changed.answers-"B_AGREEMENT_DETAILS")))
        assertFalse(h.detail(a.id).answers.containsKey("B_AGREEMENT_DETAILS"));assertEquals(3L,removed.revision)
    }
    @Test fun invalidQuestionsOptionsCountsAuthorsAndSectionOrderAreRejectedBeforeMutation()=harness().use{h->
        val a=h.create();val before=h.snapshot()
        for(section in listOf(MemorySectionSave(-1,emptyMap()),MemorySectionSave(17,emptyMap()),
            first().copy(answers=mapOf("BOGUS" to MemoryAnswerValue.Text("X"))),first().copy(answers=second().answers),
            first("🦋".repeat(8001)),first().copy(authorLabel="Different"))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.writeMemory{it.create("a","Synthetic author",section)})
        }
        for(author in listOf(" ","🦋".repeat(81)))
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.writeMemory{it.create("a",author,first())})
        for(section in listOf(first().copy(answers=mapOf("B_ENDING_MODE" to MemoryAnswerValue.Multi(listOf("SELF_LEFT")))),
            first().copy(answers=mapOf("A_ALLEGATION" to MemoryAnswerValue.Option("YES"))),first().copy(authorLabel=" ")))
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.save(a,section))
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),h.save(a,MemorySectionSave(2,emptyMap())))
        for(keys in listOf(emptyList(),listOf("BOGUS"),listOf("SELF_LEFT","SELF_LEFT"),listOf("SELF_LEFT","UNCLEAR")))
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.save(a,MemorySectionSave(1,mapOf("B_ENDING_MODE" to MemoryAnswerValue.Multi(keys)))))
        assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.save(a,MemorySectionSave(1,emptyMap(),authorLabel="Other")))
        assertEquals(before,h.snapshot());assertTrue(h.completions.drop(1).all{it==rolledBack})
    }
    @Test fun completeAndAddendumUsePersistedPhaseRevisionAndSequence()=harness().use{h->
        val a=h.create()
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),h.writeMemory{it.complete("a",a)})
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),h.writeMemory{it.addAddendum("a",a,"Synthetic")})
        val through=h.through16(a);assertEquals(16,through.lastSavedSectionOrdinal);assertEquals(16L,through.revision)
        val done=value(h.writeMemory{it.complete("a",through)})
        assertEquals(MemoryReportStatus.COMPLETED,done.status);assertEquals(done.updatedAt,done.completedAt);assertEquals(17L,done.revision)
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),h.save(done,first()))
        val oldAnswers=h.detail(a.id).answers
        var current=done
        for(time in listOf(500L,500L,400L)){
            h.time=time-1;current=value(h.writeMemory{it.addAddendum("a",current," Synthetic addition ")})
        }
        val detail=h.detail(a.id)
        assertEquals(listOf(0,1,2),detail.addenda.map{it.sequence});assertEquals(listOf(500L,500L,400L),detail.addenda.map{it.recordedAt})
        assertEquals(oldAnswers,detail.answers);assertEquals(done.completedAt,current.completedAt);assertEquals(20L,current.revision)
        assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.writeMemory{it.addAddendum("a",current," ")})
        assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.writeMemory{it.addAddendum("a",current,"x".repeat(4001))})
        runBlocking{assertEquals(MemoryReportSummary(0,1),value(MemoryReportStore(h.data,h.context){0}.summary("a")))}
    }
    @Test fun fullExpectedHeaderCasRejectsEveryChangedFieldForAllFollowupWrites()=harness().use{h->
        val a=h.create();val before=h.snapshot()
        for(old in listOf(a.copy(actionId="b"),a.copy(authorLabel="Other"),a.copy(questionnaireVersion=2),a.copy(status=MemoryReportStatus.COMPLETED),
            a.copy(revision=1),a.copy(lastSavedSectionOrdinal=1),a.copy(createdAt=999),a.copy(updatedAt=999),a.copy(completedAt=999))){
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.save(old,first()))
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.writeMemory{it.complete("a",old)})
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.writeMemory{it.addAddendum("a",old,"Synthetic")})
        }
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.save(a.copy(id="missing"),first()))
        assertEquals(before,h.snapshot());assertTrue(h.completions.drop(1).all{it==rolledBack})
    }
    @Test fun twoEditorsAndConcurrentAddendaCannotLoseConfirmedRevision()=harness().use{h->
        val a=h.create();val b=value(h.save(a,second()));val after=h.snapshot()
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.save(a,first("Stale")));assertEquals(after,h.snapshot())
        val ready=h.through16(b);val done=value(h.writeMemory{it.complete("a",ready)})
        val first=value(h.writeMemory{it.addAddendum("a",done,"First synthetic addition")})
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.writeMemory{it.addAddendum("a",done,"Second stale addition")})
        assertEquals(listOf("First synthetic addition"),h.detail(a.id).addenda.map{it.content})
        val next=value(h.writeMemory{it.addAddendum("a",first,"New editor")});assertEquals(first.revision+1,next.revision)
        assertEquals(listOf(0,1),h.detail(a.id).addenda.map{it.sequence})
    }
    @Test fun everySqlBoundaryRollsBackTheCompleteHeaderAnswersAndAddendumState() {
        val cases=listOf("create" to "header","create" to "answer-first","create" to "answer-second",
            "section" to "clear","section" to "answer-first","section" to "answer-second","section" to "update",
            "complete" to "update","addendum" to "addendum","addendum" to "update")
        for((operation,at) in cases)harness().use{h->
            val old=h.prepare(operation);val before=h.snapshot();h.completions.clear();var answer=0;var injected=false
            h.data.afterChange={sql->
                val fail=when(at){"header"->sql.startsWith("INSERT INTO memory_reports ");"clear"->sql.startsWith("DELETE FROM memory_answers ");
                    "update"->sql.startsWith("UPDATE memory_reports ");"addendum"->sql.startsWith("INSERT INTO memory_addenda ");
                    "answer-first"->sql.startsWith("INSERT INTO memory_answers ") && ++answer==1
                    else->sql.startsWith("INSERT INTO memory_answers ") && ++answer==2}
                if(fail){injected=true;error("SYNTHETIC_MEMORY_WRITE_FAILURE")}
            }
            var calls=0;h.queueMemory({calls++}){it.mutate(operation,old)};h.worker.drain();h.main.drain()
            assertTrue("$operation/$at",injected);assertEquals(0,calls);assertEquals("$operation/$at",before,h.snapshot())
            assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome);assertEquals(1,h.blocks)
        }
    }
    @Test fun actualSqlTriggerFailureAfterAnswerAndBeforeHeaderUpdateRollsBackEverything() {
        for(operation in listOf("create","section","complete","addendum"))harness().use{h->
            val old=h.prepare(operation);val before=h.snapshot();h.completions.clear()
            if(operation in listOf("create","section"))
                h.exec("CREATE TRIGGER synthetic_memory_failure BEFORE INSERT ON memory_answers WHEN NEW.questionKey='A_ALLEGATION' BEGIN SELECT RAISE(ABORT,'SYNTHETIC_FAILURE'); END")
            else h.exec("CREATE TRIGGER synthetic_memory_failure BEFORE UPDATE ON memory_reports BEGIN SELECT RAISE(ABORT,'SYNTHETIC_FAILURE'); END")
            var calls=0;h.queueMemory({calls++}){it.mutate(operation,old)};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        }
    }
    @Test fun failedCreateLeavesNoHeaderOrAnswerResidue() {
        for(at in listOf("header","answer"))harness().use{h->
            h.data.afterChange={sql->if(sql.startsWith(if(at=="header")"INSERT INTO memory_reports " else "INSERT INTO memory_answers "))error("SYNTHETIC_FAILURE")}
            var calls=0;h.queueMemory({calls++}){it.create("a","Synthetic",first())};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertTrue(tables.all{h.count(it)==0L})
        }
    }
    @Test fun revisionOverflowNeverWritesAnswersStatusOrAddenda() {
        for(operation in listOf("section","complete","addendum"))harness().use{h->
            val old=h.prepare(operation);h.exec("UPDATE memory_reports SET revision=9223372036854775807 WHERE id='${old.id}'")
            val max=h.detail(old.id).header;val before=h.snapshot();h.completions.clear();var calls=0
            h.queueMemory({calls++}){it.mutate(operation,max)};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(Long.MAX_VALUE,runBlocking{h.data.memoryReports.get("a",old.id)!!.revision})
            assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        }
    }
    @Test fun foreignMissingActionsAndReportsCannotModifyOtherData()=harness().use{h->
        val old=h.create();val before=h.snapshot()
        for(action in listOf("b","missing")){
            assertEquals(OperationsResult.Rejected(if(action=="b")OperationsFailure.NOT_ALLOWED else OperationsFailure.NOT_FOUND),h.writeMemory{it.saveSection(action,old,first())})
        }
        h.exec("UPDATE actions SET status='ACTIVE' WHERE id='b'")
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.writeMemory{it.saveSection("b",old,first())})
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.writeMemory{it.complete("b",old)})
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.writeMemory{it.addAddendum("b",old,"Synthetic")})
        assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_FOUND),h.writeMemory{it.create("missing","Synthetic",first())})
        assertEquals(before,h.snapshot())
        assertThrows(IllegalStateException::class.java){h.exec("INSERT INTO memory_answers VALUES('b','${old.id}','A_FREE_NARRATIVE','TEXT','Synthetic')")}
        assertThrows(IllegalStateException::class.java){h.exec("INSERT INTO memory_addenda VALUES('b','${old.id}',0,'Synthetic',1)")}
        h.exec("DELETE FROM actions WHERE id='a'");assertTrue(tables.all{h.count(it)==0L})
    }
    @Test fun inactiveActionPhasesKeepEveryWriteDenied() {
        for(status in listOf(ActionStatus.DRAFT,ActionStatus.PREPARATION,ActionStatus.DELETION_PENDING))harness().use{h->
            val old=h.create();val before=h.snapshot();h.exec("UPDATE actions SET status='${status.name}' WHERE id='a'")
            for(operation in operations)assertEquals(OperationsResult.Rejected(OperationsFailure.NOT_ALLOWED),h.writeMemory{it.mutate(operation,old)})
            assertEquals(before,h.snapshot())
        }
        harness().use{h->
            h.exec("UPDATE actions SET status='COMPLETED',completedAt=100 WHERE id='a'")
            val done=value(h.writeMemory{store->var old=value(store.create("a","Synthetic",first()));for(n in 1..16)old=value(store.saveSection("a",old,MemorySectionSave(n,emptyMap())));store.complete("a",old)})
            assertTrue(h.writeMemory{it.addAddendum("a",done,"Synthetic addition")} is OperationsResult.Value)
        }
    }
    @Test fun existingHeaderGuardsRejectRawTypeAndIntWrapBeforeHeaderMaterialization() {
        for(change in listOf("questionnaireVersion=4294967297","questionnaireVersion=-4294967295","questionnaireVersion=1.5","questionnaireVersion='bad'",
            "lastSavedSectionOrdinal=4294967296","lastSavedSectionOrdinal=-4294967296","lastSavedSectionOrdinal=0.5",
            "revision=-1","revision=0.5","revision='bad'","createdAt=-1","createdAt=1.5","updatedAt=1.5",
            "status='UNKNOWN'","completedAt=30","status='COMPLETED',completedAt=30","status='COMPLETED',lastSavedSectionOrdinal=16,completedAt=0"))harness().use{h->
            val old=h.create();h.exec("UPDATE memory_reports SET $change WHERE id='${old.id}'")
            var payloadRead=false;h.db.beforePrepare={if(it.startsWith("SELECT id,actionId,authorLabel"))payloadRead=true}
            assertThrows(ProductStorageFailure::class.java){h.detail(old.id)}
            assertFalse("raw header guard must precede materialization: $change",payloadRead)
            val before=h.snapshot();h.completions.clear();var calls=0
            h.queueMemory({calls++}){it.saveSection("a",old,first())};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        }
        for(label in listOf(" "," Synthetic "))harness().use{h->
            val old=h.create();h.exec("UPDATE memory_reports SET authorLabel='$label'")
            assertThrows(ProductStorageFailure::class.java){h.detail(old.id)}
        }
    }
    @Test fun existingAddendumGuardsRejectSqlTypesWrapGapsAndInvalidContent() {
        for(change in listOf("sequence=4294967296","sequence=-4294967296","sequence=2147483648","sequence=0.5","sequence='bad'",
            "sequence=1","recordedAt=0.5","recordedAt='bad'","recordedAt=-1","content=' '","content=' Synthetic '","content='${"x".repeat(4001)}'"))harness().use{h->
            val old=h.prepare("addendum")
            h.exec("INSERT INTO memory_addenda VALUES('a','${old.id}',0,'Synthetic',1)")
            h.exec("UPDATE memory_addenda SET $change")
            var guarded=false;h.db.beforePrepare={if(it.contains("typeof(sequence)!='integer'"))guarded=true}
            assertThrows(ProductStorageFailure::class.java){h.detail(old.id)};assertTrue(guarded)
            val before=h.snapshot();h.completions.clear();var calls=0
            h.queueMemory({calls++}){it.addAddendum("a",old,"Must not append")};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        }
        harness().use{h->val old=h.create();h.exec("INSERT INTO memory_addenda VALUES('a','${old.id}',0,'Synthetic',1)")
            assertThrows(ProductStorageFailure::class.java){h.detail(old.id)};Unit}
    }
    @Test fun corruptAnswerEncodingRejectsDetailAndWritesButNotContentFreeSummary() {
        for((key,kind,payload) in listOf(Triple("UNKNOWN_KEY","TEXT","Synthetic"),Triple("B_ENDING_MODE","MULTI","SELF_LEFT"),
            Triple("A_ALLEGATION","OPTION","YES"),Triple("A_FREE_NARRATIVE","BOGUS","Synthetic"),
            Triple("A_FREE_NARRATIVE","TEXT"," Synthetic "),Triple("A_FREE_NARRATIVE","COUNT","1")))harness().use{h->
            val old=h.create();h.exec("DELETE FROM memory_answers")
            h.exec("INSERT INTO memory_answers VALUES('a','${old.id}','$key','$kind','$payload')")
            assertThrows(ProductStorageFailure::class.java){h.detail(old.id)}
            runBlocking{val store=MemoryReportStore(h.data,h.context){0}
                assertEquals(MemoryReportSummary(1,0),value(store.summary("a")));assertEquals(listOf(old),value(store.list("a")))}
            val before=h.snapshot();h.completions.clear();var calls=0
            h.queueMemory({calls++}){it.saveSection("a",old,first())};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(before,h.snapshot())
        }
    }
    @Test fun corruptCanonicalMultiAndCountCannotBeReadOrRewritten() {
        for((key,kind,payload) in listOf(Triple("L_ED_MEASURES","MULTI","OTHER_PHOTOS,FACE_PHOTOS"),
            Triple("L_ED_MEASURES","MULTI","FACE_PHOTOS,FACE_PHOTOS"),Triple("L_ED_MEASURES","MULTI","BOGUS"),
            Triple("L_PHOTO_COUNT","COUNT","01"),Triple("L_PHOTO_COUNT","COUNT","100000"),
            Triple("L_PHOTO_COUNT","COUNT","-1"),Triple("L_PHOTO_COUNT","COUNT","4294967296"),
            Triple("D_RECORDING_DEVICE","MULTI","SERVICE_CAMERA,UNKNOWN"),
            Triple("G_SENSITIVE_EXPERIENCE","MULTI","UNUSUAL,NO_ASSESSMENT")))harness().use{h->
            val old=h.prepare("complete");h.exec("DELETE FROM memory_answers")
            h.exec("INSERT INTO memory_answers VALUES('a','${old.id}','$key','$kind','$payload')")
            assertThrows(ProductStorageFailure::class.java){h.detail(old.id)}
            val before=h.snapshot();h.completions.clear();var calls=0
            h.queueMemory({calls++}){it.complete("a",old)};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        }
    }
    @Test fun storePreservesStrictCountAndExclusiveMultiRules()=harness().use{h->
        val ready=h.prepare("complete");val before=h.snapshot()
        for(count in listOf(-1,100_000))assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.save(ready,MemorySectionSave(11,mapOf("L_PHOTO_COUNT" to MemoryAnswerValue.Count(count)))))
        for(q in MemoryQuestionnaireV1.questions.filter{it.type==MemoryAnswerType.MULTI_CHOICE}){
            val exclusive=q.options.firstOrNull{it.key in setOf("UNKNOWN","UNCLEAR","NO_ASSESSMENT")}?:continue
            val regular=q.options.firstOrNull{it.key !in setOf("UNKNOWN","UNCLEAR","NO_ASSESSMENT")}?:continue
            assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),h.save(ready,MemorySectionSave(q.sectionOrdinal,
                mapOf(q.key to MemoryAnswerValue.Multi(listOf(exclusive.key,regular.key))))))
        }
        assertEquals(before,h.snapshot())
        var current=ready
        val conflicts=if(MemoryQuestionnaireV1.question("L_PHOTO_COUNT")!!.dependency!=null)setOf("L_PHOTO_COUNT")else emptySet()
        for(count in listOf(0,99_999)){
            current=value(h.save(current,MemorySectionSave(11,mapOf("L_PHOTO_COUNT" to MemoryAnswerValue.Count(count)),conflicts)))
            assertEquals(MemoryAnswerValue.Count(count),h.detail(current.id).answers["L_PHOTO_COUNT"])
        }
    }
    @Test fun queuedCreateAndSectionKeepOriginalMapSetAndNestedMultiSnapshot()=harness().use{h->
        val answers=linkedMapOf<String,MemoryAnswerValue>("A_FREE_NARRATIVE" to MemoryAnswerValue.Text("Original"))
        val first=freezeMemorySection(MemorySectionSave(0,answers));var created:MemoryReportHeader?=null
        h.queueMemory({created=value(it)}){it.create("a","Synthetic author",first)}
        answers.clear();h.worker.drain();h.main.drain();val old=created!!
        val keys=mutableListOf("AFTER_REQUEST","SELF_LEFT")
        val next=linkedMapOf<String,MemoryAnswerValue>("B_ENDING_MODE" to MemoryAnswerValue.Multi(keys),
            "B_AGREEMENT_OR_CONDITION" to MemoryAnswerValue.Option("NO"),"B_AGREEMENT_DETAILS" to MemoryAnswerValue.Text("Keep intentionally"))
        val conflicts=linkedSetOf("B_AGREEMENT_DETAILS");val frozen=freezeMemorySection(MemorySectionSave(1,next,conflicts))
        var calls=0;h.queueMemory({calls++}){it.saveSection("a",old,frozen)}
        keys.clear();next.clear();conflicts.clear();h.worker.drain();h.main.drain()
        assertEquals(1,calls);val detail=h.detail(old.id)
        assertEquals(MemoryAnswerValue.Multi(listOf("SELF_LEFT","AFTER_REQUEST")),detail.answers["B_ENDING_MODE"])
        assertEquals(MemoryAnswerValue.Text("Keep intentionally"),detail.answers["B_AGREEMENT_DETAILS"])
        assertEquals(MemoryAnswerValue.Text("Original"),detail.answers["A_FREE_NARRATIVE"])
    }
    @Test fun allFourWritesSeparateSqlOutcomeCleanupAndPublicationAcrossRaces() {
        for(operation in operations)for(mode in modes)harness().use{h->
            val old=h.prepare(operation);val before=h.snapshot();h.completions.clear()
            when(mode){"initial"->h.ready=false;"blocked"->h.storageReady=false;"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;
                "db-cleanup"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};"key-cleanup"->h.keyCleanup={error("SYNTHETIC_CLEANUP")};
                "commit"->h.beforeEnd={h.ready=false};"after-commit"->h.afterEnd={h.ready=false};"publication"->h.beforePublication={h.ready=false}}
            if(mode=="during-write")h.data.afterChange={h.ready=false}
            var calls=0;h.queueMemory({calls++}){it.mutate(operation,old)}
            if(mode=="queued")h.ready=false
            val worker=h.worker.jobs.firstOrNull();h.worker.drain();worker?.run()
            when(mode){"permission"->h.permission++;"generation"->h.generation++;"action"->h.action="b"}
            val main=h.main.jobs.firstOrNull();h.main.drain();main?.run()
            assertEquals("$operation/$mode",if(mode=="normal")1 else 0,calls)
            val physicallyCommitted=mode in listOf("normal","after-commit","publication","permission","generation","action","end-after","db-cleanup","key-cleanup")
            if(!physicallyCommitted)assertEquals("$operation/$mode",before,h.snapshot())
            else when(operation){
                "create"->{assertEquals(2L,h.count("memory_reports"));assertEquals(4L,h.count("memory_answers"))}
                "section"->{assertEquals(MemoryAnswerValue.Text("Changed narrative"),runBlocking{MemoryAnswerCodec.decode("A_FREE_NARRATIVE","TEXT",h.data.memoryReports.answers("a",old.id).first{it.questionKey=="A_FREE_NARRATIVE"}.payload)})}
                "complete"->assertEquals(MemoryReportStatus.COMPLETED,runBlocking{h.data.memoryReports.get("a",old.id)!!.model().status})
                else->assertEquals(1L,h.count("memory_addenda"))
            }
            if(physicallyCommitted && operation!="create")assertEquals(old.revision+1,runBlocking{h.data.memoryReports.get("a",old.id)!!.revision})
            if(mode=="initial")assertTrue(h.completions.isEmpty())
            else if(mode.startsWith("end"))assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
            else if(physicallyCommitted)assertEquals(WorkspaceWriteOutcome.COMMITTED,h.completions.single().outcome)
            if(mode.endsWith("cleanup"))assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
            if(mode=="normal")assertEquals(committed,h.completions.single());assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun sharedPendingBlocksAllSixFamiliesInBothDirectionsWithoutBusyDbTransactions() {
        val families=listOf(WorkspaceWriterFamily.MEMORY_REPORT,WorkspaceWriterFamily.TASK,WorkspaceWriterFamily.OPERATIONS,
            WorkspaceWriterFamily.MATERIAL,WorkspaceWriterFamily.EXPENSE,WorkspaceWriterFamily.PROTOCOL)
        for(first in families)harness().use{h->
            h.queue<Any>(first,{it is OperationsResult.Rejected || it is ProtocolResult.Rejected},{null},{}){c->when(first){
                WorkspaceWriterFamily.MEMORY_REPORT->MemoryReportStore(h.data,c){0}.create("a","Synthetic author",first())
                WorkspaceWriterFamily.TASK->TaskStore(HostTaskData(h.db),c){0}.create("a",TaskInput("Synthetic"))
                WorkspaceWriterFamily.OPERATIONS->OperationsStore(h.data,c){0}.role("a",null,"New role")
                WorkspaceWriterFamily.MATERIAL->MaterialStore(h.data,c){0}.create("a",MaterialInput("Synthetic"))
                WorkspaceWriterFamily.EXPENSE->ExpenseStore(h.data,c){0}.create("a",ExpenseInput("Synthetic",1,listOf(ExpensePaymentInput("Synthetic",1))))
                else->ProtocolStore(h.data,c){0}.create("a",ActionStatus.ACTIVE,ProtocolInput(ProtocolClassification.RELEVANT,"Synthetic",1))
            }}
            for(other in families){
                if(other==WorkspaceWriterFamily.PROTOCOL){
                    var busy:ProtocolResult<Unit>?=null
                    h.queue<ProtocolResult<Unit>>(other,{it is ProtocolResult.Rejected},{ProtocolResult.Rejected(ProtocolFailure.STALE)},{busy=it}){error("No busy bypass")}
                    assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),busy)
                }else{
                    var busy:OperationsResult<Unit>?=null
                    h.queue<OperationsResult<Unit>>(other,{it is OperationsResult.Rejected},{OperationsResult.Rejected(OperationsFailure.STALE)},{busy=it}){error("No busy bypass")}
                    assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),busy)
                }
            }
            assertEquals(1L,h.epoch.mutationEpoch);assertEquals(1,h.worker.jobs.size);assertTrue(h.completions.isEmpty())
            h.worker.drain();h.main.drain();assertEquals(listOf(committed),h.completions)
            assertEquals(if(first==WorkspaceWriterFamily.MEMORY_REPORT)1L else 0L,h.count("memory_reports"));assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun duplicateWorkerMainAndLateCallbackHaveOneEffectForEveryOperation() {
        for(operation in operations)harness().use{h->
            val old=h.prepare(operation);h.completions.clear();val epoch=h.epoch.mutationEpoch;var calls=0
            h.queueMemory({calls++;assertEquals(committed,h.completions.single())}){it.mutate(operation,old)}
            val worker=h.worker.jobs.removeFirst();worker.run();worker.run();assertEquals(0,calls)
            val main=h.main.jobs.removeFirst();main.run();main.run();assertEquals(1,calls);assertEquals(1,h.completions.size)
            assertEquals(epoch+1,h.epoch.mutationEpoch)
            if(operation=="create")assertEquals(2L,h.count("memory_reports"))else assertEquals(old.revision+1,runBlocking{h.data.memoryReports.get("a",old.id)!!.revision})
            if(operation=="addendum")assertEquals(1L,h.count("memory_addenda"))
            main.run();assertEquals(1,calls)
        }
    }
    @Test fun newerMutationBeforeOldPublicationSuppressesOldCallbackForEveryOperation() {
        for(operation in operations)harness().use{h->
            val old=h.prepare(operation);h.completions.clear();val epoch=h.epoch.mutationEpoch;var oldCalls=0;var newCalls=0
            h.queueMemory({oldCalls++}){it.mutate(operation,old)};h.worker.drain()
            h.beforePublication={
                h.beforePublication={}
                h.queue<OperationsResult<MaterialItem>>(WorkspaceWriterFamily.MATERIAL,{it is OperationsResult.Rejected},{null},{newCalls++}){
                    MaterialStore(h.data,it){0}.create("a",MaterialInput("Synthetic"))
                }
            }
            val oldMain=h.main.jobs.first();h.main.drain();assertEquals(0,oldCalls)
            assertEquals(committed,h.completions.single());h.worker.drain();h.main.drain();oldMain.run()
            assertEquals(0,oldCalls);assertEquals(1,newCalls);assertEquals(epoch+2,h.epoch.mutationEpoch)
            assertEquals(listOf(committed,committed),h.completions)
        }
    }
    @Test fun lostOrFailedMainDispatchStaysPendingWithoutRetryForAllFourWrites() {
        for(operation in operations)for(fail in listOf(false,true))harness().use{h->
            val old=h.prepare(operation);h.completions.clear();val epoch=h.epoch.mutationEpoch
            if(fail)h.main.onQueue={error("SYNTHETIC_DISPATCH")}
            var calls=0;h.queueMemory({calls++}){it.mutate(operation,old)}
            if(fail)assertThrows(IllegalStateException::class.java){h.worker.drain()}else{h.worker.drain();h.main.jobs.clear()}
            assertEquals(0,calls);assertTrue(h.completions.isEmpty())
            if(operation=="create")assertEquals(2L,h.count("memory_reports"))else assertEquals(old.revision+1,runBlocking{h.data.memoryReports.get("a",old.id)!!.revision})
            var busy:OperationsResult<MemoryReportHeader>?=null;h.queueMemory({busy=it}){error("No automatic replay")}
            assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),busy);assertTrue(h.worker.jobs.isEmpty());assertEquals(epoch+1,h.epoch.mutationEpoch)
        }
    }
    @Test fun failedWorkerOrBareValueCannotFabricateSuccess() {
        for(operation in operations)harness().use{h->
            val old=h.prepare(operation);val before=h.snapshot();h.completions.clear();h.worker.onQueue={error("SYNTHETIC_DISPATCH")};var calls=0
            h.queueMemory({calls++}){it.mutate(operation,old)}
            assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(listOf(rolledBack),h.completions)
        }
        harness().use{h->
            var calls=0;h.boundary.submit<OperationsResult<Unit>>(WorkspaceMutation(WorkspaceWriterFamily.MEMORY_REPORT,"a"),{calls++},{null},{it is OperationsResult.Value}){_,_->OperationsResult.Value(Unit)}
            h.worker.drain();h.main.drain();assertEquals(0,calls);assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        }
    }
    @Test fun explicitRollbackCleanupAndUnconfirmedRollbackStaySeparateForAllWrites() {
        for(operation in operations)for(mode in listOf("normal","rollback","db-cleanup","key-cleanup"))harness().use{h->
            val old=h.prepare(operation);val before=h.snapshot();h.completions.clear()
            if(mode=="rollback")h.failRollback=true
            if(mode=="db-cleanup")h.dbCleanup={error("SYNTHETIC_CLEANUP")}
            if(mode=="key-cleanup")h.keyCleanup={error("SYNTHETIC_CLEANUP")}
            var calls=0;h.queueMemory({calls++;assertEquals(OperationsResult.Rejected(OperationsFailure.INVALID),it)}){
                value(it.mutate(operation,old));OperationsResult.Rejected(OperationsFailure.INVALID)
            }
            h.worker.drain();h.main.drain();assertEquals(if(mode=="normal")1 else 0,calls)
            if(mode=="rollback")assertNotEquals(before,h.snapshot())else assertEquals(before,h.snapshot())
            assertEquals(if(mode=="rollback")WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.ROLLED_BACK,h.completions.single().outcome)
            assertEquals(if(mode=="normal")WorkspaceWriteCleanup.COMPLETE else WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
        }
    }
    @Test fun persistedRevisionRejectsAnswerAbaEvenAtIdenticalTimestamp()=harness().use{h->
        val a=h.create();h.time=a.updatedAt-1;val b=value(h.save(a,first("B")))
        h.time=a.updatedAt-1;val again=value(h.save(b,first()))
        assertEquals(a.updatedAt,again.updatedAt);assertEquals(2L,again.revision);assertEquals(first().answers,h.detail(a.id).answers)
        assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),h.save(a,first("Stale editor after ABA")))
    }
    companion object {
        private val operations=listOf("create","section","complete","addendum")
        private val modes=listOf("normal","initial","queued","during-write","commit","after-commit","publication","permission","generation","action","blocked","end-before","end-after","db-cleanup","key-cleanup")
    }
}
