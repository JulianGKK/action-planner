package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*
import java.io.ByteArrayInputStream

class DocumentModelTest {
    private var state=SessionSnapshot(SessionState.READY,1)
    private var action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
    private val handoff=PendingDocumentPicker<String>()
    private var opens=0;private var writes=0;private var deletes=0;private var refreshes=0;private var feedback=0
    private var deferred:((DocumentResult<Unit>)->Unit)?=null
    private var rows=listOf(DocumentAsset("d","a","synthetic",DocumentFormat.TEXT,3,1))
    private val picker=object:DocumentPicker {
        override fun launch(actionId:String,generation:Long) { handoff.begin(actionId,generation);state=SessionSnapshot(SessionState.LOCKED,generation+1);model.clear() }
        override fun available(actionId:String,generation:Long)=handoff.available(actionId,generation)
        override fun consume(actionId:String,generation:Long):DocumentSource? = handoff.consume(actionId,generation)?.let { DocumentSource { opens++;ByteArrayInputStream(it.toByteArray()) } }
        override fun cancel()=handoff.clear()
    }
    private val repo=object:DocumentRepository {
        override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit)=publish(DocumentResult.Value(DocumentSummary(rows.size)))
        override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit)=publish(DocumentResult.Value(rows))
        override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit) { writes++;assertEquals("new",name);source.open().use{it.readBytes()};deferred=publish }
        override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit) { deletes++;assertEquals(rows.single(),expected);deferred=publish }
    }
    private val model: DocumentModel=DocumentModel({repo},{picker},{state},{action},{},{refreshes++},{feedback++})
    private fun returnedAndReopened() {
        model.enter();model.chooseFile();handoff.returned("synthetic")
        assertEquals(0,writes);assertEquals(0,opens)
        state=SessionSnapshot(SessionState.READY,3);handoff.ready(3);model.enter()
        assertTrue(model.canContinueImport)
    }
    private fun draft() { returnedAndReopened();model.continueImport();model.changeName(" new ");model.changeFormat(DocumentFormat.TEXT) }
    @Test fun pickerCallbackNeverImportsAndOldGenerationCannotConsume() {
        handoff.begin("a",1);handoff.returned("source")
        assertFalse(handoff.available("a",1));assertNull(handoff.consume("a",1));assertNull(handoff.consume("b",3))
        assertEquals("source",handoff.consume("a",3));assertNull(handoff.consume("a",3));assertEquals(0,opens)
    }
    @Test fun newGenerationStillNeedsExplicitAreaAndConfirmation() {
        returnedAndReopened();assertFalse(model.importing);assertEquals(0,writes)
        model.continueImport();assertFalse(model.canSave)
        model.changeName(" new ");model.changeFormat(DocumentFormat.PDF);assertTrue(model.canSave)
        model.save();assertEquals(1,writes);assertEquals(1,opens);assertEquals(0,feedback)
        deferred!!(DocumentResult.Value(Unit));assertEquals(1,feedback);assertEquals(1,refreshes)
        assertFalse(model.importing);assertEquals("",model.name)
    }
    @Test fun cancelDoesNotWriteOrKeepSource() { draft();model.cancelImport();assertEquals(0,writes);assertEquals(0,opens);assertFalse(model.canContinueImport);assertEquals("",model.name) }
    @Test fun nameCodepointBoundariesAndFormatAreRequired() {
        returnedAndReopened();model.continueImport();model.changeName(" ");assertFalse(model.canSave)
        model.changeName("🦋".repeat(160));model.changeFormat(DocumentFormat.PNG);assertTrue(model.canSave)
        model.changeName("🦋".repeat(161));assertFalse(model.canSave);model.save();assertEquals(0,writes)
        assertEquals(4,DocumentFormat.entries.size)
    }
    @Test fun lockDropsListDraftDeleteAndLateSuccess() {
        draft();model.save();state=SessionSnapshot(SessionState.LOCKED,4);model.clear();deferred!!(DocumentResult.Value(Unit))
        assertNull(model.items);assertFalse(model.busy);assertNull(model.error);assertEquals("",model.name);assertNull(model.format);assertNull(model.confirmingDelete);assertEquals(0,feedback)
    }
    @Test fun actionAndExactStatusChangesRejectCallbacks() {
        draft();model.save();action=action.copy(status=ActionStatus.COMPLETED)
        deferred!!(DocumentResult.Value(Unit));assertEquals(0,feedback);assertFalse(model.available)
        model.clear();action=action.copy(id="b");model.enter();assertNull(model.items);assertEquals(DocumentFailure.INVALID,model.error)
    }
    @Test fun failureDoesNotPretendSuccessOrRetrySource() {
        draft();model.save();deferred!!(DocumentResult.Rejected(DocumentFailure.SOURCE_UNAVAILABLE))
        assertEquals(1,opens);assertEquals(0,feedback);assertEquals(0,refreshes);assertFalse(model.importing);assertFalse(model.canContinueImport)
        model.save();assertEquals(1,writes)
    }
    @Test fun deleteConfirmationCancelAndFailedDeleteLeaveList() {
        model.enter();model.requestDelete(rows.single());model.cancelDelete();assertEquals(0,deletes)
        model.requestDelete(rows.single());model.confirmDelete();assertEquals(1,deletes)
        deferred!!(DocumentResult.Rejected(DocumentFailure.STALE));assertEquals(rows,model.items);assertEquals(0,feedback);assertNotNull(model.confirmingDelete)
        model.cancelDelete();model.requestDelete(rows.single());model.confirmDelete();rows=emptyList();deferred!!(DocumentResult.Value(Unit))
        assertTrue(model.items!!.isEmpty());assertEquals(1,feedback)
    }
    @Test fun listDeterministicAndLifecycleRestricted() {
        rows=listOf(rows.single().copy(id="z",importedAt=2),rows.single().copy(id="b"),rows.single().copy(id="a"))
        for(status in listOf(ActionStatus.PREPARATION,ActionStatus.ACTIVE,ActionStatus.COMPLETED)) { action=action.copy(status=status);model.enter();assertEquals(listOf("a","b","z"),model.items!!.map{it.id}) }
        for(status in listOf(ActionStatus.DRAFT,ActionStatus.DELETION_PENDING)) { action=action.copy(status=status);model.enter();assertFalse(model.available);assertNull(model.items) }
    }
    @Test fun handoffDiesWithSecondLeaveCancellationOrNextSession() {
        handoff.begin("a",1);handoff.leaving(false);handoff.returned("source");handoff.leaving(true);assertTrue(handoff.available("a",3))
        handoff.ready(3);handoff.locked();assertFalse(handoff.available("a",5))
        handoff.begin("a",5);handoff.returned("source");handoff.leaving(false);assertFalse(handoff.available("a",7))
        handoff.begin("a",7);handoff.returned("source");handoff.actionSelected("b");assertFalse(handoff.available("a",9))
    }
    @Test fun activityRecreationDoesNotRestoreSourceAndUnsolicitedCallbackIgnored() {
        val recreated=PendingDocumentPicker<String>();recreated.returned("synthetic");assertFalse(recreated.available("a",4))
        handoff.begin("a",1);handoff.returned(null);assertFalse(handoff.available("a",2))
    }
    @Test fun clearedAreaRejectsOlderRequestEvenAfterReopen() {
        draft();model.save();val old=deferred!!;model.clear();model.enter();old(DocumentResult.Value(Unit));assertEquals(0,feedback);assertFalse(model.importing)
    }
}
