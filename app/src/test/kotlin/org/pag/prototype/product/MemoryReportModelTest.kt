package org.pag.prototype.product
import org.pag.domain.*
import org.junit.Assert.*
import org.junit.Test

internal class MemoryFake : MemoryReportRepository {
    var stored: MemoryReportDetail? = null
    var writes=0;var summaries=0;var lists=0;var details=0
    var stale=false;var delay=false
    var pending: ((OperationsResult<MemoryReportHeader>)->Unit)?=null
    var listCallback: ((OperationsResult<List<MemoryReportHeader>>)->Unit)?=null
    var detailCallback: ((OperationsResult<MemoryReportDetail>)->Unit)?=null
    var summaryCallback: ((OperationsResult<MemoryReportSummary>)->Unit)?=null
    var delaySummary=false
    override fun summary(actionId:String,publish:(OperationsResult<MemoryReportSummary>)->Unit){summaries++;summaryCallback=publish;if(!delaySummary)publish(OperationsResult.Value(MemoryReportSummary(if(stored?.header?.status==MemoryReportStatus.DRAFT)1 else 0,if(stored?.header?.status==MemoryReportStatus.COMPLETED)1 else 0)))}
    override fun list(actionId:String,publish:(OperationsResult<List<MemoryReportHeader>>)->Unit){lists++;listCallback=publish;publish(OperationsResult.Value(listOfNotNull(stored?.header)))}
    override fun detail(actionId:String,reportId:String,publish:(OperationsResult<MemoryReportDetail>)->Unit){details++;detailCallback=publish;stored?.let{publish(OperationsResult.Value(it))}}
    private fun done(h:MemoryReportHeader,publish:(OperationsResult<MemoryReportHeader>)->Unit){pending=publish;if(!delay)publish(if(stale)OperationsResult.Rejected(OperationsFailure.STALE) else OperationsResult.Value(h))}
    override fun create(actionId:String,authorLabel:String,firstSection:MemorySectionSave,publish:(OperationsResult<MemoryReportHeader>)->Unit){writes++;val h=MemoryReportHeader("r",actionId,authorLabel.trim(),1,MemoryReportStatus.DRAFT,0,0,100,100,null);stored=MemoryReportDetail(h,firstSection.answers,emptyList());done(h,publish)}
    override fun saveSection(actionId:String,expected:MemoryReportHeader,section:MemorySectionSave,publish:(OperationsResult<MemoryReportHeader>)->Unit){writes++;val h=expected.copy(revision=expected.revision+1,lastSavedSectionOrdinal=maxOf(expected.lastSavedSectionOrdinal,section.sectionOrdinal),authorLabel=section.authorLabel?.trim()?:expected.authorLabel);if(!stale)stored=stored!!.copy(header=h,answers=stored!!.answers.filterKeys{MemoryQuestionnaireV1.question(it)!!.sectionOrdinal!=section.sectionOrdinal}+section.answers);done(h,publish)}
    override fun complete(actionId:String,expected:MemoryReportHeader,publish:(OperationsResult<MemoryReportHeader>)->Unit){writes++;val h=expected.copy(status=MemoryReportStatus.COMPLETED,revision=expected.revision+1,completedAt=100);if(!stale)stored=stored!!.copy(header=h);done(h,publish)}
    override fun addAddendum(actionId:String,expected:MemoryReportHeader,content:String,publish:(OperationsResult<MemoryReportHeader>)->Unit){writes++;val h=expected.copy(revision=expected.revision+1);if(!stale)stored=stored!!.copy(header=h,addenda=stored!!.addenda+MemoryAddendum(stored!!.addenda.size,content.trim(),100));done(h,publish)}
}
class MemoryReportModelTest {
    private var session=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
    private var interactions=0;private var onInteraction:()->Unit={};private var refreshes=0;private var feedback=0
    private val port=MemoryFake()
    private val model:MemoryReportModel=MemoryReportModel(port,{session},{action},{interactions++;onInteraction()},{refreshes++},{feedback++})
    private fun begin(){model.enter();model.create();model.changeAuthor("Motte")}
    private fun saveA(){begin();model.save()}
    @Test fun newDraftIsRamAndFirstSaveRevealsOnlyThenB(){begin();assertTrue(model.preFirstSave);assertEquals(0,port.writes);assertNull(model.detail);assertEquals(0,model.sectionOrdinal);model.save();assertFalse(model.preFirstSave);assertEquals(1,model.sectionOrdinal);assertEquals(0,model.expected!!.lastSavedSectionOrdinal);assertTrue(port.stored!!.answers.isEmpty());assertEquals("Motte",model.authorLabel)}
    @Test fun lockBeforeFirstSaveLosesEverything(){begin();model.changeAnswer("A_FREE_NARRATIVE",MemoryAnswerValue.Text("SENTINEL"));model.clear();assertNull(port.stored);assertNull(model.detail);assertTrue(model.answers.isEmpty());assertEquals("",model.authorLabel);assertEquals(0,port.writes)}
    @Test fun boundariesInvalidInputAndExternalAuthorDoNotWrite(){begin();model.changeAuthor(" ");model.save();assertEquals(0,port.writes);model.changeAuthor("Soligruppe Nord");model.changeAnswer("A_FREE_NARRATIVE",MemoryAnswerValue.Text("🦋".repeat(8001)));model.save();assertEquals(0,port.writes);model.changeAnswer("A_FREE_NARRATIVE",MemoryAnswerValue.Text("🦋".repeat(8000)));model.save();assertEquals(1,port.writes)}
    @Test fun callbacksAndDisplayAreNotInteractions(){saveA();val n=interactions;port.detailCallback!!(OperationsResult.Value(port.stored!!));assertEquals(n,interactions);assertEquals(1,refreshes);assertEquals(1,feedback)}
    @Test fun dirtyBackAsksAndCancelDoesNotWrite(){saveA();model.changeAnswer("B_AGREEMENT_OR_CONDITION",MemoryAnswerValue.Option("YES"));model.back();assertTrue(model.confirmingDiscard);assertEquals(1,port.writes);model.cancelDiscard();assertTrue(model.dirty);model.back();model.confirmDiscard();assertEquals(0,model.sectionOrdinal);assertEquals(1,port.writes)}
    @Test fun dependencyRequiresExplicitKeepOrRemove(){saveA();model.changeAnswer("B_AGREEMENT_OR_CONDITION",MemoryAnswerValue.Option("YES"));model.changeAnswer("B_AGREEMENT_DETAILS",MemoryAnswerValue.Text("synthetic"));model.save();model.back();model.changeAnswer("B_AGREEMENT_OR_CONDITION",MemoryAnswerValue.Option("NO"));assertEquals(setOf("B_AGREEMENT_DETAILS"),model.unresolved);model.save();assertEquals(2,port.writes);model.keepDetail("B_AGREEMENT_DETAILS");model.save();assertEquals(3,port.writes);assertTrue(port.stored!!.answers.containsKey("B_AGREEMENT_DETAILS"));model.back();assertTrue(model.unresolved.isNotEmpty());model.removeDetail("B_AGREEMENT_DETAILS");model.save();assertFalse(port.stored!!.answers.containsKey("B_AGREEMENT_DETAILS"))}
    @Test fun progressSurvivesEmptySectionsAndEarlierEdit(){saveA();for(i in 1..16){assertEquals(i,model.sectionOrdinal);model.save()};assertEquals(MemoryReportMode.SUMMARY,model.mode);assertEquals(16,model.expected!!.lastSavedSectionOrdinal);model.editSection(0);model.changeAuthor("Meise");model.save();assertEquals(16,model.expected!!.lastSavedSectionOrdinal);assertEquals("Meise",port.stored!!.header.authorLabel)}
    @Test fun completedReadOnlyAndAddendaNeverChangeAnswers(){saveA();repeat(16){model.save()};val answers=port.stored!!.answers;model.requestComplete();assertTrue(model.confirmingComplete);model.complete();assertEquals(MemoryReportMode.DETAIL,model.mode);assertEquals(MemoryReportStatus.COMPLETED,model.expected!!.status);model.editSection(0);assertEquals(MemoryReportMode.DETAIL,model.mode);model.startAddendum();model.changeAddendum("synthetic addition");model.saveAddendum();assertEquals(answers,port.stored!!.answers);assertEquals(1,port.stored!!.addenda.size)}
    @Test fun staleRetainsDraftAndRequiresConsciousReload(){saveA();model.changeAnswer("B_ENDING_MODE",MemoryAnswerValue.Multi(listOf("SELF_LEFT")));port.stale=true;model.save();val input=model.answers;model.save();assertEquals(2,port.writes);assertEquals(input,model.answers);assertEquals(OperationsFailure.STALE,model.error);model.reload();assertEquals(MemoryReportMode.LIST,model.mode);assertTrue(model.answers.isEmpty())}
    @Test fun committedButNoPublicationNoSuccessAndExplicitReopenFindsSavedSection(){begin();port.delay=true;model.save();val late=port.pending!!;assertNotNull(port.stored);assertTrue(model.busy);session=session.copy(state=SessionState.LOCKED,generation=2);model.clear();late(OperationsResult.Value(port.stored!!.header));assertEquals(0,feedback);assertEquals(0,refreshes);assertNull(model.detail);session=SessionSnapshot(SessionState.READY,3);assertFalse(model.available);model.enter();model.open("r");assertEquals(1,model.sectionOrdinal);assertNotNull(model.detail)}
    @Test fun statusAndActionBindingRejectLateCallbacks(){saveA();model.changeAnswer("B_ENDING_MODE",MemoryAnswerValue.Multi(listOf("SELF_LEFT")));port.delay=true;model.save();val cb=port.pending!!;action=action.copy(status=ActionStatus.COMPLETED);assertFalse(model.available);cb(OperationsResult.Value(port.stored!!.header));assertEquals(1,feedback);model.clear();assertTrue(model.answers.isEmpty());model.enter();model.open("r");assertTrue(model.completedView);assertEquals(2,model.sectionOrdinal);action=action.copy(id="b");port.detailCallback!!(OperationsResult.Value(port.stored!!));assertFalse(model.available)}
    @Test fun reenterUsesNextUnsavedAndCompletedProgressUsesSummary(){saveA();model.clear();model.enter();model.open("r");assertEquals(1,model.sectionOrdinal);repeat(16){model.save()};model.clear();model.enter();model.open("r");assertEquals(MemoryReportMode.SUMMARY,model.mode)}
    @Test fun alreadyPublishedDetailCannotReplaceEditedEarlierSection() {
        saveA();val late=port.detailCallback!!;model.back();model.changeAuthor("Soligruppe Nord")
        late(OperationsResult.Value(port.stored!!))
        assertEquals(0,model.sectionOrdinal);assertEquals("Soligruppe Nord",model.authorLabel);assertTrue(model.dirty)
    }
    @Test fun synchronousInteractionRevokeStopsSaveBeforeWrite() {
        begin();onInteraction={session=SessionSnapshot(SessionState.LOCKED,2);model.clear()};model.save()
        assertEquals(0,port.writes);assertNull(model.detail);assertEquals("",model.authorLabel);assertFalse(model.available)
    }
    @Test fun forbiddenPhasesHaveNoPath(){for(s in listOf(ActionStatus.DRAFT,ActionStatus.PREPARATION,ActionStatus.DELETION_PENDING)){action=action.copy(status=s);model.enter();model.create();model.save();assertFalse(model.available)};assertEquals(0,port.lists);assertEquals(0,port.writes)}
    @Test fun deniedAnswerAuthorAndDerivedCopiesPreserveDraftAndNeverWrite(){
        begin();model.textAnswer("A_FREE_NARRATIVE","Synthetic held")
        val held=model.answers;val author=model.authorLabel
        model.bindCopyGate{_,_->OperationsFailure.BUDGET_REFUSED}
        model.textAnswer("A_FREE_NARRATIVE","Synthetic refused");assertSame(held,model.answers)
        model.changeAuthor("Synthetic refused");assertEquals(author,model.authorLabel)
        model.removeAnswer("A_FREE_NARRATIVE");assertSame(held,model.answers)
        model.save();assertEquals(0,port.writes);assertEquals(OperationsFailure.BUDGET_REFUSED,model.error)
    }

}
