package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110FollowModelTest {
    private val action=Action("a","Synthetic","",1,2,0,ActionStatus.ACTIVE)
    @Test fun committedProtocolRefusalKeepsDraftAndNeverRetriesWriter(){
        val repo=ProtocolFake();var callback:((WorkspaceDetailResult)->Unit)?=null
        val messages=mutableListOf<String>()
        val model=ProtocolModel(repo,{SessionSnapshot(SessionState.READY,1)},{action},{},{},messages::add)
        model.bindDetailLoader{kind,_,done->assertEquals(WorkspaceDetailKind.PROTOCOL,kind);callback=done}
        model.create("POLICE_ARRIVED");val held=model.input;model.save()
        repo.writeCallback!!(ProtocolResult.Value(ProtocolSaved("e","r",1)))
        callback!!(WorkspaceDetailResult.Rejected(WorkspaceFailure.BUDGET_REFUSED))
        assertSame(held,model.input);assertTrue(model.commitConfirmed);assertTrue(model.form)
        model.save();assertEquals(1,repo.writes);assertEquals(0,repo.detailReads);assertEquals(0,repo.timelineReads)
        assertTrue(messages.contains("Gespeichert – Ansicht konnte noch nicht aktualisiert werden."))
    }
    @Test fun committedMemoryRefusalKeepsDraftAndNeverRetriesWriter(){
        val repo=MemoryFake();var callback:((WorkspaceDetailResult)->Unit)?=null
        val messages=mutableListOf<String>()
        val model=MemoryReportModel(repo,{SessionSnapshot(SessionState.READY,1)},{action},{},{},messages::add)
        model.bindDetailLoader{kind,_,done->assertEquals(WorkspaceDetailKind.MEMORY,kind);callback=done}
        model.enter(deferList=true);assertTrue(model.acceptWorkspaceHeaders("a",1,emptyList()));model.create();model.changeAuthor("Synthetic");model.textAnswer("A_FREE_NARRATIVE","Synthetic held")
        val held=model.answers;model.save();callback!!(WorkspaceDetailResult.Rejected(WorkspaceFailure.BUDGET_REFUSED))
        assertSame(held,model.answers);assertTrue(model.commitConfirmed);assertTrue(model.dirty)
        model.save();assertEquals(1,repo.writes)
        assertTrue(messages.contains("Gespeichert – Ansicht konnte noch nicht aktualisiert werden."))
        model.reload();assertSame(held,model.answers) // Decision alone does not discard before new admission.
    }
}
