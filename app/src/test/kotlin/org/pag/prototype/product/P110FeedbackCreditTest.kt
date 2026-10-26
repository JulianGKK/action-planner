package org.pag.prototype.product

import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

class P110FeedbackCreditTest {
    private class Scope:WorkspaceAccess {
        var live=true;var room=true;var positive=0
        override fun heldAllowed()=live
        override fun publish(actionId:String,status:ActionStatus,generation:Long,work:()->Unit):Boolean{if(!live)return false;work();return live}
        override fun reserve(units:ActiveWorkspaceBudget.Units):Boolean {
            val bytes=ActiveWorkspaceBudget.estimateUnits(units).second
            if(!live || (!room && bytes>0))return false
            if(bytes>0)positive++
            return true
        }
    }
    @Test fun prepaidCompletionWorksAtFullBudgetOnlyOnceAndCreatesNoNewAllowance(){
        val scope=Scope();assertTrue(scope.reserve(OriginalFeedbackCredit.cost))
        var result:String?=null;var count=0
        val credit=OriginalFeedbackCredit(scope,"a",1,{id,g->id=="a" && g==1L}){result=it;count++}
        scope.room=false
        credit("Gespeichert – Ansicht konnte noch nicht aktualisiert werden.")
        assertEquals(1,count);assertEquals("Gespeichert – Ansicht konnte noch nicht aktualisiert werden.",result)
        credit("Unexpected duplicate");assertEquals(1,count);assertEquals(1,scope.positive)
    }
    @Test fun revokedOrMovedCompletionDoesNotPublishAndCannotResurrect(){
        val scope=Scope();var action="a";var count=0
        assertTrue(scope.reserve(OriginalFeedbackCredit.cost))
        val credit=OriginalFeedbackCredit(scope,"a",1,{id,_->id==action}){count++}
        action="b";credit("Late")
        action="a";credit("ABA late");assertEquals(0,count)
        val other=OriginalFeedbackCredit(scope,"a",1,{_,_->true}){count++}
        scope.live=false;other("Locked");assertEquals(0,count)
    }
    @Test fun successNeedsNewReadAuthorityButConfirmedRefusalCanUseHeldMetadata(){
        val scope=Scope();var fresh=false;var count=0
        val success=OriginalFeedbackCredit(scope,"a",1,{_,_->true},{_,_->fresh}){count++}
        success("Success after fresh epoch revoke");assertEquals(0,count)
        fresh=true;success("Duplicate");assertEquals(0,count)
        fresh=false
        val refusal=OriginalFeedbackCredit(scope,"a",1,{_,_->true},{_,_->fresh}){count++}
        refusal(OriginalFeedbackCredit.savedNotRefreshed);assertEquals(1,count)
    }

}
