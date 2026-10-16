package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class DocumentTextModelTest {
    private var state=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
    private val asset=DocumentAsset("t","a","synthetic",DocumentFormat.TEXT,50,1)
    private var authority=true;private var interactions=0;private var onInteraction:()->Unit={}
    private data class Read(val page:Int,val request:DocumentTextRequest,val done:(DocumentResult<DocumentTextPage>)->Unit)
    private val reads=mutableListOf<Read>()
    private val repo=object:DocumentRepository {
        override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit)=publish(DocumentResult.Value(listOf(asset)))
        override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit)=publish(DocumentResult.Value(DocumentSummary(1)))
        override fun readText(actionId:String,expected:DocumentAsset,page:Int,request:DocumentTextRequest,publish:(DocumentResult<DocumentTextPage>)->Unit){assertEquals("a",actionId);assertEquals(asset,expected);reads+=Read(page,request,publish)}
        override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit)=error("No import")
        override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit)=error("No delete")
    }
    private val model=DocumentModel({repo},{null},{state},{action},{interactions++;onInteraction()},{},{},{authority})
    private fun open(){model.enter();model.viewText(asset)}
    private fun loaded(value:String="synthetic",pages:Int=3){val read=reads.last();read.done(DocumentResult.Value(DocumentTextPage(read.page,pages,value.toCharArray())))}
    @Test fun listAndHubDoNotReadTextUntilExplicitOpen(){model.enter();assertTrue(reads.isEmpty());assertNull(model.textViewed);model.viewText(asset);assertEquals(1,reads.size);assertTrue(model.textLoading);assertEquals(1L,reads.single().request.generation)}
    @Test fun onlyExactTextSelectionCanOpen(){model.enter();for(item in listOf(asset.copy(id="x"),asset.copy(actionId="b"),asset.copy(displayName="changed"))+DocumentFormat.entries.filter{it!=DocumentFormat.TEXT}.map{asset.copy(format=it)})model.viewText(item);assertTrue(reads.isEmpty())}
    @Test fun onlyPermittedActionPhasesMayRead(){for(status in listOf(ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)){action=action.copy(status=status);model.enter();model.viewText(asset)};assertTrue(reads.isEmpty())}
    @Test fun preparationActiveAndCompletedCanConsciouslyOpen(){for(status in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)){action=action.copy(status=status);open();loaded();assertEquals("synthetic",model.text);model.clear()};assertEquals(3,reads.size)}
    @Test fun newAuthoritativeSessionIsRequired(){model.enter();authority=false;model.viewText(asset);assertTrue(reads.isEmpty());authority=true;state=SessionSnapshot(SessionState.LOCKED,2);model.viewText(asset);assertTrue(reads.isEmpty())}
    @Test fun pagePublicationWipesTransferBuffer(){open();val chars="🦋\r\nsynthetic".toCharArray();reads.single().done(DocumentResult.Value(DocumentTextPage(0,2,chars)));assertEquals("🦋\r\nsynthetic",model.text);assertTrue(chars.all{it=='\u0000'});assertFalse(model.textLoading)}
    @Test fun forwardBackDropOldContentAndBoundNavigation(){open();loaded(pages=2);model.previousTextPage();assertEquals(1,reads.size);model.nextTextPage();assertNull(model.text);assertTrue(model.textLoading);assertFalse(reads.first().request.active);model.nextTextPage();assertEquals(2,reads.size);loaded("next",2);model.nextTextPage();assertEquals(2,reads.size);model.previousTextPage();assertEquals(0,reads.last().page);loaded("first",2);assertEquals("first",model.text)}
    @Test fun lateOldPageCannotReplaceCurrentPage(){open();loaded();model.nextTextPage();val chars="old".toCharArray();reads.first().done(DocumentResult.Value(DocumentTextPage(0,3,chars)));assertNull(model.text);assertTrue(chars.all{it=='\u0000'});loaded("new");assertEquals("new",model.text)}
    @Test fun closeWhileLoadingCancelsAndClosesLatePage(){open();model.closeText();assertFalse(reads.single().request.active);val chars="late".toCharArray();reads.single().done(DocumentResult.Value(DocumentTextPage(0,1,chars)));assertNull(model.text);assertNull(model.textViewed);assertTrue(chars.all{it=='\u0000'})}
    @Test fun lockWithVisiblePageAndUnlockNeverAutoOpens(){open();loaded();state=SessionSnapshot(SessionState.LOCKED,2);model.clear();assertNull(model.text);assertNull(model.textViewed);state=SessionSnapshot(SessionState.READY,3);model.enter();assertEquals(1,reads.size);assertNull(model.textViewed)}
    @Test fun actionAndGenerationChangesRejectLatePage(){open();action=action.copy(id="b");loaded();assertNull(model.text);model.clear();action=action.copy(id="a");open();state=SessionSnapshot(SessionState.READY,8);loaded();assertNull(model.text)}
    @Test fun errorStopsLoadingWithoutAutomaticRetryOrMutation(){open();reads.single().done(DocumentResult.Rejected(DocumentFailure.STALE));assertFalse(model.textLoading);assertEquals(DocumentFailure.STALE,model.error);assertEquals(1,reads.size);assertEquals(listOf(asset),model.items)}
    @Test fun callbacksAndFramesDoNotCountAsInteraction(){open();val count=interactions;loaded();model.textFrame();model.textFrame();assertEquals(count,interactions);model.nextTextPage();assertEquals(count+1,interactions)}
    @Test fun interactionRevokingSessionNeverStartsRead(){model.enter();onInteraction={state=SessionSnapshot(SessionState.LOCKED,2);model.clear()};model.viewText(asset);assertTrue(reads.isEmpty())}
    @Test fun wrongReturnedPageIsRejectedAndClosed(){open();val chars="wrong".toCharArray();reads.single().done(DocumentResult.Value(DocumentTextPage(1,2,chars)));assertNull(model.text);assertEquals(DocumentFailure.TEXT_UNSUPPORTED,model.error);assertTrue(chars.all{it=='\u0000'})}
    @Test fun longAndNewlineHeavyPagesHaveFiniteLosslessLayoutRows(){
        for(value in listOf("\n".repeat(32768),"x".repeat(32768),"x".repeat(255)+"🦋\r\ne\u0301".repeat(3000))) {
            val rows=documentTextRows(value);assertEquals(value,rows.joinToString(""));assertTrue(rows.all{it.length<=256})
            assertTrue(rows.none{it.firstOrNull()?.let(Character::isLowSurrogate)==true || it.lastOrNull()?.let(Character::isHighSurrogate)==true})
            for(i in 0 until rows.lastIndex)assertFalse(rows[i].endsWith("\r") && rows[i+1].startsWith("\n"))
        }
    }
}
