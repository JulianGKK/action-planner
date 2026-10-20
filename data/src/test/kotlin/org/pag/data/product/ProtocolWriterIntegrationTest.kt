package org.pag.data.product

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

private typealias ProtocolHarness = CoreWriterIntegrationTest.H

/** Same productive store/outer transaction/boundary, real schema 11 + production triggers.
 * DB/key cleanup and authority bindings are trusted fault simulations, not native proofs. */
class ProtocolWriterIntegrationTest {
    private fun harness()=ProtocolHarness().also{h->
        h.exec("UPDATE actions SET status='ACTIVE' WHERE id='a'")
        h.exec("INSERT INTO persons VALUES('foreign','b','Synthetic foreign','synthetic foreign',0)")
        runBlocking{h.db.installHostProtocolConstraints()}
    }
    private fun input(title:String="Synthetic protocol")=ProtocolInput(ProtocolClassification.KEY_EVENT,title,10,
        description="Synthetic detail",sourceType=ProtocolSourceType.REPORTED,reportedByPersonId="p0",
        affectedPersonIds=linkedSetOf("p0","p1"),placeLabel="Synthetic place",
        announcementText="Synthetic announcement",announcementKind=ProtocolAnnouncementKind.PARAPHRASE,
        statedLegalBasis="Synthetic attributed statement",officialIdentifiers=listOf(
            ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID,"Synthetic id A"),
            ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.UNIT_OR_VEHICLE_ID,"Synthetic id B")))
    private fun <T> value(result:ProtocolResult<T>)=(result as ProtocolResult.Value).value
    private fun <T> ProtocolHarness.writeProtocol(work:suspend(ProtocolStore)->ProtocolResult<T>)=
        write<ProtocolResult<T>>(WorkspaceWriterFamily.PROTOCOL,{it is ProtocolResult.Rejected},
            {ProtocolResult.Rejected(ProtocolFailure.STALE)}){work(ProtocolStore(data,it){++time})}
    private fun ProtocolHarness.queueProtocol(publish:(ProtocolResult<*>)->Unit={},work:suspend(ProtocolStore)->ProtocolResult<*>)=
        queue<ProtocolResult<*>>(WorkspaceWriterFamily.PROTOCOL,{it is ProtocolResult.Rejected},
            {ProtocolResult.Rejected(ProtocolFailure.STALE)},publish){work(ProtocolStore(data,it){++time})}
    private fun ProtocolHarness.create(i:ProtocolInput=input(),phase:ActionStatus=ActionStatus.ACTIVE):ProtocolSaved {
        val submitted=freezeProtocolInput(ProtocolRules.normalized(i))
        return value(writeProtocol{it.create("a",phase,submitted)})
    }
    private fun ProtocolHarness.detail(id:String)=runBlocking{value(ProtocolStore(data,context){0}.detail("a",id))}
    private fun ProtocolHarness.expected(id:String)=detail(id).let{ProtocolExpectedRevision(it.entry,it.current.revision)}
    private fun ProtocolHarness.revise(e:ProtocolExpectedRevision,i:ProtocolInput=input("Changed"),type:ProtocolRevisionType=ProtocolRevisionType.CORRECTION,
        clear:Boolean=false,phase:ActionStatus=ActionStatus.ACTIVE):ProtocolResult<ProtocolSaved> {
        val expected=freezeProtocolExpected(e);val submitted=freezeProtocolInput(ProtocolRules.normalized(i))
        return writeProtocol{it.revise("a",phase,expected,type,submitted,clear)}
    }
    private fun ProtocolHarness.mark(e:ProtocolExpectedRevision):ProtocolResult<ProtocolSaved> {
        val expected=freezeProtocolExpected(e)
        return writeProtocol{it.markMistaken("a",ActionStatus.ACTIVE,expected)}
    }
    private val committed=WorkspaceWriteCompletion(WorkspaceWriteOutcome.COMMITTED,WorkspaceWriteCleanup.COMPLETE)
    private val rolledBack=WorkspaceWriteCompletion(WorkspaceWriteOutcome.ROLLED_BACK,WorkspaceWriteCleanup.COMPLETE)
    private val tables=listOf("protocol_entries","protocol_revisions","protocol_affected_persons","protocol_reported_by_persons","protocol_official_identifiers")
    // Raw SQL snapshot bypasses only test read-authority after Revoke, never product reads.
    private fun ProtocolHarness.snapshot():List<List<List<Any?>>> = tables.map { table ->
        runBlocking {
            data.query("SELECT * FROM $table ORDER BY rowid") { s ->
                buildList {
                    while(s.step())add((0 until s.getColumnCount()).map { i ->
                        when(s.getColumnType(i)){1->s.getLong(i);2->s.getDouble(i);3->s.getText(i);else->null}
                    })
                }
            }
        }
    }

    @Test fun currentIdentifierPositionCannotWrapThroughRoomIntMapping() {
        for(position in listOf("4294967296","-4294967296","0.5","'invalid'","-1","32","1"))harness().use{h->
            val saved=h.create(input().copy(officialIdentifiers=input().officialIdentifiers.take(1)))
            // Normal INSERT/UPDATE is already blocked by production triggers. Deliberate raw corruption
            // tests the READ gate independently, rather than claiming a permitted product mutation.
            h.exec("DROP TRIGGER protocol_official_identifiers_immutable")
            h.exec("UPDATE protocol_official_identifiers SET position=$position")
            assertThrows(ProductStorageFailure::class.java){h.detail(saved.entryId)}
        }
    }
    @Test fun appendMustNotExtendAHistoryWhoseOriginalWasDeleted()=harness().use{h->
        val a=h.create();value(h.revise(h.expected(a.entryId)));val e=h.expected(a.entryId)
        h.exec("DELETE FROM protocol_revisions WHERE entryId='${a.entryId}' AND revisionNumber=1")
        val before=h.snapshot();var calls=0
        h.queueProtocol({calls++}){it.markMistaken("a",ActionStatus.ACTIVE,e)};h.worker.drain();h.main.drain()
        assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.last().outcome)
    }
    @Test fun createCorrectionAddendumAndMistakenPreserveOriginalAndEveryRelation()=harness().use{h->
        val saved=h.create(input(" Synthetic original "));val original=h.detail(saved.entryId)
        assertEquals(1L,saved.revisionNumber);assertEquals(2,original.current.affectedPersons.size)
        assertEquals("p0",original.current.reportedBy!!.id);assertEquals(2,original.current.revision.input.officialIdentifiers.size)
        val correction=value(h.revise(h.expected(saved.entryId)));assertEquals(2L,correction.revisionNumber)
        value(h.revise(h.expected(saved.entryId),input("Addendum"),ProtocolRevisionType.ADDENDUM))
        value(h.mark(h.expected(saved.entryId)));assertTrue(h.detail(saved.entryId).current.revision.mistaken)
        val marked=h.expected(saved.entryId)
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE),h.mark(marked))
        value(h.revise(marked,input("Corrected after mistaken"),clear=true))
        val all=h.detail(saved.entryId)
        assertEquals(listOf(1L,2L,3L,4L,5L),all.revisions.map{it.revision.revisionNumber})
        assertEquals(original.current,all.revisions.first());assertFalse(all.current.revision.mistaken)
        assertEquals(listOf(ProtocolRevisionType.ORIGINAL,ProtocolRevisionType.CORRECTION,ProtocolRevisionType.ADDENDUM,
            ProtocolRevisionType.MARKED_MISTAKEN,ProtocolRevisionType.CORRECTION),all.revisions.map{it.revision.revisionType})
        assertEquals(5L,h.count("protocol_revisions"));assertEquals(10L,h.count("protocol_affected_persons"))
        assertEquals(5L,h.count("protocol_reported_by_persons"));assertEquals(10L,h.count("protocol_official_identifiers"))
        val epoch=h.epoch.mutationEpoch
        runBlocking{val store=ProtocolStore(h.data,h.context){0}
            assertEquals(ProtocolSummary(1,original.current.revision.recordedAt),value(store.summary("a")))
            val timeline=value(store.timeline("a")).single();assertEquals(5L,timeline.revisionNumber)
            assertEquals(original.current.revision.recordedAt,timeline.originalRecordedAt)}
        assertEquals(epoch,h.epoch.mutationEpoch);assertFalse(h.epoch.ramReuseEnabled)
        assertTrue(h.completions.filterIndexed{i,_->i!=4}.all{it==committed});assertEquals(rolledBack,h.completions[4])
    }
    @Test fun mistakenAddendumAndCorrectionRetainExistingTransitionRules()=harness().use{h->
        val saved=h.create();value(h.mark(h.expected(saved.entryId)))
        value(h.revise(h.expected(saved.entryId),input("Addendum"),ProtocolRevisionType.ADDENDUM))
        assertTrue(h.detail(saved.entryId).current.revision.mistaken)
        value(h.revise(h.expected(saved.entryId),input("Correction")))
        assertTrue(h.detail(saved.entryId).current.revision.mistaken)
        value(h.revise(h.expected(saved.entryId),input("Explicit clear"),clear=true))
        assertFalse(h.detail(saved.entryId).current.revision.mistaken)
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE),h.revise(h.expected(saved.entryId),clear=true))
        assertEquals(5,h.detail(saved.entryId).revisions.size)
    }
    @Test fun databaseTriggersKeepEveryExistingProtocolRowImmutable()=harness().use{h->
        h.create()
        for(table in tables){
            val column=when(table){"protocol_entries"->"createdAfterCompletion";"protocol_revisions"->"title";"protocol_official_identifiers"->"value";else->"personId"}
            assertThrows(IllegalStateException::class.java){h.exec("UPDATE $table SET $column=$column")}
        }
        assertEquals(1L,h.count("protocol_revisions"))
    }
    @Test fun twoEditorsAndOldMistakenConfirmationCannotAppendTwice()=harness().use{h->
        val a=h.create();val expected=h.expected(a.entryId)
        val first=value(h.revise(expected));val after=h.snapshot()
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),h.revise(expected,input("Other editor")))
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),h.mark(expected));assertEquals(after,h.snapshot())
        assertEquals(2L,first.revisionNumber);assertEquals(rolledBack,h.completions.last())
        assertEquals(3L,value(h.revise(h.expected(a.entryId),input("New editor"))).revisionNumber)
    }
    @Test fun fullExpectedCasIncludesAllIdentityContentRelationsAndPhaseFields()=harness().use{h->
        val a=h.create();val e=h.expected(a.entryId);val r=e.revision;val i=r.input;val before=h.snapshot()
        val changed=listOf(r.copy(id="other"),r.copy(revisionNumber=2),r.copy(revisionType=ProtocolRevisionType.ADDENDUM),
            r.copy(recordedAt=999),r.copy(recordedAfterCompletion=true),r.copy(mistaken=true),
            r.copy(input=i.copy(title="Other")),r.copy(input=i.copy(description="Other")),r.copy(input=i.copy(occurredAt=11)),
            r.copy(input=i.copy(occurredAtApproximate=true)),r.copy(input=i.copy(classification=ProtocolClassification.RELEVANT)),
            r.copy(input=i.copy(templateKey=ProtocolRules.templates.first().key)),r.copy(input=i.copy(sourceType=ProtocolSourceType.UNCLEAR)),
            r.copy(input=i.copy(reportedByPersonId="p1")),r.copy(input=i.copy(affectedPersonIds=setOf("p0"))),
            r.copy(input=i.copy(placeLabel="Other")),r.copy(input=i.copy(announcementText="Other")),
            r.copy(input=i.copy(announcementKind=ProtocolAnnouncementKind.WORDING)),r.copy(input=i.copy(statedLegalBasis="Other")),
            r.copy(input=i.copy(officialIdentifiers=i.officialIdentifiers.reversed())))
        for(revision in changed){
            val old=ProtocolExpectedRevision(e.entry,revision)
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),h.revise(old))
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),h.mark(old))
        }
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),h.mark(e.copy(entry=e.entry.copy(createdAfterCompletion=true))))
        for(old in listOf(e.copy(entry=e.entry.copy(actionId="b")),e.copy(revision=r.copy(actionId="b")),e.copy(revision=r.copy(entryId="other"))))
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.WRONG_ACTION),h.mark(old))
        val missing=e.copy(entry=e.entry.copy(id="missing"),revision=r.copy(entryId="missing"))
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND),h.mark(missing));assertEquals(before,h.snapshot())
    }
    @Test fun expectedActionPhaseAndCompletedFlagsRemainAuthoritative()=harness().use{h->
        val a=h.create();val e=h.expected(a.entryId)
        h.exec("UPDATE actions SET status='COMPLETED',completedAt=100 WHERE id='a'")
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),h.revise(e))
        value(h.revise(e,phase=ActionStatus.COMPLETED));val history=h.detail(a.entryId)
        assertFalse(history.entry.createdAfterCompletion);assertFalse(history.revisions.first().revision.recordedAfterCompletion)
        assertTrue(history.current.revision.recordedAfterCompletion)
        val later=h.create(phase=ActionStatus.COMPLETED);assertTrue(h.detail(later.entryId).entry.createdAfterCompletion)
        for(status in listOf(ActionStatus.DRAFT,ActionStatus.PREPARATION,ActionStatus.DELETION_PENDING)){
            h.exec("UPDATE actions SET status='${status.name}',completedAt=NULL WHERE id='a'")
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE),h.writeProtocol{it.create("a",status,input())})
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE),h.writeProtocol{it.markMistaken("a",status,e)})
        }
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND),h.writeProtocol{it.create("missing",ActionStatus.ACTIVE,input())})
    }
    @Test fun invalidContentTemplatesKindsAndForeignPeopleAreConfirmedRollbacks()=harness().use{h->
        val a=h.create(input().copy(templateKey=ProtocolRules.templates.first().key));val e=h.expected(a.entryId);val before=h.snapshot()
        for(i in listOf(input(" "),input("🦋".repeat(121)),input().copy(description="x".repeat(2001)),
            input().copy(placeLabel="x".repeat(81)),input().copy(announcementText="x".repeat(2001)),input().copy(statedLegalBasis="x".repeat(1001)),
            input().copy(sourceType=ProtocolSourceType.OWN_OBSERVATION),input().copy(announcementKind=null),
            input().copy(templateKey="invalid"),input().copy(classification=ProtocolClassification.RELEVANT,templateKey=ProtocolRules.templates.first().key),
            input().copy(officialIdentifiers=List(33){input().officialIdentifiers.first()}),
            input().copy(officialIdentifiers=listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID," "))),
            input().copy(officialIdentifiers=listOf(ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID,"x".repeat(121))))))
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID),h.writeProtocol{it.create("a",ActionStatus.ACTIVE,i)})
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID),h.revise(e,input().copy(templateKey=ProtocolRules.templates.last().key)))
        for(type in listOf(ProtocolRevisionType.ORIGINAL,ProtocolRevisionType.MARKED_MISTAKEN))
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE),h.revise(e,e.revision.input,type))
        for(i in listOf(input().copy(affectedPersonIds=setOf("foreign")),input().copy(reportedByPersonId="foreign"),input().copy(affectedPersonIds=setOf("missing"))))
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.NOT_FOUND),h.writeProtocol{it.create("a",ActionStatus.ACTIVE,i)})
        assertEquals(before,h.snapshot());assertTrue(h.completions.drop(1).all{it==rolledBack})
        val max=h.create(input("🦋".repeat(120)).copy(description="🦋".repeat(2000),officialIdentifiers=List(32){ProtocolOfficialIdentifier(ProtocolOfficialIdentifierType.OTHER_OFFICIAL_ID,"🦋".repeat(120))}))
        assertEquals(32,h.detail(max.entryId).current.revision.input.officialIdentifiers.size)
    }
    @Test fun everyPartialInsertFailureRollsBackEntryRevisionAndAllRelations() {
        for(operation in listOf("create","revise","mark"))for(table in tables)harness().use{h->
            val saved=h.create();val old=h.expected(saved.entryId);val before=h.snapshot();var reached=false
            // Append doesn't INSERT an entry; test it on create, not a fabricated branch.
            if(table=="protocol_entries" && operation!="create")return@use
            h.data.afterChange={sql->if(sql.startsWith("INSERT INTO $table ")){reached=true;error("SYNTHETIC_PROTOCOL_FAILURE")}}
            var calls=0;h.queueProtocol({calls++}){when(operation){"create"->it.create("a",ActionStatus.ACTIVE,input());"revise"->it.revise("a",ActionStatus.ACTIVE,old,ProtocolRevisionType.CORRECTION,input("Changed"));else->it.markMistaken("a",ActionStatus.ACTIVE,old)}}
            h.worker.drain();h.main.drain();assertTrue("$operation/$table",reached);assertEquals(0,calls)
            assertEquals(before,h.snapshot());assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.last().outcome)
        }
    }
    @Test fun sqliteFailureDuringSecondIdentifierOrAffectedInsertLeavesNoHalfProtocol() {
        for(table in listOf("protocol_affected_persons","protocol_official_identifiers"))harness().use{h->
            val saved=h.create();val old=h.expected(saved.entryId);val before=h.snapshot()
            val condition=if(table=="protocol_affected_persons")"NEW.personId='p1'" else "NEW.position=1"
            h.exec("CREATE TRIGGER synthetic_protocol_failure BEFORE INSERT ON $table WHEN $condition BEGIN SELECT RAISE(ABORT,'SYNTHETIC_FAILURE'); END")
            var calls=0;h.queueProtocol({calls++}){it.revise("a",ActionStatus.ACTIVE,old,ProtocolRevisionType.ADDENDUM,input("Changed"))}
            h.worker.drain();h.main.drain();assertEquals(0,calls);assertEquals(before,h.snapshot())
        }
    }
    @Test fun createFailureLeavesNoEntryNoRevisionNoChildRows() {
        for(table in tables)harness().use{h->
            h.data.afterChange={if(it.startsWith("INSERT INTO $table "))error("SYNTHETIC_FAILURE")}
            var calls=0;h.queueProtocol({calls++}){it.create("a",ActionStatus.ACTIVE,input())};h.worker.drain();h.main.drain()
            assertEquals(0,calls);assertTrue(tables.all{h.count(it)==0L})
        }
    }
    @Test fun originalsAndPersonRelationsRemainMandatoryAndActionLocal()=harness().use{h->
        val saved=h.create();val e=h.expected(saved.entryId)
        assertThrows(IllegalStateException::class.java){h.exec("INSERT INTO protocol_affected_persons VALUES('a','${e.revision.id}','foreign')")}
        assertThrows(IllegalStateException::class.java){h.exec("INSERT INTO protocol_reported_by_persons VALUES('b','${e.revision.id}','foreign')")}
        // Deliberately bypass FKs to test a corrupt orphan relation, not normal person removal.
        h.exec("PRAGMA foreign_keys=OFF");h.exec("DELETE FROM persons WHERE id='p0'");h.exec("PRAGMA foreign_keys=ON")
        assertThrows(ProductStorageFailure::class.java){h.detail(saved.entryId)}
        h.exec("DELETE FROM protocol_revisions WHERE entryId='${saved.entryId}'")
        assertThrows(ProductStorageFailure::class.java){h.detail(saved.entryId)}
        runBlocking{val store=ProtocolStore(h.data,h.context){0}
            assertThrows(ProductStorageFailure::class.java){runBlocking{store.summary("a")}}
            assertThrows(ProductStorageFailure::class.java){runBlocking{store.timeline("a")}}}
        Unit
    }
    @Test fun revisionOverflowRejectsWithoutWrappingOrAppending()=harness().use{h->
        val saved=h.create();value(h.revise(h.expected(saved.entryId)));val before=h.detail(saved.entryId)
        h.exec("DROP TRIGGER protocol_revisions_immutable")
        h.exec("UPDATE protocol_revisions SET revisionNumber=9223372036854775807 WHERE revisionNumber=2")
        val row=runBlocking{h.data.protocol.current("a",saved.entryId)!!}
        val e=ProtocolExpectedRevision(before.entry,before.current.revision.copy(revisionNumber=Long.MAX_VALUE))
        assertEquals(row.id,e.revision.id);val sqlBefore=h.snapshot()
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE),h.revise(e))
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID_STATE),h.mark(e))
        assertEquals(sqlBefore,h.snapshot());assertEquals(rolledBack,h.completions.last())
    }
    @Test fun legacyNormalizationIsolatesOriginalCollectionsButDoesNotMakeCopiesImmutable() {
        val people=linkedSetOf("p0","p1");val ids=input().officialIdentifiers.toMutableList()
        val normalized=ProtocolRules.normalized(input().copy(affectedPersonIds=people,officialIdentifiers=ids))
        people.clear();ids.clear();assertEquals(2,normalized.affectedPersonIds.size);assertEquals(2,normalized.officialIdentifiers.size)
        (normalized.affectedPersonIds as MutableSet).clear();(normalized.officialIdentifiers as MutableList).clear()
        assertTrue(normalized.affectedPersonIds.isEmpty());assertTrue(normalized.officialIdentifiers.isEmpty())
    }
    @Test fun frozenQueuedDraftAndExpectedCannotBeChangedByOriginalsOrCollectionCasts()=harness().use{h->
        val people=linkedSetOf("p0","p1");val ids=input().officialIdentifiers.toMutableList()
        val submitted=freezeProtocolInput(ProtocolRules.normalized(input().copy(affectedPersonIds=people,officialIdentifiers=ids)))
        var saved:ProtocolSaved?=null;h.queueProtocol({saved=(it as ProtocolResult.Value).value as ProtocolSaved}){it.create("a",ActionStatus.ACTIVE,submitted)}
        people.clear();ids.clear();h.worker.drain();h.main.drain()
        val loaded=h.expected(saved!!.entryId);assertEquals(2,loaded.revision.input.affectedPersonIds.size);assertEquals(2,loaded.revision.input.officialIdentifiers.size)
        val expectedPeople=loaded.revision.input.affectedPersonIds.toMutableSet();val expectedIds=loaded.revision.input.officialIdentifiers.toMutableList()
        val expected=freezeProtocolExpected(loaded.copy(revision=loaded.revision.copy(input=loaded.revision.input.copy(affectedPersonIds=expectedPeople,officialIdentifiers=expectedIds))))
        var calls=0;h.queueProtocol({calls++}){it.markMistaken("a",ActionStatus.ACTIVE,expected)}
        expectedPeople.clear();expectedIds.clear();h.worker.drain();h.main.drain();assertEquals(1,calls)
        for(i in listOf(submitted,expected.revision.input)){
            assertThrows(UnsupportedOperationException::class.java){(i.affectedPersonIds as MutableSet).clear()}
            assertThrows(UnsupportedOperationException::class.java){(i.officialIdentifiers as MutableList).clear()}
        }
    }
    @Test fun queuedRevisionFreezesBothDraftAndNestedExpectedCollections()=harness().use{h->
        val saved=h.create();val original=h.expected(saved.entryId)
        val originalPeople=original.revision.input.affectedPersonIds.toMutableSet();val originalIds=original.revision.input.officialIdentifiers.toMutableList()
        val expected=freezeProtocolExpected(original.copy(revision=original.revision.copy(input=original.revision.input.copy(affectedPersonIds=originalPeople,officialIdentifiers=originalIds))))
        val people=linkedSetOf("p1");val ids=input().officialIdentifiers.take(1).toMutableList()
        val draft=freezeProtocolInput(ProtocolRules.normalized(input("New draft").copy(affectedPersonIds=people,officialIdentifiers=ids)))
        var calls=0;h.queueProtocol({calls++}){it.revise("a",ActionStatus.ACTIVE,expected,ProtocolRevisionType.CORRECTION,draft)}
        originalPeople.clear();originalIds.clear();people.clear();ids.clear();h.worker.drain();h.main.drain()
        assertEquals(1,calls);val detail=h.detail(saved.entryId)
        assertEquals(original.revision,detail.revisions.first().revision);assertEquals(draft,detail.current.revision.input)
    }
    @Test fun allThreeWritersSeparatePhysicalSqlOutcomeCleanupAndPublicationAcrossRaces() {
        for(operation in listOf("create","revise","mark"))for(mode in listOf("normal","initial","queued","during-write","commit","after-commit","publication","permission","generation","action","blocked","end-before","end-after","db-cleanup","key-cleanup"))harness().use{h->
            val a=h.create();val old=h.expected(a.entryId);val before=h.snapshot();h.completions.clear()
            when(mode){"initial"->h.ready=false;"queued"->Unit;"blocked"->h.storageReady=false;"end-before"->h.failEndBefore=true;"end-after"->h.failEndAfter=true;
                "db-cleanup"->h.dbCleanup={error("SYNTHETIC_CLEANUP")};"key-cleanup"->h.keyCleanup={error("SYNTHETIC_CLEANUP")};
                "commit"->h.beforeEnd={h.ready=false};"after-commit"->h.afterEnd={h.ready=false};"publication"->h.beforePublication={h.ready=false}}
            if(mode=="during-write")h.data.afterChange={h.ready=false}
            var calls=0;h.queueProtocol({calls++}){when(operation){"create"->it.create("a",ActionStatus.ACTIVE,input());"revise"->it.revise("a",ActionStatus.ACTIVE,old,ProtocolRevisionType.CORRECTION,input("Changed"));else->it.markMistaken("a",ActionStatus.ACTIVE,old)}}
            if(mode=="queued")h.ready=false
            val worker=h.worker.jobs.firstOrNull();h.worker.drain();worker?.run()
            when(mode){"permission"->h.permission++;"generation"->h.generation++;"action"->h.action="b"}
            val main=h.main.jobs.firstOrNull();h.main.drain();main?.run()
            assertEquals("$operation/$mode",if(mode=="normal")1 else 0,calls)
            val physicalCommit=mode in listOf("normal","after-commit","publication","permission","generation","action","end-after","db-cleanup","key-cleanup")
            if(!physicalCommit)assertEquals("$operation/$mode",before,h.snapshot())
            else{
                assertEquals(if(operation=="create")2L else 1L,h.count("protocol_entries"));assertEquals(2L,h.count("protocol_revisions"))
                assertEquals(4L,h.count("protocol_affected_persons"));assertEquals(2L,h.count("protocol_reported_by_persons"));assertEquals(4L,h.count("protocol_official_identifiers"))
                assertEquals(before[1].first(),h.snapshot()[1].first()) // original row survives byte/value-identical
            }
            if(mode=="initial")assertTrue(h.completions.isEmpty())
            else if(mode.startsWith("end"))assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
            else if(physicalCommit)assertEquals(WorkspaceWriteOutcome.COMMITTED,h.completions.single().outcome)
            if(mode.endsWith("cleanup"))assertEquals(WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
            if(mode=="normal")assertEquals(committed,h.completions.single());assertFalse(h.epoch.ramReuseEnabled)
        }
    }
    @Test fun protocolAndOtherFamiliesSharePendingAndOneEpochInBothDirections() {
        val families=listOf(WorkspaceWriterFamily.PROTOCOL,WorkspaceWriterFamily.TASK,WorkspaceWriterFamily.MATERIAL,WorkspaceWriterFamily.EXPENSE,WorkspaceWriterFamily.OPERATIONS)
        for(first in families)harness().use{h->
            h.queue<Any>(first,{it is ProtocolResult.Rejected || it is OperationsResult.Rejected},{null},{}){c->when(first){
                WorkspaceWriterFamily.PROTOCOL->ProtocolStore(h.data,c){0}.create("a",ActionStatus.ACTIVE,input())
                WorkspaceWriterFamily.TASK->TaskStore(HostTaskData(h.db),c){0}.create("a",TaskInput("Synthetic"))
                WorkspaceWriterFamily.MATERIAL->MaterialStore(h.data,c){0}.create("a",MaterialInput("Synthetic"))
                WorkspaceWriterFamily.EXPENSE->ExpenseStore(h.data,c){0}.create("a",ExpenseInput("Synthetic",1,listOf(ExpensePaymentInput("Synthetic",1))))
                else->OperationsStore(h.data,c){0}.role("a",null,"New role")
            }}
            for(other in families){
                if(other==WorkspaceWriterFamily.PROTOCOL){
                    var busy:ProtocolResult<*>?=null;h.queueProtocol({busy=it}){error("No busy bypass")}
                    assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),busy)
                }else{
                    var busy:OperationsResult<Unit>?=null
                    h.queue<OperationsResult<Unit>>(other,{it is OperationsResult.Rejected},{OperationsResult.Rejected(OperationsFailure.STALE)},{busy=it}){error("No busy bypass")}
                    assertEquals(OperationsResult.Rejected(OperationsFailure.STALE),busy)
                }
            }
            assertEquals(1L,h.epoch.mutationEpoch);assertEquals(1,h.worker.jobs.size);assertTrue(h.completions.isEmpty())
            h.worker.drain();h.main.drain();assertEquals(listOf(committed),h.completions);assertEquals(1L,h.epoch.mutationEpoch)
            assertEquals(if(first==WorkspaceWriterFamily.PROTOCOL)1L else 0L,h.count("protocol_entries"))
        }
    }
    @Test fun duplicateWorkerMainAndLateMainCannotDuplicateOrCrossPublishRevisions()=harness().use{h->
        var calls=0;h.queueProtocol({calls++}){it.create("a",ActionStatus.ACTIVE,input())}
        val worker=h.worker.jobs.removeFirst();worker.run();worker.run();assertEquals(0,calls)
        assertEquals(1L,h.count("protocol_revisions"));assertEquals(2L,h.count("protocol_official_identifiers"))
        val main=h.main.jobs.removeFirst();main.run();main.run();assertEquals(1,calls)
        h.queue<OperationsResult<MaterialItem>>(WorkspaceWriterFamily.MATERIAL,{it is OperationsResult.Rejected},{null},{}){MaterialStore(h.data,it){0}.create("a",MaterialInput("Synthetic"))}
        main.run();assertEquals(1,calls);h.worker.drain();h.main.drain();main.run();assertEquals(1,calls)
        assertEquals(2L,h.epoch.mutationEpoch);assertEquals(1L,h.count("protocol_revisions"))
    }
    @Test fun newerFamilyMutationBeforeOldPublicationSuppressesOldWriterResponse() {
        for(operation in listOf("create","revise","mark"))harness().use{h->
            val saved=h.create();val old=h.expected(saved.entryId);h.completions.clear();var oldCalls=0;var newCalls=0
            h.queueProtocol({oldCalls++}){when(operation){"create"->it.create("a",ActionStatus.ACTIVE,input());"revise"->it.revise("a",ActionStatus.ACTIVE,old,ProtocolRevisionType.CORRECTION,input("Changed"));else->it.markMistaken("a",ActionStatus.ACTIVE,old)}}
            h.worker.drain()
            h.beforePublication={
                h.beforePublication={}
                h.queue<OperationsResult<MaterialItem>>(WorkspaceWriterFamily.MATERIAL,{it is OperationsResult.Rejected},{null},{newCalls++}){
                    MaterialStore(h.data,it){0}.create("a",MaterialInput("Synthetic"))
                }
            }
            val oldMain=h.main.jobs.first();h.main.drain();assertEquals(0,oldCalls)
            assertEquals(2L,h.count("protocol_revisions"));assertEquals(WorkspaceWriteOutcome.COMMITTED,h.completions.single().outcome)
            h.worker.drain();h.main.drain();oldMain.run()
            assertEquals(0,oldCalls);assertEquals(1,newCalls);assertEquals(3L,h.epoch.mutationEpoch)
            assertEquals(listOf(committed,committed),h.completions)
        }
    }
    @Test fun lostOrFailedMainDispatchRemainsPendingWithoutReplay() {
        for(operation in listOf("create","revise","mark"))for(fail in listOf(true,false))harness().use{h->
            val saved=h.create();val old=h.expected(saved.entryId);h.completions.clear()
            if(fail)h.main.onQueue={error("SYNTHETIC_DISPATCH")}
            var calls=0;h.queueProtocol({calls++}){when(operation){"create"->it.create("a",ActionStatus.ACTIVE,input());"revise"->it.revise("a",ActionStatus.ACTIVE,old,ProtocolRevisionType.CORRECTION,input("Changed"));else->it.markMistaken("a",ActionStatus.ACTIVE,old)}}
            if(fail)assertThrows(IllegalStateException::class.java){h.worker.drain()}else{h.worker.drain();h.main.jobs.clear()}
            assertEquals(0,calls);assertEquals(if(operation=="create")2L else 1L,h.count("protocol_entries"));assertEquals(2L,h.count("protocol_revisions"));assertTrue(h.completions.isEmpty())
            var busy:ProtocolResult<*>?=null;h.queueProtocol({busy=it}){error("No automatic replay")}
            assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),busy);assertTrue(h.worker.jobs.isEmpty())
        }
    }
    @Test fun failedWorkerAndBareValueNeverFabricateSuccess() {
        for(operation in listOf("create","revise","mark"))harness().use{h->
            val saved=h.create();val old=h.expected(saved.entryId);val before=h.snapshot();h.completions.clear()
            h.worker.onQueue={error("SYNTHETIC_DISPATCH")};var calls=0
            h.queueProtocol({calls++}){when(operation){"create"->it.create("a",ActionStatus.ACTIVE,input());"revise"->it.revise("a",ActionStatus.ACTIVE,old,ProtocolRevisionType.CORRECTION,input("Changed"));else->it.markMistaken("a",ActionStatus.ACTIVE,old)}}
            assertEquals(0,calls);assertEquals(before,h.snapshot());assertEquals(listOf(rolledBack),h.completions)
        }
        harness().use{h->
            var calls=0;h.boundary.submit<ProtocolResult<Unit>>(WorkspaceMutation(WorkspaceWriterFamily.PROTOCOL,"a"),{calls++},{null},{it is ProtocolResult.Value}){_,_->ProtocolResult.Value(Unit)}
            h.worker.drain();h.main.drain();assertEquals(0,calls);assertEquals(WorkspaceWriteOutcome.UNKNOWN,h.completions.single().outcome)
        }
    }
    @Test fun explicitRejectionAfterWritesNeedsReturnedOuterRollbackAndCompleteCleanup() {
        for(operation in listOf("create","revise","mark"))for(mode in listOf("normal","rollback","db-cleanup","key-cleanup"))harness().use{h->
            val saved=h.create();val old=h.expected(saved.entryId);val before=h.snapshot();h.completions.clear()
            if(mode=="rollback")h.failRollback=true
            if(mode=="db-cleanup")h.dbCleanup={error("SYNTHETIC_CLEANUP")}
            if(mode=="key-cleanup")h.keyCleanup={error("SYNTHETIC_CLEANUP")}
            var calls=0;h.queueProtocol({calls++;assertEquals(ProtocolResult.Rejected(ProtocolFailure.INVALID),it)}){
                value(when(operation){"create"->it.create("a",ActionStatus.ACTIVE,input());"revise"->it.revise("a",ActionStatus.ACTIVE,old,ProtocolRevisionType.CORRECTION,input("Changed"));else->it.markMistaken("a",ActionStatus.ACTIVE,old)})
                ProtocolResult.Rejected(ProtocolFailure.INVALID)
            }
            h.worker.drain();h.main.drain();assertEquals(if(mode=="normal")1 else 0,calls)
            if(mode=="rollback"){
                // Uncommitted SQL remains visible on this connection; fixture close rolls it back.
                assertEquals(2L,h.count("protocol_revisions"));assertEquals(4L,h.count("protocol_official_identifiers"))
            }else assertEquals(before,h.snapshot())
            assertEquals(if(mode=="rollback")WorkspaceWriteOutcome.UNKNOWN else WorkspaceWriteOutcome.ROLLED_BACK,h.completions.single().outcome)
            assertEquals(if(mode=="normal")WorkspaceWriteCleanup.COMPLETE else WorkspaceWriteCleanup.FAILED_OR_UNCONFIRMED,h.completions.single().cleanup)
        }
    }
    @Test fun appendRevisionIdentityRejectsContentAbaWithSameTimestamp()=harness().use{h->
        val saved=h.create();val a=h.expected(saved.entryId)
        h.time=a.revision.recordedAt-1;value(h.revise(a,input("B")))
        h.time=a.revision.recordedAt-1;value(h.revise(h.expected(saved.entryId),a.revision.input))
        val again=h.expected(saved.entryId)
        assertEquals(a.revision.input,again.revision.input);assertEquals(a.revision.recordedAt,again.revision.recordedAt)
        assertEquals(3L,again.revision.revisionNumber)
        assertNotEquals(a.revision.id,again.revision.id)
        assertEquals(ProtocolResult.Rejected(ProtocolFailure.STALE),h.revise(a));assertEquals(rolledBack,h.completions.last())
    }
}
