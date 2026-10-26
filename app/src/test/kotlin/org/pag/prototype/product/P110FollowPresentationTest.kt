package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110FollowPresentationTest {
    private val action=Action("a","Synthetic","",1,2,0,ActionStatus.ACTIVE)
    @Test fun protocolDateAndSemanticsPresentationIsBorrowedAcrossHeldGenerations(){
        val repo=ProtocolFake();val model=ProtocolModel(repo,{SessionSnapshot(SessionState.READY,1)},{action},{},{},{})
        var positive=0
        model.bindCopyGate{units,producer->if(ActiveWorkspaceBudget.estimateUnits(units).second>0)positive++;producer();null}
        model.refresh();repo.timelineCallback!!(ProtocolResult.Value(listOf(ProtocolTimelineItem("e","r",1,ProtocolRevisionType.ORIGINAL,ProtocolClassification.RELEVANT,null,"Synthetic",1,false,2,false,false,false,2))))
        val initial=model.ui()!!;val count=positive
        for(n in listOf(100,1000,32768)){
            val aliases=List(n){model.ui()!!}
            for(alias in aliases){assertSame(initial,alias);assertSame(initial.rows["e"]!!.event,alias.rows["e"]!!.event)}
        }
        assertEquals(count,positive)
    }
    @Test fun memoryHeaderAnswerAndDateLabelsAreOneAdmittedPresentation(){
        val repo=MemoryFake();val model=MemoryReportModel(repo,{SessionSnapshot(SessionState.READY,1)},{action},{},{},{})
        var positive=0
        model.bindCopyGate{units,producer->if(ActiveWorkspaceBudget.estimateUnits(units).second>0)positive++;producer();null}
        model.enter();model.create();model.changeAuthor("Synthetic");model.textAnswer("A_FREE_NARRATIVE","Synthetic held answer");model.save()
        val old=model.ui()!!;val count=positive
        for(n in listOf(100,1000,32768)){
            val aliases=List(n){model.ui()!!}
            for(alias in aliases){assertSame(old,alias);assertSame(old.labels["A_FREE_NARRATIVE"],alias.labels["A_FREE_NARRATIVE"])}
        }
        assertEquals(count,positive);assertEquals("Revision: 0",old.revision)
        model.changeAnswer("B_AGREEMENT_OR_CONDITION",MemoryAnswerValue.Option("YES"));model.save()
        val fresh=model.ui()!!;assertNotSame(old,fresh);assertEquals("Revision: 1",fresh.revision);assertEquals("Revision: 0",old.revision)
    }
    @Test fun refusalBeforePresentationConstructorDoesNotInstallPayloadMaps(){
        val model=ProtocolModel(ProtocolFake(),{SessionSnapshot(SessionState.READY,1)},{action},{},{},{})
        model.bindCopyGate{units,producer->if(ActiveWorkspaceBudget.estimateUnits(units).second>0)OperationsFailure.BUDGET_REFUSED else {producer();null}}
        assertNull(model.ui());assertEquals(ProtocolFailure.BUDGET_REFUSED,model.error)
        assertNull(model.javaClass.getDeclaredField("readPresentation").let{it.isAccessible=true;it.get(model)})
    }
}
